package com.example.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.designsystem.ResolveColors
import com.example.domain.model.ProtectionRecoveryAction
import com.example.domain.model.ProtectionStatus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Centered minimalist side-profile running human silhouette inside a rounded square container
 * over a subtle horizontal ground line — without any outer polygon/shield/ring.
 */
@Composable
fun ResolveRunnerLogo(
    size: Dp = 52.dp,
    animateRunner: Boolean = true,
    reducedMotion: Boolean = false,
    testTagName: String = "resolve_runner_logo",
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "resolve_runner_logo")
    val rawPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "runner_stride_phase"
    )
    val shouldAnimate = animateRunner && !reducedMotion
    val stridePhase = if (shouldAnimate) rawPhase else 0.55f

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(15.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        ResolveColors.ForestBrandDeep,
                        ResolveColors.ForestSurface
                    )
                )
            )
            .border(
                1.dp,
                ResolveColors.MintAccent.copy(alpha = 0.36f),
                RoundedCornerShape(15.dp)
            )
            .testTag(testTagName),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.78f)) {
            val w = this.size.width
            val h = this.size.height

            val bob = ((cos(stridePhase * 2f) + 1f) * 0.5f) * h * 0.032f
            val swing = sin(stridePhase)
            val oppSwing = -swing
            val stroke = w * 0.072f

            // Subtle single horizontal baseline centered underneath the runner
            drawLine(
                color = ResolveColors.MintAccent.copy(alpha = 0.42f),
                start = Offset(w * 0.16f, h * 0.80f),
                end = Offset(w * 0.84f, h * 0.80f),
                strokeWidth = stroke * 0.48f,
                cap = StrokeCap.Round
            )

            // Side-profile athletic runner facing right, centered horizontally around 0.50f
            val headCenter = Offset(w * 0.56f, h * 0.25f - bob)
            val shoulder = Offset(w * 0.52f, h * 0.35f - bob)
            val hip = Offset(w * 0.45f, h * 0.55f - bob)

            // Subtle speed accent trail behind shoulder
            drawLine(
                color = ResolveColors.MintAccent.copy(alpha = 0.50f),
                start = Offset(w * 0.20f, h * 0.40f - bob),
                end = Offset(w * 0.34f, h * 0.40f - bob),
                strokeWidth = stroke * 0.52f,
                cap = StrokeCap.Round
            )

            // Head
            drawCircle(
                color = ResolveColors.MintBright,
                radius = w * 0.085f,
                center = headCenter
            )

            // Torso leaning subtly forward into focus momentum
            drawLine(
                color = ResolveColors.MintBright,
                start = shoulder,
                end = hip,
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )

            // Back arm
            val backElbow = Offset(
                w * (0.42f + oppSwing * 0.08f),
                h * (0.44f - oppSwing * 0.03f) - bob
            )
            val backHand = Offset(
                w * (0.33f + oppSwing * 0.10f),
                h * (0.49f - oppSwing * 0.04f) - bob
            )
            drawLine(
                color = ResolveColors.MintAccent,
                start = shoulder,
                end = backElbow,
                strokeWidth = stroke * 0.78f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = ResolveColors.MintAccent,
                start = backElbow,
                end = backHand,
                strokeWidth = stroke * 0.72f,
                cap = StrokeCap.Round
            )

            // Front arm
            val frontElbow = Offset(
                w * (0.60f + swing * 0.08f),
                h * (0.44f - swing * 0.04f) - bob
            )
            val frontHand = Offset(
                w * (0.69f + swing * 0.08f),
                h * (0.37f - swing * 0.05f) - bob
            )
            drawLine(
                color = Color(0xFFEAF7F1),
                start = shoulder,
                end = frontElbow,
                strokeWidth = stroke * 0.80f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFEAF7F1),
                start = frontElbow,
                end = frontHand,
                strokeWidth = stroke * 0.74f,
                cap = StrokeCap.Round
            )

            // Back leg (thigh + shin stride)
            val backKnee = Offset(
                w * (0.38f + oppSwing * 0.12f),
                h * (0.66f - oppSwing * 0.02f) - bob
            )
            val backFoot = Offset(
                w * (0.30f + oppSwing * 0.14f),
                h * (0.76f + (if (oppSwing < 0f) oppSwing * 0.03f else 0f)) - bob * 0.5f
            )
            drawLine(
                color = ResolveColors.MintAccent,
                start = hip,
                end = backKnee,
                strokeWidth = stroke * 0.88f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = ResolveColors.MintAccent,
                start = backKnee,
                end = backFoot,
                strokeWidth = stroke * 0.80f,
                cap = StrokeCap.Round
            )

            // Front leg (thigh + shin stride)
            val frontKnee = Offset(
                w * (0.56f + swing * 0.11f),
                h * (0.64f - swing * 0.04f) - bob
            )
            val frontFoot = Offset(
                w * (0.63f + swing * 0.13f),
                h * (0.76f + (if (swing < 0f) swing * 0.03f else 0f)) - bob * 0.5f
            )
            drawLine(
                color = ResolveColors.MintBright,
                start = hip,
                end = frontKnee,
                strokeWidth = stroke * 0.90f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = ResolveColors.MintBright,
                start = frontKnee,
                end = frontFoot,
                strokeWidth = stroke * 0.82f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Smooth keyboard-typing text reveal preserving full line bounds cleanly.
 */
@Composable
fun KeyboardTypingText(
    fullText: String,
    style: TextStyle,
    color: Color,
    startTyping: Boolean,
    startDelayMillis: Int,
    typingDurationMillis: Int,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    highlightTipColor: Color? = null,
    alreadyCompleted: Boolean = false,
    onTypingCompleted: () -> Unit = {}
) {
    val palette = LocalResolveExtendedPalette.current
    val resolvedTipColor = highlightTipColor ?: palette.accentStrong
    val skipAnimation = reducedMotion || alreadyCompleted
    val progress = remember(fullText) { Animatable(if (skipAnimation) 1f else 0f) }
    var showCursor by remember(fullText) { mutableStateOf(false) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    LaunchedEffect(startTyping, skipAnimation, fullText) {
        if (skipAnimation) {
            showCursor = false
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        if (startTyping && progress.value < 1f) {
            if (startDelayMillis > 0) {
                delay(startDelayMillis.toLong())
            }
            showCursor = true
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = typingDurationMillis,
                    easing = LinearEasing
                )
            )
            delay(240L)
            showCursor = false
            onTypingCompleted()
        }
    }

    val visibleCharCount = if (skipAnimation) {
        fullText.length
    } else {
        (progress.value * fullText.length).roundToInt().coerceIn(0, fullText.length)
    }

    val styledText = buildAnnotatedString {
        if (visibleCharCount > 0) {
            val freshStart = (visibleCharCount - 2).coerceAtLeast(0)
            if (visibleCharCount < fullText.length && freshStart > 0) {
                withStyle(SpanStyle(color = color)) {
                    append(fullText.substring(0, freshStart))
                }
                withStyle(SpanStyle(color = resolvedTipColor)) {
                    append(fullText.substring(freshStart, visibleCharCount))
                }
            } else {
                withStyle(SpanStyle(color = color)) {
                    append(fullText.substring(0, visibleCharCount))
                }
            }
        }
        if (visibleCharCount < fullText.length) {
            withStyle(SpanStyle(color = Color.Transparent)) {
                append(fullText.substring(visibleCharCount))
            }
        }
    }

    Text(
        text = styledText,
        style = style,
        onTextLayout = { textLayoutResult = it },
        modifier = modifier.drawWithContent {
            drawContent()
            if (showCursor && !reducedMotion) {
                val layout = textLayoutResult ?: return@drawWithContent
                runCatching {
                    val safeIndex = visibleCharCount.coerceIn(0, fullText.length)
                    val caretRect = layout.getCursorRect(safeIndex)
                    val caretHeight = (caretRect.height - 3f).coerceAtLeast(8f)
                    drawRect(
                        color = resolvedTipColor,
                        topLeft = Offset(caretRect.left, caretRect.top + 1.5f),
                        size = Size(width = 1.8.dp.toPx(), height = caretHeight)
                    )
                }
            }
        }
    )
}

@Composable
fun ProtectionStatusCard(
    status: ProtectionStatus,
    onActionClick: (ProtectionRecoveryAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalResolveExtendedPalette.current
    val (accentColor, bgContainer, icon) = when (status) {
        is ProtectionStatus.ProtectionActive ->
            Triple(palette.accent, palette.accentSoftContainer, Icons.Outlined.CheckCircle)
        is ProtectionStatus.SessionCompleted ->
            Triple(palette.accent, palette.accentSoftContainer, Icons.Outlined.CheckCircle)
        is ProtectionStatus.ProtectionLimited ->
            Triple(palette.warn, palette.warnContainer, Icons.Outlined.Info)
        is ProtectionStatus.Recovering ->
            Triple(palette.warn, palette.warnContainer, Icons.Outlined.Info)
        is ProtectionStatus.ActionRequired ->
            Triple(palette.error, palette.errorContainer, Icons.Outlined.Warning)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .resolveGlassCard(
                cornerRadius = 20.dp,
                borderColor = accentColor.copy(alpha = 0.42f)
            )
            .padding(14.dp)
            .testTag("protection_status_card"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = status.title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.textPrimary
                )
                Text(
                    text = status.whatHappened,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
            }
        }

        Text(
            text = status.affectedFunctionality,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor
        )

        val label = status.recommendedActionLabel
        if (label != null && status.actionType != ProtectionRecoveryAction.NONE) {
            OutlinedButton(
                onClick = { onActionClick(status.actionType) },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                modifier = Modifier.testTag("protection_status_action_button")
            ) {
                Spacer(Modifier.width(2.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
