package com.example.sync

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Lightweight, zero-dependency WebDAV client for syncing encrypted vault files.
 * Adheres strictly to FOSS / F-Droid compatibility (uses standard java.net).
 */
class WebDavSyncClient(private val config: WebDavConfig) {

    private fun getFullUrl(): URL {
        val base = config.serverUrl.trim().removeSuffix("/")
        val path = config.remotePath.trim().removePrefix("/")
        return URL("$base/$path")
    }

    private fun getAuthHeader(): String {
        val creds = "${config.username}:${config.password}"
        return "Basic " + Base64.encodeToString(creds.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = URL(config.serverUrl.trim())
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PROPFIND"
                setRequestProperty("Authorization", getAuthHeader())
                setRequestProperty("Depth", "0")
                connectTimeout = 10000
                readTimeout = 10000
            }
            val responseCode = conn.responseCode
            conn.disconnect()
            if (responseCode in 200..299 || responseCode == 404 || responseCode == 405) {
                Result.success(true)
            } else if (responseCode == 401 || responseCode == 403) {
                Result.failure(Exception("Authentication failed (HTTP $responseCode)"))
            } else {
                Result.failure(Exception("Server returned HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRemoteEncryptedVault(): Result<Pair<ByteArray, Long>> = withContext(Dispatchers.IO) {
        try {
            val url = getFullUrl()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", getAuthHeader())
                connectTimeout = 15000
                readTimeout = 15000
            }

            val responseCode = conn.responseCode
            if (responseCode == 404) {
                conn.disconnect()
                return@withContext Result.failure(NoSuchFileException(java.io.File(url.path)))
            }
            if (responseCode !in 200..299) {
                conn.disconnect()
                return@withContext Result.failure(Exception("Failed to fetch remote vault: HTTP $responseCode"))
            }

            val lastModified = conn.lastModified.takeIf { it > 0 } ?: System.currentTimeMillis()
            val outputStream = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                input.copyTo(outputStream)
            }
            conn.disconnect()

            Result.success(Pair(outputStream.toByteArray(), lastModified))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadEncryptedVault(data: ByteArray): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val url = getFullUrl()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                setRequestProperty("Authorization", getAuthHeader())
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("Content-Length", data.size.toString())
                connectTimeout = 15000
                readTimeout = 15000
            }

            conn.outputStream.use { output ->
                output.write(data)
                output.flush()
            }

            val responseCode = conn.responseCode
            conn.disconnect()

            if (responseCode in 200..299 || responseCode == 201 || responseCode == 204) {
                Result.success(System.currentTimeMillis())
            } else {
                Result.failure(Exception("Failed to upload vault: HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
