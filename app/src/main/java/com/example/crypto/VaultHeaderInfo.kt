package com.example.crypto

/**
 * Metadata extracted from a VaultKeep on-disk container header.
 */
data class VaultHeaderInfo(
    val formatVersion: Int,
    val cryptoVersion: Int,
    val kdfAlgorithm: String,
    val iterations: Int,
    val salt: ByteArray,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VaultHeaderInfo

        if (formatVersion != other.formatVersion) return false
        if (cryptoVersion != other.cryptoVersion) return false
        if (kdfAlgorithm != other.kdfAlgorithm) return false
        if (iterations != other.iterations) return false
        if (!salt.contentEquals(other.salt)) return false
        if (!iv.contentEquals(other.iv)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = formatVersion
        result = 31 * result + cryptoVersion
        result = 31 * result + kdfAlgorithm.hashCode()
        result = 31 * result + iterations
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}
