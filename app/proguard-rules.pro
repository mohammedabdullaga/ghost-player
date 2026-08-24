# -------------------------------------------------------------------------
# ArenaLive - High-Stability ProGuard Rules
# -------------------------------------------------------------------------
# Optimized for reliable Login and Syncing by excluding critical business
# logic and data layers from obfuscation and shrinking.

# 1. Shrinking & Optimization
-optimizationpasses 2
-allowaccessmodification

# 2. Main App Logic - Critical Paths (NO OBFUSCATION)
# We exclude the data, repository, and domain layers to ensure login and
# syncing work exactly as they do in debug mode.
-keep class com.eliteprime.app.data.** { *; }
-keep interface com.eliteprime.app.data.** { *; }
-keep class com.eliteprime.app.domain.** { *; }
-keep interface com.eliteprime.app.domain.** { *; }
-keep class com.eliteprime.app.di.** { *; }
-keep interface com.eliteprime.app.di.** { *; }

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
-keep class **.MyObjectBox { *; }

# 4. Serialization (Gson / Kotlin Serialization)
# Keep everything in common models and network packages
-keepclassmembers class ** {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepattributes *Annotation*, EnclosingMethod, InnerClasses, Signature, Exceptions
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 5. Networking (Retrofit / OkHttp)
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# 6. Media Players (libVLC)
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }

# 7. Player Engine Implementation
-keep class com.eliteprime.app.player.** { *; }
-keep interface com.eliteprime.app.player.** { *; }

# 8. Dependency Injection (Hilt / Dagger)
-keep class * extends android.app.Application
-keep class * extends android.app.Activity
-keep class * extends androidx.fragment.app.Fragment
-keep class * extends androidx.lifecycle.ViewModel
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep class **_HiltModules* { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }

# 9. General Hardening & Debugging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# 10. Refined Warning Suppression
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn okio.**
