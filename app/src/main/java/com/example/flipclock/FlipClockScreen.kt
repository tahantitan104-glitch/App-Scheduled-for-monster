package com.example.flipclock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.designsystem.OutfitTimerFamily
import com.example.designsystem.ResolveColors
import com.example.domain.model.FlipClockPage
import com.example.domain.model.FlipClockSettings
import com.example.domain.model.FlipStopwatchState
import com.example.domain.model.FlipTimerPreset
import com.example.domain.model.FlipTimerState
import com.example.presentation.components.ResolveIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun FlipClockScreen(
    manager: FlipClockManager,
    initialPage: FlipClockPage,
    isRestrictedLock: Boolean,
    initialBrightnessPercent: Float,
    controlsVisible: Boolean,
    onControlsVisibleChange: (Boolean) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onRequestTimerStartWithNotificationCheck: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val settings by manager.settings.collectAsStateWithLifecycle()
    val presets by manager.presets.collectAsStateWithLifecycle()
    val timerState by manager.timerState.collectAsStateWithLifecycle()
    val timerRemainingMs by manager.timerRemainingMs.collectAsStateWithLifecycle()
    val timerFinishedBannerVisible by manager.timerFinishedBannerVisible.collectAsStateWithLifecycle()
    val stopwatchState by manager.stopwatchState.collectAsStateWithLifecycle()
    val stopwatchElapsedMs by manager.stopwatchElapsedMs.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(
        initialPage = initialPage.ordinal,
        pageCount = { FlipClockPage.entries.size }
    )
    LaunchedEffect(initialPage) {
        if (pagerState.currentPage != initialPage.ordinal) {
            pagerState.scrollToPage(initialPage.ordinal)
        }
    }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showCustomDurationDialog by remember { mutableStateOf(false) }
    var showResetTimerConfirm by remember { mutableStateOf(false) }
    var pendingPresetSwitch by remember { mutableStateOf<FlipTimerPreset?>(null) }

    var liveZoomFraction by remember(settings.zoomFraction) {
        mutableFloatStateOf(settings.clampedZoomFraction)
    }
    var liveBrightnessPercent by remember(initialBrightnessPercent) {
        mutableFloatStateOf(
            initialBrightnessPercent.coerceIn(
                FlipClockSettings.MIN_BRIGHTNESS_PERCENT,
                FlipClockSettings.MAX_BRIGHTNESS_PERCENT
            )
        )
    }
    var isAdjustingBrightness by remember { mutableStateOf(false) }
    var interactionPulse by remember { mutableStateOf(0) }

    // Pause 5-second auto-hide while a sheet or confirmation dialog is active
    val isOverlayPaused = showSettingsSheet ||
        showCustomDurationDialog ||
        showResetTimerConfirm ||
        pendingPresetSwitch != null ||
        isAdjustingBrightness

    LaunchedEffect(controlsVisible, interactionPulse, isOverlayPaused) {
        if (controlsVisible && !isOverlayPaused) {
            delay(5_000L)
            onControlsVisibleChange(false)
        }
    }

    // Real system clock tick for Clock page
    var currentDateTime by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentDateTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(System.currentTimeMillis()),
                ZoneId.systemDefault()
            )
            val msIntoSecond = System.currentTimeMillis() % 1000L
            delay((1000L - msIntoSecond).coerceIn(100L, 1000L))
        }
    }

    val latestControlsVisible by rememberUpdatedState(controlsVisible)
    val latestZoom by rememberUpdatedState(liveZoomFraction)
    val latestBrightness by rememberUpdatedState(liveBrightnessPercent)
    val latestPage by rememberUpdatedState(FlipClockPage.entries[pagerState.currentPage])
    val density = LocalDensity.current
    val longPressTimeoutMs = 300L

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FlipClockPalette.CanvasBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                val upSwipeThresholdPx = with(density) { 64.dp.toPx() }
                val brightnessDragPerPercentPx = with(density) { 4.2.dp.toPx() }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = System.currentTimeMillis()
                    var totalDx = 0f
                    var totalDy = 0f
                    var didMovePastSlop = false
                    var pinching = false
                    var brightnessActive = false

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2) {
                            pinching = true
                            val zoomChange = event.calculateZoom()
                            if (abs(zoomChange - 1f) > 0.001f) {
                                val nextZoom = (latestZoom * zoomChange).coerceIn(
                                    FlipClockSettings.MIN_ZOOM_FRACTION,
                                    FlipClockSettings.MAX_ZOOM_FRACTION
                                )
                                liveZoomFraction = nextZoom
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            continue
                        }

                        if (pinching) {
                            // Wait for remaining fingers to lift cleanly
                            continue
                        }

                        val change = pressed.first()
                        val delta = change.position - change.previousPosition
                        totalDx += delta.x
                        totalDy += delta.y

                        if (!didMovePastSlop && (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop)) {
                            didMovePastSlop = true
                        }

                        if (!brightnessActive &&
                            !didMovePastSlop &&
                            System.currentTimeMillis() - downTime >= longPressTimeoutMs
                        ) {
                            brightnessActive = true
                            isAdjustingBrightness = true
                        }

                        if (brightnessActive && abs(delta.y) > 0.2f) {
                            // Low sensitivity upward increases brightness, downward decreases
                            val deltaPercent = -delta.y / brightnessDragPerPercentPx
                            val nextBrightness = (latestBrightness + deltaPercent).coerceIn(
                                FlipClockSettings.MIN_BRIGHTNESS_PERCENT,
                                FlipClockSettings.MAX_BRIGHTNESS_PERCENT
                            )
                            liveBrightnessPercent = nextBrightness
                            onBrightnessChanged(nextBrightness)
                            change.consume()
                        } else if (!brightnessActive &&
                            !change.isConsumed &&
                            latestPage != FlipClockPage.TIMER &&
                            abs(totalDy) > abs(totalDx) * 1.35f &&
                            totalDy < -upSwipeThresholdPx
                        ) {
                            // Upward swipe opens Settings sheet on Clock & Stopwatch pages;
                            // Timer page reserves vertical scrolling for browsing presets below the display.
                            change.consume()
                            showSettingsSheet = true
                            break
                        }
                    }

                    if (pinching) {
                        val committedZoom = latestZoom
                        scope.launch { manager.setZoomFraction(committedZoom) }
                    } else if (brightnessActive) {
                        isAdjustingBrightness = false
                        val committedBrightness = latestBrightness
                        onBrightnessChanged(committedBrightness)
                        scope.launch { manager.saveLastBrightnessPercent(committedBrightness) }
                    } else if (!didMovePastSlop && !down.isConsumed) {
                        val nextVisible = !latestControlsVisible
                        onControlsVisibleChange(nextVisible)
                        if (nextVisible) interactionPulse++
                    }
                }
            }
            .testTag("flip_clock_screen")
    ) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            userScrollEnabled = !isAdjustingBrightness && !showSettingsSheet,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            when (FlipClockPage.entries[pageIndex]) {
                FlipClockPage.TIMER -> {
                    FlipTimerPage(
                        presets = presets,
                        timerState = timerState,
                        remainingMs = timerRemainingMs,
                        finishedBannerVisible = timerFinishedBannerVisible,
                        zoomFraction = liveZoomFraction,
                        controlsVisible = controlsVisible,
                        onTapDisplayToggle = {
                            interactionPulse++
                            scope.launch {
                                if (timerState.isRunning && !timerState.isPaused) {
                                    manager.pauseTimer()
                                } else {
                                    onRequestTimerStartWithNotificationCheck()
                                }
                            }
                        },
                        onStartOrResume = {
                            interactionPulse++
                            onRequestTimerStartWithNotificationCheck()
                        },
                        onPause = {
                            interactionPulse++
                            scope.launch { manager.pauseTimer() }
                        },
                        onRequestReset = {
                            interactionPulse++
                            showResetTimerConfirm = true
                        },
                        onSelectPreset = { preset ->
                            interactionPulse++
                            if (timerState.isRunning || timerState.isPaused) {
                                pendingPresetSwitch = preset
                            } else {
                                scope.launch { manager.selectTimerPreset(preset) }
                            }
                        },
                        onOpenCustomDuration = {
                            interactionPulse++
                            showCustomDurationDialog = true
                        },
                        onOpenAppNotificationSettings = onOpenAppNotificationSettings
                    )
                }

                FlipClockPage.CLOCK -> {
                    FlipClockFacePage(
                        dateTime = currentDateTime,
                        settings = settings,
                        zoomFraction = liveZoomFraction
                    )
                }

                FlipClockPage.STOPWATCH -> {
                    FlipStopwatchPage(
                        stopwatchState = stopwatchState,
                        elapsedMs = stopwatchElapsedMs,
                        zoomFraction = liveZoomFraction,
                        controlsVisible = controlsVisible,
                        onToggleDisplay = {
                            interactionPulse++
                            scope.launch { manager.toggleStopwatch() }
                        },
                        onStartOrResume = {
                            interactionPulse++
                            scope.launch { manager.startOrResumeStopwatch() }
                        },
                        onPause = {
                            interactionPulse++
                            scope.launch { manager.pauseStopwatch() }
                        },
                        onResetImmediate = {
                            interactionPulse++
                            scope.launch { manager.resetStopwatch() }
                        }
                    )
                }
            }
        }

        // Brightness Indicator (Sun icon + progress bar ONLY, no percentage text)
        AnimatedVisibility(
            visible = isAdjustingBrightness,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(FlipClockPalette.PanelDark.copy(alpha = 0.92f))
                    .border(1.dp, FlipClockPalette.PanelOutline, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("flip_brightness_indicator"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = ResolveIcons.BrightnessHigh,
                    contentDescription = "Brightness",
                    tint = FlipClockPalette.AccentSoft,
                    modifier = Modifier.size(20.dp)
                )
                val normalized = (
                    (liveBrightnessPercent - FlipClockSettings.MIN_BRIGHTNESS_PERCENT) /
                        (FlipClockSettings.MAX_BRIGHTNESS_PERCENT - FlipClockSettings.MIN_BRIGHTNESS_PERCENT)
                    ).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { normalized },
                    color = FlipClockPalette.AccentSoft,
                    trackColor = FlipClockPalette.PanelOutline,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .width(128.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                )
            }
        }

        // Overlay top chrome (initial hidden, tap canvas to show, auto-hide after 5s)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("flip_overlay_top_bar"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    onClick = onExit,
                    shape = CircleShape,
                    color = FlipClockPalette.PanelDark.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, FlipClockPalette.PanelOutline),
                    modifier = Modifier.testTag("flip_exit_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isRestrictedLock) Icons.Outlined.Lock else Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = if (isRestrictedLock) "Return to Lock Screen" else "Exit Flip Clock",
                            tint = FlipClockPalette.AccentSoft,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isRestrictedLock) "Lock Screen" else "Exit",
                            style = MaterialTheme.typography.labelLarge,
                            color = FlipClockPalette.AccentSoft
                        )
                    }
                }

                // Page switcher pills: Timer | Clock | Stopwatch (non-circular)
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(FlipClockPalette.PanelDark.copy(alpha = 0.88f))
                        .border(1.dp, FlipClockPalette.PanelOutline, CircleShape)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    FlipClockPage.entries.forEachIndexed { idx, page ->
                        val selected = pagerState.currentPage == idx
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (selected) FlipClockPalette.PanelElevated else Color.Transparent
                                )
                                .clickable {
                                    interactionPulse++
                                    scope.launch { pagerState.animateScrollToPage(idx) }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("flip_page_pill_${page.name.lowercase()}")
                        ) {
                            Text(
                                text = when (page) {
                                    FlipClockPage.TIMER -> "Timer"
                                    FlipClockPage.CLOCK -> "Clock"
                                    FlipClockPage.STOPWATCH -> "Stopwatch"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) FlipClockPalette.AccentSoft else FlipClockPalette.SubTextGrey
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        interactionPulse++
                        showSettingsSheet = true
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(FlipClockPalette.PanelDark.copy(alpha = 0.88f))
                        .border(1.dp, FlipClockPalette.PanelOutline, CircleShape)
                        .testTag("flip_settings_open_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Flip Clock Settings",
                        tint = FlipClockPalette.AccentSoft
                    )
                }
            }
        }
    }

    if (showSettingsSheet) {
        FlipClockSettingsSheet(
            settings = settings,
            presets = presets,
            onDismiss = {
                showSettingsSheet = false
                interactionPulse++
            },
            onSetUse24Hour = { enabled -> scope.launch { manager.setUse24HourFormat(enabled) } },
            onSetKeepScreenAwake = { enabled -> scope.launch { manager.setKeepScreenAwake(enabled) } },
            onSetShowDate = { enabled -> scope.launch { manager.setShowDate(enabled) } },
            onSetAutoRepeat = { enabled -> scope.launch { manager.setAutoRepeat(enabled) } },
            onResetZoom = { scope.launch { manager.resetZoom() } },
            onSavePreset = { presetId, name, totalSeconds ->
                if (presetId == null && presets.size >= FlipTimerPreset.MAX_TOTAL_PRESETS) {
                    "Maximum ${FlipTimerPreset.MAX_TOTAL_PRESETS} timer presets allowed."
                } else if (totalSeconds <= 0L) {
                    "Duration must be at least 1 second."
                } else if (name.isBlank()) {
                    "Enter a preset name."
                } else {
                    scope.launch { manager.saveTimerPreset(presetId, name, totalSeconds) }
                    null
                }
            },
            onDeletePreset = { presetId -> scope.launch { manager.deleteTimerPreset(presetId) } },
            onResetSettingsOnly = { scope.launch { manager.resetSettingsToDefaultPreservingCustomPresets() } }
        )
    }

    if (showCustomDurationDialog) {
        FlipPresetEditorDialog(
            title = "Custom Timer Duration",
            initialName = "",
            initialSeconds = (timerState.selectedDurationMs / 1_000L).coerceAtLeast(60L),
            requireName = false,
            onDismiss = { showCustomDurationDialog = false },
            onConfirm = { label, totalSeconds ->
                showCustomDurationDialog = false
                if (timerState.isRunning || timerState.isPaused) {
                    scope.launch { manager.selectCustomDuration(totalSeconds, label) }
                } else {
                    scope.launch { manager.selectCustomDuration(totalSeconds, label) }
                }
            }
        )
    }

    if (showResetTimerConfirm) {
        AlertDialog(
            onDismissRequest = { showResetTimerConfirm = false },
            containerColor = FlipClockPalette.PanelDark,
            titleContentColor = FlipClockPalette.AccentSoft,
            textContentColor = FlipClockPalette.SubTextGrey,
            title = { Text("Reset Timer?") },
            text = { Text("Reset ${timerState.timerName} back to its full duration?") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetTimerConfirm = false
                        scope.launch { manager.resetTimer() }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ResolveColors.MintSoftContainer,
                        contentColor = ResolveColors.MintBright
                    ),
                    modifier = Modifier.testTag("flip_timer_reset_confirm_btn")
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetTimerConfirm = false }) {
                    Text("Cancel", color = FlipClockPalette.SubTextGrey)
                }
            }
        )
    }

    pendingPresetSwitch?.let { candidate ->
        AlertDialog(
            onDismissRequest = { pendingPresetSwitch = null },
            containerColor = FlipClockPalette.PanelDark,
            titleContentColor = FlipClockPalette.AccentSoft,
            textContentColor = FlipClockPalette.SubTextGrey,
            title = { Text("Switch Timer Preset?") },
            text = {
                Text("Stop the active timer and load \"${candidate.name}\"? You will still press Start manually.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val chosen = candidate
                        pendingPresetSwitch = null
                        scope.launch { manager.selectTimerPreset(chosen) }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ResolveColors.MintSoftContainer,
                        contentColor = ResolveColors.MintBright
                    ),
                    modifier = Modifier.testTag("flip_timer_switch_confirm_btn")
                ) {
                    Text("Switch")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPresetSwitch = null }) {
                    Text("Keep Current", color = FlipClockPalette.SubTextGrey)
                }
            }
        )
    }
}

