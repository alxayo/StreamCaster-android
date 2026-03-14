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
- **RtmpCamera2** (from RootEncoder) is the **sole camera owner**. Never use CameraX or Camera1. `DeviceCapabilityQuery` reads camera/codec info but never opens the camera.
- All encoder quality changes (ABR and thermal) are serialized through `EncoderController` using a coroutine `Mutex`. Never call `MediaCodec.release()`/`configure()`/`start()` outside of `EncoderController`.

## Source of Truth Boundaries

| Data | Owner | Storage |
|------|-------|---------|
| Stream state (`StreamState`) | `StreamingService` via `StateFlow` | In-memory |
| User settings | `SettingsRepository` | Jetpack DataStore |
| Credentials & profiles | `EndpointProfileRepository` | EncryptedSharedPreferences (Keystore-backed) |
| Device capabilities | `DeviceCapabilityQuery` | Read-only queries to Camera2 + MediaCodecList |

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

# F-Droid GMS check (must return zero matches)
./gradlew :app:dependencies --configuration fossReleaseRuntimeClasspath | grep -i gms

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
