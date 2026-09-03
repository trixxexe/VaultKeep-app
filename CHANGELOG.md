# Changelog

All notable changes to **VaultKeep** will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-09-03

### Added
- **Zero-Knowledge Encrypted Vault**: Master password derivation using PBKDF2-HMAC-SHA256 (310,000 rounds) and AES-256-GCM authenticated encryption.
- **Hardware-Backed Biometric Security**: Android Keystore integration for hardware-bound biometric key wrapping and unwrapping with biometric enrollment invalidation recovery.
- **Passkeys & FIDO2 / WebAuthn**: Android 14+ Credential Manager provider service generating NIST P-256 (secp256r1) ECDSA keys and COSE format public key export.
- **Autofill Framework Integration**: Android system autofill service with authentication gate and multi-field credential filling.
- **RFC 6238 TOTP Authenticator**: Time-based one-time password generation with CameraX QR code scanner and support for custom step periods and algorithms.
- **Offline Breach Inspection**: Zero-network local breach scanning against over 709,000 real breach records.
- **Security Audit & Health Score**: Real-time evaluation of password entropy (Shannon/NIST bits), reuse detection across domains, and compromised credentials.
- **Emergency Recovery Kit**: Printable cold-storage recovery sheet containing cryptographic parameters, salt hex, and QR code payload without exposing master password plaintext.
- **Atomic Encrypted Backup & Import**: Versioned `.vkeep` / `.vk` container export and import with conflict-aware merging.
- **WebDAV & SAF Synchronization**: Secure, zero-knowledge synchronizer pushing/pulling encrypted vault blobs with conflict resolution.
- **App Shortcuts, Quick Settings Tile & Widget**: Lock Vault QS tile, app shortcuts, and home screen widget.
- **First-Launch Welcome Sequence**: Privacy-first onboarding introducing security guarantees with reduced-motion accessibility support.

### Security & Hardening
- Release builds enforce `FLAG_SECURE` (`preventScreenCapture`) by default to prevent screen capture and recents overview leaks.
- Developer diagnostic crash screens and debug logs are strictly gated to debug build variants.
- Minification and resource shrinking enabled via R8 with strict ProGuard keep rules for crypto, biometric, and serialization layers.
- Production signing configurations decoupled from codebase, powered securely by CI/CD environment secrets.
