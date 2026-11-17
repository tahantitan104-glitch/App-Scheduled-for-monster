package com.example.session

import android.app.Activity
import android.app.ActivityManager
import com.example.data.preferences.ResolvePreferencesRepository
import com.example.data.repository.HistoryRepository
import com.example.data.repository.ScheduleRepository
import com.example.data.repository.SessionRepository
import com.example.devicepolicy.DevicePolicyController
import com.example.domain.model.ActiveSession
import com.example.domain.model.EnforcementMode
import com.example.domain.model.EnforcementPreparationResult
import com.example.domain.model.LockMode
import com.example.domain.model.LockSchedule
import com.example.domain.model.RecoveryVerificationResult
import com.example.domain.model.ScheduleType
import com.example.domain.model.SessionHistoryRecord
import com.example.domain.model.SessionOutcome
import com.example.domain.model.SessionState
import com.example.domain.model.SystemCapabilityStatus
import com.example.domain.scheduling.ScheduleCalculator
import com.example.domain.time.ClockProvider
import com.example.platform.SystemCapabilityChecker
import com.example.scheduling.LockAlarmScheduler
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
    val lockExpirationWallMs: Long
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
    private val platformBridge: SessionPlatformBridge
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    val activeSession: StateFlow<ActiveSession?> = _activeSession.asStateFlow()

    private val _remainingMillis = MutableStateFlow(0L)
    val remainingMillis: StateFlow<Long> = _remainingMillis.asStateFlow()

    private val _capabilities = MutableStateFlow(SystemCapabilityStatus())
    val capabilities: StateFlow<SystemCapabilityStatus> = _capabilities.asStateFlow()

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
        }
        startTickerIfNeeded()
    }

    fun refreshCapabilities() {
        _capabilities.value = capabilityChecker.queryStatus()
    }

    private fun refreshCapabilitiesInternal(): SystemCapabilityStatus {
        val status = capabilityChecker.queryStatus()
        _capabilities.value = status
        return status
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
        phoneAccessEnabled: Boolean
    ): EngineStartResult = mutex.withLock {
        if (lockDurationMinutes <= 0L) {
            return EngineStartResult.Blocked("Select a valid lock duration of at least 1 minute.")
        }
        val current = _activeSession.value
        if (current != null && !current.isTerminal) {
            return EngineStartResult.Blocked("Another session is already active.")
        }
        if (!recoveryCodeManager.hasRecoveryCodeFlow.first()) {
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
                failureReason = reason
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
                        failureReason = prep.reason
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
                        failureReason = prep.reason
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
            bootCountAtStart = bootGen
        )
        _activeSession.value = pendingSession
        sessionRepository.saveActiveSession(pendingSession)
        platformBridge.startMonitorService()
        platformBridge.launchLockScreen()
        EngineStartResult.Started
    }

    suspend fun startDelayedLock(
        freeUseMinutes: Long,
        lockDurationMinutes: Long,
        allowedPackages: List<String>,
        phoneAccessEnabled: Boolean
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
                failureReason = reason
            )
            _activeSession.value = unavailable
            sessionRepository.saveActiveSession(unavailable)
            return EngineStartResult.Blocked(reason)
        }
        if (!recoveryCodeManager.hasRecoveryCodeFlow.first()) {
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
            bootCountAtStart = preferencesRepository.getBootGeneration()
        )
        _activeSession.value = delayedSession
        _remainingMillis.value = freeUseMs
        sessionRepository.saveActiveSession(delayedSession)
        alarmScheduler.scheduleDelayedLockTransition(sessionId, deadlineWall)
        platformBridge.startMonitorService()
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
        phoneAccessEnabled: Boolean
    ): EngineStartResult {
        var delayedShouldStartTicker = false
        var immediateShouldLaunchLock = false

        val result = mutex.withLock {
            val current = _activeSession.value
            if (current != null && !current.isTerminal) {
                return@withLock EngineStartResult.Blocked("Another session is already active on this device.")
            }

            val nowWall = clockProvider.wallClockMs()
            val nowElapsed = clockProvider.elapsedRealtimeMs()
            val mode = command.resolveLockMode()
            val status = refreshCapabilitiesInternal()
            val enforcementMode = resolveAvailableEnforcementMode(status)
            if (enforcementMode == EnforcementMode.NONE) {
                return@withLock EngineStartResult.Blocked(
                    "Synced lock skipped: enable Accessibility Guard on this device."
                )
            }
            if (!recoveryCodeManager.hasRecoveryCodeFlow.first()) {
                return@withLock EngineStartResult.Blocked(
                    "Synced lock skipped: configure a Recovery Code on this device first."
                )
            }

            if (mode == LockMode.DELAYED) {
                val freeDeadline = command.freeUseDeadlineWallMs
                val lockExpire = command.lockExpirationWallMs
                val lockMins = command.lockDurationMinutes.coerceIn(1L, 1440L)
                if (freeDeadline > nowWall) {
                    val remainingFreeMs = (freeDeadline - nowWall).coerceAtLeast(1_000L)
                    val remainingFreeMins = ((remainingFreeMs + 59_999L) / 60_000L).coerceAtLeast(1L)
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
                        freeUseDeadlineWallMs = freeDeadline,
                        freeUseDeadlineElapsedMs = nowElapsed + remainingFreeMs,
                        bootCountAtStart = preferencesRepository.getBootGeneration()
                    )
                    _activeSession.value = delayedSession
                    _remainingMillis.value = remainingFreeMs
                    sessionRepository.saveActiveSession(delayedSession)
                    alarmScheduler.scheduleDelayedLockTransition(sessionId, freeDeadline)
                    platformBridge.startMonitorService()
                    delayedShouldStartTicker = true
                    return@withLock EngineStartResult.Started
                } else if (lockExpire > nowWall) {
                    val remainingLockMs = (lockExpire - nowWall).coerceAtLeast(1_000L)
                    val remainingLockMins = ((remainingLockMs + 59_999L) / 60_000L).coerceAtLeast(1L)
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
                        lockExpirationWallMs = lockExpire,
                        lockExpirationElapsedMs = nowElapsed + remainingLockMs,
                        bootCountAtStart = preferencesRepository.getBootGeneration()
                    )
                    immediateShouldLaunchLock = transitionToPendingLockInternal(session)
                    return@withLock if (immediateShouldLaunchLock) {
                        EngineStartResult.Started
                    } else {
                        EngineStartResult.Blocked("Lock enforcement unavailable on this device.")
                    }
                } else {
                    return@withLock EngineStartResult.Blocked("")
                }
            } else {
                val targetExpireWall = if (command.lockExpirationWallMs > nowWall) {
                    command.lockExpirationWallMs
                } else {
                    nowWall + command.lockDurationMinutes.coerceIn(1L, 1440L) * 60_000L
                }
                // Reject stale commands that already expired
                if (command.lockExpirationWallMs > 0L && command.lockExpirationWallMs <= nowWall) {
                    return@withLock EngineStartResult.Blocked("")
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
                    bootCountAtStart = preferencesRepository.getBootGeneration()
                )
                immediateShouldLaunchLock = transitionToPendingLockInternal(session)
                return@withLock if (immediateShouldLaunchLock) {
                    EngineStartResult.Started
                } else {
                    EngineStartResult.Blocked("Lock enforcement unavailable on this device.")
                }
            }
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

            advanceScheduleAfterTriggerInternal(schedule, nowWall)

            val session = ActiveSession(
                sessionId = UUID.randomUUID().toString(),
                mode = LockMode.SCHEDULED,
                state = SessionState.LOCK_TRANSITION_PENDING,
                enforcementMode = resolveAvailableEnforcementMode(refreshCapabilitiesInternal()),
                allowedPackages = schedule.allowedPackages,
                phoneAccessEnabled = schedule.phoneAccessEnabled,
                freeUseDurationMinutes = 0L,
                lockDurationMinutes = schedule.lockDurationMinutes,
                createdAtWallMs = nowWall,
                bootCountAtStart = preferencesRepository.getBootGeneration(),
                scheduleId = schedule.id
            )
            val transitioned = transitionToPendingLockInternal(session)
            if (transitioned) {
                broadcastPayload = RemoteLockBroadcast(
                    mode = LockMode.SCHEDULED,
                    freeUseMinutes = 0L,
                    lockDurationMinutes = schedule.lockDurationMinutes,
                    freeUseDeadlineWallMs = 0L,
                    lockExpirationWallMs = nowWall + schedule.lockDurationMinutes * 60_000L
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
            platformBridge.stopMonitorService()
            true
        }
        if (shouldSignalExit) {
            _lockExitSignals.tryEmit(LockExitSignal.ExitLockTaskAndFinish)
        }
    }

    suspend fun submitRecoveryCode(code: String): RecoveryVerificationResult {
        val result = recoveryCodeManager.verifyRecoveryCode(code)
        if (result is RecoveryVerificationResult.Verified) {
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
        return result
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
                platformBridge.stopMonitorService()
            } else if (current.state == SessionState.COMPLETED && exited) {
                platformBridge.stopMonitorService()
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
                        if (isBoot) {
                            val restarted = current.copy(
                                state = SessionState.LOCK_TRANSITION_PENDING,
                                lockStartedWallMs = null,
                                lockStartedElapsedMs = null,
                                lockExpirationWallMs = null,
                                lockExpirationElapsedMs = null,
                                bootCountAtStart = preferencesRepository.getBootGeneration()
                            )
                            launchNow = transitionToPendingLockInternal(restarted)
                        } else {
                            val remaining = computeRemainingMillis(current)
                            _remainingMillis.value = remaining
                            if (remaining <= 0L) {
                                onLockExpirationDuringRestoreLocked(current)
                            } else {
                                alarmScheduler.scheduleLockExpiration(
                                    current.sessionId,
                                    clockProvider.wallClockMs() + remaining
                                )
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
                    val session = ActiveSession(
                        sessionId = UUID.randomUUID().toString(),
                        mode = LockMode.SCHEDULED,
                        state = SessionState.LOCK_TRANSITION_PENDING,
                        enforcementMode = resolveAvailableEnforcementMode(refreshCapabilitiesInternal()),
                        allowedPackages = activeSchedule.allowedPackages,
                        phoneAccessEnabled = activeSchedule.phoneAccessEnabled,
                        freeUseDurationMinutes = 0L,
                        lockDurationMinutes = activeSchedule.lockDurationMinutes,
                        createdAtWallMs = nowWall,
                        bootCountAtStart = preferencesRepository.getBootGeneration(),
                        scheduleId = activeSchedule.id
                    )
                    launchNow = transitionToPendingLockInternal(session)
                }
            }

            rescheduleAllEnabledSchedulesInternal()
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
                val remaining = computeRemainingMillis(persisted)
                if (remaining <= 0L) {
                    onLockExpirationDuringRestoreLocked(persisted)
                } else {
                    _remainingMillis.value = remaining
                    alarmScheduler.scheduleLockExpiration(persisted.sessionId, nowWall + remaining)
                    platformBridge.startMonitorService()
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

        val session = ActiveSession(
            sessionId = UUID.randomUUID().toString(),
            mode = LockMode.SCHEDULED,
            state = SessionState.LOCK_TRANSITION_PENDING,
            enforcementMode = resolveAvailableEnforcementMode(refreshCapabilitiesInternal()),
            allowedPackages = dueSchedule.allowedPackages,
            phoneAccessEnabled = dueSchedule.phoneAccessEnabled,
            freeUseDurationMinutes = 0L,
            lockDurationMinutes = dueSchedule.lockDurationMinutes,
            createdAtWallMs = nowWall,
            lockStartedWallMs = startMs,
            lockExpirationWallMs = endWallMs,
            lockExpirationElapsedMs = nowElapsed + remainingMs,
            bootCountAtStart = preferencesRepository.getBootGeneration(),
            scheduleId = dueSchedule.id
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
        platformBridge.stopMonitorService()
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
