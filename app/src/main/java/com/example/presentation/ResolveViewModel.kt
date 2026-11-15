package com.example.presentation

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.ResolveApplication
import com.example.domain.model.ActiveSession
import com.example.domain.model.DurationSelection
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.LockSchedule
import com.example.domain.model.ScheduleType
import com.example.domain.model.SessionHistoryRecord
import com.example.domain.model.SystemCapabilityStatus
import com.example.domain.scheduling.ScheduleCalculator
import com.example.session.EngineStartResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

sealed interface UiEvent {
    data class ShowMessage(val message: String) : UiEvent
    data object NavigateBackToHome : UiEvent
}

class ResolveViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val container = (application as ResolveApplication).container
    private val engine = container.sessionEngine

    val activeSession: StateFlow<ActiveSession?> = engine.activeSession
    val remainingMillis: StateFlow<Long> = engine.remainingMillis
    val capabilities: StateFlow<SystemCapabilityStatus> = engine.capabilities

    val schedules: StateFlow<List<LockSchedule>> = container.scheduleRepository.observeSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<SessionHistoryRecord>> = container.historyRepository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedAllowedPackages: StateFlow<List<String>> = container.allowlistRepository.observeAllowedPackages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasRecoveryCode: StateFlow<Boolean> = container.recoveryCodeManager.hasRecoveryCodeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val recoveryCodeLength: StateFlow<Int> = container.recoveryCodeManager.recoveryCodeLengthFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 16)

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    // Authoritative saved-state-backed durations (immune to Activity recreation)
    private val _instantDuration = MutableStateFlow(
        DurationSelection.fromTotalMinutes(savedStateHandle[KEY_INSTANT_MINUTES] ?: 60L)
    )
    val instantDuration: StateFlow<DurationSelection> = _instantDuration.asStateFlow()

    private val _delayedFreeUseDuration = MutableStateFlow(
        DurationSelection.fromTotalMinutes(savedStateHandle[KEY_DELAYED_FREE_MINUTES] ?: 30L)
    )
    val delayedFreeUseDuration: StateFlow<DurationSelection> = _delayedFreeUseDuration.asStateFlow()

    private val _delayedLockDuration = MutableStateFlow(
        DurationSelection.fromTotalMinutes(savedStateHandle[KEY_DELAYED_LOCK_MINUTES] ?: 60L)
    )
    val delayedLockDuration: StateFlow<DurationSelection> = _delayedLockDuration.asStateFlow()

    private val _scheduleLockDuration = MutableStateFlow(
        DurationSelection.fromTotalMinutes(savedStateHandle[KEY_SCHEDULE_LOCK_MINUTES] ?: 60L)
    )
    val scheduleLockDuration: StateFlow<DurationSelection> = _scheduleLockDuration.asStateFlow()

    private val _phoneAccessEnabled = MutableStateFlow(savedStateHandle[KEY_PHONE_ACCESS] ?: true)
    val phoneAccessEnabled: StateFlow<Boolean> = _phoneAccessEnabled.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        refreshInstalledApps()
        refreshCapabilities()
    }

    fun refreshCapabilities() {
        engine.refreshCapabilities()
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = container.installedAppsRepository.loadLaunchableApps()
        }
    }

    suspend fun loadAppIcon(packageName: String): ImageBitmap? =
        container.installedAppsRepository.loadAppIcon(packageName)

    fun setInstantHours(hours: Int) = updateInstantDuration(_instantDuration.value.withHours(hours))
    fun setInstantMinutes(minutes: Int) = updateInstantDuration(_instantDuration.value.withMinutes(minutes))
    fun updateInstantDuration(duration: DurationSelection) {
        _instantDuration.value = duration
        savedStateHandle[KEY_INSTANT_MINUTES] = duration.totalMinutes
    }

    fun setDelayedFreeUseHours(hours: Int) = updateDelayedFreeUse(_delayedFreeUseDuration.value.withHours(hours))
    fun setDelayedFreeUseMinutes(minutes: Int) = updateDelayedFreeUse(_delayedFreeUseDuration.value.withMinutes(minutes))
    fun updateDelayedFreeUse(duration: DurationSelection) {
        _delayedFreeUseDuration.value = duration
        savedStateHandle[KEY_DELAYED_FREE_MINUTES] = duration.totalMinutes
    }

    fun setDelayedLockHours(hours: Int) = updateDelayedLock(_delayedLockDuration.value.withHours(hours))
    fun setDelayedLockMinutes(minutes: Int) = updateDelayedLock(_delayedLockDuration.value.withMinutes(minutes))
    fun updateDelayedLock(duration: DurationSelection) {
        _delayedLockDuration.value = duration
        savedStateHandle[KEY_DELAYED_LOCK_MINUTES] = duration.totalMinutes
    }

    fun setScheduleLockHours(hours: Int) = updateScheduleLockDuration(_scheduleLockDuration.value.withHours(hours))
    fun setScheduleLockMinutes(minutes: Int) = updateScheduleLockDuration(_scheduleLockDuration.value.withMinutes(minutes))
    fun updateScheduleLockDuration(duration: DurationSelection) {
        _scheduleLockDuration.value = duration
        savedStateHandle[KEY_SCHEDULE_LOCK_MINUTES] = duration.totalMinutes
    }

    fun setPhoneAccessEnabled(enabled: Boolean) {
        _phoneAccessEnabled.value = enabled
        savedStateHandle[KEY_PHONE_ACCESS] = enabled
    }

    fun saveAllowedAppsSelection(selectedPackages: Set<String>) {
        viewModelScope.launch {
            val installedMap = _installedApps.value.associateBy { it.packageName }
            val pairs = selectedPackages.map { pkg ->
                pkg to (installedMap[pkg]?.appName ?: pkg)
            }
            container.allowlistRepository.replaceAllowedApps(
                apps = pairs,
                nowWallMs = container.clockProvider.wallClockMs()
            )
            _events.emit(UiEvent.ShowMessage("Saved ${pairs.size} allowed apps."))
        }
    }

    fun generateRandomRecoveryCode(length: Int): String =
        container.recoveryCodeManager.generateNumericCode(length)

    fun saveRecoveryCode(code: String) {
        viewModelScope.launch {
            val saved = container.recoveryCodeManager.saveRecoveryCode(code)
            if (saved) {
                _events.emit(UiEvent.ShowMessage("Recovery code saved securely."))
            } else {
                _events.emit(UiEvent.ShowMessage("Invalid recovery code format."))
            }
        }
    }

    fun startInstantLock() {
        viewModelScope.launch {
            val result = engine.startInstantLock(
                lockDurationMinutes = _instantDuration.value.totalMinutes,
                allowedPackages = savedAllowedPackages.value,
                phoneAccessEnabled = _phoneAccessEnabled.value
            )
            when (result) {
                EngineStartResult.Started -> Unit
                is EngineStartResult.Blocked -> _events.emit(UiEvent.ShowMessage(result.reason))
            }
        }
    }

    fun startDelayedLock() {
        viewModelScope.launch {
            val result = engine.startDelayedLock(
                freeUseMinutes = _delayedFreeUseDuration.value.totalMinutes,
                lockDurationMinutes = _delayedLockDuration.value.totalMinutes,
                allowedPackages = savedAllowedPackages.value,
                phoneAccessEnabled = _phoneAccessEnabled.value
            )
            when (result) {
                EngineStartResult.Started -> {
                    _events.emit(
                        UiEvent.ShowMessage(
                            "Delayed Lock started. Phone remains unrestricted for ${_delayedFreeUseDuration.value.formattedCompact()}."
                        )
                    )
                    _events.emit(UiEvent.NavigateBackToHome)
                }
                is EngineStartResult.Blocked -> _events.emit(UiEvent.ShowMessage(result.reason))
            }
        }
    }

    fun saveSchedule(
        editingId: Long?,
        name: String,
        type: ScheduleType,
        oneTimeDate: LocalDate,
        selectedDays: Set<DayOfWeek>,
        startHour: Int,
        startMinute: Int,
        durationMinutes: Long,
        allowedPackages: List<String>,
        phoneAccessEnabled: Boolean,
        isEnabled: Boolean = true
    ) {
        viewModelScope.launch {
            if (durationMinutes <= 0L) {
                _events.emit(UiEvent.ShowMessage("Select a lock duration of at least 1 minute."))
                return@launch
            }
            if (type == ScheduleType.CUSTOM_WEEKLY && selectedDays.isEmpty()) {
                _events.emit(UiEvent.ShowMessage("Select at least one weekday."))
                return@launch
            }

            val nowWall = container.clockProvider.wallClockMs()
            val zone = container.clockProvider.zoneId()
            val candidate = LockSchedule(
                id = editingId ?: 0L,
                name = name.trim().ifEmpty {
                    when (type) {
                        ScheduleType.ONE_TIME -> "One-Time Lock"
                        ScheduleType.DAILY -> "Daily Focus Lock"
                        ScheduleType.CUSTOM_WEEKLY -> "Weekly Focus Lock"
                    }
                },
                type = type,
                oneTimeDateEpochDay = if (type == ScheduleType.ONE_TIME) oneTimeDate.toEpochDay() else null,
                weekdaysMask = if (type == ScheduleType.CUSTOM_WEEKLY) LockSchedule.maskFromDays(selectedDays) else 0,
                startHour = startHour.coerceIn(0, 23),
                startMinute = startMinute.coerceIn(0, 59),
                lockDurationMinutes = durationMinutes,
                allowedPackages = allowedPackages,
                phoneAccessEnabled = phoneAccessEnabled,
                isEnabled = isEnabled,
                createdAtWallMs = nowWall
            )

            val nextTrigger = ScheduleCalculator.computeNextTriggerWallMs(candidate, nowWall, zone)
            if (isEnabled && type == ScheduleType.ONE_TIME && nextTrigger == null) {
                _events.emit(UiEvent.ShowMessage("Selected date and time is in the past."))
                return@launch
            }

            val existing = container.scheduleRepository.getAllSchedules()
            val conflict = ScheduleCalculator.detectConflict(candidate, existing, nowWall, zone)
            if (conflict != null) {
                _events.emit(UiEvent.ShowMessage(conflict.description))
                return@launch
            }

            container.scheduleRepository.saveSchedule(candidate.copy(nextTriggerWallMs = nextTrigger))
            engine.syncAllSchedules()
            _events.emit(UiEvent.ShowMessage("Schedule saved."))
            _events.emit(UiEvent.NavigateBackToHome)
        }
    }

    fun toggleScheduleEnabled(schedule: LockSchedule, enabled: Boolean) {
        viewModelScope.launch {
            val nowWall = container.clockProvider.wallClockMs()
            val zone = container.clockProvider.zoneId()
            val updated = schedule.copy(isEnabled = enabled)
            if (enabled) {
                val conflict = ScheduleCalculator.detectConflict(
                    updated,
                    container.scheduleRepository.getAllSchedules(),
                    nowWall,
                    zone
                )
                if (conflict != null) {
                    _events.emit(UiEvent.ShowMessage(conflict.description))
                    return@launch
                }
                val next = ScheduleCalculator.computeNextTriggerWallMs(updated, nowWall, zone)
                if (updated.type == ScheduleType.ONE_TIME && next == null) {
                    _events.emit(UiEvent.ShowMessage("This one-time schedule is in the past. Edit its date/time to enable."))
                    return@launch
                }
                container.scheduleRepository.saveSchedule(updated.copy(nextTriggerWallMs = next))
            } else {
                container.scheduleRepository.saveSchedule(updated.copy(nextTriggerWallMs = null))
            }
            engine.syncAllSchedules()
        }
    }

    fun deleteSchedule(scheduleId: Long) {
        viewModelScope.launch {
            container.scheduleRepository.deleteSchedule(scheduleId)
            engine.syncAllSchedules()
            _events.emit(UiEvent.ShowMessage("Schedule deleted."))
        }
    }

    fun dismissTerminalSession() {
        viewModelScope.launch {
            engine.clearTerminalSession()
        }
    }

    fun openLockScreenIfLocked() {
        val current = activeSession.value ?: return
        if (current.state == com.example.domain.model.SessionState.LOCK_ENFORCEMENT_ACTIVE ||
            current.state == com.example.domain.model.SessionState.LOCK_TRANSITION_PENDING
        ) {
            val intent = android.content.Intent(
                getApplication(),
                com.example.presentation.lockscreen.LockScreenActivity::class.java
            ).apply {
                addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            runCatching { getApplication<Application>().startActivity(intent) }
        }
    }

    fun openAccessibilitySettings() {
        container.capabilityChecker.openAccessibilitySettings()
    }

    fun openOverlaySettings() {
        container.capabilityChecker.openOverlaySettings()
    }

    fun openUsageAccessSettings() {
        container.capabilityChecker.openUsageAccessSettings()
    }

    fun openBatteryOptimizationSettings() {
        container.capabilityChecker.openBatteryOptimizationSettings()
    }

    fun openExactAlarmSettings() {
        container.capabilityChecker.openExactAlarmSettings()
    }

    fun openNotificationSettings() {
        container.capabilityChecker.openNotificationSettings()
    }

    companion object {
        private const val KEY_INSTANT_MINUTES = "instant_minutes"
        private const val KEY_DELAYED_FREE_MINUTES = "delayed_free_minutes"
        private const val KEY_DELAYED_LOCK_MINUTES = "delayed_lock_minutes"
        private const val KEY_SCHEDULE_LOCK_MINUTES = "schedule_lock_minutes"
        private const val KEY_PHONE_ACCESS = "phone_access"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val handle = extras.createSavedStateHandle()
                return ResolveViewModel(app, handle) as T
            }
        }
    }
}
