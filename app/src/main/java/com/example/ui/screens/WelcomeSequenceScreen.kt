package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.util.AccessibilityUtils
import kotlinx.coroutines.delay

private const val AUTO_ADVANCE_DELAY_MS = 3200L
private const val TOTAL_STEPS = 5

@Composable
fun WelcomeSequenceScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val isReduceMotion = remember { AccessibilityUtils.isReduceMotionEnabled(context) }

    if (isReduceMotion) {
        StaticWelcomeFallback(onComplete = onComplete)
    } else {
        AnimatedWelcomeSequence(onComplete = onComplete)
    }
}

@Composable
private fun AnimatedWelcomeSequence(
    onComplete: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }

    // Natural reading beat timer: advances automatically unless on the final action screen or paused
    LaunchedEffect(currentStep, isPaused) {
        if (currentStep < TOTAL_STEPS - 1 && !isPaused) {
            delay(AUTO_ADVANCE_DELAY_MS)
            if (currentStep < TOTAL_STEPS - 1) {
                currentStep++
            }
        }
    }

    // Very subtle, slow ambient background gradient shift within the 3-4 color palette
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_background")
    val ambientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_offset"
    )

    val bgColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val subtleGradient = remember(bgColor, surfaceColor, ambientOffset) {
        Brush.radialGradient(
            colors = listOf(
                surfaceColor.copy(alpha = 0.4f),
                bgColor
            ),
            center = androidx.compose.ui.geometry.Offset(
                x = 400f * (0.8f + 0.4f * ambientOffset),
                y = 500f * (0.8f + 0.4f * (1f - ambientOffset))
            ),
            radius = 1200f
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .background(subtleGradient)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tap anywhere to advance to next reading beat
                if (currentStep < TOTAL_STEPS - 1) {
                    currentStep++
                }
            }
            .testTag("welcome_sequence_screen")
    ) {
        // Subtle, always-visible Skip action in top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Subtle branding mark
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "VAULTKEEP",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            TextButton(
                onClick = onComplete,
                modifier = Modifier.testTag("welcome_skip_button"),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        // Main Animated Content Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                     slideInHorizontally(
                         animationSpec = tween(300, easing = FastOutSlowInEasing),
                         initialOffsetX = { fullWidth -> (fullWidth * 0.12f).toInt() }
                     )).togetherWith(
                         fadeOut(animationSpec = tween(250, easing = FastOutSlowInEasing)) +
                         slideOutHorizontally(
                             animationSpec = tween(250, easing = FastOutSlowInEasing),
                             targetOffsetX = { fullWidth -> -(fullWidth * 0.12f).toInt() }
                         )
                     )
                },
                label = "welcome_step_transition"
            ) { step ->
                when (step) {
                    0 -> StepWelcome()
                    1 -> StepFullyLocal()
                    2 -> StepNoAccountsNoCloud()
                    3 -> StepEncryptionSecurity()
                    4 -> StepSetupVaultHandoff(onComplete = onComplete)
                }
            }
        }

        // Bottom Controls: Page Indicator and Tap/Next cue
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Page Indicator (Dots / Pills)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag("welcome_page_indicator")
            ) {
                repeat(TOTAL_STEPS) { index ->
                    val isSelected = index == currentStep
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "dot_width"
                    )
                    val color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    }

                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            if (currentStep < TOTAL_STEPS - 1) {
                Text(
                    text = "Tap anywhere to continue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// =============================================================================
// SCREEN 1: Welcome to VaultKeep
// =============================================================================
@Composable
private fun StepWelcome() {
    var iconVisible by remember { mutableStateOf(false) }
    var textVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        iconVisible = true
        delay(80)
        textVisible = true
    }

    val iconScale by animateFloatAsState(
        targetValue = if (iconVisible) 1f else 0.88f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "icon_scale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (iconVisible) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "icon_alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // App mark assembled / drawn scale + fade
        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(iconScale)
                .alpha(iconAlpha)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
        }

        AnimatedVisibility(
            visible = textVisible,
            enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { 18 }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Welcome to VaultKeep",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "An offline-first password vault designed for absolute privacy and sovereign security.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

// =============================================================================
// SCREEN 2: A fully local, private vault
// =============================================================================
@Composable
private fun StepFullyLocal() {
    var line1Visible by remember { mutableStateOf(false) }
    var line2Visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(60)
        line1Visible = true
        delay(80)
        line2Visible = true
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhonelinkLock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }

        AnimatedVisibility(
            visible = line1Visible,
            enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { 16 }
        ) {
            Text(
                text = "A fully local, private vault.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        AnimatedVisibility(
            visible = line2Visible,
            enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { 16 }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Your passwords never leave this device.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Encrypted exclusively in private application sandbox storage. No background network synchronization, no remote telemetry, no external servers.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

// =============================================================================
// SCREEN 3: No accounts. No cloud. No tracking.
// =============================================================================
@Composable
private fun StepNoAccountsNoCloud() {
    var line1Visible by remember { mutableStateOf(false) }
    var line2Visible by remember { mutableStateOf(false) }
    var line3Visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(60)
        line1Visible = true
        delay(70)
        line2Visible = true
        delay(70)
        line3Visible = true
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            )
        }

        Text(
            text = "No accounts. No cloud. No tracking.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Three short lines revealing in sequence (40-80ms stagger)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            StaggeredItem(
                visible = line1Visible,
                icon = Icons.Default.PersonOff,
                title = "No accounts required",
                subtitle = "Start using your vault instantly without giving up an email address or identity."
            )

            StaggeredItem(
                visible = line2Visible,
                icon = Icons.Default.CloudOff,
                title = "Zero cloud databases",
                subtitle = "Your keys and credentials are never transmitted or hosted on remote third-party servers."
            )

            StaggeredItem(
                visible = line3Visible,
                icon = Icons.Default.Security,
                title = "Zero tracking telemetry",
                subtitle = "No analytics packages, advertising IDs, or monitoring trackers are bundled in the app."
            )
        }
    }
}

@Composable
private fun StaggeredItem(
    visible: Boolean,
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(300, easing = FastOutSlowInEasing)) { 14 }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                .padding(12.dp),
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// =============================================================================
// SCREEN 4: Protected by AES-256 encryption and hardware
// =============================================================================
@Composable
private fun StepEncryptionSecurity() {
    var isLockedState by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(120)
        isLockedState = true
    }

    val iconRotation by animateFloatAsState(
        targetValue = if (isLockedState) 0f else -12f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "lock_rotate"
    )
    val badgeScale by animateFloatAsState(
        targetValue = if (isLockedState) 1f else 0.92f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "badge_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Shield + Lock animating into locked state
        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(badgeScale)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isLockedState) Icons.Default.Shield else Icons.Default.LockOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(44.dp)
                    .scale(if (isLockedState) 1f else 0.9f)
            )
        }

        Text(
            text = "Protected by AES-256 encryption and your device's secure hardware.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = "Hardened Cryptographic Architecture",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Your master password derives cryptographic keys via PBKDF2 with 310,000 rounds. Authenticated AES-256-GCM protects your data, and optional biometrics leverage the hardware Keystore enclave.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}

// =============================================================================
// SCREEN 5: Let's set up your vault
// =============================================================================
@Composable
private fun StepSetupVaultHandoff(
    onComplete: () -> Unit
) {
    var buttonVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        buttonVisible = true
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Let's set up your vault.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Next, you will create your master password. Since VaultKeep has no backdoors or cloud password resets, you are the sole guardian of your vault.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        AnimatedVisibility(
            visible = buttonVisible,
            enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { 18 }
        ) {
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("welcome_complete_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Create Master Password",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// =============================================================================
// ACCESSIBILITY: Reduce-Motion Fallback (Static Single-Screen Crossfade)
// =============================================================================
@Composable
private fun StaticWelcomeFallback(
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState())
            .testTag("welcome_static_fallback_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onComplete) {
                Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = "Welcome to VaultKeep",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "A fully local, private vault. Your passwords never leave this device.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "• No accounts. No cloud. No tracking.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• Protected by AES-256 encryption and your device's secure hardware.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Zero backdoors or master resets — you hold the only decryption key.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(top = 24.dp)
                .testTag("welcome_static_continue_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Let's Set Up Your Vault", style = MaterialTheme.typography.labelLarge)
        }
    }
}
