package com.example.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Refined Pure-Flat geometric RESOLVE Focus Symbol drawn natively via Compose Canvas:
 * - Symmetrical open geometric Focus Ring with architectural upper-right break (-26° to -64°, sweep 322°)
 * - Crisp flat negative space and central refined Mint Focus Dot
 * - Pure flat aesthetic: zero gradients, shadows, 3D depth, or glow artifacts.
 * Matches ic_launcher_foreground.xml, ic_resolve_splash.xml, and resolve_logo_master.svg 1:1.
 */
private val RefinedFlatRingMint = Color(0xFF6EDCB0)
private val RefinedFlatDotMint = Color(0xFFA5F2D2)

@Composable
fun FocusSymbolMark(
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    ringProgress: Float = 1f,
    ringAlpha: Float = 1f,
    dotAlpha: Float = 1f,
    glowAlpha: Float = 0f,
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

        // Proportional to 108x108 adaptive safe-zone master (r=25/72 ~ 0.347, stroke=5.5/72 ~ 0.076, dot=6.2/72 ~ 0.086)
        val ringRadius = unit * 0.347f
        val ringStroke = unit * 0.076f
        val dotRadius = unit * 0.086f

        val clampedRingProgress = ringProgress.coerceIn(0f, 1f)
        val clampedRingAlpha = ringAlpha.coerceIn(0f, 1f)
        if (clampedRingProgress > 0.005f && clampedRingAlpha > 0.005f) {
            val totalSweep = 322f
            drawArc(
                color = RefinedFlatRingMint.copy(alpha = clampedRingAlpha),
                startAngle = -26f,
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
                color = RefinedFlatDotMint.copy(alpha = clampedDotAlpha),
                radius = dotRadius,
                center = center
            )
        }
    }
}
