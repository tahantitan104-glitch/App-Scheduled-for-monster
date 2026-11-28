package com.example.di

import android.content.Context
import android.os.Build
import com.example.data.local.ResolveDatabase
import com.example.data.preferences.MascotPreferencesRepository
import com.example.data.preferences.ResolvePreferencesRepository
import com.example.data.repository.AllowlistRepository
import com.example.data.repository.AppLockRepository
import com.example.data.repository.DelayedPresetRepository
import com.example.data.repository.FlipTimerPresetRepository
import com.example.data.repository.HistoryRepository
import com.example.data.repository.ScheduleRepository
import com.example.data.repository.SessionRepository
import com.example.devicepolicy.AndroidDevicePolicyController
import com.example.devicepolicy.DevicePolicyController
import com.example.domain.model.AppUsageQuotaRule
import com.example.domain.model.ContentBlockerConfig
import com.example.domain.model.MindfulPauseConfig
import com.example.domain.time.ClockProvider
import com.example.domain.time.SystemClockProvider
import com.example.flipclock.FlipAmbientAudioEngine
import com.example.flipclock.FlipClockManager
import com.example.flipclock.FlipTimerNotificationHelper
import com.example.mascot.FocusMessageManager
import com.example.mascot.MascotSelectionManager
import com.example.platform.AppUsageRepository
import com.example.platform.DeviceAnalysisEngine
import com.example.platform.ForegroundEnforcementGuard
import com.example.platform.InstalledAppsRepository
import com.example.platform.NotificationControlManager
import com.example.platform.SystemCapabilityChecker
import com.example.scheduling.AndroidLockAlarmScheduler
import com.example.scheduling.LockAlarmScheduler
import com.example.security.AccountabilityPartnerManager
import com.example.security.AppLockCredentialManager
import com.example.security.AppLockSessionGuard
import com.example.security.RecoveryCodeManager
import com.example.service.ResolveSessionService
import com.example.service.SessionRecoveryJobService
import com.example.session.LockSessionEngine
import com.example.session.SessionPlatformBridge
import com.example.sync.CloudAccountRepository
import com.example.sync.CloudSyncGateway
import com.example.sync.MultiDeviceSyncCoordinator
import com.example.sync.RestCloudAccountRepository
import com.example.telephony.PhoneAccessManager
import com.example.vpn.ResolveDnsVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppContainer(private val appContext: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val clockProvider: ClockProvider = SystemClockProvider()
    val database: ResolveDatabase by lazy { ResolveDatabase.create(appContext) }
    val preferencesRepository: ResolvePreferencesRepository by lazy {
        ResolvePreferencesRepository(appContext)
    }
    val mascotPreferencesRepository: MascotPreferencesRepository by lazy {
        MascotPreferencesRepository(appContext)
    }
    val mascotSelectionManager: MascotSelectionManager by lazy {
        MascotSelectionManager(mascotPreferencesRepository)
    }
    val focusMessageManager: FocusMessageManager by lazy {
        FocusMessageManager(mascotPreferencesRepository)
    }

    val sessionRepository: SessionRepository by lazy {
        SessionRepository(database.activeSessionDao())
    }
    val scheduleRepository: ScheduleRepository by lazy {
        ScheduleRepository(database.lockScheduleDao())
    }
    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(database.sessionHistoryDao())
    }
    val allowlistRepository: AllowlistRepository by lazy {
        AllowlistRepository(database.allowedAppDao())
    }
    val delayedPresetRepository: DelayedPresetRepository by lazy {
        DelayedPresetRepository(database.delayedLockPresetDao())
    }
    val appLockRepository: AppLockRepository by lazy {
        AppLockRepository(database.appLockAppDao())
    }
    val flipTimerPresetRepository: FlipTimerPresetRepository by lazy {
        FlipTimerPresetRepository(database.flipTimerPresetDao(), preferencesRepository)
    }

    val phoneAccessManager: PhoneAccessManager by lazy { PhoneAccessManager(appContext) }
    val installedAppsRepository: InstalledAppsRepository by lazy {
        InstalledAppsRepository(appContext, phoneAccessManager)
    }
    val capabilityChecker: SystemCapabilityChecker by lazy { SystemCapabilityChecker(appContext) }
    val notificationControlManager: NotificationControlManager by lazy {
        NotificationControlManager(appContext, preferencesRepository, capabilityChecker)
    }
    val deviceAnalysisEngine: DeviceAnalysisEngine by lazy {
        DeviceAnalysisEngine(appContext, capabilityChecker, clockProvider)
    }
    val appUsageRepository: AppUsageRepository by lazy {
        AppUsageRepository(appContext, capabilityChecker, clockProvider)
    }
    val foregroundGuard: ForegroundEnforcementGuard by lazy {
        ForegroundEnforcementGuard(appContext, capabilityChecker)
    }

    val devicePolicyController: DevicePolicyController by lazy {
        AndroidDevicePolicyController(appContext, phoneAccessManager, installedAppsRepository)
    }
    val alarmScheduler: LockAlarmScheduler by lazy { AndroidLockAlarmScheduler(appContext) }

    val recoveryCodeManager: RecoveryCodeManager by lazy {
        RecoveryCodeManager(preferencesRepository, clockProvider)
    }
    val accountabilityPartnerManager: AccountabilityPartnerManager by lazy {
        AccountabilityPartnerManager(preferencesRepository, clockProvider)
    }
    val appLockCredentialManager: AppLockCredentialManager by lazy {
        AppLockCredentialManager(preferencesRepository, clockProvider)
    }
    val appLockSessionGuard: AppLockSessionGuard by lazy {
        AppLockSessionGuard()
    }

    val antiBypassStrictGuardState: StateFlow<Boolean> = preferencesRepository.antiBypassStrictGuardFlow
        .stateIn(appScope, SharingStarted.Eagerly, true)
    val mindfulPauseConfigState: StateFlow<MindfulPauseConfig> = preferencesRepository.mindfulPauseConfigFlow
        .stateIn(appScope, SharingStarted.Eagerly, MindfulPauseConfig())
    val dailyQuotaRulesState: StateFlow<List<AppUsageQuotaRule>> = preferencesRepository.dailyQuotaRulesFlow
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())
    val contentBlockerConfigState: StateFlow<ContentBlockerConfig> = preferencesRepository.contentBlockerConfigFlow
        .stateIn(appScope, SharingStarted.Eagerly, ContentBlockerConfig())
    val excludedScreenTimePackagesState: StateFlow<Set<String>> = preferencesRepository.excludedScreenTimePackagesFlow
        .stateIn(appScope, SharingStarted.Eagerly, emptySet())

    fun recordInterceptedAppAsync(packageName: String) {
        if (packageName.isBlank() || packageName == appContext.packageName) return
        appScope.launch(Dispatchers.IO) {
            runCatching { preferencesRepository.incrementInterceptedAppCount(packageName) }
        }
    }

    fun recordDnsBlockedAsync() {
        appScope.launch(Dispatchers.IO) {
            runCatching { preferencesRepository.incrementDnsBlockedCount() }
        }
    }

    @Volatile
    private var fgsStartBlocked: Boolean = false

    private val platformBridge = object : SessionPlatformBridge {
        override fun startMonitorService() {
            fgsStartBlocked = !ResolveSessionService.start(appContext)
        }

        override fun stopMonitorService() {
            fgsStartBlocked = false
            foregroundGuard.clearBarrier()
            ResolveSessionService.stop(appContext)
        }

        override fun launchLockScreen() {
            foregroundGuard.showBriefBarrierAndLaunchLockScreen()
        }

        override fun notifyLockStateChanged() {
            runCatching { com.example.widget.ResolveHomeWidgetProvider.requestWidgetUpdate(appContext) }
        }

        override fun isForegroundServiceBlocked(): Boolean = fgsStartBlocked

        override fun scheduleRecoveryJobIfNeeded() {
            SessionRecoveryJobService.scheduleIfNeeded(appContext)
        }

        override fun cancelRecoveryJob() {
            SessionRecoveryJobService.cancel(appContext)
        }
    }

    val sessionEngine: LockSessionEngine by lazy {
        LockSessionEngine(
            sessionRepository = sessionRepository,
            scheduleRepository = scheduleRepository,
            historyRepository = historyRepository,
            preferencesRepository = preferencesRepository,
            devicePolicyController = devicePolicyController,
            alarmScheduler = alarmScheduler,
            recoveryCodeManager = recoveryCodeManager,
            capabilityChecker = capabilityChecker,
            clockProvider = clockProvider,
            platformBridge = platformBridge,
            notificationControlManager = notificationControlManager,
            accountabilityPartnerManager = accountabilityPartnerManager
        )
    }

    val flipTimerNotificationHelper: FlipTimerNotificationHelper by lazy {
        FlipTimerNotificationHelper(appContext)
    }

    val ambientAudioEngine: FlipAmbientAudioEngine by lazy {
        FlipAmbientAudioEngine().apply { attachContext(appContext) }
    }

    val flipClockManager: FlipClockManager by lazy {
        FlipClockManager(
            preferencesRepository = preferencesRepository,
            presetRepository = flipTimerPresetRepository,
            notificationHelper = flipTimerNotificationHelper,
            sessionEngine = sessionEngine,
            clockProvider = clockProvider,
            ambientAudioEngine = ambientAudioEngine,
            appContext = appContext
        )
    }

    val cloudAccountRepository: CloudSyncGateway by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            CloudAccountRepository(appContext)
        } else {
            RestCloudAccountRepository(appContext, preferencesRepository)
        }
    }

    val multiDeviceSyncCoordinator: MultiDeviceSyncCoordinator by lazy {
        MultiDeviceSyncCoordinator(
            appContext = appContext,
            gateway = cloudAccountRepository,
            preferencesRepository = preferencesRepository,
            allowlistRepository = allowlistRepository,
            sessionEngine = sessionEngine,
            clockProvider = clockProvider,
            delayedPresetRepository = delayedPresetRepository,
            scheduleRepository = scheduleRepository,
            flipTimerPresetRepository = flipTimerPresetRepository,
            flipClockManager = flipClockManager
        )
    }

    init {
        appScope.launch {
            combine(
                appLockCredentialManager.appLockEnabledFlow,
                appLockCredentialManager.hasCredentialFlow,
                appLockRepository.observeProtectedPackages()
            ) { enabled, hasCred, packages ->
                Triple(enabled, hasCred, packages.toSet())
            }.collect { (enabled, hasCred, pkgSet) ->
                appLockSessionGuard.updateState(
                    enabled = enabled,
                    hasCredential = hasCred,
                    protectedPackages = pkgSet
                )
            }
        }
        appScope.launch {
            combine(
                contentBlockerConfigState,
                sessionEngine.activeSession
            ) { cfg, active ->
                cfg to (active?.isEnforcementPhase == true)
            }
                .distinctUntilChanged()
                .collect { (cfg, locked) ->
                    runCatching { ResolveDnsVpnService.reconcileState(appContext, cfg, locked) }
                }
        }
    }
}
