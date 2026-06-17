package com.tichealthsync.watch.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.tichealthsync.watch.data.HealthRecordEntity
import com.tichealthsync.watch.data.HealthRepository
import com.tichealthsync.watch.data.SyncSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.random.Random

class TicHealthBleManager(private val context: Context) {
    private val bluetoothManager =
        context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val advertiser
        get() = bluetoothAdapter?.bluetoothLeAdvertiser

    private var gattServer: BluetoothGattServer? = null
    private var dataNotifyCharacteristic: BluetoothGattCharacteristic? = null
    private var lastDataPayload: String = "{}"

    private val connectedDevices = CopyOnWriteArraySet<BluetoothDevice>()
    private val subscribedDevices = CopyOnWriteArraySet<BluetoothDevice>()

    // Phase 2: records live in Room, not memory. The repository owns all DB access.
    private val repository = HealthRepository(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Cached, Room-sourced count of unsynced records. STATUS_READ and the UI read this
    // because GATT callbacks run on a binder thread and cannot await a suspend query.
    // It is kept in sync by collecting the DAO's reactive Flow below.
    @Volatile
    private var pendingCountCache = 0

    // Negotiated ATT MTU. Until the central performs an MTU exchange this stays at
    // the BLE default (23), giving only 20 usable payload bytes. iOS negotiates a
    // larger MTU automatically; some Android centrals must request it explicitly.
    @Volatile
    private var negotiatedMtu = DEFAULT_MTU

    // Notification serialization queue (see drainNotificationQueue).
    private val notificationQueue = ConcurrentLinkedQueue<ByteArray>()
    private val notifyLock = Any()
    private var awaitingAcks = 0

    private val _state = MutableStateFlow(
        BleUiState(bluetoothSupported = bluetoothAdapter != null && advertiser != null)
    )
    val state: StateFlow<BleUiState> = _state

    init {
        // Reactively mirror the Room pending count into UI state.
        scope.launch {
            repository.observeUnsyncedCount().collect { count ->
                pendingCountCache = count
                updateState { this }
            }
        }
    }

    fun refreshCapabilities() {
        updateState {
            copy(bluetoothSupported = bluetoothAdapter != null && advertiser != null)
        }
    }

    fun start() {
        if (!hasBlePermissions()) {
            updateError("Missing Bluetooth advertise/connect permission")
            return
        }
        if (bluetoothAdapter == null || advertiser == null) {
            updateError("Bluetooth LE advertising is not supported")
            return
        }
        if (bluetoothAdapter.isEnabled != true) {
            updateError("Bluetooth is disabled")
            return
        }

        startGattServer()
        startAdvertising()
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        runCatching { advertiser?.stopAdvertising(advertiseCallback) }
        updateState { copy(advertising = false) }
    }

    fun shutdown() {
        stop()
        runCatching { gattServer?.close() }
        gattServer = null
        dataNotifyCharacteristic = null
        connectedDevices.clear()
        subscribedDevices.clear()
        synchronized(notifyLock) {
            notificationQueue.clear()
            awaitingAcks = 0
        }
        scope.cancel()
        updateState {
            copy(
                gattServerRunning = false,
                connectedDeviceCount = 0
            )
        }
    }

    fun generateFakeRecord(type: String) {
        dbLaunch("insert $type") {
            val record = insertFakeRecord(type)
            val pending = repository.countUnsynced()
            updateState { copy(lastCommand = "generated ${record.type}", lastError = "") }
            notifyJson(JSONObject().apply {
                put("type", "FAKE_RECORD_CREATED")
                put("recordId", record.recordId)
                put("recordType", record.type)
                put("recordsPending", pending)
            }.toString())
        }
    }

    fun clearRecords() {
        dbLaunch("clear records") {
            repository.clearAll()
            Log.i(TAG, "Room: cleared all records")
            updateState { copy(lastCommand = "local_clear_records", lastError = "") }
        }
    }

    fun resetSyncState() {
        dbLaunch("reset sync state") {
            val reset = repository.resetNonSynced()
            Log.i(TAG, "Room: reset $reset non-synced records to pending")
            updateState { copy(lastCommand = "local_reset_sync_state", lastError = "") }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        if (gattServer != null) return

        val server = bluetoothManager.openGattServer(context, gattServerCallback)
        if (server == null) {
            updateError("Could not open GATT server")
            return
        }

        val service = BluetoothGattService(
            SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        service.addCharacteristic(
            namedCharacteristic(
                STATUS_READ_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
                "STATUS_READ"
            )
        )
        service.addCharacteristic(
            namedCharacteristic(
                CONTROL_WRITE_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
                "CONTROL_WRITE"
            )
        )
        dataNotifyCharacteristic = namedCharacteristic(
            DATA_NOTIFY_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
            "DATA_NOTIFY"
        ).apply {
            addDescriptor(
                BluetoothGattDescriptor(
                    CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or
                        BluetoothGattDescriptor.PERMISSION_WRITE
                )
            )
        }
        service.addCharacteristic(dataNotifyCharacteristic)
        service.addCharacteristic(
            namedCharacteristic(
                ACK_WRITE_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
                "ACK_WRITE"
            )
        )
        service.addCharacteristic(
            namedCharacteristic(
                TIME_SYNC_WRITE_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
                "TIME_SYNC_WRITE"
            )
        )

        server.addService(service)
        gattServer = server
        Log.i(TAG, "GATT server started with TicHealthSync service $SERVICE_UUID")
        updateState { copy(gattServerRunning = true, lastError = "") }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        bluetoothAdapter?.name = DEVICE_NAME

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeTxPowerLevel(false)
            .setIncludeDeviceName(false)
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        advertiser?.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            updateState { copy(advertising = true, lastError = "") }
        }

        override fun onStartFailure(errorCode: Int) {
            updateError("Advertise failed: ${advertiseErrorName(errorCode)}")
            updateState { copy(advertising = false) }
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                connectedDevices.add(device)
                Log.i(TAG, "Device connected: ${safeDeviceName(device)} status=$status")
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                connectedDevices.remove(device)
                subscribedDevices.remove(device)
                if (connectedDevices.isEmpty()) {
                    // Next connection renegotiates from the BLE default.
                    negotiatedMtu = DEFAULT_MTU
                }
                if (subscribedDevices.isEmpty()) {
                    // No one left to notify; drop any queued payloads and the
                    // in-flight ack counter so the queue can't stall.
                    synchronized(notifyLock) {
                        notificationQueue.clear()
                        awaitingAcks = 0
                    }
                }
                Log.i(TAG, "Device disconnected: ${safeDeviceName(device)} status=$status")
            }
            updateState { copy(connectedDeviceCount = connectedDevices.size) }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            negotiatedMtu = mtu
            Log.i(TAG, "MTU changed to $mtu (usable payload ${mtu - ATT_HEADER} bytes) for ${safeDeviceName(device)}")
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val payload = when (characteristic.uuid) {
                STATUS_READ_UUID -> statusJson()
                DATA_NOTIFY_UUID -> latestDataReadJson()
                else -> "{}"
            }.toByteArray(StandardCharsets.UTF_8)

            Log.d(TAG, "Read ${characteristic.uuid} offset=$offset bytes=${payload.size}")
            gattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                payload.drop(offset).toByteArray()
            )
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            val text = value.toString(StandardCharsets.UTF_8).trim()
            Log.i(TAG, "Write ${characteristic.uuid}: '$text' responseNeeded=$responseNeeded")

            // Complete the GATT write transaction FIRST. Sending a notification
            // while a write response is still pending can cause the central
            // (e.g. nRF Connect) to miss the notification, which is why a single
            // HELLO_ACK never arrived. Respond, then process the command.
            if (responseNeeded) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    byteArrayOf()
                )
            }

