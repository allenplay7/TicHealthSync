# TicHealthSync — iOS app (Phase 3 skeleton)

SwiftUI iOS client for the TicHealthSync Wear OS peripheral. This first milestone is
**raw BLE receive/debug only**: scan, connect, discover, subscribe to `DATA_NOTIFY`,
write `HELLO` / `SYNC_NOW`, read `STATUS_READ`, and show the raw JSON. There is
intentionally **no local storage and no HealthKit yet** — those come after raw BLE
receive is proven on-device.

The app is the BLE **central** and uses `CBCentralManager` (not `CBPeripheralManager`).
The watch is the peripheral (advertiser + GATT server).

## Building (on a Mac)

This project is created on Windows but must be built on a Mac in Xcode.

1. Copy the `ios-app/` folder to the Mac.
2. Open `TicHealthSync.xcodeproj` in Xcode 16 or newer.
3. Select your Team under **Signing & Capabilities** (automatic signing). Change
   `PRODUCT_BUNDLE_IDENTIFIER` (`com.tichealthsync.ios`) if that id is taken.
4. **Run on a real iPhone.** Core Bluetooth does not work in the iOS Simulator.
5. On first launch iOS asks for Bluetooth permission. The prompt text comes from
   `INFOPLIST_KEY_NSBluetoothAlwaysUsageDescription` (set in build settings; the
   Info.plist is generated automatically).

The project uses Xcode's file-system–synchronized group, so any `.swift` file added
under `TicHealthSync/` is compiled automatically — no manual project edits needed.

## Test procedure

1. On the watch: open TicHealthSync, tap **Start BLE Advertising**.
2. In the iOS app, **Devices** tab → **Scan for TicHealthSync** → tap the device to
   connect. On connect the app discovers characteristics, subscribes to `DATA_NOTIFY`,
   and reads `STATUS_READ`.
3. **Dashboard** shows connection status and `recordsPending`.
4. **Debug** tab → tap **HELLO** (expect a `HELLO_ACK` notification), **GENERATE**,
   then **SYNC_NOW** (expect a `RECORD_BATCH`). All raw JSON appears in the log.

## Layout

```
ios-app/
  TicHealthSync.xcodeproj/        Xcode project (synchronized-folder format)
  TicHealthSync/
    TicHealthSyncApp.swift         @main entry; owns the shared BLEManager
    Assets.xcassets/               App icon + accent color placeholders
    BLE/
      HealthProtocol.swift         Service/characteristic UUIDs, CHUNK reassembly, JSON helpers
      Models.swift                 DiscoveredDevice, LogEntry
      BLEManager.swift             CBCentralManager + CBPeripheralDelegate
    Views/
      RootView.swift               TabView: Dashboard / Devices / Debug
      DashboardView.swift          Connection status, pending count, quick actions
      DevicesView.swift            Scan + connect
      DebugView.swift              Raw JSON log + command buttons
```

## Protocol (must match the watch)

- Device name: `TicHealthSync`
- Service: `7f5c7800-7a64-4f9d-9a6f-0f5e7a6f1000`
- `STATUS_READ` `…7801` (Read), `CONTROL_WRITE` `…7802` (Write),
  `DATA_NOTIFY` `…7803` (Notify/Read), `ACK_WRITE` `…7804` (Write),
  `TIME_SYNC_WRITE` `…7805` (Write)
- Commands are UTF-8 text written to `CONTROL_WRITE`: `HELLO`, `SYNC_NOW`,
  `GENERATE_FAKE_DATA`. ACKs are `ACK_BATCH:<batchId>` written to `ACK_WRITE`.
- Large notifications may arrive as `CHUNK` envelopes; `ChunkReassembler` rebuilds them.
  (iOS negotiates a large MTU automatically, so chunking is rarely needed.)

See `../shared-protocol/` for the authoritative protocol definitions.
