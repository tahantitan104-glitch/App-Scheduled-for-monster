package com.example.presentation.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.designsystem.ResolveColors
import com.example.domain.model.MascotAnimationState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private class CloudPathCache {
    val bodyPath = Path()
    val highlightArcPath = Path()
    val eyeLidPath = Path()
    val browPath = Path()
    val mouthPath = Path()
    val sparklePath = Path()
}

/**
 * Redesigned organic 2.5D Baby Cloud focus mascot:
 * - Cohesive sculpted multi-billow silhouette with gentle soft undulating bottom curves
 *   and pearlescent subsurface volume (replacing rigid pill geometry).
 * - Floating wispy mini-puff tufts alongside Baby Cloud that lift/wave during Pointing
 *   and Celebrating for lifelike animated personality matching RESOLVE Bunny.
 * - Expressive glossy eyes with soft curved brow wisps, dual catchlights, real closing lids,
 *   diffused rosy blush, and adaptive expression moods.
 */
@Composable
fun BabyCloudMascot(
    modifier: Modifier = Modifier,
    size: Dp = 190.dp,
    showFocusAura: Boolean = true,
    animationState: MascotAnimationState = MascotAnimationState.IDLE,
    isBlinkingExternal: Boolean? = null,
    reducedMotion: Boolean = false,
    isPaused: Boolean = false
) {
    val animateMotion = !reducedMotion && !isPaused
    val paths = remember { CloudPathCache() }
    val transition = rememberInfiniteTransition(label = "baby_cloud_sculpted")

    val rawFloatOffsetY by transition.animateFloat(
        initialValue = -6.5f,
        targetValue = 6.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cloud_float_y"
    )
    val rawCelebrationWave by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cloud_active_wave"
    )
    val activeWave = if (animateMotion) sin(rawCelebrationWave) else 0f

    val floatOffsetY = when {
        !animateMotion -> 0f
        animationState == MascotAnimationState.CELEBRATING -> -7f - abs(activeWave) * 7f
        animationState == MascotAnimationState.HAPPY_BOUNCE -> -abs(activeWave) * 15f
        animationState == MascotAnimationState.GENTLE_FLOAT -> -9f + sin(rawCelebrationWave * 0.5f) * 5.5f
        animationState == MascotAnimationState.HAPPY_DANCE -> rawFloatOffsetY * 1.25f - 4f
        animationState == MascotAnimationState.POINTING -> -4.5f + rawFloatOffsetY * 0.4f
        else -> rawFloatOffsetY
    }

    val rawSwayOffsetX by transition.animateFloat(
        initialValue = -3.0f,
        targetValue = 3.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cloud_sway_x"
    )
    val swayOffsetX = when {
        !animateMotion -> 0f
        animationState == MascotAnimationState.CURIOUS_SWAY -> activeWave * 11f
        else -> rawSwayOffsetX
    }

    val rawGentleTilt by transition.animateFloat(
        initialValue = -1.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cloud_tilt"
    )
    val attentionTilt by animateFloatAsState(
        targetValue = when {
            !animateMotion -> 0f
            animationState == MascotAnimationState.HEAD_EAR_ATTENTION -> -4.5f
            animationState == MascotAnimationState.POINTING -> -5.5f
            animationState == MascotAnimationState.CURIOUS -> 6.5f
            animationState == MascotAnimationState.CURIOUS_SWAY -> activeWave * 9.5f
            animationState == MascotAnimationState.CELEBRATING -> activeWave * 4.5f
            else -> 0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cloud_attention_tilt"
    )
    val gentleTilt = (if (animateMotion) rawGentleTilt else 0f) + attentionTilt

    val rawBreatheScale by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.024f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cloud_breathe"
    )
    val celebrateScale by animateFloatAsState(
        targetValue = when {
            reducedMotion -> 1f
            animationState == MascotAnimationState.CELEBRATING -> 1.05f
            animationState == MascotAnimationState.GENTLE_FLOAT -> 1.04f + 0.03f * sin(rawCelebrationWave * 0.5f)
            else -> 1f
        },
        animationSpec = tween(durationMillis = 300, easing = EaseInOutSine),
        label = "cloud_celebrate_scale"
    )
    val breatheScale = (if (animateMotion) rawBreatheScale else 1f) * celebrateScale

    // Squash-and-stretch deformation exclusively for HAPPY_BOUNCE
    val bounceStretchY = if (animateMotion && animationState == MascotAnimationState.HAPPY_BOUNCE) {
        val jumpFactor = abs(activeWave) // 0 at ground impact, 1 at peak
        0.90f + jumpFactor * 0.22f
    } else 1f
    val bounceSquashX = if (animateMotion && animationState == MascotAnimationState.HAPPY_BOUNCE) {
        1f / bounceStretchY
    } else 1f

    val tuftLift by animateFloatAsState(
        targetValue = when {
            !animateMotion && animationState == MascotAnimationState.POINTING -> 0.7f
            !animateMotion -> 0f
            animationState == MascotAnimationState.POINTING -> 0.92f
            animationState == MascotAnimationState.CELEBRATING -> 0.85f + 0.15f * activeWave
            animationState == MascotAnimationState.HAPPY_BOUNCE -> 0.45f + 0.40f * abs(activeWave)
            animationState == MascotAnimationState.CURIOUS_SWAY -> 0.35f + 0.25f * activeWave
            animationState == MascotAnimationState.GENTLE_FLOAT -> 0.30f
            animationState == MascotAnimationState.HAPPY_DANCE -> 0.65f + 0.25f * activeWave
            else -> 0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cloud_tuft_lift"
    )

    val rawAuraPulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cloud_aura_pulse"
    )
    val auraPulse = when {
        !animateMotion -> 1f
        animationState == MascotAnimationState.GENTLE_FLOAT -> rawAuraPulse * 1.12f
        else -> rawAuraPulse
    }

    val rawSparklePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cloud_sparkle_phase"
    )
    val sparklePhase = if (animateMotion) rawSparklePhase else 0.75f

    val internalBlinkFraction by transition.animateFloat(
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
        label = "cloud_double_blink"
    )
    val animatedExternalEye by animateFloatAsState(
        targetValue = if (isBlinkingExternal == true || animationState == MascotAnimationState.BLINK) 0.08f else 1f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 90),
        label = "cloud_external_blink"
    )
    val eyeOpenFraction = when {
        reducedMotion -> 1f
        isBlinkingExternal != null || animationState == MascotAnimationState.BLINK -> animatedExternalEye
        !animateMotion -> 1f
        animationState == MascotAnimationState.GENTLE_FLOAT -> 0.72f
        else -> internalBlinkFraction
    }

    Canvas(
        modifier = modifier
            .size(size)
            .testTag("mascot_baby_cloud")
            .semantics { contentDescription = "Baby Cloud focus mascot" }
    ) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h * 0.50f)

        if (showFocusAura) {
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
            drawCircle(
                color = ResolveColors.MintAccent.copy(alpha = 0.13f),
                radius = w * 0.415f,
                center = center,
                style = Stroke(width = w * 0.006f)
            )
            drawStarlightSparkle(
                path = paths.sparklePath,
                center = Offset(
                    w * 0.17f + cos(sparklePhase) * w * 0.015f,
                    h * 0.27f + sin(sparklePhase) * h * 0.02f
                ),
                radius = w * 0.022f,
                alpha = 0.35f + 0.35f * sin(sparklePhase * 2f).coerceIn(0f, 1f)
            )
            drawStarlightSparkle(
                path = paths.sparklePath,
                center = Offset(
                    w * 0.84f + sin(sparklePhase) * w * 0.015f,
                    h * 0.25f + cos(sparklePhase) * h * 0.018f
                ),
                radius = w * 0.019f,
                alpha = 0.30f + 0.40f * cos(sparklePhase * 2f).coerceIn(0f, 1f)
            )
        }

        // Ground shadow reacting softly to floating bob height
        val floatFraction = ((floatOffsetY + 6.5f) / 13f).coerceIn(0f, 1f)
        val shadowScale = 0.85f + floatFraction * 0.16f
        val shadowAlpha = 0.18f + floatFraction * 0.14f
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = shadowAlpha), Color.Transparent),
                center = Offset(w * 0.50f, h * 0.80f),
                radius = w * 0.30f * shadowScale
            ),
            topLeft = Offset(w * 0.50f - w * 0.26f * shadowScale, h * 0.76f),
            size = Size(w * 0.52f * shadowScale, h * 0.08f)
        )

        translate(left = swayOffsetX * density, top = floatOffsetY * density) {
            rotate(degrees = gentleTilt, pivot = center) {
                scale(
                    scaleX = breatheScale * bounceSquashX,
                    scaleY = breatheScale * bounceStretchY,
                    pivot = Offset(w * 0.50f, h * 0.69f)
                ) {
                    // Subtle floating micro-puff wisp behind upper-right
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ResolveColors.CloudHighlight.copy(alpha = 0.85f),
                                ResolveColors.CloudBodyMid.copy(alpha = 0.55f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.80f, h * 0.34f - tuftLift * h * 0.03f),
                            radius = w * 0.055f
                        ),
                        radius = w * 0.048f,
                        center = Offset(w * 0.80f, h * 0.34f - tuftLift * h * 0.03f)
                    )

                    // Sculpted organic billowing cloud silhouette
                    val bodyBrush = Brush.verticalGradient(
                        colors = listOf(
                            ResolveColors.CloudHighlight,
                            ResolveColors.CloudBodyTop,
                            ResolveColors.CloudBodyMid,
                            ResolveColors.CloudBodyShadow
                        ),
                        startY = h * 0.21f,
                        endY = h * 0.695f
                    )

                    // Main sculpted cloud contour built with continuous billowing lobes and gentle bottom curves
                    paths.bodyPath.reset()
                    // Start left underside shoulder
                    paths.bodyPath.moveTo(w * 0.205f, h * 0.628f)
                    // Left round billowing lobe rising
                    paths.bodyPath.cubicTo(
                        w * 0.115f, h * 0.595f,
                        w * 0.118f, h * 0.445f,
                        w * 0.238f, h * 0.415f
                    )
                    // Upper-left secondary puff
                    paths.bodyPath.cubicTo(
                        w * 0.242f, h * 0.315f,
                        w * 0.348f, h * 0.275f,
                        w * 0.415f, h * 0.310f
                    )
                    // High plush crown puff
                    paths.bodyPath.cubicTo(
                        w * 0.455f, h * 0.210f,
                        w * 0.618f, h * 0.218f,
                        w * 0.652f, h * 0.335f
                    )
                    // Upper-right rounded billow
                    paths.bodyPath.cubicTo(
                        w * 0.742f, h * 0.320f,
                        w * 0.830f, h * 0.390f,
                        w * 0.808f, h * 0.482f
                    )
                    // Right billowing flank descending into curved base
                    paths.bodyPath.cubicTo(
                        w * 0.885f, h * 0.525f,
                        w * 0.865f, h * 0.655f,
                        w * 0.752f, h * 0.665f
                    )
                    // Natural pillowy 2-wave cloud underside (right -> center -> left)
                    paths.bodyPath.cubicTo(
                        w * 0.645f, h * 0.692f,
                        w * 0.555f, h * 0.690f,
                        w * 0.495f, h * 0.672f
                    )
                    paths.bodyPath.cubicTo(
                        w * 0.410f, h * 0.695f,
                        w * 0.295f, h * 0.680f,
                        w * 0.205f, h * 0.628f
                    )
                    paths.bodyPath.close()

                    // Soft mint underglow below cloud
                    drawOval(
                        color = ResolveColors.MintAccent.copy(alpha = 0.14f),
                        topLeft = Offset(w * 0.20f, h * 0.58f),
                        size = Size(w * 0.60f, h * 0.12f)
                    )

                    drawPath(
                        path = paths.bodyPath,
                        brush = bodyBrush
                    )

                    // Volumetric upper crest specular highlights
                    drawArc(
                        color = Color.White.copy(alpha = 0.82f),
                        startAngle = 205f,
                        sweepAngle = 105f,
                        useCenter = false,
                        topLeft = Offset(w * 0.42f, h * 0.245f),
                        size = Size(w * 0.22f, h * 0.18f),
                        style = Stroke(width = w * 0.015f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color.White.copy(alpha = 0.64f),
                        startAngle = 195f,
                        sweepAngle = 92f,
                        useCenter = false,
                        topLeft = Offset(w * 0.155f, h * 0.435f),
                        size = Size(w * 0.15f, h * 0.14f),
                        style = Stroke(width = w * 0.012f, cap = StrokeCap.Round)
                    )

                    // Soft underside misty shading
                    drawOval(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, ResolveColors.SageAccent.copy(alpha = 0.18f)),
                            startY = h * 0.57f,
                            endY = h * 0.69f
                        ),
                        topLeft = Offset(w * 0.22f, h * 0.56f),
                        size = Size(w * 0.56f, h * 0.13f)
                    )

                    // Cute floating cloud-tuft little paws beside sides
                    val leftTuftY = h * (0.585f - 0.055f * (tuftLift * 0.4f))
                    val rightTuftY = h * (0.580f - 0.115f * tuftLift)
                    val rightTuftX = w * (0.835f + 0.028f * tuftLift)
                    drawCloudTuftPaw(center = Offset(w * 0.165f, leftTuftY), radius = w * 0.044f)
                    drawCloudTuftPaw(center = Offset(rightTuftX, rightTuftY), radius = w * 0.046f)

                    // Gaze subtly shifts toward lifted puff when pointing/curious
                    val gazeShiftX = when (animationState) {
                        MascotAnimationState.CURIOUS -> -w * 0.004f
                        MascotAnimationState.CURIOUS_SWAY -> activeWave * w * 0.006f
                        MascotAnimationState.POINTING -> w * 0.004f
                        else -> 0f
                    }
                    val gazeShiftY = if (animationState == MascotAnimationState.POINTING) -h * 0.004f else 0f

                    // Delicate soft eyebrow wisps for gentle animated character warmth
                    val eyeY = h * 0.482f
                    val leftEyeX = w * 0.415f
                    val rightEyeX = w * 0.585f
                    val browLift = if (animationState == MascotAnimationState.CELEBRATING ||
                        animationState == MascotAnimationState.CURIOUS ||
                        animationState == MascotAnimationState.CURIOUS_SWAY ||
                        animationState == MascotAnimationState.HAPPY_BOUNCE
                    ) -h * 0.010f else 0f

                    drawCloudBrow(paths.browPath, Offset(leftEyeX, eyeY - h * 0.048f + browLift), w * 0.042f)
                    drawCloudBrow(paths.browPath, Offset(rightEyeX, eyeY - h * 0.048f + browLift), w * 0.042f)

                    // Expressive glossy eyes with layered iris & eyelids
                    val eyeWidth = w * 0.042f
                    val eyeHeight = (w * 0.056f * eyeOpenFraction).coerceAtLeast(2.4f)
                    drawCloudEye(
                        paths = paths,
                        center = Offset(leftEyeX, eyeY),
                        eyeWidth = eyeWidth,
                        eyeHeight = eyeHeight,
                        openFraction = eyeOpenFraction,
                        gazeX = gazeShiftX,
                        gazeY = gazeShiftY
                    )
                    drawCloudEye(
                        paths = paths,
                        center = Offset(rightEyeX, eyeY),
                        eyeWidth = eyeWidth,
                        eyeHeight = eyeHeight,
                        openFraction = eyeOpenFraction,
                        gazeX = gazeShiftX,
                        gazeY = gazeShiftY
                    )

                    // Soft warm diffused cheeks
                    drawCloudBlush(Offset(w * 0.342f, h * 0.532f), w * 0.042f, h * 0.026f)
                    drawCloudBlush(Offset(w * 0.658f, h * 0.532f), w * 0.042f, h * 0.026f)

                    // Cute adaptive smile matching animation mood
                    drawCloudMouth(
                        path = paths.mouthPath,
                        centerX = w * 0.50f,
                        mouthY = h * 0.534f,
                        w = w,
                        state = animationState
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawCloudTuftPaw(
    center: Offset,
    radius: Float
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                ResolveColors.CloudHighlight,
                ResolveColors.CloudBodyTop,
                ResolveColors.CloudBodyShadow
            ),
            center = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f),
            radius = radius * 1.35f
        ),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawCloudBrow(
    path: Path,
    center: Offset,
    width: Float
) {
    path.reset()
    path.moveTo(center.x - width * 0.5f, center.y + width * 0.14f)
    path.quadraticTo(center.x, center.y - width * 0.18f, center.x + width * 0.5f, center.y + width * 0.14f)
    drawPath(
        path = path,
        color = ResolveColors.ForestDeep.copy(alpha = 0.42f),
        style = Stroke(width = width * 0.14f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawCloudBlush(
    center: Offset,
    rx: Float,
    ry: Float
) {
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                ResolveColors.CloudBlush.copy(alpha = 0.46f),
                ResolveColors.CloudBlush.copy(alpha = 0f)
            ),
            center = center,
            radius = rx
        ),
        topLeft = Offset(center.x - rx, center.y - ry),
        size = Size(rx * 2f, ry * 2f)
    )
}

