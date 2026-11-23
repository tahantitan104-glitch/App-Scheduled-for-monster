package com.example.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.domain.model.DurationSelection
import com.example.domain.model.FlipClockPage
import com.example.domain.model.LockMode
import com.example.domain.startup.StartupStage
import com.example.flipclock.FlipClockActivity
import com.example.platform.ForceDarkCapabilityHelper
import com.example.presentation.ResolveViewModel
import com.example.presentation.UiEvent
import com.example.presentation.about.AboutResolveScreen
import com.example.presentation.analysis.DeviceAnalysisScreen
import com.example.presentation.applock.AppLockSetupScreen
import com.example.presentation.components.DeviceOwnerGuideSheet
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.ResolveMotion
import com.example.presentation.components.ResolveNotificationHost
import com.example.presentation.components.rememberEffectiveReducedMotion
import com.example.presentation.components.rememberResolveNotificationController
import com.example.presentation.home.HomeScreen
import com.example.presentation.insights.InsightsScreen
import com.example.presentation.modes.ModesScreen
import com.example.presentation.more.MoreScreen
import com.example.presentation.settings.SettingsScreen
import com.example.presentation.setup.DelayedLockSetupScreen
import com.example.presentation.setup.InstantLockSetupScreen
import com.example.presentation.setup.ScheduleLockSetupScreen
import com.example.presentation.startup.BrandIntroScreen
import com.example.presentation.startup.FirstTimeWelcomeScreen
import com.example.presentation.startup.RequiredSetupScreen

object ResolveRoutes {
    const val HOME = "home"
    const val INSIGHTS = "insights"
    const val MODES = "modes"
    const val MORE = "more"
    const val SETTINGS = "settings"
    const val DEVICE_ANALYSIS = "device_analysis?focusSection={focusSection}"
    const val ABOUT_RESOLVE = "about_resolve"
    const val INSTANT_SETUP = "instant_setup"
    const val DELAYED_SETUP = "delayed_setup"
    const val APP_LOCK_SETUP = "app_lock_setup"
    const val SCHEDULE_SETUP = "schedule_setup?scheduleId={scheduleId}"

    fun deviceAnalysis(focusSection: String? = null): String =
        if (!focusSection.isNullOrBlank()) "device_analysis?focusSection=$focusSection" else "device_analysis"

    fun scheduleSetup(scheduleId: Long? = null): String =
        if (scheduleId != null) "schedule_setup?scheduleId=$scheduleId" else "schedule_setup"
}

