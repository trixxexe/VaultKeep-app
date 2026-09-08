package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * High-performance, polished skeleton/shimmer loading component.
 * Used for >500ms operations (backup imports/exports, integrity scans, sync, breach audits).
 */
@Composable
fun PolishedShimmerBox(
    modifier: Modifier = Modifier,
    shapeRadius: Int = 16
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    )

    val transition = rememberInfiniteTransition(label = "polished_shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "polished_shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 300f, translateAnim - 300f),
        end = Offset(translateAnim, translateAnim)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(shapeRadius.dp))
            .background(brush)
    )
}

/**
 * Skeleton loader for Vault Integrity Verification and Security Scanning.
 */
@Composable
fun SecurityAuditSkeletonLoader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PolishedShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            shapeRadius = 24
        )

        PolishedShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(20.dp),
            shapeRadius = 8
        )

        repeat(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PolishedShimmerBox(
                    modifier = Modifier.size(44.dp),
                    shapeRadius = 14
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PolishedShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp),
                        shapeRadius = 6
                    )
                    PolishedShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(10.dp),
                        shapeRadius = 6
                    )
                }
            }
        }
    }
}

/**
 * Skeleton loader for Backup File Parsing and Preview.
 */
@Composable
fun BackupImportSkeletonLoader() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PolishedShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(22.dp),
                shapeRadius = 8
            )

            PolishedShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp),
                shapeRadius = 6
            )

            Spacer(modifier = Modifier.height(4.dp))

            repeat(2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PolishedShimmerBox(
                        modifier = Modifier.size(38.dp),
                        shapeRadius = 12
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PolishedShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(14.dp),
                            shapeRadius = 6
                        )
                        PolishedShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .height(10.dp),
                            shapeRadius = 6
                        )
                    }
                }
            }
        }
    }
}
