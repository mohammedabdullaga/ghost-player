# -------------------------------------------------------------------------
# ArenaLive - Production Performance & Hardening Rules
# -------------------------------------------------------------------------

# 1. Balanced Optimization (Performance over APK Size)
# -------------------------------------------------------------------------
-allowaccessmodification
# Reduced to 2 passes: 5 passes can create complex bytecode that slows down
# older Android devices (ART) due to excessive inlining.
-optimizationpasses 2
# DO NOT use -overloadaggressively as it can conflict with hardware-specific
# reflection used by video decoders on some TV devices.

# 2. Jetpack Compose Performance
# -------------------------------------------------------------------------
-keep class androidx.compose.runtime.** { *; }
-keep @androidx.compose.runtime.Composable class * { *; }
-dontwarn androidx.compose.**

# 3. Kotlin Coroutines & Flow
# -------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherLoader {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.HandlerContext {}
-keepclassmembernames class kotlinx.coroutines.internal.LockFreeTaskQueue {
    long _cur;
}
-keepclassmembernames class kotlinx.coroutines.internal.ResizableAtomicArray {
    java.lang.Object[] array;
}
-dontwarn kotlinx.coroutines.**

# 4. ObjectBox Database Stability
# -------------------------------------------------------------------------
-keep class io.objectbox.** { *; }
-keep interface io.objectbox.** { *; }
-keep class com.eliteprime.app.data.local.entity.MyObjectBox { *; }
-keep class com.eliteprime.app.data.local.entity.Internal** { *; }
-keep @io.objectbox.annotation.Entity class * { *; }
-keepclassmembers class * {
    @io.objectbox.annotation.Id long id;
    @io.objectbox.annotation.Index *;
}
-keep class **Cursor { *; }
-keep class **_ { *; }

# 5. Serialization & Network (Gson, Retrofit, OkHttp)
# -------------------------------------------------------------------------
# Keep all data models for safe parsing
-keep class com.eliteprime.app.data.remote.** { *; }
-keep class com.eliteprime.app.domain.model.** { *; }
-keep class com.eliteprime.app.data.local.entity.** { *; }

# Gson & Kotlin Serialization
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses, Exceptions
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp/Retrofit
-keep @retrofit2.http.** interface * { *; }
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# 6. Media3 (ExoPlayer) & libVLC
# -------------------------------------------------------------------------
# CRITICAL: Do not obfuscate player engines as they rely on JNI and reflection
# to talk to native hardware decoders.
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }
-keep class com.eliteprime.app.player.** { *; }
-keep interface com.eliteprime.app.player.** { *; }
-dontwarn androidx.media3.**
-dontwarn org.videolan.libvlc.**

# 7. Dependency Injection (Hilt/Dagger)
# -------------------------------------------------------------------------
-keep class * extends android.app.Application
-keep class * extends android.app.Activity
-keep class * extends androidx.fragment.app.Fragment
-keep class * extends androidx.lifecycle.ViewModel
-keep @dagger.hilt.android.AndroidEntryPoint class *

# 8. General Performance Hardening
# -------------------------------------------------------------------------
# Remove log statements to reduce APK size and slightly improve runtime speed
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# Preserve line numbers for crash reporting but rename file source
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
