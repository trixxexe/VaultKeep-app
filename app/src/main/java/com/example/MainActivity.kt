package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.biometrics.BiometricHelper
import com.example.data.CrashDiagnosticsLogger
import com.example.data.ThemeMode
import com.example.data.VaultEntry
import com.example.data.VaultPreferences
import com.example.data.VaultRepository
import com.example.data.VaultState
import com.example.ui.MainViewModel
import com.example.ui.UiEvent
import com.example.ui.components.ExportPasswordDialog
import com.example.ui.components.ImportPasswordDialog
import com.example.ui.screens.*
import com.example.ui.theme.VaultKeepTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScreenRoute {
    data object Lock : ScreenRoute
    data object VaultList : ScreenRoute
    data class EntryDetail(val entryId: String) : ScreenRoute
    data object Settings : ScreenRoute
    data object SecurityCenter : ScreenRoute
    data object VerifyVault : ScreenRoute
    data object RecoveryPhilosophy : ScreenRoute
    data object Diagnostics : ScreenRoute
}

class MainActivity : FragmentActivity() {

    private lateinit var preferences: VaultPreferences
    private lateinit var repository: VaultRepository
    private lateinit var viewModel: MainViewModel

    // Pending SAF Uri states
    private var pendingExportUri: Uri? = null
    private var pendingImportUri: Uri? = null

    // Compose state triggers for SAF password dialogs
    private val showExportDialogState = mutableStateOf(false)
    private val showImportDialogState = mutableStateOf(false)

