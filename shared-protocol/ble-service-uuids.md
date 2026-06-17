# TicHealthSync BLE UUIDs

Device name:

```text
TicHealthSync
```

Primary service:

```text
TIC_HEALTH_SYNC_SERVICE
7f5c7800-7a64-4f9d-9a6f-0f5e7a6f1000
```

Characteristics:

| Name | UUID | Properties | Purpose |
| --- | --- | --- | --- |
| `STATUS_READ` | `7f5c7801-7a64-4f9d-9a6f-0f5e7a6f1000` | Read | Read watch status from nRF Connect or the future iOS app. |
| `CONTROL_WRITE` | `7f5c7802-7a64-4f9d-9a6f-0f5e7a6f1000` | Write, Write Without Response | Send commands such as `HELLO` and `SYNC_NOW`. |
| `DATA_NOTIFY` | `7f5c7803-7a64-4f9d-9a6f-0f5e7a6f1000` | Read, Notify | Receive JSON events, status responses, and record batches. |
| `ACK_WRITE` | `7f5c7804-7a64-4f9d-9a6f-0f5e7a6f1000` | Write | Acknowledge received batches. |
| `TIME_SYNC_WRITE` | `7f5c7805-7a64-4f9d-9a6f-0f5e7a6f1000` | Write | Send current iPhone time later if needed. |

Descriptor:

```text
Client Characteristic Configuration Descriptor (CCCD)
00002902-0000-1000-8000-00805f9b34fb
```

