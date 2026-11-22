package com.example.presentation.applock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.domain.model.AppLockAuthMethod
import com.example.domain.model.AppLockPinLength
import com.example.security.AppLockCredentialManager
import kotlinx.coroutines.delay
import kotlin.math.hypot

/**
 * Shared authentication input component supporting all three RESOLVE App Lock methods:
 * - [AppLockAuthMethod.PATTERN] (3x3 interactive pattern grid)
 * - [AppLockAuthMethod.PIN] (4-digit or 6-digit numeric pad)
 * - [AppLockAuthMethod.SIX_CELL_SEQUENTIAL] (2x3 tactile grid; shows order numbers ONLY during setup,
 *   completely hiding all numbers & order indicators during unlock)
 */
@Composable
fun AppLockAuthChallengePanel(
    method: AppLockAuthMethod,
    pinLength: AppLockPinLength,
    isSetupMode: Boolean,
    promptMessage: String,
    errorMessage: String?,
    onCredentialSubmitted: (String) -> Unit,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
    resetKey: Int = 0
) {
    val palette = LocalResolveExtendedPalette.current
    Column(
        modifier = modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 10.dp else 14.dp)
    ) {
        Text(
            text = promptMessage,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            color = palette.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("applock_auth_prompt")
        )

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = palette.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.errorContainer.copy(alpha = 0.72f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("applock_auth_error")
            )
        }

        when (method) {
            AppLockAuthMethod.PATTERN -> {
                PatternLockPad(
                    gridSize = if (compactMode) 210.dp else 256.dp,
                    resetKey = resetKey,
                    onPatternFinished = { sequence ->
                        onCredentialSubmitted(sequence)
                    }
                )
            }

            AppLockAuthMethod.PIN -> {
                PinLockPad(
                    digitsCount = pinLength.digits,
                    resetKey = resetKey,
                    compactMode = compactMode,
                    onPinComplete = { pin ->
                        onCredentialSubmitted(pin)
                    }
                )
            }

            AppLockAuthMethod.SIX_CELL_SEQUENTIAL -> {
                SixCellSequentialPad(
                    showSequenceNumbers = isSetupMode,
                    resetKey = resetKey,
                    compactMode = compactMode,
                    onSequenceSubmitted = { seq ->
                        onCredentialSubmitted(seq)
                    }
                )
            }
        }
    }
}

@Composable
fun PatternLockPad(
    onPatternFinished: (String) -> Unit,
    modifier: Modifier = Modifier,
    gridSize: Dp = 250.dp,
    resetKey: Int = 0
) {
    val haptic = LocalHapticFeedback.current
    val selectedNodes = remember(resetKey) { mutableStateListOf<Int>() }
    var dragPosition by remember(resetKey) { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var localHint by remember(resetKey) { mutableStateOf<String?>(null) }

    fun nodeCenter(index: Int, size: IntSize): Offset {
        val row = index / 3
        val col = index % 3
        val pad = size.width * 0.17f
        val step = (size.width - pad * 2f) / 2f
        return Offset(pad + col * step, pad + row * step)
    }

    fun tryAppendNode(idx: Int) {
        if (selectedNodes.contains(idx)) return
        // Auto-include skipped midpoint on horizontal/vertical/diagonal 3x3 jump
        val prev = selectedNodes.lastOrNull()
        if (prev != null) {
            val mid = intermediatePatternNode(prev, idx)
            if (mid != null && !selectedNodes.contains(mid)) {
                selectedNodes.add(mid)
            }
        }
        selectedNodes.add(idx)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val palette = LocalResolveExtendedPalette.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(gridSize)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.cardBottom)
                .border(1.dp, palette.cardBorder, RoundedCornerShape(18.dp))
                .onSizeChanged { canvasSize = it }
                .pointerInput(resetKey) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            selectedNodes.clear()
                            localHint = null
                            dragPosition = startOffset
                            for (i in 0..8) {
                                val c = nodeCenter(i, canvasSize)
                                val hitRadius = canvasSize.width * 0.12f
                                if (hypot(startOffset.x - c.x, startOffset.y - c.y) <= hitRadius) {
                                    tryAppendNode(i)
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val pos = change.position
                            dragPosition = pos
                            val hitRadius = canvasSize.width * 0.115f
                            for (i in 0..8) {
                                val c = nodeCenter(i, canvasSize)
                                if (hypot(pos.x - c.x, pos.y - c.y) <= hitRadius) {
                                    tryAppendNode(i)
                                }
                            }
                        },
                        onDragEnd = {
                            dragPosition = null
                            if (selectedNodes.size >= AppLockCredentialManager.MIN_PATTERN_NODES) {
                                val result = AppLockCredentialManager.formatSequence(selectedNodes.toList())
                                onPatternFinished(result)
                                selectedNodes.clear()
                            } else if (selectedNodes.isNotEmpty()) {
                                localHint = "Connect at least ${AppLockCredentialManager.MIN_PATTERN_NODES} dots"
                                selectedNodes.clear()
                            }
                        },
                        onDragCancel = {
                            dragPosition = null
                            selectedNodes.clear()
                        }
                    )
                }
                .testTag("applock_pattern_canvas")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sz = IntSize(size.width.toInt(), size.height.toInt())
                // Draw connected segments
                if (selectedNodes.size >= 2) {
                    for (i in 0 until selectedNodes.size - 1) {
                        val a = nodeCenter(selectedNodes[i], sz)
                        val b = nodeCenter(selectedNodes[i + 1], sz)
                        drawLine(
                            color = palette.accentStrong,
                            start = a,
                            end = b,
                            strokeWidth = size.width * 0.028f,
                            cap = StrokeCap.Round
                        )
                    }
                }
                val last = selectedNodes.lastOrNull()
                val finger = dragPosition
                if (last != null && finger != null) {
                    drawLine(
                        color = palette.accentStrong.copy(alpha = 0.65f),
                        start = nodeCenter(last, sz),
                        end = finger,
                        strokeWidth = size.width * 0.020f,
                        cap = StrokeCap.Round
                    )
                }

                // Draw 9 dots
                for (i in 0..8) {
                    val center = nodeCenter(i, sz)
                    val active = selectedNodes.contains(i)
                    if (active) {
                        drawCircle(
                            color = palette.accentSoftContainer,
                            radius = size.width * 0.072f,
                            center = center
                        )
                        drawCircle(
                            color = palette.accentStrong,
                            radius = size.width * 0.072f,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = size.width * 0.012f,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                    drawCircle(
                        color = if (active) palette.accentStrong else palette.textSecondary.copy(alpha = 0.65f),
                        radius = size.width * if (active) 0.030f else 0.022f,
                        center = center
                    )
                }
            }
        }

        Text(
            text = localHint ?: "Drag across at least 4 dots",
            style = MaterialTheme.typography.labelSmall,
            color = if (localHint != null) palette.warn else palette.textSecondary
        )
    }
}

