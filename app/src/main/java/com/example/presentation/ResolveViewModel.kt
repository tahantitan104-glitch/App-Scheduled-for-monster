package com.example.presentation

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.ResolveApplication
import com.example.data.repository.DelayedPresetRepository
import com.example.domain.model.ActiveSession
import com.example.domain.model.AppLockAuthMethod
import com.example.domain.model.AppLockPinLength
import com.example.domain.model.AppLockVerificationResult
import com.example.domain.model.DailyAppUsageSummary
import com.example.domain.model.DelayedLockPreset
import com.example.domain.model.DeviceAnalysisReport
import com.example.domain.model.DurationSelection
import com.example.domain.model.FocusMessageFrequency
import com.example.domain.model.ForceDarkModeApproach
import com.example.domain.model.ForceDarkPreferences
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.LockMode
import com.example.domain.model.LockProfileSettings
import com.example.domain.model.LockReminderStyle
import com.example.domain.model.LockSchedule
import com.example.domain.model.MascotPreferences
import com.example.domain.model.MascotType
import com.example.domain.model.NotificationControlConfig
import com.example.domain.model.NotificationProtectionState
import com.example.domain.model.ProtectionRecoveryAction
import com.example.domain.model.ProtectionStatus
import com.example.domain.model.ScheduleType
import com.example.domain.model.SessionHistoryRecord
import com.example.domain.model.SystemCapabilityStatus
import com.example.domain.scheduling.ScheduleCalculator
import com.example.domain.startup.StartupDecisionResolver
import com.example.domain.startup.StartupPreferences
import com.example.domain.startup.StartupStage
import com.example.platform.ForceDarkCapabilityHelper
import com.example.platform.ForceDarkCapabilityReport
import com.example.platform.ModeOperationResult
import com.example.session.EngineStartResult
import com.example.sync.AccountSyncUiState
import java.util.UUID
import kotlinx.coroutines.flow.first
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
    val protectionStatus: StateFlow<ProtectionStatus> = engine.protectionStatus
    val notificationProtectionState: StateFlow<NotificationProtectionState> =
        container.notificationControlManager.protectionState

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

    val mascotPreferences: StateFlow<MascotPreferences> = container.mascotSelectionManager.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MascotPreferences())

    val startupPreferences: StateFlow<StartupPreferences> = container.preferencesRepository.startupPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StartupPreferences())

    val accountSyncState: StateFlow<AccountSyncUiState> = container.multiDeviceSyncCoordinator.uiState

    val syncPromoDismissed: StateFlow<Boolean> = container.preferencesRepository.syncPromoDismissedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val delayedPresets: StateFlow<List<DelayedLockPreset>> =
        container.delayedPresetRepository.observePresets()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                listOf(DelayedPresetRepository.BUILT_IN_QUICK_RECOVERY)
            )

    val appLockEnabled: StateFlow<Boolean> = container.appLockCredentialManager.appLockEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasAppLockCredential: StateFlow<Boolean> = container.appLockCredentialManager.hasCredentialFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appLockMethod: StateFlow<AppLockAuthMethod> = container.appLockCredentialManager.authMethodFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLockAuthMethod.PIN)

    val appLockPinLength: StateFlow<AppLockPinLength> = container.appLockCredentialManager.pinLengthFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLockPinLength.FOUR)

    val appLockPackages: StateFlow<List<String>> = container.appLockRepository.observeProtectedPackages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val forceDarkPreferences: StateFlow<ForceDarkPreferences> = container.preferencesRepository.forceDarkPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ForceDarkPreferences())

    val instantProfile: StateFlow<LockProfileSettings> =
        container.preferencesRepository.observeLockProfileSettings(LockMode.INSTANT)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LockProfileSettings(LockMode.INSTANT))

    val delayedProfile: StateFlow<LockProfileSettings> =
        container.preferencesRepository.observeLockProfileSettings(LockMode.DELAYED)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LockProfileSettings(LockMode.DELAYED))

    val scheduledProfile: StateFlow<LockProfileSettings> =
        container.preferencesRepository.observeLockProfileSettings(LockMode.SCHEDULED)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LockProfileSettings(LockMode.SCHEDULED))

    val darkModeAppPrefs: StateFlow<Map<String, Boolean>> = container.preferencesRepository.darkModeAppPrefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val colorInversionAppPrefs: StateFlow<Map<String, Boolean>> = container.preferencesRepository.colorInversionAppPrefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val notificationControlConfig: StateFlow<NotificationControlConfig> =
        container.preferencesRepository.notificationControlConfigFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationControlConfig())

    val landscapeTiltMode: StateFlow<Boolean> =
        container.preferencesRepository.landscapeTiltModeFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val inAppColorInversionEnabled: StateFlow<Boolean> =
        container.preferencesRepository.inAppColorInversionEnabledFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val colorInversionConsentGranted: StateFlow<Boolean> =
        container.preferencesRepository.colorInversionConsentGrantedFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val priorOsColorInversionState: StateFlow<Int> =
        container.preferencesRepository.priorOsColorInversionStateFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    private val _deviceAnalysisReport = MutableStateFlow(DeviceAnalysisReport())
    val deviceAnalysisReport: StateFlow<DeviceAnalysisReport> = _deviceAnalysisReport.asStateFlow()

    private val _forceDarkReport = MutableStateFlow(ForceDarkCapabilityHelper.inspect(application))
    val forceDarkReport: StateFlow<ForceDarkCapabilityReport> = _forceDarkReport.asStateFlow()

    private val _dailyAppUsage = MutableStateFlow(DailyAppUsageSummary())
    val dailyAppUsage: StateFlow<DailyAppUsageSummary> = _dailyAppUsage.asStateFlow()

    private val _appLockManagementUnlocked = MutableStateFlow(
        container.appLockSessionGuard.isManagementUnlockedInSession
    )
    val appLockManagementUnlocked: StateFlow<Boolean> = _appLockManagementUnlocked.asStateFlow()

    private val _startupStage = MutableStateFlow(StartupStage.RESOLVING)
    val startupStage: StateFlow<StartupStage> = _startupStage.asStateFlow()

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

    // Safe initial default for per-lock scope: "This Phone Only" (false)
    private val _syncInstantLockToDevices = MutableStateFlow(false)
    val syncInstantLockToDevices: StateFlow<Boolean> = _syncInstantLockToDevices.asStateFlow()

    private val _syncDelayedLockToDevices = MutableStateFlow(false)
    val syncDelayedLockToDevices: StateFlow<Boolean> = _syncDelayedLockToDevices.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            runCatching { container.delayedPresetRepository.ensureBuiltInPresets() }
            runCatching { seedLegacyAllowlistIntoProfilesIfNeeded() }
            runCatching { container.multiDeviceSyncCoordinator.start() }
        }
        viewModelScope.launch {
            container.multiDeviceSyncCoordinator.syncEvents.collect { msg ->
                _events.emit(UiEvent.ShowMessage(msg))
            }
        }
        viewModelScope.launch {
            engine.initialize()
            refreshCapabilities()
            val startupPrefs = container.preferencesRepository.getStartupPreferencesSnapshot()
            val hasCode = container.recoveryCodeManager.hasRecoveryCodeFlow.first()
            val setupReady = hasCode && engine.capabilities.value.canEnforceRealLock
            _startupStage.value = StartupDecisionResolver.resolveInitialStage(
                activeSession = engine.activeSession.value,
                startupPreferences = startupPrefs,
                isRequiredSetupComplete = setupReady
            )
            // Defer heavier PackageManager icon/app & UsageStats scan until after startup stage is determined
            refreshInstalledApps()
            refreshAppUsage()
            refreshForceDarkReport()
        }
        viewModelScope.launch {
            engine.activeSession.collect { session ->
                if (StartupDecisionResolver.shouldRestoreLockImmediately(session)) {
                    _startupStage.value = StartupStage.RESTORE_LOCK
                } else if (_startupStage.value == StartupStage.RESTORE_LOCK && session != null && session.isTerminal) {
                    val startupPrefs = container.preferencesRepository.getStartupPreferencesSnapshot()
                    val hasCode = container.recoveryCodeManager.hasRecoveryCodeFlow.first()
                    val setupReady = hasCode && engine.capabilities.value.canEnforceRealLock
                    _startupStage.value = StartupDecisionResolver.resolvePostIntroStage(startupPrefs, setupReady)
                }
            }
        }
    }

    fun refreshCapabilities() {
        engine.refreshCapabilities()
        refreshAppUsage()
        refreshForceDarkReport()
        if (_deviceAnalysisReport.value.analyzedAtWallMs > 0L) {
            runDeviceAnalysis()
        }
    }

    fun refreshAppUsage() {
        viewModelScope.launch {
            _dailyAppUsage.value = container.appUsageRepository.queryTodayUsageSummary()
        }
    }

    fun refreshForceDarkReport() {
        _forceDarkReport.value = ForceDarkCapabilityHelper.inspect(getApplication())
    }

    fun runDeviceAnalysis() {
        viewModelScope.launch {
            _deviceAnalysisReport.value = container.deviceAnalysisEngine.runAnalysis()
        }
    }

    fun executeProtectionAction(action: ProtectionRecoveryAction) {
        when (action) {
            ProtectionRecoveryAction.NONE -> Unit
            ProtectionRecoveryAction.OPEN_ACCESSIBILITY -> openAccessibilitySettings()
            ProtectionRecoveryAction.OPEN_OVERLAY -> openOverlaySettings()
            ProtectionRecoveryAction.OPEN_USAGE_ACCESS -> openUsageAccessSettings()
            ProtectionRecoveryAction.OPEN_NOTIFICATION_POLICY -> openNotificationPolicySettings()
            ProtectionRecoveryAction.OPEN_BATTERY_OPTIMIZATION -> openBatteryOptimizationSettings()
            ProtectionRecoveryAction.OPEN_EXACT_ALARM -> openExactAlarmSettings()
            ProtectionRecoveryAction.OPEN_NOTIFICATION_PERMISSION -> openNotificationSettings()
            ProtectionRecoveryAction.RETURN_TO_LOCK_SCREEN -> openLockScreenIfLocked()
            ProtectionRecoveryAction.RUN_DEVICE_ANALYSIS -> runDeviceAnalysis()
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = container.installedAppsRepository.loadLaunchableApps()
        }
    }

    fun onBrandIntroCompleted() {
        viewModelScope.launch {
            if (StartupDecisionResolver.shouldRestoreLockImmediately(engine.activeSession.value)) {
                _startupStage.value = StartupStage.RESTORE_LOCK
                return@launch
            }
            val startupPrefs = container.preferencesRepository.getStartupPreferencesSnapshot()
            val hasCode = container.recoveryCodeManager.hasRecoveryCodeFlow.first()
            val setupReady = hasCode && engine.capabilities.value.canEnforceRealLock
            _startupStage.value = StartupDecisionResolver.resolvePostIntroStage(startupPrefs, setupReady)
        }
    }

    fun onFirstTimeWelcomeContinue() {
        viewModelScope.launch {
            if (StartupDecisionResolver.shouldRestoreLockImmediately(engine.activeSession.value)) {
                _startupStage.value = StartupStage.RESTORE_LOCK
                return@launch
            }
            val hasCode = container.recoveryCodeManager.hasRecoveryCodeFlow.first()
            val setupReady = hasCode && engine.capabilities.value.canEnforceRealLock
            if (setupReady) {
                container.preferencesRepository.markOnboardingCompleted()
                _startupStage.value = StartupStage.HOME
            } else {
                container.preferencesRepository.markFirstLaunchWelcomeSeen()
                _startupStage.value = StartupStage.REQUIRED_SETUP
            }
        }
    }

    fun onCompleteRequiredSetup() {
        viewModelScope.launch {
            val hasCode = container.recoveryCodeManager.hasRecoveryCodeFlow.first()
            val setupReady = hasCode && engine.capabilities.value.canEnforceRealLock
            if (setupReady) {
                container.preferencesRepository.markOnboardingCompleted()
                _startupStage.value = StartupStage.HOME
            } else {
                _events.emit(UiEvent.ShowMessage("Please configure Recovery Code and Focus Guard first."))
            }
        }
    }

    fun onDeferRequiredSetupToHome() {
        _startupStage.value = StartupStage.HOME
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
        viewModelScope.launch {
            container.preferencesRepository.setDefaultPhoneAccess(enabled)
        }
    }

    fun setSyncInstantLockToDevices(sync: Boolean) {
        _syncInstantLockToDevices.value = sync
    }

    fun setSyncDelayedLockToDevices(sync: Boolean) {
        _syncDelayedLockToDevices.value = sync
    }

    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            container.multiDeviceSyncCoordinator.signInWithGoogle(activityContext)
        }
    }

    fun signOutGoogleAccount() {
        viewModelScope.launch {
            container.multiDeviceSyncCoordinator.signOutSafely()
        }
    }

    fun setDeviceSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.multiDeviceSyncCoordinator.setDeviceSyncEnabled(enabled)
            if (!enabled) {
                _syncInstantLockToDevices.value = false
                _syncDelayedLockToDevices.value = false
            }
        }
    }

    fun unlinkDevice(deviceId: String) {
        viewModelScope.launch {
            container.multiDeviceSyncCoordinator.unlinkDevice(deviceId)
        }
    }

    fun updateProfileName(name: String) {
        viewModelScope.launch {
            container.multiDeviceSyncCoordinator.updateProfileName(name)
        }
    }

    fun updateProfilePhoto(uri: Uri) {
        viewModelScope.launch {
            container.multiDeviceSyncCoordinator.updateProfilePhoto(uri)
        }
    }

    fun clearProfilePhoto() {
        viewModelScope.launch {
            container.multiDeviceSyncCoordinator.clearCustomProfilePhoto()
        }
    }

    fun dismissSyncPromoBanner() {
        viewModelScope.launch {
            container.preferencesRepository.setSyncPromoDismissed(true)
        }
    }

    fun saveDelayedPreset(
        editingPresetId: String?,
        name: String,
        freeUseMinutes: Long,
        lockMinutes: Long
    ) {
        viewModelScope.launch {
            val cleanName = name.trim().take(48)
            if (cleanName.isEmpty()) {
                _events.emit(UiEvent.ShowMessage("Enter a preset name."))
                return@launch
            }
            val free = freeUseMinutes.coerceIn(1L, 1440L)
            val lock = lockMinutes.coerceIn(1L, 1440L)
            val id = editingPresetId?.takeIf { it.isNotBlank() && it != DelayedPresetRepository.QUICK_RECOVERY_ID }
                ?: "preset_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val preset = DelayedLockPreset(
                presetId = id,
                name = cleanName,
                freeUseMinutes = free,
                lockDurationMinutes = lock,
                builtIn = false,
                updatedAtWallMs = container.clockProvider.wallClockMs()
            )
            container.delayedPresetRepository.savePreset(preset)
            updateDelayedFreeUse(DurationSelection.fromTotalMinutes(free))
            updateDelayedLock(DurationSelection.fromTotalMinutes(lock))
            container.multiDeviceSyncCoordinator.syncDelayedPresetToCloud(preset, deleted = false)
            _events.emit(UiEvent.ShowMessage("Preset \"$cleanName\" saved."))
        }
    }

    fun deleteDelayedPreset(presetId: String) {
        viewModelScope.launch {
            val existing = delayedPresets.value.firstOrNull { it.presetId == presetId } ?: return@launch
            if (existing.builtIn) return@launch
            container.delayedPresetRepository.deleteCustomPreset(presetId)
            container.multiDeviceSyncCoordinator.syncDelayedPresetToCloud(existing, deleted = true)
            _events.emit(UiEvent.ShowMessage("Preset \"${existing.name}\" deleted."))
        }
    }

    suspend fun verifyAppLockForManagement(rawInput: String): AppLockVerificationResult {
        val result = container.appLockCredentialManager.verifyCredential(rawInput)
        if (result is AppLockVerificationResult.Verified) {
            container.appLockSessionGuard.grantManagementAccess()
            _appLockManagementUnlocked.value = true
        }
        return result
    }

    fun revokeAppLockManagementAccess() {
        container.appLockSessionGuard.revokeManagementAccess()
        _appLockManagementUnlocked.value = false
    }

    fun saveAppLockConfig(
        method: AppLockAuthMethod,
        pinLength: AppLockPinLength,
        rawCredential: String,
        enableAfterSave: Boolean = true
    ) {
        viewModelScope.launch {
            val saved = container.appLockCredentialManager.saveCredential(
                method = method,
                pinLength = pinLength,
                rawCredential = rawCredential
            )
            if (saved) {
                if (enableAfterSave) {
                    container.appLockCredentialManager.setAppLockEnabled(true)
                }
                container.appLockSessionGuard.grantManagementAccess()
                _appLockManagementUnlocked.value = true
                _events.emit(UiEvent.ShowMessage("App Lock credential updated securely."))
            } else {
                _events.emit(UiEvent.ShowMessage("Invalid credential format. Check length or sequence."))
            }
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !hasAppLockCredential.value) {
                _events.emit(UiEvent.ShowMessage("Set up a Pattern, PIN, or 6-Cell Sequence first."))
                return@launch
            }
            container.appLockCredentialManager.setAppLockEnabled(enabled)
        }
    }

    fun saveAppLockProtectedPackages(selectedPackages: Set<String>) {
        viewModelScope.launch {
            val installedMap = _installedApps.value.associateBy { it.packageName }
            val filtered = selectedPackages.filter { it != getApplication<Application>().packageName }
            val pairs = filtered.map { pkg -> pkg to (installedMap[pkg]?.appName ?: pkg) }
            container.appLockRepository.replaceProtectedApps(
                apps = pairs,
                nowWallMs = container.clockProvider.wallClockMs()
            )
            _events.emit(UiEvent.ShowMessage("App Lock protects ${pairs.size} apps."))
        }
    }

    fun setForceDarkEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setForceDarkEnabled(enabled)
        }
    }

    fun setForceDarkApproach(approach: ForceDarkModeApproach) {
        viewModelScope.launch {
            container.preferencesRepository.setForceDarkApproach(approach)
        }
    }

    fun setForceDarkAppOverride(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setForceDarkAppOverride(packageName, enabled)
        }
    }

    fun resetForceDarkAppOverride(packageName: String) {
        viewModelScope.launch {
            container.preferencesRepository.resetForceDarkAppOverride(packageName)
        }
    }

    fun resetAllForceDarkPreferences() {
        viewModelScope.launch {
            container.preferencesRepository.resetAllForceDarkPreferences()
            _events.emit(UiEvent.ShowMessage("All Resolve dark mode overrides cleared."))
        }
    }

    fun attemptAppDarkMode(packageName: String, enable: Boolean) {
        viewModelScope.launch {
            val report = ForceDarkCapabilityHelper.inspect(getApplication())
            when (val res = ForceDarkCapabilityHelper.tryApplyPerAppDarkMode(packageName, report.supportedDarkModeApps)) {
                ModeOperationResult.Applied ->
                    container.preferencesRepository.setAppDarkModePreference(packageName, enable)
                is ModeOperationResult.Unsupported ->
                    _events.emit(UiEvent.ShowMessage(res.reason))
            }
        }
    }

    fun attemptAppColorInversion(packageName: String, enable: Boolean) {
        viewModelScope.launch {
            val report = ForceDarkCapabilityHelper.inspect(getApplication())
            when (val res = ForceDarkCapabilityHelper.tryApplyPerAppColorInversion(packageName, report.supportedColorInversionApps)) {
                ModeOperationResult.Applied ->
                    container.preferencesRepository.setAppColorInversionPreference(packageName, enable)
                is ModeOperationResult.Unsupported ->
                    _events.emit(UiEvent.ShowMessage(res.reason))
            }
        }
    }

    fun setLandscapeTiltMode(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setLandscapeTiltMode(enabled)
        }
    }

    fun setInAppColorInversionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setInAppColorInversionEnabled(enabled)
        }
    }

    fun grantColorInversionConsentAndOpenAccessibility() {
        viewModelScope.launch {
            val currentOs = if (ForceDarkCapabilityHelper.isAccessibilityInversionEnabled(getApplication())) 1 else 0
            if (priorOsColorInversionState.value == -1) {
                container.preferencesRepository.setPriorOsColorInversionState(currentOs)
            }
            container.preferencesRepository.setColorInversionConsentGranted(true)
            ForceDarkCapabilityHelper.openAccessibilityDisplaySettings(getApplication())
        }
    }

    fun openDisplayDarkSettings() {
        ForceDarkCapabilityHelper.openDisplaySettings(getApplication())
    }

    fun resetAllModes() {
        viewModelScope.launch {
            container.preferencesRepository.resetAllModeAppPreferences()
            _events.emit(UiEvent.ShowMessage("Visual preferences reset to defaults."))
        }
    }

    fun setNotificationControlEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setNotificationControlEnabled(enabled)
        }
    }

    fun setNotificationControlAutoActivate(autoActivate: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setNotificationControlAutoActivate(autoActivate)
        }
    }

    fun setNotificationControlAllowCalls(allow: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setNotificationControlAllowCalls(allow)
        }
    }

    fun setNotificationControlAllowAlarms(allow: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setNotificationControlAllowAlarms(allow)
        }
    }

    fun openNotificationPolicySettings() {
        container.capabilityChecker.openNotificationPolicySettings()
    }

    private suspend fun seedLegacyAllowlistIntoProfilesIfNeeded() {
        val legacyPackages = container.allowlistRepository.getAllowedPackages()
        if (legacyPackages.isEmpty()) return
        LockMode.entries.forEach { mode ->
            val current = container.preferencesRepository.getLockProfileSettingsSnapshot(mode)
            if (current.updatedAtWallMs == 0L && current.allowedPackages.isEmpty()) {
                container.preferencesRepository.saveLockProfileSettings(
                    current.copy(allowedPackages = legacyPackages.distinct())
                )
            }
        }
    }

    fun setProfileStrictMode(mode: LockMode, enabled: Boolean) {
        viewModelScope.launch {
            val current = container.preferencesRepository.getLockProfileSettingsSnapshot(mode)
            val updated = current.copy(
                strictMode = enabled,
                allowSelectedApps = if (enabled) false else current.allowSelectedApps,
                updatedAtWallMs = container.clockProvider.wallClockMs()
            )
            container.preferencesRepository.saveLockProfileSettings(updated)
            container.multiDeviceSyncCoordinator.syncLockProfileToCloud(updated)
        }
    }

    fun setProfileAllowSelectedApps(mode: LockMode, enabled: Boolean) {
        viewModelScope.launch {
            val current = container.preferencesRepository.getLockProfileSettingsSnapshot(mode)
            val updated = current.copy(
                allowSelectedApps = enabled,
                updatedAtWallMs = container.clockProvider.wallClockMs()
            )
            container.preferencesRepository.saveLockProfileSettings(updated)
            container.multiDeviceSyncCoordinator.syncLockProfileToCloud(updated)
        }
    }

    fun setProfileFlipClockShortcut(mode: LockMode, enabled: Boolean) {
        viewModelScope.launch {
            val current = container.preferencesRepository.getLockProfileSettingsSnapshot(mode)
            val updated = current.copy(
                showFlipClockShortcut = enabled,
                updatedAtWallMs = container.clockProvider.wallClockMs()
            )
            container.preferencesRepository.saveLockProfileSettings(updated)
            container.multiDeviceSyncCoordinator.syncLockProfileToCloud(updated)
        }
    }

    fun saveProfileAllowedApps(mode: LockMode, selectedPackages: Set<String>) {
        viewModelScope.launch {
            val nowWall = container.clockProvider.wallClockMs()
            val cleanList = selectedPackages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val current = container.preferencesRepository.getLockProfileSettingsSnapshot(mode)
            val updated = current.copy(
                allowedPackages = cleanList,
                updatedAtWallMs = nowWall
            )
            container.preferencesRepository.saveLockProfileSettings(updated)
            val installedMap = _installedApps.value.associateBy { it.packageName }
            val pairs = cleanList.map { pkg -> pkg to (installedMap[pkg]?.appName ?: pkg) }
            container.allowlistRepository.replaceAllowedApps(apps = pairs, nowWallMs = nowWall)
            container.multiDeviceSyncCoordinator.syncLockProfileToCloud(updated)
            _events.emit(UiEvent.ShowMessage("Saved ${cleanList.size} allowed apps."))
        }
    }

    fun saveAllowedAppsSelection(selectedPackages: Set<String>) {
        saveProfileAllowedApps(LockMode.INSTANT, selectedPackages)
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
            val lockMins = _instantDuration.value.totalMinutes
            val shouldSync = _syncInstantLockToDevices.value && accountSyncState.value.isSignedIn
            val profile = container.preferencesRepository.getLockProfileSettingsSnapshot(LockMode.INSTANT)
            val result = engine.startInstantLock(
                lockDurationMinutes = lockMins,
                allowedPackages = profile.effectiveAllowedPackages,
                phoneAccessEnabled = _phoneAccessEnabled.value,
                isStrictMode = profile.strictMode,
                showFlipClockShortcut = profile.showFlipClockShortcut
            )
            when (result) {
                EngineStartResult.Started -> {
                    if (shouldSync) {
                        val nowWall = container.clockProvider.wallClockMs()
                        container.multiDeviceSyncCoordinator.publishLockTriggerIfEnabled(
                            mode = LockMode.INSTANT,
                            freeUseMinutes = 0L,
                            lockDurationMinutes = lockMins,
                            freeUseDeadlineWallMs = 0L,
                            lockExpirationWallMs = nowWall + lockMins * 60_000L,
                            syncAcrossDevices = true,
                            strictMode = profile.strictMode,
                            showFlipClockShortcut = profile.showFlipClockShortcut
                        )
                    }
                }
                is EngineStartResult.Blocked -> _events.emit(UiEvent.ShowMessage(result.reason))
            }
        }
    }

    fun startDelayedLock() {
        viewModelScope.launch {
            val freeMins = _delayedFreeUseDuration.value.totalMinutes
            val lockMins = _delayedLockDuration.value.totalMinutes
            val shouldSync = _syncDelayedLockToDevices.value && accountSyncState.value.isSignedIn
            val profile = container.preferencesRepository.getLockProfileSettingsSnapshot(LockMode.DELAYED)
            val result = engine.startDelayedLock(
                freeUseMinutes = freeMins,
                lockDurationMinutes = lockMins,
                allowedPackages = profile.effectiveAllowedPackages,
                phoneAccessEnabled = _phoneAccessEnabled.value,
                isStrictMode = profile.strictMode,
                showFlipClockShortcut = profile.showFlipClockShortcut
            )
            when (result) {
                EngineStartResult.Started -> {
                    if (shouldSync) {
                        val nowWall = container.clockProvider.wallClockMs()
                        val freeDeadline = nowWall + freeMins * 60_000L
                        val lockExpire = freeDeadline + lockMins * 60_000L
                        container.multiDeviceSyncCoordinator.publishLockTriggerIfEnabled(
                            mode = LockMode.DELAYED,
                            freeUseMinutes = freeMins,
                            lockDurationMinutes = lockMins,
                            freeUseDeadlineWallMs = freeDeadline,
                            lockExpirationWallMs = lockExpire,
                            syncAcrossDevices = true,
                            strictMode = profile.strictMode,
                            showFlipClockShortcut = profile.showFlipClockShortcut
                        )
                    }
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
        syncAcrossDevices: Boolean = false,
        isEnabled: Boolean = true,
        isStrictMode: Boolean = false,
        allowSelectedApps: Boolean = true,
        showFlipClockShortcut: Boolean = false
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
            val existingSchedule = editingId?.let { container.scheduleRepository.getScheduleById(it) }
            val resolvedCloudId = when {
                syncAcrossDevices -> existingSchedule?.cloudId?.takeIf { it.isNotBlank() }
                    ?: "sched_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                else -> existingSchedule?.cloudId.orEmpty()
            }

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
                allowedPackages = allowedPackages.distinct(),
                phoneAccessEnabled = phoneAccessEnabled,
                isEnabled = isEnabled,
                createdAtWallMs = nowWall,
                cloudId = resolvedCloudId,
                syncAcrossDevices = syncAcrossDevices,
                isStrictMode = isStrictMode,
                allowSelectedApps = allowSelectedApps,
                showFlipClockShortcut = showFlipClockShortcut
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

            val finalSchedule = candidate.copy(nextTriggerWallMs = nextTrigger)
            container.scheduleRepository.saveSchedule(finalSchedule)
            engine.syncAllSchedules()

            if (syncAcrossDevices) {
                container.multiDeviceSyncCoordinator.syncScheduleToCloud(finalSchedule, deleted = false)
            } else if (existingSchedule != null && existingSchedule.syncAcrossDevices && resolvedCloudId.isNotBlank()) {
                container.multiDeviceSyncCoordinator.syncScheduleToCloud(finalSchedule, deleted = true)
            }

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
                val persisted = updated.copy(nextTriggerWallMs = next)
                container.scheduleRepository.saveSchedule(persisted)
                if (persisted.syncAcrossDevices) {
                    container.multiDeviceSyncCoordinator.syncScheduleToCloud(persisted, deleted = false)
                }
            } else {
                val persisted = updated.copy(nextTriggerWallMs = null)
                container.scheduleRepository.saveSchedule(persisted)
                if (persisted.syncAcrossDevices) {
                    container.multiDeviceSyncCoordinator.syncScheduleToCloud(persisted, deleted = false)
                }
            }
            engine.syncAllSchedules()
        }
    }

    fun deleteSchedule(scheduleId: Long) {
        viewModelScope.launch {
            val existing = container.scheduleRepository.getScheduleById(scheduleId)
            container.scheduleRepository.deleteSchedule(scheduleId)
            if (existing != null && existing.cloudId.isNotBlank()) {
                container.multiDeviceSyncCoordinator.syncScheduleToCloud(existing, deleted = true)
            }
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
        if (StartupDecisionResolver.shouldRestoreLockImmediately(current)) {
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

    fun selectMascot(mascot: MascotType) {
        viewModelScope.launch {
            container.mascotSelectionManager.selectMascot(mascot)
        }
    }

    fun markBunnyPeekEntranceSeen() {
        viewModelScope.launch {
            container.mascotSelectionManager.markBunnyPeekEntranceSeen(true)
        }
    }

    fun setReducedMotion(enabled: Boolean) {
        viewModelScope.launch {
            container.mascotSelectionManager.setReducedMotion(enabled)
        }
    }

    fun setFocusMessagesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.focusMessageManager.setMessagesEnabled(enabled)
        }
    }

    fun setFocusMessageFrequency(frequency: FocusMessageFrequency) {
        viewModelScope.launch {
            container.focusMessageManager.setFrequency(frequency)
        }
    }

    fun addFocusMessage(rawText: String) {
        viewModelScope.launch {
            val added = container.focusMessageManager.addCustomMessage(rawText)
            if (added) {
                _events.emit(UiEvent.ShowMessage("Focus message added."))
            } else {
                _events.emit(UiEvent.ShowMessage("Enter a non-empty focus message."))
            }
        }
    }

    fun editFocusMessage(id: String, rawText: String) {
        viewModelScope.launch {
            val updated = container.focusMessageManager.editMessage(id, rawText)
            if (updated) {
                _events.emit(UiEvent.ShowMessage("Focus message updated."))
            } else {
                _events.emit(UiEvent.ShowMessage("Enter a non-empty focus message."))
            }
        }
    }

    fun deleteFocusMessage(id: String) {
        viewModelScope.launch {
            container.focusMessageManager.deleteMessage(id)
        }
    }

    fun restoreDefaultFocusMessages() {
        viewModelScope.launch {
            container.focusMessageManager.restoreDefaultMessages()
            _events.emit(UiEvent.ShowMessage("Default focus messages restored."))
        }
    }

    fun setLockReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.mascotSelectionManager.setLockReminderEnabled(enabled)
        }
    }

    fun setLockReminderText(rawText: String) {
        viewModelScope.launch {
            val clean = rawText.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
            if (clean.isEmpty()) {
                _events.emit(UiEvent.ShowMessage("Enter a reminder sentence to display."))
            } else {
                container.mascotSelectionManager.setLockReminderText(clean)
                _events.emit(UiEvent.ShowMessage("Lock screen reminder saved."))
            }
        }
    }

    fun setLockReminderStyle(style: LockReminderStyle) {
        viewModelScope.launch {
            container.mascotSelectionManager.setLockReminderStyle(style)
        }
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
