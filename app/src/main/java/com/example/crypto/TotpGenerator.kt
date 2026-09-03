package com.example.crypto

import android.net.Uri
import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * RFC 6238 / RFC 4226 TOTP (Time-based One-Time Password) Authenticator Engine.
 * Fully local on-device calculation without network calls.
 */
object TotpGenerator {

    private const val DEFAULT_PERIOD_SECONDS = 30
    private const val DEFAULT_DIGITS = 6
    private const val DEFAULT_ALGORITHM = "SHA1"

    data class TotpConfig(
        val secret: String,
        val issuer: String = "",
        val accountName: String = "",
        val periodSeconds: Int = DEFAULT_PERIOD_SECONDS,
        val digits: Int = DEFAULT_DIGITS,
        val algorithm: String = DEFAULT_ALGORITHM
    )

    /**
     * Generates a TOTP code for the given secret at epoch timestamp [timestampSeconds].
     */
    fun generateTotp(
        secretBase32: String,
        timestampSeconds: Long = System.currentTimeMillis() / 1000L,
        periodSeconds: Int = DEFAULT_PERIOD_SECONDS,
        digits: Int = DEFAULT_DIGITS,
        algorithm: String = DEFAULT_ALGORITHM
    ): String {
        if (secretBase32.isBlank()) return ""

        val cleanedSecret = secretBase32.replace(" ", "").replace("-", "").uppercase(Locale.US)
        val keyBytes = try {
            Base32.decode(cleanedSecret)
        } catch (_: Exception) {
            return ""
        }

        if (keyBytes.isEmpty()) return ""

        val counter = timestampSeconds / periodSeconds.coerceAtLeast(1)
        return generateHotp(keyBytes, counter, digits, algorithm)
    }

    fun generateTotpCode(secretBase32: String): String {
        return generateTotp(secretBase32)
    }

    fun generateCode(
        secret: String,
        timeMillis: Long = System.currentTimeMillis(),
        periodSeconds: Int = DEFAULT_PERIOD_SECONDS,
        digits: Int = DEFAULT_DIGITS,
        algorithm: String = DEFAULT_ALGORITHM
    ): String {
        return generateTotp(
            secretBase32 = secret,
            timestampSeconds = timeMillis / 1000L,
            periodSeconds = periodSeconds,
            digits = digits,
            algorithm = algorithm
        )
    }

    /**
     * Calculates remaining seconds in the current TOTP step window (0..periodSeconds).
     */
    fun getRemainingSeconds(
        timestampSeconds: Long = System.currentTimeMillis() / 1000L,
        periodSeconds: Int = DEFAULT_PERIOD_SECONDS
    ): Int {
        val period = periodSeconds.coerceAtLeast(1)
        val elapsed = (timestampSeconds % period).toInt()
        return period - elapsed
    }

    /**
     * Calculates countdown progress as a float between 0f (empty) and 1f (full).
     */
    fun getProgressFraction(
        timestampSeconds: Long = System.currentTimeMillis() / 1000L,
        periodSeconds: Int = DEFAULT_PERIOD_SECONDS
    ): Float {
        val period = periodSeconds.coerceAtLeast(1)
        val remaining = getRemainingSeconds(timestampSeconds, period)
        return remaining.toFloat() / period.toFloat()
    }

    /**
     * Parses an `otpauth://totp/...` URI into a [TotpConfig].
     */
    fun parseOtpAuthUri(uriString: String): TotpConfig? {
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme?.lowercase(Locale.ROOT) != "otpauth") return null
            if (uri.authority?.lowercase(Locale.ROOT) != "totp") return null

            val path = uri.path?.removePrefix("/") ?: ""
            var issuer = uri.getQueryParameter("issuer") ?: ""
            var accountName = path

            if (path.contains(":")) {
                val parts = path.split(":", limit = 2)
                if (issuer.isBlank()) {
                    issuer = parts[0].trim()
                }
                accountName = parts[1].trim()
            }

            val secret = uri.getQueryParameter("secret") ?: return null
            val period = uri.getQueryParameter("period")?.toIntOrNull() ?: DEFAULT_PERIOD_SECONDS
            val digits = uri.getQueryParameter("digits")?.toIntOrNull() ?: DEFAULT_DIGITS
            val algorithm = uri.getQueryParameter("algorithm")?.uppercase(Locale.US) ?: DEFAULT_ALGORITHM

            return TotpConfig(
                secret = secret,
                issuer = issuer,
                accountName = accountName,
                periodSeconds = period,
                digits = digits,
                algorithm = algorithm
            )
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Formats raw digits with a friendly visual space in the middle (e.g. "123 456" or "1234 5678").
     */
    fun formatCode(code: String): String {
        if (code.length == 6) {
            return "${code.substring(0, 3)} ${code.substring(3)}"
        } else if (code.length == 8) {
            return "${code.substring(0, 4)} ${code.substring(4)}"
        }
        return code
    }

    /**
     * Core RFC 4226 HOTP algorithm with dynamic truncation.
     */
    fun generateHotp(
        key: ByteArray,
        counter: Long,
        digits: Int = DEFAULT_DIGITS,
        algorithm: String = DEFAULT_ALGORITHM
    ): String {
        val hmacAlgorithm = when (algorithm.uppercase(Locale.US)) {
            "SHA256" -> "HmacSHA256"
            "SHA512" -> "HmacSHA512"
            else -> "HmacSHA1"
        }

        val mac = Mac.getInstance(hmacAlgorithm)
        mac.init(SecretKeySpec(key, "RAW"))

        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
        val hash = mac.doFinal(counterBytes)

        // Dynamic truncation (RFC 4226 Section 5.3)
        val offset = (hash[hash.size - 1].toInt() and 0x0F)
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val modulo = 10.0.pow(digits.toDouble()).toInt()
        val otp = binary % modulo

        return otp.toString().padStart(digits, '0')
    }

    /**
     * RFC 4648 Base32 implementation.
     */
    object Base32 {
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        private val DECODE_TABLE = IntArray(128) { -1 }.apply {
            for (i in ALPHABET.indices) {
                this[ALPHABET[i].code] = i
            }
        }

        fun decode(input: String): ByteArray {
            val clean = input.trim().replace("=", "").uppercase(Locale.US)
            if (clean.isEmpty()) return ByteArray(0)

            val out = ArrayList<Byte>()
            var buffer = 0
            var bitsLeft = 0

            for (char in clean) {
                val code = char.code
                if (code >= 128 || DECODE_TABLE[code] == -1) {
                    continue // Skip invalid chars or spaces
                }
                buffer = (buffer shl 5) or (DECODE_TABLE[code] and 0x1F)
                bitsLeft += 5

                if (bitsLeft >= 8) {
                    out.add((buffer shr (bitsLeft - 8)).toByte())
                    bitsLeft -= 8
                }
            }

            val result = ByteArray(out.size)
            for (i in out.indices) {
                result[i] = out[i]
            }
            return result
        }

        fun isValidBase32(input: String): Boolean {
            val clean = input.trim().replace("=", "").replace(" ", "").replace("-", "").uppercase(Locale.US)
            if (clean.isEmpty()) return false
            return clean.all { it.code < 128 && DECODE_TABLE[it.code] != -1 }
        }
    }
}
