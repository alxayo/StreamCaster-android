# Complete Codebase Files Provided for StreamingService Implementation

## Summary
✅ **29 complete source files** provided with full contents
✅ **All interfaces, models, and supporting code** ready for integration
✅ **Security utilities** already implemented
✅ **Tests** demonstrating expected behavior
✅ **DI modules** properly configured with Hilt

---

## 1. DATA MODELS (7 files)

### StreamState.kt
**Location:** `app/src/main/java/com/port80/app/data/model/StreamState.kt`
**Purpose:** State machine for streaming lifecycle
**States:** Idle, Connecting, Live, Reconnecting, Stopping, Stopped
```kotlin
sealed class StreamState {
    data object Idle : StreamState()
    data object Connecting : StreamState()
    data class Live(val cameraActive: Boolean, val isMuted: Boolean) : StreamState()
    data class Reconnecting(val attempt: Int, val nextRetryMs: Long) : StreamState()
    data object Stopping : StreamState()
    data class Stopped(val reason: StopReason) : StreamState()
}
```

### StreamStats.kt
**Location:** `app/src/main/java/com/port80/app/data/model/StreamStats.kt`
**Purpose:** Real-time streaming statistics (updated ~1Hz)
**Fields:** videoBitrateKbps, audioBitrateKbps, fps, droppedFrames, resolution, durationMs, isRecording, thermalLevel

### StopReason.kt
**Location:** `app/src/main/java/com/port80/app/data/model/StopReason.kt`
**Purpose:** Explains why stream stopped
**Values:** USER_REQUEST, ERROR_ENCODER, ERROR_AUTH, ERROR_CAMERA, ERROR_AUDIO, THERMAL_CRITICAL, BATTERY_CRITICAL

### ThermalLevel.kt
**Location:** `app/src/main/java/com/port80/app/data/model/ThermalLevel.kt`
**Purpose:** Device thermal states
**Values:** NORMAL, MODERATE, SEVERE, CRITICAL

### Resolution.kt
**Location:** `app/src/main/java/com/port80/app/data/model/Resolution.kt`
**Purpose:** Video resolution representation
**Fields:** width, height
**Methods:** toString() → "1280x720", label → "720p"

### StreamConfig.kt
**Location:** `app/src/main/java/com/port80/app/data/model/StreamConfig.kt`
**Purpose:** Complete streaming session configuration (snapshot)
**Fields:** profileId, videoEnabled, audioEnabled, resolution, fps, videoBitrateKbps, etc.

### EndpointProfile.kt
**Location:** `app/src/main/java/com/port80/app/data/model/EndpointProfile.kt`
**Purpose:** RTMP endpoint configuration with encrypted credentials
**Fields:** id, name, rtmpUrl, streamKey, username, password, isDefault
**Security:** All credentials encrypted via Keystore

---

## 2. SERVICE INTERFACES (3 files)

### StreamingServiceControl.kt
**Location:** `app/src/main/java/com/port80/app/service/StreamingServiceControl.kt`
**Purpose:** Contract for StreamingService (bind to clients)
**Key Methods:**
- `startStream(profileId: String)` - Idempotent start
- `stopStream()` - Idempotent stop with graceful shutdown
- `toggleMute()` - Audio mute toggle
- `switchCamera()` - Front/back camera switch
- `attachPreviewSurface(holder: SurfaceHolder)` - Camera preview
- `detachPreviewSurface()` - Stop preview display

**Key Properties:**
- `streamState: StateFlow<StreamState>` - Read-only state observable
- `streamStats: StateFlow<StreamStats>` - Real-time stats at ~1Hz

