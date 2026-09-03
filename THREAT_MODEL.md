# VaultKeep Threat Model

This document outlines the security boundaries, assets, threat actors, attack vectors, and design assumptions for VaultKeep.

---

## 1. System Overview & Trust Assumptions

VaultKeep is an offline, single-user password vault for Android.

### Primary Assets:
- **Master Password**: The secret passphrase known only to the vault owner.
- **Derived Vault Key**: The 256-bit AES symmetric key derived from the master password.
- **Stored Credentials**: Usernames, passwords, notes, URLs, TOTP seeds, and custom fields.
- **Biometric Wrapping Key**: Hardware Keystore-protected key that wraps the derived vault key.

---

## 2. In-Scope Threats & Mitigations

### 2.1 Stolen Device / Physical Storage Extraction
- **Scenario**: An attacker extracts the device's flash storage or acquires an exported `.vkeep` backup file.
- **Defense**: All persistent data is encrypted with AES-256-GCM using keys derived via PBKDF2-HMAC-SHA256 (310,000 iterations). Offline brute-force search is computationally expensive.

### 2.2 Offline Tampering & Bit-Flipping
- **Scenario**: An adversary modifies bytes inside the stored vault container on disk.
- **Defense**: AES-GCM includes a 128-bit authentication tag calculated over the entire ciphertext. Any single-bit corruption causes immediate decryption failure (`AEADBadTagException`), preventing ciphertext malleability or chosen-ciphertext attacks.

### 2.3 Side-Channel & Screenshot Capture
- **Scenario**: Background malware or the Android task switcher captures screenshots of plaintext credentials.
- **Defense**: `WindowManager.LayoutParams.FLAG_SECURE` is active on all vault activities, preventing screenshots, screen recordings, and recent-app thumbnail caching.

### 2.4 Biometric Bypass & Brute-Forcing
- **Scenario**: Unauthorized individual attempts multiple fingerprint/face unlock tries.
- **Defense**: VaultKeep enforces a strict 5-attempt limit (`MAX_FAILED_BIOMETRIC_ATTEMPTS`). Exceeding this invalidates the cached biometric configuration and forces master password authentication.

### 2.5 Stale Memory & Auto-Lock
- **Scenario**: User leaves their device unlocked and unattended.
- **Defense**: Configurable auto-lock triggers on background transitions or user inactivity (default: 60 seconds). Clipboard contents copied from the vault are purged automatically (default: 30 seconds).

---

## 3. Out-of-Scope Threats & Honest Limitations

### 3.1 Compromised / Rooted Operating System
If the host Android system is rooted or infected by rootkit-level spyware:
- An attacker with root privileges can attach debuggers (`ptrace`), inspect `/proc/$PID/mem`, or hook ART runtime methods.
- Hardware keyloggers or compromised custom keyboards can log keystrokes prior to application receipt.
- *Mitigation*: Users are advised against running critical password vaults on rooted or unpatched OS installations.

### 3.2 Advanced Cold Boot Memory Extraction
- In managed runtimes (Android ART / JVM), garbage collection algorithms manage object allocation. String immutability and memory compaction prevent 100% cryptographic erasure guarantees without low-level native allocations.

### 3.3 Loss of Master Password
- By deliberate cryptographic design, there are **NO** backdoors, secret master recovery keys, or remote recovery channels. Loss of the master password and unbacked data results in permanent, irrecoverable data loss.
