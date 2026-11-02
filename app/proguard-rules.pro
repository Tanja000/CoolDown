# Proguard / R8 configuration for CoolDown

# Kotlinx Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
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

# Google Play Services Ads / Mobile Ads
-keep public class com.google.android.gms.ads.** {
   public *;
}
-keep public class com.google.ads.** {
   public *;
}
-dontwarn com.google.android.gms.ads.**

# App internal classes
-keep class com.cooldown.data.** { *; }
-keep class com.cooldown.ui.** { *; }
