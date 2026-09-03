/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Copyright (C) 2026 VaultKeep Contributors
 */

package com.example.passkey

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Cleanly separated cryptographic engine for WebAuthn / FIDO2 Passkeys.
 * Implements W3C WebAuthn Level 3 and FIDO Alliance CTAP2 standard primitives:
 * - ECDSA with curve secp256r1 (P-256 / prime256v1)
 * - COSE Key serialization for ES256 (-7)
 * - Authenticator Data generation with flags (User Presence + User Verification)
 * - Assertion signature generation (SHA256withECDSA)
 */
object PasskeyCryptoHelper {

    private const val EC_ALGORITHM = "EC"
    private const val CURVE_NAME = "secp256r1"
    private const val SIGN_ALGORITHM = "SHA256withECDSA"

    private val secureRandom = SecureRandom()

    data class PasskeyRegistrationData(
        val credentialId: ByteArray,
        val rpId: String,
        val userHandle: ByteArray,
        val userName: String,
        val privateKeyPkcs8Base64: String,
        val publicKeyCoseBase64: String,
        val publicKeyX509Base64: String,
        val signCount: Int = 0,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        val credentialIdBase64Url: String
            get() = Base64.encodeToString(credentialId, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        val userHandleBase64Url: String
            get() = Base64.encodeToString(userHandle, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    data class PasskeyAssertionData(
        val credentialId: ByteArray,
        val authenticatorData: ByteArray,
        val clientDataHash: ByteArray,
        val signature: ByteArray,
        val userHandle: ByteArray,
        val newSignCount: Int
    )

    /**
     * Generates a new P-256 ECDSA key pair and constructs WebAuthn credential metadata.
     */
    fun createPasskey(
        rpId: String,
        userName: String,
        userHandle: ByteArray = generateRandomBytes(32),
        credentialId: ByteArray = generateRandomBytes(32)
    ): PasskeyRegistrationData {
        val kpg = KeyPairGenerator.getInstance(EC_ALGORITHM)
        kpg.initialize(ECGenParameterSpec(CURVE_NAME), secureRandom)
        val keyPair = kpg.generateKeyPair()

        val privateKey = keyPair.private as ECPrivateKey
        val publicKey = keyPair.public as ECPublicKey

        val privateKeyPkcs8 = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
        val publicKeyX509 = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)

        val coseKeyBytes = encodeCoseKey(publicKey)
        val publicKeyCose = Base64.encodeToString(coseKeyBytes, Base64.NO_WRAP)

        return PasskeyRegistrationData(
            credentialId = credentialId,
            rpId = rpId,
            userHandle = userHandle,
            userName = userName,
            privateKeyPkcs8Base64 = privateKeyPkcs8,
            publicKeyCoseBase64 = publicKeyCose,
            publicKeyX509Base64 = publicKeyX509,
            signCount = 0
        )
    }

    /**
     * Signs a WebAuthn authentication assertion challenge with the passkey's private key.
     */
    fun signAssertion(
        rpId: String,
        privateKeyPkcs8Base64: String,
        clientDataHash: ByteArray,
        currentSignCount: Int
    ): PasskeyAssertionData {
        val privateKeyBytes = Base64.decode(privateKeyPkcs8Base64, Base64.DEFAULT)
        val keyFactory = KeyFactory.getInstance(EC_ALGORITHM)
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))

        val newSignCount = currentSignCount + 1
        val rpIdHash = sha256(rpId.toByteArray(Charsets.UTF_8))

        // Authenticator Data:
        // [32 bytes: rpIdHash]
        // [1 byte: flags -> 0x05 = UP (User Present: 0x01) | UV (User Verified: 0x04)]
        // [4 bytes: signCount in big-endian]
        val authData = ByteBuffer.allocate(32 + 1 + 4).apply {
            put(rpIdHash)
            put(0x05.toByte()) // UP | UV
            putInt(newSignCount)
        }.array()

        // Signature data = authData || clientDataHash
        val dataToSign = ByteBuffer.allocate(authData.size + clientDataHash.size).apply {
            put(authData)
            put(clientDataHash)
        }.array()

        val signer = Signature.getInstance(SIGN_ALGORITHM)
        signer.initSign(privateKey)
        signer.update(dataToSign)
        val signature = signer.sign()

        return PasskeyAssertionData(
            credentialId = byteArrayOf(),
            authenticatorData = authData,
            clientDataHash = clientDataHash,
            signature = signature,
            userHandle = byteArrayOf(),
            newSignCount = newSignCount
        )
    }

