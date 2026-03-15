# StreamCaster Sideload Instructions (Step by Step)

This guide installs the APK you just built:

- artifacts/streamcaster-foss-debug.apk

## 1. Prerequisites

1. Install Android Platform Tools (adb) on your Mac.
2. Have a USB cable for your Android device.
3. On your Android device:
   - Open Settings > About phone.
   - Tap Build number 7 times to enable Developer options.
   - Open Settings > Developer options.
   - Turn on USB debugging.

## 2. Connect and verify device

1. Connect the Android phone by USB.
2. In terminal, run:

   adb devices

3. If prompted on phone, tap Allow USB debugging.
4. Confirm your device shows as device (not unauthorized).

## 3. Install the APK with adb (recommended)

1. From the project root, run:

   adb install -r artifacts/streamcaster-foss-debug.apk

2. Wait for Success.
3. Open StreamCaster on your phone.

## 4. If install is blocked by security policy

1. Remove old app version first (if package signature differs):

   adb uninstall com.port80.app.foss

2. Re-install:

   adb install artifacts/streamcaster-foss-debug.apk

## 5. Manual sideload (no adb)

1. Copy artifacts/streamcaster-foss-debug.apk to your phone (AirDrop, Drive, email, or USB file transfer).
2. On the phone, open Files and tap the APK.
3. If prompted, allow Install unknown apps for the app you used to open the file.
4. Continue install.

## 6. Verify app package and launch from adb (optional)

1. Check package installed:

   adb shell pm list packages | grep com.port80.app.foss

2. Launch app:

   adb shell am start -n com.port80.app.foss/com.port80.app.MainActivity

## 7. Rebuild for future updates

1. Build new APK:

   ./gradlew :app:assembleFossDebug

2. Refresh packaged copy:

   cp app/build/outputs/apk/foss/debug/app-foss-debug.apk artifacts/streamcaster-foss-debug.apk

3. Reinstall with replace:

   adb install -r artifacts/streamcaster-foss-debug.apk
