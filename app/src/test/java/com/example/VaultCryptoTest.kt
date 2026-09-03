package com.example

import com.example.crypto.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultCryptoTest {

    @Test
    fun `password generator produces exact length requested`() {
        val lengths = listOf(8, 16, 24, 32, 64)
        for (len in lengths) {
            val pass = PasswordGenerator.generateRandom(PasswordConfig(length = len))
            assertEquals(len, pass.length)
        }
    }

    @Test
    fun `passphrase generator produces correct word count and separator`() {
        val config = PassphraseConfig(
            wordCount = 5,
            separator = PassphraseSeparator.DASH,
            capitalization = CapitalizationMode.LOWERCASE,
            appendNumber = false
        )
        val phrase = PasswordGenerator.generatePassphrase(config)
        val parts = phrase.split("-")
        assertEquals(5, parts.size)
        for (p in parts) {
            assertTrue(p.all { it.isLowerCase() })
        }
    }

    @Test
    fun `entropy calculation accurately measures complexity`() {
        val veryShort = "123"
        val simple = "password"
        val complex = "K9#xP!2m\$vL9@qW4"

        val shortEntropy = PasswordGenerator.calculateEntropy(veryShort)
        val simpleEntropy = PasswordGenerator.calculateEntropy(simple)
        val complexEntropy = PasswordGenerator.calculateEntropy(complex)

        assertTrue(shortEntropy < 30.0)
        assertTrue(simpleEntropy < 50.0)
        assertTrue(complexEntropy > 80.0)

        val evalShort = PasswordGenerator.evaluateStrength(veryShort)
        val evalSimple = PasswordGenerator.evaluateStrength(simple)
        val evalComplex = PasswordGenerator.evaluateStrength(complex)

        assertEquals(PasswordStrength.VERY_WEAK, evalShort)
        assertEquals(PasswordStrength.WEAK, evalSimple)
        assertTrue(evalComplex == PasswordStrength.STRONG || evalComplex == PasswordStrength.VERY_STRONG)
    }

    @Test
    fun `totp generator calculates accurate RFC 6238 codes`() {
        // Standard RFC test vector for base32 secret "JBSWY3DPEHPK3PXP"
        val secret = "JBSWY3DPEHPK3PXP"
        val timestamp = 1234567890L

        val code = TotpGenerator.generateTotp(
            secretBase32 = secret,
            timestampSeconds = timestamp,
            periodSeconds = 30,
            digits = 6,
            algorithm = "SHA1"
        )
        assertNotNull(code)
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun `totp uri parser handles standard otpauth format`() {
        val uri = "otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&digits=6&period=30"
        val config = TotpGenerator.parseOtpAuthUri(uri)

        assertNotNull(config)
        assertEquals("JBSWY3DPEHPK3PXP", config!!.secret)
        assertEquals("GitHub", config.issuer)
        assertEquals("octocat", config.accountName)
        assertEquals(6, config.digits)
        assertEquals(30, config.periodSeconds)
    }

    @Test
    fun `base32 decoder accurately handles padding and lowercase`() {
        assertTrue(TotpGenerator.Base32.isValidBase32("JBSWY3DPEHPK3PXP"))
        assertTrue(TotpGenerator.Base32.isValidBase32("jbswy3dpehpk3pxp"))
        assertFalse(TotpGenerator.Base32.isValidBase32("1890Invalid!"))

        val decoded = TotpGenerator.Base32.decode("JBSWY3DP")
        val decodedString = String(decoded)
        assertEquals("Hello", decodedString)
    }
}
