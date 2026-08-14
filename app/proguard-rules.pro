-keep class com.openclaw.android.service.GatewayService { *; }
-keep class com.openclaw.android.model.** { *; }

# ---- R8 keep rules ----

# BouncyCastle：无自带 consumer 规则，且大量通过 ASN.1/反射工作
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# kotlinx.serialization：保留序列化器与伴随对象，防止 R8 移除
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.openclaw.android.**$$serializer { *; }
-keepclassmembers class com.openclaw.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.openclaw.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
