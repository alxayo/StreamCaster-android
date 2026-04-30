# StreamCaster

**StreamCaster** is a free, open-source native Android application for live streaming camera and microphone input to RTMP, RTMPS, and SRT ingestion endpoints. It is designed for creators, developers, and self-hosters who need a lightweight, privacy-respecting live streaming tool.

**Distribution channels:** Google Play Store (`gms` flavor), F-Droid (`foss` flavor, no unrelated Play Services APIs), and direct APK sideloading. The `foss` flavor intentionally includes bundled ML Kit barcode scanning and its required transitive support artifacts for offline QR endpoint import.

---

## Table of Contents

1.  [Feature Highlights](#1-feature-highlights)
2.  [Technology Stack](#2-technology-stack)
3.  [Architecture Overview](#3-architecture-overview)
4.  [Repository Layout](#4-repository-layout)
5.  [Prerequisites](#5-prerequisites)
6.  [Building the App](#6-building-the-app)
7.  [Deploying via Sideloading](#7-deploying-via-sideloading)
8.  [First-Run Setup](#8-first-run-setup)
9.  [Protocol and Codec Reference](#9-protocol-and-codec-reference)
10. [Permissions Reference](#10-permissions-reference)
11. [Security Model](#11-security-model)
12. [CI/CD](#12-cicd)
13. [Troubleshooting](#13-troubleshooting)
14. [License](#14-license)

---

## 1. Feature Highlights

### Streaming Protocols

| Protocol | Transport | Encryption | Status |
|----------|-----------|------------|--------|
| **RTMP** | TCP | None (plaintext) | ✅ Stable |
| **RTMPS** | TCP + TLS | System TrustManager | ✅ Stable |
| **SRT** | UDP | AES-128/256 via passphrase | ✅ Stable (caller mode) |

SRT listener and rendezvous modes are available but marked experimental.

### Video Codecs

| Codec | RTMP/RTMPS | SRT | Notes |
|-------|------------|-----|-------|
| **H.264** (AVC) | ✅ | ✅ | Default; universal hardware support |
| **H.265** (HEVC) | ✅ | ✅ | Enhanced RTMP; ~30% lower bitrate at same quality |
| **AV1** | ✅ | ❌ | Enhanced RTMP only; ~45% lower bitrate; requires hardware encoder |

### Other Features

- **Adaptive Bitrate (ABR)** — codec-specific quality ladders with automatic adjustment based on network conditions.
- **Thermal Management** — monitors device temperature and reduces encoder load to prevent overheating. 60-second cooldown between encoder restarts.
- **Multiple Endpoint Profiles** — save and switch between streaming targets with encrypted credential storage.
- **QR Endpoint Import** — scan versioned endpoint QR codes to prefill RTMP, RTMPS, or SRT profiles; the user reviews and saves before credentials are stored.
- **Background Streaming** — continues via foreground service when the app is backgrounded.
- **Live HUD** — displays bitrate, FPS, resolution, session duration, connection state, protocol badge, and codec badge.
- **Local Recording** — optional concurrent MP4 recording (API 29+ via MediaStore, API 23–28 via external files).
- **Audio-Only / Video-Only** — flexible media mode selection per stream.
- **Product Flavors** — `foss` (F-Droid; bundled ML Kit QR scanner allowlisted) and `gms` (Play Store).

---

## 2. Technology Stack

| Component | Choice | Version |
|-----------|--------|---------|
| **Language** | Kotlin | 2.0.21 |
| **UI Framework** | Jetpack Compose + Material 3 | BOM 2024.12.01 |
| **Architecture** | MVVM (ViewModel + StateFlow) | — |
| **Dependency Injection** | Hilt | 2.51.1 |
| **Streaming Engine** | RootEncoder (`RtmpCamera2`, `SrtCamera2`) | 2.5.5 |
| **Camera** | Camera2 via RootEncoder for streaming; CameraX only for QR endpoint import | — |
| **QR Scanner** | CameraX + bundled ML Kit barcode scanning | CameraX 1.3.4 / ML Kit 17.3.0 |
| **Navigation** | Jetpack Navigation Compose | 2.8.5 |
| **Settings Storage** | Jetpack DataStore Preferences | 1.1.1 |
| **Credential Storage** | EncryptedSharedPreferences (Keystore-backed) | 1.1.0-alpha06 |
| **Crash Reporting** | ACRA | 5.11.4 |
| **Build System** | Gradle Kotlin DSL + Version Catalog | AGP 8.13.2 |
| **Min SDK** | API 23 (Android 6.0) | — |
| **Target / Compile SDK** | API 35 (Android 15) | — |
| **JDK** | 17 | — |

---

## 3. Architecture Overview

StreamCaster follows a single-activity MVVM architecture with Hilt dependency injection.

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer (Compose)                     │
│  StreamScreen · EndpointScreen · Settings · StreamHud    │
│         │ observes StateFlow    │ user actions            │
│         ▼                       ▼                        │
│  ┌─────────────┐    ┌──────────────────┐                │
│  │ StreamVM    │    │ EndpointVM       │                │
│  │ SettingsVM  │    │                  │                │
│  └──────┬──────┘    └────────┬─────────┘                │
│         │ binds               │ reads/writes             │
├─────────┼────────────────────┼──────────────────────────┤
│         ▼                    ▼                           │
│  ┌────────────────────────────────────────┐              │
│  │         StreamingService (FGS)         │              │
│  │  • Owns MutableStateFlow<StreamState>  │              │
│  │  • Owns MutableStateFlow<StreamStats>  │              │
│  │  • Single source of truth              │              │
│  └──────┬──────────────┬─────────────────┘              │
│         │              │                                 │
│  ┌──────▼──────┐  ┌───▼─────────────────┐              │
│  │ EncoderBridge│  │ ConnectionManager   │              │
│  │ (RTMP / SRT)│  │ ReconnectPolicy     │              │
│  └──────┬──────┘  │ EncoderController   │              │
│         │         └─────────────────────┘              │
├─────────┼──────────────────────────────────────────────┤
│         ▼                                               │
│  ┌────────────────────────────────────────┐              │
│  │           Data Layer                    │              │
│  │  SettingsRepository (DataStore)         │              │
│  │  EndpointProfileRepository (Encrypted)  │              │
│  │  DeviceCapabilityQuery (Camera2/Codec)  │              │
│  └────────────────────────────────────────┘              │
└─────────────────────────────────────────────────────────┘
```

### Source-of-Truth Boundaries

| Data | Owner | Storage |
|------|-------|---------|
| Stream state (`StreamState`) | `StreamingService` via `StateFlow` | In-memory |
| User settings (resolution, fps, etc.) | `SettingsRepository` | Jetpack DataStore |
| Credentials and profiles | `EndpointProfileRepository` | EncryptedSharedPreferences |
| Device capabilities | `DeviceCapabilityQuery` | Read-only Camera2 + MediaCodecList |
| Encoder quality changes | `EncoderController` | Mutex-serialized coroutine |
| QR scanner activity gate | `StreamingService` via `ActiveStreamStateProvider` | In-memory `StateFlow<Boolean>` |

### Key Design Principles

- **StreamingService** is the single source of truth for all stream state. The UI layer is a read-only observer.
- **RtmpCamera2 / SrtCamera2** (from RootEncoder) is the sole camera owner for streaming and preview. The only CameraX exception is the endpoint QR scanner, which is blocked while RootEncoder owns the camera.
- All encoder reconfiguration (ABR + thermal) is serialized through **EncoderController** using a coroutine `Mutex`.
- **ConnectionParams** sealed class keeps protocol logic encapsulated and prevents secret leakage via URL string building.
- Per-session bridge creation — a new `EncoderBridge` is created for each streaming session, not lazily.

---

## 4. Repository Layout

```
android/
├── app/
│   ├── build.gradle.kts              # App-level build config, flavors, dependencies
│   ├── proguard-rules.pro            # R8/ProGuard rules for release builds
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── res/                   # Drawables, strings, themes
│       │   └── java/com/port80/app/
│       │       ├── App.kt            # Application class (ACRA init)
│       │       ├── MainActivity.kt   # Single Compose activity
│       │       ├── audio/            # Audio focus and source management
│       │       ├── camera/           # Device capability queries (Camera2, MediaCodec)
│       │       ├── crash/            # ACRA config + CredentialSanitizer
│       │       ├── data/             # Repositories + data models
│       │       │   ├── qr/           # QR endpoint import parser
│       │       │   └── model/        # EndpointProfile, StreamState, StreamConfig, etc.
│       │       ├── di/               # Hilt DI modules (App, Camera, Data, Service, etc.)
│       │       ├── navigation/       # Compose NavGraph
│       │       ├── overlay/          # Overlay manager (no-op placeholder)
│       │       ├── service/          # Streaming service, bridges, ABR, reconnect, etc.
│       │       ├── thermal/          # Thermal monitoring (API 29+ and legacy)
│       │       ├── ui/
│       │       │   ├── components/   # CameraPreview, StreamHud, PermissionHandler
│       │       │   ├── settings/     # Endpoint, Video, Audio, General settings screens
│       │       │   └── stream/       # Main streaming screen + ViewModel
│       │       └── util/             # OrientationHelper, RedactingLogger
│       └── test/                     # Unit tests (245 test methods)
│           └── java/com/port80/app/
│               ├── crash/            # CredentialSanitizer, ACRA tests
│               ├── data/             # Repository and model tests
│               └── service/          # ABR, connection, encoder, state tests
├── artifacts/                        # Pre-built APKs
├── build.gradle.kts                  # Root build file (plugin declarations)
├── settings.gradle.kts               # Project settings, repository config
├── gradle/libs.versions.toml         # Version catalog (all dependency versions)
├── gradle.properties                 # Gradle JVM args, AndroidX flags
├── .github/
│   └── workflows/
│       ├── build.yml                 # CI: build both flavors, run tests, GMS check
│       └── release-apk.yml          # Release: build + attach APKs to GitHub release
├── SPECIFICATION.md                  # Full application specification
├── IMPLEMENTATION_PLAN.md            # Technical implementation plan
└── SIDELOAD_INSTRUCTIONS.md          # Quick-reference sideloading guide
```

---

## 5. Prerequisites

### For Building from Source

| Requirement | Details |
|-------------|---------|
| **JDK 17** | Android Studio bundles one, or install [Eclipse Temurin](https://adoptium.net/) |
| **Android SDK** | Platform API 35, Build Tools for API 35, Platform Tools |
| **Android Studio** | Latest stable (optional — CLI builds work without it) |
| **Git** | To clone the repository |

### For Sideloading Only

| Requirement | Details |
|-------------|---------|
| **Android device** | Running Android 6.0 (API 23) or newer |
| **USB cable** | For ADB install (or use wireless ADB on Android 11+) |
| **ADB** | Part of Android Platform Tools — [download standalone](https://developer.android.com/tools/releases/platform-tools) |

### Verify Your Environment

```bash
# Check JDK version (must be 17+)
java -version

# Check ADB is available
adb --version

# Check connected devices
adb devices -l
```

---

## 6. Building the App

### 6.1 Clone the Repository

```bash
git clone https://github.com/alxayo/StreamCaster-android.git
cd StreamCaster-android
```

### 6.2 Build Variants

StreamCaster has two product flavors and two build types, giving four build variants:

| Variant | Flavor | Build Type | Application ID | Use Case |
|---------|--------|-----------|----------------|----------|
| `fossDebug` | foss | debug | `com.port80.app.foss` | Local testing, F-Droid (bundled ML Kit QR scanner allowlisted) |
| `fossRelease` | foss | release | `com.port80.app.foss` | F-Droid distribution |
| `gmsDebug` | gms | debug | `com.port80.app` | Local testing with Google Play Services |
| `gmsRelease` | gms | release | `com.port80.app` | Google Play Store |

> **Recommended for most users:** `fossDebug` — works on all devices, includes offline QR scanning, and does not require Play Services.

### 6.3 Build from Command Line

#### Build a Single Variant

```bash
# Make gradlew executable (first time only)
chmod +x gradlew

# Build FOSS debug APK (recommended)
./gradlew :app:assembleFossDebug

# Build GMS debug APK
./gradlew :app:assembleGmsDebug

# Build FOSS release APK (minified + shrunk)
./gradlew :app:assembleFossRelease

# Build GMS release APK
./gradlew :app:assembleGmsRelease
```

#### Build All Variants

```bash
./gradlew assembleDebug          # Both foss + gms debug
./gradlew assembleRelease        # Both foss + gms release
./gradlew assemble               # All four variants
```

#### Output APK Locations

After a successful build, APKs are located at:

```
app/build/outputs/apk/foss/debug/app-foss-debug.apk
app/build/outputs/apk/foss/release/app-foss-release.apk
app/build/outputs/apk/gms/debug/app-gms-debug.apk
app/build/outputs/apk/gms/release/app-gms-release.apk
```

> **Note:** Release builds use R8 minification and resource shrinking. They require signing — unsigned release APKs cannot be installed. See [Release Signing](#release-signing) below.

#### Run Unit Tests

```bash
./gradlew testFossDebugUnitTest
```

#### Verify F-Droid Compliance (FOSS Flavor Allowlist)

```bash
./gradlew :app:dependencies --configuration fossReleaseRuntimeClasspath | grep -i "gms\|play-services\|mlkit"
# Expected: bundled com.google.mlkit:barcode-scanning may appear.
# Expected: ML Kit's required com.google.android.gms transitive artifacts may appear under that subtree.
# Not allowed: unrelated Play Services APIs outside the QR scanner dependency tree.
```

### 6.4 Build from Android Studio

1. **Open** the `android/` folder in Android Studio.
2. **Wait** for Gradle sync to complete (status bar at the bottom).
3. **Select build variant:** Build → Select Build Variant → choose `fossDebug` (or any variant).
4. **Run on device:** connect a device or start an emulator, then click ▶ Run.
5. **Build APK only:** Build → Build Bundle(s) / APK(s) → Build APK(s).

### Release Signing

Release builds require a signing configuration. To sign locally:

1. Generate a keystore (if you don't have one):

   ```bash
   keytool -genkey -v -keystore streamcaster-release.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias streamcaster
   ```

2. Add signing config to `app/build.gradle.kts` inside the `android {}` block:

   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file("../streamcaster-release.jks")
           storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
           keyAlias = "streamcaster"
           keyPassword = System.getenv("KEY_PASSWORD") ?: ""
       }
   }
   ```

3. Reference it in the release build type:

   ```kotlin
   buildTypes {
       release {
           signingConfig = signingConfigs.getByName("release")
           // ... existing config
       }
   }
   ```

> **Never commit your keystore or passwords to version control.**

---

## 7. Deploying via Sideloading

Sideloading installs an APK directly onto an Android device without going through an app store. There are three methods: USB with ADB (recommended), wireless ADB, and manual file transfer.

### 7.1 Prepare Your Android Device

These steps are required once per device:

1. **Enable Developer Options:**
   - Open **Settings → About Phone**.
   - Tap **Build Number** 7 times. You will see "You are now a developer!"

2. **Enable USB Debugging:**
   - Open **Settings → Developer Options** (may be under System → Developer Options on some devices).
   - Toggle **USB Debugging** on.

3. **(Android 11+) Enable Wireless Debugging** (optional, for wireless ADB):
   - In Developer Options, toggle **Wireless Debugging** on.

### 7.2 Method 1: USB with ADB (Recommended)

This is the fastest and most reliable method.

**Step 1 — Connect the device**

Plug your Android device into your computer via USB cable.

**Step 2 — Authorize the connection**

When prompted on the phone, tap **"Allow USB Debugging"**. Check "Always allow from this computer" for convenience.

**Step 3 — Verify the device is recognized**

```bash
adb devices -l
```

You should see output like:

```
List of devices attached
XXXXXXXXXX    device    usb:336855040X product:... model:Pixel_8 ...
```

If you see `unauthorized` instead of `device`, check the phone for the authorization prompt.

**Step 4 — Build the APK** (if you haven't already)

```bash
cd StreamCaster-android
./gradlew :app:assembleFossDebug
```

**Step 5 — Install the APK**

```bash
adb install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

The `-r` flag replaces an existing installation (preserving app data). You should see:

```
Performing Streamed Install
Success
```

**Step 6 — Launch the app**

```bash
adb shell am start -n com.port80.app.foss/com.port80.app.MainActivity
```

Or simply open **StreamCaster** from the app drawer on your device.

**Updating the App**

After making code changes, rebuild and reinstall:

```bash
./gradlew :app:assembleFossDebug && \
adb install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

### 7.3 Method 2: Wireless ADB (Android 11+)

No USB cable needed after initial pairing.

**Step 1 — Enable Wireless Debugging**

On your phone: Settings → Developer Options → Wireless Debugging → On.

**Step 2 — Pair (first time only)**

On the phone, tap **"Pair device with pairing code"**. Note the IP:port and pairing code shown.

```bash
adb pair <ip>:<pairing-port>
# Enter the pairing code when prompted
```

**Step 3 — Connect**

After pairing, the Wireless Debugging screen shows a different IP:port for connecting:

```bash
adb connect <ip>:<port>
```

**Step 4 — Install the APK**

```bash
adb install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

> **Tip:** Both devices must be on the same Wi-Fi network. Enterprise networks with client isolation may block this.

### 7.4 Method 3: Manual File Transfer (No ADB)

For users who don't have ADB installed.

**Step 1 — Copy the APK to your phone**

Transfer the APK file using any of these methods:
- **USB file transfer**: Connect via USB, select "File Transfer" mode on the phone, copy the APK to the Downloads folder.
- **Cloud storage**: Upload to Google Drive, Dropbox, etc., then download on the phone.
- **Email**: Email the APK to yourself and download the attachment on the phone.
- **Direct download**: Host the APK on a web server and download via the phone's browser.

**Step 2 — Allow installation from unknown sources**

When you tap the APK file on your phone, Android will prompt you to allow installing from the source app (Files, Chrome, etc.):
- Tap **Settings** in the prompt.
- Toggle **"Allow from this source"** on.
- Go back and tap **Install**.

**Step 3 — Install and open**

Tap **Install** → **Open** when the installation completes.

### 7.5 Verifying the Installation

```bash
# Check the package is installed
adb shell pm list packages | grep com.port80.app

# Check app version
adb shell dumpsys package com.port80.app.foss | grep versionName

# Launch the app
adb shell am start -n com.port80.app.foss/com.port80.app.MainActivity
```

### 7.6 Uninstalling

```bash
# Via ADB
adb uninstall com.port80.app.foss

# Or: Settings → Apps → StreamCaster → Uninstall
```

### 7.7 Installing the GMS Flavor

The GMS flavor uses application ID `com.port80.app` (no suffix):

```bash
./gradlew :app:assembleGmsDebug
adb install -r app/build/outputs/apk/gms/debug/app-gms-debug.apk
adb shell am start -n com.port80.app/com.port80.app.MainActivity
```

> Both flavors can be installed side-by-side since they have different application IDs.

---

## 8. First-Run Setup

### 8.1 Grant Permissions

On first launch, StreamCaster requests permissions as needed:

1. **Camera** and **Microphone** — requested when you tap **Start** for the first time.
2. **Notifications** (Android 13+) — requested before starting the foreground service.
3. **Camera for QR import** — requested only after you choose **Scan QR Code** in the endpoint add flow.

If you deny a permission, the app will show a rationale dialog. You can grant permissions later in system Settings → Apps → StreamCaster → Permissions.

### 8.2 Configure an Endpoint Profile

1. Open the app and navigate to **Endpoints** (from the settings or navigation menu).
2. A default "Local RTMP" profile may be pre-configured:
   - URL: `rtmp://192.168.0.12:1935/live`
   - Stream key: `test`
3. Tap a profile to **edit**, or tap **+** to create a new one.
   - Choose **Manual Entry** to type the endpoint fields yourself.
   - Choose **Scan QR Code** to scan a versioned QR payload. Scanning is disabled while a stream or preview owns the camera.
4. Fill in:
   - **Name** — a label for this endpoint.
   - **URL** — your streaming server address. The protocol is auto-detected from the URL scheme:
     - `rtmp://` → RTMP
     - `rtmps://` → RTMPS (TLS)
     - `srt://` → SRT
   - **Stream Key** (RTMP/RTMPS only) — provided by your streaming platform.
   - **SRT fields** (SRT only) — passphrase, stream ID, latency, mode (caller/listener/rendezvous).
   - **Video Codec** — H.264 (default), H.265, or AV1. Availability depends on device hardware.
5. If a scanned QR matches an existing endpoint, StreamCaster asks before updating the saved URL and credentials.
6. If a scanned QR requests default status, StreamCaster asks before applying it.
7. **Set as default** — mark the profile you want to stream to by default.

#### QR Endpoint Payload Format

QR import accepts either a plain endpoint URL or a versioned JSON object. RTMP/RTMPS URLs may include the stream key as the final path segment; StreamCaster splits it into `URL` and `Stream Key` before showing the editor.

```json
{
   "v": 1,
   "name": "Main Channel",
   "url": "rtmps://live.example.com/app/live_key_123",
   "videoCodec": "H264",
   "isDefault": false
}
```

Supported JSON fields: `v`, `name`, `url`, `streamKey`, `username`, `password`, `videoCodec`, `srtPassphrase`, `srtKeyLength`, `srtLatencyMs`, `srtMode`, `srtStreamId`, and `isDefault`.

### 8.3 Start Streaming

1. Return to the main streaming screen.
2. Tap **Start**.
3. The HUD will show connection state, bitrate, FPS, resolution, protocol badge, and codec badge.
4. To stop, tap **Stop** or use the notification action.

### Emulator Tips

- Use `10.0.2.2` instead of `localhost` to reach the host machine from the Android Emulator.
- Physical device on LAN: use your host machine's LAN IP (e.g., `rtmp://192.168.1.100:1935/live`).

---

## 9. Protocol and Codec Reference

### Protocol Compatibility Matrix

| Feature | RTMP | RTMPS | SRT |
|---------|------|-------|-----|
| Transport | TCP | TCP + TLS | UDP |
| Encryption | None | TLS (system CA) | AES passphrase |
| H.264 | ✅ | ✅ | ✅ |
| H.265 (Enhanced RTMP) | ✅ | ✅ | ✅ |
| AV1 (Enhanced RTMP) | ✅ | ✅ | ❌ |
| Reconnect | Exponential backoff | Exponential backoff | Exponential backoff |
| Connection test | TCP socket | TCP socket | UDP datagram |

### SRT Configuration

| Field | Default | Description |
|-------|---------|-------------|
| **Host:Port** | (from URL) | Parsed from `srt://host:port` |
| **Mode** | Caller | `caller` (default), `listener`, `rendezvous` (listener/rendezvous are experimental) |
| **Latency** | 120 ms | End-to-end latency in milliseconds |
| **Passphrase** | (empty) | AES encryption key (10–79 characters). Treated as a credential — encrypted at rest, redacted in logs |
| **Stream ID** | (empty) | Identifies the stream to the server. Treated as a credential |

SRT URLs are built internally as: `srt://host:port?mode=caller&latency=120&passphrase=****&streamid=****`

### Enhanced RTMP

Enhanced RTMP extends the standard RTMP protocol to support modern codecs (H.265/HEVC and AV1). Requirements:
- The receiving server must support Enhanced RTMP (e.g., recent versions of OBS Server, Cloudflare Stream, YouTube Live).
- The device must have a hardware encoder for the selected codec.
- AV1 hardware encoders are rare on current Android devices — the app only shows AV1 as an option if the device supports it.

### ABR (Adaptive Bitrate) Ladders

Bitrate targets are codec-specific to account for encoding efficiency differences:

| Resolution | H.264 Bitrate | H.265 Bitrate | AV1 Bitrate |
|------------|---------------|---------------|-------------|
| 1080p | 4500 kbps | 3150 kbps | 2475 kbps |
| 720p | 2500 kbps | 1750 kbps | 1375 kbps |
| 480p | 1000 kbps | 700 kbps | 550 kbps |
| 360p | 600 kbps | 420 kbps | 330 kbps |

The ABR system adjusts bitrate automatically based on network conditions. Resolution and FPS changes require a brief encoder restart (~3 seconds).

---

## 10. Permissions Reference

| Permission | Required From | Purpose |
|------------|---------------|---------|
| `CAMERA` | API 23+ | Capture video from the device camera; scan endpoint QR codes |
| `RECORD_AUDIO` | API 23+ | Capture audio from the microphone |
| `INTERNET` | All | Send stream data to the ingestion server |
| `ACCESS_NETWORK_STATE` | All | Detect network changes for reconnection |
| `FOREGROUND_SERVICE` | API 28+ | Run the streaming service in the foreground |
| `FOREGROUND_SERVICE_CAMERA` | API 34+ | Foreground service type declaration |
| `FOREGROUND_SERVICE_MICROPHONE` | API 34+ | Foreground service type declaration |
| `POST_NOTIFICATIONS` | API 33+ | Show the persistent streaming notification |
| `WAKE_LOCK` | All | Keep CPU active during streaming |

Hardware features (`camera`, `camera.autofocus`, `microphone`) are declared as `android:required="false"` so the app can be installed on devices without a camera (for audio-only streaming).

---

## 11. Security Model

### Credential Storage

All stream keys, passwords, SRT passphrases, and auth tokens are stored in **EncryptedSharedPreferences** backed by the Android Keystore. There is no fallback to plain SharedPreferences — if encryption fails, the user sees an error.

QR endpoint payloads are treated as untrusted input. The app parses only known v1 fields, never silently applies default-profile changes, and routes parsed credentials through the normal encrypted endpoint editor/save path.

### Intent Security

The foreground service start Intent carries **only a profile ID** (a string). The service reads credentials from the encrypted repository at runtime. Credentials are never passed through Intent extras.

### Transport Security

- **RTMPS** uses the system default TrustManager. No custom `X509TrustManager` implementations are used.
- **RTMP** (plaintext) shows an explicit warning dialog and requires per-attempt user opt-in.
- **SRT without passphrase** shows a similar unencrypted transport warning.

### Log Redaction

All log statements that may contain RTMP URLs, stream keys, SRT passphrases, or auth tokens are processed through `CredentialSanitizer`. This applies at all log levels in both debug and release builds.

### Crash Reports (ACRA)

ACRA is configured to **exclude** sensitive fields: `LOGCAT`, `SHARED_PREFERENCES`, `DUMPSYS_MEMINFO`, `THREAD_DETAILS`. A `ReportTransformer` runs `CredentialSanitizer` on all string fields before transmission. ACRA transport enforces HTTPS.

### Manifest Hardening

- `android:allowBackup="false"` prevents backup of app data (which would include encrypted preferences).
- No `android:usesCleartextTraffic="true"`.

---

## 12. CI/CD

### GitHub Actions Workflows

#### `build.yml` — Build and Test

Runs on every push to `main` and every pull request targeting `main`:

1. Checks out code.
2. Sets up JDK 17.
3. Builds both `fossDebug` and `gmsDebug` APKs.
4. Runs `testFossDebugUnitTest`.
5. Verifies the FOSS flavor dependency matches are limited to bundled ML Kit barcode scanning and its required transitive support artifacts.

#### `release-apk.yml` — Release APK

Triggered on GitHub release publication or manual workflow dispatch:

1. Builds FOSS debug, FOSS release, and GMS release APKs.
2. Attaches renamed APKs as release assets.

### Triggering a Release

1. Tag a commit: `git tag v1.1.0 && git push origin v1.1.0`
2. Create a GitHub release from the tag.
3. The `release-apk.yml` workflow runs automatically and attaches APKs to the release.

Or trigger manually: Actions → Release APK → Run workflow → enter the tag name.

---

## 13. Troubleshooting

### Build Issues

#### Gradle sync fails

- Ensure JDK 17 is installed and selected. Check with `java -version`.
- Delete Gradle caches and re-sync:

  ```bash
  rm -rf ~/.gradle/caches/
  ./gradlew --refresh-dependencies
  ```

#### "SDK location not found"

- Create a `local.properties` file in the project root:

  ```properties
  sdk.dir=/path/to/your/Android/sdk
  ```

  On macOS with Android Studio: `sdk.dir=/Users/<you>/Library/Android/sdk`

### Device / ADB Issues

#### "No connected devices" when installing

```bash
adb devices -l
```

If empty: check USB cable, USB Debugging toggle, and authorization prompt on the phone.

#### "INSTALL_FAILED_UPDATE_INCOMPATIBLE"

The installed APK has a different signing key. Uninstall first:

```bash
adb uninstall com.port80.app.foss
adb install app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

### Streaming Issues

#### Start button does not stream

Check in order:
1. Camera and microphone permissions are granted.
2. A default endpoint profile exists.
3. The endpoint is reachable from the device network.
4. The RTMP/SRT server is listening on the expected interface and port.

#### "Auth Failed"

The streaming server rejected the credentials. Verify:
- Stream key is correct (RTMP/RTMPS).
- Passphrase matches the server config (SRT).
- The server is configured to accept connections.

#### SRT connection fails

- Verify the server supports SRT and is listening on the specified UDP port.
- Check that the SRT mode matches (both sides must agree on caller/listener or use rendezvous).
- Ensure passphrase matches exactly (SRT passphrase is case-sensitive).
- Try increasing latency if you're on a high-latency network.

#### Stream works briefly then drops

- Check network stability. SRT is more resilient to packet loss than RTMP.
- If using RTMP, the ABR system may be reducing quality — watch the HUD bitrate indicator.
- Thermal throttling may be reducing encoder quality. Check for the thermal badge on the HUD.

#### Camera preview is black

- Close other camera apps.
- Re-grant camera permission (Settings → Apps → StreamCaster → Permissions).
- Restart the device.
- On emulator: ensure the emulated camera is configured in AVD settings.

#### Foreground service stops immediately (Android 13+)

- Grant the notification permission (POST_NOTIFICATIONS).
- Start streaming from the in-app button (foreground action), not from a background trigger.

### Logging

```bash
# Clear old logs
adb logcat -c

# Watch StreamCaster logs
adb logcat -v time | grep --line-buffered -E "com.port80.app|StreamCaster"

# Watch crashes
adb logcat -v time | grep --line-buffered -E "FATAL EXCEPTION|AndroidRuntime"
```

---

## 14. License

See project license files and dependency licenses for details.

### Key Dependency Licenses

| Library | License |
|---------|---------|
| RootEncoder | Apache 2.0 |
| Jetpack (Compose, Hilt, DataStore) | Apache 2.0 |
| ACRA | Apache 2.0 |
| Kotlin | Apache 2.0 |
