# Install On TicWatch Pro 5

1. On the TicWatch, enable Developer Options:
   - Open Settings.
   - Go to System > About.
   - Tap Build number seven times.
2. Enable ADB debugging:
   - Settings > Developer options.
   - Enable ADB debugging.
   - Enable Debug over Wi-Fi if USB debugging is not convenient.
3. In Android Studio on Windows, open `TicHealthSync`.
4. Pair or connect the watch:
   - Use Android Studio Device Manager for Wear OS pairing, or
   - Use `adb connect WATCH_IP:PORT` after enabling wireless debugging.
5. Confirm the ADB prompt on the watch.
6. Select the connected TicWatch as the target device.
7. Run the `watch-app` configuration.
8. Open the app on the watch.
9. Grant Bluetooth permissions if prompted.
10. Tap `Start BLE Advertising`.

Expected watch UI after starting:

```text
Bluetooth supported: yes
Advertising: yes
GATT server: running
Connected devices: 0
Pending records: 0
Last error: -
```