@Composable
private fun FlipClockFacePage(
    dateTime: ZonedDateTime,
    settings: FlipClockSettings,
    zoomFraction: Float
) {
    val hourValue = if (settings.use24HourFormat) {
        "%02d".format(dateTime.hour)
    } else {
        val h12 = dateTime.hour % 12
        "%02d".format(if (h12 == 0) 12 else h12)
    }
    val minuteValue = "%02d".format(dateTime.minute)
    val secondValue = "%02d".format(dateTime.second)
    val amPmText = if (dateTime.hour < 12) "AM" else "PM"
    val dateLineText = remember(dateTime.toLocalDate()) {
        dateTime.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())).uppercase(Locale.getDefault())
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("flip_clock_face_page"),
        contentAlignment = Alignment.Center
    ) {
        val isLandscape = maxWidth > maxHeight
        val scale = zoomFraction.coerceIn(
            FlipClockSettings.MIN_ZOOM_FRACTION,
            FlipClockSettings.MAX_ZOOM_FRACTION
        )

        if (isLandscape) {
            val baseHeight = min(maxHeight * 0.62f, maxWidth * 0.34f).coerceIn(130.dp, 260.dp)
            val baseWidth = (baseHeight * 0.92f).coerceAtLeast(130.dp)
            val digitFontSize = (baseHeight.value * 0.56f).sp

            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .testTag("flip_clock_landscape_cluster"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (settings.showDate) {
                    Text(
                        text = dateLineText,
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 1.8.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = FlipClockPalette.SubTextGrey,
                        modifier = Modifier.testTag("flip_clock_date_text")
                    )
                }

                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SplitFlipCard(
                            value = hourValue,
                            cardWidth = baseWidth,
                            cardHeight = baseHeight,
                            fontSize = digitFontSize,
                            modifier = Modifier.testTag("flip_clock_hour_card")
                        )
                        if (!settings.use24HourFormat) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = amPmText,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = OutfitTimerFamily,
                                    letterSpacing = 1.4.sp
                                ),
                                color = FlipClockPalette.SubTextGrey,
                                modifier = Modifier.testTag("flip_clock_ampm_label")
                            )
                        }
                    }

                    // Static non-blinking colon
                    Box(
                        modifier = Modifier.height(baseHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ":",
                            fontFamily = OutfitTimerFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = digitFontSize,
                            color = FlipClockPalette.SubTextGrey,
                            modifier = Modifier.testTag("flip_clock_colon")
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SplitFlipCard(
                            value = minuteValue,
                            cardWidth = baseWidth,
                            cardHeight = baseHeight,
                            fontSize = digitFontSize,
                            modifier = Modifier.testTag("flip_clock_minute_card")
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = secondValue,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = OutfitTimerFamily,
                                letterSpacing = 1.2.sp,
                                fontFeatureSettings = "tnum"
                            ),
                            color = FlipClockPalette.SubTextGrey,
                            modifier = Modifier.testTag("flip_clock_seconds_label")
                        )
                    }
                }
            }
        } else {
            val baseHeight = min(maxHeight * 0.27f, maxWidth * 0.56f).coerceIn(130.dp, 240.dp)
            val baseWidth = (baseHeight * 0.94f).coerceAtLeast(130.dp)
            val digitFontSize = (baseHeight.value * 0.56f).sp

            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .testTag("flip_clock_portrait_cluster"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SplitFlipCard(
                    value = hourValue,
                    cardWidth = baseWidth,
                    cardHeight = baseHeight,
                    fontSize = digitFontSize,
                    modifier = Modifier.testTag("flip_clock_hour_card")
                )

                if (!settings.use24HourFormat) {
                    Text(
                        text = amPmText,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = OutfitTimerFamily,
                            letterSpacing = 1.6.sp
                        ),
                        color = FlipClockPalette.SubTextGrey,
                        modifier = Modifier.testTag("flip_clock_ampm_label")
                    )
                }

                if (settings.showDate) {
                    Text(
                        text = dateLineText,
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                        color = FlipClockPalette.SubTextGrey,
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .testTag("flip_clock_date_text")
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                }

                SplitFlipCard(
                    value = minuteValue,
                    cardWidth = baseWidth,
                    cardHeight = baseHeight,
                    fontSize = digitFontSize,
                    modifier = Modifier.testTag("flip_clock_minute_card")
                )

                Text(
                    text = secondValue,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = OutfitTimerFamily,
                        letterSpacing = 1.4.sp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = FlipClockPalette.SubTextGrey,
                    modifier = Modifier.testTag("flip_clock_seconds_label")
                )
            }
        }
    }
}

