# Technical Implementation Plan — StreamCaster Android

**Generated:** 2026-03-14
**Source:** SPECIFICATION.md v2.0 (Hardened)
**Package:** `com.port80.app`

---

## 1. Delivery Assumptions

### Team Assumptions
- **3–5 contributors** working in parallel (Android engineers + 1 QA/DevOps).
- Each contributor can own an independent vertical slice.
- No dedicated security engineer; security requirements are embedded in every task with peer review gates.

### Scope Assumptions
- Phase 1 (Core Streaming MVP) and Phase 2 (Settings & Configuration) are the primary delivery targets.
- Phase 3 (Resilience & Polish) follows immediately; Phase 4 is secondary.
- Phase 5 (Overlay implementation, H.265, SRT, multi-destination) is explicitly out of scope.
- The `foss` build flavor must compile and pass CI from day one, even if the `gms` flavor adds nothing yet.

### API/Device Constraints Affecting Execution
- **minSdk 23 / targetSdk 35 / compileSdk 35**: every API-conditional path must branch on `Build.VERSION.SDK_INT`.
- FGS start restrictions (API 31+): `startForegroundService()` legal only from foreground user action. Notification "Start" must deep-link to Activity, not call `startForegroundService()`.
- `FOREGROUND_SERVICE_CAMERA` and `FOREGROUND_SERVICE_MICROPHONE` permissions required from API 34 as `<uses-permission>` entries.
- `POST_NOTIFICATIONS` runtime permission required from API 33.
- `android:foregroundServiceType="camera|microphone"` required from API 30 on the `<service>` element.
- EncryptedSharedPreferences requires API 23+ (guaranteed by minSdk).
- `OnThermalStatusChangedListener` available only on API 29+. API 23–28 must use `BatteryManager.EXTRA_TEMPERATURE` via `ACTION_BATTERY_CHANGED`.
- OEM battery killers (Samsung, Xiaomi, Huawei) are assumed hostile.

---

## 2. Architecture Baseline (Implementation View)

### Core Modules to Implement First (in order)
1. **Data layer contracts** — `StreamState`, `EndpointProfile`, `StreamConfig` sealed classes/data classes.
2. **Repository interfaces** — `SettingsRepository`, `EndpointProfileRepository`.
3. **Service interface contract** — `StreamingServiceControl` exposed via bound service.
4. **DI module skeleton** — Hilt modules providing all dependencies.
5. **StreamingService** — FGS owning RootEncoder, authoritative state source.
6. **StreamViewModel** — binds to service, reflects state to UI.
7. **UI screens** — Compose screens consuming ViewModel StateFlows.

### Source-of-Truth Boundaries
| Boundary | Owner | Consumers |
|---|---|---|
| Stream state (`StreamState`) | `StreamingService` via `StateFlow` | `StreamViewModel` → UI |
| User settings | `SettingsRepository` (DataStore) | `SettingsViewModel`, `StreamingService` |
| Credentials & profiles | `EndpointProfileRepository` (EncryptedSharedPreferences) | `StreamingService` (reads at connect time) |
| Device capabilities | `DeviceCapabilityQuery` (read-only Camera2 + MediaCodecList) | `SettingsViewModel` (UI filtering) |
| Encoder quality changes | `EncoderController` (Mutex-serialized) | ABR system, Thermal system |

### Contract Surfaces Between Layers

```
UI Layer (Compose)
    │ observes StateFlow<StreamState>
    │ calls StreamViewModel actions (startStream, stopStream, mute, switchCamera)
    ▼
StreamViewModel
    │ binds to StreamingService via ServiceConnection
    │ delegates all mutations to service
    ▼
StreamingService (FGS)
    │ owns RtmpCamera2, ConnectionManager, EncoderController
    │ reads credentials from EndpointProfileRepository
    │ reads config from SettingsRepository
    │ exposes StateFlow<StreamState>, StateFlow<StreamStats>
    ▼
RootEncoder (RtmpCamera2)
    │ Camera2 capture → H.264 encode → RTMP mux → network
    ▼
ConnectionManager
    │ RTMP connect/disconnect, reconnect with backoff
    │ driven by ConnectivityManager.NetworkCallback + timer
```

### Module/Package Layout

```
com.port80.app/
├── App.kt                           // @HiltAndroidApp
├── MainActivity.kt                  // Single Activity, orientation lock
├── navigation/
│   └── AppNavGraph.kt               // Compose NavHost
├── ui/
│   ├── stream/
│   │   ├── StreamScreen.kt          // Camera preview + HUD + controls
│   │   └── StreamViewModel.kt       // Service binding, state projection
│   ├── settings/
│   │   ├── EndpointScreen.kt        // RTMP URL, key, auth, profiles
│   │   ├── VideoAudioSettingsScreen.kt
│   │   ├── GeneralSettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   └── components/
│       ├── CameraPreview.kt         // AndroidView wrapping SurfaceView for RootEncoder
│       ├── StreamHud.kt             // Bitrate, FPS, duration, thermal badge
│       └── PermissionHandler.kt     // Runtime permission orchestration
├── service/
│   ├── StreamingService.kt          // FGS: owns RtmpCamera2, state machine
│   ├── StreamingServiceControl.kt   // Interface exposed to ViewModel
│   ├── ConnectionManager.kt         // RTMP connect/reconnect logic
│   ├── EncoderController.kt         // Mutex-serialized quality changes
│   └── NotificationController.kt    // FGS notification + action intents
├── camera/
│   ├── DeviceCapabilityQuery.kt     // Interface
│   └── Camera2CapabilityQuery.kt    // Implementation
├── audio/
│   └── AudioSourceManager.kt
├── thermal/
│   ├── ThermalMonitor.kt            // Interface
│   ├── ThermalStatusMonitor.kt      // API 29+ impl
│   └── BatteryTempMonitor.kt        // API 23-28 impl
├── overlay/
│   ├── OverlayManager.kt            // Interface
│   └── NoOpOverlayManager.kt
├── data/
│   ├── SettingsRepository.kt
│   ├── EndpointProfileRepository.kt
│   └── model/
│       ├── StreamState.kt
│       ├── StreamConfig.kt
│       ├── EndpointProfile.kt
│       ├── StreamStats.kt
│       └── StopReason.kt
├── crash/
│   ├── AcraConfigurator.kt
│   └── CredentialSanitizer.kt       // URL/key redaction transformer
└── di/
    ├── AppModule.kt
    └── StreamModule.kt
```

### Data Contracts (Shared Across All Agents)

```kotlin
// --- StreamState.kt ---
enum class StopReason {
    USER_REQUEST, ERROR_ENCODER, ERROR_AUTH, ERROR_CAMERA,
    THERMAL_CRITICAL, BATTERY_CRITICAL
}

sealed class StreamState {
    data object Idle : StreamState()
    data object Connecting : StreamState()
    data class Live(val cameraActive: Boolean = true) : StreamState()
    data class Reconnecting(val attempt: Int, val nextRetryMs: Long) : StreamState()
    data object Stopping : StreamState()
    data class Stopped(val reason: StopReason) : StreamState()
}

// --- StreamStats.kt ---
data class StreamStats(
    val videoBitrateKbps: Int = 0,
    val audioBitrateKbps: Int = 0,
    val fps: Float = 0f,
    val droppedFrames: Long = 0,
    val resolution: String = "",
    val durationMs: Long = 0,
    val isRecording: Boolean = false,
    val thermalLevel: ThermalLevel = ThermalLevel.NORMAL,
    val isMuted: Boolean = false
)

enum class ThermalLevel { NORMAL, MODERATE, SEVERE, CRITICAL }

// --- EndpointProfile.kt ---
data class EndpointProfile(
    val id: String,          // UUID
    val name: String,
    val rtmpUrl: String,     // rtmp:// or rtmps://
    val streamKey: String,
    val username: String?,
    val password: String?,
    val isDefault: Boolean = false
)

// --- StreamConfig.kt ---
data class StreamConfig(
    val profileId: String,
    val videoEnabled: Boolean = true,
    val audioEnabled: Boolean = true,
    val resolution: Resolution = Resolution(1280, 720),
    val fps: Int = 30,
    val videoBitrateKbps: Int = 2500,
    val audioBitrateKbps: Int = 128,
    val audioSampleRate: Int = 44100,
    val stereo: Boolean = true,
    val keyframeIntervalSec: Int = 2,
    val abrEnabled: Boolean = true,
    val localRecordingEnabled: Boolean = false,
    val recordingTreeUri: String? = null
)

data class Resolution(val width: Int, val height: Int) {
    override fun toString() = "${width}x${height}"
}
```

---

## 3. Work Breakdown Structure (WBS)