    /**
     * Encodes an EC P-256 public key into RFC 8152 / RFC 9052 COSE Key format:
     * Map:
     *   1 (kty) -> 2 (EC2)
     *   3 (alg) -> -7 (ES256)
     *  -1 (crv) -> 1 (P-256)
     *  -2 (x)   -> 32-byte X coordinate
     *  -3 (y)   -> 32-byte Y coordinate
     */
    fun encodeCoseKey(publicKey: ECPublicKey): ByteArray {
        val w = publicKey.w
        val x = toFixedLengthByteArray(w.affineX.toByteArray(), 32)
        val y = toFixedLengthByteArray(w.affineY.toByteArray(), 32)

        val baos = ByteArrayOutputStream()
        // Map with 5 elements (0xA5)
        baos.write(0xA5)

        // 1: 2 (kty: EC2)
        baos.write(0x01)
        baos.write(0x02)

        // 3: -7 (alg: ES256 -> -7 in CBOR is 0x26)
        baos.write(0x03)
        baos.write(0x26)

        // -1: 1 (crv: P-256 -> -1 is 0x20)
        baos.write(0x20)
        baos.write(0x01)

        // -2: x coordinate (-2 is 0x21, byte string 32 bytes: 0x58 0x20)
        baos.write(0x21)
        baos.write(0x58)
        baos.write(0x20)
        baos.write(x)

        // -3: y coordinate (-3 is 0x22, byte string 32 bytes: 0x58 0x20)
        baos.write(0x22)
        baos.write(0x58)
        baos.write(0x20)
        baos.write(y)

        return baos.toByteArray()
    }

    /**
     * Builds the standard WebAuthn Authenticator Data for registration (Attestation):
     * [32 bytes: rpIdHash]
     * [1 byte: flags -> 0x45 = UP (0x01) | UV (0x04) | AT (0x40)]
     * [4 bytes: signCount (0)]
     * [16 bytes: AAGUID (all zeroes for standard software authenticator)]
     * [2 bytes: credentialId length (big endian)]
     * [N bytes: credentialId]
     * [M bytes: COSE public key]
     */
    fun buildAttestationAuthData(
        rpId: String,
        credentialId: ByteArray,
        cosePublicKey: ByteArray
    ): ByteArray {
        val rpIdHash = sha256(rpId.toByteArray(Charsets.UTF_8))
        val aaguid = ByteArray(16) // 16 zeroes
        val credIdLen = credentialId.size

        val totalSize = 32 + 1 + 4 + 16 + 2 + credIdLen + cosePublicKey.size
        val buffer = ByteBuffer.allocate(totalSize)

        buffer.put(rpIdHash)
        buffer.put(0x45.toByte()) // UP | UV | AT
        buffer.putInt(0) // signCount
        buffer.put(aaguid)
        buffer.putShort(credIdLen.toShort())
        buffer.put(credentialId)
        buffer.put(cosePublicKey)

        return buffer.array()
    }

    /**
     * Constructs a self-attested WebAuthn "none" attestation object (CBOR):
     * {"fmt": "none", "attStmt": {}, "authData": <authData>}
     */
    fun buildNoneAttestationObject(authData: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        // Map of 3 items (0xA3)
        baos.write(0xA3)

        // "fmt": "none"
        writeCborTextString(baos, "fmt")
        writeCborTextString(baos, "none")

        // "attStmt": {}
        writeCborTextString(baos, "attStmt")
        baos.write(0xA0) // Empty map

        // "authData": <bytes>
        writeCborTextString(baos, "authData")
        writeCborByteString(baos, authData)

        return baos.toByteArray()
    }

    private fun writeCborTextString(baos: ByteArrayOutputStream, str: String) {
        val bytes = str.toByteArray(Charsets.UTF_8)
        if (bytes.size < 24) {
            baos.write(0x60 or bytes.size)
        } else {
            baos.write(0x78)
            baos.write(bytes.size)
        }
        baos.write(bytes)
    }

    private fun writeCborByteString(baos: ByteArrayOutputStream, bytes: ByteArray) {
        val len = bytes.size
        when {
            len < 24 -> {
                baos.write(0x40 or len)
            }
            len <= 255 -> {
                baos.write(0x58)
                baos.write(len)
            }
            else -> {
                baos.write(0x59)
                baos.write(len shr 8)
                baos.write(len and 0xFF)
            }
        }
        baos.write(bytes)
    }

    fun sha256(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    fun generateRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    private fun toFixedLengthByteArray(bytes: ByteArray, length: Int): ByteArray {
        if (bytes.size == length) return bytes
        if (bytes.size > length) {
            // Trim leading zero sign byte if present
            return bytes.copyOfRange(bytes.size - length, bytes.size)
        }
        // Pad with leading zeroes
        val padded = ByteArray(length)
        System.arraycopy(bytes, 0, padded, length - bytes.size, bytes.size)
        return padded
    }
}
