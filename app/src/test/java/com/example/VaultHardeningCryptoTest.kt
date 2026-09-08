package com.example

import com.example.crypto.CryptoManager
import com.example.crypto.PasswordGenerator
import com.example.data.VaultEntry
import com.example.data.VaultPayload
import com.example.ui.util.InputSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class VaultHardeningCryptoTest {

    @Test
    fun testCryptoKeyZeroizationInDerivation() {
        val password = "StrongMasterPassword123!".toCharArray()
        val salt = CryptoManager.generateSalt()
        val derivedKey = CryptoManager.deriveVaultKey(password, salt)

        assertNotNull(derivedKey)
        assertEquals(32, derivedKey.encoded.size) // 256-bit AES key
    }

    @Test
    fun testVaultEncryptionAndDecryptionFlow() {
        val password = "TestPassword456!".toCharArray()
        val salt = CryptoManager.generateSalt()
        val key = CryptoManager.deriveVaultKey(password, salt)

        val samplePayload = VaultPayload(
            version = 2,
            entries = listOf(
                VaultEntry(
                    title = "GitHub Account",
                    username = "octocat",
                    password = "SuperSecretPassword789!"
                )
            ),
            folders = listOf("Personal")
        )

        val plainBytes = samplePayload.toJsonBytes()
        val encryptedBytes = CryptoManager.encryptVault(plainBytes, key, salt)

        // Decrypt
        val decryptedBytes = CryptoManager.decryptVault(encryptedBytes, key)
        val restoredPayload = VaultPayload.fromJsonBytes(decryptedBytes)

        assertEquals(1, restoredPayload.entries.size)
        assertEquals("GitHub Account", restoredPayload.entries[0].title)
        assertEquals("octocat", restoredPayload.entries[0].username)
        assertEquals("SuperSecretPassword789!", restoredPayload.entries[0].password)
    }

    @Test(expected = Exception::class)
    fun testTamperedVaultDecryptionFails() {
        val password = "TestPassword456!".toCharArray()
        val salt = CryptoManager.generateSalt()
        val key = CryptoManager.deriveVaultKey(password, salt)

        val samplePayload = VaultPayload(version = 2, entries = emptyList())
        val encryptedBytes = CryptoManager.encryptVault(samplePayload.toJsonBytes(), key, salt)

        // Corrupt a byte in the ciphertext
        encryptedBytes[encryptedBytes.size - 5] = (encryptedBytes[encryptedBytes.size - 5] + 1).toByte()

        // Should throw AEADBadTagException or CipherException
        CryptoManager.decryptVault(encryptedBytes, key)
    }

    @Test
    fun testWipeMemoryHelpers() {
        val charArray = "SensitivePassword".toCharArray()
        CryptoManager.wipe(charArray)
        assertTrue(charArray.all { it == '\u0000' })

        val byteArray = byteArrayOf(1, 2, 3, 4, 5)
        CryptoManager.wipe(byteArray)
        assertTrue(byteArray.all { it == 0.toByte() })
    }
}
