# TicHealthSync Handoff

## Project Goal

Build a personal-use local sync system:

```text
TicWatch Pro 5 Wear OS app
  -> Bluetooth Low Energy
  -> iPhone 16 Pro
  -> local storage
  -> optional Apple Health export later
```

Constraints:

- No cloud.
- No Firebase.
- No Supabase.
- No external server.
- No accounts.
- Data transfer must be local over BLE.
- Development is mainly on Windows.
- Wear OS app must build/test on Windows with Android Studio.
- iOS app code can be created on Windows, but will later be built on a Mac in Xcode.
- First test client is nRF Connect on iPhone, not a custom iOS app.

Current milestone: Phase 2 (Room persistence) is implemented and verified. Phase 1
(BLE proof of concept) is complete.

**Status (2026-06-17): Phase 1 + Phase 2 working and verified on hardware.**
- Phase 1: notification-delivery bug fixed; `HELLO`/`GENERATE_FAKE_DATA`/`SYNC_NOW`/
  `ACK_BATCH` confirmed end-to-end with nRF Connect on a Pixel 7. See "Verified On
  Hardware".
- Phase 2: records now persist in a Room database. Generate inserts Room rows, the
  pending count is reactive from Room, records survive app restart, and
  `SYNC_NOW`/`ACK_BATCH` read and update Room. See "Phase 2: Room Persistence
  (Implemented + Verified)".

## Repository Location

```text
C:\Users\alexa\Desktop\Codex Projects\Health App\TicHealthSync
```

Important files:

```text
TicHealthSync/
  README.md
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  local.properties
  watch-app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/java/com/tichealthsync/watch/MainActivity.kt
    src/main/java/com/tichealthsync/watch/ble/TicHealthBleManager.kt
    src/main/java/com/tichealthsync/watch/ble/BleUiState.kt
    src/main/java/com/tichealthsync/watch/ble/HealthRecord.kt
  shared-protocol/
    ble-service-uuids.md
    sync-message-format.md
    health-record-schema.json
    example-messages.json
  docs/
    windows-setup.md
    ticwatch-install.md
    nrf-connect-testing.md
    mac-xcode-setup.md
    healthkit-export-plan.md
    troubleshooting.md
```

## Current Implementation

The Wear OS app exists and builds. It uses:

- Kotlin.
- Android Gradle Plugin.
- Jetpack Compose for Wear OS UI.
- Android BLE APIs:
  - `BluetoothGattServer`
  - `BluetoothLeAdvertiser`
  - `BluetoothManager`
- In-memory fake health records for Phase 1.

Room is not implemented yet. Real Wear OS Health Services are not implemented yet. iOS app is not implemented yet.

## Android Studio / SDK State

Android Studio is installed and the project has been opened successfully.

Local SDK path:

```text
C:\Users\alexa\AppData\Local\Android\Sdk
```

`local.properties` points to that SDK:

```properties
sdk.dir=C\:\\Users\\alexa\\AppData\\Local\\Android\\Sdk
```

ADB path:

```text
C:\Users\alexa\AppData\Local\Android\Sdk\platform-tools\adb.exe
```

The project has built successfully with:

```powershell
cd "C:\Users\alexa\Desktop\Codex Projects\Health App\TicHealthSync"
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:JAVA_HOME='C:\Program Files\Java\jdk-23'
$env:ANDROID_HOME=$sdkRoot
$env:ANDROID_SDK_ROOT=$sdkRoot
$env:Path="$env:JAVA_HOME\bin;$sdkRoot\platform-tools;$env:Path"
.\gradlew.bat :watch-app:assembleDebug
```

Debug APK output:

```text
C:\Users\alexa\Desktop\Codex Projects\Health App\TicHealthSync\watch-app\build\outputs\apk\debug\watch-app-debug.apk
```

## Watch / ADB State

The watch was connected over wireless ADB.

Known successful `adb devices -l` shape:

```text
adb-C101X44061912-RsQ9Et._adb-tls-connect._tcp device product:dace model:TicWatch_Pro_5 device:dace transport_id:1
```

If disconnected, reconnect via Android Studio Device Manager or wireless ADB. Ask the user to accept the watch-side debug prompt if it appears.

## Current BLE Design

