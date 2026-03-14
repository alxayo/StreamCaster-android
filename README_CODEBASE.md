# StreamCaster Android Codebase — Complete Reference Index

## 📌 Quick Navigation

This repository contains a **complete Android RTMP streaming application** with professional-grade architecture. All supporting code is ready; only `StreamingService` implementation remains.

### 📚 Documentation Files (New)

| Document | Size | Purpose |
|----------|------|---------|
| **[CODEBASE_REFERENCE.md](./CODEBASE_REFERENCE.md)** | 35 KB | **START HERE** — Complete reference with all 29 source files, architecture diagrams, security model, defaults |
| **[FILES_PROVIDED.md](./FILES_PROVIDED.md)** | 17 KB | Detailed inventory of all files with purposes, methods, and usage patterns |
| **[SPECIFICATION.md](./SPECIFICATION.md)** | 57 KB | Product specification and technical requirements |
| **[IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md)** | 103 KB | Detailed task breakdown and implementation strategy |

### 📂 Source Code Structure

```
app/src/main/java/com/port80/app/
├── data/
│   ├── model/              (7 files - all data classes)
│   │   ├── StreamState.kt ................... ✅
│   │   ├── StreamStats.kt .................. ✅
│   │   ├── StopReason.kt ................... ✅
│   │   ├── ThermalLevel.kt ................. ✅
│   │   ├── Resolution.kt ................... ✅
│   │   ├── StreamConfig.kt ................. ✅
│   │   └── EndpointProfile.kt .............. ✅
│   ├── EndpointProfileRepository.kt ........ ✅ (interface)
│   ├── EncryptedEndpointProfileRepository.kt ✅ (implementation)
│   ├── SettingsRepository.kt ............... ✅ (interface)
│   └── DataStoreSettingsRepository.kt ...... ✅ (implementation)
├── service/
│   ├── StreamingServiceControl.kt .......... ✅ (interface)
│   ├── EncoderBridge.kt .................... ✅ (interface)
│   ├── NotificationController.kt ........... ✅ (interface)
│   ├── ReconnectPolicy.kt .................. ✅ (interface + impl)
│   └── StreamingService.kt ................. ❌ (TO IMPLEMENT)
├── crash/
│   ├── CredentialSanitizer.kt .............. ✅
│   └── AcraConfigurator.kt ................. ✅
├── util/
│   └── RedactingLogger.kt .................. ✅
└── di/
    ├── AppModule.kt ....................... ✅
    ├── DataModule.kt ...................... ✅
    ├── RepositoryModule.kt ................ ✅
    ├── StreamModule.kt .................... ✅
    └── CameraModule.kt .................... ✅

app/src/test/java/com/port80/app/
├── crash/
│   ├── CredentialSanitizerTest.kt ......... ✅ (9 tests)
│   └── AcraConfiguratorTest.kt ............ ✅ (3 tests)
└── data/
    ├── DataStoreSettingsRepositoryTest.kt . ✅ (30+ tests)
    └── EndpointProfileSerializationTest.kt ✅ (8 tests)

Configuration
├── app/build.gradle.kts ................... ✅
└── app/src/main/AndroidManifest.xml ....... ✅
```

---

## 🎯 What's Included

### ✅ Complete (29 files)

1. **Data Models** — Stream state, stats, configuration, profiles, resolutions
2. **Service Interfaces** — Control contract, encoder abstraction, notification management
3. **Repository Layer** — Encrypted credential storage, DataStore-backed settings
4. **Security Infrastructure** — Credential sanitization, redacting logger, ACRA integration
5. **Dependency Injection** — 5 Hilt modules with all bindings pre-configured
6. **Reconnection Logic** — Exponential backoff with random jitter
7. **Unit Tests** — Comprehensive tests for all supporting code (50+ test cases)
8. **Build Configuration** — Complete Gradle setup with all dependencies
9. **Android Configuration** — Manifest with all required permissions and components

### ❌ To Implement (1 file)

- **StreamingService** — The main service class that orchestrates RTMP streaming

---

## 🚀 Getting Started

### Step 1: Read the Architecture
```bash
cat CODEBASE_REFERENCE.md
```
This provides:
- Full source code for all 29 files
- Architecture diagrams
- Security model documentation
- Default configuration values
- Implementation checklist

### Step 2: Understand the State Machine
```
Idle → Connecting → Live → Stopping → Stopped
                     ↘ Reconnecting ↗
```

All state transitions managed by StreamingService via `StateFlow<StreamState>`.

### Step 3: Review Key Interfaces
```kotlin
// Main service contract
interface StreamingServiceControl {
    val streamState: StateFlow<StreamState>  // Read-only state observable
    val streamStats: StateFlow<StreamStats>  // Real-time stats at ~1Hz
    fun startStream(profileId: String)
    fun stopStream()
    fun toggleMute()
    fun switchCamera()
    fun attachPreviewSurface(holder: SurfaceHolder)
    fun detachPreviewSurface()
}
```

