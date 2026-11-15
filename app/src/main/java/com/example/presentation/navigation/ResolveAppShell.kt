package com.example.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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

    var showDeviceOwnerGuide by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                UiEvent.NavigateBackToHome -> {
                    navController.popBackStack(ResolveRoutes.HOME, inclusive = false)
                }
            }
        }
    }

    val isTopLevelDestination = TOP_LEVEL_TABS.any { it.route == currentRoute }

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
                    Column {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = ResolveColors.ForestOutline.copy(alpha = 0.65f)
                        )
                        NavigationBar(
                            containerColor = ResolveColors.ForestSecondary
                        ) {
                            TOP_LEVEL_TABS.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ResolveColors.MintAccent,
                                        selectedTextColor = ResolveColors.MintAccent,
                                        indicatorColor = ResolveColors.MintSoftContainer,
                                        unselectedIconColor = ResolveColors.TextSecondary,
                                        unselectedTextColor = ResolveColors.TextSecondary
                                    ),
                                    modifier = Modifier.testTag("nav_tab_${tab.route}")
                                )
                            }
                        }
                    }
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
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
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
                        if (isSubScreen) {
                            slideInHorizontally(
                                animationSpec = tween(280, easing = FastOutSlowInEasing)
                            ) { it / 5 } + fadeIn(tween(240))
                        } else {
                            fadeIn(tween(220, easing = FastOutSlowInEasing))
                        }
                    },
                    exitTransition = {
                        fadeOut(tween(180, easing = FastOutSlowInEasing))
                    },
                    popEnterTransition = {
                        fadeIn(tween(220, easing = FastOutSlowInEasing))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                        ) { it / 5 } + fadeOut(tween(200))
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(ResolveRoutes.HOME) {
                        HomeScreen(
                            activeSession = activeSession,
                            remainingMillis = remainingMillis,
                            capabilities = capabilities,
                            onOpenInstantSetup = { navController.navigate(ResolveRoutes.INSTANT_SETUP) },
                            onOpenDelayedSetup = { navController.navigate(ResolveRoutes.DELAYED_SETUP) },
                            onOpenScheduleSetup = { navController.navigate(ResolveRoutes.scheduleSetup()) },
                            onOpenDeviceOwnerGuide = {
                                if (!capabilities.canEnforceRealLock) {
                                    navController.navigate(ResolveRoutes.SETTINGS) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
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
                            onAddSchedule = { navController.navigate(ResolveRoutes.scheduleSetup()) },
                            onEditSchedule = { schedule ->
                                viewModel.updateScheduleLockDuration(
                                    DurationSelection.fromTotalMinutes(schedule.lockDurationMinutes)
                                )
                                navController.navigate(ResolveRoutes.scheduleSetup(schedule.id))
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
                            onOpenNotificationSettings = { viewModel.openNotificationSettings() }
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
                            onBack = { navController.popBackStack() }
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
                            onBack = { navController.popBackStack() }
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
                            onBack = { navController.popBackStack() }
                        )
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
