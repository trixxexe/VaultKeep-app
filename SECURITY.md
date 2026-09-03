# Security Policy & Cryptographic Architecture

VaultKeep is designed from the ground up as a **zero-knowledge, offline-first, self-custodial** credential management system. This document outlines the cryptographic specifications, key management lifecycle, threat assumptions, and security guidelines implemented in the application.

---

## 1. Cryptographic Specifications

| Component | Primitive / Standard | Implementation Details |
| :--- | :--- | :--- |
| **Symmetric Encryption** | AES-256-GCM (`AES/GCM/NoPadding`) | 256-bit derived keys, 96-bit (12-byte) cryptographically secure random nonces (`SecureRandom`), and 128-bit authentication tags. |
| **Key Derivation Function (KDF)** | PBKDF2 with HMAC-SHA256 (`PBKDF2WithHmacSHA256`) | 310,000 rounds (OWASP recommended minimum for PBKDF2), 128-bit (16-byte) per-vault salt generated via `SecureRandom`. |
| **Hardware Key Protection** | Android Keystore TEE / StrongBox | Hardware-bound AES-256-GCM biometric wrapping key (`KeyProperties.PURPOSE_ENCRYPT or PURPOSE_DECRYPT`) with `setUserAuthenticationRequired(true)`. |
| **File Format Specification** | VaultKeep Binary Container (v2) | 4-byte magic (`VKV2`), 4-byte format version, 4-byte iteration count, 16-byte salt, 12-byte IV, followed by AES-GCM ciphertext + 16-byte authentication tag. |
| **Atomic Durability** | Atomic Write & Fallback Replication | Staged write to `.tmp` -> `fsync()` descriptor sync -> active backup `.bak` rotation -> atomic rename to `.enc`. |

---

## 2. In-Memory Hygiene & Session Lifecycle

### Volatile Key Handling
- When the master password is provided, a 256-bit symmetric key (`SecretKeySpec`) is derived in memory.
- Decrypted vault data exists solely in transient JVM memory (`VaultPayload`).
- When the user locks the vault or the app background/inactivity timer expires:
  1. Active symmetric keys are discarded and garbage-collected.
  2. Temporary character arrays (`CharArray`) and raw byte arrays (`ByteArray`) are zeroed using `Arrays.fill(..., '\u0000')`.
  3. `activePayload` state is reset to `null`.

### Memory Zeroing Caveat (JVM Limitations)
> **Notice**: In managed runtimes such as the Android JVM (ART), `String` objects are immutable and interned, and garbage collector compaction cycles may copy memory buffers before explicit overwriting. True, guaranteed hardware zero-fill requires native C/Rust code (`mlock` / `memset_s`). While VaultKeep employs `CharArray` clearing where possible, users should be aware that cold-boot physical memory extraction on compromised runtime environments is an inherent limitation of managed mobile runtimes.

---

## 3. Threat Model Summary

| Vector | Status | Mitigation Strategy |
| :--- | :--- | :--- |
| **Network Sniffing & MITM** | **Immune** | VaultKeep requests **ZERO** network permissions in `AndroidManifest.xml`. No data ever leaves the device. |
| **Ciphertext Tampering / Bit-Flips** | **Protected** | AES-256-GCM provides authenticated encryption (AEAD). Any alteration fails tag validation (`AEADBadTagException`). |
| **Disk Inspection & Stolen Files** | **Protected** | On-disk vaults and exported `.vkeep` backups are encrypted with 310,000-round PBKDF2 + AES-256-GCM. |
| **Rooted Device / Kernel Compromise** | **Out of Scope** | If the host OS is rooted, malicious root processes can hook zygote memory, ptrace processes, or log keystrokes. |
| **Forgotten Master Password** | **Unrecoverable by Design** | No backdoors, master reset keys, or developer overrides exist. Encrypted backup files require the original password. |

---

## 4. Backup & Disaster Recovery Architecture

1. **Self-Custody**: Data safety is strictly the user's responsibility. 
2. **Pre-Flight Verification**: During backup export (`.vkeep`), VaultKeep test-decrypts the generated stream in-memory before acknowledging success to the user.
3. **Recovery Path**: The sole method to restore a corrupted or lost vault is importing an exported `.vkeep` file with the associated master password.

---

## 5. Responsible Disclosure

If you discover a potential cryptographic vulnerability or security defect in VaultKeep:
- **Do not open a public GitHub issue.**
- Email the security team at `security@vaultkeep.local` with detailed reproduction steps and affected versions.
- We will acknowledge receipt within 48 hours and work with you to release an audited patch.
