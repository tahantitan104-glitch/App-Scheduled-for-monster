package com.example.session

import android.app.Activity
import android.app.ActivityManager
import com.example.data.preferences.ResolvePreferencesRepository
import com.example.data.repository.HistoryRepository
import com.example.data.repository.ScheduleRepository
import com.example.data.repository.SessionRepository
import com.example.devicepolicy.DevicePolicyController
import com.example.domain.model.AccessibilityHealthState
import com.example.domain.model.ActiveSession
import com.example.domain.model.AppLockVerificationResult
import com.example.domain.model.DeviceOwnerState
import com.example.domain.model.EnforcementMode
import com.example.domain.model.EnforcementPreparationResult
import com.example.domain.model.LockMode
import com.example.domain.model.LockSchedule
import com.example.domain.model.NotificationProtectionState
import com.example.domain.model.ProtectionRecoveryAction
import com.example.domain.model.ProtectionStatus
import com.example.domain.model.RecoveryVerificationResult
import com.example.domain.model.ScheduleType
import com.example.domain.model.SessionHistoryRecord
import com.example.domain.model.SessionOutcome
import com.example.domain.model.SessionState
import com.example.domain.model.SystemCapabilityStatus
import com.example.domain.scheduling.ScheduleCalculator
import com.example.domain.time.ClockProvider
import com.example.platform.NotificationControlManager
import com.example.platform.SystemCapabilityChecker
import com.example.scheduling.LockAlarmScheduler
import com.example.security.AccountabilityPartnerManager
import com.example.security.RecoveryCodeManager
import com.example.sync.CloudLockCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

sealed interface EngineStartResult {
    data object Started : EngineStartResult
    data class Blocked(val reason: String) : EngineStartResult
}

sealed interface LockExitSignal {
    data object ExitLockTaskAndFinish : LockExitSignal
}

data class RemoteLockBroadcast(
    val mode: LockMode,
    val freeUseMinutes: Long,
    val lockDurationMinutes: Long,
    val freeUseDeadlineWallMs: Long,
    val lockExpirationWallMs: Long,
    val scheduleId: String = "",
    val occurrenceId: String = "",
    val isStrictMode: Boolean = false,
    val showFlipClockShortcut: Boolean = false,
    val allowSelectedApps: Boolean = true,
    val phoneAccessEnabled: Boolean = true
)

