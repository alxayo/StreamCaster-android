# StreamCaster — Android RTMP Streaming Application Specification

**Version:** 2.0 (Hardened)  
**Date:** March 14, 2026  
**Status:** Draft  
**Package ID:** `com.port80.app`  
**App Name:** StreamCaster

---

## 1. Overview

**StreamCaster** is a free, open-source native Android application that captures video and/or audio from the device camera and microphone and streams it in real-time to a user-configured RTMP/RTMPS ingestion endpoint.

Distributed via Google Play Store, F-Droid, and direct APK download.

### 1.1 Product Scope

- Native Android app to live-stream camera and/or microphone to a single RTMP/RTMPS ingestion endpoint.
- Optional concurrent local MP4 recording.
- Basic streaming HUD (bitrate, fps, resolution, duration, connection state).
- Multiple saved endpoint profiles with encrypted credential storage.
- Adaptive bitrate with device-capability-aware quality ladder.
- Background-capable via foreground service.

### 1.2 Non-Goals

The following are explicitly out of scope for the current version:

- Multi-destination streaming.
- Overlay rendering beyond a no-op architectural hook.
- H.265 encoding (deferred).
- SRT protocol (deferred).
- Stream scheduling.
- Analytics or tracking SDKs.
- Ads or in-app purchases.
- GMS dependencies in the `foss` build flavor.
- Tablet or Chromebook-optimized UI.

---

## 2. Technology Stack

| Component | Choice | Rationale |
|---|---|---|
| **Language** | **Kotlin** | Modern, concise, null-safe. RootEncoder is 65% Kotlin-native, ensuring seamless interop. |
| **Streaming Library** | **RootEncoder v2.7.x** (Apache 2.0) | Actively maintained (daily commits as of March 2026). Supports RTMP, RTMPS, RTSP, SRT. Provides Camera2 integration, adaptive bitrate, H.264/H.265/AAC encoding. |
| **Camera Framework** | **RootEncoder `RtmpCamera2`** (Camera2 internally) | RootEncoder's built-in camera class is the sole camera owner. No CameraX or Camera1 layering. See §5.3. |
| **Build System** | **Gradle (Kotlin DSL)** with Android Gradle Plugin 8.x | Standard toolchain, compatible with VS Code + Gradle extension. |
| **Min SDK** | **API 23 (Android 6.0 Marshmallow)** | Required for EncryptedSharedPreferences (Android Keystore-backed), runtime permissions model, and modern MediaCodec behavior. Covers ~97% of active Android devices. |
| **Target SDK** | **API 35 (Android 15)** | Required for Google Play Store submission in 2026. |
| **Compile SDK** | **35** | Access to latest platform APIs. |
| **Architecture** | **MVVM** with Android ViewModel + StateFlow | Clean separation, lifecycle-aware, testable. |
| **DI** | **Hilt** | Standard Jetpack DI, minimal boilerplate. |
| **UI** | **Jetpack Compose** + Material 3 | Modern declarative UI; the camera preview surface uses an `AndroidView` wrapper around the RootEncoder preview. |
| **Persistence** | **DataStore (Preferences)** | For storing non-sensitive settings (default camera, resolution, etc.). |
| **Credential Storage** | **EncryptedSharedPreferences** (Keystore-backed) | For stream keys, passwords. Requires API ≥ 23. |
| **Background Service** | **Foreground Service** (type `camera` + `microphone`) | Required for background streaming. Displays a persistent notification. |
| **Crash Reporting** | **ACRA** (Apache 2.0) | Open-source, privacy-respecting, F-Droid compatible. Reports via email or self-hosted HTTP endpoint. No third-party cloud dependencies. |

### 2.1 Why RootEncoder over Alternatives

| Library | Min API | RTMPS | Active | Verdict |
|---|---|---|---|---|
| **RootEncoder** | 16 (23 for Camera2 path) | Yes | Yes (March 2026) | **Selected** — widest compat, full feature set |
| Larix SDK | 24 | Yes | Yes | Rejected — higher min API, proprietary |
| libstreaming | 14 | No | No (EOL) | Rejected — dead project, no RTMP |
| HaishinKit Android | N/A | N/A | No | Rejected — no maintained Android port |

---

## 3. Supported Platforms and Operating Assumptions

| Dimension | Assumption |
|---|---|
| **Min SDK** | API 23 (Android 6.0). Required for EncryptedSharedPreferences, runtime permissions, and modern MediaCodec behavior. |
| **Target SDK** | API 35 (Android 15). Required for Google Play Store submission in 2026. |
| **Compile SDK** | 35. |
| **Device class** | Phones only. Tablets and Chromebooks are not guaranteed to work and are not tested against. |
| **Camera/encoder** | Hardware H.264 (Baseline/Main) + AAC-LC expected. H.265 is deferred. Devices without a hardware H.264 encoder are unsupported. |
| **Network** | Hostile networks assumed. RTMPS is the preferred transport. RTMP is permitted only with explicit user consent (see §9). |
| **OEM posture** | Aggressive battery/FGS restrictions (Samsung, Xiaomi, Huawei, etc.) assumed active. Doze and app-standby are assumed active. The app must not rely on behavior that only works with battery optimizations disabled. |

---

## 4. Functional Requirements

### 4.1 Media Capture

