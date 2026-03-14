---
applyTo: "**/ui/**"
description: "Use when working on Compose UI screens, ViewModels, camera preview, HUD, or permission handling in the StreamCaster app. Covers Compose patterns, ViewModel-service binding, and surface lifecycle."
---
# Compose UI Patterns

## ViewModel–Service Binding

- `StreamViewModel` binds to `StreamingService` via `ServiceConnection`.
- Observes `StateFlow<StreamState>` and `StateFlow<StreamStats>` — **never** mutates service state directly.
- All user actions (start, stop, mute, switch camera) delegate to the bound service.
- If the service is dead on bind attempt, show `Idle` state. No automatic stream resumption.

## Surface Lifecycle

- `CameraPreview` uses `AndroidView` wrapping a `SurfaceView` for RootEncoder.
- Gate preview attach on `CompletableDeferred<SurfaceHolder>` — resolved in `surfaceCreated()`.
- On `surfaceDestroyed()`: call `detachPreviewSurface()` and reset the `CompletableDeferred`.
- Hold `SurfaceHolder` as `WeakReference` in ViewModel. Never retain strong `View`/`Surface`/`Activity` references.

## Layout

- **Landscape-first** — controls at the right edge for thumb reach.
- **Portrait** is a user-toggled secondary layout.
- Respect `WindowInsets.displayCutout` and `WindowInsets.navigationBars` on all screens.

## HUD

- Display: live bitrate, FPS, resolution, session duration, connection state, recording state, thermal badge.
- Update from `StreamStats` StateFlow at ~1 Hz. Don't poll faster.

## Permissions

- Request `CAMERA`, `RECORD_AUDIO` at stream start (not app launch).
- Request `POST_NOTIFICATIONS` on API 33+ before starting FGS.
- Show rationale dialogs on denial. Offer mode fallback (e.g., audio-only if camera denied).

## Orientation

- Lock orientation in `Activity.onCreate()` **before** `setContentView()` using the persisted preference.
- Unconditionally lock when a stream is active (on every `onCreate()`) to prevent re-orientation race.
- Unlock only when idle (no active stream).
