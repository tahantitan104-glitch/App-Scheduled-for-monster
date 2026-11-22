package com.example.presentation.components

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.designsystem.ResolveColors

object ResolveMotion {
    const val STANDARD_DURATION_MS = 260
    const val SUBSCREEN_DURATION_MS = 290
    const val EXIT_DURATION_MS = 190
    const val REDUCED_DURATION_MS = 95

    val StandardEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

    fun <T> standardTween(reducedMotion: Boolean): FiniteAnimationSpec<T> = tween(
        durationMillis = if (reducedMotion) REDUCED_DURATION_MS else STANDARD_DURATION_MS,
        easing = StandardEasing
    )

    fun <T> subScreenTween(reducedMotion: Boolean): FiniteAnimationSpec<T> = tween(
        durationMillis = if (reducedMotion) REDUCED_DURATION_MS else SUBSCREEN_DURATION_MS,
        easing = StandardEasing
    )

    fun <T> fastTween(reducedMotion: Boolean): FiniteAnimationSpec<T> = tween(
        durationMillis = if (reducedMotion) REDUCED_DURATION_MS else 220,
        easing = StandardEasing
    )

    fun <T> exitTween(reducedMotion: Boolean): FiniteAnimationSpec<T> = tween(
        durationMillis = if (reducedMotion) REDUCED_DURATION_MS else EXIT_DURATION_MS,
        easing = StandardEasing
    )

    fun intOffsetSlideTween(reducedMotion: Boolean): FiniteAnimationSpec<IntOffset> = tween(
        durationMillis = if (reducedMotion) REDUCED_DURATION_MS else SUBSCREEN_DURATION_MS,
        easing = StandardEasing
    )

    fun gentleSpring(): FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.84f,
        stiffness = Spring.StiffnessMediumLow
    )
}

fun isSystemReducedMotionEnabled(context: android.content.Context): Boolean = runCatching {
    val animatorScale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
    val transitionScale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        1f
    )
    animatorScale <= 0f || transitionScale <= 0f
}.getOrDefault(false)

@Composable
fun rememberEffectiveReducedMotion(appReducedMotion: Boolean): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var osReduced by remember(context) { mutableStateOf(isSystemReducedMotionEnabled(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                osReduced = isSystemReducedMotionEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return appReducedMotion || osReduced
}

@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "press_scale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun PulsingStatusDot(
    color: Color,
    size: Dp = 9.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "status_dot_pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier.size(size * 2),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    alpha = pulseAlpha
                }
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun TactilePrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    cornerRadius: Dp = 16.dp,
    height: Dp = 54.dp,
    content: @Composable RowScope.() -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    val resolvedContainer = if (containerColor == Color.Unspecified) palette.primaryButton else containerColor
    val resolvedContent = if (contentColor == Color.Unspecified) palette.onPrimaryButton else contentColor
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(cornerRadius),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        border = if (enabled) {
            androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = palette.accentStrong.copy(alpha = 0.25f)
            )
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = resolvedContainer,
            contentColor = resolvedContent,
            disabledContainerColor = palette.cardBottom,
            disabledContentColor = palette.textMuted
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pressScale(interactionSource, pressedScale = 0.975f)
    ) {
        content()
    }
}

fun resolveCardBrush(
    topColor: Color = ResolveColors.ForestSurface,
    bottomColor: Color = ResolveColors.ForestSecondary
): Brush = Brush.verticalGradient(colors = listOf(topColor, bottomColor))

fun Modifier.resolveGlassCard(
    cornerRadius: Dp = 18.dp,
    borderColor: Color? = null
): Modifier = composed {
    val palette = LocalResolveExtendedPalette.current
    val effectiveBorder = borderColor ?: palette.cardBorder
    this
        .clip(RoundedCornerShape(cornerRadius))
        .background(
            Brush.verticalGradient(
                colors = listOf(palette.cardTop, palette.cardBottom)
            )
        )
        .border(1.dp, effectiveBorder, RoundedCornerShape(cornerRadius))
}

@Composable
fun resolveSwitchColors(): SwitchColors {
    val palette = LocalResolveExtendedPalette.current
    return SwitchDefaults.colors(
        checkedThumbColor = palette.onPrimaryButton,
        checkedTrackColor = palette.accent,
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = Color(0xFF93A39B),
        uncheckedTrackColor = palette.controlSurface,
        uncheckedBorderColor = palette.controlBorder,
        disabledCheckedThumbColor = palette.onPrimaryButton.copy(alpha = 0.6f),
        disabledCheckedTrackColor = palette.accent.copy(alpha = 0.45f),
        disabledUncheckedThumbColor = palette.textMuted,
        disabledUncheckedTrackColor = palette.cardBottom
    )
}

@Composable
fun FeatureTipNote(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val palette = LocalResolveExtendedPalette.current
    val resolvedTint = tint ?: palette.accentSecondary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = resolvedTint.copy(alpha = 0.90f),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            maxLines = 2
        )
    }
}
