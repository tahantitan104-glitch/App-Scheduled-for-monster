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

/**
 * Geometric RESOLVE Focus Symbol drawn natively via Compose Canvas:
 * - Soft radial center glow behind the focal dot
 * - Mint-emerald open geometric Focus Ring with upper-right gap (-14° clockwise 323° to -51°)
 * - Crisp Central Focus Point dot (#75E0B5)
 * Matches ic_launcher_foreground.xml, ic_resolve_splash.xml, and the brand logo PNG 1:1.
 */
private val FocusSymbolMint = Color(0xFF75E0B5)
private val FocusSymbolBackdrop = Color(0xFF10251F)

@Composable
fun FocusSymbolMark(
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    ringProgress: Float = 1f,
    ringAlpha: Float = 1f,
    dotAlpha: Float = 1f,
    glowAlpha: Float = 1f,
    showBackdropCircle: Boolean = false,
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

        if (showBackdropCircle) {
            drawCircle(
                color = FocusSymbolBackdrop,
                radius = unit * 0.50f,
                center = center
            )
        }

        val scaleFactor = if (showBackdropCircle) 1f else 1.28f
        val ringRadius = unit * 0.320f * scaleFactor
        val ringStroke = unit * 0.067f * scaleFactor
        val dotRadius = unit * 0.077f * scaleFactor
        val glowRadius = unit * 0.225f * scaleFactor

        val clampedGlowAlpha = glowAlpha.coerceIn(0f, 1f)
        if (clampedGlowAlpha > 0.005f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        FocusSymbolMint.copy(alpha = 0.07f * clampedGlowAlpha),
                        FocusSymbolMint.copy(alpha = 0.02f * clampedGlowAlpha),
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
            val totalSweep = 323f
            drawArc(
                color = FocusSymbolMint.copy(alpha = clampedRingAlpha),
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
                color = FocusSymbolMint.copy(alpha = clampedDotAlpha),
                radius = dotRadius,
                center = center
            )
        }
    }
}
