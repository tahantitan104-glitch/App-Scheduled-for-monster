package com.example.presentation.components.bunny

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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.presentation.components.bunny.BunnyMasterSpec.BENT_EAR_X
import com.example.presentation.components.bunny.BunnyMasterSpec.BODY_CENTER_Y
import com.example.presentation.components.bunny.BunnyMasterSpec.BODY_RADIUS_X
import com.example.presentation.components.bunny.BunnyMasterSpec.BODY_RADIUS_Y
import com.example.presentation.components.bunny.BunnyMasterSpec.CENTER_X
import com.example.presentation.components.bunny.BunnyMasterSpec.CHEEK_OFFSET_X
import com.example.presentation.components.bunny.BunnyMasterSpec.CHEEK_Y
import com.example.presentation.components.bunny.BunnyMasterSpec.CheekPink
import com.example.presentation.components.bunny.BunnyMasterSpec.DetailCocoa
import com.example.presentation.components.bunny.BunnyMasterSpec.DetailCocoaSoft
import com.example.presentation.components.bunny.BunnyMasterSpec.EAR_BASE_Y
import com.example.presentation.components.bunny.BunnyMasterSpec.EAR_HEIGHT
import com.example.presentation.components.bunny.BunnyMasterSpec.EAR_WIDTH
import com.example.presentation.components.bunny.BunnyMasterSpec.EYE_HEIGHT
import com.example.presentation.components.bunny.BunnyMasterSpec.EYE_OFFSET_X
import com.example.presentation.components.bunny.BunnyMasterSpec.EYE_WIDTH
import com.example.presentation.components.bunny.BunnyMasterSpec.EYE_Y
import com.example.presentation.components.bunny.BunnyMasterSpec.EyeIrisWarm
import com.example.presentation.components.bunny.BunnyMasterSpec.FurDeepShadow
import com.example.presentation.components.bunny.BunnyMasterSpec.FurHighlight
import com.example.presentation.components.bunny.BunnyMasterSpec.FurMidShadow
import com.example.presentation.components.bunny.BunnyMasterSpec.FurPrimary
import com.example.presentation.components.bunny.BunnyMasterSpec.GROUND_Y
import com.example.presentation.components.bunny.BunnyMasterSpec.HEAD_CENTER_Y
import com.example.presentation.components.bunny.BunnyMasterSpec.HEAD_CHEEK_OFFSET_Y
import com.example.presentation.components.bunny.BunnyMasterSpec.HEAD_CHEEK_RX
import com.example.presentation.components.bunny.BunnyMasterSpec.HEAD_CHEEK_RY
import com.example.presentation.components.bunny.BunnyMasterSpec.HEAD_CROWN_RX
import com.example.presentation.components.bunny.BunnyMasterSpec.HEAD_CROWN_RY
import com.example.presentation.components.bunny.BunnyMasterSpec.InnerEarDeepPink
import com.example.presentation.components.bunny.BunnyMasterSpec.InnerEarPink
import com.example.presentation.components.bunny.BunnyMasterSpec.MOUTH_Y
import com.example.presentation.components.bunny.BunnyMasterSpec.MintAccent
import com.example.presentation.components.bunny.BunnyMasterSpec.NOSE_HEIGHT
import com.example.presentation.components.bunny.BunnyMasterSpec.NOSE_WIDTH
import com.example.presentation.components.bunny.BunnyMasterSpec.NOSE_Y
import com.example.presentation.components.bunny.BunnyMasterSpec.NosePink
import com.example.presentation.components.bunny.BunnyMasterSpec.UPRIGHT_EAR_X
import com.example.presentation.components.bunny.BunnyMasterSpec.WALL_BOTTOM_Y
import com.example.presentation.components.bunny.BunnyMasterSpec.WALL_CREST_Y
import com.example.presentation.components.bunny.BunnyMasterSpec.WallBevel
import com.example.presentation.components.bunny.BunnyMasterSpec.WallForestGreen
import com.example.presentation.components.bunny.BunnyMasterSpec.WallShadow

class BunnyPathCache {
    val wallPath = Path()
    val behindWallClipPath = Path()
    val torsoPath = Path()
    val headPath = Path()
    val uprightOuterEar = Path()
    val uprightInnerEar = Path()
    val bentOuterEar = Path()
    val bentInnerEar = Path()
    val nosePath = Path()
    val mouthPath = Path()
    val sparklePath = Path()
    val eyeLidPath = Path()
}

