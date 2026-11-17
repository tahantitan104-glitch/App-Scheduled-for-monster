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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.ResolveColors
import com.example.domain.model.DurationSelection
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
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var showAppsSheet by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = ResolveColors.ForestDeep,
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
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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
                    SetupActionRow(
                        title = "Allowed Applications",
                        subtitle = if (allowedPackages.isEmpty()) "Only Resolve & Phone (if enabled)"
                        else "${allowedPackages.size} apps allowed during Lock Mode",
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
                    SetupActionRow(
                        title = "Recovery Code",
                        subtitle = if (hasRecoveryCode) "$recoveryCodeLength-digit verifier configured"
                        else "Required — configure 16-digit recovery code",
                        icon = ResolveIcons.Key,
                        onClick = { showRecoveryDialog = true },
                        testTag = "instant_recovery_row"
                    )
                    Spacer(Modifier.height(4.dp))
                    FeatureTipNote(
                        text = if (hasRecoveryCode) {
                            "Tip: Allowed apps & Phone Dialer stay accessible on Lock Screen."
                        } else {
                            "Note: Configure a 16-digit code first for emergency exit access."
                        },
                        icon = Icons.Outlined.Info
                    )
                }

                item {
                    ReviewSummaryCard(
                        lines = listOf(
                            "Mode: Instant Lock (restricts immediately)",
                            "Lock Duration: ${duration.formattedCompact()}",
                            "Allowed Apps: ${allowedPackages.size} selected",
                            "Phone Calls: ${if (phoneAccessEnabled) "Enabled" else "Disabled"}"
                        )
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
            }
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var showAppsSheet by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = ResolveColors.ForestDeep,
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
                    SetupActionRow(
                        title = "Allowed Apps During Lock",
                        subtitle = if (allowedPackages.isEmpty()) "Only applies once Free Use Window expires"
                        else "${allowedPackages.size} apps allowed during Lock Mode",
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
                    SetupActionRow(
                        title = "Recovery Code",
                        subtitle = if (hasRecoveryCode) "$recoveryCodeLength-digit verifier configured"
                        else "Required — configure 16-digit recovery code",
                        icon = ResolveIcons.Key,
                        onClick = { showRecoveryDialog = true },
                        testTag = "delayed_recovery_row"
                    )
                }

                item {
                    ReviewSummaryCard(
                        lines = listOf(
                            "Free Use Window: ${freeUseDuration.formattedCompact()} (all apps open)",
                            "Automatic Lock After: ${freeUseDuration.formattedCompact()}",
                            "Lock Duration: ${lockDuration.formattedCompact()} (starts when locked)",
                            "Allowed Apps During Lock: ${allowedPackages.size} selected"
                        )
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
            }
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
        phoneAccessEnabled: Boolean
    ) -> Unit,
    onBack: () -> Unit
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
    var showAppsSheet by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    val keyboardController = LocalSoftwareKeyboardController.current
    CleanupTextInteractionsOnDispose()

    Scaffold(
        containerColor = ResolveColors.ForestDeep,
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
                            .clip(RoundedCornerShape(20.dp))
                            .background(ResolveColors.ForestSurface)
                            .border(1.dp, ResolveColors.ForestOutline, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Schedule Type",
                            style = MaterialTheme.typography.titleMedium,
                            color = ResolveColors.TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                ScheduleType.ONE_TIME to "One-Time",
                                ScheduleType.DAILY to "Daily",
                                ScheduleType.CUSTOM_WEEKLY to "Weekly"
                            ).forEach { (type, label) ->
                                FilterChip(
                                    selected = scheduleType == type,
                                    onClick = { scheduleType = type },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ResolveColors.MintSoftContainer,
                                        selectedLabelColor = ResolveColors.MintAccent
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
                                            selectedContainerColor = ResolveColors.MintSoftContainer,
                                            selectedLabelColor = ResolveColors.MintAccent
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
                    SetupActionRow(
                        title = "Allowed Applications",
                        subtitle = "${schedulePackages.size} apps allowed during scheduled lock",
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
                                schedulePhoneAccess
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
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = ResolveColors.TextPrimary
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("setup_back_button")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = ResolveColors.TextPrimary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = ResolveColors.ForestDeep)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(cornerRadius = 18.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(ResolveColors.MintSoftContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ResolveColors.MintAccent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = ResolveColors.TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = ResolveColors.TextSecondary)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = ResolveColors.TextSecondary
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(cornerRadius = 18.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(ResolveColors.MintSoftContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ResolveColors.MintAccent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = ResolveColors.TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = ResolveColors.TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ResolveColors.ForestDeep,
                checkedTrackColor = ResolveColors.MintAccent
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun ReviewSummaryCard(lines: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ResolveColors.ForestSecondary)
            .border(1.dp, ResolveColors.ForestBorderGlow.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "SESSION SUMMARY",
            style = MaterialTheme.typography.labelSmall,
            color = ResolveColors.MintAccent
        )
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                color = ResolveColors.TextSecondary
            )
        }
    }
}