            when (characteristic.uuid) {
                CONTROL_WRITE_UUID -> handleControlCommand(text)
                ACK_WRITE_UUID -> handleAck(text)
                TIME_SYNC_WRITE_UUID -> {
                    updateState { copy(lastCommand = "TIME_SYNC_WRITE: $text", lastError = "") }
                    notifyJson(simpleEvent("TIME_SYNC_RECEIVED", "value" to text))
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            val value = when (descriptor.uuid) {
                CCCD_UUID ->
                    if (subscribedDevices.contains(device)) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                CUD_UUID -> descriptorName(descriptor).toByteArray(StandardCharsets.UTF_8)
                else -> byteArrayOf()
            }
            Log.d(TAG, "Read descriptor ${descriptor.uuid} value=${value.toHexString()}")
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (descriptor.uuid == CCCD_UUID) {
                val enabled = value.isNotEmpty() && value[0].toInt() != 0
                Log.i(TAG, "CCCD write value=${value.toHexString()} enabled=$enabled")
                if (enabled) {
                    subscribedDevices.add(device)
                    updateState { copy(lastCommand = "DATA_NOTIFY subscribed", lastError = "") }
                } else {
                    subscribedDevices.remove(device)
                    updateState { copy(lastCommand = "DATA_NOTIFY unsubscribed", lastError = "") }
                }
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            val statusText = if (status == BluetoothGatt.GATT_SUCCESS) "success" else "status=$status"
            Log.i(TAG, "Notification sent to ${safeDeviceName(device)}: $statusText")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                updateError("notify failure (status=$status)")
            }
            synchronized(notifyLock) {
                if (awaitingAcks > 0) awaitingAcks--
            }
            // This in-flight notification is acknowledged; send the next queued one.
            drainNotificationQueue()
        }
    }

