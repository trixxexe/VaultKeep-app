package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.example.BuildConfig

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

enum class VaultSortMode(val displayName: String) {
    FAVORITES_FIRST("Favorites First"),
    ALPHABETICAL_ASC("Name (A → Z)"),
    ALPHABETICAL_DESC("Name (Z → A)"),
    RECENTLY_MODIFIED("Recently Modified"),
    RECENTLY_CREATED("Recently Created");

    val label: String get() = displayName
}

class VaultPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var autoLockTimeoutSeconds: Long
        get() = prefs.getLong(KEY_AUTO_LOCK_TIMEOUT, 60L) // Default 1 min (60s)
        set(value) = prefs.edit().putLong(KEY_AUTO_LOCK_TIMEOUT, value).apply()

    var clipboardClearTimeoutSeconds: Long
        get() = prefs.getLong(KEY_CLIPBOARD_TIMEOUT, 30L) // Default 30s
        set(value) = prefs.edit().putLong(KEY_CLIPBOARD_TIMEOUT, value).apply()

    var lockOnBackground: Boolean
        get() = prefs.getBoolean(KEY_LOCK_ON_BACKGROUND, true)
        set(value) = prefs.edit().putBoolean(KEY_LOCK_ON_BACKGROUND, value).apply()

    var themeMode: ThemeMode
        get() {
            val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
            return try {
                ThemeMode.valueOf(name)
            } catch (_: Exception) {
                ThemeMode.SYSTEM
            }
        }
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    var sortMode: VaultSortMode
        get() {
            val name = prefs.getString(KEY_SORT_MODE, VaultSortMode.FAVORITES_FIRST.name) ?: VaultSortMode.FAVORITES_FIRST.name
            return try {
                VaultSortMode.valueOf(name)
            } catch (_: Exception) {
                VaultSortMode.FAVORITES_FIRST
            }
        }
        set(value) = prefs.edit().putString(KEY_SORT_MODE, value.name).apply()

    var selectedFolder: String
        get() = prefs.getString(KEY_SELECTED_FOLDER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SELECTED_FOLDER, value).apply()

    var wrappedBiometricKey: ByteArray?
        get() {
            val b64 = prefs.getString(KEY_WRAPPED_BIOMETRIC_KEY, null) ?: return null
            return try {
                Base64.decode(b64, Base64.NO_WRAP)
            } catch (_: Exception) {
                null
            }
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_WRAPPED_BIOMETRIC_KEY).apply()
            } else {
                prefs.edit().putString(
                    KEY_WRAPPED_BIOMETRIC_KEY,
                    Base64.encodeToString(value, Base64.NO_WRAP)
                ).apply()
            }
        }

    var biometricIv: ByteArray?
        get() {
            val b64 = prefs.getString(KEY_BIOMETRIC_IV, null) ?: return null
            return try {
                Base64.decode(b64, Base64.NO_WRAP)
            } catch (_: Exception) {
                null
            }
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_BIOMETRIC_IV).apply()
            } else {
                prefs.edit().putString(
                    KEY_BIOMETRIC_IV,
                    Base64.encodeToString(value, Base64.NO_WRAP)
                ).apply()
            }
        }

    var failedBiometricAttempts: Int
        get() = prefs.getInt(KEY_FAILED_BIOMETRIC_ATTEMPTS, 0)
        set(value) = prefs.edit().putInt(KEY_FAILED_BIOMETRIC_ATTEMPTS, value).apply()

    var lastBackupTimestamp: Long
        get() = prefs.getLong(KEY_LAST_BACKUP_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP_TIMESTAMP, value).apply()

    var lastSecurityScanTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SECURITY_SCAN_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SECURITY_SCAN_TIMESTAMP, value).apply()

    var oldPasswordThresholdMonths: Int
        get() = prefs.getInt(KEY_OLD_PASSWORD_MONTHS, 12)
        set(value) = prefs.edit().putInt(KEY_OLD_PASSWORD_MONTHS, value).apply()

    fun clearBiometricData() {
        prefs.edit()
            .remove(KEY_WRAPPED_BIOMETRIC_KEY)
            .remove(KEY_BIOMETRIC_IV)
            .remove(KEY_FAILED_BIOMETRIC_ATTEMPTS)
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "vaultkeep_prefs"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
        private const val KEY_CLIPBOARD_TIMEOUT = "clipboard_timeout"
        private const val KEY_LOCK_ON_BACKGROUND = "lock_on_background"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_SELECTED_FOLDER = "selected_folder"
        private const val KEY_WRAPPED_BIOMETRIC_KEY = "wrapped_biometric_key"
        private const val KEY_BIOMETRIC_IV = "biometric_iv"
        private const val KEY_FAILED_BIOMETRIC_ATTEMPTS = "failed_biometric_attempts"
        private const val KEY_LAST_BACKUP_TIMESTAMP = "last_backup_timestamp"
        private const val KEY_LAST_SECURITY_SCAN_TIMESTAMP = "last_security_scan_timestamp"
        private const val KEY_OLD_PASSWORD_MONTHS = "old_password_months"

        const val MAX_FAILED_BIOMETRIC_ATTEMPTS = 5
        private const val KEY_PREVENT_SCREEN_CAPTURE = "prevent_screen_capture"

        private const val KEY_SYNC_TYPE = "sync_type"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_WEBDAV_SERVER_URL = "webdav_server_url"
        private const val KEY_WEBDAV_USERNAME = "webdav_username"
        private const val KEY_WEBDAV_PASSWORD = "webdav_password"
        private const val KEY_WEBDAV_REMOTE_PATH = "webdav_remote_path"
        private const val KEY_LOCAL_FOLDER_URI = "local_folder_uri"
        private const val KEY_LOCAL_FOLDER_FILE_NAME = "local_folder_file_name"

        private const val KEY_HARDWARE_KEY_ENABLED = "hardware_key_enabled"
        private const val KEY_HARDWARE_KEY_CRED_ID = "hardware_key_cred_id"
        private const val KEY_HARDWARE_KEY_PUBLIC_KEY = "hardware_key_public_key"
        private const val KEY_HARDWARE_KEY_NAME = "hardware_key_name"

        private const val KEY_LAST_SEEN_APP_VERSION = "last_seen_app_version"
        private const val KEY_COMPLETED_ONBOARDING = "completed_onboarding"
        private const val KEY_HAS_SEEN_WELCOME_SEQUENCE = "has_seen_welcome_sequence"
        private const val KEY_WIDGET_SHOW_NAMES_ONLY = "widget_show_names_only"
    }

    var hasSeenWelcomeSequence: Boolean
        get() = prefs.getBoolean(KEY_HAS_SEEN_WELCOME_SEQUENCE, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_SEEN_WELCOME_SEQUENCE, value).apply()

    var lastSeenAppVersion: String
        get() = prefs.getString(KEY_LAST_SEEN_APP_VERSION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SEEN_APP_VERSION, value).apply()

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_COMPLETED_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPLETED_ONBOARDING, value).apply()

    var widgetShowNamesOnly: Boolean
        get() = prefs.getBoolean(KEY_WIDGET_SHOW_NAMES_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_WIDGET_SHOW_NAMES_ONLY, value).apply()

    var passwordHint: String
        get() = prefs.getString("password_hint", "") ?: ""
        set(value) = prefs.edit().putString("password_hint", value).apply()

    var preventScreenCapture: Boolean
        get() = prefs.getBoolean(KEY_PREVENT_SCREEN_CAPTURE, BuildConfig.DEFAULT_PREVENT_SCREEN_CAPTURE)
        set(value) = prefs.edit().putBoolean(KEY_PREVENT_SCREEN_CAPTURE, value).apply()

    var syncType: com.example.sync.SyncType
        get() {
            val name = prefs.getString(KEY_SYNC_TYPE, com.example.sync.SyncType.DISABLED.name) ?: com.example.sync.SyncType.DISABLED.name
            return try {
                com.example.sync.SyncType.valueOf(name)
            } catch (_: Exception) {
                com.example.sync.SyncType.DISABLED
            }
        }
        set(value) = prefs.edit().putString(KEY_SYNC_TYPE, value.name).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, value).apply()

    var webDavServerUrl: String
        get() = prefs.getString(KEY_WEBDAV_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEBDAV_SERVER_URL, value).apply()

    var webDavUsername: String
        get() = prefs.getString(KEY_WEBDAV_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEBDAV_USERNAME, value).apply()

    var webDavPasswordEncrypted: String
        get() = prefs.getString(KEY_WEBDAV_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEBDAV_PASSWORD, value).apply()

    var webDavRemotePath: String
        get() = prefs.getString(KEY_WEBDAV_REMOTE_PATH, "vaultkeep_backup.vkeep") ?: "vaultkeep_backup.vkeep"
        set(value) = prefs.edit().putString(KEY_WEBDAV_REMOTE_PATH, value).apply()

    var localFolderTreeUri: String
        get() = prefs.getString(KEY_LOCAL_FOLDER_URI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LOCAL_FOLDER_URI, value).apply()

    var localFolderFileName: String
        get() = prefs.getString(KEY_LOCAL_FOLDER_FILE_NAME, "vaultkeep_backup.vkeep") ?: "vaultkeep_backup.vkeep"
        set(value) = prefs.edit().putString(KEY_LOCAL_FOLDER_FILE_NAME, value).apply()

    fun getWebDavConfig(): com.example.sync.WebDavConfig {
        return com.example.sync.WebDavConfig(
            serverUrl = webDavServerUrl,
            username = webDavUsername,
            password = webDavPasswordEncrypted,
            remotePath = webDavRemotePath
        )
    }

    fun getLocalFolderConfig(): com.example.sync.LocalFolderConfig {
        return com.example.sync.LocalFolderConfig(
            treeUriString = localFolderTreeUri,
            fileName = localFolderFileName
        )
    }

    // Hardware Security Key (FIDO2/WebAuthn) enrollment settings
    var isHardwareKeyEnabled: Boolean
        get() = prefs.getBoolean(KEY_HARDWARE_KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_HARDWARE_KEY_ENABLED, value).apply()

    var hardwareKeyCredentialId: String
        get() = prefs.getString(KEY_HARDWARE_KEY_CRED_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HARDWARE_KEY_CRED_ID, value).apply()

    var hardwareKeyPublicKeyCose: String
        get() = prefs.getString(KEY_HARDWARE_KEY_PUBLIC_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HARDWARE_KEY_PUBLIC_KEY, value).apply()

    var hardwareKeyName: String
        get() = prefs.getString(KEY_HARDWARE_KEY_NAME, "FIDO2 Security Key") ?: "FIDO2 Security Key"
        set(value) = prefs.edit().putString(KEY_HARDWARE_KEY_NAME, value).apply()
}