@Composable
private fun FlipTimerPage(
    presets: List<FlipTimerPreset>,
    timerState: FlipTimerState,
    remainingMs: Long,
    finishedBannerVisible: Boolean,
    zoomFraction: Float,
    controlsVisible: Boolean,
    onTapDisplayToggle: () -> Unit,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onRequestReset: () -> Unit,
    onSelectPreset: (FlipTimerPreset) -> Unit,
    onOpenCustomDuration: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit
) {
    val totalSeconds = ((remainingMs + 999L) / 1000L).coerceAtLeast(0L)
    val hours = (totalSeconds / 3600L).toInt()
    val minutes = ((totalSeconds % 3600L) / 60L).toInt()
    val seconds = (totalSeconds % 60L).toInt()
    val listState = rememberLazyListState()
    val timerScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("flip_timer_page")
    ) {
        val boxMaxWidth = maxWidth
        val boxMaxHeight = maxHeight
        val isLandscape = boxMaxWidth > boxMaxHeight
        val scale = zoomFraction.coerceIn(
            FlipClockSettings.MIN_ZOOM_FRACTION,
            FlipClockSettings.MAX_ZOOM_FRACTION
        )
        val heroCardHeight = if (isLandscape) {
            min(boxMaxHeight * 0.48f, boxMaxWidth * 0.25f).coerceIn(106.dp, 188.dp)
        } else {
            min(boxMaxWidth * 0.31f, boxMaxHeight * 0.24f).coerceIn(96.dp, 168.dp)
        }

        // Initial viewport shows ONLY the large split-flap Timer Display (+ playback overlay when shown).
        // Scrolling vertically reveals Custom Duration and all Timer Presets below cleanly.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("flip_timer_preset_list"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item(key = "timer_hero_viewport") {
                Box(
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        TimerDisplayCluster(
                            hours = hours,
                            minutes = minutes,
                            seconds = seconds,
                            timerState = timerState,
                            finishedBannerVisible = finishedBannerVisible,
                            cardHeight = heroCardHeight,
                            scale = scale,
                            onTapDisplayToggle = onTapDisplayToggle
                        )

                        if (timerState.notificationPermissionDeniedNotice) {
                            Spacer(Modifier.height(10.dp))
                            NotificationDeniedNotice(onOpenAppSettings = onOpenAppNotificationSettings)
                        }

                        AnimatedVisibility(visible = controlsVisible) {
                            HorizontalFloatingPlaybackButtons(
                                isRunning = timerState.isRunning && !timerState.isPaused,
                                isPaused = timerState.isPaused,
                                onStartOrResume = onStartOrResume,
                                onPause = onPause,
                                onReset = onRequestReset,
                                startTag = "flip_timer_start_btn",
                                pauseTag = "flip_timer_pause_btn",
                                resetTag = "flip_timer_reset_btn",
                                modifier = Modifier.padding(top = 18.dp)
                            )
                        }
                    }

                    Text(
                        text = "Scroll down for timer durations & presets",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                        color = FlipClockPalette.SubTextGrey.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    )
                }
            }

            item(key = "presets_section_heading") {
                Text(
                    text = "TIMER PRESETS & CUSTOM DURATION",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                    color = FlipClockPalette.SubTextGrey,
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp)
                )
            }

            item(key = "custom_duration_btn") {
                Surface(
                    onClick = onOpenCustomDuration,
                    shape = RoundedCornerShape(14.dp),
                    color = FlipClockPalette.PanelElevated,
                    border = BorderStroke(1.dp, FlipClockPalette.PanelOutline),
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .testTag("flip_timer_custom_duration_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Custom Duration (H : M : S)",
                            style = MaterialTheme.typography.titleSmall,
                            color = ResolveColors.MintBright
                        )
                        Text(
                            text = "Pick",
                            style = MaterialTheme.typography.labelLarge,
                            color = ResolveColors.MintAccent
                        )
                    }
                }
            }

            items(presets, key = { it.presetId }) { preset ->
                val selected = preset.presetId == timerState.activePresetId
                Surface(
                    onClick = {
                        onSelectPreset(preset)
                        if (!timerState.isRunning && !timerState.isPaused) {
                            timerScope.launch { listState.animateScrollToItem(0) }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) ResolveColors.MintSoftContainer.copy(alpha = 0.55f) else FlipClockPalette.PanelDark,
                    border = BorderStroke(
                        1.dp,
                        if (selected) ResolveColors.MintAccent.copy(alpha = 0.55f) else FlipClockPalette.PanelOutline
                    ),
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .testTag("flip_preset_item_${preset.presetId}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = if (selected) ResolveColors.MintBright else FlipClockPalette.AccentSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatDurationWords(preset.durationSeconds),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) ResolveColors.MintAccent else FlipClockPalette.SubTextGrey
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerDisplayCluster(
    hours: Int,
    minutes: Int,
    seconds: Int,
    timerState: FlipTimerState,
    finishedBannerVisible: Boolean,
    cardHeight: Dp,
    scale: Float,
    onTapDisplayToggle: () -> Unit
) {
    val cardWidth = (cardHeight * 0.88f).coerceAtLeast(78.dp)
    val fontSize = (cardHeight.value * 0.54f).sp

    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(onClick = onTapDisplayToggle)
            .padding(8.dp)
            .testTag("flip_timer_display"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = timerState.timerName.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.4.sp),
            color = FlipClockPalette.SubTextGrey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (finishedBannerVisible) {
            Text(
                text = "${timerState.timerName} — Completed!",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ResolveColors.MintBright,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ResolveColors.MintSoftContainer)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("flip_timer_finished_banner")
            )
        }

        // Pure split-flip Timer cards with static ':' and NO progress bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hours > 0) {
                SplitFlipCard(
                    value = "%02d".format(hours),
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                    fontSize = fontSize,
                    cornerRadius = 18.dp
                )
                StaticColon(fontSize = fontSize)
            }
            SplitFlipCard(
                value = "%02d".format(minutes),
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                fontSize = fontSize,
                cornerRadius = 18.dp
            )
            StaticColon(fontSize = fontSize)
            SplitFlipCard(
                value = "%02d".format(seconds),
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                fontSize = fontSize,
                cornerRadius = 18.dp
            )
        }
    }
}


