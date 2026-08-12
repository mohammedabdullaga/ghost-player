# -------------------------------------------------------------------------
# ArenaLive - Reverse Engineering Hardening Rules (STEP 1)
# -------------------------------------------------------------------------

# 1. Aggressive Obfuscation & Optimization
# Flatten the package hierarchy into a single root package (Temporarily disabled to debug login)
# -repackageclasses ''
# Allow R8 to change access modifiers for better inlining/merging
-allowaccessmodification
# Overload method names aggressively (multiple methods share same name if signatures differ)
-overloadaggressively
# Perform up to 5 optimization passes
-optimizationpasses 5

# 2. ObjectBox Database Protection
-keep class io.objectbox.** { *; }
-keep interface io.objectbox.** { *; }
-keep class com.eliteprime.app.data.local.entity.MyObjectBox { *; }
-keep class com.eliteprime.app.data.local.entity.Internal** { *; }

# Protect Entity classes
-keep @io.objectbox.annotation.Entity class * { *; }
-keepclassmembers class * {
    @io.objectbox.annotation.Id long id;
    @io.objectbox.annotation.Index *;
}
-keep class **Cursor { *; }
-keep class **_ { *; }

# 3. Serialization (Gson & Kotlin Serialization)
# Keep all data classes used for JSON parsing
-keep class com.eliteprime.app.data.remote.** { *; }
-keep class com.eliteprime.app.domain.model.** { *; }
-keep class com.eliteprime.app.data.local.entity.** { *; }

# Kotlin Serialization specific rules
-keepattributes *Annotation*, EnclosingMethod, InnerClasses
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Gson specific rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 4. Retrofit
-keep @retrofit2.http.** interface * { *; }
-keep class retrofit2.** { *; }
-keepclasseswithmembers interface * {
    @retrofit2.http.** <methods>;
}
# Prevent stripping of Hilt entry points
-keep class * extends android.app.Application
-keep class * extends android.app.Activity
-keep class * extends androidx.fragment.app.Fragment
-keep class * extends androidx.lifecycle.ViewModel
-keep @dagger.hilt.android.AndroidEntryPoint class *

# 5. General Hardening
# Remove log statements from the production build
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# Prevent R8 from keeping original file names and line numbers
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
