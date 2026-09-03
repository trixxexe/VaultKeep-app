package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.PasswordStrength
import com.example.ui.theme.SecurityAmber
import com.example.ui.theme.SecurityEmerald
import com.example.ui.theme.SecurityRed
import com.example.ui.theme.SecurityTeal

@Composable
fun StrengthMeter(
    strength: PasswordStrength,
    entropyBits: Double = 0.0,
    modifier: Modifier = Modifier
) {
    val targetProgress = when (strength) {
        PasswordStrength.VERY_WEAK -> 0.2f
        PasswordStrength.WEAK -> 0.4f
        PasswordStrength.MODERATE -> 0.6f
        PasswordStrength.STRONG -> 0.8f
        PasswordStrength.VERY_STRONG -> 1.0f
    }

    val targetColor = when (strength) {
        PasswordStrength.VERY_WEAK -> SecurityRed
        PasswordStrength.WEAK -> SecurityRed.copy(alpha = 0.85f)
        PasswordStrength.MODERATE -> SecurityAmber
        PasswordStrength.STRONG -> SecurityTeal
        PasswordStrength.VERY_STRONG -> SecurityEmerald
    }

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 250),
        label = "strengthProgress"
    )

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 250),
        label = "strengthColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("strength_meter"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (entropyBits > 0) "Entropy: ${entropyBits.toInt()} bits" else "Password Strength",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = strength.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = animatedColor
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(animatedColor)
            )
        }
    }
}

