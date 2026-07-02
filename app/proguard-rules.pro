# ─── ShadowLink VPN Pro - ProGuard Rules ───────────────────────

# Keep application class
-keep class com.shadowlink.vpn.ShadowLinkApp { *; }

# Keep all Activities, Fragments, Services, Receivers
-keep class com.shadowlink.vpn.activities.** { *; }
-keep class com.shadowlink.vpn.fragments.** { *; }
-keep class com.shadowlink.vpn.services.** { *; }
-keep class com.shadowlink.vpn.utils.BootReceiver { *; }

# Keep all data models (Gson needs them)
-keep class com.shadowlink.vpn.models.** { *; }
-keepclassmembers class com.shadowlink.vpn.models.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# JSch (SSH)
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# V2Ray / Libv2ray
-keep class com.v2ray.** { *; }
-keep class go.** { *; }
-keep class libv2ray.** { *; }
-dontwarn libv2ray.**

# Conscrypt
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# AdMob
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Lottie
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# Kotlin Coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# Kotlin Parcelize
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep VpnManager singleton state
-keep class com.shadowlink.vpn.vpn.VpnManager { *; }
-keep class com.shadowlink.vpn.vpn.V2RayConfigBuilder { *; }
-keep class com.shadowlink.vpn.network.ApiClient { *; }
-keep class com.shadowlink.vpn.utils.** { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
