import SwiftUI

struct DashboardView: View {
    @EnvironmentObject private var ble: BLEManager

    var body: some View {
        NavigationStack {
            List {
                Section("Connection") {
                    row("Status", ble.connectionState.rawValue)
                    row("Device", ble.connectedDeviceName ?? "-")
                    row("Notifications", ble.notificationsSubscribed ? "subscribed" : "no")
                    row("Records pending", ble.recordsPending)
                }

                Section("Quick actions") {
                    Button("Write HELLO") { ble.writeHello() }
                        .disabled(ble.connectionState != .ready)
                    Button("Read STATUS_READ") { ble.readStatus() }
                        .disabled(ble.connectionState != .ready)
                    Button("Write SYNC_NOW") { ble.writeSyncNow() }
                        .disabled(ble.connectionState != .ready)
                }

                if let status = ble.lastStatusJSON {
                    Section("Last STATUS_READ") {
                        Text(status)
                            .font(.system(.footnote, design: .monospaced))
                            .textSelection(.enabled)
                    }
                }

                if let notification = ble.lastNotificationJSON {
                    Section("Last notification") {
                        Text(notification)
                            .font(.system(.footnote, design: .monospaced))
                            .textSelection(.enabled)
                    }
                }
            }
            .navigationTitle("TicHealthSync")
        }
    }

    private func row(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
            Spacer()
            Text(value).foregroundStyle(.secondary)
        }
    }
}

#Preview {
    DashboardView().environmentObject(BLEManager())
}