fun DrawScope.drawCurvedForestWall(
    paths: BunnyPathCache,
    alpha: Float,
    offsetXFraction: Float
) {
    if (alpha <= 0.01f) return
    val w = size.width
    val h = size.height
    val dx = offsetXFraction * w
    val left = w * 0.14f + dx
    val right = w * 0.86f + dx
    val midX = (left + right) * 0.5f
    val shoulderY = h * (WALL_CREST_Y + 0.048f)
    val crestControlY = h * (WALL_CREST_Y - 0.042f)
    val bottomY = h * WALL_BOTTOM_Y

    paths.wallPath.reset()
    paths.wallPath.moveTo(left, bottomY)
    paths.wallPath.lineTo(left, shoulderY)
    paths.wallPath.quadraticTo(midX, crestControlY, right, shoulderY)
    paths.wallPath.lineTo(right, bottomY)
    paths.wallPath.quadraticTo(midX, bottomY + h * 0.032f, left, bottomY)
    paths.wallPath.close()

    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = 0.28f * alpha), Color.Transparent),
            center = Offset(midX, bottomY + h * 0.008f),
            radius = (right - left) * 0.55f
        ),
        topLeft = Offset(left - w * 0.04f, bottomY - h * 0.025f),
        size = Size((right - left) + w * 0.08f, h * 0.075f)
    )

    drawPath(
        path = paths.wallPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                WallBevel.copy(alpha = alpha),
                WallForestGreen.copy(alpha = alpha),
                WallShadow.copy(alpha = alpha)
            ),
            startY = h * WALL_CREST_Y,
            endY = bottomY
        )
    )

    paths.mouthPath.reset()
    paths.mouthPath.moveTo(left + w * 0.02f, shoulderY - h * 0.003f)
    paths.mouthPath.quadraticTo(midX, crestControlY + h * 0.002f, right - w * 0.02f, shoulderY - h * 0.003f)
    drawPath(
        path = paths.mouthPath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                MintAccent.copy(alpha = 0.14f * alpha),
                MintAccent.copy(alpha = 0.42f * alpha),
                MintAccent.copy(alpha = 0.14f * alpha)
            ),
            startX = left,
            endX = right
        ),
        style = Stroke(width = w * 0.011f, cap = StrokeCap.Round)
    )
}

fun DrawScope.clipAboveCurvedWall(
    paths: BunnyPathCache,
    wallOffsetXFraction: Float,
    block: DrawScope.() -> Unit
) {
    val w = size.width
    val h = size.height
    val dx = wallOffsetXFraction * w
    val left = w * 0.14f + dx
    val right = w * 0.86f + dx
    val midX = (left + right) * 0.5f
    val shoulderY = h * (WALL_CREST_Y + 0.048f)
    val crestControlY = h * (WALL_CREST_Y - 0.042f)

    paths.behindWallClipPath.reset()
    paths.behindWallClipPath.moveTo(0f, 0f)
    paths.behindWallClipPath.lineTo(w, 0f)
    paths.behindWallClipPath.lineTo(w, shoulderY)
    paths.behindWallClipPath.lineTo(right, shoulderY)
    paths.behindWallClipPath.quadraticTo(midX, crestControlY, left, shoulderY)
    paths.behindWallClipPath.lineTo(0f, shoulderY)
    paths.behindWallClipPath.close()

    clipPath(paths.behindWallClipPath) {
        block()
    }
}

fun DrawScope.drawWallRestingPaws(
    pose: BunnyPose,
    alpha: Float
) {
    if (pose.pawsOnWallStrength <= 0.02f) return
    val w = size.width
    val h = size.height
    val s = pose.pawsOnWallStrength * alpha
    val dx = (pose.wallOffsetXFraction + pose.offsetXFraction * 0.4f) * w
    val crestY = h * (WALL_CREST_Y + 0.010f)
    val pawW = w * 0.056f
    val pawH = h * 0.040f

    listOf(w * 0.415f + dx, w * 0.585f + dx).forEach { pawCx ->
        drawOval(
            color = WallShadow.copy(alpha = 0.42f * s),
            topLeft = Offset(pawCx - pawW * 0.55f, crestY - pawH * 0.18f),
            size = Size(pawW * 1.1f, pawH * 0.90f)
        )
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    FurHighlight.copy(alpha = s),
                    FurPrimary.copy(alpha = s),
                    FurMidShadow.copy(alpha = s)
                ),
                center = Offset(pawCx - pawW * 0.14f, crestY - pawH * 0.25f),
                radius = pawW * 0.72f
            ),
            topLeft = Offset(pawCx - pawW * 0.5f, crestY - pawH * 0.52f),
            size = Size(pawW, pawH)
        )
    }
}

fun DrawScope.drawBunnyShadow(pose: BunnyPose) {
    val w = size.width
    val h = size.height
    val cx = w * (CENTER_X + pose.offsetXFraction)
    val liftFactor = (-pose.offsetYFraction * 4f).coerceIn(-0.2f, 0.5f)
    val shadowScale = (1f - liftFactor * 0.35f).coerceIn(0.65f, 1.15f)
    val shadowAlpha = (0.28f - liftFactor * 0.10f).coerceIn(0.10f, 0.34f)
    val shadowW = w * 0.34f * shadowScale
    val shadowH = h * 0.058f * shadowScale

    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = shadowAlpha),
                Color.Transparent
            ),
            center = Offset(cx, h * GROUND_Y),
            radius = shadowW * 0.54f
        ),
        topLeft = Offset(cx - shadowW * 0.5f, h * GROUND_Y - shadowH * 0.5f),
        size = Size(shadowW, shadowH)
    )
}

