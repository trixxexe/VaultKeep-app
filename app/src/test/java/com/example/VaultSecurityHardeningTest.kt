package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.CryptoManager
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultSecurityHardeningTest {

    private lateinit var context: Context
    private lateinit var preferences: VaultPreferences
    private lateinit var repository: VaultRepository

    private fun getMasterPassword() = "TestMasterPassword123!".toCharArray()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.filesDir.listFiles()?.forEach { it.delete() }
        context.getSharedPreferences("vaultkeep_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        preferences = VaultPreferences(context)
        repository = VaultRepository(context, preferences)
    }

    @Test
    fun `tampered ciphertext triggers AEAD authentication failure`() {
        val salt = CryptoManager.generateSalt()
        val key = CryptoManager.deriveVaultKey(getMasterPassword(), salt)
        val plaintext = "Sensitive Credentials Data".toByteArray(Charsets.UTF_8)

        val encryptedBytes = CryptoManager.encryptVault(plaintext, key, salt)
        assertTrue(encryptedBytes.isNotEmpty())

        // Corrupt a byte in the ciphertext payload (after header)
        val corruptedBytes = encryptedBytes.clone()
        val targetIndex = corruptedBytes.size - 5
        corruptedBytes[targetIndex] = (corruptedBytes[targetIndex].toInt() xor 0xFF).toByte()

        // Decrypting tampered ciphertext must throw an AEAD integrity exception
        assertThrows(Exception::class.java) {
            CryptoManager.decryptVault(corruptedBytes, key)
        }
    }

    @Test
    fun `tampered nonce or IV triggers decryption failure`() {
        val salt = CryptoManager.generateSalt()
        val key = CryptoManager.deriveVaultKey(getMasterPassword(), salt)
        val plaintext = "Sample Credentials Payload".toByteArray(Charsets.UTF_8)

        val encryptedBytes = CryptoManager.encryptVault(plaintext, key, salt)

        // Find IV offset from the header info
        val header = CryptoManager.extractHeaderInfo(encryptedBytes)
        val ivFirstByte = header.iv[0]
        val ivIndex = encryptedBytes.indexOf(ivFirstByte)
        assertTrue("IV must be present in encrypted byte payload", ivIndex >= 0)

        val corruptedBytes = encryptedBytes.clone()
        corruptedBytes[ivIndex] = (corruptedBytes[ivIndex].toInt() xor 0x01).toByte()

        assertThrows(Exception::class.java) {
            CryptoManager.decryptVault(corruptedBytes, key)
        }
    }

    @Test
    fun `kdf parameters match strict security envelope`() {
        val salt = CryptoManager.generateSalt()
        val key = CryptoManager.deriveVaultKey(getMasterPassword(), salt)
        val plaintext = "Security Verification".toByteArray(Charsets.UTF_8)
        val encryptedBytes = CryptoManager.encryptVault(plaintext, key, salt)

        val header = CryptoManager.extractHeaderInfo(encryptedBytes)
        assertEquals(2, header.formatVersion)
        assertTrue("PBKDF2 iterations must be at least 310,000", header.iterations >= 310_000)
        assertEquals("Salt length must be 16 bytes (128 bits)", 16, header.salt.size)
        assertEquals("IV length must be 12 bytes (96 bits)", 12, header.iv.size)
    }

    @Test
    fun `verify my vault integrity checker passes on valid vault`() = runBlocking {
        repository.createInitialVault(getMasterPassword())

        val entry = VaultEntry(
            id = UUID.randomUUID().toString(),
            title = "Integrity Test Account",
            username = "admin@example.org",
            password = "VeryStrongPassword#99!",
            url = "https://example.org"
        )
        repository.saveEntry(entry)

        val integrityReport = repository.verifyVaultIntegrity()
        assertTrue(integrityReport.isAllPassed)
        assertNull(integrityReport.failureRecommendation)
        assertEquals(5, integrityReport.items.size)
        assertTrue(integrityReport.items.all { it.isPassed })
    }

    @Test
    fun `security center scanner correctly identifies all security issues`() {
        val now = System.currentTimeMillis()
        val twoYearsAgo = now - (24L * 30L * 24L * 60L * 60L * 1000L) // 2 years old

        val entries = listOf(
            // 1. Strong password, fresh
            VaultEntry(id = "1", title = "Bank", username = "alice", password = "K9#xP!2m\$vL9@qW4", url = "https://bank.com", updatedAt = now),
            // 2. Weak password (short, simple)
            VaultEntry(id = "2", title = "Forum", username = "alice", password = "123", url = "https://forum.com", updatedAt = now),
            // 3. Reused password with #4
            VaultEntry(id = "3", title = "Social 1", username = "alice", password = "ReusedSecret99!", url = "https://social1.com", updatedAt = now),
            VaultEntry(id = "4", title = "Social 2", username = "bob", password = "ReusedSecret99!", url = "https://social2.com", updatedAt = now),
            // 5. Duplicate domain & username with #1
            VaultEntry(id = "5", title = "Bank Backup", username = "alice", password = "AnotherSecretPassword#123", url = "https://bank.com/login", updatedAt = now),
            // 6. Old password (> 12 months)
            VaultEntry(id = "6", title = "Old Email", username = "alice", password = "OldSecurePassword#888!", url = "https://oldmail.com", updatedAt = twoYearsAgo),
            // 7. Missing username
            VaultEntry(id = "7", title = "No User Account", username = "", password = "SecureStandaloneKey#77", url = "https://secret.com", updatedAt = now)
        )

        val report = SecurityScanner.scanVault(
            entries = entries,
            oldThresholdMonths = 12,
            currentTimeMillis = now
        )

        assertEquals(7, report.totalEntries)
        assertEquals(1, report.weakPasswords.size)
        assertEquals("2", report.weakPasswords[0].id)

        assertEquals(2, report.reusedEntries.size)
        assertEquals(1, report.reusedPasswordGroups.size)

        assertEquals(2, report.duplicateEntries.size)
        assertEquals(1, report.duplicateEntryGroups.size)

        assertEquals(1, report.oldPasswords.size)
        assertEquals("6", report.oldPasswords[0].id)

        assertEquals(1, report.missingUsernames.size)
        assertEquals("7", report.missingUsernames[0].id)

        assertFalse(report.isAllClear)
        assertTrue(report.healthScorePercentage in 1..95)
    }

    @Test
    fun `changing master password securely re-encrypts vault`() = runBlocking {
        repository.createInitialVault(getMasterPassword())

        val entry = VaultEntry(
            id = "test-id",
            title = "Test Credentials",
            username = "user",
            password = "password123"
        )
        repository.saveEntry(entry)

        val newPassword = "BrandNewMasterPassword456#"
        val changeResult = repository.changeMasterPassword(getMasterPassword(), newPassword.toCharArray())
        assertTrue("changeMasterPassword failed: ${changeResult.exceptionOrNull()?.message}", changeResult.isSuccess)

        // Old password must fail to unlock
        repository.lockVault()
        val oldUnlock = repository.unlockWithPassword(getMasterPassword())
        assertFalse(oldUnlock.isSuccess)

        // New password must unlock successfully and recover entry
        val newUnlock = repository.unlockWithPassword(newPassword.toCharArray())
        assertTrue(newUnlock.isSuccess)
        val state = repository.vaultState.value as VaultState.Unlocked
        assertEquals(1, state.entries.size)
        assertEquals("Test Credentials", state.entries[0].title)
    }

    @Test
    fun `export backup updates last backup timestamp and verifies health`() = runBlocking {
        repository.createInitialVault(getMasterPassword())

        val initialTimestamp = preferences.lastBackupTimestamp
        assertEquals(0L, initialTimestamp)

        val stream = ByteArrayOutputStream()
        val exportResult = repository.exportEncryptedBackup("BackupKey#123".toCharArray(), stream)
        assertTrue(exportResult.isSuccess)

        assertTrue(preferences.lastBackupTimestamp > 0L)
    }
}
