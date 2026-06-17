import Foundation

/// A peripheral found during scanning.
struct DiscoveredDevice: Identifiable, Equatable {
    let id: UUID          // CBPeripheral.identifier
    let name: String
    var rssi: Int
}

/// One line in the Debug log.
struct LogEntry: Identifiable {
    enum Kind {
        case incoming   // notification / read result from the watch
        case outgoing   // command we wrote
        case info       // connection lifecycle / status
        case error
    }

    let id = UUID()
    let timestamp: Date
    let kind: Kind
    let title: String
    let detail: String?

    init(_ kind: Kind, _ title: String, detail: String? = nil) {
        self.timestamp = Date()
        self.kind = kind
        self.title = title
        self.detail = detail
    }
}
