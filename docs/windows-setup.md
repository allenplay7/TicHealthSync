# Windows Android Studio Setup

1. Install Android Studio on Windows.
2. Install the Android SDK Platform for API 36.
3. Install Android SDK Platform-Tools.
4. Install Android SDK Build-Tools.
5. Open Android Studio.
6. Select `File > Open`.
7. Open:

```text
C:\Users\alexa\Desktop\Codex Projects\Health App\TicHealthSync
```

8. Let Gradle sync.
9. Select the `watch-app` run configuration.
10. Build the project.

The project uses:

- Android Gradle Plugin `8.13.2`
- Kotlin `2.2.21`
- Compose BOM `2026.05.01`
- Wear Compose `1.6.2`
- `compileSdk` `36`
- `minSdk` `30`

If Android Studio asks to use its embedded JDK, accept it. AGP builds should use JDK 17.

