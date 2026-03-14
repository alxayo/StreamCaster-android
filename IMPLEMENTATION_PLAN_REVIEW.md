# Adversarial Implementation Plan Review — StreamCaster Android

**Reviewed:** 2026-03-14
**Source:** IMPLEMENTATION_PLAN.md (generated 2026-03-14)
**Cross-referenced:** SPECIFICATION.md v2.0 (Hardened)

---

## 1. Task Decomposition and Scope Accuracy

- **[Severity: Critical] T-007 (StreamingService) bundles the entire architectural spine into a single 4-day task.**
  - **The Execution Failure:** T-007 includes FGS lifecycle, RtmpCamera2 ownership and integration, the full state machine, service binding, CompletableDeferred surface gating, 1 Hz stats polling, notification delegation, `onDestroy` cleanup, and `onStartCommand` with profile lookup. RootEncoder integration is listed as the _biggest unknown surface_ in Playbook 1 itself ("RootEncoder integration is the biggest unknowns surface"). Bundling the unknown with the spine means any RootEncoder Camera HAL issue blocks the state machine, which blocks **every Stage 4+ task** (T-008, T-009, T-010, T-019, T-020, T-022–T-025, T-029, T-030, T-036). A single camera quirk on the emulator stalls the entire project.
  - **The Plan Fix:** Split T-007 into T-007a (FGS lifecycle, state machine, service binding, notification delegation — no RootEncoder, uses a stub encoder interface) and T-007b (RtmpCamera2 integration, stats polling, camera HAL handling). T-007a unblocks all downstream consumers; T-007b can run in parallel with T-009 and T-010 since they only need the state machine and binding interface.
  - **The Plan Evidence:** T-007 WBS entry: "Implement FGS with correct type declarations, lifecycle, RtmpCamera2 ownership, StateFlow emission, service binding." Playbook 1 Step 4: "Instantiate `RtmpCamera2`…Configure resolution, fps, bitrate, audio settings. Connect RTMP." OQ-01 confirms the exact RootEncoder version is an _unresolved blocker_.

- **[Severity: High] T-019 (ABR) and T-020 (EncoderController) both deliver to `service/EncoderController.kt`.**
  - **The Execution Failure:** T-019's deliverables column says "ABR logic in `EncoderController` + config." T-020's deliverables say "`EncoderController.kt`." Two agents writing to the same file in overlapping timeframes will produce merge conflicts, duplicate class definitions, or incompatible internal APIs. Since T-019 calls `requestAbrChange()` which is defined by T-020, the ABR agent must consume an interface it can't define.
  - **The Plan Fix:** T-020 must define and deliver the complete `EncoderController` public API (including `requestAbrChange` and `requestThermalChange`). T-019 delivers an `AbrPolicy` or `AbrLadder` class that decides _when_ to step, and a unit-tested integration that calls `EncoderController.requestAbrChange()`. Separate files. T-019 must explicitly depend on T-020.
  - **The Plan Evidence:** T-019 WBS "Files/Packages Likely Touched: `service/EncoderController.kt`". T-020 WBS "Files/Packages Likely Touched: `service/EncoderController.kt`". T-019 dependencies: `[T-006, T-007]` — missing T-020.

- **[Severity: High] T-025 (Local MP4 Recording) is scoped at M (2d) with an unresolved technical blocker.**
  - **The Execution Failure:** OQ-04 states: "RootEncoder's exact API for tee-ing encoded buffers to MP4 muxer without a second encoder: `RtmpCamera2` may support this via `startRecord()`, but API compatibility needs verification." The agent assigned T-025 has no verified API to build against. Additionally, the task includes SAF picker integration with `takePersistableUriPermission()`, API 23–28 fallback via `getExternalFilesDir`, fail-fast on missing storage grant, and spec §11 "insufficient storage → stop recording, continue streaming, notify user." That's four distinct problem domains in 2 days with an unresolved tech question.
  - **The Plan Fix:** Resolve OQ-04 _before_ T-025 starts (add a spike task T-025a: "Verify RootEncoder `startRecord()` API", 0.5d, during Milestone 2). Expand T-025 effort to L (3d). Add the "insufficient storage → stop recording, continue streaming" failure mode from spec §11 to the task scope, which is currently missing.
  - **The Plan Evidence:** OQ-04 in §11. T-025 WBS scope: "Tee encoded buffers to MP4 muxer (no second encoder)." Spec §11: "Insufficient storage → Stop recording, continue streaming, notify user" — not mentioned in T-025.

- **[Severity: High] T-024 (Background Camera Revocation) hides "placeholder frame" complexity.**
  - **The Execution Failure:** Spec SL-06 says "keep the audio-only RTMP session alive (or send a static placeholder frame if video-only mode)." In video-only mode, there's no audio track. Sending a placeholder frame into the RTMP stream without a camera means generating synthetic video frames and feeding them to the encoder or RTMP muxer directly. This requires deep RootEncoder internals knowledge that no agent prompt provides. The task is estimated at M (2d) and says nothing about the placeholder frame path.
  - **The Plan Fix:** Explicitly scope whether video-only mode with camera revocation should (a) stop the stream entirely, (b) hold the RTMP connection idle, or (c) inject placeholder frames. Option (c) is likely not feasible with RootEncoder's `RtmpCamera2` API and should be called out as a design decision, not silently deferred. Add a fallback: video-only mode + camera revocation = graceful stop with reason.
  - **The Plan Evidence:** T-024 WBS: "Detect camera revocation in background. Switch to audio-only (or placeholder frame)." Spec SL-06: "send a static placeholder frame if video-only mode."