| ID | Requirement | Priority |
|---|---|---|
| MC-01 | Stream **video only**, **audio only**, or **both** — user selects before or during stream. Mid-session video→audio downgrade is permitted; audio→video upgrade requires camera reacquire and encoder re-init. | Must |
| MC-02 | Default to **back camera**. User can switch to front camera before or during stream. | Must |
| MC-03 | Live camera preview displayed before and during streaming. Preview must rebind after process death or activity recreation if the service is still alive. | Must |
| MC-04 | Orientation (portrait / landscape) selected by user before stream start; locked for the duration of the active session; unlocked only when idle. | Must |
| MC-05 | **Local recording** — optional toggle to save a local MP4 copy simultaneously. On API 29+, the user selects a storage target via SAF or the app writes to MediaStore. On API 23–28, the app writes to app-specific external storage (`getExternalFilesDir`) with an export/share flow. If storage permission is denied or unavailable, recording must fail fast with a user prompt; streaming must not be blocked. | Must |

### 4.2 Video Settings

| ID | Requirement | Default | Priority |
|---|---|---|---|
| VS-01 | **Resolution** selectable from device-supported list, capped to codec-supported profiles/levels via `MediaCodecInfo`. | **720p (1280×720)** | Must |
| VS-02 | **Frame rate** selectable: 24, 25, 30, 60 fps — shown only if the device encoder advertises support. | **30 fps** | Must |
| VS-03 | **Video codec**: H.264 (Baseline/Main profile). H.265 deferred. | H.264 | Must |
| VS-04 | **Video bitrate** selectable or auto. Range: 500 kbps – 8 Mbps, capped to encoder capability. | **2.5 Mbps** (for 720p30) | Must |
| VS-05 | **Keyframe interval** configurable (1–5 seconds). | **2 seconds** | Should |

### 4.3 Audio Settings

| ID | Requirement | Default | Priority |
|---|---|---|---|
| AS-01 | **Audio codec**: AAC-LC. | AAC-LC | Must |
| AS-02 | **Sample rate**: 44100 Hz or 48000 Hz. | **44100 Hz** | Must |
| AS-03 | **Audio bitrate**: 64 / 96 / 128 / 192 kbps. | **128 kbps** | Must |
| AS-04 | **Channels**: Mono / Stereo. | **Stereo** | Should |
| AS-05 | **Mute toggle** during active stream (stops sending audio data). | — | Must |

### 4.4 RTMP Endpoint Configuration

| ID | Requirement | Priority |
|---|---|---|
| EP-01 | User can enter an **RTMP URL** (e.g., `rtmp://ingest.example.com/live`). | Must |
| EP-02 | Support **RTMPS** (RTMP over TLS/SSL) endpoints. | Must |
| EP-03 | Optional **stream key** field (appended to URL or sent separately, per convention). | Must |
| EP-04 | Optional **username / password** authentication fields. | Must |
| EP-05 | **Save as default** — persists the last-used endpoint + key so the user doesn't re-enter it. Credentials stored only via EncryptedSharedPreferences. | Must |
| EP-06 | Multiple saved endpoint profiles (name + URL + key + auth). | Should |
| EP-07 | **Connection test** button — validates connectivity before going live. Must obey the same transport security rules as live streaming (see §9.2). | Should |

### 4.5 Adaptive Bitrate (ABR)

| ID | Requirement | Priority |
|---|---|---|
| AB-01 | Toggle to **enable/disable** adaptive bitrate. | Must |
| AB-02 | When enabled, dynamically lower video bitrate and/or resolution within a device-capability-aware ABR ladder on network congestion. | Must |
| AB-03 | Automatically recover bitrate when bandwidth improves. | Must |
| AB-04 | Display current effective bitrate on the streaming HUD. | Should |

### 4.6 Streaming Lifecycle

| ID | Requirement | Priority |
|---|---|---|
| SL-01 | **Start / Stop** stream via prominent button. | Must |
| SL-02 | **Auto-reconnect** on network drop — configurable retry count (default: unlimited) and interval using exponential backoff with jitter (3 s, 6 s, 12 s, …, cap 60 s). Reconnect must operate within an already-running FGS; no new FGS starts from background. Respect Doze: retries must not exceed one attempt per minute while the device is in idle mode unless the user has exempted the app from battery optimizations. | Must |
| SL-03 | **Background streaming** — continues via foreground service when app is backgrounded or screen is off. The FGS may only be started from a user-initiated action (in-app button or notification action) while the activity is in the foreground or within the allowed post-activity window (API 31+ FGS start restrictions). | Must |
| SL-04 | **Notification controls** — start/stop/mute accessible from the persistent notification. A stop action must cancel any in-flight reconnect and leave the stream fully stopped. Actions must be debounced to prevent double-toggle races. | Must |
| SL-05 | Graceful shutdown on low battery (configurable threshold, default 5%). Auto-stop and finalize local recording at critical (≤ 2%). | Should |
| SL-06 | **Background camera revocation handling** — when the OS revokes camera access in the background, cleanly stop the video track, keep the audio-only RTMP session alive (or send a static placeholder frame if video-only mode). Show "Camera paused" in the notification. On return to foreground, re-acquire camera, re-init video encoder, and send an IDR frame to resume video. | Must |
| SL-07 | **Thermal throttling response** — on API 29+, register `PowerManager.OnThermalStatusChangedListener`. On `THERMAL_STATUS_MODERATE`: show HUD warning. On `THERMAL_STATUS_SEVERE`: step down the ABR ladder (e.g., 720p→480p, 30→15 fps), performing a controlled encoder restart if resolution/fps change requires it. On `THERMAL_STATUS_CRITICAL`: stop stream and recording gracefully and show the user the reason. Enforce a minimum 60-second cooldown between thermal step changes to avoid rapid oscillation. Restore quality when thermals return to normal. | Must |
| SL-08 | **Audio focus / interruption handling** — on incoming call or audio focus loss, mute the microphone and show a muted indicator. Resume sending audio only on explicit user action (unmute). | Must |

