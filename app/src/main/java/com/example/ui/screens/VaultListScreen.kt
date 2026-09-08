package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.TotpGenerator
import com.example.data.EntryType
import com.example.data.VaultEntry
import com.example.data.VaultSortMode
import com.example.ui.MainViewModel
import com.example.ui.theme.BadgeBgBank
import com.example.ui.theme.BadgeBgGitHub
import com.example.ui.theme.BadgeBgPersonal
import com.example.ui.theme.BadgeBgProton
import com.example.ui.theme.BadgeBgSlack
import com.example.ui.theme.BadgeFgBank
import com.example.ui.theme.BadgeFgGitHub
import com.example.ui.theme.BadgeFgPersonal
import com.example.ui.theme.BadgeFgProton
import com.example.ui.theme.BadgeFgSlack
import com.example.ui.util.InputSanitizer
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    viewModel: MainViewModel,
    onAddNewEntry: () -> Unit,
    onEditEntry: (VaultEntry) -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSecurityCenter: () -> Unit = {}
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val entries by viewModel.filteredEntries.collectAsState()
    val revealedPasswordIds by viewModel.revealedPasswordIds.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val selectedTypeFilter by viewModel.selectedTypeFilter.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val totpTick by viewModel.totpTick.collectAsState()
    val selectedEntryIds by viewModel.selectedEntryIds.collectAsState()
    val trashEntries by viewModel.trashEntries.collectAsState()
    val breachMatches by viewModel.offlineBreachMatches.collectAsState()

    val isSelectionMode = selectedEntryIds.isNotEmpty()
    val isTrashView = selectedFolder == "__trash__" || selectedFolder.equals("Trash", ignoreCase = true)

    var isSearchExpanded by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }
    var showBatchFolderDialog by remember { mutableStateOf(false) }
    var showEmptyTrashConfirmDialog by remember { mutableStateOf(false) }

    if (showAddFolderDialog) {
        AlertDialog(
            onDismissRequest = { showAddFolderDialog = false },
            title = { Text("New Folder", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderNameInput,
                    onValueChange = { newFolderNameInput = InputSanitizer.sanitizeSingleLine(it, InputSanitizer.MAX_FOLDER_LENGTH) },
                    placeholder = { Text("e.g. Work, Crypto, Social") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanFolder = InputSanitizer.sanitizeSingleLine(newFolderNameInput, InputSanitizer.MAX_FOLDER_LENGTH)
                        if (cleanFolder.isNotBlank()) {
                            viewModel.addFolder(cleanFolder)
                            viewModel.setSelectedFolder(cleanFolder)
                            newFolderNameInput = ""
                        }
                        showAddFolderDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Add Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBatchFolderDialog) {
        val nonTrashFolders = folders.filter { it.isNotBlank() && it != "All" && it != "Favorites" && it != "Trash" }
        var targetFolder by remember { mutableStateOf(nonTrashFolders.firstOrNull() ?: "General") }
        var isNewFolder by remember { mutableStateOf(nonTrashFolders.isEmpty()) }
        var customFolderInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showBatchFolderDialog = false },
            title = { Text("Move ${selectedEntryIds.size} Items", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose destination folder:", style = MaterialTheme.typography.bodyMedium)
                    nonTrashFolders.forEach { f ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    targetFolder = f
                                    isNewFolder = false
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (!isNewFolder && targetFolder == f),
                                onClick = { targetFolder = f; isNewFolder = false }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(f)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isNewFolder = true }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = isNewFolder,
                            onClick = { isNewFolder = true }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Create new folder...")
                    }
                    if (isNewFolder) {
                        OutlinedTextField(
                            value = customFolderInput,
                            onValueChange = { customFolderInput = InputSanitizer.sanitizeSingleLine(it, InputSanitizer.MAX_FOLDER_LENGTH) },
                            placeholder = { Text("New folder name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalFolder = if (isNewFolder) InputSanitizer.sanitizeSingleLine(customFolderInput, InputSanitizer.MAX_FOLDER_LENGTH) else targetFolder
                        if (finalFolder.isNotBlank()) {
                            viewModel.bulkMoveToFolder(selectedEntryIds, finalFolder)
                            viewModel.clearSelection()
                        }
                        showBatchFolderDialog = false
                    }
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEmptyTrashConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirmDialog = false },
            title = { Text("Empty Trash?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Permanently erase all items in Trash? This action cannot be reversed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.emptyTrash()
                        showEmptyTrashConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Empty Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.testTag("clear_selection_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Selection"
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "${selectedEntryIds.size} Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.selectAll(entries.map { it.id }) },
                            modifier = Modifier.testTag("select_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Select All"
                            )
                        }

                        if (!isTrashView) {
                            IconButton(
                                onClick = { showBatchFolderDialog = true },
                                modifier = Modifier.testTag("batch_move_folder_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Move to Folder"
                                )
                            }
                            IconButton(
                                onClick = { viewModel.bulkMoveToTrash(selectedEntryIds) },
                                modifier = Modifier.testTag("batch_move_trash_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Move to Trash"
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { viewModel.bulkRestoreFromTrash(selectedEntryIds) },
                                modifier = Modifier.testTag("batch_restore_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restore,
                                    contentDescription = "Restore Selected"
                                )
                            }
                            IconButton(
                                onClick = { viewModel.bulkDelete(selectedEntryIds) },
                                modifier = Modifier.testTag("batch_delete_forever_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = "Delete Forever",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "VaultKeep",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 22.sp,
                                    letterSpacing = (-0.5).sp
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "OFFLINE VAULT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 1.6.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    actions = {
                        // Search toggle
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { isSearchExpanded = !isSearchExpanded },
                                modifier = Modifier.size(38.dp).testTag("toggle_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = if (isSearchExpanded || searchQuery.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Sort menu button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.size(38.dp).testTag("sort_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sort Vault",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                VaultSortMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (sortMode == mode) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                } else {
                                                    Spacer(Modifier.size(16.dp))
                                                }
                                                Text(mode.label)
                                            }
                                        },
                                        onClick = {
                                            viewModel.setSortMode(mode)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Lock button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { viewModel.lockVault() },
                                modifier = Modifier.size(38.dp).testTag("top_bar_lock_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock Vault",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode && !isTrashView) {
                FloatingActionButton(
                    onClick = onAddNewEntry,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("add_entry_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add New Login",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Vault Tab (Active)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { }
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Vault",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Vault",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // Security Center Tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenSecurityCenter() }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .testTag("bottom_nav_security")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (breachMatches.isNotEmpty()) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ) {
                                            Text("${breachMatches.size}")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Security Center",
                                    tint = if (breachMatches.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Text(
                                text = "Security",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Generate Tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenGenerator() }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .testTag("bottom_nav_generator")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = "Generate",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Generate",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Settings Tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenSettings() }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .testTag("bottom_nav_settings")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Box (Collapsible or persistent if query non-empty)
            AnimatedVisibility(visible = isSearchExpanded || searchQuery.isNotEmpty()) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            val cleanQuery = InputSanitizer.sanitizeSearchQuery(it)
                            viewModel.onSearchQueryChange(cleanQuery)
                        },
                        placeholder = {
                            Text(
                                text = "Search logins, sites, usernames...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.onSearchQueryChange("") },
                                    modifier = Modifier.testTag("clear_search_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear Search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_search_input")
                    )
                }
            }

            // Folder Filter Chips Carousel
            val allFolderChips = remember(folders, trashEntries.size, isTrashView) {
                val list = mutableListOf("All", "Favorites")
                list.addAll(folders.filter { it.isNotBlank() && it != "All" && it != "Favorites" && it != "Trash" && it != "__trash__" })
                if (trashEntries.isNotEmpty() || isTrashView) {
                    list.add("Trash")
                }
                list
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allFolderChips) { folderName ->
                    val isSelected = when (folderName) {
                        "All" -> selectedFolder.isBlank()
                        "Trash" -> isTrashView
                        else -> selectedFolder == folderName
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            when (folderName) {
                                "All" -> viewModel.setSelectedFolder("")
                                "Trash" -> viewModel.setSelectedFolder("__trash__")
                                else -> viewModel.setSelectedFolder(folderName)
                            }
                        },
                        label = {
                            if (folderName == "Trash") {
                                Text(
                                    text = "Trash (${trashEntries.size})",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Text(
                                    text = folderName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        leadingIcon = when (folderName) {
                            "Favorites" -> {
                                { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
                            }
                            "Trash" -> {
                                { Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            else -> null
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                item {
                    AssistChip(
                        onClick = { showAddFolderDialog = true },
                        label = { Text("+ Folder") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Trash Banner (if viewing Trash)
            if (isTrashView && entries.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Deleted items are stored here locally.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(
                            onClick = { showEmptyTrashConfirmDialog = true },
                            modifier = Modifier.testTag("empty_trash_banner_button")
                        ) {
                            Text(
                                text = "Empty Trash",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // Type Filter Chips Row
            val entryTypes = listOf(
                null to "All Types",
                EntryType.PASSWORD to "Logins",
                EntryType.PASSKEY to "Passkeys",
                EntryType.CREDIT_CARD to "Cards",
                EntryType.SECURE_NOTE to "Notes",
                EntryType.IDENTITY to "Identities"
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entryTypes) { (type, label) ->
                    val isSelected = selectedTypeFilter == type
                    SuggestionChip(
                        onClick = { viewModel.selectTypeFilter(if (isSelected && type != null) null else type) },
                        label = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = if (isSelected) SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = MaterialTheme.colorScheme.primary) else null,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // Subheader Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${entries.size} ${if (entries.size == 1) "entry" else "entries"} • ${sortMode.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "v2.0 • AES-256-GCM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Entry List or Empty States (Adaptive Layout for Phones and Large/Foldable Screens)
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 680.dp

                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isTrashView) Icons.Default.DeleteOutline else if (searchQuery.isNotBlank()) Icons.Default.Search else Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Text(
                                text = if (isTrashView) "Trash is empty" else if (searchQuery.isNotBlank()) "No matching credentials" else "Your vault is empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = if (isTrashView)
                                    "Deleted credentials will appear here before permanent erasure."
                                else if (searchQuery.isNotBlank())
                                    "No entries match \"$searchQuery\". Try another query or select 'All' folders."
                                else
                                    "Tap the + button to securely store your first login.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("vault_entry_list"),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (breachMatches.isNotEmpty() && !isTrashView) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenSecurityCenter() }
                                        .testTag("vault_breach_warning_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Breached Passwords",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${breachMatches.size} accounts have passwords leaked in rockyou.txt!",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                text = "Tap to review in Security Center",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        items(entries, key = { it.id }) { entry ->
                            VaultEntryCard(
                                entry = entry,
                                searchQuery = searchQuery,
                                totpTick = totpTick,
                                isPasswordVisible = revealedPasswordIds.contains(entry.id),
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedEntryIds.contains(entry.id),
                                isTrashView = isTrashView,
                                breachMatch = breachMatches[entry.password],
                                onToggleSelect = { viewModel.toggleSelectEntry(entry.id) },
                                onLongClick = { viewModel.toggleSelectEntry(entry.id) },
                                onTogglePassword = { viewModel.togglePasswordVisibility(entry.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(entry.id) },
                                onCopyUsername = {
                                    viewModel.copyToClipboard(context, "Username", entry.username, false)
                                },
                                onCopyPassword = {
                                    viewModel.copyToClipboard(context, "Password", entry.password, true)
                                },
                                onCopyTotp = { code ->
                                    viewModel.copyToClipboard(context, "TOTP Code", code, true)
                                },
                                onDuplicateEntry = { viewModel.duplicateEntry(entry.id) },
                                onRestoreFromTrash = { viewModel.restoreFromTrash(entry.id) },
                                onDeletePermanently = { viewModel.deletePermanently(entry.id) },
                                onClick = {
                                    if (isTrashView) {
                                        viewModel.toggleSelectEntry(entry.id)
                                    } else {
                                        onEditEntry(entry)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultEntryCard(
    entry: VaultEntry,
    searchQuery: String,
    totpTick: Long,
    isPasswordVisible: Boolean,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isTrashView: Boolean = false,
    breachMatch: com.example.data.BreachMatch? = null,
    onToggleSelect: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onTogglePassword: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit,
    onCopyTotp: (String) -> Unit,
    onDuplicateEntry: () -> Unit = {},
    onRestoreFromTrash: () -> Unit = {},
    onDeletePermanently: () -> Unit = {},
    onClick: () -> Unit
) {
    val initial = if (entry.title.isNotBlank()) entry.title.first().uppercase() else "V"
    val (badgeBg, badgeFg) = getBadgeColors(entry.title)

    val highlightedTitle = remember(entry.title, searchQuery) {
        highlightSearchQuery(entry.title, searchQuery)
    }
    val highlightedUsername = remember(entry.username, searchQuery) {
        highlightSearchQuery(if (entry.username.isNotBlank()) entry.username else "No username", searchQuery)
    }

    // TOTP state
    val totpCode = remember(entry.totpSecret, totpTick) {
        if (entry.totpSecret.isNotBlank()) {
            TotpGenerator.generateTotp(
                secretBase32 = entry.totpSecret,
                timestampSeconds = totpTick / 1000L,
                periodSeconds = entry.totpPeriod,
                digits = entry.totpDigits,
                algorithm = entry.totpAlgorithm
            )
        } else null
    }

    val totpRemaining = remember(entry.totpPeriod, totpTick) {
        if (entry.totpSecret.isNotBlank()) {
            TotpGenerator.getRemainingSeconds(totpTick / 1000L, entry.totpPeriod)
        } else 0
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelect() else onClick()
                },
                onLongClick = {
                    onLongClick()
                }
            )
            .testTag("entry_card_${entry.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Main Row: Avatar + Title/Username + Action Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            modifier = Modifier.testTag("select_checkbox_${entry.id}")
                        )
                    }

                    // Minimalist Brand Avatar
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(badgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = highlightedTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entry.isPasskey) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text(
                                            text = "PASSKEY",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            if (breachMatch != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Breached",
                                            modifier = Modifier.size(10.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "LEAKED #${breachMatch.rank}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                            if (entry.notes.isNotBlank()) {
                                Icon(
                                    imageVector = Icons.Default.Notes,
                                    contentDescription = "Has Notes",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            if (entry.folder.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = entry.folder,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = highlightedUsername,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action Buttons: Favorite + Visibility + Copy OR Trash Actions
                if (isTrashView) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onRestoreFromTrash,
                            modifier = Modifier.size(34.dp).testTag("restore_button_${entry.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = "Restore ${entry.title}",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDeletePermanently,
                            modifier = Modifier.size(34.dp).testTag("delete_forever_button_${entry.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = "Delete permanently ${entry.title}",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(34.dp).testTag("favorite_button_${entry.id}")
                        ) {
                            Icon(
                                imageVector = if (entry.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (entry.isFavorite) "Remove ${entry.title} from favorites" else "Add ${entry.title} to favorites",
                                tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (!entry.isPasskey) {
                            IconButton(
                                onClick = onTogglePassword,
                                modifier = Modifier.size(34.dp).testTag("reveal_password_button")
                            ) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPasswordVisible) "Hide password for ${entry.title}" else "Reveal password for ${entry.title}",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = onCopyPassword,
                                modifier = Modifier.size(34.dp).testTag("copy_password_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy password for ${entry.title}",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = onCopyUsername,
                                modifier = Modifier.size(34.dp).testTag("copy_username_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Copy username for ${entry.title}",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        var showEntryMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showEntryMenu = true },
                                modifier = Modifier.size(30.dp).testTag("entry_menu_button_${entry.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showEntryMenu,
                                onDismissRequest = { showEntryMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Duplicate Entry") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        showEntryMenu = false
                                        onDuplicateEntry()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move to Trash") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showEntryMenu = false
                                        onDeletePermanently()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // TOTP Preview Pill
            if (totpCode != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "TOTP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = totpCode.chunked(3).joinToString(" "),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${totpRemaining}s",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (totpRemaining <= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { onCopyTotp(totpCode) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy TOTP",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Revealed Password Preview (Inline Minimalist Pill)
            if (isPasswordVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.password,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Highlights matching search query characters in bold in the text
 */
fun highlightSearchQuery(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val lowerText = text.lowercase(Locale.ROOT)
    val lowerQuery = query.trim().lowercase(Locale.ROOT)

    return buildAnnotatedString {
        var currentIndex = 0

        while (currentIndex < text.length) {
            val matchIndex = lowerText.indexOf(lowerQuery, currentIndex)
            if (matchIndex == -1) {
                append(text.substring(currentIndex))
                break
            }

            if (matchIndex > currentIndex) {
                append(text.substring(currentIndex, matchIndex))
            }

            val matchEnd = matchIndex + lowerQuery.length
            val matchedSub = text.substring(matchIndex, matchEnd)

            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFA8C7FA)
                )
            ) {
                append(matchedSub)
            }

            currentIndex = matchEnd
        }
    }
}

/**
 * Deterministic badge color generator adhering to the Clean Minimalism palette
 */
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