- **[Severity: Medium] T-038 (Structured Logging) and T-026 (ACRA Credential Redaction) share the same deliverable file.**
  - **The Execution Failure:** Both tasks list `crash/CredentialSanitizer.kt` as a primary deliverable. T-038 is in Stage 1 (Day 1), T-026 is in Milestone 6 (Day 14+). If T-038 defines `CredentialSanitizer` first, T-026 must extend it. But T-026's playbook writes the full sanitizer from scratch ("Create `CredentialSanitizer`"). The agent on T-026 will overwrite T-038's work or produce a duplicate class.
  - **The Plan Fix:** T-038 should fully own `CredentialSanitizer` (regex patterns, `sanitize()` function, unit tests). T-026 should _consume_ the sanitizer via the ACRA `ReportTransformer`, not redefine it. Update T-026 playbook to reference the existing sanitizer.
  - **The Plan Evidence:** T-038 WBS: "Files/Packages Likely Touched: `crash/CredentialSanitizer.kt` (shared with ACRA)." T-026 Playbook Step 2: "Create `CredentialSanitizer`."

---

## 2. Dependency Graph Integrity

- **[Severity: Critical] T-029 and T-030 are placed in Stage 4 but depend on T-012 which is Stage 6.**
  - **The Execution Failure:** T-029 (Mute Toggle) declares dependencies `[T-007, T-008, T-012]`. T-030 (Camera Switching) declares `[T-007, T-012]`. T-012 (Stream Screen UI) is in Stage 6 with dependencies on T-010 and T-011. The DAG text places both T-029 and T-030 in Stage 4, but they cannot start until T-012 is complete — which requires T-011 → T-010 → T-007 (the full UI chain). This makes the Mermaid graph and stage assignments internally inconsistent. Any scheduler (human or automated) following the Stage 4 label will attempt these tasks too early and block.
  - **The Plan Fix:** Move T-029 and T-030 to Stage 6 (after T-012). Alternatively, split the UI-dependent portion (button on StreamScreen) from the service-side logic (mute/switch implementation in StreamingService), allowing the service logic to execute in Stage 4 while the UI wiring waits for Stage 6.
  - **The Plan Evidence:** T-029 WBS "Dependencies: T-007, T-008, T-012". Stage 4 listing: "T-029: Mute Toggle [T-007, T-008, T-012]." Stage 6 listing: "T-012: Stream Screen UI [T-010, T-011]."

- **[Severity: High] T-019 (ABR) is missing a dependency on T-020 (EncoderController).**
  - **The Execution Failure:** ABR must call `EncoderController.requestAbrChange()` to apply quality changes. T-019 depends on `[T-006, T-007]` but not T-020. An agent starting T-019 in Stage 5 will find no `EncoderController` to call. They'll either stub it (duplicating T-020's work) or block until T-020 appears, which defeats the parallelism the plan assumes.
  - **The Plan Fix:** Add T-020 as a dependency for T-019. Move T-019 to Stage 5 or later, after T-020 completes.
  - **The Plan Evidence:** T-019 WBS "Dependencies: T-006, T-007." T-019 objective: "ABR ladder definition …, resolution/fps step-down via EncoderController." T-020 is in Stage 4.

- **[Severity: High] T-037 (Overlay Stub) is listed in Stage 1 alongside T-003, but depends on T-003.**
  - **The Execution Failure:** Stage 1 header says "Depends on Stage 0." T-037 dependencies are `[T-002, T-003]`. But T-003 is _also_ in Stage 1. T-037 cannot be parallel with T-003; it must be in Stage 2 or later.
  - **The Plan Fix:** Move T-037 to Stage 2.
  - **The Plan Evidence:** Stage 1 listing: "T-037: Overlay Stub [T-002, T-003]." T-003 is also listed in Stage 1.

- **[Severity: High] The stated critical path omits the T-016 → T-018 dependency chain.**
  - **The Execution Failure:** The plan states the critical path as `T-001 → T-003 → T-005 → T-007 → T-009 → T-018 → T-028`. But T-018 (RTMPS) depends on `[T-009, T-016]`. T-016 depends on `[T-005, T-015]`. T-015 depends on `[T-004, T-006]`. T-006 depends on `[T-002, T-003]`. So the path through T-018 via T-016 is: `T-001 → T-003 → T-006 → T-015 → T-016 → T-018 → T-028`. This chain includes T-006 (M, 2d) + T-015 (M, 2d) + T-016 (M, 2d) = 6 additional days not on the stated critical path. If T-006 or T-015 slips, T-018 is delayed and the plan doesn't account for it.
  - **The Plan Fix:** Recalculate the critical path including both branches into T-018. The true critical path is the longer of the two. Milestone 5 must not assume T-018 is ready on Day 10 unless T-016's entire chain is complete.
  - **The Plan Evidence:** Critical path section §4. T-018 WBS: "Dependencies: T-009, T-016." T-016 WBS: "Dependencies: T-005, T-015."