    private fun handleControlCommand(command: String) {
        updateState { copy(lastCommand = command, lastError = "") }
        when (command.uppercase()) {
            "HELLO" -> notifyJson(simpleEvent("HELLO_ACK", "message" to "TicHealthSync ready"))
            "GET_STATUS" -> notifyJson(statusJson())
            "GENERATE_FAKE_DATA" -> dbLaunch("generate fake data") {
                insertFakeRecord("heart_rate")
                insertFakeRecord("step_count")
                val pending = repository.countUnsynced()
                updateState { copy(lastCommand = "generated heart_rate+step_count", lastError = "") }
                notifyJson(simpleEvent("GENERATE_FAKE_DATA_ACK", "recordsPending" to pending))
            }
            "SYNC_NOW" -> dbLaunch("sync") { sendNextRecordBatch() }
            "ACK_BATCH", "CLEAR_SYNCED" -> handleAck(command)
            else -> notifyJson(simpleEvent("ERROR", "message" to "Unsupported command: $command"))
        }
    }

    /** Build a fake record, assign its sequence in Room, and persist it. */
    private suspend fun insertFakeRecord(type: String): HealthRecordEntity {
        val now = OffsetDateTime.now()
        val recordType = if (type in SUPPORTED_TYPES) type else "heart_rate"
        val value = when (recordType) {
            "heart_rate" -> Random.nextInt(68, 96).toDouble()
            "step_count" -> Random.nextInt(20, 250).toDouble()
            "active_energy" -> Random.nextInt(5, 40).toDouble()
            "distance" -> Random.nextInt(10, 250).toDouble()
            "workout_start", "workout_end" -> 1.0
            else -> 0.0
        }
        val unit = when (recordType) {
            "heart_rate" -> "bpm"
            "step_count" -> "count"
            "active_energy" -> "kcal"
            "distance" -> "m"
            "workout_start", "workout_end" -> "event"
            else -> "unknown"
        }

        val record = HealthRecordEntity(
            recordId = UUID.randomUUID().toString(),
            deviceId = DEVICE_ID,
            type = recordType,
            value = value,
            unit = unit,
            startTime = now.minusSeconds(5).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            endTime = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            createdAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            sequence = 0,
            syncStatus = "pending",
            batchId = null
        )
        val stored = repository.insertWithNextSequence(record)
        Log.i(TAG, "Room: inserted ${stored.type} seq=${stored.sequence} id=${stored.recordId}")
        return stored
    }

