package com.example.flipclock

import android.os.SystemClock
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.designsystem.FlipClockLabelFamily
import com.example.designsystem.ResolveColors
import com.example.domain.model.FlipBatteryStyle
import com.example.domain.model.FlipClockCustomLayout
import com.example.domain.model.FlipClockElementAlign
import com.example.domain.model.FlipClockElementId
import com.example.domain.model.FlipClockOrientationLayout
import com.example.domain.model.FlipClockPage
import com.example.domain.model.FlipClockSettings
import com.example.domain.model.FlipDateFormat
import com.example.domain.model.FlipStopwatchState
import com.example.domain.model.FlipTimerPreset
import com.example.domain.model.FlipTimerState
import com.example.domain.model.FlipWeatherStyle
import com.example.domain.model.FlipWeekdayStyle
import com.example.presentation.components.ResolveIcons
import com.example.session.EngineStartResult
import androidx.compose.material.icons.outlined.Edit
import kotlin.math.roundToInt
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
    modifier: Modifier = Modifier,
    onSleepTimerExpired: () -> Unit = onExit
) {
    val scope = rememberCoroutineScope()
    val settings by manager.settings.collectAsStateWithLifecycle()
    val presets by manager.presets.collectAsStateWithLifecycle()
    val timerState by manager.timerState.collectAsStateWithLifecycle()
    val timerRemainingMs by manager.timerRemainingMs.collectAsStateWithLifecycle()
    val timerFinishedBannerVisible by manager.timerFinishedBannerVisible.collectAsStateWithLifecycle()
    val stopwatchState by manager.stopwatchState.collectAsStateWithLifecycle()
    val stopwatchElapsedMs by manager.stopwatchElapsedMs.collectAsStateWithLifecycle()
    val pomodoroStatus by manager.pomodoroStatus.collectAsStateWithLifecycle()
    val focusLockSession by manager.focusLockSession.collectAsStateWithLifecycle()
    val focusLockRemainingMs by manager.focusLockRemainingMs.collectAsStateWithLifecycle()

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
    var showWhiteNoiseStudio by remember { mutableStateOf(false) }
    var showCustomDurationDialog by remember { mutableStateOf(false) }
    var showResetTimerConfirm by remember { mutableStateOf(false) }
    var pendingPresetSwitch by remember { mutableStateOf<FlipTimerPreset?>(null) }

    var isLayoutEditMode by remember { mutableStateOf(false) }
    var currentCanvasIsLandscape by remember { mutableStateOf(false) }
    var selectedEditorElement by remember { mutableStateOf(FlipClockElementId.HOUR) }
    var draftCustomLayout by remember(settings.customLayout) { mutableStateOf(settings.customLayout) }
    var draftShowSeconds by remember(settings.showSeconds) { mutableStateOf(settings.showSeconds) }
    var draftShowDate by remember(settings.showDate) { mutableStateOf(settings.showDate) }
    var draftShowWeekday by remember(settings.showWeekday) { mutableStateOf(settings.showWeekday) }
    var draftShowWeather by remember(settings.showWeather) { mutableStateOf(settings.showWeather) }
    var draftShowBattery by remember(settings.showBattery) { mutableStateOf(settings.showBattery) }
    var draftDateFormatPattern by remember(settings.dateFormatPattern) { mutableStateOf(settings.dateFormatPattern) }

    fun enterLayoutEditMode() {
        draftCustomLayout = settings.customLayout
        draftShowSeconds = settings.showSeconds
        draftShowDate = settings.showDate
        draftShowWeekday = settings.showWeekday
        draftShowWeather = settings.showWeather
        draftShowBattery = settings.showBattery
        draftDateFormatPattern = settings.dateFormatPattern
        selectedEditorElement = FlipClockElementId.HOUR
        showSettingsSheet = false
        onControlsVisibleChange(false)
        isLayoutEditMode = true
        scope.launch { pagerState.animateScrollToPage(FlipClockPage.CLOCK.ordinal) }
    }

    BackHandler(enabled = isLayoutEditMode) {
        isLayoutEditMode = false
    }

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

    // Pause 5-second auto-hide while a sheet, editor, or confirmation dialog is active
    val isOverlayPaused = showSettingsSheet ||
        showWhiteNoiseStudio ||
        showCustomDurationDialog ||
        showResetTimerConfirm ||
        pendingPresetSwitch != null ||
        isAdjustingBrightness ||
        isLayoutEditMode

    LaunchedEffect(controlsVisible, interactionPulse, isOverlayPaused) {
        if (controlsVisible && !isOverlayPaused) {
            delay(5_000L)
            onControlsVisibleChange(false)
        }
    }

    val context = LocalContext.current
    val tickerAudioPlayer = remember(context) { FlipTickerAudioPlayer(context) }

    DisposableEffect(manager) {
        manager.setFlipClockUiOpen(true)
        onDispose { manager.setFlipClockUiOpen(false) }
    }

    LaunchedEffect(manager) {
        manager.pomodoroChimeEvents.collect {
            manager.ambientAudioEngine.playPomodoroChime()
        }
    }

    var oledShiftStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(settings.oledBurnInProtection) {
        if (!settings.oledBurnInProtection) {
            oledShiftStep = 0
            return@LaunchedEffect
        }
        while (true) {
            delay(60_000L)
            oledShiftStep = (oledShiftStep + 1) % 8
        }
    }
    val oledShiftDp = remember(settings.oledBurnInProtection, oledShiftStep) {
        if (!settings.oledBurnInProtection) {
            0 to 0
        } else {
            val offsets = listOf(
                -5 to -3, 0 to -5, 5 to -3, 6 to 0,
                5 to 3, 0 to 5, -5 to 3, -6 to 0
            )
            offsets[oledShiftStep % offsets.size]
        }
    }

    // Real system clock tick for Clock page
    var currentDateTime by remember { mutableStateOf(ZonedDateTime.now()) }
    var didEmitInitialClockSample by remember { mutableStateOf(false) }

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

    // Play subtle mechanical tick sound on Clock second change when enabled
    LaunchedEffect(currentDateTime.second, settings.clockTickerSoundEnabled, pagerState.currentPage) {
        if (!didEmitInitialClockSample) {
            didEmitInitialClockSample = true
        } else if (settings.clockTickerSoundEnabled &&
            pagerState.currentPage == FlipClockPage.CLOCK.ordinal
        ) {
            tickerAudioPlayer.playTickIfPermitted()
        }
    }

    // Sleep timer countdown (local to active Flip Clock session)
    var sleepDeadlineElapsedMs by remember(settings.sleepTimerMinutes) {
        mutableStateOf(
            if (settings.sleepTimerMinutes > 0) {
                SystemClock.elapsedRealtime() + settings.sleepTimerMinutes * 60_000L
            } else {
                null
            }
        )
    }
    var sleepRemainingSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sleepDeadlineElapsedMs) {
        val deadline = sleepDeadlineElapsedMs
        if (deadline == null) {
            sleepRemainingSeconds = 0L
            return@LaunchedEffect
        }
        while (true) {
            val remMs = deadline - SystemClock.elapsedRealtime()
            if (remMs <= 0L) {
                sleepRemainingSeconds = 0L
                manager.ambientAudioEngine.setSleepFadeGain(0f)
                manager.setSleepTimerMinutes(0)
                manager.stopAmbientSound()
                manager.ambientAudioEngine.setSleepFadeGain(1f)
                onSleepTimerExpired()
                break
            }
            if (remMs <= 8_000L) {
                manager.ambientAudioEngine.setSleepFadeGain((remMs.toFloat() / 8_000f).coerceIn(0.02f, 1f))
            } else {
                manager.ambientAudioEngine.setSleepFadeGain(1f)
            }
            sleepRemainingSeconds = ((remMs + 999L) / 1000L).coerceAtLeast(1L)
            delay(500L)
        }
    }

    // Telemetry state for Battery, Upcoming Alarm, and optional Open-Meteo Weather
    val isSystem24Hour = DateFormat.is24HourFormat(context)
    val effective24Hour = if (settings.useSystemTimeFormat) isSystem24Hour else settings.use24HourFormat
    var batteryTelemetry by remember { mutableStateOf<FlipBatteryTelemetry?>(null) }
    var batteryLabel by remember { mutableStateOf<String?>(null) }
    var alarmLabel by remember { mutableStateOf<String?>(null) }
    var weatherState by remember { mutableStateOf<FlipWeatherResult>(FlipWeatherResult.NeedsCity) }

    val effectiveShowBattery = if (isLayoutEditMode) draftShowBattery else settings.showBattery
    val effectiveShowWeather = if (isLayoutEditMode) draftShowWeather else settings.showWeather

    LaunchedEffect(effectiveShowBattery, settings.showUpcomingAlarm, effective24Hour, currentDateTime.minute) {
        val battery = if (effectiveShowBattery) FlipClockTelemetry.readBattery(context) else null
        batteryTelemetry = battery
        batteryLabel = battery?.formatLabel()
        alarmLabel = if (settings.showUpcomingAlarm) {
            FlipClockTelemetry.readUpcomingAlarmLabel(context, effective24Hour) ?: "No alarm"
        } else null
    }

    LaunchedEffect(effectiveShowWeather, settings.weatherLocationQuery, settings.weatherAutoDetect) {
        if (!effectiveShowWeather) {
            weatherState = FlipWeatherResult.NeedsCity
            return@LaunchedEffect
        }
        if (settings.weatherLocationQuery.isBlank() && !settings.weatherAutoDetect) {
            weatherState = FlipWeatherResult.NeedsCity
            return@LaunchedEffect
        }
        weatherState = FlipWeatherResult.Loading
        weatherState = FlipWeatherRepository.fetchWeather(
            cityQuery = settings.weatherLocationQuery,
            autoDetect = settings.weatherAutoDetect,
            context = context
        )
    }

    val latestControlsVisible by rememberUpdatedState(controlsVisible)
    val latestZoom by rememberUpdatedState(liveZoomFraction)
    val latestBrightness by rememberUpdatedState(liveBrightnessPercent)
    val latestPage by rememberUpdatedState(FlipClockPage.entries[pagerState.currentPage])
    val density = LocalDensity.current
    val oledOffsetPx = with(density) {
        IntOffset(
            x = (oledShiftDp.first.dp).toPx().roundToInt(),
            y = (oledShiftDp.second.dp).toPx().roundToInt()
        )
    }
    val longPressTimeoutMs = 300L

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FlipClockPalette.CanvasBlack)
            .pointerInput(isLayoutEditMode) {
                if (isLayoutEditMode) return@pointerInput
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
            userScrollEnabled = !isAdjustingBrightness && !showSettingsSheet && !isLayoutEditMode,
            modifier = Modifier
                .fillMaxSize()
                .offset { oledOffsetPx }
        ) { pageIndex ->
            when (FlipClockPage.entries[pageIndex]) {
                FlipClockPage.TIMER -> {
                    FlipTimerPage(
                        presets = presets,
                        timerState = timerState,
                        pomodoroStatus = pomodoroStatus,
                        pomodoroEnabled = settings.pomodoroEnabled,
                        remainingMs = timerRemainingMs,
                        finishedBannerVisible = timerFinishedBannerVisible,
                        showFlipAxis = settings.showFlipAxis,
                        zoomFraction = liveZoomFraction,
                        controlsVisible = controlsVisible,
                        syncFlipTimerWithFocusLock = settings.syncFlipTimerWithFocusLock,
                        focusLockActive = focusLockSession?.isEnforcementPhase == true,
                        focusLockRemainingMs = focusLockRemainingMs,
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
                        onOpenAppNotificationSettings = onOpenAppNotificationSettings,
                        onStartFocusLockFromTimer = {
                            interactionPulse++
                            scope.launch {
                                when (val res = manager.startFocusLockFromFlipTimer()) {
                                    EngineStartResult.Started -> Unit
                                    is EngineStartResult.Blocked -> {
                                        Toast.makeText(context, res.reason, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )
                }

                FlipClockPage.CLOCK -> {
                    FlipClockFacePage(
                        dateTime = currentDateTime,
                        settings = settings,
                        effective24Hour = effective24Hour,
                        batteryTelemetry = batteryTelemetry,
                        batteryLabel = batteryLabel,
                        alarmLabel = alarmLabel,
                        weatherResult = weatherState,
                        sleepRemainingSeconds = sleepRemainingSeconds,
                        focusLockActive = focusLockSession?.isEnforcementPhase == true,
                        focusLockRemainingMs = focusLockRemainingMs,
                        zoomFraction = liveZoomFraction,
                        isLayoutEditMode = isLayoutEditMode,
                        selectedEditorElement = selectedEditorElement,
                        activeCustomLayout = if (isLayoutEditMode) draftCustomLayout else settings.customLayout,
                        overrideShowSeconds = if (isLayoutEditMode) draftShowSeconds else settings.showSeconds,
                        overrideShowDate = if (isLayoutEditMode) draftShowDate else settings.showDate,
                        overrideShowWeekday = if (isLayoutEditMode) draftShowWeekday else settings.showWeekday,
                        overrideShowWeather = if (isLayoutEditMode) draftShowWeather else settings.showWeather,
                        overrideShowBattery = if (isLayoutEditMode) draftShowBattery else settings.showBattery,
                        overrideDateFormatPattern = if (isLayoutEditMode) draftDateFormatPattern else settings.dateFormatPattern,
                        onLandscapeChanged = { currentCanvasIsLandscape = it },
                        onSelectEditorElement = { selectedEditorElement = it },
                        onUpdateOrientationLayout = { isLandscapeCanvas, updatedOrientation ->
                            draftCustomLayout = if (isLandscapeCanvas) {
                                draftCustomLayout.copy(landscape = updatedOrientation)
                            } else {
                                draftCustomLayout.copy(portrait = updatedOrientation)
                            }
                        }
                    )
                }

                FlipClockPage.STOPWATCH -> {
                    FlipStopwatchPage(
                        stopwatchState = stopwatchState,
                        elapsedMs = stopwatchElapsedMs,
                        showFlipAxis = settings.showFlipAxis,
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
                .windowInsetsPadding(WindowInsets.safeDrawing)
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
            visible = controlsVisible && !isLayoutEditMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
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
                        .background(FlipClockPalette.PanelDark.copy(alpha = 0.92f))
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
                                    if (selected) FlipClockPalette.PrimaryCta else Color.Transparent
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
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (selected) FlipClockPalette.PrimaryCtaText else FlipClockPalette.SubTextGrey
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val soundActive = settings.ambientSoundType != com.example.domain.model.AmbientSoundType.OFF
                    Surface(
                        onClick = {
                            interactionPulse++
                            showWhiteNoiseStudio = true
                        },
                        shape = CircleShape,
                        color = if (soundActive) FlipClockPalette.PanelActive else FlipClockPalette.PanelDark.copy(alpha = 0.88f),
                        border = BorderStroke(
                            1.dp,
                            if (soundActive) FlipClockPalette.PrimaryCta else FlipClockPalette.PanelOutline
                        ),
                        modifier = Modifier.testTag("flip_open_white_noise_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (soundActive) FlipClockPalette.PrimaryCta else FlipClockPalette.SubTextGrey
                                    )
                            )
                            Text(
                                text = if (soundActive) settings.ambientSoundType.label else "Sounds",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = FlipClockPalette.AccentSoft,
                                maxLines = 1
                            )
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

        if (isLayoutEditMode) {
            val activeOrientation = if (currentCanvasIsLandscape) {
                draftCustomLayout.landscape
            } else {
                draftCustomLayout.portrait
            }
            FlipLayoutEditorTopBar(
                isLandscape = currentCanvasIsLandscape,
                onCancel = {
                    isLayoutEditMode = false
                    onControlsVisibleChange(true)
                    interactionPulse++
                },
                onReset = {
                    draftCustomLayout = if (currentCanvasIsLandscape) {
                        draftCustomLayout.copy(landscape = FlipClockOrientationLayout.defaultLandscape())
                    } else {
                        draftCustomLayout.copy(portrait = FlipClockOrientationLayout.defaultPortrait())
                    }
                },
                onConfirm = {
                    val committedLayout = if (currentCanvasIsLandscape) {
                        draftCustomLayout.copy(landscape = draftCustomLayout.landscape.copy(enabled = true))
                    } else {
                        draftCustomLayout.copy(portrait = draftCustomLayout.portrait.copy(enabled = true))
                    }
                    isLayoutEditMode = false
                    onControlsVisibleChange(true)
                    interactionPulse++
                    scope.launch {
                        manager.saveCustomLayoutAndDisplay(
                            layout = committedLayout,
                            showSeconds = draftShowSeconds,
                            showDate = draftShowDate,
                            showWeekday = draftShowWeekday,
                            showWeather = draftShowWeather,
                            showBattery = draftShowBattery,
                            dateFormatPattern = draftDateFormatPattern
                        )
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            FlipLayoutEditorInspectorBar(
                selectedElement = selectedEditorElement,
                orientationLayout = activeOrientation,
                effective24Hour = effective24Hour,
                showSeconds = draftShowSeconds,
                showDate = draftShowDate,
                showWeekday = draftShowWeekday,
                showWeather = draftShowWeather,
                showBattery = draftShowBattery,
                dateFormatPattern = draftDateFormatPattern,
                onSelectElement = { selectedEditorElement = it },
                onUpdateOrientationLayout = { updated ->
                    draftCustomLayout = if (currentCanvasIsLandscape) {
                        draftCustomLayout.copy(landscape = updated)
                    } else {
                        draftCustomLayout.copy(portrait = updated)
                    }
                },
                onSetShowSeconds = { draftShowSeconds = it },
                onSetShowDate = { draftShowDate = it },
                onSetShowWeekday = { draftShowWeekday = it },
                onSetShowWeather = { draftShowWeather = it },
                onSetShowBattery = { draftShowBattery = it },
                onSetDateFormatPattern = { draftDateFormatPattern = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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
            onSetUseSystemTimeFormat = { enabled -> scope.launch { manager.setUseSystemTimeFormat(enabled) } },
            onSetUse24Hour = { enabled -> scope.launch { manager.setUse24HourFormat(enabled) } },
            onSetHourLeadingZero = { enabled -> scope.launch { manager.setHourLeadingZero(enabled) } },
            onSetDateFormatPattern = { pattern -> scope.launch { manager.setDateFormatPattern(pattern) } },
            onSetShowFlipAxis = { enabled -> scope.launch { manager.setShowFlipAxis(enabled) } },
            onSetShowSeconds = { enabled -> scope.launch { manager.setShowSeconds(enabled) } },
            onSetShowWeekday = { enabled -> scope.launch { manager.setShowWeekday(enabled) } },
            onSetShowDate = { enabled -> scope.launch { manager.setShowDate(enabled) } },
            onSetShowBattery = { enabled -> scope.launch { manager.setShowBattery(enabled) } },
            onSetShowUpcomingAlarm = { enabled -> scope.launch { manager.setShowUpcomingAlarm(enabled) } },
            onSetShowWeather = { enabled -> scope.launch { manager.setShowWeather(enabled) } },
            onSetWeatherLocationQuery = { query -> scope.launch { manager.setWeatherLocationQuery(query) } },
            onSetSleepTimerMinutes = { mins -> scope.launch { manager.setSleepTimerMinutes(mins) } },
            onSetClockTickerSound = { enabled -> scope.launch { manager.setClockTickerSoundEnabled(enabled) } },
            onSetCustomTag = { tag -> scope.launch { manager.setCustomTag(tag) } },
            onSetKeepScreenAwake = { enabled -> scope.launch { manager.setKeepScreenAwake(enabled) } },
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
            onResetSettingsOnly = { scope.launch { manager.resetSettingsToDefaultPreservingCustomPresets() } },
            onSetPomodoroEnabled = { enabled -> scope.launch { manager.setPomodoroEnabled(enabled) } },
            onSetPomodoroDurations = { focusM, breakM -> scope.launch { manager.setPomodoroDurations(focusM, breakM) } },
            onSetAmbientSoundType = { type -> scope.launch { manager.setAmbientSoundType(type) } },
            onSetAmbientVolume = { vol -> scope.launch { manager.setAmbientVolume(vol) } },
            onSetOledBurnInProtection = { enabled -> scope.launch { manager.setOledBurnInProtection(enabled) } },
            onOpenLayoutEditor = { enterLayoutEditMode() },
            onSetWeatherUseFahrenheit = { enabled -> scope.launch { manager.setWeatherUseFahrenheit(enabled) } },
            onSetWeatherAutoDetect = { enabled -> scope.launch { manager.setWeatherAutoDetect(enabled) } },
            onSetPlayAmbientDuringFocusLock = { enabled -> scope.launch { manager.setPlayAmbientSoundDuringFocusLock(enabled) } },
            onSetAmbientPlayInBackground = { enabled -> scope.launch { manager.setAmbientPlayInBackground(enabled) } },
            onSetSyncFlipTimerWithFocusLock = { enabled -> scope.launch { manager.setSyncFlipTimerWithFocusLock(enabled) } },
            onOpenWhiteNoiseStudio = {
                showSettingsSheet = false
                showWhiteNoiseStudio = true
            }
        )
    }

    if (showWhiteNoiseStudio) {
        FlipWhiteNoiseStudioSheet(
            settings = settings,
            onDismiss = {
                showWhiteNoiseStudio = false
                interactionPulse++
            },
            onSelectSound = { type -> scope.launch { manager.setAmbientSoundType(type) } },
            onVolumeChange = { vol -> scope.launch { manager.setAmbientVolume(vol) } },
            onSetPlayInBackground = { enabled -> scope.launch { manager.setAmbientPlayInBackground(enabled) } },
            onSetPlayDuringFocusLock = { enabled -> scope.launch { manager.setPlayAmbientSoundDuringFocusLock(enabled) } },
            onSetSleepTimerMinutes = { mins -> scope.launch { manager.setSleepTimerMinutes(mins) } }
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
                        containerColor = FlipClockPalette.PrimaryCta,
                        contentColor = FlipClockPalette.PrimaryCtaText
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
                        containerColor = FlipClockPalette.PrimaryCta,
                        contentColor = FlipClockPalette.PrimaryCtaText
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlipClockFacePage(
    dateTime: ZonedDateTime,
    settings: FlipClockSettings,
    effective24Hour: Boolean,
    batteryTelemetry: FlipBatteryTelemetry?,
    batteryLabel: String?,
    alarmLabel: String?,
    weatherResult: FlipWeatherResult,
    sleepRemainingSeconds: Long,
    focusLockActive: Boolean = false,
    focusLockRemainingMs: Long = 0L,
    zoomFraction: Float,
    isLayoutEditMode: Boolean,
    selectedEditorElement: FlipClockElementId,
    activeCustomLayout: FlipClockCustomLayout,
    overrideShowSeconds: Boolean,
    overrideShowDate: Boolean,
    overrideShowWeekday: Boolean,
    overrideShowWeather: Boolean,
    overrideShowBattery: Boolean,
    overrideDateFormatPattern: String,
    onLandscapeChanged: (Boolean) -> Unit,
    onSelectEditorElement: (FlipClockElementId) -> Unit,
    onUpdateOrientationLayout: (Boolean, FlipClockOrientationLayout) -> Unit
) {
    val rawHour = if (effective24Hour) {
        dateTime.hour
    } else {
        val h12 = dateTime.hour % 12
        if (h12 == 0) 12 else h12
    }
    val hourValue = if (settings.hourLeadingZero || effective24Hour) {
        "%02d".format(rawHour)
    } else {
        "%d".format(rawHour)
    }
    val minuteValue = "%02d".format(dateTime.minute)
    val secondValue = "%02d".format(dateTime.second)
    val amPmText = if (dateTime.hour < 12) "AM" else "PM"
    val fullWeekdayText = remember(dateTime.toLocalDate()) {
        dateTime.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
    }
    val shortWeekdayText = remember(dateTime.toLocalDate()) {
        dateTime.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
    }
    val dateLineText = remember(dateTime.toLocalDate(), overrideDateFormatPattern) {
        val fmt = FlipDateFormat.fromPattern(overrideDateFormatPattern).pattern
        runCatching {
            dateTime.format(DateTimeFormatter.ofPattern(fmt, Locale.getDefault()))
        }.getOrElse {
            dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yy", Locale.getDefault()))
        }
    }

    val statusItems = buildList {
        if (settings.customTag.isNotBlank()) add(settings.customTag.trim())
        if (settings.syncFlipTimerWithFocusLock && focusLockActive && focusLockRemainingMs > 0L) {
            val totalSec = (focusLockRemainingMs / 1000L).coerceAtLeast(0L)
            val mins = totalSec / 60L
            val secs = totalSec % 60L
            add("Focus Lock %02d:%02d".format(mins, secs))
        }
        if (overrideShowBattery && !batteryLabel.isNullOrBlank()) add(batteryLabel)
        if (settings.showUpcomingAlarm && !alarmLabel.isNullOrBlank()) add(alarmLabel)
        if (overrideShowWeather) {
            when (weatherResult) {
                FlipWeatherResult.NeedsCity -> add(if (settings.weatherAutoDetect) "Weather: Auto" else "Weather: Set city")
                FlipWeatherResult.Loading -> add("Weather...")
                is FlipWeatherResult.Ready -> add(weatherResult.formatLabel(useFahrenheit = settings.weatherUseFahrenheit))
                FlipWeatherResult.Unavailable -> add("Weather offline")
            }
        }
        if (sleepRemainingSeconds > 0L) {
            val mins = ((sleepRemainingSeconds + 59L) / 60L).coerceAtLeast(1L)
            add("Sleep ${mins}m")
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(FlipClockPalette.CanvasBlack)
            .testTag("flip_clock_face_page"),
        contentAlignment = Alignment.Center
    ) {
        val isLandscape = maxWidth > maxHeight
        LaunchedEffect(isLandscape) {
            onLandscapeChanged(isLandscape)
        }
        val scale = zoomFraction.coerceIn(
            FlipClockSettings.MIN_ZOOM_FRACTION,
            FlipClockSettings.MAX_ZOOM_FRACTION
        )
        val orientationLayout = if (isLandscape) activeCustomLayout.landscape else activeCustomLayout.portrait
        val useFreeCanvas = isLayoutEditMode || orientationLayout.enabled

        var showColon by rememberSaveable { mutableStateOf(true) }
        val colonAlpha by animateFloatAsState(
            targetValue = if (showColon) 1f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "flip_clock_colon_alpha"
        )
        val colonInteractionSource = remember { MutableInteractionSource() }

        val weekdayDisplayText = when (orientationLayout.weekdayStyle) {
            FlipWeekdayStyle.FULL -> fullWeekdayText
            FlipWeekdayStyle.SHORT -> shortWeekdayText
        }
        val batteryDisplayText = when {
            batteryTelemetry == null && isLayoutEditMode ->
                if (orientationLayout.batteryStyle == FlipBatteryStyle.PERCENT_ONLY) "85%" else "85% ⚡"
            batteryTelemetry == null -> ""
            orientationLayout.batteryStyle == FlipBatteryStyle.PERCENT_ONLY -> "${batteryTelemetry.percent}%"
            else -> batteryTelemetry.formatLabel()
        }
        val weatherDisplayText = when (weatherResult) {
            FlipWeatherResult.NeedsCity -> when {
                isLayoutEditMode -> if (settings.weatherUseFahrenheit) "75°F · Weather" else "24°C · Weather"
                settings.weatherAutoDetect -> "Weather: Auto"
                else -> "Weather: Set city"
            }
            FlipWeatherResult.Loading -> "Weather..."
            is FlipWeatherResult.Ready -> when (orientationLayout.weatherStyle) {
                FlipWeatherStyle.COMPACT -> weatherResult.formatLabel(
                    useFahrenheit = settings.weatherUseFahrenheit,
                    compact = true
                )
                FlipWeatherStyle.DETAILED -> weatherResult.formatLabel(
                    useFahrenheit = settings.weatherUseFahrenheit,
                    compact = false
                )
            }
            FlipWeatherResult.Unavailable -> "Weather offline"
        }

        if (useFreeCanvas) {
            val densityLocal = LocalDensity.current
            val canvasWidthPx = with(densityLocal) { maxWidth.toPx() }
            val canvasHeightPx = with(densityLocal) { maxHeight.toPx() }
            val baseHeight = if (isLandscape) {
                min(maxHeight * 0.62f, maxWidth * 0.29f).coerceIn(116.dp, 240.dp)
            } else {
                min(maxHeight * 0.26f, maxWidth * 0.54f).coerceIn(124.dp, 220.dp)
            }
            val digitGap = (baseHeight * 0.035f).coerceIn(4.dp, 8.dp)
            val singleSlotWidth = (baseHeight * 0.50f).coerceAtLeast(64.dp)
            val pairWidth = singleSlotWidth * 2 + digitGap
            val isSingleHour = hourValue.length == 1
            val hourScale = orientationLayout.hourScale.coerceIn(
                FlipClockOrientationLayout.MIN_DIGIT_SCALE,
                FlipClockOrientationLayout.MAX_DIGIT_SCALE
            )
            val minuteScale = orientationLayout.minuteScale.coerceIn(
                FlipClockOrientationLayout.MIN_DIGIT_SCALE,
                FlipClockOrientationLayout.MAX_DIGIT_SCALE
            )
            val hourGroupWidth by animateDpAsState(
                targetValue = (if (isSingleHour) singleSlotWidth else pairWidth) * hourScale,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "flip_clock_custom_hour_width"
            )
            val hourCardHeight = baseHeight * hourScale
            val minuteCardWidth = pairWidth * minuteScale
            val minuteCardHeight = baseHeight * minuteScale
            val hourFontSize = (hourCardHeight.value * 0.90f).sp
            val minuteFontSize = (minuteCardHeight.value * 0.90f).sp
            val seamThickness = (baseHeight * 0.015f).coerceIn(2.6.dp, 4.0.dp)
            val labelStyle = MaterialTheme.typography.titleSmall.copy(
                fontFamily = FlipClockLabelFamily,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.7.sp
            )

            fun updateDrag(id: FlipClockElementId, dx: Float, dy: Float) {
                val cur = orientationLayout.placementFor(id)
                onUpdateOrientationLayout(
                    isLandscape,
                    orientationLayout.withPlacement(
                        id,
                        cur.copy(
                            offsetXFrac = cur.offsetXFrac + dx,
                            offsetYFrac = cur.offsetYFrac + dy
                        )
                    )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .testTag(if (isLandscape) "flip_clock_landscape_cluster" else "flip_clock_portrait_cluster"),
                contentAlignment = Alignment.Center
            ) {
                // Hour group
                FlipDraggableElementBox(
                    elementId = FlipClockElementId.HOUR,
                    isEditMode = isLayoutEditMode,
                    isSelected = selectedEditorElement == FlipClockElementId.HOUR,
                    placement = orientationLayout.hourPlacement,
                    canvasWidthPx = canvasWidthPx,
                    canvasHeightPx = canvasHeightPx,
                    onSelect = { onSelectEditorElement(FlipClockElementId.HOUR) },
                    onDragFractionDelta = { dx, dy -> updateDrag(FlipClockElementId.HOUR, dx, dy) }
                ) {
                    SplitFlipCard(
                        value = hourValue,
                        cardWidth = hourGroupWidth,
                        cardHeight = hourCardHeight,
                        fontSize = hourFontSize,
                        seamThickness = seamThickness,
                        digitGap = digitGap * hourScale,
                        showFlipAxis = settings.showFlipAxis,
                        modifier = Modifier.testTag("flip_clock_hour_card")
                    )
                }

                // Interactive Colon between Hour & Minute when arranged horizontally
                val hPlace = orientationLayout.hourPlacement
                val mPlace = orientationLayout.minutePlacement
                if (isLandscape && abs(hPlace.offsetYFrac - mPlace.offsetYFrac) < 0.18f && mPlace.offsetXFrac > hPlace.offsetXFrac) {
                    val midX = ((hPlace.offsetXFrac + mPlace.offsetXFrac) * 0.5f * canvasWidthPx).roundToInt()
                    val midY = ((hPlace.offsetYFrac + mPlace.offsetYFrac) * 0.5f * canvasHeightPx).roundToInt()
                    val colonDotSize = (baseHeight * 0.082f).coerceIn(10.dp, 18.dp)
                    val colonDotGap = (baseHeight * 0.18f).coerceIn(20.dp, 36.dp)
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(midX, midY) }
                            .clickable(
                                interactionSource = colonInteractionSource,
                                indication = null,
                                enabled = !isLayoutEditMode,
                                role = Role.Button,
                                onClick = { showColon = !showColon }
                            )
                            .semantics {
                                contentDescription = if (showColon) "Hide clock colon" else "Show clock colon"
                            }
                            .testTag("flip_clock_colon_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(colonDotGap),
                            modifier = Modifier.graphicsLayer { alpha = colonAlpha }
                        ) {
                            Box(Modifier.size(colonDotSize).clip(CircleShape).background(FlipClockPalette.DigitGrey))
                            Box(Modifier.size(colonDotSize).clip(CircleShape).background(FlipClockPalette.DigitGrey))
                        }
                    }
                }

                // Minute group
                FlipDraggableElementBox(
                    elementId = FlipClockElementId.MINUTE,
                    isEditMode = isLayoutEditMode,
                    isSelected = selectedEditorElement == FlipClockElementId.MINUTE,
                    placement = orientationLayout.minutePlacement,
                    canvasWidthPx = canvasWidthPx,
                    canvasHeightPx = canvasHeightPx,
                    onSelect = { onSelectEditorElement(FlipClockElementId.MINUTE) },
                    onDragFractionDelta = { dx, dy -> updateDrag(FlipClockElementId.MINUTE, dx, dy) }
                ) {
                    SplitFlipCard(
                        value = minuteValue,
                        cardWidth = minuteCardWidth,
                        cardHeight = minuteCardHeight,
                        fontSize = minuteFontSize,
                        seamThickness = seamThickness,
                        digitGap = digitGap * minuteScale,
                        showFlipAxis = settings.showFlipAxis,
                        modifier = Modifier.testTag("flip_clock_minute_card")
                    )
                }

                // AM/PM
                if (!effective24Hour) {
                    FlipDraggableElementBox(
                        elementId = FlipClockElementId.AM_PM,
                        isEditMode = isLayoutEditMode,
                        isSelected = selectedEditorElement == FlipClockElementId.AM_PM,
                        placement = orientationLayout.amPmPlacement,
                        canvasWidthPx = canvasWidthPx,
                        canvasHeightPx = canvasHeightPx,
                        onSelect = { onSelectEditorElement(FlipClockElementId.AM_PM) },
                        onDragFractionDelta = { dx, dy -> updateDrag(FlipClockElementId.AM_PM, dx, dy) }
                    ) {
                        Text(
                            text = amPmText,
                            style = labelStyle.copy(fontSize = 16.sp),
                            textAlign = when (orientationLayout.amPmAlign) {
                                FlipClockElementAlign.LEFT -> TextAlign.Start
                                FlipClockElementAlign.CENTER -> TextAlign.Center
                                FlipClockElementAlign.RIGHT -> TextAlign.End
                            },
                            color = FlipClockPalette.SubTextGrey,
                            modifier = Modifier
                                .widthIn(min = 56.dp)
                                .testTag("flip_clock_ampm_label")
                        )
                    }
                }

                // Seconds
                if (overrideShowSeconds) {
                    FlipDraggableElementBox(
                        elementId = FlipClockElementId.SECONDS,
                        isEditMode = isLayoutEditMode,
                        isSelected = selectedEditorElement == FlipClockElementId.SECONDS,
                        placement = orientationLayout.secondsPlacement,
                        canvasWidthPx = canvasWidthPx,
                        canvasHeightPx = canvasHeightPx,
                        onSelect = { onSelectEditorElement(FlipClockElementId.SECONDS) },
                        onDragFractionDelta = { dx, dy -> updateDrag(FlipClockElementId.SECONDS, dx, dy) }
                    ) {
                        Text(
                            text = secondValue,
                            style = labelStyle.copy(fontSize = 18.sp, fontFeatureSettings = "tnum"),
                            textAlign = when (orientationLayout.secondsAlign) {
                                FlipClockElementAlign.LEFT -> TextAlign.Start
                                FlipClockElementAlign.CENTER -> TextAlign.Center
                                FlipClockElementAlign.RIGHT -> TextAlign.End
                            },
                            color = FlipClockPalette.SubTextGrey,
                            modifier = Modifier
                                .widthIn(min = 48.dp)
                                .testTag("flip_clock_seconds_label")
                        )
                    }
                }

                // Date
                if (overrideShowDate) {
                    FlipDraggableElementBox(
                        elementId = FlipClockElementId.DATE,
                        isEditMode = isLayoutEditMode,
                        isSelected = selectedEditorElement == FlipClockElementId.DATE,
                        placement = orientationLayout.datePlacement,
                        canvasWidthPx = canvasWidthPx,
                        canvasHeightPx = canvasHeightPx,
                        onSelect = { onSelectEditorElement(FlipClockElementId.DATE) },
                        onDragFractionDelta = { dx, dy -> updateDrag(FlipClockElementId.DATE, dx, dy) }
                    ) {
                        Text(
                            text = dateLineText,
                            style = labelStyle,
                            color = FlipClockPalette.SubTextGrey,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.testTag("flip_clock_date_text")
                        )
                    }
                }

                // Weekday
                if (overrideShowWeekday) {
                    FlipDraggableElementBox(
                        elementId = FlipClockElementId.WEEKDAY,
                        isEditMode = isLayoutEditMode,
                        isSelected = selectedEditorElement == FlipClockElementId.WEEKDAY,
                        placement = orientationLayout.weekdayPlacement,
                        canvasWidthPx = canvasWidthPx,
                        canvasHeightPx = canvasHeightPx,
                        onSelect = { onSelectEditorElement(FlipClockElementId.WEEKDAY) },
                        onDragFractionDelta = { dx, dy -> updateDrag(FlipClockElementId.WEEKDAY, dx, dy) }
                    ) {
                        Text(
                            text = weekdayDisplayText,
                            style = labelStyle,
                            color = FlipClockPalette.SubTextGrey,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.testTag("flip_clock_weekday_label")
                        )
                    }
                }

                // Weather
                if (overrideShowWeather) {
                    FlipDraggableElementBox(
                        elementId = FlipClockElementId.WEATHER,
                        isEditMode = isLayoutEditMode,
                        isSelected = selectedEditorElement == FlipClockElementId.WEATHER,
                        placement = orientationLayout.weatherPlacement,
                        canvasWidthPx = canvasWidthPx,
                        canvasHeightPx = canvasHeightPx,
                        onSelect = { onSelectEditorElement(FlipClockElementId.WEATHER) },
                        onDragFractionDelta = { dx, dy -> updateDrag(FlipClockElementId.WEATHER, dx, dy) }
                    ) {
                        Text(
                            text = weatherDisplayText,
                            style = labelStyle.copy(fontSize = 13.sp),
                            color = FlipClockPalette.SubTextGrey,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.testTag("flip_clock_weather_label")
                        )
                    }
                }

                // Battery %
                if (overrideShowBattery && batteryDisplayText.isNotEmpty()) {
                    FlipDraggableElementBox(
                        elementId = FlipClockElementId.BATTERY,
                        isEditMode = isLayoutEditMode,
                        isSelected = selectedEditorElement == FlipClockElementId.BATTERY,
                        placement = orientationLayout.batteryPlacement,
                        canvasWidthPx = canvasWidthPx,
                        canvasHeightPx = canvasHeightPx,
                        onSelect = { onSelectEditorElement(FlipClockElementId.BATTERY) },
                        onDragFractionDelta = { dx, dy -> updateDrag(FlipClockElementId.BATTERY, dx, dy) }
                    ) {
                        Text(
                            text = batteryDisplayText,
                            style = labelStyle.copy(fontSize = 13.sp),
                            color = FlipClockPalette.SubTextGrey,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.testTag("flip_clock_battery_label")
                        )
                    }
                }
            }
        } else if (isLandscape) {
            val baseHeight = min(maxHeight * 0.64f, maxWidth * 0.30f).coerceIn(124.dp, 256.dp)
            val digitGap = (baseHeight * 0.035f).coerceIn(4.dp, 8.dp)
            val singleSlotWidth = (baseHeight * 0.50f).coerceAtLeast(64.dp)
            val pairWidth = singleSlotWidth * 2 + digitGap
            val isSingleHour = hourValue.length == 1
            val hourGroupWidth by animateDpAsState(
                targetValue = if (isSingleHour) singleSlotWidth else pairWidth,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "flip_clock_landscape_hour_width"
            )
            val digitFontSize = (baseHeight.value * 0.90f).sp
            val seamThickness = (baseHeight * 0.015f).coerceIn(2.6.dp, 4.0.dp)
            val colonDotSize = (baseHeight * 0.088f).coerceIn(11.dp, 19.dp)
            val colonDotGap = (baseHeight * 0.19f).coerceIn(22.dp, 40.dp)
            val colonSpanWidth = (baseHeight * 0.40f).coerceAtLeast(52.dp)

            val subLabelTopStyle = MaterialTheme.typography.titleSmall.copy(
                fontFamily = FlipClockLabelFamily,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.7.sp
            )
            val subLabelBottomStyle = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FlipClockLabelFamily,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp
            )

            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .testTag("flip_clock_landscape_cluster"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Left block: tight single-digit width when Leading Zero is OFF, full two-digit pair width when ON/2-digit
                    Column(
                        modifier = Modifier.width(hourGroupWidth),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (settings.showDate) dateLineText else " ",
                            style = subLabelTopStyle,
                            color = if (settings.showDate) FlipClockPalette.SubTextGrey else Color.Transparent,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .align(if (isSingleHour) Alignment.CenterHorizontally else Alignment.End)
                                .wrapContentWidth(unbounded = true)
                                .padding(bottom = 4.dp)
                                .let { if (settings.showDate) it.testTag("flip_clock_date_text") else it }
                        )

                        SplitFlipCard(
                            value = hourValue,
                            cardWidth = hourGroupWidth,
                            cardHeight = baseHeight,
                            fontSize = digitFontSize,
                            seamThickness = seamThickness,
                            digitGap = digitGap,
                            showFlipAxis = settings.showFlipAxis,
                            modifier = Modifier.testTag("flip_clock_hour_card")
                        )

                        Text(
                            text = if (!effective24Hour) amPmText else " ",
                            style = subLabelBottomStyle,
                            color = if (!effective24Hour) FlipClockPalette.SubTextGrey else Color.Transparent,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .align(if (isSingleHour) Alignment.CenterHorizontally else Alignment.Start)
                                .wrapContentWidth(unbounded = true)
                                .padding(start = if (isSingleHour) 0.dp else 4.dp, top = 4.dp)
                                .let { if (!effective24Hour) it.testTag("flip_clock_ampm_label") else it }
                        )
                    }

                    // Center interactive (:) colon with balanced horizontal breathing space on both sides
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = " ",
                            style = subLabelTopStyle,
                            color = Color.Transparent,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        Box(
                            modifier = Modifier
                                .width(colonSpanWidth)
                                .height(baseHeight)
                                .clickable(
                                    interactionSource = colonInteractionSource,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = { showColon = !showColon }
                                )
                                .semantics {
                                    contentDescription = if (showColon) "Hide clock colon" else "Show clock colon"
                                }
                                .testTag("flip_clock_colon_toggle"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(colonDotGap),
                                modifier = Modifier.graphicsLayer { alpha = colonAlpha }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(colonDotSize)
                                        .clip(CircleShape)
                                        .background(FlipClockPalette.DigitGrey)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(colonDotSize)
                                        .clip(CircleShape)
                                        .background(FlipClockPalette.DigitGrey)
                                )
                            }
                        }

                        Text(
                            text = " ",
                            style = subLabelBottomStyle,
                            color = Color.Transparent,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Right block: Weekday top-center, Minute center, Seconds bottom-right
                    Column(
                        modifier = Modifier.width(pairWidth),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (settings.showWeekday) weekdayDisplayText else " ",
                            style = subLabelTopStyle.copy(letterSpacing = 0.6.sp),
                            color = if (settings.showWeekday) FlipClockPalette.SubTextGrey else Color.Transparent,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .wrapContentWidth(unbounded = true)
                                .padding(bottom = 4.dp)
                                .let { if (settings.showWeekday) it.testTag("flip_clock_weekday_label") else it }
                        )

                        SplitFlipCard(
                            value = minuteValue,
                            cardWidth = pairWidth,
                            cardHeight = baseHeight,
                            fontSize = digitFontSize,
                            seamThickness = seamThickness,
                            digitGap = digitGap,
                            showFlipAxis = settings.showFlipAxis,
                            modifier = Modifier.testTag("flip_clock_minute_card")
                        )

                        Text(
                            text = if (settings.showSeconds) secondValue else " ",
                            style = subLabelBottomStyle.copy(fontFeatureSettings = "tnum"),
                            color = if (settings.showSeconds) FlipClockPalette.SubTextGrey else Color.Transparent,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(end = 4.dp, top = 4.dp)
                                .let { if (settings.showSeconds) it.testTag("flip_clock_seconds_label") else it }
                        )
                    }
                }

                if (statusItems.isNotEmpty()) {
                    FlipClockSubtleStatusStrip(statusItems = statusItems)
                }
            }
        } else {
            val baseHeight = min(maxHeight * 0.27f, maxWidth * 0.56f).coerceIn(136.dp, 232.dp)
            val digitGap = (baseHeight * 0.035f).coerceIn(4.dp, 8.dp)
            val singleSlotWidth = (baseHeight * 0.50f).coerceAtLeast(64.dp)
            val minuteWidth = singleSlotWidth * 2 + digitGap
            val hourWidth by animateDpAsState(
                targetValue = if (hourValue.length == 1) singleSlotWidth else minuteWidth,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "flip_clock_portrait_hour_width"
            )
            val digitFontSize = (baseHeight.value * 0.90f).sp
            val seamThickness = (baseHeight * 0.015f).coerceIn(2.6.dp, 4.0.dp)

            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .testTag("flip_clock_portrait_cluster"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SplitFlipCard(
                    value = hourValue,
                    cardWidth = hourWidth,
                    cardHeight = baseHeight,
                    fontSize = digitFontSize,
                    seamThickness = seamThickness,
                    digitGap = digitGap,
                    showFlipAxis = settings.showFlipAxis,
                    modifier = Modifier.testTag("flip_clock_hour_card")
                )

                Row(
                    modifier = Modifier
                        .width(minuteWidth)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!effective24Hour) {
                        Text(
                            text = amPmText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FlipClockLabelFamily,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = FlipClockPalette.SubTextGrey,
                            modifier = Modifier.testTag("flip_clock_ampm_label")
                        )
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    if (settings.showDate) {
                        Text(
                            text = dateLineText,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = FlipClockLabelFamily,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.6.sp
                            ),
                            color = FlipClockPalette.SubTextGrey,
                            modifier = Modifier.testTag("flip_clock_date_text")
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (settings.showWeekday) {
                    Text(
                        text = weekdayDisplayText,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = FlipClockLabelFamily,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp
                        ),
                        color = FlipClockPalette.SubTextGrey,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .testTag("flip_clock_weekday_label")
                    )
                }

                SplitFlipCard(
                    value = minuteValue,
                    cardWidth = minuteWidth,
                    cardHeight = baseHeight,
                    fontSize = digitFontSize,
                    seamThickness = seamThickness,
                    digitGap = digitGap,
                    showFlipAxis = settings.showFlipAxis,
                    modifier = Modifier.testTag("flip_clock_minute_card")
                )

                if (settings.showSeconds) {
                    Row(
                        modifier = Modifier
                            .width(minuteWidth)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = secondValue,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FlipClockLabelFamily,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp,
                                fontFeatureSettings = "tnum"
                            ),
                            color = FlipClockPalette.SubTextGrey,
                            modifier = Modifier.testTag("flip_clock_seconds_label")
                        )
                    }
                }

                if (statusItems.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlipClockSubtleStatusStrip(statusItems = statusItems)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlipClockSubtleStatusStrip(
    statusItems: List<String>,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier
            .widthIn(max = 460.dp)
            .padding(horizontal = 16.dp)
            .testTag("flip_clock_status_strip"),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        statusItems.forEachIndexed { idx, label ->
            val display = if (idx < statusItems.lastIndex) "$label ·" else label
            Text(
                text = display,
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 0.5.sp
                ),
                color = FlipClockPalette.SubTextGrey.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FlipTimerPage(
    presets: List<FlipTimerPreset>,
    timerState: FlipTimerState,
    pomodoroStatus: FlipPomodoroStatus,
    pomodoroEnabled: Boolean,
    remainingMs: Long,
    finishedBannerVisible: Boolean,
    showFlipAxis: Boolean,
    zoomFraction: Float,
    controlsVisible: Boolean,
    onTapDisplayToggle: () -> Unit,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onRequestReset: () -> Unit,
    onSelectPreset: (FlipTimerPreset) -> Unit,
    onOpenCustomDuration: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    syncFlipTimerWithFocusLock: Boolean = false,
    focusLockActive: Boolean = false,
    focusLockRemainingMs: Long = 0L,
    onStartFocusLockFromTimer: () -> Unit = {}
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
        val heroCardHeight = if (hours > 0) {
            if (isLandscape) {
                min(boxMaxHeight * 0.44f, boxMaxWidth * 0.20f).coerceIn(88.dp, 176.dp)
            } else {
                min(boxMaxWidth * 0.24f, boxMaxHeight * 0.21f).coerceIn(82.dp, 148.dp)
            }
        } else {
            if (isLandscape) {
                min(boxMaxHeight * 0.48f, boxMaxWidth * 0.26f).coerceIn(106.dp, 196.dp)
            } else {
                min(boxMaxWidth * 0.34f, boxMaxHeight * 0.25f).coerceIn(102.dp, 176.dp)
            }
        }
        val presetColumns = if (isLandscape) 3 else 2
        val presetSectionMaxWidth = if (isLandscape) 720.dp else 520.dp

        // Initial viewport shows ONLY the pure-black split-flap Timer Display (+ playback overlay when shown).
        // Scrolling vertically reveals Custom Duration and compact Timer Presets Grid cleanly below without peeking.
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
                        .padding(vertical = 12.dp),
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
                            pomodoroStatus = pomodoroStatus,
                            pomodoroEnabled = pomodoroEnabled,
                            finishedBannerVisible = finishedBannerVisible,
                            showFlipAxis = showFlipAxis,
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
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }

                        if (syncFlipTimerWithFocusLock) {
                            Spacer(Modifier.height(10.dp))
                            if (focusLockActive && focusLockRemainingMs > 0L) {
                                val lockSec = (focusLockRemainingMs / 1000L).coerceAtLeast(0L)
                                val lh = lockSec / 3600L
                                val lm = (lockSec % 3600L) / 60L
                                val ls = lockSec % 60L
                                val lockText = if (lh > 0) {
                                    "Focus Lock Active · %02d:%02d:%02d".format(lh, lm, ls)
                                } else {
                                    "Focus Lock Active · %02d:%02d".format(lm, ls)
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(FlipClockPalette.PanelDark.copy(alpha = 0.90f))
                                        .border(1.dp, FlipClockPalette.SageLabel.copy(alpha = 0.5f), CircleShape)
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                        .testTag("flip_timer_focus_lock_pill"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = FlipClockPalette.SageLabel,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = lockText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = FlipClockPalette.SageLabel
                                    )
                                }
                            } else {
                                val lockMins = (((remainingMs.coerceAtLeast(60_000L)) + 59_999L) / 60_000L).coerceIn(1L, 1440L)
                                OutlinedButton(
                                    onClick = onStartFocusLockFromTimer,
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, FlipClockPalette.SageLabel.copy(alpha = 0.55f)),
                                    modifier = Modifier.testTag("flip_timer_start_focus_lock_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Lock,
                                        contentDescription = "Start Focus Lock",
                                        tint = FlipClockPalette.SageLabel,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Start Focus Lock (${lockMins}m)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = FlipClockPalette.SageLabel
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "presets_section_heading") {
                Text(
                    text = "TIMER PRESETS & CUSTOM DURATION",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                    color = FlipClockPalette.SubTextGrey,
                    modifier = Modifier
                        .widthIn(max = presetSectionMaxWidth)
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 4.dp)
                        .testTag("flip_timer_presets_heading")
                )
            }

            item(key = "custom_duration_btn") {
                Surface(
                    onClick = onOpenCustomDuration,
                    shape = RoundedCornerShape(16.dp),
                    color = FlipClockPalette.PanelElevated,
                    border = BorderStroke(1.dp, FlipClockPalette.PanelOutline),
                    modifier = Modifier
                        .widthIn(max = presetSectionMaxWidth)
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
                            color = FlipClockPalette.AccentSoft
                        )
                        Text(
                            text = "Pick",
                            style = MaterialTheme.typography.labelLarge,
                            color = FlipClockPalette.PrimaryCtaText,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(FlipClockPalette.PrimaryCta)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            item(key = "timer_presets_grid") {
                val presetRows = presets.chunked(presetColumns)
                Column(
                    modifier = Modifier
                        .widthIn(max = presetSectionMaxWidth)
                        .fillMaxWidth()
                        .testTag("flip_timer_presets_grid"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetRows.forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { preset ->
                                val selected = preset.presetId == timerState.activePresetId
                                Surface(
                                    onClick = {
                                        onSelectPreset(preset)
                                        if (!timerState.isRunning && !timerState.isPaused) {
                                            timerScope.launch { listState.animateScrollToItem(0) }
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) FlipClockPalette.PanelActive else FlipClockPalette.PanelDark,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) FlipClockPalette.SageLabel.copy(alpha = 0.7f) else FlipClockPalette.PanelOutline
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("flip_preset_item_${preset.presetId}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = preset.name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (selected) FlipClockPalette.SageLabel else FlipClockPalette.AccentSoft,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatDurationWords(preset.durationSeconds),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) FlipClockPalette.AccentSoft else FlipClockPalette.SubTextGrey,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            repeat(presetColumns - rowPresets.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
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
    pomodoroStatus: FlipPomodoroStatus,
    pomodoroEnabled: Boolean,
    finishedBannerVisible: Boolean,
    showFlipAxis: Boolean,
    cardHeight: Dp,
    scale: Float,
    onTapDisplayToggle: () -> Unit
) {
    val digitGap = (cardHeight * 0.035f).coerceIn(3.dp, 7.dp)
    val cardWidth = (cardHeight * 0.96f).coerceAtLeast(88.dp)
    val fontSize = (cardHeight.value * 0.90f).sp
    val seamThickness = (cardHeight * 0.015f).coerceIn(2.4.dp, 3.8.dp)
    val cardSpacing = if (hours > 0) 16.dp else 22.dp

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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(FlipClockPalette.TimerIndicator)
            )
            Text(
                text = timerState.timerName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = FlipClockLabelFamily,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = FlipClockPalette.SubTextGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (pomodoroEnabled || pomodoroStatus.active) {
                val phaseLabel = if (pomodoroStatus.phase == PomodoroPhase.BREAK) {
                    "BREAK · #${pomodoroStatus.completedCycles + 1}"
                } else {
                    "FOCUS · #${pomodoroStatus.completedCycles + 1}"
                }
                Text(
                    text = phaseLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = FlipClockPalette.PrimaryCtaText,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(FlipClockPalette.PrimaryCta)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("flip_pomodoro_phase_badge")
                )
            }
        }

        if (finishedBannerVisible) {
            Text(
                text = "${timerState.timerName} — Completed!",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = FlipClockPalette.PrimaryCtaText,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(FlipClockPalette.PrimaryCta)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("flip_timer_finished_banner")
            )
        }

        // Borderless split-flip Timer numerals on pure black (no colon, no box)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(cardSpacing)
        ) {
            if (hours > 0) {
                SplitFlipCard(
                    value = "%02d".format(hours),
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                    fontSize = fontSize,
                    seamThickness = seamThickness,
                    digitGap = digitGap,
                    digitColor = FlipClockPalette.DigitGrey,
                    showFlipAxis = showFlipAxis
                )
            }
            SplitFlipCard(
                value = "%02d".format(minutes),
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                fontSize = fontSize,
                seamThickness = seamThickness,
                digitGap = digitGap,
                digitColor = FlipClockPalette.DigitGrey,
                showFlipAxis = showFlipAxis
            )
            SplitFlipCard(
                value = "%02d".format(seconds),
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                fontSize = fontSize,
                seamThickness = seamThickness,
                digitGap = digitGap,
                digitColor = FlipClockPalette.DigitGrey,
                showFlipAxis = showFlipAxis
            )
        }
    }
}


@Composable
private fun FlipStopwatchPage(
    stopwatchState: FlipStopwatchState,
    elapsedMs: Long,
    showFlipAxis: Boolean,
    zoomFraction: Float,
    controlsVisible: Boolean,
    onToggleDisplay: () -> Unit,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onResetImmediate: () -> Unit
) {
    // MM SS seconds only, no milliseconds, no laps
    val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
    val minutes = (totalSeconds / 60L).toInt()
    val seconds = (totalSeconds % 60L).toInt()
    val minStr = "%02d".format(minutes)
    val secStr = "%02d".format(seconds)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(FlipClockPalette.CanvasBlack)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("flip_stopwatch_page")
    ) {
        val isLandscape = maxWidth > maxHeight
        val scale = zoomFraction.coerceIn(
            FlipClockSettings.MIN_ZOOM_FRACTION,
            FlipClockSettings.MAX_ZOOM_FRACTION
        )
        val cardHeight = if (isLandscape) {
            min(maxHeight * 0.56f, maxWidth * 0.30f).coerceIn(124.dp, 224.dp)
        } else {
            min(maxWidth * 0.38f, maxHeight * 0.27f).coerceIn(120.dp, 200.dp)
        }
        val digitGap = (cardHeight * 0.035f).coerceIn(3.dp, 7.dp)
        val cardWidth = (cardHeight * 0.96f).coerceAtLeast(116.dp)
        val fontSize = (cardHeight.value * 0.90f).sp
        val seamThickness = (cardHeight * 0.015f).coerceIn(2.6.dp, 3.8.dp)

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
                    text = "Stopwatch",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = FlipClockLabelFamily,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.7.sp
                    ),
                    color = FlipClockPalette.SubTextGrey
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    SplitFlipCard(
                        value = minStr,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        fontSize = fontSize,
                        seamThickness = seamThickness,
                        digitGap = digitGap,
                        digitColor = FlipClockPalette.DigitGrey,
                        showFlipAxis = showFlipAxis,
                        modifier = Modifier.testTag("flip_stopwatch_min_card")
                    )
                    SplitFlipCard(
                        value = secStr,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        fontSize = fontSize,
                        seamThickness = seamThickness,
                        digitGap = digitGap,
                        digitColor = FlipClockPalette.DigitGrey,
                        showFlipAxis = showFlipAxis,
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
                    containerColor = FlipClockPalette.PrimaryCta,
                    contentColor = FlipClockPalette.PrimaryCtaText
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
            Text("Settings", color = FlipClockPalette.SageLabel)
        }
    }
}
