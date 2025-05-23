-dontobfuscate

# --- Retrofit2 ---
# Platform calls Class.forName on types which do not exist on Android to determine platform.
-dontnote retrofit2.Platform
# Platform used when running on Java 8 VMs. Will not be used at runtime.
-dontwarn retrofit2.Platform$Java8
# Retain generic type information for use by reflection by converters and adapters.
-keepattributes Signature
# Retain declared checked exceptions for use by a Proxy instance.
-keepattributes Exceptions
# --- /Retrofit ---

# --- OkHttp + Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
# --- /OkHttp + Okio ---

# --- Wikipedia ---
-keep class org.wikipedia.** { <init>(...); *; }
-keep enum org.wikipedia.** { <init>(...); *; }
# --- /Wikipedia ---

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt # core serialization annotations

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class org.wikipedia.**$$serializer { *; }
-keepclassmembers class org.wikipedia.** {
    *** Companion;
}
-keepclasseswithmembers class org.wikipedia.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Metrics Platform ---
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
-dontwarn java.beans.ConstructorProperties
-dontwarn lombok.Generated
-dontwarn lombok.NonNull
# --- /Metrics Platform ---
