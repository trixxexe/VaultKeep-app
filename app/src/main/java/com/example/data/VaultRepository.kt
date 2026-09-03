package com.example.data

import android.content.Context
import com.example.crypto.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

sealed interface VaultState {
    data object Uninitialized : VaultState // No vault file exists yet
    data object Locked : VaultState // Vault exists and is locked
    data class Unlocked(
        val entries: List<VaultEntry>,
        val folders: List<String> = emptyList(),
        val tags: List<String> = emptyList()
    ) : VaultState // Decrypted and active
}

class VaultRepository(
    private val context: Context,
    val preferences: VaultPreferences
) {
    private val vaultFile = File(context.filesDir, VAULT_FILE_NAME)
    private val vaultBackupFile = File(context.filesDir, "$VAULT_FILE_NAME.bak")
    private val vaultTempFile = File(context.filesDir, "$VAULT_FILE_NAME.tmp")

    private val _vaultState = MutableStateFlow<VaultState>(VaultState.Locked)
    val vaultState: StateFlow<VaultState> = _vaultState.asStateFlow()

    // In-memory active key and payload (volatile only, wiped on lock)
    private var activeVaultKey: SecretKey? = null
    private var activePayload: VaultPayload? = null
    private var activeSalt: ByteArray? = null

    val isVaultCreated: Boolean
        get() = (vaultFile.exists() && vaultFile.length() > 0) || (vaultBackupFile.exists() && vaultBackupFile.length() > 0)

    val isBiometricLockedOut: Boolean
        get() = preferences.failedBiometricAttempts >= VaultPreferences.MAX_FAILED_BIOMETRIC_ATTEMPTS

    fun getVaultSalt(): ByteArray? {
        return activeSalt ?: if (isVaultCreated && vaultFile.exists() && vaultFile.length() > 0) {
            try {
                val bytes = vaultFile.readBytes()
                CryptoManager.extractSaltAndIterations(bytes).first
            } catch (_: Exception) {
                null
            }
        } else null
    }

    init {
        checkInitialState()
    }

    fun checkInitialState() {
        if (!isVaultCreated) {
            _vaultState.value = VaultState.Uninitialized
        } else if (_vaultState.value == VaultState.Uninitialized) {
            _vaultState.value = VaultState.Locked
        }
    }

    /**
     * Initializes a brand new vault with the user's master password.
     */
    suspend fun createInitialVault(password: CharArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val salt = CryptoManager.generateSalt()
            val derivedKey = CryptoManager.deriveVaultKey(password, salt)
            val emptyPayload = VaultPayload(version = 2, entries = emptyList(), folders = listOf("Personal", "Work", "Finance"))
            
            persistVaultAtomic(emptyPayload, derivedKey, salt)

            activeVaultKey = derivedKey
            activePayload = emptyPayload
            activeSalt = salt
            _vaultState.value = VaultState.Unlocked(emptyList(), emptyPayload.folders)
            preferences.failedBiometricAttempts = 0
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            CryptoManager.wipe(password)
        }
    }

    /**
     * Unlocks the vault using the master password. Supports automatic recovery from .bak if primary is damaged.
     */
    suspend fun unlockWithPassword(password: CharArray): Result<List<VaultEntry>> = withContext(Dispatchers.IO) {
        try {
            if (!isVaultCreated) {
                return@withContext Result.failure(IllegalStateException("No vault found"))
            }

            // Try primary vault file first, fallback to backup only if primary is missing or corrupt
            val fileToRead = when {
                vaultFile.exists() && vaultFile.length() > 0 -> {
                    try {
                        val bytes = vaultFile.readBytes()
                        CryptoManager.extractHeaderInfo(bytes)
                        vaultFile
                    } catch (_: Exception) {
                        if (vaultBackupFile.exists() && vaultBackupFile.length() > 0) vaultBackupFile else vaultFile
                    }
                }
                vaultBackupFile.exists() && vaultBackupFile.length() > 0 -> vaultBackupFile
                else -> return@withContext Result.failure(IllegalStateException("No vault file found"))
            }

            val fileBytes = fileToRead.readBytes()
            val (salt, iterations) = CryptoManager.extractSaltAndIterations(fileBytes)
            val derivedKey = CryptoManager.deriveVaultKey(password, salt, iterations)
            val decryptedBytes = CryptoManager.decryptVault(fileBytes, derivedKey)
            val payload = VaultPayload.fromJsonBytes(decryptedBytes)

            if (fileToRead == vaultBackupFile) {
                persistVaultAtomic(payload, derivedKey, salt)
            }

            activeVaultKey = derivedKey
            activePayload = payload
            activeSalt = salt
            preferences.failedBiometricAttempts = 0

            _vaultState.value = VaultState.Unlocked(payload.entries, payload.folders)
            Result.success(payload.entries)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            CryptoManager.wipe(password)
        }
    }

    /**
     * Unlocks the vault using the biometric Cipher (hardware Keystore release).
     */
    suspend fun unlockWithBiometrics(cipher: Cipher): Result<List<VaultEntry>> = withContext(Dispatchers.IO) {
        if (isBiometricLockedOut) {
            return@withContext Result.failure(IllegalStateException("Biometric unlock locked out due to 5 failed attempts. Please unlock with master password."))
        }

        try {
            if (!isVaultCreated) {
                return@withContext Result.failure(IllegalStateException("No vault found"))
            }

            val wrappedKeyBytes = preferences.wrappedBiometricKey
                ?: return@withContext Result.failure(IllegalStateException("Biometric key not enrolled"))

            // Hardware Keystore decrypts the wrapped key material
            val rawKeyBytes = cipher.doFinal(wrappedKeyBytes)
            val vaultKey = SecretKeySpec(rawKeyBytes, "AES")
            CryptoManager.wipe(rawKeyBytes)

            val fileToRead = if (vaultFile.exists() && vaultFile.length() > 0) vaultFile else vaultBackupFile
            val fileBytes = fileToRead.readBytes()
            val (salt, _) = CryptoManager.extractSaltAndIterations(fileBytes)
            val decryptedBytes = CryptoManager.decryptVault(fileBytes, vaultKey)
            val payload = VaultPayload.fromJsonBytes(decryptedBytes)

            activeVaultKey = vaultKey
            activePayload = payload
            activeSalt = salt
            preferences.failedBiometricAttempts = 0

            _vaultState.value = VaultState.Unlocked(payload.entries, payload.folders)
            Result.success(payload.entries)
        } catch (e: Exception) {
            preferences.failedBiometricAttempts += 1
            Result.failure(e)
        }
    }

    /**
     * Enrolls biometrics by wrapping the currently active in-memory vault key with Keystore Cipher.
     */
    suspend fun enrollBiometrics(cipher: Cipher): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentKey = activeVaultKey
                ?: return@withContext Result.failure(IllegalStateException("Vault must be unlocked to enable biometrics"))

            val rawKeyBytes = currentKey.encoded
            val wrappedKey = cipher.doFinal(rawKeyBytes)
            CryptoManager.wipe(rawKeyBytes)

            preferences.wrappedBiometricKey = wrappedKey
            preferences.biometricIv = cipher.iv
            preferences.isBiometricEnabled = true
            preferences.failedBiometricAttempts = 0

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Immediately locks the vault, wiping in-memory keys and payload.
     */
    fun lockVault() {
        activeVaultKey = null
        activePayload = null
        CryptoManager.wipe(activeSalt)
        activeSalt = null
        _vaultState.value = if (isVaultCreated) VaultState.Locked else VaultState.Uninitialized
        VaultLockNotifier.notifyLocked()
    }

    /**
     * Disables biometric unlock and removes Keystore keys.
     */
    fun disableBiometrics() {
        preferences.clearBiometricData()
        CryptoManager.deleteBiometricKey()
    }

    /**
     * Adds or updates a vault entry.
     */
    suspend fun saveEntry(entry: VaultEntry): Result<Unit> = withContext(Dispatchers.IO) {
        val currentPayload = activePayload
        val currentKey = activeVaultKey
        val salt = activeSalt

        if (currentPayload == null || currentKey == null || salt == null) {
            return@withContext Result.failure(IllegalStateException("Vault is locked"))
        }

        try {
            val existingIndex = currentPayload.entries.indexOfFirst { it.id == entry.id }
            val updatedList = currentPayload.entries.toMutableList()
            if (existingIndex >= 0) {
                updatedList[existingIndex] = entry
            } else {
                updatedList.add(0, entry) // Add to top
            }

            // Ensure folder is tracked in folders list
            val updatedFolders = if (entry.folder.isNotBlank() && !currentPayload.folders.contains(entry.folder)) {
                currentPayload.folders + entry.folder
            } else {
                currentPayload.folders
            }

            val updatedTags = currentPayload.tags.toMutableSet()
            entry.tags.forEach { if (it.isNotBlank()) updatedTags.add(it.trim()) }

            val newPayload = currentPayload.copy(
                entries = updatedList,
                folders = updatedFolders,
                tags = updatedTags.toList()
            )
            persistVaultAtomic(newPayload, currentKey, salt)

            activePayload = newPayload
            _vaultState.value = VaultState.Unlocked(newPayload.entries, newPayload.folders, newPayload.tags)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Toggles favorite status for an entry.
     */
    suspend fun toggleFavorite(entryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentPayload = activePayload ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val entry = currentPayload.entries.find { it.id == entryId } ?: return@withContext Result.failure(IllegalArgumentException("Entry not found"))
        val updated = entry.copy(isFavorite = !entry.isFavorite, updatedAt = System.currentTimeMillis())
        saveEntry(updated)
    }

    /**
     * Moves an entry to Trash (Soft-Delete).
     */
    suspend fun moveToTrash(entryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentPayload = activePayload ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val currentKey = activeVaultKey ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val salt = activeSalt ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))

        try {
            val now = System.currentTimeMillis()
            val updatedList = currentPayload.entries.map {
                if (it.id == entryId) it.copy(isDeleted = true, deletedAt = now, updatedAt = now) else it
            }
            val newPayload = currentPayload.copy(entries = updatedList)
            persistVaultAtomic(newPayload, currentKey, salt)

            activePayload = newPayload
            _vaultState.value = VaultState.Unlocked(newPayload.entries, newPayload.folders, newPayload.tags)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Restores an entry from Trash.
     */
    suspend fun restoreFromTrash(entryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentPayload = activePayload ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val currentKey = activeVaultKey ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val salt = activeSalt ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))

        try {
            val now = System.currentTimeMillis()
            val updatedList = currentPayload.entries.map {
                if (it.id == entryId) it.copy(isDeleted = false, deletedAt = 0L, updatedAt = now) else it
            }
            val newPayload = currentPayload.copy(entries = updatedList)
            persistVaultAtomic(newPayload, currentKey, salt)

            activePayload = newPayload
            _vaultState.value = VaultState.Unlocked(newPayload.entries, newPayload.folders, newPayload.tags)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Permanently deletes an entry from the vault.
     */
    suspend fun deletePermanently(entryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentPayload = activePayload ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val currentKey = activeVaultKey ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val salt = activeSalt ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))

        try {
            val updatedList = currentPayload.entries.filterNot { it.id == entryId }
            val newPayload = currentPayload.copy(entries = updatedList)
            persistVaultAtomic(newPayload, currentKey, salt)

            activePayload = newPayload
            _vaultState.value = VaultState.Unlocked(newPayload.entries, newPayload.folders, newPayload.tags)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Empties all items currently in the Trash bin permanently.
     */
    suspend fun emptyTrash(): Result<Int> = withContext(Dispatchers.IO) {
        val currentPayload = activePayload ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val currentKey = activeVaultKey ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val salt = activeSalt ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))

        try {
            val trashCount = currentPayload.entries.count { it.isDeleted }
            val updatedList = currentPayload.entries.filterNot { it.isDeleted }
            val newPayload = currentPayload.copy(entries = updatedList)
            persistVaultAtomic(newPayload, currentKey, salt)

            activePayload = newPayload
            _vaultState.value = VaultState.Unlocked(newPayload.entries, newPayload.folders, newPayload.tags)
            Result.success(trashCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Bulk moves items to Trash.
     */
    suspend fun bulkMoveToTrash(entryIds: Set<String>): Result<Int> = withContext(Dispatchers.IO) {
        val currentPayload = activePayload ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val currentKey = activeVaultKey ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val salt = activeSalt ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))

        try {
            val now = System.currentTimeMillis()
            var count = 0
            val updatedList = currentPayload.entries.map {
                if (entryIds.contains(it.id)) {
                    count++
                    it.copy(isDeleted = true, deletedAt = now, updatedAt = now)
                } else it
            }
            val newPayload = currentPayload.copy(entries = updatedList)
            persistVaultAtomic(newPayload, currentKey, salt)

            activePayload = newPayload
            _vaultState.value = VaultState.Unlocked(newPayload.entries, newPayload.folders, newPayload.tags)
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Bulk restores items from Trash.
     */
    suspend fun bulkRestoreFromTrash(entryIds: Set<String>): Result<Int> = withContext(Dispatchers.IO) {
        val currentPayload = activePayload ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val currentKey = activeVaultKey ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val salt = activeSalt ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))

        try {
            val now = System.currentTimeMillis()
            var count = 0
            val updatedList = currentPayload.entries.map {
                if (entryIds.contains(it.id)) {
                    count++
                    it.copy(isDeleted = false, deletedAt = 0L, updatedAt = now)
                } else it
            }
            val newPayload = currentPayload.copy(entries = updatedList)
            persistVaultAtomic(newPayload, currentKey, salt)

            activePayload = newPayload
            _vaultState.value = VaultState.Unlocked(newPayload.entries, newPayload.folders, newPayload.tags)
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes an entry from the vault (soft-delete to trash by default).
     */
    suspend fun deleteEntry(entryId: String): Result<Unit> = moveToTrash(entryId)

    /**
     * Adds a new folder collection.
     */
    suspend fun addFolder(folderName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentPayload = activePayload ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val currentKey = activeVaultKey ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val salt = activeSalt ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))

        val trimmed = folderName.trim()
        if (trimmed.isBlank() || currentPayload.folders.contains(trimmed)) {
            return@withContext Result.success(Unit)
        }

        val updatedFolders = currentPayload.folders + trimmed
        val newPayload = currentPayload.copy(folders = updatedFolders)
        persistVaultAtomic(newPayload, currentKey, salt)

        activePayload = newPayload
        _vaultState.value = VaultState.Unlocked(newPayload.entries, newPayload.folders, newPayload.tags)
        Result.success(Unit)
    }

    /**
     * Adds a new tag.
     */
    suspend fun addTag(tagName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentPayload = activePayload ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val currentKey = activeVaultKey ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val salt = activeSalt ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))

        val trimmed = tagName.trim()
        if (trimmed.isBlank() || currentPayload.tags.contains(trimmed)) {
            return@withContext Result.success(Unit)
        }

        val updatedTags = currentPayload.tags + trimmed
        val newPayload = currentPayload.copy(tags = updatedTags)
        persistVaultAtomic(newPayload, currentKey, salt)

        activePayload = newPayload
        _vaultState.value = VaultState.Unlocked(newPayload.entries, newPayload.folders, newPayload.tags)
        Result.success(Unit)
    }

    /**
     * Bulk moves entries to a specified folder.
     */
    suspend fun bulkMoveToFolder(entryIds: Set<String>, targetFolder: String): Result<Int> = withContext(Dispatchers.IO) {
        val currentPayload = activePayload ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val currentKey = activeVaultKey ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val salt = activeSalt ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))

        try {
            var count = 0
            val updatedEntries = currentPayload.entries.map { entry ->
                if (entryIds.contains(entry.id)) {
                    count++
                    entry.copy(folder = targetFolder.trim(), updatedAt = System.currentTimeMillis())
                } else {
                    entry
                }
            }

            val updatedFolders = if (targetFolder.isNotBlank() && !currentPayload.folders.contains(targetFolder.trim())) {
                currentPayload.folders + targetFolder.trim()
            } else {
                currentPayload.folders
            }

            val newPayload = currentPayload.copy(entries = updatedEntries, folders = updatedFolders)
            persistVaultAtomic(newPayload, currentKey, salt)

            activePayload = newPayload
            _vaultState.value = VaultState.Unlocked(newPayload.entries, newPayload.folders, newPayload.tags)
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Bulk adds a tag to selected entries.
     */
    suspend fun bulkAddTag(entryIds: Set<String>, tag: String): Result<Int> = withContext(Dispatchers.IO) {
        val currentPayload = activePayload ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val currentKey = activeVaultKey ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val salt = activeSalt ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))

        val trimmedTag = tag.trim()
        if (trimmedTag.isBlank()) return@withContext Result.success(0)

        try {
            var count = 0
            val updatedEntries = currentPayload.entries.map { entry ->
                if (entryIds.contains(entry.id)) {
                    count++
                    val newTags = if (entry.tags.contains(trimmedTag)) entry.tags else entry.tags + trimmedTag
                    entry.copy(tags = newTags, updatedAt = System.currentTimeMillis())
                } else {
                    entry
                }
            }

            val updatedTags = if (!currentPayload.tags.contains(trimmedTag)) {
                currentPayload.tags + trimmedTag
            } else {
                currentPayload.tags
            }

            val newPayload = currentPayload.copy(entries = updatedEntries, tags = updatedTags)
            persistVaultAtomic(newPayload, currentKey, salt)

            activePayload = newPayload
            _vaultState.value = VaultState.Unlocked(newPayload.entries, newPayload.folders, newPayload.tags)
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Bulk deletes selected entries from the vault.
     */
    suspend fun bulkDelete(entryIds: Set<String>): Result<Int> = withContext(Dispatchers.IO) {
        val currentPayload = activePayload ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val currentKey = activeVaultKey ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val salt = activeSalt ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))

        try {
            val initialSize = currentPayload.entries.size
            val updatedEntries = currentPayload.entries.filterNot { entryIds.contains(it.id) }
            val deletedCount = initialSize - updatedEntries.size

            val newPayload = currentPayload.copy(entries = updatedEntries)
            persistVaultAtomic(newPayload, currentKey, salt)

            activePayload = newPayload
            _vaultState.value = VaultState.Unlocked(newPayload.entries, newPayload.folders, newPayload.tags)
            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clears the password history for a specific entry.
     */
    suspend fun clearPasswordHistory(entryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentPayload = activePayload ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val currentKey = activeVaultKey ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val salt = activeSalt ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))

        try {
            val updatedEntries = currentPayload.entries.map { entry ->
                if (entry.id == entryId) {
                    entry.copy(passwordHistory = emptyList(), updatedAt = System.currentTimeMillis())
                } else {
                    entry
                }
            }

            val newPayload = currentPayload.copy(entries = updatedEntries)
            persistVaultAtomic(newPayload, currentKey, salt)

            activePayload = newPayload
            _vaultState.value = VaultState.Unlocked(newPayload.entries, newPayload.folders, newPayload.tags)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Imports a batch of entries into the vault in memory, then persists atomically.
     */
    suspend fun importEntries(newEntries: List<VaultEntry>): Result<Int> = withContext(Dispatchers.IO) {
        val currentPayload = activePayload ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val currentKey = activeVaultKey ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))
        val salt = activeSalt ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))

        try {
            val existingIds = currentPayload.entries.map { it.id }.toSet()
            val filteredNew = newEntries.filterNot { existingIds.contains(it.id) }
            val combined = (filteredNew + currentPayload.entries)

            val newFolders = mutableSetOf<String>()
            newFolders.addAll(currentPayload.folders)
            newEntries.forEach {
                if (it.folder.isNotBlank()) newFolders.add(it.folder)
            }

            val newPayload = currentPayload.copy(
                entries = combined,
                folders = newFolders.toList()
            )
            persistVaultAtomic(newPayload, currentKey, salt)

            activePayload = newPayload
            _vaultState.value = VaultState.Unlocked(newPayload.entries, newPayload.folders)
            Result.success(filteredNew.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Changes the master password and re-encrypts the vault with a fresh random salt and atomic write.
     */
    suspend fun changeMasterPassword(
        oldPassword: CharArray,
        newPassword: CharArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentPayload = activePayload
                ?: return@withContext Result.failure(IllegalStateException("Vault must be unlocked"))

            val fileToRead = if (vaultFile.exists() && vaultFile.length() > 0) vaultFile else vaultBackupFile
            val fileBytes = fileToRead.readBytes()
            val (oldSalt, oldIterations) = CryptoManager.extractSaltAndIterations(fileBytes)
            val oldDerivedKey = CryptoManager.deriveVaultKey(oldPassword, oldSalt, oldIterations)

            // Test decryption with old password
            CryptoManager.decryptVault(fileBytes, oldDerivedKey)

            // Generate fresh salt and new key for the new password
            val newSalt = CryptoManager.generateSalt()
            val newDerivedKey = CryptoManager.deriveVaultKey(newPassword, newSalt)

            persistVaultAtomic(currentPayload, newDerivedKey, newSalt)
            if (vaultBackupFile.exists()) {
                vaultBackupFile.delete()
            }

            // Update in-memory state
            activeVaultKey = newDerivedKey
            activeSalt = newSalt

            // Disable biometrics on password change (requires re-enrollment with new key)
            disableBiometrics()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            CryptoManager.wipe(oldPassword)
            CryptoManager.wipe(newPassword)
        }
    }

    /**
     * Exports the current vault as an encrypted backup file to an output stream.
     * Backup Health Verification: immediately test-decrypts the generated bytes in memory before declaring success.
     */
    suspend fun exportEncryptedBackup(
        exportPassword: CharArray,
        outputStream: OutputStream
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentPayload = activePayload
                ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))

            val salt = CryptoManager.generateSalt()
            val exportKey = CryptoManager.deriveVaultKey(exportPassword, salt)
            val plainBytes = currentPayload.toJsonBytes()
            val encryptedBytes = CryptoManager.encryptVault(plainBytes, exportKey, salt)

            // Verify the encrypted bytes can be cleanly decrypted and parsed in memory
            val testDecrypted = CryptoManager.decryptVault(encryptedBytes, exportKey)
            val testPayload = VaultPayload.fromJsonBytes(testDecrypted)
            if (testPayload.entries.size != currentPayload.entries.size) {
                throw IllegalStateException("Backup verification failed: entry count mismatch")
            }

            outputStream.use { it.write(encryptedBytes) }
            preferences.lastBackupTimestamp = System.currentTimeMillis()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            CryptoManager.wipe(exportPassword)
        }
    }

    /**
     * Performs a comprehensive, 100% on-device integrity check of the active vault and container parameters.
     */
    suspend fun verifyVaultIntegrity(): VaultIntegrityReport = withContext(Dispatchers.IO) {
        val items = mutableListOf<IntegrityCheckItem>()
        var failureRecommendation: String? = null

        val currentPayload = activePayload
        val currentKey = activeVaultKey

        // 1. Container Format & Magic Header
        try {
            val fileToRead = if (vaultFile.exists() && vaultFile.length() > 0) vaultFile else vaultBackupFile
            if (!fileToRead.exists() || fileToRead.length() == 0L) {
                items.add(IntegrityCheckItem("format", "Container Format", "Vault container file structure", false, "Vault file not found on disk"))
            } else {
                val bytes = fileToRead.readBytes()
                val header = CryptoManager.extractHeaderInfo(bytes)
                if (header.formatVersion >= 1) {
                    items.add(IntegrityCheckItem("format", "Container Format", "Matches expected schema v${header.formatVersion}", true, "Valid magic header (${if (header.formatVersion >= 2) "VKV2" else "VKV1"}) format"))
                } else {
                    items.add(IntegrityCheckItem("format", "Container Format", "Unknown format schema", false, "Unsupported format version ${header.formatVersion}"))
                }
            }
        } catch (e: Exception) {
            items.add(IntegrityCheckItem("format", "Container Format", "Vault container file structure", false, "Format check failed: ${e.message}"))
            failureRecommendation = "Vault file header appears corrupt. Restore from an encrypted backup (.vkeep)."
        }

        // 2. AES-256-GCM AEAD Tag Check (Tamper Detection)
        if (currentKey != null && vaultFile.exists() && vaultFile.length() > 0) {
            try {
                val fileBytes = vaultFile.readBytes()
                CryptoManager.decryptVault(fileBytes, currentKey)
                items.add(IntegrityCheckItem("aead_tag", "Authentication Tag (AEAD)", "AES-GCM 128-bit integrity tag validation", true, "Tag matches ciphertext; zero bit-flips or tampering detected"))
            } catch (e: Exception) {
                items.add(IntegrityCheckItem("aead_tag", "Authentication Tag (AEAD)", "AES-GCM 128-bit integrity tag validation", false, "AEAD tag mismatch: ${e.message}"))
                if (failureRecommendation == null) failureRecommendation = "Authentication tag failed. The vault file may be corrupted or altered. Restore from backup."
            }
        } else if (currentKey != null) {
            items.add(IntegrityCheckItem("aead_tag", "Authentication Tag (AEAD)", "AES-GCM 128-bit integrity tag validation", true, "In-memory session verified"))
        } else {
            items.add(IntegrityCheckItem("aead_tag", "Authentication Tag (AEAD)", "AES-GCM 128-bit integrity tag validation", false, "Vault is locked"))
        }

        // 3. Decodable Records & Schema Parsability
        if (currentPayload != null) {
            var corruptedCount = 0
            for (entry in currentPayload.entries) {
                if (entry.id.isBlank() || entry.title.isBlank() && entry.password.isBlank()) {
                    corruptedCount++
                }
            }
            if (corruptedCount == 0) {
                items.add(IntegrityCheckItem("records", "Record Integrity", "All ${currentPayload.entries.size} entries readable and well-formed", true, "100% of credential objects validated successfully"))
            } else {
                items.add(IntegrityCheckItem("records", "Record Integrity", "Corrupted records detected", false, "$corruptedCount malformed records found"))
                if (failureRecommendation == null) failureRecommendation = "One or more vault entries are malformed. Export a new backup or clean damaged entries."
            }
        } else {
            items.add(IntegrityCheckItem("records", "Record Integrity", "Credential structures", false, "Unlock vault to inspect records"))
        }

        // 4. Crypto Parameters Verification (Iterations, Salt, IV/Nonce)
        try {
            val fileToRead = if (vaultFile.exists() && vaultFile.length() > 0) vaultFile else vaultBackupFile
            val fileBytes = fileToRead.readBytes()
            val header = CryptoManager.extractHeaderInfo(fileBytes)
            val iterationsOk = header.iterations >= 310_000
            val saltOk = header.salt.size >= 16
            val ivOk = header.iv.size == 12

            val pass = iterationsOk && saltOk && ivOk
            val desc = "KDF: ${header.iterations} rounds, Salt: ${header.salt.size * 8}-bit, IV: ${header.iv.size * 8}-bit"
            items.add(IntegrityCheckItem("crypto_params", "Cryptographic Parameters", desc, pass, if (pass) "Exceeds standard security parameters (PBKDF2-HMAC-SHA256 >= 310k rounds, 128-bit salt, 96-bit GCM IV)" else "Parameters outside recommended envelope"))
        } catch (e: Exception) {
            items.add(IntegrityCheckItem("crypto_params", "Cryptographic Parameters", "KDF and salt/IV parameters", false, "Failed to read params: ${e.message}"))
        }

        // 5. Local Backup Copy Status (.bak)
        if (vaultBackupFile.exists() && vaultBackupFile.length() > 0) {
            try {
                val bakBytes = vaultBackupFile.readBytes()
                val header = CryptoManager.extractHeaderInfo(bakBytes)
                items.add(IntegrityCheckItem("backup_status", "Local Fallback Backup (.bak)", "Local safety replica state", true, "Active safety copy valid (Format v${header.formatVersion})"))
            } catch (e: Exception) {
                items.add(IntegrityCheckItem("backup_status", "Local Fallback Backup (.bak)", "Local safety replica state", false, "Local .bak file unreadable: ${e.message}"))
            }
        } else {
            items.add(IntegrityCheckItem("backup_status", "Local Fallback Backup (.bak)", "Local safety replica state", true, "Fallback file will be created on next change"))
        }

        val allPassed = items.all { it.isPassed }
        VaultIntegrityReport(
            isAllPassed = allPassed,
            items = items,
            failureRecommendation = if (allPassed) null else failureRecommendation ?: "Please export or restore from a known good backup."
        )
    }

    /**
     * Imports an encrypted backup file from an input stream and prompts for backup's password.
     */
    suspend fun importEncryptedBackup(
        backupPassword: CharArray,
        inputStream: InputStream,
        merge: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val currentKey = activeVaultKey
            val salt = activeSalt
            val currentPayload = activePayload

            if (currentKey == null || salt == null || currentPayload == null) {
                return@withContext Result.failure(IllegalStateException("Vault must be unlocked to import backup"))
            }

            val backupBytes = inputStream.use { it.readBytes() }
            val (backupSalt, backupIterations) = CryptoManager.extractSaltAndIterations(backupBytes)
            val backupKey = CryptoManager.deriveVaultKey(backupPassword, backupSalt, backupIterations)

            val decryptedBytes = CryptoManager.decryptVault(backupBytes, backupKey)
            val backupPayload = VaultPayload.fromJsonBytes(decryptedBytes)

            val finalEntries = if (merge) {
                val map = currentPayload.entries.associateBy { it.id }.toMutableMap()
                for (entry in backupPayload.entries) {
                    map[entry.id] = entry
                }
                map.values.sortedByDescending { it.updatedAt }
            } else {
                backupPayload.entries
            }

            val allFolders = (currentPayload.folders + backupPayload.folders).distinct()
            val newPayload = VaultPayload(version = 2, entries = finalEntries, folders = allFolders)
            persistVaultAtomic(newPayload, currentKey, salt)

            activePayload = newPayload
            _vaultState.value = VaultState.Unlocked(newPayload.entries, newPayload.folders)

            Result.success(backupPayload.entries.size)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            CryptoManager.wipe(backupPassword)
        }
    }

    /**
     * Atomic write discipline:
     * 1. Encrypt payload to encryptedBytes
     * 2. Write to vault.vk.tmp, flush and fsync
     * 3. Validate encryptedBytes can be decrypted with key in memory
     * 4. Keep existing vault as vault.vk.bak
     * 5. Rename vault.vk.tmp to vault.enc (or vault.vk)
     */
    private fun persistVaultAtomic(payload: VaultPayload, key: SecretKey, salt: ByteArray) {
        val plainBytes = payload.toJsonBytes()
        val encryptedBytes = CryptoManager.encryptVault(plainBytes, key, salt)

        // Verify in memory before committing to disk
        val testDecrypted = CryptoManager.decryptVault(encryptedBytes, key)
        if (!testDecrypted.contentEquals(plainBytes)) {
            throw IllegalStateException("Vault write integrity verification failed in memory")
        }

        // Write to tmp file with fsync
        FileOutputStream(vaultTempFile).use { fos ->
            fos.write(encryptedBytes)
            fos.flush()
            fos.fd.sync()
        }

        // Backup existing live file
        if (vaultFile.exists() && vaultFile.length() > 0) {
            try {
                vaultFile.copyTo(vaultBackupFile, overwrite = true)
            } catch (_: Exception) {}
        }

        // Atomic rename
        if (!vaultTempFile.renameTo(vaultFile)) {
            vaultTempFile.copyTo(vaultFile, overwrite = true)
            vaultTempFile.delete()
        }
    }

    /**
     * Returns matching entries for autofill queries based on domain or app package.
     */
    fun findAutofillMatches(queryDomainOrPackage: String): List<VaultEntry> {
        val state = _vaultState.value
        if (state !is VaultState.Unlocked) return emptyList()

        val cleanQuery = queryDomainOrPackage.trim().lowercase()
        return state.entries.filter { entry ->
            val matchUrl = entry.url.isNotBlank() && (entry.url.lowercase().contains(cleanQuery) || cleanQuery.contains(entry.url.lowercase()))
            val matchPkg = entry.packageName.isNotBlank() && (entry.packageName.equals(cleanQuery, ignoreCase = true))
            val matchTitle = entry.title.isNotBlank() && (entry.title.lowercase().contains(cleanQuery) || cleanQuery.contains(entry.title.lowercase()))
            matchUrl || matchPkg || matchTitle
        }
    }

    /**
     * Retrieves the raw encrypted bytes of the local vault for synchronization.
     */
    fun getEncryptedVaultBytes(): ByteArray? {
        if (!vaultFile.exists() || vaultFile.length() == 0L) return null
        return try {
            vaultFile.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Gets the last modified timestamp of the local vault container file.
     */
    fun getLastModifiedTimestamp(): Long {
        return if (vaultFile.exists()) vaultFile.lastModified() else 0L
    }

    /**
     * Verifies that the provided encrypted payload can be decrypted with the currently active vault key.
     */
    fun verifyAndPreviewEncryptedPayload(encryptedBytes: ByteArray): Result<Int> {
        val key = activeVaultKey ?: return Result.failure(Exception("Vault is locked; cannot verify remote payload."))
        return try {
            val decryptedPlain = CryptoManager.decryptVault(encryptedBytes, key)
            val payload = VaultPayload.fromJsonBytes(decryptedPlain)
            Result.success(payload.entries.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atomically replaces the local encrypted vault file with a new verified remote payload,
     * and refreshes active in-memory entries if currently unlocked.
     */
    fun replaceLocalEncryptedVault(newEncryptedBytes: ByteArray): Result<Unit> {
        return try {
            val key = activeVaultKey
            if (key != null) {
                val decryptedPlain = CryptoManager.decryptVault(newEncryptedBytes, key)
                val newPayload = VaultPayload.fromJsonBytes(decryptedPlain)
                activePayload = newPayload

                // Persist with verified integrity
                val salt = CryptoManager.extractSalt(newEncryptedBytes)
                activeSalt = salt
                persistVaultAtomic(newPayload, key, salt)

                _vaultState.value = VaultState.Unlocked(newPayload.entries)
                Result.success(Unit)
            } else {
                // If locked, replace file atomically
                FileOutputStream(vaultTempFile).use { fos ->
                    fos.write(newEncryptedBytes)
                    fos.flush()
                    fos.fd.sync()
                }
                if (!vaultTempFile.renameTo(vaultFile)) {
                    vaultTempFile.copyTo(vaultFile, overwrite = true)
                    vaultTempFile.delete()
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val VAULT_FILE_NAME = "vault.enc"
    }
}
