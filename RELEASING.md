# Releasing VaultKeep & F-Droid Distribution

This guide documents the release process, reproducible build instructions, F-Droid metadata compatibility, and CI/CD automation for VaultKeep.

---

## 1. Versioning & Source of Truth

VaultKeep versions are centrally managed in `gradle.properties`:
- `app.versionCode`: Integer, monotonically incremented for every release build.
- `app.versionName`: SemVer string (`MAJOR.MINOR.PATCH`, e.g., `1.0.0`).

Both `versionCode` and `versionName` in `app/build.gradle.kts` automatically consume these properties.

---

## 2. GitHub Actions Automation

VaultKeep maintains two automated CI/CD pipelines in `.github/workflows/`:

### A. `build-and-test.yml` (Continuous Integration)
- **Triggers**: On every push and pull request to `main` or `master`.
- **Environment**: Ubuntu Latest, Temurin JDK 21, pinned AGP 9.1.1, Kotlin 2.2.10.
- **Tasks**:
  1. Checks out source code.
  2. Sets up JDK 21 (Temurin) with Gradle dependency caching.
  3. Executes full unit and Robolectric test suites (`:app:testDebugUnitTest`). Hard fails if any test fails.
  4. Builds debug APK (`:app:assembleDebug`) and uploads artifact.

### B. `release.yml` (Automated Tag Release)
- **Triggers**: On tag push matching `v*.*.*` (e.g. `v1.0.0`).
- **Required GitHub Encrypted Secrets**:
  - `KEYSTORE_BASE64`: Base64-encoded Java KeyStore (`.jks` or `.keystore`).
  - `KEYSTORE_PASSWORD`: Password for the keystore.
  - `KEY_ALIAS`: Key alias within the keystore.
  - `KEY_PASSWORD`: Password for the specific key.
- **Strict Failure-Mode Safety**:
  - If any of the signing secrets are missing or blank, the workflow **fails loudly and immediately**.
  - It will **never** silently fall back to debug signing or produce an unsigned release artifact labeled as a release.
- **Artifacts & Checksums**:
  - Compiles minified, resource-shrunk Release APK (`:app:assembleRelease`) and Android App Bundle (`:app:bundleRelease`).
  - Signs binaries using `apksigner` / jarsigner.
  - Computes cryptographic SHA-256 checksums (`SHA256SUMS.txt`).
  - Automatically extracts release notes for the corresponding version from `CHANGELOG.md`.
  - Publishes a formal GitHub Release attaching the signed APK, signed AAB, and `SHA256SUMS.txt`.

---

## 3. Reproducible Build Instructions

VaultKeep is designed to provide verifiable, reproducible builds. Anyone can build the release binary locally using the exact pinned toolchain:

### Pinned Toolchain Environment
- **Operating System**: Linux (Ubuntu 22.04 LTS or containerized Linux x86_64)
- **JDK**: Eclipse Temurin OpenJDK 21 (build 21.0.12+8-LTS or equivalent JDK 21)
- **Gradle**: 9.3.1
- **Android Gradle Plugin (AGP)**: 9.1.1
- **Kotlin**: 2.2.10
- **Android SDK Platform**: API 36 (targetSdk 35, minSdk 24)

### Reproducing the Unsigned APK Locally:
```bash
# 1. Clone repository
git clone https://github.com/your-org/vaultkeep.git
cd vaultkeep
git checkout v1.0.0

# 2. Build unsigned release APK
gradle :app:packageReleaseUnsigned --no-daemon

# 3. Inspect / calculate SHA-256
sha256sum app/build/outputs/apk/release/*.apk
```

### Known Variances in Build Reproducibility:
- **Zip / APK Timestamps**: Android Gradle Plugin packaging inserts creation timestamps into ZIP archive headers unless `android.packagingOptions.resources.excludes` or reproducible-archive plugins are forced.
- **R8 Mapping Salt / Line Map**: Minified DEX bytecode is deterministic when compiled under identical JDK and Gradle versions, but the APK signature block will vary depending on the signer's certificate. Verify unsigned APK checksums when comparing across third-party build environments.

---

## 4. F-Droid Build Compatibility

VaultKeep is designed for zero-friction packaging by F-Droid's official build server (`fdroiddata`).

### Verification Checklist:
- **Zero Proprietary SDKs**: VaultKeep has no Google Play Services dependencies (Google Play Services Location, Google Sign-In, Firebase, Crashlytics, or AdMob are intentionally absent or commented out).
- **Offline Build Capability**: Gradle dependencies are standard Maven Central / Google dependencies.
- **Network Isolation**: The application core requires no network access to function; the build does not fetch non-maven binary blobs during compile time.
- **Fastlane Metadata**: Store metadata, app description, and icons are organized under `fastlane/metadata/android/en-US/` ready for F-Droid scraping.

### F-Droid Recipe Spec (`metadata/com.aistudio.vaultkeep.secure.yml`):
```yaml
Categories:
  - Security
License: GPL-3.0-or-later
WebSite: https://github.com/your-org/vaultkeep
SourceCode: https://github.com/your-org/vaultkeep
IssueTracker: https://github.com/your-org/vaultkeep/issues

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags

Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: v1.0.0
    subdir: app
    gradle:
      - yes
    prebuild: sed -i -e '/googleServices/d' ../build.gradle.kts || true
```
