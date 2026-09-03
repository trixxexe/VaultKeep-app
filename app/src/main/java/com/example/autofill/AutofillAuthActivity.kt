package com.example.autofill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.crypto.CryptoManager
import com.example.data.ThemeMode
import com.example.data.VaultEntry
import com.example.data.VaultPreferences
import com.example.data.VaultRepository
import com.example.data.VaultState
import com.example.ui.theme.VaultKeepTheme
import kotlinx.coroutines.launch
import java.util.UUID

class AutofillAuthActivity : FragmentActivity() {

    private lateinit var repository: VaultRepository
    private lateinit var preferences: VaultPreferences

    private var queryDomain: String = ""
    private var usernameId: AutofillId? = null
    private var passwordId: AutofillId? = null
    private var isSaveMode: Boolean = false
    private var capturedUser: String = ""
    private var capturedPass: String = ""

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

        queryDomain = intent.getStringExtra(EXTRA_QUERY_DOMAIN) ?: ""
        usernameId = intent.getParcelableExtra(EXTRA_USERNAME_ID)
        passwordId = intent.getParcelableExtra(EXTRA_PASSWORD_ID)
        isSaveMode = intent.getBooleanExtra(EXTRA_IS_SAVE_MODE, false)
        capturedUser = intent.getStringExtra(EXTRA_CAPTURED_USER) ?: ""
        capturedPass = intent.getStringExtra(EXTRA_CAPTURED_PASS) ?: ""

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
                    AutofillAuthContent(
                        queryDomain = queryDomain,
                        isSaveMode = isSaveMode,
                        capturedUser = capturedUser,
                        capturedPass = capturedPass,
                        isBiometricEnabled = preferences.isBiometricEnabled && preferences.wrappedBiometricKey != null && !repository.isBiometricLockedOut,
                        onUnlockWithPassword = { password ->
                            lifecycleScope.launch {
                                val result = repository.unlockWithPassword(password.toCharArray())
                                if (result.isSuccess) {
                                    handleUnlockSuccess()
                                } else {
                                    Toast.makeText(this@AutofillAuthActivity, "Incorrect master password", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onBiometricClick = {
                            promptBiometrics()
                        },
                        onSaveNewEntry = { title, user, pass, folder ->
                            lifecycleScope.launch {
                                val entry = VaultEntry(
                                    id = UUID.randomUUID().toString(),
                                    title = title.ifBlank { queryDomain },
                                    username = user,
                                    password = pass,
                                    url = queryDomain,
                                    folder = folder
                                )
                                repository.saveEntry(entry)
                                Toast.makeText(this@AutofillAuthActivity, "Saved to VaultKeep!", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        },
                        onSelectEntryToAutofill = { entry ->
                            deliverAutofillResult(entry)
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
        try {
            val cipher = CryptoManager.getBiometricDecryptCipher(iv)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock VaultKeep")
                .setSubtitle("Authenticate to autofill credentials")
                .setNegativeButtonText("Use Password")
                .build()

            val biometricPrompt = BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val authCipher = result.cryptoObject?.cipher ?: return
                        lifecycleScope.launch {
                            val unlockRes = repository.unlockWithBiometrics(authCipher)
                            if (unlockRes.isSuccess) {
                                handleUnlockSuccess()
                            } else {
                                Toast.makeText(this@AutofillAuthActivity, "Biometric unlock failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    override fun onAuthenticationFailed() {
                        preferences.failedBiometricAttempts += 1
                    }
                }
            )

            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } catch (_: Exception) {}
    }

    private fun handleUnlockSuccess() {
        val matches = repository.findAutofillMatches(queryDomain)
        if (matches.size == 1 && !isSaveMode) {
            deliverAutofillResult(matches.first())
        }
    }

    private fun deliverAutofillResult(entry: VaultEntry) {
        val datasetBuilder = Dataset.Builder()
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
            setTextViewText(android.R.id.text1, entry.title)
            setTextViewText(android.R.id.text2, entry.username)
        }

        if (usernameId != null && entry.username.isNotBlank()) {
            datasetBuilder.setValue(usernameId!!, AutofillValue.forText(entry.username), presentation)
        }
        if (passwordId != null && entry.password.isNotBlank()) {
            datasetBuilder.setValue(passwordId!!, AutofillValue.forText(entry.password), presentation)
        }

        val resultIntent = Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, datasetBuilder.build())
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        const val EXTRA_QUERY_DOMAIN = "extra_query_domain"
        const val EXTRA_USERNAME_ID = "extra_username_id"
        const val EXTRA_PASSWORD_ID = "extra_password_id"
        const val EXTRA_IS_SAVE_MODE = "extra_is_save_mode"
        const val EXTRA_CAPTURED_USER = "extra_captured_user"
        const val EXTRA_CAPTURED_PASS = "extra_captured_pass"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutofillAuthContent(
    queryDomain: String,
    isSaveMode: Boolean,
    capturedUser: String,
    capturedPass: String,
    isBiometricEnabled: Boolean,
    onUnlockWithPassword: (String) -> Unit,
    onBiometricClick: () -> Unit,
    onSaveNewEntry: (title: String, user: String, pass: String, folder: String) -> Unit,
    onSelectEntryToAutofill: (VaultEntry) -> Unit,
    onCancel: () -> Unit,
    repository: VaultRepository
) {
    val vaultState by repository.vaultState.collectAsState()
    var passwordInput by remember { mutableStateOf("") }
    var saveTitle by remember { mutableStateOf(queryDomain) }
    var saveUser by remember { mutableStateOf(capturedUser) }
    var savePass by remember { mutableStateOf(capturedPass) }
    var saveFolder by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isSaveMode) Icons.Default.Save else Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isSaveMode) "Save to VaultKeep" else "Autofill with VaultKeep",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onCancel, modifier = Modifier.testTag("autofill_close_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (queryDomain.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = queryDomain,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                when (val state = vaultState) {
                    is VaultState.Locked, is VaultState.Uninitialized -> {
                        Text(
                            text = "Unlock your vault with master password to continue",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Master Password") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (passwordInput.isNotBlank()) onUnlockWithPassword(passwordInput)
                            }),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("autofill_password_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isBiometricEnabled) {
                                OutlinedButton(
                                    onClick = onBiometricClick,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f).testTag("autofill_biometric_button")
                                ) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Biometric")
                                }
                            }
                            Button(
                                onClick = { if (passwordInput.isNotBlank()) onUnlockWithPassword(passwordInput) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f).testTag("autofill_unlock_button")
                            ) {
                                Text("Unlock")
                            }
                        }
                    }

                    is VaultState.Unlocked -> {
                        if (isSaveMode) {
                            OutlinedTextField(
                                value = saveTitle,
                                onValueChange = { saveTitle = it },
                                label = { Text("Title / Site") },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = saveUser,
                                onValueChange = { saveUser = it },
                                label = { Text("Username") },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = savePass,
                                onValueChange = { savePass = it },
                                label = { Text("Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = { onSaveNewEntry(saveTitle, saveUser, savePass, saveFolder) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Credentials")
                            }
                        } else {
                            val matches = remember(state.entries, queryDomain) {
                                repository.findAutofillMatches(queryDomain)
                            }

                            if (matches.isEmpty()) {
                                Text(
                                    text = "No matching credentials found in vault for $queryDomain.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Available entries:",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.align(Alignment.Start)
                                )
                            } else {
                                Text(
                                    text = "Select credentials to autofill:",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.align(Alignment.Start)
                                )
                            }

                            val entriesToShow = if (matches.isNotEmpty()) matches else state.entries.take(5)
                            for (entry in entriesToShow) {
                                OutlinedCard(
                                    onClick = { onSelectEntryToAutofill(entry) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = entry.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = entry.username,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
