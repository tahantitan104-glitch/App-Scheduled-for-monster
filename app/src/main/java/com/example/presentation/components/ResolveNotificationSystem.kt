package com.example.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.designsystem.LocalResolveExtendedPalette
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ResolveNotificationTone {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class ResolveNotificationMessage(
    val id: Long = System.nanoTime(),
    val message: String,
    val tone: ResolveNotificationTone = inferNotificationTone(message),
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val showCloseButton: Boolean = false,
    val durationMs: Long? = null
)

fun inferNotificationTone(message: String): ResolveNotificationTone {
    val lower = message.lowercase()
    return when {
        listOf("invalid", "failed", "error", "denied", "conflict", "overlap", "cannot", "past")
            .any { lower.contains(it) } -> ResolveNotificationTone.ERROR
        listOf("please ", "enter ", "select at least", "set up ", "unavailable", "not supported", "warning")
            .any { lower.contains(it) } -> ResolveNotificationTone.WARNING
        listOf("saved", "added", "updated", "restored", "started", "protects", "deleted", "cleared", "reset", "completed", "synced")
            .any { lower.contains(it) } -> ResolveNotificationTone.SUCCESS
        else -> ResolveNotificationTone.INFO
    }
}

@Stable
class ResolveNotificationController {
    var current by mutableStateOf<ResolveNotificationMessage?>(null)
        private set

    fun show(
        message: String,
        tone: ResolveNotificationTone = inferNotificationTone(message),
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        showCloseButton: Boolean = false,
        durationMs: Long? = null
    ) {
        current = ResolveNotificationMessage(
            message = message,
            tone = tone,
            actionLabel = actionLabel,
            onAction = onAction,
            showCloseButton = showCloseButton,
            durationMs = durationMs
        )
    }

    fun show(notification: ResolveNotificationMessage) {
        current = notification
    }

    fun dismiss() {
        current = null
    }
}

@Composable
fun rememberResolveNotificationController(): ResolveNotificationController =
    remember { ResolveNotificationController() }

/**
 * Hybrid Premium Notification Host:
 * - Adaptive form: Compact Pill (short status), Rounded Card (standard), or Extended Card (actions/multiline)
 * - Context-aware bottom clearance above bottom navigation dock, IME keyboards, and system bars
 * - Smooth Slide + Fade + Scale motion respecting reducedMotion
 * - Swipe horizontal/downward to dismiss + contextual action/close controls
 */
@Composable
fun ResolveNotificationHost(
    controller: ResolveNotificationController,
    reducedMotion: Boolean,
    bottomClearance: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val active = controller.current

    LaunchedEffect(active?.id) {
        if (active != null) {
            val defaultDuration = when {
                active.actionLabel != null || active.tone == ResolveNotificationTone.ERROR -> 4_500L
                active.tone == ResolveNotificationTone.WARNING -> 3_800L
                active.message.length <= 40 -> 2_400L
                else -> 3_000L
            }
            delay(active.durationMs ?: defaultDuration)
            if (controller.current?.id == active.id) {
                controller.dismiss()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = bottomClearance),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = active != null,
            enter = if (reducedMotion) {
                fadeIn(ResolveMotion.fastTween(true))
            } else {
                slideInVertically(ResolveMotion.standardTween(false)) { it / 2 } +
                    fadeIn(ResolveMotion.standardTween(false)) +
                    scaleIn(initialScale = 0.94f, animationSpec = ResolveMotion.standardTween(false))
            },
            exit = if (reducedMotion) {
                fadeOut(ResolveMotion.exitTween(true))
            } else {
                slideOutVertically(ResolveMotion.exitTween(false)) { it / 2 } +
                    fadeOut(ResolveMotion.exitTween(false)) +
                    scaleOut(targetScale = 0.96f, animationSpec = ResolveMotion.exitTween(false))
            }
        ) {
            if (active != null) {
                AnimatedContent(
                    targetState = active,
                    transitionSpec = {
                        fadeIn(ResolveMotion.fastTween(reducedMotion)) togetherWith
                            fadeOut(ResolveMotion.exitTween(reducedMotion))
                    },
                    label = "resolve_notification_swap"
                ) { item ->
                    ResolveNotificationBanner(
                        item = item,
                        onDismiss = { controller.dismiss() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ResolveNotificationBanner(
    item: ResolveNotificationMessage,
    onDismiss: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    var dragOffsetX by remember(item.id) { mutableFloatStateOf(0f) }
    var dragOffsetY by remember(item.id) { mutableFloatStateOf(0f) }

    val toneColor = when (item.tone) {
        ResolveNotificationTone.SUCCESS -> palette.accentStrong
        ResolveNotificationTone.WARNING -> palette.warn
        ResolveNotificationTone.ERROR -> palette.error
        ResolveNotificationTone.INFO -> palette.info
    }
    val toneContainer = when (item.tone) {
        ResolveNotificationTone.SUCCESS -> palette.successContainer
        ResolveNotificationTone.WARNING -> palette.warnContainer
        ResolveNotificationTone.ERROR -> palette.errorContainer
        ResolveNotificationTone.INFO -> palette.infoContainer
    }
    val icon = when (item.tone) {
        ResolveNotificationTone.SUCCESS -> Icons.Outlined.CheckCircle
        ResolveNotificationTone.WARNING -> Icons.Outlined.Warning
        ResolveNotificationTone.ERROR -> Icons.Outlined.Warning
        ResolveNotificationTone.INFO -> Icons.Outlined.Info
    }

    val hasAction = !item.actionLabel.isNullOrBlank() && item.onAction != null
    val isCompactPill = !hasAction && !item.showCloseButton && item.message.length <= 40
    val isExtended = hasAction || item.showCloseButton || item.message.length > 68
    val cornerRadius = if (isCompactPill) 24.dp else 16.dp
    val shadowAlpha = if (palette.isDark) 0.30f else 0.12f

    val swipeModifier = Modifier
        .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
        .graphicsLayer {
            val totalPull = (abs(dragOffsetX) + dragOffsetY.coerceAtLeast(0f)) / 240f
            alpha = (1f - totalPull).coerceIn(0.35f, 1f)
        }
        .pointerInput(item.id) {
            detectDragGestures(
                onDragEnd = {
                    if (abs(dragOffsetX) > 95f || dragOffsetY > 55f) {
                        onDismiss()
                    } else {
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    }
                },
                onDragCancel = {
                    dragOffsetX = 0f
                    dragOffsetY = 0f
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffsetX += dragAmount.x
                    dragOffsetY = (dragOffsetY + dragAmount.y).coerceAtLeast(0f)
                }
            )
        }

    if (isCompactPill) {
        Row(
            modifier = swipeModifier
                .widthIn(max = 460.dp)
                .shadow(
                    elevation = if (palette.isDark) 8.dp else 4.dp,
                    shape = RoundedCornerShape(cornerRadius),
                    ambientColor = Color.Black.copy(alpha = shadowAlpha),
                    spotColor = Color.Black.copy(alpha = shadowAlpha)
                )
                .clip(RoundedCornerShape(cornerRadius))
                .background(palette.surfaceSecondary.copy(alpha = 0.97f))
                .border(1.dp, palette.cardBorder, RoundedCornerShape(cornerRadius))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("resolve_notification_pill"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(toneContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = toneColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = item.message,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        Row(
            modifier = swipeModifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = if (palette.isDark) 10.dp else 6.dp,
                    shape = RoundedCornerShape(cornerRadius),
                    ambientColor = Color.Black.copy(alpha = shadowAlpha),
                    spotColor = Color.Black.copy(alpha = shadowAlpha)
                )
                .clip(RoundedCornerShape(cornerRadius))
                .background(palette.surfaceSecondary.copy(alpha = 0.98f))
                .border(1.dp, toneColor.copy(alpha = 0.36f), RoundedCornerShape(cornerRadius))
                .padding(start = 12.dp, end = if (item.showCloseButton) 6.dp else 14.dp, top = 11.dp, bottom = 11.dp)
                .testTag("resolve_notification_card"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (isExtended) 32.dp else 22.dp)
                    .clip(CircleShape)
                    .background(toneColor)
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(toneContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = toneColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.message,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = palette.textPrimary,
                    maxLines = if (isExtended) 3 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (hasAction) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = item.actionLabel.orEmpty(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = toneColor,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(toneContainer)
                        .clickable {
                            item.onAction?.invoke()
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("resolve_notification_action_btn")
                )
            }
            if (item.showCloseButton || isExtended) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("resolve_notification_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Dismiss notification",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
