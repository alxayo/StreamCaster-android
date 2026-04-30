# Plan: DASH Playback with Anti-Capture & Feature Flags

## TL;DR
Add DASH/HLS video playback to StreamCaster using AndroidX Media3 (ExoPlayer), with `FLAG_SECURE` anti-capture protection on the playback screen, gated behind local DataStore-backed feature flags. Playback is a standalone feature — separate screen, separate ViewModel, no interference with the existing streaming pipeline.

---

## Phase 1: Feature Flag Infrastructure

Add a general-purpose local feature flag system to SettingsRepository/DataStore so all future features can be toggled.

### Steps

1. **Define `FeatureFlags` object** — New file `data/FeatureFlags.kt`
   - Contains `enum class Feature(val key: String, val defaultEnabled: Boolean)` entries:
     - `PLAYBACK("feature_playback", false)`
     - `PLAYBACK_SECURE_WINDOW("feature_playback_secure_window", true)`
   - Centralizes all flag keys and defaults in one place.

2. **Add feature flag methods to `SettingsRepository`** interface
   - `fun isFeatureEnabled(feature: Feature): Flow<Boolean>`
   - `suspend fun setFeatureEnabled(feature: Feature, enabled: Boolean)`
   - Modify `SettingsRepository.kt` — add these two methods.

3. **Implement in `DataStoreSettingsRepository`**
   - Use `booleanPreferencesKey(feature.key)` with `feature.defaultEnabled` as fallback.
   - Implementation is generic — works for any `Feature` enum entry without per-flag boilerplate.

4. **Add Feature Flags settings screen** — New file `ui/settings/FeatureFlagsScreen.kt`
   - List all `Feature` entries with toggle switches.
   - Only accessible from Settings Hub (not from main stream screen).
   - Show a restart hint if a flag change requires navigation rebuild.

5. **Wire into SettingsHub navigation**
   - Add `Routes.FEATURE_FLAGS = "settings/feature_flags"` to `AppNavGraph.kt`.
   - Add entry in `SettingsHubScreen.kt` (e.g. "Experimental Features" row).

6. **Expose flags in `SettingsViewModel`**
   - Add `fun isFeatureEnabled(feature: Feature): StateFlow<Boolean>` using the same `stateIn()` pattern as existing settings.

### Files to modify
- `app/src/main/java/com/port80/app/data/SettingsRepository.kt` — add 2 methods
- `app/src/main/java/com/port80/app/data/DataStoreSettingsRepository.kt` — implement 2 methods
- `app/src/main/java/com/port80/app/navigation/AppNavGraph.kt` — add feature flags route + playback route
- `app/src/main/java/com/port80/app/ui/settings/SettingsHubScreen.kt` — add "Experimental Features" entry
- `app/src/main/java/com/port80/app/ui/settings/SettingsViewModel.kt` — expose flag flows

### New files
- `app/src/main/java/com/port80/app/data/FeatureFlags.kt`
- `app/src/main/java/com/port80/app/ui/settings/FeatureFlagsScreen.kt`

---

## Phase 2: Media3 Dependency Setup

Add AndroidX Media3 libraries to the project. No code changes — just build config.

### Steps

7. **Add Media3 versions and libraries to version catalog** — `gradle/libs.versions.toml`
   - Add version: `media3 = "1.5.1"` (latest stable as of early 2026)
   - Add libraries:
     - `media3-exoplayer` = `androidx.media3:media3-exoplayer`
     - `media3-exoplayer-dash` = `androidx.media3:media3-exoplayer-dash`
     - `media3-exoplayer-hls` = `androidx.media3:media3-exoplayer-hls` (optional, for HLS support)
     - `media3-ui` = `androidx.media3:media3-ui`
     - `media3-session` = `androidx.media3:media3-session` (for media notification/controls)

8. **Add dependencies to `app/build.gradle.kts`**
   - All media3 deps as `implementation(libs.media3.XXX)`.

9. **Add ProGuard rules for Media3** — `app/proguard-rules.pro`
   - Media3 ships consumer rules, but add `-dontwarn` for any transitive warnings.
   - Keep `androidx.media3.**` to be safe.

10. **Verify build compiles** — `./gradlew assembleFossDebug assembleGmsDebug`

### Files to modify
- `gradle/libs.versions.toml` — add media3 version + 4-5 library entries
- `app/build.gradle.kts` — add media3 dependency lines
- `app/proguard-rules.pro` — add media3 keep rules

---

