/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Copyright (C) 2026 VaultKeep Contributors
 */

package com.example.credentials

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.biometrics.BiometricHelper
import com.example.crypto.CryptoManager
import com.example.data.EntryType
import com.example.data.ThemeMode
import com.example.data.VaultEntry
import com.example.data.VaultPreferences
import com.example.data.VaultRepository
import com.example.data.VaultState
import com.example.passkey.PasskeyCryptoHelper
import com.example.ui.theme.VaultKeepTheme
import kotlinx.coroutines.launch
import java.util.UUID

class CredentialAuthActivity : FragmentActivity() {

    private lateinit var repository: VaultRepository
    private lateinit var preferences: VaultPreferences

    private var callingPackage: String = ""
    private var isGetRequest: Boolean = false
    private var isCreateRequest: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = VaultPreferences(applicationContext)
        repository = VaultRepository(applicationContext, preferences)

        if (preferences.preventScreenCapture) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        enableEdgeToEdge()

        callingPackage = intent.getStringExtra(EXTRA_CALLING_PACKAGE) ?: ""
        isGetRequest = intent.getBooleanExtra(EXTRA_IS_GET_REQUEST, false)
        isCreateRequest = intent.getBooleanExtra(EXTRA_IS_CREATE_REQUEST, false)

        setContent {
            val isDark = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            VaultKeepTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                ) {
                    CredentialAuthContent(
                        callingPackage = callingPackage,
                        isCreateRequest = isCreateRequest,
                        isBiometricEnabled = preferences.isBiometricEnabled && preferences.wrappedBiometricKey != null && !repository.isBiometricLockedOut,
                        onUnlockWithPassword = { password ->
                            lifecycleScope.launch {
                                val result = repository.unlockWithPassword(password.toCharArray())
                                if (result.isSuccess) {
                                    Toast.makeText(this@CredentialAuthActivity, "Vault unlocked", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@CredentialAuthActivity, "Incorrect master password", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onBiometricClick = {
                            promptBiometrics()
                        },
                        onSelectEntry = { entry ->
                            handleEntrySelected(entry)
                        },
                        onCreatePasskey = { rpId, userName ->
                            lifecycleScope.launch {
                                handleCreatePasskey(rpId, userName)
                            }
                        },
                        onCancel = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        },
                        repository = repository
                    )
                }
            }
        }

        // Auto-prompt biometrics if eligible
        if (preferences.isBiometricEnabled && preferences.wrappedBiometricKey != null && !repository.isBiometricLockedOut) {
            promptBiometrics()
        }
    }

