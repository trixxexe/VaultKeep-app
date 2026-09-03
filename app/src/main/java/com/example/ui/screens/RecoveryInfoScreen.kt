package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.EmergencyRecoveryKitDialog
import com.example.ui.theme.SecurityEmerald
import com.example.ui.theme.SecurityRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryInfoScreen(
    onBack: () -> Unit,
    onExportRequested: () -> Unit,
    viewModel: com.example.ui.MainViewModel? = null
) {
    var showRecoveryKitDialog by remember { mutableStateOf(false) }

    val saltHex = remember(viewModel) {
        viewModel?.repositoryRef?.getVaultSalt()?.joinToString("") { "%02x".format(it) } ?: ""
    }
    val passwordHint = viewModel?.preferences?.passwordHint ?: ""

    if (showRecoveryKitDialog) {
        EmergencyRecoveryKitDialog(
            saltHex = saltHex,
            passwordHint = passwordHint,
            onDismissRequest = { showRecoveryKitDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "About Vault Recovery",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("recovery_info_back_button")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {
            // Main Banner: Zero Backdoors
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Text(
                            text = "No Backdoor. No Cloud Reset.",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "If you forget your master password, VaultKeep cannot recover your vault. There is no backdoor, no developer master key, and no cloud-based password reset mechanism.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Section: Principles
            item {
                Text(
                    text = "RECOVERY PRINCIPLES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.6.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PhilosophyCard(
                        icon = Icons.Default.Key,
                        title = "Sole Custody of Keys",
                        description = "Your master password is never stored on disk. It is fed into PBKDF2 with 310,000 iterations to derive volatile memory keys that vanish when the app locks."
                    )

                    PhilosophyCard(
                        icon = Icons.Default.Folder,
                        title = "The Only Recovery Path",
                        description = "The single way to restore lost vault data is a previously exported encrypted backup file (.vkeep) unlocked with the password you assigned when exporting."
                    )

                    PhilosophyCard(
                        icon = Icons.Default.Shield,
                        title = "Why This Protects You",
                        description = "Any system with an 'admin reset' or 'recovery key' is inherently vulnerable to unauthorized access. By eliminating backdoors, VaultKeep ensures only you can access your vault."
                    )
                }
            }

            // Section: Recommended Actions
            item {
                Text(
                    text = "RECOMMENDED BEST PRACTICES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.6.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BestPracticeItem(
                            step = "1",
                            text = "Write your master password on paper and store it in a secure, fireproof physical location."
                        )
                        BestPracticeItem(
                            step = "2",
                            text = "Export an encrypted backup (.vkeep) regularly, especially after adding critical accounts."
                        )
                        BestPracticeItem(
                            step = "3",
                            text = "Store backup files in trusted offline media (such as a private USB drive) or personal encrypted archives."
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedButton(
                            onClick = { showRecoveryKitDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recovery_screen_kit_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Emergency Recovery Kit")
                        }

                        Button(
                            onClick = onExportRequested,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recovery_screen_export_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export Encrypted Backup Now")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhilosophyCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun BestPracticeItem(
    step: String,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 18.sp
        )
    }
}
