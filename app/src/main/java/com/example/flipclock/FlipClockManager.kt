package com.example.flipclock

import com.example.data.preferences.ResolvePreferencesRepository
import com.example.data.repository.FlipPresetMutationResult
import com.example.data.repository.FlipTimerPresetRepository
import com.example.domain.model.FlipClockSettings
import com.example.domain.model.FlipStopwatchState
import com.example.domain.model.FlipTimerPreset
import com.example.domain.model.FlipTimerState
import com.example.domain.model.SessionState
import com.example.domain.time.ClockProvider
import com.example.session.LockSessionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface FlipPresetSyncEvent {
    data class Saved(val preset: FlipTimerPreset) : FlipPresetSyncEvent
    data class Deleted(val presetId: String, val deletedAtWallMs: Long) : FlipPresetSyncEvent
}

class FlipClockManager(
    private val preferencesRepository: ResolvePreferencesRepository,
    private val presetRepository: FlipTimerPresetRepository,
    private val notificationHelper: FlipTimerNotificationHelper,
    private val sessionEngine: LockSessionEngine,
    private val clockProvider: ClockProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    val settings: StateFlow<FlipClockSettings> = preferencesRepository.flipClockSettingsFlow
        .stateIn(scope, SharingStarted.Eagerly, FlipClockSettings())

    val presets: StateFlow<List<FlipTimerPreset>> = presetRepository.observePresets()
        .stateIn(scope, SharingStarted.Eagerly, FlipTimerPresetRepository.DEFAULT_BUILT_IN_PRESETS)

    private val _timerState = MutableStateFlow(FlipTimerState())
    val timerState: StateFlow<FlipTimerState> = _timerState.asStateFlow()

    private val _timerRemainingMs = MutableStateFlow(25L * 60_000L)
    val timerRemainingMs: StateFlow<Long> = _timerRemainingMs.asStateFlow()

    private val _timerFinishedBannerVisible = MutableStateFlow(false)
    val timerFinishedBannerVisible: StateFlow<Boolean> = _timerFinishedBannerVisible.asStateFlow()

    private val _stopwatchState = MutableStateFlow(FlipStopwatchState())
    val stopwatchState: StateFlow<FlipStopwatchState> = _stopwatchState.asStateFlow()

    private val _stopwatchElapsedMs = MutableStateFlow(0L)
    val stopwatchElapsedMs: StateFlow<Long> = _stopwatchElapsedMs.asStateFlow()

    private val _presetSyncEvents = MutableSharedFlow<FlipPresetSyncEvent>(extraBufferCapacity = 16)
    val presetSyncEvents: SharedFlow<FlipPresetSyncEvent> = _presetSyncEvents.asSharedFlow()

    private var tickerJob: Job? = null
    private var initialized = false

    suspend fun initialize() {
        val shouldRunTicker = mutex.withLock {
            if (!initialized) {
                initialized = true
                presetRepository.ensureDefaultPresetsSeeded()
                val loadedSettings = preferencesRepository.getFlipClockSettingsSnapshot()
                val loadedTimer = preferencesRepository.getFlipTimerStateSnapshot()
                val loadedStopwatch = preferencesRepository.getFlipStopwatchStateSnapshot()
                reconcileTimerOnProcessStartLocked(loadedTimer, loadedSettings)
                reconcileStopwatchOnProcessStartLocked(loadedStopwatch)
            }
            _timerState.value.isRunning ||
                _timerState.value.completionMessageUntilWallMs != null ||
                _stopwatchState.value.isRunning
        }
        if (shouldRunTicker) {
            ensureTickerRunning()
        }
    }

    private suspend fun reconcileTimerOnProcessStartLocked(
        loaded: FlipTimerState,
        currentSettings: FlipClockSettings
    ) {
        val nowWall = clockProvider.wallClockMs()
        if (loaded.isRunning && !loaded.isPaused && loaded.targetEndWallMs != null) {
            val targetEnd = loaded.targetEndWallMs
            if (nowWall < targetEnd) {
                _timerState.value = loaded
                _timerRemainingMs.value = targetEnd - nowWall
                _timerFinishedBannerVisible.value = false
                notificationHelper.scheduleTimerAlarm(targetEnd)
                return
            }
            // Timer already ended while app was stopped / force-stopped -> restore Finished state cleanly without duplicate sound/notification loops
            val justEndedRecently = (nowWall - targetEnd) <= COMPLETION_BANNER_DURATION_MS
            val bannerUntil = if (justEndedRecently) targetEnd + COMPLETION_BANNER_DURATION_MS else null
            val restored = loaded.copy(
                isRunning = false,
                isPaused = false,
                targetEndWallMs = null,
                remainingWhenPausedMs = null,
                finishedAtWallMs = targetEnd,
                completionMessageUntilWallMs = bannerUntil,
                autoRepeatScheduledAtWallMs = if (currentSettings.autoRepeat && bannerUntil != null) {
                    bannerUntil
                } else {
                    null
                }
            )
            _timerState.value = restored
            _timerRemainingMs.value = restored.selectedDurationMs
            _timerFinishedBannerVisible.value = bannerUntil != null && nowWall < bannerUntil
            preferencesRepository.saveFlipTimerState(restored)
            if (restored.autoRepeatScheduledAtWallMs != null) {
                notificationHelper.scheduleAutoRepeatAlarm(restored.autoRepeatScheduledAtWallMs)
            }
        } else if (loaded.isPaused && loaded.remainingWhenPausedMs != null) {
            _timerState.value = loaded
            _timerRemainingMs.value = loaded.remainingWhenPausedMs.coerceAtLeast(1_000L)
            _timerFinishedBannerVisible.value = false
        } else {
            val bannerUntil = loaded.completionMessageUntilWallMs?.takeIf { nowWall < it }
            val cleared = loaded.copy(
                completionMessageUntilWallMs = bannerUntil,
                autoRepeatScheduledAtWallMs = loaded.autoRepeatScheduledAtWallMs?.takeIf { bannerUntil != null }
            )
            _timerState.value = cleared
            _timerRemainingMs.value = cleared.selectedDurationMs
            _timerFinishedBannerVisible.value = bannerUntil != null
        }
    }

    private suspend fun reconcileStopwatchOnProcessStartLocked(loaded: FlipStopwatchState) {
        val nowElapsed = clockProvider.elapsedRealtimeMs()
        val nowWall = clockProvider.wallClockMs()
        if (loaded.isRunning) {
            val startElapsed = loaded.startedElapsedMs
            val startWall = loaded.startedWallMs
            val effectiveStartElapsed = when {
                startElapsed != null && startElapsed <= nowElapsed -> startElapsed
                startWall != null && startWall <= nowWall -> {
                    val deltaWall = (nowWall - startWall).coerceAtLeast(0L)
                    (nowElapsed - deltaWall).coerceAtLeast(0L)
                }
                else -> nowElapsed
            }
            val updated = loaded.copy(startedElapsedMs = effectiveStartElapsed)
            _stopwatchState.value = updated
            _stopwatchElapsedMs.value = (updated.accumulatedMs + (nowElapsed - effectiveStartElapsed)).coerceAtLeast(0L)
            if (updated != loaded) {
                preferencesRepository.saveFlipStopwatchState(updated)
            }
        } else {
            _stopwatchState.value = loaded
            _stopwatchElapsedMs.value = loaded.accumulatedMs.coerceAtLeast(0L)
        }
    }

    suspend fun selectTimerPreset(preset: FlipTimerPreset) {
        mutex.withLock {
            notificationHelper.cancelAllTimerAlarms()
            val durationMs = (preset.durationSeconds * 1_000L).coerceAtLeast(1_000L)
            val updated = _timerState.value.copy(
                activePresetId = preset.presetId,
                timerName = preset.name,
                selectedDurationMs = durationMs,
                targetEndWallMs = null,
                remainingWhenPausedMs = null,
                isRunning = false,
                isPaused = false,
                finishedAtWallMs = null,
                completionMessageUntilWallMs = null,
                autoRepeatScheduledAtWallMs = null
            )
            _timerState.value = updated
            _timerRemainingMs.value = durationMs
            _timerFinishedBannerVisible.value = false
            preferencesRepository.saveFlipTimerState(updated)
        }
    }

    suspend fun selectCustomDuration(durationSeconds: Long, label: String = "Custom Timer") {
        mutex.withLock {
            notificationHelper.cancelAllTimerAlarms()
            val clampedSeconds = durationSeconds.coerceIn(1L, 86_399L)
            val durationMs = clampedSeconds * 1_000L
            val updated = _timerState.value.copy(
                activePresetId = null,
                timerName = label,
                selectedDurationMs = durationMs,
                targetEndWallMs = null,
                remainingWhenPausedMs = null,
                isRunning = false,
                isPaused = false,
                finishedAtWallMs = null,
                completionMessageUntilWallMs = null,
                autoRepeatScheduledAtWallMs = null
            )
            _timerState.value = updated
            _timerRemainingMs.value = durationMs
            _timerFinishedBannerVisible.value = false
            preferencesRepository.saveFlipTimerState(updated)
        }
    }

    suspend fun startOrResumeTimer() {
        mutex.withLock {
            val current = _timerState.value
            if (current.isRunning && !current.isPaused) return@withLock
            val durationToRun = if (current.isPaused && current.remainingWhenPausedMs != null) {
                current.remainingWhenPausedMs.coerceAtLeast(1_000L)
            } else {
                current.selectedDurationMs.coerceAtLeast(1_000L)
            }
            val nowWall = clockProvider.wallClockMs()
            val targetEnd = nowWall + durationToRun
            val updated = current.copy(
                targetEndWallMs = targetEnd,
                remainingWhenPausedMs = null,
                isRunning = true,
                isPaused = false,
                finishedAtWallMs = null,
                completionMessageUntilWallMs = null,
                autoRepeatScheduledAtWallMs = null
            )
            _timerState.value = updated
            _timerRemainingMs.value = durationToRun
            _timerFinishedBannerVisible.value = false
            preferencesRepository.saveFlipTimerState(updated)
            notificationHelper.scheduleTimerAlarm(targetEnd)
        }
        ensureTickerRunning()
    }

    suspend fun pauseTimer() {
        mutex.withLock {
            val current = _timerState.value
            if (!current.isRunning || current.isPaused) return@withLock
            val nowWall = clockProvider.wallClockMs()
            val remaining = ((current.targetEndWallMs ?: nowWall) - nowWall).coerceAtLeast(1_000L)
            notificationHelper.cancelAllTimerAlarms()
            val updated = current.copy(
                isRunning = false,
                isPaused = true,
                remainingWhenPausedMs = remaining,
                targetEndWallMs = null
            )
            _timerState.value = updated
            _timerRemainingMs.value = remaining
            preferencesRepository.saveFlipTimerState(updated)
        }
    }

    suspend fun toggleTimerPauseResume() {
        val current = _timerState.value
        if (current.isRunning && !current.isPaused) {
            pauseTimer()
        } else {
            startOrResumeTimer()
        }
    }

    suspend fun resetTimer() {
        mutex.withLock {
            notificationHelper.cancelAllTimerAlarms()
            notificationHelper.cancelCompletionNotification()
            val current = _timerState.value
            val updated = current.copy(
                targetEndWallMs = null,
                remainingWhenPausedMs = null,
                isRunning = false,
                isPaused = false,
                finishedAtWallMs = null,
                completionMessageUntilWallMs = null,
                autoRepeatScheduledAtWallMs = null
            )
            _timerState.value = updated
            _timerRemainingMs.value = updated.selectedDurationMs
            _timerFinishedBannerVisible.value = false
            preferencesRepository.saveFlipTimerState(updated)
        }
    }

    suspend fun onTimerAlarmFired(expectedWallMs: Long?) {
        handleCompletionIfDue(expectedWallMs)
    }

    suspend fun onAutoRepeatAlarmFired(expectedWallMs: Long?) {
        handleAutoRepeatIfDue(expectedWallMs)
    }

    private suspend fun handleCompletionIfDue(expectedWallMs: Long?) {
        var didComplete = false
        var completedName = ""
        var repeatAtWallMs: Long? = null

        mutex.withLock {
            val current = _timerState.value
            if (!current.isRunning || current.isPaused) return@withLock
            val targetEnd = current.targetEndWallMs ?: return@withLock
            if (expectedWallMs != null && kotlin.math.abs(targetEnd - expectedWallMs) > 2_000L) {
                return@withLock
            }
            if (current.finishedAtWallMs == targetEnd) return@withLock
            val nowWall = clockProvider.wallClockMs()
            if (nowWall + 250L < targetEnd && expectedWallMs == null) return@withLock

            notificationHelper.cancelAllTimerAlarms()
            val autoRepeat = settings.value.autoRepeat
            val messageUntil = nowWall + COMPLETION_BANNER_DURATION_MS
            val nextRepeatAt = if (autoRepeat) messageUntil else null

            val updated = current.copy(
                targetEndWallMs = null,
                remainingWhenPausedMs = null,
                isRunning = false,
                isPaused = false,
                finishedAtWallMs = targetEnd,
                completionMessageUntilWallMs = messageUntil,
                autoRepeatScheduledAtWallMs = nextRepeatAt
            )
            _timerState.value = updated
            _timerRemainingMs.value = updated.selectedDurationMs
            _timerFinishedBannerVisible.value = true
            preferencesRepository.saveFlipTimerState(updated)
            if (nextRepeatAt != null) {
                notificationHelper.scheduleAutoRepeatAlarm(nextRepeatAt)
            }
            didComplete = true
            completedName = updated.timerName
            repeatAtWallMs = nextRepeatAt
        }

        if (didComplete) {
            notificationHelper.playShortCompletionAlertAndVibrate()
            val session = sessionEngine.activeSession.value
            val isLockActive = session != null && (
                session.isEnforcementPhase || session.state == SessionState.LOCK_TRANSITION_PENDING
                )
            notificationHelper.showTimerCompletedNotification(
                timerName = completedName,
                isFocusLockActive = isLockActive
            )
            ensureTickerRunning()
        }
    }

    private suspend fun handleAutoRepeatIfDue(expectedWallMs: Long?) {
        var restarted = false
        mutex.withLock {
            val current = _timerState.value
            val scheduledAt = current.autoRepeatScheduledAtWallMs ?: return@withLock
            if (current.isRunning) return@withLock
            if (expectedWallMs != null && kotlin.math.abs(scheduledAt - expectedWallMs) > 2_000L) {
                return@withLock
            }
            val nowWall = clockProvider.wallClockMs()
            if (nowWall < scheduledAt && expectedWallMs == null) return@withLock

            if (!settings.value.autoRepeat) {
                val cleared = current.copy(
                    completionMessageUntilWallMs = null,
                    autoRepeatScheduledAtWallMs = null
                )
                _timerState.value = cleared
                _timerFinishedBannerVisible.value = false
                preferencesRepository.saveFlipTimerState(cleared)
                return@withLock
            }

            val durationToRun = current.selectedDurationMs.coerceAtLeast(1_000L)
            val nextTarget = nowWall + durationToRun
            val restartedState = current.copy(
                targetEndWallMs = nextTarget,
                remainingWhenPausedMs = null,
                isRunning = true,
                isPaused = false,
                finishedAtWallMs = null,
                completionMessageUntilWallMs = null,
                autoRepeatScheduledAtWallMs = null
            )
            _timerState.value = restartedState
            _timerRemainingMs.value = durationToRun
            _timerFinishedBannerVisible.value = false
            preferencesRepository.saveFlipTimerState(restartedState)
            notificationHelper.scheduleTimerAlarm(nextTarget)
            restarted = true
        }
        if (restarted) {
            ensureTickerRunning()
        }
    }

    suspend fun startOrResumeStopwatch() {
        mutex.withLock {
            val current = _stopwatchState.value
            if (current.isRunning) return@withLock
            val nowElapsed = clockProvider.elapsedRealtimeMs()
            val nowWall = clockProvider.wallClockMs()
            val updated = current.copy(
                isRunning = true,
                startedElapsedMs = nowElapsed,
                startedWallMs = nowWall
            )
            _stopwatchState.value = updated
            preferencesRepository.saveFlipStopwatchState(updated)
        }
        ensureTickerRunning()
    }

    suspend fun pauseStopwatch() {
        mutex.withLock {
            val current = _stopwatchState.value
            if (!current.isRunning) return@withLock
            val nowElapsed = clockProvider.elapsedRealtimeMs()
            val startElapsed = current.startedElapsedMs ?: nowElapsed
            val delta = (nowElapsed - startElapsed).coerceAtLeast(0L)
            val total = (current.accumulatedMs + delta).coerceAtLeast(0L)
            val updated = FlipStopwatchState(
                isRunning = false,
                accumulatedMs = total,
                startedElapsedMs = null,
                startedWallMs = null
            )
            _stopwatchState.value = updated
            _stopwatchElapsedMs.value = total
            preferencesRepository.saveFlipStopwatchState(updated)
        }
    }

    suspend fun toggleStopwatch() {
        if (_stopwatchState.value.isRunning) {
            pauseStopwatch()
        } else {
            startOrResumeStopwatch()
        }
    }

    suspend fun resetStopwatch() {
        mutex.withLock {
            val cleared = FlipStopwatchState(
                isRunning = false,
                accumulatedMs = 0L,
                startedElapsedMs = null,
                startedWallMs = null
            )
            _stopwatchState.value = cleared
            _stopwatchElapsedMs.value = 0L
            preferencesRepository.saveFlipStopwatchState(cleared)
        }
    }

    suspend fun setUse24HourFormat(enabled: Boolean) {
        updateSyncedSettings { it.copy(use24HourFormat = enabled) }
    }

    suspend fun setUseSystemTimeFormat(enabled: Boolean) {
        updateSyncedSettings { it.copy(useSystemTimeFormat = enabled) }
    }

    suspend fun setHourLeadingZero(enabled: Boolean) {
        updateSyncedSettings { it.copy(hourLeadingZero = enabled) }
    }

    suspend fun setDateFormatPattern(pattern: String) {
        val resolved = com.example.domain.model.FlipDateFormat.fromPattern(pattern).pattern
        updateSyncedSettings { it.copy(dateFormatPattern = resolved) }
    }

    suspend fun setKeepScreenAwake(enabled: Boolean) {
        updateSyncedSettings { it.copy(keepScreenAwake = enabled) }
    }

    suspend fun setShowDate(enabled: Boolean) {
        updateSyncedSettings { it.copy(showDate = enabled) }
    }

    suspend fun setShowWeekday(enabled: Boolean) {
        updateSyncedSettings { it.copy(showWeekday = enabled) }
    }

    suspend fun setShowSeconds(enabled: Boolean) {
        updateSyncedSettings { it.copy(showSeconds = enabled) }
    }

    suspend fun setShowFlipAxis(enabled: Boolean) {
        updateSyncedSettings { it.copy(showFlipAxis = enabled) }
    }

    suspend fun setShowUpcomingAlarm(enabled: Boolean) {
        updateSyncedSettings { it.copy(showUpcomingAlarm = enabled) }
    }

    suspend fun setShowBattery(enabled: Boolean) {
        updateSyncedSettings { it.copy(showBattery = enabled) }
    }

    suspend fun setShowWeather(enabled: Boolean) {
        updateSyncedSettings { it.copy(showWeather = enabled) }
    }

    suspend fun setWeatherLocationQuery(query: String) {
        val cleaned = query.trim().take(48)
        updateSyncedSettings { it.copy(weatherLocationQuery = cleaned) }
    }

    suspend fun setSleepTimerMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(0, 240)
        mutex.withLock {
            val current = preferencesRepository.getFlipClockSettingsSnapshot()
            preferencesRepository.saveFlipClockSettings(current.copy(sleepTimerMinutes = clamped))
        }
    }

    suspend fun setClockTickerSoundEnabled(enabled: Boolean) {
        updateSyncedSettings { it.copy(clockTickerSoundEnabled = enabled) }
    }

    suspend fun setCustomTag(tag: String) {
        val cleaned = tag.take(28)
        updateSyncedSettings { it.copy(customTag = cleaned) }
    }

    suspend fun setAutoRepeat(enabled: Boolean) {
        updateSyncedSettings { it.copy(autoRepeat = enabled) }
        mutex.withLock {
            val current = _timerState.value
            val bannerUntil = current.completionMessageUntilWallMs
            if (bannerUntil != null && !current.isRunning) {
                val updatedTimer = current.copy(
                    autoRepeatScheduledAtWallMs = if (enabled) bannerUntil else null
                )
                _timerState.value = updatedTimer
                preferencesRepository.saveFlipTimerState(updatedTimer)
                if (enabled) {
                    notificationHelper.scheduleAutoRepeatAlarm(bannerUntil)
                } else {
                    notificationHelper.cancelAllTimerAlarms()
                }
            }
        }
    }

    suspend fun setZoomFraction(zoomFraction: Float) {
        val clamped = zoomFraction.coerceIn(
            FlipClockSettings.MIN_ZOOM_FRACTION,
            FlipClockSettings.MAX_ZOOM_FRACTION
        )
        updateSyncedSettings { it.copy(zoomFraction = clamped) }
    }

    suspend fun resetZoom() {
        updateSyncedSettings { it.copy(zoomFraction = FlipClockSettings.DEFAULT_ZOOM_FRACTION) }
    }

    suspend fun saveLastBrightnessPercent(percent: Float) {
        val clamped = percent.coerceIn(
            FlipClockSettings.MIN_BRIGHTNESS_PERCENT,
            FlipClockSettings.MAX_BRIGHTNESS_PERCENT
        )
        mutex.withLock {
            val current = preferencesRepository.getFlipClockSettingsSnapshot()
            preferencesRepository.saveFlipClockSettings(
                current.copy(lastBrightnessPercent = clamped)
            )
        }
    }

    suspend fun resetSettingsToDefaultPreservingCustomPresets() {
        val nowWall = clockProvider.wallClockMs()
        mutex.withLock {
            preferencesRepository.resetFlipClockSettingsOnly(nowWall)
            presetRepository.restoreMissingBuiltInsWithinCap(nowWall)
            val current = _timerState.value
            if (current.autoRepeatScheduledAtWallMs != null) {
                val updated = current.copy(autoRepeatScheduledAtWallMs = null)
                _timerState.value = updated
                preferencesRepository.saveFlipTimerState(updated)
                notificationHelper.cancelAllTimerAlarms()
            }
        }
    }

    suspend fun markNotificationPermissionHandled(denied: Boolean) {
        mutex.withLock {
            val updated = _timerState.value.copy(
                notificationPermissionRequestedOnce = true,
                notificationPermissionDeniedNotice = denied
            )
            _timerState.value = updated
            preferencesRepository.saveFlipTimerState(updated)
        }
    }

    suspend fun saveTimerPreset(
        presetId: String?,
        name: String,
        durationSeconds: Long
    ): FlipPresetMutationResult {
        val result = presetRepository.savePreset(
            presetId = presetId,
            name = name,
            durationSeconds = durationSeconds,
            nowWallMs = clockProvider.wallClockMs()
        )
        if (result is FlipPresetMutationResult.Saved) {
            _presetSyncEvents.tryEmit(FlipPresetSyncEvent.Saved(result.preset))
        }
        return result
    }

    suspend fun deleteTimerPreset(presetId: String) {
        val nowWall = clockProvider.wallClockMs()
        presetRepository.deletePreset(presetId, nowWall)
        _presetSyncEvents.tryEmit(FlipPresetSyncEvent.Deleted(presetId, nowWall))
    }

    private suspend fun updateSyncedSettings(transform: (FlipClockSettings) -> FlipClockSettings) {
        val nowWall = clockProvider.wallClockMs()
        mutex.withLock {
            val current = preferencesRepository.getFlipClockSettingsSnapshot()
            val updated = transform(current).copy(updatedAtWallMs = nowWall)
            preferencesRepository.saveFlipClockSettings(updated)
        }
    }

    private fun ensureTickerRunning() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                val tState = _timerState.value
                val swState = _stopwatchState.value
                val nowWall = clockProvider.wallClockMs()
                val nowElapsed = clockProvider.elapsedRealtimeMs()

                if (tState.isRunning && !tState.isPaused && tState.targetEndWallMs != null) {
                    val rem = (tState.targetEndWallMs - nowWall).coerceAtLeast(0L)
                    _timerRemainingMs.value = rem
                    if (rem <= 0L) {
                        handleCompletionIfDue(tState.targetEndWallMs)
                    }
                } else if (tState.completionMessageUntilWallMs != null) {
                    val stillVisible = nowWall < tState.completionMessageUntilWallMs
                    _timerFinishedBannerVisible.value = stillVisible
                    if (!stillVisible) {
                        if (tState.autoRepeatScheduledAtWallMs != null && settings.value.autoRepeat) {
                            handleAutoRepeatIfDue(tState.autoRepeatScheduledAtWallMs)
                        } else {
                            mutex.withLock {
                                val latest = _timerState.value
                                if (latest.completionMessageUntilWallMs != null &&
                                    clockProvider.wallClockMs() >= latest.completionMessageUntilWallMs
                                ) {
                                    val cleaned = latest.copy(
                                        completionMessageUntilWallMs = null,
                                        autoRepeatScheduledAtWallMs = null
                                    )
                                    _timerState.value = cleaned
                                    _timerFinishedBannerVisible.value = false
                                    preferencesRepository.saveFlipTimerState(cleaned)
                                }
                            }
                        }
                    }
                }

                if (swState.isRunning && swState.startedElapsedMs != null) {
                    val elapsed = (swState.accumulatedMs + (nowElapsed - swState.startedElapsedMs)).coerceAtLeast(0L)
                    _stopwatchElapsedMs.value = elapsed
                }

                delay(200L)
            }
        }
    }

    companion object {
        const val COMPLETION_BANNER_DURATION_MS = 5_000L
    }
}