| Task ID | Title | Objective | Scope (In/Out) | Inputs | Deliverables | Dependencies | Parallelizable | Owner Profile | Effort | Risk Level | Verification Command | Files/Packages Likely Touched |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| T-001 | Project Scaffolding & Gradle Setup | Create buildable project skeleton with Kotlin DSL, Hilt, Compose, version catalog, product flavors (`foss`/`gms`), ABI splits | In: Gradle config, manifest, Hilt app class, empty Activity. Out: any feature code | Spec §2, §13, §14, §16.1 | Compiling empty app with both flavors | None | Yes | DevOps/Android | M (2d) | Low | `./gradlew assembleFossDebug assembleGmsDebug` | `build.gradle.kts`, `settings.gradle.kts`, `libs.versions.toml`, `AndroidManifest.xml`, `App.kt`, `MainActivity.kt` |
| T-002 | Data Models & Interface Contracts | Define all shared data classes, sealed classes, and repository/service interfaces | In: StreamState, StopReason, EndpointProfile, StreamConfig, StreamStats, ThermalLevel, all interfaces. Out: implementations | Spec §6.2, §4 | Compilable `data/model/` package + interfaces | None | Yes | Android | S (1d) | Low | `./gradlew compileDebugKotlin` | `data/model/*`, interfaces in `service/`, `camera/`, `overlay/`, `thermal/` |
| T-003 | Hilt DI Modules | Wire all interface bindings, provide Context-dependent singletons (DataStore, EncryptedSharedPreferences, ConnectivityManager, PowerManager) | In: module providers. Out: actual impl injection | Spec §2, §6.2 | `AppModule.kt`, `StreamModule.kt` | T-001, T-002 | No | Android | S (1d) | Low | `./gradlew compileDebugKotlin` | `di/AppModule.kt`, `di/StreamModule.kt` |
| T-004 | SettingsRepository (DataStore) | Implement non-sensitive settings persistence using Jetpack DataStore Preferences | In: resolution, fps, bitrate prefs, orientation, ABR toggle, camera default, reconnect config, battery threshold. Out: credential storage | Spec §4.2–4.5, §6.2 | `SettingsRepository.kt` impl + unit tests | T-002, T-003 | Yes (after T-002) | Android | S (1d) | Low | `./gradlew testFossDebugUnitTest --tests '*SettingsRepo*'` | `data/SettingsRepository.kt` |
| T-005 | EndpointProfileRepository (Encrypted) | CRUD for RTMP endpoint profiles with EncryptedSharedPreferences. No plaintext fallback. Handle Keystore restore failures | In: profile CRUD, encryption. Out: RTMP connection logic | Spec §4.4, §9.1, §9.2 | `EndpointProfileRepository.kt` impl + unit tests | T-002, T-003 | Yes (after T-002) | Android/Security | M (2d) | Medium | `./gradlew testFossDebugUnitTest --tests '*EndpointProfile*'` | `data/EndpointProfileRepository.kt` |
| T-006 | DeviceCapabilityQuery | Query CameraManager + MediaCodecList for supported resolutions, fps, codec profiles/levels. Read-only — never opens camera | In: capability enumeration. Out: camera open/preview | Spec §5.3, §6.2, §8.1 | `DeviceCapabilityQuery.kt` interface + `Camera2CapabilityQuery.kt` impl | T-002, T-003 | Yes (after T-002) | Android | M (2d) | Medium | `./gradlew connectedFossDebugAndroidTest --tests '*Capability*'` | `camera/DeviceCapabilityQuery.kt`, `camera/Camera2CapabilityQuery.kt` |
| T-007 | StreamingService (FGS Core) | Implement FGS with correct type declarations, lifecycle, RtmpCamera2 ownership, StateFlow emission, service binding | In: FGS lifecycle, state machine, RtmpCamera2 init. Out: reconnect, ABR, thermal, recording | Spec §6.2, §7.1, §7.2, §9.1 | `StreamingService.kt` | T-002, T-003, T-005 | No | Android | L (4d) | High | `./gradlew testFossDebugUnitTest --tests '*StreamingService*'` | `service/StreamingService.kt`, `AndroidManifest.xml` |
| T-008 | NotificationController | FGS notification with state display (Live, Reconnecting, Paused, Stopped), action buttons (Stop, Mute/Unmute), deep-link Start, debounce. No zombie notifications | In: notification management. Out: notification appearance design | Spec §7.1, §7.4, §4.6 SL-04 | `NotificationController.kt` | T-007 | No | Android | M (2d) | Medium | `./gradlew testFossDebugUnitTest --tests '*Notification*'` | `service/NotificationController.kt` |
| T-009 | ConnectionManager (RTMP Connect/Reconnect) | RTMP/RTMPS connect, disconnect, auto-reconnect with exponential backoff + jitter, Doze awareness, ConnectivityManager.NetworkCallback integration | In: connection lifecycle. Out: ABR, thermal triggers | Spec §4.6 SL-02, §11, §3 (App Standby) | `ConnectionManager.kt` + unit tests | T-002, T-007 | No | Android | L (3d) | High | `./gradlew testFossDebugUnitTest --tests '*ConnectionManager*'` | `service/ConnectionManager.kt` |
| T-010 | StreamViewModel & Service Binding | ViewModel binds to StreamingService, projects StreamState + StreamStats as Compose-observable StateFlows, preview surface management with WeakReference/CompletableDeferred, idempotent commands | In: ViewModel. Out: UI composables | Spec §6.2, §7.2, §7.3 | `StreamViewModel.kt` + unit tests | T-002, T-007 | No | Android | M (2d) | Medium | `./gradlew testFossDebugUnitTest --tests '*StreamViewModel*'` | `ui/stream/StreamViewModel.kt` |
| T-011 | Camera Preview Composable | AndroidView wrapping SurfaceView, SurfaceHolder.Callback driving CompletableDeferred, surface lifecycle management, no strong View references across config change | In: preview UI. Out: HUD, controls | Spec §4.1 MC-03, §7.3 | `CameraPreview.kt` | T-010 | No | Android | M (2d) | Medium | Manual: preview appears on device | `ui/components/CameraPreview.kt` |
| T-012 | Stream Screen UI (Controls + HUD) | Start/Stop button, mute button, camera-switch button, status badge, recording indicator, HUD overlay (bitrate, fps, duration, connection status, thermal badge). Landscape-first layout with portrait variant | In: Compose UI. Out: settings screens | Spec §10.1, §10.3 | `StreamScreen.kt`, `StreamHud.kt` | T-010, T-011 | No | Android | M (2d) | Low | Compose UI test: `./gradlew connectedFossDebugAndroidTest --tests '*StreamScreen*'` | `ui/stream/StreamScreen.kt`, `ui/components/StreamHud.kt` |
| T-013 | Runtime Permissions Handler | Compose-friendly permission flow for CAMERA, RECORD_AUDIO, POST_NOTIFICATIONS (API 33+). Rationale dialogs, denial handling, mode fallback | In: permission UI. Out: — | Spec §9.4, §9.5 | `PermissionHandler.kt` | T-001 | Yes | Android | S (1d) | Low | `./gradlew connectedFossDebugAndroidTest --tests '*Permission*'` | `ui/components/PermissionHandler.kt` |
| T-014 | Orientation Lock Logic | Lock orientation in `Activity.onCreate()` before `setContentView()` from persisted pref. Unconditional lock when stream is active. Unlock only when idle | In: orientation. Out: — | Spec §4.1 MC-04 | `MainActivity.kt` orientation handling | T-004 | Yes | Android | S (0.5d) | Low | Instrumented test: rotation during stream doesn't restart | `MainActivity.kt` |
| T-015 | Settings Screens (Video/Audio/General) | Resolution picker (filtered by DeviceCapabilityQuery), fps picker, bitrate controls, audio settings, ABR toggle, camera default, orientation, reconnect config, battery threshold, media mode selection | In: UI. Out: — | Spec §4.2, §4.3, §10.1 | `VideoAudioSettingsScreen.kt`, `GeneralSettingsScreen.kt`, `SettingsViewModel.kt` | T-004, T-006 | Yes (after T-004) | Android | M (2d) | Low | `./gradlew testFossDebugUnitTest --tests '*SettingsViewModel*'` | `ui/settings/*` |
| T-016 | Endpoint Setup Screen | RTMP URL input, stream key, username/password, Test Connection button, Save as Default, profile CRUD list. Transport security warnings (§9.2) | In: UI. Out: — | Spec §4.4, §9.2 | `EndpointScreen.kt` | T-005, T-015 | Yes (after T-005) | Android | M (2d) | Medium | `./gradlew testFossDebugUnitTest --tests '*Endpoint*'` | `ui/settings/EndpointScreen.kt` |
| T-017 | Compose Navigation | NavHost with routes: Stream, EndpointSetup, VideoAudioSettings, GeneralSettings. Single-activity | In: navigation. Out: — | Spec §10.2 | `AppNavGraph.kt` | T-012, T-015, T-016 | No | Android | S (0.5d) | Low | App launches and navigates all routes | `navigation/AppNavGraph.kt` |
| T-018 | RTMPS & Transport Security Enforcement | RTMPS via system TrustManager. Warn+confirm dialog for credentials over plaintext RTMP. Connection test obeys same rules. No custom X509TrustManager | In: transport security. Out: — | Spec §9.2, §5 NF-08 | Security logic in `ConnectionManager` + UI warning dialog | T-009, T-016 | No | Android/Security | M (2d) | High | Unit test: auth over rtmp:// triggers warning. No custom TrustManager in codebase | `service/ConnectionManager.kt`, `ui/settings/EndpointScreen.kt` |
| T-019 | Adaptive Bitrate (ABR) System | ABR ladder definition per device capabilities, bitrate-only reduction first, resolution/fps step-down via EncoderController, recovery on bandwidth improvement. Encoder backpressure detection (§8.1) | In: ABR. Out: — | Spec §4.5, §8.2, §8.3, §8.4 | ABR logic in `EncoderController` + config | T-006, T-007 | No | Android | L (3d) | High | `./gradlew testFossDebugUnitTest --tests '*ABR*' --tests '*EncoderController*'` | `service/EncoderController.kt` |
| T-020 | EncoderController (Mutex-Serialized Quality Changes) | Single component serializing all encoder re-init requests from ABR + thermal systems via coroutine Mutex. Controlled restart sequence: stop preview → release encoder → reconfigure → restart → IDR | In: encoder control. Out: — | Spec §8.2, §8.3 | `EncoderController.kt` | T-007 | No | Android | M (2d) | High | `./gradlew testFossDebugUnitTest --tests '*EncoderController*'` | `service/EncoderController.kt` |
| T-021 | Thermal Monitoring & Response | API 29+: `OnThermalStatusChangedListener`. API 23–28: `BatteryManager.EXTRA_TEMPERATURE`. Progressive degradation with 60s cooldown. Critical → graceful stop | In: thermal handling. Out: — | Spec §4.6 SL-07, §5 NF-09 | `ThermalMonitor.kt`, `ThermalStatusMonitor.kt`, `BatteryTempMonitor.kt` | T-002, T-020 | Yes (after T-020) | Android | M (2d) | High | `./gradlew testFossDebugUnitTest --tests '*Thermal*'` | `thermal/*` |
| T-022 | Audio Focus & Interruption Handling | Register `AudioManager.OnAudioFocusChangeListener`. On focus loss: mute mic, show indicator. Resume only on explicit user unmute | In: audio focus. Out: — | Spec §4.6 SL-08, §11 | Audio focus logic in `StreamingService` | T-007 | No | Android | S (1d) | Medium | `./gradlew testFossDebugUnitTest --tests '*AudioFocus*'` | `service/StreamingService.kt`, `audio/AudioSourceManager.kt` |
| T-023 | Low Battery Handling | Monitor battery level. Configurable warning threshold (default 5%). Auto-stop at ≤ 2%. Finalize local recording | In: battery. Out: — | Spec §4.6 SL-05, §11 | Battery logic in `StreamingService` | T-007 | Yes (after T-007) | Android | S (1d) | Low | `./gradlew testFossDebugUnitTest --tests '*Battery*'` | `service/StreamingService.kt` |
| T-024 | Background Camera Revocation Handling | Detect camera revocation in background. Switch to audio-only (or placeholder frame). Show "Camera paused" in notification. Re-acquire on foreground return with IDR | In: camera revocation. Out: — | Spec §4.6 SL-06, §11 | Camera revocation logic in `StreamingService` | T-007, T-008 | No | Android | M (2d) | High | Instrumented test: revoke camera permission in background, verify audio continues | `service/StreamingService.kt` |
| T-025 | Local MP4 Recording | Tee encoded buffers to MP4 muxer (no second encoder). API 29+: SAF picker + `takePersistableUriPermission()`. API 23–28: `getExternalFilesDir`. Fail fast if no storage grant, don't block streaming | In: recording. Out: — | Spec §4.1 MC-05 | Recording logic in `StreamingService` | T-007 | No | Android | M (2d) | Medium | Manual: record and verify MP4 playback | `service/StreamingService.kt` |
| T-026 | ACRA Crash Reporting with Credential Redaction | Configure ACRA. Exclude `SHARED_PREFERENCES`, `LOGCAT` in release. URL sanitization transformer. HTTPS enforcement for report transport. Unit test verifying redaction | In: crash reporting. Out: — | Spec §9.3 | `AcraConfigurator.kt`, `CredentialSanitizer.kt` + unit tests | T-001 | Yes | Android/Security | M (2d) | High | `./gradlew testFossDebugUnitTest --tests '*Sanitizer*' --tests '*Acra*'` | `crash/AcraConfigurator.kt`, `crash/CredentialSanitizer.kt` |
| T-027 | Process Death Recovery | On activity recreation with surviving service: rebind, restore preview via CompletableDeferred surface-ready gate, reflect live stats. If service dead: show Stopped. No automatic stream resumption | In: process death. Out: — | Spec §7.3 | Logic in `StreamViewModel` + `CameraPreview` | T-010, T-011 | No | Android | M (2d) | High | Instrumented test: kill activity process, verify rebind | `ui/stream/StreamViewModel.kt`, `ui/components/CameraPreview.kt` |
| T-028 | Connection Test Button | Lightweight RTMP handshake probe. 10s timeout cap. Obeys transport security rules. Actionable result messaging (success, timeout, auth failure, TLS error) | In: connection test. Out: — | Spec §4.4 EP-07, §12.4 | Connection test in `ConnectionManager` + UI | T-009, T-018 | No | Android | S (1d) | Medium | `./gradlew testFossDebugUnitTest --tests '*ConnectionTest*'` | `service/ConnectionManager.kt`, `ui/settings/EndpointScreen.kt` |
| T-029 | Mute Toggle | Stop sending audio data during stream. HUD muted indicator. Notification mute/unmute action | In: mute. Out: — | Spec §4.3 AS-05 | Mute logic in `StreamingService` + UI | T-007, T-008, T-012 | Yes (after T-007) | Android | S (0.5d) | Low | Manual: mute during stream, verify silence | `service/StreamingService.kt`, `ui/stream/StreamScreen.kt` |
| T-030 | Camera Switching | Front ↔ back via `RtmpCamera2.switchCamera()` before and during stream | In: camera switch. Out: — | Spec §4.1 MC-02 | Camera switch in `StreamingService` + UI | T-007, T-012 | Yes (after T-007) | Android | S (0.5d) | Low | Manual: switch camera during stream | `service/StreamingService.kt`, `ui/stream/StreamScreen.kt` |
| T-031 | Prolonged Session Monitor | On low-end devices (`isLowRamDevice` or < 2GB RAM), warn after configurable duration (default 90 min). Suppress if charging | In: session monitor. Out: — | Spec §11 (Prolonged session) | Logic in `StreamingService` | T-007, T-023 | Yes (after T-007) | Android | S (0.5d) | Low | `./gradlew testFossDebugUnitTest --tests '*SessionMonitor*'` | `service/StreamingService.kt` |
| T-032 | Manifest Hardening & Backup Rules | `android:allowBackup="false"` or BackupAgent excluding encrypted prefs. All FGS type declarations, permissions. Credential re-entry prompt on restore failure | In: manifest. Out: — | Spec §9.1, §9.4, §7.1 | `AndroidManifest.xml` hardening | T-001 | Yes | Security | S (0.5d) | Medium | Grep for `allowBackup`, `foregroundServiceType`, all required permissions | `AndroidManifest.xml` |
| T-033 | F-Droid / FOSS Flavor CI Verification | CI step: `./gradlew :app:dependencies --configuration fossReleaseRuntimeClasspath | grep -i gms` must return empty. ABI split config for `foss` | In: CI. Out: — | Spec §16.1 | CI workflow file | T-001 | Yes | DevOps | S (0.5d) | Low | `./gradlew :app:dependencies --configuration fossReleaseRuntimeClasspath \| grep -i gms` returns 0 matches | `.github/workflows/`, `build.gradle.kts` |
| T-034 | ProGuard / R8 Rules | R8 config for release builds. RootEncoder ProGuard rules. EncryptedSharedPreferences keep rules. ACRA keep rules | In: obfuscation. Out: — | Spec §16 | `proguard-rules.pro` | T-001, T-026 | Yes | DevOps | S (0.5d) | Low | `./gradlew assembleFossRelease` succeeds | `proguard-rules.pro` |
| T-035 | QA Test Matrix & Acceptance Test Suite | Manual test scripts for E2E matrix (3 devices × transports × modes). Automated acceptance tests for AC-01 through AC-19 where possible | In: QA. Out: — | Spec §15, §20 | Test plan + instrumented tests | T-007 through T-031 | No | QA | L (3d) | Medium | `./gradlew connectedFossDebugAndroidTest` | `androidTest/` |
| T-036 | Intent Security — No Credentials in Extras | FGS start Intent carries only profile ID. Service fetches credentials from EndpointProfileRepository. Verify via test that no key/password appears in Intent | In: security. Out: — | Spec §9.1, AC-13 | Enforcement in `StreamingService` start path | T-005, T-007 | No | Security | S (0.5d) | High | `./gradlew testFossDebugUnitTest --tests '*IntentSecurity*'` | `service/StreamingService.kt`, `ui/stream/StreamViewModel.kt` |
| T-037 | Overlay Architecture Stub | `OverlayManager` interface with `onDrawFrame(canvas: GlCanvas)`. `NoOpOverlayManager` default. Hilt-provided | In: interface + no-op. Out: actual rendering | Spec §4.7 | `OverlayManager.kt`, `NoOpOverlayManager.kt` | T-002, T-003 | Yes | Android | S (0.5d) | Low | Compiles | `overlay/*` |
| T-038 | Structured Logging & Secret Redaction | Wrap all log calls through a redacting logger. URL sanitization pattern: `rtmp[s]?://([^/\s]+/[^/\s]+)/\S+` → masked. No secrets at any log level | In: logging. Out: — | Spec §9.3, §12.3 | Logging utility | T-001 | Yes | Security | S (1d) | Medium | `./gradlew testFossDebugUnitTest --tests '*Logging*' --tests '*Redact*'` | `crash/CredentialSanitizer.kt` (shared with ACRA) |