/**
 * Slimmer, taller sculpted pear-tapered rabbit torso with tucked seated haunches,
 * slender elongated rabbit hind feet, and a tufted fluffy cottontail.
 */
fun DrawScope.drawBunnyBodyAndFeet(
    paths: BunnyPathCache,
    pose: BunnyPose
) {
    val w = size.width
    val h = size.height
    val cx = w * CENTER_X
    val cy = h * BODY_CENTER_Y
    val rx = w * BODY_RADIUS_X * pose.bodyScaleX
    val ry = h * BODY_RADIUS_Y * pose.bodyScaleY
    val turnShift = pose.turnYawFraction * w * 0.015f

    // 1. Fluffy multi-tuft cottontail peeking neatly behind haunch
    val tailSide = if (pose.turnYawFraction < -0.15f) 1f else -1f
    val tailX = cx + tailSide * rx * 0.96f - turnShift * 0.8f
    val tailY = cy + ry * 0.36f
    val tailR = w * 0.044f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(FurHighlight, FurPrimary, FurMidShadow),
            center = Offset(tailX - tailR * 0.2f, tailY - tailR * 0.2f),
            radius = tailR * 1.25f
        ),
        radius = tailR,
        center = Offset(tailX, tailY)
    )
    drawCircle(
        color = FurHighlight.copy(alpha = 0.92f),
        radius = tailR * 0.56f,
        center = Offset(tailX + tailSide * tailR * 0.30f, tailY - tailR * 0.34f)
    )

    // 2. Sculpted slim-shouldered, graceful tapered rabbit torso (narrower upper chest -> curved hips)
    val bodyCx = cx + turnShift
    val topY = cy - ry * 0.96f
    val bottomY = cy + ry * 0.96f
    paths.torsoPath.reset()
    paths.torsoPath.moveTo(bodyCx, topY)
    // Right shoulder tapering gently out to hip
    paths.torsoPath.cubicTo(
        bodyCx + rx * 0.68f, topY + ry * 0.04f,
        bodyCx + rx * 0.84f, cy - ry * 0.28f,
        bodyCx + rx * 1.00f, cy + ry * 0.30f
    )
    paths.torsoPath.cubicTo(
        bodyCx + rx * 1.04f, bottomY - ry * 0.16f,
        bodyCx + rx * 0.62f, bottomY,
        bodyCx, bottomY
    )
    // Left hip tapering smoothly back to slender shoulder
    paths.torsoPath.cubicTo(
        bodyCx - rx * 0.62f, bottomY,
        bodyCx - rx * 1.04f, bottomY - ry * 0.16f,
        bodyCx - rx * 1.00f, cy + ry * 0.30f
    )
    paths.torsoPath.cubicTo(
        bodyCx - rx * 0.84f, cy - ry * 0.28f,
        bodyCx - rx * 0.68f, topY + ry * 0.04f,
        bodyCx, topY
    )
    paths.torsoPath.close()

    drawPath(
        path = paths.torsoPath,
        brush = Brush.radialGradient(
            colors = listOf(FurHighlight, FurPrimary, FurMidShadow, FurDeepShadow),
            center = Offset(bodyCx - rx * 0.24f, cy - ry * 0.24f),
            radius = ry * 1.28f
        )
    )

    // 3. Soft tucked seated rabbit hind haunches along outer lower hips for real bunny posture
    val haunchW = rx * 0.54f
    val haunchH = ry * 0.62f
    val haunchY = cy + ry * 0.42f
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(FurHighlight, FurPrimary, FurMidShadow),
            center = Offset(bodyCx - rx * 0.68f, haunchY - haunchH * 0.15f),
            radius = haunchH
        ),
        topLeft = Offset(bodyCx - rx * 1.04f, haunchY - haunchH * 0.5f),
        size = Size(haunchW, haunchH)
    )
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(FurHighlight, FurPrimary, FurMidShadow),
            center = Offset(bodyCx + rx * 0.62f, haunchY - haunchH * 0.15f),
            radius = haunchH
        ),
        topLeft = Offset(bodyCx + rx * 1.04f - haunchW, haunchY - haunchH * 0.5f),
        size = Size(haunchW, haunchH)
    )

    // 4. Slender soft cream chest/belly fur highlight
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(FurHighlight.copy(alpha = 0.94f), FurPrimary.copy(alpha = 0f)),
            center = Offset(cx + turnShift * 1.2f, cy - ry * 0.04f),
            radius = rx * 0.60f
        ),
        topLeft = Offset(cx + turnShift * 1.2f - rx * 0.48f, cy - ry * 0.68f),
        size = Size(rx * 0.96f, ry * 1.28f)
    )

    // 5. Soft neck/chin ambient shadow under head
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(FurDeepShadow.copy(alpha = 0.52f), Color.Transparent),
            center = Offset(bodyCx, topY + ry * 0.18f),
            radius = rx * 0.62f
        ),
        topLeft = Offset(bodyCx - rx * 0.60f, topY - ry * 0.05f),
        size = Size(rx * 1.20f, ry * 0.40f)
    )

    // 6. Slender, slightly longer rounded rabbit hind feet
    val footW = w * 0.088f
    val footH = h * 0.042f
    val footY = h * (BODY_CENTER_Y + BODY_RADIUS_Y * 0.88f)
    val leftFootX = cx - w * 0.086f + turnShift * 0.55f
    val rightFootX = cx + w * 0.086f + turnShift * 0.55f

    drawSingleHindFoot(leftFootX, footY, footW, footH, angleDeg = -9f)
    drawSingleHindFoot(rightFootX, footY, footW, footH, angleDeg = 9f)
}

