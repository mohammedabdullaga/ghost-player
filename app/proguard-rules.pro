# -------------------------------------------------------------------------
# ArenaLive - "Ultra-Soft" Stability & Speed Rules
# -------------------------------------------------------------------------
# These rules prioritize 100% device compatibility and runtime performance.
# We minimize obfuscation on critical paths (Player, Network, DB) to avoid
# reflection overhead and ensure smooth execution on low-end TV hardware.

# 1. Disable Aggressive Optimization
# -------------------------------------------------------------------------
-optimizationpasses 1
-allowaccessmodification
-dontnote **
-dontwarn **

# 2. Main App Logic Protection (NO OBFUSCATION)
# -------------------------------------------------------------------------
# We keep the entire app package structure and names to ensure speed and
# to prevent any issues with reflection, serialization, or JNI.
-keep class com.eliteprime.app.** { *; }
-keep interface com.eliteprime.app.** { *; }

# 3. Database Layer (ObjectBox)
# -------------------------------------------------------------------------
-keep class io.objectbox.** { *; }
-keep interface io.objectbox.** { *; }
-keep enum io.objectbox.** { *; }

# 4. Networking & API (Retrofit, OkHttp, Gson)
# -------------------------------------------------------------------------
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }

# 5. Media Players (Media3, ExoPlayer, libVLC)
# -------------------------------------------------------------------------
# Player engines MUST be identical to source for hardware decoder stability.
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }

# 6. Jetpack Compose & UI Performance
# -------------------------------------------------------------------------
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }
-keep class androidx.navigation.** { *; }
-keep interface androidx.navigation.** { *; }

# 7. Async & Threading (Coroutines)
# -------------------------------------------------------------------------
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }

# 8. Dependency Injection (Hilt/Dagger)
# -------------------------------------------------------------------------
-keep class dagger.** { *; }
-keep interface dagger.** { *; }
-keep class com.google.dagger.** { *; }

# 9. Resource & Attribute Hardening
# -------------------------------------------------------------------------
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses, Exceptions, SourceFile, LineNumberTable

# 10. General Cleanliness
# -------------------------------------------------------------------------
# Remove logs to keep the binary clean, but keep everything else fast.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# Maintain original filenames in stacktraces for easier debugging of "silent" device errors
-keepattributes SourceFile,LineNumberTable
