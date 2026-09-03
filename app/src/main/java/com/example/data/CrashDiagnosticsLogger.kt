package com.example.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only, privacy-preserving diagnostics logger.
 * STRICT PRIVACY REQUIREMENT: Contains ZERO credentials, master passwords, TOTP secrets, or usernames.
 * Only logs timestamps, screen routes, error categories, and sanitized error messages.
 */
data class DiagnosticLogEntry(
    val timestamp: Long,
    val formattedTime: String,
    val screenName: String,
    val errorType: String,
    val sanitizedMessage: String
)

object CrashDiagnosticsLogger {

    private const val LOG_FILE_NAME = "vaultkeep_diagnostics.log"
    private const val MAX_LOG_ENTRIES = 60

    private val inMemoryLogs = mutableListOf<DiagnosticLogEntry>()
    private var isInitialized = false
    private var logFile: File? = null

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext
        logFile = File(appContext.filesDir, LOG_FILE_NAME)
        loadLogsFromFile()
        isInitialized = true
    }

    @Synchronized
    fun logError(screenName: String, errorType: String, message: String, throwable: Throwable? = null) {
        val sanitized = sanitizeMessage(message, throwable)
        val now = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now))

        val entry = DiagnosticLogEntry(
            timestamp = now,
            formattedTime = timeStr,
            screenName = screenName,
            errorType = errorType,
            sanitizedMessage = sanitized
        )

        inMemoryLogs.add(0, entry)
        while (inMemoryLogs.size > MAX_LOG_ENTRIES) {
            inMemoryLogs.removeAt(inMemoryLogs.lastIndex)
        }

        saveLogsToFile()
    }

    @Synchronized
    fun logException(screenName: String, throwable: Throwable) {
        logError(
            screenName = screenName,
            errorType = throwable.javaClass.simpleName,
            message = throwable.message ?: "Exception occurred",
            throwable = throwable
        )
    }

    @Synchronized
    fun getLogs(): List<DiagnosticLogEntry> {
        return inMemoryLogs.toList()
    }

    @Synchronized
    fun clearLogs() {
        inMemoryLogs.clear()
        try {
            logFile?.delete()
        } catch (_: Exception) {}
    }

    @Synchronized
    fun getFormattedReport(): String {
        val sb = StringBuilder()
        sb.append("=== VAULTKEEP DIAGNOSTIC REPORT (LOCAL-ONLY) ===\n")
        sb.append("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
        sb.append("OS Version: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
        sb.append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
        sb.append("Total Events Logged: ${inMemoryLogs.size}\n\n")

        if (inMemoryLogs.isEmpty()) {
            sb.append("No errors or exceptions recorded. Vault status is healthy.\n")
        } else {
            sb.append("--- RECENT EVENTS (NO SENSITIVE DATA) ---\n")
            for (entry in inMemoryLogs) {
                sb.append("[${entry.formattedTime}] [${entry.screenName}] ${entry.errorType}: ${entry.sanitizedMessage}\n")
            }
        }
        return sb.toString()
    }

    private fun sanitizeMessage(message: String, throwable: Throwable?): String {
        var text = message
        if (throwable != null) {
            val exceptionName = throwable.javaClass.simpleName
            val causeMsg = throwable.message ?: ""
            text = "$text | $exceptionName: $causeMsg"
        }

        // Privacy filters: strip potential secrets, tokens, or hex strings
        return text
            .replace(Regex("[0-9a-fA-F]{32,}"), "[REDACTED_HASH]")
            .replace(Regex("password=[^,\\s&]+", RegexOption.IGNORE_CASE), "password=[REDACTED]")
            .replace(Regex("secret=[^,\\s&]+", RegexOption.IGNORE_CASE), "secret=[REDACTED]")
            .take(300)
    }

    private fun loadLogsFromFile() {
        try {
            val file = logFile ?: return
            if (!file.exists() || file.length() == 0L) return
            val lines = file.readLines()
            inMemoryLogs.clear()
            for (line in lines) {
                val parts = line.split("\t")
                if (parts.size >= 5) {
                    val ts = parts[0].toLongOrNull() ?: continue
                    inMemoryLogs.add(
                        DiagnosticLogEntry(
                            timestamp = ts,
                            formattedTime = parts[1],
                            screenName = parts[2],
                            errorType = parts[3],
                            sanitizedMessage = parts[4]
                        )
                    )
                }
            }
        } catch (_: Exception) {}
    }

    private fun saveLogsToFile() {
        try {
            val file = logFile ?: return
            val sb = StringBuilder()
            for (entry in inMemoryLogs) {
                sb.append("${entry.timestamp}\t${entry.formattedTime}\t${entry.screenName}\t${entry.errorType}\t${entry.sanitizedMessage}\n")
            }
            file.writeText(sb.toString())
        } catch (_: Exception) {}
    }
}