    private fun promptBiometrics() {
        val iv = preferences.biometricIv ?: return
        BiometricHelper.showBiometricUnlockPrompt(
            activity = this,
            iv = iv,
            purpose = BiometricHelper.PromptPurpose.CREDENTIAL_MANAGER_UNLOCK,
            onSuccess = { cipher ->
                lifecycleScope.launch {
                    val result = repository.unlockWithBiometrics(cipher)
                    if (result.isSuccess) {
                        Toast.makeText(this@CredentialAuthActivity, "Vault unlocked", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@CredentialAuthActivity, "Biometric authorization failed", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onKeyInvalidated = {
                preferences.wrappedBiometricKey = null
                preferences.isBiometricEnabled = false
            },
            onError = { errorMsg ->
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
            },
            onFailed = {
                Toast.makeText(this, "Biometric not recognized", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun handleEntrySelected(entry: VaultEntry) {
        if (entry.isPasskey) {
            // Sign assertion challenge if applicable
            lifecycleScope.launch {
                val updated = entry.copy(passkeySignCount = entry.passkeySignCount + 1)
                repository.saveEntry(updated)
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_SELECTED_USERNAME, updated.username)
                    putExtra(EXTRA_SELECTED_CREDENTIAL_ID, updated.passkeyCredentialId)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                Toast.makeText(this@CredentialAuthActivity, "Passkey authorized", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            val resultIntent = Intent().apply {
                putExtra(EXTRA_SELECTED_USERNAME, entry.username)
                putExtra(EXTRA_SELECTED_PASSWORD, entry.password)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            Toast.makeText(this@CredentialAuthActivity, "Credential selected", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private suspend fun handleCreatePasskey(rpId: String, userName: String) {
        val regData = PasskeyCryptoHelper.createPasskey(
            rpId = rpId.ifBlank { callingPackage },
            userName = userName
        )

        val entry = VaultEntry(
            id = UUID.randomUUID().toString(),
            title = rpId.ifBlank { callingPackage },
            username = userName,
            password = "",
            url = rpId,
            packageName = callingPackage,
            entryType = EntryType.PASSKEY,
            passkeyCredentialId = regData.credentialIdBase64Url,
            passkeyRpId = regData.rpId,
            passkeyUserHandle = regData.userHandleBase64Url,
            passkeyPrivateKeyPkcs8 = regData.privateKeyPkcs8Base64,
            passkeyPublicKeyCose = regData.publicKeyCoseBase64,
            passkeySignCount = 0
        )

        repository.saveEntry(entry)
        val resultIntent = Intent().apply {
            putExtra(EXTRA_CREATED_PASSKEY_ID, regData.credentialIdBase64Url)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        Toast.makeText(this, "Passkey created and encrypted in VaultKeep!", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_CALLING_PACKAGE = "extra_calling_package"
        const val EXTRA_IS_GET_REQUEST = "extra_is_get_request"
        const val EXTRA_IS_CREATE_REQUEST = "extra_is_create_request"
        const val EXTRA_SELECTED_USERNAME = "extra_selected_username"
        const val EXTRA_SELECTED_PASSWORD = "extra_selected_password"
        const val EXTRA_SELECTED_CREDENTIAL_ID = "extra_selected_credential_id"
        const val EXTRA_CREATED_PASSKEY_ID = "extra_created_passkey_id"
    }
}

@Composable
fun CredentialAuthContent(
    callingPackage: String,
    isCreateRequest: Boolean,
    isBiometricEnabled: Boolean,
    onUnlockWithPassword: (String) -> Unit,
    onBiometricClick: () -> Unit,
    onSelectEntry: (VaultEntry) -> Unit,
    onCreatePasskey: (String, String) -> Unit,
    onCancel: () -> Unit,
    repository: VaultRepository
) {
    val vaultState by repository.vaultState.collectAsState()
    var passwordInput by remember { mutableStateOf("") }
    var passkeyRpId by remember { mutableStateOf(callingPackage) }
    var passkeyUserName by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Credential Manager",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                when (val state = vaultState) {
                    is VaultState.Unlocked -> {
                        if (isCreateRequest) {
                            Text(
                                text = "Create Passkey for $callingPackage",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = passkeyRpId,
                                onValueChange = { passkeyRpId = it },
                                label = { Text("Relying Party / Domain") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = passkeyUserName,
                                onValueChange = { passkeyUserName = it },
                                label = { Text("Username / Account") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = { onCreatePasskey(passkeyRpId, passkeyUserName) },
                                enabled = passkeyUserName.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Create & Save Passkey")
                            }
                        } else {
                            val matches = repository.findAutofillMatches(callingPackage)
                            Text(
                                text = if (matches.isNotEmpty()) "Select credential for $callingPackage" else "No exact match for $callingPackage (showing all entries)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val displayList = if (matches.isNotEmpty()) matches else state.entries

                            if (displayList.isEmpty()) {
                                Text("No credentials in vault", style = MaterialTheme.typography.bodySmall)
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(displayList) { entry ->
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            onClick = { onSelectEntry(entry) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (entry.isPasskey) Icons.Default.Key else Icons.Default.Lock,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(entry.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                        if (entry.isPasskey) {
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = MaterialTheme.colorScheme.primaryContainer
                                                            ) {
                                                                Text(
                                                                    "PASSKEY",
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(entry.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = "Unlock your vault to authorize credentials for $callingPackage",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Master Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (passwordInput.isNotBlank()) onUnlockWithPassword(passwordInput)
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cm_master_password_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isBiometricEnabled) {
                                OutlinedButton(
                                    onClick = onBiometricClick,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Biometric")
                                }
                            }

                            Button(
                                onClick = { if (passwordInput.isNotBlank()) onUnlockWithPassword(passwordInput) },
                                modifier = Modifier.weight(1f),
                                enabled = passwordInput.isNotBlank()
                            ) {
                                Text("Unlock")
                            }
                        }
                    }
                }
            }
        }
    }
}
