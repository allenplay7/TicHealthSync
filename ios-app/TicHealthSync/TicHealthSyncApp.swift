import SwiftUI

@main
struct TicHealthSyncApp: App {
    // A single BLE manager shared across all screens.
    @StateObject private var ble = BLEManager()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(ble)
        }
    }
}