- **[Severity: Medium] T-021 (Thermal Monitoring) is missing a dependency on T-007 (StreamingService).**
  - **The Execution Failure:** Thermal `CRITICAL` requires "stop stream and recording gracefully" (spec SL-07). The thermal monitor must call into StreamingService to stop the stream. T-021 dependencies are `[T-002, T-020]` — no T-007. The agent building thermal monitoring will have no way to trigger a graceful stop.
  - **The Plan Fix:** Add T-007 as a dependency for T-021, or define a callback interface in T-020 that T-021 uses to communicate stop requests.
  - **The Plan Evidence:** T-021 WBS: "Dependencies: T-002, T-020." Spec SL-07: "On `THERMAL_STATUS_CRITICAL`: stop stream and recording gracefully."

---

## 3. Agent Handoff Completeness

- **[Severity: Critical] Only 5 of 38 tasks have agent prompts. 87% of tasks have zero handoff instructions.**
  - **The Execution Failure:** The plan premise (§1: "agents will follow task prompts literally and ignore anything not written") means 33 tasks will be executed by agents with only a WBS row and no playbook. WBS rows like T-022 ("Register `AudioManager.OnAudioFocusChangeListener`. On focus loss: mute mic, show indicator") don't specify: which coroutine scope to use, how to coordinate with the mute toggle (T-029), whether to update StreamState or StreamStats, how to handle the case where the user has already manually muted, or how to integrate with NotificationController. An agent will guess all of these.
  - **The Plan Fix:** At minimum, provide agent prompts for all Critical Path and High-Risk tasks: T-008, T-010, T-011, T-018, T-019, T-021, T-024, T-025. Each prompt must specify: exact input interfaces, exact output interfaces, error handling contract, and how to coordinate with adjacent tasks.
  - **The Plan Evidence:** Section 5 "Agent Handoff Prompts" contains prompts for T-001, T-002, T-007, T-009, T-020 only. T-008 through T-038 (minus those 5) have no prompts.

- **[Severity: Critical] T-007 agent prompt and the `StreamingServiceControl` interface disagree on `startStream()` signature.**
  - **The Execution Failure:** The agent prompt (§5, T-007) says: "On `startStream(profileId)`: fetch credentials from `EndpointProfileRepository`." The interface contract (§8.2) defines: `fun startStream(profileId: String, config: StreamConfig)`. The T-010 agent (StreamViewModel) will call the interface method with two parameters. The T-007 agent will implement a one-parameter method. The service won't bind and the app will crash at integration with a `NoSuchMethodError` or compilation failure.
  - **The Plan Fix:** Reconcile the interface: either `startStream(profileId)` with the service reading config internally from `SettingsRepository`, or `startStream(profileId, config)` with the ViewModel resolving config. Update both the prompt and the interface contract. The playbook Step 3 also says "extract `profileId` from Intent extra" in `onStartCommand` — clarify whether `onStartCommand` or `startStream()` is the entry point for a new stream.
  - **The Plan Evidence:** T-007 prompt: "On `startStream(profileId)`: fetch credentials…" §8.2 interface: `fun startStream(profileId: String, config: StreamConfig)`.

- **[Severity: High] T-020 agent prompt assumes direct access to `RtmpCamera2` but doesn't specify how.**
  - **The Execution Failure:** The T-020 playbook says: "call `rtmpCamera2.setVideoBitrateOnFly()`", "`rtmpCamera2.stopPreview()`", "`rtmpCamera2.startPreview(surface)`", "`rtmpCamera2.startStream(url)`." But `RtmpCamera2` is owned by `StreamingService` (architecture §6.3: "the app uses RtmpCamera2 exclusively for camera ownership"). The agent prompt never tells the T-020 agent how to get a reference to `RtmpCamera2`. An agent following literally will either (a) inject `RtmpCamera2` directly via Hilt (wrong — it's not a singleton provided by DI, it's created at stream-start time), or (b) add a `RtmpCamera2` parameter to every method (breaking the interface).
  - **The Plan Fix:** Define the EncoderController's contract with StreamingService. Either: (a) StreamingService passes a `RtmpCamera2` reference to EncoderController at construction time (factory method, not Hilt), or (b) EncoderController exposes a command interface and StreamingService executes the actual RootEncoder calls. Specify this in the T-020 prompt.
  - **The Plan Evidence:** T-020 Playbook Steps 3-5 reference `rtmpCamera2.*` directly. Architecture §2: "StreamingService owns RtmpCamera2."

- **[Severity: High] T-009 prompt references `ConnectionState` — a type that is never defined in any contract.**
  - **The Execution Failure:** The T-009 prompt says: "Expose `connectionState: StateFlow<ConnectionState>` with states: `Disconnected`, `Connecting`, `Connected`, `Reconnecting(attempt)`." But `ConnectionState` is not in T-002's deliverables, not in §8 interface contracts, and not in the data model package. The agent must invent this type. When T-010 (StreamViewModel) tries to consume it, the type may not match what T-010 expects, or T-010 may use `StreamState.Reconnecting` instead, creating a dual-state-tracking bug.
  - **The Plan Fix:** Add `ConnectionState` to T-002's deliverables, or clarify that ConnectionManager should use `StreamState` directly (eliminating the need for a separate type). Define how `ConnectionState` maps to `StreamState` for UI consumption.
  - **The Plan Evidence:** T-009 prompt: "Expose `connectionState: StateFlow<ConnectionState>` with states: `Disconnected, Connecting, Connected, Reconnecting(attempt)`." T-002 deliverables: no `ConnectionState`.

