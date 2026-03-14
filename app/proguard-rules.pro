# ProGuard/R8 rules for StreamCaster
# Add project specific ProGuard rules here.

# Keep Hilt-generated components
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep ACRA classes
-keep class org.acra.** { *; }
