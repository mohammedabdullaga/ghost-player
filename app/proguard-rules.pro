# -------------------------------------------------------------------------
# ArenaLive - Minimal Proguard Rules (Non-interfering)
# -------------------------------------------------------------------------

# Disable shrinking, optimization, and obfuscation to prevent any interference
-dontshrink
-dontoptimize
-dontobfuscate

# Keep all project classes and attributes
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions, SourceFile, LineNumberTable
-keep class com.eliteprime.app.** { *; }
-keep interface com.eliteprime.app.** { *; }

# Keep all 3rd party components and libraries intact
-keep class com.google.firebase.** { *; }
-keep class io.objectbox.** { *; }
-keep class dagger.** { *; }
-keep class androidx.** { *; }
-keep class org.videolan.** { *; }
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class coil3.** { *; }

# Suppress all library warnings/notes
-dontnote **
-dontwarn **
