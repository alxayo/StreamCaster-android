# StreamCaster — Sideload Quick Reference

A concise guide for installing StreamCaster APKs directly onto an Android device. For full documentation, see [README.md](README.md).

---

## Prerequisites

- **ADB** installed on your computer ([download Platform Tools](https://developer.android.com/tools/releases/platform-tools)).
- **USB cable** (or Wi-Fi for wireless ADB on Android 11+).
- **Developer Options** and **USB Debugging** enabled on the Android device:
  1. Settings → About Phone → tap **Build Number** 7 times.
  2. Settings → Developer Options → enable **USB Debugging**.

---

## 1. Build the APK

```bash
# FOSS debug (recommended — works on all devices, no Google dependencies)
./gradlew :app:assembleFossDebug

# GMS debug (with Google Play Services support)
./gradlew :app:assembleGmsDebug

# FOSS release (minified, requires signing config)
./gradlew :app:assembleFossRelease

# GMS release (minified, requires signing config)
./gradlew :app:assembleGmsRelease
```

### APK Locations

| Variant | Path |
|---------|------|
| FOSS debug | `app/build/outputs/apk/foss/debug/app-foss-debug.apk` |
| FOSS release | `app/build/outputs/apk/foss/release/app-foss-release.apk` |
| GMS debug | `app/build/outputs/apk/gms/debug/app-gms-debug.apk` |
| GMS release | `app/build/outputs/apk/gms/release/app-gms-release.apk` |

A pre-built FOSS debug APK is also available at: `artifacts/streamcaster-foss-debug.apk`

---

## 2. Connect and Verify Device

### USB

```bash
# Plug in the device, then:
adb devices -l
```

Accept the "Allow USB Debugging" prompt on the phone if it appears. You should see your device listed as `device` (not `unauthorized`).

### Wireless ADB (Android 11+)

```bash
# On the phone: Developer Options → Wireless Debugging → Pair device with pairing code
adb pair <ip>:<pairing-port>
# Enter the pairing code

# Then connect (use the IP:port shown on the Wireless Debugging screen, NOT the pairing port):
adb connect <ip>:<port>
```

Both devices must be on the same Wi-Fi network.

---

## 3. Install

### Fresh Install

```bash
# FOSS flavor
adb install app/build/outputs/apk/foss/debug/app-foss-debug.apk

# GMS flavor
adb install app/build/outputs/apk/gms/debug/app-gms-debug.apk

# Or from the pre-built artifact
adb install artifacts/streamcaster-foss-debug.apk
```

### Update (Replace Existing)

```bash
adb install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

### If Install Is Blocked (Signature Mismatch)

```bash
# Uninstall the old version first
adb uninstall com.port80.app.foss    # FOSS flavor
adb uninstall com.port80.app         # GMS flavor

# Then install fresh
adb install app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

---

## 4. Manual Sideload (No ADB)

1. Copy the APK to your phone via USB file transfer, cloud storage, email, or direct download.
2. Open the APK in the phone's file manager.
3. If prompted, allow **"Install unknown apps"** for the app you used to open the file.
4. Tap **Install** → **Open**.

---

## 5. Launch

```bash
# FOSS flavor
adb shell am start -n com.port80.app.foss/com.port80.app.MainActivity

# GMS flavor
adb shell am start -n com.port80.app/com.port80.app.MainActivity
```

Or open **StreamCaster** from the app drawer.

---

## 6. Verify Installation

```bash
# Check package is installed
adb shell pm list packages | grep com.port80.app

# Check version
adb shell dumpsys package com.port80.app.foss | grep versionName
```

---

## 7. Rebuild and Update Workflow

After making code changes:

```bash
./gradlew :app:assembleFossDebug && \
adb install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

To refresh the pre-built artifact copy:

```bash
cp app/build/outputs/apk/foss/debug/app-foss-debug.apk artifacts/streamcaster-foss-debug.apk
```

---

## 8. Uninstall

```bash
adb uninstall com.port80.app.foss    # FOSS
adb uninstall com.port80.app         # GMS
```

---

## Application IDs

| Flavor | Application ID | Can coexist? |
|--------|---------------|--------------|
| FOSS | `com.port80.app.foss` | ✅ Yes — both flavors install side-by-side |
| GMS | `com.port80.app` | ✅ Yes — different application IDs |
