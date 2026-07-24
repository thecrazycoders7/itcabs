# ITCABS release keep rules. R8 full-mode is on; these keep the reflection/codegen surfaces.

# --- kotlinx.serialization ---
# Keep generated serializers and @Serializable classes' companion serializer accessors.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.itcabs.**$$serializer { *; }
-keepclassmembers class com.itcabs.** {
    *** Companion;
}
-keepclasseswithmembers class com.itcabs.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep all DTOs and domain models (serialized over the wire).
-keep class com.itcabs.core.network.dto.** { *; }
-keep class com.itcabs.domain.model.** { *; }

# --- Retrofit / OkHttp ---
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# --- Hilt / Dagger generate their own keep rules; nothing extra needed. ---

# --- Firebase (auth/messaging) ships consumer rules; silence optional-dep warnings. ---
-dontwarn com.google.firebase.**

# --- Google Maps / Places / Location ship consumer rules; silence an internal R8 note. ---
-dontwarn com.google.android.gms.internal.**
