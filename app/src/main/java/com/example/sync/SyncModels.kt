package com.example.sync

import java.io.Serializable

enum class SyncType {
    DISABLED,
    LOCAL_FOLDER,
    WEBDAV
}

sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    data class Success(val lastSyncTime: Long) : SyncStatus()
    data class Conflict(
        val localModifiedTime: Long,
        val remoteModifiedTime: Long,
        val remotePayloadBytes: ByteArray
    ) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

data class WebDavConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "", // Stored encrypted in preferences
    val remotePath: String = "vaultkeep_backup.vkeep"
) : Serializable

data class LocalFolderConfig(
    val treeUriString: String = "",
    val fileName: String = "vaultkeep_backup.vkeep"
) : Serializable
