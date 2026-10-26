# Pleos Connect SDKs talk to system services through AIDL and reflection; keep them whole.
-keep class ai.pleos.playground.** { *; }
-keep interface ai.pleos.playground.** { *; }
-dontwarn ai.pleos.playground.**

# The Vehicle SDK reads Kotlin metadata at runtime (kotlin-reflect).
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations

# navi-data uses kotlinx.serialization.
-keepclassmembers class ai.pleos.playground.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# android.car is provided by the head unit at runtime (compileOnly).
-dontwarn android.car.**
