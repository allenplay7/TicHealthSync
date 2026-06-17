package com.tichealthsync.watch.ble

data class BleUiState(
    val bluetoothSupported: Boolean = false,
    val advertising: Boolean = false,
    val gattServerRunning: Boolean = false,
    val connectedDeviceCount: Int = 0,
    val pendingRecordsCount: Int = 0,
    val lastCommand: String = "",
    val lastError: String = ""
)

