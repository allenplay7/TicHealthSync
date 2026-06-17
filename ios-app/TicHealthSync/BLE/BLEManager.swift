import Combine
import CoreBluetooth
import Foundation

/// BLE central for TicHealthSync. The watch is the peripheral (advertiser + GATT
/// server); this is the central + GATT client. Per the project plan we use
/// CBCentralManager (NOT CBPeripheralManager) for this first iOS milestone.
///
/// Phase 3 scope: raw BLE receive/debug only. No local storage, no HealthKit.
final class BLEManager: NSObject, ObservableObject {

    enum ConnectionState: String {
        case poweredOff = "Bluetooth off"
        case disconnected = "Disconnected"
        case scanning = "Scanning"
        case connecting = "Connecting"
        case discovering = "Discovering services"
        case ready = "Ready"
    }

    // MARK: Published UI state
    @Published private(set) var bluetoothReady = false
    @Published private(set) var isScanning = false
    @Published private(set) var devices: [DiscoveredDevice] = []
    @Published private(set) var connectionState: ConnectionState = .disconnected
    @Published private(set) var connectedDeviceName: String?
    @Published private(set) var notificationsSubscribed = false
    @Published private(set) var log: [LogEntry] = []
    @Published private(set) var lastStatusJSON: String?
    @Published private(set) var lastNotificationJSON: String?
    @Published private(set) var recordsPending: String = "-"

    // MARK: Internals
    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var characteristics: [CBUUID: CBCharacteristic] = [:]
    private var discoveredPeripherals: [UUID: CBPeripheral] = [:]
    private let reassembler = ChunkReassembler()

    override init() {
        super.init()
        // Deliver delegate callbacks on the main queue so @Published updates are safe.
        central = CBCentralManager(delegate: self, queue: .main)
    }

    // MARK: Public actions

    func startScan() {
        guard central.state == .poweredOn else {
            append(.error, "Cannot scan: Bluetooth not powered on")
            return
        }
        devices.removeAll()
        discoveredPeripherals.removeAll()
        central.scanForPeripherals(withServices: [HealthProtocol.service], options: nil)
        isScanning = true
        if connectionState == .disconnected { connectionState = .scanning }
        append(.info, "Started scan for \(HealthProtocol.deviceName)")
    }

    func stopScan() {
        central.stopScan()
        isScanning = false
        if connectionState == .scanning { connectionState = .disconnected }
        append(.info, "Stopped scan")
    }

    func connect(_ device: DiscoveredDevice) {
        guard let target = discoveredPeripherals[device.id] else { return }
        stopScan()
        peripheral = target
        target.delegate = self
        connectionState = .connecting
        connectedDeviceName = device.name
        central.connect(target, options: nil)
        append(.info, "Connecting to \(device.name)")
    }

    func disconnect() {
        guard let peripheral else { return }
        central.cancelPeripheralConnection(peripheral)
        append(.info, "Disconnecting")
    }

    func writeHello() { writeControl(HealthProtocol.cmdHello) }

    func writeSyncNow() { writeControl(HealthProtocol.cmdSyncNow) }

    func generateFakeData() { writeControl(HealthProtocol.cmdGenerateFakeData) }

    func readStatus() {
        guard let peripheral, let characteristic = characteristics[HealthProtocol.statusRead] else {
            append(.error, "STATUS_READ not available")
            return
        }
        peripheral.readValue(for: characteristic)
        append(.outgoing, "Read STATUS_READ")
    }

    func writeAckBatch(_ batchId: String) {
        guard let peripheral, let characteristic = characteristics[HealthProtocol.ackWrite] else {
            append(.error, "ACK_WRITE not available")
            return
        }
        let command = "ACK_BATCH:\(batchId)"
        guard let data = command.data(using: .utf8) else { return }
        peripheral.writeValue(data, for: characteristic, type: .withResponse)
        append(.outgoing, "Write ACK_WRITE", detail: command)
    }

    func clearLog() { log.removeAll() }

    // MARK: Private

    private func writeControl(_ command: String) {
        guard let peripheral, let characteristic = characteristics[HealthProtocol.controlWrite] else {
            append(.error, "CONTROL_WRITE not available")
            return
        }
        guard let data = command.data(using: .utf8) else { return }
        peripheral.writeValue(data, for: characteristic, type: .withResponse)
        append(.outgoing, "Write CONTROL_WRITE", detail: command)
    }

    private func append(_ kind: LogEntry.Kind, _ title: String, detail: String? = nil) {
        log.insert(LogEntry(kind, title, detail: detail), at: 0)
        if log.count > 200 { log.removeLast(log.count - 200) }
    }

