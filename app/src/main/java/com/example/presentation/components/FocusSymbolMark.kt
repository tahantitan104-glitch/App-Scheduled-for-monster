package com.example.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.designsystem.ResolveColors

/**
 * Pure geometric RESOLVE Focus Symbol drawn natively via Compose Canvas:
 * - Custom open geometric Focus Ring with architectural upper-right interruption
 * - Restrained ambient mint glow around center point
 * - Central mint focus dot (#75E0B5 via ResolveColors.FocusMint)
 *
 * Matches ic_launcher_foreground.xml proportions (scaled inside adaptive safe zone)
 * with zero mascots, zero text, and zero bitmap/network allocations.
 */
@Composable
fun FocusSymbolMark(
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    ringProgress: Float = 1f,
    ringAlpha: Float = 1f,
    dotAlpha: Float = 1f,
    glowAlpha: Float = 1f,
    testTagName: String = "focus_symbol_mark"
) {
    Canvas(
        modifier = modifier
            .size(size)
            .testTag(testTagName)
            .semantics { contentDescription = "RESOLVE focus symbol" }
    ) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val unit = this.size.minDimension

        // Scaled relative to the crisp central safe-zone geometry (68% of canvas)
        val ringRadius = unit * 0.34f
        val ringStroke = unit * 0.068f
        val dotRadius = unit * 0.08f
        val glowRadius = unit * 0.24f

        if (glowAlpha > 0.005f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ResolveColors.FocusMint.copy(alpha = 0.20f * glowAlpha.coerceIn(0f, 1f)),
                        ResolveColors.FocusMint.copy(alpha = 0.06f * glowAlpha.coerceIn(0f, 1f)),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )
        }

        val clampedRingProgress = ringProgress.coerceIn(0f, 1f)
        val clampedRingAlpha = ringAlpha.coerceIn(0f, 1f)
        if (clampedRingProgress > 0.005f && clampedRingAlpha > 0.005f) {
            val totalSweep = 322f
            drawArc(
                color = ResolveColors.FocusMint.copy(alpha = clampedRingAlpha),
                startAngle = -14f,
                sweepAngle = totalSweep * clampedRingProgress,
                useCenter = false,
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                size = Size(ringRadius * 2f, ringRadius * 2f),
                style = Stroke(width = ringStroke, cap = StrokeCap.Round)
            )
        }

        val clampedDotAlpha = dotAlpha.coerceIn(0f, 1f)
        if (clampedDotAlpha > 0.005f) {
            drawCircle(
                color = ResolveColors.FocusMint.copy(alpha = clampedDotAlpha),
                radius = dotRadius,
                center = center
            )
        }
    }
}
