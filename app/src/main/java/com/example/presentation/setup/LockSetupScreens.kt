package com.example.presentation.setup

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.domain.model.DelayedLockPreset
import com.example.domain.model.DurationSelection
import com.example.domain.model.FocusProfile
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.LockSchedule
import com.example.domain.model.ScheduleType
import com.example.presentation.components.AllowedAppsPickerSheet
import com.example.presentation.components.CleanupTextInteractionsOnDispose
import com.example.presentation.components.DurationSelector
import com.example.presentation.components.FeatureTipNote
import com.example.presentation.components.RecoveryCodeSetupDialog
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.TactilePrimaryButton
import com.example.presentation.components.dismissTextInteractions
import com.example.presentation.components.resolveGlassCard
import com.example.presentation.components.resolveSwitchColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstantLockSetupScreen(
    duration: DurationSelection,
    allowedPackages: List<String>,
    installedApps: List<InstalledAppInfo>,
    phoneAccessEnabled: Boolean,
    hasRecoveryCode: Boolean,
    recoveryCodeLength: Int,
    loadIcon: suspend (String) -> ImageBitmap?,
    generateRandomCode: (Int) -> String,
    onHoursChanged: (Int) -> Unit,
    onMinutesChanged: (Int) -> Unit,
    onDurationReplaced: (DurationSelection) -> Unit,
    onSaveAllowedPackages: (Set<String>) -> Unit,
    onPhoneAccessChanged: (Boolean) -> Unit,
    onSaveRecoveryCode: (String) -> Unit,
    onStartInstantLock: () -> Unit,
    onBack: () -> Unit,
    isAccountSignedIn: Boolean = false,
    syncAcrossDevices: Boolean = false,
    onSyncAcrossDevicesChanged: (Boolean) -> Unit = {},
    isStrictMode: Boolean = false,
    allowSelectedApps: Boolean = true,
    showFlipClockShortcut: Boolean = false,
    onStrictModeChanged: (Boolean) -> Unit = {},
    onAllowSelectedAppsChanged: (Boolean) -> Unit = {},
    onShowFlipClockShortcutChanged: (Boolean) -> Unit = {},
    focusProfiles: List<FocusProfile> = FocusProfile.BUILT_IN_PRESETS,
    onApplyFocusProfile: (FocusProfile) -> Unit = {}
) {
    BackHandler(onBack = onBack)
    val palette = LocalResolveExtendedPalette.current
    var showAppsSheet by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    val installedPkgSet = remember(installedApps) { installedApps.mapTo(HashSet()) { it.packageName } }
    val missingAppsCount = remember(allowedPackages, installedPkgSet) {
        if (installedPkgSet.isEmpty()) 0 else allowedPackages.count { !installedPkgSet.contains(it) }
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            SetupTopBar(title = "Instant Lock", onBack = onBack)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxSize()
                    .testTag("instant_setup_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (focusProfiles.isNotEmpty()) {
                    item {
                        FocusProfilesQuickRow(
                            profiles = focusProfiles,
                            onSelectProfile = onApplyFocusProfile,
                            testTagPrefix = "instant_profile"
                        )
                    }
                }

                item {
                    DurationSelector(
                        label = "Lock Duration",
                        duration = duration,
                        onHoursChanged = onHoursChanged,
                        onMinutesChanged = onMinutesChanged,
                        onDurationReplaced = onDurationReplaced,
                        testTagPrefix = "instant_duration"
                    )
                }

                item {
                    SetupSwitchRow(
                        title = "Allow Selected Apps",
                        subtitle = if (allowSelectedApps) {
                            "Selected apps stay accessible during Lock Mode"
                        } else {
                            "Paused (${allowedPackages.size} saved apps preserved)"
                        },
                        icon = ResolveIcons.Apps,
                        checked = allowSelectedApps,
                        onCheckedChange = onAllowSelectedAppsChanged,
                        testTag = "instant_allow_selected_toggle"
                    )
                }

                item {
                    SetupActionRow(
                        title = "Allowed Applications",
                        subtitle = when {
                            !allowSelectedApps && allowedPackages.isEmpty() -> "Paused — only Resolve & Phone (if enabled)"
                            !allowSelectedApps -> "Paused — ${allowedPackages.size} saved (tap to manage)"
                            allowedPackages.isEmpty() -> "Only Resolve & Phone (if enabled)"
                            missingAppsCount > 0 -> "${allowedPackages.size} apps allowed ($missingAppsCount not installed here)"
                            else -> "${allowedPackages.size} apps allowed during Lock Mode"
                        },
                        icon = ResolveIcons.Apps,
                        onClick = { showAppsSheet = true },
                        testTag = "instant_allowed_apps_row"
                    )
                }

                item {
                    SetupSwitchRow(
                        title = "Phone Call Access",
                        subtitle = "Allow incoming calls and system Dialer during Lock Mode",
                        icon = Icons.Outlined.Call,
                        checked = phoneAccessEnabled,
                        onCheckedChange = onPhoneAccessChanged,
                        testTag = "instant_phone_switch"
                    )
                }

                item {
                    SetupSwitchRow(
                        title = "Show Flip Clock Shortcut",
                        subtitle = "Show Flip Clock shortcut button in Lock Screen dock",
                        icon = ResolveIcons.FlipClock,
                        checked = showFlipClockShortcut,
                        onCheckedChange = onShowFlipClockShortcutChanged,
                        testTag = "instant_flip_shortcut_toggle"
                    )
                }

                item {
                    RecoveryWithStrictRow(
                        hasRecoveryCode = hasRecoveryCode,
                        recoveryCodeLength = recoveryCodeLength,
                        isStrictMode = isStrictMode,
                        onRecoveryClick = { showRecoveryDialog = true },
                        onStrictModeChanged = onStrictModeChanged,
                        recoveryTestTag = "instant_recovery_row",
                        strictTestTag = "instant_strict_toggle"
                    )
                    Spacer(Modifier.height(4.dp))
                    FeatureTipNote(
                        text = when {
                            isStrictMode -> "Strict Mode ON: Recovery exit is disabled until timer finishes."
                            hasRecoveryCode -> "Tip: Allowed apps & Phone Dialer stay accessible on Lock Screen."
                            else -> "Note: Configure a 16-digit code first or turn on Strict Mode."
                        },
                        icon = Icons.Outlined.Info
                    )
                }

                if (isAccountSignedIn) {
                    item {
                        SyncTargetScopeCard(
                            syncAcrossDevices = syncAcrossDevices,
                            onSyncAcrossDevicesChanged = onSyncAcrossDevicesChanged,
                            switchTestTag = "instant_sync_switch",
                            scopePrefix = "instant"
                        )
                    }
                }

                item {
                    ReviewSummaryCard(
                        lines = buildList {
                            add("Mode: Instant Lock (restricts immediately)")
                            add("Lock Duration: ${duration.formattedCompact()}")
                            add("Strict Mode: ${if (isStrictMode) "ON (no recovery exit)" else "OFF"}")
                            add(
                                "Allowed Apps: " + if (allowSelectedApps) {
                                    "${allowedPackages.size} selected"
                                } else {
                                    "Disabled (${allowedPackages.size} saved)"
                                }
                            )
                            add("Phone Calls: ${if (phoneAccessEnabled) "Enabled" else "Disabled"}")
                            add("Flip Clock Shortcut: ${if (showFlipClockShortcut) "Shown" else "Hidden"}")
                            if (isAccountSignedIn) {
                                add("Device Sync: ${if (syncAcrossDevices) "All Linked Devices" else "This Phone Only"}")
                            }
                        }
                    )
                }

                item {
                    TactilePrimaryButton(
                        onClick = onStartInstantLock,
                        enabled = duration.isValid,
                        modifier = Modifier.testTag("start_instant_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Start Instant Lock",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showAppsSheet) {
        AllowedAppsPickerSheet(
            installedApps = installedApps,
            initialSelectedPackages = allowedPackages.toSet(),
            loadIcon = loadIcon,
            onDismiss = { showAppsSheet = false },
            onSaveSelection = {
                onSaveAllowedPackages(it)
                showAppsSheet = false
            },
            focusProfiles = focusProfiles,
            onApplyFocusProfile = onApplyFocusProfile
        )
    }

    if (showRecoveryDialog) {
        RecoveryCodeSetupDialog(
            generateRandomCode = generateRandomCode,
            onDismiss = { showRecoveryDialog = false },
            onSaveCode = { code ->
                onSaveRecoveryCode(code)
                showRecoveryDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DelayedLockSetupScreen(
    freeUseDuration: DurationSelection,
    lockDuration: DurationSelection,
    allowedPackages: List<String>,
    installedApps: List<InstalledAppInfo>,
    phoneAccessEnabled: Boolean,
    hasRecoveryCode: Boolean,
    recoveryCodeLength: Int,
    loadIcon: suspend (String) -> ImageBitmap?,
    generateRandomCode: (Int) -> String,
    onFreeUseHoursChanged: (Int) -> Unit,
    onFreeUseMinutesChanged: (Int) -> Unit,
    onFreeUseReplaced: (DurationSelection) -> Unit,
    onLockHoursChanged: (Int) -> Unit,
    onLockMinutesChanged: (Int) -> Unit,
    onLockReplaced: (DurationSelection) -> Unit,
    onSaveAllowedPackages: (Set<String>) -> Unit,
    onPhoneAccessChanged: (Boolean) -> Unit,
    onSaveRecoveryCode: (String) -> Unit,
    onStartDelayedLock: () -> Unit,
    onBack: () -> Unit,
    isAccountSignedIn: Boolean = false,
    syncAcrossDevices: Boolean = false,
    onSyncAcrossDevicesChanged: (Boolean) -> Unit = {},
    presets: List<DelayedLockPreset> = emptyList(),
    onSavePreset: (presetId: String?, name: String, freeUseMinutes: Long, lockMinutes: Long) -> Unit = { _, _, _, _ -> },
    onDeletePreset: (presetId: String) -> Unit = {},
    isStrictMode: Boolean = false,
    allowSelectedApps: Boolean = true,
    showFlipClockShortcut: Boolean = false,
    onStrictModeChanged: (Boolean) -> Unit = {},
    onAllowSelectedAppsChanged: (Boolean) -> Unit = {},
    onShowFlipClockShortcutChanged: (Boolean) -> Unit = {},
    focusProfiles: List<FocusProfile> = FocusProfile.BUILT_IN_PRESETS,
    onApplyFocusProfile: (FocusProfile) -> Unit = {}
) {
    BackHandler(onBack = onBack)
    val palette = LocalResolveExtendedPalette.current
    var showAppsSheet by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var showPresetEditor by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<DelayedLockPreset?>(null) }
    val installedPkgSet = remember(installedApps) { installedApps.mapTo(HashSet()) { it.packageName } }
    val missingAppsCount = remember(allowedPackages, installedPkgSet) {
        if (installedPkgSet.isEmpty()) 0 else allowedPackages.count { !installedPkgSet.contains(it) }
    }

    val matchingPresetId = presets.firstOrNull {
        it.freeUseMinutes == freeUseDuration.totalMinutes &&
            it.lockDurationMinutes == lockDuration.totalMinutes
    }?.presetId

    Scaffold(
        containerColor = palette.background,
        topBar = {
            SetupTopBar(title = "Delayed Lock", onBack = onBack)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    DelayedPresetsSection(
                        presets = presets,
                        selectedPresetId = matchingPresetId,
                        onSelectPreset = { preset ->
                            onFreeUseReplaced(DurationSelection.fromTotalMinutes(preset.freeUseMinutes))
                            onLockReplaced(DurationSelection.fromTotalMinutes(preset.lockDurationMinutes))
                        },
                        onCreatePresetClick = {
                            editingPreset = null
                            showPresetEditor = true
                        },
                        onEditPresetClick = { preset ->
                            editingPreset = preset
                            showPresetEditor = true
                        },
                        onDeletePresetClick = onDeletePreset
                    )
                }

                item {
                    DurationSelector(
                        label = "1. Free Use Window (Unrestricted)",
                        duration = freeUseDuration,
                        onHoursChanged = onFreeUseHoursChanged,
                        onMinutesChanged = onFreeUseMinutesChanged,
                        onDurationReplaced = onFreeUseReplaced,
                        testTagPrefix = "delayed_free_duration"
                    )
                }

                item {
                    DurationSelector(
                        label = "2. Lock Duration (After Window Ends)",
                        duration = lockDuration,
                        onHoursChanged = onLockHoursChanged,
                        onMinutesChanged = onLockMinutesChanged,
                        onDurationReplaced = onLockReplaced,
                        testTagPrefix = "delayed_lock_duration"
                    )
                    Spacer(Modifier.height(4.dp))
                    FeatureTipNote(
                        text = "Tip: Lock Duration begins full-length right when Free Use ends.",
                        icon = ResolveIcons.HourglassTop
                    )
                }

                item {
                    SetupSwitchRow(
                        title = "Allow Selected Apps",
                        subtitle = if (allowSelectedApps) {
                            "Selected apps stay accessible once Lock Mode starts"
                        } else {
                            "Paused (${allowedPackages.size} saved apps preserved)"
                        },
                        icon = ResolveIcons.Apps,
                        checked = allowSelectedApps,
                        onCheckedChange = onAllowSelectedAppsChanged,
                        testTag = "delayed_allow_selected_toggle"
                    )
                }

                item {
                    SetupActionRow(
                        title = "Allowed Apps During Lock",
                        subtitle = when {
                            !allowSelectedApps && allowedPackages.isEmpty() -> "Paused — only applies once Free Use Window expires"
                            !allowSelectedApps -> "Paused — ${allowedPackages.size} saved (tap to manage)"
                            allowedPackages.isEmpty() -> "Only applies once Free Use Window expires"
                            missingAppsCount > 0 -> "${allowedPackages.size} apps allowed ($missingAppsCount not installed here)"
                            else -> "${allowedPackages.size} apps allowed during Lock Mode"
                        },
                        icon = ResolveIcons.Apps,
                        onClick = { showAppsSheet = true },
                        testTag = "delayed_allowed_apps_row"
                    )
                }

                item {
                    SetupSwitchRow(
                        title = "Phone Call Access",
                        subtitle = "Retain incoming calls and Dialer during Lock Mode",
                        icon = Icons.Outlined.Call,
                        checked = phoneAccessEnabled,
                        onCheckedChange = onPhoneAccessChanged,
                        testTag = "delayed_phone_switch"
                    )
                }

                item {
                    SetupSwitchRow(
                        title = "Show Flip Clock Shortcut",
                        subtitle = "Show Flip Clock shortcut button in Lock Screen dock",
                        icon = ResolveIcons.FlipClock,
                        checked = showFlipClockShortcut,
                        onCheckedChange = onShowFlipClockShortcutChanged,
                        testTag = "delayed_flip_shortcut_toggle"
                    )
                }

                item {
                    RecoveryWithStrictRow(
                        hasRecoveryCode = hasRecoveryCode,
                        recoveryCodeLength = recoveryCodeLength,
                        isStrictMode = isStrictMode,
                        onRecoveryClick = { showRecoveryDialog = true },
                        onStrictModeChanged = onStrictModeChanged,
                        recoveryTestTag = "delayed_recovery_row",
                        strictTestTag = "delayed_strict_toggle"
                    )
                }

                if (isAccountSignedIn) {
                    item {
                        SyncTargetScopeCard(
                            syncAcrossDevices = syncAcrossDevices,
                            onSyncAcrossDevicesChanged = onSyncAcrossDevicesChanged,
                            switchTestTag = "delayed_sync_switch",
                            scopePrefix = "delayed"
                        )
                    }
                }

                item {
                    ReviewSummaryCard(
                        lines = buildList {
                            add("Free Use Window: ${freeUseDuration.formattedCompact()} (all apps open)")
                            add("Automatic Lock After: ${freeUseDuration.formattedCompact()}")
                            add("Lock Duration: ${lockDuration.formattedCompact()} (starts when locked)")
                            add("Strict Mode: ${if (isStrictMode) "ON (no recovery exit)" else "OFF"}")
                            add(
                                "Allowed Apps During Lock: " + if (allowSelectedApps) {
                                    "${allowedPackages.size} selected"
                                } else {
                                    "Disabled (${allowedPackages.size} saved)"
                                }
                            )
                            add("Flip Clock Shortcut: ${if (showFlipClockShortcut) "Shown" else "Hidden"}")
                            if (isAccountSignedIn) {
                                add("Device Sync: ${if (syncAcrossDevices) "All Linked Devices" else "This Phone Only"}")
                            }
                        }
                    )
                }

                item {
                    TactilePrimaryButton(
                        onClick = onStartDelayedLock,
                        enabled = freeUseDuration.isValid && lockDuration.isValid,
                        modifier = Modifier.testTag("start_delayed_lock_button")
                    ) {
                        Text(
                            text = "Start Delayed Lock",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showAppsSheet) {
        AllowedAppsPickerSheet(
            installedApps = installedApps,
            initialSelectedPackages = allowedPackages.toSet(),
            loadIcon = loadIcon,
            onDismiss = { showAppsSheet = false },
            onSaveSelection = {
                onSaveAllowedPackages(it)
                showAppsSheet = false
            },
            focusProfiles = focusProfiles,
            onApplyFocusProfile = onApplyFocusProfile
        )
    }

    if (showRecoveryDialog) {
        RecoveryCodeSetupDialog(
            generateRandomCode = generateRandomCode,
            onDismiss = { showRecoveryDialog = false },
            onSaveCode = { code ->
                onSaveRecoveryCode(code)
                showRecoveryDialog = false
            }
        )
    }

    if (showPresetEditor) {
        DelayedPresetEditorDialog(
            existing = editingPreset,
            initialFreeUseMinutes = editingPreset?.freeUseMinutes ?: freeUseDuration.totalMinutes,
            initialLockMinutes = editingPreset?.lockDurationMinutes ?: lockDuration.totalMinutes,
            onDismiss = { showPresetEditor = false },
            onSave = { name, freeMins, lockMins ->
                onSavePreset(editingPreset?.presetId, name, freeMins, lockMins)
                showPresetEditor = false
            }
        )
    }
}

@Composable
private fun DelayedPresetsSection(
    presets: List<DelayedLockPreset>,
    selectedPresetId: String?,
    onSelectPreset: (DelayedLockPreset) -> Unit,
    onCreatePresetClick: () -> Unit,
    onEditPresetClick: (DelayedLockPreset) -> Unit,
    onDeletePresetClick: (String) -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(cornerRadius = 20.dp)
            .padding(16.dp)
            .testTag("delayed_presets_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary
                )
                Text(
                    text = "Tap a preset to apply durations cleanly without affecting active locks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
            }
            OutlinedButton(
                onClick = onCreatePresetClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = palette.accent,
                    contentColor = palette.onAccent
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.accent),
                modifier = Modifier.testTag("delayed_new_preset_button")
            ) {
                Text(
                    text = "+ New",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                val isSelected = preset.presetId == selectedPresetId
                val freeStr = DurationSelection.fromTotalMinutes(preset.freeUseMinutes).formattedCompact()
                val lockStr = DurationSelection.fromTotalMinutes(preset.lockDurationMinutes).formattedCompact()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) palette.surfaceElevated
                            else palette.surfaceSecondary
                        )
                        .border(
                            1.dp,
                            if (isSelected) palette.accentSecondary else palette.cardBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectPreset(preset) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("delayed_preset_item_${preset.presetId}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (preset.builtIn) {
                                Text(
                                    text = "Built-in",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.onAccent,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(palette.accent)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Free $freeStr -> Lock $lockStr",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = palette.textSecondary
                        )
                    }

                    if (!preset.builtIn) {
                        IconButton(
                            onClick = { onEditPresetClick(preset) },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("delayed_preset_edit_${preset.presetId}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit preset ${preset.name}",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { onDeletePresetClick(preset.presetId) },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("delayed_preset_delete_${preset.presetId}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete preset ${preset.name}",
                                tint = palette.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DelayedPresetEditorDialog(
    existing: DelayedLockPreset?,
    initialFreeUseMinutes: Long,
    initialLockMinutes: Long,
    onDismiss: () -> Unit,
    onSave: (name: String, freeUseMinutes: Long, lockMinutes: Long) -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var freeMinutesText by remember(existing) { mutableStateOf(initialFreeUseMinutes.coerceAtLeast(1L).toString()) }
    var lockMinutesText by remember(existing) { mutableStateOf(initialLockMinutes.coerceAtLeast(1L).toString()) }
    val freeVal = freeMinutesText.toLongOrNull()?.coerceIn(1L, 1440L)
    val lockVal = lockMinutesText.toLongOrNull()?.coerceIn(1L, 1440L)
    val isValid = name.trim().isNotEmpty() && freeVal != null && lockVal != null

    CleanupTextInteractionsOnDispose()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceSecondary,
        title = {
            Text(
                text = if (existing == null) "New Delayed Preset" else "Edit Preset",
                style = MaterialTheme.typography.titleLarge,
                color = palette.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 48) name = it },
                    label = { Text("Preset Name") },
                    placeholder = { Text("e.g. Evening Study") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preset_name_input")
                )
                OutlinedTextField(
                    value = freeMinutesText,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) freeMinutesText = it },
                    label = { Text("Free Use Duration (minutes)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preset_free_minutes_input")
                )
                OutlinedTextField(
                    value = lockMinutesText,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) lockMinutesText = it },
                    label = { Text("Lock Duration (minutes)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preset_lock_minutes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val safeFree = freeVal
                    val safeLock = lockVal
                    if (name.trim().isNotEmpty() && safeFree != null && safeLock != null) {
                        onSave(name.trim(), safeFree, safeLock)
                    }
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primaryButton,
                    contentColor = palette.onPrimaryButton
                ),
                modifier = Modifier.testTag("preset_save_button")
            ) {
                Text("Save Preset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.textSecondary)
            }
        }
    )
}

@Composable
private fun FocusProfilesQuickRow(
    profiles: List<FocusProfile>,
    onSelectProfile: (FocusProfile) -> Unit,
    testTagPrefix: String
) {
    val palette = LocalResolveExtendedPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "FOCUS PROFILES",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = palette.textSecondary
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(profiles, key = { it.profileId }) { profile ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surfaceSecondary)
                        .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button) { onSelectProfile(profile) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("${testTagPrefix}_${profile.profileId}"),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = palette.accentStrong
                    )
                    Text(
                        text = profile.subtitle.ifBlank { "${profile.defaultDurationMinutes}m" },
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncTargetScopeCard(
    syncAcrossDevices: Boolean,
    onSyncAcrossDevicesChanged: (Boolean) -> Unit,
    switchTestTag: String,
    scopePrefix: String
) {
    val palette = LocalResolveExtendedPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(
                cornerRadius = 16.dp,
                borderColor = if (syncAcrossDevices) palette.accentSecondary.copy(alpha = 0.45f) else palette.cardBorder
            )
            .clickable { onSyncAcrossDevicesChanged(!syncAcrossDevices) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("${scopePrefix}_sync_compact_card"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (syncAcrossDevices) palette.accent else palette.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ResolveIcons.Sync,
                contentDescription = "Sync across linked devices",
                tint = if (syncAcrossDevices) palette.onAccent else palette.textSecondary,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Sync to Linked Devices",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = palette.textPrimary
            )
            Text(
                text = if (syncAcrossDevices) "All linked devices" else "This device only",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        }
        Switch(
            checked = syncAcrossDevices,
            onCheckedChange = onSyncAcrossDevicesChanged,
            colors = resolveSwitchColors(),
            modifier = Modifier.testTag(switchTestTag)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleLockSetupScreen(
    existingSchedule: LockSchedule?,
    duration: DurationSelection,
    defaultAllowedPackages: List<String>,
    installedApps: List<InstalledAppInfo>,
    phoneAccessEnabled: Boolean,
    loadIcon: suspend (String) -> ImageBitmap?,
    onHoursChanged: (Int) -> Unit,
    onMinutesChanged: (Int) -> Unit,
    onDurationReplaced: (DurationSelection) -> Unit,
    onSaveAllowedPackages: (Set<String>) -> Unit,
    onSaveSchedule: (
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
        syncAcrossDevices: Boolean,
        isStrictMode: Boolean,
        allowSelectedApps: Boolean,
        showFlipClockShortcut: Boolean
    ) -> Unit,
    onBack: () -> Unit,
    isAccountSignedIn: Boolean = false,
    defaultStrictMode: Boolean = false,
    defaultAllowSelectedApps: Boolean = true,
    defaultShowFlipClockShortcut: Boolean = false,
    focusProfiles: List<FocusProfile> = FocusProfile.BUILT_IN_PRESETS,
    onApplyFocusProfile: (FocusProfile) -> Unit = {}
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var name by remember(existingSchedule) { mutableStateOf(existingSchedule?.name ?: "") }
    var scheduleType by remember(existingSchedule) {
        mutableStateOf(existingSchedule?.type ?: ScheduleType.DAILY)
    }
    var oneTimeDate by remember(existingSchedule) {
        mutableStateOf(
            existingSchedule?.oneTimeDateEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
        )
    }
    var selectedDays by remember(existingSchedule) {
        mutableStateOf(
            existingSchedule?.selectedDays()?.takeIf { it.isNotEmpty() }
                ?: setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        )
    }
    val initialTime = remember { LocalTime.now().plusMinutes(5) }
    var startHour by remember(existingSchedule) {
        mutableIntStateOf(existingSchedule?.startHour ?: initialTime.hour)
    }
    var startMinute by remember(existingSchedule) {
        mutableIntStateOf(existingSchedule?.startMinute ?: initialTime.minute)
    }
    var schedulePackages by remember(existingSchedule, defaultAllowedPackages) {
        mutableStateOf(existingSchedule?.allowedPackages ?: defaultAllowedPackages)
    }
    var schedulePhoneAccess by remember(existingSchedule, phoneAccessEnabled) {
        mutableStateOf(existingSchedule?.phoneAccessEnabled ?: phoneAccessEnabled)
    }
    var scheduleSyncAcrossDevices by remember(existingSchedule) {
        mutableStateOf(existingSchedule?.syncAcrossDevices ?: false)
    }
    var scheduleStrictMode by remember(existingSchedule, defaultStrictMode) {
        mutableStateOf(existingSchedule?.isStrictMode ?: defaultStrictMode)
    }
    var scheduleAllowSelectedApps by remember(existingSchedule, defaultAllowSelectedApps) {
        mutableStateOf(existingSchedule?.allowSelectedApps ?: defaultAllowSelectedApps)
    }
    var scheduleShowFlipClockShortcut by remember(existingSchedule, defaultShowFlipClockShortcut) {
        mutableStateOf(existingSchedule?.showFlipClockShortcut ?: defaultShowFlipClockShortcut)
    }
    var showAppsSheet by remember { mutableStateOf(false) }
    val installedPkgSet = remember(installedApps) { installedApps.mapTo(HashSet()) { it.packageName } }
    val missingAppsCount = remember(schedulePackages, installedPkgSet) {
        if (installedPkgSet.isEmpty()) 0 else schedulePackages.count { !installedPkgSet.contains(it) }
    }

    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    val keyboardController = LocalSoftwareKeyboardController.current
    CleanupTextInteractionsOnDispose()

    val palette = LocalResolveExtendedPalette.current
    Scaffold(
        containerColor = palette.background,
        topBar = {
            SetupTopBar(
                title = if (existingSchedule != null) "Edit Schedule" else "Schedule Lock",
                onBack = {
                    dismissTextInteractions(focusManager, textToolbar, keyboardController)
                    onBack()
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Schedule Label (optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("schedule_name_input")
                    )
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(cornerRadius = 16.dp)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Schedule Type",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                ScheduleType.ONE_TIME to "One-Time",
                                ScheduleType.DAILY to "Daily",
                                ScheduleType.CUSTOM_WEEKLY to "Weekly"
                            ).forEach { (type, label) ->
                                val selectedType = scheduleType == type
                                FilterChip(
                                    selected = selectedType,
                                    onClick = { scheduleType = type },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = palette.accent,
                                        selectedLabelColor = palette.onAccent,
                                        containerColor = palette.surfaceSecondary,
                                        labelColor = palette.textSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selectedType,
                                        borderColor = palette.cardBorder,
                                        selectedBorderColor = palette.accent
                                    ),
                                    modifier = Modifier.testTag("schedule_type_${type.name}")
                                )
                            }
                        }

                        if (scheduleType == ScheduleType.ONE_TIME) {
                            SetupActionRow(
                                title = "Start Date",
                                subtitle = oneTimeDate.toString(),
                                icon = ResolveIcons.CalendarToday,
                                onClick = {
                                    dismissTextInteractions(focusManager, textToolbar, keyboardController)
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d -> oneTimeDate = LocalDate.of(y, m + 1, d) },
                                        oneTimeDate.year,
                                        oneTimeDate.monthValue - 1,
                                        oneTimeDate.dayOfMonth
                                    ).show()
                                },
                                testTag = "schedule_date_picker"
                            )
                        }

                        if (scheduleType == ScheduleType.CUSTOM_WEEKLY) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DayOfWeek.entries.forEach { day ->
                                    val selected = selectedDays.contains(day)
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            selectedDays = if (selected) selectedDays - day else selectedDays + day
                                        },
                                        label = {
                                            Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = palette.accent,
                                            selectedLabelColor = palette.onAccent,
                                            containerColor = palette.surfaceSecondary,
                                            labelColor = palette.textSecondary
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = selected,
                                            borderColor = palette.cardBorder,
                                            selectedBorderColor = palette.accent
                                        )
                                    )
                                }
                            }
                        }

                        SetupActionRow(
                            title = "Start Time",
                            subtitle = "%02d:%02d".format(startHour, startMinute),
                            icon = ResolveIcons.Schedule,
                            onClick = {
                                dismissTextInteractions(focusManager, textToolbar, keyboardController)
                                TimePickerDialog(
                                    context,
                                    { _, h, m ->
                                        startHour = h
                                        startMinute = m
                                    },
                                    startHour,
                                    startMinute,
                                    true
                                ).show()
                            },
                            testTag = "schedule_time_picker"
                        )
                        FeatureTipNote(
                            text = "Tip: Overlapping times with active schedules are prevented automatically.",
                            icon = ResolveIcons.EventAvailable
                        )
                    }
                }

                item {
                    DurationSelector(
                        label = "Lock Duration",
                        duration = duration,
                        onHoursChanged = onHoursChanged,
                        onMinutesChanged = onMinutesChanged,
                        onDurationReplaced = onDurationReplaced,
                        testTagPrefix = "schedule_duration"
                    )
                }

                item {
                    SetupSwitchRow(
                        title = "Allow Selected Apps",
                        subtitle = if (scheduleAllowSelectedApps) {
                            "Selected apps stay accessible during scheduled lock"
                        } else {
                            "Paused (${schedulePackages.size} saved apps preserved)"
                        },
                        icon = ResolveIcons.Apps,
                        checked = scheduleAllowSelectedApps,
                        onCheckedChange = { scheduleAllowSelectedApps = it },
                        testTag = "schedule_allow_selected_toggle"
                    )
                }

                item {
                    SetupActionRow(
                        title = "Allowed Applications",
                        subtitle = when {
                            !scheduleAllowSelectedApps && schedulePackages.isEmpty() -> "Paused — only Resolve & Phone (if enabled)"
                            !scheduleAllowSelectedApps -> "Paused — ${schedulePackages.size} saved (tap to manage)"
                            missingAppsCount > 0 -> "${schedulePackages.size} apps allowed ($missingAppsCount not installed here)"
                            else -> "${schedulePackages.size} apps allowed during scheduled lock"
                        },
                        icon = ResolveIcons.Apps,
                        onClick = { showAppsSheet = true },
                        testTag = "schedule_allowed_apps_row"
                    )
                }

                item {
                    SetupSwitchRow(
                        title = "Phone Call Access",
                        subtitle = "Retain incoming calls and system Dialer",
                        icon = Icons.Outlined.Call,
                        checked = schedulePhoneAccess,
                        onCheckedChange = { schedulePhoneAccess = it },
                        testTag = "schedule_phone_switch"
                    )
                }

                item {
                    SetupSwitchRow(
                        title = "Show Flip Clock Shortcut",
                        subtitle = "Show Flip Clock shortcut button in Lock Screen dock",
                        icon = ResolveIcons.FlipClock,
                        checked = scheduleShowFlipClockShortcut,
                        onCheckedChange = { scheduleShowFlipClockShortcut = it },
                        testTag = "schedule_flip_shortcut_toggle"
                    )
                }

                item {
                    ScheduleStrictModeRow(
                        isStrictMode = scheduleStrictMode,
                        onStrictModeChanged = { enabled ->
                            scheduleStrictMode = enabled
                            if (enabled) {
                                scheduleAllowSelectedApps = false
                            }
                        },
                        strictTestTag = "schedule_strict_toggle"
                    )
                }

                if (isAccountSignedIn) {
                    item {
                        SyncTargetScopeCard(
                            syncAcrossDevices = scheduleSyncAcrossDevices,
                            onSyncAcrossDevicesChanged = { scheduleSyncAcrossDevices = it },
                            switchTestTag = "schedule_sync_switch",
                            scopePrefix = "schedule"
                        )
                    }
                }

                item {
                    TactilePrimaryButton(
                        onClick = {
                            dismissTextInteractions(focusManager, textToolbar, keyboardController)
                            onSaveSchedule(
                                existingSchedule?.id,
                                name,
                                scheduleType,
                                oneTimeDate,
                                selectedDays,
                                startHour,
                                startMinute,
                                duration.totalMinutes,
                                schedulePackages,
                                schedulePhoneAccess,
                                isAccountSignedIn && scheduleSyncAcrossDevices,
                                scheduleStrictMode,
                                scheduleAllowSelectedApps,
                                scheduleShowFlipClockShortcut
                            )
                        },
                        enabled = duration.isValid,
                        modifier = Modifier.testTag("save_schedule_button")
                    ) {
                        Text(
                            text = if (existingSchedule != null) "Update Schedule" else "Enable Schedule",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showAppsSheet) {
        AllowedAppsPickerSheet(
            installedApps = installedApps,
            initialSelectedPackages = schedulePackages.toSet(),
            loadIcon = loadIcon,
            onDismiss = { showAppsSheet = false },
            onSaveSelection = { selected ->
                schedulePackages = selected.toList()
                onSaveAllowedPackages(selected)
                showAppsSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupTopBar(title: String, onBack: () -> Unit) {
    val palette = LocalResolveExtendedPalette.current
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("setup_back_button")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = palette.textPrimary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background)
    )
}

@Composable
private fun SetupActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalResolveExtendedPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(cornerRadius = 16.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(palette.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = palette.textMuted
        )
    }
}

@Composable
private fun SetupSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val palette = LocalResolveExtendedPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(cornerRadius = 16.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(palette.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = resolveSwitchColors(),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun RecoveryWithStrictRow(
    hasRecoveryCode: Boolean,
    recoveryCodeLength: Int,
    isStrictMode: Boolean,
    onRecoveryClick: () -> Unit,
    onStrictModeChanged: (Boolean) -> Unit,
    recoveryTestTag: String,
    strictTestTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            SetupActionRow(
                title = "Recovery Code",
                subtitle = when {
                    isStrictMode -> "Disabled in Strict Mode"
                    hasRecoveryCode -> "$recoveryCodeLength-digit verifier configured"
                    else -> "Required unless Strict Mode is ON"
                },
                icon = ResolveIcons.Key,
                onClick = onRecoveryClick,
                testTag = recoveryTestTag
            )
        }
        CompactStrictTogglePill(
            isStrictMode = isStrictMode,
            onStrictModeChanged = onStrictModeChanged,
            testTag = strictTestTag
        )
    }
}

@Composable
private fun ScheduleStrictModeRow(
    isStrictMode: Boolean,
    onStrictModeChanged: (Boolean) -> Unit,
    strictTestTag: String
) {
    val palette = LocalResolveExtendedPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .resolveGlassCard(cornerRadius = 18.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isStrictMode) palette.warnContainer else palette.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ResolveIcons.Shield,
                    contentDescription = null,
                    tint = if (isStrictMode) palette.warn else palette.onAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Strict Lock Mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary
                )
                Text(
                    text = if (isStrictMode) {
                        "Recovery Code exit disabled during scheduled lock"
                    } else {
                        "Recovery Code unlock remains allowed"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
            }
        }
        CompactStrictTogglePill(
            isStrictMode = isStrictMode,
            onStrictModeChanged = onStrictModeChanged,
            testTag = strictTestTag
        )
    }
}

@Composable
private fun CompactStrictTogglePill(
    isStrictMode: Boolean,
    onStrictModeChanged: (Boolean) -> Unit,
    testTag: String
) {
    val palette = LocalResolveExtendedPalette.current
    Column(
        modifier = Modifier
            .resolveGlassCard(
                cornerRadius = 18.dp,
                borderColor = if (isStrictMode) palette.warn else palette.cardBorder
            )
            .clip(RoundedCornerShape(18.dp))
            .background(if (isStrictMode) palette.warnContainer.copy(alpha = 0.8f) else palette.cardBottom)
            .toggleable(
                value = isStrictMode,
                role = Role.Switch,
                onValueChange = onStrictModeChanged
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = ResolveIcons.Shield,
            contentDescription = "Strict Mode",
            tint = if (isStrictMode) palette.warn else palette.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "STRICT",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = if (isStrictMode) palette.warn else palette.textPrimary
        )
        Text(
            text = if (isStrictMode) "ON" else "OFF",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = if (isStrictMode) palette.warn else palette.textMuted,
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (isStrictMode) palette.background.copy(alpha = 0.65f)
                    else palette.background.copy(alpha = 0.5f)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ReviewSummaryCard(lines: List<String>) {
    val palette = LocalResolveExtendedPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(cornerRadius = 18.dp, borderColor = palette.cardBorder)
            .padding(16.dp)
            .testTag("review_summary_card"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "SESSION SUMMARY",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textPrimary
        )
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                color = palette.textSecondary
            )
        }
    }
}
