package com.example.data.importers

import com.example.data.VaultEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

enum class ImportFormat(val displayName: String) {
    AUTO_DETECT("Auto Detect"),
    BITWARDEN_JSON("Bitwarden (JSON)"),
    KEEPASS_XML("KeePass (XML)"),
    KEEPASS_CSV("KeePass (CSV)"),
    ONE_PASSWORD_CSV("1Password (CSV)"),
    BROWSER_CSV("Chrome / Firefox / Brave (CSV)"),
    GENERIC_CSV("Custom CSV (Field Mapping)")
}

data class ImportCandidate(
    val entry: VaultEntry,
    val isDuplicate: Boolean = false,
    val isSelected: Boolean = true,
    val sourceFormat: String = ""
)

data class ImportResult(
    val candidates: List<ImportCandidate>,
    val detectedFormat: ImportFormat,
    val duplicateCount: Int,
    val totalCount: Int
)

data class CsvColumnMapping(
    val titleIndex: Int = -1,
    val usernameIndex: Int = -1,
    val passwordIndex: Int = -1,
    val urlIndex: Int = -1,
    val notesIndex: Int = -1,
    val folderIndex: Int = -1,
    val totpIndex: Int = -1
)

object VaultImporter {

    /**
     * Parses an input stream purely in volatile memory.
     * Guaranteed: No imported plaintext touches disk.
     */
    fun parseStream(
        inputStream: InputStream,
        existingEntries: List<VaultEntry>,
        forcedFormat: ImportFormat = ImportFormat.AUTO_DETECT,
        customMapping: CsvColumnMapping? = null
    ): ImportResult {
        val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parseString(content, existingEntries, forcedFormat, customMapping)
    }

    fun parseString(
        content: String,
        existingEntries: List<VaultEntry>,
        forcedFormat: ImportFormat = ImportFormat.AUTO_DETECT,
        customMapping: CsvColumnMapping? = null
    ): ImportResult {
        val trimmed = content.trim()
        val detected = if (forcedFormat == ImportFormat.AUTO_DETECT) {
            detectFormat(trimmed)
        } else {
            forcedFormat
        }

        val rawEntries = when (detected) {
            ImportFormat.BITWARDEN_JSON -> parseBitwardenJson(trimmed)
            ImportFormat.KEEPASS_XML -> parseKeePassXml(trimmed)
            ImportFormat.KEEPASS_CSV -> parseKeePassCsv(trimmed)
            ImportFormat.ONE_PASSWORD_CSV -> parse1PasswordCsv(trimmed)
            ImportFormat.BROWSER_CSV -> parseBrowserCsv(trimmed)
            ImportFormat.GENERIC_CSV -> parseGenericCsv(trimmed, customMapping ?: detectCsvMapping(trimmed))
            ImportFormat.AUTO_DETECT -> parseGenericCsv(trimmed, detectCsvMapping(trimmed))
        }

        // Duplicate detection: match on (title or URL) AND (username)
        val existingSignatures = existingEntries.map { createSignature(it.title, it.url, it.username) }.toSet()

        val candidates = rawEntries.map { entry ->
            val signature = createSignature(entry.title, entry.url, entry.username)
            val isDup = existingSignatures.contains(signature)
            ImportCandidate(
                entry = entry,
                isDuplicate = isDup,
                isSelected = !isDup, // Uncheck duplicates by default
                sourceFormat = detected.displayName
            )
        }

        val duplicateCount = candidates.count { it.isDuplicate }

        return ImportResult(
            candidates = candidates,
            detectedFormat = detected,
            duplicateCount = duplicateCount,
            totalCount = candidates.size
        )
    }

    private fun createSignature(title: String, url: String, username: String): String {
        val cleanDomain = extractDomainOrTitle(if (url.isNotBlank()) url else title)
        val cleanUser = username.trim().lowercase()
        return "$cleanDomain::$cleanUser"
    }

    private fun extractDomainOrTitle(input: String): String {
        val clean = input.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
        return clean.split("/").firstOrNull() ?: clean
    }

