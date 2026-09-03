package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.PasswordGenerator
import com.example.crypto.TotpGenerator
import com.example.data.CustomFieldType
import com.example.data.EntryType
import com.example.data.VaultEntry
import com.example.ui.MainViewModel
import com.example.ui.components.StrengthMeter
import com.example.ui.theme.SecurityRed
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

private val BadgeBgGitHub = Color(0xFF24292E)
private val BadgeFgGitHub = Color(0xFFF0F6FC)
private val BadgeBgProton = Color(0xFF2C2448)
private val BadgeFgProton = Color(0xFFD4C5F9)
private val BadgeBgSlack = Color(0xFF1E3A34)
private val BadgeFgSlack = Color(0xFFA3E7D8)
private val BadgeBgBank = Color(0xFF382E1E)
private val BadgeFgBank = Color(0xFFFFDDB3)
private val BadgeBgPersonal = Color(0xFF2D2F31)
private val BadgeFgPersonal = Color(0xFFE2E2E6)

private fun getBadgeColors(title: String): Pair<Color, Color> {
    val lower = title.lowercase(Locale.ROOT)
    return when {
        lower.contains("git") -> BadgeBgGitHub to BadgeFgGitHub
        lower.contains("proton") || lower.contains("mail") -> BadgeBgProton to BadgeFgProton
        lower.contains("slack") || lower.contains("cloud") || lower.contains("google") -> BadgeBgSlack to BadgeFgSlack
        lower.contains("bank") || lower.contains("pay") || lower.contains("card") -> BadgeBgBank to BadgeFgBank
        else -> {
            val hash = abs(lower.hashCode())
            when (hash % 5) {
                0 -> BadgeBgGitHub to BadgeFgGitHub
                1 -> BadgeBgProton to BadgeFgProton
                2 -> BadgeBgSlack to BadgeFgSlack
                3 -> BadgeBgBank to BadgeFgBank
                else -> BadgeBgPersonal to BadgeFgPersonal
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryDetailScreen(
    entry: VaultEntry,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isCvvVisible by remember { mutableStateOf(false) }
    var isPinVisible by remember { mutableStateOf(false) }
    var revealedCustomFields by remember { mutableStateOf(setOf<Int>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showPasswordHistory by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val totpTick by viewModel.totpTick.collectAsState()
    val totpCode = remember(entry.totpSecret, totpTick) {
        if (entry.totpSecret.isNotBlank()) TotpGenerator.generateTotpCode(entry.totpSecret) else null
    }
    val totpRemaining = remember(totpTick) {
        TotpGenerator.getRemainingSeconds()
    }

    val strength = remember(entry.password) { PasswordGenerator.evaluateStrength(entry.password) }
    val entropyBits = remember(entry.password) { PasswordGenerator.calculateEntropy(entry.password) }

    val breachMatches by viewModel.offlineBreachMatches.collectAsState()
    val breachMatch = remember(entry.password, breachMatches) {
        breachMatches[entry.password] ?: viewModel.checkFastBreach(entry.password)
    }

    val initial = if (entry.title.isNotBlank()) entry.title.first().uppercase() else "V"
    val (badgeBg, badgeFg) = getBadgeColors(entry.title)

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Entry") },
            text = { Text("Are you sure you want to delete \"${entry.title}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SecurityRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("Share Entry") },
            text = {
                Text(
                    text = if (entry.isPasskey)
                        "Share account details for \"${entry.title}\" (${entry.passkeyRpId})?"
                    else
                        "How would you like to share \"${entry.title}\"? Be cautious when transmitting passwords across insecure apps."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showShareDialog = false
                        viewModel.shareEntryDetails(context, entry, includePassword = false)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (entry.isPasskey) "Share Info" else "Share Without Password")
                }
            },
            dismissButton = {
                if (entry.entryType == EntryType.PASSWORD && entry.password.isNotBlank()) {
                    TextButton(
                        onClick = {
                            showShareDialog = false
                            viewModel.shareEntryDetails(context, entry, includePassword = true)
                        }
                    ) {
                        Text("Include Password", color = SecurityRed)
                    }
                } else {
                    TextButton(onClick = { showShareDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when (entry.entryType) {
                    EntryType.SECURE_NOTE -> "Secure Note"
                    EntryType.CREDIT_CARD -> "Payment Card"
                    EntryType.IDENTITY -> "Identity Item"
                    EntryType.PASSKEY -> "Passkey Details"
                    else -> "Credential Details"
                }) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showShareDialog = true },
                        modifier = Modifier.testTag("detail_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Entry",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleFavorite(entry.id) },
                        modifier = Modifier.testTag("detail_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (entry.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Toggle Favorite",
                            tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.testTag("detail_edit_button")) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.testTag("detail_delete_button")) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = SecurityRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card with Brand Avatar, Title, Folder, Tags
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(badgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (entry.entryType) {
                                EntryType.SECURE_NOTE -> Icons.Default.Notes
                                EntryType.CREDIT_CARD -> Icons.Default.CreditCard
                                EntryType.IDENTITY -> Icons.Default.Badge
                                EntryType.PASSKEY -> Icons.Default.Key
                                else -> Icons.Default.Password
                            },
                            contentDescription = null,
                            tint = badgeFg,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (entry.entryType != EntryType.PASSWORD) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = entry.entryType.name.replace("_", " "),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (entry.folder.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = entry.folder,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tags display
            if (entry.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    entry.tags.forEach { tag ->
                        SuggestionChip(
                            onClick = { },
                            label = { Text("#$tag", fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // TYPE SPECIFIC SECTIONS
            when (entry.entryType) {
                EntryType.PASSWORD -> {
                    // Username Card
                    if (entry.username.isNotBlank()) {
                        DetailItemCard(
                            label = "Username / Email",
                            value = entry.username,
                            onCopy = { viewModel.copyToClipboard(entry.username, "Username", false) }
                        )
                    }

                    // Password Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Password",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isPasswordVisible) entry.password else "••••••••••••••••",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = if (isPasswordVisible) 0.5.sp else 2.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )

                                Row {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isPasswordVisible) "Hide Password" else "Show Password",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { viewModel.copyToClipboard(entry.password, "Password", true) }) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Password",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // Strength Meter
                            StrengthMeter(
                                strength = strength,
                                entropyBits = entropyBits,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            // Offline Breach Database Check Banner
                            if (entry.password.isNotBlank()) {
                                if (breachMatch != null) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("entry_breach_warning_card"),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Breached Password",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(26.dp)
                                            )
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = "⚠️ LEAKED IN ${breachMatch.databaseDisplayName.uppercase()}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                                Text(
                                                    text = "This password was exposed in public leaks (Rank #${breachMatch.rank}). Brute-force attackers check this database first. Please change it immediately!",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("entry_breach_safe_card"),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFF1B5E20).copy(alpha = 0.12f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Offline Breach Safe",
                                                tint = Color(0xFF2E7D32),
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = "✓ Offline Breach Check: Normal Yay!",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF2E7D32)
                                                )
                                                Text(
                                                    text = "Not found in rockyou.txt or any of the 709,839 offline breach records. Clear of common brute-force wordlists.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    lineHeight = 15.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2FA / TOTP Card
                    if (totpCode != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Two-Factor Authentication (TOTP)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${totpRemaining}s",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (totpRemaining <= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = totpCode.chunked(3).joinToString(" "),
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    IconButton(onClick = { viewModel.copyToClipboard(totpCode, "TOTP Code", true) }) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy TOTP",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Website URL
                    if (entry.url.isNotBlank()) {
                        DetailItemCard(
                            label = "Website URL",
                            value = entry.url,
                            onCopy = { viewModel.copyToClipboard(entry.url, "URL", false) }
                        )
                    }
                }

                EntryType.PASSKEY -> {
                    if (entry.username.isNotBlank()) {
                        DetailItemCard(
                            label = "Username / Account",
                            value = entry.username,
                            onCopy = { viewModel.copyToClipboard(entry.username, "Username", false) }
                        )
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "FIDO2 / WebAuthn Passkey",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            DetailItemCard(
                                label = "Relying Party (RP ID)",
                                value = entry.passkeyRpId.ifBlank { "Unspecified" },
                                onCopy = { viewModel.copyToClipboard(entry.passkeyRpId, "RP ID", false) }
                            )

                            if (entry.passkeyCredentialId.isNotBlank()) {
                                DetailItemCard(
                                    label = "Credential ID",
                                    value = entry.passkeyCredentialId,
                                    onCopy = { viewModel.copyToClipboard(entry.passkeyCredentialId, "Credential ID", false) }
                                )
                            }
                        }
                    }
                }

                EntryType.CREDIT_CARD -> {
                    if (entry.cardholderName.isNotBlank()) {
                        DetailItemCard(
                            label = "Cardholder Name",
                            value = entry.cardholderName,
                            onCopy = { viewModel.copyToClipboard(entry.cardholderName, "Cardholder", false) }
                        )
                    }

                    DetailItemCard(
                        label = "Card Number",
                        value = entry.cardNumber.chunked(4).joinToString(" "),
                        onCopy = { viewModel.copyToClipboard(entry.cardNumber.replace(" ", ""), "Card Number", true) }
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (entry.cardExpiry.isNotBlank()) {
                            Box(modifier = Modifier.weight(1f)) {
                                DetailItemCard(
                                    label = "Expiry",
                                    value = entry.cardExpiry,
                                    onCopy = { viewModel.copyToClipboard(entry.cardExpiry, "Expiry", false) }
                                )
                            }
                        }
                        if (entry.cardCvv.isNotBlank()) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("CVV", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (isCvvVisible) entry.cardCvv else "•••", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                        Row {
                                            IconButton(onClick = { isCvvVisible = !isCvvVisible }, modifier = Modifier.size(28.dp)) {
                                                Icon(if (isCvvVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(onClick = { viewModel.copyToClipboard(entry.cardCvv, "CVV", true) }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (entry.cardPin.isNotBlank()) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("PIN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (isPinVisible) entry.cardPin else "••••", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                        Row {
                                            IconButton(onClick = { isPinVisible = !isPinVisible }, modifier = Modifier.size(28.dp)) {
                                                Icon(if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(onClick = { viewModel.copyToClipboard(entry.cardPin, "PIN", true) }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                EntryType.IDENTITY -> {
                    if (entry.identityName.isNotBlank()) {
                        DetailItemCard(label = "Full Name", value = entry.identityName, onCopy = { viewModel.copyToClipboard(entry.identityName, "Name", false) })
                    }
                    if (entry.identityIdNumber.isNotBlank()) {
                        DetailItemCard(label = "ID / Passport / SSN Number", value = entry.identityIdNumber, onCopy = { viewModel.copyToClipboard(entry.identityIdNumber, "ID", true) })
                    }
                    if (entry.identityAddress.isNotBlank()) {
                        DetailItemCard(label = "Address", value = entry.identityAddress, onCopy = { viewModel.copyToClipboard(entry.identityAddress, "Address", false) })
                    }
                    if (entry.identityPhone.isNotBlank()) {
                        DetailItemCard(label = "Phone", value = entry.identityPhone, onCopy = { viewModel.copyToClipboard(entry.identityPhone, "Phone", false) })
                    }
                    if (entry.identityEmail.isNotBlank()) {
                        DetailItemCard(label = "Email", value = entry.identityEmail, onCopy = { viewModel.copyToClipboard(entry.identityEmail, "Email", false) })
                    }
                }

                EntryType.SECURE_NOTE -> {
                    // Large note display
                }
            }

            // Custom Fields Display
            if (entry.customFields.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Custom Fields", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        entry.customFields.forEachIndexed { idx, cf ->
                            val isSecret = cf.fieldType == CustomFieldType.SECRET
                            val isRevealed = revealedCustomFields.contains(idx)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cf.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = if (isSecret && !isRevealed) "••••••••" else cf.value,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Row {
                                    if (isSecret) {
                                        IconButton(onClick = {
                                            revealedCustomFields = if (isRevealed) revealedCustomFields - idx else revealedCustomFields + idx
                                        }) {
                                            Icon(if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                                        }
                                    }
                                    IconButton(onClick = { viewModel.copyToClipboard(cf.value, cf.label, isSecret) }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Notes / Encrypted Note Body
            if (entry.notes.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (entry.entryType == EntryType.SECURE_NOTE) "Encrypted Note Body" else "Notes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = { viewModel.copyToClipboard(entry.notes, "Notes", false) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Notes",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = entry.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Encrypted Attachments
            if (entry.attachments.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Encrypted Attachments (${entry.attachments.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        entry.attachments.forEach { att ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(att.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("${att.fileSize / 1024} KB • AES-256 encrypted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    Toast.makeText(context, "Attachment '${att.fileName}' is secure in vault container", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.Download, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // Password History Section
            if (entry.passwordHistory.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Password History (${entry.passwordHistory.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { showPasswordHistory = !showPasswordHistory }) {
                                Text(if (showPasswordHistory) "Hide" else "View")
                            }
                        }

                        if (showPasswordHistory) {
                            val histFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                            entry.passwordHistory.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(histFormat.format(Date(item.changedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(item.password, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                                    }
                                    IconButton(onClick = { viewModel.copyToClipboard(item.password, "Old Password", true) }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            TextButton(
                                onClick = {
                                    val updated = entry.copy(passwordHistory = emptyList())
                                    viewModel.saveEntry(updated)
                                    Toast.makeText(context, "Password history cleared", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Clear History", color = SecurityRed)
                            }
                        }
                    }
                }
            }

            // Timestamps
            val dateFormat = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Created: ${dateFormat.format(Date(entry.createdAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Last Updated: ${dateFormat.format(Date(entry.updatedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DetailItemCard(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
