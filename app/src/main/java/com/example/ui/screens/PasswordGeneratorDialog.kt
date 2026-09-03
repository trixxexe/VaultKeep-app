package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.crypto.CapitalizationMode
import com.example.crypto.GeneratorMode
import com.example.crypto.PassphraseConfig
import com.example.crypto.PassphraseSeparator
import com.example.crypto.PasswordConfig
import com.example.crypto.PasswordGenerator
import com.example.crypto.PasswordStrength
import com.example.ui.components.StrengthMeter
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PasswordGeneratorDialog(
    onDismissRequest: () -> Unit,
    onUsePassword: ((String) -> Unit)? = null,
    onCopyPassword: (String) -> Unit
) {
    var mode by remember { mutableStateOf(GeneratorMode.RANDOM) }

    // Random Config
    var length by remember { mutableFloatStateOf(16f) }
    var uppercase by remember { mutableStateOf(true) }
    var lowercase by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var excludeAmbiguous by remember { mutableStateOf(true) }

    // Passphrase Config
    var wordCount by remember { mutableFloatStateOf(4f) }
    var separator by remember { mutableStateOf(PassphraseSeparator.DASH) }
    var capitalization by remember { mutableStateOf(CapitalizationMode.TITLE_CASE) }
    var appendNumber by remember { mutableStateOf(true) }

    var generatedPassword by remember { mutableStateOf("") }

    fun refreshPassword() {
        generatedPassword = when (mode) {
            GeneratorMode.RANDOM -> {
                val config = PasswordConfig(
                    length = length.toInt(),
                    includeUppercase = uppercase,
                    includeLowercase = lowercase,
                    includeDigits = digits,
                    includeSymbols = symbols,
                    excludeAmbiguous = excludeAmbiguous
                )
                PasswordGenerator.generateRandom(config)
            }
            GeneratorMode.PASSPHRASE -> {
                val config = PassphraseConfig(
                    wordCount = wordCount.toInt(),
                    separator = separator,
                    capitalization = capitalization,
                    appendNumber = appendNumber
                )
                PasswordGenerator.generatePassphrase(config)
            }
        }
    }

    LaunchedEffect(
        mode,
        length, uppercase, lowercase, digits, symbols, excludeAmbiguous,
        wordCount, separator, capitalization, appendNumber
    ) {
        refreshPassword()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val strengthEvaluation = remember(generatedPassword) {
        PasswordGenerator.evaluateStrengthDetailed(generatedPassword)
    }
    val breachMatch = remember(generatedPassword) {
        com.example.data.OfflineBreachDatabase.checkFastSync(context, generatedPassword)
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("password_generator_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Generator",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { refreshPassword() },
                        modifier = Modifier.testTag("regenerate_password_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate Password",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Mode Tabs
                TabRow(
                    selectedTabIndex = mode.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = mode == GeneratorMode.RANDOM,
                        onClick = { mode = GeneratorMode.RANDOM },
                        text = { Text("Random", fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Password, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = mode == GeneratorMode.PASSPHRASE,
                        onClick = { mode = GeneratorMode.PASSPHRASE },
                        text = { Text("Passphrase", fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                // Password Display Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = generatedPassword,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generated_password_text")
                    )
                }

                // Strength & Entropy Meter
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StrengthMeter(strength = strengthEvaluation.strength)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${strengthEvaluation.entropyBits.roundToInt()} bits entropy",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strengthEvaluation.feedback,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        color = if (breachMatch != null) MaterialTheme.colorScheme.errorContainer else Color(0xFF1B5E20).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (breachMatch != null) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (breachMatch != null) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (breachMatch != null) "⚠️ Leaked in ${breachMatch.databaseDisplayName} (Rank #${breachMatch.rank})" else "✓ Offline Check: Normal Yay! (Not in rockyou.txt)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (breachMatch != null) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF2E7D32)
                            )
                        }
                    }
                }

                if (mode == GeneratorMode.RANDOM) {
                    // Length Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Length",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${length.toInt()} chars",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = length,
                            onValueChange = { length = it },
                            valueRange = 8f..48f,
                            steps = 39,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.testTag("password_length_slider")
                        )
                    }

                    // Character Sets
                    Text(
                        text = "Character Sets",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = uppercase,
                            onClick = {
                                if (!uppercase || lowercase || digits || symbols) uppercase = !uppercase
                            },
                            label = { Text("A-Z") },
                            leadingIcon = if (uppercase) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )

                        FilterChip(
                            selected = lowercase,
                            onClick = {
                                if (!lowercase || uppercase || digits || symbols) lowercase = !lowercase
                            },
                            label = { Text("a-z") },
                            leadingIcon = if (lowercase) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )

                        FilterChip(
                            selected = digits,
                            onClick = {
                                if (!digits || uppercase || lowercase || symbols) digits = !digits
                            },
                            label = { Text("0-9") },
                            leadingIcon = if (digits) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )

                        FilterChip(
                            selected = symbols,
                            onClick = {
                                if (!symbols || uppercase || lowercase || digits) symbols = !symbols
                            },
                            label = { Text("!@#") },
                            leadingIcon = if (symbols) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )

                        FilterChip(
                            selected = excludeAmbiguous,
                            onClick = { excludeAmbiguous = !excludeAmbiguous },
                            label = { Text("Exclude Ambiguous (1, l, 0, O)") },
                            leadingIcon = if (excludeAmbiguous) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                } else {
                    // Passphrase Word Count
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Word Count",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${wordCount.toInt()} words",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = wordCount,
                            onValueChange = { wordCount = it },
                            valueRange = 3f..8f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }

                    // Separator
                    Text(
                        text = "Word Separator",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PassphraseSeparator.values().forEach { sep ->
                            FilterChip(
                                selected = separator == sep,
                                onClick = { separator = sep },
                                label = { Text(sep.label) },
                                leadingIcon = if (separator == sep) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }

                    // Capitalization & Number
                    Text(
                        text = "Options",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CapitalizationMode.values().forEach { cap ->
                            FilterChip(
                                selected = capitalization == cap,
                                onClick = { capitalization = cap },
                                label = { Text(cap.label) },
                                leadingIcon = if (capitalization == cap) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }

                        FilterChip(
                            selected = appendNumber,
                            onClick = { appendNumber = !appendNumber },
                            label = { Text("Append Number (e.g. 42)") },
                            leadingIcon = if (appendNumber) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onCopyPassword(generatedPassword)
                            onDismissRequest()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_generated_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }

                    if (onUsePassword != null) {
                        Button(
                            onClick = {
                                onUsePassword(generatedPassword)
                                onDismissRequest()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("use_generated_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Text("Use Password")
                        }
                    } else {
                        Button(
                            onClick = onDismissRequest,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("close_generator_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

