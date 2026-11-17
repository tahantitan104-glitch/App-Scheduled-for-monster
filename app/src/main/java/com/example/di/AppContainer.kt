package com.example.di

import android.content.Context
import com.example.data.local.ResolveDatabase
import com.example.data.preferences.MascotPreferencesRepository
import com.example.data.preferences.ResolvePreferencesRepository
import com.example.data.repository.AllowlistRepository
import com.example.data.repository.HistoryRepository
import com.example.data.repository.ScheduleRepository
import com.example.data.repository.SessionRepository
import com.example.devicepolicy.AndroidDevicePolicyController
import com.example.devicepolicy.DevicePolicyController
import com.example.domain.time.ClockProvider
import com.example.domain.time.SystemClockProvider
import com.example.mascot.FocusMessageManager
import com.example.mascot.MascotSelectionManager
import com.example.platform.ForegroundEnforcementGuard
import com.example.platform.InstalledAppsRepository
import com.example.platform.SystemCapabilityChecker
import com.example.scheduling.AndroidLockAlarmScheduler
import com.example.scheduling.LockAlarmScheduler
import com.example.security.RecoveryCodeManager
import com.example.service.ResolveSessionService
import com.example.session.LockSessionEngine
import com.example.session.SessionPlatformBridge
import com.example.telephony.PhoneAccessManager

class AppContainer(private val appContext: Context) {

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

    val phoneAccessManager: PhoneAccessManager by lazy { PhoneAccessManager(appContext) }
    val installedAppsRepository: InstalledAppsRepository by lazy {
        InstalledAppsRepository(appContext, phoneAccessManager)
    }
    val capabilityChecker: SystemCapabilityChecker by lazy { SystemCapabilityChecker(appContext) }
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

    private val platformBridge = object : SessionPlatformBridge {
        override fun startMonitorService() {
            ResolveSessionService.start(appContext)
        }

        override fun stopMonitorService() {
            foregroundGuard.clearBarrier()
            ResolveSessionService.stop(appContext)
        }

        override fun launchLockScreen() {
            foregroundGuard.showBriefBarrierAndLaunchLockScreen()
        }

        override fun notifyLockStateChanged() = Unit
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
            platformBridge = platformBridge
        )
    }
}