### Step 4: Implement StreamingService

Create a class that:
- Implements `StreamingServiceControl`
- Extends Android `Service` (foreground service)
- Injects all dependencies via Hilt `@Inject constructor`
- Manages the state machine
- Handles reconnection via `ReconnectPolicy`
- Publishes stats at ~1Hz
- Logs all operations using `RedactingLogger`

See [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) for detailed task breakdown.

---

## 🔒 Security Model

### Credential Storage
- **Encrypted:** EncryptedSharedPreferences + Android Keystore (AES-256-GCM)
- **Never logged:** All logs sanitized by CredentialSanitizer
- **Safe storage:** Credentials never appear in Intent extras
- **Graceful degradation:** Handles Keystore loss (device restore) elegantly

### Credential Redaction
All three mechanisms use the same **CredentialSanitizer**:
1. **RedactingLogger** — Automatic log sanitization
2. **ACRA Reporter** — Sanitized crash reports
3. **Sensitive field exclusion** — Never include LOGCAT, SHARED_PREFERENCES, etc.

Example redaction:
```
Input:  "Connecting to rtmp://ingest.example.com/live/my_secret_key_12345"
Output: "Connecting to rtmp://ingest.example.com/live/****"
```

---

## 📊 Architecture Highlights

### State Management
- **Single source of truth:** StreamingService owns `StreamState`
- **UI observes:** Via `StateFlow<StreamState>` (read-only)
- **Reactive updates:** Stats published at ~1Hz
- **Idempotent operations:** Safe to call repeatedly

### Dependency Injection
- **Hilt @Singleton:** All service dependencies are singletons
- **Interface-based:** Repositories, policies, bridges are interfaces
- **Testable:** Mock implementations for all dependencies

### Reconnection Strategy
- **Exponential backoff:** 3s → 6s → 12s → 24s → 48s → 60s (capped)
- **Random jitter:** ±20% prevents "thundering herd"
- **Pluggable:** ReconnectPolicy interface allows custom strategies

### Error Handling
- **Graceful degradation:** Keystore loss → user re-enters credentials
- **Clear stop reasons:** StopReason enum (USER_REQUEST, ERROR_*, THERMAL_*, BATTERY_*)
- **Thermal management:** ThermalLevel enum (NORMAL, MODERATE, SEVERE, CRITICAL)

---

## 📦 Key Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| **RootEncoder** | 2.7.x | RTMP streaming, Camera2, H.264 encoding |
| **Jetpack Compose** | Latest | Modern declarative UI |
| **Jetpack DataStore** | Latest | Settings storage (non-sensitive) |
| **EncryptedSharedPreferences** | Latest | Keystore-backed credential storage |
| **Hilt** | Latest | Dependency injection |
| **ACRA** | Latest | Open-source crash reporting |
| **Kotlin Coroutines** | Latest | Async/concurrency |

---

## 🧪 Test Coverage

All supporting infrastructure has comprehensive tests:

| Test File | Test Count | Coverage |
|-----------|-----------|----------|
| CredentialSanitizerTest | 9 | URL redaction, parameter sanitization, edge cases |
| AcraConfiguratorTest | 3 | Report sanitization, HTTPS validation |
| DataStoreSettingsRepositoryTest | 30+ | Default values, round-trip serialization |
| EndpointProfileSerializationTest | 8 | Profile serialization, optional fields |
| **Total** | **50+** | **All supporting code** |

---

## 🔍 Implementation Checklist for StreamingService

### Core Service Structure
- [ ] `class StreamingService(context: Context) : Service(), StreamingServiceControl`
- [ ] `@Inject constructor` with all dependencies
- [ ] Implement `StreamingServiceControl` interface

### State Management
- [ ] Create `MutableStateFlow<StreamState>(StreamState.Idle)`
- [ ] Create `MutableStateFlow<StreamStats>(default)`
- [ ] Implement state machine transitions
- [ ] Publish updates at ~1Hz

### Operations
- [ ] `startStream(profileId: String)` — Fetch profile, validate, transition to Connecting
- [ ] `stopStream()` — Graceful shutdown, transition to Stopped
- [ ] `toggleMute()` — Toggle mute in Live state
- [ ] `switchCamera()` — Switch front/back camera
- [ ] `attachPreviewSurface()` — Display camera preview
- [ ] `detachPreviewSurface()` — Stop displaying preview

### Foreground Service
- [ ] Start with `startForeground(NOTIFICATION_ID, notification)`
- [ ] Use `NotificationController` for notification management
- [ ] Register `BroadcastReceiver` for ACTION_STOP and ACTION_TOGGLE_MUTE
- [ ] Stop with `stopForeground(STOP_FOREGROUND_REMOVE)`