The watch is the BLE peripheral:

```text
Watch app = BLE advertiser + GATT server
iPhone nRF Connect = BLE central + GATT client
```

The iPhone does not need a peripheral server or advertiser. For the future iOS app, use `CBCentralManager`, not `CBPeripheralManager`, for the first version.

Device name:

```text
TicHealthSync
```

Service UUID:

```text
7f5c7800-7a64-4f9d-9a6f-0f5e7a6f1000
```

Characteristics:

| Name | UUID | Properties |
| --- | --- | --- |
| `STATUS_READ` | `7f5c7801-7a64-4f9d-9a6f-0f5e7a6f1000` | Read |
| `CONTROL_WRITE` | `7f5c7802-7a64-4f9d-9a6f-0f5e7a6f1000` | Write, Write Without Response |
| `DATA_NOTIFY` | `7f5c7803-7a64-4f9d-9a6f-0f5e7a6f1000` | Read, Notify |
| `ACK_WRITE` | `7f5c7804-7a64-4f9d-9a6f-0f5e7a6f1000` | Write |
| `TIME_SYNC_WRITE` | `7f5c7805-7a64-4f9d-9a6f-0f5e7a6f1000` | Write |

Standard descriptors now used:

```text
CCCD: 00002902-0000-1000-8000-00805f9b34fb
CUD:  00002901-0000-1000-8000-00805f9b34fb
```

`CUD` means Characteristic User Description. It was added so nRF Connect can read/display names such as `STATUS_READ` and `CONTROL_WRITE`. nRF may still primarily show UUIDs depending on its UI, but the name descriptor is available under each characteristic.

## Watch UI

The Wear OS app shows:

```text
TicHealthSync

Bluetooth supported: yes/no
Advertising: yes/no
GATT server: running/stopped
Connected devices: number
Pending records: number
Last command: text
Last error: text

Buttons:
- Start BLE Advertising
- Stop BLE Advertising
- Generate Fake Heart Rate Record
- Generate Fake Step Count Record
- Clear Local Records
- Reset Sync State
```

Known successful state after starting BLE:

```text
Bluetooth supported: yes
Advertising: yes
GATT server: running
Connected devices: 0
Pending records: 0
```

## nRF Connect Test Procedure

On the watch:

1. Open TicHealthSync.
2. Grant Bluetooth/Nearby Devices permission if prompted.
3. Tap `Start BLE Advertising`.
4. Confirm:

```text
Advertising: yes
GATT server: running
```

On iPhone nRF Connect:

1. Scan.
2. Connect to `TicHealthSync`.
3. Find service:

```text
7f5c7800-7a64-4f9d-9a6f-0f5e7a6f1000
```

4. Read `STATUS_READ`.
5. Subscribe/enable notifications on `DATA_NOTIFY`.
6. Write `HELLO` as UTF-8/Text to `CONTROL_WRITE`.
7. Expected notification:

```json
{"type":"HELLO_ACK","message":"TicHealthSync ready"}
```

8. Generate fake data either by watch button or by writing `GENERATE_FAKE_DATA` to `CONTROL_WRITE`.
9. Write `SYNC_NOW` to `CONTROL_WRITE`.
10. Expected notification is a `RECORD_BATCH` or `CHUNK`.
11. If `RECORD_BATCH`, copy `batchId`.
12. Write ACK to `ACK_WRITE`:

```text
ACK_BATCH:batch-id-here
```

13. Read `STATUS_READ` again. `recordsPending` should decrease after ACK.

Use UTF-8/Text parser for all writes and JSON display. If nRF shows hex, JSON begins with:

```text
7B 22
```

which is:

```text
{"
```

## Recent Issue (RESOLVED 2026-06-16)

User reported:

- `HELLO` command works.
- The watch UI shows `HELLO` as the last command.
- nRF Connect does not receive output/notification.

Root cause (confirmed and fixed):

1. **Notifications were not serialized.** Android's GATT server allows only one
   notification in flight at a time; you must wait for `onNotificationSent` before
   calling `notifyCharacteristicChanged` again. The code fired notifications
   back-to-back, so all but the first were silently dropped (this broke
   `GENERATE_FAKE_DATA`'s 3-notification burst and `SYNC_NOW`'s multi-chunk batch).
