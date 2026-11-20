package com.example.flipclock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.designsystem.OutfitTimerFamily

object FlipClockPalette {
    val CanvasBlack = Color(0xFF000000)
    val CardSurface = Color(0xFF1A1A1D)
    val CardSurfaceTop = Color(0xFF232327)
    val CardSurfaceTopLower = Color(0xFF1C1C20)
    val CardSurfaceBottom = Color(0xFF18181B)
    val CardSurfaceBottomLower = Color(0xFF131316)
    val CardBorderEdge = Color(0xFF2B2B30)
    val HingeTick = Color(0xFF2F2F36)
    val DigitGrey = Color(0xFFC8C8CE)
    val SubTextGrey = Color(0xFF96969E)
    val SeamBlack = Color(0xFF020203)
    val PanelDark = Color(0xFF141416)
    val PanelElevated = Color(0xFF232326)
    val PanelOutline = Color(0xFF2F2F35)
    val AccentSoft = Color(0xFFE0E0E6)
}

/**
 * Authentic mechanical split-flap card rendered cleanly on pure #000000 canvas:
 * - Matte charcoal split plates with restrained overhead sheen & zero pocket/bezel clutter
 * - Crisp horizontal split gap with subtle hinge bevels
 * - Real 3D mechanical page-turn animation (top flap 0° -> -90° with fold shading,
 *   bottom flap 90° -> 0° with unfolding shadow scrim) ONLY when [value] genuinely changes
 */
@Composable
fun SplitFlipCard(
    value: String,
    cardWidth: Dp,
    cardHeight: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    seamThickness: Dp = 2.5.dp
) {
    var currentValue by remember { mutableStateOf(value) }
    var previousValue by remember { mutableStateOf(value) }
    val flipProgress = remember { Animatable(1f) }

    LaunchedEffect(value) {
        if (value != currentValue) {
            previousValue = currentValue
            currentValue = value
            flipProgress.snapTo(0f)
            flipProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 390, easing = FastOutSlowInEasing)
            )
            previousValue = currentValue
        }
    }

    val progress = flipProgress.value
    val isFlipping = progress < 0.999f && previousValue != currentValue
    val cameraDistancePx = with(LocalDensity.current) { 16.dp.toPx() }
    val cardShape = RoundedCornerShape(cornerRadius)

    val topStaticBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                FlipClockPalette.CardSurfaceTop,
                FlipClockPalette.CardSurfaceTopLower
            )
        )
    }
    val bottomStaticBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                FlipClockPalette.CardSurfaceBottom,
                FlipClockPalette.CardSurfaceBottomLower
            )
        )
    }

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(cardShape)
            .background(FlipClockPalette.CardSurface)
            .border(0.75.dp, FlipClockPalette.CardBorderEdge.copy(alpha = 0.65f), cardShape),
        contentAlignment = Alignment.Center
    ) {
        // Static upper plate shows incoming target value beneath folding top flap
        SplitCardHalf(
            text = currentValue,
            fontSize = fontSize,
            cardHeight = cardHeight,
            isTopHalf = true,
            backgroundBrush = topStaticBrush,
            shadeAlpha = if (isFlipping && progress < 0.5f) (0.28f * (1f - progress * 2f)) else 0f,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(cardHeight / 2f)
        )

        // Static lower plate keeps previous value during first half, then holds target value
        val bottomFoldShadow = if (isFlipping && progress >= 0.5f) {
            0.22f * (1f - ((progress - 0.5f) / 0.5f))
        } else 0f
        SplitCardHalf(
            text = if (isFlipping) previousValue else currentValue,
            fontSize = fontSize,
            cardHeight = cardHeight,
            isTopHalf = false,
            backgroundBrush = bottomStaticBrush,
            shadeAlpha = bottomFoldShadow,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(cardHeight / 2f)
        )

        // Animated 3D upper flap folding down (0° -> -90°)
        if (isFlipping && progress < 0.5f) {
            val phase = (progress / 0.5f).coerceIn(0f, 1f)
            val angle = -phase * 90f
            SplitCardHalf(
                text = previousValue,
                fontSize = fontSize,
                cardHeight = cardHeight,
                isTopHalf = true,
                backgroundBrush = topStaticBrush,
                shadeAlpha = phase * 0.46f,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(cardHeight / 2f)
                    .graphicsLayer {
                        rotationX = angle
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        cameraDistance = cameraDistancePx
                    }
            )
        }

        // Animated 3D lower flap unfolding down (90° -> 0°)
        if (isFlipping && progress >= 0.5f) {
            val phase = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
            val angle = (1f - phase) * 90f
            SplitCardHalf(
                text = currentValue,
                fontSize = fontSize,
                cardHeight = cardHeight,
                isTopHalf = false,
                backgroundBrush = bottomStaticBrush,
                shadeAlpha = (1f - phase) * 0.34f,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(cardHeight / 2f)
                    .graphicsLayer {
                        rotationX = angle
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        cameraDistance = cameraDistancePx
                    }
            )
        }

        // Precision center horizontal split gap + subtle hinge bevel notches
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(seamThickness)
                .background(FlipClockPalette.SeamBlack)
        )
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(seamThickness + 2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(FlipClockPalette.HingeTick)
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(seamThickness + 2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(FlipClockPalette.HingeTick)
            )
        }
    }
}

@Composable
private fun SplitCardHalf(
    text: String,
    fontSize: TextUnit,
    cardHeight: Dp,
    isTopHalf: Boolean,
    backgroundBrush: Brush,
    shadeAlpha: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(backgroundBrush)
            .clipToBounds(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(cardHeight)
                .offset(y = if (isTopHalf) 0.dp else -(cardHeight / 2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontFamily = OutfitTimerFamily,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                lineHeight = fontSize,
                color = FlipClockPalette.DigitGrey,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }

        if (shadeAlpha > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = shadeAlpha.coerceIn(0f, 0.7f)))
            )
        }
    }
}