@Composable
private fun FlipStopwatchPage(
    stopwatchState: FlipStopwatchState,
    elapsedMs: Long,
    zoomFraction: Float,
    controlsVisible: Boolean,
    onToggleDisplay: () -> Unit,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onResetImmediate: () -> Unit
) {
    // MM:SS seconds only, no milliseconds, no laps
    val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
    val minutes = (totalSeconds / 60L).toInt()
    val seconds = (totalSeconds % 60L).toInt()
    val minStr = "%02d".format(minutes)
    val secStr = "%02d".format(seconds)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("flip_stopwatch_page")
    ) {
        val isLandscape = maxWidth > maxHeight
        val scale = zoomFraction.coerceIn(
            FlipClockSettings.MIN_ZOOM_FRACTION,
            FlipClockSettings.MAX_ZOOM_FRACTION
        )
        val cardHeight = if (isLandscape) {
            min(maxHeight * 0.54f, maxWidth * 0.30f).coerceIn(115.dp, 220.dp)
        } else {
            min(maxWidth * 0.36f, maxHeight * 0.25f).coerceIn(115.dp, 200.dp)
        }
        val cardWidth = (cardHeight * 0.92f).coerceAtLeast(110.dp)
        val fontSize = (cardHeight.value * 0.56f).sp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clickable(onClick = onToggleDisplay)
                    .padding(10.dp)
                    .testTag("flip_stopwatch_display"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "STOPWATCH",
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.6.sp),
                    color = FlipClockPalette.SubTextGrey
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SplitFlipCard(
                        value = minStr,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        fontSize = fontSize,
                        modifier = Modifier.testTag("flip_stopwatch_min_card")
                    )
                    StaticColon(fontSize = fontSize)
                    SplitFlipCard(
                        value = secStr,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        fontSize = fontSize,
                        modifier = Modifier.testTag("flip_stopwatch_sec_card")
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Horizontal floating buttons present in BOTH Portrait and Landscape, auto-hiding with controlsVisible
            AnimatedVisibility(visible = controlsVisible) {
                HorizontalFloatingPlaybackButtons(
                    isRunning = stopwatchState.isRunning,
                    isPaused = !stopwatchState.isRunning && elapsedMs > 0L,
                    onStartOrResume = onStartOrResume,
                    onPause = onPause,
                    onReset = onResetImmediate,
                    startTag = "flip_stopwatch_start_btn",
                    pauseTag = "flip_stopwatch_pause_btn",
                    resetTag = "flip_stopwatch_reset_btn"
                )
            }
        }
    }
}