2. **Notification fired before the write response.** In `onCharacteristicWriteRequest`
   the command handler (which notifies) ran before `sendResponse`, so the single
   `HELLO_ACK` notification could be missed by the central.
3. **Payloads were truncated at the default MTU.** Chunk size was hard-coded to 160
   bytes, but until an MTU exchange the usable ATT payload is only 20 bytes. Long
   writes (e.g. a full `ACK_BATCH:batch-...`) and notifications were truncated.

All three are fixed; see "Recent Patch Applied" and "Verified On Hardware".

## Recent Patch Applied

`TicHealthBleManager.kt` was patched to improve notification debugging and characteristic names.

Patch intent:

- Add Logcat tag:

```text
TicHealthSyncBle
```

- Log connection changes.
- Log characteristic reads.
- Log characteristic writes.
- Log CCCD descriptor writes.
- Accept any CCCD value with first byte non-zero as notification enabled, instead of only exact byte equality with `ENABLE_NOTIFICATION_VALUE`.
- Add `onNotificationSent` logging.
- Cache the last notification payload in `lastDataPayload`.
- Let `DATA_NOTIFY` reads return:
  - `recordsPending`
  - `subscribedDevices`
  - `lastPayload`
  - hint text
- Add Characteristic User Description descriptors:
  - `STATUS_READ`
  - `CONTROL_WRITE`
  - `DATA_NOTIFY`
  - `ACK_WRITE`
  - `TIME_SYNC_WRITE`

After the patch, `.\gradlew.bat :watch-app:assembleDebug` completed successfully.

## Follow-up Patch Applied (2026-06-16) — fixes the notification bug

`TicHealthBleManager.kt` was patched again to fix the actual notification-delivery
defects and harden the protocol:

- **Notification queue / serialization.** A `ConcurrentLinkedQueue<ByteArray>` plus an
  `awaitingAcks` counter (guarded by `notifyLock`) drains notifications one at a time.
  The next payload is only sent after `onNotificationSent` for the previous one. The
  queue is cleared on disconnect/shutdown and self-heals if the stack refuses a send.
- **Write response before notify.** `onCharacteristicWriteRequest` now calls
  `sendResponse` first, then runs the command handler, so the GATT write transaction
  completes before any notification is sent.
- **MTU-aware payload sizing.** Added an `onMtuChanged` override that records the
  negotiated MTU (`negotiatedMtu`, reset to the BLE default of 23 on disconnect). New
  `buildNotificationPayloads()` sizes each notification to `mtu - 3`: if it fits it is
  sent whole, otherwise it is chunked with a per-chunk budget derived from the live MTU
  (no longer the old hard-coded 160). Old constants `NOTIFY_MAX_BYTES` /
  `CHUNK_PAYLOAD_CHARS` were replaced by `DEFAULT_MTU`, `ATT_HEADER`,
  `CHUNK_ENVELOPE_OVERHEAD`, `MIN_CHUNK_CHARS`.

Both `:watch-app:assembleDebug` builds completed successfully and the patched APK was
installed and run on the watch.

## Verified On Hardware (2026-06-16)

Tested with the watch (BLE peripheral) and a Pixel 7 running nRF Connect (BLE central),
both driven over ADB. Watch logcat tag `TicHealthSyncBle`:

- Subscribe to `DATA_NOTIFY` -> `CCCD write value=01 00 enabled=true`.
- `HELLO` -> single `HELLO_ACK` notification, `Notification sent ... success`, received
  in nRF.
- `GENERATE_FAKE_DATA` -> 3 notifications (2x `FAKE_RECORD_CREATED` +
  `GENERATE_FAKE_DATA_ACK`) all delivered serially via the queue; pending = 2.
- After nRF requested MTU 517 -> `MTU changed to 517 (usable payload 514 bytes)`.
- `SYNC_NOW` -> a 380-byte `RECORD_BATCH` sent as a **single, un-chunked** notification
  and received complete/untruncated in nRF (pre-fix this would have been ~5 chunks).
- Full-length `ACK_BATCH:batch-...` (29 bytes) arrived **intact** (truncated to
  `batch-17` before the MTU fix) and dropped **Pending records 2 -> 1**.

