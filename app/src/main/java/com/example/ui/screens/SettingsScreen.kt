package com.example.ui.screens

import android.content.Intent
import com.example.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ThemeMode
import com.example.data.VaultPreferences
import com.example.sync.SyncStatus
import com.example.sync.SyncType
import com.example.ui.MainViewModel
import com.example.ui.theme.SecurityEmerald
import com.example.ui.theme.SecurityRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onEnrollBiometricsRequested: () -> Unit,
    onExportBackupRequested: () -> Unit,
    onImportBackupRequested: () -> Unit,
    onNavigateToSecurityCenter: () -> Unit = {},
    onNavigateToVerifyVault: () -> Unit = {},
    onNavigateToRecoveryPhilosophy: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {}
) {
    val prefs = viewModel.preferences
    var isBiometricEnabled by remember { mutableStateOf(prefs.isBiometricEnabled) }
    var autoLockTimeout by remember { mutableStateOf(prefs.autoLockTimeoutSeconds) }
    var clipboardTimeout by remember { mutableStateOf(prefs.clipboardClearTimeoutSeconds) }
    var lockOnBackground by remember { mutableStateOf(prefs.lockOnBackground) }
    var preventScreenCapture by remember { mutableStateOf(prefs.preventScreenCapture) }
    var themeMode by remember { mutableStateOf(prefs.themeMode) }

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showAutoLockDialog by remember { mutableStateOf(false) }
    var showClipboardDialog by remember { mutableStateOf(false) }

    val importResult by viewModel.importResult.collectAsState()
    var showUnencryptedExportWarning by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf<String?>(null) }
    var showEmergencyKitDialog by remember { mutableStateOf(false) }
    var emergencyKitContent by remember { mutableStateOf("") }

    val context = LocalContext.current

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    val res = viewModel.exportToCsv(out)
                    if (res.isSuccess) {
                        android.widget.Toast.makeText(context, "Exported ${res.getOrNull()} entries to CSV", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, "CSV export failed: ${res.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    val res = viewModel.exportToJson(out)
                    if (res.isSuccess) {
                        android.widget.Toast.makeText(context, "Exported ${res.getOrNull()} entries to JSON", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, "JSON export failed: ${res.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val importFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    viewModel.parseImportFile(inputStream)
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Import parse error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showUnencryptedExportWarning) {
        AlertDialog(
            onDismissRequest = {
                showUnencryptedExportWarning = false
                pendingExportFormat = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = SecurityRed,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Export Plaintext Data?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "WARNING: The exported $pendingExportFormat file will NOT be encrypted. Anyone who accesses this file will be able to read your passwords in plain text.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Store it in a secure location or delete it immediately after migrating.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val format = pendingExportFormat
                        showUnencryptedExportWarning = false
                        pendingExportFormat = null
                        if (format == "CSV") {
                            csvExportLauncher.launch("vaultkeep-export-${System.currentTimeMillis()}.csv")
                        } else if (format == "JSON") {
                            jsonExportLauncher.launch("vaultkeep-export-${System.currentTimeMillis()}.json")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SecurityRed)
                ) {
                    Text("Export Unencrypted")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnencryptedExportWarning = false
                        pendingExportFormat = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEmergencyKitDialog) {
        AlertDialog(
            onDismissRequest = { showEmergencyKitDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text("Emergency Recovery Kit", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Print or write down this zero-knowledge recovery kit and store it in a secure physical location (safe, deposit box).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = emergencyKitContent,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.copyToClipboard(context, "Recovery Kit", emergencyKitContent, false)
                        showEmergencyKitDialog = false
                    }
                ) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyKitDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Import Preview Dialog
    if (importResult != null) {
        val result = importResult!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportPreview() },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Import Credentials", fontWeight = FontWeight.Bold)
                    Text(
                        text = "Source: ${result.detectedFormat.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Found ${result.totalCount} items (${result.duplicateCount} duplicates detected).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(result.candidates.size) { idx ->
                            val cand = result.candidates[idx]
                            Surface(
                                color = if (cand.isDuplicate) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleImportCandidate(idx) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        androidx.compose.material3.Checkbox(
                                            checked = cand.isSelected,
                                            onCheckedChange = { viewModel.toggleImportCandidate(idx) }
                                        )
                                        Column {
                                            Text(
                                                text = cand.entry.title.ifBlank { "Untitled" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = cand.entry.username.ifBlank { cand.entry.url.ifBlank { "No details" } },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    if (cand.isDuplicate) {
                                        Text(
                                            text = "DUPLICATE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val selectedCount = result.candidates.count { it.isSelected }
                Button(
                    onClick = { viewModel.confirmImportSelected() },
                    enabled = selectedCount > 0
                ) {
                    Text("Import ($selectedCount)")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissImportPreview() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAutoLockDialog) {
        AutoLockTimeoutDialog(
            currentTimeout = autoLockTimeout,
            onDismiss = { showAutoLockDialog = false },
            onSelect = {
                autoLockTimeout = it
                viewModel.setAutoLockTimeout(it)
                showAutoLockDialog = false
            }
        )
    }

    if (showClipboardDialog) {
        ClipboardTimeoutDialog(
            currentTimeout = clipboardTimeout,
            onDismiss = { showClipboardDialog = false },
            onSelect = {
                clipboardTimeout = it
                viewModel.setClipboardTimeout(it)
                showClipboardDialog = false
            }
        )
    }

    if (showChangePasswordDialog) {
        ChangeMasterPasswordDialog(
            onDismiss = { showChangePasswordDialog = false },
            onChangePassword = { oldPass, newPass, onResult ->
                viewModel.changeMasterPassword(oldPass, newPass) { success, err ->
                    if (success) {
                        isBiometricEnabled = false
                        showChangePasswordDialog = false
                    }
                    onResult(success, err)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Security & Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Authentication & Security
            SettingsSectionHeader(title = "AUTHENTICATION & ACCESS")

            val isSecurityKeyEnrolled by viewModel.isSecurityKeyEnrolled.collectAsState()
            val securityKeyName by viewModel.securityKeyName.collectAsState()
            val context = LocalContext.current
            var showSecurityKeyEnrollDialog by remember { mutableStateOf(false) }

            SettingsCard {
                // Biometric Switch
                SettingsRow(
                    icon = Icons.Default.Fingerprint,
                    title = "Biometric Unlock",
                    subtitle = "Release AES key via hardware Keystore biometric prompt"
                ) {
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { enable ->
                            if (enable) {
                                onEnrollBiometricsRequested()
                            } else {
                                viewModel.disableBiometrics()
                                isBiometricEnabled = false
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("biometric_switch")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                // Hardware Security Key (FIDO2 / WebAuthn)
                SettingsRow(
                    icon = Icons.Default.VpnKey,
                    title = "Hardware Security Key (FIDO2)",
                    subtitle = if (isSecurityKeyEnrolled) "Enrolled: $securityKeyName" else "Tap to enroll YubiKey / FIDO2 key as optional 2FA"
                ) {
                    if (isSecurityKeyEnrolled) {
                        OutlinedButton(
                            onClick = { viewModel.removeHardwareKey() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SecurityRed),
                            modifier = Modifier.testTag("remove_hardware_key_button")
                        ) {
                            Text("Remove", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.enrollHardwareKey("Hardware Key")
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("enroll_hardware_key_button")
                        ) {
                            Text("Enroll", fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                // Change Master Password
                SettingsClickableRow(
                    icon = Icons.Default.Key,
                    title = "Change Master Password",
                    subtitle = "Re-encrypts vault with new derived PBKDF2 key",
                    onClick = { showChangePasswordDialog = true },
                    testTag = "change_password_row"
                )
            }

            // Section 2: Session & Clipboard
            SettingsSectionHeader(title = "SESSION & HYGIENE")

            SettingsCard {
                // Auto-lock Timeout
                val autoLockLabel = when (autoLockTimeout) {
                    0L -> "Immediately on background"
                    30L -> "30 seconds"
                    60L -> "1 minute (Default)"
                    120L -> "2 minutes"
                    300L -> "5 minutes"
                    -1L -> "Never (Inactivity only)"
                    else -> "$autoLockTimeout seconds"
                }

                SettingsClickableRow(
                    icon = Icons.Default.LockClock,
                    title = "Auto-Lock Timeout",
                    subtitle = autoLockLabel,
                    onClick = { showAutoLockDialog = true },
                    testTag = "auto_lock_row"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                // Lock on Background Switch
                SettingsRow(
                    icon = Icons.Default.PhonelinkLock,
                    title = "Lock on App Background",
                    subtitle = "Immediately lock vault whenever leaving the app"
                ) {
                    Switch(
                        checked = lockOnBackground,
                        onCheckedChange = {
                            lockOnBackground = it
                            viewModel.setLockOnBackground(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("lock_on_background_switch")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                // Screen Capture Protection Switch (FLAG_SECURE)
                SettingsRow(
                    icon = Icons.Default.Security,
                    title = "Screen Capture Protection",
                    subtitle = "Blocks screenshots, screen recording, and display mirrors (FLAG_SECURE)"
                ) {
                    Switch(
                        checked = preventScreenCapture,
                        onCheckedChange = {
                            preventScreenCapture = it
                            viewModel.setPreventScreenCapture(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("prevent_screen_capture_switch")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                // Clipboard Auto-Clear Timeout
                val clipboardLabel = when (clipboardTimeout) {
                    10L -> "10 seconds"
                    30L -> "30 seconds (Default)"
                    60L -> "60 seconds"
                    120L -> "2 minutes"
                    else -> "$clipboardTimeout seconds"
                }

                SettingsClickableRow(
                    icon = Icons.Default.Timer,
                    title = "Auto-Clear Clipboard",
                    subtitle = clipboardLabel,
                    onClick = { showClipboardDialog = true },
                    testTag = "clipboard_timeout_row"
                )
            }

            // Section 3: Backup Health & Restore
            val (backupStatusLabel, isBackupRecommended) = viewModel.getBackupStatusSummary()
            SettingsSectionHeader(title = "BACKUP HEALTH & ARCHIVES")

            SettingsCard {
                // Backup Health status row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isBackupRecommended) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isBackupRecommended) Color(0xFFFFB74D) else SecurityEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Backup Status",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = backupStatusLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isBackupRecommended) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFB74D).copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Recommended",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB74D)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SecurityEmerald.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Healthy",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SecurityEmerald
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                SettingsClickableRow(
                    icon = Icons.Default.FileUpload,
                    title = "Export Encrypted Backup",
                    subtitle = "Test-decrypts & saves AES-256-GCM .vkeep vault via SAF",
                    onClick = onExportBackupRequested,
                    testTag = "export_backup_row"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                SettingsClickableRow(
                    icon = Icons.Default.FileDownload,
                    title = "Import Encrypted Backup",
                    subtitle = "Restore or merge an existing encrypted vault file",
                    onClick = onImportBackupRequested,
                    testTag = "import_backup_row"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                SettingsClickableRow(
                    icon = Icons.Default.Description,
                    title = "Export Plaintext CSV",
                    subtitle = "Standard RFC-4180 format for Bitwarden, KeePass, Chrome",
                    onClick = {
                        pendingExportFormat = "CSV"
                        showUnencryptedExportWarning = true
                    },
                    testTag = "export_csv_row"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                SettingsClickableRow(
                    icon = Icons.Default.Code,
                    title = "Export Plaintext JSON",
                    subtitle = "Full decrypted vault data dump in clean JSON format",
                    onClick = {
                        pendingExportFormat = "JSON"
                        showUnencryptedExportWarning = true
                    },
                    testTag = "export_json_row"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                SettingsClickableRow(
                    icon = Icons.Default.DriveFolderUpload,
                    title = "Import Credentials (CSV / JSON)",
                    subtitle = "Import from Bitwarden, Chrome, Firefox, KeePass, 1Password",
                    onClick = {
                        importFilePickerLauncher.launch(arrayOf("text/*", "application/json", "*/*"))
                    },
                    testTag = "import_credentials_row"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                SettingsClickableRow(
                    icon = Icons.Default.HealthAndSafety,
                    title = "Emergency Recovery Kit",
                    subtitle = "Generate printable zero-knowledge emergency recovery sheet",
                    onClick = {
                        emergencyKitContent = viewModel.generateEmergencyRecoveryKit()
                        showEmergencyKitDialog = true
                    },
                    testTag = "emergency_kit_row"
                )
            }

            // Section 4: Optional Self-Hosted Synchronization
            val currentSyncType by viewModel.syncType.collectAsState()
            val syncStatus by viewModel.syncStatus.collectAsState()
            val webDavUrl by viewModel.webDavServerUrl.collectAsState()
            val webDavUsername by viewModel.webDavUsername.collectAsState()
            val webDavPassword by viewModel.webDavPassword.collectAsState()
            val webDavRemotePath by viewModel.webDavRemotePath.collectAsState()
            val localFolderTreeUri by viewModel.localFolderTreeUri.collectAsState()

            val folderPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri != null) {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    try {
                        context.contentResolver.takePersistableUriPermission(uri, flags)
                    } catch (e: Exception) {
                        // ignore if already granted or unsupported
                    }
                    viewModel.updateLocalFolderTreeUri(uri.toString())
                    viewModel.setSyncType(SyncType.LOCAL_FOLDER)
                }
            }

            SettingsSectionHeader(title = "OPTIONAL SELF-HOSTED SYNC")

            SettingsCard {
                // Info banner explaining zero-knowledge offline architecture
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp).padding(top = 2.dp)
                        )
                        Text(
                            text = "VaultKeep is 100% offline-first. Sync is completely optional and zero-knowledge: only your already-encrypted AES-256-GCM vault container is transferred.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                // Sync Mode Selector
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Sync Provider",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentSyncType == SyncType.DISABLED,
                            onClick = {
                                viewModel.setSyncType(SyncType.DISABLED)
                            },
                            label = { Text("Disabled") }
                        )

                        FilterChip(
                            selected = currentSyncType == SyncType.WEBDAV,
                            onClick = {
                                viewModel.setSyncType(SyncType.WEBDAV)
                            },
                            label = { Text("WebDAV / Nextcloud") }
                        )

                        FilterChip(
                            selected = currentSyncType == SyncType.LOCAL_FOLDER,
                            onClick = {
                                viewModel.setSyncType(SyncType.LOCAL_FOLDER)
                            },
                            label = { Text("Synced Folder") }
                        )
                    }
                }

                // WebDAV Configuration Fields
                if (currentSyncType == SyncType.WEBDAV) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = webDavUrl,
                            onValueChange = { viewModel.updateWebDavServerUrl(it) },
                            label = { Text("WebDAV Server URL") },
                            placeholder = { Text("https://nextcloud.example.com/remote.php/dav/files/user/") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = webDavUsername,
                            onValueChange = { viewModel.updateWebDavUsername(it) },
                            label = { Text("Username") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = webDavPassword,
                            onValueChange = { viewModel.updateWebDavPassword(it) },
                            label = { Text("App Password / Token") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = webDavRemotePath,
                            onValueChange = { viewModel.updateWebDavRemotePath(it) },
                            label = { Text("Remote Vault File Path") },
                            placeholder = { Text("/VaultKeep/vault.vk") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.testSyncConnection() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Test Connection")
                            }

                            Button(
                                onClick = { viewModel.syncNow() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Sync Now")
                            }
                        }
                    }
                }

                // Synced Folder (SAF / Syncthing) Configuration
                if (currentSyncType == SyncType.LOCAL_FOLDER) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (localFolderTreeUri.isNotBlank())
                                "Selected Folder: $localFolderTreeUri"
                            else
                                "No folder selected. Pick a directory managed by Syncthing, Nextcloud, or Drive.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { folderPickerLauncher.launch(null) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Choose Folder")
                            }

                            Button(
                                onClick = { viewModel.syncNow() },
                                enabled = localFolderTreeUri.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Sync Now")
                            }
                        }
                    }
                }

                // Sync Status / Conflict Resolution Footer
                if (currentSyncType != SyncType.DISABLED) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (val status = syncStatus) {
                                is SyncStatus.Idle -> {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SecurityEmerald, modifier = Modifier.size(16.dp))
                                    Text("Sync Ready / Idle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                is SyncStatus.Syncing -> {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Text("Synchronizing...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                                is SyncStatus.Success -> {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SecurityEmerald, modifier = Modifier.size(16.dp))
                                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(status.lastSyncTime))
                                    Text("Last synced at $time", style = MaterialTheme.typography.bodySmall, color = SecurityEmerald)
                                }
                                is SyncStatus.Error -> {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = SecurityRed, modifier = Modifier.size(16.dp))
                                    Text("Sync error: ${status.message}", style = MaterialTheme.typography.bodySmall, color = SecurityRed)
                                }
                                is SyncStatus.Conflict -> {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(16.dp))
                                    Text("Conflict detected: Remote vault modified elsewhere.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Conflict Resolution Buttons
                        if (syncStatus is SyncStatus.Conflict) {
                            val conflict = syncStatus as SyncStatus.Conflict

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.resolveConflictKeepLocal() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Keep Local", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.resolveConflictKeepRemote(conflict.remotePayloadBytes) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Use Remote", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Section 5: Security Hardening & Diagnostics
            SettingsSectionHeader(title = "SECURITY AUDIT & DIAGNOSTICS")

            SettingsCard {
                SettingsClickableRow(
                    icon = Icons.Default.Security,
                    title = "Security Center",
                    subtitle = "Scan for weak, reused, duplicate, or old passwords",
                    onClick = onNavigateToSecurityCenter,
                    testTag = "settings_security_center_row"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                SettingsClickableRow(
                    icon = Icons.Default.Shield,
                    title = "Verify My Vault",
                    subtitle = "Run container, AEAD GCM tag, and crypto parameter check",
                    onClick = onNavigateToVerifyVault,
                    testTag = "settings_verify_vault_row"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                SettingsClickableRow(
                    icon = Icons.Default.Lock,
                    title = "About Vault Recovery",
                    subtitle = "Zero-backdoor philosophy & self-custodial backup guidance",
                    onClick = onNavigateToRecoveryPhilosophy,
                    testTag = "settings_recovery_philosophy_row"
                )

                if (BuildConfig.ENABLE_CRASH_DIAGNOSTICS_UI) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                    SettingsClickableRow(
                        icon = Icons.Default.BugReport,
                        title = "Diagnostics & Error Logs",
                        subtitle = "Inspect sanitized on-device error logs and copy crash reports",
                        onClick = onNavigateToDiagnostics,
                        testTag = "settings_diagnostics_row"
                    )
                }
            }

            // Section 4: Appearance / Theme
            SettingsSectionHeader(title = "APPEARANCE")

            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (themeMode == ThemeMode.DARK) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Theme Mode",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (themeMode) {
                                    ThemeMode.SYSTEM -> "System Default"
                                    ThemeMode.DARK -> "Dark Theme"
                                    ThemeMode.LIGHT -> "Light Theme"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ThemeOptionChip(
                            label = "Sys",
                            selected = themeMode == ThemeMode.SYSTEM,
                            onClick = {
                                themeMode = ThemeMode.SYSTEM
                                viewModel.setThemeMode(ThemeMode.SYSTEM)
                            }
                        )
                        ThemeOptionChip(
                            label = "Dark",
                            selected = themeMode == ThemeMode.DARK,
                            onClick = {
                                themeMode = ThemeMode.DARK
                                viewModel.setThemeMode(ThemeMode.DARK)
                            }
                        )
                        ThemeOptionChip(
                            label = "Light",
                            selected = themeMode == ThemeMode.LIGHT,
                            onClick = {
                                themeMode = ThemeMode.LIGHT
                                viewModel.setThemeMode(ThemeMode.LIGHT)
                            }
                        )
                    }
                }
            }

            // Section 5: Developer Credits & Architecture Audit
            SettingsSectionHeader(title = "ABOUT & SECURITY AUDIT")

            SettingsCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "VaultKeep v1.0",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Local-First Offline Password Vault",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Zero network permissions in AndroidManifest. Zero cloud sync or telemetry. All vault data is locally encrypted using AES-256-GCM with 12-byte random IVs and PBKDF2-HMAC-SHA256 (310,000 iterations). Hardware biometric unlock is bound directly to Android Keystore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Developer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "VaultKeep Core Engineering Team",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    action: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        action()
    }
}

@Composable
private fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AutoLockTimeoutDialog(
    currentTimeout: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val options = listOf(
        0L to "Immediately on background",
        30L to "30 seconds",
        60L to "1 minute (Default)",
        120L to "2 minutes",
        300L to "5 minutes",
        -1L to "Never (Stay unlocked until app killed)"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Auto-Lock Timeout",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                options.forEach { (seconds, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(seconds) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTimeout == seconds,
                            onClick = { onSelect(seconds) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardTimeoutDialog(
    currentTimeout: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val options = listOf(
        10L to "10 seconds",
        30L to "30 seconds (Default)",
        60L to "60 seconds (1 min)",
        120L to "120 seconds (2 min)"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Clipboard Auto-Clear Delay",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                options.forEach { (seconds, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(seconds) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTimeout == seconds,
                            onClick = { onSelect(seconds) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeMasterPasswordDialog(
    onDismiss: () -> Unit,
    onChangePassword: (CharArray, CharArray, (Boolean, String?) -> Unit) -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.testTag("change_master_password_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Change Master Password",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Re-encrypts your entire vault with a fresh random salt and derived key. Biometric unlock will be reset.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecurityRed
                    )
                }

                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = {
                        oldPassword = it
                        errorMessage = null
                    },
                    label = { Text("Current Master Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("current_master_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text("New Master Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_master_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("Confirm New Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_new_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (oldPassword.isBlank()) {
                                errorMessage = "Please enter current master password"
                                return@Button
                            }
                            if (newPassword.length < 8) {
                                errorMessage = "New password must be at least 8 characters"
                                return@Button
                            }
                            if (newPassword != confirmPassword) {
                                errorMessage = "New passwords do not match"
                                return@Button
                            }

                            isProcessing = true
                            onChangePassword(
                                oldPassword.toCharArray(),
                                newPassword.toCharArray()
                            ) { success, err ->
                                isProcessing = false
                                if (!success) {
                                    errorMessage = err ?: "Failed to re-encrypt vault"
                                }
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(if (isProcessing) "Encrypting..." else "Update")
                    }
                }
            }
        }
    }
}
