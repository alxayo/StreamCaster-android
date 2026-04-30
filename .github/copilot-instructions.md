# StreamCaster — Project Guidelines

## Project Identity

- **Package:** `com.port80.app`
- **App:** StreamCaster — native Android RTMP/RTMPS streaming app
- **SDK targets:** minSdk 23, targetSdk 35, compileSdk 35
- **Build:** Gradle Kotlin DSL, version catalog at `gradle/libs.versions.toml`
- **Flavors:** `foss` (F-Droid, no GMS) and `gms` (Play Store)

## Architecture

- **MVVM** with Hilt DI, Jetpack Compose UI, Android ViewModel + StateFlow.
- **Single Activity** (`MainActivity`) with Compose Navigation.
- **StreamingService** (foreground service) is the **single source of truth** for all stream state. UI is a read-only observer.
- **RtmpCamera2** (from RootEncoder) is the **sole camera owner** for streaming and preview. Never use CameraX or Camera1 for the streaming camera path. The only approved exception is the QR endpoint-import scanner, which may use CameraX while no stream is active.
- All encoder quality changes (ABR and thermal) are serialized through `EncoderController` using a coroutine `Mutex`. Never call `MediaCodec.release()`/`configure()`/`start()` outside of `EncoderController`.

## Source of Truth Boundaries

| Data | Owner | Storage |
|------|-------|---------|
| Stream state (`StreamState`) | `StreamingService` via `StateFlow` | In-memory |
| User settings | `SettingsRepository` | Jetpack DataStore |
| Credentials & profiles | `EndpointProfileRepository` | EncryptedSharedPreferences (Keystore-backed) |
| Device capabilities | `DeviceCapabilityQuery` | Read-only queries to Camera2 + MediaCodecList |
| Active-stream gate for QR scanning | `StreamingService` via `ActiveStreamStateProvider` | In-memory `StateFlow<Boolean>` |

## QR Scanner Exception

- QR endpoint import may use CameraX and bundled ML Kit barcode scanning (`com.google.mlkit:barcode-scanning`) in both `foss` and `gms` flavors.
- This exception is limited to the settings endpoint-import scanner. Streaming, preview, encoder setup, and service code must continue to use RootEncoder's `RtmpCamera2` path.
- QR scanning must be blocked while a stream or preview-owned camera session is active. The scanner reads `ActiveStreamStateProvider` and never opens the streaming camera.
- Bundled ML Kit is intentionally accepted in the `foss` flavor for offline QR scanning. Its required `com.google.android.gms:*` transitive dependencies are allowlisted only as part of the ML Kit barcode-scanning subtree; do not add unrelated Play Services APIs to `foss`.

## Security — Hard Rules

These are non-negotiable. Every PR must satisfy them:

1. **No plaintext credential storage.** All stream keys, passwords, and auth tokens use EncryptedSharedPreferences. No fallback to plain SharedPreferences.
2. **No credentials in Intent extras.** The FGS start Intent carries only a profile ID string. The service reads credentials from `EndpointProfileRepository` at runtime.
3. **No custom TrustManager.** RTMPS uses the system default `TrustManager`. Never implement `X509TrustManager` that accepts all certificates.
4. **Redact secrets in all logs.** Use `CredentialSanitizer` for any string that may contain RTMP URLs, stream keys, or auth tokens. This applies to both app logs and ACRA crash reports.
5. **`android:allowBackup="false"`** in the manifest.
6. **ACRA excludes `LOGCAT` and `SHARED_PREFERENCES`** from report fields.

## API Level Branching

Always branch on `Build.VERSION.SDK_INT` for API-conditional behavior:

- **API 29+:** `OnThermalStatusChangedListener`, MediaStore/SAF for recording
- **API 23–28:** `BatteryManager.EXTRA_TEMPERATURE` for thermal, `getExternalFilesDir` for recording
- **API 30+:** `android:foregroundServiceType` required on `<service>`
- **API 31+:** FGS start only from foreground user action
- **API 33+:** `POST_NOTIFICATIONS` runtime permission
- **API 34+:** `FOREGROUND_SERVICE_CAMERA` and `FOREGROUND_SERVICE_MICROPHONE` permissions

## Key Patterns

- **State modeling:** Use `sealed class` / `sealed interface` for state (see `StreamState`). Use `enum class` for finite sets (`StopReason`, `ThermalLevel`).
- **Idempotent commands:** All start/stop/mute methods must be no-ops when already in the target state.
- **Surface lifecycle:** Gate `startPreview()` behind a `CompletableDeferred<SurfaceHolder>`. Never call it before `surfaceCreated()`.
- **View references:** Use `WeakReference<SurfaceHolder>` in ViewModel. Never retain strong references to `View`, `Surface`, or `Activity` across lifecycle boundaries.
- **FGS notification actions:** Stop and Mute/Unmute are broadcasts to the running service. "Start" must deep-link to the Activity — never call `startForegroundService()` from a notification action.
- **Reconnect:** Exponential backoff with jitter (3s, 6s, 12s… cap 60s). Cancel all retries on explicit user stop. No retries on auth failure.
- **Thermal cooldown:** 60-second minimum between encoder restarts triggered by thermal events. Bitrate-only changes bypass cooldown.

## Build & Test

```sh
# Build both flavors
./gradlew assembleFossDebug assembleGmsDebug

# Unit tests
./gradlew testFossDebugUnitTest

# F-Droid GMS check: matches must be limited to bundled ML Kit and its transitive support artifacts
./gradlew :app:dependencies --configuration fossReleaseRuntimeClasspath | grep -i "gms\|play-services\|mlkit"

# Instrumented tests
./gradlew connectedFossDebugAndroidTest
```

## Spec & Plan References

- Architecture: `SPECIFICATION.md` §6
- Lifecycle & state: `SPECIFICATION.md` §7
- Media pipeline: `SPECIFICATION.md` §8
- Security: `SPECIFICATION.md` §9
- Task breakdown: `IMPLEMENTATION_PLAN.md` §3 (WBS)
- Data contracts: `IMPLEMENTATION_PLAN.md` §8
