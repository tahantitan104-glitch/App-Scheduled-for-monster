package com.example.presentation.components.bunny

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.MascotAnimationState
import com.example.domain.model.MascotMicroAnimation
import com.example.presentation.components.bunny.BunnyMasterSpec.CENTER_X
import com.example.presentation.components.bunny.BunnyMasterSpec.HEAD_CENTER_Y
import com.example.presentation.components.bunny.BunnyMasterSpec.MintAccent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Programmatic 2.5D hybrid RESOLVE Bunny composable.
 *
 * Uses zero external images/SVGs/PNGs/Lottie files and enforces Master Character Consistency
 * across all states:
 * - 7 Core Animations (`IDLE`, `BLINK`, `POINTING`, `CURIOUS`, `TURNING`, `HAPPY_DANCE`, `CELEBRATING`)
 * - 8-Stage Signature Peek-a-Boo Entrance (`PEEK_ENTRANCE` with independent `#10251F` curved wall)
 * - Micro-Animations (`EAR_WIGGLE`, `SMALL_HOP`, `GENTLE_WAVE`, `SHY`, `SLEEPY`)
 *
 * Respects [reducedMotion] and [isPaused] smoothly with calm static fallback.
 */
@Composable
fun ResolveBunnyMascot(
    modifier: Modifier = Modifier,
    size: Dp = 190.dp,
    showFocusAura: Boolean = true,
    animationState: MascotAnimationState = MascotAnimationState.IDLE,
    microAnimation: MascotMicroAnimation = MascotMicroAnimation.NONE,
    isBlinkingExternal: Boolean? = null,
    peekProgress: Float = 1f,
    showWallOverride: Boolean = false,
    reducedMotion: Boolean = false,
    isPaused: Boolean = false
) {
    val animateMotion = !reducedMotion && !isPaused
    val paths = remember { BunnyPathCache() }
    val transition = rememberInfiniteTransition(label = "resolve_bunny_ambient")

    // Subtle organic breathing & ear breeze cycle
    val rawBreathPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bunny_breath"
    )
    val breathSin = if (animateMotion) sin(rawBreathPhase) else 0f

    // Rhythmic hop / wiggle wave for HAPPY_DANCE, CELEBRATING, SMALL_HOP, EAR_WIGGLE, GENTLE_WAVE
    val rawActivePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bunny_active_wave"
    )
    val activeWave = if (animateMotion) sin(rawActivePhase) else 0f
    val activeCos = if (animateMotion) cos(rawActivePhase) else 0f

    // Internal natural blink fallback when no external controller drives blinking
    val internalBlink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5400
                1f at 0
                1f at 4200
                0.06f at 4310
                1f at 4440
                1f at 4620
                0.06f at 4720
                1f at 4850
                1f at 5400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "bunny_blink"
    )
    val externalEyeOpen by animateFloatAsState(
        targetValue = when {
            isBlinkingExternal == true || animationState == MascotAnimationState.BLINK -> 0.06f
            microAnimation == MascotMicroAnimation.SLEEPY -> 0.25f
            else -> 1f
        },
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 95),
        label = "bunny_external_blink"
    )

    val eyeOpen = when {
        reducedMotion && microAnimation != MascotMicroAnimation.SLEEPY -> 1f
        isBlinkingExternal != null || animationState == MascotAnimationState.BLINK ||
            microAnimation == MascotMicroAnimation.SLEEPY -> externalEyeOpen
        !animateMotion -> 1f
        else -> internalBlink
    }

    // Smoothly spring-animated state targets for distinct personality poses
    val headTiltTarget by animateFloatAsState(
        targetValue = when {
            !animateMotion -> 0f
            animationState == MascotAnimationState.CURIOUS -> 11f
            animationState == MascotAnimationState.HEAD_EAR_ATTENTION -> -6.5f
            animationState == MascotAnimationState.POINTING -> -4.5f
            animationState == MascotAnimationState.HAPPY_DANCE -> activeWave * 5.5f
            animationState == MascotAnimationState.CELEBRATING -> activeWave * 6.5f
            microAnimation == MascotMicroAnimation.SHY -> -7f
            microAnimation == MascotMicroAnimation.SLEEPY -> 5f
            else -> breathSin * 1.4f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "bunny_head_tilt"
    )

    val yawTarget by animateFloatAsState(
        targetValue = when {
            !animateMotion -> 0f
            animationState == MascotAnimationState.TURNING -> 0.58f * activeWave
            animationState == MascotAnimationState.CURIOUS -> -0.26f
            animationState == MascotAnimationState.POINTING -> 0.14f
            microAnimation == MascotMicroAnimation.SHY -> -0.34f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 360, easing = EaseInOutSine),
        label = "bunny_yaw"
    )

    val rightPawRaiseTarget by animateFloatAsState(
        targetValue = when {
            !animateMotion && animationState == MascotAnimationState.POINTING -> 0.85f
            !animateMotion -> 0f
            animationState == MascotAnimationState.POINTING -> 0.96f
            animationState == MascotAnimationState.CELEBRATING -> 0.72f + 0.18f * activeWave
            animationState == MascotAnimationState.HAPPY_DANCE -> 0.56f + 0.24f * activeWave
            microAnimation == MascotMicroAnimation.GENTLE_WAVE -> 0.78f + 0.16f * activeWave
            microAnimation == MascotMicroAnimation.SHY -> 0.36f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "bunny_right_paw"
    )

    val leftPawRaiseTarget by animateFloatAsState(
        targetValue = when {
            !animateMotion -> 0f
            animationState == MascotAnimationState.CELEBRATING -> 0.68f - 0.18f * activeWave
            animationState == MascotAnimationState.HAPPY_DANCE -> 0.50f - 0.22f * activeWave
            microAnimation == MascotMicroAnimation.SHY -> 0.36f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "bunny_left_paw"
    )

    val isPeekEntrance = animationState == MascotAnimationState.PEEK_ENTRANCE && !reducedMotion

    val resolvedPose = remember(
        animationState,
        microAnimation,
        peekProgress,
        breathSin,
        activeWave,
        activeCos,
        eyeOpen,
        headTiltTarget,
        yawTarget,
        rightPawRaiseTarget,
        leftPawRaiseTarget,
        showWallOverride,
        animateMotion
    ) {
        if (isPeekEntrance) {
            resolvePeekEntrancePose(peekProgress)
        } else {
            val bounceY = when {
                !animateMotion -> 0f
                animationState == MascotAnimationState.CELEBRATING -> -kotlin.math.abs(activeWave) * 0.055f
                animationState == MascotAnimationState.HAPPY_DANCE -> -kotlin.math.abs(activeWave) * 0.048f
                microAnimation == MascotMicroAnimation.SMALL_HOP -> -kotlin.math.abs(activeWave) * 0.042f
                else -> breathSin * 0.006f
            }
            val scaleStretchY = when {
                !animateMotion -> 1f
                animationState == MascotAnimationState.CELEBRATING ||
                    animationState == MascotAnimationState.HAPPY_DANCE ||
                    microAnimation == MascotMicroAnimation.SMALL_HOP -> 1f + (-bounceY * 1.1f)
                else -> 1f + breathSin * 0.015f
            }
            val scaleSquashX = 1f / scaleStretchY

            val earWiggleBoost = when {
                !animateMotion -> 0f
                microAnimation == MascotMicroAnimation.EAR_WIGGLE -> activeWave * 11f
                animationState == MascotAnimationState.HEAD_EAR_ATTENTION -> 7f + activeWave * 3f
                animationState == MascotAnimationState.CURIOUS -> -5f
                animationState == MascotAnimationState.CELEBRATING ||
                    animationState == MascotAnimationState.HAPPY_DANCE -> activeWave * 8f
                else -> breathSin * 2.2f
            }

            val bentEarDynamic = when {
                !animateMotion -> 0f
                microAnimation == MascotMicroAnimation.EAR_WIGGLE -> -activeWave * 9f
                animationState == MascotAnimationState.CURIOUS -> 8f
                animationState == MascotAnimationState.CELEBRATING ||
                    animationState == MascotAnimationState.HAPPY_DANCE -> -activeWave * 7f
                else -> -breathSin * 1.8f
            }

            val mood = when {
                microAnimation == MascotMicroAnimation.SLEEPY -> BunnyMouthMood.SLEEPY
                microAnimation == MascotMicroAnimation.SHY -> BunnyMouthMood.SHY
                animationState == MascotAnimationState.CURIOUS -> BunnyMouthMood.CURIOUS
                animationState == MascotAnimationState.CELEBRATING ||
                    animationState == MascotAnimationState.HAPPY_DANCE -> BunnyMouthMood.HAPPY
                else -> BunnyMouthMood.SERENE
            }

            val auraBoost = when {
                !animateMotion -> 0f
                animationState == MascotAnimationState.CELEBRATING -> 0.9f
                animationState == MascotAnimationState.POINTING -> 0.55f
                else -> 0.25f + 0.15f * breathSin
            }

            BunnyPose(
                offsetXFraction = if (animationState == MascotAnimationState.HAPPY_DANCE && animateMotion) activeCos * 0.016f else 0f,
                offsetYFraction = bounceY,
                bodyScaleX = scaleSquashX,
                bodyScaleY = scaleStretchY,
                headTiltDeg = headTiltTarget,
                turnYawFraction = yawTarget,
                uprightEarDeg = earWiggleBoost,
                bentEarDeg = bentEarDynamic,
                bentEarTipCurl = 1f + if (animateMotion) 0.05f * activeWave else 0f,
                eyeOpenFraction = eyeOpen,
                gazeOffsetX = when (animationState) {
                    MascotAnimationState.CURIOUS -> -0.005f
                    MascotAnimationState.TURNING -> yawTarget * 0.010f
                    else -> 0f
                },
                gazeOffsetY = when (animationState) {
                    MascotAnimationState.POINTING -> -0.005f
                    MascotAnimationState.CURIOUS -> -0.002f
                    else -> 0f
                },
                mouthMood = mood,
                cheekAlpha = if (microAnimation == MascotMicroAnimation.SHY) 0.66f else 0.48f,
                leftPawRaise = leftPawRaiseTarget,
                rightPawRaise = rightPawRaiseTarget,
                rightPawPointAngleDeg = if (animationState == MascotAnimationState.POINTING) -10f else activeWave * 8f,
                pawsOnWallStrength = 0f,
                wallMode = if (showWallOverride) BunnyWallMode.BESIDE_WALL else BunnyWallMode.NONE,
                wallAlpha = if (showWallOverride) 1f else 0f,
                wallOffsetXFraction = if (showWallOverride) -0.30f else 0f,
                auraBoost = auraBoost
            )
        }
    }

    Canvas(
        modifier = modifier
            .size(size)
            .testTag("mascot_resolve_bunny")
            .semantics {
                contentDescription = "RESOLVE Bunny focus mascot"
            }
    ) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w * CENTER_X, h * 0.50f)

        // Atmospheric #75DDB5 focus aura behind Bunny (optional, crisp at both hero and badge scales)
        if (showFocusAura) {
            val auraScale = 0.94f + 0.08f * breathSin + resolvedPose.auraBoost * 0.08f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MintAccent.copy(alpha = (0.20f + resolvedPose.auraBoost * 0.09f).coerceIn(0.10f, 0.34f)),
                        MintAccent.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = w * 0.50f * auraScale
                ),
                radius = w * 0.50f * auraScale,
                center = center
            )

            drawCircle(
                color = MintAccent.copy(alpha = 0.14f),
                radius = w * 0.42f,
                center = center,
                style = Stroke(width = w * 0.006f)
            )

            // Subtle mint starlight sparkles during POINTING or CELEBRATING
            if (animationState == MascotAnimationState.CELEBRATING ||
                animationState == MascotAnimationState.POINTING ||
                animationState == MascotAnimationState.HAPPY_DANCE
            ) {
                val sparkAlpha = if (animateMotion) 0.45f + 0.40f * kotlin.math.abs(activeWave) else 0.55f
                drawMintSparkleAccent(
                    paths = paths,
                    center = Offset(w * 0.18f, h * 0.25f + breathSin * h * 0.015f),
                    radius = w * 0.025f,
                    alpha = sparkAlpha
                )
                drawMintSparkleAccent(
                    paths = paths,
                    center = Offset(w * 0.83f, h * 0.22f - breathSin * h * 0.015f),
                    radius = w * 0.022f,
                    alpha = sparkAlpha
                )
            }
        }

        // Render order supports both standalone Bunny and the 8-stage Peek-a-Boo Entrance with Wall:
        // Case A: Bunny is behind curved wall (Stage 0..5 of Peek-a-Boo Entrance)
        if (resolvedPose.wallMode == BunnyWallMode.PEEK_BEHIND && resolvedPose.wallAlpha > 0.02f) {
            // Draw forest wall first as background/ledge plane
            drawCurvedForestWall(
                paths = paths,
                alpha = resolvedPose.wallAlpha,
                offsetXFraction = resolvedPose.wallOffsetXFraction
            )
            // Clip Bunny strictly above the curved crest so body stays concealed behind wall
            clipAboveCurvedWall(
                paths = paths,
                wallOffsetXFraction = resolvedPose.wallOffsetXFraction
            ) {
                drawBunnyCharacter(paths = paths, pose = resolvedPose, drawShadow = false)
            }
            // Paws resting ON TOP of wall ledge draw cleanly in front of the wall crest
            drawWallRestingPaws(pose = resolvedPose, alpha = resolvedPose.wallAlpha)
        } else {
            // Case B: Bunny beside curved wall (Stage 6..7) OR standalone Bunny (IDLE/Lock/Home/Settings)
            if (resolvedPose.wallAlpha > 0.02f) {
                drawCurvedForestWall(
                    paths = paths,
                    alpha = resolvedPose.wallAlpha,
                    offsetXFraction = resolvedPose.wallOffsetXFraction
                )
            }
            drawBunnyCharacter(paths = paths, pose = resolvedPose, drawShadow = true)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBunnyCharacter(
    paths: BunnyPathCache,
    pose: BunnyPose,
    drawShadow: Boolean
) {
    val w = size.width
    val h = size.height

    if (drawShadow) {
        drawBunnyShadow(pose)
    }

    translate(left = pose.offsetXFraction * w, top = pose.offsetYFraction * h) {
        val bodyAnchor = Offset(w * CENTER_X, h * BunnyMasterSpec.GROUND_Y)
        scale(scaleX = pose.bodyScaleX, scaleY = pose.bodyScaleY, pivot = bodyAnchor) {
            drawBunnyBodyAndFeet(paths, pose)

            val headPivot = Offset(w * CENTER_X, h * HEAD_CENTER_Y)
            rotate(degrees = pose.headTiltDeg, pivot = headPivot) {
                drawBunnyEars(paths, pose)
                drawBunnyHeadAndFace(paths, pose)
            }

            drawBunnyPaws(pose)
        }
    }
}
