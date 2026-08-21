# MyMoneyKeep ProGuard / R8 Obfuscation & Keep Rules

# ── Attributes ──────────────────────────────────────────────────────────────
# Preserve line numbers, source file names, signatures, and ALL annotations
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-renamesourcefileattribute SourceFile

# ── Kotlin Metadata (必須保留，否則 Coroutines / Kotlin Reflect 執行時崩潰) ──
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @kotlin.Metadata *;
}
-dontwarn kotlin.**

# ── Android Core & Jetpack Compose ──────────────────────────────────────────
-keep class androidx.compose.** { *; }
-keepclassmembers class * extends androidx.compose.ui.Modifier { *; }

# ── Room Database (含 KSP 生成的 _Impl 類別) ────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * implements androidx.room.RoomDatabase { *; }
# 明確保留 KSP/KAPT 生成的 _Impl 類別，防止 R8 裁剪導致啟動閃退
-keep class **_Impl { *; }
-keep class **_Impl$* { *; }
-dontwarn androidx.room.**

# ── Moshi & Retrofit ────────────────────────────────────────────────────────
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
    @com.squareup.moshi.JsonQualifier *;
}
# 保留 Moshi Codegen (KSP) 生成的 JsonAdapter，防止 R8 裁剪
-keep class **JsonAdapter { *; }
-keep class **JsonAdapter$* { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}
-dontwarn retrofit2.**
-dontwarn com.squareup.moshi.**

# ── Kotlin Coroutines ────────────────────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── Firebase & Crashlytics ──────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ── App 自有類別 (com.example 套件) & ViewModel ──────────────────────────────
-keep class com.example.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}

# ── OkHttp 可選 TLS 提供者 (R8 自動偵測，加入 dontwarn 即可) ─────────────────
# 這些是 OkHttp 在不同平台上的可選 TLS 實作，Android 不需要，忽略即可。
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# ── Google Play Services Auth & Google Drive API ──────────────────────────────
-keep class com.google.android.gms.auth.api.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.services.sheets.** { *; }
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-dontwarn com.google.api.client.**
-dontwarn com.google.common.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**
-dontwarn javax.annotation.**

# ── Google Mobile Ads (AdMob) ───────────────────────────────────────────────
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

