package com.example.presentation.components.bunny

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.sin

/**
 * Master Character Consistency Specification for RESOLVE Bunny.
 *
 * Proportions follow graceful animated-cinema stylized rabbit anatomy:
 * - Slim, taller vertical silhouette (slender chest tapering to rounded rabbit hips & tucked
 *   hind haunches — never a squat round ball/ragdoll).
 * - Dominant vertical rabbit ears (~32% of stage height, slender and gently tapered) anchored
 *   closely on top of the poised oval head crown; right ear keeps a signature soft upper fold.
 * - Balanced oval rabbit head with gentle cheek contour, expressive tall-oval cocoa eyes,
 *   delicate brow arcs, tiny pastel-pink nose, subtle cream muzzle puffs, and refined Y-mouth.
 * - Slender articulated forepaws, elongated rabbit hind feet, and fluffy tufted cottontail.
 */
object BunnyMasterSpec {

    val FurPrimary = Color(0xFFF3E9D8)
    val FurHighlight = Color(0xFFFAF4E9)
    val FurMidShadow = Color(0xFFE3D4BE)
    val FurDeepShadow = Color(0xFFCBB598)

    val InnerEarPink = Color(0xFFEBAFB7)
    val InnerEarDeepPink = Color(0xFFD995A0)
    val NosePink = Color(0xFFEBAFB7)
    val CheekPink = Color(0xFFF2B6BE)

    val DetailCocoa = Color(0xFF382C2A)
    val DetailCocoaSoft = Color(0xFF5A4743)
    val EyeIrisWarm = Color(0xFF4F3B36)

    val WallForestGreen = Color(0xFF0E1A15)
    val WallShadow = Color(0xFF07110D)
    val WallBevel = Color(0xFF1E3027)
    val MintAccent = Color(0xFF176B5B)

    // Master normalized proportions (1.0 x 1.0 square viewport)
    const val CENTER_X = 0.50f
    const val GROUND_Y = 0.905f

    // Sleek, slightly taller oval rabbit head with natural muzzle taper
    const val HEAD_CENTER_Y = 0.488f
    const val HEAD_CROWN_RX = 0.132f
    const val HEAD_CROWN_RY = 0.142f
    const val HEAD_CHEEK_RX = 0.146f
    const val HEAD_CHEEK_RY = 0.095f
    const val HEAD_CHEEK_OFFSET_Y = 0.044f

    // Slimmer, taller pear-tapered rabbit torso (height > width)
    const val BODY_CENTER_Y = 0.728f
    const val BODY_RADIUS_X = 0.122f
    const val BODY_RADIUS_Y = 0.160f

    // Expressive tall-oval animated rabbit eyes
    const val EYE_OFFSET_X = 0.058f
    const val EYE_Y = 0.492f
    const val EYE_WIDTH = 0.038f
    const val EYE_HEIGHT = 0.062f

    const val NOSE_Y = 0.538f
    const val NOSE_WIDTH = 0.025f
    const val NOSE_HEIGHT = 0.018f

    const val MOUTH_Y = 0.562f

    const val CHEEK_OFFSET_X = 0.100f
    const val CHEEK_Y = 0.538f

    // Slender, tall tapered cartoon rabbit ears
    const val EAR_BASE_Y = 0.370f
    const val UPRIGHT_EAR_X = 0.448f
    const val BENT_EAR_X = 0.552f
    const val EAR_HEIGHT = 0.320f
    const val EAR_WIDTH = 0.076f

    const val WALL_CREST_Y = 0.645f
    const val WALL_BOTTOM_Y = 0.915f
}

enum class BunnyMouthMood {
    SERENE,
    CURIOUS,
    HAPPY,
    SHY,
    SLEEPY
}

enum class BunnyWallMode {
    NONE,
    PEEK_BEHIND,
    BESIDE_WALL
}

data class BunnyPose(
    val offsetXFraction: Float = 0f,
    val offsetYFraction: Float = 0f,
    val bodyScaleX: Float = 1f,
    val bodyScaleY: Float = 1f,
    val headTiltDeg: Float = 0f,
    val turnYawFraction: Float = 0f,
    val uprightEarDeg: Float = 0f,
    val bentEarDeg: Float = 0f,
    val bentEarTipCurl: Float = 1f,
    val eyeOpenFraction: Float = 1f,
    val gazeOffsetX: Float = 0f,
    val gazeOffsetY: Float = 0f,
    val mouthMood: BunnyMouthMood = BunnyMouthMood.SERENE,
    val cheekAlpha: Float = 0.44f,
    val leftPawRaise: Float = 0f,
    val rightPawRaise: Float = 0f,
    val rightPawPointAngleDeg: Float = 0f,
    val pawsOnWallStrength: Float = 0f,
    val wallMode: BunnyWallMode = BunnyWallMode.NONE,
    val wallAlpha: Float = 0f,
    val wallOffsetXFraction: Float = 0f,
    val auraBoost: Float = 0f
)

