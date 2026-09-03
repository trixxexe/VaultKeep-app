package com.example.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * VaultKeep Cryptographic Engine
 *
 * Security Architecture & Auditable Flow:
 * 1. KEY DERIVATION:
 *    - The user's master password (held strictly as CharArray) is passed into PBKDF2-HMAC-SHA256
 *      with >= 310,000 iterations and a cryptographically secure 16-byte random salt.
 *    - This generates a high-entropy 256-bit AES symmetric key in volatile memory only.
 *    - The password CharArray and raw key byte representations are actively zeroed (wiped)
 *      from memory once no longer required.
 *
 * 2. VAULT ENCRYPTION & INTEGRITY:
 *    - Vault payloads are serialized to UTF-8 bytes and encrypted using AES-256-GCM (Authenticated
 *      Encryption with Associated Data - AEAD).
 *    - A fresh, non-repeating 12-byte IV (nonce) is generated using SecureRandom for EVERY write.
 *    - The 128-bit GCM authentication tag guarantees tamper detection; decryption immediately
 *      fails with AEADBadTagException if any byte is corrupted or if the password is incorrect.
 *
 * 3. HARDWARE-BACKED BIOMETRIC KEYSTORE WRAPPING:
 *    - Android Keystore generates an AES-256-GCM master wrapping key marked non-exportable and
 *      protected by Android's hardware security module (TEE / StrongBox).
 *    - When Biometric Unlock is enabled, the derived vault key is wrapped (encrypted) by this
 *      hardware key via a BiometricPrompt.CryptoObject session.
 *    - During biometric unlock, biometric approval authorizes the hardware Keystore Cipher to unwrap
 *      the vault key in memory for the active session.
 *    - If biometrics fail or are disabled, the app cleanly falls back to master-password derivation
 *      with ZERO plaintext persistence.
 */
object CryptoManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val BIOMETRIC_KEY_ALIAS = "VaultKeep_Biometric_MasterKey_v1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"

    const val PBKDF2_ITERATIONS = 310_000
    const val KEY_LENGTH_BITS = 256
    const val SALT_LENGTH_BYTES = 16
    const val GCM_IV_LENGTH_BYTES = 12
    const val GCM_TAG_LENGTH_BITS = 128

    const val CURRENT_FORMAT_VERSION = 2
    const val CURRENT_CRYPTO_VERSION = 1

    // Magic header bytes
    val VAULT_MAGIC_V1 = byteArrayOf(0x56, 0x4B, 0x56, 0x31) // 'V', 'K', 'V', '1'
    val VAULT_MAGIC_V2 = byteArrayOf(0x56, 0x4B, 0x56, 0x32) // 'V', 'K', 'V', '2'

    private val secureRandom = SecureRandom()

    // ---------------------------------------------------------------------------------------------
    // Memory Hygiene Helpers
    // ---------------------------------------------------------------------------------------------

    fun wipe(chars: CharArray?) {
        if (chars != null) {
            Arrays.fill(chars, '\u0000')
        }
    }

    fun wipe(bytes: ByteArray?) {
        if (bytes != null) {
            Arrays.fill(bytes, 0.toByte())
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Key Derivation (PBKDF2-HMAC-SHA256)
    // ---------------------------------------------------------------------------------------------

    /**
     * Generates a fresh cryptographically secure random salt for vault creation/re-keying.
     */
    fun generateSalt(length: Int = SALT_LENGTH_BYTES): ByteArray {
        val salt = ByteArray(length)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Generates a fresh cryptographically secure random 12-byte IV for AES-GCM.
     */
    fun generateIv(length: Int = GCM_IV_LENGTH_BYTES): ByteArray {
        val iv = ByteArray(length)
        secureRandom.nextBytes(iv)
        return iv
    }

    /**
     * Derives a 256-bit AES SecretKey from the given master password CharArray and salt.
     * Memory hygiene: wipes the temporary raw derived bytes once wrapped in SecretKeySpec.
     */
    fun deriveVaultKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS,
        algorithm: String = PBKDF2_ALGORITHM
    ): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(algorithm)
        val keyBytes = factory.generateSecret(spec).encoded
        try {
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
            wipe(keyBytes)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Vault Payload Encryption & Decryption (Versioned AES-256-GCM Container)
    // ---------------------------------------------------------------------------------------------

    /**
     * Encrypts plaintext vault data using the v2 versioned container format with AES-256-GCM.
     *
     * Format specification:
     * [4 bytes MAGIC_HEADER "VKV2"]
     * [4 bytes formatVersion (2)]
     * [4 bytes cryptoVersion (1)]
     * [UTF string kdfAlgorithm e.g. "PBKDF2WithHmacSHA256"]
     * [4 bytes iterations (310,000)]
     * [1 byte salt_length] [salt_bytes]
     * [1 byte iv_length] [iv_bytes]
     * [4 bytes ciphertext_length] [ciphertext_bytes + 16 bytes GCM tag]
     */
    fun encryptVault(
        plainData: ByteArray,
        vaultKey: SecretKey,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS,
        kdfAlgorithm: String = PBKDF2_ALGORITHM
    ): ByteArray {
        val iv = generateIv()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, vaultKey, gcmSpec)

        val ciphertext = cipher.doFinal(plainData)

        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // Write Header
        dos.write(VAULT_MAGIC_V2)
        dos.writeInt(CURRENT_FORMAT_VERSION)
        dos.writeInt(CURRENT_CRYPTO_VERSION)
        dos.writeUTF(kdfAlgorithm)
        dos.writeInt(iterations)
        dos.writeByte(salt.size)
        dos.write(salt)
        dos.writeByte(iv.size)
        dos.write(iv)
        dos.writeInt(ciphertext.size)
        dos.write(ciphertext)
        dos.flush()

        return baos.toByteArray()
    }

    /**
     * Decrypts and authenticates vault data with AES-256-GCM, seamlessly supporting v2 and v1 containers.
     * Throws an exception if header is invalid or if authentication tag fails (tampered/wrong key).
     */
    fun decryptVault(encryptedBytes: ByteArray, vaultKey: SecretKey): ByteArray {
        val bais = ByteArrayInputStream(encryptedBytes)
        val dis = DataInputStream(bais)

        val header = ByteArray(4)
        dis.readFully(header)

        return when {
            header.contentEquals(VAULT_MAGIC_V2) -> {
                val formatVersion = dis.readInt()
                val cryptoVersion = dis.readInt()
                val kdfAlgorithm = dis.readUTF()
                val iterations = dis.readInt()

                val saltLength = dis.readByte().toInt() and 0xFF
                val salt = ByteArray(saltLength)
                dis.readFully(salt)

                val ivLength = dis.readByte().toInt() and 0xFF
                val iv = ByteArray(ivLength)
                dis.readFully(iv)

                val ciphertextLength = dis.readInt()
                val ciphertext = ByteArray(ciphertextLength)
                dis.readFully(ciphertext)

                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.DECRYPT_MODE, vaultKey, gcmSpec)

                cipher.doFinal(ciphertext)
            }
            header.contentEquals(VAULT_MAGIC_V1) -> {
                // Backwards-compatible legacy v1 format
                val saltLength = dis.readByte().toInt() and 0xFF
                val salt = ByteArray(saltLength)
                dis.readFully(salt)

                val iterations = dis.readInt()

                val ivLength = dis.readByte().toInt() and 0xFF
                val iv = ByteArray(ivLength)
                dis.readFully(iv)

                val ciphertextLength = dis.readInt()
                val ciphertext = ByteArray(ciphertextLength)
                dis.readFully(ciphertext)

                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.DECRYPT_MODE, vaultKey, gcmSpec)

                cipher.doFinal(ciphertext)
            }
            else -> {
                throw IllegalArgumentException("Invalid vault file format: magic header mismatch")
            }
        }
    }

    /**
     * Extracts header parameters (format version, kdf algorithm, salt, iterations) from an encrypted file.
     */
    fun extractHeaderInfo(encryptedBytes: ByteArray): VaultHeaderInfo {
        val bais = ByteArrayInputStream(encryptedBytes)
        val dis = DataInputStream(bais)

        val header = ByteArray(4)
        dis.readFully(header)

        return when {
            header.contentEquals(VAULT_MAGIC_V2) -> {
                val formatVersion = dis.readInt()
                val cryptoVersion = dis.readInt()
                val kdfAlgorithm = dis.readUTF()
                val iterations = dis.readInt()

                val saltLength = dis.readByte().toInt() and 0xFF
                val salt = ByteArray(saltLength)
                dis.readFully(salt)

                val ivLength = dis.readByte().toInt() and 0xFF
                val iv = ByteArray(ivLength)
                dis.readFully(iv)

                VaultHeaderInfo(
                    formatVersion = formatVersion,
                    cryptoVersion = cryptoVersion,
                    kdfAlgorithm = kdfAlgorithm,
                    iterations = iterations,
                    salt = salt,
                    iv = iv
                )
            }
            header.contentEquals(VAULT_MAGIC_V1) -> {
                val saltLength = dis.readByte().toInt() and 0xFF
                val salt = ByteArray(saltLength)
                dis.readFully(salt)

                val iterations = dis.readInt()

                val ivLength = dis.readByte().toInt() and 0xFF
                val iv = ByteArray(ivLength)
                dis.readFully(iv)

                VaultHeaderInfo(
                    formatVersion = 1,
                    cryptoVersion = 1,
                    kdfAlgorithm = PBKDF2_ALGORITHM,
                    iterations = iterations,
                    salt = salt,
                    iv = iv
                )
            }
            else -> {
                throw IllegalArgumentException("Invalid vault file format: magic header mismatch")
            }
        }
    }

    /**
     * Extracts salt and iterations from an encrypted vault file header without decrypting payload.
     */
    fun extractSaltAndIterations(encryptedBytes: ByteArray): Pair<ByteArray, Int> {
        val info = extractHeaderInfo(encryptedBytes)
        return Pair(info.salt, info.iterations)
    }

    /**
     * Extracts salt from an encrypted vault file header.
     */
    fun extractSalt(encryptedBytes: ByteArray): ByteArray {
        return extractHeaderInfo(encryptedBytes).salt
    }

    // ---------------------------------------------------------------------------------------------
    // Android Keystore Hardware-Backed Key for Biometric Wrapping
    // ---------------------------------------------------------------------------------------------

    /**
     * Gets or creates the hardware-backed AES key in AndroidKeyStore.
     */
    private fun getOrCreateBiometricMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (!keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val builder = KeyGenParameterSpec.Builder(
                BIOMETRIC_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_LENGTH_BITS)
                .setUserAuthenticationRequired(true)
                .setRandomizedEncryptionRequired(true)

            keyGenerator.init(builder.build())
            return keyGenerator.generateKey()
        }

        return (keyStore.getEntry(BIOMETRIC_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    /**
     * Prepares an initialized Cipher for BiometricPrompt encryption (wrapping vault key).
     */
    fun getBiometricEncryptCipher(): Cipher {
        val key = getOrCreateBiometricMasterKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }

    /**
     * Prepares an initialized Cipher for BiometricPrompt decryption (unwrapping vault key).
     */
    fun getBiometricDecryptCipher(iv: ByteArray): Cipher {
        val key = getOrCreateBiometricMasterKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher
    }

    /**
     * Deletes the biometric wrapping key from Keystore if biometric is disabled or revoked.
     */
    fun deleteBiometricKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
            }
        } catch (_: Exception) {}
    }
}
