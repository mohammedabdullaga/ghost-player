# -------------------------------------------------------------------------
# ArenaLive - "Safe-Mode" ProGuard Rules
# -------------------------------------------------------------------------
# Completely disables shrinking and obfuscation for the app's code to
# ensure 100% compatibility with all devices and servers, effectively
# removing the app logic from the ProGuard process.

# 1. Disable R8/ProGuard Core Features for the App
-dontshrink
-dontobfuscate
-dontoptimize

# 2. Keep EVERYTHING in the app package
-keep class com.eliteprime.app.** { *; }
-keep interface com.eliteprime.app.** { *; }

# 3. Keep attributes for reflection and serialization
-keepattributes *

# 4. Database (ObjectBox)
-keep class io.objectbox.** { *; }
-keep interface io.objectbox.** { *; }
-keep enum io.objectbox.** { *; }

# 5. Networking (Retrofit / OkHttp / Gson)
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }

# 6. Media Players (libVLC & Media3)
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }

# 7. Dependency Injection (Hilt / Dagger)
-keep class dagger.** { *; }
-keep interface dagger.** { *; }
-keep class com.google.dagger.** { *; }

# 8. Log Stripping (Optional, but kept for cleanliness)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# 9. Avoid warnings from 3rd party libraries
-dontnote **
-dontwarn **