private data class TopLevelTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TOP_LEVEL_TABS = listOf(
    TopLevelTab(ResolveRoutes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    TopLevelTab(ResolveRoutes.INSIGHTS, "Insights", ResolveIcons.InsightsFilled, ResolveIcons.InsightsOutlined),
    TopLevelTab(ResolveRoutes.MODES, "Modes", ResolveIcons.ModesFilled, ResolveIcons.ModesOutlined),
    TopLevelTab(ResolveRoutes.MORE, "More", ResolveIcons.MoreHoriz, ResolveIcons.MoreHoriz)
)

private fun isSubScreenRoute(route: String?): Boolean {
    if (route.isNullOrBlank()) return false
    return route.contains("setup") ||
        route == ResolveRoutes.SETTINGS ||
        route.startsWith("device_analysis") ||
        route == ResolveRoutes.ABOUT_RESOLVE
}

private fun tabIndexForRoute(route: String?): Int =
    TOP_LEVEL_TABS.indexOfFirst { it.route == route }

@Composable
fun ResolveAppShell(viewModel: ResolveViewModel) {
    val palette = LocalResolveExtendedPalette.current
    val navController = rememberNavController()
    val notificationController = rememberResolveNotificationController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val remainingMillis by viewModel.remainingMillis.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val protectionStatus by viewModel.protectionStatus.collectAsStateWithLifecycle()
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val allowedPackages by viewModel.savedAllowedPackages.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val hasRecoveryCode by viewModel.hasRecoveryCode.collectAsStateWithLifecycle()
    val recoveryCodeLength by viewModel.recoveryCodeLength.collectAsStateWithLifecycle()

    val instantDuration by viewModel.instantDuration.collectAsStateWithLifecycle()
    val delayedFreeDuration by viewModel.delayedFreeUseDuration.collectAsStateWithLifecycle()
    val delayedLockDuration by viewModel.delayedLockDuration.collectAsStateWithLifecycle()
    val scheduleDuration by viewModel.scheduleLockDuration.collectAsStateWithLifecycle()
    val delayedPresets by viewModel.delayedPresets.collectAsStateWithLifecycle()
    val phoneAccessEnabled by viewModel.phoneAccessEnabled.collectAsStateWithLifecycle()
    val mascotPreferences by viewModel.mascotPreferences.collectAsStateWithLifecycle()
    val startupStage by viewModel.startupStage.collectAsStateWithLifecycle()
    val accountSyncState by viewModel.accountSyncState.collectAsStateWithLifecycle()
    val syncPromoDismissed by viewModel.syncPromoDismissed.collectAsStateWithLifecycle()
    val syncInstantLockToDevices by viewModel.syncInstantLockToDevices.collectAsStateWithLifecycle()
    val syncDelayedLockToDevices by viewModel.syncDelayedLockToDevices.collectAsStateWithLifecycle()

    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val hasAppLockCredential by viewModel.hasAppLockCredential.collectAsStateWithLifecycle()
    val appLockMethod by viewModel.appLockMethod.collectAsStateWithLifecycle()
    val appLockPinLength by viewModel.appLockPinLength.collectAsStateWithLifecycle()
    val appLockPackages by viewModel.appLockPackages.collectAsStateWithLifecycle()
    val appLockManagementUnlocked by viewModel.appLockManagementUnlocked.collectAsStateWithLifecycle()

    val dailyAppUsage by viewModel.dailyAppUsage.collectAsStateWithLifecycle()
    val instantProfile by viewModel.instantProfile.collectAsStateWithLifecycle()
    val delayedProfile by viewModel.delayedProfile.collectAsStateWithLifecycle()
    val scheduledProfile by viewModel.scheduledProfile.collectAsStateWithLifecycle()
    val darkModeAppPrefs by viewModel.darkModeAppPrefs.collectAsStateWithLifecycle()
    val forceDarkReport by viewModel.forceDarkReport.collectAsStateWithLifecycle()
    val notificationControlConfig by viewModel.notificationControlConfig.collectAsStateWithLifecycle()
    val notificationProtectionState by viewModel.notificationProtectionState.collectAsStateWithLifecycle()
    val deviceAnalysisReport by viewModel.deviceAnalysisReport.collectAsStateWithLifecycle()
    val landscapeTiltMode by viewModel.landscapeTiltMode.collectAsStateWithLifecycle()

    var showDeviceOwnerGuide by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val reducedMotion = rememberEffectiveReducedMotion(mascotPreferences.reducedMotionEnabled)

    val navigateToTab: (String) -> Unit = { route ->
        if (currentRoute != route) {
            runCatching {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    val navigateSafely: (String) -> Unit = { route ->
        runCatching {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    val popBackSafely: () -> Unit = {
        runCatching {
            navController.popBackStack()
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute != null && currentRoute != ResolveRoutes.APP_LOCK_SETUP) {
            viewModel.revokeAppLockManagementAccess()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> notificationController.show(
                    message = event.message,
                    tone = event.tone,
                    actionLabel = event.actionLabel,
                    showCloseButton = event.showCloseButton
                )
                UiEvent.NavigateBackToHome -> {
                    runCatching {
                        navController.popBackStack(ResolveRoutes.HOME, inclusive = false)
                    }
                }
            }
        }
    }

    LaunchedEffect(startupStage) {
        if (startupStage == StartupStage.RESTORE_LOCK) {
            viewModel.openLockScreenIfLocked()
        }
    }

    val isTopLevelDestination = TOP_LEVEL_TABS.any { it.route == currentRoute }

    AnimatedContent(
        targetState = startupStage,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(ResolveMotion.fastTween(true)) togetherWith
                    fadeOut(ResolveMotion.exitTween(true))
            } else {
                (fadeIn(ResolveMotion.standardTween(false)) +
                    scaleIn(
                        initialScale = 0.975f,
                        animationSpec = ResolveMotion.standardTween(false)
                    )) togetherWith
                    fadeOut(ResolveMotion.exitTween(false))
            }
        },
        label = "startup_stage_transition"
    ) { stage ->
        when (stage) {
            StartupStage.RESOLVING,
            StartupStage.RESTORE_LOCK -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.background)
                )
            }

            StartupStage.BRAND_INTRO -> {
                BrandIntroScreen(
                    reducedMotion = reducedMotion,
                    onIntroCompleted = { viewModel.onBrandIntroCompleted() }
                )
            }

            StartupStage.FIRST_TIME_WELCOME -> {
                FirstTimeWelcomeScreen(
                    reducedMotion = reducedMotion,
                    onContinue = { viewModel.onFirstTimeWelcomeContinue() }
                )
            }

            StartupStage.REQUIRED_SETUP -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    RequiredSetupScreen(
                        capabilities = capabilities,
                        hasRecoveryCode = hasRecoveryCode,
                        generateRandomCode = { viewModel.generateRandomRecoveryCode(it) },
                        onSaveRecoveryCode = { viewModel.saveRecoveryCode(it) },
                        onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                        onCompleteSetup = { viewModel.onCompleteRequiredSetup() },
                        onDeferSetupToHome = { viewModel.onDeferRequiredSetupToHome() }
                    )
                    ResolveNotificationHost(
                        controller = notificationController,
                        reducedMotion = reducedMotion,
                        bottomClearance = 18.dp,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            StartupStage.HOME -> {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val useNavigationRail = maxWidth >= 600.dp || maxWidth > maxHeight

                    Scaffold(
                        containerColor = palette.background,
                        bottomBar = {
                            AnimatedVisibility(
                                visible = isTopLevelDestination && !useNavigationRail,
                                enter = if (reducedMotion) {
                                    fadeIn(ResolveMotion.fastTween(true))
                                } else {
                                    fadeIn(ResolveMotion.fastTween(false)) +
                                        slideInVertically(ResolveMotion.intOffsetSlideTween(false)) { it / 2 }
                                },
                                exit = if (reducedMotion) {
                                    fadeOut(ResolveMotion.exitTween(true))
                                } else {
                                    fadeOut(ResolveMotion.exitTween(false)) +
                                        slideOutVertically(ResolveMotion.intOffsetSlideTween(false)) { it / 2 }
                                }
                            ) {
                                FloatingCapsuleNavDock(
                                    tabs = TOP_LEVEL_TABS,
                                    currentRoute = currentRoute,
                                    reducedMotion = reducedMotion,
                                    onSelectTab = { route ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        navigateToTab(route)
                                    }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (isTopLevelDestination && useNavigationRail) {
                                NavigationRail(containerColor = palette.surfaceSecondary) {
                                    TOP_LEVEL_TABS.forEach { tab ->
                                        val selected = currentRoute == tab.route
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                navigateToTab(tab.route)
                                            },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.label
                                                )
                                            },
                                            label = { Text(tab.label) },
                                            colors = NavigationRailItemDefaults.colors(
                                                selectedIconColor = palette.navPillContent,
                                                selectedTextColor = palette.navPillContent,
                                                indicatorColor = palette.navPillContainer,
                                                unselectedIconColor = palette.textMuted,
                                                unselectedTextColor = palette.textMuted
                                            )
                                        )
                                    }
                                }
                            }

                            NavHost(
                                navController = navController,
                                startDestination = ResolveRoutes.HOME,
                                enterTransition = {
                                    val fromRoute = initialState.destination.route
                                    val toRoute = targetState.destination.route
                                    val fromTab = tabIndexForRoute(fromRoute)
                                    val toTab = tabIndexForRoute(toRoute)
                                    when {
                                        reducedMotion -> fadeIn(ResolveMotion.fastTween(true))
                                        isSubScreenRoute(toRoute) ->
                                            slideInHorizontally(ResolveMotion.intOffsetSlideTween(false)) { it / 5 } +
                                                fadeIn(ResolveMotion.subScreenTween(false)) +
                                                scaleIn(
                                                    initialScale = 0.97f,
                                                    animationSpec = ResolveMotion.subScreenTween(false)
                                                )
                                        fromTab >= 0 && toTab >= 0 && fromTab != toTab -> {
                                            val direction = if (toTab > fromTab) 1 else -1
                                            slideInHorizontally(ResolveMotion.intOffsetSlideTween(false)) {
                                                direction * (it / 10)
                                            } +
                                                fadeIn(ResolveMotion.standardTween(false)) +
                                                scaleIn(
                                                    initialScale = 0.975f,
                                                    animationSpec = ResolveMotion.standardTween(false)
                                                )
                                        }
                                        else ->
                                            fadeIn(ResolveMotion.standardTween(false)) +
                                                scaleIn(
                                                    initialScale = 0.975f,
                                                    animationSpec = ResolveMotion.standardTween(false)
                                                )
                                    }
                                },
                                exitTransition = {
                                    val fromRoute = initialState.destination.route
                                    val toRoute = targetState.destination.route
                                    val fromTab = tabIndexForRoute(fromRoute)
                                    val toTab = tabIndexForRoute(toRoute)
                                    when {
                                        reducedMotion -> fadeOut(ResolveMotion.exitTween(true))
                                        isSubScreenRoute(toRoute) ->
                                            slideOutHorizontally(ResolveMotion.intOffsetSlideTween(false)) { -it / 10 } +
                                                fadeOut(ResolveMotion.exitTween(false))
                                        fromTab >= 0 && toTab >= 0 && fromTab != toTab -> {
                                            val direction = if (toTab > fromTab) -1 else 1
                                            slideOutHorizontally(ResolveMotion.intOffsetSlideTween(false)) {
                                                direction * (it / 10)
                                            } +
                                                fadeOut(ResolveMotion.exitTween(false))
                                        }
                                        else -> fadeOut(ResolveMotion.exitTween(false))
                                    }
                                },
                                popEnterTransition = {
                                    if (reducedMotion) {
                                        fadeIn(ResolveMotion.fastTween(true))
                                    } else {
                                        slideInHorizontally(ResolveMotion.intOffsetSlideTween(false)) { -it / 10 } +
                                            fadeIn(ResolveMotion.subScreenTween(false))
                                    }
                                },
                                popExitTransition = {
                                    if (reducedMotion) {
                                        fadeOut(ResolveMotion.exitTween(true))
                                    } else {
                                        slideOutHorizontally(ResolveMotion.intOffsetSlideTween(false)) { it / 5 } +
                                            fadeOut(ResolveMotion.exitTween(false)) +
                                            scaleOut(
                                                targetScale = 0.975f,
                                                animationSpec = ResolveMotion.exitTween(false)
                                            )
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable(ResolveRoutes.HOME) {
                                    HomeScreen(
                                        activeSession = activeSession,
                                        remainingMillis = remainingMillis,
                                        capabilities = capabilities,
                                        onOpenInstantSetup = { navigateSafely(ResolveRoutes.INSTANT_SETUP) },
                                        onOpenDelayedSetup = { navigateSafely(ResolveRoutes.DELAYED_SETUP) },
                                        onOpenScheduleSetup = { navigateSafely(ResolveRoutes.scheduleSetup()) },
                                        onOpenDeviceOwnerGuide = {
                                            if (!capabilities.canEnforceRealLock) {
                                                navigateSafely(ResolveRoutes.SETTINGS)
                                            } else {
                                                showDeviceOwnerGuide = true
                                            }
                                        },
                                        onReturnToLockScreen = { viewModel.openLockScreenIfLocked() },
                                        onDismissTerminalSession = { viewModel.dismissTerminalSession() },
                                        onOpenAppLockSetup = { navigateSafely(ResolveRoutes.APP_LOCK_SETUP) },
                                        historyRecords = history,
                                        dailyAppUsage = dailyAppUsage,
                                        loadAppIcon = { viewModel.loadAppIcon(it) },
                                        onGrantUsageAccess = { viewModel.openUsageAccessSettings() },
                                        mascotType = mascotPreferences.selectedMascot,
                                        bunnyPeekEntranceSeen = mascotPreferences.bunnyPeekEntranceSeen,
                                        onBunnyPeekEntranceSeen = { viewModel.markBunnyPeekEntranceSeen() },
                                        reducedMotion = reducedMotion,
                                        accountSyncState = accountSyncState,
                                        syncPromoDismissed = syncPromoDismissed,
                                        onConnectGoogleAccount = { viewModel.signInWithGoogle(context) },
                                        onDismissSyncPromo = { viewModel.dismissSyncPromoBanner() },
                                        onOpenAccountSettings = { navigateSafely(ResolveRoutes.SETTINGS) },
                                        protectionStatus = protectionStatus,
                                        onProtectionAction = { viewModel.executeProtectionAction(it) }
                                    )
                                }

                                composable(ResolveRoutes.INSIGHTS) {
                                    InsightsScreen(
                                        records = history,
                                        schedules = schedules,
                                        dailyAppUsage = dailyAppUsage,
                                        onGrantUsageAccess = { viewModel.openUsageAccessSettings() },
                                        onAddSchedule = { navigateSafely(ResolveRoutes.scheduleSetup()) },
                                        onEditSchedule = { schedule ->
                                            viewModel.updateScheduleLockDuration(
                                                DurationSelection.fromTotalMinutes(schedule.lockDurationMinutes)
                                            )
                                            navigateSafely(ResolveRoutes.scheduleSetup(schedule.id))
                                        },
                                        onToggleSchedule = { schedule, enabled ->
                                            viewModel.toggleScheduleEnabled(schedule, enabled)
                                        },
                                        onDeleteSchedule = { id -> viewModel.deleteSchedule(id) }
                                    )
                                }

                                composable(ResolveRoutes.MODES) {
                                    ModesScreen(
                                        report = forceDarkReport,
                                        installedApps = installedApps,
                                        darkModeAppPrefs = darkModeAppPrefs,
                                        loadAppIcon = { viewModel.loadAppIcon(it) },
                                        onEnterFlipClock = {
                                            context.startActivity(
                                                FlipClockActivity.createIntent(
                                                    context = context,
                                                    restrictedLock = false,
                                                    initialPage = FlipClockPage.CLOCK
                                                )
                                            )
                                        },
                                        onAttemptAppDarkMode = { pkg, enable ->
                                            viewModel.attemptAppDarkMode(pkg, enable)
                                        },
                                        onOpenDisplaySettings = {
                                            viewModel.openDisplayDarkSettings()
                                        },
                                        onResetAllModes = { viewModel.resetAllModes() }
                                    )
                                }

                                composable(ResolveRoutes.MORE) {
                                    MoreScreen(
                                        capabilities = capabilities,
                                        protectionStatus = protectionStatus,
                                        deviceAnalysisReport = deviceAnalysisReport,
                                        hasRecoveryCode = hasRecoveryCode,
                                        recoveryCodeLength = recoveryCodeLength,
                                        landscapeTiltMode = landscapeTiltMode,
                                        notificationConfig = notificationControlConfig,
                                        notificationProtectionState = notificationProtectionState,
                                        mascotPreferences = mascotPreferences,
                                        accountSyncState = accountSyncState,
                                        generateRandomCode = { viewModel.generateRandomRecoveryCode(it) },
                                        onSaveRecoveryCode = { viewModel.saveRecoveryCode(it) },
                                        onToggleLandscapeTiltMode = { viewModel.setLandscapeTiltMode(it) },
                                        onRefreshCapabilities = { viewModel.refreshCapabilities() },
                                        onProtectionAction = { viewModel.executeProtectionAction(it) },
                                        onOpenDeviceAnalysis = { focusSection ->
                                            navigateSafely(ResolveRoutes.deviceAnalysis(focusSection))
                                        },
                                        onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                                        onOpenOverlaySettings = { viewModel.openOverlaySettings() },
                                        onOpenUsageAccessSettings = { viewModel.openUsageAccessSettings() },
                                        onOpenBatteryOptimizationSettings = { viewModel.openBatteryOptimizationSettings() },
                                        onOpenExactAlarmSettings = { viewModel.openExactAlarmSettings() },
                                        onOpenNotificationSettings = { viewModel.openNotificationSettings() },
                                        onOpenNotificationPolicySettings = { viewModel.openNotificationPolicySettings() },
                                        onToggleNotificationControlEnabled = { viewModel.setNotificationControlEnabled(it) },
                                        onToggleNotificationControlAutoActivate = { viewModel.setNotificationControlAutoActivate(it) },
                                        onToggleNotificationControlAllowCalls = { viewModel.setNotificationControlAllowCalls(it) },
                                        onToggleNotificationControlAllowAlarms = { viewModel.setNotificationControlAllowAlarms(it) },
                                        onSelectMascot = { viewModel.selectMascot(it) },
                                        onToggleReducedMotion = { viewModel.setReducedMotion(it) },
                                        onToggleFocusMessagesEnabled = { viewModel.setFocusMessagesEnabled(it) },
                                        onSelectFocusMessageFrequency = { viewModel.setFocusMessageFrequency(it) },
                                        onAddFocusMessage = { viewModel.addFocusMessage(it) },
                                        onEditFocusMessage = { id, text -> viewModel.editFocusMessage(id, text) },
                                        onDeleteFocusMessage = { viewModel.deleteFocusMessage(it) },
                                        onRestoreDefaultFocusMessages = { viewModel.restoreDefaultFocusMessages() },
                                        onToggleLockReminderEnabled = { viewModel.setLockReminderEnabled(it) },
                                        onSaveLockReminderText = { viewModel.setLockReminderText(it) },
                                        onSelectLockReminderStyle = { viewModel.setLockReminderStyle(it) },
                                        onSignInGoogle = { viewModel.signInWithGoogle(context) },
                                        onSignOutGoogle = { viewModel.signOutGoogleAccount() },
                                        onToggleDeviceSync = { viewModel.setDeviceSyncEnabled(it) },
                                        onUpdateProfileName = { viewModel.updateProfileName(it) },
                                        onUpdateProfilePhoto = { viewModel.updateProfilePhoto(it) },
                                        onClearProfilePhoto = { viewModel.clearProfilePhoto() },
                                        onUnlinkDevice = { viewModel.unlinkDevice(it) }
                                    )
                                }

                                composable(ResolveRoutes.SETTINGS) {
                                    SettingsScreen(
                                        capabilities = capabilities,
                                        hasRecoveryCode = hasRecoveryCode,
                                        recoveryCodeLength = recoveryCodeLength,
                                        generateRandomCode = { viewModel.generateRandomRecoveryCode(it) },
                                        onSaveRecoveryCode = { viewModel.saveRecoveryCode(it) },
                                        onRefreshCapabilities = { viewModel.refreshCapabilities() },
                                        onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                                        onOpenOverlaySettings = { viewModel.openOverlaySettings() },
                                        onOpenUsageAccessSettings = { viewModel.openUsageAccessSettings() },
                                        onOpenBatteryOptimizationSettings = { viewModel.openBatteryOptimizationSettings() },
                                        onOpenDeviceOwnerGuide = { showDeviceOwnerGuide = true },
                                        onOpenExactAlarmSettings = { viewModel.openExactAlarmSettings() },
                                        onOpenNotificationSettings = { viewModel.openNotificationSettings() },
                                        mascotPreferences = mascotPreferences,
                                        onSelectMascot = { viewModel.selectMascot(it) },
                                        onToggleReducedMotion = { viewModel.setReducedMotion(it) },
                                        onToggleFocusMessagesEnabled = { viewModel.setFocusMessagesEnabled(it) },
                                        onSelectFocusMessageFrequency = { viewModel.setFocusMessageFrequency(it) },
                                        onAddFocusMessage = { viewModel.addFocusMessage(it) },
                                        onEditFocusMessage = { id, text -> viewModel.editFocusMessage(id, text) },
                                        onDeleteFocusMessage = { viewModel.deleteFocusMessage(it) },
                                        onRestoreDefaultFocusMessages = { viewModel.restoreDefaultFocusMessages() },
                                        onToggleLockReminderEnabled = { viewModel.setLockReminderEnabled(it) },
                                        onSaveLockReminderText = { viewModel.setLockReminderText(it) },
                                        onSelectLockReminderStyle = { viewModel.setLockReminderStyle(it) },
                                        accountSyncState = accountSyncState,
                                        onSignInGoogle = { viewModel.signInWithGoogle(context) },
                                        onSignOutGoogle = { viewModel.signOutGoogleAccount() },
                                        onToggleDeviceSync = { viewModel.setDeviceSyncEnabled(it) },
                                        onUpdateProfilePhoto = { viewModel.updateProfilePhoto(it) },
                                        onClearProfilePhoto = { viewModel.clearProfilePhoto() },
                                        onUnlinkDevice = { viewModel.unlinkDevice(it) },
                                        protectionStatus = protectionStatus,
                                        onProtectionAction = { viewModel.executeProtectionAction(it) },
                                        notificationControlConfig = notificationControlConfig,
                                        notificationProtectionState = notificationProtectionState,
                                        onToggleNotificationControlEnabled = { viewModel.setNotificationControlEnabled(it) },
                                        onToggleNotificationControlAutoActivate = { viewModel.setNotificationControlAutoActivate(it) },
                                        onToggleNotificationControlAllowCalls = { viewModel.setNotificationControlAllowCalls(it) },
                                        onToggleNotificationControlAllowAlarms = { viewModel.setNotificationControlAllowAlarms(it) },
                                        onOpenNotificationPolicySettings = { viewModel.openNotificationPolicySettings() },
                                        onOpenModes = { navigateToTab(ResolveRoutes.MODES) },
                                        onOpenAppLock = { navigateSafely(ResolveRoutes.APP_LOCK_SETUP) },
                                        onOpenDeviceAnalysis = { navigateSafely(ResolveRoutes.deviceAnalysis()) },
                                        onOpenAboutResolve = { navigateSafely(ResolveRoutes.ABOUT_RESOLVE) },
                                        onBack = popBackSafely
                                    )
                                }

                                composable(
                                    route = ResolveRoutes.DEVICE_ANALYSIS,
                                    arguments = listOf(
                                        navArgument("focusSection") {
                                            type = NavType.StringType
                                            defaultValue = ""
                                        }
                                    )
                                ) { entry ->
                                    val focusSection = entry.arguments?.getString("focusSection")
                                        ?.takeIf { it.isNotBlank() }
                                        ?: "summary"
                                    DeviceAnalysisScreen(
                                        report = deviceAnalysisReport,
                                        focusSection = focusSection,
                                        onRunAnalysis = { viewModel.runDeviceAnalysis() },
                                        onRefreshCapabilities = { viewModel.refreshCapabilities() },
                                        onExecuteAction = { viewModel.executeProtectionAction(it) },
                                        onOpenDeviceOwnerGuide = { showDeviceOwnerGuide = true },
                                        onBack = popBackSafely
                                    )
                                }

                                composable(ResolveRoutes.ABOUT_RESOLVE) {
                                    AboutResolveScreen(
                                        reducedMotion = reducedMotion,
                                        onBack = popBackSafely
                                    )
                                }

                                composable(ResolveRoutes.INSTANT_SETUP) {
                                    val instantAllowed = instantProfile.allowedPackages.ifEmpty { allowedPackages }
                                    InstantLockSetupScreen(
                                        duration = instantDuration,
                                        allowedPackages = instantAllowed,
                                        installedApps = installedApps,
                                        phoneAccessEnabled = phoneAccessEnabled,
                                        hasRecoveryCode = hasRecoveryCode,
                                        recoveryCodeLength = recoveryCodeLength,
                                        loadIcon = { viewModel.loadAppIcon(it) },
                                        generateRandomCode = { viewModel.generateRandomRecoveryCode(it) },
                                        onHoursChanged = { viewModel.setInstantHours(it) },
                                        onMinutesChanged = { viewModel.setInstantMinutes(it) },
                                        onDurationReplaced = { viewModel.updateInstantDuration(it) },
                                        onSaveAllowedPackages = {
                                            viewModel.saveProfileAllowedApps(LockMode.INSTANT, it)
                                        },
                                        onPhoneAccessChanged = { viewModel.setPhoneAccessEnabled(it) },
                                        onSaveRecoveryCode = { viewModel.saveRecoveryCode(it) },
                                        onStartInstantLock = { viewModel.startInstantLock() },
                                        onBack = popBackSafely,
                                        isAccountSignedIn = accountSyncState.isSignedIn,
                                        syncAcrossDevices = syncInstantLockToDevices,
                                        onSyncAcrossDevicesChanged = { viewModel.setSyncInstantLockToDevices(it) },
                                        isStrictMode = instantProfile.strictMode,
                                        allowSelectedApps = instantProfile.allowSelectedApps,
                                        showFlipClockShortcut = instantProfile.showFlipClockShortcut,
                                        onStrictModeChanged = {
                                            viewModel.setProfileStrictMode(LockMode.INSTANT, it)
                                        },
                                        onAllowSelectedAppsChanged = {
                                            viewModel.setProfileAllowSelectedApps(LockMode.INSTANT, it)
                                        },
                                        onShowFlipClockShortcutChanged = {
                                            viewModel.setProfileFlipClockShortcut(LockMode.INSTANT, it)
                                        }
                                    )
                                }

                                composable(ResolveRoutes.DELAYED_SETUP) {
                                    val delayedAllowed = delayedProfile.allowedPackages.ifEmpty { allowedPackages }
                                    DelayedLockSetupScreen(
                                        freeUseDuration = delayedFreeDuration,
                                        lockDuration = delayedLockDuration,
                                        allowedPackages = delayedAllowed,
                                        installedApps = installedApps,
                                        phoneAccessEnabled = phoneAccessEnabled,
                                        hasRecoveryCode = hasRecoveryCode,
                                        recoveryCodeLength = recoveryCodeLength,
                                        loadIcon = { viewModel.loadAppIcon(it) },
                                        generateRandomCode = { viewModel.generateRandomRecoveryCode(it) },
                                        onFreeUseHoursChanged = { viewModel.setDelayedFreeUseHours(it) },
                                        onFreeUseMinutesChanged = { viewModel.setDelayedFreeUseMinutes(it) },
                                        onFreeUseReplaced = { viewModel.updateDelayedFreeUse(it) },
                                        onLockHoursChanged = { viewModel.setDelayedLockHours(it) },
                                        onLockMinutesChanged = { viewModel.setDelayedLockMinutes(it) },
                                        onLockReplaced = { viewModel.updateDelayedLock(it) },
                                        onSaveAllowedPackages = {
                                            viewModel.saveProfileAllowedApps(LockMode.DELAYED, it)
                                        },
                                        onPhoneAccessChanged = { viewModel.setPhoneAccessEnabled(it) },
                                        onSaveRecoveryCode = { viewModel.saveRecoveryCode(it) },
                                        onStartDelayedLock = { viewModel.startDelayedLock() },
                                        onBack = popBackSafely,
                                        isAccountSignedIn = accountSyncState.isSignedIn,
                                        syncAcrossDevices = syncDelayedLockToDevices,
                                        onSyncAcrossDevicesChanged = { viewModel.setSyncDelayedLockToDevices(it) },
                                        presets = delayedPresets,
                                        onSavePreset = { id, name, free, lock ->
                                            viewModel.saveDelayedPreset(id, name, free, lock)
                                        },
                                        onDeletePreset = { id -> viewModel.deleteDelayedPreset(id) },
                                        isStrictMode = delayedProfile.strictMode,
                                        allowSelectedApps = delayedProfile.allowSelectedApps,
                                        showFlipClockShortcut = delayedProfile.showFlipClockShortcut,
                                        onStrictModeChanged = {
                                            viewModel.setProfileStrictMode(LockMode.DELAYED, it)
                                        },
                                        onAllowSelectedAppsChanged = {
                                            viewModel.setProfileAllowSelectedApps(LockMode.DELAYED, it)
                                        },
                                        onShowFlipClockShortcutChanged = {
                                            viewModel.setProfileFlipClockShortcut(LockMode.DELAYED, it)
                                        }
                                    )
                                }

                                composable(
                                    route = ResolveRoutes.SCHEDULE_SETUP,
                                    arguments = listOf(
                                        navArgument("scheduleId") {
                                            type = NavType.LongType
                                            defaultValue = -1L
                                        }
                                    )
                                ) { entry ->
                                    val scheduleId = entry.arguments?.getLong("scheduleId") ?: -1L
                                    val existing = schedules.firstOrNull { it.id == scheduleId }
                                    val scheduledAllowed = scheduledProfile.allowedPackages.ifEmpty { allowedPackages }
                                    ScheduleLockSetupScreen(
                                        existingSchedule = existing,
                                        duration = scheduleDuration,
                                        defaultAllowedPackages = scheduledAllowed,
                                        installedApps = installedApps,
                                        phoneAccessEnabled = phoneAccessEnabled,
                                        loadIcon = { viewModel.loadAppIcon(it) },
                                        onHoursChanged = { viewModel.setScheduleLockHours(it) },
                                        onMinutesChanged = { viewModel.setScheduleLockMinutes(it) },
                                        onDurationReplaced = { viewModel.updateScheduleLockDuration(it) },
                                        onSaveAllowedPackages = {
                                            viewModel.saveProfileAllowedApps(LockMode.SCHEDULED, it)
                                        },
                                        onSaveSchedule = { id, name, type, date, days, hour, minute, mins, pkgs, phone, syncDevices, strictMode, allowSelected, flipShortcut ->
                                            viewModel.saveSchedule(
                                                editingId = id,
                                                name = name,
                                                type = type,
                                                oneTimeDate = date,
                                                selectedDays = days,
                                                startHour = hour,
                                                startMinute = minute,
                                                durationMinutes = mins,
                                                allowedPackages = pkgs,
                                                phoneAccessEnabled = phone,
                                                syncAcrossDevices = syncDevices,
                                                isStrictMode = strictMode,
                                                allowSelectedApps = allowSelected,
                                                showFlipClockShortcut = flipShortcut
                                            )
                                        },
                                        onBack = popBackSafely,
                                        isAccountSignedIn = accountSyncState.isSignedIn,
                                        defaultStrictMode = scheduledProfile.strictMode,
                                        defaultAllowSelectedApps = scheduledProfile.allowSelectedApps,
                                        defaultShowFlipClockShortcut = scheduledProfile.showFlipClockShortcut
                                    )
                                }

                                composable(ResolveRoutes.APP_LOCK_SETUP) {
                                    BackHandler {
                                        viewModel.revokeAppLockManagementAccess()
                                        popBackSafely()
                                    }
                                    AppLockSetupScreen(
                                        appLockEnabled = appLockEnabled,
                                        hasCredential = hasAppLockCredential,
                                        configuredMethod = appLockMethod,
                                        configuredPinLength = appLockPinLength,
                                        protectedPackages = appLockPackages,
                                        installedApps = installedApps,
                                        managementUnlocked = appLockManagementUnlocked,
                                        capabilities = capabilities,
                                        loadIcon = { viewModel.loadAppIcon(it) },
                                        onVerifyManagementAccess = { input ->
                                            viewModel.verifyAppLockForManagement(input)
                                        },
                                        onToggleAppLockEnabled = { viewModel.setAppLockEnabled(it) },
                                        onSaveCredential = { method, pinLen, secret ->
                                            viewModel.saveAppLockConfig(method, pinLen, secret)
                                        },
                                        onSaveProtectedPackages = { viewModel.saveAppLockProtectedPackages(it) },
                                        onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                                        onOpenDeviceOwnerGuide = { showDeviceOwnerGuide = true },
                                        onBack = {
                                            viewModel.revokeAppLockManagementAccess()
                                            popBackSafely()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    ResolveNotificationHost(
                        controller = notificationController,
                        reducedMotion = reducedMotion,
                        bottomClearance = if (isTopLevelDestination && !useNavigationRail) 92.dp else 16.dp,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    if (showDeviceOwnerGuide) {
        DeviceOwnerGuideSheet(
            deviceOwnerState = capabilities.deviceOwnerState,
            onDismiss = { showDeviceOwnerGuide = false }
        )
    }
}

@Composable
private fun FloatingCapsuleNavDock(
    tabs: List<TopLevelTab>,
    currentRoute: String?,
    reducedMotion: Boolean,
    onSelectTab: (String) -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = if (palette.isDark) 8.dp else 4.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = Color.Black.copy(alpha = if (palette.isDark) 0.28f else 0.10f),
                    spotColor = Color.Black.copy(alpha = if (palette.isDark) 0.34f else 0.12f)
                )
                .clip(RoundedCornerShape(22.dp))
                .background(palette.surfaceSecondary.copy(alpha = 0.98f))
                .border(
                    1.dp,
                    palette.cardBorder,
                    RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .testTag("bottom_nav_dock"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                val pillColor by animateColorAsState(
                    targetValue = if (selected) {
                        palette.navPillContainer
                    } else {
                        Color.Transparent
                    },
                    animationSpec = ResolveMotion.fastTween(reducedMotion),
                    label = "nav_pill_color_${tab.route}"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (selected) palette.navPillContent else palette.textMuted,
                    animationSpec = ResolveMotion.fastTween(reducedMotion),
                    label = "nav_content_color_${tab.route}"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (selected && !reducedMotion) 1.08f else 1f,
                    animationSpec = if (reducedMotion) {
                        ResolveMotion.fastTween(true)
                    } else {
                        ResolveMotion.gentleSpring()
                    },
                    label = "nav_icon_scale_${tab.route}"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(pillColor)
                        .border(
                            width = if (selected) 1.dp else 0.dp,
                            color = if (selected) {
                                palette.navPillBorder
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable(
                            role = Role.Tab,
                            onClick = { onSelectTab(tab.route) }
                        )
                        .padding(horizontal = 6.dp, vertical = 7.dp)
                        .testTag("nav_tab_${tab.route}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                        tint = contentColor,
                        modifier = Modifier
                            .size(21.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            }
                    )
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = contentColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
