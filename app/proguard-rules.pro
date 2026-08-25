# -------------------------------------------------------------------------
# ArenaLive - Performance Optimized "Release-Safe" Rules
# -------------------------------------------------------------------------

# 1. Shrinking & Performance Optimization
# We enable optimization but disable obfuscation for the app package
# to ensure the UI logic and Data mapping remain 100% stable.
-optimizationpasses 2
-allowaccessmodification
-dontobfuscate

# 2. Main App Logic - STABILITY KEEPS
# This ensures all mobile/TV UI screens and business logic stay intact.
-keep class com.eliteprime.app.** { *; }
-keep interface com.eliteprime.app.** { *; }

# 3. Database (ObjectBox)
-keep class io.objectbox.** { *; }
-keep interface io.objectbox.** { *; }
-keep enum io.objectbox.** { *; }
-keep @io.objectbox.annotation.Entity class * { *; }
-keepclassmembers class * {
    @io.objectbox.annotation.Id long id;
    @io.objectbox.annotation.Index *;
}
-keep class com.eliteprime.app.data.local.entity.*_ { *; }
-keep class com.eliteprime.app.data.local.entity.**Cursor { *; }
-keep class com.eliteprime.app.data.local.entity.MyObjectBox { *; }

# 4. Serialization (Gson / Retrofit)
# Essential for Login and Syncing to work in Release builds
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses, Exceptions
-keepclassmembers class ** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 5. Networking (OkHttp / Retrofit)
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# 6. Media Players (libVLC & Media3)
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }
-keep class androidx.media3.** { *; }

# 7. Dependency Injection (Hilt / Dagger)
-keep class * extends android.app.Application
-keep class * extends android.app.Activity
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep class **_HiltModules* { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }

# 8. DISABLE DEBUG LOGS IN RELEASE
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# 9. Avoid warnings from 3rd party libraries
-dontnote **
-dontwarn **
