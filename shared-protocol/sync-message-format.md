# Sync Message Format

Phase 1 uses compact JSON strings over BLE characteristics.

## Commands

Write UTF-8 text to `CONTROL_WRITE`:

```text
HELLO
GET_STATUS
GENERATE_FAKE_DATA
SYNC_NOW
ACK_BATCH
CLEAR_SYNCED
```

Write UTF-8 text or JSON to `ACK_WRITE`:

```text
ACK_BATCH
ACK_BATCH:batch-123
batchId=batch-123
{"type":"ACK_BATCH","batchId":"batch-123"}
```

## Status Read

Read `STATUS_READ`:

```json
{
  "status": "ready",
  "deviceId": "ticwatch-pro-5-main",
  "recordsPending": 3,
  "protocolVersion": 1,
  "advertising": true,
  "gattServer": "running"
}
```

## Notification: HELLO_ACK

Subscribe to `DATA_NOTIFY`, then write `HELLO` to `CONTROL_WRITE`:

```json
{
  "type": "HELLO_ACK",
  "message": "TicHealthSync ready"
}
```

## Notification: RECORD_BATCH

Write `SYNC_NOW` to `CONTROL_WRITE`:

```json
{
  "type": "RECORD_BATCH",
  "batchId": "batch-001",
  "records": [
    {
      "recordId": "uuid-here",
      "deviceId": "ticwatch-pro-5-main",
      "type": "heart_rate",
      "value": 82,
      "unit": "bpm",
      "startTime": "2026-06-16T15:30:00+10:00",
      "endTime": "2026-06-16T15:30:05+10:00",
      "createdAt": "2026-06-16T15:30:06+10:00",
      "sequence": 1,
      "syncStatus": "sent_unconfirmed"
    }
  ]
}
```

Phase 1 sends at most one record per `RECORD_BATCH`.

## Chunking

Notifications larger than the current Phase 1 threshold are split into chunk messages:

```json
{
  "type": "CHUNK",
  "batchId": "chunk-001",
  "chunkIndex": 0,
  "chunkCount": 3,
  "payload": "partial-json"
}
```

For nRF Connect testing, copy the `payload` values in `chunkIndex` order and join them to reconstruct the original JSON.

