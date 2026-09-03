package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.biometrics.BiometricHelper
import com.example.crypto.PasswordConfig
import com.example.crypto.PasswordGenerator
import com.example.data.CrashDiagnosticsLogger
import com.example.data.EntryType
import com.example.data.ThemeMode
import com.example.data.VaultEntry
import com.example.data.VaultPreferences
import com.example.data.VaultRepository
import com.example.data.VaultSortMode
import com.example.data.VaultState
import com.example.data.OfflineBreachDatabase
import com.example.data.BreachMatch
import com.example.data.BreachDatabaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.crypto.Cipher

sealed interface UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent
    data class ShowToast(val message: String) : UiEvent
    data object VaultLocked : UiEvent
}

sealed interface BreachCheckState {
    data object Idle : BreachCheckState
    data object Checking : BreachCheckState
    data class Breached(val match: BreachMatch) : BreachCheckState
    data class Safe(val checkedCount: Int) : BreachCheckState
}

class MainViewModel(
    private val context: Context,
    private val repository: VaultRepository,
    val preferences: VaultPreferences
) : ViewModel() {

    private val syncManager = com.example.sync.VaultSyncManager(context.applicationContext, repository, preferences)
    val syncStatus: StateFlow<com.example.sync.SyncStatus> = syncManager.syncStatus

    val breachCatalog: List<BreachDatabaseInfo> = OfflineBreachDatabase.catalog
    val totalBreachRecordsCount: Int = OfflineBreachDatabase.totalRecordsCount

    private val _offlineBreachMatches = MutableStateFlow<Map<String, BreachMatch>>(emptyMap())
    val offlineBreachMatches: StateFlow<Map<String, BreachMatch>> = _offlineBreachMatches.asStateFlow()

    private val _onDemandCheckQuery = MutableStateFlow("")
    val onDemandCheckQuery: StateFlow<String> = _onDemandCheckQuery.asStateFlow()

    private val _onDemandCheckState = MutableStateFlow<BreachCheckState>(BreachCheckState.Idle)
    val onDemandCheckState: StateFlow<BreachCheckState> = _onDemandCheckState.asStateFlow()

    init {
        // Collect external lock events (e.g. Quick Settings Tile)
        viewModelScope.launch {
            com.example.data.VaultLockNotifier.lockEvents.collect {
                _revealedPasswordIds.value = emptySet()
                _uiEvents.emit(UiEvent.VaultLocked)
            }
        }

        // Automatic streaming offline breach audit across all vault entries whenever unlocked
        viewModelScope.launch(Dispatchers.IO) {
            vaultState.collect { state ->
                if (state is VaultState.Unlocked) {
                    val passwords = state.entries
                        .filterNot { it.isDeleted }
                        .map { it.password }
                        .filter { it.isNotBlank() }
                        .toSet()
                    val matches = OfflineBreachDatabase.checkVaultPasswords(context, passwords)
                    _offlineBreachMatches.value = matches
                } else {
                    _offlineBreachMatches.value = emptyMap()
                }
            }
        }
    }

    val vaultState: StateFlow<VaultState> = repository.vaultState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortMode = MutableStateFlow(preferences.sortMode)
    val sortMode: StateFlow<VaultSortMode> = _sortMode.asStateFlow()

    private val _selectedFolder = MutableStateFlow(preferences.selectedFolder)
    val selectedFolder: StateFlow<String> = _selectedFolder.asStateFlow()

    private val _selectedTag = MutableStateFlow("")
    val selectedTag: StateFlow<String> = _selectedTag.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow<EntryType?>(null)
    val selectedTypeFilter: StateFlow<EntryType?> = _selectedTypeFilter.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(preferences.hasCompletedOnboarding)
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    private val _hasSeenWelcomeSequence = MutableStateFlow(preferences.hasSeenWelcomeSequence)
    val hasSeenWelcomeSequence: StateFlow<Boolean> = _hasSeenWelcomeSequence.asStateFlow()

    fun markWelcomeSequenceSeen() {
        preferences.hasSeenWelcomeSequence = true
        _hasSeenWelcomeSequence.value = true
    }

    val repositoryRef: VaultRepository get() = repository

    private val _showWhatsNewDialog = MutableStateFlow(false)
    val showWhatsNewDialog: StateFlow<Boolean> = _showWhatsNewDialog.asStateFlow()

    private val _widgetShowNamesOnly = MutableStateFlow(preferences.widgetShowNamesOnly)
    val widgetShowNamesOnly: StateFlow<Boolean> = _widgetShowNamesOnly.asStateFlow()

    private val _revealedPasswordIds = MutableStateFlow<Set<String>>(emptySet())
    val revealedPasswordIds: StateFlow<Set<String>> = _revealedPasswordIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private val _themeMode = MutableStateFlow(preferences.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _preventScreenCapture = MutableStateFlow(preferences.preventScreenCapture)
    val preventScreenCapture: StateFlow<Boolean> = _preventScreenCapture.asStateFlow()

    // Incoming Share-Sheet & Deep-Link pending intent states (must route through vault unlock)
    private val _pendingSharedText = MutableStateFlow<String?>(null)
    val pendingSharedText: StateFlow<String?> = _pendingSharedText.asStateFlow()

    private val _pendingDeepLink = MutableStateFlow<String?>(null)
    val pendingDeepLink: StateFlow<String?> = _pendingDeepLink.asStateFlow()

    // Real-time tick for TOTP countdowns & codes
    private val _totpTick = MutableStateFlow(System.currentTimeMillis())
    val totpTick: StateFlow<Long> = _totpTick.asStateFlow()

    // Security Audit & Integrity Check State
    private val _securityScanTick = MutableStateFlow(System.currentTimeMillis())
    val securityScanTick: StateFlow<Long> = _securityScanTick.asStateFlow()

    private val _selectedIssueFilter = MutableStateFlow<com.example.data.SecurityIssueType?>(null)
    val selectedIssueFilter: StateFlow<com.example.data.SecurityIssueType?> = _selectedIssueFilter.asStateFlow()

    private val _integrityReport = MutableStateFlow<com.example.data.VaultIntegrityReport?>(null)
    val integrityReport: StateFlow<com.example.data.VaultIntegrityReport?> = _integrityReport.asStateFlow()

    private val _isVerifyingIntegrity = MutableStateFlow(false)
    val isVerifyingIntegrity: StateFlow<Boolean> = _isVerifyingIntegrity.asStateFlow()

    val securityAuditReport: StateFlow<com.example.data.SecurityAuditReport> = combine(
        vaultState,
        _securityScanTick,
        _offlineBreachMatches
    ) { state, _, breachMatches ->
        if (state is VaultState.Unlocked) {
            com.example.data.SecurityScanner.scanVault(
                entries = state.entries,
                breachMatches = breachMatches,
                oldThresholdMonths = preferences.oldPasswordThresholdMonths
            )
        } else {
            com.example.data.SecurityAuditReport(
                totalEntries = 0,
                strongPasswords = emptyList(),
                weakPasswords = emptyList(),
                breachMatchMap = emptyMap(),
                reusedPasswordGroups = emptyMap(),
                duplicateEntryGroups = emptyList(),
                oldPasswords = emptyList(),
                missingUsernames = emptyList(),
                healthScorePercentage = 100
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.data.SecurityAuditReport(
            totalEntries = 0,
            strongPasswords = emptyList(),
            weakPasswords = emptyList(),
            breachMatchMap = emptyMap(),
            reusedPasswordGroups = emptyMap(),
            duplicateEntryGroups = emptyList(),
            oldPasswords = emptyList(),
            missingUsernames = emptyList(),
            healthScorePercentage = 100
        )
    )

    fun setOnDemandCheckQuery(query: String) {
        _onDemandCheckQuery.value = query
        if (query.isBlank()) {
            _onDemandCheckState.value = BreachCheckState.Idle
        }
    }

    fun runOnDemandBreachCheck(password: String = _onDemandCheckQuery.value) {
        if (password.isBlank()) {
            _onDemandCheckState.value = BreachCheckState.Idle
            return
        }
        viewModelScope.launch {
            _onDemandCheckState.value = BreachCheckState.Checking
            val match = OfflineBreachDatabase.checkSinglePassword(context, password)
            if (match != null) {
                _onDemandCheckState.value = BreachCheckState.Breached(match)
            } else {
                _onDemandCheckState.value = BreachCheckState.Safe(OfflineBreachDatabase.totalRecordsCount)
            }
        }
    }

    fun checkFastBreach(password: String): BreachMatch? {
        return OfflineBreachDatabase.checkFastSync(context, password)
    }

    suspend fun checkSinglePasswordBreach(password: String): BreachMatch? {
        return OfflineBreachDatabase.checkSinglePassword(context, password)
    }

    val folders: StateFlow<List<String>> = combine(vaultState) { stateArray ->
        val state = stateArray.first()
        if (state is VaultState.Unlocked) state.folders else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tags: StateFlow<List<String>> = combine(vaultState) { stateArray ->
        val state = stateArray.first()
        if (state is VaultState.Unlocked) {
            val fromPayload = state.tags
            val fromEntries = state.entries.flatMap { it.tags }
            (fromPayload + fromEntries).filter { it.isNotBlank() }.distinct().sorted()
        } else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val entries: StateFlow<List<VaultEntry>> = combine(vaultState) { stateArray ->
        val state = stateArray.first()
        if (state is VaultState.Unlocked) state.entries.filterNot { it.isDeleted } else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashEntries: StateFlow<List<VaultEntry>> = combine(vaultState) { stateArray ->
        val state = stateArray.first()
        if (state is VaultState.Unlocked) state.entries.filter { it.isDeleted } else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Multi-Selection State for Batch Operations
    private val _selectedEntryIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedEntryIds: StateFlow<Set<String>> = _selectedEntryIds.asStateFlow()

    fun toggleEntrySelection(id: String) {
        val current = _selectedEntryIds.value
        _selectedEntryIds.value = if (current.contains(id)) current - id else current + id
    }

    fun toggleSelectEntry(id: String) = toggleEntrySelection(id)

    fun selectAll(ids: List<String>) {
        _selectedEntryIds.value = ids.toSet()
    }

    fun clearSelection() {
        _selectedEntryIds.value = emptySet()
    }

    // Filtered & Sorted entries according to search query, folder, tag, type filter, sort mode, and favorites
    val filteredEntries: StateFlow<List<VaultEntry>> = combine(
        combine(vaultState, searchQuery, sortMode, ::Triple),
        combine(selectedFolder, selectedTag, selectedTypeFilter, ::Triple)
    ) { (state, query, sort), (folder, tag, typeFilter) ->
        if (state is VaultState.Unlocked) {
            val isTrashFolder = folder == "__trash__" || folder.equals("Trash", ignoreCase = true)
            var list = if (isTrashFolder) {
                state.entries.filter { it.isDeleted }
            } else {
                state.entries.filterNot { it.isDeleted }
            }

            // Folder filtering
            if (!isTrashFolder && folder.isNotBlank() && folder != "All") {
                list = if (folder == "Favorites") {
                    list.filter { it.isFavorite }
                } else {
                    list.filter { it.folder.equals(folder, ignoreCase = true) }
                }
            }

            // Tag filtering
            if (tag.isNotBlank() && tag != "All") {
                list = list.filter { it.tags.any { t -> t.equals(tag, ignoreCase = true) } }
            }

            // Entry Type filtering
            if (typeFilter != null) {
                list = list.filter { it.entryType == typeFilter }
            }

            // Live search query (matches title, username, url, folder, tags, notes, cardholder, identity)
            if (query.isNotBlank()) {
                val cleanQuery = query.trim().lowercase()
                list = list.filter { entry ->
                    entry.title.lowercase().contains(cleanQuery) ||
                            entry.username.lowercase().contains(cleanQuery) ||
                            entry.url.lowercase().contains(cleanQuery) ||
                            entry.folder.lowercase().contains(cleanQuery) ||
                            entry.notes.lowercase().contains(cleanQuery) ||
                            entry.cardholderName.lowercase().contains(cleanQuery) ||
                            entry.identityName.lowercase().contains(cleanQuery) ||
                            entry.tags.any { it.lowercase().contains(cleanQuery) } ||
                            entry.customFields.any { it.label.lowercase().contains(cleanQuery) || it.value.lowercase().contains(cleanQuery) }
                }
            }

            // Sorting
            val comparator: Comparator<VaultEntry> = when (sort) {
                VaultSortMode.ALPHABETICAL_ASC -> compareBy { it.title.lowercase() }
                VaultSortMode.ALPHABETICAL_DESC -> compareByDescending { it.title.lowercase() }
                VaultSortMode.RECENTLY_MODIFIED -> compareByDescending { it.updatedAt }
                VaultSortMode.RECENTLY_CREATED -> compareBy { it.createdAt }
                VaultSortMode.FAVORITES_FIRST -> compareByDescending<VaultEntry> { it.isFavorite }.thenBy { it.title.lowercase() }
            }

            list.sortedWith(comparator)
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Clipboard auto-clear job
    private var clipboardClearJob: Job? = null
    private var lastCopiedText: String? = null

    // Inactivity auto-lock tracking & Form state protection
    private var lastActivityTime = System.currentTimeMillis()
    private var inactivityCheckJob: Job? = null
    private val _isUserEditing = MutableStateFlow(false)
    val isUserEditing: StateFlow<Boolean> = _isUserEditing.asStateFlow()

    init {
        startInactivityMonitor()
        startTotpTicker()
    }

    fun setUserEditing(isEditing: Boolean) {
        _isUserEditing.value = isEditing
        if (isEditing) {
            onUserInteraction()
        }
    }

    private fun startTotpTicker() {
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                _totpTick.value = System.currentTimeMillis()
            }
        }
    }

    fun onUserInteraction() {
        lastActivityTime = System.currentTimeMillis()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        onUserInteraction()
    }

    fun setSortMode(mode: VaultSortMode) {
        preferences.sortMode = mode
        _sortMode.value = mode
        onUserInteraction()
    }

    fun setSelectedFolder(folder: String) {
        preferences.selectedFolder = folder
        _selectedFolder.value = folder
        onUserInteraction()
    }

    fun togglePasswordVisibility(entryId: String) {
        onUserInteraction()
        val current = _revealedPasswordIds.value
        if (current.contains(entryId)) {
            _revealedPasswordIds.value = current - entryId
        } else {
            _revealedPasswordIds.value = current + entryId
        }
    }

    fun toggleFavorite(entryId: String) {
        onUserInteraction()
        viewModelScope.launch {
            try {
                repository.toggleFavorite(entryId)
            } catch (e: Exception) {
                CrashDiagnosticsLogger.logException("VaultList", e)
            }
        }
    }

    fun addFolder(folderName: String) {
        onUserInteraction()
        viewModelScope.launch {
            try {
                repository.addFolder(folderName)
            } catch (e: Exception) {
                CrashDiagnosticsLogger.logException("VaultList", e)
            }
        }
    }

    fun importEntries(entries: List<VaultEntry>, onComplete: (Int) -> Unit) {
        onUserInteraction()
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.importEntries(entries)
                _isLoading.value = false
                if (result.isSuccess) {
                    val count = result.getOrDefault(0)
                    _uiEvents.emit(UiEvent.ShowSnackbar("Imported $count entries into vault"))
                    onComplete(count)
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Unknown import error"
                    CrashDiagnosticsLogger.logError("Import", "ImportFailed", err)
                    _uiEvents.emit(UiEvent.ShowSnackbar("Import failed: $err"))
                    onComplete(0)
                }
            } catch (e: Exception) {
                _isLoading.value = false
                CrashDiagnosticsLogger.logException("Import", e)
                _uiEvents.emit(UiEvent.ShowSnackbar("Import error: ${e.message}"))
                onComplete(0)
            }
        }
    }

    fun clearAuthError() {
        _authErrorMessage.value = null
    }

    // ---------------------------------------------------------------------------------------------
    // Vault Authentication & Setup
    // ---------------------------------------------------------------------------------------------

    fun setupMasterPassword(password: CharArray) {
        viewModelScope.launch {
            _isLoading.value = true
            _authErrorMessage.value = null
            try {
                val result = repository.createInitialVault(password)
                _isLoading.value = false
                if (result.isSuccess) {
                    _uiEvents.emit(UiEvent.ShowSnackbar("Vault initialized securely"))
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Failed to initialize vault"
                    CrashDiagnosticsLogger.logError("Setup", "InitFailed", err)
                    _authErrorMessage.value = err
                }
            } catch (e: Exception) {
                _isLoading.value = false
                CrashDiagnosticsLogger.logException("Setup", e)
                _authErrorMessage.value = e.message ?: "Failed to initialize vault"
            }
        }
    }

    fun unlockWithPassword(password: CharArray) {
        viewModelScope.launch {
            _isLoading.value = true
            _authErrorMessage.value = null
            try {
                val result = repository.unlockWithPassword(password)
                _isLoading.value = false
                if (result.isSuccess) {
                    _searchQuery.value = ""
                    _revealedPasswordIds.value = emptySet()
                    _uiEvents.emit(UiEvent.ShowSnackbar("Vault unlocked"))
                } else {
                    val err = result.exceptionOrNull()
                    val message = if (err is IllegalArgumentException || err?.message?.contains("corrupt", ignoreCase = true) == true) {
                        CrashDiagnosticsLogger.logError("Auth", "VaultCorrupt", err.message ?: "Integrity error")
                        "Vault file is unreadable or corrupted. Please verify backup in Settings."
                    } else {
                        "Incorrect master password"
                    }
                    _authErrorMessage.value = message
                }
            } catch (e: Exception) {
                _isLoading.value = false
                CrashDiagnosticsLogger.logException("Auth", e)
                _authErrorMessage.value = "Authentication error: ${e.message}"
            }
        }
    }

    fun unlockWithBiometrics(cipher: Cipher) {
        viewModelScope.launch {
            if (repository.isBiometricLockedOut) {
                _authErrorMessage.value = "Biometric locked after 5 failed attempts. Enter master password."
                return@launch
            }

            _isLoading.value = true
            _authErrorMessage.value = null
            try {
                val result = repository.unlockWithBiometrics(cipher)
                _isLoading.value = false
                if (result.isSuccess) {
                    _searchQuery.value = ""
                    _revealedPasswordIds.value = emptySet()
                    _uiEvents.emit(UiEvent.ShowSnackbar("Vault unlocked with biometrics"))
                } else {
                    val attempts = preferences.failedBiometricAttempts
                    if (attempts >= VaultPreferences.MAX_FAILED_BIOMETRIC_ATTEMPTS) {
                        _authErrorMessage.value = "Biometrics locked (5 failed attempts). Master password required."
                    } else {
                        _authErrorMessage.value = "Biometric authentication failed (${attempts}/5)"
                    }
                }
            } catch (e: Exception) {
                _isLoading.value = false
                CrashDiagnosticsLogger.logException("Biometrics", e)
                _authErrorMessage.value = "Biometric unlock failed"
            }
        }
    }

    fun lockVault() {
        repository.lockVault()
        _searchQuery.value = ""
        _revealedPasswordIds.value = emptySet()
        _authErrorMessage.value = null
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.VaultLocked)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Entry Operations
    // ---------------------------------------------------------------------------------------------

    fun saveEntry(entry: VaultEntry, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.saveEntry(entry)
                _isLoading.value = false
                if (result.isSuccess) {
                    _uiEvents.emit(UiEvent.ShowSnackbar("Saved \"${entry.title}\""))
                    onComplete()
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Failed to save entry"
                    CrashDiagnosticsLogger.logError("EntryEdit", "SaveFailed", err)
                    _uiEvents.emit(UiEvent.ShowSnackbar("Failed to save entry: $err"))
                }
            } catch (e: Exception) {
                _isLoading.value = false
                CrashDiagnosticsLogger.logException("EntryEdit", e)
                _uiEvents.emit(UiEvent.ShowSnackbar("Error saving entry: ${e.message}"))
            }
        }
    }

    fun deleteEntry(entryId: String, entryTitle: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.deleteEntry(entryId)
                _isLoading.value = false
                if (result.isSuccess) {
                    _uiEvents.emit(UiEvent.ShowSnackbar("Deleted \"$entryTitle\""))
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Failed to delete entry"
                    CrashDiagnosticsLogger.logError("EntryDetail", "DeleteFailed", err)
                    _uiEvents.emit(UiEvent.ShowSnackbar("Failed to delete entry: $err"))
                }
            } catch (e: Exception) {
                _isLoading.value = false
                CrashDiagnosticsLogger.logException("EntryDetail", e)
                _uiEvents.emit(UiEvent.ShowSnackbar("Error deleting entry: ${e.message}"))
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Clipboard Management (With Auto-Clear Timeout & Memory Safety)
    // ---------------------------------------------------------------------------------------------

    fun copyToClipboard(text: String, label: String = "Text", isSensitive: Boolean = false) {
        onUserInteraction()
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowSnackbar("$label copied to clipboard"))
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String, isSensitive: Boolean = false) {
        onUserInteraction()
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)

            // Android 13+ sensitive content flag
            if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = android.os.PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }

            clipboard.setPrimaryClip(clip)
            lastCopiedText = text

            val timeoutSec = preferences.clipboardClearTimeoutSeconds
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowSnackbar("$label copied (auto-clears in ${timeoutSec}s)"))
            }

            // Schedule clipboard auto-clear
            scheduleClipboardClear(context, timeoutSec)
        } catch (e: Exception) {
            CrashDiagnosticsLogger.logException("Clipboard", e)
        }
    }

    private fun scheduleClipboardClear(context: Context, delaySeconds: Long) {
        clipboardClearJob?.cancel()
        if (delaySeconds <= 0) return

        clipboardClearJob = viewModelScope.launch {
            delay(delaySeconds * 1000L)
            clearClipboardIfMatching(context)
        }
    }

    fun clearClipboardOnBackground(context: Context) {
        clearClipboardIfMatching(context)
    }

    private fun clearClipboardIfMatching(context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val primaryClip = clipboard.primaryClip
            if (primaryClip != null && primaryClip.itemCount > 0) {
                val currentText = primaryClip.getItemAt(0).text?.toString()
                if (currentText == lastCopiedText) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                    lastCopiedText = null
                }
            }
        } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------------------------------------
    // Inactivity & Lifecycle Auto-Lock
    // ---------------------------------------------------------------------------------------------

    private fun startInactivityMonitor() {
        inactivityCheckJob?.cancel()
        inactivityCheckJob = viewModelScope.launch {
            while (true) {
                delay(5000L)
                val timeoutSeconds = preferences.autoLockTimeoutSeconds
                // If user is actively typing in a form dialog (isUserEditing), do not auto-lock unexpectedly
                if (timeoutSeconds > 0 && !_isUserEditing.value && vaultState.value is VaultState.Unlocked) {
                    val idleTime = (System.currentTimeMillis() - lastActivityTime) / 1000L
                    if (idleTime >= timeoutSeconds) {
                        lockVault()
                    }
                }
            }
        }
    }

    fun onAppBackgrounded(context: Context) {
        if (preferences.lockOnBackground && vaultState.value is VaultState.Unlocked) {
            lockVault()
        }
        clearClipboardOnBackground(context)
    }

    // ---------------------------------------------------------------------------------------------
    // Settings & Master Password Operations
    // ---------------------------------------------------------------------------------------------

    fun enrollBiometrics(cipher: Cipher, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val result = repository.enrollBiometrics(cipher)
                if (result.isSuccess) {
                    _uiEvents.emit(UiEvent.ShowSnackbar("Biometric unlock enabled"))
                    onComplete(true, null)
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Biometric enrollment failed"
                    CrashDiagnosticsLogger.logError("Settings", "BiometricEnrollFailed", err)
                    _uiEvents.emit(UiEvent.ShowSnackbar(err))
                    onComplete(false, err)
                }
            } catch (e: Exception) {
                CrashDiagnosticsLogger.logException("Settings", e)
                onComplete(false, e.message)
            }
        }
    }

    fun disableBiometrics() {
        try {
            repository.disableBiometrics()
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowSnackbar("Biometric unlock disabled"))
            }
        } catch (e: Exception) {
            CrashDiagnosticsLogger.logException("Settings", e)
        }
    }

    fun changeMasterPassword(
        oldPassword: CharArray,
        newPassword: CharArray,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.changeMasterPassword(oldPassword, newPassword)
                _isLoading.value = false
                if (result.isSuccess) {
                    _uiEvents.emit(UiEvent.ShowSnackbar("Master password changed and vault re-encrypted"))
                    onComplete(true, null)
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Incorrect current password"
                    CrashDiagnosticsLogger.logError("Settings", "PasswordChangeFailed", err)
                    onComplete(false, err)
                }
            } catch (e: Exception) {
                _isLoading.value = false
                CrashDiagnosticsLogger.logException("Settings", e)
                onComplete(false, e.message)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        preferences.themeMode = mode
        _themeMode.value = mode
    }

    fun setAutoLockTimeout(seconds: Long) {
        preferences.autoLockTimeoutSeconds = seconds
    }

    fun setClipboardTimeout(seconds: Long) {
        preferences.clipboardClearTimeoutSeconds = seconds
    }

    fun setLockOnBackground(enabled: Boolean) {
        preferences.lockOnBackground = enabled
    }

    fun setPreventScreenCapture(enabled: Boolean) {
        preferences.preventScreenCapture = enabled
        _preventScreenCapture.value = enabled
    }

    fun setPendingSharedText(text: String?) {
        _pendingSharedText.value = text
    }

    fun clearPendingSharedText() {
        _pendingSharedText.value = null
    }

    fun setPendingDeepLink(link: String?) {
        _pendingDeepLink.value = link
    }

    fun clearPendingDeepLink() {
        _pendingDeepLink.value = null
    }

    fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank()) {
                        _pendingSharedText.value = sharedText.trim()
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                val dataUri = intent.dataString
                if (!dataUri.isNullOrBlank()) {
                    _pendingDeepLink.value = dataUri
                }
            }
        }
    }

    fun createPasskeyEntry(
        title: String,
        rpId: String,
        userName: String,
        folder: String = "",
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val regData = com.example.passkey.PasskeyCryptoHelper.createPasskey(
                    rpId = rpId.ifBlank { title.lowercase().replace(" ", "") + ".com" },
                    userName = userName
                )
                val newEntry = VaultEntry(
                    title = title.ifBlank { rpId },
                    username = userName,
                    password = "",
                    url = if (rpId.startsWith("http")) rpId else "https://$rpId",
                    folder = folder,
                    entryType = com.example.data.EntryType.PASSKEY,
                    passkeyCredentialId = regData.credentialIdBase64Url,
                    passkeyRpId = regData.rpId,
                    passkeyUserHandle = regData.userHandleBase64Url,
                    passkeyPrivateKeyPkcs8 = regData.privateKeyPkcs8Base64,
                    passkeyPublicKeyCose = regData.publicKeyCoseBase64,
                    passkeySignCount = 0
                )
                val result = repository.saveEntry(newEntry)
                if (result.isSuccess) {
                    _uiEvents.emit(UiEvent.ShowSnackbar("Passkey generated and encrypted in vault"))
                    onComplete(true)
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Failed to save passkey"
                    _uiEvents.emit(UiEvent.ShowSnackbar(err))
                    onComplete(false)
                }
            } catch (e: Exception) {
                CrashDiagnosticsLogger.logException("PasskeyCreation", e)
                _uiEvents.emit(UiEvent.ShowSnackbar("Failed to create passkey: ${e.message}"))
                onComplete(false)
            }
        }
    }

    fun shareEntryDetails(
        context: Context,
        entry: VaultEntry,
        includePassword: Boolean = false
    ) {
        try {
            val textBuilder = StringBuilder()
            textBuilder.append("Account: ${entry.title}\n")
            if (entry.username.isNotBlank()) textBuilder.append("Username: ${entry.username}\n")
            if (entry.url.isNotBlank()) textBuilder.append("URL: ${entry.url}\n")
            if (entry.isPasskey) {
                textBuilder.append("Type: Passkey (FIDO2/WebAuthn)\n")
                textBuilder.append("RP ID: ${entry.passkeyRpId}\n")
            } else if (includePassword && entry.password.isNotBlank()) {
                textBuilder.append("Password: ${entry.password}\n")
            }
            if (entry.notes.isNotBlank()) textBuilder.append("Notes: ${entry.notes}\n")

            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, "VaultKeep: ${entry.title}")
                putExtra(android.content.Intent.EXTRA_TEXT, textBuilder.toString().trim())
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Entry via...").apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            CrashDiagnosticsLogger.logException("ShareEntry", e)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Security Center & Integrity Checker Actions
    // ---------------------------------------------------------------------------------------------

    fun triggerSecurityScan() {
        onUserInteraction()
        preferences.lastSecurityScanTimestamp = System.currentTimeMillis()
        _securityScanTick.value = System.currentTimeMillis()
    }

    fun setSelectedIssueFilter(issueType: com.example.data.SecurityIssueType?) {
        onUserInteraction()
        _selectedIssueFilter.value = issueType
    }

    fun setOldPasswordThresholdMonths(months: Int) {
        preferences.oldPasswordThresholdMonths = months
        triggerSecurityScan()
    }

    fun runVaultIntegrityCheck() {
        onUserInteraction()
        viewModelScope.launch {
            _isVerifyingIntegrity.value = true
            val report = repository.verifyVaultIntegrity()
            _integrityReport.value = report
            _isVerifyingIntegrity.value = false
        }
    }

    fun getBackupStatusSummary(): Pair<String, Boolean> {
        val lastTimestamp = preferences.lastBackupTimestamp
        if (lastTimestamp == 0L) {
            return Pair("No backups created yet", true) // needs backup
        }
        val daysAgo = (System.currentTimeMillis() - lastTimestamp) / (1000L * 60L * 60L * 24L)
        val label = when {
            daysAgo == 0L -> "Backed up today"
            daysAgo == 1L -> "Backed up yesterday"
            else -> "Backed up $daysAgo days ago"
        }
        return Pair(label, daysAgo >= 30L)
    }

    // ---------------------------------------------------------------------------------------------
    // ---------------------------------------------------------------------------------------------
    // Hardware Security Key & Sync Reactive State
    // ---------------------------------------------------------------------------------------------

    private val _isSecurityKeyEnrolled = MutableStateFlow(preferences.isHardwareKeyEnabled)
    val isSecurityKeyEnrolled: StateFlow<Boolean> = _isSecurityKeyEnrolled.asStateFlow()

    private val _securityKeyName = MutableStateFlow(preferences.hardwareKeyName)
    val securityKeyName: StateFlow<String> = _securityKeyName.asStateFlow()

    private val _syncType = MutableStateFlow(preferences.syncType)
    val syncType: StateFlow<com.example.sync.SyncType> = _syncType.asStateFlow()

    private val _webDavServerUrl = MutableStateFlow(preferences.webDavServerUrl)
    val webDavServerUrl: StateFlow<String> = _webDavServerUrl.asStateFlow()

    private val _webDavUsername = MutableStateFlow(preferences.webDavUsername)
    val webDavUsername: StateFlow<String> = _webDavUsername.asStateFlow()

    private val _webDavPassword = MutableStateFlow(preferences.webDavPasswordEncrypted)
    val webDavPassword: StateFlow<String> = _webDavPassword.asStateFlow()

    private val _webDavRemotePath = MutableStateFlow(preferences.webDavRemotePath)
    val webDavRemotePath: StateFlow<String> = _webDavRemotePath.asStateFlow()

    private val _localFolderTreeUri = MutableStateFlow(preferences.localFolderTreeUri)
    val localFolderTreeUri: StateFlow<String> = _localFolderTreeUri.asStateFlow()

    // ---------------------------------------------------------------------------------------------
    // Optional Self-Hosted Sync Actions (WebDAV / Local SAF Folder)
    // ---------------------------------------------------------------------------------------------

    fun syncNow() {
        onUserInteraction()
        viewModelScope.launch {
            val status = syncManager.performSync()
            when (status) {
                is com.example.sync.SyncStatus.Success -> {
                    _uiEvents.emit(UiEvent.ShowSnackbar("Sync completed successfully"))
                }
                is com.example.sync.SyncStatus.Error -> {
                    _uiEvents.emit(UiEvent.ShowSnackbar("Sync failed: ${status.message}"))
                }
                is com.example.sync.SyncStatus.Conflict -> {
                    _uiEvents.emit(UiEvent.ShowSnackbar("Sync conflict detected. Please review."))
                }
                else -> {}
            }
        }
    }

    fun resolveConflictKeepLocal() {
        onUserInteraction()
        viewModelScope.launch {
            val res = syncManager.resolveConflictKeepLocal()
            if (res.isSuccess) {
                _uiEvents.emit(UiEvent.ShowSnackbar("Resolved conflict: Local vault kept and uploaded."))
            } else {
                _uiEvents.emit(UiEvent.ShowSnackbar("Conflict resolution failed: ${res.exceptionOrNull()?.message}"))
            }
        }
    }

    fun resolveConflictKeepRemote(remoteBytes: ByteArray) {
        onUserInteraction()
        viewModelScope.launch {
            val res = syncManager.resolveConflictKeepRemote(remoteBytes)
            if (res.isSuccess) {
                _uiEvents.emit(UiEvent.ShowSnackbar("Resolved conflict: Remote vault applied."))
            } else {
                _uiEvents.emit(UiEvent.ShowSnackbar("Conflict resolution failed: ${res.exceptionOrNull()?.message}"))
            }
        }
    }

    fun setSyncType(type: com.example.sync.SyncType) {
        preferences.syncType = type
        _syncType.value = type
    }

    fun updateWebDavServerUrl(url: String) {
        preferences.webDavServerUrl = url
        _webDavServerUrl.value = url
    }

    fun updateWebDavUsername(username: String) {
        preferences.webDavUsername = username
        _webDavUsername.value = username
    }

    fun updateWebDavPassword(password: String) {
        preferences.webDavPasswordEncrypted = password
        _webDavPassword.value = password
    }

    fun updateWebDavRemotePath(path: String) {
        preferences.webDavRemotePath = path
        _webDavRemotePath.value = path
    }

    fun updateLocalFolderTreeUri(uriString: String) {
        preferences.localFolderTreeUri = uriString
        _localFolderTreeUri.value = uriString
    }

    fun testSyncConnection() {
        viewModelScope.launch {
            val config = preferences.getWebDavConfig()
            val client = com.example.sync.WebDavSyncClient(config)
            val result = client.testConnection()
            if (result.isSuccess) {
                _uiEvents.emit(UiEvent.ShowSnackbar("WebDAV Connection Successful"))
            } else {
                _uiEvents.emit(UiEvent.ShowSnackbar("WebDAV Connection failed: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Hardware Security Key (FIDO2/WebAuthn) Optional Unlock Factor
    // ---------------------------------------------------------------------------------------------

    fun enrollHardwareKey(keyName: String = "Hardware Key") {
        viewModelScope.launch {
            try {
                val challenge = com.example.securitykey.SecurityKeyHelper.generateEnrollmentChallenge()
                val result = com.example.securitykey.SecurityKeyHelper.enrollKey(keyName, challenge)
                if (result.isSuccess) {
                    val enroll = result.getOrThrow()
                    preferences.isHardwareKeyEnabled = true
                    preferences.hardwareKeyCredentialId = enroll.credentialId
                    preferences.hardwareKeyPublicKeyCose = enroll.publicKeyCose
                    preferences.hardwareKeyName = enroll.keyName
                    _isSecurityKeyEnrolled.value = true
                    _securityKeyName.value = enroll.keyName
                    _uiEvents.emit(UiEvent.ShowSnackbar("Hardware key '${enroll.keyName}' enrolled successfully."))
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Enrollment failed"
                    _uiEvents.emit(UiEvent.ShowSnackbar("Enrollment failed: $err"))
                }
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowSnackbar("Enrollment failed: ${e.message}"))
            }
        }
    }

    fun removeHardwareKey() {
        preferences.isHardwareKeyEnabled = false
        preferences.hardwareKeyCredentialId = ""
        preferences.hardwareKeyPublicKeyCose = ""
        _isSecurityKeyEnrolled.value = false
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowSnackbar("Hardware security key removed."))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Tags, Type Filters & Bulk Actions
    // ---------------------------------------------------------------------------------------------

    fun selectTag(tag: String) {
        _selectedTag.value = tag
    }

    fun selectTypeFilter(type: EntryType?) {
        _selectedTypeFilter.value = type
    }

    fun addTag(tag: String) {
        viewModelScope.launch {
            repository.addTag(tag)
        }
    }

    fun bulkMoveToFolder(entryIds: Set<String>, folder: String) {
        viewModelScope.launch {
            val result = repository.bulkMoveToFolder(entryIds, folder)
            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                _uiEvents.emit(UiEvent.ShowSnackbar("Moved $count items to $folder"))
            } else {
                _uiEvents.emit(UiEvent.ShowSnackbar("Bulk move failed: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun bulkAddTag(entryIds: Set<String>, tag: String) {
        viewModelScope.launch {
            val result = repository.bulkAddTag(entryIds, tag)
            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                _uiEvents.emit(UiEvent.ShowSnackbar("Tagged $count items with '$tag'"))
            } else {
                _uiEvents.emit(UiEvent.ShowSnackbar("Bulk tag failed: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun bulkDelete(entryIds: Set<String>) {
        viewModelScope.launch {
            val result = repository.bulkDelete(entryIds)
            clearSelection()
            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                _uiEvents.emit(UiEvent.ShowSnackbar("Deleted $count items"))
            } else {
                _uiEvents.emit(UiEvent.ShowSnackbar("Bulk delete failed: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun moveToTrash(entryId: String, entryTitle: String = "") {
        viewModelScope.launch {
            val result = repository.moveToTrash(entryId)
            if (result.isSuccess) {
                _uiEvents.emit(UiEvent.ShowSnackbar(if (entryTitle.isNotBlank()) "Moved \"$entryTitle\" to Trash" else "Moved to Trash"))
            } else {
                _uiEvents.emit(UiEvent.ShowSnackbar("Failed to move to Trash: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun restoreFromTrash(entryId: String, entryTitle: String = "") {
        viewModelScope.launch {
            val result = repository.restoreFromTrash(entryId)
            if (result.isSuccess) {
                _uiEvents.emit(UiEvent.ShowSnackbar(if (entryTitle.isNotBlank()) "Restored \"$entryTitle\"" else "Item restored"))
            } else {
                _uiEvents.emit(UiEvent.ShowSnackbar("Failed to restore: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun deletePermanently(entryId: String, entryTitle: String = "") {
        viewModelScope.launch {
            val result = repository.deletePermanently(entryId)
            if (result.isSuccess) {
                _uiEvents.emit(UiEvent.ShowSnackbar(if (entryTitle.isNotBlank()) "Permanently deleted \"$entryTitle\"" else "Item permanently deleted"))
            } else {
                _uiEvents.emit(UiEvent.ShowSnackbar("Failed to delete: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val result = repository.emptyTrash()
            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                _uiEvents.emit(UiEvent.ShowSnackbar("Emptied Trash ($count items deleted)"))
            } else {
                _uiEvents.emit(UiEvent.ShowSnackbar("Failed to empty Trash: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun bulkMoveToTrash(entryIds: Set<String>) {
        viewModelScope.launch {
            val result = repository.bulkMoveToTrash(entryIds)
            clearSelection()
            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                _uiEvents.emit(UiEvent.ShowSnackbar("Moved $count items to Trash"))
            } else {
                _uiEvents.emit(UiEvent.ShowSnackbar("Bulk trash failed: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun bulkRestoreFromTrash(entryIds: Set<String>) {
        viewModelScope.launch {
            val result = repository.bulkRestoreFromTrash(entryIds)
            clearSelection()
            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                _uiEvents.emit(UiEvent.ShowSnackbar("Restored $count items from Trash"))
            } else {
                _uiEvents.emit(UiEvent.ShowSnackbar("Bulk restore failed: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun duplicateEntry(entry: VaultEntry, onComplete: (VaultEntry) -> Unit = {}) {
        viewModelScope.launch {
            val cloned = entry.copy(
                id = java.util.UUID.randomUUID().toString(),
                title = "${entry.title} (Copy)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isDeleted = false,
                deletedAt = 0L,
                passwordHistory = emptyList(),
                passkeyCredentialId = "",
                passkeySignCount = 0
            )
            val result = repository.saveEntry(cloned)
            if (result.isSuccess) {
                _uiEvents.emit(UiEvent.ShowSnackbar("Cloned \"${entry.title}\""))
                onComplete(cloned)
            } else {
                _uiEvents.emit(UiEvent.ShowSnackbar("Failed to duplicate: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun duplicateEntry(id: String) {
        val entry = entries.value.find { it.id == id } ?: return
        duplicateEntry(entry)
    }

    fun exportToCsv(outputStream: java.io.OutputStream): Result<Int> {
        val currentEntries = entries.value
        return try {
            com.example.data.VaultExporter.exportToCsv(currentEntries, outputStream)
            Result.success(currentEntries.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun exportToJson(outputStream: java.io.OutputStream): Result<Int> {
        val currentEntries = entries.value
        return try {
            com.example.data.VaultExporter.exportToJson(currentEntries, outputStream)
            Result.success(currentEntries.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateEmergencyRecoveryKit(): String {
        val activeEntries = entries.value
        val folderList = folders.value
        return com.example.data.VaultExporter.generateEmergencyRecoveryKit(
            context = context,
            entriesCount = activeEntries.size,
            foldersCount = folderList.size,
            vaultHint = preferences.passwordHint
        )
    }

    private val _importResult = MutableStateFlow<com.example.data.importers.ImportResult?>(null)
    val importResult: StateFlow<com.example.data.importers.ImportResult?> = _importResult.asStateFlow()

    fun parseImportFile(inputStream: java.io.InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentEntries = entries.value
                val result = com.example.data.importers.VaultImporter.parseStream(inputStream, currentEntries)
                _importResult.value = result
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowSnackbar("Failed to parse import file: ${e.message}"))
            }
        }
    }

    fun toggleImportCandidate(index: Int) {
        val current = _importResult.value ?: return
        val updated = current.candidates.toMutableList()
        if (index in updated.indices) {
            val cand = updated[index]
            updated[index] = cand.copy(isSelected = !cand.isSelected)
            _importResult.value = current.copy(candidates = updated)
        }
    }

    fun confirmImportSelected() {
        val current = _importResult.value ?: return
        val selectedEntries = current.candidates.filter { it.isSelected }.map { it.entry }
        if (selectedEntries.isEmpty()) {
            _importResult.value = null
            return
        }
        viewModelScope.launch {
            var count = 0
            for (entry in selectedEntries) {
                val res = repository.saveEntry(entry)
                if (res.isSuccess) count++
            }
            _importResult.value = null
            _uiEvents.emit(UiEvent.ShowSnackbar("Imported $count entries successfully"))
        }
    }

    fun dismissImportPreview() {
        _importResult.value = null
    }

    fun clearPasswordHistory(entryId: String) {
        viewModelScope.launch {
            val result = repository.clearPasswordHistory(entryId)
            if (result.isSuccess) {
                _uiEvents.emit(UiEvent.ShowSnackbar("Password history cleared"))
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Onboarding, What's New Changelog, and Widget Preferences
    // ---------------------------------------------------------------------------------------------

    fun completeOnboarding() {
        preferences.hasCompletedOnboarding = true
        _hasCompletedOnboarding.value = true
    }

    fun checkWhatsNew(currentVersion: String = "1.0.0") {
        val lastSeen = preferences.lastSeenAppVersion
        if (lastSeen != currentVersion && repository.isVaultCreated) {
            _showWhatsNewDialog.value = true
        }
    }

    fun dismissWhatsNewDialog(currentVersion: String = "1.0.0") {
        preferences.lastSeenAppVersion = currentVersion
        _showWhatsNewDialog.value = false
    }

    fun setWidgetShowNamesOnly(enabled: Boolean) {
        preferences.widgetShowNamesOnly = enabled
        _widgetShowNamesOnly.value = enabled
    }
}


