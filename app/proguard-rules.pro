# The Claude SDK maps JSON with Jackson and Kotlin reflection: keep its model classes and their metadata whole.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,RuntimeVisible*Annotations*

# App state is saved with kotlinx.serialization; keep the classes and their generated serializers (nested in them).
-keep class my.sado.app.data.** { *; }

# Optional classes some libraries reference but do not need on Android
-dontwarn **
