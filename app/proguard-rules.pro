# -------------------------------------------------------------------------
# ArenaLive - High-Stability ProGuard Rules
# -------------------------------------------------------------------------
# EXTREMELY LENIENT POLICY: We exclude the entire app package from
# obfuscation and shrinking to ensure 100% parity with debug builds
# for critical business logic like Login and Syncing.

# 1. Disable Aggressive Optimization
-optimizationpasses 1
-allowaccessmodification
-dontnote **
-dontwarn **

# 2. Main App Logic - FULL EXCLUSION
# This "removes" the app logic from ProGuard as requested.
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
-keep class **.MyObjectBox { *; }

# 4. Serialization (Gson / Kotlin Serialization / Retrofit)
# Keep all field names and annotations for reliable JSON mapping
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses, Exceptions
-keepclassmembers class ** {
    @com.google.gson.annotations.SerializedName <fields>;
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

# 6. Media Players (libVLC & Media3)
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }

# 7. Dependency Injection (Hilt / Dagger)
-keep class dagger.** { *; }
-keep interface dagger.** { *; }
-keep class com.google.dagger.** { *; }
-keep class * extends android.app.Application
-keep class * extends android.app.Activity
-keep class * extends androidx.fragment.app.Fragment
-keep class * extends androidx.lifecycle.ViewModel
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep class **_HiltModules* { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }

# 8. Log Stripping
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# 9. Preservation of Stacktraces
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