private fun DrawScope.drawSingleHindFoot(
    centerX: Float,
    centerY: Float,
    footW: Float,
    footH: Float,
    angleDeg: Float
) {
    rotate(degrees = angleDeg, pivot = Offset(centerX, centerY)) {
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(FurHighlight, FurPrimary, FurMidShadow, FurDeepShadow),
                center = Offset(centerX - footW * 0.16f, centerY - footH * 0.25f),
                radius = footW * 0.72f
            ),
            topLeft = Offset(centerX - footW * 0.5f, centerY - footH * 0.5f),
            size = Size(footW, footH)
        )
    }
}

/**
 * Draws tall, slender, gently tapered animated-rabbit ears anchored neatly on the upper crown.
 */
fun DrawScope.drawBunnyEars(
    paths: BunnyPathCache,
    pose: BunnyPose
) {
    val w = size.width
    val h = size.height
    val yawShift = pose.turnYawFraction * w * 0.018f
    val uprightPivot = Offset(w * UPRIGHT_EAR_X + yawShift, h * EAR_BASE_Y)
    val bentPivot = Offset(w * BENT_EAR_X + yawShift, h * EAR_BASE_Y)

    if (pose.turnYawFraction < -0.3f) {
        drawBentEar(paths, bentPivot, pose)
        drawUprightEar(paths, uprightPivot, pose)
    } else {
        drawUprightEar(paths, uprightPivot, pose)
        drawBentEar(paths, bentPivot, pose)
    }
}

private fun DrawScope.drawUprightEar(
    paths: BunnyPathCache,
    pivot: Offset,
    pose: BunnyPose
) {
    val w = size.width
    val h = size.height
    val earW = w * EAR_WIDTH
    val earH = h * EAR_HEIGHT
    val totalDeg = -6f + pose.uprightEarDeg

    rotate(degrees = totalDeg, pivot = pivot) {
        // Slender tapered cartoon rabbit ear: narrow base swelling gently mid-ear and tapering smoothly to rounded tip
        paths.uprightOuterEar.reset()
        paths.uprightOuterEar.moveTo(pivot.x - earW * 0.26f, pivot.y + earH * 0.04f)
        paths.uprightOuterEar.cubicTo(
            pivot.x - earW * 0.54f, pivot.y - earH * 0.34f,
            pivot.x - earW * 0.52f, pivot.y - earH * 0.90f,
            pivot.x - earW * 0.02f, pivot.y - earH * 1.00f
        )
        paths.uprightOuterEar.cubicTo(
            pivot.x + earW * 0.48f, pivot.y - earH * 0.90f,
            pivot.x + earW * 0.52f, pivot.y - earH * 0.34f,
            pivot.x + earW * 0.28f, pivot.y + earH * 0.04f
        )
        paths.uprightOuterEar.close()

        drawPath(
            path = paths.uprightOuterEar,
            brush = Brush.linearGradient(
                colors = listOf(FurHighlight, FurPrimary, FurMidShadow),
                start = Offset(pivot.x - earW * 0.45f, pivot.y - earH),
                end = Offset(pivot.x + earW * 0.45f, pivot.y)
            )
        )

        paths.uprightInnerEar.reset()
        paths.uprightInnerEar.moveTo(pivot.x - earW * 0.12f, pivot.y - earH * 0.07f)
        paths.uprightInnerEar.cubicTo(
            pivot.x - earW * 0.30f, pivot.y - earH * 0.36f,
            pivot.x - earW * 0.28f, pivot.y - earH * 0.78f,
            pivot.x - earW * 0.01f, pivot.y - earH * 0.85f
        )
        paths.uprightInnerEar.cubicTo(
            pivot.x + earW * 0.26f, pivot.y - earH * 0.78f,
            pivot.x + earW * 0.28f, pivot.y - earH * 0.36f,
            pivot.x + earW * 0.13f, pivot.y - earH * 0.07f
        )
        paths.uprightInnerEar.close()

        drawPath(
            path = paths.uprightInnerEar,
            brush = Brush.verticalGradient(
                colors = listOf(InnerEarPink, InnerEarDeepPink.copy(alpha = 0.88f)),
                startY = pivot.y - earH * 0.85f,
                endY = pivot.y - earH * 0.07f
            )
        )
    }
}

