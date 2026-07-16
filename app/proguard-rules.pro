# Add project specific ProGuard rules here.
# Optimizations
-optimizations !code/simplification/arithmetic,!field/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Keep the foreground service
-keep class com.example.aetherandroid.AetherService { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep the application class
-keep class com.example.aetherandroid.AetherApplication { *; }