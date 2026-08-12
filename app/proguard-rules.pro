# -------------------------------------------------------------------------
# ArenaLive - Reverse Engineering Hardening Rules (STEP 1)
# -------------------------------------------------------------------------

# 1. Aggressive Obfuscation & Optimization
# Flatten the package hierarchy into a single root package
-repackageclasses ''
# Allow R8 to change access modifiers for better inlining/merging
-allowaccessmodification
# Overload method names aggressively (multiple methods share same name if signatures differ)
-overloadaggressively
# Perform up to 5 optimization passes
-optimizationpasses 5

# 2. ObjectBox Database Protection
# Do not obfuscate or strip ObjectBox internal management classes
-keep class io.objectbox.** { *; }
-keep interface io.objectbox.** { *; }
-keep public class com.eliteprime.app.data.local.entity.MyObjectBox { *; }
-keep public class com.eliteprime.app.data.local.entity.Internal** { *; }

# Protect Entity classes (ObjectBox relies on their names/fields via reflection)
-keep @io.objectbox.annotation.Entity class * { *; }
-keepclassmembers class * {
    @io.objectbox.annotation.Id long id;
    @io.objectbox.annotation.Index *;
}
-keep class **Cursor { *; }
-keep class **_ { *; }

# 3. Serialization & Network (Gson / Retrofit)
# We must keep field names for anything parsed from JSON via reflection
-keepclassmembers class com.eliteprime.app.data.local.entity.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keepclassmembers class com.eliteprime.app.data.remote.** {
    <fields>;
}

# Keep Retrofit interface methods and parameters
-keep @retrofit2.http.** interface * { *; }

# 4. Hilt / Dependency Injection
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