private fun DrawScope.drawBentEar(
    paths: BunnyPathCache,
    pivot: Offset,
    pose: BunnyPose
) {
    val w = size.width
    val h = size.height
    val earW = w * (EAR_WIDTH * 1.02f)
    val earH = h * (EAR_HEIGHT * 0.96f)
    val curl = pose.bentEarTipCurl.coerceAtLeast(0.82f)
    val totalDeg = 7f + pose.bentEarDeg

    rotate(degrees = totalDeg, pivot = pivot) {
        val bendX = pivot.x + earW * 0.36f
        val bendTopY = pivot.y - earH * 0.88f
        val tipX = pivot.x + earW * (1.28f * curl)
        val tipY = pivot.y - earH * (0.46f - 0.08f * (curl - 1f))

        paths.bentOuterEar.reset()
        paths.bentOuterEar.moveTo(pivot.x - earW * 0.26f, pivot.y + earH * 0.04f)
        paths.bentOuterEar.cubicTo(
            pivot.x - earW * 0.42f, pivot.y - earH * 0.48f,
            pivot.x - earW * 0.04f, pivot.y - earH * 1.00f,
            bendX + earW * 0.30f, bendTopY
        )
        paths.bentOuterEar.cubicTo(
            tipX + earW * 0.24f, bendTopY + earH * 0.06f,
            tipX + earW * 0.28f, tipY + earH * 0.12f,
            tipX - earW * 0.18f, tipY + earH * 0.18f
        )
        paths.bentOuterEar.cubicTo(
            bendX + earW * 0.08f, tipY - earH * 0.04f,
            pivot.x + earW * 0.40f, pivot.y - earH * 0.38f,
            pivot.x + earW * 0.28f, pivot.y + earH * 0.04f
        )
        paths.bentOuterEar.close()

        drawPath(
            path = paths.bentOuterEar,
            brush = Brush.linearGradient(
                colors = listOf(FurHighlight, FurPrimary, FurMidShadow, FurDeepShadow),
                start = Offset(pivot.x - earW * 0.2f, pivot.y - earH),
                end = Offset(tipX, pivot.y)
            )
        )

        paths.bentInnerEar.reset()
        paths.bentInnerEar.moveTo(pivot.x - earW * 0.11f, pivot.y - earH * 0.07f)
        paths.bentInnerEar.cubicTo(
            pivot.x - earW * 0.18f, pivot.y - earH * 0.42f,
            pivot.x + earW * 0.02f, pivot.y - earH * 0.72f,
            bendX + earW * 0.06f, pivot.y - earH * 0.64f
        )
        paths.bentInnerEar.cubicTo(
            pivot.x + earW * 0.24f, pivot.y - earH * 0.46f,
            pivot.x + earW * 0.20f, pivot.y - earH * 0.14f,
            pivot.x + earW * 0.12f, pivot.y - earH * 0.07f
        )
        paths.bentInnerEar.close()

        drawPath(
            path = paths.bentInnerEar,
            brush = Brush.verticalGradient(
                colors = listOf(InnerEarPink.copy(alpha = 0.94f), InnerEarDeepPink.copy(alpha = 0.85f)),
                startY = pivot.y - earH * 0.70f,
                endY = pivot.y - earH * 0.07f
            )
        )

        paths.mouthPath.reset()
        paths.mouthPath.moveTo(pivot.x + earW * 0.16f, pivot.y - earH * 0.61f)
        paths.mouthPath.quadraticTo(
            bendX + earW * 0.26f, pivot.y - earH * 0.70f,
            tipX - earW * 0.18f, tipY + earH * 0.06f
        )
        drawPath(
            path = paths.mouthPath,
            color = FurDeepShadow.copy(alpha = 0.62f),
            style = Stroke(width = w * 0.006f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Refined, slightly slimmer oval rabbit head with subtle cheek fullness, soft brow arcs,
 * expressive tall-oval animated eyes, sculpted bunny nose, and delicate Y-cleft mouth.
 */
fun DrawScope.drawBunnyHeadAndFace(
    paths: BunnyPathCache,
    pose: BunnyPose
) {
    val w = size.width
    val h = size.height
    val cx = w * CENTER_X
    val cy = h * HEAD_CENTER_Y
    val crownRx = w * HEAD_CROWN_RX
    val crownRy = h * HEAD_CROWN_RY
    val cheekRx = w * HEAD_CHEEK_RX
    val cheekRy = h * HEAD_CHEEK_RY
    val cheekCy = cy + h * HEAD_CHEEK_OFFSET_Y
    val yawShift = pose.turnYawFraction * w * 0.036f

    // 1. Subtle lower-cheek contour (narrow & sculpted so bunny never looks overly wide/round)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(FurHighlight, FurPrimary, FurMidShadow, FurDeepShadow),
            center = Offset(cx - cheekRx * 0.20f + yawShift * 0.25f, cheekCy - cheekRy * 0.30f),
            radius = cheekRx * 1.38f
        ),
        topLeft = Offset(cx - cheekRx, cheekCy - cheekRy),
        size = Size(cheekRx * 2f, cheekRy * 2f)
    )

    // 2. Taller sculpted oval cranium dome
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(FurHighlight, FurPrimary, FurMidShadow),
            center = Offset(cx - crownRx * 0.26f + yawShift * 0.3f, cy - crownRy * 0.38f),
            radius = crownRx * 1.48f
        ),
        topLeft = Offset(cx - crownRx, cy - crownRy),
        size = Size(crownRx * 2f, crownRy * 1.98f)
    )

    // Forehead silk rim glow
    drawArc(
        color = Color.White.copy(alpha = 0.42f),
        startAngle = 212f,
        sweepAngle = 72f,
        useCenter = false,
        topLeft = Offset(cx - crownRx * 0.82f, cy - crownRy * 0.88f),
        size = Size(crownRx * 1.64f, crownRy * 1.72f),
        style = Stroke(width = w * 0.007f, cap = StrokeCap.Round)
    )

    val leftScale = (1f + pose.turnYawFraction * 0.14f).coerceIn(0.76f, 1.12f)
    val rightScale = (1f - pose.turnYawFraction * 0.14f).coerceIn(0.76f, 1.12f)

    // Soft diffused pastel cheek blush
    drawCheekBlush(
        Offset(cx - w * CHEEK_OFFSET_X + yawShift, h * CHEEK_Y),
        w * 0.034f * leftScale,
        h * 0.022f,
        pose.cheekAlpha
    )
    drawCheekBlush(
        Offset(cx + w * CHEEK_OFFSET_X + yawShift, h * CHEEK_Y),
        w * 0.034f * rightScale,
        h * 0.022f,
        pose.cheekAlpha
    )

    // Subtle animated brow arcs for real cartoon rabbit expressiveness
    val browLift = if (pose.mouthMood == BunnyMouthMood.CURIOUS || pose.mouthMood == BunnyMouthMood.HAPPY) {
        -h * 0.008f
    } else 0f
    val eyeW = w * EYE_WIDTH
    val eyeH = h * EYE_HEIGHT
    drawBunnyBrow(
        center = Offset(cx - w * EYE_OFFSET_X + yawShift, h * (EYE_Y - 0.044f) + browLift),
        width = eyeW * 0.85f * leftScale
    )
    drawBunnyBrow(
        center = Offset(cx + w * EYE_OFFSET_X + yawShift, h * (EYE_Y - 0.044f) + browLift),
        width = eyeW * 0.85f * rightScale
    )

    // Expressive tall-oval rabbit eyes (#382C2A) with warm iris gradient & catchlights
    drawBunnyEye(
        paths = paths,
        center = Offset(cx - w * EYE_OFFSET_X + yawShift, h * EYE_Y),
        eyeW = eyeW * leftScale,
        eyeH = eyeH,
        eyeOpenFraction = pose.eyeOpenFraction,
        gazeX = pose.gazeOffsetX * w,
        gazeY = pose.gazeOffsetY * h,
        mood = pose.mouthMood
    )
    drawBunnyEye(
        paths = paths,
        center = Offset(cx + w * EYE_OFFSET_X + yawShift, h * EYE_Y),
        eyeW = eyeW * rightScale,
        eyeH = eyeH,
        eyeOpenFraction = pose.eyeOpenFraction,
        gazeX = pose.gazeOffsetX * w,
        gazeY = pose.gazeOffsetY * h,
        mood = pose.mouthMood
    )

    // Delicate tapered rabbit muzzle puffs (no feline whiskers)
    val noseCx = cx + yawShift * 1.12f
    val muzzleY = h * (NOSE_Y + 0.022f)
    val puffR = w * 0.026f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(FurHighlight.copy(alpha = 0.92f), FurPrimary.copy(alpha = 0f)),
            center = Offset(noseCx - w * 0.018f, muzzleY),
            radius = puffR
        ),
        radius = puffR,
        center = Offset(noseCx - w * 0.018f, muzzleY)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(FurHighlight.copy(alpha = 0.92f), FurPrimary.copy(alpha = 0f)),
            center = Offset(noseCx + w * 0.018f, muzzleY),
            radius = puffR
        ),
        radius = puffR,
        center = Offset(noseCx + w * 0.018f, muzzleY)
    )

    // Sculpted small pink rabbit nose (#EBAFB7)
    val noseCy = h * NOSE_Y
    val noseW = w * NOSE_WIDTH
    val noseH = h * NOSE_HEIGHT
    paths.nosePath.reset()
    paths.nosePath.moveTo(noseCx - noseW * 0.44f, noseCy - noseH * 0.25f)
    paths.nosePath.quadraticTo(noseCx, noseCy - noseH * 0.56f, noseCx + noseW * 0.44f, noseCy - noseH * 0.25f)
    paths.nosePath.quadraticTo(noseCx + noseW * 0.44f, noseCy + noseH * 0.24f, noseCx, noseCy + noseH * 0.58f)
    paths.nosePath.quadraticTo(noseCx - noseW * 0.44f, noseCy + noseH * 0.24f, noseCx - noseW * 0.44f, noseCy - noseH * 0.25f)
    paths.nosePath.close()

    drawPath(
        path = paths.nosePath,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFF6C8CE), NosePink, InnerEarDeepPink),
            startY = noseCy - noseH * 0.55f,
            endY = noseCy + noseH * 0.55f
        )
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.55f),
        radius = noseW * 0.13f,
        center = Offset(noseCx - noseW * 0.13f, noseCy - noseH * 0.12f)
    )

    // Classic refined rabbit Y-cleft mouth
    drawBunnyMouth(
        paths = paths,
        noseCx = noseCx,
        noseBottomY = noseCy + noseH * 0.52f,
        mouthY = h * MOUTH_Y,
        w = w,
        mood = pose.mouthMood
    )
}

