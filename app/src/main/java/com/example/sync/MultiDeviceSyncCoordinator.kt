package com.example.sync

import android.content.Context
import android.net.Uri
import com.example.data.preferences.ResolvePreferencesRepository
import com.example.data.repository.AllowlistRepository
import com.example.data.repository.DelayedPresetRepository
import com.example.data.repository.FlipTimerPresetRepository
import com.example.data.repository.ScheduleRepository
import com.example.domain.model.DelayedLockPreset
import com.example.domain.model.FlipClockSettings
import com.example.domain.model.FlipTimerPreset
import com.example.domain.model.LockMode
import com.example.domain.model.LockProfileSettings
import com.example.domain.model.LockSchedule
import com.example.domain.scheduling.ScheduleCalculator
import com.example.domain.time.ClockProvider
import com.example.flipclock.FlipClockManager
import com.example.flipclock.FlipPresetSyncEvent
import com.example.session.EngineStartResult
import com.example.session.LockSessionEngine
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coordinates optional multi-device Google account linking, synced profile avatars,
 * cross-device lock triggers, Delayed Lock presets, and synced schedules while
 * preserving strict per-device preferences and offline-first local state.
 */
class MultiDeviceSyncCoordinator(
    private val appContext: Context,
    private val gateway: CloudSyncGateway,
    private val preferencesRepository: ResolvePreferencesRepository,
    private val allowlistRepository: AllowlistRepository,
    private val sessionEngine: LockSessionEngine,
    private val clockProvider: ClockProvider,
    private val delayedPresetRepository: DelayedPresetRepository? = null,
    private val scheduleRepository: ScheduleRepository? = null,
    private val flipTimerPresetRepository: FlipTimerPresetRepository? = null,
    private val flipClockManager: FlipClockManager? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commandMutex = Mutex()

    private val _uiState = MutableStateFlow(AccountSyncUiState())
    val uiState: StateFlow<AccountSyncUiState> = _uiState.asStateFlow()

    private val _syncEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val syncEvents: SharedFlow<String> = _syncEvents.asSharedFlow()

    private var started = false
    private var authObserveJob: Job? = null
    private var activeUserListenersJob: Job? = null
    private var activeUid: String? = null
    private var lastAuthTokenEpochMs: Long = 0L

    @Volatile
    private var lastRemoteOrUploadedFlipSettingsWallMs: Long = 0L

    @Volatile
    private var lastObservedFlipSyncedSignature: FlipSyncedSignature? = null

    @Volatile
    private var isLocalAvatarWriteInFlight: Boolean = false

    @Volatile
    private var pendingRemoteAvatarClear: Boolean = false

    private data class FlipSyncedSignature(
        val use24HourFormat: Boolean,
        val useSystemTimeFormat: Boolean,
        val hourLeadingZero: Boolean,
        val dateFormatPattern: String,
        val keepScreenAwake: Boolean,
        val showDate: Boolean,
        val showWeekday: Boolean,
        val showSeconds: Boolean,
        val showFlipAxis: Boolean,
        val showUpcomingAlarm: Boolean,
        val showBattery: Boolean,
        val showWeather: Boolean,
        val weatherLocationQuery: String,
        val clockTickerSoundEnabled: Boolean,
        val customTag: String,
        val autoRepeat: Boolean,
        val zoomFraction: Float
    )

    private fun FlipClockSettings.syncedSignature(): FlipSyncedSignature = FlipSyncedSignature(
        use24HourFormat = use24HourFormat,
        useSystemTimeFormat = useSystemTimeFormat,
        hourLeadingZero = hourLeadingZero,
        dateFormatPattern = resolvedDateFormat.pattern,
        keepScreenAwake = keepScreenAwake,
        showDate = showDate,
        showWeekday = showWeekday,
        showSeconds = showSeconds,
        showFlipAxis = showFlipAxis,
        showUpcomingAlarm = showUpcomingAlarm,
        showBattery = showBattery,
        showWeather = showWeather,
        weatherLocationQuery = weatherLocationQuery,
        clockTickerSoundEnabled = clockTickerSoundEnabled,
        customTag = customTag,
        autoRepeat = autoRepeat,
        zoomFraction = clampedZoomFraction
    )

    fun start() {
        if (started) return
        started = true

        scope.launch {
            delayedPresetRepository?.ensureBuiltInPresets()
            val deviceId = preferencesRepository.getOrCreateDeviceInstallId()
            val syncEnabled = preferencesRepository.isSyncLockEnabledSnapshot()
            val localName = preferencesRepository.getLocalProfileNameSnapshot()
            val localAvatar = preferencesRepository.getLocalAvatarBase64Snapshot()
            _uiState.update {
                it.copy(
                    displayName = localName.ifBlank { it.displayName },
                    avatarBase64 = localAvatar.ifBlank { it.avatarBase64 },
                    thisDeviceId = deviceId,
                    thisDeviceName = resolveReadableDeviceName(),
                    syncLockEnabled = syncEnabled
                )
            }
        }

        scope.launch {
            preferencesRepository.syncLockEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(syncLockEnabled = enabled) }
                val uid = activeUid
                if (!uid.isNullOrBlank()) {
                    val deviceId = preferencesRepository.getOrCreateDeviceInstallId()
                    gateway.updateDeviceSyncEnabled(
                        userId = uid,
                        deviceId = deviceId,
                        deviceName = resolveReadableDeviceName(),
                        syncEnabled = enabled
                    )
                }
            }
        }

        scope.launch {
            sessionEngine.scheduledBroadcasts.collect { broadcast ->
                publishLockTriggerIfEnabled(
                    mode = broadcast.mode,
                    freeUseMinutes = broadcast.freeUseMinutes,
                    lockDurationMinutes = broadcast.lockDurationMinutes,
                    freeUseDeadlineWallMs = broadcast.freeUseDeadlineWallMs,
                    lockExpirationWallMs = broadcast.lockExpirationWallMs,
                    syncAcrossDevices = true,
                    scheduleId = broadcast.scheduleId,
                    occurrenceId = broadcast.occurrenceId,
                    strictMode = broadcast.isStrictMode,
                    showFlipClockShortcut = broadcast.showFlipClockShortcut
                )
            }
        }

        scope.launch {
            preferencesRepository.flipClockSettingsFlow.collect { settings ->
                val signature = settings.syncedSignature()
                val prevSignature = lastObservedFlipSyncedSignature
                lastObservedFlipSyncedSignature = signature
                val isFirstObservation = prevSignature == null
                val syncedFieldChanged = !isFirstObservation && signature != prevSignature
                if (syncedFieldChanged &&
                    settings.updatedAtWallMs > lastRemoteOrUploadedFlipSettingsWallMs &&
                    activeUid != null
                ) {
                    lastRemoteOrUploadedFlipSettingsWallMs = settings.updatedAtWallMs
                    syncFlipClockSettingsToCloud(settings)
                }
            }
        }

        if (flipClockManager != null) {
            scope.launch {
                flipClockManager.presetSyncEvents.collect { event ->
                    when (event) {
                        is FlipPresetSyncEvent.Saved -> syncFlipTimerPresetToCloud(event.preset, deleted = false)
                        is FlipPresetSyncEvent.Deleted -> syncFlipTimerPresetToCloud(
                            FlipTimerPreset(
                                presetId = event.presetId,
                                name = "Deleted Preset",
                                durationSeconds = 60L,
                                builtIn = false,
                                sortOrder = 0,
                                updatedAtWallMs = event.deletedAtWallMs
                            ),
                            deleted = true
                        )
                    }
                }
            }
        }

        authObserveJob = scope.launch {
            gateway.authState.collect { user ->
                handleAuthUserChanged(user)
            }
        }
    }

    private suspend fun handleAuthUserChanged(user: FirebaseUser?) {
        if (user == null) {
            stopUserListeners()
            activeUid = null
            val localName = preferencesRepository.getLocalProfileNameSnapshot()
            val localAvatar = preferencesRepository.getLocalAvatarBase64Snapshot()
            _uiState.update {
                AccountSyncUiState(
                    isSignedIn = false,
                    isAuthBusy = false,
                    displayName = localName,
                    avatarBase64 = localAvatar,
                    thisDeviceId = it.thisDeviceId,
                    thisDeviceName = it.thisDeviceName,
                    syncLockEnabled = it.syncLockEnabled
                )
            }
            return
        }

        val uid = user.uid
        if (activeUid == uid && activeUserListenersJob?.isActive == true) {
            return
        }

        stopUserListeners()
        activeUid = uid
        val deviceId = preferencesRepository.getOrCreateDeviceInstallId()
        val deviceName = resolveReadableDeviceName()
        val syncEnabled = preferencesRepository.isSyncLockEnabledSnapshot()

        val localName = preferencesRepository.getLocalProfileNameSnapshot()
        val localAvatar = preferencesRepository.getLocalAvatarBase64Snapshot()
        _uiState.update {
            it.copy(
                isSignedIn = true,
                isAuthBusy = false,
                userId = uid,
                displayName = localName.ifBlank {
                    user.displayName?.takeIf { n -> n.isNotBlank() }
                        ?: user.email?.substringBefore('@')
                        ?: "Resolve Focus User"
                },
                email = user.email.orEmpty(),
                photoUrl = user.photoUrl?.toString().orEmpty(),
                avatarBase64 = localAvatar.ifBlank { it.avatarBase64 },
                thisDeviceId = deviceId,
                thisDeviceName = deviceName,
                syncLockEnabled = syncEnabled,
                errorMessage = null
            )
        }

        scope.launch {
            gateway.ensureUserProfile(user)
            gateway.registerOrUpdateDevice(
                userId = uid,
                deviceId = deviceId,
                deviceName = deviceName,
                syncEnabled = syncEnabled
            )
        }

        startUserListeners(uid, deviceId)
    }

    private fun startUserListeners(uid: String, localDeviceId: String) {
        activeUserListenersJob = scope.launch {
            // 1. Profile & Synced Avatar stream
            launch {
                gateway.observeUserProfile(uid)
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                        _uiState.update {
                            it.copy(errorMessage = CloudAccountRepository.unwrapFirestoreErrorMessage(error, "Sync interrupted."))
                        }
                    }
                    .collect { profile ->
                        if (profile != null) {
                            val remoteAvatar = profile.avatarBase64
                            val remoteName = profile.displayName
                            if (remoteName.isNotBlank()) {
                                preferencesRepository.setLocalProfileName(remoteName)
                            }
                            if (remoteAvatar.isNotBlank() && !pendingRemoteAvatarClear) {
                                preferencesRepository.setLocalAvatarBase64(remoteAvatar)
                                _uiState.update {
                                    it.copy(
                                        displayName = remoteName.ifBlank { it.displayName },
                                        email = profile.email.ifBlank { it.email },
                                        photoUrl = profile.photoUrl.ifBlank { it.photoUrl },
                                        avatarBase64 = remoteAvatar,
                                        errorMessage = null
                                    )
                                }
                            } else {
                                val localAvatar = preferencesRepository.getLocalAvatarBase64Snapshot()
                                if (localAvatar.isNotBlank() && !pendingRemoteAvatarClear && !isLocalAvatarWriteInFlight) {
                                    // Local avatar was saved offline before sign-in -> push up to Firestore instead of erasing
                                    isLocalAvatarWriteInFlight = true
                                    scope.launch {
                                        try {
                                            gateway.updateAvatarBase64(uid, localAvatar)
                                        } finally {
                                            isLocalAvatarWriteInFlight = false
                                        }
                                    }
                                    _uiState.update {
                                        it.copy(
                                            displayName = remoteName.ifBlank { it.displayName },
                                            email = profile.email.ifBlank { it.email },
                                            photoUrl = profile.photoUrl.ifBlank { it.photoUrl },
                                            avatarBase64 = localAvatar,
                                            errorMessage = null
                                        )
                                    }
                                } else if (!isLocalAvatarWriteInFlight) {
                                    if (pendingRemoteAvatarClear && remoteAvatar.isBlank()) {
                                        pendingRemoteAvatarClear = false
                                    }
                                    _uiState.update {
                                        it.copy(
                                            displayName = remoteName.ifBlank { it.displayName },
                                            email = profile.email.ifBlank { it.email },
                                            photoUrl = profile.photoUrl.ifBlank { it.photoUrl },
                                            avatarBase64 = if (pendingRemoteAvatarClear) "" else it.avatarBase64.ifBlank { remoteAvatar },
                                            errorMessage = null
                                        )
                                    }
                                }
                            }
                        }
                    }
            }

            // 2. Linked Devices stream
            launch {
                gateway.observeDevices(uid)
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                    }
                    .collect { devices ->
                        _uiState.update { state ->
                            state.copy(connectedDevices = devices.sortedByDescending { it.syncEnabled })
                        }
                    }
            }

            // 3A. Versioned lock_commands stream
            launch {
                gateway.observeRecentLockCommands(uid)
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                    }
                    .collect { commands ->
                        // Process newest valid unseen commands cleanly in ascending order
                        commands.sortedBy { it.issuedAtWallMs }.forEach { cmd ->
                            processIncomingLockCommand(cmd, localDeviceId)
                        }
                    }
            }

            // 3B. Legacy sync/lock_command stream for backward parity
            launch {
                gateway.observeLatestLockCommand(uid)
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                    }
                    .collect { command ->
                        if (command != null) {
                            processIncomingLockCommand(command, localDeviceId)
                        }
                    }
            }

            // 4. Synced Custom Delayed Presets stream
            if (delayedPresetRepository != null) {
                launch {
                    gateway.observeDelayedPresets(uid)
                        .catch { error ->
                            val fsErr = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (fsErr == null && error is CancellationException) throw error
                        }
                        .collect { remotePresets ->
                            mergeRemotePresets(remotePresets, localDeviceId)
                        }
                }
            }

            // 5. Synced Schedules stream (schedules sync rules, NOT device allowlists)
            if (scheduleRepository != null) {
                launch {
                    gateway.observeSyncedSchedules(uid)
                        .catch { error ->
                            val fsErr = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (fsErr == null && error is CancellationException) throw error
                        }
                        .collect { remoteSchedules ->
                            mergeRemoteSchedules(remoteSchedules, localDeviceId)
                        }
                }
            }

            // 6. Synced Lock Profile Settings stream (INSTANT / DELAYED / SCHEDULED defaults)
            launch {
                gateway.observeLockProfiles(uid)
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                    }
                    .collect { remoteProfiles ->
                        mergeRemoteLockProfiles(remoteProfiles, localDeviceId)
                    }
            }

            // 7. Synced Flip Clock Settings stream (excludes local lastBrightnessPercent)
            launch {
                gateway.observeFlipClockSettings(uid)
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                    }
                    .collect { remoteSettings ->
                        mergeRemoteFlipClockSettings(remoteSettings, localDeviceId)
                    }
            }

            // 8. Synced Flip Timer Presets stream (max 15 cap + tombstone LWW merge)
            if (flipTimerPresetRepository != null) {
                launch {
                    gateway.observeFlipTimerPresets(uid)
                        .catch { error ->
                            val fsErr = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (fsErr == null && error is CancellationException) throw error
                        }
                        .collect { remotePresets ->
                            mergeRemoteFlipTimerPresets(remotePresets, localDeviceId)
                        }
                }
            }
        }
    }

    private suspend fun processIncomingLockCommand(
        command: CloudLockCommand,
        localDeviceId: String
    ) {
        commandMutex.withLock {
            if (command.commandId.isBlank()) return
            if (command.originDeviceId == localDeviceId) return

            if (preferencesRepository.hasProcessedSyncCommandId(command.commandId)) return
            if (command.occurrenceId.isNotBlank() &&
                preferencesRepository.hasProcessedOccurrenceId(command.occurrenceId)
            ) {
                preferencesRepository.recordProcessedSyncCommandId(command.commandId)
                return
            }

            // Always record this command ID as seen so stale commands never re-fire later
            preferencesRepository.recordProcessedSyncCommandId(command.commandId)

            // Respect device-level opt-out toggle ("alada alada" independent operation)
            if (!preferencesRepository.isSyncLockEnabledSnapshot()) return

            // Use THIS phone's own local profile allowlist & phone-access setting
            val targetMode = command.resolveLockMode()
            val localProfile = preferencesRepository.getLockProfileSettingsSnapshot(targetMode)
            val effectiveAllowed = if (localProfile.allowSelectedApps) {
                localProfile.allowedPackages.ifEmpty { allowlistRepository.getAllowedPackages() }.distinct()
            } else {
                emptyList()
            }
            val localPhoneAccess = preferencesRepository.defaultPhoneAccessFlow.first()

            val result = sessionEngine.applyRemoteLockCommand(
                command = command,
                allowedPackages = effectiveAllowed,
                phoneAccessEnabled = localPhoneAccess,
                isStrictMode = command.strictMode,
                showFlipClockShortcut = command.showFlipClockShortcut
            )
            if (result is EngineStartResult.Started) {
                val fromName = command.originDeviceName.ifBlank { "another device" }
                _uiState.update { it.copy(lastSyncedStatusMessage = "Synced lock started from $fromName") }
                _syncEvents.tryEmit("Lock synced from $fromName")
            } else if (result is EngineStartResult.Blocked && result.reason.isNotBlank()) {
                _syncEvents.tryEmit(result.reason)
            }
        }
    }

    private suspend fun mergeRemotePresets(
        remotePresets: List<CloudDelayedPreset>,
        localDeviceId: String
    ) {
        val repo = delayedPresetRepository ?: return
        repo.ensureBuiltInPresets()
        val localMap = repo.getAllPresets().associateBy { it.presetId }
        remotePresets.forEach { remote ->
            if (remote.presetId.isBlank() || remote.presetId == DelayedPresetRepository.QUICK_RECOVERY_ID) {
                return@forEach
            }
            if (remote.originDeviceId == localDeviceId) return@forEach
            val local = localMap[remote.presetId]
            if (remote.deleted) {
                if (local != null && !local.builtIn && remote.updatedAtWallMs >= local.updatedAtWallMs) {
                    repo.deleteCustomPreset(remote.presetId)
                }
            } else if (local == null || remote.updatedAtWallMs > local.updatedAtWallMs) {
                repo.savePreset(
                    DelayedLockPreset(
                        presetId = remote.presetId,
                        name = remote.name.ifBlank { "Synced Preset" },
                        freeUseMinutes = remote.freeUseMinutes.coerceIn(1L, 1440L),
                        lockDurationMinutes = remote.lockDurationMinutes.coerceIn(1L, 1440L),
                        builtIn = false,
                        updatedAtWallMs = remote.updatedAtWallMs
                    )
                )
            }
        }
    }

    private suspend fun mergeRemoteSchedules(
        remoteSchedules: List<CloudScheduleRecord>,
        localDeviceId: String
    ) {
        val repo = scheduleRepository ?: return
        val localAllowed = allowlistRepository.getAllowedPackages()
        val localPhoneAccess = preferencesRepository.defaultPhoneAccessFlow.first()
        val nowWall = clockProvider.wallClockMs()
        val zone = clockProvider.zoneId()
        var anyChanged = false

        remoteSchedules.forEach { remote ->
            if (remote.scheduleId.isBlank()) return@forEach
            if (remote.originDeviceId == localDeviceId) return@forEach

            val existing = repo.getScheduleByCloudId(remote.scheduleId)
            if (remote.deleted || !remote.syncAcrossDevices) {
                if (existing != null) {
                    repo.deleteSchedule(existing.id)
                    anyChanged = true
                }
                return@forEach
            }

            if (existing == null || remote.updatedAtWallMs > existing.createdAtWallMs) {
                val baseSchedule = LockSchedule(
                    id = existing?.id ?: 0L,
                    name = remote.name.ifBlank { "Synced Schedule" },
                    type = remote.resolveScheduleType(),
                    oneTimeDateEpochDay = remote.oneTimeDateEpochDay.takeIf { it >= 0L },
                    weekdaysMask = remote.weekdaysMask,
                    startHour = remote.startHour.coerceIn(0, 23),
                    startMinute = remote.startMinute.coerceIn(0, 59),
                    lockDurationMinutes = remote.lockDurationMinutes.coerceIn(1L, 1440L),
                    allowedPackages = existing?.allowedPackages ?: localAllowed,
                    phoneAccessEnabled = existing?.phoneAccessEnabled ?: localPhoneAccess,
                    isEnabled = remote.enabled,
                    createdAtWallMs = remote.updatedAtWallMs.coerceAtLeast(nowWall),
                    cloudId = remote.scheduleId,
                    syncAcrossDevices = true,
                    isStrictMode = remote.strictMode,
                    allowSelectedApps = remote.allowSelectedApps,
                    showFlipClockShortcut = remote.showFlipClockShortcut
                )
                val nextTrigger = if (baseSchedule.isEnabled) {
                    ScheduleCalculator.computeNextTriggerWallMs(baseSchedule, nowWall, zone)
                } else null
                repo.saveSchedule(baseSchedule.copy(nextTriggerWallMs = nextTrigger))
                anyChanged = true
            }
        }

        if (anyChanged) {
            sessionEngine.syncAllSchedules()
        }
    }

    private suspend fun mergeRemoteLockProfiles(
        remoteProfiles: List<CloudLockProfileSettings>,
        localDeviceId: String
    ) {
        val localMap = preferencesRepository.getAllLockProfileSettingsSnapshot()
        remoteProfiles.forEach { remote ->
            if (remote.originDeviceId == localDeviceId) return@forEach
            val mode = remote.resolveLockMode()
            val local = localMap[mode]
            if (local == null || remote.updatedAtWallMs > local.updatedAtWallMs) {
                preferencesRepository.saveLockProfileSettings(
                    LockProfileSettings(
                        mode = mode,
                        strictMode = remote.strictMode,
                        allowSelectedApps = remote.allowSelectedApps,
                        showFlipClockShortcut = remote.showFlipClockShortcut,
                        allowedPackages = remote.allowedPackages.distinct(),
                        updatedAtWallMs = remote.updatedAtWallMs
                    )
                )
            }
        }
    }

    private suspend fun mergeRemoteFlipClockSettings(
        remote: CloudFlipClockSettings?,
        localDeviceId: String
    ) {
        val local = preferencesRepository.getFlipClockSettingsSnapshot()
        if (remote == null) {
            if (local.updatedAtWallMs > 0L) {
                lastRemoteOrUploadedFlipSettingsWallMs = local.updatedAtWallMs
                syncFlipClockSettingsToCloud(local)
            }
            return
        }
        if (remote.originDeviceId == localDeviceId) return
        if (remote.updatedAtWallMs > local.updatedAtWallMs) {
            val merged = local.copy(
                use24HourFormat = remote.use24HourFormat,
                useSystemTimeFormat = remote.useSystemTimeFormat,
                hourLeadingZero = remote.hourLeadingZero,
                dateFormatPattern = com.example.domain.model.FlipDateFormat.fromPattern(remote.dateFormatPattern).pattern,
                keepScreenAwake = remote.keepScreenAwake,
                showDate = remote.showDate,
                showWeekday = remote.showWeekday,
                showSeconds = remote.showSeconds,
                showFlipAxis = remote.showFlipAxis,
                showUpcomingAlarm = remote.showUpcomingAlarm,
                showBattery = remote.showBattery,
                showWeather = remote.showWeather,
                weatherLocationQuery = remote.weatherLocationQuery,
                clockTickerSoundEnabled = remote.clockTickerSoundEnabled,
                customTag = remote.customTag,
                autoRepeat = remote.autoRepeat,
                zoomFraction = remote.zoomFraction.toFloat().coerceIn(
                    FlipClockSettings.MIN_ZOOM_FRACTION,
                    FlipClockSettings.MAX_ZOOM_FRACTION
                ),
                updatedAtWallMs = remote.updatedAtWallMs
            )
            lastRemoteOrUploadedFlipSettingsWallMs = remote.updatedAtWallMs
            lastObservedFlipSyncedSignature = merged.syncedSignature()
            preferencesRepository.saveFlipClockSettings(merged)
        } else if (local.updatedAtWallMs > remote.updatedAtWallMs) {
            lastRemoteOrUploadedFlipSettingsWallMs = local.updatedAtWallMs
            syncFlipClockSettingsToCloud(local)
        }
    }

    private suspend fun mergeRemoteFlipTimerPresets(
        remotePresets: List<CloudFlipTimerPreset>,
        localDeviceId: String
    ) {
        val repo = flipTimerPresetRepository ?: return
        repo.ensureDefaultPresetsSeeded()
        val localMap = repo.getAllPresets().associateBy { it.presetId }
        val tombstones = preferencesRepository.getFlipPresetTombstones()
        remotePresets.forEach { remote ->
            if (remote.presetId.isBlank()) return@forEach
            if (remote.originDeviceId == localDeviceId) return@forEach
            val local = localMap[remote.presetId]
            if (remote.deleted) {
                if (local == null || remote.updatedAtWallMs >= local.updatedAtWallMs) {
                    repo.deletePreset(remote.presetId, remote.updatedAtWallMs)
                }
            } else {
                val deletedAt = tombstones[remote.presetId] ?: 0L
                val isAfterTombstone = remote.updatedAtWallMs > deletedAt
                val isNewerThanLocal = local == null || remote.updatedAtWallMs > local.updatedAtWallMs
                if (isAfterTombstone && isNewerThanLocal) {
                    repo.upsertFromSync(
                        FlipTimerPreset(
                            presetId = remote.presetId,
                            name = remote.name.ifBlank { "Synced Preset" },
                            durationSeconds = remote.durationSeconds.coerceIn(1L, 86_399L),
                            builtIn = remote.builtIn,
                            sortOrder = remote.sortOrder.coerceIn(0, 1000),
                            updatedAtWallMs = remote.updatedAtWallMs
                        )
                    )
                }
            }
        }
    }

    suspend fun signInWithGoogle(activityContext: Context): Boolean {
        _uiState.update { it.copy(isAuthBusy = true, errorMessage = null) }
        return when (val outcome = GoogleSignInHelper.signInInteractive(activityContext)) {
            is GoogleSignInOutcome.Success -> {
                val firebaseResult = gateway.signInWithGoogleIdToken(outcome.idToken)
                _uiState.update { it.copy(isAuthBusy = false) }
                if (firebaseResult.isSuccess) {
                    lastAuthTokenEpochMs = clockProvider.wallClockMs()
                    _syncEvents.tryEmit("Google Account connected for multi-device sync.")
                    true
                } else {
                    val msg = firebaseResult.exceptionOrNull()?.localizedMessage
                        ?: "Could not complete Google authentication."
                    _uiState.update { it.copy(errorMessage = msg) }
                    _syncEvents.tryEmit(msg)
                    false
                }
            }
            GoogleSignInOutcome.Cancelled -> {
                _uiState.update { it.copy(isAuthBusy = false) }
                false
            }
            is GoogleSignInOutcome.Failed -> {
                _uiState.update { it.copy(isAuthBusy = false, errorMessage = outcome.message) }
                _syncEvents.tryEmit(outcome.message)
                false
            }
        }
    }

    suspend fun signOutSafely() {
        _uiState.update { it.copy(isAuthBusy = true, errorMessage = null) }
        withContext(NonCancellable) {
            stopUserListeners()
            activeUid = null
            GoogleSignInHelper.clearCredentialState(appContext)
            gateway.signOut()
            val localName = preferencesRepository.getLocalProfileNameSnapshot()
            val localAvatar = preferencesRepository.getLocalAvatarBase64Snapshot()
            _uiState.update {
                AccountSyncUiState(
                    isSignedIn = false,
                    isAuthBusy = false,
                    displayName = localName,
                    avatarBase64 = localAvatar,
                    thisDeviceId = it.thisDeviceId,
                    thisDeviceName = it.thisDeviceName,
                    syncLockEnabled = it.syncLockEnabled
                )
            }
        }
        _syncEvents.tryEmit("Account disconnected. Resolve continues offline cleanly.")
    }

    suspend fun unlinkDevice(targetDeviceId: String) {
        val uid = activeUid ?: return
        _uiState.update { it.copy(unlinkingDeviceId = targetDeviceId) }
        val result = gateway.unlinkDevice(uid, targetDeviceId)
        _uiState.update { it.copy(unlinkingDeviceId = null) }
        if (result.isSuccess) {
            if (targetDeviceId == _uiState.value.thisDeviceId) {
                signOutSafely()
                _syncEvents.tryEmit("This phone was unlinked. All local settings & locks remain intact.")
            } else {
                _syncEvents.tryEmit("Device unlinked from your account.")
            }
        } else {
            val msg = result.exceptionOrNull()?.let {
                CloudAccountRepository.unwrapFirestoreErrorMessage(it, "Could not unlink device.")
            } ?: "Could not unlink device."
            _syncEvents.tryEmit(msg)
        }
    }

    suspend fun setDeviceSyncEnabled(enabled: Boolean) {
        preferencesRepository.setSyncLockEnabled(enabled)
        _uiState.update { it.copy(syncLockEnabled = enabled) }
        val uid = activeUid ?: return
        val deviceId = preferencesRepository.getOrCreateDeviceInstallId()
        val result = gateway.updateDeviceSyncEnabled(
            userId = uid,
            deviceId = deviceId,
            deviceName = resolveReadableDeviceName(),
            syncEnabled = enabled
        )
        if (result.isFailure) {
            _syncEvents.tryEmit("Saved locally — cloud sync will update when online.")
        }
    }

    suspend fun updateProfileName(rawName: String) {
        val cleanName = rawName.trim().take(80)
        if (cleanName.isBlank()) {
            _syncEvents.tryEmit("Enter a valid profile name.")
            return
        }
        preferencesRepository.setLocalProfileName(cleanName)
        _uiState.update { it.copy(displayName = cleanName) }
        val uid = activeUid
        if (uid == null) {
            _syncEvents.tryEmit("Profile name updated on this device.")
            return
        }
        val result = gateway.updateDisplayName(uid, cleanName)
        if (result.isSuccess) {
            _syncEvents.tryEmit("Profile name synced across connected devices.")
        } else {
            _syncEvents.tryEmit("Profile name saved locally — cloud sync will retry online.")
        }
    }

    suspend fun updateProfilePhoto(uri: Uri) {
        pendingRemoteAvatarClear = false
        isLocalAvatarWriteInFlight = true
        _uiState.update { it.copy(isAvatarUploading = true, errorMessage = null) }
        val encoded = AvatarImageProcessor.encodeAvatarFromUri(appContext, uri)
        if (encoded.isFailure) {
            isLocalAvatarWriteInFlight = false
            _uiState.update { it.copy(isAvatarUploading = false) }
            _syncEvents.tryEmit("Could not read selected image.")
            return
        }
        val base64 = encoded.getOrThrow()
        preferencesRepository.setLocalAvatarBase64(base64)
        _uiState.update { it.copy(avatarBase64 = base64) }
        val uid = activeUid
        if (uid == null) {
            isLocalAvatarWriteInFlight = false
            _uiState.update { it.copy(isAvatarUploading = false) }
            _syncEvents.tryEmit("Profile photo saved on this device.")
            return
        }
        val writeResult = gateway.updateAvatarBase64(uid, base64)
        isLocalAvatarWriteInFlight = false
        _uiState.update { it.copy(isAvatarUploading = false) }
        if (writeResult.isSuccess) {
            _syncEvents.tryEmit("Profile photo synced across connected devices.")
        } else {
            val msg = writeResult.exceptionOrNull()?.let {
                CloudAccountRepository.unwrapFirestoreErrorMessage(it, "Could not sync profile photo.")
            } ?: "Saved locally — cloud sync will update when online."
            _syncEvents.tryEmit(msg)
        }
    }

    suspend fun clearCustomProfilePhoto() {
        pendingRemoteAvatarClear = true
        isLocalAvatarWriteInFlight = true
        preferencesRepository.setLocalAvatarBase64("")
        _uiState.update { it.copy(avatarBase64 = "") }
        val uid = activeUid ?: run {
            isLocalAvatarWriteInFlight = false
            _syncEvents.tryEmit("Profile photo removed.")
            return
        }
        _uiState.update { it.copy(isAvatarUploading = true) }
        val writeResult = gateway.updateAvatarBase64(uid, "")
        isLocalAvatarWriteInFlight = false
        _uiState.update { it.copy(isAvatarUploading = false) }
        if (writeResult.isSuccess) {
            _syncEvents.tryEmit("Custom profile photo removed.")
        }
    }

    suspend fun publishLockTriggerIfEnabled(
        mode: LockMode,
        freeUseMinutes: Long,
        lockDurationMinutes: Long,
        freeUseDeadlineWallMs: Long,
        lockExpirationWallMs: Long,
        syncAcrossDevices: Boolean,
        scheduleId: String = "",
        occurrenceId: String = "",
        strictMode: Boolean = false,
        showFlipClockShortcut: Boolean = false
    ) {
        if (!syncAcrossDevices) return
        val uid = activeUid ?: return
        val deviceId = preferencesRepository.getOrCreateDeviceInstallId()
        val deviceName = resolveReadableDeviceName()
        val nowWall = clockProvider.wallClockMs()

        val result = gateway.publishLockCommand(
            userId = uid,
            originDeviceId = deviceId,
            originDeviceName = deviceName,
            mode = mode,
            freeUseDurationMinutes = freeUseMinutes,
            lockDurationMinutes = lockDurationMinutes,
            issuedAtWallMs = nowWall,
            intendedStartWallMs = nowWall,
            freeUseDeadlineWallMs = freeUseDeadlineWallMs,
            lockExpirationWallMs = lockExpirationWallMs,
            scheduleId = scheduleId,
            occurrenceId = occurrenceId,
            strictMode = strictMode,
            showFlipClockShortcut = showFlipClockShortcut
        )
        if (result.isSuccess) {
            val commandId = result.getOrNull().orEmpty()
            if (commandId.isNotBlank()) {
                preferencesRepository.recordProcessedSyncCommandId(commandId)
            }
            _syncEvents.tryEmit("Lock synced to connected devices.")
        } else {
            _syncEvents.tryEmit("Locked this device (cloud sync skipped while offline).")
        }
    }

    suspend fun syncDelayedPresetToCloud(preset: DelayedLockPreset, deleted: Boolean = false) {
        if (preset.builtIn) return
        val uid = activeUid ?: return
        val deviceId = preferencesRepository.getOrCreateDeviceInstallId()
        gateway.upsertDelayedPreset(
            userId = uid,
            preset = CloudDelayedPreset(
                userId = uid,
                presetId = preset.presetId,
                name = preset.name,
                freeUseMinutes = preset.freeUseMinutes,
                lockDurationMinutes = preset.lockDurationMinutes,
                originDeviceId = deviceId,
                updatedAtWallMs = clockProvider.wallClockMs(),
                deleted = deleted
            )
        )
    }

    suspend fun syncScheduleToCloud(schedule: LockSchedule, deleted: Boolean = false) {
        val uid = activeUid ?: return
        if (schedule.cloudId.isBlank()) return
        val deviceId = preferencesRepository.getOrCreateDeviceInstallId()
        gateway.upsertSyncedSchedule(
            userId = uid,
            schedule = CloudScheduleRecord(
                userId = uid,
                scheduleId = schedule.cloudId,
                name = schedule.name,
                type = schedule.type.name,
                oneTimeDateEpochDay = schedule.oneTimeDateEpochDay ?: -1L,
                weekdaysMask = schedule.weekdaysMask,
                startHour = schedule.startHour,
                startMinute = schedule.startMinute,
                lockDurationMinutes = schedule.lockDurationMinutes,
                enabled = schedule.isEnabled,
                syncAcrossDevices = schedule.syncAcrossDevices,
                strictMode = schedule.isStrictMode,
                allowSelectedApps = schedule.allowSelectedApps,
                showFlipClockShortcut = schedule.showFlipClockShortcut,
                originDeviceId = deviceId,
                updatedAtWallMs = clockProvider.wallClockMs(),
                deleted = deleted
            )
        )
    }

    suspend fun syncLockProfileToCloud(profile: LockProfileSettings) {
        val uid = activeUid ?: return
        val deviceId = preferencesRepository.getOrCreateDeviceInstallId()
        gateway.upsertLockProfile(
            userId = uid,
            profile = CloudLockProfileSettings(
                userId = uid,
                profileMode = profile.mode.name,
                strictMode = profile.strictMode,
                allowSelectedApps = profile.allowSelectedApps,
                showFlipClockShortcut = profile.showFlipClockShortcut,
                allowedPackages = profile.allowedPackages.distinct(),
                originDeviceId = deviceId,
                updatedAtWallMs = profile.updatedAtWallMs.takeIf { it > 0L } ?: clockProvider.wallClockMs()
            )
        )
    }

    suspend fun syncFlipClockSettingsToCloud(settings: FlipClockSettings) {
        val uid = activeUid ?: return
        val deviceId = preferencesRepository.getOrCreateDeviceInstallId()
        gateway.upsertFlipClockSettings(
            userId = uid,
            settings = CloudFlipClockSettings(
                userId = uid,
                use24HourFormat = settings.use24HourFormat,
                useSystemTimeFormat = settings.useSystemTimeFormat,
                hourLeadingZero = settings.hourLeadingZero,
                dateFormatPattern = settings.resolvedDateFormat.pattern,
                keepScreenAwake = settings.keepScreenAwake,
                showDate = settings.showDate,
                showWeekday = settings.showWeekday,
                showSeconds = settings.showSeconds,
                showFlipAxis = settings.showFlipAxis,
                showUpcomingAlarm = settings.showUpcomingAlarm,
                showBattery = settings.showBattery,
                showWeather = settings.showWeather,
                weatherLocationQuery = settings.weatherLocationQuery,
                clockTickerSoundEnabled = settings.clockTickerSoundEnabled,
                customTag = settings.customTag,
                autoRepeat = settings.autoRepeat,
                zoomFraction = settings.clampedZoomFraction.toDouble(),
                originDeviceId = deviceId,
                updatedAtWallMs = settings.updatedAtWallMs.takeIf { it > 0L } ?: clockProvider.wallClockMs()
            )
        )
    }

    suspend fun syncFlipTimerPresetToCloud(preset: FlipTimerPreset, deleted: Boolean = false) {
        val uid = activeUid ?: return
        val deviceId = preferencesRepository.getOrCreateDeviceInstallId()
        val updatedWall = preset.updatedAtWallMs.takeIf { it > 0L } ?: clockProvider.wallClockMs()
        gateway.upsertFlipTimerPreset(
            userId = uid,
            preset = CloudFlipTimerPreset(
                userId = uid,
                presetId = preset.presetId,
                name = preset.name,
                durationSeconds = preset.durationSeconds,
                builtIn = preset.builtIn,
                sortOrder = preset.sortOrder,
                originDeviceId = deviceId,
                updatedAtWallMs = updatedWall,
                deleted = deleted
            )
        )
    }

    private fun stopUserListeners() {
        activeUserListenersJob?.cancel()
        activeUserListenersJob = null
    }
}
