package com.example.presentation.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.designsystem.ResolveColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BabyCloudMascot(
    modifier: Modifier = Modifier,
    size: Dp = 190.dp,
    showFocusAura: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "baby_cloud_premium")

    val floatOffsetY by transition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_y"
    )

    val swayOffsetX by transition.animateFloat(
        initialValue = -3.2f,
        targetValue = 3.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway_x"
    )

    val gentleTilt by transition.animateFloat(
        initialValue = -1.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt"
    )

    val breatheScale by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.028f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val auraPulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_pulse"
    )

    val sparklePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkle_phase"
    )

    val eyeOpenFraction by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5200
                1f at 0
                1f at 3950
                0.08f at 4080
                1f at 4220
                1f at 4420
                0.08f at 4530
                1f at 4680
                1f at 5200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "double_blink"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Baby Cloud focus mascot" }
    ) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)

        if (showFocusAura) {
            // Outer atmospheric mint aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ResolveColors.MintAccent.copy(alpha = 0.22f),
                        ResolveColors.SageAccent.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = w * 0.50f * auraPulse
                ),
                radius = w * 0.50f * auraPulse,
                center = center
            )

            // Subtle serenity ring
            drawCircle(
                color = ResolveColors.MintAccent.copy(alpha = 0.12f),
                radius = w * 0.41f,
                center = center,
                style = Stroke(width = w * 0.006f)
            )

            // Soft twinkling starlight sparkles around Baby Cloud
            drawStarlightSparkle(
                center = Offset(
                    w * 0.18f + cos(sparklePhase) * w * 0.015f,
                    h * 0.28f + sin(sparklePhase) * h * 0.02f
                ),
                radius = w * 0.022f,
                alpha = 0.35f + 0.35f * sin(sparklePhase * 2f).coerceIn(0f, 1f)
            )
            drawStarlightSparkle(
                center = Offset(
                    w * 0.83f + sin(sparklePhase) * w * 0.015f,
                    h * 0.25f + cos(sparklePhase) * h * 0.018f
                ),
                radius = w * 0.018f,
                alpha = 0.30f + 0.40f * cos(sparklePhase * 2f).coerceIn(0f, 1f)
            )
            drawCircle(
                color = ResolveColors.MintBright.copy(alpha = 0.45f),
                radius = w * 0.010f,
                center = Offset(w * 0.78f, h * 0.67f + floatOffsetY * density * 0.3f)
            )
            drawCircle(
                color = ResolveColors.MintAccent.copy(alpha = 0.35f),
                radius = w * 0.008f,
                center = Offset(w * 0.22f, h * 0.63f - floatOffsetY * density * 0.25f)
            )
        }

        // Ground shadow that responds inversely to float height
        val floatFraction = ((floatOffsetY + 7f) / 14f).coerceIn(0f, 1f)
        val shadowScale = 0.86f + floatFraction * 0.16f
        val shadowAlpha = 0.20f + floatFraction * 0.14f
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = shadowAlpha),
                    Color.Transparent
                ),
                center = Offset(w * 0.50f, h * 0.79f),
                radius = w * 0.30f * shadowScale
            ),
            topLeft = Offset(w * 0.50f - w * 0.28f * shadowScale, h * 0.75f),
            size = Size(w * 0.56f * shadowScale, h * 0.08f)
        )

        translate(left = swayOffsetX * density, top = floatOffsetY * density) {
            rotate(degrees = gentleTilt, pivot = center) {
                scale(scale = breatheScale, pivot = center) {
                    val cloudBottom = h * 0.685f
                    val cloudWidth = w * 0.74f
                    val leftX = (w - cloudWidth) / 2f
                    val rightX = leftX + cloudWidth

                    // Soft mint underglow on cloud belly
                    drawOval(
                        color = ResolveColors.MintAccent.copy(alpha = 0.14f),
                        topLeft = Offset(leftX + w * 0.05f, cloudBottom - h * 0.09f),
                        size = Size(cloudWidth - w * 0.10f, h * 0.12f)
                    )

                    val bodyBrush = Brush.verticalGradient(
                        colors = listOf(
                            ResolveColors.CloudHighlight,
                            ResolveColors.CloudBodyTop,
                            ResolveColors.CloudBodyMid,
                            ResolveColors.CloudBodyShadow
                        ),
                        startY = h * 0.22f,
                        endY = cloudBottom
                    )

                    // Base body pill
                    drawRoundRect(
                        brush = bodyBrush,
                        topLeft = Offset(leftX, h * 0.44f),
                        size = Size(cloudWidth, h * 0.245f),
                        cornerRadius = CornerRadius(h * 0.135f, h * 0.135f)
                    )

                    // Left outer lobe
                    drawCircle(
                        brush = bodyBrush,
                        radius = w * 0.148f,
                        center = Offset(leftX + w * 0.165f, h * 0.47f)
                    )

                    // Upper-left secondary lobe
                    drawCircle(
                        brush = bodyBrush,
                        radius = w * 0.135f,
                        center = Offset(w * 0.36f, h * 0.385f)
                    )

                    // Center-top crown lobe
                    drawCircle(
                        brush = bodyBrush,
                        radius = w * 0.195f,
                        center = Offset(w * 0.505f, h * 0.355f)
                    )

                    // Right lobe
                    drawCircle(
                        brush = bodyBrush,
                        radius = w * 0.158f,
                        center = Offset(rightX - w * 0.165f, h * 0.46f)
                    )

                    // Soft inner specular rim highlights on upper crown for 3D volumetric feel
                    drawArc(
                        color = Color.White.copy(alpha = 0.72f),
                        startAngle = 200f,
                        sweepAngle = 115f,
                        useCenter = false,
                        topLeft = Offset(w * 0.505f - w * 0.165f, h * 0.355f - w * 0.165f),
                        size = Size(w * 0.33f, w * 0.33f),
                        style = Stroke(width = w * 0.016f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color.White.copy(alpha = 0.55f),
                        startAngle = 195f,
                        sweepAngle = 95f,
                        useCenter = false,
                        topLeft = Offset(leftX + w * 0.165f - w * 0.12f, h * 0.47f - w * 0.12f),
                        size = Size(w * 0.24f, w * 0.24f),
                        style = Stroke(width = w * 0.012f, cap = StrokeCap.Round)
                    )

                    // Soft mint-gray belly shading curve
                    drawOval(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                ResolveColors.SageAccent.copy(alpha = 0.16f)
                            ),
                            startY = h * 0.56f,
                            endY = cloudBottom
                        ),
                        topLeft = Offset(leftX + w * 0.05f, h * 0.55f),
                        size = Size(cloudWidth - w * 0.10f, h * 0.13f)
                    )

                    // Expressive glossy eyes with catchlights
                    val eyeY = h * 0.49f
                    val leftEyeX = w * 0.42f
                    val rightEyeX = w * 0.58f
                    val eyeWidth = w * 0.040f
                    val eyeHeight = (w * 0.048f * eyeOpenFraction).coerceAtLeast(2.4f)

                    drawCloudEye(
                        centerX = leftEyeX,
                        centerY = eyeY,
                        eyeWidth = eyeWidth,
                        eyeHeight = eyeHeight,
                        isOpen = eyeOpenFraction > 0.35f
                    )
                    drawCloudEye(
                        centerX = rightEyeX,
                        centerY = eyeY,
                        eyeWidth = eyeWidth,
                        eyeHeight = eyeHeight,
                        isOpen = eyeOpenFraction > 0.35f
                    )

                    // Warm rosy-coral soft cheeks
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ResolveColors.CloudBlush.copy(alpha = 0.42f),
                                ResolveColors.CloudBlush.copy(alpha = 0.0f)
                            ),
                            center = Offset(w * 0.355f, h * 0.538f),
                            radius = w * 0.045f
                        ),
                        topLeft = Offset(w * 0.31f, h * 0.512f),
                        size = Size(w * 0.09f, h * 0.052f)
                    )
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ResolveColors.CloudBlush.copy(alpha = 0.42f),
                                ResolveColors.CloudBlush.copy(alpha = 0.0f)
                            ),
                            center = Offset(w * 0.645f, h * 0.538f),
                            radius = w * 0.045f
                        ),
                        topLeft = Offset(w * 0.60f, h * 0.512f),
                        size = Size(w * 0.09f, h * 0.052f)
                    )

                    // Gentle serene smile
                    val smilePath = Path().apply {
                        moveTo(w * 0.468f, h * 0.536f)
                        quadraticTo(w * 0.50f, h * 0.566f, w * 0.532f, h * 0.536f)
                    }
                    drawPath(
                        path = smilePath,
                        color = ResolveColors.ForestDeep.copy(alpha = 0.88f),
                        style = Stroke(width = w * 0.0135f, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawCloudEye(
    centerX: Float,
    centerY: Float,
    eyeWidth: Float,
    eyeHeight: Float,
    isOpen: Boolean
) {
    drawOval(
        color = ResolveColors.ForestDeep,
        topLeft = Offset(centerX - eyeWidth / 2f, centerY - eyeHeight / 2f),
        size = Size(eyeWidth, eyeHeight)
    )
    if (isOpen) {
        // Primary top-left specular catchlight
        val catchSize = eyeWidth * 0.34f
        drawCircle(
            color = Color.White.copy(alpha = 0.92f),
            radius = catchSize / 2f,
            center = Offset(centerX - eyeWidth * 0.16f, centerY - eyeHeight * 0.18f)
        )
        // Secondary subtle bottom-right glint
        drawCircle(
            color = ResolveColors.MintBright.copy(alpha = 0.55f),
            radius = catchSize * 0.28f,
            center = Offset(centerX + eyeWidth * 0.18f, centerY + eyeHeight * 0.18f)
        )
    }
}

private fun DrawScope.drawStarlightSparkle(
    center: Offset,
    radius: Float,
    alpha: Float
) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(
        path = path,
        color = ResolveColors.MintBright.copy(alpha = alpha.coerceIn(0f, 1f))
    )
}