### 4.7 Overlay Architecture (Future)

| ID | Requirement | Priority |
|---|---|---|
| OV-01 | Architecture supports an **overlay pipeline** (text, timestamps, watermarks) that can be rendered onto the video frame before encoding. | Must (arch) |
| OV-02 | Actual overlay rendering implementation. | Deferred |

> **Implementation note:** RootEncoder supports `GlStreamInterface` for custom OpenGL filters. The architecture will include a pluggable `OverlayManager` interface with a no-op default implementation. Future overlays will implement this interface and render via OpenGL shaders.

---

## 5. Non-Functional Requirements

| ID | Requirement | Target |
|---|---|---|
| NF-01 | **Startup to preview** in < 2 seconds on mid-range devices. | Must |
| NF-02 | **Streaming latency** (glass-to-glass) ≤ 3 seconds over stable LTE. Surface as a debug metric if exceeded. | Should |
| NF-03 | **Battery drain** ≤ 15% per hour of streaming at 720p30 on a 4000 mAh battery. | Should |
| NF-04 | **Crash-free rate** ≥ 99.5%. | Must |
| NF-05 | **APK size** < 15 MB (before Play Store optimization). | Should |
| NF-06 | No third-party analytics or tracking SDKs. | Must |
| NF-07 | All sensitive data (stream keys, passwords) must be stored encrypted via Android Keystore-backed EncryptedSharedPreferences. The app must never fall back to plaintext storage. | Must |
| NF-08 | **No custom SSL bypass.** RTMPS connections must use the system default `TrustManager`. No `X509TrustManager` that accepts all certificates. Users with self-signed certs install them via Android system settings. | Must |
| NF-09 | **Thermal awareness.** On API 29+, register `OnThermalStatusChangedListener`. Progressively degrade stream quality to prevent device overheating and OS-forced frame drops. See SL-07. | Must |

---

## 6. Architecture

### 6.1 High-Level Diagram

```
┌─────────────────────────────────────────────────────────┐
│                        UI Layer                         │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────────┐  │
│  │ Preview  │  │ Controls │  │ Settings Screens      │  │
│  │ (Compose)│  │ (Compose)│  │ (Compose + Navigation)│  │
│  └────┬─────┘  └────┬─────┘  └──────────┬────────────┘  │
│       │              │                   │               │
│  ┌────▼──────────────▼───────────────────▼────────────┐  │
│  │              ViewModels (MVVM)                     │  │
│  │  StreamViewModel · SettingsViewModel               │  │
│  └────────────────────┬──────────────────────────────┘  │
│                       │  binds to service                │
├───────────────────────┼──────────────────────────────────┤
│                 Domain / Service Layer                   │
│  ┌────────────────────▼──────────────────────────────┐  │
│  │       StreamingService (Foreground Service)        │  │
│  │       ← authoritative source of stream state →    │  │
│  │  ┌─────────────┐  ┌────────────┐  ┌───────────┐  │  │
│  │  │ RtmpCamera2 │  │ AudioSource│  │OverlayMgr │  │  │
│  │  │ (Camera2    │  │ (Mic)      │  │ (No-op)   │  │  │
│  │  │  internally)│  │            │  │           │  │  │
│  │  └──────┬──────┘  └─────┬──────┘  └─────┬─────┘  │  │
│  │         │               │               │         │  │
│  │  ┌──────▼───────────────▼───────────────▼──────┐  │  │
│  │  │         RootEncoder Streaming Engine         │  │  │
│  │  │  ┌──────────┐ ┌──────────┐ ┌─────────────┐  │  │  │
│  │  │  │H.264 Enc │ │ AAC Enc  │ │ RTMP/S Conn │  │  │  │
│  │  │  └──────────┘ └──────────┘ └─────────────┘  │  │  │
│  │  │  ┌──────────────┐ ┌───────────────────────┐  │  │  │
│  │  │  │Adaptive Rate │ │ Local Muxer (opt MP4) │  │  │  │
│  │  │  └──────────────┘ └───────────────────────┘  │  │  │
│  │  └─────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────┘  │
├──────────────────────────────────────────────────────────┤
│                    Data Layer                            │
│  ┌────────────────┐  ┌──────────────────────────────┐   │
│  │ SettingsRepo   │  │ EndpointProfileRepo           │   │
│  │ (DataStore)    │  │ (EncryptedSharedPreferences)  │   │
│  └────────────────┘  └──────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
```

### 6.2 Key Components

