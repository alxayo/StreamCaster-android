---
applyTo: "**/service/**"
description: "Use when working on StreamingService, ConnectionManager, EncoderController, NotificationController, or any file in the service layer. Covers FGS lifecycle, state machine, reconnect logic, and encoder serialization."
---
# Service Layer Patterns

## StreamingService is the Source of Truth

- `StreamingService` owns `MutableStateFlow<StreamState>` and `MutableStateFlow<StreamStats>`.
- UI layer (ViewModel) is a **read-only observer** via `StateFlow`.
- All mutations (start, stop, mute, switch camera) go through the service.
- `onStartCommand` returns `START_NOT_STICKY`. Never silently restart after OS kill.

## State Machine

```
Idle → Connecting → Live(cameraActive) → Stopping → Stopped(reason)
                  ↘ Reconnecting(attempt, nextRetryMs) ↗
```

- All commands are **idempotent**: calling `stopStream()` from `Idle` is a no-op.
- `StopReason` enum: `USER_REQUEST`, `ERROR_ENCODER`, `ERROR_AUTH`, `ERROR_CAMERA`, `THERMAL_CRITICAL`, `BATTERY_CRITICAL`.

## FGS Rules

- Call `startForeground()` within 10 seconds of `onCreate()` on API 31+.
- `android:foregroundServiceType="camera|microphone"` on the `<service>` element.
- FGS starts only from a foreground user action (in-app button). Never from a notification broadcast.
- Notification "Stop" and "Mute/Unmute" are broadcasts to the **running** service. "Start" deep-links to the Activity.
- Debounce notification actions ≥ 500ms.

## ConnectionManager — Reconnect

- Exponential backoff: 3s, 6s, 12s, 24s, 48s, 60s (cap). Jitter: ±20%.
- Use `ConnectivityManager.NetworkCallback.onAvailable()` for immediate retry (override timer).
- Suppress timer retries in Doze (`PowerManager.isDeviceIdleMode`).
- **Cancel all retries** on explicit user stop. No zombie retry coroutines.
- **No retries** on auth failure → `Stopped(ERROR_AUTH)`.

## EncoderController — Serialized Quality Changes

- **Single component** for all encoder reconfiguration (ABR + thermal).
- Use `Mutex` — never `synchronized` in coroutine code.
- **Bitrate-only** changes: `setVideoBitrateOnFly()`, no restart, no cooldown.
- **Resolution/FPS** changes: full encoder restart sequence (stop preview → release → reconfigure → restart → IDR). Target ≤ 3s gap.
- **60-second cooldown** between encoder restarts (thermal and ABR resolution/FPS changes). Bitrate-only bypasses.
- On restart failure: try one ABR step lower. If that fails: `Stopped(ERROR_ENCODER)`.

## RtmpCamera2 Ownership

- `StreamingService` is the **sole owner** of `RtmpCamera2`.
- No CameraX. No Camera1. No separate Camera2 sessions.
- Preview attach: `rtmpCamera2.startPreview(surfaceView)` only after `CompletableDeferred<SurfaceHolder>` completes.
- Preview detach: `rtmpCamera2.stopPreview()` on `surfaceDestroyed()`. Continue streaming without preview.
