import SwiftUI

struct DevicesView: View {
    @EnvironmentObject private var ble: BLEManager

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if ble.isScanning {
                        Button(role: .cancel) { ble.stopScan() } label: {
                            Label("Stop scanning", systemImage: "stop.circle")
                        }
                    } else {
                        Button { ble.startScan() } label: {
                            Label("Scan for TicHealthSync", systemImage: "magnifyingglass")
                        }
                        .disabled(!ble.bluetoothReady)
                    }

                    if ble.connectedDeviceName != nil {
                        Button(role: .destructive) { ble.disconnect() } label: {
                            Label("Disconnect", systemImage: "xmark.circle")
                        }
                    }
                }

                Section("Discovered") {
                    if ble.devices.isEmpty {
                        Text(ble.isScanning ? "Scanning…" : "No devices found")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(ble.devices) { device in
                            Button { ble.connect(device) } label: {
                                HStack {
                                    VStack(alignment: .leading) {
                                        Text(device.name)
                                        Text(device.id.uuidString)
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Text("\(device.rssi) dBm")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .navigationTitle("Devices")
        }
    }
}

#Preview {
    DevicesView().environmentObject(BLEManager())
}
