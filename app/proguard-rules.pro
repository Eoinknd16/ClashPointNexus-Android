# Nexus: let R8 optimise and shrink the libraries, but keep our own code as-is
# (it's small, and this rules out reflection/serialization surprises).
-keep class com.eoinknd16.nexus.** { *; }
-keepclassmembers class com.eoinknd16.nexus.** { *; }

# Readable crash traces.
-dontobfuscate

# kotlinx.serialization (belt and braces on top of the library's own rules).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }

# OkHttp / Okio optional platform bits.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