Note on MTU: iOS auto-negotiates a large MTU on connect, so the iPhone path needs no
manual step. On Android nRF Connect, MTU must be requested manually via the
per-connection menu (the per-connection "more"/action_more button -> Request MTU)
to exercise the large-payload path.

## Next Immediate Steps

1. Confirm ADB still sees the watch:

```powershell
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
& (Join-Path $sdkRoot 'platform-tools\adb.exe') devices -l
```

2. Reinstall/run the patched app through Android Studio UI or CLI.

CLI option:

```powershell
cd "C:\Users\alexa\Desktop\Codex Projects\Health App\TicHealthSync"
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:JAVA_HOME='C:\Program Files\Java\jdk-23'
$env:ANDROID_HOME=$sdkRoot
$env:ANDROID_SDK_ROOT=$sdkRoot
$env:Path="$env:JAVA_HOME\bin;$sdkRoot\platform-tools;$env:Path"
.\gradlew.bat :watch-app:installDebug
```

Android Studio UI option:

1. Confirm device selector shows `Mobvoi TicWatch Pro 5`.
2. Confirm run config is `watch-app`.
3. Click green Run triangle.
4. Accept permission prompts on watch if needed.
5. Tap `Start BLE Advertising`.

3. Open Logcat in Android Studio or use CLI:

```powershell
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
& (Join-Path $sdkRoot 'platform-tools\adb.exe') logcat -s TicHealthSyncBle
```

4. In nRF Connect:
   - Disconnect from the watch.
   - Reconnect.
   - Subscribe to `DATA_NOTIFY`.
   - Confirm Logcat shows a CCCD write with `enabled=true`.
   - Write `HELLO`.
   - Confirm Logcat shows:

```text
Write ... HELLO
Notify ... {"type":"HELLO_ACK",...}
Notification sent ... success
```

5. If nRF still shows no notification:
   - Read `DATA_NOTIFY` manually.
   - Check if `lastPayload` contains `HELLO_ACK`.
   - If yes, command processing is fine and notification subscription/delivery is the remaining issue.
   - If no, command handling did not call notification path.

## Characteristic Names Answer

The user asked whether nRF can show names instead of UUIDs.

Answer:

- For custom 128-bit UUIDs, nRF Connect usually shows UUIDs because they are not in the Bluetooth SIG adopted UUID database.
- The app can add standard `Characteristic User Description` descriptors (`0x2901`) so each characteristic has a readable name.
- This has been added in the patch.
- Depending on nRF Connect UI, names may appear under the descriptor/details rather than replacing the UUID in the main list.
- Future custom iOS app can always map UUIDs to names directly in code.

## Current Protocol Examples

`STATUS_READ` expected response:

```json
{
  "status": "ready",
  "deviceId": "ticwatch-pro-5-main",
  "recordsPending": 0,
  "protocolVersion": 1,
  "advertising": true,
  "gattServer": "running"
}
```

`HELLO` expected notification:

```json
{
  "type": "HELLO_ACK",
  "message": "TicHealthSync ready"
}
```

`GENERATE_FAKE_DATA` expected behavior:

- Creates one fake heart rate record.
- Creates one fake step count record.
- Sends a `GENERATE_FAKE_DATA_ACK` notification.

`SYNC_NOW` expected notification:

```json
{
  "type": "RECORD_BATCH",
  "batchId": "batch-...",
  "records": [
    {
      "recordId": "...",
      "deviceId": "ticwatch-pro-5-main",
      "type": "heart_rate",
      "value": 82,
      "unit": "bpm",
      "startTime": "...",
      "endTime": "...",
      "createdAt": "...",
      "sequence": 1,
      "syncStatus": "sent_unconfirmed"
    }
  ]
}
```

If payload is too large, notification becomes chunks:

```json
{
  "type": "CHUNK",
  "batchId": "chunk-...",
  "chunkIndex": 0,
  "chunkCount": 3,
  "payload": "partial-json"
}
```

## Known Design Caveats

- Records now persist in Room (Phase 2). They survive disconnect and app restart.
- ACK only marks `sent_unconfirmed` records as `synced`.
- The watch does not delete records after sending.
- `GENERATE_FAKE_DATA` currently triggers multiple notifications quickly. If nRF misses one, test with:
  - subscribe first
  - write `HELLO`
  - write `SYNC_NOW`
  - read `DATA_NOTIFY` if needed