| Component | Responsibility |
|---|---|
| `StreamViewModel` | Binds to `StreamingService`. Reads authoritative streaming state (idle / connecting / live / reconnecting / stopped). Exposes preview surface, stream stats, and control actions. All start/stop/mute commands are idempotent. |
| `SettingsViewModel` | Reads/writes user preferences. Queries device for supported resolutions, frame rates, and codec profiles via `DeviceCapabilityQuery`. |
| `StreamingService` | Android Foreground Service (`camera` + `microphone` types). Owns the RootEncoder instance. Is the **single source of truth** for stream state. Manages lifecycle independently of the Activity so streaming survives backgrounding. Exposes state via `StateFlow` to bound clients. |
| `DeviceCapabilityQuery` | Queries `CameraManager` and `MediaCodecList` for available cameras, resolutions, frame rates, and codec profiles/levels. Used by settings UI only — does NOT own the camera or open it. |
| `AudioSourceManager` | Configures microphone via RootEncoder's `MicrophoneManager`. |
| `OverlayManager` | Interface with `fun onDrawFrame(canvas: GlCanvas)`. Default no-op. Future overlays plug in here. |
| `SettingsRepository` | Persists non-sensitive settings via Jetpack DataStore. |
| `EndpointProfileRepository` | CRUD for saved RTMP endpoint profiles. Credentials encrypted via EncryptedSharedPreferences backed by Android Keystore. |
| `ConnectionManager` | Handles RTMP connect/disconnect, auto-reconnect logic with exponential backoff + jitter, connection health monitoring. Cancels retries on explicit user stop. |

### 6.3 Camera Strategy — RootEncoder as Sole Camera Owner

> **Design decision:** RootEncoder provides optimized, battle-tested camera management classes (`RtmpCamera2`) that tightly couple camera capture with hardware encoding and RTMP muxing. Layering CameraX or a separate Camera2 session on top would risk surface contention, double camera ownership, and pipeline desynchronization.
>
> **Therefore, the app uses `RtmpCamera2` exclusively for camera ownership.**
> - No CameraX dependency.
> - No Camera1 path (minSdk is 23; Camera2 is universally available).
> - `DeviceCapabilityQuery` only reads `CameraCharacteristics` and `MediaCodecInfo`; it never opens the camera.

```
┌───────────────────────────────┐
│   RootEncoder Camera Classes  │
│   (sole camera owner)         │
├───────────────────────────────┤
│ API ≥ 23                      │
│   → RtmpCamera2 (Camera2)    │
└───────────────────────────────┘
```

Camera switching and preview attachment are delegated directly to `RtmpCamera2.switchCamera()` and `RtmpCamera2.startPreview(surfaceView)`.

---

## 7. Lifecycle and State Management

### 7.1 Foreground Service Rules

- The foreground service declares types `camera` and `microphone` (API 34+ manifest declarations: `FOREGROUND_SERVICE_CAMERA`, `FOREGROUND_SERVICE_MICROPHONE`).
- The FGS may only be started from a **user-initiated action**: the in-app Start button while the Activity is in the foreground, or a notification action. This satisfies API 31+ FGS start restrictions.
- Auto-reconnect operates within an already-running FGS. The app must never attempt to start a new FGS from the background without a user affordance.
- If the OS kills the FGS, the app must not silently restart it. On next activity launch, display a notification or in-app message indicating the session ended and require the user to start a new session.

### 7.2 Activity ↔ Service Binding

- `StreamViewModel` binds to `StreamingService` via `ServiceConnection`.
- The service exposes authoritative state via `StateFlow<StreamState>`. The UI layer is a read-only observer of this state.
- On activity recreation (config change, process death with surviving service), the ViewModel must rebind, restore the preview surface to the existing RootEncoder instance, and reflect current stats.
- If the service has been killed by the time the activity restarts, the ViewModel must show "Stopped" state and clear any stale reconnect state.

### 7.3 Process Death Recovery

- If the service is alive and the activity process is recreated, the preview surface must be re-attached to RootEncoder's existing camera session.
- If both activity and service are dead, the app starts in the default idle state. No automatic stream resumption occurs.

### 7.4 Notification Behavior

- The persistent FGS notification shows current state: Live, Reconnecting, Paused (camera revoked), or Stopped.
- Notification actions: Start, Stop, Mute/Unmute.
- A Stop action must immediately cancel any pending reconnect attempts and transition to stopped state. No zombie notifications may persist after the service stops.
- Actions must be debounced (≥ 500 ms) to prevent double-toggle races between notification and in-app UI.

---

## 8. Media Pipeline Requirements

### 8.1 Encoder Initialization

- Before starting a stream, validate the chosen resolution, frame rate, and profile against `MediaCodecInfo.CodecCapabilities` and `VideoCapabilities`. If the device cannot support the requested configuration, fail fast with an actionable error message and suggest a supported configuration.
- Pre-flight: attempt `MediaCodec.configure()` with the chosen parameters before connecting to the RTMP endpoint to catch encoder failures early.

### 8.2 ABR Ladder

- Define a per-device quality ladder based on encoder capabilities, e.g.:
  - **1080p30 → 720p30 → 540p30 → 480p30** (resolution steps)
  - **30 fps → 24 fps → 15 fps** (frame rate steps)
  - Bitrate scales proportionally to resolution × fps.
- ABR first reduces bitrate only. If insufficient, step down resolution/fps via controlled encoder restart.
- Prefer bitrate reduction before frame skipping. If encoder backpressure is detected, drop non-keyframes first.

### 8.3 Encoder Restart for Quality Changes

- Resolution or frame rate changes during a live stream require a controlled re-init sequence:
  1. Stop the preview and video track.
  2. Release the encoder.
  3. Reconfigure with the new profile.
  4. Restart the encoder and preview.
  5. Send an IDR frame immediately.
- Target: stream gap ≤ 3 seconds during a quality change.