@Composable
private fun StaticColon(fontSize: androidx.compose.ui.unit.TextUnit) {
    Text(
        text = ":",
        fontFamily = OutfitTimerFamily,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        color = FlipClockPalette.SubTextGrey
    )
}

@Composable
private fun HorizontalFloatingPlaybackButtons(
    isRunning: Boolean,
    isPaused: Boolean,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    startTag: String,
    pauseTag: String,
    resetTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(FlipClockPalette.PanelDark.copy(alpha = 0.90f))
            .border(1.dp, FlipClockPalette.PanelOutline, CircleShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isRunning) {
            Button(
                onClick = onPause,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FlipClockPalette.PanelElevated,
                    contentColor = FlipClockPalette.AccentSoft
                ),
                modifier = Modifier.testTag(pauseTag)
            ) {
                Icon(ResolveIcons.Pause, contentDescription = "Pause", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pause")
            }
        } else {
            Button(
                onClick = onStartOrResume,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ResolveColors.MintSoftContainer,
                    contentColor = ResolveColors.MintBright
                ),
                modifier = Modifier.testTag(startTag)
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isPaused) "Resume" else "Start")
            }
        }

        OutlinedButton(
            onClick = onReset,
            shape = CircleShape,
            border = BorderStroke(1.dp, FlipClockPalette.PanelOutline),
            modifier = Modifier.testTag(resetTag)
        ) {
            Icon(
                imageVector = ResolveIcons.Restart,
                contentDescription = "Reset",
                tint = FlipClockPalette.AccentSoft,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("Reset", color = FlipClockPalette.AccentSoft)
        }
    }
}

@Composable
private fun NotificationDeniedNotice(
    onOpenAppSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ResolveColors.AmberContainer)
            .border(1.dp, ResolveColors.AmberWarn.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("flip_notification_denied_notice"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Timer runs without completion notifications until enabled.",
            style = MaterialTheme.typography.bodySmall,
            color = ResolveColors.AmberWarn,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onOpenAppSettings) {
            Text("Settings", color = ResolveColors.MintBright)
        }
    }
}