- **[Severity: Medium] T-026 prompt instructs using `@AcraHttpSender` annotation with conditional initialization — which is contradictory.**
  - **The Execution Failure:** The prompt says: "Configure `@AcraHttpSender` with HTTPS-only endpoint" and "only initialize ACRA in release builds (check `BuildConfig.DEBUG`)." ACRA annotations are processed at compile time. You cannot conditionally apply an annotation at runtime. The agent will either apply the annotation unconditionally (enabling ACRA in debug builds) or attempt a runtime conditional that doesn't work with the annotation approach. The correct approach is ACRA's `CoreConfigurationBuilder` programmatic API.
  - **The Plan Fix:** Replace the `@AcraHttpSender` instruction with programmatic configuration via `CoreConfigurationBuilder` in `Application.attachBaseContext()`, guarded by `!BuildConfig.DEBUG`.
  - **The Plan Evidence:** T-026 Playbook Step 3: "Configure `@AcraHttpSender`" and Step 4: "only initialize ACRA in release builds."

---

## 4. Interface Contract Gaps

- **[Severity: Critical] Who constructs `StreamConfig` and passes it to the service? The interface says the caller does, but the playbook says the service does.**
  - **The Execution Failure:** `StreamingServiceControl.startStream(profileId: String, config: StreamConfig)` expects the ViewModel to pass a `StreamConfig`. But Playbook 1 (T-007, Step 4) says the service reads config from SettingsRepository after receiving only a `profileId`. If the ViewModel builds `StreamConfig`, it needs access to `SettingsRepository`. If the service builds it, the `config` parameter is unused dead code. At integration, either the ViewModel passes garbage config or the service ignores the parameter — both produce wrong runtime behavior.
  - **The Plan Fix:** Pick one owner. Recommendation: the ViewModel constructs `StreamConfig` from `SettingsRepository` and passes it. Remove config-reading from the service's `startStream()` path. Or: change the interface to `startStream(profileId: String)` and have the service read config internally. Update __all__ prompts and the interface definition to match.
  - **The Plan Evidence:** §8.2: `fun startStream(profileId: String, config: StreamConfig)`. T-007 Playbook Step 4: "Validate encoder config via MediaCodecInfo.CodecCapabilities pre-flight."

- **[Severity: High] No `AudioSourceManager` interface exists despite being a documented component.**
  - **The Execution Failure:** The package layout (§2) lists `audio/AudioSourceManager.kt`. Spec §6.2 lists AudioSourceManager as a key component: "Configures microphone via RootEncoder's `MicrophoneManager`." T-022 (Audio Focus) references `audio/AudioSourceManager.kt` in its deliverables. But T-002 (Data Models & Interface Contracts) does not define any interface for audio management. The agent on T-022 will create an arbitrary implementation with no contract for T-007 or T-029 to integrate against.
  - **The Plan Fix:** Add `AudioSourceManager` interface to T-002's deliverables: `fun mute()`, `fun unmute()`, `val isMuted: StateFlow<Boolean>`. T-029 (Mute Toggle) and T-022 (Audio Focus) both call this interface; T-007's service owns the implementation.
  - **The Plan Evidence:** Package layout: `audio/AudioSourceManager.kt`. T-002 deliverables list: no AudioSourceManager. T-022 WBS: "Files/Packages Likely Touched: `audio/AudioSourceManager.kt`."

- **[Severity: High] `EndpointProfileRepository` interface differs between T-002 prompt and §8.4 scaffolding.**
  - **The Execution Failure:** T-002 prompt defines: `fun getById(id: String): EndpointProfile?` (non-suspend), and no `setDefault()` method. §8.4 scaffolding defines: `suspend fun getById(id: String): EndpointProfile?` (suspend), and includes `suspend fun setDefault(id: String)`. An agent implementing from the T-002 prompt will produce a non-suspend `getById()` that can't be called from a coroutine context expecting suspension, and will omit `setDefault()` — breaking T-016 (Endpoint Screen) which needs to set a default profile.
  - **The Plan Fix:** Reconcile §8.4 and the T-002 prompt. All repository methods touching EncryptedSharedPreferences should be `suspend` (crypto operations block). Add `setDefault()` and `isKeystoreAvailable()` to the T-002 prompt.
  - **The Plan Evidence:** T-002 prompt: "fun getById(id: String): EndpointProfile?" §8.4: "suspend fun getById(id: String): EndpointProfile?" and "suspend fun setDefault(id: String)".

- **[Severity: Medium] `StreamStats.isMuted` splits mute state from `StreamState`, creating stale-state races.**
  - **The Execution Failure:** Mute status lives in `StreamStats`, updated at 1 Hz. Camera activity lives in `StreamState.Live(cameraActive)`, updated immediately. When a user taps mute, the HUD won't reflect the change for up to 1 second (next stats tick). The notification update (via `NotificationController.updateNotification(state: StreamState)`) takes only `StreamState` — it can never show mute status because mute is on `StreamStats`, not `StreamState`.
  - **The Plan Fix:** Either move `isMuted` into `StreamState.Live(cameraActive, isMuted)` for immediate propagation and notification access, or pass `StreamStats` to `NotificationController.updateNotification()` alongside `StreamState`.
  - **The Plan Evidence:** `StreamStats` definition: `val isMuted: Boolean`. `NotificationController.updateNotification(state: StreamState)` — takes only state. Spec §7.4: notification shows "Mute/Unmute" — needs to know current mute state.