private fun DrawScope.drawBunnyBrow(
    center: Offset,
    width: Float
) {
    val path = Path().apply {
        moveTo(center.x - width * 0.5f, center.y + width * 0.14f)
        quadraticTo(center.x, center.y - width * 0.18f, center.x + width * 0.5f, center.y + width * 0.14f)
    }
    drawPath(
        path = path,
        color = DetailCocoaSoft.copy(alpha = 0.45f),
        style = Stroke(width = width * 0.12f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawCheekBlush(
    center: Offset,
    rx: Float,
    ry: Float,
    alpha: Float
) {
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                CheekPink.copy(alpha = alpha.coerceIn(0f, 0.65f)),
                CheekPink.copy(alpha = 0f)
            ),
            center = center,
            radius = rx
        ),
        topLeft = Offset(center.x - rx, center.y - ry),
        size = Size(rx * 2f, ry * 2f)
    )
}

private fun DrawScope.drawBunnyEye(
    paths: BunnyPathCache,
    center: Offset,
    eyeW: Float,
    eyeH: Float,
    eyeOpenFraction: Float,
    gazeX: Float,
    gazeY: Float,
    mood: BunnyMouthMood
) {
    val open = eyeOpenFraction.coerceIn(0.02f, 1f)

    if (open <= 0.16f || mood == BunnyMouthMood.SLEEPY) {
        val dip = if (mood == BunnyMouthMood.SLEEPY) eyeH * 0.18f else eyeH * 0.14f
        paths.eyeLidPath.reset()
        paths.eyeLidPath.moveTo(center.x - eyeW * 0.54f, center.y + dip * 0.15f)
        paths.eyeLidPath.quadraticTo(center.x, center.y + dip, center.x + eyeW * 0.54f, center.y + dip * 0.15f)
        drawPath(
            path = paths.eyeLidPath,
            color = DetailCocoa,
            style = Stroke(width = eyeW * 0.21f, cap = StrokeCap.Round)
        )
        return
    }

    val eyeRect = Rect(
        left = center.x - eyeW * 0.5f,
        top = center.y - eyeH * 0.5f,
        right = center.x + eyeW * 0.5f,
        bottom = center.y + eyeH * 0.5f
    )

    // Layered deep cocoa iris with subtle warm bottom sheen
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF281F1D), DetailCocoa, EyeIrisWarm),
            center = Offset(center.x + gazeX, center.y + gazeY - eyeH * 0.10f),
            radius = eyeH * 0.72f
        ),
        topLeft = eyeRect.topLeft,
        size = eyeRect.size
    )

    drawCircle(
        color = Color.White.copy(alpha = 0.96f),
        radius = eyeW * 0.21f,
        center = Offset(center.x - eyeW * 0.16f + gazeX * 0.7f, center.y - eyeH * 0.20f + gazeY * 0.7f)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.74f),
        radius = eyeW * 0.10f,
        center = Offset(center.x + eyeW * 0.18f + gazeX * 0.7f, center.y + eyeH * 0.18f + gazeY * 0.7f)
    )

    if (open < 0.98f) {
        val lidCloseFactor = (1f - open).coerceIn(0f, 1f)
        val lidHeight = eyeH * lidCloseFactor * 0.95f
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(FurPrimary, FurMidShadow),
                startY = eyeRect.top - lidHeight * 0.3f,
                endY = eyeRect.top + lidHeight
            ),
            topLeft = Offset(eyeRect.left - eyeW * 0.12f, eyeRect.top - eyeH * 0.15f),
            size = Size(eyeW * 1.24f, lidHeight + eyeH * 0.18f)
        )
        val lashY = eyeRect.top + lidHeight
        drawLine(
            color = DetailCocoa,
            start = Offset(eyeRect.left - eyeW * 0.06f, lashY),
            end = Offset(eyeRect.right + eyeW * 0.06f, lashY),
            strokeWidth = eyeW * 0.14f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawBunnyMouth(
    paths: BunnyPathCache,
    noseCx: Float,
    noseBottomY: Float,
    mouthY: Float,
    w: Float,
    mood: BunnyMouthMood
) {
    val strokeW = w * 0.0068f
    val span = w * 0.017f

    drawLine(
        color = DetailCocoa,
        start = Offset(noseCx, noseBottomY),
        end = Offset(noseCx, mouthY - w * 0.003f),
        strokeWidth = strokeW * 0.85f,
        cap = StrokeCap.Round
    )

    paths.mouthPath.reset()
    when (mood) {
        BunnyMouthMood.HAPPY -> {
            paths.mouthPath.moveTo(noseCx - span * 1.15f, mouthY - w * 0.002f)
            paths.mouthPath.quadraticTo(noseCx - span * 0.45f, mouthY + w * 0.017f, noseCx, mouthY + w * 0.001f)
            paths.mouthPath.quadraticTo(noseCx + span * 0.45f, mouthY + w * 0.017f, noseCx + span * 1.15f, mouthY - w * 0.002f)
            drawPath(
                path = paths.mouthPath,
                color = DetailCocoa,
                style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawOval(
                color = InnerEarPink,
                topLeft = Offset(noseCx - span * 0.40f, mouthY + w * 0.002f),
                size = Size(span * 0.80f, w * 0.013f)
            )
        }
        BunnyMouthMood.CURIOUS -> {
            drawOval(
                color = DetailCocoa,
                topLeft = Offset(noseCx - span * 0.34f, mouthY - w * 0.002f),
                size = Size(span * 0.68f, w * 0.014f),
                style = Stroke(width = strokeW * 0.9f)
            )
        }
        BunnyMouthMood.SHY,
        BunnyMouthMood.SLEEPY -> {
            paths.mouthPath.moveTo(noseCx - span * 0.72f, mouthY + w * 0.002f)
            paths.mouthPath.quadraticTo(noseCx, mouthY + w * 0.009f, noseCx + span * 0.72f, mouthY + w * 0.002f)
            drawPath(
                path = paths.mouthPath,
                color = DetailCocoa,
                style = Stroke(width = strokeW * 0.9f, cap = StrokeCap.Round)
            )
        }
        BunnyMouthMood.SERENE -> {
            paths.mouthPath.moveTo(noseCx - span, mouthY - w * 0.002f)
            paths.mouthPath.quadraticTo(noseCx - span * 0.42f, mouthY + w * 0.010f, noseCx, mouthY - w * 0.002f)
            paths.mouthPath.quadraticTo(noseCx + span * 0.42f, mouthY + w * 0.010f, noseCx + span, mouthY - w * 0.002f)
            drawPath(
                path = paths.mouthPath,
                color = DetailCocoa,
                style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

fun DrawScope.drawBunnyPaws(
    pose: BunnyPose
) {
    val w = size.width
    val h = size.height
    val yawShift = pose.turnYawFraction * w * 0.018f
    val shoulderY = h * 0.650f
    val pawRadius = w * 0.034f

    val leftShoulderX = w * 0.415f + yawShift
    val leftRaise = pose.leftPawRaise.coerceIn(0f, 1f)
    val leftHandX = leftShoulderX - w * (0.014f + 0.056f * leftRaise)
    val leftHandY = shoulderY + h * (0.056f - 0.145f * leftRaise)
    drawSingleForepaw(
        shoulder = Offset(leftShoulderX, shoulderY),
        hand = Offset(leftHandX, leftHandY),
        pawRadius = pawRadius
    )

    val rightShoulderX = w * 0.585f + yawShift
    val rightRaise = pose.rightPawRaise.coerceIn(0f, 1f)
    val rightHandX = rightShoulderX + w * (0.014f + 0.068f * rightRaise)
    val rightHandY = shoulderY + h * (0.056f - 0.215f * rightRaise)
    rotate(degrees = pose.rightPawPointAngleDeg * rightRaise, pivot = Offset(rightShoulderX, shoulderY)) {
        drawSingleForepaw(
            shoulder = Offset(rightShoulderX, shoulderY),
            hand = Offset(rightHandX, rightHandY),
            pawRadius = pawRadius
        )
    }
}

private fun DrawScope.drawSingleForepaw(
    shoulder: Offset,
    hand: Offset,
    pawRadius: Float
) {
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(FurMidShadow, FurPrimary, FurHighlight),
            start = shoulder,
            end = hand
        ),
        start = shoulder,
        end = hand,
        strokeWidth = pawRadius * 1.42f,
        cap = StrokeCap.Round
    )
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(FurHighlight, FurPrimary, FurMidShadow),
            center = Offset(hand.x - pawRadius * 0.18f, hand.y - pawRadius * 0.22f),
            radius = pawRadius * 1.25f
        ),
        topLeft = Offset(hand.x - pawRadius * 0.88f, hand.y - pawRadius * 0.74f),
        size = Size(pawRadius * 1.76f, pawRadius * 1.48f)
    )
}

fun DrawScope.drawMintSparkleAccent(
    paths: BunnyPathCache,
    center: Offset,
    radius: Float,
    alpha: Float
) {
    if (alpha <= 0.02f) return
    paths.sparklePath.reset()
    paths.sparklePath.moveTo(center.x, center.y - radius)
    paths.sparklePath.quadraticTo(center.x, center.y, center.x + radius, center.y)
    paths.sparklePath.quadraticTo(center.x, center.y, center.x, center.y + radius)
    paths.sparklePath.quadraticTo(center.x, center.y, center.x - radius, center.y)
    paths.sparklePath.quadraticTo(center.x, center.y, center.x, center.y - radius)
    paths.sparklePath.close()
    drawPath(
        path = paths.sparklePath,
        color = MintAccent.copy(alpha = alpha.coerceIn(0f, 1f))
    )
}