### 8.4 Frame Drop Policy

- Expose a `droppedFrameCount` metric (see §14).
- Prefer bitrate reduction over frame dropping.
- If backpressure forces drops, drop B/P-frames before keyframes.

### 8.5 Latency

- Target glass-to-glass ≤ 3 seconds on stable LTE.
- Surface current measured latency as a debug metric if it exceeds target.

---

## 9. Security and Privacy Requirements

### 9.1 Credential Storage

- Stream keys and passwords must be stored using EncryptedSharedPreferences backed by Android Keystore.
- The app must never fall back to plaintext storage under any circumstance.
- MinSdk 23 guarantees Keystore availability. No API < 23 fallback path exists or is needed.

### 9.2 Transport Security

- If a profile includes authentication (username/password) or a stream key, the app must enforce RTMPS.
- If the user has configured auth and enters an `rtmp://` URL, the app must display a warning dialog explaining the risk of sending credentials over plaintext and require explicit per-attempt opt-in before proceeding.
- The connection test button must obey the same transport rules: it must not send credentials over plaintext RTMP without explicit user consent.
- RTMPS must use the system default `TrustManager`. No custom `X509TrustManager` that accepts all certificates. Users needing self-signed certs must install them into the Android system trust store.

### 9.3 Logging and Crash Reports

- The app must never log RTMP URLs containing stream keys, auth headers, passwords, or tokens in any log level.
- All sensitive fields must be masked in logs and metrics (e.g., `rtmp://host/app/****`).
- ACRA crash reports must:
  - Exclude or redact RTMP URLs, stream keys, and auth fields from all `ReportField` entries.
  - Disable automatic logcat attachment unless a custom scrubber has sanitized the output.
  - Send reports only to user-configured endpoints (HTTP or email).

### 9.4 Permissions

| Permission | When Requested | Required For |
|---|---|---|
| `CAMERA` | Stream start (video modes) | Video capture |
| `RECORD_AUDIO` | Stream start (audio modes) | Audio capture |
| `FOREGROUND_SERVICE` | Manifest (auto-granted) | Background streaming |
| `FOREGROUND_SERVICE_CAMERA` | Manifest (API 34+) | FGS type declaration |
| `FOREGROUND_SERVICE_MICROPHONE` | Manifest (API 34+) | FGS type declaration |
| `POST_NOTIFICATIONS` | Runtime (API 33+) | FGS notification display |
| `INTERNET` | Manifest (auto-granted) | RTMP connection |
| `WAKE_LOCK` | Manifest (auto-granted) | Keep CPU alive during background stream |

> **Removed:** `WRITE_EXTERNAL_STORAGE` is not needed with minSdk 23. Local recording uses app-specific external storage (API 23–28) or MediaStore/SAF (API 29+).

### 9.5 Permissions Flow

```
App Launch
  │
  ├─ API ≥ 33? → Request POST_NOTIFICATIONS
  │
  └─ User taps "Start Stream"
       │
       ├─ Video enabled? → Check CAMERA permission
       │     └─ Denied? → Show rationale → Re-request or disable video
       │
       ├─ Audio enabled? → Check RECORD_AUDIO permission
       │     └─ Denied? → Show rationale → Re-request or disable audio
       │
       └─ All required permissions granted → Start StreamingService → Connect RTMP
```

### 9.6 Background Capture

- The camera and microphone must only be accessed while the foreground service is active with the corresponding type declarations.
- Camera/mic use indicators are shown per OS defaults (API 31+ privacy indicators).
- No audio or video capture may occur without an active FGS.

---

## 10. Screen Map & UI

### 10.1 Screens

| Screen | Description |
|---|---|
| **Main / Stream** | Camera preview (full-screen), start/stop button, mute button, camera-switch button, stream status badge, recording indicator. Minimal HUD overlay showing: bitrate, FPS, duration, connection status. |
| **Endpoint Setup** | RTMP(S) URL field, stream key field, optional username/password, "Test Connection" button, "Save as Default" toggle, saved profiles list. |
| **Video/Audio Settings** | Resolution picker (filtered by device), frame rate picker, video bitrate slider, audio bitrate picker, mono/stereo toggle, ABR enable/disable, keyframe interval, local recording toggle. |
| **General Settings** | Default camera (front/back), orientation lock (portrait/landscape), auto-reconnect toggle + retry settings, battery threshold, media stream selection (video+audio / video-only / audio-only). |

### 10.2 Navigation

```
Main (Stream) ──┬── Endpoint Setup
                ├── Video/Audio Settings
                └── General Settings
```

Single-activity architecture with Compose Navigation.

### 10.3 Stream Screen HUD Layout

```
┌──────────────────────────────────────┐
│ ● LIVE  00:12:34        🔴 REC      │  ← status bar
│                                      │
│                                      │
│         [Camera Preview]             │
│                                      │
│                                      │
│  ↕ 2.4 Mbps   30fps   720p          │  ← stats bar
├──────────────────────────────────────┤
│  [🔇 Mute]  [⏺ START]  [🔄 Cam]    │  ← controls
└──────────────────────────────────────┘
```

---

> **Note:** Permissions and permissions flow are covered in §9.4 and §9.5.

---

## 11. Reliability and Failure Handling