- **[Severity: Medium] `NotificationController.createNotification(state: StreamState)` cannot display real-time stats.**
  - **The Execution Failure:** The notification should show bitrate and connection quality per spec §7.4 and §10.1. But the interface only receives `StreamState`. An implementation can show "Live" or "Reconnecting" but not "2.4 Mbps". The notification will be less informative than the HUD, and adding stats later requires an interface change that breaks all implementations.
  - **The Plan Fix:** Extend the interface: `fun updateNotification(state: StreamState, stats: StreamStats)`.
  - **The Plan Evidence:** §8.5: `fun createNotification(state: StreamState): Notification`. Spec §12.2: "HUD must display: live bitrate, fps, resolution…"

---

## 5. Test Coverage and Verification Gaps

- **[Severity: Critical] No automated test verifies FGS start restriction compliance (AC-01).**
  - **The Execution Failure:** AC-01: "FGS start succeeds only via user action. Attempting background auto-start without user affordance is blocked." Violating API 31+ FGS restrictions throws `ForegroundServiceStartNotAllowedException` — a production crash. No unit test, instrumented test, or failure injection scenario covers this. IT-02 tests FGS starts and survives activity destruction but doesn't test the _negative case_ (attempting a background start). This will be caught in production on API 31+ devices when a notification Start action incorrectly calls `startForegroundService()`.
  - **The Plan Fix:** Add an instrumented test on API 31+ emulator: verify that a `BroadcastReceiver` calling `startForegroundService()` with `camera|microphone` types from background throws the expected exception and is handled gracefully. Map to AC-01.
  - **The Plan Evidence:** AC-01. No test in §9 Unit Tests or Instrumented Tests table covers this negative case. IT-02 only checks "FGS running after `finish()`."

- **[Severity: High] No automated test for microphone revocation mid-stream (spec §11).**
  - **The Execution Failure:** Spec §11: "Microphone revoked mid-stream → Stop stream entirely and surface an error." No task, test ID, or failure injection scenario covers this. If a user revokes `RECORD_AUDIO` permission via Settings during a stream, the behavior is undefined and likely a crash in RootEncoder's audio encoder.
  - **The Plan Fix:** Add a failure injection test FI-07 mapped to a new subtask under T-007 or T-022. On API 30+, use `adb shell pm revoke` to revoke RECORD_AUDIO mid-stream and verify the stream stops with an error message.
  - **The Plan Evidence:** Spec §11: "Microphone revoked mid-stream → Stop stream entirely and surface an error." FI-01 through FI-06 exist; none covers microphone revocation.

- **[Severity: High] IT-05 (Camera Revocation) has no viable automation path.**
  - **The Execution Failure:** IT-05 says "revoke permission via Settings" — this requires navigating to Android Settings UI during an instrumented test, which is fragile and framework-dependent. On API 30+, `adb shell pm revoke` can revoke permissions, but camera revocation mid-stream relies on the OS triggering `CameraDevice.StateCallback.onDisconnected()`, which `pm revoke` may not trigger identically to OS-level revocation. The test may pass with `pm revoke` but fail on real OS-triggered revocation, or vice versa.
  - **The Plan Fix:** Define the automation method: use `adb shell pm revoke com.port80.app android.permission.CAMERA` on API 30+ and document which callback sequence this triggers. Add a note that real OEM camera revocation (e.g., another app grabbing the camera) requires a separate manual test.
  - **The Plan Evidence:** IT-05: "Physical device (revoke via Settings)."

- **[Severity: High] No test for encoder backpressure detection (spec §8.1 — the 80% threshold).**
  - **The Execution Failure:** Spec §8.1: "If measured output fps falls below 80% of configured fps for more than 5 consecutive seconds, treat this as a backpressure event and trigger the ABR step-down path." This is a key ABR trigger, specifically for MediaTek/Unisoc SoCs. UT-12 tests "ABR ladder steps down correctly" but doesn't test the _trigger condition_ that fires ABR in the first place. Without testing the backpressure detector, the ABR system may never activate on devices where it's most needed.
  - **The Plan Fix:** Add UT-14: mock encoder output at 22 fps with configured 30 fps. Verify ABR step-down fires after 5 seconds. Verify it does not fire at 25 fps (83% > 80%).
  - **The Plan Evidence:** Spec §8.1 backpressure paragraph. UT-12 description: "ABR ladder steps down correctly, recovers on bandwidth improvement."

- **[Severity: Medium] DM-04 (Thermal degradation) has no reproducible trigger method.**
  - **The Execution Failure:** "Physical device with thermal stress. Quality steps down, no crash." No method is defined for inducing thermal stress. Without `adb shell cmd thermalservice override-status` (which requires root or debug builds), you cannot programmatically trigger `THERMAL_STATUS_SEVERE`. The test becomes "run something CPU-intensive and hope the phone gets hot enough," which is non-deterministic and may never fire the degradation path.
  - **The Plan Fix:** For API 29+, use `adb shell cmd thermalservice override-status <level>` (requires `shell` user with debug build). Add this as the test automation method. For API 23–28, mock `ACTION_BATTERY_CHANGED` with elevated `EXTRA_TEMPERATURE` in an instrumented test. Add these methods to the DM-04 test description.
  - **The Plan Evidence:** DM-04: "Method: Physical device with thermal stress."