class LockSessionEngine(
    private val sessionRepository: SessionRepository,
    private val scheduleRepository: ScheduleRepository,
    private val historyRepository: HistoryRepository,
    private val preferencesRepository: ResolvePreferencesRepository,
    private val devicePolicyController: DevicePolicyController,
    private val alarmScheduler: LockAlarmScheduler,
    private val recoveryCodeManager: RecoveryCodeManager,
    private val capabilityChecker: SystemCapabilityChecker,
    private val clockProvider: ClockProvider,
    private val platformBridge: SessionPlatformBridge,
    private val notificationControlManager: NotificationControlManager? = null,
    private val accountabilityPartnerManager: AccountabilityPartnerManager? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    val activeSession: StateFlow<ActiveSession?> = _activeSession.asStateFlow()

    private val _remainingMillis = MutableStateFlow(0L)
    val remainingMillis: StateFlow<Long> = _remainingMillis.asStateFlow()

    private val _capabilities = MutableStateFlow(SystemCapabilityStatus())
    val capabilities: StateFlow<SystemCapabilityStatus> = _capabilities.asStateFlow()

    private val _protectionStatus = MutableStateFlow<ProtectionStatus>(ProtectionStatus.ProtectionActive())
    val protectionStatus: StateFlow<ProtectionStatus> = _protectionStatus.asStateFlow()

    private val _lockExitSignals = MutableSharedFlow<LockExitSignal>(extraBufferCapacity = 4)
    val lockExitSignals: SharedFlow<LockExitSignal> = _lockExitSignals.asSharedFlow()

    private val _scheduledBroadcasts = MutableSharedFlow<RemoteLockBroadcast>(extraBufferCapacity = 4)
    val scheduledBroadcasts: SharedFlow<RemoteLockBroadcast> = _scheduledBroadcasts.asSharedFlow()

    private var tickerJob: Job? = null
    private var initialized = false

    suspend fun initialize() {
        mutex.withLock {
            if (initialized) return
            initialized = true
            refreshCapabilitiesInternal()
            val persisted = sessionRepository.getActiveSession()
            _activeSession.value = persisted
            if (persisted != null && !persisted.isTerminal) {
                reconcilePersistedSessionOnProcessStart(persisted)
            }
            reconcileDueScheduleOnProcessStartLocked()
            rescheduleAllEnabledSchedulesInternal()
            syncRecoveryJobInternal()
            updateProtectionStatusInternal()
        }
        startTickerIfNeeded()
    }

    fun refreshCapabilities() {
        _capabilities.value = capabilityChecker.queryStatus()
        updateProtectionStatusInternal()
    }

    private fun refreshCapabilitiesInternal(): SystemCapabilityStatus {
        val status = capabilityChecker.queryStatus()
        _capabilities.value = status
        updateProtectionStatusInternal(status)
        return status
    }

    private fun updateProtectionStatusInternal(
        status: SystemCapabilityStatus = _capabilities.value,
        session: ActiveSession? = _activeSession.value
    ) {
        val notifState = notificationControlManager?.protectionState?.value ?: NotificationProtectionState.INACTIVE
        _protectionStatus.value = computeProtectionStatus(status, session, notifState)
    }

    private fun computeProtectionStatus(
        status: SystemCapabilityStatus,
        session: ActiveSession?,
        notifState: NotificationProtectionState
    ): ProtectionStatus {
        if (session != null && !session.isTerminal && platformBridge.isForegroundServiceBlocked()) {
            return ProtectionStatus.Recovering(
                title = "Focus Session Recovery Ready",
                whatHappened = "Android deferred background service restart after device restart or update.",
                affectedFunctionality = "Session timing stays preserved. Tap below to bring Lock Screen forward.",
                recommendedActionLabel = "Resume Lock Screen",
                actionType = ProtectionRecoveryAction.RETURN_TO_LOCK_SCREEN
            )
        }
        if (session?.state == SessionState.COMPLETED) {
            return ProtectionStatus.SessionCompleted(
                title = "Focus Session Completed",
                whatHappened = "Your focus session finished on schedule.",
                affectedFunctionality = "Normal device access and restored notification policy are active."
            )
        }
        if (session?.state == SessionState.RECOVERED) {
            return ProtectionStatus.SessionCompleted(
                title = "Session Unlocked via Recovery",
                whatHappened = "Focus restriction ended early with your Recovery Code.",
                affectedFunctionality = "Normal device access and restored notification policy are active."
            )
        }
        if (status.deviceOwnerState != DeviceOwnerState.DEVICE_OWNER_ACTIVE) {
            when (status.accessibilityHealthState) {
                AccessibilityHealthState.DISABLED -> return ProtectionStatus.ActionRequired(
                    title = "Accessibility Guard Disabled",
                    whatHappened = "Resolve Focus Guard is not turned on in Android Accessibility Settings.",
                    affectedFunctionality = "Instant, Delayed, Scheduled, and App Locks cannot intercept distracting apps.",
                    recommendedActionLabel = "Enable Accessibility",
                    actionType = ProtectionRecoveryAction.OPEN_ACCESSIBILITY
                )
                AccessibilityHealthState.ENABLED_DISCONNECTED -> return ProtectionStatus.ActionRequired(
                    title = "Accessibility Service Disconnected",
                    whatHappened = "Resolve Focus Guard is toggled on in Settings, but Android has disconnected it.",
                    affectedFunctionality = "App blocking and Focus Guard detection may pause until reconnected.",
                    recommendedActionLabel = "Reconnect Service",
                    actionType = ProtectionRecoveryAction.OPEN_ACCESSIBILITY
                )
                AccessibilityHealthState.ENABLED_CONNECTED -> Unit
            }
        }
        if (!status.canDrawOverlays) {
            return ProtectionStatus.ProtectionLimited(
                title = "Display Over Other Apps Missing",
                whatHappened = "Overlay permission is disabled on this device.",
                affectedFunctionality = "Lock Screen background switches can be delayed on Android 10+.",
                recommendedActionLabel = "Enable Overlay",
                actionType = ProtectionRecoveryAction.OPEN_OVERLAY
            )
        }
        if (!status.isIgnoringBatteryOptimizations) {
            return ProtectionStatus.ProtectionLimited(
                title = "Battery Optimization Active",
                whatHappened = "Android battery optimization may restrict Resolve when the screen stays off.",
                affectedFunctionality = "Delayed Lock or Scheduled transitions could recover later than expected.",
                recommendedActionLabel = "Allow Unrestricted",
                actionType = ProtectionRecoveryAction.OPEN_BATTERY_OPTIMIZATION
            )
        }
        if (status.exactAlarmRequiredByOs && !status.canScheduleExactAlarms) {
            return ProtectionStatus.ProtectionLimited(
                title = "Exact Alarms Disabled",
                whatHappened = "Alarms & Reminders permission is not enabled.",
                affectedFunctionality = "Delayed and Scheduled Lock start/end times may drift by a few minutes.",
                recommendedActionLabel = "Enable Alarms",
                actionType = ProtectionRecoveryAction.OPEN_EXACT_ALARM
            )
        }
        if (notifState == NotificationProtectionState.UNAVAILABLE_NO_PERMISSION) {
            return ProtectionStatus.ProtectionLimited(
                title = "DND Policy Access Missing",
                whatHappened = "Notification Control is turned on, but Notification Policy Access is denied.",
                affectedFunctionality = "Distracting notifications will not be silenced during sessions.",
                recommendedActionLabel = "Grant DND Access",
                actionType = ProtectionRecoveryAction.OPEN_NOTIFICATION_POLICY
            )
        }
        return ProtectionStatus.ProtectionActive(notificationState = notifState)
    }

    private suspend fun syncRecoveryJobInternal() {
        val session = _activeSession.value
        val hasActiveSession = session != null && !session.isTerminal
        val hasEnabledSchedules = scheduleRepository.getAllSchedules().any { it.isEnabled }
        if (hasActiveSession || hasEnabledSchedules) {
            platformBridge.scheduleRecoveryJobIfNeeded()
        } else {
            platformBridge.cancelRecoveryJob()
        }
    }

    private fun resolveAvailableEnforcementMode(status: SystemCapabilityStatus): EnforcementMode {
        return when {
            devicePolicyController.isDeviceOwner() -> EnforcementMode.DEVICE_OWNER_LOCK_TASK
            status.isAccessibilityServiceEnabled -> EnforcementMode.ACCESSIBILITY_GUARD
            else -> EnforcementMode.NONE
        }
    }

    suspend fun startInstantLock(
        lockDurationMinutes: Long,
        allowedPackages: List<String>,
        phoneAccessEnabled: Boolean,
        isStrictMode: Boolean = false,
        showFlipClockShortcut: Boolean = false
    ): EngineStartResult = mutex.withLock {
        if (lockDurationMinutes <= 0L) {
            return EngineStartResult.Blocked("Select a valid lock duration of at least 1 minute.")
        }
        val current = _activeSession.value
        if (current != null && !current.isTerminal) {
            return EngineStartResult.Blocked("Another session is already active.")
        }
        if (!isStrictMode && !recoveryCodeManager.hasRecoveryCodeFlow.first()) {
            return EngineStartResult.Blocked("Configure a 16-digit or 32-digit recovery code before starting a lock session.")
        }

        val nowWall = clockProvider.wallClockMs()
        val bootGen = preferencesRepository.getBootGeneration()
        val sessionId = UUID.randomUUID().toString()
        val status = refreshCapabilitiesInternal()
        val enforcementMode = resolveAvailableEnforcementMode(status)

        if (enforcementMode == EnforcementMode.NONE) {
            val reason = "Enable Accessibility Service (Resolve Focus Guard) in Settings before starting Instant Lock."
            val unavailableSession = ActiveSession(
                sessionId = sessionId,
                mode = LockMode.INSTANT,
                state = SessionState.ENFORCEMENT_UNAVAILABLE,
                enforcementMode = EnforcementMode.NONE,
                allowedPackages = allowedPackages,
                phoneAccessEnabled = phoneAccessEnabled,
                freeUseDurationMinutes = 0L,
                lockDurationMinutes = lockDurationMinutes,
                createdAtWallMs = nowWall,
                bootCountAtStart = bootGen,
                failureReason = reason,
                isStrictMode = isStrictMode,
                showFlipClockShortcut = showFlipClockShortcut
            )
            _activeSession.value = unavailableSession
            sessionRepository.saveActiveSession(unavailableSession)
            return EngineStartResult.Blocked(reason)
        }

        if (enforcementMode == EnforcementMode.DEVICE_OWNER_LOCK_TASK) {
            when (val prep = devicePolicyController.prepareLockTaskPolicy(allowedPackages, phoneAccessEnabled)) {
                is EnforcementPreparationResult.Unavailable -> {
                    val unavailableSession = ActiveSession(
                        sessionId = sessionId,
                        mode = LockMode.INSTANT,
                        state = SessionState.ENFORCEMENT_UNAVAILABLE,
                        enforcementMode = EnforcementMode.NONE,
                        allowedPackages = allowedPackages,
                        phoneAccessEnabled = phoneAccessEnabled,
                        freeUseDurationMinutes = 0L,
                        lockDurationMinutes = lockDurationMinutes,
                        createdAtWallMs = nowWall,
                        bootCountAtStart = bootGen,
                        failureReason = prep.reason,
                        isStrictMode = isStrictMode,
                        showFlipClockShortcut = showFlipClockShortcut
                    )
                    _activeSession.value = unavailableSession
                    sessionRepository.saveActiveSession(unavailableSession)
                    return EngineStartResult.Blocked(prep.reason)
                }
                is EnforcementPreparationResult.Failed -> {
                    val failedSession = ActiveSession(
                        sessionId = sessionId,
                        mode = LockMode.INSTANT,
                        state = SessionState.ENFORCEMENT_FAILED,
                        enforcementMode = EnforcementMode.NONE,
                        allowedPackages = allowedPackages,
                        phoneAccessEnabled = phoneAccessEnabled,
                        freeUseDurationMinutes = 0L,
                        lockDurationMinutes = lockDurationMinutes,
                        createdAtWallMs = nowWall,
                        bootCountAtStart = bootGen,
                        failureReason = prep.reason,
                        isStrictMode = isStrictMode,
                        showFlipClockShortcut = showFlipClockShortcut
                    )
                    _activeSession.value = failedSession
                    sessionRepository.saveActiveSession(failedSession)
                    recordHistoryInternal(failedSession, SessionOutcome.ENFORCEMENT_FAILED, nowWall)
                    return EngineStartResult.Blocked(prep.reason)
                }
                is EnforcementPreparationResult.Ready -> Unit
            }
        }

        val pendingSession = ActiveSession(
            sessionId = sessionId,
            mode = LockMode.INSTANT,
            state = SessionState.LOCK_TRANSITION_PENDING,
            enforcementMode = enforcementMode,
            allowedPackages = allowedPackages,
            phoneAccessEnabled = phoneAccessEnabled,
            freeUseDurationMinutes = 0L,
            lockDurationMinutes = lockDurationMinutes,
            createdAtWallMs = nowWall,
            bootCountAtStart = bootGen,
            isStrictMode = isStrictMode,
            showFlipClockShortcut = showFlipClockShortcut
        )
        _activeSession.value = pendingSession
        sessionRepository.saveActiveSession(pendingSession)
        platformBridge.startMonitorService()
        syncRecoveryJobInternal()
        updateProtectionStatusInternal()
        platformBridge.launchLockScreen()
        EngineStartResult.Started
    }

    suspend fun startDelayedLock(
        freeUseMinutes: Long,
        lockDurationMinutes: Long,
        allowedPackages: List<String>,
        phoneAccessEnabled: Boolean,
        isStrictMode: Boolean = false,
        showFlipClockShortcut: Boolean = false
    ): EngineStartResult = mutex.withLock {
        if (freeUseMinutes <= 0L || lockDurationMinutes <= 0L) {
            return EngineStartResult.Blocked("Both Free Use Window and Lock Duration must be at least 1 minute.")
        }
        val current = _activeSession.value
        if (current != null && !current.isTerminal) {
            return EngineStartResult.Blocked("Another session is already active.")
        }

        val status = refreshCapabilitiesInternal()
        val enforcementMode = resolveAvailableEnforcementMode(status)
        if (enforcementMode == EnforcementMode.NONE) {
            val reason = "Enable Accessibility Service (Resolve Focus Guard) in Settings before starting Delayed Lock."
            val unavailable = ActiveSession(
                sessionId = UUID.randomUUID().toString(),
                mode = LockMode.DELAYED,
                state = SessionState.ENFORCEMENT_UNAVAILABLE,
                enforcementMode = EnforcementMode.NONE,
                allowedPackages = allowedPackages,
                phoneAccessEnabled = phoneAccessEnabled,
                freeUseDurationMinutes = freeUseMinutes,
                lockDurationMinutes = lockDurationMinutes,
                createdAtWallMs = clockProvider.wallClockMs(),
                failureReason = reason,
                isStrictMode = isStrictMode,
                showFlipClockShortcut = showFlipClockShortcut
            )
            _activeSession.value = unavailable
            sessionRepository.saveActiveSession(unavailable)
            return EngineStartResult.Blocked(reason)
        }
        if (!isStrictMode && !recoveryCodeManager.hasRecoveryCodeFlow.first()) {
            return EngineStartResult.Blocked("Configure a 16-digit or 32-digit recovery code before starting Delayed Lock.")
        }

        val nowWall = clockProvider.wallClockMs()
        val nowElapsed = clockProvider.elapsedRealtimeMs()
        val freeUseMs = freeUseMinutes * 60_000L
        val deadlineWall = nowWall + freeUseMs
        val deadlineElapsed = nowElapsed + freeUseMs
        val sessionId = UUID.randomUUID().toString()

        val delayedSession = ActiveSession(
            sessionId = sessionId,
            mode = LockMode.DELAYED,
            state = SessionState.DELAYED_FREE_USE_ACTIVE,
            enforcementMode = enforcementMode,
            allowedPackages = allowedPackages,
            phoneAccessEnabled = phoneAccessEnabled,
            freeUseDurationMinutes = freeUseMinutes,
            lockDurationMinutes = lockDurationMinutes,
            createdAtWallMs = nowWall,
            freeUseStartedElapsedMs = nowElapsed,
            freeUseDeadlineWallMs = deadlineWall,
            freeUseDeadlineElapsedMs = deadlineElapsed,
            bootCountAtStart = preferencesRepository.getBootGeneration(),
            isStrictMode = isStrictMode,
            showFlipClockShortcut = showFlipClockShortcut
        )
        _activeSession.value = delayedSession
        _remainingMillis.value = freeUseMs
        sessionRepository.saveActiveSession(delayedSession)
        alarmScheduler.scheduleDelayedLockTransition(sessionId, deadlineWall)
        platformBridge.startMonitorService()
        syncRecoveryJobInternal()
        updateProtectionStatusInternal()
        startTickerIfNeeded()
        EngineStartResult.Started
    }

    suspend fun onDelayedFreeUseDeadlineReached(expectedSessionId: String? = null) {
        val shouldLaunch = mutex.withLock {
            val current = _activeSession.value ?: return@withLock false
            if (current.state != SessionState.DELAYED_FREE_USE_ACTIVE) return@withLock false
            if (expectedSessionId != null && current.sessionId != expectedSessionId) return@withLock false

            alarmScheduler.cancelDelayedLockTransition()
            transitionToPendingLockInternal(current)
        }
        if (shouldLaunch) {
            platformBridge.launchLockScreen()
        }
    }

    suspend fun applyRemoteLockCommand(
        command: CloudLockCommand,
        allowedPackages: List<String>,
        phoneAccessEnabled: Boolean,
        isStrictMode: Boolean = false,
        showFlipClockShortcut: Boolean = false,
        serverClockOffsetMs: Long = 0L,
        localDeviceId: String = ""
    ): EngineStartResult {
        var delayedShouldStartTicker = false
        var immediateShouldLaunchLock = false
        var shouldEmitLockExit = false

        val result = mutex.withLock {
            if (localDeviceId.isNotBlank() &&
                command.targetDeviceId.isNotBlank() &&
                command.targetDeviceId != localDeviceId
            ) {
                return@withLock EngineStartResult.Blocked("")
            }

            val nowWall = clockProvider.wallClockMs()
            val nowElapsed = clockProvider.elapsedRealtimeMs()
            val serverNowWall = nowWall + serverClockOffsetMs
            val action = command.commandAction.uppercase().ifBlank { "START" }

            if (action == "STOP") {
                val current = _activeSession.value ?: return@withLock EngineStartResult.Blocked("")
                if (current.isTerminal) return@withLock EngineStartResult.Blocked("")
                if (command.issuedAtWallMs > 0L && (serverNowWall - command.issuedAtWallMs) > 24L * 60L * 60L * 1000L) {
                    return@withLock EngineStartResult.Blocked("")
                }
                if (current.state == SessionState.DELAYED_FREE_USE_ACTIVE ||
                    current.enforcementMode != EnforcementMode.DEVICE_OWNER_LOCK_TASK
                ) {
                    val recovered = current.copy(state = SessionState.RECOVERED)
                    _activeSession.value = recovered
                    _remainingMillis.value = 0L
                    sessionRepository.saveActiveSession(recovered)
                    alarmScheduler.cancelAllSessionAlarms()
                    recordHistoryInternal(recovered, SessionOutcome.RECOVERED, nowWall)
                    notificationControlManager?.onSessionEnded(recovered.sessionId)
                    platformBridge.stopMonitorService()
                    syncRecoveryJobInternal()
                    updateProtectionStatusInternal()
                    platformBridge.notifyLockStateChanged()
                    shouldEmitLockExit = true
                } else {
                    val recovering = current.copy(state = SessionState.RECOVERY_IN_PROGRESS)
                    _activeSession.value = recovering
                    sessionRepository.saveActiveSession(recovering)
                    shouldEmitLockExit = true
                }
                return@withLock EngineStartResult.Started
            }

            if (action == "EXTEND") {
                val current = _activeSession.value ?: return@withLock EngineStartResult.Blocked("No active lock session to extend.")
                if (current.isTerminal) return@withLock EngineStartResult.Blocked("No active lock session to extend.")
                if (command.issuedAtWallMs > 0L && (serverNowWall - command.issuedAtWallMs) > 24L * 60L * 60L * 1000L) {
                    return@withLock EngineStartResult.Blocked("")
                }
                val addMinutes = command.extendMinutes.takeIf { it > 0L }
                    ?: command.lockDurationMinutes.coerceIn(1L, 720L)
                val addMs = addMinutes * 60_000L
                val baseWall = current.lockExpirationWallMs?.takeIf { it > nowWall }
                    ?: (nowWall + current.lockDurationMinutes.coerceAtLeast(1L) * 60_000L)
                val baseElapsed = current.lockExpirationElapsedMs?.takeIf { it > nowElapsed }
                    ?: (nowElapsed + current.lockDurationMinutes.coerceAtLeast(1L) * 60_000L)
                val newExpireWall = baseWall + addMs
                val newExpireElapsed = baseElapsed + addMs
                val updated = current.copy(
                    lockDurationMinutes = (current.lockDurationMinutes + addMinutes).coerceAtMost(1440L),
                    lockExpirationWallMs = newExpireWall,
                    lockExpirationElapsedMs = newExpireElapsed
                )
                _activeSession.value = updated
                if (updated.isEnforcementPhase) {
                    _remainingMillis.value = (newExpireElapsed - nowElapsed).coerceAtLeast(0L)
                    alarmScheduler.scheduleLockExpiration(updated.sessionId, newExpireWall)
                }
                sessionRepository.saveActiveSession(updated)
                platformBridge.notifyLockStateChanged()
                return@withLock EngineStartResult.Started
            }

            val mode = command.resolveLockMode()

            // Reject stale/expired commands relative to server-calibrated clock
            if (command.lockExpirationWallMs > 0L && command.lockExpirationWallMs <= serverNowWall) {
                return@withLock EngineStartResult.Blocked("")
            }
            if (command.issuedAtWallMs > 0L && (serverNowWall - command.issuedAtWallMs) > 24L * 60L * 60L * 1000L) {
                return@withLock EngineStartResult.Blocked("")
            }

            if (command.occurrenceId.isNotBlank() &&
                preferencesRepository.hasProcessedOccurrenceId(command.occurrenceId)
            ) {
                return@withLock EngineStartResult.Blocked("")
            }

            val current = _activeSession.value
            if (current != null && !current.isTerminal) {
                val canEscalateDelayedToImmediate =
                    current.state == SessionState.DELAYED_FREE_USE_ACTIVE && mode != LockMode.DELAYED
                if (!canEscalateDelayedToImmediate) {
                    return@withLock EngineStartResult.Blocked("Existing active focus lock preserved on this device.")
                }
                alarmScheduler.cancelDelayedLockTransition()
            }

            val status = refreshCapabilitiesInternal()
            val enforcementMode = resolveAvailableEnforcementMode(status)
            if (enforcementMode == EnforcementMode.NONE) {
                return@withLock EngineStartResult.Blocked(
                    "Synced lock skipped: enable Accessibility Guard on this device."
                )
            }
            if (!isStrictMode && !recoveryCodeManager.hasRecoveryCodeFlow.first()) {
                return@withLock EngineStartResult.Blocked(
                    "Synced lock skipped: configure a Recovery Code on this device first."
                )
            }

            if (mode == LockMode.DELAYED) {
                val freeDeadlineServer = command.freeUseDeadlineWallMs
                val lockExpireServer = command.lockExpirationWallMs
                val lockMins = command.lockDurationMinutes.coerceIn(1L, 1440L)
                if (freeDeadlineServer > serverNowWall) {
                    val remainingFreeMs = (freeDeadlineServer - serverNowWall).coerceAtLeast(1_000L)
                    val remainingFreeMins = ((remainingFreeMs + 59_999L) / 60_000L).coerceAtLeast(1L)
                    val localFreeDeadlineWall = nowWall + remainingFreeMs
                    val localLockExpireWall = if (lockExpireServer > freeDeadlineServer) {
                        nowWall + (lockExpireServer - serverNowWall)
                    } else {
                        localFreeDeadlineWall + lockMins * 60_000L
                    }
                    val localLockExpireElapsed = nowElapsed + (localLockExpireWall - nowWall).coerceAtLeast(1_000L)
                    val sessionId = UUID.randomUUID().toString()
                    val delayedSession = ActiveSession(
                        sessionId = sessionId,
                        mode = LockMode.DELAYED,
                        state = SessionState.DELAYED_FREE_USE_ACTIVE,
                        enforcementMode = enforcementMode,
                        allowedPackages = allowedPackages,
                        phoneAccessEnabled = phoneAccessEnabled,
                        freeUseDurationMinutes = remainingFreeMins,
                        lockDurationMinutes = lockMins,
                        createdAtWallMs = nowWall,
                        freeUseStartedElapsedMs = nowElapsed,
                        freeUseDeadlineWallMs = localFreeDeadlineWall,
                        freeUseDeadlineElapsedMs = nowElapsed + remainingFreeMs,
                        lockExpirationWallMs = localLockExpireWall,
                        lockExpirationElapsedMs = localLockExpireElapsed,
                        bootCountAtStart = preferencesRepository.getBootGeneration(),
                        isStrictMode = isStrictMode,
                        showFlipClockShortcut = showFlipClockShortcut
                    )
                    _activeSession.value = delayedSession
                    _remainingMillis.value = remainingFreeMs
                    sessionRepository.saveActiveSession(delayedSession)
                    alarmScheduler.scheduleDelayedLockTransition(sessionId, localFreeDeadlineWall)
                    platformBridge.startMonitorService()
                    syncRecoveryJobInternal()
                    updateProtectionStatusInternal()
                    delayedShouldStartTicker = true
                    if (command.occurrenceId.isNotBlank()) {
                        preferencesRepository.recordProcessedOccurrenceId(command.occurrenceId)
                    }
                    return@withLock EngineStartResult.Started
                } else if (lockExpireServer > serverNowWall) {
                    val remainingLockMs = (lockExpireServer - serverNowWall).coerceAtLeast(1_000L)
                    val remainingLockMins = ((remainingLockMs + 59_999L) / 60_000L).coerceAtLeast(1L)
                    val localLockExpireWall = nowWall + remainingLockMs
                    val session = ActiveSession(
                        sessionId = UUID.randomUUID().toString(),
                        mode = LockMode.DELAYED,
                        state = SessionState.LOCK_TRANSITION_PENDING,
                        enforcementMode = enforcementMode,
                        allowedPackages = allowedPackages,
                        phoneAccessEnabled = phoneAccessEnabled,
                        freeUseDurationMinutes = 0L,
                        lockDurationMinutes = remainingLockMins,
                        createdAtWallMs = nowWall,
                        lockExpirationWallMs = localLockExpireWall,
                        lockExpirationElapsedMs = nowElapsed + remainingLockMs,
                        bootCountAtStart = preferencesRepository.getBootGeneration(),
                        isStrictMode = isStrictMode,
                        showFlipClockShortcut = showFlipClockShortcut
                    )
                    immediateShouldLaunchLock = transitionToPendingLockInternal(session)
                    if (immediateShouldLaunchLock) {
                        syncRecoveryJobInternal()
                        updateProtectionStatusInternal()
                        if (command.occurrenceId.isNotBlank()) {
                            preferencesRepository.recordProcessedOccurrenceId(command.occurrenceId)
                        }
                    }
                    return@withLock if (immediateShouldLaunchLock) {
                        EngineStartResult.Started
                    } else {
                        EngineStartResult.Blocked("Lock enforcement unavailable on this device.")
                    }
                } else {
                    return@withLock EngineStartResult.Blocked("")
                }
            } else {
                val baseExpireWall = if (command.lockExpirationWallMs > serverNowWall) {
                    nowWall + (command.lockExpirationWallMs - serverNowWall)
                } else {
                    nowWall + command.lockDurationMinutes.coerceIn(1L, 1440L) * 60_000L
                }
                val targetExpireWall = if (current?.state == SessionState.DELAYED_FREE_USE_ACTIVE) {
                    maxOf(baseExpireWall, nowWall + current.lockDurationMinutes.coerceIn(1L, 1440L) * 60_000L)
                } else {
                    baseExpireWall
                }
                val remainingMs = (targetExpireWall - nowWall).coerceAtLeast(1_000L)
                val effectiveMins = ((remainingMs + 59_999L) / 60_000L).coerceAtLeast(1L)
                val session = ActiveSession(
                    sessionId = UUID.randomUUID().toString(),
                    mode = mode,
                    state = SessionState.LOCK_TRANSITION_PENDING,
                    enforcementMode = enforcementMode,
                    allowedPackages = allowedPackages,
                    phoneAccessEnabled = phoneAccessEnabled,
                    freeUseDurationMinutes = 0L,
                    lockDurationMinutes = effectiveMins,
                    createdAtWallMs = nowWall,
                    lockExpirationWallMs = targetExpireWall,
                    lockExpirationElapsedMs = nowElapsed + remainingMs,
                    bootCountAtStart = preferencesRepository.getBootGeneration(),
                    isStrictMode = isStrictMode,
                    showFlipClockShortcut = showFlipClockShortcut
                )
                immediateShouldLaunchLock = transitionToPendingLockInternal(session)
                if (immediateShouldLaunchLock) {
                    syncRecoveryJobInternal()
                    updateProtectionStatusInternal()
                    if (command.occurrenceId.isNotBlank()) {
                        preferencesRepository.recordProcessedOccurrenceId(command.occurrenceId)
                    }
                }
                return@withLock if (immediateShouldLaunchLock) {
                    EngineStartResult.Started
                } else {
                    EngineStartResult.Blocked("Lock enforcement unavailable on this device.")
                }
            }
        }

        if (shouldEmitLockExit) {
            _lockExitSignals.tryEmit(LockExitSignal.ExitLockTaskAndFinish)
        }
        if (delayedShouldStartTicker) {
            startTickerIfNeeded()
        }
        if (immediateShouldLaunchLock) {
            platformBridge.launchLockScreen()
        }
        return result
    }

    suspend fun onScheduleTriggered(scheduleId: Long) {
        var broadcastPayload: RemoteLockBroadcast? = null
        val shouldLaunch = mutex.withLock {
            val schedule = scheduleRepository.getScheduleById(scheduleId) ?: return@withLock false
            if (!schedule.isEnabled) return@withLock false

            val nowWall = clockProvider.wallClockMs()
            val current = _activeSession.value
            if (current != null && !current.isTerminal) {
                advanceScheduleAfterTriggerInternal(schedule, nowWall)
                return@withLock false
            }

            val inProgressStart = ScheduleCalculator.findInProgressWindowStartMs(
                schedule,
                nowWall,
                clockProvider.zoneId()
            )
            if (inProgressStart == null) {
                advanceScheduleAfterTriggerInternal(schedule, nowWall)
                return@withLock false
            }

            val stableSchedId = schedule.cloudId.ifBlank { schedule.id.toString() }
            val occurrenceId = "occ_${stableSchedId}_$inProgressStart"
            if (preferencesRepository.hasProcessedOccurrenceId(occurrenceId)) {
                advanceScheduleAfterTriggerInternal(schedule, nowWall)
                return@withLock false
            }

            advanceScheduleAfterTriggerInternal(schedule, nowWall)
            preferencesRepository.recordProcessedOccurrenceId(occurrenceId)

            val effectiveAllowed = if (schedule.allowSelectedApps) schedule.allowedPackages else emptyList()
            val session = ActiveSession(
                sessionId = UUID.randomUUID().toString(),
                mode = LockMode.SCHEDULED,
                state = SessionState.LOCK_TRANSITION_PENDING,
                enforcementMode = resolveAvailableEnforcementMode(refreshCapabilitiesInternal()),
                allowedPackages = effectiveAllowed,
                phoneAccessEnabled = schedule.phoneAccessEnabled,
                freeUseDurationMinutes = 0L,
                lockDurationMinutes = schedule.lockDurationMinutes,
                createdAtWallMs = nowWall,
                bootCountAtStart = preferencesRepository.getBootGeneration(),
                scheduleId = schedule.id,
                isStrictMode = schedule.isStrictMode,
                showFlipClockShortcut = schedule.showFlipClockShortcut
            )
            val transitioned = transitionToPendingLockInternal(session)
            if (transitioned && schedule.syncAcrossDevices) {
                broadcastPayload = RemoteLockBroadcast(
                    mode = LockMode.SCHEDULED,
                    freeUseMinutes = 0L,
                    lockDurationMinutes = schedule.lockDurationMinutes,
                    freeUseDeadlineWallMs = 0L,
                    lockExpirationWallMs = nowWall + schedule.lockDurationMinutes * 60_000L,
                    scheduleId = stableSchedId,
                    occurrenceId = occurrenceId,
                    isStrictMode = schedule.isStrictMode,
                    showFlipClockShortcut = schedule.showFlipClockShortcut,
                    allowSelectedApps = schedule.allowSelectedApps,
                    phoneAccessEnabled = schedule.phoneAccessEnabled
                )
            }
            transitioned
        }
        broadcastPayload?.let { _scheduledBroadcasts.tryEmit(it) }
        if (shouldLaunch) {
            platformBridge.launchLockScreen()
        }
    }

    private suspend fun transitionToPendingLockInternal(session: ActiveSession): Boolean {
        val nowWall = clockProvider.wallClockMs()
        val status = refreshCapabilitiesInternal()
        val enforcementMode = resolveAvailableEnforcementMode(status)

        if (enforcementMode == EnforcementMode.NONE) {
            val failed = session.copy(
                state = SessionState.ENFORCEMENT_UNAVAILABLE,
                enforcementMode = EnforcementMode.NONE,
                failureReason = "Accessibility Service (Resolve Focus Guard) or Device Owner is disabled."
            )
            _activeSession.value = failed
            sessionRepository.saveActiveSession(failed)
            recordHistoryInternal(failed, SessionOutcome.ENFORCEMENT_FAILED, nowWall)
            platformBridge.stopMonitorService()
            return false
        }

        if (enforcementMode == EnforcementMode.DEVICE_OWNER_LOCK_TASK) {
            when (val prep = devicePolicyController.prepareLockTaskPolicy(session.allowedPackages, session.phoneAccessEnabled)) {
                is EnforcementPreparationResult.Ready -> Unit
                is EnforcementPreparationResult.Unavailable -> {
                    val failed = session.copy(
                        state = SessionState.ENFORCEMENT_UNAVAILABLE,
                        enforcementMode = EnforcementMode.NONE,
                        failureReason = prep.reason
                    )
                    _activeSession.value = failed
                    sessionRepository.saveActiveSession(failed)
                    recordHistoryInternal(failed, SessionOutcome.ENFORCEMENT_FAILED, nowWall)
                    platformBridge.stopMonitorService()
                    return false
                }
                is EnforcementPreparationResult.Failed -> {
                    val failed = session.copy(
                        state = SessionState.ENFORCEMENT_FAILED,
                        enforcementMode = EnforcementMode.NONE,
                        failureReason = prep.reason
                    )
                    _activeSession.value = failed
                    sessionRepository.saveActiveSession(failed)
                    recordHistoryInternal(failed, SessionOutcome.ENFORCEMENT_FAILED, nowWall)
                    platformBridge.stopMonitorService()
                    return false
                }
            }
        }

        val pending = session.copy(
            state = SessionState.LOCK_TRANSITION_PENDING,
            enforcementMode = enforcementMode,
            failureReason = null
        )
        _activeSession.value = pending
        sessionRepository.saveActiveSession(pending)
        platformBridge.startMonitorService()
        return true
    }

    suspend fun confirmLockTaskEntered(activity: Activity): Boolean {
        val confirmed = mutex.withLock {
            val current = _activeSession.value ?: return@withLock false
            val status = refreshCapabilitiesInternal()
            val mode = when {
                current.enforcementMode != EnforcementMode.NONE -> current.enforcementMode
                else -> resolveAvailableEnforcementMode(status)
            }

            if (current.state == SessionState.LOCK_ENFORCEMENT_ACTIVE) {
                val alreadyActive = when (mode) {
                    EnforcementMode.DEVICE_OWNER_LOCK_TASK ->
                        devicePolicyController.getLockTaskModeState() == 1
                    EnforcementMode.ACCESSIBILITY_GUARD -> status.isAccessibilityServiceEnabled
                    EnforcementMode.NONE -> false
                }
                if (alreadyActive) return@withLock true
            }

            if (current.state != SessionState.LOCK_TRANSITION_PENDING &&
                current.state != SessionState.LOCK_ENFORCEMENT_ACTIVE
            ) {
                return@withLock false
            }

            val entered = when (mode) {
                EnforcementMode.DEVICE_OWNER_LOCK_TASK -> devicePolicyController.enterLockTask(activity)
                EnforcementMode.ACCESSIBILITY_GUARD -> status.isAccessibilityServiceEnabled
                EnforcementMode.NONE -> false
            }

            if (!entered) {
                val failed = current.copy(
                    state = SessionState.ENFORCEMENT_FAILED,
                    failureReason = if (mode == EnforcementMode.DEVICE_OWNER_LOCK_TASK) {
                        "Android did not confirm Lock Task Mode entry."
                    } else {
                        "Accessibility Service (Resolve Focus Guard) is not active."
                    }
                )
                _activeSession.value = failed
                sessionRepository.saveActiveSession(failed)
                recordHistoryInternal(failed, SessionOutcome.ENFORCEMENT_FAILED, clockProvider.wallClockMs())
                platformBridge.stopMonitorService()
                return@withLock false
            }

            // Lock Duration begins ONLY after enforcement is confirmed (preserving remaining window on scheduled/synced/active restore)
            val nowWall = clockProvider.wallClockMs()
            val nowElapsed = clockProvider.elapsedRealtimeMs()
            val durationMs = current.lockDurationMinutes * 60_000L
            val hasPreservedTargetWindow = current.lockExpirationWallMs != null &&
                current.lockExpirationElapsedMs != null &&
                current.lockExpirationWallMs > nowWall
            val expireWall = current.lockExpirationWallMs?.takeIf {
                (current.state == SessionState.LOCK_ENFORCEMENT_ACTIVE && current.lockStartedElapsedMs != null) ||
                    (hasPreservedTargetWindow && it > nowWall)
            } ?: (nowWall + durationMs)
            val expireElapsed = current.lockExpirationElapsedMs?.takeIf {
                (current.state == SessionState.LOCK_ENFORCEMENT_ACTIVE && current.lockStartedElapsedMs != null) ||
                    (hasPreservedTargetWindow && it > nowElapsed)
            } ?: (nowElapsed + durationMs)

            val active = current.copy(
                state = SessionState.LOCK_ENFORCEMENT_ACTIVE,
                enforcementMode = mode,
                lockStartedWallMs = current.lockStartedWallMs ?: nowWall,
                lockStartedElapsedMs = current.lockStartedElapsedMs ?: nowElapsed,
                lockExpirationWallMs = expireWall,
                lockExpirationElapsedMs = expireElapsed,
                failureReason = null
            )
            _activeSession.value = active
            _remainingMillis.value = (expireElapsed - nowElapsed).coerceAtLeast(0L)
            sessionRepository.saveActiveSession(active)
            alarmScheduler.scheduleLockExpiration(active.sessionId, expireWall)
            platformBridge.startMonitorService()
            syncRecoveryJobInternal()
            notificationControlManager?.onSessionEnforcementStarted(active.sessionId)
            updateProtectionStatusInternal()
            platformBridge.notifyLockStateChanged()
            true
        }
        if (confirmed) {
            startTickerIfNeeded()
        }
        return confirmed
    }

    suspend fun onLockExpirationReached(expectedSessionId: String? = null) {
        val shouldSignalExit = mutex.withLock {
            val current = _activeSession.value ?: return@withLock false
            if (!current.isEnforcementPhase) return@withLock false
            if (expectedSessionId != null && current.sessionId != expectedSessionId) return@withLock false

            val nowWall = clockProvider.wallClockMs()
            val completed = current.copy(state = SessionState.COMPLETED)
            _activeSession.value = completed
            _remainingMillis.value = 0L
            sessionRepository.saveActiveSession(completed)
            alarmScheduler.cancelAllSessionAlarms()
            recordHistoryInternal(completed, SessionOutcome.COMPLETED, nowWall)
            notificationControlManager?.onSessionEnded(completed.sessionId)
            platformBridge.stopMonitorService()
            syncRecoveryJobInternal()
            updateProtectionStatusInternal()
            platformBridge.notifyLockStateChanged()
            true
        }
        if (shouldSignalExit) {
            _lockExitSignals.tryEmit(LockExitSignal.ExitLockTaskAndFinish)
        }
    }

    suspend fun submitRecoveryCode(code: String): RecoveryVerificationResult {
        val strictBlocked = mutex.withLock {
            _activeSession.value?.isStrictMode == true
        }
        if (strictBlocked) {
            return RecoveryVerificationResult.NotConfigured
        }
        val result = recoveryCodeManager.verifyRecoveryCode(code)
        if (result is RecoveryVerificationResult.Verified) {
            val shouldSignal = mutex.withLock {
                val current = _activeSession.value ?: return@withLock false
                if (current.isStrictMode) return@withLock false
                if (!current.isEnforcementPhase && current.state != SessionState.LOCK_TRANSITION_PENDING) {
                    return@withLock false
                }
                val recovering = current.copy(state = SessionState.RECOVERY_IN_PROGRESS)
                _activeSession.value = recovering
                sessionRepository.saveActiveSession(recovering)
                true
            }
            if (shouldSignal) {
                _lockExitSignals.tryEmit(LockExitSignal.ExitLockTaskAndFinish)
            } else {
                return RecoveryVerificationResult.NotConfigured
            }
        }
        return result
    }

    suspend fun submitPartnerStrictUnlock(pin: String): AppLockVerificationResult {
        val manager = accountabilityPartnerManager ?: return AppLockVerificationResult.NotConfigured
        val cfg = manager.configFlow.first()
        if (!cfg.enabled || !cfg.hasPartnerPin || !cfg.requireForStrictUnlock) {
            return AppLockVerificationResult.NotConfigured
        }
        val res = manager.verifyPartnerPin(pin)
        if (res is AppLockVerificationResult.Verified) {
            val shouldSignal = mutex.withLock {
                val current = _activeSession.value ?: return@withLock false
                if (!current.isEnforcementPhase && current.state != SessionState.LOCK_TRANSITION_PENDING) {
                    return@withLock false
                }
                val recovering = current.copy(state = SessionState.RECOVERY_IN_PROGRESS)
                _activeSession.value = recovering
                sessionRepository.saveActiveSession(recovering)
                true
            }
            if (shouldSignal) {
                _lockExitSignals.tryEmit(LockExitSignal.ExitLockTaskAndFinish)
            }
        }
        return res
    }

    suspend fun finalizeLockExit(activity: Activity) {
        val currentBefore = _activeSession.value
        val exited = when (currentBefore?.enforcementMode) {
            EnforcementMode.DEVICE_OWNER_LOCK_TASK -> devicePolicyController.exitLockTask(activity)
            else -> true
        }
        mutex.withLock {
            val current = _activeSession.value ?: return@withLock
            if (current.state == SessionState.RECOVERY_IN_PROGRESS && exited) {
                val nowWall = clockProvider.wallClockMs()
                val recovered = current.copy(state = SessionState.RECOVERED)
                _activeSession.value = recovered
                _remainingMillis.value = 0L
                sessionRepository.saveActiveSession(recovered)
                alarmScheduler.cancelAllSessionAlarms()
                recordHistoryInternal(recovered, SessionOutcome.RECOVERED, nowWall)
                notificationControlManager?.onSessionEnded(recovered.sessionId)
                platformBridge.stopMonitorService()
                syncRecoveryJobInternal()
                platformBridge.notifyLockStateChanged()
            } else if (current.state == SessionState.COMPLETED && exited) {
                notificationControlManager?.onSessionEnded(current.sessionId)
                platformBridge.stopMonitorService()
                syncRecoveryJobInternal()
                platformBridge.notifyLockStateChanged()
            }
            refreshCapabilitiesInternal()
        }
    }

    suspend fun clearTerminalSession() {
        mutex.withLock {
            val current = _activeSession.value ?: return@withLock
            if (current.isTerminal) {
                _activeSession.value = null
                _remainingMillis.value = 0L
                sessionRepository.clearActiveSession()
                updateProtectionStatusInternal()
            }
        }
    }

    suspend fun onSystemBootOrTimeChange(isBoot: Boolean) {
        val shouldLaunch = mutex.withLock {
            if (isBoot) {
                preferencesRepository.incrementBootGeneration()
            }
            val current = _activeSession.value
            var launchNow = false

            if (current != null && !current.isTerminal) {
                when (current.state) {
                    SessionState.DELAYED_FREE_USE_ACTIVE -> {
                        val nowWall = clockProvider.wallClockMs()
                        val nowElapsed = clockProvider.elapsedRealtimeMs()
                        val deadlineWall = current.freeUseDeadlineWallMs ?: nowWall
                        if (nowWall >= deadlineWall) {
                            launchNow = transitionToPendingLockInternal(current)
                        } else {
                            val remaining = deadlineWall - nowWall
                            val updated = current.copy(
                                freeUseDeadlineElapsedMs = nowElapsed + remaining
                            )
                            _activeSession.value = updated
                            _remainingMillis.value = remaining
                            sessionRepository.saveActiveSession(updated)
                            alarmScheduler.scheduleDelayedLockTransition(updated.sessionId, deadlineWall)
                            platformBridge.startMonitorService()
                        }
                    }

                    SessionState.LOCK_ENFORCEMENT_ACTIVE,
                    SessionState.PHONE_CALL_ACTIVE,
                    SessionState.LOCK_TRANSITION_PENDING,
                    SessionState.RECOVERY_IN_PROGRESS -> {
                        val nowWall = clockProvider.wallClockMs()
                        val nowElapsed = clockProvider.elapsedRealtimeMs()
                        val expireWall = current.lockExpirationWallMs
                        if (isBoot) {
                            preferencesRepository.recordSessionRecoveryTimestamp(nowWall)
                            if (expireWall != null) {
                                val remaining = (expireWall - nowWall).coerceAtLeast(0L)
                                if (remaining <= 0L) {
                                    onLockExpirationDuringRestoreLocked(current)
                                } else {
                                    // Preserve original deadline & compute remaining time across reboot cleanly
                                    val resumed = current.copy(
                                        state = SessionState.LOCK_TRANSITION_PENDING,
                                        lockExpirationWallMs = expireWall,
                                        lockExpirationElapsedMs = nowElapsed + remaining,
                                        bootCountAtStart = preferencesRepository.getBootGeneration()
                                    )
                                    _remainingMillis.value = remaining
                                    launchNow = transitionToPendingLockInternal(resumed)
                                    alarmScheduler.scheduleLockExpiration(resumed.sessionId, expireWall)
                                    notificationControlManager?.reconcileDuringActiveSession(resumed.sessionId)
                                }
                            } else {
                                // Only never-confirmed pending transitions lack a deadline; keep duration unchanged
                                val resumed = current.copy(
                                    state = SessionState.LOCK_TRANSITION_PENDING,
                                    bootCountAtStart = preferencesRepository.getBootGeneration()
                                )
                                launchNow = transitionToPendingLockInternal(resumed)
                            }
                        } else {
                            val remaining = if (expireWall != null) {
                                (expireWall - nowWall).coerceAtLeast(0L)
                            } else {
                                computeRemainingMillis(current)
                            }
                            _remainingMillis.value = remaining
                            if (remaining <= 0L && expireWall != null) {
                                onLockExpirationDuringRestoreLocked(current)
                            } else if (remaining > 0L) {
                                val targetWall = expireWall ?: (nowWall + remaining)
                                val updated = current.copy(
                                    lockExpirationWallMs = targetWall,
                                    lockExpirationElapsedMs = nowElapsed + remaining
                                )
                                _activeSession.value = updated
                                sessionRepository.saveActiveSession(updated)
                                alarmScheduler.scheduleLockExpiration(updated.sessionId, targetWall)
                                notificationControlManager?.reconcileDuringActiveSession(updated.sessionId)
                            }
                        }
                    }

                    else -> Unit
                }
            } else {
                val nowWall = clockProvider.wallClockMs()
                val schedules = scheduleRepository.getAllSchedules()
                val activeSchedule = schedules.firstOrNull { schedule ->
                    schedule.isEnabled && ScheduleCalculator.findInProgressWindowStartMs(
                        schedule,
                        nowWall,
                        clockProvider.zoneId()
                    ) != null
                }
                if (activeSchedule != null) {
                    advanceScheduleAfterTriggerInternal(activeSchedule, nowWall)
                    val effectiveAllowed = if (activeSchedule.allowSelectedApps) {
                        activeSchedule.allowedPackages
                    } else {
                        emptyList()
                    }
                    val session = ActiveSession(
                        sessionId = UUID.randomUUID().toString(),
                        mode = LockMode.SCHEDULED,
                        state = SessionState.LOCK_TRANSITION_PENDING,
                        enforcementMode = resolveAvailableEnforcementMode(refreshCapabilitiesInternal()),
                        allowedPackages = effectiveAllowed,
                        phoneAccessEnabled = activeSchedule.phoneAccessEnabled,
                        freeUseDurationMinutes = 0L,
                        lockDurationMinutes = activeSchedule.lockDurationMinutes,
                        createdAtWallMs = nowWall,
                        bootCountAtStart = preferencesRepository.getBootGeneration(),
                        scheduleId = activeSchedule.id,
                        isStrictMode = activeSchedule.isStrictMode,
                        showFlipClockShortcut = activeSchedule.showFlipClockShortcut
                    )
                    launchNow = transitionToPendingLockInternal(session)
                }
            }

            rescheduleAllEnabledSchedulesInternal()
            syncRecoveryJobInternal()
            refreshCapabilitiesInternal()
            launchNow
        }

        startTickerIfNeeded()
        if (shouldLaunch) {
            platformBridge.launchLockScreen()
        }
    }

    suspend fun syncAllSchedules() {
        mutex.withLock {
            rescheduleAllEnabledSchedulesInternal()
            syncRecoveryJobInternal()
        }
    }

    private suspend fun reconcilePersistedSessionOnProcessStart(persisted: ActiveSession) {
        val nowWall = clockProvider.wallClockMs()
        val nowElapsed = clockProvider.elapsedRealtimeMs()
        when (persisted.state) {
            SessionState.DELAYED_FREE_USE_ACTIVE -> {
                val deadlineWall = persisted.freeUseDeadlineWallMs ?: nowWall
                if (nowWall >= deadlineWall) {
                    if (transitionToPendingLockInternal(persisted)) {
                        platformBridge.launchLockScreen()
                    }
                } else {
                    val remaining = deadlineWall - nowWall
                    val updated = persisted.copy(freeUseDeadlineElapsedMs = nowElapsed + remaining)
                    _activeSession.value = updated
                    _remainingMillis.value = remaining
                    sessionRepository.saveActiveSession(updated)
                    alarmScheduler.scheduleDelayedLockTransition(updated.sessionId, deadlineWall)
                    platformBridge.startMonitorService()
                }
            }
            SessionState.LOCK_ENFORCEMENT_ACTIVE,
            SessionState.PHONE_CALL_ACTIVE,
            SessionState.RECOVERY_IN_PROGRESS -> {
                val wallRemaining = persisted.lockExpirationWallMs?.let { (it - nowWall).coerceAtLeast(0L) }
                val remaining = wallRemaining ?: computeRemainingMillis(persisted)
                if (remaining <= 0L) {
                    onLockExpirationDuringRestoreLocked(persisted)
                } else {
                    val updated = persisted.copy(
                        lockExpirationElapsedMs = nowElapsed + remaining
                    )
                    _activeSession.value = updated
                    _remainingMillis.value = remaining
                    sessionRepository.saveActiveSession(updated)
                    alarmScheduler.scheduleLockExpiration(updated.sessionId, nowWall + remaining)
                    platformBridge.startMonitorService()
                    notificationControlManager?.reconcileDuringActiveSession(updated.sessionId)
                    preferencesRepository.recordSessionRecoveryTimestamp(nowWall)
                }
            }
            SessionState.LOCK_TRANSITION_PENDING -> {
                platformBridge.startMonitorService()
            }
            else -> Unit
        }
    }

    private suspend fun reconcileDueScheduleOnProcessStartLocked() {
        val current = _activeSession.value
        if (current != null && !current.isTerminal) return

        val nowWall = clockProvider.wallClockMs()
        val nowElapsed = clockProvider.elapsedRealtimeMs()
        val zone = clockProvider.zoneId()
        val dueSchedule = scheduleRepository.getAllSchedules().firstOrNull { schedule ->
            schedule.isEnabled &&
                schedule.nextTriggerWallMs != null &&
                schedule.nextTriggerWallMs <= nowWall &&
                ScheduleCalculator.findInProgressWindowStartMs(schedule, nowWall, zone) != null
        } ?: return

        val startMs = ScheduleCalculator.findInProgressWindowStartMs(dueSchedule, nowWall, zone) ?: return
        val endWallMs = startMs + (dueSchedule.lockDurationMinutes * 60_000L)
        val remainingMs = (endWallMs - nowWall).coerceAtLeast(1_000L)
        advanceScheduleAfterTriggerInternal(dueSchedule, nowWall)

        val effectiveAllowed = if (dueSchedule.allowSelectedApps) {
            dueSchedule.allowedPackages
        } else {
            emptyList()
        }
        val session = ActiveSession(
            sessionId = UUID.randomUUID().toString(),
            mode = LockMode.SCHEDULED,
            state = SessionState.LOCK_TRANSITION_PENDING,
            enforcementMode = resolveAvailableEnforcementMode(refreshCapabilitiesInternal()),
            allowedPackages = effectiveAllowed,
            phoneAccessEnabled = dueSchedule.phoneAccessEnabled,
            freeUseDurationMinutes = 0L,
            lockDurationMinutes = dueSchedule.lockDurationMinutes,
            createdAtWallMs = nowWall,
            lockStartedWallMs = startMs,
            lockExpirationWallMs = endWallMs,
            lockExpirationElapsedMs = nowElapsed + remainingMs,
            bootCountAtStart = preferencesRepository.getBootGeneration(),
            scheduleId = dueSchedule.id,
            isStrictMode = dueSchedule.isStrictMode,
            showFlipClockShortcut = dueSchedule.showFlipClockShortcut
        )
        if (transitionToPendingLockInternal(session)) {
            platformBridge.launchLockScreen()
        }
    }

    private suspend fun onLockExpirationDuringRestoreLocked(session: ActiveSession) {
        val nowWall = clockProvider.wallClockMs()
        val completed = session.copy(state = SessionState.COMPLETED)
        _activeSession.value = completed
        _remainingMillis.value = 0L
        sessionRepository.saveActiveSession(completed)
        alarmScheduler.cancelAllSessionAlarms()
        recordHistoryInternal(completed, SessionOutcome.COMPLETED, nowWall)
        notificationControlManager?.onSessionEnded(completed.sessionId)
        platformBridge.stopMonitorService()
        syncRecoveryJobInternal()
        updateProtectionStatusInternal()
        _lockExitSignals.tryEmit(LockExitSignal.ExitLockTaskAndFinish)
    }

    private suspend fun advanceScheduleAfterTriggerInternal(schedule: LockSchedule, nowWall: Long) {
        if (schedule.type == ScheduleType.ONE_TIME) {
            scheduleRepository.saveSchedule(schedule.copy(isEnabled = false, nextTriggerWallMs = null))
            alarmScheduler.cancelScheduleTrigger(schedule.id)
        } else {
            val next = ScheduleCalculator.computeNextTriggerWallMs(
                schedule,
                nowWall + 60_000L,
                clockProvider.zoneId()
            )
            scheduleRepository.saveSchedule(schedule.copy(nextTriggerWallMs = next))
            if (next != null) {
                alarmScheduler.scheduleNextScheduleTrigger(schedule.id, next)
            }
        }
    }

    private suspend fun rescheduleAllEnabledSchedulesInternal() {
        val nowWall = clockProvider.wallClockMs()
        val zone = clockProvider.zoneId()
        val schedules = scheduleRepository.getAllSchedules()
        schedules.forEach { schedule ->
            if (!schedule.isEnabled) {
                alarmScheduler.cancelScheduleTrigger(schedule.id)
                if (schedule.nextTriggerWallMs != null) {
                    scheduleRepository.saveSchedule(schedule.copy(nextTriggerWallMs = null))
                }
            } else {
                val next = ScheduleCalculator.computeNextTriggerWallMs(schedule, nowWall, zone)
                if (next == null && schedule.type == ScheduleType.ONE_TIME) {
                    scheduleRepository.saveSchedule(schedule.copy(isEnabled = false, nextTriggerWallMs = null))
                    alarmScheduler.cancelScheduleTrigger(schedule.id)
                } else {
                    if (schedule.nextTriggerWallMs != next) {
                        scheduleRepository.saveSchedule(schedule.copy(nextTriggerWallMs = next))
                    }
                    if (next != null) {
                        alarmScheduler.scheduleNextScheduleTrigger(schedule.id, next)
                    }
                }
            }
        }
    }

    private suspend fun recordHistoryInternal(
        session: ActiveSession,
        outcome: SessionOutcome,
        endedAtWallMs: Long
    ) {
        val startedWall = session.lockStartedWallMs ?: session.createdAtWallMs
        historyRepository.recordOnce(
            SessionHistoryRecord(
                sessionId = session.sessionId,
                mode = session.mode,
                freeUseDurationMinutes = session.freeUseDurationMinutes,
                lockDurationMinutes = session.lockDurationMinutes,
                startedAtWallMs = startedWall,
                endedAtWallMs = endedAtWallMs,
                outcome = outcome,
                allowedAppCount = session.allowedPackages.size,
                scheduleId = session.scheduleId
            )
        )
    }

    private fun computeRemainingMillis(session: ActiveSession): Long {
        val nowElapsed = clockProvider.elapsedRealtimeMs()
        val nowWall = clockProvider.wallClockMs()
        return when (session.state) {
            SessionState.DELAYED_FREE_USE_ACTIVE -> {
                val elapsedDeadline = session.freeUseDeadlineElapsedMs
                val wallDeadline = session.freeUseDeadlineWallMs
                when {
                    elapsedDeadline != null -> (elapsedDeadline - nowElapsed).coerceAtLeast(0L)
                    wallDeadline != null -> (wallDeadline - nowWall).coerceAtLeast(0L)
                    else -> 0L
                }
            }
            SessionState.LOCK_ENFORCEMENT_ACTIVE,
            SessionState.PHONE_CALL_ACTIVE,
            SessionState.RECOVERY_IN_PROGRESS -> {
                val elapsedExpire = session.lockExpirationElapsedMs
                val wallExpire = session.lockExpirationWallMs
                when {
                    elapsedExpire != null -> (elapsedExpire - nowElapsed).coerceAtLeast(0L)
                    wallExpire != null -> (wallExpire - nowWall).coerceAtLeast(0L)
                    else -> 0L
                }
            }
            else -> 0L
        }
    }

    private fun startTickerIfNeeded() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                val session = _activeSession.value
                if (session == null || session.isTerminal) {
                    _remainingMillis.value = 0L
                    delay(1000L)
                    continue
                }

                val remaining = computeRemainingMillis(session)
                _remainingMillis.value = remaining

                if (session.state == SessionState.DELAYED_FREE_USE_ACTIVE && remaining <= 0L) {
                    onDelayedFreeUseDeadlineReached(session.sessionId)
                } else if (session.state == SessionState.LOCK_ENFORCEMENT_ACTIVE && remaining <= 0L) {
                    onLockExpirationReached(session.sessionId)
                }

                delay(500L)
            }
        }
    }

    fun shutdown() {
        tickerJob?.cancel()
    }
}