    private fun detectFormat(content: String): ImportFormat {
        if (content.startsWith("{") && content.contains("\"items\"") && content.contains("\"login\"")) {
            return ImportFormat.BITWARDEN_JSON
        }
        if (content.startsWith("<?xml") || (content.contains("<KeePassFile>") || content.contains("<pwentry>"))) {
            return ImportFormat.KEEPASS_XML
        }

        val firstLine = content.lines().firstOrNull { it.isNotBlank() } ?: ""
        val lowerFirstLine = firstLine.lowercase()

        if (lowerFirstLine.contains("group") && lowerFirstLine.contains("title") && lowerFirstLine.contains("password")) {
            return ImportFormat.KEEPASS_CSV
        }
        if (lowerFirstLine.contains("title") && (lowerFirstLine.contains("url") || lowerFirstLine.contains("website")) && lowerFirstLine.contains("password")) {
            if (lowerFirstLine.contains("favorite") || lowerFirstLine.contains("type")) {
                return ImportFormat.ONE_PASSWORD_CSV
            }
            return ImportFormat.BROWSER_CSV
        }
        if (lowerFirstLine.contains("name") && lowerFirstLine.contains("url") && lowerFirstLine.contains("password")) {
            return ImportFormat.BROWSER_CSV
        }

        return ImportFormat.GENERIC_CSV
    }

    // ---------------------------------------------------------------------------------------------
    // Format Parsers
    // ---------------------------------------------------------------------------------------------

