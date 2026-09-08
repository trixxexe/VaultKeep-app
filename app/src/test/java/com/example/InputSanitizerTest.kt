package com.example.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputSanitizerTest {

    @Test
    fun testControlCharactersRemoved() {
        val input = "Hello\u0000World\u0007!"
        val result = InputSanitizer.sanitizeText(input, 100)
        assertEquals("HelloWorld!", result)
    }

    @Test
    fun testMaxLengthTruncation() {
        val input = "a".repeat(200)
        val result = InputSanitizer.sanitizeSingleLine(input, 50)
        assertEquals(50, result.length)
    }

    @Test
    fun testDangerousUrlSchemesStripped() {
        val input = "javascript:alert('xss')"
        val result = InputSanitizer.sanitizeUrl(input)
        assertEquals("", result)
    }

    @Test
    fun testValidUrlPreserved() {
        val input = "https://example.com/login"
        val result = InputSanitizer.sanitizeUrl(input)
        assertEquals("https://example.com/login", result)
    }

    @Test
    fun testSearchQuerySanitization() {
        val query = " <script>alert(1)</script> github "
        val result = InputSanitizer.sanitizeSearchQuery(query)
        assertEquals("alert(1) github", result)
    }
}
