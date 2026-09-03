package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SecurityAuditReport
import com.example.data.SecurityIssueType
import com.example.data.VaultEntry
import com.example.ui.MainViewModel
import com.example.ui.theme.SecurityEmerald
import com.example.ui.theme.SecurityRed
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityCenterScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToEntry: (String) -> Unit,
    onVerifyVaultRequested: () -> Unit
) {
    val auditReport by viewModel.securityAuditReport.collectAsState()
    val activeFilter by viewModel.selectedIssueFilter.collectAsState()
    val onDemandQuery by viewModel.onDemandCheckQuery.collectAsState()
    val onDemandState by viewModel.onDemandCheckState.collectAsState()

    var showThresholdDialog by remember { mutableStateOf(false) }

    if (showThresholdDialog) {
        OldPasswordThresholdDialog(
            currentMonths = viewModel.preferences.oldPasswordThresholdMonths,
            onDismiss = { showThresholdDialog = false },
            onSelect = { months ->
                viewModel.setOldPasswordThresholdMonths(months)
                showThresholdDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Security Center",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (activeFilter != null) {
                                viewModel.setSelectedIssueFilter(null)
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("security_center_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.triggerSecurityScan() },
                        modifier = Modifier.testTag("rescan_security_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan Now",
                            tint = MaterialTheme.colorScheme.primary
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
            // Header: Summary Banner & Health Score
            item {
                SecurityHealthSummaryCard(
                    report = auditReport,
                    onVerifyVaultClick = onVerifyVaultRequested
                )
            }

            // Offline Breach Shield Banner
            item {
                if (auditReport.compromisedPasswords.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setSelectedIssueFilter(SecurityIssueType.COMPROMISED_PASSWORD) }
                            .testTag("breach_shield_alert_banner"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Breached Alert",
                                    tint = MaterialTheme.colorScheme.onError
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "⚠️ ${auditReport.compromisedPasswords.size} Leaked Passwords Detected!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Found in offline rockyou.txt or breach databases. Attackers test these first. Tap to review.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("breach_shield_normal_banner"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2E7D32)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "All Normal Yay",
                                    tint = Color.White
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Offline Breach Shield: Everything's Normal Yay!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B5E20)
                                )
                                Text(
                                    text = "0 of your ${auditReport.totalEntries} accounts match any leaked passwords across our 709,839 offline breach records (rockyou.txt, NCSC, Xato, DarkWeb).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Category Filter Pills or Active Filter Indicator
            if (activeFilter != null) {
                item {
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
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Filtered: ${activeFilter?.title}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        TextButton(
                            onClick = { viewModel.setSelectedIssueFilter(null) },
                            modifier = Modifier.testTag("clear_filter_button")
                        ) {
                            Text("Show All Audits", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Show entries for the selected issue
                val filteredList: List<VaultEntry> = when (activeFilter) {
                    SecurityIssueType.COMPROMISED_PASSWORD -> auditReport.compromisedPasswords
                    SecurityIssueType.WEAK_PASSWORD -> auditReport.weakPasswords
                    SecurityIssueType.REUSED_PASSWORD -> auditReport.reusedEntries
                    SecurityIssueType.DUPLICATE_ENTRY -> auditReport.duplicateEntries
                    SecurityIssueType.OLD_PASSWORD -> auditReport.oldPasswords
                    SecurityIssueType.MISSING_USERNAME -> auditReport.missingUsernames
                    null -> emptyList()
                }

                if (filteredList.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No issues found in this category. Looking great!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SecurityEmerald
                                )
                            }
                        }
                    }
                } else {
                    items(filteredList, key = { it.id }) { entry ->
                        SecurityIssueEntryCard(
                            entry = entry,
                            issueType = activeFilter!!,
                            reusedCount = if (activeFilter == SecurityIssueType.REUSED_PASSWORD) {
                                auditReport.reusedPasswordGroups[entry.password]?.size ?: 1
                            } else 0,
                            breachMatch = auditReport.breachMatchMap[entry.password],
                            onReviewClick = { onNavigateToEntry(entry.id) }
                        )
                    }
                }
            } else {
                // Section: 5 Core Local Audit Categories
                item {
                    Text(
                        text = "AUDIT CATEGORIES",
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
                        // 0. Breached Passwords
                        SecurityAuditCategoryCard(
                            title = "Breached Passwords",
                            description = "Exposed in rockyou.txt, UK NCSC, Xato 10M, or DarkWeb leak lists (709,839 offline records)",
                            count = auditReport.compromisedPasswords.size,
                            icon = Icons.Default.Security,
                            isPassed = auditReport.compromisedPasswords.isEmpty(),
                            onClick = { viewModel.setSelectedIssueFilter(SecurityIssueType.COMPROMISED_PASSWORD) },
                            testTag = "audit_compromised_passwords_card"
                        )

                        // 1. Weak Passwords
                        SecurityAuditCategoryCard(
                            title = "Weak Passwords",
                            description = "Low entropy or simple patterns prone to dictionary guessing",
                            count = auditReport.weakPasswords.size,
                            icon = Icons.Default.Warning,
                            isPassed = auditReport.weakPasswords.isEmpty(),
                            onClick = { viewModel.setSelectedIssueFilter(SecurityIssueType.WEAK_PASSWORD) },
                            testTag = "audit_weak_passwords_card"
                        )

                        // 2. Reused Passwords
                        SecurityAuditCategoryCard(
                            title = "Reused Passwords",
                            description = "Identical passwords shared across multiple accounts",
                            count = auditReport.reusedEntries.size,
                            icon = Icons.Default.ContentCopy,
                            isPassed = auditReport.reusedEntries.isEmpty(),
                            onClick = { viewModel.setSelectedIssueFilter(SecurityIssueType.REUSED_PASSWORD) },
                            testTag = "audit_reused_passwords_card"
                        )

                        // 3. Duplicate Accounts
                        SecurityAuditCategoryCard(
                            title = "Duplicate Accounts",
                            description = "Multiple saved credentials for the same domain & username",
                            count = auditReport.duplicateEntries.size,
                            icon = Icons.Default.Layers,
                            isPassed = auditReport.duplicateEntries.isEmpty(),
                            onClick = { viewModel.setSelectedIssueFilter(SecurityIssueType.DUPLICATE_ENTRY) },
                            testTag = "audit_duplicate_accounts_card"
                        )

                        // 4. Old Passwords
                        SecurityAuditCategoryCard(
                            title = "Old Passwords (>${viewModel.preferences.oldPasswordThresholdMonths} Mo)",
                            description = "Credentials not modified in over ${viewModel.preferences.oldPasswordThresholdMonths} months",
                            count = auditReport.oldPasswords.size,
                            icon = Icons.Default.Timer,
                            isPassed = auditReport.oldPasswords.isEmpty(),
                            onClick = { viewModel.setSelectedIssueFilter(SecurityIssueType.OLD_PASSWORD) },
                            onSettingsClick = { showThresholdDialog = true },
                            testTag = "audit_old_passwords_card"
                        )

                        // 5. Missing Usernames
                        SecurityAuditCategoryCard(
                            title = "Missing Usernames",
                            description = "Accounts saved without a username or email identifier",
                            count = auditReport.missingUsernames.size,
                            icon = Icons.Default.Person,
                            isPassed = auditReport.missingUsernames.isEmpty(),
                            onClick = { viewModel.setSelectedIssueFilter(SecurityIssueType.MISSING_USERNAME) },
                            testTag = "audit_missing_usernames_card"
                        )
                    }
                }

                // Section: Test Any Password Offline
                item {
                    Text(
                        text = "TEST ANY PASSWORD OFFLINE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.6.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp)
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("on_demand_breach_test_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Instant Offline Leak Verifier",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Check if any password has ever been leaked in rockyou.txt or brute-force dictionaries without connecting to the internet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            var testPasswordInput by remember { mutableStateOf("") }

                            OutlinedTextField(
                                value = testPasswordInput,
                                onValueChange = {
                                    testPasswordInput = it
                                    viewModel.runOnDemandBreachCheck(it)
                                },
                                label = { Text("Enter password to test") },
                                placeholder = { Text("e.g. password123, 123456, or custom") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("test_password_input"),
                                shape = RoundedCornerShape(14.dp),
                                trailingIcon = {
                                    if (testPasswordInput.isNotEmpty()) {
                                        IconButton(onClick = {
                                            testPasswordInput = ""
                                            viewModel.runOnDemandBreachCheck("")
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                                        }
                                    }
                                }
                            )

                            // Result feedback
                            when (val state = onDemandState) {
                                is com.example.ui.BreachCheckState.Idle -> {
                                    // Empty state
                                }
                                is com.example.ui.BreachCheckState.Checking -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text(
                                            text = "Scanning 709,839 offline passwords...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                is com.example.ui.BreachCheckState.Breached -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("on_demand_breached_result")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Breached",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = "⚠️ LEAKED IN ${state.match.databaseDisplayName.uppercase()}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                                Text(
                                                    text = "Rank #${state.match.rank} in public brute-force wordlist. Attackers test this immediately.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                }
                                is com.example.ui.BreachCheckState.Safe -> {
                                    Surface(
                                        color = Color(0xFF1B5E20).copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("on_demand_safe_result")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Safe",
                                                tint = Color(0xFF2E7D32),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = "✓ Everything's Normal Yay!",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1B5E20)
                                                )
                                                Text(
                                                    text = "Not found in rockyou.txt or any breach database (709,839 real records checked).",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF2E7D32)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section: Packaged Offline Breach Databases
                item {
                    Text(
                        text = "PACKAGED OFFLINE BREACH DATABASES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.6.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp)
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("breach_databases_inventory_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Offline Records",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "709,839 REAL PASSWORDS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Real brute-force wordlists bundled directly inside the app assets. No internet queries or telemetry.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            val databases = listOf(
                                Triple("rockyou.txt", "500,000 real passwords", "4.1 MB • Top leaks world-wide"),
                                Triple("100k-most-used-passwords-NCSC.txt", "99,840 passwords", "816 KB • UK Gov Cyber Security Centre"),
                                Triple("xato-net-10-million-passwords-100000.txt", "100,000 passwords", "764 KB • Top 100k from 10M collection"),
                                Triple("darkweb2017-top10000.txt", "9,999 passwords", "81 KB • Top darkweb dump list")
                            )

                            databases.forEach { (fileName, count, desc) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = fileName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = count,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Section: Local Privacy Assurance Banner
                item {
                    Spacer(modifier = Modifier.height(6.dp))
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockClock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "100% Local In-Memory Audit",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Zero network queries or live API lookups. Scan results are computed directly inside decrypted volatile memory and never persisted unencrypted.",
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
    }
}

@Composable
private fun SecurityHealthSummaryCard(
    report: SecurityAuditReport,
    onVerifyVaultClick: () -> Unit
) {
    val dateStr = remember(report.scanTimestamp) {
        val sdf = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault())
        sdf.format(Date(report.scanTimestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("security_health_summary_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Vault Security Health",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Last scan: $dateStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Health Score Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (report.healthScorePercentage >= 85) SecurityEmerald.copy(alpha = 0.15f)
                            else if (report.healthScorePercentage >= 60) Color(0xFFFFB74D).copy(alpha = 0.15f)
                            else SecurityRed.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${report.healthScorePercentage}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (report.healthScorePercentage >= 85) SecurityEmerald
                        else if (report.healthScorePercentage >= 60) Color(0xFFFFB74D)
                        else SecurityRed
                    )
                }
            }

            // Summary counts line: "42 strong · 3 reused · 2 weak · 1 old"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${report.strongPasswords.size} strong",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SecurityEmerald
                )
                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${report.reusedEntries.size} reused",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (report.reusedEntries.isNotEmpty()) Color(0xFFFFB74D) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${report.weakPasswords.size} weak",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (report.weakPasswords.isNotEmpty()) SecurityRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${report.oldPasswords.size} old",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

            // 1-Tap Verify My Vault action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onVerifyVaultClick)
                    .padding(vertical = 4.dp)
                    .testTag("verify_my_vault_action_row"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Verify My Vault",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Run container & AEAD integrity diagnostics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SecurityAuditCategoryCard(
    title: String,
    description: String,
    count: Int,
    icon: ImageVector,
    isPassed: Boolean,
    onClick: () -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPassed) SecurityEmerald.copy(alpha = 0.12f)
                            else SecurityRed.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPassed) Icons.Default.Check else icon,
                        contentDescription = null,
                        tint = if (isPassed) SecurityEmerald else SecurityRed,
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
                        lineHeight = 16.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onSettingsClick != null) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Configure Threshold",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isPassed) SecurityEmerald.copy(alpha = 0.12f)
                            else SecurityRed.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPassed) "Passed" else "$count flagged",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isPassed) SecurityEmerald else SecurityRed
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityIssueEntryCard(
    entry: VaultEntry,
    issueType: SecurityIssueType,
    reusedCount: Int,
    breachMatch: com.example.data.BreachMatch? = null,
    onReviewClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onReviewClick)
            .testTag("security_issue_entry_${entry.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.title.ifBlank { "Untitled Account" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = entry.username.ifBlank { "(No username saved)" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.username.isBlank()) SecurityRed else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Issue specific explanation badge
                val reasonText = when (issueType) {
                    SecurityIssueType.WEAK_PASSWORD -> "Password strength: Low entropy"
                    SecurityIssueType.REUSED_PASSWORD -> "Reused across $reusedCount accounts"
                    SecurityIssueType.DUPLICATE_ENTRY -> "Duplicate credentials for this domain"
                    SecurityIssueType.OLD_PASSWORD -> "Password unchanged in > 12 months"
                    SecurityIssueType.MISSING_USERNAME -> "Login username / email missing"
                    SecurityIssueType.COMPROMISED_PASSWORD -> breachMatch?.let {
                        "⚠️ Leaked in ${it.databaseDisplayName} (Rank #${it.rank})"
                    } ?: "⚠️ Found in offline leak database"
                }

                Text(
                    text = reasonText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (issueType == SecurityIssueType.COMPROMISED_PASSWORD) MaterialTheme.colorScheme.error else Color(0xFFFFB74D),
                    fontWeight = FontWeight.Medium
                )
            }

            Button(
                onClick = onReviewClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Fix", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun OldPasswordThresholdDialog(
    currentMonths: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val options = listOf(
        3 to "3 Months",
        6 to "6 Months",
        12 to "12 Months (1 Year - Default)",
        24 to "24 Months (2 Years)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Old Password Threshold", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column {
                Text(
                    text = "Flag credentials that have not been modified within this time period:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                options.forEach { (months, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(months) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMonths == months,
                            onClick = { onSelect(months) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