- **[Severity: Medium] T-011, T-025, T-029, T-030 all use "Manual" as verification with no automated alternative.**
  - **The Execution Failure:** Four tasks—Camera Preview, Local Recording, Mute Toggle, Camera Switching—rely entirely on manual verification. Camera preview surface binding (T-011) is the most lifecycle-sensitive component in the app and the root cause surface for process-death bugs. An instrumented test using `SurfaceView` in a test harness can verify `surfaceCreated` → `attachPreviewSurface` → no crash, which is strictly better than "Manual: preview appears on device."
  - **The Plan Fix:** Add instrumented tests for T-011 (SurfaceHolder lifecycle), T-029 (mute state propagation from service to UI), and T-030 (camera ID switches). T-025 recording can remain partially manual but the SAF permission flow should have a UI test.
  - **The Plan Evidence:** T-011 Verification: "Manual: preview appears on device." T-025: "Manual: record and verify MP4 playback." T-029: "Manual: mute during stream, verify silence." T-030: "Manual: switch camera during stream."

---

## 6. Specification Coverage Gaps

- **[Severity: Critical] Spec §11 "Microphone revoked mid-stream" has no task.**
  - **The Execution Failure:** Spec §11: "Microphone revoked mid-stream → Stop stream entirely and surface an error. Audio track loss cannot be gracefully degraded." No task in the WBS addresses this. T-024 handles camera revocation only. If a user revokes `RECORD_AUDIO` mid-stream, the app will crash or silently send corrupted audio frames.
  - **The Plan Fix:** Add a subtask to T-007 or create T-039: "Microphone revocation handling — detect RECORD_AUDIO revocation, stop stream with `Stopped(ERROR_CAMERA)` or a new `ERROR_AUDIO` reason, surface error to user."
  - **The Plan Evidence:** Spec §11 "Microphone revoked mid-stream" row. WBS T-024 scope: "camera revocation" only.

- **[Severity: High] No task implements spec §11 "Insufficient storage" handling.**
  - **The Execution Failure:** Spec §11: "Insufficient storage → Stop recording, continue streaming, notify user." T-025 (Local Recording) scope says "Fail fast if no storage grant, don't block streaming" — this covers the _pre-stream_ case (no SAF grant) but not the _mid-stream_ case (disk fills up during recording). During a long stream with local recording, the device can run out of storage. The MP4 muxer will throw an IOException. Without handling, this either crashes the app or corrupts the recording and potentially the RTMP stream.
  - **The Plan Fix:** Add "insufficient storage during recording" to T-025 scope: catch muxer write errors, stop recording, continue RTMP stream, notify user via notification.
  - **The Plan Evidence:** Spec §11: "Insufficient storage → Stop recording, continue streaming, notify user." T-025 WBS scope: no mention of mid-stream storage exhaustion.

- **[Severity: High] No task implements OEM battery optimization guidance (spec §18 Risk).**
  - **The Execution Failure:** Spec §18 Risk: "Guide user to disable battery optimization; show a one-time setup guide for Samsung/Xiaomi/Huawei." Spec Phase 4 checklist: "OEM battery optimization guidance flow." OEM battery killers are listed as a core assumption in §3: "Aggressive battery/FGS restrictions (Samsung, Xiaomi, Huawei, etc.) assumed active." Yet no WBS task creates this guidance flow, the one-time dialog, or the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` implementation. On Samsung devices, the FGS will be killed within minutes of backgrounding without this.
  - **The Plan Fix:** Add T-039 or expand T-023 (Low Battery): "OEM battery optimization guidance — detect manufacturer, show one-time setup guide with deep links to OEM-specific battery settings, request IGNORE_BATTERY_OPTIMIZATIONS."
  - **The Plan Evidence:** Spec §18 risk row referencing `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Spec §17 Phase 4: "OEM battery optimization guidance flow." WBS: no task.

- **[Severity: High] Spec §11 "Encoder error → Attempt one re-init" has no dedicated task or test.**
  - **The Execution Failure:** Spec §11: "Encoder error → Attempt one re-init. If it fails, stop stream and show explicit error identifying the failure cause." T-007's edge cases mention pre-flight validation, and T-020 mentions restart failure fallback. But no task _specifically_ implements the "attempt one re-init" logic for a spontaneous mid-stream encoder crash (as opposed to a deliberate quality change). FI-03 tests "encoder failure mid-stream" but the method is "force unsupported config change" — this tests a different code path than a spontaneous `MediaCodec.CodecException` during streaming.
  - **The Plan Fix:** Add explicit scope to T-007: "Register `MediaCodec.Callback.onError()` (or equivalent RootEncoder error callback). On encoder error: attempt one reconfigure with current settings. If that fails: emit `Stopped(ERROR_ENCODER)` with error details." Add a unit test mocking an encoder error mid-stream.
  - **The Plan Evidence:** Spec §11: "Encoder error → Attempt one re-init." T-007 Playbook: no mention of `onError` callback. FI-03 method: "Force unsupported config change."