### EncoderBridge.kt
**Location:** `app/src/main/java/com/port80/app/service/EncoderBridge.kt`
**Purpose:** Abstraction over RootEncoder's RtmpCamera2 (enables testing)
**Key Methods:**
- `startPreview(holder: SurfaceHolder)` - Show camera preview
- `stopPreview()` - Stop preview display
- `connect(url: String, streamKey: String)` - Connect to RTMP server
- `disconnect()` - Disconnect from RTMP server
- `switchCamera()` - Toggle front/back camera
- `setVideoBitrateOnFly(bitrateKbps: Int)` - Adaptive bitrate adjustment
- `release()` - Clean up all resources
- `isStreaming(): Boolean` - Connection status check

### NotificationController.kt
**Location:** `app/src/main/java/com/port80/app/service/NotificationController.kt`
**Purpose:** Foreground service notification management
**Key Methods:**
- `createNotification(state: StreamState, stats: StreamStats): Notification` - Initial notification
- `updateNotification(state: StreamState, stats: StreamStats)` - Periodic updates
- `cancel()` - Remove notification

**Key Features:**
- Broadcast actions: ACTION_STOP, ACTION_TOGGLE_MUTE
- Notification ID: 1001
- Channel ID: "stream_service_channel"
- Debounced actions (≥500ms)
- Tap opens Activity (NOT startForegroundService)

---

## 3. REPOSITORY INTERFACES (2 files)

### EndpointProfileRepository.kt
**Location:** `app/src/main/java/com/port80/app/data/EndpointProfileRepository.kt`
**Purpose:** Interface for RTMP endpoint profile management
**Key Methods:**
- `getAll(): Flow<List<EndpointProfile>>` - Observable all profiles
- `getById(id: String): EndpointProfile?` - Fetch single profile
- `getDefault(): EndpointProfile?` - Fetch default profile
- `save(profile: EndpointProfile)` - Create/update profile
- `delete(id: String)` - Delete profile
- `setDefault(id: String)` - Mark as default
- `isKeystoreAvailable(): Boolean` - Check Keystore availability

### SettingsRepository.kt
**Location:** `app/src/main/java/com/port80/app/data/SettingsRepository.kt`
**Purpose:** Interface for user preferences (non-sensitive)
**Groups:**
- **Video:** resolution, fps, videoBitrateKbps, keyframeIntervalSec
- **Audio:** audioBitrateKbps, audioSampleRate, stereo
- **General:** abrEnabled, defaultCameraId, orientationLocked, preferredOrientation
- **Battery:** lowBatteryThreshold, criticalBatteryThreshold
- **Recording:** localRecordingEnabled
**Default Values:** Includes full set of sensible defaults (e.g., 1280x720, 30fps, 2500kbps)

---

## 4. REPOSITORY IMPLEMENTATIONS (2 files)

### EncryptedEndpointProfileRepository.kt
**Location:** `app/src/main/java/com/port80/app/data/EncryptedEndpointProfileRepository.kt`
**Implementation Details:**
- Uses EncryptedSharedPreferences with MasterKeys.AES256_GCM_SPEC
- Encrypts both keys and values (PrefKeyEncryptionScheme.AES256_SIV + PrefValueEncryptionScheme.AES256_GCM)
- Stores profiles as JSON via internal ProfileSerializer
- Maintains profiles index in KEY_PROFILES_INDEX
- Tracks default profile in KEY_DEFAULT_PROFILE_ID
- Gracefully handles Keystore unavailability
- Emits updates via MutableStateFlow<List<EndpointProfile>>

**Internal ProfileSerializer Object:**
- `toMap(profile: EndpointProfile): Map<String, Any?>` - Convert to testable map
- `fromMap(map: Map<String, Any?>): EndpointProfile` - Reconstruct from map
- `toJsonString(profile: EndpointProfile): String` - Serialize to JSON
- `fromJsonString(jsonString: String): EndpointProfile` - Deserialize from JSON

### DataStoreSettingsRepository.kt
**Location:** `app/src/main/java/com/port80/app/data/DataStoreSettingsRepository.kt`
**Implementation Details:**
- Uses Jetpack DataStore (Preferences protocol buffer format)
- Provides Flow<T> for reactive updates
- Implements all methods from SettingsRepository interface
- Includes complete default values for all settings
- Stored at: `data/data/com.port80.app/files/datastore/settings.preferences_pb`