---

## 4. Dependency Graph and Parallel Execution Lanes

### Dependency DAG (Text Form)

```
Stage 0 (Foundation — No Dependencies):
  T-001: Project Scaffolding
  T-002: Data Models & Interfaces

Stage 1 (Core Infra — Depends on Stage 0):
  T-003: Hilt DI Modules        [T-001, T-002]
  T-013: Permissions Handler     [T-001]
  T-026: ACRA Crash Reporting    [T-001]
  T-032: Manifest Hardening      [T-001]
  T-033: F-Droid CI              [T-001]
  T-037: Overlay Stub            [T-002, T-003]
  T-038: Structured Logging      [T-001]

Stage 2 (Data + Capabilities — Depends on Stage 1):
  T-004: SettingsRepository      [T-002, T-003]
  T-005: EndpointProfileRepo     [T-002, T-003]
  T-006: DeviceCapabilityQuery   [T-002, T-003]

Stage 3 (Service Core — Critical Path):
  T-007: StreamingService FGS    [T-002, T-003, T-005]  ← CRITICAL PATH
  T-014: Orientation Lock        [T-004]
  T-015: Settings Screens        [T-004, T-006]

Stage 4 (Service Features — Depends on Service Core):
  T-008: NotificationController  [T-007]
  T-009: ConnectionManager       [T-002, T-007]           ← CRITICAL PATH
  T-010: StreamViewModel         [T-002, T-007]           ← CRITICAL PATH
  T-020: EncoderController       [T-007]
  T-022: Audio Focus             [T-007]
  T-023: Low Battery             [T-007]
  T-029: Mute Toggle             [T-007, T-008, T-012]
  T-030: Camera Switching        [T-007, T-012]
  T-036: Intent Security         [T-005, T-007]

Stage 5 (UI + Advanced Features):
  T-011: Camera Preview          [T-010]
  T-016: Endpoint Screen         [T-005, T-015]
  T-018: RTMPS & Transport Sec.  [T-009, T-016]
  T-019: ABR System              [T-006, T-007]
  T-021: Thermal Monitoring      [T-002, T-020]
  T-024: Camera Revocation       [T-007, T-008]
  T-025: Local MP4 Recording     [T-007]
  T-031: Prolonged Session       [T-007, T-023]

Stage 6 (Integration + Polish):
  T-012: Stream Screen UI        [T-010, T-011]
  T-017: Compose Navigation      [T-012, T-015, T-016]
  T-027: Process Death Recovery   [T-010, T-011]
  T-028: Connection Test Button   [T-009, T-018]
  T-034: ProGuard/R8             [T-001, T-026]

Stage 7 (Validation):
  T-035: QA Test Matrix          [all above]
```

### Critical Path
```
T-001 → T-003 → T-005 → T-007 → T-009 → T-018 → T-028
                          ↓
                         T-010 → T-011 → T-012 → T-017
                          ↓
                         T-027 (Process Death Recovery)
```

### Mermaid Dependency Graph

```mermaid
graph TD
  T001[T-001: Scaffolding] --> T003[T-003: Hilt DI]
  T002[T-002: Data Models] --> T003
  T001 --> T013[T-013: Permissions]
  T001 --> T026[T-026: ACRA]
  T001 --> T032[T-032: Manifest]
  T001 --> T033[T-033: F-Droid CI]
  T001 --> T038[T-038: Logging]
  T002 --> T037[T-037: Overlay Stub]
  T003 --> T037
  T003 --> T004[T-004: SettingsRepo]
  T003 --> T005[T-005: EndpointProfileRepo]
  T003 --> T006[T-006: DeviceCapQuery]
  T005 --> T007[T-007: StreamingService]
  T002 --> T007
  T003 --> T007
  T004 --> T014[T-014: Orientation Lock]
  T004 --> T015[T-015: Settings Screens]
  T006 --> T015
  T007 --> T008[T-008: NotificationController]
  T007 --> T009[T-009: ConnectionManager]
  T007 --> T010[T-010: StreamViewModel]
  T007 --> T020[T-020: EncoderController]
  T007 --> T022[T-022: Audio Focus]
  T007 --> T023[T-023: Low Battery]
  T007 --> T025[T-025: Local Recording]
  T007 --> T030[T-030: Camera Switching]
  T005 --> T036[T-036: Intent Security]
  T007 --> T036
  T010 --> T011[T-011: Camera Preview]
  T005 --> T016[T-016: Endpoint Screen]
  T015 --> T016
  T009 --> T018[T-018: RTMPS Security]
  T016 --> T018
  T006 --> T019[T-019: ABR System]
  T007 --> T019
  T020 --> T021[T-021: Thermal Monitor]
  T007 --> T024[T-024: Camera Revocation]
  T008 --> T024
  T023 --> T031[T-031: Prolonged Session]
  T010 --> T012[T-012: Stream Screen UI]
  T011 --> T012
  T008 --> T029[T-029: Mute Toggle]
  T012 --> T029
  T012 --> T030
  T012 --> T017[T-017: Navigation]
  T015 --> T017
  T016 --> T017
  T010 --> T027[T-027: Process Death Recovery]
  T011 --> T027
  T009 --> T028[T-028: Connection Test]
  T018 --> T028
  T001 --> T034[T-034: ProGuard]
  T026 --> T034
  T017 --> T035[T-035: QA Matrix]
  T027 --> T035
  T028 --> T035

  style T007 fill:#e53935,color:#fff
  style T009 fill:#e53935,color:#fff
  style T010 fill:#e53935,color:#fff
  style T020 fill:#e53935,color:#fff
```

