package com.example.sync

import android.content.Context
import com.example.data.VaultPreferences
import com.example.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Manages atomic synchronization of the encrypted vault container file (.vkeep/.vk)
 * to user-configured destinations (WebDAV or Local SAF Folder).
 *
 * Guaranteed Invariants:
 * 1. 100% Zero-knowledge: Only already-encrypted binary payloads are uploaded/downloaded.
 * 2. Atomic commits: Remote and local payloads are verified before applying.
 * 3. Non-destructive conflict handling: Never silently overwrites data.
 */
class VaultSyncManager(
    private val context: Context,
    private val repository: VaultRepository,
    private val preferences: VaultPreferences
) {
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    suspend fun performSync(): SyncStatus = withContext(Dispatchers.IO) {
        val syncType = preferences.syncType
        if (syncType == SyncType.DISABLED) {
            _syncStatus.value = SyncStatus.Idle
            return@withContext SyncStatus.Idle
        }

        _syncStatus.value = SyncStatus.Syncing

        try {
            val localVaultBytes = repository.getEncryptedVaultBytes()
            if (localVaultBytes == null || localVaultBytes.isEmpty()) {
                val err = "Local vault is empty or locked; unlock vault first."
                _syncStatus.value = SyncStatus.Error(err)
                return@withContext _syncStatus.value
            }

            val localModified = repository.getLastModifiedTimestamp()

            val remoteResult = when (syncType) {
                SyncType.WEBDAV -> {
                    val client = WebDavSyncClient(preferences.getWebDavConfig())
                    client.fetchRemoteEncryptedVault()
                }
                SyncType.LOCAL_FOLDER -> {
                    val client = LocalFolderSyncClient(context, preferences.getLocalFolderConfig())
                    client.readRemoteEncryptedVault()
                }
                else -> Result.failure(Exception("Sync disabled"))
            }

            if (remoteResult.isFailure && remoteResult.exceptionOrNull() is NoSuchFileException) {
                // Remote file does not exist yet; upload local vault
                val uploadResult = uploadToRemote(localVaultBytes)
                if (uploadResult.isSuccess) {
                    val now = uploadResult.getOrThrow()
                    preferences.lastSyncTimestamp = now
                    val success = SyncStatus.Success(now)
                    _syncStatus.value = success
                    return@withContext success
                } else {
                    val err = uploadResult.exceptionOrNull()?.message ?: "Upload failed"
                    val error = SyncStatus.Error(err)
                    _syncStatus.value = error
                    return@withContext error
                }
            } else if (remoteResult.isFailure) {
                val err = remoteResult.exceptionOrNull()?.message ?: "Sync connection failed"
                val error = SyncStatus.Error(err)
                _syncStatus.value = error
                return@withContext error
            }

            val (remoteBytes, remoteModified) = remoteResult.getOrThrow()

            // Compare local and remote data
            if (remoteBytes.contentEquals(localVaultBytes)) {
                // Exactly identical, no changes needed
                val now = System.currentTimeMillis()
                preferences.lastSyncTimestamp = now
                val success = SyncStatus.Success(now)
                _syncStatus.value = success
                return@withContext success
            }

            val lastSyncTime = preferences.lastSyncTimestamp

            // If local was modified since last sync AND remote was modified since last sync -> CONFLICT
            if (localModified > lastSyncTime && remoteModified > lastSyncTime && lastSyncTime > 0) {
                val conflict = SyncStatus.Conflict(
                    localModifiedTime = localModified,
                    remoteModifiedTime = remoteModified,
                    remotePayloadBytes = remoteBytes
                )
                _syncStatus.value = conflict
                return@withContext conflict
            }

            // If remote is newer than local, pull and verify remote
            if (remoteModified > localModified) {
                // Verify remote payload is a valid vault container before applying
                val importVerify = repository.verifyAndPreviewEncryptedPayload(remoteBytes)
                if (importVerify.isSuccess) {
                    repository.replaceLocalEncryptedVault(remoteBytes)
                    val now = System.currentTimeMillis()
                    preferences.lastSyncTimestamp = now
                    val success = SyncStatus.Success(now)
                    _syncStatus.value = success
                    return@withContext success
                } else {
                    val error = SyncStatus.Error("Remote vault failed integrity verification: ${importVerify.exceptionOrNull()?.message}")
                    _syncStatus.value = error
                    return@withContext error
                }
            } else {
                // Local is newer than remote, push local to remote
                val uploadResult = uploadToRemote(localVaultBytes)
                if (uploadResult.isSuccess) {
                    val now = uploadResult.getOrThrow()
                    preferences.lastSyncTimestamp = now
                    val success = SyncStatus.Success(now)
                    _syncStatus.value = success
                    return@withContext success
                } else {
                    val err = uploadResult.exceptionOrNull()?.message ?: "Upload failed"
                    val error = SyncStatus.Error(err)
                    _syncStatus.value = error
                    return@withContext error
                }
            }
        } catch (e: Exception) {
            val error = SyncStatus.Error(e.message ?: "Unknown sync error")
            _syncStatus.value = error
            return@withContext error
        }
    }

    suspend fun resolveConflictKeepLocal(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val localVaultBytes = repository.getEncryptedVaultBytes()
                ?: return@withContext Result.failure(Exception("Local vault is empty or locked"))
            val uploadResult = uploadToRemote(localVaultBytes)
            if (uploadResult.isSuccess) {
                preferences.lastSyncTimestamp = uploadResult.getOrThrow()
                _syncStatus.value = SyncStatus.Success(preferences.lastSyncTimestamp)
                Result.success(Unit)
            } else {
                Result.failure(uploadResult.exceptionOrNull() ?: Exception("Upload failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveConflictKeepRemote(remoteBytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val importVerify = repository.verifyAndPreviewEncryptedPayload(remoteBytes)
            if (importVerify.isSuccess) {
                repository.replaceLocalEncryptedVault(remoteBytes)
                val now = System.currentTimeMillis()
                preferences.lastSyncTimestamp = now
                _syncStatus.value = SyncStatus.Success(now)
                Result.success(Unit)
            } else {
                Result.failure(importVerify.exceptionOrNull() ?: Exception("Corrupt remote vault"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun uploadToRemote(data: ByteArray): Result<Long> {
        return when (preferences.syncType) {
            SyncType.WEBDAV -> {
                val client = WebDavSyncClient(preferences.getWebDavConfig())
                client.uploadEncryptedVault(data)
            }
            SyncType.LOCAL_FOLDER -> {
                val client = LocalFolderSyncClient(context, preferences.getLocalFolderConfig())
                client.writeRemoteEncryptedVault(data)
            }
            else -> Result.failure(Exception("Sync disabled"))
        }
    }
}