- **[Severity: Medium] Spec §4.1 MC-01 mid-session video→audio downgrade has no task.**
  - **The Execution Failure:** Spec MC-01: "Mid-session video→audio downgrade is permitted; audio→video upgrade requires camera reacquire and encoder re-init." No task implements hot media-mode switching during an active stream. T-029 (mute) stops audio. T-030 (camera switch) switches cameras. Neither enables the user to drop video mid-stream while keeping audio, or re-add video mid-stream. The General Settings screen (T-015) includes "media stream selection" but only "before or during stream" — the "during stream" part requires service-side logic that no task delivers.
  - **The Plan Fix:** Add a subtask: "Mid-stream media mode transition. Service supports transitioning from video+audio to audio-only (release camera, stop video encoder, keep audio+RTMP alive) and from audio-only back to video+audio (reacquire camera, re-init video encoder, send IDR)." Effort: M (2d).
  - **The Plan Evidence:** Spec MC-01. T-015 WBS: "media mode selection" in scope. No service-side task for runtime mode transitions.

- **[Severity: Medium] Spec §5 NF-01, NF-03, NF-05 non-functional requirements have no verification tasks.**
  - **The Execution Failure:** NF-01 (startup < 2s), NF-03 (battery ≤ 15%/hr), NF-05 (APK < 15 MB) are all "Must" or "Should" NFRs. The operational readiness checklist in §10 mentions them but no WBS task performs the measurement. Without measurement, these pass the checklist by default (nobody checks the box because nobody is assigned to).
  - **The Plan Fix:** Add a task or expand T-035 (QA Matrix): "NFR Measurement — measure startup time on mid-range device, measure battery drain over 1hr stream, measure per-ABI APK size. Record results. Fail QA if Must-level NFRs are not met."
  - **The Plan Evidence:** Spec §5 NF-01, NF-03, NF-05. Operational Readiness Checklist §10 "Performance" section. No WBS task.

- **[Severity: Medium] Spec §12.1 metrics infrastructure has no task.**
  - **The Execution Failure:** Spec §12.1 lists 8 metrics categories: "encoder init success/failure count, reconnect attempt count and success/failure ratio, thermal level transitions, storage write errors, FGS start success/failure events, permission denial events." T-012 builds the HUD (displaying a subset) but no task creates the internal metrics collection, aggregation, or exposure infrastructure. These metrics are needed for diagnostics and debugging in production.
  - **The Plan Fix:** Add T-039: "Internal metrics collection — counters for encoder init, reconnect, thermal transitions, storage errors, FGS events, permission denials. Exposed via debug screen or logged on stream stop." Or explicitly note that these are deferred to a future phase and remove them from the spec's "Must" requirements.
  - **The Plan Evidence:** Spec §12.1 metrics list. WBS: no task. T-012 deliverables: HUD only.

---

## 7. Timeline and Milestone Feasibility

- **[Severity: Critical] Milestone 3 critical path is 10 sequential person-days in a 5-calendar-day window.**
  - **The Execution Failure:** Milestone 3 (Days 5–10) tasks include T-007 (4d) → T-010 (2d) → T-011 (2d) → T-012 (2d) = 10 sequential days. These cannot be parallelized: T-010 needs T-007's service binding, T-011 needs T-010's ViewModel, T-012 needs both T-010 and T-011. Additionally, T-008 (2d) and T-009 (3d) run in parallel with T-010 but both depend on T-007. Total Milestone 3 effort is 15.5 person-days. Even with 5 agents, the serial dependency chain through T-007→T-012 makes 5 calendar days physically impossible. The first end-to-end demo can't happen until Day 15 at absolute minimum, not Day 10.
  - **The Plan Fix:** Milestone 3 must be extended to Days 5–15 (10 calendar days). Or split T-007 to recover 2–3 days on the critical path (see finding 1.1). Adjust all downstream milestones accordingly. Milestone 5 start should be Day 15 at earliest, not Day 10.
  - **The Plan Evidence:** Milestone 3 section: "Days 5–10." T-007 effort: "L (4d)." T-010: "M (2d)." T-011: "M (2d)." T-012: "M (2d)." Dependencies: T-010 → [T-007], T-011 → [T-010], T-012 → [T-010, T-011].

- **[Severity: Critical] The 72-hour starter plan assumes T-007 starts Day 3 but takes 4 days, blocking Day 4 tasks.**
  - **The Execution Failure:** Section 12 says Day 3 starts "T-007: StreamingService (FGS Core) — start scaffold" and acknowledges "Streaming may not work E2E yet." The "Critical Path Status After 72 Hours" shows T-007 as 🔄 (in progress) and claims "T-009 (ready to start Day 4), T-010 (ready to start Day 4)." But T-007 is estimated at 4 days. It cannot be scaffolded in 1 day to the point where T-009 and T-010 can integrate against it. T-010 needs the `StreamingServiceControl` binder implementation. T-009 needs to call into the service's connection state. Without T-007 complete, Day 4 agents will attempt to call interfaces that throw `NotImplementedError`.
  - **The Plan Fix:** The 72-hour plan should scope Day 3 T-007 work as: "service lifecycle + state machine + binder stub returning dummy StateFlows." The binder must emit real `StateFlow<StreamState>` and `StateFlow<StreamStats>` so T-009 and T-010 can code against the flows. Actual RtmpCamera2 integration, stats polling, and real streaming are Days 4–6. Relabel the starter plan as a "96-hour" plan and add Day 4.
  - **The Plan Evidence:** Section 12 Day 3: "T-007: StreamingService… start scaffold." Critical Path Status: "T-009 (ready to start Day 4)." T-007 WBS: "Effort: L (4d)."

