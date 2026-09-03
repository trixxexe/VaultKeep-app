package com.example.data

import com.example.crypto.PasswordGenerator
import com.example.crypto.PasswordStrength
import java.net.URI
import java.util.Locale

/**
 * Result of a 100% on-device local security audit over decrypted vault entries.
 * Never written unencrypted to disk.
 */
data class SecurityAuditReport(
    val scanTimestamp: Long = System.currentTimeMillis(),
    val totalEntries: Int,
    val strongPasswords: List<VaultEntry>,
    val weakPasswords: List<VaultEntry>,
    val compromisedPasswords: List<VaultEntry> = emptyList(),
    val breachMatchMap: Map<String, BreachMatch> = emptyMap(), // password -> breach details
    val reusedPasswordGroups: Map<String, List<VaultEntry>>, // password -> list of entries
    val duplicateEntryGroups: List<List<VaultEntry>>, // groups of duplicate domain + username
    val oldPasswords: List<VaultEntry>,
    val missingUsernames: List<VaultEntry>,
    val healthScorePercentage: Int
) {
    val reusedEntries: List<VaultEntry>
        get() = reusedPasswordGroups.values.flatten().distinctBy { it.id }

    val duplicateEntries: List<VaultEntry>
        get() = duplicateEntryGroups.flatten().distinctBy { it.id }

    val totalIssuesCount: Int
        get() = weakPasswords.size + compromisedPasswords.size + reusedEntries.size + duplicateEntries.size + oldPasswords.size + missingUsernames.size

    val isAllClear: Boolean
        get() = weakPasswords.isEmpty() && compromisedPasswords.isEmpty() && reusedPasswordGroups.isEmpty() && duplicateEntryGroups.isEmpty() && oldPasswords.isEmpty() && missingUsernames.isEmpty()
}

enum class SecurityIssueType(val title: String, val description: String) {
    COMPROMISED_PASSWORD("Breached Passwords", "Passwords found in 709,839 offline rockyou.txt & leak database records"),
    WEAK_PASSWORD("Weak Passwords", "Passwords with low entropy or weak complexity"),
    REUSED_PASSWORD("Reused Passwords", "Identical passwords shared across multiple accounts"),
    DUPLICATE_ENTRY("Duplicate Accounts", "Multiple entries saved for the same service and username"),
    OLD_PASSWORD("Old Passwords", "Passwords not updated in over 12 months"),
    MISSING_USERNAME("Missing Usernames", "Entries saved with a password but no login identifier")
}

object SecurityScanner {

    private const val DEFAULT_ENTROPY_THRESHOLD = 50.0

    private val COMMON_BREACHED_PASSWORDS = setOf(
        "123456", "password", "12345678", "qwerty", "123456789", "12345", "111111", "1234567",
        "dragon", "123123", "baseball", "football", "monkey", "letmein", "shadow", "master",
        "666666", "trustno1", "admin", "welcome", "iloveyou", "secret", "princess", "sunshine",
        "starwars", "passw0rd", "superman", "killer", "charlie", "jordan", "michael", "password1",
        "password123", "qwertyuiop", "adobe123", "admin123", "root", "toor", "pass1234",
        "default", "access", "test", "testing", "guest", "welcome1", "login", "changeme",
        "123321", "654321", "000000", "777777", "888888", "999999", "abc123", "myspace1"
    )

