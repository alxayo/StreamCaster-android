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
│   ├── EncoderBridge.kt             // Interface abstracting RtmpCamera2 ops
│   ├── ConnectionManager.kt         // RTMP connect/reconnect logic
│   ├── EncoderController.kt         // Mutex-serialized quality changes
│   ├── AbrPolicy.kt                 // ABR decision logic (when to step)
│   ├── AbrLadder.kt                 // ABR quality ladder definition
│   └── NotificationController.kt    // FGS notification + action intents
├── camera/
│   ├── DeviceCapabilityQuery.kt     // Interface
│   └── Camera2CapabilityQuery.kt    // Implementation
├── audio/
│   └── AudioSourceManager.kt       // Interface + impl for mute/unmute
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
│   ├── MetricsCollector.kt          // Internal metrics counters
│   └── model/
│       ├── StreamState.kt
│       ├── StreamConfig.kt
│       ├── EndpointProfile.kt
│       ├── StreamStats.kt
│       └── StopReason.kt
├── crash/
│   ├── AcraConfigurator.kt
│   └── CredentialSanitizer.kt       // URL/key redaction (owned by T-038, consumed by T-026)
├── util/
│   └── RedactingLogger.kt           // Structured logging wrapper
└── di/
    ├── AppModule.kt
    └── StreamModule.kt
```

### Data Contracts (Shared Across All Agents)

```kotlin
// --- StreamState.kt ---
enum class StopReason {
    USER_REQUEST, ERROR_ENCODER, ERROR_AUTH, ERROR_CAMERA,
    ERROR_AUDIO, THERMAL_CRITICAL, BATTERY_CRITICAL
}

