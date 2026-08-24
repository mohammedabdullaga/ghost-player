# -------------------------------------------------------------------------
# ArenaLive - Optimized Scoped Rules (Stability Fix)
# -------------------------------------------------------------------------

# 1. Shrinking & Optimization
-optimizationpasses 2
-allowaccessmodification

# 2. Database (ObjectBox)
-keep class io.objectbox.** { *; }
-keep interface io.objectbox.** { *; }
-keep enum io.objectbox.** { *; }
-keep @io.objectbox.annotation.Entity class * { *; }
-keepclassmembers class * {
    @io.objectbox.annotation.Id long id;
    @io.objectbox.annotation.Index *;
}
# Scoped Cursor and generated model keeps
-keep class com.eliteprime.app.data.local.entity.*_ { *; }
-keep class com.eliteprime.app.data.local.entity.**Cursor { *; }
# Keep the generated MyObjectBox (using a more lenient pattern if package varies)
-keep class com.eliteprime.app.data.local.entity.MyObjectBox { *; }
-keep class **.MyObjectBox { *; }

# 3. Serialization & Remote Models (Gson / Retrofit / Kotlin Serialization)
# Keep all data models used for JSON parsing to prevent mapping failures
-keep class com.eliteprime.app.data.remote.** { *; }
-keepclassmembers class com.eliteprime.app.data.remote.** {
    <fields>;
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class com.eliteprime.app.data.local.entity.** { *; }
-keepclassmembers class com.eliteprime.app.data.local.entity.** {
    <fields>;
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class com.eliteprime.app.domain.model.** { *; }
-keepclassmembers class com.eliteprime.app.domain.model.** {
    <fields>;
}

# Preserve fields annotated with @SerializedName everywhere
-keepclassmembers class ** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 4. Media Players (libVLC)
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }

# 5. Player Engine Implementation
-keep class com.eliteprime.app.player.playback.CapabilityAwareMediaCodecSelector { *; }
-keepclassmembers enum com.eliteprime.app.player.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 6. Dependency Injection (Hilt / Dagger)
-keep class * extends android.app.Application
-keep class * extends android.app.Activity
-keep class * extends androidx.fragment.app.Fragment
-keep class * extends androidx.lifecycle.ViewModel
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep class **_HiltModules* { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }

# 7. Kotlin Serialization
-keepattributes *Annotation*, EnclosingMethod, InnerClasses
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 8. Debugging Attributes & Source Mapping
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses, Exceptions, SourceFile, LineNumberTable

# 9. Log Stripping
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# 10. Refined Warning Suppression
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn okio.**
-dontwarn retrofit2.**
