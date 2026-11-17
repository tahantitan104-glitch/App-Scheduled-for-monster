package com.example.presentation.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.example.designsystem.ResolveColors
import com.example.domain.model.DurationSelection
import com.example.domain.startup.StartupStage
import com.example.presentation.ResolveViewModel
import com.example.presentation.UiEvent
import com.example.presentation.components.DeviceOwnerGuideSheet
import com.example.presentation.components.ResolveIcons
import com.example.presentation.history.HistoryScreen
import com.example.presentation.home.HomeScreen
import com.example.presentation.schedule.ScheduleScreen
import com.example.presentation.settings.SettingsScreen
import com.example.presentation.setup.DelayedLockSetupScreen
import com.example.presentation.setup.InstantLockSetupScreen
import com.example.presentation.setup.ScheduleLockSetupScreen
import com.example.presentation.startup.BrandIntroScreen
import com.example.presentation.startup.FirstTimeWelcomeScreen
import com.example.presentation.startup.RequiredSetupScreen

object ResolveRoutes {
    const val HOME = "home"
    const val SCHEDULE = "schedule"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val INSTANT_SETUP = "instant_setup"
    const val DELAYED_SETUP = "delayed_setup"
    const val SCHEDULE_SETUP = "schedule_setup?scheduleId={scheduleId}"

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
    TopLevelTab(ResolveRoutes.SCHEDULE, "Schedule", ResolveIcons.Event, ResolveIcons.Event),
    TopLevelTab(ResolveRoutes.HISTORY, "History", ResolveIcons.History, ResolveIcons.History),
    TopLevelTab(ResolveRoutes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun ResolveAppShell(viewModel: ResolveViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val remainingMillis by viewModel.remainingMillis.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
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
    val phoneAccessEnabled by viewModel.phoneAccessEnabled.collectAsStateWithLifecycle()
    val mascotPreferences by viewModel.mascotPreferences.collectAsStateWithLifecycle()
    val startupStage by viewModel.startupStage.collectAsStateWithLifecycle()
    val accountSyncState by viewModel.accountSyncState.collectAsStateWithLifecycle()
    val syncPromoDismissed by viewModel.syncPromoDismissed.collectAsStateWithLifecycle()
    val syncInstantLockToDevices by viewModel.syncInstantLockToDevices.collectAsStateWithLifecycle()
    val syncDelayedLockToDevices by viewModel.syncDelayedLockToDevices.collectAsStateWithLifecycle()

    var showDeviceOwnerGuide by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val reducedMotion = mascotPreferences.reducedMotionEnabled

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

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
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
            fadeIn(tween(240, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(tween(180, easing = FastOutSlowInEasing))
        },
        label = "startup_stage_transition"
    ) { stage ->
        when (stage) {
            StartupStage.RESOLVING,
            StartupStage.RESTORE_LOCK -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ResolveColors.ForestBrandDeep)
                )
            }

            StartupStage.BRAND_INTRO -> {
                BrandIntroScreen(
                    reducedMotion = mascotPreferences.reducedMotionEnabled,
                    onIntroCompleted = { viewModel.onBrandIntroCompleted() }
                )
            }

            StartupStage.FIRST_TIME_WELCOME -> {
                FirstTimeWelcomeScreen(
                    reducedMotion = mascotPreferences.reducedMotionEnabled,
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
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                    )
                }
            }

            StartupStage.HOME -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useNavigationRail = maxWidth >= 600.dp