sealed class StreamState {
    data object Idle : StreamState()
    data object Connecting : StreamState()
    data class Live(
        val cameraActive: Boolean = true,
        val isMuted: Boolean = false
    ) : StreamState()
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
    val thermalLevel: ThermalLevel = ThermalLevel.NORMAL
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
| T-007a | StreamingService (FGS Lifecycle & State Machine) | Implement FGS lifecycle, state machine, service binding, notification delegation. Uses a stub encoder interface — no RootEncoder dependency. Unblocks all downstream consumers | In: FGS lifecycle, state machine, service binding. Out: RtmpCamera2 integration, stats polling, camera HAL | Spec §6.2, §7.1, §7.2, §9.1 | `StreamingService.kt` (skeleton), `EncoderBridge.kt` (interface/stub) | T-002, T-003, T-005 | No | Android | M (2d) | High | `./gradlew testFossDebugUnitTest --tests '*StreamingService*'` | `service/StreamingService.kt`, `service/EncoderBridge.kt`, `AndroidManifest.xml` |
| T-007b | StreamingService (RtmpCamera2 Integration) | Integrate RootEncoder `RtmpCamera2` into StreamingService: camera/audio capture, 1 Hz stats polling, encoder callbacks, camera HAL handling. Register `MediaCodec.Callback.onError()` for mid-stream encoder error detection — attempt one re-init, then emit `Stopped(ERROR_ENCODER)` | In: RtmpCamera2 integration, stats polling, encoder error recovery. Out: reconnect, ABR, thermal | Spec §6.2, §8.1, §11 | `StreamingService.kt` (complete), RootEncoder callbacks wired | T-007a | Yes (after T-007a) | Android | M (2d) | High | Manual: stream to test RTMP server, verify HUD stats | `service/StreamingService.kt` |
| T-008 | NotificationController | FGS notification with state display (Live, Reconnecting, Paused, Stopped), action buttons (Stop, Mute/Unmute), deep-link Start, debounce. No zombie notifications. Accepts both `StreamState` and `StreamStats` for real-time stats display (bitrate, connection quality) | In: notification management. Out: notification appearance design | Spec §7.1, §7.4, §4.6 SL-04, §12.2 | `NotificationController.kt` | T-007a | No | Android | M (2d) | Medium | `./gradlew testFossDebugUnitTest --tests '*Notification*'` | `service/NotificationController.kt` |
| T-009 | ConnectionManager (RTMP Connect/Reconnect) | RTMP/RTMPS connect, disconnect, auto-reconnect with exponential backoff + jitter, Doze awareness, ConnectivityManager.NetworkCallback integration. Uses `StreamState` directly (no separate `ConnectionState` type) — drives transitions on the service's `MutableStateFlow<StreamState>` | In: connection lifecycle. Out: ABR, thermal triggers | Spec §4.6 SL-02, §11, §3 (App Standby) | `ConnectionManager.kt` + unit tests | T-002, T-007a | No | Android | L (3d) | High | `./gradlew testFossDebugUnitTest --tests '*ConnectionManager*'` | `service/ConnectionManager.kt` |
| T-010 | StreamViewModel & Service Binding | ViewModel binds to StreamingService, projects StreamState + StreamStats as Compose-observable StateFlows, preview surface management with WeakReference/CompletableDeferred, idempotent commands | In: ViewModel. Out: UI composables | Spec §6.2, §7.2, §7.3 | `StreamViewModel.kt` + unit tests | T-002, T-007a | No | Android | M (2d) | Medium | `./gradlew testFossDebugUnitTest --tests '*StreamViewModel*'` | `ui/stream/StreamViewModel.kt` |
| T-011 | Camera Preview Composable | AndroidView wrapping SurfaceView, SurfaceHolder.Callback driving CompletableDeferred, surface lifecycle management, no strong View references across config change | In: preview UI. Out: HUD, controls | Spec §4.1 MC-03, §7.3 | `CameraPreview.kt` | T-010 | No | Android | M (2d) | Medium | Manual: preview appears on device | `ui/components/CameraPreview.kt` |
| T-012 | Stream Screen UI (Controls + HUD) | Start/Stop button, mute button, camera-switch button, status badge, recording indicator, HUD overlay (bitrate, fps, duration, connection status, thermal badge). Landscape-first layout with portrait variant | In: Compose UI. Out: settings screens | Spec §10.1, §10.3 | `StreamScreen.kt`, `StreamHud.kt` | T-010, T-011 | No | Android | M (2d) | Low | Compose UI test: `./gradlew connectedFossDebugAndroidTest --tests '*StreamScreen*'` | `ui/stream/StreamScreen.kt`, `ui/components/StreamHud.kt` |
| T-013 | Runtime Permissions Handler | Compose-friendly permission flow for CAMERA, RECORD_AUDIO, POST_NOTIFICATIONS (API 33+). Rationale dialogs, denial handling, mode fallback | In: permission UI. Out: — | Spec §9.4, §9.5 | `PermissionHandler.kt` | T-001 | Yes | Android | S (1d) | Low | `./gradlew connectedFossDebugAndroidTest --tests '*Permission*'` | `ui/components/PermissionHandler.kt` |
| T-014 | Orientation Lock Logic | Lock orientation in `Activity.onCreate()` before `setContentView()` from persisted pref. Unconditional lock when stream is active. Unlock only when idle | In: orientation. Out: — | Spec §4.1 MC-04 | `MainActivity.kt` orientation handling | T-004 | Yes | Android | S (0.5d) | Low | Instrumented test: rotation during stream doesn't restart | `MainActivity.kt` |
| T-015 | Settings Screens (Video/Audio/General) | Resolution picker (filtered by DeviceCapabilityQuery), fps picker, bitrate controls, audio settings, ABR toggle, camera default, orientation, reconnect config, battery threshold, media mode selection | In: UI. Out: — | Spec §4.2, §4.3, §10.1 | `VideoAudioSettingsScreen.kt`, `GeneralSettingsScreen.kt`, `SettingsViewModel.kt` | T-004, T-006 | Yes (after T-004) | Android | M (2d) | Low | `./gradlew testFossDebugUnitTest --tests '*SettingsViewModel*'` | `ui/settings/*` |
| T-016 | Endpoint Setup Screen | RTMP URL input, stream key, username/password, Test Connection button, Save as Default, profile CRUD list. Transport security warnings (§9.2) | In: UI. Out: — | Spec §4.4, §9.2 | `EndpointScreen.kt` | T-005, T-015 | Yes (after T-005) | Android | M (2d) | Medium | `./gradlew testFossDebugUnitTest --tests '*Endpoint*'` | `ui/settings/EndpointScreen.kt` |
| T-017 | Compose Navigation | NavHost with routes: Stream, EndpointSetup, VideoAudioSettings, GeneralSettings. Single-activity | In: navigation. Out: — | Spec §10.2 | `AppNavGraph.kt` | T-012, T-015, T-016 | No | Android | S (0.5d) | Low | App launches and navigates all routes | `navigation/AppNavGraph.kt` |
| T-018 | RTMPS & Transport Security Enforcement | RTMPS via system TrustManager. Warn+confirm dialog for credentials over plaintext RTMP. Connection test obeys same rules. No custom X509TrustManager | In: transport security. Out: — | Spec §9.2, §5 NF-08 | Security logic in `ConnectionManager` + UI warning dialog | T-009, T-016 | No | Android/Security | M (2d) | High | Unit test: auth over rtmp:// triggers warning. No custom TrustManager in codebase | `service/ConnectionManager.kt`, `ui/settings/EndpointScreen.kt` |
| T-019 | Adaptive Bitrate (ABR) System | ABR ladder definition per device capabilities, bitrate-only reduction first, resolution/fps step-down, recovery on bandwidth improvement. Encoder backpressure detection (§8.1): if output fps < 80% of configured fps for 5 consecutive seconds, trigger ABR step-down. Delivers `AbrPolicy` and `AbrLadder` classes that decide _when_ to step. Calls `EncoderController.requestAbrChange()` to apply changes | In: ABR decision logic. Out: encoder control (owned by T-020) | Spec §4.5, §8.1, §8.2, §8.3, §8.4 | `AbrPolicy.kt`, `AbrLadder.kt` + unit tests | T-006, T-007a, T-020 | No | Android | L (3d) | High | `./gradlew testFossDebugUnitTest --tests '*ABR*' --tests '*AbrPolicy*' --tests '*AbrLadder*'` | `service/AbrPolicy.kt`, `service/AbrLadder.kt` |
| T-020 | EncoderController (Mutex-Serialized Quality Changes) | Single component serializing all encoder re-init requests from ABR + thermal systems via coroutine Mutex. Controlled restart sequence: stop preview → release encoder → reconfigure → restart → IDR. Owns the complete `EncoderController` public API including `requestAbrChange()` and `requestThermalChange()`. StreamingService passes `RtmpCamera2` reference at construction time (factory method, not Hilt) | In: encoder control. Out: — | Spec §8.2, §8.3 | `EncoderController.kt` (complete public API) | T-007a | No | Android | M (2d) | High | `./gradlew testFossDebugUnitTest --tests '*EncoderController*'` | `service/EncoderController.kt` |
| T-021 | Thermal Monitoring & Response | API 29+: `OnThermalStatusChangedListener`. API 23–28: `BatteryManager.EXTRA_TEMPERATURE`. Progressive degradation with 60s cooldown. Critical → graceful stop via `StreamingService` (calls `stopStream()` with `THERMAL_CRITICAL`) | In: thermal handling. Out: — | Spec §4.6 SL-07, §5 NF-09 | `ThermalMonitor.kt`, `ThermalStatusMonitor.kt`, `BatteryTempMonitor.kt` | T-002, T-007a, T-020 | Yes (after T-020) | Android | M (2d) | High | `./gradlew testFossDebugUnitTest --tests '*Thermal*'` | `thermal/*` |
| T-022 | Audio Focus & Interruption Handling | Register `AudioManager.OnAudioFocusChangeListener`. On focus loss: mute mic via `AudioSourceManager`, show indicator. Resume only on explicit user unmute. Coordinate with T-029 mute toggle — if user already manually muted, focus loss is a no-op | In: audio focus. Out: — | Spec §4.6 SL-08, §11 | Audio focus logic in `StreamingService` | T-007a | No | Android | S (1d) | Medium | `./gradlew testFossDebugUnitTest --tests '*AudioFocus*'` | `service/StreamingService.kt`, `audio/AudioSourceManager.kt` |
| T-023 | Low Battery Handling | Monitor battery level. Configurable warning threshold (default 5%). Auto-stop at ≤ 2%. Finalize local recording | In: battery. Out: — | Spec §4.6 SL-05, §11 | Battery logic in `StreamingService` | T-007a | Yes (after T-007a) | Android | S (1d) | Low | `./gradlew testFossDebugUnitTest --tests '*Battery*'` | `service/StreamingService.kt` |
| T-024 | Background Camera Revocation Handling | Detect camera revocation in background. Switch to audio-only if audio track is active. **Video-only mode + camera revocation = graceful stop** (placeholder frame injection is not feasible with RtmpCamera2 API — explicitly scoped out). Show "Camera paused" in notification for audio+video mode. Re-acquire on foreground return with IDR | In: camera revocation. Out: placeholder frame injection | Spec §4.6 SL-06, §11 | Camera revocation logic in `StreamingService` | T-007a, T-007b, T-008 | No | Android | M (2d) | High | Instrumented test: revoke camera permission in background, verify audio continues; video-only mode stops gracefully | `service/StreamingService.kt` |
| T-025 | Local MP4 Recording | Tee encoded buffers to MP4 muxer (no second encoder) via RootEncoder `startRecord()` (verified by T-025a spike). API 29+: SAF picker + `takePersistableUriPermission()`. API 23–28: `getExternalFilesDir`. Fail fast if no storage grant, don't block streaming. **Mid-stream insufficient storage**: catch muxer write errors, stop recording, continue RTMP stream, notify user via notification (spec §11) | In: recording. Out: — | Spec §4.1 MC-05, §11 | Recording logic in `StreamingService` | T-007b, T-025a | No | Android | L (3d) | Medium | Manual: record and verify MP4 playback. Unit test: mock storage-full → recording stops, stream continues | `service/StreamingService.kt` |
| T-026 | ACRA Crash Reporting with Credential Redaction | Configure ACRA via programmatic `CoreConfigurationBuilder` in `Application.attachBaseContext()`, guarded by `!BuildConfig.DEBUG`. Exclude `SHARED_PREFERENCES`, `LOGCAT` in release. HTTPS enforcement for report transport. Register `ReportTransformer` that _consumes_ the existing `CredentialSanitizer` (owned by T-038) — do NOT redefine it. Unit test verifying redaction | In: crash reporting. Out: — | Spec §9.3 | `AcraConfigurator.kt` + unit tests | T-001, T-038 | Yes | Android/Security | M (2d) | High | `./gradlew testFossDebugUnitTest --tests '*Acra*'` | `crash/AcraConfigurator.kt` |
| T-027 | Process Death Recovery | On activity recreation with surviving service: rebind, restore preview via CompletableDeferred surface-ready gate, reflect live stats. If service dead: show Stopped. No automatic stream resumption | In: process death. Out: — | Spec §7.3 | Logic in `StreamViewModel` + `CameraPreview` | T-010, T-011 | No | Android | M (2d) | High | Instrumented test: kill activity process, verify rebind | `ui/stream/StreamViewModel.kt`, `ui/components/CameraPreview.kt` |
| T-028 | Connection Test Button | Lightweight RTMP handshake probe. 10s timeout cap. Obeys transport security rules. Actionable result messaging (success, timeout, auth failure, TLS error) | In: connection test. Out: — | Spec §4.4 EP-07, §12.4 | Connection test in `ConnectionManager` + UI | T-009, T-018 | No | Android | S (1d) | Medium | `./gradlew testFossDebugUnitTest --tests '*ConnectionTest*'` | `service/ConnectionManager.kt`, `ui/settings/EndpointScreen.kt` |
| T-029 | Mute Toggle (Service Logic) | Implement mute/unmute in `StreamingService` via `AudioSourceManager`. Update `StreamState.Live(isMuted)` immediately. Notification mute/unmute action via `NotificationController`. UI button wiring deferred to T-012 | In: mute service logic. Out: UI button (wired in T-012) | Spec §4.3 AS-05 | Mute logic in `StreamingService` + notification action | T-007a, T-008 | Yes (after T-007a) | Android | S (0.5d) | Low | `./gradlew testFossDebugUnitTest --tests '*Mute*'` | `service/StreamingService.kt`, `audio/AudioSourceManager.kt` |
| T-030 | Camera Switching (Service Logic) | Front ↔ back via `RtmpCamera2.switchCamera()` before and during stream. Service-side implementation only. UI button wiring deferred to T-012 | In: camera switch service logic. Out: UI button (wired in T-012) | Spec §4.1 MC-02 | Camera switch in `StreamingService` | T-007b | Yes (after T-007b) | Android | S (0.5d) | Low | `./gradlew testFossDebugUnitTest --tests '*CameraSwitch*'` | `service/StreamingService.kt` |
| T-031 | Prolonged Session Monitor | On low-end devices (`isLowRamDevice` or < 2GB RAM), warn after configurable duration (default 90 min). Suppress if charging | In: session monitor. Out: — | Spec §11 (Prolonged session) | Logic in `StreamingService` | T-007a, T-023 | Yes (after T-007a) | Android | S (0.5d) | Low | `./gradlew testFossDebugUnitTest --tests '*SessionMonitor*'` | `service/StreamingService.kt` |
| T-032 | Manifest Hardening & Backup Rules | `android:allowBackup="false"` or BackupAgent excluding encrypted prefs. All FGS type declarations, permissions. Credential re-entry prompt on restore failure | In: manifest. Out: — | Spec §9.1, §9.4, §7.1 | `AndroidManifest.xml` hardening | T-001 | Yes | Security | S (0.5d) | Medium | Grep for `allowBackup`, `foregroundServiceType`, all required permissions | `AndroidManifest.xml` |
| T-033 | F-Droid / FOSS Flavor CI Verification | CI step: `./gradlew :app:dependencies --configuration fossReleaseRuntimeClasspath | grep -i gms` must return empty. ABI split config for `foss` | In: CI. Out: — | Spec §16.1 | CI workflow file | T-001 | Yes | DevOps | S (0.5d) | Low | `./gradlew :app:dependencies --configuration fossReleaseRuntimeClasspath \| grep -i gms` returns 0 matches | `.github/workflows/`, `build.gradle.kts` |
| T-034 | ProGuard / R8 Rules | R8 config for release builds. RootEncoder ProGuard rules. EncryptedSharedPreferences keep rules. ACRA keep rules | In: obfuscation. Out: — | Spec §16 | `proguard-rules.pro` | T-001, T-026 | Yes | DevOps | S (0.5d) | Low | `./gradlew assembleFossRelease` succeeds | `proguard-rules.pro` |
| T-035 | QA Test Matrix & Acceptance Test Suite | Manual test scripts for E2E matrix (3 devices × transports × modes). Automated acceptance tests for AC-01 through AC-19 where possible. **NFR measurements:** startup time < 2s on mid-range device (NF-01), battery drain ≤ 15%/hr during 1080p30 stream (NF-03), per-ABI APK size < 15 MB (NF-05). Record results. Fail QA if Must-level NFRs are not met | In: QA + NFR measurement. Out: — | Spec §5, §15, §20 | Test plan + instrumented tests + NFR measurement report | T-007a through T-042 | No | QA | L (4d) | Medium | `./gradlew connectedFossDebugAndroidTest` | `androidTest/` |
| T-036 | Intent Security — No Credentials in Extras | FGS start Intent carries only profile ID. Service fetches credentials from EndpointProfileRepository. Verify via test that no key/password appears in Intent | In: security. Out: — | Spec §9.1, AC-13 | Enforcement in `StreamingService` start path | T-005, T-007a | No | Security | S (0.5d) | High | `./gradlew testFossDebugUnitTest --tests '*IntentSecurity*'` | `service/StreamingService.kt`, `ui/stream/StreamViewModel.kt` |
| T-037 | Overlay Architecture Stub | `OverlayManager` interface with `onDrawFrame(canvas: GlCanvas)`. `NoOpOverlayManager` default. Hilt-provided | In: interface + no-op. Out: actual rendering | Spec §4.7 | `OverlayManager.kt`, `NoOpOverlayManager.kt` | T-002, T-003 | Yes | Android | S (0.5d) | Low | Compiles | `overlay/*` |
| T-038 | Structured Logging & Secret Redaction | Wrap all log calls through a redacting logger. URL sanitization pattern: `rtmp[s]?://([^/\s]+/[^/\s]+)/\S+` → masked. No secrets at any log level. **Fully owns `CredentialSanitizer`** — regex patterns, `sanitize()` function, unit tests. T-026 (ACRA) consumes this sanitizer via `ReportTransformer` | In: logging + sanitizer. Out: — | Spec §9.3, §12.3 | `CredentialSanitizer.kt` (complete), logging utility | T-001 | Yes | Security | S (1d) | Medium | `./gradlew testFossDebugUnitTest --tests '*Logging*' --tests '*Redact*' --tests '*Sanitizer*'` | `crash/CredentialSanitizer.kt`, `util/RedactingLogger.kt` |
| T-025a | Recording API Spike — Verify RootEncoder `startRecord()` | Spike task: verify RootEncoder `RtmpCamera2.startRecord()` API compatibility with v2.7.x. Confirm tee-ing encoded buffers to MP4 muxer without a second encoder works. Document API surface and limitations | In: API verification. Out: actual recording implementation | OQ-04 | Spike report: confirmed API or alternative approach documented | T-001 | Yes | Tech Lead | S (0.5d) | Medium | Written spike report with working code snippet | N/A (spike) |
| T-039 | Microphone Revocation Handling | Detect `RECORD_AUDIO` permission revocation mid-stream. Stop stream with `Stopped(ERROR_AUDIO)`. Surface error to user via notification and UI. Audio track loss cannot be gracefully degraded (spec §11) | In: mic revocation detection. Out: — | Spec §11 | Mic revocation logic in `StreamingService` + unit test | T-007a, T-007b | No | Android | S (1d) | High | `./gradlew testFossDebugUnitTest --tests '*MicRevocation*'` | `service/StreamingService.kt` |
| T-040 | OEM Battery Optimization Guidance | Detect manufacturer (Samsung, Xiaomi, Huawei). Show one-time setup guide with deep links to OEM-specific battery settings. Request `IGNORE_BATTERY_OPTIMIZATIONS` permission. Persist dismissal so guide shows only once | In: OEM guidance UX. Out: — | Spec §18 Risk, §17 Phase 4 | OEM guidance dialog + utility | T-004, T-013 | Yes | Android | M (2d) | Medium | Manual: dialog appears on Samsung/Xiaomi emulator; dismissed state persists | `ui/components/BatteryOptimizationGuide.kt`, `data/SettingsRepository.kt` |
| T-041 | Mid-Stream Media Mode Transition | Service supports transitioning from video+audio to audio-only (release camera, stop video encoder, keep audio+RTMP alive) and from audio-only back to video+audio (reacquire camera, re-init video encoder, send IDR). Coordinate with EncoderController for encoder re-init | In: runtime mode switch. Out: — | Spec §4.1 MC-01 | Media mode transition in `StreamingService` + `EncoderController` | T-007b, T-020 | No | Android | M (2d) | High | `./gradlew testFossDebugUnitTest --tests '*MediaMode*'` | `service/StreamingService.kt`, `service/EncoderController.kt` |
| T-042 | Internal Metrics Collection | Counters for encoder init success/failure, reconnect attempts and success/failure ratio, thermal level transitions, storage write errors, FGS start events, permission denial events. Exposed via debug screen or logged on stream stop | In: metrics infrastructure. Out: — | Spec §12.1 | `MetricsCollector.kt` + debug screen | T-007a | Yes | Android | M (2d) | Low | `./gradlew testFossDebugUnitTest --tests '*Metrics*'` | `data/MetricsCollector.kt`, `ui/debug/MetricsScreen.kt` |

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
  T-025a: Recording API Spike    [T-001]
  T-032: Manifest Hardening      [T-001]
  T-033: F-Droid CI              [T-001]
  T-038: Structured Logging      [T-001]  ← OWNS CredentialSanitizer

Stage 2 (Data + Capabilities — Depends on Stage 1):
  T-004: SettingsRepository      [T-002, T-003]
  T-005: EndpointProfileRepo     [T-002, T-003]
  T-006: DeviceCapabilityQuery   [T-002, T-003]
  T-026: ACRA Crash Reporting    [T-001, T-038]  ← CONSUMES CredentialSanitizer
  T-037: Overlay Stub            [T-002, T-003]

Stage 3 (Service Core — Critical Path):
  T-007a: StreamingService FGS   [T-002, T-003, T-005]  ← CRITICAL PATH
  T-014: Orientation Lock        [T-004]
  T-015: Settings Screens        [T-004, T-006]
  T-040: OEM Battery Guidance    [T-004, T-013]

Stage 4 (Service Features — Depends on T-007a):
  T-007b: RtmpCamera2 Integration [T-007a]
  T-008: NotificationController  [T-007a]
  T-009: ConnectionManager       [T-002, T-007a]           ← CRITICAL PATH
  T-010: StreamViewModel         [T-002, T-007a]           ← CRITICAL PATH
  T-020: EncoderController       [T-007a]
  T-022: Audio Focus             [T-007a]
  T-023: Low Battery             [T-007a]
  T-029: Mute Toggle (Service)   [T-007a, T-008]
  T-036: Intent Security         [T-005, T-007a]
  T-042: Internal Metrics        [T-007a]

Stage 5 (UI + Advanced Features):
  T-011: Camera Preview          [T-010]
  T-016: Endpoint Screen         [T-005, T-015]
  T-019: ABR System              [T-006, T-007a, T-020]    ← DEPENDS ON T-020
  T-021: Thermal Monitoring      [T-002, T-007a, T-020]    ← DEPENDS ON T-007a + T-020
  T-024: Camera Revocation       [T-007a, T-007b, T-008]
  T-025: Local MP4 Recording     [T-007b, T-025a]
  T-030: Camera Switching (Svc)  [T-007b]
  T-031: Prolonged Session       [T-007a, T-023]
  T-039: Microphone Revocation   [T-007a, T-007b]
  T-041: Mid-Stream Media Mode   [T-007b, T-020]

Stage 6 (Integration + Polish):
  T-012: Stream Screen UI        [T-010, T-011]
  T-017: Compose Navigation      [T-012, T-015, T-016]
  T-018: RTMPS & Transport Sec.  [T-009, T-016]
  T-027: Process Death Recovery   [T-010, T-011]
  T-028: Connection Test Button   [T-009, T-018]
  T-034: ProGuard/R8             [T-001, T-026]

Stage 7 (Validation):
  T-035: QA Test Matrix          [all above]
```

### Critical Path

The critical path has two branches merging at Stage 6. The longer branch determines the true project duration:

**Branch A (Service → Connection → Security):**
```
T-001 → T-003 → T-005 → T-007a → T-009 → ... → T-018 → T-028
```
But T-018 also depends on T-016, which depends on T-015, which depends on T-004 + T-006.

**Branch B (Service → UI chain):**
```
T-001 → T-003 → T-005 → T-007a → T-010 → T-011 → T-012 → T-017
                                    ↓
                                   T-027 (Process Death Recovery)
```

**Branch C (T-018 via T-016 — previously hidden):**
```
T-001 → T-003 → T-006 → T-015 → T-016 → T-018 → T-028
              (2d)    (2d)     (2d)     (2d)     (2d)    (1d) = 11d
```

**True critical path** is the longer of branches A, B, and C converging at T-017/T-028. Branch B (T-007a→T-010→T-011→T-012→T-017) totals: 2d + 2d + 2d + 2d + 0.5d = 8.5d from T-007a start. With T-007a starting after T-005 (Day 5 at earliest), first E2E demo is Day 13.5 minimum.

### Mermaid Dependency Graph

```mermaid
graph TD
  T001[T-001: Scaffolding] --> T003[T-003: Hilt DI]
  T002[T-002: Data Models] --> T003
  T001 --> T013[T-013: Permissions]
  T001 --> T025a[T-025a: Recording Spike]
  T001 --> T032[T-032: Manifest]
  T001 --> T033[T-033: F-Droid CI]
  T001 --> T038[T-038: Logging + Sanitizer]
  T038 --> T026[T-026: ACRA]
  T003 --> T037[T-037: Overlay Stub]
  T002 --> T037
  T003 --> T004[T-004: SettingsRepo]
  T003 --> T005[T-005: EndpointProfileRepo]
  T003 --> T006[T-006: DeviceCapQuery]
  T005 --> T007a[T-007a: FGS Lifecycle]
  T002 --> T007a
  T003 --> T007a
  T004 --> T014[T-014: Orientation Lock]
  T004 --> T015[T-015: Settings Screens]
  T006 --> T015
  T004 --> T040[T-040: OEM Battery Guide]
  T013 --> T040
  T007a --> T007b[T-007b: RtmpCamera2]
  T007a --> T008[T-008: NotificationController]
  T007a --> T009[T-009: ConnectionManager]
  T007a --> T010[T-010: StreamViewModel]
  T007a --> T020[T-020: EncoderController]
  T007a --> T022[T-022: Audio Focus]
  T007a --> T023[T-023: Low Battery]
  T007a --> T029[T-029: Mute Toggle Svc]
  T008 --> T029
  T005 --> T036[T-036: Intent Security]
  T007a --> T036
  T007a --> T042[T-042: Metrics]
  T010 --> T011[T-011: Camera Preview]
  T005 --> T016[T-016: Endpoint Screen]
  T015 --> T016
  T006 --> T019[T-019: ABR System]
  T007a --> T019
  T020 --> T019
  T002 --> T021[T-021: Thermal Monitor]
  T007a --> T021
  T020 --> T021
  T007a --> T024[T-024: Camera Revocation]
  T007b --> T024
  T008 --> T024
  T007b --> T025[T-025: Local Recording]
  T025a --> T025
  T007b --> T030[T-030: Camera Switching Svc]
  T023 --> T031[T-031: Prolonged Session]
  T007a --> T031
  T007a --> T039[T-039: Mic Revocation]
  T007b --> T039
  T007b --> T041[T-041: Media Mode Transition]
  T020 --> T041
  T010 --> T012[T-012: Stream Screen UI]
  T011 --> T012
  T009 --> T018[T-018: RTMPS Security]
  T016 --> T018
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

