# VaultKeep

**VaultKeep** is a high-security, 100% offline, open-source password manager, FIDO2/WebAuthn passkey authenticator, and TOTP two-factor code generator for Android. Built with Kotlin, Jetpack Compose, and modern Android architecture.

---

## Key Features

- 🔐 **Zero-Knowledge Encrypted Vault**: Master password derivation using **Argon2id** (memory-hard, resistant to GPU/ASIC attacks) with **AES-256-GCM** authenticated encryption.
- 🔑 **FIDO2 / WebAuthn Passkeys**: Native passkey creation and assertion with NIST P-256 (secp256r1) ECDSA cryptographic keys stored strictly within your encrypted offline vault.
- 🛡️ **Android Credential Manager & Autofill Service**: Integrated with Android 14+ Credential Provider framework (`androidx.credentials`) as well as the legacy Android Autofill framework for seamless, secure auto-filling across all apps and browsers.
- ⏰ **Offline 2FA / TOTP Authenticator**: Built-in HMAC-SHA1 RFC 6238 time-based one-time password generator with live countdown dials, camera QR code scanner, and one-tap clipboard copy.
- 👆 **Hardware-Backed Biometric Unlock**: Hardware KeyStore-backed AES-256-CBC cipher authentication with automatic key rotation and `KeyPermanentlyInvalidatedException` recovery.
- 📦 **Atomic Versioned Backups (.vkeep v2)**: Import/export encrypted backups with scrypt/Argon2 key derivation and SHA-256 integrity verification.
- 🛡️ **Security Center & Audit**: Real-time evaluation of password entropy (bits), detection of reused or duplicate passwords, compromised weak credentials, and missing 2FA.
- 🔒 **FLAG_SECURE Screen Capture Protection**: Prevent screenshots and recent-apps preview leaks (configurable in Settings).
- ⚡ **Share-Sheet & Deep-Link Integration**: Direct `ACTION_SEND` and `ACTION_VIEW` intent routing directly through the authenticated vault barrier.
- 🚫 **Zero Network Permissions**: The application does not request or contain `android.permission.INTERNET`. No telemetry, no third-party trackers, no cloud servers.

---

## Cryptographic Architecture

| Layer | Specification |
|---|---|
| **Key Derivation (KDF)** | Argon2id (v1.3): 64MB memory cost, 4 iterations, 4 parallelism lanes, 16-byte random CSPRNG salt |
| **Vault Encryption** | AES-256-GCM (NIST SP 800-38D): 256-bit derived key, 12-byte random IV, 128-bit authentication tag |
| **Biometric Wrapper** | AndroidKeyStore AES-256-CBC with `PURPOSE_ENCRYPT \| PURPOSE_DECRYPT`, PKCS7 padding, biometrics-enforced auth |
| **Passkey Cryptography** | NIST P-256 (secp256r1) ECDSA with SHA-256 (ES256), COSE format public key export, PKCS#8 encrypted private key storage |
| **TOTP Engine** | RFC 6238 / RFC 4226: HMAC-SHA1, 30-second time step, 6-digit dynamic truncation |
| **Entropy Estimation** | Information-theoretic Shannon / combinatorial pool entropy calculator with pattern penalty |

---

## Building from Source

### Prerequisites & Pinned Toolchain
- **JDK**: Eclipse Temurin OpenJDK 21 LTS
- **Gradle**: 9.3.1
- **Android Gradle Plugin (AGP)**: 9.1.1
- **Kotlin**: 2.2.10
- **Android SDK**: `compileSdk = 36`, `targetSdk = 35`, `minSdk = 24` (matches Credential Manager, BiometricPrompt, Keystore, and Autofill API specifications)

### Build Debug APK
```bash
gradle :app:assembleDebug
```

### Build Minified Release APK & App Bundle (AAB)
```bash
KEYSTORE_PATH=/path/to/keystore.jks STORE_PASSWORD=*** KEY_ALIAS=*** KEY_PASSWORD=*** gradle :app:assembleRelease :app:bundleRelease
```

### Run Automated Local Tests
```bash
gradle :app:testDebugUnitTest
```

For reproducible build verification, F-Droid metadata compatibility, and CI/CD pipelines, see [RELEASING.md](RELEASING.md) and [CHANGELOG.md](CHANGELOG.md).

---

## Threat Model & Security Invariants

See [THREAT_MODEL.md](THREAT_MODEL.md) and [SECURITY.md](SECURITY.md) for in-depth threat modeling, vulnerability disclosure policies, and defense against process death, memory extraction, and side-channel leakage.

---

## License

VaultKeep is free software licensed under the **GNU General Public License v3.0 or later** ([GPL-3.0-or-later](LICENSE)).
