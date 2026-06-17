import CoreBluetooth
import Foundation

/// TicHealthSync BLE protocol constants and helpers. These mirror the Wear OS
/// peripheral (see shared-protocol/ and TicHealthBleManager.kt).
enum HealthProtocol {
    static let deviceName = "TicHealthSync"

    static let service = CBUUID(string: "7f5c7800-7a64-4f9d-9a6f-0f5e7a6f1000")
    static let statusRead = CBUUID(string: "7f5c7801-7a64-4f9d-9a6f-0f5e7a6f1000")
    static let controlWrite = CBUUID(string: "7f5c7802-7a64-4f9d-9a6f-0f5e7a6f1000")
    static let dataNotify = CBUUID(string: "7f5c7803-7a64-4f9d-9a6f-0f5e7a6f1000")
    static let ackWrite = CBUUID(string: "7f5c7804-7a64-4f9d-9a6f-0f5e7a6f1000")
    static let timeSyncWrite = CBUUID(string: "7f5c7805-7a64-4f9d-9a6f-0f5e7a6f1000")

    /// All characteristics we want to discover on the service.
    static let allCharacteristics: [CBUUID] = [
        statusRead, controlWrite, dataNotify, ackWrite, timeSyncWrite
    ]

    /// Control commands (written as UTF-8 text to CONTROL_WRITE).
    static let cmdHello = "HELLO"
    static let cmdSyncNow = "SYNC_NOW"
    static let cmdGenerateFakeData = "GENERATE_FAKE_DATA"

    static func shortName(for uuid: CBUUID) -> String {
        switch uuid {
        case statusRead: return "STATUS_READ"
        case controlWrite: return "CONTROL_WRITE"
        case dataNotify: return "DATA_NOTIFY"
        case ackWrite: return "ACK_WRITE"
        case timeSyncWrite: return "TIME_SYNC_WRITE"
        default: return uuid.uuidString
        }
    }
}

/// Reassembles CHUNK notifications back into a single JSON string.
///
/// The watch splits large notifications into CHUNK envelopes when the negotiated
/// MTU is too small for a payload. iOS usually negotiates a large MTU so chunking
/// rarely happens, but we handle it for correctness.
final class ChunkReassembler {
    private var parts: [String: [Int: String]] = [:]
    private var counts: [String: Int] = [:]

    /// Feed one received JSON string. Returns a complete payload when one is ready,
    /// or `nil` if more chunks are still expected. Non-chunk messages pass straight
    /// through.
    func accept(_ json: String) -> String? {
        guard
            let data = json.data(using: .utf8),
            let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            (obj["type"] as? String) == "CHUNK",
            let batchId = obj["batchId"] as? String,
            let index = obj["chunkIndex"] as? Int,
            let count = obj["chunkCount"] as? Int,
            let payload = obj["payload"] as? String
        else {
            // Not a chunk (or not JSON we recognise) — deliver as-is.
            return json
        }

        var buffer = parts[batchId] ?? [:]
        buffer[index] = payload
        parts[batchId] = buffer
        counts[batchId] = count

        guard buffer.count == count else { return nil }

        let assembled = (0..<count).compactMap { buffer[$0] }.joined()
        parts[batchId] = nil
        counts[batchId] = nil
        return assembled
    }
}

enum JSONFormatter {
    /// Pretty-print a JSON string for display. Returns the original on failure.
    static func pretty(_ raw: String) -> String {
        guard
            let data = raw.data(using: .utf8),
            let obj = try? JSONSerialization.jsonObject(with: data),
            let formatted = try? JSONSerialization.data(
                withJSONObject: obj,
                options: [.prettyPrinted, .sortedKeys]
            ),
            let string = String(data: formatted, encoding: .utf8)
        else {
            return raw
        }
        return string
    }

    /// Extract a string field from a JSON object string, if present.
    static func field(_ key: String, in raw: String) -> String? {
        guard
            let data = raw.data(using: .utf8),
            let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return nil }
        if let s = obj[key] as? String { return s }
        if let n = obj[key] as? NSNumber { return n.stringValue }
        return nil
    }
}
