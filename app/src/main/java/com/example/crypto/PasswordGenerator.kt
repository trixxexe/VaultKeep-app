package com.example.crypto

import java.security.SecureRandom
import java.util.Locale
import kotlin.math.log2

enum class GeneratorMode {
    RANDOM,
    PASSPHRASE
}

enum class PassphraseSeparator(val char: String, val label: String) {
    DASH("-", "Dash (-)"),
    DOT(".", "Dot (.)"),
    SPACE(" ", "Space ( )"),
    UNDERSCORE("_", "Underscore (_)"),
    NONE("", "None")
}

enum class CapitalizationMode(val label: String) {
    TITLE_CASE("Title Case (Word)"),
    LOWERCASE("Lowercase (word)"),
    UPPERCASE("Uppercase (WORD)")
}

data class PasswordConfig(
    val length: Int = 16,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true,
    val excludeAmbiguous: Boolean = true
)

data class PassphraseConfig(
    val wordCount: Int = 4,
    val separator: PassphraseSeparator = PassphraseSeparator.DASH,
    val capitalization: CapitalizationMode = CapitalizationMode.TITLE_CASE,
    val appendNumber: Boolean = true
)

enum class PasswordStrength(val label: String, val score: Int) {
    VERY_WEAK("Very Weak", 1),
    WEAK("Weak", 2),
    MODERATE("Moderate", 3),
    STRONG("Strong", 4),
    VERY_STRONG("Very Strong", 5)
}

data class StrengthEvaluation(
    val strength: PasswordStrength,
    val entropyBits: Double,
    val feedback: String
)

object PasswordGenerator {

    private const val UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val UPPERCASE_ALL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijkmnopqrstuvwxyz"
    private const val LOWERCASE_ALL = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "23456789"
    private const val DIGITS_ALL = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"
    private const val SYMBOLS_SIMPLE = "!@#$%^&*-_+"

    private val secureRandom = SecureRandom()

    fun generate(config: PasswordConfig = PasswordConfig()): String = generateRandom(config)

    fun generateRandom(config: PasswordConfig): String {
        val uppercaseSet = if (config.excludeAmbiguous) UPPERCASE else UPPERCASE_ALL
        val lowercaseSet = if (config.excludeAmbiguous) LOWERCASE else LOWERCASE_ALL
        val digitsSet = if (config.excludeAmbiguous) DIGITS else DIGITS_ALL
        val symbolsSet = if (config.excludeAmbiguous) SYMBOLS_SIMPLE else SYMBOLS

        val charPool = StringBuilder()
        val guaranteedChars = mutableListOf<Char>()

        if (config.includeUppercase) {
            charPool.append(uppercaseSet)
            guaranteedChars.add(uppercaseSet[secureRandom.nextInt(uppercaseSet.length)])
        }
        if (config.includeLowercase) {
            charPool.append(lowercaseSet)
            guaranteedChars.add(lowercaseSet[secureRandom.nextInt(lowercaseSet.length)])
        }
        if (config.includeDigits) {
            charPool.append(digitsSet)
            guaranteedChars.add(digitsSet[secureRandom.nextInt(digitsSet.length)])
        }
        if (config.includeSymbols) {
            charPool.append(symbolsSet)
            guaranteedChars.add(symbolsSet[secureRandom.nextInt(symbolsSet.length)])
        }

        if (charPool.isEmpty()) {
            charPool.append(lowercaseSet)
            guaranteedChars.add(lowercaseSet[secureRandom.nextInt(lowercaseSet.length)])
        }

        val poolString = charPool.toString()
        val result = CharArray(config.length)

        // Place guaranteed characters randomly
        val availableIndices = (0 until config.length).toMutableList()
        for (char in guaranteedChars) {
            if (availableIndices.isEmpty()) break
            val randomIndexInList = secureRandom.nextInt(availableIndices.size)
            val chosenPos = availableIndices.removeAt(randomIndexInList)
            result[chosenPos] = char
        }

        // Fill remainder from pool
        for (i in availableIndices) {
            result[i] = poolString[secureRandom.nextInt(poolString.length)]
        }

        return String(result)
    }

