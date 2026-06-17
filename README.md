# TicHealthSync

[![Codemagic build status](https://api.codemagic.io/apps/6a3219d07f177169062d5849/ios-compile/status_badge.svg)](https://codemagic.io/app/6a3219d07f177169062d5849)

Personal-use local health sync proof of concept.

Current milestone: Phase 3 — iOS BLE client skeleton. Phase 1 (Wear OS BLE PoC) and
Phase 2 (Room persistence) are complete and verified on hardware. iOS install via
Codemagic + TestFlight: see [ios-app/INSTALL.md](ios-app/INSTALL.md).

```text
TicWatch Pro 5 Wear OS app
BLE advertiser + GATT server
        |
Bluetooth Low Energy
        |
iPhone + nRF Connect
```

No cloud services, accounts, Firebase, Supabase, or external server are used.

## Repository layout

```text
TicHealthSync/
  watch-app/          Wear OS Kotlin app
  ios-app/            Placeholder for later SwiftUI app
  shared-protocol/    BLE UUIDs and sync message docs
  docs/               Setup and testing guides
```

## Phase 1 status

Implemented:

- Wear OS Kotlin app scaffold.
- Jetpack Compose for Wear OS UI.
- BLE GATT server with fixed TicHealthSync service UUID.
- BLE advertising with custom service UUID and scan-response device name.
- `STATUS_READ`, `CONTROL_WRITE`, `DATA_NOTIFY`, `ACK_WRITE`, and `TIME_SYNC_WRITE` characteristics.
- In-memory fake health records for nRF Connect testing.
- JSON notifications for `HELLO`, `GET_STATUS`, `GENERATE_FAKE_DATA`, `SYNC_NOW`, and ACK commands.
- Protocol docs and nRF Connect test guide.

Deferred to later phases:

- Room persistence.
- Wear OS Health Services.
- SwiftUI iOS app.
- HealthKit export.

## Build entry point

Open this directory in Android Studio:

```text
TicHealthSync/
```

Then build or run the `watch-app` module.