private fun intermediatePatternNode(a: Int, b: Int): Int? {
    val ra = a / 3
    val ca = a % 3
    val rb = b / 3
    val cb = b % 3
    val dr = kotlin.math.abs(ra - rb)
    val dc = kotlin.math.abs(ca - cb)
    return if ((dr == 0 && dc == 2) || (dc == 0 && dr == 2) || (dr == 2 && dc == 2)) {
        val rm = (ra + rb) / 2
        val cm = (ca + cb) / 2
        rm * 3 + cm
    } else null
}

@Composable
fun PinLockPad(
    digitsCount: Int,
    onPinComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    resetKey: Int = 0,
    compactMode: Boolean = false
) {
    val palette = LocalResolveExtendedPalette.current
    val haptic = LocalHapticFeedback.current
    var enteredPin by remember(resetKey, digitsCount) { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 10.dp else 14.dp)
    ) {
        // PIN Dot indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("applock_pin_dots")
        ) {
            repeat(digitsCount) { idx ->
                val filled = idx < enteredPin.length
                val dotColor by animateColorAsState(
                    targetValue = if (filled) palette.accent else palette.cardBottom,
                    animationSpec = tween(140),
                    label = "pin_dot_$idx"
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                        .border(
                            1.5.dp,
                            if (filled) palette.accentStrong else palette.cardBorder,
                            CircleShape
                        )
                )
            }
        }

        val buttonHeight = if (compactMode) 48.dp else 56.dp
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("CLR", "0", "DEL")
        )

        Column(
            modifier = Modifier.widthIn(max = 264.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(buttonHeight)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (key == "CLR" || key == "DEL") {
                                        palette.background.copy(alpha = 0.72f)
                                    } else {
                                        palette.cardTop.copy(alpha = 0.92f)
                                    }
                                )
                                .border(
                                    1.dp,
                                    palette.cardBorder,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable(role = Role.Button) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    when (key) {
                                        "CLR" -> enteredPin = ""
                                        "DEL" -> if (enteredPin.isNotEmpty()) {
                                            enteredPin = enteredPin.dropLast(1)
                                        }
                                        else -> {
                                            if (enteredPin.length < digitsCount) {
                                                val next = enteredPin + key
                                                enteredPin = next
                                                if (next.length == digitsCount) {
                                                    onPinComplete(next)
                                                    enteredPin = ""
                                                }
                                            }
                                        }
                                    }
                                }
                                .semantics {
                                    contentDescription = when (key) {
                                        "CLR" -> "Clear PIN"
                                        "DEL" -> "Delete last digit"
                                        else -> "Digit $key"
                                    }
                                }
                                .testTag("applock_pin_key_$key"),
                            contentAlignment = Alignment.Center
                        ) {
                            when (key) {
                                "DEL" -> Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = null,
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                "CLR" -> Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.textSecondary
                                )
                                else -> Text(
                                    text = key,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = palette.textPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Six-Cell Sequential Lock:
 * - Setup Mode ([showSequenceNumbers] = true): shows cell indices & order badges (`1, 2, 3...`)
 * - Unlock Mode ([showSequenceNumbers] = false): hides ALL sequence numbers, digits, and order
 *   indicators. Displays only the 6 forest-glass cells with subtle transient tap feedback.
 */
@Composable
fun SixCellSequentialPad(
    showSequenceNumbers: Boolean,
    onSequenceSubmitted: (String) -> Unit,
    modifier: Modifier = Modifier,
    resetKey: Int = 0,
    compactMode: Boolean = false
) {
    val palette = LocalResolveExtendedPalette.current
    val haptic = LocalHapticFeedback.current
    val tappedOrder = remember(resetKey) { mutableStateListOf<Int>() }
    var lastTransientPulseCell by remember(resetKey) { mutableStateOf<Int?>(null) }

    LaunchedEffect(lastTransientPulseCell) {
        if (lastTransientPulseCell != null) {
            delay(210L)
            lastTransientPulseCell = null
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 10.dp else 14.dp)
    ) {
        val cellHeight = if (compactMode) 56.dp else 72.dp
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (row in 0 until 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (col in 0 until 3) {
                        val cellIndex = row * 3 + col
                        val orderPosition = tappedOrder.indexOf(cellIndex)
                        val isSelectedInSetup = showSequenceNumbers && orderPosition >= 0
                        val isPulsing = !showSequenceNumbers && lastTransientPulseCell == cellIndex

                        val cellBg by animateColorAsState(
                            targetValue = when {
                                isSelectedInSetup -> palette.surfaceElevated
                                isPulsing -> palette.primaryButton.copy(alpha = 0.45f)
                                else -> palette.cardBottom
                            },
                            animationSpec = tween(140),
                            label = "six_cell_bg_$cellIndex"
                        )
                        val cellBorder by animateColorAsState(
                            targetValue = when {
                                isSelectedInSetup || isPulsing -> palette.accentSecondary
                                else -> palette.cardBorder
                            },
                            animationSpec = tween(140),
                            label = "six_cell_border_$cellIndex"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(cellHeight)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(cellBg)
                                .border(1.5.dp, cellBorder, RoundedCornerShape(16.dp))
                                .clickable(role = Role.Button) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (!tappedOrder.contains(cellIndex) && tappedOrder.size < 6) {
                                        tappedOrder.add(cellIndex)
                                    }
                                    if (!showSequenceNumbers) {
                                        lastTransientPulseCell = cellIndex
                                    }
                                }
                                .semantics {
                                    contentDescription = if (showSequenceNumbers) {
                                        "Cell ${cellIndex + 1}"
                                    } else {
                                        "Lock grid cell"
                                    }
                                }
                                .testTag("applock_six_cell_$cellIndex"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (showSequenceNumbers) {
                                // Visible numbers & tap-order badge ONLY in Setup mode
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${cellIndex + 1}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = if (isSelectedInSetup) palette.accentStrong else palette.textSecondary
                                    )
                                    if (orderPosition >= 0) {
                                        Text(
                                            text = "Step ${orderPosition + 1}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = palette.accent
                                        )
                                    }
                                }
                            } else {
                                // Unlock mode: strictly clean unmarked cells with subtle central dot cue only
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isPulsing) palette.accentStrong
                                            else palette.cardBorder.copy(alpha = 0.7f)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showSequenceNumbers) {
            Text(
                text = "Tap ${AppLockCredentialManager.MIN_SIX_CELL_SEQUENCE}–6 cells in order (${tappedOrder.size}/6 selected)",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                modifier = Modifier.testTag("applock_six_cell_setup_hint")
            )
        }

        Row(
            modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { tappedOrder.clear() },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("applock_six_cell_clear")
            ) {
                Text("Clear", color = palette.textSecondary)
            }
            Button(
                enabled = tappedOrder.size >= AppLockCredentialManager.MIN_SIX_CELL_SEQUENCE,
                onClick = {
                    val seq = AppLockCredentialManager.formatSequence(tappedOrder.toList())
                    tappedOrder.clear()
                    onSequenceSubmitted(seq)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primaryButton,
                    contentColor = palette.onPrimaryButton
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1.3f)
                    .minimumInteractiveComponentSize()
                    .testTag("applock_six_cell_submit")
            ) {
                Text(if (showSequenceNumbers) "Confirm Sequence" else "Unlock")
            }
        }
    }
}
