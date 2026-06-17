# Troubleshooting

## iPhone cannot see TicHealthSync

1. Confirm the watch app is open.
2. Confirm Bluetooth is enabled on the watch.
3. Confirm Bluetooth permissions were granted to the watch app.
4. Tap `Stop BLE Advertising`, then `Start BLE Advertising`.
5. In nRF Connect, stop and restart scanning.
6. Move the iPhone close to the watch.
7. Turn watch Bluetooth off and on.
8. Reboot the watch if advertising still fails.
9. Check the watch UI `Last error` field.

## Advertising says no

Possible causes:

- Bluetooth is off.
- Bluetooth permissions were denied.
- Another app is using too many advertiser instances.
- The watch firmware does not expose BLE advertising correctly.

## Service appears but reads or writes fail

1. Disconnect in nRF Connect.
2. Stop advertising on the watch.
3. Start advertising on the watch.
4. Reconnect in nRF Connect.
5. Subscribe to `DATA_NOTIFY` before writing `HELLO` or `SYNC_NOW`.

## Notifications are chunked

Large JSON payloads are sent as:

```json
{"type":"CHUNK","batchId":"chunk-001","chunkIndex":0,"chunkCount":3,"payload":"..."}
```

Copy the `payload` strings in `chunkIndex` order and join them.

## Device name is missing

The service UUID is the authoritative filter:

```text
7f5c7800-7a64-4f9d-9a6f-0f5e7a6f1000
```

Android advertises the 128-bit service UUID in the primary advertising packet and the device name in the scan response. Some scanners show the UUID first and fill in the name only after scan response data is received.