    fun generatePassphrase(config: PassphraseConfig): String {
        val wordCount = config.wordCount.coerceIn(3, 10)
        val words = mutableListOf<String>()

        val totalAvailableWords = WordList.WORDS.size
        for (i in 0 until wordCount) {
            val chosen = WordList.WORDS[secureRandom.nextInt(totalAvailableWords)]
            val formatted = when (config.capitalization) {
                CapitalizationMode.TITLE_CASE -> chosen.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                CapitalizationMode.LOWERCASE -> chosen.lowercase(Locale.US)
                CapitalizationMode.UPPERCASE -> chosen.uppercase(Locale.US)
            }
            words.add(formatted)
        }

        val basePhrase = words.joinToString(separator = config.separator.char)
        return if (config.appendNumber) {
            val number = secureRandom.nextInt(90) + 10 // 10..99
            if (config.separator == PassphraseSeparator.NONE) {
                "$basePhrase$number"
            } else {
                "$basePhrase${config.separator.char}$number"
            }
        } else {
            basePhrase
        }
    }

    /**
     * Calculates estimated entropy bits for a random config.
     */
    fun calculateRandomEntropy(config: PasswordConfig): Double {
        var pool = 0
        if (config.includeUppercase) pool += 26
        if (config.includeLowercase) pool += 26
        if (config.includeDigits) pool += 10
        if (config.includeSymbols) pool += 32
        if (pool == 0) pool = 26
        return config.length * log2(pool.toDouble())
    }

    /**
     * Calculates estimated entropy bits for a passphrase config.
     */
    fun calculatePassphraseEntropy(config: PassphraseConfig): Double {
        val wordListSize = WordList.WORDS.size.toDouble()
        var entropy = config.wordCount * log2(wordListSize)
        if (config.appendNumber) {
            entropy += log2(90.0) // 10..99
        }
        if (config.capitalization != CapitalizationMode.LOWERCASE) {
            entropy += log2(2.0)
        }
        return entropy
    }

    /**
     * Calculates password entropy and strength for arbitrary text.
     */
    fun evaluateStrength(password: String): PasswordStrength {
        return evaluateStrengthDetailed(password).strength
    }

    /**
     * Calculates estimated entropy bits for arbitrary text.
     */
    fun calculateEntropy(password: String): Double {
        return evaluateStrengthDetailed(password).entropyBits
    }

    /**
     * Comprehensive strength evaluation with entropy bits and descriptive feedback.
     */
    fun evaluateStrengthDetailed(password: String): StrengthEvaluation {
        if (password.isEmpty()) {
            return StrengthEvaluation(PasswordStrength.VERY_WEAK, 0.0, "Enter a password")
        }

        var poolSize = 0
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }

        if (hasUpper) poolSize += 26
        if (hasLower) poolSize += 26
        if (hasDigit) poolSize += 10
        if (hasSymbol) poolSize += 32
        if (poolSize == 0) poolSize = 10

        // Handle space/dash separated passphrases
        val isPassphrase = password.contains(" ") || password.contains("-") || password.contains(".")
        val wordTokens = password.split(" ", "-", ".", "_").filter { it.isNotBlank() }

        val entropy = if (isPassphrase && wordTokens.size >= 3) {
            // Evaluated as passphrase
            wordTokens.size * log2(WordList.WORDS.size.toDouble()) + (if (hasDigit) 6.5 else 0.0)
        } else {
            password.length * log2(poolSize.toDouble())
        }

        val strength = when {
            entropy < 35 || password.length < 8 -> PasswordStrength.VERY_WEAK
            entropy < 55 -> PasswordStrength.WEAK
            entropy < 75 -> PasswordStrength.MODERATE
            entropy < 95 -> PasswordStrength.STRONG
            else -> PasswordStrength.VERY_STRONG
        }

        val feedback = when (strength) {
            PasswordStrength.VERY_WEAK -> "Very vulnerable to brute-force"
            PasswordStrength.WEAK -> "Weak. Add more length or variety"
            PasswordStrength.MODERATE -> "Reasonable. Consider adding length"
            PasswordStrength.STRONG -> "Strong security"
            PasswordStrength.VERY_STRONG -> "Excellent cryptographic strength"
        }

        return StrengthEvaluation(strength, entropy, feedback)
    }
}