---

## 5. SECURITY UTILITIES (3 files)

### CredentialSanitizer.kt
**Location:** `app/src/main/java/com/port80/app/crash/CredentialSanitizer.kt`
**Purpose:** Single source of truth for credential redaction
**Method:** `sanitize(input: String): String`
**Removes:**
1. RTMP/RTMPS stream keys in URLs
   - `rtmp://host/app/secret_key` → `rtmp://host/app/****`
2. Embedded credentials in URLs
   - `rtmp://user:pass@host` → `rtmp://****:****@host`
3. Sensitive query parameters (case-insensitive)
   - Parameters: streamKey, stream_key, key, password, passwd, auth, token, secret
   - `streamKey=abc123` → `streamKey=****`

**Regex Patterns Used:**
```kotlin
RTMP_URL_PATTERN = Regex("""(rtmps?://[^/\s]+/[^/\s]+)/\S+""")
EMBEDDED_CREDENTIALS_PATTERN = Regex("""(rtmps?://)([^:@\s]+:[^@\s]+)@""")
SENSITIVE_PARAM_PATTERN = Regex("""((?:streamKey|stream_key|key|password|passwd|auth|token|secret)=)[^\s&]+""")
```

### RedactingLogger.kt
**Location:** `app/src/main/java/com/port80/app/util/RedactingLogger.kt`
**Purpose:** Logging wrapper with automatic credential sanitization
**Methods:**
- `d(tag: String, message: String)` - Debug
- `i(tag: String, message: String)` - Info
- `w(tag: String, message: String)` - Warning
- `w(tag: String, message: String, throwable: Throwable)` - Warning with exception
- `e(tag: String, message: String)` - Error
- `e(tag: String, message: String, throwable: Throwable)` - Error with exception

**Usage:** Replace `android.util.Log.d()` with `RedactingLogger.d()` for messages containing URLs/keys

### AcraConfigurator.kt
**Location:** `app/src/main/java/com/port80/app/crash/AcraConfigurator.kt`
**Purpose:** Configure ACRA crash reporting with security
**Methods:**
- `init(app: Application, reportUrl: String? = null)` - Initialize ACRA
- `sanitizeReport(data: MutableMap<String, String>)` - Sanitize crash report fields

**Security Features:**
1. Excluded Fields (prevented from reporting):
   - LOGCAT, SHARED_PREFERENCES, DUMPSYS_MEMINFO, THREAD_DETAILS
   - These could leak stream keys logged internally by RootEncoder
2. Included Safe Fields:
   - STACK_TRACE, ANDROID_VERSION, APP_VERSION_*, PHONE_MODEL, BRAND, PRODUCT, CUSTOM_DATA, BUILD_CONFIG, USER_COMMENT
3. HTTPS-Only: Rejects plaintext HTTP for report URL
4. Automatic Sanitization: All string fields run through CredentialSanitizer before sending

---

## 6. DEPENDENCY INJECTION MODULES (5 files)

### AppModule.kt
**Location:** `app/src/main/java/com/port80/app/di/AppModule.kt`
**Scope:** @Singleton @InstallIn(SingletonComponent::class)
**Provides:**
- `DataStore<Preferences>` - Used by SettingsRepository
- `ConnectivityManager` - For network monitoring
- `PowerManager` - For thermal and battery states
- `OverlayManager` - No-op implementation (extensible hook)

### DataModule.kt
**Location:** `app/src/main/java/com/port80/app/di/DataModule.kt`
**Scope:** @Singleton @InstallIn(SingletonComponent::class)
**Bindings:**
- `EndpointProfileRepository` → `EncryptedEndpointProfileRepository`

### RepositoryModule.kt
**Location:** `app/src/main/java/com/port80/app/di/RepositoryModule.kt`
**Scope:** @Singleton @InstallIn(SingletonComponent::class)
**Bindings:**
- `SettingsRepository` → `DataStoreSettingsRepository`