    private func handleIncoming(_ raw: String, from characteristic: CBUUID) {
        // DATA_NOTIFY payloads may be chunked; reassemble before displaying.
        let completed: String?
        if characteristic == HealthProtocol.dataNotify {
            completed = reassembler.accept(raw)
            if completed == nil {
                append(.info, "Received CHUNK (waiting for more)")
                return
            }
        } else {
            completed = raw
        }

        guard let payload = completed else { return }
        let pretty = JSONFormatter.pretty(payload)

        if characteristic == HealthProtocol.statusRead {
            lastStatusJSON = pretty
            if let pending = JSONFormatter.field("recordsPending", in: payload) {
                recordsPending = pending
            }
            append(.incoming, "STATUS_READ", detail: pretty)
        } else {
            lastNotificationJSON = pretty
            if let pending = JSONFormatter.field("recordsPending", in: payload) {
                recordsPending = pending
            }
            let type = JSONFormatter.field("type", in: payload) ?? "notification"
            append(.incoming, type, detail: pretty)
        }
    }

    private func resetConnection() {
        peripheral = nil
        characteristics.removeAll()
        notificationsSubscribed = false
        connectedDeviceName = nil
        connectionState = .disconnected
    }
}

// MARK: - CBCentralManagerDelegate

extension BLEManager: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        bluetoothReady = central.state == .poweredOn
        switch central.state {
        case .poweredOn:
            append(.info, "Bluetooth powered on")
        case .poweredOff:
            connectionState = .poweredOff
            append(.error, "Bluetooth is off")
        case .unauthorized:
            append(.error, "Bluetooth permission denied")
        case .unsupported:
            append(.error, "Bluetooth LE not supported on this device")
        default:
            append(.info, "Bluetooth state: \(central.state.rawValue)")
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        let advName = advertisementData[CBAdvertisementDataLocalNameKey] as? String
        let name = advName ?? peripheral.name ?? "Unknown"
        discoveredPeripherals[peripheral.identifier] = peripheral

        let device = DiscoveredDevice(id: peripheral.identifier, name: name, rssi: RSSI.intValue)
        if let index = devices.firstIndex(where: { $0.id == device.id }) {
            devices[index].rssi = device.rssi
        } else {
            devices.append(device)
            append(.info, "Found \(name) (\(RSSI.intValue) dBm)")
        }
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        connectionState = .discovering
        append(.info, "Connected; discovering services")
        peripheral.delegate = self
        peripheral.discoverServices([HealthProtocol.service])
    }

    func centralManager(
        _ central: CBCentralManager,
        didFailToConnect peripheral: CBPeripheral,
        error: Error?
    ) {
        append(.error, "Failed to connect", detail: error?.localizedDescription)
        resetConnection()
    }

    func centralManager(
        _ central: CBCentralManager,
        didDisconnectPeripheral peripheral: CBPeripheral,
        error: Error?
    ) {
        append(.info, "Disconnected", detail: error?.localizedDescription)
        resetConnection()
    }
}

// MARK: - CBPeripheralDelegate

extension BLEManager: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error {
            append(.error, "Service discovery failed", detail: error.localizedDescription)
            return
        }
        for service in peripheral.services ?? [] where service.uuid == HealthProtocol.service {
            peripheral.discoverCharacteristics(HealthProtocol.allCharacteristics, for: service)
        }
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didDiscoverCharacteristicsFor service: CBService,
        error: Error?
    ) {
        if let error {
            append(.error, "Characteristic discovery failed", detail: error.localizedDescription)
            return
        }
        for characteristic in service.characteristics ?? [] {
            characteristics[characteristic.uuid] = characteristic
        }

        // Subscribe to DATA_NOTIFY so we receive pushed JSON.
        if let notify = characteristics[HealthProtocol.dataNotify] {
            peripheral.setNotifyValue(true, for: notify)
        }
        // Prime the dashboard with an initial status read.
        if let status = characteristics[HealthProtocol.statusRead] {
            peripheral.readValue(for: status)
        }

        connectionState = .ready
        append(.info, "Ready: \(characteristics.count) characteristics discovered")
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateNotificationStateFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        if characteristic.uuid == HealthProtocol.dataNotify {
            notificationsSubscribed = characteristic.isNotifying
            append(.info, characteristic.isNotifying
                ? "Subscribed to DATA_NOTIFY"
                : "Unsubscribed from DATA_NOTIFY")
        }
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateValueFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        if let error {
            append(.error, "Read error on \(HealthProtocol.shortName(for: characteristic.uuid))",
                   detail: error.localizedDescription)
            return
        }
        guard let data = characteristic.value else { return }
        let raw = String(data: data, encoding: .utf8) ?? data.map { String(format: "%02X", $0) }.joined(separator: " ")
        handleIncoming(raw, from: characteristic.uuid)
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didWriteValueFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        if let error {
            append(.error, "Write failed on \(HealthProtocol.shortName(for: characteristic.uuid))",
                   detail: error.localizedDescription)
        } else {
            append(.info, "Write confirmed: \(HealthProtocol.shortName(for: characteristic.uuid))")
        }
    }
}