---

## 5. Agent Handoff Prompts

### Agent Prompt for T-001 — Project Scaffolding & Gradle Setup

**Context:** You are building StreamCaster, a native Android RTMP streaming app. Package: `com.port80.app`. The project starts from an empty workspace at `/android/`.

**Your Task:** Create the complete project skeleton: Gradle Kotlin DSL, version catalog, product flavors (`foss`/`gms`), ABI splits, Hilt application class, empty single-Activity shell, AndroidManifest with all required permissions and FGS declarations, and a Compose theme.

**Input Files/Paths:**
- Workspace root: `/Users/alex/Code/rtmp-client/android/`
- Spec reference: `SPECIFICATION.md` §2, §13, §14, §16, §16.1

**Requirements:**
- `settings.gradle.kts` with project name `StreamCaster`, JitPack repository for RootEncoder.
- `gradle/libs.versions.toml` with all dependency versions per spec §14 (use latest stable releases: Kotlin 2.0.21, AGP 8.7.3, RootEncoder 2.7.0, Compose BOM 2025.03.00, Hilt 2.51, DataStore 1.1.1, security-crypto 1.1.0-alpha07, navigation-compose 2.8.8, lifecycle 2.8.7, coroutines 1.9.0, ACRA 5.11.4).
- `app/build.gradle.kts` with `minSdk = 23`, `targetSdk = 35`, `compileSdk = 35`, Compose enabled, Hilt KSP, product flavors (`foss` dimension `distribution`, `gms` dimension `distribution`), ABI splits for `foss` variant (`arm64-v8a`, `armeabi-v7a`), `isUniversalApk = false`.
- `AndroidManifest.xml` with: `android:allowBackup="false"`, all permissions from spec §9.4, `<service android:name=".service.StreamingService" android:foregroundServiceType="camera|microphone" android:exported="false" />`.
- `App.kt`: `@HiltAndroidApp` Application class.
- `MainActivity.kt`: `@AndroidEntryPoint` single Activity with `enableEdgeToEdge()`, empty `setContent { }` Compose surface.
- Material 3 theme in `ui/theme/` (`Theme.kt`, `Color.kt`, `Type.kt`) using brand colors: primary #E53935, accent #1E88E5, dark surface #121212.
- Empty `di/AppModule.kt` and `di/StreamModule.kt` Hilt modules.

**Success Criteria:**
- `./gradlew assembleFossDebug` compiles successfully.
- `./gradlew assembleGmsDebug` compiles successfully.
- `./gradlew :app:dependencies --configuration fossReleaseRuntimeClasspath | grep -i gms` returns zero matches.
- App launches on an emulator showing an empty Compose screen.

---

### Agent Prompt for T-002 — Data Models & Interface Contracts

**Context:** You are building StreamCaster (`com.port80.app`), an Android RTMP streaming app. Architecture is MVVM with Hilt, Jetpack Compose, RootEncoder for camera/streaming. The service layer owns all stream state.

**Your Task:** Define all shared data classes, sealed classes, enums, and interface contracts that will be used across the entire codebase. These contracts must compile independently and serve as the API surface for all parallel work.

**Input Files/Paths:**
- Package root: `app/src/main/kotlin/com/port80/app/`
- Target subpackages: `data/model/`, `service/`, `camera/`, `overlay/`, `thermal/`

**Requirements:**
Create the following files with full Kotlin code:
1. `data/model/StreamState.kt` — `sealed class StreamState` with: `Idle`, `Connecting`, `Live(cameraActive: Boolean)`, `Reconnecting(attempt: Int, nextRetryMs: Long)`, `Stopping`, `Stopped(reason: StopReason)`. `enum class StopReason`: `USER_REQUEST`, `ERROR_ENCODER`, `ERROR_AUTH`, `ERROR_CAMERA`, `THERMAL_CRITICAL`, `BATTERY_CRITICAL`.
2. `data/model/StreamStats.kt` — `data class StreamStats` with: `videoBitrateKbps`, `audioBitrateKbps`, `fps`, `droppedFrames`, `resolution`, `durationMs`, `isRecording`, `thermalLevel`, `isMuted`. `enum class ThermalLevel`: `NORMAL`, `MODERATE`, `SEVERE`, `CRITICAL`.
3. `data/model/EndpointProfile.kt` — `data class EndpointProfile` with: `id` (UUID string), `name`, `rtmpUrl`, `streamKey`, `username?`, `password?`, `isDefault`.
4. `data/model/StreamConfig.kt` — `data class StreamConfig` and `data class Resolution(width, height)`.
5. `service/StreamingServiceControl.kt` — Interface with: `val streamState: StateFlow<StreamState>`, `val streamStats: StateFlow<StreamStats>`, `fun startStream(profileId: String)`, `fun stopStream()`, `fun toggleMute()`, `fun switchCamera()`, `fun attachPreviewSurface(holder: SurfaceHolder)`, `fun detachPreviewSurface()`.
6. `service/ReconnectPolicy.kt` — Interface: `fun nextDelayMs(attempt: Int): Long`, `fun shouldRetry(attempt: Int): Boolean`, `fun reset()`.
7. `camera/DeviceCapabilityQuery.kt` — Interface: `fun getSupportedResolutions(cameraId: String): List<Resolution>`, `fun getSupportedFps(cameraId: String): List<Int>`, `fun isResolutionFpsSupported(res: Resolution, fps: Int): Boolean`, `fun getAvailableCameras(): List<CameraInfo>`.
8. `data/SettingsRepository.kt` — Interface for all non-sensitive settings (read/write via DataStore Preferences Flow).
9. `data/EndpointProfileRepository.kt` — Interface: `fun getAll(): Flow<List<EndpointProfile>>`, `fun getById(id: String): EndpointProfile?`, `fun save(profile: EndpointProfile)`, `fun delete(id: String)`, `fun getDefault(): EndpointProfile?`.
10. `overlay/OverlayManager.kt` — Interface: `fun onDrawFrame(canvas: Any)` (placeholder type for GlCanvas).
11. `thermal/ThermalMonitor.kt` — Interface: `val thermalLevel: StateFlow<ThermalLevel>`, `fun start()`, `fun stop()`.

**Success Criteria:**
- `./gradlew compileDebugKotlin` succeeds with all files.
- No circular dependencies between packages.
- All return types and parameter types are fully specified (no `Any` except the GlCanvas placeholder).

---

### Agent Prompt for T-007 — StreamingService (Foreground Service Core)

**Context:** You are building the core foreground service for StreamCaster (`com.port80.app`). This service is the single source of truth for stream state. It owns the RootEncoder `RtmpCamera2` instance, manages camera/audio capture, and exposes state via `StateFlow`. Architecture is MVVM with Hilt. The service runs independently of the Activity lifecycle.

**Your Task:** Implement `StreamingService` as an Android foreground service.

**Input Files/Paths:**
- `app/src/main/kotlin/com/port80/app/service/StreamingService.kt`
- Interfaces: `service/StreamingServiceControl.kt`, `data/model/StreamState.kt`, `data/model/StreamStats.kt`
- Repository: `data/EndpointProfileRepository.kt` (inject via Hilt)

**Requirements:**
- Annotate with `@AndroidEntryPoint`. Declare `android:foregroundServiceType="camera|microphone"` (in manifest, not in code).
- Implement `StreamingServiceControl` interface.
- On `startStream(profileId)`: fetch credentials from `EndpointProfileRepository` (never from Intent extras — Intent carries only profile ID string as an extra). Validate encoder config via `MediaCodecInfo` pre-flight. Initialize `RtmpCamera2`. Connect RTMP.
- State machine: `Idle → Connecting → Live → Stopping → Stopped`. Transitions via `MutableStateFlow<StreamState>`. All command methods are idempotent (e.g., calling `stopStream()` from `Idle` is a no-op).
- `startPreview()` must not be called until a `SurfaceHolder` is attached via `attachPreviewSurface()`. Gate using `CompletableDeferred<SurfaceHolder>`.
- Emit `StreamStats` updates at 1 Hz from RootEncoder callbacks.
- On `stopStream()`: disconnect RTMP, release encoder, stop camera, transition to `Stopped(USER_REQUEST)`.
- FGS notification: delegate to `NotificationController` (can be a minimal stub initially).
- On `onDestroy()`: ensure full cleanup — release camera, encoder, RTMP connection.
- `onStartCommand` returns `START_NOT_STICKY` (do not silently restart).
- Bind/unbind via `onBind()` returning a `Binder` that exposes `StreamingServiceControl`.

**Success Criteria:**
- Service compiles with Hilt injection.
- Unit tests verify state transitions (Idle→Connecting→Live→Stopped).
- No credentials appear in Intent extras or logs.
- `./gradlew testFossDebugUnitTest --tests '*StreamingService*'` passes.

---

### Agent Prompt for T-009 — ConnectionManager (Reconnect Logic)

**Context:** StreamCaster (`com.port80.app`) needs robust RTMP reconnection. The app streams over hostile mobile networks where drops are frequent. Auto-reconnect must work within an already-running FGS (no new FGS starts from background). Doze mode and App Standby Buckets restrict background network access.

**Your Task:** Implement `ConnectionManager` handling RTMP connect/disconnect and auto-reconnect.

**Input Files/Paths:**
- `app/src/main/kotlin/com/port80/app/service/ConnectionManager.kt`
- Interface: `service/ReconnectPolicy.kt`

**Requirements:**
- Inject `ConnectivityManager` via Hilt. Register `NetworkCallback` for network availability events.
- Implement `ReconnectPolicy` with exponential backoff + jitter: base 3s, multiplier 2x, cap 60s. Jitter: ±20% of computed delay. Configurable max retry count (default: unlimited).
- On network drop: emit `StreamState.Reconnecting(attempt, nextRetryMs)`. Start backoff timer.
- On `ConnectivityManager.NetworkCallback.onAvailable()`: attempt reconnect immediately (override timer).
- Doze awareness: suppress timer-based retries while device is in Doze (detect via `PowerManager.isDeviceIdleMode`). Reconnect fires on `onAvailable()` which aligns with Doze maintenance windows.
- On explicit user `stopStream()`: cancel ALL pending reconnect attempts (coroutine job cancellation). Clear retry counter. No zombie retries.
- On auth failure (RTMP 401/403 equivalent): do NOT retry. Transition to `Stopped(ERROR_AUTH)`.
- Thread safety: all reconnect state mutations via a `Mutex` or confined to a single coroutine dispatcher.
- Expose `connectionState: StateFlow<ConnectionState>` with states: `Disconnected`, `Connecting`, `Connected`, `Reconnecting(attempt)`.

