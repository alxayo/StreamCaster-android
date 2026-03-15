# StreamCaster (Android)

StreamCaster is a native Android app for live streaming camera and/or microphone to RTMP/RTMPS endpoints.

It is designed for:
- creators and developers who need a lightweight self-hostable live stream app,
- local/LAN testing against NGINX-RTMP or similar ingest servers,
- privacy-focused distribution (including a `foss` flavor with no Google Play services dependency).

## What This App Is For

- Live stream video + audio, video-only, or audio-only.
- Save and reuse endpoint profiles (URL, stream key, optional auth credentials).
- Continue streaming in background via a foreground service.
- Show live stream HUD (state, bitrate/fps/resolution/duration).
- Optional local recording support (platform/version dependent).

## Tech Snapshot

- Package: `com.port80.app`
- Main app id (foss): `com.port80.app.foss`
- minSdk: 23
- targetSdk / compileSdk: 35
- Language: Kotlin
- UI: Jetpack Compose
- Architecture: MVVM + Hilt + StateFlow
- Streaming engine: RootEncoder (`RtmpCamera2`)
- Credentials storage: EncryptedSharedPreferences (Keystore-backed)

## Project Flavors

- `foss`: F-Droid friendly, no GMS dependencies.
- `gms`: Play Store flavor.

## Prerequisites (Local Development)

- Android Studio (latest stable).
- JDK 17 (Android Studio bundled JDK is fine).
- Android SDK components:
  - Platform Tools
  - Build Tools for API 35
  - Android Platform API 35
  - Emulator (optional)
- A connected Android device (USB debugging enabled) or an Android Emulator.

## Open and Run in Android Studio

1. Open this folder in Android Studio.
2. Let Gradle sync finish.
3. Select build variant `fossDebug` (recommended for local testing).
4. Connect a device or start an emulator.
5. Press Run.

## Run from CLI

Build debug APK:

```bash
./gradlew :app:assembleFossDebug
```

Install on connected device/emulator:

```bash
./gradlew :app:installFossDebug
```

Launch app:

```bash
adb shell am start -n com.port80.app.foss/com.port80.app.MainActivity
```

## Useful Development Commands

Build both flavors:

```bash
./gradlew assembleFossDebug assembleGmsDebug
```

Unit tests:

```bash
./gradlew testFossDebugUnitTest
```

FOSS flavor check for accidental GMS deps (must be empty):

```bash
./gradlew :app:dependencies --configuration fossReleaseRuntimeClasspath | grep -i gms
```

Instrumented tests:

```bash
./gradlew connectedFossDebugAndroidTest
```

## First-Run Streaming Setup

1. Open the app.
2. Go to Endpoints and confirm at least one profile exists.
3. Ensure one profile is marked default.
4. Grant runtime permissions when prompted:
   - Camera
   - Microphone
   - Notifications (API 33+)
5. Tap Start.

## Default Local Endpoint (if seeded)

A fresh install may include this default profile:
- Name: `Local RTMP`
- URL: `rtmp://192.168.0.12:1935/live`
- Stream key: `test`

If your server runs on your development machine and you stream from Android Emulator, prefer `10.0.2.2` instead of `localhost`.

Examples:
- Emulator -> host machine: `rtmp://10.0.2.2:1935/live`
- Physical device -> host machine on LAN: `rtmp://<your-host-lan-ip>:1935/live`

## Troubleshooting

### 1) "No connected devices" when installing

Symptom:
- `:app:installFossDebug` fails with `No connected devices`.

Fix:
- Start an emulator or connect a physical device.
- Verify with:

```bash
adb devices -l
```

You should see at least one `device` entry.

### 2) Start button does not stream

Check these in order:
1. Permissions granted (camera/mic/notifications).
2. Default endpoint profile exists.
3. Endpoint reachable from the device/emulator network.
4. RTMP server actually listening on expected interface/port.

### 3) "Auth Failed" vs profile errors

Current behavior is intentionally split:
- `Auth Failed`: server-side authentication rejection (wrong stream key or credentials).
- `Profile Missing`: app could not resolve the selected/default endpoint profile.
- `No streaming endpoint configured`: no profile available to start.

If you see profile-related messages:
- Open Endpoints.
- Create/update a profile.
- Mark it as default.

### 4) "Profile Missing" after changing code or app data

Cause:
- Profile IDs in storage do not match requested profile IDs.

Fix:
- Recreate endpoint profile and set as default.
- If needed for local dev, clear app data and relaunch.

### 5) Stream cannot reach local server from emulator

Common cause:
- Using LAN IP that emulator cannot route to in your environment.

Try:
- `10.0.2.2` for host machine access from emulator.
- Confirm server binds to `0.0.0.0` or host LAN interface, not only `127.0.0.1`.

### 6) Foreground service / notification issues

Symptoms:
- Stream start immediately stops on Android 13+ / 14+.

Fix:
- Confirm notification permission granted on API 33+.
- Start streaming from in-app foreground action (button), not background trigger.

### 7) Camera preview black / camera unavailable

Possible causes:
- Another app holds camera.
- Emulator camera config issue.
- Permission denied.

Fix:
- Close other camera apps.
- Re-grant camera permission.
- Restart emulator/device.

### 8) Capture logs for debugging

Clear logs:

```bash
adb logcat -c
```

Watch app/crash related output:

```bash
adb logcat -v time | grep --line-buffered -E "FATAL EXCEPTION|AndroidRuntime|com.port80.app"
```

If `adb` behaves unexpectedly in your shell, run absolute path:

```bash
/opt/homebrew/bin/adb devices -l
```

## Security Notes

- Credentials are encrypted at rest (Keystore-backed EncryptedSharedPreferences).
- Service start intent carries only profile ID, not raw credentials.
- RTMPS is preferred for production.
- Sensitive values in logs are sanitized/redacted.

## Known Local Dev Caveats

- Emulator networking can differ from physical devices.
- Thermal/camera behavior in emulator does not fully represent physical hardware behavior.
- Some features are sensitive to Android API level and OEM restrictions.

## Repository Layout (high level)

- `app/src/main/java/com/port80/app/ui` - Compose UI and ViewModels
- `app/src/main/java/com/port80/app/service` - Foreground service and streaming control
- `app/src/main/java/com/port80/app/data` - Settings/profile repositories and models
- `app/src/main/java/com/port80/app/di` - Hilt modules

## License

See project license files and dependency licenses for details.
