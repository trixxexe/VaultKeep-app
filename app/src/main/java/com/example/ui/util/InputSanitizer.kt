package com.example.ui.util

import java.text.Normalizer
import java.util.Locale

/**
 * Robust, structured input sanitizer and validator utility for VaultKeep.
 * Mitigates Cross-Site Scripting (XSS), control character injection, header injection,
 * and input field overflow issues across all forms, dialogs, and search bars.
 */
object InputSanitizer {

    const val MAX_TITLE_LENGTH = 120
    const val MAX_USERNAME_LENGTH = 150
    const val MAX_PASSWORD_LENGTH = 500
    const val MAX_URL_LENGTH = 2048
    const val MAX_NOTE_LENGTH = 50000
    const val MAX_FOLDER_LENGTH = 60
    const val MAX_TAG_LENGTH = 40
    const val MAX_CUSTOM_FIELD_LABEL_LENGTH = 80
    const val MAX_CUSTOM_FIELD_VALUE_LENGTH = 5000
    const val MAX_SEARCH_QUERY_LENGTH = 100

    private val CONTROL_CHARS_REGEX = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]")
    private val HTML_TAG_REGEX = Regex("<[^>]*>")
    private val SCRIPT_SCHEME_REGEX = Regex("^(javascript|vbscript|data):", RegexOption.IGNORE_CASE)

    /**
     * Removes dangerous ASCII control characters and normalizes Unicode characters.
     */
    fun sanitizeText(input: String, maxLength: Int = 1000): String {
        if (input.isEmpty()) return ""
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFC)
        val cleaned = CONTROL_CHARS_REGEX.replace(normalized, "")
        return cleaned.take(maxLength)
    }

    /**
     * Live input filtering for single-line text fields (allows spaces during typing).
     */
    fun filterLiveSingleLine(input: String, maxLength: Int): String {
        val cleaned = sanitizeText(input, maxLength)
        return cleaned.replace("\r", "").replace("\n", "")
    }

    /**
     * Live input filtering for search queries (allows spaces during typing).
     */
    fun filterLiveSearchQuery(query: String): String {
        if (query.isEmpty()) return ""
        val cleaned = sanitizeText(query, MAX_SEARCH_QUERY_LENGTH)
        return HTML_TAG_REGEX.replace(cleaned, "")
    }

    /**
     * Sanitizes user input specifically intended for search fields (final clean).
     */
    fun sanitizeSearchQuery(query: String): String {
        if (query.isEmpty()) return ""
        val cleaned = filterLiveSearchQuery(query)
        return cleaned.trim()
    }

    /**
     * Sanitizes URL strings and prevents dangerous URI scheme execution (e.g. `javascript:`).
     */
    fun sanitizeUrl(urlStr: String): String {
        val trimmed = sanitizeText(urlStr.trim(), MAX_URL_LENGTH)
        if (trimmed.isEmpty()) return ""
        if (SCRIPT_SCHEME_REGEX.containsMatchIn(trimmed)) {
            return ""
        }
        return trimmed
    }

    /**
     * Sanitizes single-line fields such as titles, usernames, folders, and custom field labels (final clean on save).
     */
    fun sanitizeSingleLine(input: String, maxLength: Int): String {
        val cleaned = filterLiveSingleLine(input, maxLength)
        return cleaned.trim()
    }

    /**
     * Validates an input field value against a maximum length constraint.
     */
    fun validateLength(input: String, maxLength: Int, fieldName: String): String? {
        if (input.length > maxLength) {
            return "$fieldName must not exceed $maxLength characters."
        }
        return null
    }

    /**
     * Validates that a required input is not blank or whitespace.
     */
    fun validateRequired(input: String, fieldName: String): String? {
        if (input.isBlank()) {
            return "$fieldName is required."
        }
        return null
    }

    /**
     * Structured result for validating entry field inputs.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    /**
     * Validates a URL input to ensure it is reasonably formatted.
     */
    fun validateUrl(url: String): ValidationResult {
        if (url.isBlank()) return ValidationResult(isValid = true)
        val sanitized = sanitizeUrl(url)
        if (sanitized.isEmpty() && url.isNotBlank()) {
            return ValidationResult(isValid = false, errorMessage = "Invalid or unsafe URL scheme")
        }
        return ValidationResult(isValid = true)
    }
}