| Scenario | Behavior |
|---|---|
| **Network drop** | Pause send. Reconnect with exponential backoff + jitter (3 s, 6 s, 12 s, …, cap 60 s). In Doze, retries must not exceed one per minute unless the user has exempted the app from battery optimizations. Show "Reconnecting…" badge. Resume on success. |
| **RTMP auth failure** | Stop stream, show error with option to edit credentials. |
| **Encoder error** | Attempt one re-init. If it fails, stop stream and show explicit error identifying the failure cause. |
| **Camera unavailable** | Try alternate camera. If none available, offer audio-only mode. |
| **Camera revoked (background)** | Cleanly stop video track. Keep audio-only RTMP session alive (or send static placeholder frame if video-only). Show "Camera paused" in notification. On return to foreground: re-acquire camera, re-init video encoder, send IDR to resume video. |
| **Microphone revoked mid-stream** | Stop stream entirely and surface an error. Audio track loss cannot be gracefully degraded. |
| **Thermal throttle** | On `THERMAL_STATUS_MODERATE`: warn user via HUD badge. On `THERMAL_STATUS_SEVERE`: step down ABR ladder with controlled encoder restart if needed (minimum 60 s between steps). On `THERMAL_STATUS_CRITICAL`: stop stream and recording gracefully, display reason to user. |
| **FGS killed by OS** | Do not silently restart. On next activity launch, display notification/toast indicating session ended. Require user to start a new session. |
| **Low battery** | Below configured threshold: show warning. Below critical (≤ 2%): auto-stop stream and finalize local recording. |
| **Insufficient storage** | Stop recording, continue streaming, notify user. |
| **Audio focus loss / incoming call** | Mute microphone and show muted indicator. Resume sending audio only on explicit user action (unmute button). |

---

## 12. Observability and Diagnostics

### 12.1 Metrics (non-PII)

The following metrics must be tracked internally for HUD display and debug diagnostics. They must not contain PII or credentials.

- Current and target bitrate (video/audio).
- Current fps and dropped frame count.
- Encoder init success/failure count.
- Reconnect attempt count and success/failure ratio.
- Thermal level transitions.
- Storage write errors.
- FGS start success/failure events.
- Permission denial events.

### 12.2 HUD

The streaming HUD must display: live bitrate, fps, resolution, session duration, connection state (live/reconnecting/stopped), recording state (on/off), and a thermal warning badge when quality has been degraded.

### 12.3 Debug Logging

- Structured logging via Logcat only in debug builds.
- All secrets must be redacted in every log level (debug and release).
- Production logs must be minimal and rate-limited.

### 12.4 Health Checks

- The connection test endpoint should use a lightweight probe (e.g., RTMP handshake only, or HEAD/OPTIONS where applicable).
- Timeouts for connection test must be capped (default: 10 seconds).
- Test result must be surfaced to the user with actionable messaging (success, timeout, auth failure, TLS error).

---

## 13. Build & Project Structure

```
app/
├── build.gradle.kts
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml
│   │   ├── kotlin/com/port80/app/
│   │   │   ├── App.kt                          // Application class + Hilt
│   │   │   ├── MainActivity.kt                 // Single activity
│   │   │   ├── navigation/
│   │   │   │   └── AppNavGraph.kt
│   │   │   ├── ui/
│   │   │   │   ├── stream/
│   │   │   │   │   ├── StreamScreen.kt
│   │   │   │   │   └── StreamViewModel.kt
│   │   │   │   ├── settings/
│   │   │   │   │   ├── EndpointScreen.kt
│   │   │   │   │   ├── VideoAudioSettingsScreen.kt
│   │   │   │   │   ├── GeneralSettingsScreen.kt
│   │   │   │   │   └── SettingsViewModel.kt
│   │   │   │   └── components/
│   │   │   │       ├── CameraPreview.kt         // AndroidView wrapper
│   │   │   │       ├── StreamHud.kt
│   │   │   │       └── PermissionHandler.kt
│   │   │   ├── service/
│   │   │   │   ├── StreamingService.kt          // Foreground service
│   │   │   │   └── ConnectionManager.kt
│   │   │   ├── camera/
│   │   │   │   ├── DeviceCapabilityQuery.kt     // Interface: resolution/FPS enumeration
│   │   │   │   └── Camera2CapabilityQuery.kt    // Implementation via CameraManager
│   │   │   ├── audio/
│   │   │   │   └── AudioSourceManager.kt
│   │   │   ├── overlay/
│   │   │   │   ├── OverlayManager.kt            // Interface
│   │   │   │   └── NoOpOverlayManager.kt
│   │   │   ├── data/
│   │   │   │   ├── SettingsRepository.kt
│   │   │   │   ├── EndpointProfileRepository.kt
│   │   │   │   └── model/
│   │   │   │       ├── StreamConfig.kt
│   │   │   │       ├── EndpointProfile.kt
│   │   │   │       └── StreamState.kt
│   │   │   └── di/
│   │   │       ├── AppModule.kt
│   │   │       └── StreamModule.kt
│   │   └── res/
│   │       ├── values/
│   │       │   ├── strings.xml
│   │       │   └── themes.xml
│   │       └── drawable/
│   └── test/                                     // Unit tests
│   └── androidTest/                              // Instrumented tests
├── gradle/
│   └── libs.versions.toml                        // Version catalog
└── settings.gradle.kts
```

---

## 14. Dependencies (Gradle Version Catalog)