    // SAF Activity Result Launchers
    private val exportFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            pendingExportUri = uri
            showExportDialogState.value = true
        }
    }

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportDialogState.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CrashDiagnosticsLogger.init(applicationContext)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        // Top-level Uncaught Exception Handler: Gated to debug builds for developer diagnostics
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashDiagnosticsLogger.logException("UncaughtException", throwable)
            if (BuildConfig.ENABLE_CRASH_DIAGNOSTICS_UI) {
                CrashDisplayActivity.start(applicationContext, throwable)
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            } else {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        try {
            preferences = VaultPreferences(applicationContext)
            repository = VaultRepository(applicationContext, preferences)
            viewModel = MainViewModel(applicationContext, repository, preferences)

            // Screen capture protection (FLAG_SECURE): Prevents screenshots and display capture.
            // Disabled by default to enable browser streaming preview in AI Studio, toggleable in Settings.
            if (preferences.preventScreenCapture) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            enableEdgeToEdge()

            setContent {
                val themeMode by viewModel.themeMode.collectAsState()
                val preventScreenCapture by viewModel.preventScreenCapture.collectAsState()
                val isDarkTheme = when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                }

                LaunchedEffect(preventScreenCapture) {
                    if (preventScreenCapture) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                VaultKeepTheme(darkTheme = isDarkTheme) {
                    VaultKeepApp(
                        viewModel = viewModel,
                        onTriggerBiometricUnlock = { triggerBiometricUnlock() },
                        onTriggerBiometricEnroll = { triggerBiometricEnroll() },
                        onExportRequested = { exportFileLauncher.launch("vaultkeep-backup-${System.currentTimeMillis()}.vkeep") },
                        onImportRequested = { importFileLauncher.launch(arrayOf("*/*")) },
                        showExportDialog = showExportDialogState.value,
                        onDismissExportDialog = { showExportDialogState.value = false },
                        onConfirmExport = { password ->
                            handleExport(password)
                            showExportDialogState.value = false
                        },
                        showImportDialog = showImportDialogState.value,
                        onDismissImportDialog = { showImportDialogState.value = false },
                        onConfirmImport = { password, merge ->
                            handleImport(password, merge)
                            showExportDialogState.value = false
                        }
                    )
                }
            }

            // Handle incoming share-sheet or deep-link intents
            viewModel.handleIncomingIntent(intent)

            // Auto-prompt biometrics on launch if enabled
            if (repository.isVaultCreated && preferences.isBiometricEnabled &&
                preferences.failedBiometricAttempts < VaultPreferences.MAX_FAILED_BIOMETRIC_ATTEMPTS
            ) {
                triggerBiometricUnlock()
            }
        } catch (t: Throwable) {
            CrashDiagnosticsLogger.logException("MainActivity_onCreate", t)
            if (BuildConfig.ENABLE_CRASH_DIAGNOSTICS_UI) {
                CrashDisplayActivity.start(applicationContext, t)
            }
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::viewModel.isInitialized) {
            viewModel.handleIncomingIntent(intent)
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::viewModel.isInitialized) {
            viewModel.onUserInteraction()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::viewModel.isInitialized) {
            viewModel.onAppBackgrounded(applicationContext)
        }
    }

    private fun triggerBiometricUnlock() {
        val iv = preferences.biometricIv ?: return
        BiometricHelper.showBiometricUnlockPrompt(
            activity = this,
            iv = iv,
            onSuccess = { cipher ->
                viewModel.unlockWithBiometrics(cipher)
            },
            onError = { errMsg ->
                Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show()
            },
            onFailed = {
                // Biometric mismatch handled gracefully
            }
        )
    }

    private fun triggerBiometricEnroll() {
        BiometricHelper.showBiometricEnrollPrompt(
            activity = this,
            onSuccess = { cipher ->
                viewModel.enrollBiometrics(cipher) { success, err ->
                    if (!success && err != null) {
                        Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onError = { errMsg ->
                Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun handleExport(password: CharArray) {
        val uri = pendingExportUri ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outputStream = contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    val result = repository.exportEncryptedBackup(password, outputStream)
                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            Toast.makeText(this@MainActivity, "Backup exported successfully", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Export failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                pendingExportUri = null
            }
        }
    }

    private fun handleImport(password: CharArray, merge: Boolean) {
        val uri = pendingImportUri ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val result = repository.importEncryptedBackup(password, inputStream, merge)
                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            val count = result.getOrNull() ?: 0
                            Toast.makeText(this@MainActivity, "Restored $count entries from backup", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Import failed: Invalid password or corrupted file", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Import error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                pendingImportUri = null
            }
        }
    }
}

@Composable
fun VaultKeepApp(
    viewModel: MainViewModel,
    onTriggerBiometricUnlock: () -> Unit,
    onTriggerBiometricEnroll: () -> Unit,
    onExportRequested: () -> Unit,
    onImportRequested: () -> Unit,
    showExportDialog: Boolean,
    onDismissExportDialog: () -> Unit,
    onConfirmExport: (CharArray) -> Unit,
    showImportDialog: Boolean,
    onDismissImportDialog: () -> Unit,
    onConfirmImport: (CharArray, Boolean) -> Unit
) {
    val vaultState by viewModel.vaultState.collectAsState()
    val hasSeenWelcomeSequence by viewModel.hasSeenWelcomeSequence.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val pendingSharedText by viewModel.pendingSharedText.collectAsState()
    val pendingDeepLink by viewModel.pendingDeepLink.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentRoute by remember { mutableStateOf<ScreenRoute>(ScreenRoute.VaultList) }
    var isGeneratorOpen by remember { mutableStateOf(false) }
    var entryBeingEdited by remember { mutableStateOf<VaultEntry?>(null) }
    var isAddEntryOpen by remember { mutableStateOf(false) }

    // Intercept pending share-sheet intent once vault is unlocked
    LaunchedEffect(vaultState, pendingSharedText) {
        if (vaultState is VaultState.Unlocked && pendingSharedText != null) {
            val text = pendingSharedText ?: return@LaunchedEffect
            val isUrl = text.startsWith("http://") || text.startsWith("https://")
            val guessedTitle = if (isUrl) {
                try {
                    val host = android.net.Uri.parse(text).host ?: ""
                    host.removePrefix("www.").substringBefore(".")
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                } catch (e: Exception) {
                    ""
                }
            } else ""

            entryBeingEdited = VaultEntry(
                title = guessedTitle.ifBlank { "Shared Link" },
                url = if (isUrl) text else "",
                notes = if (!isUrl) text else "",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            isAddEntryOpen = true
            viewModel.clearPendingSharedText()
        }
    }

    // Intercept pending deep-link intent once vault is unlocked
    LaunchedEffect(vaultState, pendingDeepLink, entries) {
        val linkString = pendingDeepLink ?: return@LaunchedEffect
        if (linkString == "vaultkeep://action/lock") {
            viewModel.lockVault()
            viewModel.clearPendingDeepLink()
            return@LaunchedEffect
        }
        if (vaultState is VaultState.Unlocked) {
            if (linkString == "vaultkeep://action/add") {
                entryBeingEdited = null
                isAddEntryOpen = true
                viewModel.clearPendingDeepLink()
                return@LaunchedEffect
            } else if (linkString == "vaultkeep://action/security") {
                currentRoute = ScreenRoute.SecurityCenter
                viewModel.clearPendingDeepLink()
                return@LaunchedEffect
            }

            val uri = try {
                Uri.parse(linkString)
            } catch (e: Exception) {
                null
            }
            val host = uri?.host ?: ""
            val path = uri?.path ?: ""
            // Match entry by domain or url or ID
            val matched = entries.find { entry ->
                (host.isNotBlank() && (entry.url.contains(host, ignoreCase = true) || entry.passkeyRpId.contains(host, ignoreCase = true))) ||
                (entry.id.isNotBlank() && path.contains(entry.id))
            }
            if (matched != null) {
                currentRoute = ScreenRoute.EntryDetail(matched.id)
            } else {
                entryBeingEdited = VaultEntry(
                    title = host.removePrefix("www.").substringBefore(".")
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                        .ifBlank { "New Account" },
                    url = linkString,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                isAddEntryOpen = true
            }
            viewModel.clearPendingDeepLink()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collectLatest { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is UiEvent.ShowToast -> {}
                is UiEvent.VaultLocked -> {
                    currentRoute = ScreenRoute.VaultList
                    isGeneratorOpen = false
                    isAddEntryOpen = false
                    entryBeingEdited = null
                }
            }
        }
    }

    // Backup & Restore Dialogs
    if (showExportDialog) {
        ExportPasswordDialog(
            onDismiss = onDismissExportDialog,
            onConfirmExport = onConfirmExport
        )
    }

    if (showImportDialog) {
        ImportPasswordDialog(
            onDismiss = onDismissImportDialog,
            onConfirmImport = onConfirmImport
        )
    }

    // Password Generator Modal
    if (isGeneratorOpen) {
        PasswordGeneratorDialog(
            onDismissRequest = { isGeneratorOpen = false },
            onCopyPassword = { generated ->
                viewModel.copyToClipboard(generated, "Password", true)
            }
        )
    }

    // Entry Add / Edit Sheet / Dialog
    if (isAddEntryOpen || entryBeingEdited != null) {
        DisposableEffect(Unit) {
            viewModel.setUserEditing(true)
            onDispose {
                viewModel.setUserEditing(false)
            }
        }
        EntryEditDialog(
            entryToEdit = entryBeingEdited,
            onDismissRequest = {
                isAddEntryOpen = false
                entryBeingEdited = null
                viewModel.setUserEditing(false)
            },
            onSave = { entry ->
                viewModel.saveEntry(entry) {
                    isAddEntryOpen = false
                    entryBeingEdited = null
                    viewModel.setUserEditing(false)
                }
            },
            onDelete = { entry ->
                viewModel.deleteEntry(entry.id, entry.title)
                isAddEntryOpen = false
                entryBeingEdited = null
                viewModel.setUserEditing(false)
                if (currentRoute is ScreenRoute.EntryDetail) {
                    currentRoute = ScreenRoute.VaultList
                }
            },
            onCopyPassword = { pass ->
                viewModel.copyToClipboard(pass, "Password", true)
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (vaultState) {
                is VaultState.Uninitialized -> {
                    AnimatedContent(
                        targetState = hasSeenWelcomeSequence,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) togetherWith
                            fadeOut(animationSpec = tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                        },
                        label = "welcome_sequence_handoff"
                    ) { hasSeen ->
                        if (!hasSeen) {
                            WelcomeSequenceScreen(
                                onComplete = { viewModel.markWelcomeSequenceSeen() }
                            )
                        } else {
                            LockScreen(
                                viewModel = viewModel,
                                isFirstTimeSetup = true,
                                onTriggerBiometrics = onTriggerBiometricUnlock
                            )
                        }
                    }
                }
                is VaultState.Locked -> {
                    LockScreen(
                        viewModel = viewModel,
                        isFirstTimeSetup = false,
                        onTriggerBiometrics = onTriggerBiometricUnlock
                    )
                }
                is VaultState.Unlocked -> {
                    AnimatedContent(
                        targetState = currentRoute,
                        transitionSpec = {
                            if (targetState is ScreenRoute.EntryDetail || targetState is ScreenRoute.Settings) {
                                (slideInHorizontally(
                                    initialOffsetX = { fullWidth -> (fullWidth * 0.35f).toInt() },
                                    animationSpec = tween(300)
                                ) + fadeIn(animationSpec = tween(300))).togetherWith(
                                    slideOutHorizontally(
                                        targetOffsetX = { fullWidth -> -(fullWidth * 0.35f).toInt() },
                                        animationSpec = tween(300)
                                    ) + fadeOut(animationSpec = tween(300))
                                )
                            } else {
                                (slideInHorizontally(
                                    initialOffsetX = { fullWidth -> -(fullWidth * 0.35f).toInt() },
                                    animationSpec = tween(300)
                                ) + fadeIn(animationSpec = tween(300))).togetherWith(
                                    slideOutHorizontally(
                                        targetOffsetX = { fullWidth -> (fullWidth * 0.35f).toInt() },
                                        animationSpec = tween(300)
                                    ) + fadeOut(animationSpec = tween(300))
                                )
                            }
                        },
                        label = "screen_slide_transition"
                    ) { route ->
                        when (route) {
                            is ScreenRoute.VaultList -> {
                                VaultListScreen(
                                    viewModel = viewModel,
                                    onAddNewEntry = { isAddEntryOpen = true },
                                    onEditEntry = { entry ->
                                        currentRoute = ScreenRoute.EntryDetail(entry.id)
                                    },
                                    onOpenGenerator = { isGeneratorOpen = true },
                                    onOpenSettings = { currentRoute = ScreenRoute.Settings },
                                    onOpenSecurityCenter = { currentRoute = ScreenRoute.SecurityCenter }
                                )
                            }
                            is ScreenRoute.SecurityCenter -> {
                                BackHandler {
                                    currentRoute = ScreenRoute.VaultList
                                }
                                SecurityCenterScreen(
                                    viewModel = viewModel,
                                    onBack = { currentRoute = ScreenRoute.VaultList },
                                    onNavigateToEntry = { entryId ->
                                        currentRoute = ScreenRoute.EntryDetail(entryId)
                                    },
                                    onVerifyVaultRequested = { currentRoute = ScreenRoute.VerifyVault }
                                )
                            }
                            is ScreenRoute.VerifyVault -> {
                                BackHandler {
                                    currentRoute = ScreenRoute.Settings
                                }
                                VerifyVaultScreen(
                                    viewModel = viewModel,
                                    onBack = { currentRoute = ScreenRoute.Settings }
                                )
                            }
                            is ScreenRoute.RecoveryPhilosophy -> {
                                BackHandler {
                                    currentRoute = ScreenRoute.Settings
                                }
                                RecoveryInfoScreen(
                                    onBack = { currentRoute = ScreenRoute.Settings },
                                    onExportRequested = onExportRequested,
                                    viewModel = viewModel
                                )
                            }
                            is ScreenRoute.Settings -> {
                                BackHandler {
                                    currentRoute = ScreenRoute.VaultList
                                }
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onBack = { currentRoute = ScreenRoute.VaultList },
                                    onEnrollBiometricsRequested = onTriggerBiometricEnroll,
                                    onExportBackupRequested = onExportRequested,
                                    onImportBackupRequested = onImportRequested,
                                    onNavigateToSecurityCenter = { currentRoute = ScreenRoute.SecurityCenter },
                                    onNavigateToVerifyVault = { currentRoute = ScreenRoute.VerifyVault },
                                    onNavigateToRecoveryPhilosophy = { currentRoute = ScreenRoute.RecoveryPhilosophy },
                                    onNavigateToDiagnostics = { currentRoute = ScreenRoute.Diagnostics }
                                )
                            }
                            is ScreenRoute.Diagnostics -> {
                                BackHandler {
                                    currentRoute = ScreenRoute.Settings
                                }
                                DiagnosticsScreen(
                                    viewModel = viewModel,
                                    onBack = { currentRoute = ScreenRoute.Settings }
                                )
                            }
                            is ScreenRoute.EntryDetail -> {
                                BackHandler {
                                    currentRoute = ScreenRoute.VaultList
                                }
                                val selectedEntry = entries.find { it.id == route.entryId }
                                if (selectedEntry != null) {
                                    EntryDetailScreen(
                                        entry = selectedEntry,
                                        viewModel = viewModel,
                                        onBack = { currentRoute = ScreenRoute.VaultList },
                                        onEdit = { entryBeingEdited = selectedEntry },
                                        onDelete = {
                                            viewModel.deleteEntry(selectedEntry.id, selectedEntry.title)
                                            currentRoute = ScreenRoute.VaultList
                                        }
                                    )
                                } else {
                                    currentRoute = ScreenRoute.VaultList
                                }
                            }
                            is ScreenRoute.Lock -> {}
                        }
                    }
                }
            }
        }
    }
}

