package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultRepositoryTest {

    private lateinit var context: Context
    private lateinit var preferences: VaultPreferences
    private lateinit var repository: VaultRepository

    private fun getMasterPassword() = "SuperSecureMasterPassword123!".toCharArray()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.filesDir.listFiles()?.forEach { it.delete() }
        context.getSharedPreferences("vaultkeep_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        preferences = VaultPreferences(context)
        repository = VaultRepository(context, preferences)
    }

    @Test
    fun `vault lifecycle - create, lock, unlock, and manage entries`() = runBlocking {
        assertFalse(repository.isVaultCreated)
        assertEquals(VaultState.Uninitialized, repository.vaultState.value)

        // 1. Create vault
        val createResult = repository.createInitialVault(getMasterPassword())
        assertTrue(createResult.isSuccess)
        assertTrue(repository.isVaultCreated)
        assertTrue(repository.vaultState.value is VaultState.Unlocked)

        // 2. Add entry
        val entry = VaultEntry(
            id = UUID.randomUUID().toString(),
            title = "Test Service",
            username = "user@example.com",
            password = "SecretPassword!99",
            url = "https://example.com",
            folder = "Work",
            isFavorite = true
        )
        val saveResult = repository.saveEntry(entry)
        assertTrue(saveResult.isSuccess)

        val unlockedState = repository.vaultState.value as VaultState.Unlocked
        assertEquals(1, unlockedState.entries.size)
        assertEquals("Test Service", unlockedState.entries[0].title)

        // 3. Lock vault
        repository.lockVault()
        assertEquals(VaultState.Locked, repository.vaultState.value)

        // 4. Unlock vault with wrong password fails
        val wrongUnlock = repository.unlockWithPassword("WrongPassword".toCharArray())
        assertFalse(wrongUnlock.isSuccess)
        assertEquals(VaultState.Locked, repository.vaultState.value)

        // 5. Unlock vault with correct password succeeds
        val correctUnlock = repository.unlockWithPassword(getMasterPassword())
        assertTrue(correctUnlock.isSuccess)
        val restoredState = repository.vaultState.value as VaultState.Unlocked
        assertEquals(1, restoredState.entries.size)
        assertEquals("Test Service", restoredState.entries[0].title)
        assertEquals("SecretPassword!99", restoredState.entries[0].password)
    }

    @Test
    fun `encrypted backup export and import works end-to-end`() = runBlocking {
        repository.createInitialVault(getMasterPassword())

        val entry1 = VaultEntry(
            id = "e1",
            title = "GitHub",
            username = "octocat",
            password = "gh-password",
            url = "https://github.com",
            folder = "Dev"
        )
        repository.saveEntry(entry1)

        // Export backup with backup password
        val backupPassword = "BackupPassword99!".toCharArray()
        val backupStream = ByteArrayOutputStream()
        val exportResult = repository.exportEncryptedBackup(backupPassword.clone(), backupStream)
        assertTrue(exportResult.isSuccess)

        val backupBytes = backupStream.toByteArray()
        assertTrue(backupBytes.isNotEmpty())

        // Create new second repository to restore
        val context2: Context = ApplicationProvider.getApplicationContext()
        val preferences2 = VaultPreferences(context2)
        val repo2 = VaultRepository(context2, preferences2)
        repo2.createInitialVault("TemporaryPassword123!".toCharArray())

        val importInputStream = ByteArrayInputStream(backupBytes)
        val importResult = repo2.importEncryptedBackup(backupPassword.clone(), importInputStream, merge = false)
        assertTrue("Import failed: ${importResult.exceptionOrNull()?.message}", importResult.isSuccess)
        assertEquals(1, importResult.getOrNull())

        val repo2State = repo2.vaultState.value as VaultState.Unlocked
        assertEquals(1, repo2State.entries.size)
        assertEquals("GitHub", repo2State.entries[0].title)
        assertEquals("gh-password", repo2State.entries[0].password)
    }

    @Test
    fun `autofill matcher matches exact domain and subdomains`() = runBlocking {
        repository.createInitialVault(getMasterPassword())

        val entry = VaultEntry(
            id = "e2",
            title = "Proton Mail",
            username = "user@proton.me",
            password = "proton-secure-pass",
            url = "https://mail.proton.me/login"
        )
        repository.saveEntry(entry)

        val matches = repository.findAutofillMatches("mail.proton.me")
        assertEquals(1, matches.size)
        assertEquals("Proton Mail", matches[0].title)

        val parentMatches = repository.findAutofillMatches("proton.me")
        assertEquals(1, parentMatches.size)

        val nonMatches = repository.findAutofillMatches("google.com")
        assertEquals(0, nonMatches.size)
    }
}
