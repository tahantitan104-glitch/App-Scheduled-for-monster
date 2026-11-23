package com.example.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.domain.model.MascotAnimationState
import com.example.domain.model.MascotMicroAnimation
import com.example.domain.model.MascotType
import com.example.mascot.MascotControllerUiState
import com.example.presentation.components.bunny.ResolveBunnyMascot

/**
 * Single authoritative Mascot Renderer used consistently across Settings previews, Home badge,
 * Lock Screen, and Completion Celebrations.
 *
 * Guarantees strict mutual exclusivity: renders ONLY [MascotType.BABY_CLOUD] or ONLY
 * [MascotType.RESOLVE_BUNNY] — never both simultaneously.
 */
@Composable
fun MascotRenderer(
    mascot: MascotType,
    modifier: Modifier = Modifier,
    size: Dp = 176.dp,
    showFocusAura: Boolean = true,
    animationState: MascotAnimationState = MascotAnimationState.IDLE,
    microAnimation: MascotMicroAnimation = MascotMicroAnimation.NONE,
    isBlinking: Boolean? = null,
    peekProgress: Float = 1f,
    showWallOverride: Boolean = false,
    reducedMotion: Boolean = false,
    isPaused: Boolean = false
) {
    val safeSize = if (size.value.isFinite() && size.value > 0f) size else 64.dp
    val safePeek = peekProgress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f

    when (mascot) {
        MascotType.BABY_CLOUD -> {
            BabyCloudMascot(
                modifier = modifier,
                size = safeSize,
                showFocusAura = showFocusAura,
                animationState = animationState,
                isBlinkingExternal = isBlinking,
                reducedMotion = reducedMotion,
                isPaused = isPaused
            )
        }

        MascotType.RESOLVE_BUNNY -> {
            ResolveBunnyMascot(
                modifier = modifier,
                size = safeSize,
                showFocusAura = showFocusAura,
                animationState = animationState,
                microAnimation = microAnimation,
                isBlinkingExternal = isBlinking,
                peekProgress = safePeek,
                showWallOverride = showWallOverride,
                reducedMotion = reducedMotion,
                isPaused = isPaused
            )
        }
    }
}

/**
 * Lock Screen Mascot Stage coordinating the single active mascot with its Focus Message speech
 * bubble directly above it (never overlapping face, countdown, or bottom controls).
 */
@Composable
fun LockScreenMascotStage(
    activeMascot: MascotType,
    controllerState: MascotControllerUiState,
    reducedMotion: Boolean,
    isPaused: Boolean,
    mascotStageSize: Dp,
    modifier: Modifier = Modifier,
    bubbleSlotMinHeight: Dp = 54.dp
) {
    val effectiveState = when {
        controllerState.isCelebrating -> MascotAnimationState.CELEBRATING
        else -> controllerState.animationState
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("lock_screen_mascot_stage"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Reserve clean vertical slot for speech bubble so mascot & timer stay rock-steady
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = bubbleSlotMinHeight)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = controllerState.isBubbleVisible && !controllerState.bubbleMessage.isNullOrBlank(),
                enter = fadeIn(tween(260, easing = EaseInOutSine)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(260, easing = EaseInOutSine)),
                exit = fadeOut(tween(260, easing = EaseInOutSine)) +
                    scaleOut(targetScale = 0.94f, animationSpec = tween(260, easing = EaseInOutSine))
            ) {
                FocusMessageBubble(
                    message = controllerState.bubbleMessage.orEmpty(),
                    pointerAlignment = Alignment.CenterHorizontally,
                    isCelebration = controllerState.isCelebrating
                )
            }
        }

        Spacer(modifier = Modifier.height(if (bubbleSlotMinHeight < 50.dp) 4.dp else 6.dp))

        MascotRenderer(
            mascot = activeMascot,
            size = mascotStageSize,
            showFocusAura = true,
            animationState = effectiveState,
            microAnimation = controllerState.microAnimation,
            isBlinking = controllerState.isBlinking,
            peekProgress = controllerState.peekProgress,
            reducedMotion = reducedMotion,
            isPaused = isPaused
        )
    }
}

@Composable
fun FocusMessageBubble(
    message: String,
    pointerAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    isCelebration: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = LocalResolveExtendedPalette.current
    val bubbleBg = if (isCelebration) {
        palette.accent
    } else {
        palette.textPrimary
    }
    val bubbleBorder = if (isCelebration) {
        palette.accentStrong.copy(alpha = 0.65f)
    } else {
        palette.accent.copy(alpha = 0.55f)
    }
    val bubbleTextColor = if (isCelebration) {
        palette.onAccent
    } else {
        palette.background
    }
    val tailPath = remember { Path() }

    Column(
        modifier = modifier
            .widthIn(min = 120.dp, max = 260.dp)
            .testTag("focus_message_bubble")
            .semantics { contentDescription = "Focus encouragement message: $message" },
        horizontalAlignment = pointerAlignment
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = bubbleBg,
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, bubbleBorder)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 17.sp
                ),
                color = bubbleTextColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        // Subtle downward speech-bubble tail pointing cleanly toward the active mascot
        Canvas(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .size(width = 14.dp, height = 7.dp)
        ) {
            tailPath.reset()
            tailPath.moveTo(0f, 0f)
            tailPath.lineTo(size.width, 0f)
            tailPath.lineTo(size.width * 0.5f, size.height)
            tailPath.close()
            drawPath(path = tailPath, color = bubbleBg)
        }
    }
}