                    Scaffold(
                        containerColor = ResolveColors.ForestDeep,
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                AnimatedVisibility(
                    visible = isTopLevelDestination && !useNavigationRail,
                    enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 2 },
                    exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 2 }
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
                    NavigationRail(containerColor = ResolveColors.ForestSecondary) {
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
                                    selectedIconColor = ResolveColors.MintAccent,
                                    selectedTextColor = ResolveColors.MintAccent,
                                    indicatorColor = ResolveColors.MintSoftContainer,
                                    unselectedIconColor = ResolveColors.TextSecondary,
                                    unselectedTextColor = ResolveColors.TextSecondary
                                )
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ResolveRoutes.HOME,
                    enterTransition = {
                        val isSubScreen = targetState.destination.route?.contains("setup") == true
                        when {
                            reducedMotion -> fadeIn(tween(120))
                            isSubScreen -> slideInHorizontally(
                                animationSpec = tween(260, easing = FastOutSlowInEasing)
                            ) { it / 4 } + fadeIn(tween(240))
                            else -> fadeIn(tween(230, easing = FastOutSlowInEasing)) +
                                scaleIn(
                                    initialScale = 0.975f,
                                    animationSpec = tween(230, easing = FastOutSlowInEasing)
                                )
                        }
                    },
                    exitTransition = {
                        if (reducedMotion) {
                            fadeOut(tween(90))
                        } else {
                            fadeOut(tween(170, easing = FastOutSlowInEasing))
                        }
                    },
                    popEnterTransition = {
                        if (reducedMotion) {
                            fadeIn(tween(120))
                        } else {
                            fadeIn(tween(230, easing = FastOutSlowInEasing))
                        }
                    },
                    popExitTransition = {
                        when {
                            reducedMotion -> fadeOut(tween(90))
                            else -> slideOutHorizontally(
                                animationSpec = tween(240, easing = FastOutSlowInEasing)
                            ) { it / 4 } + fadeOut(tween(190))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(ResolveRoutes.HOME) {
                        HomeScreen(
                            activeSession = activeSession,
                            remainingMillis = remainingMillis,
                            capabilities = capabilities,
                            mascotType = mascotPreferences.selectedMascot,
                            bunnyPeekEntranceSeen = mascotPreferences.bunnyPeekEntranceSeen,
                            onBunnyPeekEntranceSeen = { viewModel.markBunnyPeekEntranceSeen() },
                            reducedMotion = mascotPreferences.reducedMotionEnabled,
                            accountSyncState = accountSyncState,
                            syncPromoDismissed = syncPromoDismissed,
                            onConnectGoogleAccount = { viewModel.signInWithGoogle(context) },
                            onDismissSyncPromo = { viewModel.dismissSyncPromoBanner() },
                            onOpenAccountSettings = { navigateToTab(ResolveRoutes.SETTINGS) },
                            onOpenInstantSetup = { navigateSafely(ResolveRoutes.INSTANT_SETUP) },
                            onOpenDelayedSetup = { navigateSafely(ResolveRoutes.DELAYED_SETUP) },
                            onOpenScheduleSetup = { navigateSafely(ResolveRoutes.scheduleSetup()) },
                            onOpenDeviceOwnerGuide = {
                                if (!capabilities.canEnforceRealLock) {
                                    navigateToTab(ResolveRoutes.SETTINGS)
                                } else {
                                    showDeviceOwnerGuide = true
                                }
                            },
                            onReturnToLockScreen = { viewModel.openLockScreenIfLocked() },
                            onDismissTerminalSession = { viewModel.dismissTerminalSession() }
                        )
                    }

                    composable(ResolveRoutes.SCHEDULE) {
                        ScheduleScreen(
                            schedules = schedules,
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

                    composable(ResolveRoutes.HISTORY) {
                        HistoryScreen(records = history)
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
                            onClearProfilePhoto = { viewModel.clearProfilePhoto() }
                        )
                    }

                    composable(ResolveRoutes.INSTANT_SETUP) {
                        InstantLockSetupScreen(
                            duration = instantDuration,
                            allowedPackages = allowedPackages,
                            installedApps = installedApps,
                            phoneAccessEnabled = phoneAccessEnabled,
                            hasRecoveryCode = hasRecoveryCode,
                            recoveryCodeLength = recoveryCodeLength,
                            loadIcon = { viewModel.loadAppIcon(it) },
                            generateRandomCode = { viewModel.generateRandomRecoveryCode(it) },
                            onHoursChanged = { viewModel.setInstantHours(it) },
                            onMinutesChanged = { viewModel.setInstantMinutes(it) },
                            onDurationReplaced = { viewModel.updateInstantDuration(it) },
                            onSaveAllowedPackages = { viewModel.saveAllowedAppsSelection(it) },
                            onPhoneAccessChanged = { viewModel.setPhoneAccessEnabled(it) },
                            onSaveRecoveryCode = { viewModel.saveRecoveryCode(it) },
                            onStartInstantLock = { viewModel.startInstantLock() },
                            onBack = popBackSafely,
                            isAccountSignedIn = accountSyncState.isSignedIn,
                            syncAcrossDevices = syncInstantLockToDevices,
                            onSyncAcrossDevicesChanged = { viewModel.setSyncInstantLockToDevices(it) }
                        )
                    }

                    composable(ResolveRoutes.DELAYED_SETUP) {
                        DelayedLockSetupScreen(
                            freeUseDuration = delayedFreeDuration,
                            lockDuration = delayedLockDuration,
                            allowedPackages = allowedPackages,
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
                            onSaveAllowedPackages = { viewModel.saveAllowedAppsSelection(it) },
                            onPhoneAccessChanged = { viewModel.setPhoneAccessEnabled(it) },
                            onSaveRecoveryCode = { viewModel.saveRecoveryCode(it) },
                            onStartDelayedLock = { viewModel.startDelayedLock() },
                            onBack = popBackSafely,
                            isAccountSignedIn = accountSyncState.isSignedIn,
                            syncAcrossDevices = syncDelayedLockToDevices,
                            onSyncAcrossDevicesChanged = { viewModel.setSyncDelayedLockToDevices(it) }
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
                        ScheduleLockSetupScreen(
                            existingSchedule = existing,
                            duration = scheduleDuration,
                            defaultAllowedPackages = allowedPackages,
                            installedApps = installedApps,
                            phoneAccessEnabled = phoneAccessEnabled,
                            loadIcon = { viewModel.loadAppIcon(it) },
                            onHoursChanged = { viewModel.setScheduleLockHours(it) },
                            onMinutesChanged = { viewModel.setScheduleLockMinutes(it) },
                            onDurationReplaced = { viewModel.updateScheduleLockDuration(it) },
                            onSaveAllowedPackages = { viewModel.saveAllowedAppsSelection(it) },
                            onSaveSchedule = { id, name, type, date, days, hour, minute, mins, pkgs, phone ->
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
                                    phoneAccessEnabled = phone
                                )
                            },
                            onBack = popBackSafely
                        )
                    }
                }
            }
        }
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
                    elevation = 14.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = ResolveColors.MintAccent.copy(alpha = 0.16f),
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ResolveColors.ForestSurfaceElevated.copy(alpha = 0.96f),
                            ResolveColors.ForestSecondary.copy(alpha = 0.96f)
                        )
                    )
                )
                .border(
                    1.dp,
                    ResolveColors.MintAccent.copy(alpha = 0.20f),
                    RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                val pillColor by animateColorAsState(
                    targetValue = if (selected) {
                        ResolveColors.MintSoftContainer.copy(alpha = 0.92f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(durationMillis = if (reducedMotion) 90 else 220),
                    label = "nav_pill_color_${tab.route}"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (selected) ResolveColors.MintAccent else ResolveColors.TextSecondary,
                    animationSpec = tween(durationMillis = if (reducedMotion) 90 else 200),
                    label = "nav_content_color_${tab.route}"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (selected && !reducedMotion) 1.08f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "nav_icon_scale_${tab.route}"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(22.dp))
                        .background(pillColor)
                        .border(
                            width = if (selected) 1.dp else 0.dp,
                            color = if (selected) {
                                ResolveColors.MintAccent.copy(alpha = 0.28f)
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(22.dp)
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
