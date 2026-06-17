package com.tichealthsync.watch

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.tichealthsync.watch.ble.BleUiState
import com.tichealthsync.watch.ble.TicHealthBleManager

class MainActivity : ComponentActivity() {
    private lateinit var bleManager: TicHealthBleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleManager = TicHealthBleManager(applicationContext)

        setContent {
            val state by bleManager.state.collectAsState()
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { bleManager.refreshCapabilities() }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_ADVERTISE,
                            Manifest.permission.BLUETOOTH_CONNECT
                        )
                    )
                }
            }

            TicHealthSyncScreen(
                state = state,
                onStartAdvertising = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_ADVERTISE,
                                Manifest.permission.BLUETOOTH_CONNECT
                            )
                        )
                    }
                    bleManager.start()
                },
                onStopAdvertising = bleManager::stop,
                onGenerateHeartRate = { bleManager.generateFakeRecord("heart_rate") },
                onGenerateSteps = { bleManager.generateFakeRecord("step_count") },
                onClearRecords = bleManager::clearRecords,
                onResetSync = bleManager::resetSyncState
            )
        }
    }

    override fun onDestroy() {
        bleManager.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun TicHealthSyncScreen(
    state: BleUiState,
    onStartAdvertising: () -> Unit,
    onStopAdvertising: () -> Unit,
    onGenerateHeartRate: () -> Unit,
    onGenerateSteps: () -> Unit,
    onClearRecords: () -> Unit,
    onResetSync: () -> Unit
) {
    MaterialTheme {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "TicHealthSync",
                    style = MaterialTheme.typography.title2,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    StatusLine("Bluetooth supported", state.bluetoothSupported.yesNo())
                    StatusLine("Advertising", state.advertising.yesNo())
                    StatusLine("GATT server", if (state.gattServerRunning) "running" else "stopped")
                    StatusLine("Connected devices", state.connectedDeviceCount.toString())
                    StatusLine("Pending records", state.pendingRecordsCount.toString())
                    StatusLine("Last command", state.lastCommand.ifBlank { "-" })
                    StatusLine("Last error", state.lastError.ifBlank { "-" })
                }
            }
            item { ActionChip("Start BLE Advertising", onStartAdvertising) }
            item { ActionChip("Stop BLE Advertising", onStopAdvertising) }
            item { ActionChip("Generate Fake Heart Rate Record", onGenerateHeartRate) }
            item { ActionChip("Generate Fake Step Count Record", onGenerateSteps) }
            item { ActionChip("Clear Local Records", onClearRecords) }
            item { ActionChip("Reset Sync State", onResetSync) }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        style = MaterialTheme.typography.caption1
    )
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        label = { Text(label) }
    )
}

private fun Boolean.yesNo(): String = if (this) "yes" else "no"