/**
 * Computes all 8 stages of the Signature Peek-a-Boo Entrance for [progress] in 0f..1f.
 */
fun resolvePeekEntrancePose(progress: Float): BunnyPose {
    val p = progress.coerceIn(0f, 1f)

    fun smoothStep(start: Float, end: Float): Float {
        if (p <= start) return 0f
        if (p >= end) return 1f
        val t = (p - start) / (end - start)
        return t * t * (3f - 2f * t)
    }

    val earRise = smoothStep(0.06f, 0.24f)
    val eyeRise = smoothStep(0.34f, 0.52f)
    val emergeSlide = smoothStep(0.75f, 0.92f)
    val wallFadeOut = smoothStep(0.90f, 1.00f)

    // Hidden drop ensures tall slender ears (top ~ 0.05f) tuck cleanly below wall crest (~0.645f)
    val hiddenDrop = 0.64f * (1f - earRise) +
        0.18f * (1f - eyeRise) * earRise +
        0.09f * (1f - emergeSlide) * eyeRise

    val wigglePhase = if (p in 0.22f..0.40f) {
        val local = (p - 0.22f) / 0.18f
        sin(local * 4f * PI).toFloat() * (1f - local * 0.4f)
    } else 0f

    val peekBlink = if (p in 0.43f..0.50f) {
        val mid = 1f - kotlin.math.abs((p - 0.465f) / 0.035f)
        (1f - mid * 0.92f).coerceIn(0.08f, 1f)
    } else 1f

    val pawsIn = smoothStep(0.50f, 0.60f)
    val pawsOut = smoothStep(0.74f, 0.84f)
    val pawsOnWall = (pawsIn * (1f - pawsOut)).coerceIn(0f, 1f)

    val curiousPhase = smoothStep(0.60f, 0.70f) * (1f - smoothStep(0.75f, 0.88f))
    val curiousGaze = if (p in 0.60f..0.77f) {
        sin(((p - 0.60f) / 0.17f) * 2f * PI).toFloat() * 0.65f
    } else 0f

    val emergeHopY = if (p in 0.76f..0.92f) {
        val h = (p - 0.76f) / 0.16f
        -sin(h * PI).toFloat() * 0.045f
    } else 0f

    val wallAlpha = when {
        p < 0.06f -> smoothStep(0f, 0.06f)
        p >= 0.90f -> (1f - wallFadeOut).coerceIn(0f, 1f)
        else -> 1f
    }
    val wallShiftX = -0.34f * emergeSlide
    val bunnyShiftX = 0.10f * emergeSlide * (1f - wallFadeOut)
    val bentDelayDeg = -14f * (1f - smoothStep(0.14f, 0.28f))

    return BunnyPose(
        offsetXFraction = bunnyShiftX,
        offsetYFraction = hiddenDrop + emergeHopY,
        bodyScaleX = 1f + 0.03f * (emergeHopY * -15f).coerceIn(0f, 1f),
        bodyScaleY = 1f - 0.02f * (emergeHopY * -15f).coerceIn(0f, 1f),
        headTiltDeg = 9.5f * curiousPhase + wigglePhase * 2.5f,
        turnYawFraction = -0.20f * emergeSlide * (1f - wallFadeOut) + curiousGaze * 0.22f,
        uprightEarDeg = wigglePhase * 9f - 4f * (1f - earRise),
        bentEarDeg = bentDelayDeg - wigglePhase * 7f + 5f * curiousPhase,
        bentEarTipCurl = 1f + 0.08f * wigglePhase,
        eyeOpenFraction = peekBlink,
        gazeOffsetX = curiousGaze * 0.008f,
        gazeOffsetY = if (p in 0.36f..0.75f) -0.003f else 0f,
        mouthMood = if (curiousPhase > 0.3f) BunnyMouthMood.CURIOUS else BunnyMouthMood.SERENE,
        cheekAlpha = 0.42f + 0.10f * emergeSlide,
        leftPawRaise = if (emergeSlide > 0.2f && wallFadeOut < 0.9f) 0.25f * (1f - wallFadeOut) else 0f,
        rightPawRaise = if (emergeSlide > 0.2f && wallFadeOut < 0.9f) 0.30f * (1f - wallFadeOut) else 0f,
        pawsOnWallStrength = pawsOnWall,
        wallMode = if (emergeSlide < 0.38f) BunnyWallMode.PEEK_BEHIND else BunnyWallMode.BESIDE_WALL,
        wallAlpha = wallAlpha,
        wallOffsetXFraction = wallShiftX,
        auraBoost = 0.35f * emergeSlide
    )
}