**Success Criteria:**
- Unit tests for: backoff timing sequence, jitter bounds, max retry cap, user-stop cancels retries, auth failure stops retries, Doze suppression, `onAvailable()` immediate retry.
- `./gradlew testFossDebugUnitTest --tests '*ConnectionManager*'` passes.

---

### Agent Prompt for T-020 — EncoderController (Mutex-Serialized Quality Changes)

**Context:** StreamCaster has two systems that can trigger encoder restarts: the ABR system (network congestion) and the thermal monitoring system (device overheating). Both can fire simultaneously. Concurrent `MediaCodec.release()`/`configure()`/`start()` calls will crash with `IllegalStateException`. All quality changes must be serialized.

**Your Task:** Implement `EncoderController` as the single point of control for all encoder quality changes.

**Input Files/Paths:**
- `app/src/main/kotlin/com/port80/app/service/EncoderController.kt`
- References: `data/model/StreamConfig.kt`, `data/model/StreamStats.kt`

**Requirements:**
- Use a coroutine `Mutex` to serialize all quality-change requests.
- Two entry points: `requestAbrChange(newBitrateKbps: Int, newResolution: Resolution?, newFps: Int?)` and `requestThermalChange(newResolution: Resolution, newFps: Int)`.
- Bitrate-only changes (no resolution/fps change): apply directly to RootEncoder's `setVideoBitrateOnFly()` — no encoder restart, no cooldown.
- Resolution or FPS changes requiring encoder restart: execute the 5-step sequence from spec §8.3 (stop preview → release encoder → reconfigure → restart → send IDR). Target ≤ 3s stream gap.
- Thermal-triggered resolution/fps changes: enforce 60-second cooldown between restart operations. If a thermal request arrives within 60s of the last thermal restart, queue it (apply after cooldown expires). ABR bitrate-only changes bypass the cooldown entirely.
- ABR resolution/fps changes are also subject to the 60s cooldown timer.
- Emit the current effective quality via `StateFlow<EffectiveQuality>` (resolution, fps, bitrate).
- Handle encoder restart failures: if reconfigure fails, try one step lower on the ABR ladder. If that also fails, emit `StreamState.Stopped(ERROR_ENCODER)`.

**Success Criteria:**
- Unit tests: concurrent ABR + thermal requests don't crash, cooldown is enforced for restarts, bitrate-only changes bypass cooldown, restart failure falls back to lower quality.
- `./gradlew testFossDebugUnitTest --tests '*EncoderController*'` passes.

---

## 6. Sprint / Milestone Plan

### Milestone 1: Foundation (Days 1–3)
**Goal:** Buildable project skeleton with all interfaces defined, compiling on both flavors.

**Entry Criteria:** Empty workspace, spec finalized.

**Exit Criteria:**
- `./gradlew assembleFossDebug assembleGmsDebug` pass.
- All data models and interface contracts compile.
- Hilt DI modules wire correctly.
- Manifest contains all permissions and FGS declarations.
- F-Droid flavor CI check passes.

**Tasks:** T-001, T-002, T-003, T-013, T-032, T-033, T-037, T-038

**Risks:** RootEncoder Gradle dependency resolution from JitPack may be flaky. **Rollback:** pin exact commit hash instead of version tag.

---

### Milestone 2: Data + Capabilities (Days 3–5)
**Goal:** Persistent settings, encrypted credential storage, and device capability enumeration working.

**Entry Criteria:** Milestone 1 complete.

**Exit Criteria:**
- SettingsRepository reads/writes all preferences.
- EndpointProfileRepository encrypts/decrypts profile data.
- DeviceCapabilityQuery returns valid camera and codec info on test devices.
- Unit tests pass for all three components.

**Tasks:** T-004, T-005, T-006, T-014

**Risks:** EncryptedSharedPreferences may behave differently on various OEM devices. **Rollback:** document device-specific quirks; no plaintext fallback.

---

### Milestone 3: Core Streaming (Days 5–10)
**Goal:** End-to-end streaming works: camera preview, RTMP connect, live stream, stop.

**Entry Criteria:** Milestone 2 complete.

**Exit Criteria:**
- StreamingService starts, captures camera + mic, streams to RTMP endpoint.
- StreamViewModel binds and reflects state.
- Camera preview visible in Compose UI.
- Start/Stop functional from UI.
- FGS notification shows with basic state.

**Tasks:** T-007, T-008, T-009, T-010, T-011, T-012, T-036

**Risks:** RootEncoder `RtmpCamera2` integration may require debugging camera HAL quirks. **Rollback:** test on emulator first, then physical devices.

---

### Milestone 4: Settings & Configuration UI (Days 8–12)
**Goal:** All settings screens functional, navigation complete, endpoint profiles savable.

**Entry Criteria:** T-004, T-005, T-006 complete.

**Exit Criteria:**
- Video/Audio/General settings screens render with device-filtered options.
- Endpoint setup screen saves profiles with encrypted credentials.
- Navigation between all screens works.

**Tasks:** T-015, T-016, T-017

**Risks:** Low risk. **Rollback:** N/A.

---

### Milestone 5: Resilience (Days 10–16)
**Goal:** Auto-reconnect, ABR, thermal handling, transport security, all failure modes handled.

**Entry Criteria:** Milestone 3 complete.

**Exit Criteria:**
- Auto-reconnect survives network drops with correct backoff + Doze awareness.
- ABR ladder steps down on congestion, recovers on bandwidth improvement.
- Thermal monitoring triggers degradation on API 29+ and API 23–28.
- RTMPS enforced with credentials. Plaintext warning dialog functional.
- Mute/unmute, camera switching, audio focus all work mid-stream.

**Tasks:** T-018, T-019, T-020, T-021, T-022, T-023, T-024, T-025, T-028, T-029, T-030, T-031

**Risks:** EncoderController concurrent access is highest-risk item. **Rollback:** fall back to single-threaded command queue if Mutex approach introduces deadlocks.

---

### Milestone 6: Hardening (Days 14–18)
**Goal:** Security audit, process death recovery, crash reporting, release readiness.

**Entry Criteria:** Milestones 3–5 functionally complete.

**Exit Criteria:**
- Process death with surviving service: activity rebinds, preview restores, stats reflect.
- ACRA reports contain zero credential occurrences (verified by unit test).
- No credentials in Intent extras (verified by test).
- `android:allowBackup="false"` confirmed.
- ProGuard/R8 release build succeeds.
- API 31+ FGS start restrictions verified on emulator.
- Thermal stress test on physical device passes.
- All AC-01 through AC-19 acceptance criteria verified.

**Tasks:** T-026, T-027, T-034, T-035

**Risks:** Process death recovery is fragile across OEM devices. **Rollback:** document known unsupported devices; ensure graceful degradation (show idle state rather than crash).

---

## 7. Detailed Task Playbooks

### Playbook 1: T-007 — StreamingService (FGS Core)

**Why this task is risky:**
The FGS is the architectural spine. Incorrect lifecycle management causes ANRs, silent stream death, or OS-forced kills. API 31+ FGS start restrictions add complexity. RootEncoder integration is the biggest unknowns surface.

**Implementation Steps:**
1. Create `StreamingService` extending `Service`, annotated `@AndroidEntryPoint`.
2. Define `MutableStateFlow<StreamState>` initialized to `Idle` and `MutableStateFlow<StreamStats>`.
3. Implement `onStartCommand()`: extract `profileId` from Intent extra. Fetch `EndpointProfile` from repository. Validate it exists. Call `startForeground()` with notification from `NotificationController`. Return `START_NOT_STICKY`.
4. Implement `startStream()`: validate encoder config via `MediaCodecInfo.CodecCapabilities` pre-flight. Instantiate `RtmpCamera2` with the app's `SurfaceView`. Configure resolution, fps, bitrate, audio settings. Connect RTMP. Transition state: `Idle → Connecting → Live`.
5. Implement `stopStream()`: disconnect RTMP. Release encoder. Stop camera. Cancel reconnect jobs. Transition to `Stopped(USER_REQUEST)`. Call `stopForeground(STOP_FOREGROUND_REMOVE)`, then `stopSelf()`.
6. Implement `onBind()`: return a `Binder` inner class exposing `this as StreamingServiceControl`.
7. Implement RootEncoder callbacks: `onConnectionSuccess`, `onConnectionFailed`, `onDisconnect`, `onAuthError`, `onNewBitrate`. Map each to `StreamState` transitions.
8. Implement `attachPreviewSurface()`/`detachPreviewSurface()`: complete `CompletableDeferred<SurfaceHolder>`. On attach, call `rtmpCamera2.startPreview(surfaceView)`. On detach, call `rtmpCamera2.stopPreview()`. Do NOT retain strong `SurfaceHolder` reference across config changes.
9. Stats collection: launch a coroutine in `serviceScope` that polls RootEncoder metrics every 1 second and emits to `StreamStats` StateFlow.
10. `onDestroy()`: full cleanup — stop stream if active, release all resources, cancel all coroutines.

**Edge Cases and Failure Modes:**
- `startForeground()` must be called within 10 seconds of `onCreate()` on API 31+, or the system throws `ForegroundServiceStartNotAllowedException`.
- If the `profileId` doesn't match any stored profile (e.g., deleted after Intent was created), fail fast with `Stopped(ERROR_AUTH)`.
- Camera may be unavailable if another app holds it; catch `CameraAccessException` and offer audio-only.
- Encoder may not support the requested config; pre-flight catch prevents this.

**Verification Strategy:**
- Unit tests (mocked RootEncoder): state transitions, idempotent commands, no credentials in Intent.
- Instrumented tests: FGS starts and shows notification, survives activity destruction.
- Manual: stream to test RTMP server, verify HUD stats, background the app and verify stream continues.

**Definition of Done:**
- Service starts, streams, stops without crash.
- State machine transitions are correct for all paths.
- No credentials in Intent extras or logs.
- Unit tests pass. FGS notification visible.

---

### Playbook 2: T-009 — ConnectionManager (Auto-Reconnect)

**Why this task is risky:**
Network behavior is unpredictable. Doze mode, App Standby Buckets, and OEM battery killers can all interfere with retry timers. A reconnect loop that doesn't properly cancel can drain battery or zombie the FGS.

