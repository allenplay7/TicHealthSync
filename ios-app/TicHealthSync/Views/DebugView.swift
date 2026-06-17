import SwiftUI

struct DebugView: View {
    @EnvironmentObject private var ble: BLEManager

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                actionBar

                Divider()

                if ble.log.isEmpty {
                    ContentUnavailableView(
                        "No activity yet",
                        systemImage: "ladybug",
                        description: Text("Connect on the Devices tab, then send a command.")
                    )
                    .frame(maxHeight: .infinity)
                } else {
                    List(ble.log) { entry in
                        LogRow(entry: entry)
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Debug")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Clear") { ble.clearLog() }
                        .disabled(ble.log.isEmpty)
                }
            }
        }
    }

    private var actionBar: some View {
        let ready = ble.connectionState == .ready
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack {
                Button("HELLO") { ble.writeHello() }.disabled(!ready)
                Button("STATUS_READ") { ble.readStatus() }.disabled(!ready)
                Button("SYNC_NOW") { ble.writeSyncNow() }.disabled(!ready)
                Button("GENERATE") { ble.generateFakeData() }.disabled(!ready)
            }
            .buttonStyle(.borderedProminent)
            .padding(.horizontal)
            .padding(.vertical, 8)
        }
    }
}

private struct LogRow: View {
    let entry: LogEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .foregroundStyle(color)
                Text(entry.title)
                    .font(.subheadline.weight(.semibold))
                Spacer()
                Text(entry.timestamp, format: .dateTime.hour().minute().second())
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            if let detail = entry.detail {
                Text(detail)
                    .font(.system(.caption, design: .monospaced))
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
            }
        }
        .padding(.vertical, 2)
    }

    private var icon: String {
        switch entry.kind {
        case .incoming: return "arrow.down.circle.fill"
        case .outgoing: return "arrow.up.circle.fill"
        case .info: return "info.circle"
        case .error: return "exclamationmark.triangle.fill"
        }
    }

    private var color: Color {
        switch entry.kind {
        case .incoming: return .green
        case .outgoing: return .blue
        case .info: return .secondary
        case .error: return .red
        }
    }
}

#Preview {
    DebugView().environmentObject(BLEManager())
}