- **[Severity: High] Milestones 3, 4, and 5 run concurrently with zero integration buffer.**
  - **The Execution Failure:** Milestone 3: Days 5–10. Milestone 4: Days 8–12. Milestone 5: Days 10–16. Milestone 5 entry criteria: "Milestone 3 complete." M5 starts the same day M3 ends. No time is allocated for integration testing between milestones. When T-009 (ConnectionManager) integrates with T-007 (StreamingService) for the first time, there will be interface mismatches, threading bugs, and state synchronization issues. These are discovered during integration, not during individual task unit tests. The plan assumes zero integration friction.
  - **The Plan Fix:** Add 1–2 day integration buffers between milestones. Milestone 3 exit criteria should include: "End-to-end stream to a test RTMP server (OQ-03) succeeds from UI." Milestone 5 start should be Day 12 (not 10). Add explicit integration test tasks between milestones.
  - **The Plan Evidence:** Milestone 3: "Days 5–10." Milestone 5: "Days 10–16. Entry Criteria: Milestone 3 complete." No integration task between milestones.

- **[Severity: High] Day 2 of 72-hour plan wastes 30% of agent-hours on T-003 blocking.**
  - **The Execution Failure:** Day 2 allocates 4 agents: Agent A on T-003 (Hilt DI), Agents B/C/D on T-004/T-005/T-006. The plan notes "T-003 blocks B/C/D in practice, but Agent A should complete it within 2–3 hours." Agents B, C, D idle for 2–3 hours. Then T-005 (EndpointProfileRepo) is estimated at M (2d) but the plan expects it complete by end of Day 2 (~5 remaining hours). This is optimistic by at least a factor of 2. The "End of Day 2 artifacts" list includes "Credential encryption/decryption verified in unit test" — which requires T-005 to be fully implemented and tested in 5 hours despite being scoped at 2 days.
  - **The Plan Fix:** Restructure Day 2: Agents B/C/D should work on Stage 1 tasks that don't depend on T-003 (e.g., T-013 Permissions, T-026 ACRA, T-033 F-Droid CI, T-034 ProGuard) until T-003 unblocks. Then start T-004/T-005/T-006 in the afternoon. Don't expect T-005 to complete on Day 2; it will complete Day 3.
  - **The Plan Evidence:** Section 12 Day 2 plan. T-003: "S (1d)" but plan says "2–3 hours." T-005: "M (2d)" but plan expects completion in ~5 hours.

- **[Severity: Medium] Eight open questions (OQ-01 through OQ-08) have no resolution timeline.**
  - **The Execution Failure:** OQ-01 (RootEncoder exact version), OQ-03 (test RTMP server), OQ-04 (recording API), OQ-05 (Compose BOM version) all block Day 1 or Day 3 tasks. OQ-06 (brand icon assets) blocks T-001 and T-008. The plan assigns "Proposed Decision Owner" but no deadline. If OQ-03 (test RTMP server) is unresolved by Day 5, Milestone 3 cannot validate streaming end-to-end.
  - **The Plan Fix:** Add an OQ resolution deadline for each: OQ-01 and OQ-05 must be resolved before T-001 starts (Day 0). OQ-03 must have a working test server by Day 5 (M3 start). OQ-04 must be resolved before T-025 starts. OQ-06 can use placeholder assets until Day 14. Assign owners with explicit day-of deadlines.
  - **The Plan Evidence:** Section 11 "Open Questions and Blockers." No resolution dates.

---

## Top Three Execution Risks

**1. T-007 (StreamingService) is a single-threaded bottleneck that will blow the schedule.**
T-007 is scoped at 4 days, sits on the critical path, contains the biggest technical unknown (RootEncoder integration), and blocks 15 downstream tasks. The 72-hour plan assumes it can be scaffolded in 1 day to unblock parallel work, contradicting its own 4-day estimate. With the plan's own 30% slip assumption, T-007 takes 5.5 days. This pushes the first E2E demo from Day 10 to Day 15, and Milestone 6 completion from Day 18 to Day 24+. Every day T-007 slips, three agents sit idle.

**2. Agent prompts are missing for 87% of tasks and the five that exist have interface contract mismatches.**
The plan's own assumption is "agents follow task prompts literally and ignore anything not written." Five prompts exist for 38 tasks. The `startStream()` signature mismatch between T-007's prompt and the §8.2 interface will cause a compile-time or runtime failure at the T-007/T-010 integration boundary — the most critical integration surface in the entire app. `ConnectionState` (T-009) is undefined. `EncoderController` (T-020) lacks ownership semantics. The first time two tasks integrate, every ambiguity becomes a bug.

**3. Milestone 3's critical path (10 sequential days) cannot fit in its 5-day window, making the entire downstream schedule fictional.**
The serial chain T-007 (4d) → T-010 (2d) → T-011 (2d) → T-012 (2d) sums to 10 days of blocking work. Zero integration buffer exists between milestones. Milestones 4 and 5 start while Milestone 3 is still running, and Milestone 5 starts the day Milestone 3 nominally ends. The schedule assumes perfect-day productivity, zero integration friction, and no RootEncoder surprises. Applying the plan's own 30% critical-path slip factor, the entire project extends from 18 days to at least 26 days.