### StreamModule.kt
**Location:** `app/src/main/java/com/port80/app/di/StreamModule.kt`
**Scope:** @Singleton @InstallIn(SingletonComponent::class)
**Provides:**
- `ReconnectPolicy` → `ExponentialBackoffReconnectPolicy()`

### CameraModule.kt
**Location:** `app/src/main/java/com/port80/app/di/CameraModule.kt`
**Scope:** @Singleton @InstallIn(SingletonComponent::class)
**Bindings:**
- `DeviceCapabilityQuery` → `Camera2CapabilityQuery`

---

## 7. RECONNECTION LOGIC (1 file)

### ReconnectPolicy.kt
**Location:** `app/src/main/java/com/port80/app/service/ReconnectPolicy.kt`

**Interface:**
```kotlin
interface ReconnectPolicy {
    fun nextDelayMs(attempt: Int): Long  // Calculate delay with jitter
    fun shouldRetry(attempt: Int): Boolean  // Check if another retry is allowed
    fun reset()  // Reset retry counter
}
```

**Implementation: ExponentialBackoffReconnectPolicy**
```kotlin
class ExponentialBackoffReconnectPolicy(
    baseDelayMs: Long = 3_000L,
    maxDelayMs: Long = 60_000L,
    maxAttempts: Int = Int.MAX_VALUE,
    jitterFactor: Double = 0.2
) : ReconnectPolicy
```

**Backoff Schedule:**
| Attempt | Exponential | With ±20% Jitter |
|---------|------------|------------------|
| 0 | 3s | ~2.4–3.6s |
| 1 | 6s | ~4.8–7.2s |
| 2 | 12s | ~9.6–14.4s |
| 3 | 24s | ~19.2–28.8s |
| 4 | 48s | ~38.4–57.6s |
| 5+ | 60s (cap) | ~48–72s |

---

## 8. TEST FILES (4 files)

### CredentialSanitizerTest.kt
**Location:** `app/src/test/java/com/port80/app/crash/CredentialSanitizerTest.kt`
**Tests:**
- RTMP URL stream key redaction
- RTMPS URL stream key redaction
- Query parameter sanitization
- Embedded credential removal
- Multiple URLs in single string
- Case-insensitive parameter matching
- Safe strings unmodified
- Empty string handling

### AcraConfiguratorTest.kt
**Location:** `app/src/test/java/com/port80/app/crash/AcraConfiguratorTest.kt`
**Tests:**
- Crash report field sanitization
- Multiple sensitive fields
- Empty report handling
- Safe data preservation

### DataStoreSettingsRepositoryTest.kt
**Location:** `app/src/test/java/com/port80/app/data/DataStoreSettingsRepositoryTest.kt`
**Tests:**
- Default values for all 15+ settings
- Round-trip (set then get) for each setting
- Uses temporary in-memory DataStore
- Verifies correct types and ranges

### EndpointProfileSerializationTest.kt
**Location:** `app/src/test/java/com/port80/app/data/EndpointProfileSerializationTest.kt`
**Tests:**
- Full profile round-trip serialization
- Minimal profile round-trip
- Map representation
- Optional field handling
- Missing field deserialization
- Special character preservation
- Empty string field handling

---

## 9. BUILD CONFIGURATION (2 files)

### app/build.gradle.kts
**Location:** `app/build.gradle.kts`
**Key Settings:**
- Namespace: `com.port80.app`
- Compile SDK: 35
- Min SDK: 23
- Target SDK: 35
- Source compatibility: Java 17

**Product Flavors:**
- `foss` - F-Droid version (no GMS)
- `gms` - Google Play version

**Key Dependencies:**
- Compose (UI framework)
- Hilt (dependency injection)
- RootEncoder (RTMP streaming)
- EncryptedSharedPreferences (credential storage)
- DataStore (settings storage)
- ACRA (crash reporting)
- Coroutines, Lifecycle, etc.

