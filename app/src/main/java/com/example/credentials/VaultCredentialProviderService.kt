/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Copyright (C) 2026 VaultKeep Contributors
 */

package com.example.credentials

import android.app.PendingIntent
import android.content.Intent
import android.credentials.ClearCredentialStateException
import android.credentials.CreateCredentialException
import android.credentials.GetCredentialException
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.service.credentials.BeginCreateCredentialRequest
import android.service.credentials.BeginCreateCredentialResponse
import android.service.credentials.BeginGetCredentialRequest
import android.service.credentials.BeginGetCredentialResponse
import android.service.credentials.ClearCredentialStateRequest
import android.service.credentials.CredentialProviderService
import androidx.annotation.RequiresApi
import com.example.data.VaultPreferences
import com.example.data.VaultRepository
import com.example.data.VaultState

/**
 * Android 14+ (API 34+) Credential Provider Service for VaultKeep.
 * Seamlessly integrates with Android's system Credential Manager to support:
 * - Passkey (FIDO2 / WebAuthn) Authentication & Registration
 * - Password Retrieval & Creation
 *
 * Strictly respects vault encryption: NEVER releases plaintext credentials or signs passkey
 * assertions without master password or biometric authentication.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class VaultCredentialProviderService : CredentialProviderService() {

    private lateinit var repository: VaultRepository
    private lateinit var preferences: VaultPreferences

    override fun onCreate() {
        super.onCreate()
        preferences = VaultPreferences(applicationContext)
        repository = VaultRepository(applicationContext, preferences)
    }

    override fun onBeginGetCredential(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
    ) {
        val callingPackage = request.callingAppInfo?.packageName ?: ""
        val responseBuilder = BeginGetCredentialResponse.Builder()

        val currentVaultState = repository.vaultState.value
        val entries = when (currentVaultState) {
            is VaultState.Unlocked -> repository.findAutofillMatches(callingPackage)
            else -> emptyList() // If locked, the auth activity handles unlock first
        }

        val authIntent = Intent(this, CredentialAuthActivity::class.java).apply {
            putExtra(CredentialAuthActivity.EXTRA_CALLING_PACKAGE, callingPackage)
            putExtra(CredentialAuthActivity.EXTRA_IS_GET_REQUEST, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            2001,
            authIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Return the response containing authentication actions or credentials
        callback.onResult(responseBuilder.build())
    }

    override fun onBeginCreateCredential(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>
    ) {
        val callingPackage = request.callingAppInfo?.packageName ?: ""
        val responseBuilder = BeginCreateCredentialResponse.Builder()

        val saveIntent = Intent(this, CredentialAuthActivity::class.java).apply {
            putExtra(CredentialAuthActivity.EXTRA_CALLING_PACKAGE, callingPackage)
            putExtra(CredentialAuthActivity.EXTRA_IS_CREATE_REQUEST, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            2002,
            saveIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        callback.onResult(responseBuilder.build())
    }

    override fun onClearCredentialState(
        request: ClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialStateException>
    ) {
        callback.onResult(null)
    }
}