    private suspend fun sendNextRecordBatch() {
        val record = repository.nextPendingRecord()
        if (record == null) {
            updateState { copy(lastError = "no pending records") }
            notifyJson(simpleEvent("NO_PENDING_RECORDS", "recordsPending" to repository.countUnsynced()))
            return
        }

        val batchId = "batch-${System.currentTimeMillis()}"
        val sessionId = "sync-${System.currentTimeMillis()}"
        repository.startSession(SyncSessionEntity(syncSessionId = sessionId, startedAt = nowIso()))
        repository.markSentUnconfirmed(record.recordId, batchId)
        Log.i(TAG, "Room: sync record ${record.recordId} seq=${record.sequence} -> batch $batchId")

        val sentRecord = record.copy(syncStatus = "sent_unconfirmed", batchId = batchId)
        val payload = JSONObject().apply {
            put("type", "RECORD_BATCH")
            put("batchId", batchId)
            put("records", JSONArray().put(recordToJson(sentRecord)))
        }.toString()

        repository.completeSession(sessionId, nowIso(), 1, "sent", null)
        updateState { copy(lastCommand = "sync batch $batchId", lastError = "") }
        notifyJson(payload)
    }

    private fun handleAck(value: String) {
        val ackBatchId = extractBatchId(value)
        dbLaunch("ack") {
            val updated = if (ackBatchId == null) {
                repository.markAllSentSynced()
            } else {
                repository.markBatchSynced(ackBatchId)
            }
            Log.i(TAG, "Room: ack batch=${ackBatchId ?: "all_sent"} marked $updated synced")
            updateState {
                copy(lastCommand = "ack batch ${ackBatchId ?: "all_sent"}", lastError = "")
            }
            notifyJson(simpleEvent("ACK_RECEIVED", "batchId" to (ackBatchId ?: "all_sent")))
        }
    }

