import SwiftUI

struct RootView: View {
    var body: some View {
        TabView {
            DashboardView()
                .tabItem { Label("Dashboard", systemImage: "heart.text.square") }

            DevicesView()
                .tabItem { Label("Devices", systemImage: "dot.radiowaves.left.and.right") }

            DebugView()
                .tabItem { Label("Debug", systemImage: "ladybug") }
        }
    }
}

#Preview {
    RootView().environmentObject(BLEManager())
}
