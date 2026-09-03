# Contributing to VaultKeep

Thank you for your interest in improving VaultKeep! VaultKeep is an offline, security-focused password manager and passkey authenticator.

## Security Invariants (MANDATORY)

Before submitting any code changes, ensure your modifications adhere strictly to these invariants:

1. **Zero Network Traffic**: Under no circumstances may `android.permission.INTERNET` be added to `AndroidManifest.xml`. VaultKeep must remain completely offline.
2. **Zero-Knowledge Guarantee**: Master passwords and derived keys must never touch persistent storage in plaintext. Memory must be explicitly wiped (`fill(0.toByte())`) where possible.
3. **No Unauthenticated Crypto**: All encrypted payloads must use authenticated encryption (AES-256-GCM) or verify MAC signatures before processing.
4. **Biometric Security**: Biometric unlock must wrap the vault master key inside hardware-backed KeyStore ciphers. If biometric enrollment changes (`KeyPermanentlyInvalidatedException`), the key must be wiped and password re-entry enforced.
5. **No Telemetry / Third-Party Analytics**: No analytics SDKs or remote logging frameworks are allowed.

## Development Workflow

1. Fork and clone the repository.
2. Create a feature branch: `git checkout -b feature/my-feature`.
3. Ensure the project builds cleanly:
   ```bash
   gradle :app:assembleDebug
   ```
4. Run all unit and Robolectric tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```
5. Open a Pull Request with a clear description of the problem solved.

## Code Style & Architecture
- **Language**: Kotlin 2.0+ exclusively.
- **UI**: 100% Jetpack Compose with Material 3 styling.
- **Architecture**: MVVM with Clean Architecture separation (`data`, `crypto`, `biometrics`, `passkey`, `ui`).
- **Testability**: Include unit / Robolectric tests for any new business logic or cryptographic routines.