    private fun extractBatchId(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.startsWith("{")) {
            return runCatching { JSONObject(trimmed).optString("batchId").ifBlank { null } }
                .getOrNull()
        }
        return trimmed.substringAfter("batchId=", "")
            .ifBlank { trimmed.substringAfter("ACK_BATCH:", "") }
            .ifBlank { null }
    }

    private fun statusJson(): String = JSONObject().apply {
        put("status", "ready")
        put("deviceId", DEVICE_ID)
        put("recordsPending", pendingCountCache)
        put("protocolVersion", 1)
        put("advertising", state.value.advertising)
        put("gattServer", if (state.value.gattServerRunning) "running" else "stopped")
    }.toString()

    private fun latestDataReadJson(): String = JSONObject().apply {
        put("type", "DATA_NOTIFY_READ")
        put("recordsPending", pendingCountCache)
        put("subscribedDevices", subscribedDevices.size)
        put("lastPayload", lastDataPayload)
        put("hint", "Subscribe to receive notifications, then write SYNC_NOW")
    }.toString()

    private fun recordToJson(record: HealthRecordEntity): JSONObject = JSONObject().apply {
        put("recordId", record.recordId)
        put("deviceId", record.deviceId)
        put("type", record.type)
        put("value", record.value)
        put("unit", record.unit)
        put("startTime", record.startTime)
        put("endTime", record.endTime)
        put("createdAt", record.createdAt)
        put("sequence", record.sequence)
        put("syncStatus", record.syncStatus)
    }

    private fun simpleEvent(type: String, vararg pairs: Pair<String, Any>): String =
        JSONObject().apply {
            put("type", type)
            pairs.forEach { (key, value) -> put(key, value) }
        }.toString()

    private fun notifyJson(json: String) {
        if (dataNotifyCharacteristic == null) return
        lastDataPayload = json
        if (subscribedDevices.isEmpty()) {
            Log.w(TAG, "No DATA_NOTIFY subscribers; cached payload for read: $json")
            updateError("no notification subscriber")
            return
        }

        val payloads = buildNotificationPayloads(json)

        payloads.forEach { payload ->
            val bytes = payload.toByteArray(StandardCharsets.UTF_8)
            Log.i(TAG, "Queue notify ${bytes.size} bytes: $payload")
            notificationQueue.add(bytes)
        }
        drainNotificationQueue()
    }

    /**
     * Splits [json] into one or more notification payloads that each fit inside the
     * negotiated ATT MTU (mtu - 3 bytes). If it fits in a single packet it is sent
     * as-is; otherwise it is wrapped in CHUNK envelopes. The per-chunk character
     * budget conservatively assumes worst-case JSON escaping (each char may double
     * when embedded as a string) plus a fixed envelope overhead, so the serialized
     * CHUNK never exceeds the MTU.
     */
    private fun buildNotificationPayloads(json: String): List<String> {
        val attPayload = (negotiatedMtu - ATT_HEADER).coerceAtLeast(DEFAULT_MTU - ATT_HEADER)

        if (json.toByteArray(StandardCharsets.UTF_8).size <= attPayload) {
            return listOf(json)
        }

        val perChunkChars = ((attPayload - CHUNK_ENVELOPE_OVERHEAD) / 2).coerceAtLeast(MIN_CHUNK_CHARS)
        if (attPayload - CHUNK_ENVELOPE_OVERHEAD < 2 * MIN_CHUNK_CHARS) {
            Log.w(
                TAG,
                "Negotiated MTU $negotiatedMtu is too small to chunk safely; " +
                    "central should request a larger MTU. Sending best-effort chunks."
            )
        }

        val batchId = "chunk-${System.currentTimeMillis()}"
        val rawChunks = json.chunked(perChunkChars)
        return rawChunks.mapIndexed { index, payload ->
            JSONObject().apply {
                put("type", "CHUNK")
                put("batchId", batchId)
                put("chunkIndex", index)
                put("chunkCount", rawChunks.size)
                put("payload", payload)
            }.toString()
        }
    }

    /**
     * Sends queued notifications one at a time. A new send only starts once the
     * previous one has been acknowledged via onNotificationSent (tracked by
     * [awaitingAcks]). This prevents the GATT stack from silently dropping
     * notifications fired in quick succession (e.g. GENERATE_FAKE_DATA, SYNC_NOW).
     */
    private fun drainNotificationQueue() {
        synchronized(notifyLock) {
            if (awaitingAcks > 0) return

            val devices = subscribedDevices.toList()
            if (devices.isEmpty()) {
                notificationQueue.clear()
                return
            }

            val next = notificationQueue.poll() ?: return
            var accepted = 0
            devices.forEach { device ->
                if (sendNotification(device, next)) accepted++
            }
            if (accepted > 0) {
                awaitingAcks = accepted
            } else {
                // The stack refused every send (e.g. congestion). Drop this
                // payload rather than stalling the queue forever.
                Log.w(TAG, "Notification not accepted by GATT stack; dropping payload")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendNotification(device: BluetoothDevice, value: ByteArray): Boolean {
        val characteristic = dataNotifyCharacteristic ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = gattServer?.notifyCharacteristicChanged(device, characteristic, false, value)
            Log.d(TAG, "notifyCharacteristicChanged result=$result device=${safeDeviceName(device)}")
            result == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            val result = gattServer?.notifyCharacteristicChanged(device, characteristic, false) ?: false
            Log.d(TAG, "notifyCharacteristicChanged result=$result device=${safeDeviceName(device)}")
            result
        }
    }

    private fun namedCharacteristic(
        uuid: UUID,
        properties: Int,
        permissions: Int,
        name: String
    ): BluetoothGattCharacteristic =
        BluetoothGattCharacteristic(uuid, properties, permissions).apply {
            addDescriptor(
                BluetoothGattDescriptor(
                    CUD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ
                ).also { descriptor ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        @Suppress("DEPRECATION")
                        descriptor.value = name.toByteArray(StandardCharsets.UTF_8)
                    }
                }
            )
        }

    private fun descriptorName(descriptor: BluetoothGattDescriptor): String =
        when (descriptor.characteristic?.uuid) {
            STATUS_READ_UUID -> "STATUS_READ"
            CONTROL_WRITE_UUID -> "CONTROL_WRITE"
            DATA_NOTIFY_UUID -> "DATA_NOTIFY"
            ACK_WRITE_UUID -> "ACK_WRITE"
            TIME_SYNC_WRITE_UUID -> "TIME_SYNC_WRITE"
            else -> "TicHealthSync characteristic"
        }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String =
        runCatching { device.name ?: device.address ?: "unknown" }.getOrDefault("unknown")

    private fun ByteArray.toHexString(): String =
        joinToString(separator = " ") { byte -> "%02X".format(byte) }

    private fun nowIso(): String = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    /** Run a Room operation off the binder thread, surfacing failures to the UI. */
    private fun dbLaunch(label: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "Room failure during $label", e)
                updateError("Room write/read failure")
            }
        }
    }

    private fun hasBlePermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) ==
            PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun updateError(message: String) {
        updateState { copy(lastError = message) }
    }

    private fun updateState(reducer: BleUiState.() -> BleUiState) {
        _state.update { current ->
            current.reducer().copy(
                bluetoothSupported = bluetoothAdapter != null && advertiser != null,
                connectedDeviceCount = connectedDevices.size,
                pendingRecordsCount = pendingCountCache
            )
        }
    }

    private fun advertiseErrorName(errorCode: Int): String = when (errorCode) {
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "already started"
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "data too large"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "feature unsupported"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "internal error"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "too many advertisers"
        else -> "code $errorCode"
    }

    companion object {
        const val DEVICE_NAME = "TicHealthSync"
        const val DEVICE_ID = "ticwatch-pro-5-main"

        val SERVICE_UUID: UUID = UUID.fromString("7f5c7800-7a64-4f9d-9a6f-0f5e7a6f1000")
        val STATUS_READ_UUID: UUID = UUID.fromString("7f5c7801-7a64-4f9d-9a6f-0f5e7a6f1000")
        val CONTROL_WRITE_UUID: UUID = UUID.fromString("7f5c7802-7a64-4f9d-9a6f-0f5e7a6f1000")
        val DATA_NOTIFY_UUID: UUID = UUID.fromString("7f5c7803-7a64-4f9d-9a6f-0f5e7a6f1000")
        val ACK_WRITE_UUID: UUID = UUID.fromString("7f5c7804-7a64-4f9d-9a6f-0f5e7a6f1000")
        val TIME_SYNC_WRITE_UUID: UUID = UUID.fromString("7f5c7805-7a64-4f9d-9a6f-0f5e7a6f1000")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val CUD_UUID: UUID = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb")

        private const val TAG = "TicHealthSyncBle"

        // BLE default ATT MTU and the 3-byte ATT notification/write header.
        private const val DEFAULT_MTU = 23
        private const val ATT_HEADER = 3

        // Fixed size of a CHUNK envelope without its payload contents (type, batchId,
        // indices, JSON punctuation), with headroom for chunk-count digits.
        private const val CHUNK_ENVELOPE_OVERHEAD = 110
        private const val MIN_CHUNK_CHARS = 8

        private val SUPPORTED_TYPES = setOf(
            "heart_rate",
            "step_count",
            "active_energy",
            "distance",
            "workout_start",
            "workout_end"
        )
    }
}
