# ============================================================================
# ProGuard / R8 rules for StreamCaster
# ============================================================================
#
# R8 is the code shrinker that runs during release builds. It does three things:
#   1. Removes unused classes, methods, and fields ("tree shaking")
#   2. Renames remaining classes/methods to short names ("obfuscation")
#   3. Optimizes bytecode for smaller APK size
#
# Some libraries use reflection (looking up classes by name at runtime).
# If R8 renames or removes those classes, the app will crash. The rules
# below tell R8 "don't touch these" so reflection keeps working.
# ============================================================================


# ── RootEncoder (RTMP streaming library) ─────────────────────────────────────
# RootEncoder (com.pedro.*) uses reflection internally for MediaCodec codec
# discovery and camera configuration. If R8 renames or removes these classes,
# streaming will crash at runtime with ClassNotFoundException.
-keep class com.pedro.** { *; }
-dontwarn com.pedro.**


# ── ACRA (Crash reporting) ───────────────────────────────────────────────────
# ACRA uses reflection to discover annotated configuration classes and to
# build crash reports. It also reads annotations at runtime to determine
# which fields to include in reports.
-keep class org.acra.** { *; }
-dontwarn org.acra.**

# Keep any class annotated with ACRA annotations (e.g. @AcraHttpSender).
# Without this, R8 would strip the annotations and ACRA wouldn't know
# how to configure itself.
-keep @org.acra.annotation.* class * { *; }
-keepattributes *Annotation*


# ── Hilt (Dependency Injection) ──────────────────────────────────────────────
# Hilt generates component classes at compile time using annotation processing.
# While Hilt bundles its own consumer ProGuard rules, these safety keeps
# prevent edge-case issues where generated classes get stripped.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Hilt-generated entry point interfaces. R8 can sometimes remove these
# because they look unused from a static analysis perspective, but Hilt
# accesses them via reflection at runtime.
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponentManager { *; }


# ── EncryptedSharedPreferences (AndroidX Security-Crypto) ────────────────────
# EncryptedSharedPreferences uses the Android Keystore and Google Tink crypto
# library under the hood. Tink discovers cipher implementations via reflection.
# If these classes are removed, credential storage will fail at runtime.
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**


# ── Data Models (serialization) ──────────────────────────────────────────────
# These are the app's data classes (EndpointProfile, StreamConfig, etc.).
# They may be serialized/deserialized by DataStore or passed across process
# boundaries. Keeping them prevents R8 from renaming fields, which would
# break serialization.
-keep class com.port80.app.data.model.** { *; }


# ── Kotlin Coroutines ────────────────────────────────────────────────────────
# Coroutines use reflection to discover the coroutine context and to report
# meaningful stack traces in crash reports. Without these rules, coroutine
# stack traces become unreadable and some internal machinery can break.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ServiceLoader-based discovery used by coroutines dispatchers.
# R8 can strip META-INF/services entries; this prevents that.
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}

# Keep coroutine debug metadata so stack traces remain useful in crash reports.
-keepattributes SourceFile,LineNumberTable


# ── Kotlin (general) ────────────────────────────────────────────────────────
# Kotlin's reflection and metadata annotations are used by serialization
# libraries and some DI frameworks. Keep them to prevent subtle runtime errors.
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}


# ── Build tooling (compile-time only) ────────────────────────────────────────
# Google AutoService references javax.annotation.processing classes that are
# only available at compile time (not in the Android runtime). These warnings
# are safe to suppress because the processor code is never called at runtime.
-dontwarn javax.annotation.processing.AbstractProcessor
-dontwarn javax.annotation.processing.SupportedOptions


# ── Debugging aids ───────────────────────────────────────────────────────────
# Keep source file names and line numbers in stack traces so crash reports
# (from ACRA or logcat) show meaningful locations instead of obfuscated names.
# This doesn't significantly increase APK size.
-renamesourcefileattribute SourceFile