### Reconnection
- [ ] Inject `ReconnectPolicy`
- [ ] Transition to `Reconnecting(attempt, nextRetryMs)` on disconnect
- [ ] Use `policy.nextDelayMs(attempt)` for delay calculation
- [ ] Use `policy.shouldRetry(attempt)` to check if retry allowed
- [ ] Call `policy.reset()` on successful reconnection

### Credential & Settings Handling
- [ ] Fetch `EndpointProfile` via `EndpointProfileRepository.getById()`
- [ ] Build RTMP URL: `profile.rtmpUrl + "/" + profile.streamKey`
- [ ] Read `StreamConfig` from `SettingsRepository`
- [ ] Use `RedactingLogger` for all logging

### Thermal & Battery Handling
- [ ] Monitor `ThermalMonitor` for thermal level changes
- [ ] Monitor battery level for critical threshold
- [ ] Stop stream with appropriate `StopReason` when limits reached
- [ ] Update `StreamStats.thermalLevel` for UI display

### Testing
- [ ] Mock `EncoderBridge` for unit tests
- [ ] Mock `EndpointProfileRepository`
- [ ] Mock `SettingsRepository`
- [ ] Mock `ReconnectPolicy`
- [ ] Use `TestDispatchers` for coroutine testing
- [ ] Test all state transitions
- [ ] Test all StopReason cases
- [ ] Test reconnection logic
- [ ] Test credential redaction in logs

---

## 📋 Files to Review in Order

1. **Models** → Understand data structures
   ```bash
   less app/src/main/java/com/port80/app/data/model/*.kt
   ```

2. **Interfaces** → Understand contracts
   ```bash
   less app/src/main/java/com/port80/app/service/*.kt
   ```

3. **Repositories** → Understand data access patterns
   ```bash
   less app/src/main/java/com/port80/app/data/*Repository*.kt
   ```

4. **Security** → Understand credential protection
   ```bash
   less app/src/main/java/com/port80/app/crash/*.kt
   less app/src/main/java/com/port80/app/util/*.kt
   ```

5. **DI Modules** → Understand dependency wiring
   ```bash
   less app/src/main/java/com/port80/app/di/*.kt
   ```

6. **Tests** → Understand expected behavior
   ```bash
   less app/src/test/java/com/port80/app/**/*.kt
   ```

---

## 🚦 Status

| Component | Status | Notes |
|-----------|--------|-------|
| Data Models | ✅ Complete | All 7 models implemented |
| Repositories | ✅ Complete | Encrypted storage + DataStore |
| Service Interfaces | ✅ Complete | Control, Encoder, Notification |
| Security Utils | ✅ Complete | Sanitizer, Logger, ACRA |
| DI Configuration | ✅ Complete | 5 Hilt modules, all bindings |
| Unit Tests | ✅ Complete | 50+ tests for all support code |
| Build Config | ✅ Complete | Gradle + Manifest |
| **StreamingService** | ❌ **TODO** | Main implementation required |

---

## 📞 How to Proceed

1. **Understand the architecture** by reading [CODEBASE_REFERENCE.md](./CODEBASE_REFERENCE.md)
2. **Review the specification** in [SPECIFICATION.md](./SPECIFICATION.md)
3. **Check the implementation plan** in [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md)
4. **Start implementing** `StreamingService` using the checklist above
5. **Reference existing code** for patterns and best practices
6. **Write tests** as you go, following the test patterns established

---

## 📖 Key Concepts

### StateFlow Pattern
All state is published via `StateFlow<T>`:
- **Producers:** StreamingService (owns mutable state)
- **Consumers:** ViewModels, Activities (observe read-only)
- **Thread-safe:** Coroutine-safe, lifecycle-aware

### Repository Pattern
All data access goes through repository interfaces:
- **EndpointProfileRepository** → Encrypted credential storage
- **SettingsRepository** → Non-sensitive user preferences
- **Testable:** Mock implementations for unit tests

### Credential Protection
Multi-layered security:
1. **Encryption:** EncryptedSharedPreferences + Android Keystore
2. **Sanitization:** CredentialSanitizer removes secrets from strings
3. **Logging:** RedactingLogger automatically redacts sensitive data
4. **Crash reports:** ACRA excludes sensitive fields and sanitizes others

### Reconnection Strategy
Intelligent retry logic:
- **Exponential backoff** prevents overwhelming the server
- **Random jitter** prevents synchronized reconnection storms
- **Pluggable interface** allows custom policies
- **Graceful degradation** stops retrying after max attempts

---

## ✨ Quality Attributes

- **Secure** — Industry-standard credential protection
- **Testable** — All components mockable via interfaces
- **Maintainable** — Clear separation of concerns
- **Scalable** — Single-responsibility principle throughout
- **Reactive** — Coroutine-based async throughout
- **Modern** — Kotlin, Jetpack, Android best practices

---

**Last Updated:** March 2024  
**Completeness:** 29/30 files (97%)  
**Status:** Ready for StreamingService implementation