**Implementation Steps:**
1. Create `ConnectionManager` class injected with `ConnectivityManager`, `PowerManager`, `CoroutineScope` (service-scoped).
2. Implement `ExponentialBackoffReconnectPolicy`: `nextDelayMs(attempt) = min(baseMs * 2^attempt + jitter, capMs)` where `jitter = Random.nextLong(-0.2 * delay, 0.2 * delay)`.
3. Register `ConnectivityManager.NetworkCallback` in `start()`. On `onAvailable()`: if state is `Reconnecting`, immediately attempt reconnect (cancel pending timer).
4. On `onLost()`: if state is `Connected`, transition to `Reconnecting(0, nextDelay)`. Start backoff timer using `delay()` in a coroutine.
5. Doze check: before each timer-based retry, check `PowerManager.isDeviceIdleMode`. If true, skip the retry (don't burn backoff steps). Wait for `onAvailable()` instead.
6. On successful reconnect: reset retry counter. Transition to `Connected`.
7. On auth failure: do NOT retry. Transition to `Stopped(ERROR_AUTH)`. Cancel all pending jobs.
8. On explicit `stop()`: cancel the retry job, unregister `NetworkCallback`, reset state.
9. Thread safety: confine all state mutations to a single `Dispatchers.Default` coroutine with `Mutex`.

**Edge Cases and Failure Modes:**
- `onAvailable()` may fire before `onLost()` on network handoff (WiFi→cellular). Handle by checking if already connected.
- On API 28+, app may be in `RARE` standby bucket, restricting network. First connect attempt after FGS start may fail. Retry on `onAvailable()`.
- If user toggles airplane mode rapidly, multiple `onAvailable`/`onLost` events fire. Debounce with 500ms delay.
- Doze `onAvailable()` aligns with maintenance windows (~every 15 min). Tolerate gaps.

**Verification Strategy:**
- Unit tests: mock `ConnectivityManager` and `PowerManager`. Verify backoff sequence (3, 6, 12, 24, 48, 60, 60...). Verify jitter bounds. Verify auth failure stops retries. Verify user stop cancels retries. Verify Doze skips timer retries.
- Instrumented: toggle airplane mode during stream. Verify reconnect within expected time.
- Manual: stream over LTE, walk into dead zone, return. Verify auto-reconnect.

**Definition of Done:**
- All unit tests pass.
- Reconnect loop does not leak coroutine jobs after `stop()`.
- Auth failure terminates retries immediately.
- Doze-aware behavior verified in test.

---

### Playbook 3: T-020 — EncoderController (Mutex-Serialized Quality Changes)

**Why this task is risky:**
Concurrent encoder restarts from ABR and thermal systems cause `IllegalStateException` crashes. The 60-second thermal cooldown adds timing complexity. Encoder restart must complete within 3 seconds to avoid viewer-visible gaps.

**Implementation Steps:**
1. Create `EncoderController` with a `Mutex` and a `CoroutineScope`.
2. Track `lastThermalRestartTime: Long` for cooldown enforcement.
3. Implement `requestAbrChange(bitrateKbps, resolution?, fps?)`:
   - Acquire `mutex.withLock { }`.
   - If only bitrate changed: call `rtmpCamera2.setVideoBitrateOnFly(bitrateKbps)`. No restart. No cooldown. Return.
   - If resolution or fps changed: check cooldown (same timer for thermal AND ABR restarts). If within 60s, queue the request. If outside 60s, execute restart sequence. Update `lastRestartTime`.
4. Implement `requestThermalChange(resolution, fps)`:
   - Acquire `mutex.withLock { }`.
   - Check cooldown. If within 60s of last restart, queue. Otherwise, execute restart sequence. Update `lastThermalRestartTime`.
5. Restart sequence (inside Mutex lock):
   a. `rtmpCamera2.stopPreview()`
   b. `rtmpCamera2.stopStream()` (video track only if possible, else full)
   c. Reconfigure encoder with new resolution/fps/bitrate.
   d. `rtmpCamera2.startPreview(surface)`
   e. `rtmpCamera2.startStream(url)` — reconnect with new params.
   f. Request IDR frame.
   g. If reconfigure fails: try one ABR step lower. If that fails: emit `Stopped(ERROR_ENCODER)`.
6. Expose `StateFlow<EffectiveQuality>` reflecting current resolution/fps/bitrate.
7. Implement cooldown queue processing: launch a coroutine that, after cooldown expires, dequeues and applies the latest pending request (coalescing multiple requests into one).

**Edge Cases and Failure Modes:**
- If `mutex.withLock` is held while RootEncoder callbacks fire on a different thread, ensure no deadlock by using `Dispatchers.Default` for the Mutex scope and not calling Mutex-guarded code from within RootEncoder callbacks.
- Encoder restart may take longer than 3s on low-end devices with MediaTek SoCs. Log a warning but don't time out — let it complete.
- If the app receives `THERMAL_STATUS_CRITICAL` during an encoder restart, abort the restart and go straight to `Stopped(THERMAL_CRITICAL)`.

**Verification Strategy:**
- Unit tests: fire ABR and thermal requests concurrently (via `launch` + `delay`). Verify no concurrent restart. Verify cooldown enforcement. Verify bitrate-only bypasses cooldown. Verify restart failure fallback.
- Instrumented: simulate thermal event during ABR step-down. Verify no crash.

**Definition of Done:**
- Concurrent requests serialized without crash.
- Cooldown timer prevents rapid oscillation.
- Bitrate-only changes are instant (no restart).
- Failed restart falls back gracefully.

---

### Playbook 4: T-026 — ACRA Crash Reporting with Credential Redaction

**Why this task is risky:**
RootEncoder logs full RTMP URLs (including stream keys) at `Log.d`/`Log.i` internally. If ACRA captures logcat, credentials leak into crash reports sent over the network. The sanitization must be bulletproof.

**Implementation Steps:**
1. Add ACRA dependencies (`acra-http`, `acra-dialog`) to version catalog and `build.gradle.kts`.
2. Create `CredentialSanitizer`:
   - Regex: `rtmp[s]?://([^/\s]+/[^/\s]+)/\S+` → `rtmp[s]://<host>/<app>/****`
   - Also match: `streamKey=`, `key=`, `password=`, `auth=` query params and mask values.
   - Function: `fun sanitize(input: String): String`
3. Create `AcraConfigurator` in `App.kt`:
   - Configure `@AcraHttpSender` with HTTPS-only endpoint.
   - Set `reportContent` to explicitly list only safe fields: `STACK_TRACE`, `ANDROID_VERSION`, `APP_VERSION_CODE`, `APP_VERSION_NAME`, `PHONE_MODEL`, `BRAND`, `PRODUCT`, `CUSTOM_DATA`, `CRASH_CONFIGURATION`, `BUILD_CONFIG`, `USER_COMMENT`.
   - **Exclude** `SHARED_PREFERENCES`, `LOGCAT`, `DUMPSYS_MEMINFO`, `THREAD_DETAILS` from all configurations.
   - Register a custom `ReportTransformer` that runs `CredentialSanitizer.sanitize()` on every string-valued field before serialization.
4. In `App.kt`, only initialize ACRA in release builds (check `BuildConfig.DEBUG`).
5. If user configures `http://` ACRA endpoint: show warning dialog, require opt-in. Never send silently over plaintext.
6. Write unit test:
   - Create a mock crash report with a known stream key string (e.g., `rtmp://ingest.example.com/live/my_secret_stream_key_12345`).
   - Run through `CredentialSanitizer.sanitize()` and all field processing.
   - Assert zero occurrences of `my_secret_stream_key_12345` in the output.
   - Assert the output contains `****`.

**Edge Cases and Failure Modes:**
- RootEncoder may log URLs in non-standard formats. Test with: `rtmp://host/app/key`, `rtmps://host/app/key?auth=token`, `rtmp://user:pass@host/app/key`.
- If ACRA initialization fails (e.g., misconfigured endpoint), the app must still launch without crashing. Wrap in try-catch.
- Ensure ProGuard/R8 doesn't strip the ACRA annotations or transformer class.

**Verification Strategy:**
- Unit test: `CredentialSanitizer` with all URL variants.
- Unit test: synthetic crash report contains zero secrets.
- Manual: trigger a crash in debug build, inspect the report for leaked secrets.

**Definition of Done:**
- `CredentialSanitizer` handles all known URL patterns.
- ACRA config excludes `LOGCAT` and `SHARED_PREFERENCES`.
- Unit test for zero-secret output passes.
- Release builds only.

---

### Playbook 5: T-027 — Process Death Recovery

**Why this task is risky:**
Process death with a surviving FGS is a race condition minefield. The new Activity must rebind to the service, re-attach the preview surface, and reflect live stats — without calling `startPreview()` on a dead `Surface`. The `CompletableDeferred<SurfaceHolder>` pattern is the critical synchronization primitive.

**Implementation Steps:**
1. In `StreamViewModel`: on `init`, attempt to bind to `StreamingService`. If bind succeeds, collect `streamState` and `streamStats` StateFlows. If bind fails (service not running), set state to `Idle`.
2. In `StreamViewModel`: expose `surfaceReady: CompletableDeferred<SurfaceHolder>` (recreated on each new Activity lifecycle).
3. In `CameraPreview` composable (`AndroidView`):
   - On `SurfaceHolder.Callback.surfaceCreated()`: call `viewModel.onSurfaceReady(holder)` which completes the `CompletableDeferred`.
   - On `surfaceDestroyed()`: call `viewModel.onSurfaceDestroyed()` which resets the `CompletableDeferred` and calls `service.detachPreviewSurface()`.
4. In `StreamViewModel`: when a new surface is ready AND the service is in `Live` state, call `service.attachPreviewSurface(holder)`.
5. In `StreamingService.attachPreviewSurface()`: call `rtmpCamera2.startPreview(surfaceView)` only after verifying the surface is valid.
6. In `StreamViewModel`: use `WeakReference<SurfaceHolder>` to avoid leaking the View across Activity lifecycle boundaries.
7. Handle the "service killed" case: in `ServiceConnection.onServiceDisconnected()`, set state to `Stopped(USER_REQUEST)`. Clear any stale reconnect state. Show a message to the user.
8. On fresh app start (no service running): state defaults to `Idle`. No automatic stream resumption.

**Edge Cases and Failure Modes:**
- The new Activity's `SurfaceView` may take 100–500ms to create after `setContentView`. `startPreview()` before `surfaceCreated()` → `IllegalArgumentException`. The `CompletableDeferred` gate prevents this.
- On some OEM devices, `surfaceCreated()` fires but the Surface is not yet valid. Add a 1-frame delay (`withContext(Dispatchers.Main) { yield() }`) before calling `startPreview()`.
- If the service is in `Reconnecting` state when the activity rebinds, the ViewModel must show the reconnecting UI, not attempt to override the state.
- Memory pressure: if the ViewModel is recreated (SavedStateHandle), it must re-derive everything from the service, not from saved state.

**Verification Strategy:**
- Instrumented test: start stream → kill activity process via `adb shell am kill com.port80.app` → relaunch → verify preview and stats restore within 2 seconds.
- Unit test: ViewModel correctly gates preview attach on surface readiness.
- Manual: background app, wait 5 minutes, return. Verify preview is live.

**Definition of Done:**
- Process death + service alive: preview rebinds, stats restore, no crash.
- Service dead on activity relaunch: shows Idle, no auto-restart.
- No leaked Surface or View references.

---

## 8. Interface Contracts & Scaffolding

### 8.1 Stream State Model

```kotlin
// File: data/model/StreamState.kt
package com.port80.app.data.model

/**
 * Authoritative stream state, owned exclusively by StreamingService.
 * UI layer observes this as a read-only StateFlow.
 */
sealed class StreamState {
    /** No stream active. Ready to start. */
    data object Idle : StreamState()

    /** RTMP handshake in progress. */
    data object Connecting : StreamState()

    /**
     * Actively streaming.
     * @param cameraActive false when camera has been revoked in background
     */
    data class Live(val cameraActive: Boolean = true) : StreamState()

    /**
     * Network dropped, attempting to reconnect.
     * @param attempt current retry attempt (0-indexed)
     * @param nextRetryMs milliseconds until next retry
     */
    data class Reconnecting(val attempt: Int, val nextRetryMs: Long) : StreamState()

    /** Graceful shutdown in progress. */
    data object Stopping : StreamState()

    /**
     * Stream ended.
     * @param reason why the stream stopped
     */
    data class Stopped(val reason: StopReason) : StreamState()
}

enum class StopReason {
    USER_REQUEST,
    ERROR_ENCODER,
    ERROR_AUTH,
    ERROR_CAMERA,
    THERMAL_CRITICAL,
    BATTERY_CRITICAL
}
```

### 8.2 Service-to-ViewModel State Flow Contract

```kotlin
// File: service/StreamingServiceControl.kt
package com.port80.app.service

import android.view.SurfaceHolder
import com.port80.app.data.model.StreamState
import com.port80.app.data.model.StreamStats
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract exposed by StreamingService to bound clients (ViewModels).
 * All methods are idempotent and safe to call from any state.
 */
interface StreamingServiceControl {

    /** Authoritative stream state. Never null. */
    val streamState: StateFlow<StreamState>

    /** Real-time stream statistics, updated at ~1 Hz. */
    val streamStats: StateFlow<StreamStats>

    /**
     * Start streaming using the given endpoint profile.
     * The service fetches credentials internally — no secrets in this call.
     * No-op if already streaming or connecting.
     *
     * @param profileId ID of the EndpointProfile to use
     * @param config resolved StreamConfig for this session
     */
    fun startStream(profileId: String, config: StreamConfig)

    /**
     * Stop the active stream. Cancels reconnect if in progress.
     * No-op if already stopped or idle.
     */
    fun stopStream()

    /** Toggle audio mute. No-op if no audio track is active. */
    fun toggleMute()

    /** Switch between front and back camera. No-op if video is not active. */
    fun switchCamera()

    /**
     * Attach a preview surface. The service will call startPreview()
     * only after this surface is attached and valid.
     * Safe to call multiple times (replaces previous surface).
     */
    fun attachPreviewSurface(holder: SurfaceHolder)

    /**
     * Detach the preview surface. Called on surfaceDestroyed().
     * The service stops preview rendering but continues streaming.
     */
    fun detachPreviewSurface()
}
```

### 8.3 Reconnect Policy Interface

```kotlin
// File: service/ReconnectPolicy.kt
package com.port80.app.service

/**
 * Defines the retry strategy for RTMP reconnection.
 * Implementations must be thread-safe.
 */
interface ReconnectPolicy {

    /**
     * Compute the delay before the next reconnection attempt.
     * @param attempt 0-indexed attempt number
     * @return delay in milliseconds (includes jitter)
     */
    fun nextDelayMs(attempt: Int): Long

    /**
     * Whether another retry should be attempted.
     * @param attempt 0-indexed current attempt number
     * @return true if retry is allowed
     */
    fun shouldRetry(attempt: Int): Boolean

    /** Reset internal state (e.g., after a successful reconnection). */
    fun reset()
}

/**
 * Exponential backoff with jitter.
 * Sequence: 3s, 6s, 12s, 24s, 48s, 60s, 60s, ...
 * Jitter: ±20% of computed delay.
 *
 * @param baseDelayMs initial delay (default 3000)
 * @param maxDelayMs cap (default 60000)
 * @param maxAttempts max retries, or Int.MAX_VALUE for unlimited
 * @param jitterFactor jitter range as fraction of delay (default 0.2)
 */
class ExponentialBackoffReconnectPolicy(
    private val baseDelayMs: Long = 3_000L,
    private val maxDelayMs: Long = 60_000L,
    private val maxAttempts: Int = Int.MAX_VALUE,
    private val jitterFactor: Double = 0.2
) : ReconnectPolicy {

    override fun nextDelayMs(attempt: Int): Long {
        val exponentialDelay = (baseDelayMs * (1L shl attempt.coerceAtMost(20)))
            .coerceAtMost(maxDelayMs)
        val jitterRange = (exponentialDelay * jitterFactor).toLong()
        val jitter = if (jitterRange > 0) {
            kotlin.random.Random.nextLong(-jitterRange, jitterRange + 1)
        } else 0L
        return (exponentialDelay + jitter).coerceAtLeast(0L)
    }

    override fun shouldRetry(attempt: Int): Boolean = attempt < maxAttempts

    override fun reset() {
        // Stateless — no internal state to reset.
        // Subclasses may track adaptive state.
    }
}
```

### 8.4 Encrypted Profile Repository Interface

```kotlin
// File: data/EndpointProfileRepository.kt
package com.port80.app.data

import com.port80.app.data.model.EndpointProfile
import kotlinx.coroutines.flow.Flow

/**
 * CRUD for RTMP endpoint profiles.
 * All credential fields (streamKey, username, password) are stored
 * encrypted via EncryptedSharedPreferences backed by Android Keystore.
 *
 * The repository never exposes credentials in logs, Intent extras,
 * or any serialized form outside of EncryptedSharedPreferences.
 */
interface EndpointProfileRepository {

    /** Observe all saved profiles. Emits on any change. */
    fun getAll(): Flow<List<EndpointProfile>>

    /** Get a single profile by ID. Returns null if not found. */
    suspend fun getById(id: String): EndpointProfile?

    /** Get the default profile, or null if none set. */
    suspend fun getDefault(): EndpointProfile?

    /**
     * Save or update a profile. If [profile.id] already exists, it is updated.
     * Credentials are encrypted before persistence.
     */
    suspend fun save(profile: EndpointProfile)

    /** Delete a profile by ID. No-op if not found. */
    suspend fun delete(id: String)

    /**
     * Set a profile as the default. Clears the default flag on all others.
     * @param id profile to make default
     */
    suspend fun setDefault(id: String)

    /**
     * Check if the Keystore key is available (i.e., not a post-restore scenario).
     * If false, the caller should prompt the user to re-enter credentials.
     */
    suspend fun isKeystoreAvailable(): Boolean
}
```

### 8.5 Notification Action Intent Factory

```kotlin
// File: service/NotificationController.kt
package com.port80.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.port80.app.MainActivity
import com.port80.app.data.model.StreamState

/**
 * Manages the FGS notification: creation, updates, and action PendingIntents.
 *
 * Design rules (spec §7.1, §7.4):
 * - "Start" action deep-links to Activity (cannot start FGS from background).
 * - "Stop" action sends broadcast to running service (valid on already-running FGS).
 * - "Mute/Unmute" action sends broadcast to running service.
 * - All actions are debounced (≥ 500ms) to prevent double-toggle.
 * - No zombie notifications after service stops.
 */
interface NotificationController {

    /** Create the initial FGS notification. */
    fun createNotification(state: StreamState): Notification

    /** Update the notification to reflect new state. */
    fun updateNotification(state: StreamState)

    /** Cancel the notification (called from onDestroy). */
    fun cancel()

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "stream_service_channel"

        // Action constants for BroadcastReceiver
        const val ACTION_STOP = "com.port80.app.ACTION_STOP_STREAM"
        const val ACTION_TOGGLE_MUTE = "com.port80.app.ACTION_TOGGLE_MUTE"

        /**
         * PendingIntent to open the Activity (used for "Start" action and notification tap).
         * This does NOT call startForegroundService() — it launches the Activity,
         * which can then initiate a stream start from the foreground.
         */
        fun openActivityIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * PendingIntent to stop the stream (broadcast to running service).
         */
        fun stopStreamIntent(context: Context): PendingIntent {
            val intent = Intent(ACTION_STOP).apply {
                setPackage(context.packageName)
            }
            return PendingIntent.getBroadcast(
                context,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * PendingIntent to toggle mute (broadcast to running service).
         */
        fun toggleMuteIntent(context: Context): PendingIntent {
            val intent = Intent(ACTION_TOGGLE_MUTE).apply {
                setPackage(context.packageName)
            }
            return PendingIntent.getBroadcast(
                context,
                2,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
```

---

## 9. Test Strategy Mapped to Tasks

### Unit Tests

| Test ID | Related Tasks | What is Being Proven | Min Environment | Pass/Fail Signal |
|---|---|---|---|---|
| UT-01 | T-004 | SettingsRepository reads/writes all prefs correctly | JVM + Robolectric | All prefs round-trip |
| UT-02 | T-005 | EndpointProfileRepository CRUD, encryption, Keystore failure detection | JVM + Robolectric | Profile saves/loads; Keystore absence returns `isKeystoreAvailable() == false` |
| UT-03 | T-007, T-036 | StreamingService state transitions, no credentials in Intent | JVM + mocked RootEncoder | State machine transitions match spec; Intent extras contain only profile ID |
| UT-04 | T-009 | ConnectionManager backoff sequence, jitter bounds, Doze suppression, auth stop | JVM | Backoff sequence: 3, 6, 12, 24, 48, 60, 60. Auth failure → no retry. Doze → skips timer |
| UT-05 | T-020 | EncoderController serializes concurrent requests, cooldown enforced | JVM + coroutine test | No concurrent restarts. Cooldown blocks within 60s. Bitrate-only bypasses |
| UT-06 | T-021 | ThermalMonitor emits correct ThermalLevel on status changes | JVM | Level transitions match threshold map |
| UT-07 | T-026 | CredentialSanitizer redacts all URL/key patterns | JVM | Zero occurrences of test key in output |
| UT-08 | T-026 | ACRA report fields exclude LOGCAT, SHARED_PREFERENCES | JVM | Excluded fields not in report content list |
| UT-09 | T-010 | StreamViewModel correctly gates preview on surface readiness | JVM + Turbine | `attachPreviewSurface` called only after `CompletableDeferred` completes |
| UT-10 | T-022 | Audio focus loss mutes mic, resume only on unmute | JVM | State reflects muted on focus loss |
| UT-11 | T-023 | Battery below threshold triggers warning, ≤ 2% triggers stop | JVM | State transitions at correct thresholds |
| UT-12 | T-019 | ABR ladder steps down correctly, recovers on bandwidth improvement | JVM | Step sequence matches defined ladder |
| UT-13 | T-038 | Structured logger redacts secrets at all log levels | JVM | No secrets in formatted log output |

### Instrumented Tests

| Test ID | Related Tasks | What is Being Proven | Min Environment | Pass/Fail Signal |
|---|---|---|---|---|
| IT-01 | T-006 | DeviceCapabilityQuery returns valid resolutions/fps on real hardware | Physical device API 23 + API 35 | Non-empty results, all values within hardware capability |
| IT-02 | T-007 | FGS starts with notification, survives activity destruction | Emulator API 31+ | FGS running after `finish()` |
| IT-03 | T-013 | Permission grant/denial flow works correctly | Emulator API 33+ | Stream starts after grant; shows rationale after denial |
| IT-04 | T-014 | Orientation lock holds during active stream | Physical device | No orientation change during stream |
| IT-05 | T-024 | Camera revocation switches to audio-only | Physical device (revoke via Settings) | Audio stream continues; notification shows "Camera paused" |
| IT-06 | T-027 | Process death recovery rebinds and restores preview | Emulator + `adb shell am kill` | Preview visible within 2 seconds |
| IT-07 | T-025 | Local recording produces playable MP4 | Physical device API 29+ | MP4 opens in video player |
| IT-08 | T-012 | Compose UI renders all controls, HUD updates | Emulator | Compose test assertions pass |

### Device Matrix Tests (E2E Manual)

| Test ID | Related Tasks | What is Being Proven | Device Matrix | Pass/Fail Signal |
|---|---|---|---|---|
| DM-01 | T-007, T-009, T-018 | Full stream lifecycle over RTMP | Low-end API 23, mid-range API 28, flagship API 35 | Stream starts, runs 5 min, stops cleanly |
| DM-02 | T-007, T-009, T-018 | Full stream lifecycle over RTMPS | Same 3 devices | Stream starts, runs 5 min, stops cleanly |
| DM-03 | T-007 | Video-only, audio-only, video+audio modes | Mid-range API 28 | Each mode streams correctly |
| DM-04 | T-021 | Thermal degradation under sustained load | Physical device with thermal stress | Quality steps down, no crash |
| DM-05 | T-009 | Reconnect after network drop + Doze | Physical device, airplane mode toggle | Reconnects within expected backoff window |

### Failure Injection Scenarios

| Test ID | Related Tasks | What is Being Proven | Method | Pass/Fail Signal |
|---|---|---|---|---|
| FI-01 | T-009 | Network loss during stream | Toggle airplane mode | Reconnecting state shown, stream resumes on network restore |
| FI-02 | T-020 | Concurrent ABR + thermal event | Mock both signals simultaneously | No crash, requests serialized |
| FI-03 | T-007 | Encoder failure mid-stream | Force unsupported config change | One re-init attempted, then Stopped(ERROR_ENCODER) |
| FI-04 | T-027 | Process death with active stream | `adb shell am kill` | Preview rebinds, stats restore |
| FI-05 | T-024 | Camera revocation in background | Revoke permission via Settings | Audio-only continues, video resumes on foreground return |
| FI-06 | T-023 | Battery critical during stream | Mock battery level ≤ 2% | Stream auto-stops, recording finalized |

---

## 10. Operational Readiness Checklist

### Security
- [ ] `android:allowBackup="false"` in manifest.
- [ ] No custom `X509TrustManager` in codebase (grep: `TrustManager`, `X509`).
- [ ] EncryptedSharedPreferences used for all credential storage (grep: `SharedPreferences` — only `EncryptedSharedPreferences` for secrets).
- [ ] No stream key, password, or auth token in any Intent extra (grep: `putExtra.*key`, `putExtra.*pass`, `putExtra.*auth`).
- [ ] ACRA excludes `LOGCAT` and `SHARED_PREFERENCES` in release config.
- [ ] `CredentialSanitizer` unit test passes with zero leaked secrets.
- [ ] ACRA HTTP transport enforces HTTPS (no `http://` without user confirmation).
- [ ] No secrets logged at any level (grep for `Log.` calls near credential variables).
- [ ] FGS start Intent carries only profile ID string.

### Reliability
- [ ] `onStartCommand` returns `START_NOT_STICKY`.
- [ ] Service not silently restarted after OS kill. User sees session-ended message.
- [ ] Auto-reconnect cancels on explicit user stop.
- [ ] Auth failure stops all retries.
- [ ] All `MediaCodec` quality changes serialized through `EncoderController`.
- [ ] 60-second cooldown between thermal-triggered encoder restarts.
- [ ] Process death with surviving service: preview rebinds within 2 seconds.
- [ ] `CompletableDeferred<SurfaceHolder>` gates `startPreview()`.
- [ ] No zombie notifications after service stops.
- [ ] Notification actions debounced ≥ 500ms.

### Performance
- [ ] Startup to preview < 2 seconds on mid-range device (measured).
- [ ] APK size < 15 MB per ABI for `foss` variant (measured).
- [ ] No `StrictMode` violations in debug build.
- [ ] No memory leaks: ViewModel does not retain `View`, `Surface`, or `Activity` references.
- [ ] Stats collection coroutine runs at 1 Hz, not faster.

### Compliance / Distribution
- [ ] `./gradlew :app:dependencies --configuration fossReleaseRuntimeClasspath | grep -i gms` returns zero matches.
- [ ] `foss` variant builds without GMS dependencies.
- [ ] `gms` variant builds for Play Store.
- [ ] ProGuard/R8 rules include RootEncoder, ACRA, EncryptedSharedPreferences keep rules.
- [ ] Release APK signed with release keystore.
- [ ] `.aab` for Play Store, `.apk` for sideloading/F-Droid.
- [ ] ABI splits configured for `foss` release: `arm64-v8a`, `armeabi-v7a`.

---

## 11. Open Questions and Blockers

| Blocker ID | What is Missing | Impacted Tasks | Proposed Decision Owner |
|---|---|---|---|
| OQ-01 | RootEncoder v2.7.x exact version: spec says 2.7.x but the latest as of March 2026 needs to be verified from JitPack/GitHub. If 2.7.0 is unavailable, must determine exact artifact coordinates | T-001, T-007, T-019 | Tech Lead |
| OQ-02 | ACRA self-hosted endpoint URL: spec says "self-hosted HTTP endpoint or email." No endpoint URL is specified. Needed for `AcraConfigurator` | T-026 | Product Owner |
| OQ-03 | Test RTMP server for development: an ingest endpoint is needed for E2E testing. Options: Nginx RTMP module locally, or a shared staging endpoint | T-007, T-009, T-035 | DevOps |
| OQ-04 | RootEncoder's exact API for tee-ing encoded buffers to MP4 muxer without a second encoder: `RtmpCamera2` may support this via `startRecord()`, but API compatibility needs verification | T-025 | Tech Lead |
| OQ-05 | Compose BOM version 2025.03.xx: exact version needs to be resolved. Spec uses placeholder | T-001 | Tech Lead |
| OQ-06 | Brand icon assets: spec describes geometric camera lens + broadcast arcs with specific colors, but no asset files exist. Needed for launcher icon and notification icon | T-001, T-008 | Designer |
| OQ-07 | RootEncoder `GlStreamInterface` for overlay architecture: exact API surface for `onDrawFrame(canvas)` needs to be verified against v2.7.x | T-037 | Tech Lead |
| OQ-08 | SAF (`ACTION_OPEN_DOCUMENT_TREE`) UX: should the SAF picker launch immediately when recording is toggled, or on first stream start with recording enabled? Spec says "immediately" on toggle (§4.1 MC-05) | T-025 | Product Owner |

---

## 12. First 72-Hour Execution Starter

### Day 1 (Hours 0–8)

**Parallel tracks (3 agents):**

| Agent | Task | Goal |
|---|---|---|
| Agent A | T-001: Project Scaffolding | Buildable project with both flavors, manifest, Hilt app class |
| Agent B | T-002: Data Models & Interfaces | All sealed classes, data classes, interfaces compilable |
| Agent C | T-038: Structured Logging + T-032: Manifest Hardening | Logging utility and manifest security baseline |

**End of Day 1 artifacts:**
- `./gradlew assembleFossDebug assembleGmsDebug` passes.
- All data model and interface files exist in correct packages.
- Manifest contains all permissions, FGS declaration, `allowBackup="false"`.
- Logging utility with credential redaction compiles.

### Day 2 (Hours 8–16)

**Parallel tracks (4 agents):**

| Agent | Task | Goal |
|---|---|---|
| Agent A | T-003: Hilt DI Modules | All interfaces wired with Hilt |
| Agent B | T-004: SettingsRepository | DataStore preferences impl + unit tests |
| Agent C | T-005: EndpointProfileRepository | Encrypted CRUD + unit tests |
| Agent D | T-006: DeviceCapabilityQuery + T-013: Permissions Handler | Camera/codec query + permission flow |

**Note:** T-003 blocks B/C/D in practice, but Agent A should complete it within 2–3 hours, unblocking others for the remainder of Day 2.

**End of Day 2 artifacts:**
- DI graph compiles and wires correctly.
- Settings save/load round-trip verified in unit test.
- Credential encryption/decryption verified in unit test.
- Device capability query compiles (instrumented test deferred to device availability).
- Permission handler composable exists.

### Day 3 (Hours 16–24)

**Parallel tracks (3 agents):**

| Agent | Task | Goal |
|---|---|---|
| Agent A | T-007: StreamingService (FGS Core) — start scaffold | FGS lifecycle, state machine, RtmpCamera2 init stub. Streaming may not work E2E yet but the state machine and service binding compile |
| Agent B | T-026: ACRA + T-033: F-Droid CI | ACRA config, credential sanitizer + unit test, CI flavor check |
| Agent C | T-015: Settings Screens | Video/Audio/General settings UI (Compose), reading from SettingsRepository |

**End of Day 3 artifacts:**
- StreamingService compiles with Hilt, state machine unit tests pass, FGS notification stub exists.
- ACRA credential sanitizer unit test passes.
- Settings screens render with device-filtered options (mocked capability data).
- F-Droid CI check script exists and passes.
- **Total: 12 of 38 tasks started, 8+ completed.**

### Critical Path Status After 72 Hours
```
T-001 ✅ → T-003 ✅ → T-005 ✅ → T-007 🔄 (in progress)
                                    ↓
                      T-009 (ready to start Day 4)
                      T-010 (ready to start Day 4)
                      T-020 (ready to start Day 4)
```

The critical path is on track. Day 4 proceeds with the streaming service completion, ConnectionManager, and StreamViewModel — the three components that unlock end-to-end streaming.