## Phase 3: Playback Domain Layer

Build the playback controller and state model, separate from the streaming service.

### Steps

11. **Define playback state model** — New file `data/model/PlaybackState.kt`
    - `sealed interface PlaybackState` with cases: `Idle`, `Loading(uri)`, `Playing(uri, position, duration)`, `Paused(uri, position, duration)`, `Error(uri, message)`
    - Follows the same `sealed interface` pattern as existing `StreamState`.

12. **Create `PlaybackController`** — New file `playback/PlaybackController.kt`
    - Wraps Media3 `ExoPlayer` instance.
    - Owns player lifecycle (create/release).
    - Exposes `StateFlow<PlaybackState>` mirroring player state via `Player.Listener`.
    - Accepts a DASH or HLS URI to play.
    - Hilt `@Singleton` — injected into ViewModel.
    - Player is created lazily on first `play()` call and released on `release()`.

13. **Create `PlaybackModule`** — New file `di/PlaybackModule.kt`
    - Hilt module providing `PlaybackController` with `@ApplicationContext`.
    - `@InstallIn(SingletonComponent::class)`.

### New files
- `app/src/main/java/com/port80/app/data/model/PlaybackState.kt`
- `app/src/main/java/com/port80/app/playback/PlaybackController.kt`
- `app/src/main/java/com/port80/app/di/PlaybackModule.kt`

---

## Phase 4: Playback UI

Build the playback screen with anti-capture protection.

### Steps

14. **Create `PlaybackViewModel`** — New file `ui/playback/PlaybackViewModel.kt`
    - `@HiltViewModel` injected with `PlaybackController` and `SettingsRepository`.
    - Exposes `playbackState: StateFlow<PlaybackState>`.
    - Methods: `play(uri: String)`, `pause()`, `resume()`, `stop()`, `seekTo(ms: Long)`.
    - Checks `Feature.PLAYBACK_SECURE_WINDOW` flag and exposes `secureWindowEnabled: StateFlow<Boolean>`.
    - On `onCleared()`: calls `playbackController.release()`.

