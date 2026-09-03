# VaultKeep ProGuard / R8 Optimization & Obfuscation Rules

# 1. Source files and line numbers for stack trace de-obfuscation
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 2. Kotlin Metadata & Coroutines
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**

# 3. Cryptographic Core, KDF, and Android Keystore
-keep class com.example.crypto.** { *; }
-keepclassmembers class com.example.crypto.** { *; }
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }
-dontwarn java.security.**

# 4. AndroidX Biometric & Hardware Authentication
-keep class androidx.biometric.** { *; }
-keepclassmembers class androidx.biometric.** { *; }

# 5. Moshi & Model Serialization (Backups, Export, Recovery Kit, Passkeys)
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
    @com.squareup.moshi.JsonClass *;
}
-keep class com.example.model.** { *; }
-keepclassmembers class com.example.model.** { *; }
-keep class com.example.data.** { *; }
-keepclassmembers class com.example.data.** { *; }
-keep class com.example.sync.** { *; }
-keepclassmembers class com.example.sync.** { *; }
-keep class com.example.passkey.** { *; }
-keepclassmembers class com.example.passkey.** { *; }

# 6. Room Database
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# 7. Android 14+ Credential Manager & Passkeys (WebAuthn / FIDO2)
-keep class androidx.credentials.** { *; }
-keepclassmembers class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.example.credentials.** { *; }

# 8. Android Autofill Service
-keep class com.example.autofill.** { *; }
-keepclassmembers class com.example.autofill.** { *; }

# 9. CameraX & ZXing (TOTP QR code scanning)
-keep class androidx.camera.** { *; }
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# 10. Services, App Widgets, and Quick Settings Tile
-keep class com.example.service.** { *; }
-keep class com.example.widget.** { *; }

# 11. OkHttp & Retrofit (WebDAV sync transport)
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# 12. Crash Diagnostics Logger & UI
-keep class com.example.CrashDiagnosticsLogger { *; }
-keep class com.example.CrashDisplayActivity { *; }

