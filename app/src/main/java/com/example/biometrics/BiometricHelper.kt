/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Copyright (C) 2026 VaultKeep Contributors
 */

package com.example.biometrics

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.crypto.CryptoManager
import javax.crypto.Cipher

object BiometricHelper {

    enum class PromptPurpose {
        APP_UNLOCK,
        AUTOFILL_UNLOCK,
        CREDENTIAL_MANAGER_UNLOCK,
        SESSION_REAUTH,
        ENROLL_BIOMETRICS
    }

    fun isBiometricHardwareAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return canAuth == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Unified Biometric Prompt builder providing consistent copy and UX across all entry points:
     * - Main App Unlock
     * - Autofill Framework Unlock
     * - Credential Manager & Passkey Unlock
     * - Inactivity Re-authentication
     */
    fun createPromptInfo(purpose: PromptPurpose): BiometricPrompt.PromptInfo {
        val (title, subtitle, negative) = when (purpose) {
            PromptPurpose.APP_UNLOCK -> Triple(
                "Unlock VaultKeep",
                "Authenticate with fingerprint or face",
                "Use Master Password"
            )
            PromptPurpose.AUTOFILL_UNLOCK -> Triple(
                "Unlock VaultKeep",
                "Authenticate to autofill credentials",
                "Use Master Password"
            )
            PromptPurpose.CREDENTIAL_MANAGER_UNLOCK -> Triple(
                "Unlock VaultKeep",
                "Authenticate to access your passkeys & passwords",
                "Use Master Password"
            )
            PromptPurpose.SESSION_REAUTH -> Triple(
                "Session Expired",
                "Authenticate to resume your session",
                "Use Master Password"
            )
            PromptPurpose.ENROLL_BIOMETRICS -> Triple(
                "Enable Biometric Unlock",
                "Confirm your biometric identity to protect your vault key",
                "Cancel"
            )
        }

        return BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negative)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
    }

    /**
     * Prompts the user with BiometricPrompt to authorize the Keystore Cipher for unlocking.
     * Safely catches and handles KeyPermanentlyInvalidatedException when OS fingerprints change.
     */
    fun showBiometricUnlockPrompt(
        activity: FragmentActivity,
        iv: ByteArray,
        purpose: PromptPurpose = PromptPurpose.APP_UNLOCK,
        onSuccess: (Cipher) -> Unit,
        onKeyInvalidated: (() -> Unit)? = null,
        onError: (String) -> Unit,
        onFailed: () -> Unit
    ) {
        try {
            val cipher = CryptoManager.getBiometricDecryptCipher(iv)
            val cryptoObject = BiometricPrompt.CryptoObject(cipher)

            val executor = ContextCompat.getMainExecutor(activity)
            val promptInfo = createPromptInfo(purpose)

            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        val authenticatedCipher = result.cryptoObject?.cipher
                        if (authenticatedCipher != null) {
                            onSuccess(authenticatedCipher)
                        } else {
                            onError("Biometric cipher release failed")
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        // User canceled or tapped negative button (use password)
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            onError(errString.toString())
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onFailed()
                    }
                }
            )

            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } catch (e: KeyPermanentlyInvalidatedException) {
            CryptoManager.deleteBiometricKey()
            onKeyInvalidated?.invoke()
            onError("Biometrics changed on your device. Please unlock with your Master Password to re-enable biometric unlock.")
        } catch (e: Exception) {
            if (e.cause is KeyPermanentlyInvalidatedException || e.message?.contains("Key permanently invalidated", ignoreCase = true) == true) {
                CryptoManager.deleteBiometricKey()
                onKeyInvalidated?.invoke()
                onError("Biometrics changed on your device. Please unlock with your Master Password to re-enable biometric unlock.")
            } else {
                onError(e.message ?: "Failed to initialize biometric hardware")
            }
        }
    }

    /**
     * Prompts the user with BiometricPrompt to authorize the Keystore Cipher for key wrapping during enrollment.
     */
    fun showBiometricEnrollPrompt(
        activity: FragmentActivity,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val cipher = CryptoManager.getBiometricEncryptCipher()
            val cryptoObject = BiometricPrompt.CryptoObject(cipher)

            val executor = ContextCompat.getMainExecutor(activity)
            val promptInfo = createPromptInfo(PromptPurpose.ENROLL_BIOMETRICS)

            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        val authenticatedCipher = result.cryptoObject?.cipher
                        if (authenticatedCipher != null) {
                            onSuccess(authenticatedCipher)
                        } else {
                            onError("Biometric cipher authorization failed")
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            onError(errString.toString())
                        }
                    }
                }
            )

            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } catch (e: Exception) {
            onError(e.message ?: "Failed to initialize biometric enrollment")
        }
    }
}
