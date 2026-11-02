# Proguard / R8 configuration for CoolDown
# Generates mapping.txt for Google Play Console without breaking OSMDroid, AdMob, Billing or Kotlinx Serialization

-dontoptimize
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Kotlin & Kotlinx Serialization
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class **$Companion {
    public kotlinx.serialization.KSerializer serializer();
}
-dontwarn kotlinx.serialization.**

# Osmdroid (Map rendering and tile providers)
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Google Play Services Ads / Mobile Ads & Billing
-keep class com.google.android.gms.** { *; }
-keep class com.google.ads.** { *; }
-keep class com.android.billingclient.** { *; }
-dontwarn com.google.android.gms.**
-dontwarn com.android.billingclient.**

# AndroidX & Compose runtime safety
-keep class androidx.** { *; }

# App internal classes
-keep class com.findcoolplaceswithcooldown.** { *; }
