package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.CryptoManager
import com.example.data.VaultPreferences
import com.example.data.VaultRepository
import com.example.ui.MainViewModel
import com.example.ui.util.AccessibilityUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WelcomeSequenceAndRecoveryKitTest {

    private lateinit var context: Context
    private lateinit var preferences: VaultPreferences
    private lateinit var repository: VaultRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.filesDir.listFiles()?.forEach { it.delete() }
        context.getSharedPreferences("vaultkeep_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        preferences = VaultPreferences(context)
        repository = VaultRepository(context, preferences)
    }

    @Test
    fun `hasSeenWelcomeSequence is false on fresh install and persists true once completed`() {
        assertFalse("Welcome sequence must show on fresh install", preferences.hasSeenWelcomeSequence)

        preferences.hasSeenWelcomeSequence = true
        assertTrue("hasSeenWelcomeSequence must be true after setting", preferences.hasSeenWelcomeSequence)

        // Simulate app restart / new instance of preferences reading from disk
        val reloadedPrefs = VaultPreferences(context)
        assertTrue("hasSeenWelcomeSequence must persist across app restarts", reloadedPrefs.hasSeenWelcomeSequence)
    }

    @Test
    fun `viewModel markWelcomeSequenceSeen updates stateFlow and SharedPreferences`() {
        val viewModel = MainViewModel(context, repository, preferences)
        assertFalse(viewModel.hasSeenWelcomeSequence.value)

        viewModel.markWelcomeSequenceSeen()
        assertTrue(viewModel.hasSeenWelcomeSequence.value)
        assertTrue(preferences.hasSeenWelcomeSequence)
    }

    @Test
    fun `recovery kit salt extraction succeeds when vault is created`() = runBlocking {
        val password = "StrongMasterPass123!".toCharArray()
        val result = repository.createInitialVault(password)
        assertTrue(result.isSuccess)

        val salt = repository.getVaultSalt()
        assertNotNull("Salt must not be null after vault creation", salt)
        assertEquals(CryptoManager.SALT_LENGTH_BYTES, salt!!.size)

        val saltHex = salt.joinToString("") { "%02x".format(it) }
        assertEquals(CryptoManager.SALT_LENGTH_BYTES * 2, saltHex.length)
    }

    @Test
    fun `reduce motion helper returns boolean without crashing`() {
        val reduceMotion = AccessibilityUtils.isReduceMotionEnabled(context)
        assertNotNull(reduceMotion)
    }
}
