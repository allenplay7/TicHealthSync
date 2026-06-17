# nRF Connect Testing On iPhone

Install nRF Connect on the iPhone first.

## Test flow

1. Install and open the TicHealthSync watch app.
2. Tap `Start BLE Advertising`.
3. Open nRF Connect on iPhone.
4. Start scanning.
5. Look for `TicHealthSync`.
6. Connect.
7. Confirm service UUID appears:

```text
7f5c7800-7a64-4f9d-9a6f-0f5e7a6f1000
```

8. Read `STATUS_READ`:

```text
7f5c7801-7a64-4f9d-9a6f-0f5e7a6f1000
```

Expected JSON:

```json
{"status":"ready","deviceId":"ticwatch-pro-5-main","recordsPending":0,"protocolVersion":1,"advertising":true,"gattServer":"running"}
```

9. Subscribe to `DATA_NOTIFY`:

```text
7f5c7803-7a64-4f9d-9a6f-0f5e7a6f1000
```

10. Write UTF-8 text to `CONTROL_WRITE`:

```text
HELLO
```

Expected notification:

```json
{"type":"HELLO_ACK","message":"TicHealthSync ready"}
```

11. Tap `Generate Fake Heart Rate Record` on the watch.
12. Write UTF-8 text to `CONTROL_WRITE`:

```text
SYNC_NOW
```

13. Expected result is either one `RECORD_BATCH` notification or multiple `CHUNK` notifications. If chunks are shown, join each `payload` value in `chunkIndex` order.
14. Copy the `batchId`.
15. Write an ACK to `ACK_WRITE`:

```text
ACK_BATCH:batch-id-from-notification
```

or:

```json
{"type":"ACK_BATCH","batchId":"batch-id-from-notification"}
```

16. Read `STATUS_READ` again. `recordsPending` should drop after the ACK.

## Useful command writes

Write these as UTF-8 text to `CONTROL_WRITE`:

```text
GET_STATUS
GENERATE_FAKE_DATA
SYNC_NOW
ACK_BATCH
CLEAR_SYNCED
```

