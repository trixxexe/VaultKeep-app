package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.crypto.PasswordGenerator
import com.example.crypto.TotpGenerator
import com.example.data.*
import com.example.ui.components.QrScannerDialog
import com.example.ui.components.StrengthMeter
import com.example.ui.theme.SecurityRed
import com.example.ui.util.InputSanitizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryEditDialog(
    entryToEdit: VaultEntry?,
    onDismissRequest: () -> Unit,
    onSave: (VaultEntry) -> Unit,
    onDelete: ((VaultEntry) -> Unit)? = null,
    onCopyPassword: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedType by rememberSaveable {
        mutableStateOf(entryToEdit?.entryType ?: EntryType.PASSWORD)
    }

    var title by rememberSaveable { mutableStateOf(entryToEdit?.title ?: "") }
    var username by rememberSaveable { mutableStateOf(entryToEdit?.username ?: "") }
    var password by rememberSaveable { mutableStateOf(entryToEdit?.password ?: "") }
    var url by rememberSaveable { mutableStateOf(entryToEdit?.url ?: "") }
    var folder by rememberSaveable { mutableStateOf(entryToEdit?.folder ?: "") }
    var tagInput by rememberSaveable { mutableStateOf(entryToEdit?.tags?.joinToString(", ") ?: "") }
    var isFavorite by rememberSaveable { mutableStateOf(entryToEdit?.isFavorite ?: false) }
    var totpSecret by rememberSaveable { mutableStateOf(entryToEdit?.totpSecret ?: "") }
    var notes by rememberSaveable { mutableStateOf(entryToEdit?.notes ?: "") }
    var passkeyRpId by rememberSaveable { mutableStateOf(entryToEdit?.passkeyRpId ?: "") }

    // Credit Card Fields
    var cardholderName by rememberSaveable { mutableStateOf(entryToEdit?.cardholderName ?: "") }
    var cardNumber by rememberSaveable { mutableStateOf(entryToEdit?.cardNumber ?: "") }
    var cardExpiry by rememberSaveable { mutableStateOf(entryToEdit?.cardExpiry ?: "") }
    var cardCvv by rememberSaveable { mutableStateOf(entryToEdit?.cardCvv ?: "") }
    var cardPin by rememberSaveable { mutableStateOf(entryToEdit?.cardPin ?: "") }

    // Identity Fields
    var identityName by rememberSaveable { mutableStateOf(entryToEdit?.identityName ?: "") }
    var identityAddress by rememberSaveable { mutableStateOf(entryToEdit?.identityAddress ?: "") }
    var identityIdNumber by rememberSaveable { mutableStateOf(entryToEdit?.identityIdNumber ?: "") }
    var identityPhone by rememberSaveable { mutableStateOf(entryToEdit?.identityPhone ?: "") }
    var identityEmail by rememberSaveable { mutableStateOf(entryToEdit?.identityEmail ?: "") }

    // Custom Fields
    var customFields by remember { mutableStateOf(entryToEdit?.customFields ?: emptyList()) }
    var showAddCustomFieldDialog by rememberSaveable { mutableStateOf(false) }

    // Attachments
    var attachments by remember { mutableStateOf(entryToEdit?.attachments ?: emptyList()) }

    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isCvvVisible by rememberSaveable { mutableStateOf(false) }
    var showGeneratorDialog by rememberSaveable { mutableStateOf(false) }
    var showQrScanner by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    val strength = remember(password) { PasswordGenerator.evaluateStrength(password) }
    val entropyBits = remember(password) { PasswordGenerator.calculateEntropy(password) }

    var breachMatch by remember { mutableStateOf<com.example.data.BreachMatch?>(null) }
    LaunchedEffect(password) {
        if (password.isNotBlank()) {
            breachMatch = com.example.data.OfflineBreachDatabase.checkFastSync(context, password)
                ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.data.OfflineBreachDatabase.checkSinglePassword(context, password)
                }
        } else {
            breachMatch = null
        }
    }

    val isEditing = entryToEdit != null

    // Attachment Picker
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver
                var fileName = "attachment"
                var fileSize = 0L
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }

                if (fileSize > EncryptedAttachment.MAX_ATTACHMENT_SIZE_BYTES) {
                    Toast.makeText(context, "File exceeds 2 MB limit for encrypted attachments", Toast.LENGTH_LONG).show()
                    return@rememberLauncherForActivityResult
                }

                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    if (bytes.size > EncryptedAttachment.MAX_ATTACHMENT_SIZE_BYTES) {
                        Toast.makeText(context, "File exceeds 2 MB limit", Toast.LENGTH_LONG).show()
                        return@rememberLauncherForActivityResult
                    }
                    val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                    val newAttachment = EncryptedAttachment(
                        fileName = InputSanitizer.filterLiveSingleLine(fileName, InputSanitizer.MAX_TITLE_LENGTH),
                        mimeType = mimeType,
                        fileSize = bytes.size.toLong(),
                        dataBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    )
                    attachments = attachments + newAttachment
                    Toast.makeText(context, "Attachment added ($fileName)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read attachment: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showQrScanner) {
        QrScannerDialog(
            onDismiss = { showQrScanner = false },
            onQrCodeScanned = { config ->
                totpSecret = InputSanitizer.filterLiveSingleLine(config.secret, 200)
                if (title.isBlank() && config.issuer.isNotBlank()) {
                    title = InputSanitizer.filterLiveSingleLine(config.issuer, InputSanitizer.MAX_TITLE_LENGTH)
                }
                if (username.isBlank() && config.accountName.isNotBlank()) {
                    username = InputSanitizer.filterLiveSingleLine(config.accountName, InputSanitizer.MAX_USERNAME_LENGTH)
                }
                showQrScanner = false
            }
        )
    }

    if (showGeneratorDialog) {
        PasswordGeneratorDialog(
            onDismissRequest = { showGeneratorDialog = false },
            onUsePassword = { generated ->
                password = generated
                showGeneratorDialog = false
            },
            onCopyPassword = onCopyPassword
        )
    }

    if (showDeleteConfirm && entryToEdit != null) {
        Dialog(onDismissRequest = { showDeleteConfirm = false }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Delete Entry",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Are you sure you want to delete \"${entryToEdit.title}\"? This action cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                showDeleteConfirm = false
                                onDelete?.invoke(entryToEdit)
                                onDismissRequest()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SecurityRed),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }

    if (showAddCustomFieldDialog) {
        AddCustomFieldDialog(
            onDismiss = { showAddCustomFieldDialog = false },
            onAddField = { field ->
                customFields = customFields + field
                showAddCustomFieldDialog = false
            }
        )
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("entry_edit_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditing) "Edit ${selectedType.name.replace("_", " ")}" else "New Vault Entry",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { isFavorite = !isFavorite },
                            modifier = Modifier.size(36.dp).testTag("entry_favorite_toggle")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isEditing && onDelete != null) {
                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.size(36.dp).testTag("delete_entry_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Entry",
                                    tint = SecurityRed
                                )
                            }
                        }
                    }
                }

                // Error Banner
                AnimatedVisibility(visible = validationError != null) {
                    Text(
                        text = validationError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecurityRed
                    )
                }

                // Entry Type Selector (When creating new entry)
                if (!isEditing) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedType == EntryType.PASSWORD,
                            onClick = { selectedType = EntryType.PASSWORD },
                            label = { Text("Login") },
                            leadingIcon = { Icon(Icons.Default.Password, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            shape = RoundedCornerShape(10.dp)
                        )
                        FilterChip(
                            selected = selectedType == EntryType.PASSKEY,
                            onClick = { selectedType = EntryType.PASSKEY },
                            label = { Text("Passkey") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            shape = RoundedCornerShape(10.dp)
                        )
                        FilterChip(
                            selected = selectedType == EntryType.SECURE_NOTE,
                            onClick = { selectedType = EntryType.SECURE_NOTE },
                            label = { Text("Secure Note") },
                            leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            shape = RoundedCornerShape(10.dp)
                        )
                        FilterChip(
                            selected = selectedType == EntryType.CREDIT_CARD,
                            onClick = { selectedType = EntryType.CREDIT_CARD },
                            label = { Text("Card") },
                            leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            shape = RoundedCornerShape(10.dp)
                        )
                        FilterChip(
                            selected = selectedType == EntryType.IDENTITY,
                            onClick = { selectedType = EntryType.IDENTITY },
                            label = { Text("Identity") },
                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Common Field: Title
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        val cleanTitle = InputSanitizer.filterLiveSingleLine(it, InputSanitizer.MAX_TITLE_LENGTH)
                        title = cleanTitle
                        validationError = null
                        if (selectedType == EntryType.PASSKEY && passkeyRpId.isBlank() && cleanTitle.isNotBlank()) {
                            passkeyRpId = cleanTitle.trim().lowercase().replace(" ", "") + ".com"
                        }
                    },
                    label = { Text("Title / Name *") },
                    placeholder = {
                        Text(when (selectedType) {
                            EntryType.CREDIT_CARD -> "e.g. Primary Visa, Corporate Amex"
                            EntryType.IDENTITY -> "e.g. Personal Passport, Driver's License"
                            EntryType.SECURE_NOTE -> "e.g. Server Recovery Mnemonic"
                            else -> "e.g. GitHub, Google, Work Email"
                        })
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_title_input"),
                    shape = RoundedCornerShape(16.dp)
                )

                // Type-Specific Fields
                when (selectedType) {
                    EntryType.PASSWORD -> {
                        // Username
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = InputSanitizer.filterLiveSingleLine(it, InputSanitizer.MAX_USERNAME_LENGTH) },
                            label = { Text("Username or Email *") },
                            placeholder = { Text("e.g. alex@example.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth().testTag("entry_username_input"),
                            shape = RoundedCornerShape(16.dp)
                        )

                        // Password
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = InputSanitizer.sanitizeText(it, InputSanitizer.MAX_PASSWORD_LENGTH)
                                validationError = null
                            },
                            label = { Text("Password *") },
                            placeholder = { Text("Enter or generate password") },
                            singleLine = true,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { showGeneratorDialog = true },
                                        modifier = Modifier.testTag("open_inline_generator_button")
                                    ) {
                                        Icon(Icons.Default.AutoFixHigh, contentDescription = "Generate Password", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(
                                        onClick = { isPasswordVisible = !isPasswordVisible },
                                        modifier = Modifier.testTag("toggle_entry_password_visibility")
                                    ) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isPasswordVisible) "Hide" else "Show",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("entry_password_input"),
                            shape = RoundedCornerShape(16.dp)
                        )

                        if (password.isNotEmpty()) {
                            StrengthMeter(strength = strength, entropyBits = entropyBits, modifier = Modifier.padding(horizontal = 4.dp))

                            if (breachMatch != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .testTag("edit_breach_warning_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Breached Password",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "⚠️ Warning: Leaked in ${breachMatch?.databaseDisplayName}",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                text = "Rank #${breachMatch?.rank} in leaked brute-force wordlist. High risk of dictionary attacks.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    color = Color(0xFF1B5E20).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .testTag("edit_breach_safe_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Safe",
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "✓ Offline Breach Check: Normal Yay! Not found in rockyou.txt",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                            }
                        }

                        // URL
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = InputSanitizer.filterLiveSingleLine(it, InputSanitizer.MAX_URL_LENGTH) },
                            label = { Text("Website URL (for Autofill)") },
                            placeholder = { Text("https://example.com/login") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth().testTag("entry_url_input"),
                            shape = RoundedCornerShape(16.dp)
                        )

                        // 2FA / TOTP
                        OutlinedTextField(
                            value = totpSecret,
                            onValueChange = { totpSecret = InputSanitizer.filterLiveSingleLine(it, 200) },
                            label = { Text("2FA / TOTP Authenticator Key") },
                            placeholder = { Text("Base32 Key") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { showQrScanner = true }) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("entry_totp_input"),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    EntryType.PASSKEY -> {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = InputSanitizer.filterLiveSingleLine(it, InputSanitizer.MAX_USERNAME_LENGTH) },
                            label = { Text("Username or Email *") },
                            placeholder = { Text("e.g. alex@example.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth().testTag("entry_username_input"),
                            shape = RoundedCornerShape(16.dp)
                        )

                        OutlinedTextField(
                            value = passkeyRpId,
                            onValueChange = { passkeyRpId = InputSanitizer.filterLiveSingleLine(it, InputSanitizer.MAX_URL_LENGTH) },
                            label = { Text("Relying Party Domain (RP ID) *") },
                            placeholder = { Text("e.g. github.com, google.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth().testTag("passkey_rpid_input"),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    EntryType.SECURE_NOTE -> {
                        // Note body takes precedence in the main notes field
                    }

                    EntryType.CREDIT_CARD -> {
                        OutlinedTextField(
                            value = cardholderName,
                            onValueChange = { cardholderName = InputSanitizer.filterLiveSingleLine(it, InputSanitizer.MAX_TITLE_LENGTH) },
                            label = { Text("Cardholder Name") },
                            placeholder = { Text("Alex Smith") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )

                        OutlinedTextField(
                            value = cardNumber,
                            onValueChange = { cardNumber = it.filter { c -> c.isDigit() || c == ' ' }.take(30) },
                            label = { Text("Card Number *") },
                            placeholder = { Text("•••• •••• •••• ••••") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = cardExpiry,
                                onValueChange = { cardExpiry = InputSanitizer.filterLiveSingleLine(it, 10) },
                                label = { Text("Expiry (MM/YY)") },
                                placeholder = { Text("12/28") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            OutlinedTextField(
                                value = cardCvv,
                                onValueChange = { cardCvv = it.filter { c -> c.isDigit() }.take(6) },
                                label = { Text("CVV / CVC") },
                                placeholder = { Text("•••") },
                                singleLine = true,
                                visualTransformation = if (isCvvVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isCvvVisible = !isCvvVisible }) {
                                        Icon(
                                            imageVector = if (isCvvVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            OutlinedTextField(
                                value = cardPin,
                                onValueChange = { cardPin = it.filter { c -> c.isDigit() }.take(12) },
                                label = { Text("PIN") },
                                placeholder = { Text("••••") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    EntryType.IDENTITY -> {
                        OutlinedTextField(
                            value = identityName,
                            onValueChange = { identityName = InputSanitizer.filterLiveSingleLine(it, InputSanitizer.MAX_TITLE_LENGTH) },
                            label = { Text("Full Legal Name") },
                            placeholder = { Text("Alexander Smith") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )

                        OutlinedTextField(
                            value = identityIdNumber,
                            onValueChange = { identityIdNumber = InputSanitizer.filterLiveSingleLine(it, 50) },
                            label = { Text("ID / Passport / SSN Number") },
                            placeholder = { Text("A12345678") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )

                        OutlinedTextField(
                            value = identityAddress,
                            onValueChange = { identityAddress = InputSanitizer.sanitizeText(it, 300) },
                            label = { Text("Full Address") },
                            placeholder = { Text("123 Main St, Springfield, USA") },
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = identityPhone,
                                onValueChange = { identityPhone = InputSanitizer.filterLiveSingleLine(it, 30) },
                                label = { Text("Phone") },
                                placeholder = { Text("+1 555-0199") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            OutlinedTextField(
                                value = identityEmail,
                                onValueChange = { identityEmail = InputSanitizer.filterLiveSingleLine(it, InputSanitizer.MAX_USERNAME_LENGTH) },
                                label = { Text("Email") },
                                placeholder = { Text("alex@domain.com") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                }

                // Organization: Folder & Tags
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = folder,
                        onValueChange = { folder = InputSanitizer.filterLiveSingleLine(it, InputSanitizer.MAX_FOLDER_LENGTH) },
                        label = { Text("Folder") },
                        placeholder = { Text("e.g. Work, Banking") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("entry_folder_input"),
                        shape = RoundedCornerShape(16.dp)
                    )

                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = InputSanitizer.filterLiveSingleLine(it, 200) },
                        label = { Text("Tags (comma sep.)") },
                        placeholder = { Text("finance, personal") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                // Notes Input
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = InputSanitizer.sanitizeText(it, InputSanitizer.MAX_NOTE_LENGTH) },
                    label = { Text(if (selectedType == EntryType.SECURE_NOTE) "Encrypted Note Body *" else "Notes (Optional)") },
                    placeholder = { Text("Encrypted at rest with AES-256-GCM...") },
                    minLines = if (selectedType == EntryType.SECURE_NOTE) 5 else 2,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth().testTag("entry_notes_input"),
                    shape = RoundedCornerShape(16.dp)
                )

                // Custom Fields Section
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Custom Fields (${customFields.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { showAddCustomFieldDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Field")
                    }
                }

                customFields.forEachIndexed { index, field ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = if (field.fieldType == CustomFieldType.SECRET) "••••••••" else field.value,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            IconButton(onClick = {
                                customFields = customFields.filterIndexed { i, _ -> i != index }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove Field", tint = SecurityRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Attachments Section
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Encrypted Attachments (${attachments.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { attachmentPicker.launch("*/*") }) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Attach File")
                    }
                }

                attachments.forEachIndexed { index, att ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(att.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("${att.fileSize / 1024} KB • Encrypted at rest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                attachments = attachments.filterIndexed { i, _ -> i != index }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = SecurityRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Metadata timestamps
                if (entryToEdit != null) {
                    val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                    Text(
                        text = "Last modified: ${dateFormat.format(Date(entryToEdit.updatedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f).testTag("cancel_entry_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val sanitizedTitle = InputSanitizer.sanitizeSingleLine(title, InputSanitizer.MAX_TITLE_LENGTH)
                            val sanitizedUsername = InputSanitizer.sanitizeSingleLine(username, InputSanitizer.MAX_USERNAME_LENGTH)
                            val sanitizedPassword = InputSanitizer.sanitizeText(password, InputSanitizer.MAX_PASSWORD_LENGTH)
                            val sanitizedNotes = InputSanitizer.sanitizeText(notes, InputSanitizer.MAX_NOTE_LENGTH)
                            val sanitizedUrl = InputSanitizer.sanitizeUrl(url)

                            if (sanitizedTitle.isBlank()) {
                                validationError = "Please enter a valid title or name"
                                return@Button
                            }

                            val urlVal = InputSanitizer.validateUrl(url)
                            if (!urlVal.isValid) {
                                validationError = urlVal.errorMessage ?: "Invalid URL"
                                return@Button
                            }

                            if (selectedType == EntryType.PASSWORD && sanitizedPassword.isBlank()) {
                                validationError = "Please enter or generate a password"
                                return@Button
                            }

                            if (selectedType == EntryType.PASSKEY && sanitizedUsername.isBlank()) {
                                validationError = "Please enter an account username for this passkey"
                                return@Button
                            }

                            if (selectedType == EntryType.SECURE_NOTE && sanitizedNotes.isBlank()) {
                                validationError = "Please enter text for this secure note"
                                return@Button
                            }

                            if (selectedType == EntryType.CREDIT_CARD && cardNumber.isBlank()) {
                                validationError = "Please enter a card number"
                                return@Button
                            }

                            val cleanTags = tagInput.split(",")
                                .map { InputSanitizer.sanitizeSingleLine(it, InputSanitizer.MAX_TAG_LENGTH) }
                                .filter { it.isNotBlank() }
                                .distinct()
                            val cleanRpId = if (passkeyRpId.isNotBlank()) InputSanitizer.sanitizeSingleLine(passkeyRpId, InputSanitizer.MAX_URL_LENGTH) else sanitizedTitle.lowercase().replace(" ", "") + ".com"

                            // Password history tracking
                            val history = if (entryToEdit != null && entryToEdit.password.isNotBlank() && entryToEdit.password != sanitizedPassword) {
                                (listOf(PasswordHistoryItem(entryToEdit.password)) + entryToEdit.passwordHistory).take(5)
                            } else {
                                entryToEdit?.passwordHistory ?: emptyList()
                            }

                            val newOrUpdated = if (entryToEdit != null) {
                                entryToEdit.copy(
                                    title = sanitizedTitle,
                                    username = sanitizedUsername,
                                    password = sanitizedPassword,
                                    notes = sanitizedNotes,
                                    folder = InputSanitizer.sanitizeSingleLine(folder, InputSanitizer.MAX_FOLDER_LENGTH),
                                    tags = cleanTags,
                                    isFavorite = isFavorite,
                                    totpSecret = InputSanitizer.sanitizeSingleLine(totpSecret, 200),
                                    url = sanitizedUrl,
                                    entryType = selectedType,
                                    cardholderName = InputSanitizer.sanitizeSingleLine(cardholderName, InputSanitizer.MAX_TITLE_LENGTH),
                                    cardNumber = cardNumber.trim(),
                                    cardExpiry = InputSanitizer.sanitizeSingleLine(cardExpiry, 10),
                                    cardCvv = cardCvv.trim(),
                                    cardPin = cardPin.trim(),
                                    identityName = InputSanitizer.sanitizeSingleLine(identityName, InputSanitizer.MAX_TITLE_LENGTH),
                                    identityAddress = InputSanitizer.sanitizeText(identityAddress, 300),
                                    identityIdNumber = InputSanitizer.sanitizeSingleLine(identityIdNumber, 50),
                                    identityPhone = InputSanitizer.sanitizeSingleLine(identityPhone, 30),
                                    identityEmail = InputSanitizer.sanitizeSingleLine(identityEmail, InputSanitizer.MAX_USERNAME_LENGTH),
                                    customFields = customFields,
                                    passwordHistory = history,
                                    attachments = attachments,
                                    passkeyRpId = cleanRpId,
                                    updatedAt = System.currentTimeMillis()
                                )
                            } else {
                                if (selectedType == EntryType.PASSKEY) {
                                    val reg = com.example.passkey.PasskeyCryptoHelper.createPasskey(
                                        rpId = cleanRpId,
                                        userName = sanitizedUsername
                                    )
                                    VaultEntry(
                                        title = sanitizedTitle,
                                        username = sanitizedUsername,
                                        url = sanitizedUrl.ifBlank { if (cleanRpId.startsWith("http")) cleanRpId else "https://$cleanRpId" },
                                        folder = InputSanitizer.sanitizeSingleLine(folder, InputSanitizer.MAX_FOLDER_LENGTH),
                                        tags = cleanTags,
                                        isFavorite = isFavorite,
                                        notes = sanitizedNotes,
                                        entryType = EntryType.PASSKEY,
                                        customFields = customFields,
                                        attachments = attachments,
                                        passkeyCredentialId = reg.credentialIdBase64Url,
                                        passkeyRpId = reg.rpId,
                                        passkeyUserHandle = reg.userHandleBase64Url,
                                        passkeyPrivateKeyPkcs8 = reg.privateKeyPkcs8Base64,
                                        passkeyPublicKeyCose = reg.publicKeyCoseBase64,
                                        passkeySignCount = 0,
                                        createdAt = System.currentTimeMillis(),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                } else {
                                    VaultEntry(
                                        title = sanitizedTitle,
                                        username = sanitizedUsername,
                                        password = sanitizedPassword,
                                        url = sanitizedUrl,
                                        folder = InputSanitizer.sanitizeSingleLine(folder, InputSanitizer.MAX_FOLDER_LENGTH),
                                        tags = cleanTags,
                                        isFavorite = isFavorite,
                                        totpSecret = InputSanitizer.sanitizeSingleLine(totpSecret, 200),
                                        notes = sanitizedNotes,
                                        entryType = selectedType,
                                        cardholderName = InputSanitizer.sanitizeSingleLine(cardholderName, InputSanitizer.MAX_TITLE_LENGTH),
                                        cardNumber = cardNumber.trim(),
                                        cardExpiry = InputSanitizer.sanitizeSingleLine(cardExpiry, 10),
                                        cardCvv = cardCvv.trim(),
                                        cardPin = cardPin.trim(),
                                        identityName = InputSanitizer.sanitizeSingleLine(identityName, InputSanitizer.MAX_TITLE_LENGTH),
                                        identityAddress = InputSanitizer.sanitizeText(identityAddress, 300),
                                        identityIdNumber = InputSanitizer.sanitizeSingleLine(identityIdNumber, 50),
                                        identityPhone = InputSanitizer.sanitizeSingleLine(identityPhone, 30),
                                        identityEmail = InputSanitizer.sanitizeSingleLine(identityEmail, InputSanitizer.MAX_USERNAME_LENGTH),
                                        customFields = customFields,
                                        passwordHistory = emptyList(),
                                        attachments = attachments,
                                        createdAt = System.currentTimeMillis(),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                }
                            }
                            onSave(newOrUpdated)
                        },
                        modifier = Modifier.weight(1f).testTag("save_entry_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun AddCustomFieldDialog(
    onDismiss: () -> Unit,
    onAddField: (CustomField) -> Unit
) {
    var label by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    var fieldType by rememberSaveable { mutableStateOf(CustomFieldType.PLAIN_TEXT) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Add Custom Field", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = label,
                    onValueChange = {
                        label = InputSanitizer.filterLiveSingleLine(it, InputSanitizer.MAX_CUSTOM_FIELD_LABEL_LENGTH)
                        error = null
                    },
                    label = { Text("Field Label") },
                    placeholder = { Text("e.g. Security Question, Server PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = if (fieldType == CustomFieldType.PLAIN_TEXT) {
                            InputSanitizer.sanitizeText(it, InputSanitizer.MAX_CUSTOM_FIELD_VALUE_LENGTH)
                        } else {
                            InputSanitizer.filterLiveSingleLine(it, InputSanitizer.MAX_CUSTOM_FIELD_VALUE_LENGTH)
                        }
                        error = null
                    },
                    label = { Text("Field Value") },
                    placeholder = { Text("Secret or text value") },
                    singleLine = fieldType != CustomFieldType.PLAIN_TEXT,
                    visualTransformation = if (fieldType == CustomFieldType.SECRET) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Text("Field Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CustomFieldType.entries.forEach { type ->
                        FilterChip(
                            selected = fieldType == type,
                            onClick = { fieldType = type },
                            label = { Text(type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                if (error != null) {
                    Text(error ?: "", color = SecurityRed, style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cleanLabel = InputSanitizer.sanitizeSingleLine(label, InputSanitizer.MAX_CUSTOM_FIELD_LABEL_LENGTH)
                            if (cleanLabel.isBlank()) {
                                error = "Please provide a valid label"
                                return@Button
                            }
                            val cleanValue = if (fieldType == CustomFieldType.PLAIN_TEXT) {
                                InputSanitizer.sanitizeText(value, InputSanitizer.MAX_CUSTOM_FIELD_VALUE_LENGTH)
                            } else {
                                InputSanitizer.sanitizeSingleLine(value, InputSanitizer.MAX_CUSTOM_FIELD_VALUE_LENGTH)
                            }
                            onAddField(CustomField(label = cleanLabel, value = cleanValue, fieldType = fieldType))
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}