    private fun parseBitwardenJson(jsonString: String): List<VaultEntry> {
        val list = mutableListOf<VaultEntry>()
        try {
            val root = JSONObject(jsonString)
            val foldersMap = mutableMapOf<String, String>()

            val foldersArray = root.optJSONArray("folders")
            if (foldersArray != null) {
                for (i in 0 until foldersArray.length()) {
                    val f = foldersArray.optJSONObject(i)
                    if (f != null) {
                        foldersMap[f.optString("id")] = f.optString("name")
                    }
                }
            }

            val items = root.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val name = item.optString("name", "Untitled")
                val notes = item.optString("notes", "")
                val folderId = item.optString("folderId", "")
                val folderName = foldersMap[folderId] ?: ""
                val isFavorite = item.optBoolean("favorite", false)

                val login = item.optJSONObject("login")
                var username = ""
                var password = ""
                var totp = ""
                var url = ""

                if (login != null) {
                    username = login.optString("username", "")
                    password = login.optString("password", "")
                    totp = login.optString("totp", "")

                    val urisArray = login.optJSONArray("uris")
                    if (urisArray != null && urisArray.length() > 0) {
                        url = urisArray.optJSONObject(0)?.optString("uri", "") ?: ""
                    }
                }

                if (name.isNotBlank() || username.isNotBlank() || password.isNotBlank()) {
                    list.add(
                        VaultEntry(
                            id = UUID.randomUUID().toString(),
                            title = name,
                            username = username,
                            password = password,
                            notes = notes,
                            folder = folderName,
                            isFavorite = isFavorite,
                            totpSecret = totp,
                            url = url
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun parseKeePassXml(xml: String): List<VaultEntry> {
        val list = mutableListOf<VaultEntry>()
        // Lightweight XML regex tokenizer to avoid heavy XML pull parsers
        val entryRegex = Regex("<Entry>(.*?)</Entry>", RegexOption.DOT_MATCHES_ALL)
        val stringFieldRegex = Regex("<String>\\s*<Key>(.*?)</Key>\\s*<Value>(.*?)</Value>\\s*</String>", RegexOption.DOT_MATCHES_ALL)

        val matches = entryRegex.findAll(xml)
        for (match in matches) {
            val entryXml = match.groupValues[1]
            var title = ""
            var username = ""
            var password = ""
            var url = ""
            var notes = ""

            for (fieldMatch in stringFieldRegex.findAll(entryXml)) {
                val key = fieldMatch.groupValues[1].trim()
                val value = fieldMatch.groupValues[2].trim()
                when (key.lowercase()) {
                    "title" -> title = value
                    "username", "user name" -> username = value
                    "password" -> password = value
                    "url" -> url = value
                    "notes", "comment" -> notes = value
                }
            }

            if (title.isNotBlank() || username.isNotBlank() || password.isNotBlank()) {
                list.add(
                    VaultEntry(
                        id = UUID.randomUUID().toString(),
                        title = title.ifBlank { "KeePass Item" },
                        username = username,
                        password = password,
                        notes = notes,
                        url = url
                    )
                )
            }
        }
        return list
    }

    private fun parseKeePassCsv(csv: String): List<VaultEntry> {
        val rows = parseCsvRows(csv)
        if (rows.isEmpty()) return emptyList()

        val headers = rows.first().map { it.lowercase().trim() }
        val titleIdx = headers.indexOfFirst { it.contains("title") }
        val userIdx = headers.indexOfFirst { it.contains("user") }
        val passIdx = headers.indexOfFirst { it.contains("password") }
        val urlIdx = headers.indexOfFirst { it.contains("url") }
        val notesIdx = headers.indexOfFirst { it.contains("notes") || it.contains("comment") }
        val groupIdx = headers.indexOfFirst { it.contains("group") }

        val list = mutableListOf<VaultEntry>()
        for (row in rows.drop(1)) {
            val title = if (titleIdx in row.indices) row[titleIdx] else ""
            val user = if (userIdx in row.indices) row[userIdx] else ""
            val pass = if (passIdx in row.indices) row[passIdx] else ""
            val url = if (urlIdx in row.indices) row[urlIdx] else ""
            val notes = if (notesIdx in row.indices) row[notesIdx] else ""
            val group = if (groupIdx in row.indices) row[groupIdx] else ""

            if (title.isNotBlank() || user.isNotBlank() || pass.isNotBlank()) {
                list.add(
                    VaultEntry(
                        id = UUID.randomUUID().toString(),
                        title = title.ifBlank { "Untitled" },
                        username = user,
                        password = pass,
                        url = url,
                        notes = notes,
                        folder = group
                    )
                )
            }
        }
        return list
    }

    private fun parse1PasswordCsv(csv: String): List<VaultEntry> {
        val rows = parseCsvRows(csv)
        if (rows.isEmpty()) return emptyList()

        val headers = rows.first().map { it.lowercase().trim() }
        val titleIdx = headers.indexOfFirst { it == "title" || it.contains("title") }
        val userIdx = headers.indexOfFirst { it == "username" || it.contains("user") }
        val passIdx = headers.indexOfFirst { it == "password" || it.contains("pass") }
        val urlIdx = headers.indexOfFirst { it == "url" || it.contains("website") }
        val notesIdx = headers.indexOfFirst { it == "notes" || it.contains("notes") }
        val favIdx = headers.indexOfFirst { it.contains("favorite") }

        val list = mutableListOf<VaultEntry>()
        for (row in rows.drop(1)) {
            val title = if (titleIdx in row.indices) row[titleIdx] else ""
            val user = if (userIdx in row.indices) row[userIdx] else ""
            val pass = if (passIdx in row.indices) row[passIdx] else ""
            val url = if (urlIdx in row.indices) row[urlIdx] else ""
            val notes = if (notesIdx in row.indices) row[notesIdx] else ""
            val fav = if (favIdx in row.indices) row[favIdx].lowercase() in listOf("1", "true", "yes") else false

            if (title.isNotBlank() || user.isNotBlank() || pass.isNotBlank()) {
                list.add(
                    VaultEntry(
                        id = UUID.randomUUID().toString(),
                        title = title.ifBlank { "1Password Item" },
                        username = user,
                        password = pass,
                        url = url,
                        notes = notes,
                        isFavorite = fav
                    )
                )
            }
        }
        return list
    }

    private fun parseBrowserCsv(csv: String): List<VaultEntry> {
        val rows = parseCsvRows(csv)
        if (rows.isEmpty()) return emptyList()

        val headers = rows.first().map { it.lowercase().trim() }
        val nameIdx = headers.indexOfFirst { it == "name" || it == "title" }
        val urlIdx = headers.indexOfFirst { it == "url" || it.contains("url") }
        val userIdx = headers.indexOfFirst { it == "username" || it.contains("user") || it.contains("login") }
        val passIdx = headers.indexOfFirst { it == "password" || it.contains("pass") }
        val noteIdx = headers.indexOfFirst { it == "note" || it.contains("notes") }

        val list = mutableListOf<VaultEntry>()
        for (row in rows.drop(1)) {
            var title = if (nameIdx in row.indices) row[nameIdx] else ""
            val url = if (urlIdx in row.indices) row[urlIdx] else ""
            val user = if (userIdx in row.indices) row[userIdx] else ""
            val pass = if (passIdx in row.indices) row[passIdx] else ""
            val note = if (noteIdx in row.indices) row[noteIdx] else ""

            if (title.isBlank() && url.isNotBlank()) {
                title = extractDomainOrTitle(url)
            }

            if (title.isNotBlank() || user.isNotBlank() || pass.isNotBlank()) {
                list.add(
                    VaultEntry(
                        id = UUID.randomUUID().toString(),
                        title = title.ifBlank { "Browser Login" },
                        username = user,
                        password = pass,
                        url = url,
                        notes = note
                    )
                )
            }
        }
        return list
    }

    private fun parseGenericCsv(csv: String, mapping: CsvColumnMapping): List<VaultEntry> {
        val rows = parseCsvRows(csv)
        if (rows.isEmpty()) return emptyList()

        val list = mutableListOf<VaultEntry>()
        for (row in rows.drop(1)) {
            val title = if (mapping.titleIndex in row.indices) row[mapping.titleIndex] else ""
            val user = if (mapping.usernameIndex in row.indices) row[mapping.usernameIndex] else ""
            val pass = if (mapping.passwordIndex in row.indices) row[mapping.passwordIndex] else ""
            val url = if (mapping.urlIndex in row.indices) row[mapping.urlIndex] else ""
            val notes = if (mapping.notesIndex in row.indices) row[mapping.notesIndex] else ""
            val folder = if (mapping.folderIndex in row.indices) row[mapping.folderIndex] else ""
            val totp = if (mapping.totpIndex in row.indices) row[mapping.totpIndex] else ""

            if (title.isNotBlank() || user.isNotBlank() || pass.isNotBlank()) {
                list.add(
                    VaultEntry(
                        id = UUID.randomUUID().toString(),
                        title = title.ifBlank { url.ifBlank { "Entry" } },
                        username = user,
                        password = pass,
                        url = url,
                        notes = notes,
                        folder = folder,
                        totpSecret = totp
                    )
                )
            }
        }
        return list
    }

    fun detectCsvMapping(csv: String): CsvColumnMapping {
        val rows = parseCsvRows(csv)
        if (rows.isEmpty()) return CsvColumnMapping()

        val headers = rows.first().map { it.lowercase().trim() }
        return CsvColumnMapping(
            titleIndex = headers.indexOfFirst { it == "name" || it == "title" || it.contains("title") },
            usernameIndex = headers.indexOfFirst { it == "username" || it.contains("user") || it.contains("login") || it.contains("email") },
            passwordIndex = headers.indexOfFirst { it == "password" || it.contains("pass") || it.contains("secret") },
            urlIndex = headers.indexOfFirst { it == "url" || it.contains("url") || it.contains("website") || it.contains("domain") },
            notesIndex = headers.indexOfFirst { it == "notes" || it.contains("note") || it.contains("comment") },
            folderIndex = headers.indexOfFirst { it == "folder" || it.contains("folder") || it.contains("group") || it.contains("collection") },
            totpIndex = headers.indexOfFirst { it == "totp" || it.contains("totp") || it.contains("2fa") || it.contains("otp") }
        )
    }

    fun getCsvHeaders(csv: String): List<String> {
        val rows = parseCsvRows(csv)
        return rows.firstOrNull() ?: emptyList()
    }

    /**
     * Robust RFC 4180 CSV parser handling multiline values, escaped quotes, and comma delimiters.
     */
    fun parseCsvRows(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var insideQuotes = false
        var i = 0

        while (i < csv.length) {
            val c = csv[i]
            when {
                c == '"' -> {
                    if (insideQuotes && i + 1 < csv.length && csv[i + 1] == '"') {
                        currentField.append('"')
                        i++ // skip escaped quote
                    } else {
                        insideQuotes = !insideQuotes
                    }
                }
                c == ',' && !insideQuotes -> {
                    currentRow.add(currentField.toString().trim())
                    currentField.setLength(0)
                }
                (c == '\r' || c == '\n') && !insideQuotes -> {
                    if (c == '\r' && i + 1 < csv.length && csv[i + 1] == '\n') {
                        i++ // handle CRLF
                    }
                    currentRow.add(currentField.toString().trim())
                    currentField.setLength(0)
                    if (currentRow.any { it.isNotBlank() }) {
                        rows.add(currentRow.toList())
                    }
                    currentRow.clear()
                }
                else -> {
                    currentField.append(c)
                }
            }
            i++
        }

        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString().trim())
            if (currentRow.any { it.isNotBlank() }) {
                rows.add(currentRow.toList())
            }
        }

        return rows
    }
}