### AndroidManifest.xml
**Location:** `app/src/main/AndroidManifest.xml`
**Permissions:**
- CAMERA, RECORD_AUDIO, INTERNET, ACCESS_NETWORK_STATE
- FOREGROUND_SERVICE, FOREGROUND_SERVICE_CAMERA, FOREGROUND_SERVICE_MICROPHONE
- POST_NOTIFICATIONS (API 33+)
- WAKE_LOCK

**Components:**
- MainActivity (main activity, exported)
- StreamingService (foreground service, not exported)

---

## File Organization Map

```
app/src/
├── main/
│   ├── java/com/port80/app/
│   │   ├── data/
│   │   │   ├── model/
│   │   │   │   ├── StreamState.kt ........................... ✅
│   │   │   │   ├── StreamStats.kt ........................... ✅
│   │   │   │   ├── StopReason.kt ............................ ✅
│   │   │   │   ├── ThermalLevel.kt .......................... ✅
│   │   │   │   ├── Resolution.kt ............................ ✅
│   │   │   │   ├── StreamConfig.kt .......................... ✅
│   │   │   │   └── EndpointProfile.kt ....................... ✅
│   │   │   ├── EndpointProfileRepository.kt ................. ✅
│   │   │   ├── EncryptedEndpointProfileRepository.kt ......... ✅
│   │   │   ├── SettingsRepository.kt ........................ ✅
│   │   │   └── DataStoreSettingsRepository.kt ............... ✅
│   │   ├── service/
│   │   │   ├── StreamingServiceControl.kt ................... ✅
│   │   │   ├── EncoderBridge.kt ............................. ✅
│   │   │   ├── NotificationController.kt .................... ✅
│   │   │   ├── ReconnectPolicy.kt ........................... ✅
│   │   │   └── StreamingService.kt .......................... ❌ (TO IMPLEMENT)
│   │   ├── crash/
│   │   │   ├── CredentialSanitizer.kt ....................... ✅
│   │   │   └── AcraConfigurator.kt .......................... ✅
│   │   ├── util/
│   │   │   └── RedactingLogger.kt ........................... ✅
│   │   └── di/
│   │       ├── AppModule.kt ................................. ✅
│   │       ├── DataModule.kt ................................ ✅
│   │       ├── RepositoryModule.kt .......................... ✅
│   │       ├── StreamModule.kt .............................. ✅
│   │       └── CameraModule.kt .............................. ✅
│   └── AndroidManifest.xml .................................. ✅
├── test/
│   └── java/com/port80/app/
│       ├── crash/
│       │   ├── CredentialSanitizerTest.kt ................... ✅
│       │   └── AcraConfiguratorTest.kt ...................... ✅
│       └── data/
│           ├── DataStoreSettingsRepositoryTest.kt ........... ✅
│           └── EndpointProfileSerializationTest.kt .......... ✅
└── build.gradle.kts ........................................ ✅

TOTAL: 29 Files ✅ Complete | 1 File ❌ To Implement (StreamingService)
```

---

## Quick Links

**Full Reference Document:**
- `/Users/alex/Code/rtmp-client/android/CODEBASE_REFERENCE.md`

**Key Architecture Diagrams:**
- State Machine: Idle → Connecting → Live/Reconnecting → Stopping → Stopped
- Credential Flow: EncryptedSharedPreferences → ProfileSerializer → EndpointProfile
- Dependency Injection: AppModule → all @Singleton components

**Security Checklist:**
- ✅ Credentials encrypted via Keystore
- ✅ Logs sanitized via CredentialSanitizer + RedactingLogger
- ✅ Crash reports sanitized + HTTPS-only
- ✅ No credential Intent extras
- ✅ Graceful Keystore failure handling

**Ready to Implement:**
- All interfaces defined
- All supporting code complete
- All tests passing
- All DI configured
- Only StreamingService class remains

---

Generated: 2024  
Status: **All Requested Files Provided Complete**