```toml
[versions]
kotlin = "2.0.x"
agp = "8.7.x"
rootencoder = "2.7.x"
compose-bom = "2025.03.xx"
hilt = "2.51"
datastore = "1.1.x"
security-crypto = "1.1.0-alpha07"
navigation-compose = "2.8.x"
lifecycle = "2.8.x"
coroutines = "1.9.x"
acra = "5.11.x"

[libraries]
rootencoder-rtmp = { module = "com.github.pedroSG94.RootEncoder:rtmp", version.ref = "rootencoder" }
rootencoder-extra = { module = "com.github.pedroSG94.RootEncoder:extra", version.ref = "rootencoder" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
security-crypto = { module = "androidx.security:security-crypto", version.ref = "security-crypto" }
acra-http = { module = "ch.acra:acra-http", version.ref = "acra" }
acra-dialog = { module = "ch.acra:acra-dialog", version.ref = "acra" }
```

> **Removed:** CameraX dependencies (`camerax-core`, `camerax-camera2`, `camerax-lifecycle`). Camera is managed exclusively by RootEncoder's `RtmpCamera2`. See §6.3.

---

## 15. Testing Strategy

| Layer | Approach |
|---|---|
| **ViewModel** | JUnit 5 + Turbine (StateFlow testing). Mock repositories and service binding. |
| **Repository** | JUnit 5 + Robolectric for DataStore and EncryptedSharedPreferences tests. |
| **ConnectionManager** | Unit test reconnection logic, backoff timing, jitter, Doze-aware retry capping. |
| **DeviceCapabilityQuery** | Instrumented tests on real devices (API 23 + latest). |
| **Streaming E2E** | Manual test matrix: 3 devices (low-end API 23, mid-range API 28, flagship API 35) × (RTMP, RTMPS) × (video+audio, video-only, audio-only). |
| **Lifecycle** | Instrumented tests for process death with active service, activity recreation, preview rebind, FGS start restrictions. |
| **UI** | Compose UI tests for navigation, control states, and notification action handling. |

---

## 16. Release & Signing

- **Debug builds:** Auto-signed with debug keystore.
- **Release builds:** Signed with a release keystore stored outside the repo. Keystore path and passwords provided via `local.properties` or CI environment variables.
- **ProGuard / R8:** Enabled for release builds. RootEncoder ProGuard rules included.
- **App Bundle:** `.aab` format for Play Store. `.apk` for sideloading.

### 16.1 Build Flavors (F-Droid Compatibility)

To guarantee F-Droid acceptance, the project uses Gradle product flavors:

```kotlin
// app/build.gradle.kts
android {
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
            // Excludes ALL proprietary / GMS dependencies
        }
        create("gms") {
            dimension = "distribution"
            // May include Google Play Services in the future if needed
        }
    }
}
```

| Flavor | Play Store | F-Droid | Direct APK | GMS allowed |
|---|---|---|---|---|
| `gms` | Yes | No | Yes | Yes |
| `foss` | Yes | Yes | Yes | **No** |

**Rule:** No `com.google.android.gms`, Firebase, or proprietary library may appear in the `foss` dependency tree. CI must verify this via a `./gradlew :app:dependencies --configuration fossReleaseRuntimeClasspath | grep -i gms` check that fails the build if any match is found.

---

## 17. Phased Implementation Plan

### Phase 1 — Core Streaming (MVP)
- [ ] Project scaffolding (Gradle, Hilt, Compose, Navigation)
- [ ] Camera preview via RootEncoder `RtmpCamera2` (back camera default)
- [ ] Basic RTMP streaming (video + audio) via RootEncoder
- [ ] Start / stop controls
- [ ] Single RTMP endpoint input (URL + stream key)
- [ ] Foreground service for background streaming (with FGS start restriction compliance)
- [ ] Runtime permissions handling
- [ ] Activity ↔ service binding with `StateFlow<StreamState>`

### Phase 2 — Settings & Configuration
- [ ] Video settings screen (resolution, FPS, bitrate, keyframe interval — all filtered by `MediaCodecInfo`)
- [ ] Audio settings screen (bitrate, sample rate, channels)
- [ ] Camera switching (front ↔ back)
- [ ] Stream mode selection (video+audio / video-only / audio-only)
- [ ] Orientation lock (portrait / landscape)
- [ ] Encrypted credential storage via EncryptedSharedPreferences
- [ ] Save default endpoint; endpoint profiles

### Phase 3 — Resilience & Polish
- [ ] RTMPS (TLS) support with transport security enforcement (§9.2)
- [ ] Username/password authentication (with RTMPS-or-warn enforcement)
- [ ] Adaptive bitrate with device-capability ABR ladder
- [ ] Auto-reconnect with exponential backoff + jitter and Doze awareness
- [ ] Connection test button (obeys transport rules)
- [ ] Streaming HUD (bitrate, FPS, duration, status, thermal badge)
- [ ] Mute toggle
- [ ] Low battery handling
- [ ] Audio focus / interruption handling (SL-08)
- [ ] Thermal throttling response with cooldown (SL-07)

### Phase 4 — Local Recording & Extras
- [ ] Local MP4 recording (MediaStore/SAF on API 29+, app-specific storage on 23–28)
- [ ] Notification controls (start/stop/mute) with debounce and reconnect cancellation
- [ ] ACRA crash reporting with credential redaction
- [ ] Process death recovery (preview rebind, state restore)
- [ ] OEM battery optimization guidance flow

