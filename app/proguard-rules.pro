# Ghost generated serializers & benchmark models
-keep class com.ghost.serialization.generated.** { *; }
-keep @com.ghost.serialization.annotations.GhostSerialization class * { *; }
-keep class com.ghost.android.test.domain.** { *; }

# Ghost Ktor adapter (compiled against Ktor 2.3)
-keep class com.ghost.serialization.ktor.** { *; }
-keepclassmembers class * implements io.ktor.serialization.ContentConverter {
    public <methods>;
}

# Ktor / SLF4J
-dontwarn org.slf4j.**
-dontwarn io.ktor.**

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.internal.platform.**

# Ktorfit generated APIs
-keep interface com.ghost.android.test.data.** { *; }
-keep class com.ghost.android.test.data.**$* { *; }
