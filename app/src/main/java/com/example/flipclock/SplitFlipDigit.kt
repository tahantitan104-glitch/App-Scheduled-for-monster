package com.example.flipclock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.designsystem.FlipClockDisplayFamily

object FlipClockPalette {
    val CanvasBlack = Color(0xFF000000)
    val DigitGrey = Color(0xFFC8CEC9)
    val DigitGreyFlip = Color(0xFFA0A7A2)
    val SubTextGrey = Color(0xFFA2ACA5)
    val SeamBlack = Color(0xFF000000)

    // Rich Dark Forest Green control chrome tokens + tasteful Mint CTA
    val PanelDark = Color(0xFF10221C)
    val PanelElevated = Color(0xFF152B23)
    val PanelActive = Color(0xFF1B362C)
    val PanelOutline = Color(0xFF28483B)
    val DividerSubtle = Color(0xFF1D362D)
    val PrimaryCta = Color(0xFF5BD1A4)
    val PrimaryCtaText = Color(0xFF071A14)
    val SageAccent = Color(0xFF176B5B)
    val SageLabel = Color(0xFF5BD1A4)
    val AccentSoft = Color(0xFFF2F5F1)
    val TimerIndicator = Color(0xFF5BD1A4)
}

private val FlipPageEasing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1.0f)

private val TopHalfClipShape: Shape = GenericShape { size, _ ->
    addRect(Rect(left = 0f, top = 0f, right = size.width, bottom = size.height * 0.5f))
}

private val BottomHalfClipShape: Shape = GenericShape { size, _ ->
    addRect(Rect(left = 0f, top = size.height * 0.5f, right = size.width, bottom = size.height))
}

// Empirically verified optical midpoint ratio of Manrope numerals (0..9) above baseline (capH ~ 0.72 * em)
private const val MANROPE_NUMERAL_MID_RATIO = 0.360f

/**
 * Borderless pure-black split-flap numeral group:
 * - Zero rectangular box, zero border, zero background card plate at rest
 * - Bold rounded geometric sans-serif numerals (Manrope ExtraBold, tabular figures) directly on #000000
 * - Per-digit 3D split-flap animation so ONLY the digit(s) that changed flip
 * - Both halves occupy the exact full digit bounds and clip strictly at 50% height with TransformOrigin(0.5f, 0.5f)
 * - [showFlipAxis] toggles visual seam appearance ONLY without altering the 50% hinge geometry
 */