### Phase 5 — Future (Deferred)
- [ ] Overlay pipeline implementation (text, timestamps, watermarks)
- [ ] H.265 streaming option
- [ ] Multi-destination streaming
- [ ] Stream scheduling
- [ ] SRT protocol option

---

## 18. Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| RootEncoder API breaking changes | Build failure | Pin to specific version; monitor releases. |
| RootEncoder Camera2 quirks on low-end / OEM devices | Black preview, crashes | Use `DeviceCapabilityQuery` to validate before selecting resolution/fps. Test on diversified device set. File upstream issues. |
| RTMPS certificate validation failures | Cannot connect to some endpoints | Strictly enforce standard CA validation. No custom `X509TrustManager`. Users needing self-signed certs must install them into the Android system trust store. Document this in a help screen. |
| Background streaming killed by OEM battery optimization | Stream drops | Guide user to disable battery optimization for the app; use `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` where appropriate. Show a one-time setup guide for Samsung/Xiaomi/Huawei. |
| Large APK size from RootEncoder native libs | User drop-off | Use App Bundle; split by ABI. Target < 15 MB. |
| Thermal throttling causes frame drops / encoder crash | Stuttering stream, ANR | Monitor thermals via `OnThermalStatusChangedListener` (API 29+). Progressive degradation with 60 s cooldown. See SL-07. |
| OEM battery optimization kills foreground service | Silent stream death | Do not silently restart. Notify user on next launch. Use `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; display OEM-specific guidance. |
| FGS start restrictions (API 31+) | Cannot start streaming from background | FGS starts only from user-initiated actions while activity is foregrounded or via notification action. Auto-reconnect operates within an already-running FGS only. |
| Encoder does not support requested config | Crash or silent failure on stream start | Pre-flight validate against `MediaCodecInfo` before connecting. Fail fast with actionable suggestion. |
| F-Droid build rejected due to proprietary dependencies | Cannot distribute on F-Droid | Use Gradle product flavors (`foss` / `gms`). CI verifies no GMS in `foss` dependency tree. See §16.1. |

---

## 19. Resolved Decisions

| # | Question | Decision |
|---|---|---|
| 1 | App name & package ID | **StreamCaster** / `com.port80.app` |
| 2 | Icon / branding | Minimal geometric: camera lens + broadcast signal arcs. Primary: #E53935 (red), Accent: #1E88E5 (blue), Dark surface: #121212. |
| 3 | Distribution | **All:** Google Play Store (`.aab`), F-Droid, direct APK (`.apk` via GitHub Releases). |
| 4 | Monetization | **Free.** No ads, no in-app purchases. |
| 5 | Crash reporting | **ACRA** (open-source, Apache 2.0). Reports via HTTP to self-hosted endpoint or email. F-Droid compatible, zero third-party tracking. Credential redaction required. |
| 6 | minSdk | **API 23** (raised from 21). Required for EncryptedSharedPreferences, reliable Keystore, and runtime permissions. Drops ~1% of active devices with no viable workaround. |
| 7 | Camera framework | **RootEncoder `RtmpCamera2` exclusively.** CameraX removed to avoid surface contention. Camera1 path removed (unnecessary with minSdk 23). |
| 8 | Transport security default | **RTMPS enforced when auth/keys are present.** RTMP with credentials requires explicit per-attempt user opt-in. |

---

## 20. Acceptance Criteria

The following criteria are testable conditions that must pass before the corresponding feature is considered complete.

| # | Criterion |
|---|---|
| AC-01 | FGS start succeeds only via user action (in-app button or notification action). Attempting background auto-start without user affordance is blocked and surfaced to the user. |
| AC-02 | Auto-reconnect honors Doze: retries do not exceed one attempt per minute while the device is in idle mode, unless the user has exempted the app from battery optimizations. |
| AC-03 | Switching from 720p30 to 480p15 on `THERMAL_STATUS_SEVERE` restarts the encoder without crash. Stream resumes within 3 seconds. |
| AC-04 | Credentials are stored encrypted on API ≥ 23 via EncryptedSharedPreferences. No plaintext fallback exists. |
| AC-05 | Notification stop action cancels in-flight reconnect and leaves stream stopped. No zombie notifications remain after the service stops. |
| AC-06 | Connection test with auth over `rtmp://` prompts a plaintext warning dialog. Credentials are transmitted only after explicit user confirmation. |
| AC-07 | ACRA crash reports do not contain stream keys, passwords, or auth headers in any `ReportField`. |
| AC-08 | After process death with the service still running, relaunching the activity rebinds to the service, restores preview, and reflects live stats within 2 seconds. |
| AC-09 | If the OS kills the FGS, the next activity launch shows a session-ended message. No silent restart occurs. |
| AC-10 | Local recording on API 29+ uses MediaStore or SAF. If the user has not granted storage access, recording fails fast with a user prompt; streaming is not blocked. |
| AC-11 | On incoming phone call, the app mutes the microphone and displays a muted indicator. Audio resumes only on explicit user unmute. |
| AC-12 | Camera revocation in background switches to audio-only. Returning to foreground re-acquires camera and resumes video with an IDR frame. |

---

## 21. Open Questions

| # | Question | Impact |
|---|---|---|
| OQ-01 | Do we permit any tablet or landscape-only devices, or is phone portrait-first the only supported UX? | Affects layout validation, device test matrix, and orientation handling. |
| OQ-02 | Should there be a maximum session duration to limit heat and battery risk on low-end devices? | Affects UX (forced stop) and thermal strategy. |