- `lastDataPayload` readback is a debug fallback, not the final protocol.

## Recommended Next Implementation Work

Phase 1 stabilization is COMPLETE and verified on hardware (2026-06-16):

1. [x] Reinstall patched app.
2. [x] Confirm CCCD subscription logs (`CCCD write ... enabled=true`).
3. [x] Confirm `HELLO_ACK` notification in nRF.
4. [x] Confirm `SYNC_NOW` sends `RECORD_BATCH` (single packet at MTU 517; chunks if MTU small).
5. [x] Confirm ACK changes pending count (`2 -> 1`).
6. [x] Notification serialization, write-before-notify, and MTU-aware sizing fixed.

Optional Phase 1 polish still open:
- The `DATA_NOTIFY` read fallback (`lastPayload`) is unchanged; document it if kept.
- Consider greedy chunk packing (measure each serialized CHUNK) instead of the current
  conservative worst-case estimate, if you ever target very small MTUs.

## Phase 2: Room Persistence (Implemented + Verified 2026-06-17)

Room now backs all record storage. The in-memory `records` list is gone.

Build setup (`watch-app/build.gradle.kts`, root `build.gradle.kts`):

- KSP plugin `com.google.devtools.ksp` version `2.2.21-2.0.4` (matches Kotlin 2.2.21).
- Room `2.7.1`: `room-runtime`, `room-ktx`, and `room-compiler` via `ksp(...)`.
- `kotlinx-coroutines-android` `1.10.2`.
- Schema export enabled: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`,
  and `@Database(exportSchema = true)`.

New package `com.tichealthsync.watch.data`:

- `HealthRecordEntity` (table `health_records`): recordId PK, deviceId, type, value,
  unit, startTime, endTime, createdAt, sequence, syncStatus, batchId?.
- `SyncSessionEntity` (table `sync_sessions`): syncSessionId PK, startedAt,
  completedAt?, recordsSent, result, errorMessage?.
- `HealthRecordDao`: insert; reactive `observeUnsyncedCount()` Flow; `countUnsynced`;
  `nextPendingRecord` (oldest pending/failed by sequence); `markSentUnconfirmed`;
  `markBatchSynced`; `markAllSentSynced` (debug ACK fallback); `resetNonSynced`;
  `clearAll`; `maxSequence`.
- `SyncSessionDao`: upsert, complete, count.
- `TicHealthDatabase` (singleton) and `HealthRepository` (assigns sequence atomically,
  wraps DAOs).

`TicHealthBleManager` changes:

- Holds a `HealthRepository` and a `CoroutineScope(SupervisorJob + Dispatchers.IO)`.
- Collects `observeUnsyncedCount()` into a `pendingCountCache`; STATUS_READ and the UI
  read this (binder threads can't await a suspend query). The cache is Room-sourced.
- `generateFakeRecord` / `GENERATE_FAKE_DATA` insert Room rows (sequence from
  `maxSequence()+1`). `SYNC_NOW` fetches the next pending/failed row, marks it
  `sent_unconfirmed` with a new batchId, records a SyncSession, then sends
  `RECORD_BATCH`. `ACK_BATCH:<id>` marks only that batch synced; bare `ACK_BATCH`
  marks all `sent_unconfirmed` (debug fallback). `clear`/`reset` go through Room.
- Sync states unchanged: pending, sent_unconfirmed, synced, failed.
- Clearer `Last command` (`generated heart_rate`, `sync batch ...`, `ack batch ...`)
  and `Last error` (`no pending records`, `no notification subscriber`,
  `Room write/read failure`, `notify failure (...)`). Room ops log under tag
  `TicHealthSyncBle` (`Room: inserted ...`, `Room: sync ...`, `Room: ack ...`,
  `Room: reset ...`).

Verified on hardware (watch + Pixel 7 nRF Connect, both over ADB):

- Generate heart_rate -> `Room: inserted heart_rate seq=1`, pending 1; generate
  step_count -> `seq=2`, pending 2 (`Last command: generated <type>`).
- **Force-stop + relaunch -> Pending records still 2** (records reloaded from Room).
- `SYNC_NOW` -> `Room: sync record ... seq=1 -> batch ...`, sent the *persisted*
  pre-restart record as a single 380-byte `RECORD_BATCH`.
- `ACK_BATCH:batch-...` -> `Room: ack batch=... marked 1 synced`, pending dropped 2 -> 1
  (the synced heart_rate left; the step_count seq=2 remained pending).

## Phase 3: iOS Skeleton (Implemented 2026-06-17, not yet built on Mac)

Created `ios-app/` — a SwiftUI app skeleton for the BLE **central** using
`CBCentralManager` (NOT `CBPeripheralManager`). Scope is raw BLE receive/debug only;
**no local storage and no HealthKit yet**, by design.

Project format: modern Xcode 16 project (`objectVersion = 77`) using a
**file-system–synchronized group**, so any `.swift` added under `TicHealthSync/` is
compiled automatically — no per-file pbxproj edits. Info.plist is generated
(`GENERATE_INFOPLIST_FILE = YES`); the Bluetooth prompt comes from
`INFOPLIST_KEY_NSBluetoothAlwaysUsageDescription`. Bundle id `com.tichealthsync.ios`,
deployment target iOS 17.

Files:

```
ios-app/
  TicHealthSync.xcodeproj/project.pbxproj
  TicHealthSync/
    TicHealthSyncApp.swift        @main; owns a shared BLEManager (@StateObject)
    Assets.xcassets/              AppIcon + AccentColor placeholders
    BLE/HealthProtocol.swift      UUIDs, ChunkReassembler, JSON pretty/field helpers
    BLE/Models.swift              DiscoveredDevice, LogEntry
    BLE/BLEManager.swift          CBCentralManager + CBPeripheralDelegate
    Views/RootView.swift          TabView: Dashboard / Devices / Debug
    Views/DashboardView.swift     status, recordsPending, quick actions
    Views/DevicesView.swift       scan + connect
    Views/DebugView.swift         raw JSON log + command buttons
  README.md                       Mac/Xcode build + test steps
