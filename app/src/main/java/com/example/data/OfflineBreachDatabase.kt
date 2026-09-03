package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

data class BreachDatabaseInfo(
    val filename: String,
    val displayName: String,
    val description: String,
    val count: Int,
    val fileSizeFormatted: String
)

data class BreachMatch(
    val password: String,
    val databaseFilename: String,
    val databaseDisplayName: String,
    val rank: Int
) {
    val summaryWarning: String
        get() = "Found in $databaseDisplayName (Rank #$rank)"
}

object OfflineBreachDatabase {

    private const val ASSET_FOLDER = "breach_databases"

    val catalog: List<BreachDatabaseInfo> = listOf(
        BreachDatabaseInfo(
            filename = "rockyou.txt",
            displayName = "RockYou Leak (Top 500k)",
            description = "The most notorious real-world leaked password database extracted from the RockYou breach, sorted by exposure frequency.",
            count = 500_000,
            fileSizeFormatted = "4.1 MB"
        ),
        BreachDatabaseInfo(
            filename = "100k-most-used-passwords-NCSC.txt",
            displayName = "UK NCSC Most Used",
            description = "Official threat intelligence list of real-world breached passwords published by the UK National Cyber Security Centre.",
            count = 99_840,
            fileSizeFormatted = "816 KB"
        ),
        BreachDatabaseInfo(
            filename = "xato-net-10-million-passwords-100000.txt",
            displayName = "Xato 10M Leaks (Top 100k)",
            description = "Top 100,000 most frequently observed passwords extracted from a global corpus of over 10 million compromised credentials.",
            count = 100_000,
            fileSizeFormatted = "764 KB"
        ),
        BreachDatabaseInfo(
            filename = "darkweb2017-top10000.txt",
            displayName = "Dark Web Dumps (Top 10k)",
            description = "High-velocity brute-force dictionary extracted from underground dark web credential dumps and botnet logs.",
            count = 9_999,
            fileSizeFormatted = "81 KB"
        )
    )

    val totalRecordsCount: Int = catalog.sumOf { it.count }

    // Fast in-memory cache for the top 20,000 most common passwords (instant synchronous lookup)
    @Volatile
    private var fastCache: Map<String, BreachMatch>? = null
    private val cacheLock = Any()

    private fun ensureFastCacheLoaded(context: Context) {
        if (fastCache != null) return
        synchronized(cacheLock) {
            if (fastCache != null) return
            val map = HashMap<String, BreachMatch>(25_000)
            try {
                context.assets.open("$ASSET_FOLDER/rockyou.txt").use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                        var rank = 1
                        var line = reader.readLine()
                        while (line != null && rank <= 20_000) {
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty()) {
                                val lower = trimmed.lowercase(Locale.ROOT)
                                if (!map.containsKey(lower)) {
                                    map[lower] = BreachMatch(
                                        password = trimmed,
                                        databaseFilename = "rockyou.txt",
                                        databaseDisplayName = "RockYou Leak",
                                        rank = rank
                                    )
                                }
                            }
                            rank++
                            line = reader.readLine()
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore if asset missing or read error
            }
            fastCache = map
        }
    }

    /**
     * Fast synchronous check using the top-tier in-memory cache.
     * Returns a match if within the top 20,000 common passwords.
     */
    fun checkFastSync(context: Context, password: String): BreachMatch? {
        if (password.isBlank()) return null
        ensureFastCacheLoaded(context)
        val lower = password.lowercase(Locale.ROOT)
        return fastCache?.get(lower)
    }

    /**
     * Complete streaming search across all 709,839 offline breach records.
     * Streaming avoids memory spikes and operates purely on-device.
     */
    suspend fun checkSinglePassword(context: Context, password: String): BreachMatch? = withContext(Dispatchers.IO) {
        if (password.isBlank()) return@withContext null

        // 1. Check fast cache first
        val cached = checkFastSync(context, password)
        if (cached != null) return@withContext cached

        val targetLower = password.lowercase(Locale.ROOT)

        for (db in catalog) {
            try {
                context.assets.open("$ASSET_FOLDER/${db.filename}").use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                        var rank = 1
                        var line: String? = reader.readLine()
                        while (line != null) {
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty() && trimmed.lowercase(Locale.ROOT) == targetLower) {
                                return@withContext BreachMatch(
                                    password = password,
                                    databaseFilename = db.filename,
                                    databaseDisplayName = db.displayName,
                                    rank = rank
                                )
                            }
                            rank++
                            line = reader.readLine()
                        }
                    }
                }
            } catch (_: Exception) {
                // Continue to next database
            }
        }
        null
    }

    /**
     * Batched single-pass stream scan for all passwords in the user's vault.
     * Searches all 709,839 records in a single streaming sweep without keeping file contents in RAM.
     */
    suspend fun checkVaultPasswords(
        context: Context,
        passwords: Set<String>
    ): Map<String, BreachMatch> = withContext(Dispatchers.IO) {
        val nonBlank = passwords.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return@withContext emptyMap()

        val results = mutableMapOf<String, BreachMatch>()
        // Map lowercase password to original passwords
        val remaining = mutableMapOf<String, MutableList<String>>()
        for (p in nonBlank) {
            remaining.getOrPut(p.lowercase(Locale.ROOT)) { mutableListOf() }.add(p)
        }

        // 1. Check fast cache
        ensureFastCacheLoaded(context)
        fastCache?.let { cache ->
            val it = remaining.iterator()
            while (it.hasNext()) {
                val (lower, originals) = it.next()
                val match = cache[lower]
                if (match != null) {
                    for (orig in originals) {
                        results[orig] = match.copy(password = orig)
                    }
                    it.remove()
                }
            }
        }

        if (remaining.isEmpty()) return@withContext results

        // 2. Stream through catalog files for any remaining
        for (db in catalog) {
            if (remaining.isEmpty()) break
            try {
                context.assets.open("$ASSET_FOLDER/${db.filename}").use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                        var rank = 1
                        var line: String? = reader.readLine()
                        while (line != null && remaining.isNotEmpty()) {
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty()) {
                                val lower = trimmed.lowercase(Locale.ROOT)
                                val matchedOriginals = remaining.remove(lower)
                                if (matchedOriginals != null) {
                                    val match = BreachMatch(
                                        password = trimmed,
                                        databaseFilename = db.filename,
                                        databaseDisplayName = db.displayName,
                                        rank = rank
                                    )
                                    for (orig in matchedOriginals) {
                                        results[orig] = match.copy(password = orig)
                                    }
                                }
                            }
                            rank++
                            line = reader.readLine()
                        }
                    }
                }
            } catch (_: Exception) {
                // Continue
            }
        }

        results
    }
}