15. **Create `PlaybackScreen`** — New file `ui/playback/PlaybackScreen.kt`
    - Compose screen with:
      - `AndroidView` wrapping Media3 `PlayerView` for video rendering.
      - Play/pause/seek controls (can use Media3's built-in `PlayerView` controls initially).
      - URI input field (or accept URI via nav argument).
      - Loading/error state display.
    - **Anti-capture: `FLAG_SECURE`** — Apply via `SideEffect` or `DisposableEffect`:
      ```
      // Pseudocode — when secureWindowEnabled is true:
      // activity.window.setFlags(FLAG_SECURE, FLAG_SECURE)
      // On dispose: activity.window.clearFlags(FLAG_SECURE)
      ```
    - This blocks screenshots, screen recording, and screen sharing system-wide while the playback screen is visible.
    - `FLAG_SECURE` is cleared when navigating away (DisposableEffect cleanup).

16. **Wire playback route into navigation** — Modify `AppNavGraph.kt`
    - Add `Routes.PLAYBACK = "playback?uri={uri}"` with optional URI nav argument.
    - Gate the route: only register the `composable()` if `Feature.PLAYBACK` is enabled.
    - Add a "Watch" button/entry point on the main StreamScreen (visible only when flag is on).

17. **Add navigation entry point** — Modify `StreamScreen.kt`
    - Add a conditional "Playback" button that navigates to `Routes.PLAYBACK`.
    - Only visible when `Feature.PLAYBACK` is enabled (observed from SettingsRepository).

### New files
- `app/src/main/java/com/port80/app/ui/playback/PlaybackViewModel.kt`
- `app/src/main/java/com/port80/app/ui/playback/PlaybackScreen.kt`

### Files to modify
- `app/src/main/java/com/port80/app/navigation/AppNavGraph.kt` — add playback route
- `app/src/main/java/com/port80/app/ui/stream/StreamScreen.kt` — add conditional navigation button

---

## Phase 5: Testing & Verification

### Steps

18. **Unit tests for feature flags** — New file `test/.../data/FeatureFlagsTest.kt`
    - Test default values.
    - Test enable/disable persistence via DataStore.
    - Follow pattern from existing `DataStoreSettingsRepositoryTest.kt`.

19. **Unit tests for PlaybackController** — New file `test/.../playback/PlaybackControllerTest.kt`
    - Test state transitions: Idle → Loading → Playing → Paused → Idle.
    - Test release lifecycle.
    - Mock ExoPlayer with mockk.

20. **Unit tests for PlaybackViewModel** — New file `test/.../ui/playback/PlaybackViewModelTest.kt`
    - Test flag gating (playback disabled → no-op on play).
    - Test secure window flag propagation.
    - Follow pattern from existing ViewModel tests.

21. **Manual verification checklist**
    - Enable feature flag → playback button appears on stream screen.
    - Disable feature flag → playback button hidden, route inaccessible.
    - Play a DASH stream → video renders, controls work.
    - While playing: attempt screenshot → should be blank/black.
    - While playing: attempt screen recording → playback area should be black.
    - Navigate away from playback → FLAG_SECURE cleared, screenshots work again.
    - Verify streaming still works independently (no regression).

---

## Relevant Files (Summary)

### Modify
- `gradle/libs.versions.toml` — add media3 version + libraries
- `app/build.gradle.kts` — add media3 dependencies
- `app/proguard-rules.pro` — add media3 ProGuard rules
- `app/src/main/java/com/port80/app/data/SettingsRepository.kt` — add feature flag methods
- `app/src/main/java/com/port80/app/data/DataStoreSettingsRepository.kt` — implement feature flag methods
- `app/src/main/java/com/port80/app/ui/settings/SettingsViewModel.kt` — expose flag flows
- `app/src/main/java/com/port80/app/ui/settings/SettingsHubScreen.kt` — add Experimental Features entry
- `app/src/main/java/com/port80/app/navigation/AppNavGraph.kt` — add playback + feature flags routes
- `app/src/main/java/com/port80/app/ui/stream/StreamScreen.kt` — add conditional playback button

### Create
- `app/src/main/java/com/port80/app/data/FeatureFlags.kt` — feature flag enum
- `app/src/main/java/com/port80/app/data/model/PlaybackState.kt` — playback state sealed interface
- `app/src/main/java/com/port80/app/playback/PlaybackController.kt` — Media3 player wrapper
- `app/src/main/java/com/port80/app/di/PlaybackModule.kt` — Hilt DI for playback
- `app/src/main/java/com/port80/app/ui/playback/PlaybackViewModel.kt` — playback ViewModel
- `app/src/main/java/com/port80/app/ui/playback/PlaybackScreen.kt` — playback Compose screen
- `app/src/main/java/com/port80/app/ui/settings/FeatureFlagsScreen.kt` — flag toggle UI
- `app/src/test/java/com/port80/app/data/FeatureFlagsTest.kt` — flag unit tests
- `app/src/test/java/com/port80/app/playback/PlaybackControllerTest.kt` — controller tests
- `app/src/test/java/com/port80/app/ui/playback/PlaybackViewModelTest.kt` — ViewModel tests

---

## Verification

1. `./gradlew assembleFossDebug assembleGmsDebug` — both flavors compile
2. `./gradlew testFossDebugUnitTest` — all unit tests pass (existing + new)
3. `./gradlew :app:dependencies --configuration fossReleaseRuntimeClasspath | grep -i "gms\|play-services\|mlkit"` — matches must be limited to the allowlisted bundled ML Kit barcode-scanning subtree
4. Manual: enable flag → play DASH URL → verify video + anti-capture → disable flag → verify hidden
5. Manual: verify streaming still works with playback feature both enabled and disabled

---

## Decisions

- **Media3 over building from scratch** — Media3 is the standard Android playback library, maintained by Google, Apache 2.0 licensed, works on both FOSS and GMS flavors (no Play Services dependency for basic playback + Widevine).
- **`FLAG_SECURE` for anti-capture** — Sufficient for "basic protection" level. Blocks Android's built-in screenshot/recording/casting. Applied per-screen via DisposableEffect, not globally.
- **Widevine DRM deferred** — Not in this plan. Media3 supports it natively, so it can be added later as a Phase 2 enhancement by enabling `MediaItem.DrmConfiguration` on the player. The architecture accommodates this without restructuring.
- **Local feature flags only** — DataStore-backed, no remote config. Works offline, both flavors, no GMS dependency.
- **Playback is fully decoupled from streaming** — Separate ViewModel, controller, navigation route. No shared state with StreamingService.
- **HLS support included** — Adding `media3-exoplayer-hls` alongside DASH is trivial (one extra dependency line) and broadens stream format compatibility.
- **No new permissions needed** — Media3 playback uses INTERNET (already declared) and no camera/mic.
- **No manifest changes needed** — No new Activity or Service for playback.