```

Implemented raw BLE flow: scan for the service UUID, connect, discover
characteristics, subscribe to `DATA_NOTIFY`, write `HELLO`, read `STATUS_READ`, write
`SYNC_NOW` (and `GENERATE_FAKE_DATA`), `ACK_BATCH:<id>` to `ACK_WRITE`, with CHUNK
reassembly and raw JSON shown in the Debug log.

**Not yet built/run** — Swift cannot be compiled on the Windows dev machine. Next
session on a Mac: open `ios-app/TicHealthSync.xcodeproj` in Xcode 16+, set a signing
Team, and **run on a real iPhone** (Core Bluetooth does not work in the Simulator).
Verify the raw round-trip against the advertising watch before adding any storage or
HealthKit.

## If Asked To Use Desktop/Mouse

The user explicitly likes seeing Android Studio actions done with desktop automation.

Prior UI steps that worked:

1. Android Studio opened to `TicHealthSync`.
2. Device selector showed `Mobvoi TicWatch Pro 5`.
3. Run config showed `watch-app`.
4. Clicked green Run triangle.
5. Build Output showed successful install.
6. Watch mirrored view showed permission prompt.
7. Clicked `Allow`.
8. Clicked `Start BLE Advertising`.
9. Scrolled watch preview to verify status.

If repeating, narrate the UI steps so the user learns how to do it manually.

## Logs To Ask User For

If notification still fails, ask for:

- nRF Connect exported debug log.
- Screenshot of `DATA_NOTIFY` showing whether notifications are enabled.
- Exact write format used: Text/UTF-8 or Hex.
- Android Logcat filtered to:

```text
TicHealthSyncBle
```

Most important Logcat lines:

```text
Device connected
CCCD write value=... enabled=true
Write ... 'HELLO'
Notify ... {"type":"HELLO_ACK",...}
Notification sent ... success
```

If there is no `CCCD write`, nRF did not subscribe to `DATA_NOTIFY`.

If there is `CCCD write enabled=true` and `Notification sent success`, the watch side is likely working and the issue is nRF display/parser/log view.

If there is `No DATA_NOTIFY subscribers`, subscription is missing or not recognized.