    /**
     * Executes a fast, purely local analysis across the in-memory vault entries.
     */
    fun scanVault(
        entries: List<VaultEntry>,
        breachMatches: Map<String, BreachMatch> = emptyMap(),
        oldThresholdMonths: Int = 12,
        minEntropyThreshold: Double = DEFAULT_ENTROPY_THRESHOLD,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): SecurityAuditReport {
        // Filter out deleted / trash items
        val activeEntries = entries.filterNot { it.isDeleted }

        if (activeEntries.isEmpty()) {
            return SecurityAuditReport(
                scanTimestamp = currentTimeMillis,
                totalEntries = 0,
                strongPasswords = emptyList(),
                weakPasswords = emptyList(),
                compromisedPasswords = emptyList(),
                breachMatchMap = emptyMap(),
                reusedPasswordGroups = emptyMap(),
                duplicateEntryGroups = emptyList(),
                oldPasswords = emptyList(),
                missingUsernames = emptyList(),
                healthScorePercentage = 100
            )
        }

        val strong = mutableListOf<VaultEntry>()
        val weak = mutableListOf<VaultEntry>()
        val compromised = mutableListOf<VaultEntry>()
        val old = mutableListOf<VaultEntry>()
        val missingUser = mutableListOf<VaultEntry>()

        val oldAgeMillis = oldThresholdMonths.toLong() * 30L * 24L * 60L * 60L * 1000L

        // Password reuse tracking
        val passwordToEntries = mutableMapOf<String, MutableList<VaultEntry>>()

        // Duplicate tracking: normalized identifier -> entries
        val domainUserMap = mutableMapOf<String, MutableList<VaultEntry>>()

        for (entry in activeEntries) {
            // Passkeys are modern FIDO2 unphishable public-key credentials
            if (entry.entryType == EntryType.PASSKEY) {
                strong.add(entry)
                continue
            }

            // Secure notes, payment cards, and identities do not use login passwords
            if (entry.entryType != EntryType.PASSWORD) {
                continue
            }

            val pass = entry.password

            // 1. Password Strength, Entropy & Breach check
            if (pass.isBlank()) {
                weak.add(entry)
            } else {
                val lowerPass = pass.lowercase(Locale.ROOT)
                val isBreached = breachMatches.containsKey(pass) ||
                        breachMatches.containsKey(lowerPass) ||
                        COMMON_BREACHED_PASSWORDS.contains(lowerPass)

                if (isBreached) {
                    compromised.add(entry)
                }

                val entropy = PasswordGenerator.calculateEntropy(pass)
                val strength = PasswordGenerator.evaluateStrength(pass)

                if (entropy < minEntropyThreshold || strength == PasswordStrength.VERY_WEAK || strength == PasswordStrength.WEAK) {
                    weak.add(entry)
                } else if (!isBreached) {
                    strong.add(entry)
                }

                // Track reuse
                passwordToEntries.getOrPut(pass) { mutableListOf() }.add(entry)
            }

            // 2. Old Passwords
            val entryTime = if (entry.updatedAt > 0) entry.updatedAt else entry.createdAt
            if (currentTimeMillis - entryTime >= oldAgeMillis) {
                old.add(entry)
            }

            // 3. Missing Username
            if (entry.username.trim().isBlank() && pass.isNotBlank()) {
                missingUser.add(entry)
            }

            // 4. Duplicate Identifier
            val domainKey = extractDomainKey(entry)
            val userKey = entry.username.trim().lowercase(Locale.ROOT)
            if (domainKey.isNotBlank() && userKey.isNotBlank()) {
                val dupKey = "$domainKey|||$userKey"
                domainUserMap.getOrPut(dupKey) { mutableListOf() }.add(entry)
            }
        }

        val reusedGroups = passwordToEntries.filter { it.value.size > 1 }
        val duplicateGroups = domainUserMap.values.filter { it.size > 1 }

        // Calculate a 0-100% health score
        val total = activeEntries.size
        var deductions = 0
        deductions += compromised.size * 30
        deductions += weak.size * 20
        deductions += (reusedGroups.values.sumOf { it.size }) * 15
        deductions += (duplicateGroups.sumOf { it.size }) * 10
        deductions += old.size * 5
        deductions += missingUser.size * 5

        val maxPossiblePenalty = total * 30
        val rawScore = if (maxPossiblePenalty > 0) {
            (100 - (deductions.toFloat() / maxPossiblePenalty * 100)).toInt().coerceIn(0, 100)
        } else {
            100
        }

        return SecurityAuditReport(
            scanTimestamp = currentTimeMillis,
            totalEntries = total,
            strongPasswords = strong,
            weakPasswords = weak,
            compromisedPasswords = compromised,
            breachMatchMap = breachMatches,
            reusedPasswordGroups = reusedGroups,
            duplicateEntryGroups = duplicateGroups,
            oldPasswords = old,
            missingUsernames = missingUser,
            healthScorePercentage = if (weak.isEmpty() && compromised.isEmpty() && reusedGroups.isEmpty() && duplicateGroups.isEmpty() && old.isEmpty() && missingUser.isEmpty()) 100 else rawScore
        )
    }

    /**
     * Extracts normalized domain/identifier for duplicate detection.
     */
    fun extractDomainKey(entry: VaultEntry): String {
        if (entry.url.isNotBlank()) {
            val url = entry.url.trim().lowercase(Locale.ROOT)
            val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
            return try {
                val host = URI(cleanUrl).host ?: cleanUrl
                host.removePrefix("www.")
            } catch (_: Exception) {
                cleanUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore("/")
            }
        }
        if (entry.packageName.isNotBlank()) {
            return entry.packageName.trim().lowercase(Locale.ROOT)
        }
        return entry.title.trim().lowercase(Locale.ROOT)
    }
}