private fun DrawScope.drawCloudEye(
    paths: CloudPathCache,
    center: Offset,
    eyeWidth: Float,
    eyeHeight: Float,
    openFraction: Float,
    gazeX: Float,
    gazeY: Float
) {
    if (openFraction <= 0.18f) {
        paths.eyeLidPath.reset()
        paths.eyeLidPath.moveTo(center.x - eyeWidth * 0.55f, center.y + eyeHeight * 0.12f)
        paths.eyeLidPath.quadraticTo(
            center.x,
            center.y + eyeWidth * 0.34f,
            center.x + eyeWidth * 0.55f,
            center.y + eyeHeight * 0.12f
        )
        drawPath(
            path = paths.eyeLidPath,
            color = ResolveColors.ForestDeep,
            style = Stroke(width = eyeWidth * 0.22f, cap = StrokeCap.Round)
        )
        return
    }

    val eyeRect = Rect(
        left = center.x - eyeWidth / 2f,
        top = center.y - eyeHeight / 2f,
        right = center.x + eyeWidth / 2f,
        bottom = center.y + eyeHeight / 2f
    )

    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF0A1B16),
                ResolveColors.ForestDeep,
                Color(0xFF214A3E)
            ),
            center = Offset(center.x + gazeX, center.y + gazeY - eyeHeight * 0.12f),
            radius = eyeHeight * 0.72f
        ),
        topLeft = eyeRect.topLeft,
        size = eyeRect.size
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.95f),
        radius = eyeWidth * 0.20f,
        center = Offset(center.x - eyeWidth * 0.16f + gazeX * 0.7f, center.y - eyeHeight * 0.19f + gazeY * 0.7f)
    )
    drawCircle(
        color = ResolveColors.MintBright.copy(alpha = 0.65f),
        radius = eyeWidth * 0.095f,
        center = Offset(center.x + eyeWidth * 0.18f + gazeX * 0.7f, center.y + eyeHeight * 0.18f + gazeY * 0.7f)
    )

    if (openFraction < 0.97f) {
        val lidHeight = eyeHeight * (1f - openFraction).coerceIn(0f, 1f) * 0.94f
        drawOval(
            color = ResolveColors.CloudBodyTop,
            topLeft = Offset(eyeRect.left - eyeWidth * 0.12f, eyeRect.top - eyeHeight * 0.12f),
            size = Size(eyeWidth * 1.24f, lidHeight + eyeHeight * 0.14f)
        )
        drawLine(
            color = ResolveColors.ForestDeep,
            start = Offset(eyeRect.left - eyeWidth * 0.05f, eyeRect.top + lidHeight),
            end = Offset(eyeRect.right + eyeWidth * 0.05f, eyeRect.top + lidHeight),
            strokeWidth = eyeWidth * 0.14f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawCloudMouth(
    path: Path,
    centerX: Float,
    mouthY: Float,
    w: Float,
    state: MascotAnimationState
) {
    val span = w * 0.032f
    val strokeW = w * 0.0115f
    path.reset()

    when (state) {
        MascotAnimationState.CELEBRATING,
        MascotAnimationState.HAPPY_DANCE -> {
            // Joyful warm cloud smile with soft coral inner tint
            path.moveTo(centerX - span * 0.95f, mouthY - w * 0.002f)
            path.quadraticTo(centerX, mouthY + w * 0.034f, centerX + span * 0.95f, mouthY - w * 0.002f)
            drawPath(
                path = path,
                color = ResolveColors.ForestDeep.copy(alpha = 0.90f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawOval(
                color = ResolveColors.CloudBlush.copy(alpha = 0.85f),
                topLeft = Offset(centerX - span * 0.36f, mouthY + w * 0.004f),
                size = Size(span * 0.72f, w * 0.015f)
            )
        }
        MascotAnimationState.CURIOUS -> {
            drawOval(
                color = ResolveColors.ForestDeep.copy(alpha = 0.88f),
                topLeft = Offset(centerX - span * 0.28f, mouthY - w * 0.003f),
                size = Size(span * 0.56f, w * 0.018f),
                style = Stroke(width = strokeW * 0.88f)
            )
        }
        else -> {
            // Cute gentle double-curve 'w' smile echoing companion charm
            path.moveTo(centerX - span * 0.88f, mouthY - w * 0.002f)
            path.quadraticTo(centerX - span * 0.42f, mouthY + w * 0.020f, centerX, mouthY + w * 0.002f)
            path.quadraticTo(centerX + span * 0.42f, mouthY + w * 0.020f, centerX + span * 0.88f, mouthY - w * 0.002f)
            drawPath(
                path = path,
                color = ResolveColors.ForestDeep.copy(alpha = 0.88f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

private fun DrawScope.drawStarlightSparkle(
    path: Path,
    center: Offset,
    radius: Float,
    alpha: Float
) {
    path.reset()
    path.moveTo(center.x, center.y - radius)
    path.quadraticTo(center.x, center.y, center.x + radius, center.y)
    path.quadraticTo(center.x, center.y, center.x, center.y + radius)
    path.quadraticTo(center.x, center.y, center.x - radius, center.y)
    path.quadraticTo(center.x, center.y, center.x, center.y - radius)
    path.close()
    drawPath(
        path = path,
        color = ResolveColors.MintBright.copy(alpha = alpha.coerceIn(0f, 1f))
    )
}
