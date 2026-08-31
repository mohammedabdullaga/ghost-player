# -------------------------------------------------------------------------
# ArenaLive - Minimum Safe Protection (Mixed Device Compatibility)
# -------------------------------------------------------------------------

# Disable aggressive bytecode optimizations to prevent ART/JIT compiler bugs on budget TV boxes
-dontoptimize

# Preserve reflection attributes & line numbers for Crashlytics stack trace demangling
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Suppress harmless third-party library notes and warnings
-dontnote **
-dontwarn **

# -------------------------------------------------------------------------
# 1. Android Entry Points (Activities, Services, Workers, Receivers)
# -------------------------------------------------------------------------
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.app.Application
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends androidx.work.ListenableWorker
-keep public class * extends androidx.work.Worker

# -------------------------------------------------------------------------
# 2. Native JNI Bindings (Fast VPN / ByeDPI C Core)
# -------------------------------------------------------------------------
-keepclasseswithmembernames class * {
    native <methods>;
}
# MUST keep the class name itself — native-lib.c calls FindClass("com/eliteprime/app/data/remote/proxy/SecureStreamingManager")
-keep class com.eliteprime.app.data.remote.proxy.SecureStreamingManager { *; }
-keepclassmembers class com.eliteprime.app.data.remote.proxy.SecureStreamingManager {
    native <methods>;
}

# -------------------------------------------------------------------------
# 3. ObjectBox Database & Local Data Entities
# -------------------------------------------------------------------------
-keep class io.objectbox.** { *; }
-keep interface io.objectbox.** { *; }
-keep class com.eliteprime.app.data.local.entity.** { *; }
-keepclassmembers class com.eliteprime.app.data.local.entity.** { *; }
-keep class com.eliteprime.app.data.local.entity.MyObjectBox { *; }

# -------------------------------------------------------------------------
# 4. Media Players (Media3 ExoPlayer & libVLC)
# -------------------------------------------------------------------------
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }
-keepclassmembers class org.videolan.libvlc.** { *; }

-keep class androidx.media3.** { *; }
-keep class com.eliteprime.app.player.** { *; }
-keepclassmembers class com.eliteprime.app.player.** { *; }

# -------------------------------------------------------------------------
# 5. Serialization, Network DTOs & Data Models
# -------------------------------------------------------------------------
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep all Remote API models, responses, and DTOs
-keep class com.eliteprime.app.data.remote.** { *; }
-keepclassmembers class com.eliteprime.app.data.remote.** { *; }

# Keep all Domain models and ObjectBox entities
-keep class com.eliteprime.app.domain.model.** { *; }
-keepclassmembers class com.eliteprime.app.domain.model.** { *; }

-keep class com.eliteprime.app.data.remote.util.FlexibleStringAdapter { *; }

# Retrofit
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# kotlinx.serialization — must keep compiler-generated $serializer classes or R8 strips them
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }
# Keep all classes annotated with @Serializable (their companion $serializer is needed at runtime)
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers class * {
    *** Companion;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializer *** serializer(...);
}

# -------------------------------------------------------------------------
# 6. Dependency Injection (Hilt / Dagger)
# -------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class com.eliteprime.app.di.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @jakarta.inject.Inject <init>(...);
    @dagger.Provides <methods>;
}

# -------------------------------------------------------------------------
# 7. Firebase Services
# -------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-keepclassmembers class com.google.firebase.** { *; }