  style T007a fill:#e53935,color:#fff
  style T007b fill:#e53935,color:#fff
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
- Target subpackages: `data/model/`, `service/`, `camera/`, `overlay/`, `thermal/`, `audio/`

**Requirements:**
Create the following files with full Kotlin code:
1. `data/model/StreamState.kt` — `sealed class StreamState` with: `Idle`, `Connecting`, `Live(cameraActive: Boolean, isMuted: Boolean)`, `Reconnecting(attempt: Int, nextRetryMs: Long)`, `Stopping`, `Stopped(reason: StopReason)`. `enum class StopReason`: `USER_REQUEST`, `ERROR_ENCODER`, `ERROR_AUTH`, `ERROR_CAMERA`, `ERROR_AUDIO`, `THERMAL_CRITICAL`, `BATTERY_CRITICAL`. Note: `isMuted` lives in `StreamState.Live` (not in `StreamStats`) for immediate propagation to notification and UI.
2. `data/model/StreamStats.kt` — `data class StreamStats` with: `videoBitrateKbps`, `audioBitrateKbps`, `fps`, `droppedFrames`, `resolution`, `durationMs`, `isRecording`, `thermalLevel`. `enum class ThermalLevel`: `NORMAL`, `MODERATE`, `SEVERE`, `CRITICAL`.
3. `data/model/EndpointProfile.kt` — `data class EndpointProfile` with: `id` (UUID string), `name`, `rtmpUrl`, `streamKey`, `username?`, `password?`, `isDefault`.
4. `data/model/StreamConfig.kt` — `data class StreamConfig` and `data class Resolution(width, height)`.
5. `service/StreamingServiceControl.kt` — Interface with: `val streamState: StateFlow<StreamState>`, `val streamStats: StateFlow<StreamStats>`, `fun startStream(profileId: String)` (service reads config internally from `SettingsRepository`), `fun stopStream()`, `fun toggleMute()`, `fun switchCamera()`, `fun attachPreviewSurface(holder: SurfaceHolder)`, `fun detachPreviewSurface()`.
6. `service/ReconnectPolicy.kt` — Interface: `fun nextDelayMs(attempt: Int): Long`, `fun shouldRetry(attempt: Int): Boolean`, `fun reset()`.
7. `camera/DeviceCapabilityQuery.kt` — Interface: `fun getSupportedResolutions(cameraId: String): List<Resolution>`, `fun getSupportedFps(cameraId: String): List<Int>`, `fun isResolutionFpsSupported(res: Resolution, fps: Int): Boolean`, `fun getAvailableCameras(): List<CameraInfo>`.
8. `data/SettingsRepository.kt` — Interface for all non-sensitive settings (read/write via DataStore Preferences Flow).
9. `data/EndpointProfileRepository.kt` — Interface: `fun getAll(): Flow<List<EndpointProfile>>`, `suspend fun getById(id: String): EndpointProfile?`, `suspend fun save(profile: EndpointProfile)`, `suspend fun delete(id: String)`, `suspend fun getDefault(): EndpointProfile?`, `suspend fun setDefault(id: String)`, `suspend fun isKeystoreAvailable(): Boolean`. **All methods touching EncryptedSharedPreferences must be `suspend`** (crypto operations block).
10. `overlay/OverlayManager.kt` — Interface: `fun onDrawFrame(canvas: Any)` (placeholder type for GlCanvas).
11. `thermal/ThermalMonitor.kt` — Interface: `val thermalLevel: StateFlow<ThermalLevel>`, `fun start()`, `fun stop()`.
12. `audio/AudioSourceManager.kt` — Interface: `fun mute()`, `fun unmute()`, `val isMuted: StateFlow<Boolean>`. This is the shared contract for both T-029 (Mute Toggle) and T-022 (Audio Focus). `StreamingService` owns the implementation.

**Success Criteria:**
- `./gradlew compileDebugKotlin` succeeds with all files.
- No circular dependencies between packages.
- All return types and parameter types are fully specified (no `Any` except the GlCanvas placeholder).

---

### Agent Prompt for T-007a — StreamingService (FGS Lifecycle & State Machine)

**Context:** You are building the core foreground service for StreamCaster (`com.port80.app`). This service is the single source of truth for stream state. In the full implementation, it will own the RootEncoder `RtmpCamera2` instance. **However, this task (T-007a) focuses only on the FGS lifecycle, state machine, service binding, and notification delegation — with no RootEncoder dependency.** RtmpCamera2 integration is handled separately in T-007b. This split ensures downstream consumers (T-009 ConnectionManager, T-010 StreamViewModel, T-020 EncoderController) can code against the service's StateFlow interfaces immediately without being blocked by camera HAL issues.

**Your Task:** Implement `StreamingService` as an Android foreground service with a complete state machine and binding interface, using a stub encoder bridge.

**Input Files/Paths:**
- `app/src/main/kotlin/com/port80/app/service/StreamingService.kt`
- `app/src/main/kotlin/com/port80/app/service/EncoderBridge.kt` (interface/stub)
- Interfaces: `service/StreamingServiceControl.kt`, `data/model/StreamState.kt`, `data/model/StreamStats.kt`
- Repository: `data/EndpointProfileRepository.kt`, `data/SettingsRepository.kt` (inject via Hilt)

**Requirements:**
- Annotate with `@AndroidEntryPoint`. Declare `android:foregroundServiceType="camera|microphone"` (in manifest, not in code).
- Implement `StreamingServiceControl` interface.
- On `startStream(profileId)`: fetch credentials from `EndpointProfileRepository` and config from `SettingsRepository` (never from Intent extras — Intent carries only profile ID string as an extra). **The service reads config internally — the `startStream()` method takes only `profileId`**. Validate encoder config via `MediaCodecInfo` pre-flight.
- Define an `EncoderBridge` interface that abstracts RtmpCamera2 operations (startPreview, stopPreview, connect, disconnect, switchCamera, setVideoBitrateOnFly). Provide a stub implementation for T-007a. T-007b replaces the stub with the real RtmpCamera2 implementation.
- State machine: `Idle → Connecting → Live → Stopping → Stopped`. Transitions via `MutableStateFlow<StreamState>`. All command methods are idempotent (e.g., calling `stopStream()` from `Idle` is a no-op).
- `startPreview()` must not be called until a `SurfaceHolder` is attached via `attachPreviewSurface()`. Gate using `CompletableDeferred<SurfaceHolder>`.
- Register encoder error callback path: on mid-stream encoder error, attempt one re-init with current settings. If re-init fails, emit `Stopped(ERROR_ENCODER)` with error details (spec §11).
- On `stopStream()`: disconnect RTMP, release encoder, stop camera, transition to `Stopped(USER_REQUEST)`.
- FGS notification: delegate to `NotificationController` (can be a minimal stub initially).
- On `onDestroy()`: ensure full cleanup — release camera, encoder, RTMP connection.
- `onStartCommand` returns `START_NOT_STICKY` (do not silently restart).
- Bind/unbind via `onBind()` returning a `Binder` that exposes `StreamingServiceControl`.
- The binder must emit real `StateFlow<StreamState>` and `StateFlow<StreamStats>` so downstream tasks (T-009, T-010) can code against the flows immediately.

**Success Criteria:**
- Service compiles with Hilt injection.
- Unit tests verify state transitions (Idle→Connecting→Live→Stopped).
- No credentials appear in Intent extras or logs.
- `./gradlew testFossDebugUnitTest --tests '*StreamingService*'` passes.
- T-009 and T-010 agents can bind to the service and observe StateFlows without requiring RtmpCamera2.

---

### Agent Prompt for T-007b — StreamingService (RtmpCamera2 Integration)

**Context:** T-007a delivered the StreamingService FGS with a stub encoder bridge. This task integrates the real RootEncoder `RtmpCamera2` replacing the stub, wires camera/audio capture, implements 1 Hz stats polling, and handles camera HAL quirks.

**Your Task:** Replace the `EncoderBridge` stub with a real `RtmpCamera2` implementation.

**Input Files/Paths:**
- `app/src/main/kotlin/com/port80/app/service/StreamingService.kt` (from T-007a)
- `app/src/main/kotlin/com/port80/app/service/EncoderBridge.kt` (replace stub impl)

**Requirements:**
- Implement `RtmpCamera2EncoderBridge` that wraps all `RtmpCamera2` calls.
- Configure resolution, fps, bitrate, audio settings from `StreamConfig`.
- Wire RootEncoder callbacks: `onConnectionSuccess`, `onConnectionFailed`, `onDisconnect`, `onAuthError`, `onNewBitrate`. Map each to `StreamState` transitions.
- Stats collection: launch a coroutine in `serviceScope` that polls RootEncoder metrics every 1 second and emits to `StreamStats` StateFlow.
- Register `MediaCodec.Callback.onError()` (or equivalent RootEncoder error callback). On encoder error: attempt one reconfigure with current settings. If that fails: emit `Stopped(ERROR_ENCODER)`.
- Camera may be unavailable if another app holds it; catch `CameraAccessException` and offer audio-only.
- Pass `RtmpCamera2` reference to `EncoderController` at construction time (factory method, not Hilt injection).

**Success Criteria:**
- Stream to test RTMP server works end-to-end.
- Stats update at 1 Hz in HUD.
- Encoder error triggers one re-init attempt.

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
- **Do NOT define a separate `ConnectionState` type.** ConnectionManager drives transitions directly on the service's `MutableStateFlow<StreamState>` (e.g., emitting `StreamState.Reconnecting`, `StreamState.Connecting`). This avoids dual-state-tracking bugs between `ConnectionState` and `StreamState`.

**Success Criteria:**
- Unit tests for: backoff timing sequence, jitter bounds, max retry cap, user-stop cancels retries, auth failure stops retries, Doze suppression, `onAvailable()` immediate retry.
- `./gradlew testFossDebugUnitTest --tests '*ConnectionManager*'` passes.

---

### Agent Prompt for T-020 — EncoderController (Mutex-Serialized Quality Changes)

**Context:** StreamCaster has two systems that can trigger encoder restarts: the ABR system (network congestion) and the thermal monitoring system (device overheating). Both can fire simultaneously. Concurrent `MediaCodec.release()`/`configure()`/`start()` calls will crash with `IllegalStateException`. All quality changes must be serialized.

**Your Task:** Implement `EncoderController` as the single point of control for all encoder quality changes. This component owns the complete public API including `requestAbrChange()` and `requestThermalChange()`. T-019 (ABR) delivers an `AbrPolicy`/`AbrLadder` class that decides _when_ to step, and calls `EncoderController.requestAbrChange()` to apply changes.

**Input Files/Paths:**
- `app/src/main/kotlin/com/port80/app/service/EncoderController.kt`
- References: `data/model/StreamConfig.kt`, `data/model/StreamStats.kt`, `service/EncoderBridge.kt`

**Requirements:**
- Use a coroutine `Mutex` to serialize all quality-change requests.
- Two entry points: `requestAbrChange(newBitrateKbps: Int, newResolution: Resolution?, newFps: Int?)` and `requestThermalChange(newResolution: Resolution, newFps: Int)`.
- **RtmpCamera2 access:** `StreamingService` passes a reference to `EncoderBridge` (which wraps `RtmpCamera2`) at construction time via a factory method. **Do NOT inject RtmpCamera2 via Hilt** — it's not a singleton; it's created at stream-start time. EncoderController calls `EncoderBridge.setVideoBitrateOnFly()`, `EncoderBridge.stopPreview()`, `EncoderBridge.startPreview(surface)`, etc.
- Bitrate-only changes (no resolution/fps change): apply directly via `encoderBridge.setVideoBitrateOnFly(bitrateKbps)` — no encoder restart, no cooldown.
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

**Entry Criteria:** Empty workspace, spec finalized. OQ-01 (RootEncoder version) and OQ-05 (Compose BOM version) resolved.

**Exit Criteria:**
- `./gradlew assembleFossDebug assembleGmsDebug` pass.
- All data models and interface contracts compile (including `AudioSourceManager`, `EncoderBridge`).
- Hilt DI modules wire correctly.
- Manifest contains all permissions and FGS declarations.
- F-Droid flavor CI check passes.
- `CredentialSanitizer` fully implemented with unit tests (owned by T-038).

**Tasks:** T-001, T-002, T-003, T-013, T-025a, T-032, T-033, T-038

**Risks:** RootEncoder Gradle dependency resolution from JitPack may be flaky. **Rollback:** pin exact commit hash instead of version tag.

---

### Milestone 2: Data + Capabilities (Days 3–6)
**Goal:** Persistent settings, encrypted credential storage, device capability enumeration, and ACRA crash reporting working.

**Entry Criteria:** Milestone 1 complete. T-025a spike report available.

**Exit Criteria:**
- SettingsRepository reads/writes all preferences.
- EndpointProfileRepository encrypts/decrypts profile data (all methods `suspend`).
- DeviceCapabilityQuery returns valid camera and codec info on test devices.
- ACRA configured with programmatic `CoreConfigurationBuilder`, consuming `CredentialSanitizer` from T-038.
- Overlay stub compiles.
- Unit tests pass for all components.

**Tasks:** T-004, T-005, T-006, T-014, T-026, T-037

**Risks:** EncryptedSharedPreferences may behave differently on various OEM devices. **Rollback:** document device-specific quirks; no plaintext fallback.

---

### Milestone 3: Core Streaming (Days 6–16)
**Goal:** End-to-end streaming works: camera preview, RTMP connect, live stream, stop.

**Entry Criteria:** Milestone 2 complete. OQ-03 (test RTMP server) resolved.

**Exit Criteria:**
- StreamingService FGS lifecycle and state machine work (T-007a).
- RtmpCamera2 integrated and streaming to test server (T-007b).
- StreamViewModel binds and reflects state.
- Camera preview visible in Compose UI.
- Start/Stop functional from UI.
- FGS notification shows with state and stats.
- End-to-end stream to test RTMP server succeeds (OQ-03 validation).

**Tasks:** T-007a, T-007b, T-008, T-009, T-010, T-011, T-012, T-036

**Schedule detail:** T-007a (2d) unblocks T-008, T-009, T-010, T-020 in parallel. T-007b (2d) runs after T-007a. T-010 (2d) → T-011 (2d) → T-012 (2d) is the longest serial chain at 6d. Total: 2d (T-007a) + 6d (T-010→T-011→T-012) = 8d from milestone start, plus T-007b running in parallel.

**Integration buffer:** 2 days (Days 15–16) for integration testing: end-to-end stream to test RTMP server, fix interface mismatches, resolve threading bugs.

**Risks:** RootEncoder `RtmpCamera2` integration may require debugging camera HAL quirks. **Rollback:** test on emulator first, then physical devices. T-007a/T-007b split mitigates: if T-007b is delayed, T-009/T-010/T-020 proceed against the stub.

---

### Milestone 4: Settings & Configuration UI (Days 10–14)
**Goal:** All settings screens functional, navigation complete, endpoint profiles savable.

**Entry Criteria:** T-004, T-005, T-006 complete (from Milestone 2).

**Exit Criteria:**
- Video/Audio/General settings screens render with device-filtered options.
- Endpoint setup screen saves profiles with encrypted credentials.
- Navigation between all screens works.
- OEM battery optimization guidance dialog functional.

**Tasks:** T-015, T-016, T-017, T-040

**Risks:** Low risk. **Rollback:** N/A.

---

### Milestone 5: Resilience (Days 16–24)
**Goal:** Auto-reconnect, ABR, thermal handling, transport security, all failure modes handled.

**Entry Criteria:** Milestones 3 and 4 complete.

**Exit Criteria:**
- Auto-reconnect survives network drops with correct backoff + Doze awareness.
- ABR ladder steps down on congestion, recovers on bandwidth improvement. Backpressure detection verified.
- Thermal monitoring triggers degradation on API 29+ and API 23–28.
- RTMPS enforced with credentials. Plaintext warning dialog functional.
- Mute/unmute, camera switching, audio focus all work mid-stream.
- Microphone revocation stops stream with `ERROR_AUDIO`.
- Local recording works with insufficient storage handling.
- Mid-stream media mode transitions work.
- Internal metrics collection operational.

**Tasks:** T-018, T-019, T-020, T-021, T-022, T-023, T-024, T-025, T-028, T-029, T-030, T-031, T-039, T-041, T-042

**Integration buffer:** 2 days (Days 23–24) for cross-feature integration testing.

**Risks:** EncoderController concurrent access is highest-risk item. **Rollback:** fall back to single-threaded command queue if Mutex approach introduces deadlocks.

---

### Milestone 6: Hardening (Days 24–30)
**Goal:** Security audit, process death recovery, NFR measurements, release readiness.

**Entry Criteria:** Milestones 3–5 functionally complete.

**Exit Criteria:**
- Process death with surviving service: activity rebinds, preview restores, stats reflect.
- ACRA reports contain zero credential occurrences (verified by unit test).
- No credentials in Intent extras (verified by test).
- `android:allowBackup="false"` confirmed.
- ProGuard/R8 release build succeeds.
- API 31+ FGS start restrictions verified on emulator (IT-09).
- Thermal stress test on physical device passes.
- **NFR measurements completed:** startup time < 2s (NF-01), battery drain ≤ 15%/hr (NF-03), APK < 15 MB per ABI (NF-05).
- All AC-01 through AC-19 acceptance criteria verified.
- All AC-01 through AC-19 acceptance criteria verified.

**Tasks:** T-027, T-034, T-035

**Risks:** Process death recovery is fragile across OEM devices. **Rollback:** document known unsupported devices; ensure graceful degradation (show idle state rather than crash).

---

## 7. Detailed Task Playbooks

### Playbook 1: T-007a — StreamingService (FGS Lifecycle & State Machine)

**Why this task is risky:**
The FGS is the architectural spine. Incorrect lifecycle management causes ANRs, silent stream death, or OS-forced kills. API 31+ FGS start restrictions add complexity. By splitting RootEncoder integration into T-007b, we ensure the state machine and binding interface are available to downstream tasks without being blocked by camera HAL issues.

**Implementation Steps:**
1. Create `StreamingService` extending `Service`, annotated `@AndroidEntryPoint`.
2. Define `MutableStateFlow<StreamState>` initialized to `Idle` and `MutableStateFlow<StreamStats>`.
3. Define `EncoderBridge` interface abstracting RtmpCamera2 operations: `startPreview(surface)`, `stopPreview()`, `connect(url, key)`, `disconnect()`, `switchCamera()`, `setVideoBitrateOnFly(kbps)`, `release()`. Provide a `StubEncoderBridge` that logs calls and returns success.
4. Implement `onStartCommand()`: extract `profileId` from Intent extra. Fetch `EndpointProfile` from repository. Fetch `StreamConfig` from `SettingsRepository`. Validate credentials exist. Call `startForeground()` with notification from `NotificationController`. Return `START_NOT_STICKY`.
5. Implement `startStream(profileId)`: the service reads config internally from `SettingsRepository`. Validate encoder config via `MediaCodecInfo.CodecCapabilities` pre-flight. Call `encoderBridge.connect()`. Transition state: `Idle → Connecting → Live`.
6. Implement `stopStream()`: call `encoderBridge.disconnect()`. Release encoder. Stop camera. Cancel reconnect jobs. Transition to `Stopped(USER_REQUEST)`. Call `stopForeground(STOP_FOREGROUND_REMOVE)`, then `stopSelf()`.
7. Implement `onBind()`: return a `Binder` inner class exposing `this as StreamingServiceControl`.
8. Implement `attachPreviewSurface()`/`detachPreviewSurface()`: complete `CompletableDeferred<SurfaceHolder>`. On attach, call `encoderBridge.startPreview(surface)`. On detach, call `encoderBridge.stopPreview()`. Do NOT retain strong `SurfaceHolder` reference across config changes.
9. On `onDestroy()`: full cleanup — stop stream if active, release all resources, cancel all coroutines.

**Edge Cases and Failure Modes:**
- `startForeground()` must be called within 10 seconds of `onCreate()` on API 31+, or the system throws `ForegroundServiceStartNotAllowedException`.
- If the `profileId` doesn't match any stored profile (e.g., deleted after Intent was created), fail fast with `Stopped(ERROR_AUTH)`.
- Camera may be unavailable if another app holds it; catch `CameraAccessException` and offer audio-only (handled fully in T-007b).
- Encoder may not support the requested config; pre-flight catch prevents this.
- Mid-stream encoder error (spec §11): attempt one re-init with current settings. If re-init fails, emit `Stopped(ERROR_ENCODER)` with error details. (Full implementation in T-007b, but the error callback path must be defined in T-007a's `EncoderBridge` interface.)
- Microphone revocation mid-stream (spec §11): detect `RECORD_AUDIO` revocation, stop stream with `Stopped(ERROR_AUDIO)`. (Handled in T-039, but the `ERROR_AUDIO` stop reason must exist in T-007a's state model.)

**Verification Strategy:**
- Unit tests (stub encoder bridge): state transitions, idempotent commands, no credentials in Intent.
- Instrumented tests: FGS starts and shows notification, survives activity destruction.
- Negative test (API 31+): verify that attempting background FGS start throws expected exception and is handled gracefully (maps to AC-01).
- Manual: bind from Activity, observe StateFlows, verify state transitions.

**Definition of Done:**
- Service starts, state machine transitions work via stub encoder.
- T-009, T-010, and T-020 can bind and code against the StateFlow interfaces.
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
1. Create `EncoderController` with a `Mutex`, a `CoroutineScope`, and an `EncoderBridge` reference (passed at construction time by `StreamingService`).
2. Track `lastThermalRestartTime: Long` for cooldown enforcement.
3. Implement `requestAbrChange(bitrateKbps, resolution?, fps?)`:
   - Acquire `mutex.withLock { }`.
   - If only bitrate changed: call `encoderBridge.setVideoBitrateOnFly(bitrateKbps)`. No restart. No cooldown. Return.
   - If resolution or fps changed: check cooldown (same timer for thermal AND ABR restarts). If within 60s, queue the request. If outside 60s, execute restart sequence. Update `lastRestartTime`.
4. Implement `requestThermalChange(resolution, fps)`:
   - Acquire `mutex.withLock { }`.
   - Check cooldown. If within 60s of last restart, queue. Otherwise, execute restart sequence. Update `lastThermalRestartTime`.
5. Restart sequence (inside Mutex lock):
   a. `encoderBridge.stopPreview()`
   b. `encoderBridge.stopStream()` (video track only if possible, else full)
   c. Reconfigure encoder with new resolution/fps/bitrate.
   d. `encoderBridge.startPreview(surface)`
   e. `encoderBridge.startStream(url)` — reconnect with new params.
   f. Request IDR frame.
   g. If reconfigure fails: try one ABR step lower. If that fails: emit `Stopped(ERROR_ENCODER)`.
6. Expose `StateFlow<EffectiveQuality>` reflecting current resolution/fps/bitrate.
7. Implement cooldown queue processing: launch a coroutine that, after cooldown expires, dequeues and applies the latest pending request (coalescing multiple requests into one).

**Edge Cases and Failure Modes:**
- If `mutex.withLock` is held while RootEncoder callbacks fire on a different thread, ensure no deadlock by using `Dispatchers.Default` for the Mutex scope and not calling Mutex-guarded code from within RootEncoder callbacks. EncoderController accesses RtmpCamera2 only through the `EncoderBridge` interface.
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
2. **Consume** the existing `CredentialSanitizer` from T-038 (do NOT redefine it). The sanitizer already handles all regex patterns and `sanitize()` function.
3. Create `AcraConfigurator` in `App.kt`:
   - Use **programmatic `CoreConfigurationBuilder`** in `Application.attachBaseContext()`, guarded by `!BuildConfig.DEBUG`. Do NOT use `@AcraHttpSender` annotation (annotations cannot be conditionally applied at runtime).
   - Configure `HttpSenderConfigurationBuilder` with HTTPS-only endpoint.
   - Set `reportContent` to explicitly list only safe fields: `STACK_TRACE`, `ANDROID_VERSION`, `APP_VERSION_CODE`, `APP_VERSION_NAME`, `PHONE_MODEL`, `BRAND`, `PRODUCT`, `CUSTOM_DATA`, `CRASH_CONFIGURATION`, `BUILD_CONFIG`, `USER_COMMENT`.
   - **Exclude** `SHARED_PREFERENCES`, `LOGCAT`, `DUMPSYS_MEMINFO`, `THREAD_DETAILS` from all configurations.
   - Register a custom `ReportTransformer` that calls `CredentialSanitizer.sanitize()` (from T-038) on every string-valued field before serialization.
4. Wrap ACRA initialization in try-catch — if it fails (e.g., misconfigured endpoint), the app must still launch without crashing.
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
     * @param isMuted true when audio is muted (lives here for immediate UI/notification propagation)
     */
    data class Live(
        val cameraActive: Boolean = true,
        val isMuted: Boolean = false
    ) : StreamState()

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
    ERROR_AUDIO,
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
     * The service fetches credentials from EndpointProfileRepository
     * and config from SettingsRepository internally — no secrets or config in this call.
     * No-op if already streaming or connecting.
     *
     * @param profileId ID of the EndpointProfile to use
     */
    fun startStream(profileId: String)

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
    fun createNotification(state: StreamState, stats: StreamStats): Notification

    /** Update the notification to reflect new state and real-time stats (bitrate, connection quality). */
    fun updateNotification(state: StreamState, stats: StreamStats)

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

### 8.6 Audio Source Manager Interface

```kotlin
// File: audio/AudioSourceManager.kt
package com.port80.app.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Shared contract for audio mute/unmute.
 * Used by T-029 (Mute Toggle) and T-022 (Audio Focus).
 * StreamingService owns the implementation.
 */
interface AudioSourceManager {

    /** Current mute state. Observed by UI and NotificationController. */
    val isMuted: StateFlow<Boolean>

    /** Mute the microphone. No-op if already muted. */
    fun mute()

    /** Unmute the microphone. No-op if already unmuted. */
    fun unmute()
}
```

### 8.7 Encoder Bridge Interface

```kotlin
// File: service/EncoderBridge.kt
package com.port80.app.service

import android.view.SurfaceHolder
import com.port80.app.data.model.Resolution

/**
 * Abstraction over RtmpCamera2 operations.
 * T-007a provides a StubEncoderBridge. T-007b replaces it with
 * RtmpCamera2EncoderBridge wrapping real RootEncoder calls.
 * EncoderController accesses RtmpCamera2 only through this interface.
 */
interface EncoderBridge {
    fun startPreview(holder: SurfaceHolder)
    fun stopPreview()
    fun connect(url: String, streamKey: String)
    fun disconnect()
    fun switchCamera()
    fun setVideoBitrateOnFly(bitrateKbps: Int)
    fun release()
    fun isStreaming(): Boolean
}
```

---

## 9. Test Strategy Mapped to Tasks

### Unit Tests

| Test ID | Related Tasks | What is Being Proven | Min Environment | Pass/Fail Signal |
|---|---|---|---|---|
| UT-01 | T-004 | SettingsRepository reads/writes all prefs correctly | JVM + Robolectric | All prefs round-trip |
| UT-02 | T-005 | EndpointProfileRepository CRUD, encryption, Keystore failure detection | JVM + Robolectric | Profile saves/loads; Keystore absence returns `isKeystoreAvailable() == false` |
| UT-03 | T-007a, T-036 | StreamingService state transitions, no credentials in Intent | JVM + stub EncoderBridge | State machine transitions match spec; Intent extras contain only profile ID |
| UT-04 | T-009 | ConnectionManager backoff sequence, jitter bounds, Doze suppression, auth stop | JVM | Backoff sequence: 3, 6, 12, 24, 48, 60, 60. Auth failure → no retry. Doze → skips timer |
| UT-05 | T-020 | EncoderController serializes concurrent requests, cooldown enforced | JVM + coroutine test | No concurrent restarts. Cooldown blocks within 60s. Bitrate-only bypasses |
| UT-06 | T-021 | ThermalMonitor emits correct ThermalLevel on status changes | JVM | Level transitions match threshold map |
| UT-07 | T-026 | CredentialSanitizer redacts all URL/key patterns | JVM | Zero occurrences of test key in output |
| UT-08 | T-026 | ACRA report fields exclude LOGCAT, SHARED_PREFERENCES | JVM | Excluded fields not in report content list |
| UT-09 | T-010 | StreamViewModel correctly gates preview on surface readiness | JVM + Turbine | `attachPreviewSurface` called only after `CompletableDeferred` completes |
| UT-10 | T-022 | Audio focus loss mutes mic via AudioSourceManager, resume only on explicit user unmute. No-op if already manually muted | JVM | State reflects muted on focus loss; already-muted is no-op |
| UT-11 | T-023 | Battery below threshold triggers warning, ≤ 2% triggers stop | JVM | State transitions at correct thresholds |
| UT-12 | T-019 | ABR ladder steps down correctly, recovers on bandwidth improvement | JVM | Step sequence matches defined ladder |
| UT-13 | T-038 | Structured logger redacts secrets at all log levels | JVM | No secrets in formatted log output |
| UT-14 | T-019 | Encoder backpressure detection: output fps < 80% of configured fps for 5s triggers ABR step-down | JVM | Mock encoder at 22 fps with configured 30 fps → ABR fires after 5s. 25 fps (83%) → no fire |
| UT-15 | T-039 | Microphone revocation mid-stream triggers Stopped(ERROR_AUDIO) | JVM | State transition to Stopped(ERROR_AUDIO) on RECORD_AUDIO revocation |
| UT-16 | T-029 | Mute toggle updates StreamState.Live(isMuted) immediately | JVM + Turbine | StateFlow emits updated isMuted within same frame |

### Instrumented Tests

| Test ID | Related Tasks | What is Being Proven | Min Environment | Pass/Fail Signal |
|---|---|---|---|---|
| IT-01 | T-006 | DeviceCapabilityQuery returns valid resolutions/fps on real hardware | Physical device API 23 + API 35 | Non-empty results, all values within hardware capability |
| IT-02 | T-007a | FGS starts with notification, survives activity destruction | Emulator API 31+ | FGS running after `finish()` |
| IT-03 | T-013 | Permission grant/denial flow works correctly | Emulator API 33+ | Stream starts after grant; shows rationale after denial |
| IT-04 | T-014 | Orientation lock holds during active stream | Physical device | No orientation change during stream |
| IT-05 | T-024 | Camera revocation switches to audio-only | Emulator API 30+ using `adb shell pm revoke com.port80.app android.permission.CAMERA`. Documents which callback sequence this triggers (`CameraDevice.StateCallback.onDisconnected()`). **Note:** real OEM camera revocation (another app grabbing camera) requires separate manual test | Audio stream continues; notification shows "Camera paused" |
| IT-06 | T-027 | Process death recovery rebinds and restores preview | Emulator + `adb shell am kill` | Preview visible within 2 seconds |
| IT-07 | T-025 | Local recording produces playable MP4 | Physical device API 29+ | MP4 opens in video player |
| IT-08 | T-012 | Compose UI renders all controls, HUD updates | Emulator | Compose test assertions pass |
| IT-09 | T-007a | FGS start restriction compliance (AC-01): verify that background FGS start attempt on API 31+ throws `ForegroundServiceStartNotAllowedException` and is handled gracefully | Emulator API 31+ | BroadcastReceiver calling `startForegroundService()` from background produces expected exception, no crash |
| IT-10 | T-011 | Camera preview SurfaceHolder lifecycle: `surfaceCreated` → `attachPreviewSurface` → no crash. Verify CompletableDeferred surface-ready gating works | Emulator | SurfaceView renders, no IllegalArgumentException |
| IT-11 | T-029 | Mute state propagation: service mute → StateFlow.Live(isMuted=true) → UI observes immediately | Emulator | Mute state visible in UI within 100ms |
| IT-12 | T-030 | Camera ID switches correctly during preview and during stream | Emulator with front+back camera | Camera ID changes, no crash |
| IT-13 | T-039 | Microphone revocation mid-stream via `adb shell pm revoke com.port80.app android.permission.RECORD_AUDIO` on API 30+ | Emulator API 30+ | Stream stops with error message, no crash |

### Device Matrix Tests (E2E Manual)

| Test ID | Related Tasks | What is Being Proven | Device Matrix | Pass/Fail Signal |
|---|---|---|---|---|
| DM-01 | T-007a, T-007b, T-009, T-018 | Full stream lifecycle over RTMP | Low-end API 23, mid-range API 28, flagship API 35 | Stream starts, runs 5 min, stops cleanly |
| DM-02 | T-007a, T-007b, T-009, T-018 | Full stream lifecycle over RTMPS | Same 3 devices | Stream starts, runs 5 min, stops cleanly |
| DM-03 | T-007b | Video-only, audio-only, video+audio modes | Mid-range API 28 | Each mode streams correctly |
| DM-04 | T-021 | Thermal degradation under sustained load | Physical device. **API 29+:** use `adb shell cmd thermalservice override-status <level>` (requires debug build). **API 23–28:** mock `ACTION_BATTERY_CHANGED` with elevated `EXTRA_TEMPERATURE` in instrumented test | Quality steps down, no crash |
| DM-05 | T-009 | Reconnect after network drop + Doze | Physical device, airplane mode toggle | Reconnects within expected backoff window |

### Failure Injection Scenarios

| Test ID | Related Tasks | What is Being Proven | Method | Pass/Fail Signal |
|---|---|---|---|---|
| FI-01 | T-009 | Network loss during stream | Toggle airplane mode | Reconnecting state shown, stream resumes on network restore |
| FI-02 | T-020 | Concurrent ABR + thermal event | Mock both signals simultaneously | No crash, requests serialized |
| FI-03 | T-007b | Encoder failure mid-stream | Force unsupported config change or mock `MediaCodec.CodecException` | One re-init attempted, then Stopped(ERROR_ENCODER) |
| FI-04 | T-027 | Process death with active stream | `adb shell am kill` | Preview rebinds, stats restore |
| FI-05 | T-024 | Camera revocation in background | Revoke permission via Settings | Audio-only continues, video resumes on foreground return |
| FI-06 | T-023 | Battery critical during stream | Mock battery level ≤ 2% | Stream auto-stops, recording finalized |
| FI-07 | T-039 | Microphone revocation mid-stream | `adb shell pm revoke com.port80.app android.permission.RECORD_AUDIO` on API 30+ | Stream stops with `Stopped(ERROR_AUDIO)`, error surfaced to user |

---

## 10. Operational Readiness Checklist

### Security
- [ ] `android:allowBackup="false"` in manifest.
- [ ] No custom `X509TrustManager` in codebase (grep: `TrustManager`, `X509`).
- [ ] EncryptedSharedPreferences used for all credential storage (grep: `SharedPreferences` — only `EncryptedSharedPreferences` for secrets).
- [ ] No stream key, password, or auth token in any Intent extra (grep: `putExtra.*key`, `putExtra.*pass`, `putExtra.*auth`).
- [ ] ACRA excludes `LOGCAT` and `SHARED_PREFERENCES` in release config.
- [ ] ACRA uses programmatic `CoreConfigurationBuilder`, not annotation-based config.
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
- [ ] `EncoderController` accesses RtmpCamera2 only through `EncoderBridge` interface.
- [ ] 60-second cooldown between thermal-triggered encoder restarts.
- [ ] Process death with surviving service: preview rebinds within 2 seconds.
- [ ] `CompletableDeferred<SurfaceHolder>` gates `startPreview()`.
- [ ] No zombie notifications after service stops.
- [ ] Notification actions debounced ≥ 500ms.
- [ ] Microphone revocation mid-stream stops with `Stopped(ERROR_AUDIO)`.
- [ ] Encoder error mid-stream triggers one re-init attempt before `Stopped(ERROR_ENCODER)`.
- [ ] Insufficient storage during recording stops recording, continues streaming, notifies user.

### Performance
- [ ] Startup to preview < 2 seconds on mid-range device (measured, NF-01).
- [ ] Battery drain ≤ 15%/hr during 1080p30 stream (measured, NF-03).
- [ ] APK size < 15 MB per ABI for `foss` variant (measured, NF-05).
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

### OEM Compatibility
- [ ] OEM battery optimization guidance dialog shows on Samsung/Xiaomi/Huawei (T-040).
- [ ] `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` requested appropriately.
- [ ] FGS start restriction compliance verified on API 31+ (IT-09).

---

## 11. Open Questions and Blockers

| Blocker ID | What is Missing | Impacted Tasks | Proposed Decision Owner | Resolution Deadline |
|---|---|---|---|---|
| OQ-01 | RootEncoder v2.7.x exact version: spec says 2.7.x but the latest as of March 2026 needs to be verified from JitPack/GitHub. If 2.7.0 is unavailable, must determine exact artifact coordinates | T-001, T-007b, T-019 | Tech Lead | **Day 0** (before T-001 starts) |
| OQ-02 | ACRA self-hosted endpoint URL: spec says "self-hosted HTTP endpoint or email." No endpoint URL is specified. Needed for `AcraConfigurator` | T-026 | Product Owner | **Day 3** (before T-026 starts) |
| OQ-03 | Test RTMP server for development: an ingest endpoint is needed for E2E testing. Options: Nginx RTMP module locally, or a shared staging endpoint | T-007b, T-009, T-035 | DevOps | **Day 6** (Milestone 3 start) |
| OQ-04 | RootEncoder's exact API for tee-ing encoded buffers to MP4 muxer without a second encoder: `RtmpCamera2` may support this via `startRecord()`, but API compatibility needs verification | T-025 | Tech Lead | **Day 2** (T-025a spike resolves this) |
| OQ-05 | Compose BOM version 2025.03.xx: exact version needs to be resolved. Spec uses placeholder | T-001 | Tech Lead | **Day 0** (before T-001 starts) |
| OQ-06 | Brand icon assets: spec describes geometric camera lens + broadcast arcs with specific colors, but no asset files exist. Needed for launcher icon and notification icon | T-001, T-008 | Designer | **Day 14** (use placeholder assets until then) |
| OQ-07 | RootEncoder `GlStreamInterface` for overlay architecture: exact API surface for `onDrawFrame(canvas)` needs to be verified against v2.7.x | T-037 | Tech Lead | **Day 6** (non-blocking; overlay is stub only) |
| OQ-08 | SAF (`ACTION_OPEN_DOCUMENT_TREE`) UX: should the SAF picker launch immediately when recording is toggled, or on first stream start with recording enabled? Spec says "immediately" on toggle (§4.1 MC-05) | T-025 | Product Owner | **Day 16** (before T-025 starts in Milestone 5) |

---

## 12. First 96-Hour Execution Starter

### Day 1 (Hours 0–8)

**Parallel tracks (3 agents):**

| Agent | Task | Goal |
|---|---|---|
| Agent A | T-001: Project Scaffolding | Buildable project with both flavors, manifest, Hilt app class |
| Agent B | T-002: Data Models & Interfaces | All sealed classes, data classes, interfaces compilable (including AudioSourceManager, EncoderBridge) |
| Agent C | T-038: Structured Logging + T-032: Manifest Hardening | CredentialSanitizer (fully owned by T-038) + logging utility + manifest security baseline |

**End of Day 1 artifacts:**
- `./gradlew assembleFossDebug assembleGmsDebug` passes.
- All data model and interface files exist in correct packages (including `AudioSourceManager`, `EncoderBridge`).
- Manifest contains all permissions, FGS declaration, `allowBackup="false"`.
- `CredentialSanitizer` fully implemented with unit tests. Logging utility compiles.

### Day 2 (Hours 8–16)

**Parallel tracks (4 agents):**

| Agent | Task | Goal |
|---|---|---|
| Agent A | T-003: Hilt DI Modules | All interfaces wired with Hilt |
| Agent B | T-013: Permissions Handler + T-033: F-Droid CI | Permission flow + CI flavor check (don't need T-003) |
| Agent C | T-025a: Recording API Spike | Verify RootEncoder `startRecord()` API, document findings |
| Agent D | T-026: ACRA (consume CredentialSanitizer from T-038) | Programmatic ACRA config, ReportTransformer wired |

**Note:** Agents B, C, D work on tasks that depend only on T-001 (not T-003), so they don't idle. Agent A completes T-003 within 2–3 hours, then starts T-004 (SettingsRepository) for the remainder of Day 2.

**End of Day 2 artifacts:**
- DI graph compiles and wires correctly.
- Permission handler composable exists.
- T-025a spike report: RootEncoder recording API verified or alternative documented.
- ACRA configured with `CoreConfigurationBuilder`, credential sanitizer integrated.
- F-Droid CI check script exists and passes.
- SettingsRepository in progress (Agent A, afternoon).

### Day 3 (Hours 16–24)

**Parallel tracks (4 agents):**

| Agent | Task | Goal |
|---|---|---|
| Agent A | T-004: SettingsRepository (finish) + T-005: EndpointProfileRepository (start) | DataStore preferences + encrypted CRUD |
| Agent B | T-006: DeviceCapabilityQuery | Camera/codec query impl |
| Agent C | T-007a: StreamingService FGS Lifecycle & State Machine | FGS lifecycle, state machine, EncoderBridge stub, service binding. Binder emits real StateFlows. **No RtmpCamera2** |
| Agent D | T-015: Settings Screens (start) | Video/Audio/General settings UI (Compose), reading from SettingsRepository |

**End of Day 3 artifacts:**
- SettingsRepository save/load round-trip verified in unit test.
- EndpointProfileRepository started (encrypted CRUD in progress).
- DeviceCapabilityQuery compiles (instrumented test deferred to device availability).
- T-007a compiles with Hilt, state machine unit tests pass, FGS notification stub exists.
- Binder returns real `StateFlow<StreamState>` and `StateFlow<StreamStats>` — T-009 and T-010 can code against them.
- Settings screens started.

### Day 4 (Hours 24–32)

**Parallel tracks (4 agents):**

| Agent | Task | Goal |
|---|---|---|
| Agent A | T-005: EndpointProfileRepository (finish) + T-036: Intent Security | Credential encryption verified + no-credentials-in-intent test |
| Agent B | T-009: ConnectionManager | RTMP connect/reconnect with backoff, Doze awareness, drives StreamState directly |
| Agent C | T-010: StreamViewModel | Service binding, StateFlow projection, surface management |
| Agent D | T-020: EncoderController | Mutex-serialized quality changes, EncoderBridge access via constructor injection |

**Note:** Agents B, C, D all depend on T-007a (completed Day 3) and can start immediately. T-007b (RtmpCamera2 integration) is not needed yet — these agents code against the `EncoderBridge` interface and `StreamingServiceControl` StateFlows.

**End of Day 4 artifacts:**
- EndpointProfileRepository credential encryption/decryption verified in unit test.
- Intent security test passes.
- ConnectionManager unit tests pass (backoff, jitter, auth failure, Doze).
- StreamViewModel gates preview on surface readiness.
- EncoderController serializes concurrent requests, cooldown enforced.
- **Total: 16 of 44 tasks started, 12+ completed.**

### Critical Path Status After 96 Hours
```
T-001 ✅ → T-003 ✅ → T-005 ✅ → T-007a ✅ → T-009 🔄 (in progress)
                                    ↓          T-010 🔄 (in progress)
                                    ↓          T-020 🔄 (in progress)
                                    ↓
                            T-007b (ready to start Day 5)
```

The critical path is on track. Day 5 proceeds with:
- T-007b (RtmpCamera2 integration) — completing the full streaming pipeline.
- T-011 (Camera Preview) — once T-010 is done.
- T-015/T-016 (Settings/Endpoint screens) — parallel UI track.

The T-007a/T-007b split recovered 2 days on the critical path by unblocking T-009, T-010, and T-020 before RtmpCamera2 is integrated.
