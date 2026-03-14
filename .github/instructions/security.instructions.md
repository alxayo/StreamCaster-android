---
description: "Use when working on credential storage, RTMP transport security, logging, ACRA crash reporting, Intent security, or any code handling stream keys, passwords, or auth tokens. Covers OWASP-aligned security requirements for StreamCaster."
---
# Security Requirements

## Credential Storage

- All stream keys, passwords, and auth tokens **must** use `EncryptedSharedPreferences` backed by Android Keystore.
- Never fall back to plain `SharedPreferences` if encryption fails — surface an error to the user.
- On Keystore unavailability (post-restore), prompt the user to re-enter credentials.

## Intent Security

The FGS start Intent carries **only a profile ID** (`String`):

```kotlin
// CORRECT
intent.putExtra("profileId", profile.id)

// WRONG — never do this
intent.putExtra("streamKey", profile.streamKey)  // ← NEVER
intent.putExtra("url", profile.rtmpUrl)          // ← NEVER
```

The service reads credentials from `EndpointProfileRepository` at runtime using the profile ID.

## Transport Security

- Credentials over RTMP (non-TLS) require an explicit user warning + per-attempt opt-in.
- RTMPS uses the **system default TrustManager**. No custom `X509TrustManager` implementations.
- Connection test button obeys the same transport security rules.

## Log Redaction

- **Every** log statement that touches RTMP URLs, stream keys, or auth tokens must go through `CredentialSanitizer`.
- Sanitization regex: `rtmp[s]?://([^/\s]+/[^/\s]+)/\S+` → `rtmp[s]://<host>/<app>/****`
- Also mask `streamKey=`, `key=`, `password=`, `auth=` query parameter values.
- This applies at all log levels in both debug and release builds.

## ACRA Crash Reports

- **Exclude:** `LOGCAT`, `SHARED_PREFERENCES`, `DUMPSYS_MEMINFO`, `THREAD_DETAILS`.
- **Include only:** `STACK_TRACE`, `ANDROID_VERSION`, `APP_VERSION_CODE`, `APP_VERSION_NAME`, `PHONE_MODEL`, `BRAND`, `PRODUCT`, `CUSTOM_DATA`, `CRASH_CONFIGURATION`, `BUILD_CONFIG`, `USER_COMMENT`.
- Register a `ReportTransformer` that runs `CredentialSanitizer.sanitize()` on all string fields.
- ACRA HTTP transport must enforce HTTPS. Plaintext `http://` requires explicit user opt-in.

## Manifest

- `android:allowBackup="false"` on `<application>`.
- No `android:usesCleartextTraffic="true"`.
