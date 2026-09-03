package com.example.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Sync client using Android's Storage Access Framework (SAF) tree URIs.
 * Allows the user to store the encrypted container file in any synced folder (e.g. Syncthing, Nextcloud folder, USB).
 */
class LocalFolderSyncClient(
    private val context: Context,
    private val config: LocalFolderConfig
) {
    suspend fun readRemoteEncryptedVault(): Result<Pair<ByteArray, Long>> = withContext(Dispatchers.IO) {
        try {
            val treeUri = Uri.parse(config.treeUriString)
            val dir = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.failure(Exception("Cannot access selected folder"))

            val file = dir.findFile(config.fileName)
                ?: return@withContext Result.failure(NoSuchFileException(java.io.File(config.fileName)))

            val lastModified = file.lastModified()
            val byteStream = ByteArrayOutputStream()
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                input.copyTo(byteStream)
            } ?: return@withContext Result.failure(Exception("Cannot open remote file for reading"))

            Result.success(Pair(byteStream.toByteArray(), lastModified))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeRemoteEncryptedVault(data: ByteArray): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val treeUri = Uri.parse(config.treeUriString)
            val dir = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.failure(Exception("Cannot access selected folder"))

            // Find existing file or create new
            var file = dir.findFile(config.fileName)
            if (file == null) {
                file = dir.createFile("application/octet-stream", config.fileName)
            }

            if (file == null) {
                return@withContext Result.failure(Exception("Failed to create file in selected folder"))
            }

            // Atomic write
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { output ->
                output.write(data)
                output.flush()
            } ?: return@withContext Result.failure(Exception("Cannot open file for writing"))

            Result.success(System.currentTimeMillis())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
