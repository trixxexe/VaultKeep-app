package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.PasswordGenerator
import com.example.data.VaultPreferences
import com.example.ui.MainViewModel
import com.example.ui.components.StrengthMeter
import com.example.ui.theme.SecurityAmber
import com.example.ui.theme.SecurityRed

@Composable
fun LockScreen(
    viewModel: MainViewModel,
    isFirstTimeSetup: Boolean,
    onTriggerBiometrics: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val authError by viewModel.authErrorMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val preferences = viewModel.preferences

    val strength = remember(password) { PasswordGenerator.evaluateStrength(password) }
    val entropyBits = remember(password) { PasswordGenerator.calculateEntropy(password) }

    val isBiometricLockedOut = preferences.failedBiometricAttempts >= VaultPreferences.MAX_FAILED_BIOMETRIC_ATTEMPTS

    fun submit() {
        localError = null
        viewModel.clearAuthError()

        if (password.isBlank()) {
            localError = "Please enter your master password"
            return
        }

        if (isFirstTimeSetup) {
            if (password.length < 8) {
                localError = "Master password must be at least 8 characters"
                return
            }
            if (password != confirmPassword) {
                localError = "Passwords do not match"
                return
            }
            viewModel.setupMasterPassword(password.toCharArray())
        } else {
            viewModel.unlockWithPassword(password.toCharArray())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Brand Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFirstTimeSetup) Icons.Default.Shield else Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Titles
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isFirstTimeSetup) "Create Your Vault" else "Vault Locked",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isFirstTimeSetup)
                        "Set a strong master password to initialize your AES-256-GCM encrypted vault."
                    else
                        "Enter your master password to decrypt and access your credentials.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // Biometric lockout banner (if 5 failed attempts reached)
            if (!isFirstTimeSetup && isBiometricLockedOut) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("biometric_lockout_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SecurityRed.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = SecurityRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Biometrics Locked Out",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SecurityRed
                            )
                            Text(
                                text = "5 failed biometric attempts reached. Master password is required to unlock.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Error banner
            val displayError = localError ?: authError
            AnimatedVisibility(
                visible = displayError != null && !(isBiometricLockedOut && displayError.contains("Biometrics locked")),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SecurityRed.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = SecurityRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = displayError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecurityRed
                        )
                    }
                }
            }

            // Input fields card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            localError = null
                            viewModel.clearAuthError()
                        },
                        label = { Text(if (isFirstTimeSetup) "Master Password" else "Enter Master Password") },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (isFirstTimeSetup) ImeAction.Next else ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { if (!isFirstTimeSetup) submit() }
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = { isPasswordVisible = !isPasswordVisible },
                                modifier = Modifier.testTag("toggle_lock_password_visibility")
                            ) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPasswordVisible) "Hide Password" else "Show Password",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("master_password_input"),
                        shape = RoundedCornerShape(16.dp)
                    )

                    if (isFirstTimeSetup) {
                        if (password.isNotEmpty()) {
                            StrengthMeter(
                                strength = strength,
                                entropyBits = entropyBits,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                localError = null
                                viewModel.clearAuthError()
                            },
                            label = { Text("Confirm Master Password") },
                            singleLine = true,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_master_password_input"),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Primary Submit Button
                    Button(
                        onClick = { submit() },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("unlock_or_setup_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onSecondary,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isFirstTimeSetup) "Initialize Vault" else "Unlock Vault",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Biometric Unlock Button (for existing vaults with biometric enabled and not locked out)
                    if (!isFirstTimeSetup && preferences.isBiometricEnabled && !isBiometricLockedOut) {
                        OutlinedButton(
                            onClick = onTriggerBiometrics,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("biometric_unlock_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Unlock with Biometrics",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Zero-Knowledge Security Notice
            if (isFirstTimeSetup) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Zero-Knowledge Warning",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = SecurityAmber
                        )
                        Text(
                            text = "Your master password is never stored or transmitted anywhere. If you forget this password, there is no backdoor or recovery mechanism.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