@Composable
fun SplitFlipCard(
    value: String,
    cardWidth: Dp,
    cardHeight: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") cornerRadius: Dp = 0.dp,
    seamThickness: Dp = 3.2.dp,
    digitColor: Color = FlipClockPalette.DigitGrey,
    showFlipAxis: Boolean = true
) {
    val normalized = value.ifEmpty { "0" }
    val digits = normalized.toCharArray()
    val slotGap = if (digits.size > 1) 4.dp else 0.dp

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (digits.size == 1) {
            key("single_${digits.size}") {
                val singleSlotWidth = (cardWidth * 0.60f).coerceAtLeast(cardHeight * 0.58f)
                SplitFlipSingleDigit(
                    digit = digits[0],
                    digitWidth = singleSlotWidth,
                    cardHeight = cardHeight,
                    fontSize = fontSize,
                    seamThickness = seamThickness,
                    digitColor = digitColor,
                    showFlipAxis = showFlipAxis
                )
            }
        } else {
            val totalSlots = digits.size
            val singleSlotWidth = ((cardWidth - slotGap * (totalSlots - 1)) / totalSlots.toFloat())
                .coerceAtLeast(24.dp)
            Row(
                modifier = Modifier
                    .width(cardWidth)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(slotGap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                digits.forEachIndexed { index, ch ->
                    // Right-aligned positional key keeps seconds/minutes units animating cleanly even if width changes
                    val rightAlignedKey = totalSlots - index
                    key("slot_$rightAlignedKey") {
                        SplitFlipSingleDigit(
                            digit = ch,
                            digitWidth = singleSlotWidth,
                            cardHeight = cardHeight,
                            fontSize = fontSize,
                            seamThickness = seamThickness,
                            digitColor = digitColor,
                            showFlipAxis = showFlipAxis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitFlipSingleDigit(
    digit: Char,
    digitWidth: Dp,
    cardHeight: Dp,
    fontSize: TextUnit,
    seamThickness: Dp,
    digitColor: Color,
    showFlipAxis: Boolean
) {
    var currentDigit by remember { mutableStateOf(digit) }
    var previousDigit by remember { mutableStateOf(digit) }
    val flipProgress = remember { Animatable(1f) }

    LaunchedEffect(digit) {
        if (digit != currentDigit) {
            previousDigit = currentDigit
            currentDigit = digit
            flipProgress.snapTo(0f)
            flipProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FlipPageEasing)
            )
            previousDigit = currentDigit
        }
    }

    val progress = flipProgress.value
    val isFlipping = progress < 0.999f && previousDigit != currentDigit
    val density = LocalDensity.current
    val cardHeightPx = with(density) { cardHeight.toPx() }
    val fontSizePx = with(density) { fontSize.toPx() }
    val cameraDistancePx = with(density) { 22.dp.toPx() }

    val digitTextStyle = remember(fontSize) {
        TextStyle(
            fontFamily = FlipClockDisplayFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontFeatureSettings = "tnum",
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Proportional,
                trim = LineHeightStyle.Trim.None
            )
        )
    }

    // Measure reference glyph "8" synchronously so every digit shares identical baseline & exact 50% ink center
    val textMeasurer = rememberTextMeasurer()
    val exactTranslationY = remember(digitTextStyle, cardHeightPx, fontSizePx) {
        val referenceBaseline = textMeasurer.measure(
            text = AnnotatedString("8"),
            style = digitTextStyle,
            softWrap = false,
            maxLines = 1
        ).firstBaseline
        val numeralVisualMidInLayout = referenceBaseline - (MANROPE_NUMERAL_MID_RATIO * fontSizePx)
        (cardHeightPx * 0.5f) - numeralVisualMidInLayout
    }

    val currentText = currentDigit.toString()
    val previousText = previousDigit.toString()

    Box(
        modifier = Modifier
            .width(digitWidth)
            .height(cardHeight)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        // 1. Static upper half [0..50%): reveals incoming digit as previous top flap folds forward
        val topRevealAlpha = if (isFlipping && progress < 0.5f) {
            0.52f + 0.48f * (progress / 0.5f)
        } else 1f
        FullCardClippedDigitFace(
            text = currentText,
            style = digitTextStyle,
            digitColor = digitColor.copy(alpha = topRevealAlpha),
            translationYPx = exactTranslationY,
            plateBackground = Color.Transparent,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = TopHalfClipShape
                }
        )

        // 2. Static lower half [50%..100%]: holds previous digit while top folds, dims smoothly under incoming flap
        val bottomFadeAlpha = if (isFlipping && progress >= 0.5f) {
            1f - ((progress - 0.5f) / 0.5f) * 0.80f
        } else 1f
        FullCardClippedDigitFace(
            text = if (isFlipping) previousText else currentText,
            style = digitTextStyle,
            digitColor = digitColor.copy(alpha = bottomFadeAlpha),
            translationYPx = exactTranslationY,
            plateBackground = Color.Transparent,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = BottomHalfClipShape
                }
        )

        // 3. Folding upper flap (0° -> -90°) hinged at exact 50% vertical center TransformOrigin(0.5f, 0.5f)
        if (isFlipping && progress < 0.5f) {
            val phase = (progress / 0.5f).coerceIn(0f, 1f)
            val angle = -phase * 90f
            FullCardClippedDigitFace(
                text = previousText,
                style = digitTextStyle,
                digitColor = FlipClockPalette.DigitGreyFlip.copy(alpha = 1f - phase * 0.45f),
                translationYPx = exactTranslationY,
                plateBackground = FlipClockPalette.CanvasBlack,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        shape = TopHalfClipShape
                        rotationX = angle
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        cameraDistance = cameraDistancePx
                    }
            )
        }

        // 4. Unfolding lower flap (+90° -> 0°) hinged at exact 50% vertical center TransformOrigin(0.5f, 0.5f)
        if (isFlipping && progress >= 0.5f) {
            val phase = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
            val angle = (1f - phase) * 90f
            FullCardClippedDigitFace(
                text = currentText,
                style = digitTextStyle,
                digitColor = digitColor.copy(alpha = 0.72f + 0.28f * phase),
                translationYPx = exactTranslationY,
                plateBackground = FlipClockPalette.CanvasBlack,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        shape = BottomHalfClipShape
                        rotationX = angle
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        cameraDistance = cameraDistancePx
                    }
            )
        }

        // 5. Visual horizontal split axis centered strictly at 50% card height
        if (showFlipAxis) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(seamThickness)
                    .background(FlipClockPalette.SeamBlack)
            )
        }
    }
}

@Composable
private fun FullCardClippedDigitFace(
    text: String,
    style: TextStyle,
    digitColor: Color,
    translationYPx: Float,
    plateBackground: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(plateBackground),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = text,
            style = style,
            color = digitColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.graphicsLayer {
                translationY = translationYPx
            }
        )
    }
}
