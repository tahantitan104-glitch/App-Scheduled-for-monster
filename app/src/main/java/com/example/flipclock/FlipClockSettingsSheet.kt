package com.example.flipclock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.designsystem.ResolveColors
import com.example.domain.model.AmbientSoundType
import com.example.domain.model.FlipClockSettings
import com.example.domain.model.FlipDateFormat
import com.example.domain.model.FlipTimerPreset
import com.example.presentation.components.ProvideSafeTextToolbar
import kotlin.math.roundToInt

private val SLEEP_TIMER_OPTIONS = listOf(0, 15, 30, 45, 60, 90)
private val POMODORO_PRESETS = listOf(25 to 5, 50 to 10, 45 to 15, 15 to 3)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FlipClockSettingsSheet(
    settings: FlipClockSettings,
    presets: List<FlipTimerPreset>,
    onDismiss: () -> Unit,
    onSetUseSystemTimeFormat: (Boolean) -> Unit,
    onSetUse24Hour: (Boolean) -> Unit,
    onSetHourLeadingZero: (Boolean) -> Unit,
    onSetDateFormatPattern: (String) -> Unit,
    onSetShowFlipAxis: (Boolean) -> Unit,
    onSetShowSeconds: (Boolean) -> Unit,
    onSetShowWeekday: (Boolean) -> Unit,
    onSetShowDate: (Boolean) -> Unit,
    onSetShowBattery: (Boolean) -> Unit,
    onSetShowUpcomingAlarm: (Boolean) -> Unit,
    onSetShowWeather: (Boolean) -> Unit,
    onSetWeatherLocationQuery: (String) -> Unit,
    onSetSleepTimerMinutes: (Int) -> Unit,
    onSetClockTickerSound: (Boolean) -> Unit,
    onSetCustomTag: (String) -> Unit,
    onSetKeepScreenAwake: (Boolean) -> Unit,
    onSetAutoRepeat: (Boolean) -> Unit,
    onResetZoom: () -> Unit,
    onSavePreset: (presetId: String?, name: String, durationSeconds: Long) -> String?,
    onDeletePreset: (presetId: String) -> Unit,
    onResetSettingsOnly: () -> Unit,
    onSetPomodoroEnabled: (Boolean) -> Unit = {},
    onSetPomodoroDurations: (focusMinutes: Int, breakMinutes: Int) -> Unit = { _, _ -> },
    onSetAmbientSoundType: (AmbientSoundType) -> Unit = {},
    onSetAmbientVolume: (Float) -> Unit = {},
    onSetOledBurnInProtection: (Boolean) -> Unit = {},
    onOpenLayoutEditor: () -> Unit = {},
    onSetWeatherUseFahrenheit: (Boolean) -> Unit = {},
    onSetWeatherAutoDetect: (Boolean) -> Unit = {},
    onSetPlayAmbientDuringFocusLock: (Boolean) -> Unit = {},
    onSetAmbientPlayInBackground: (Boolean) -> Unit = {},
    onSetSyncFlipTimerWithFocusLock: (Boolean) -> Unit = {},
    onOpenWhiteNoiseStudio: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingPreset by remember { mutableStateOf<FlipTimerPreset?>(null) }
    var showAddPresetDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var presetError by remember { mutableStateOf<String?>(null) }

    var weatherCityDraft by remember(settings.weatherLocationQuery) {
        mutableStateOf(settings.weatherLocationQuery)
    }
    var customTagDraft by remember(settings.customTag) {
        mutableStateOf(settings.customTag)
    }
    val focusManager = LocalFocusManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = FlipClockPalette.PanelOutline)
        },
        containerColor = FlipClockPalette.PanelDark,
        contentColor = FlipClockPalette.AccentSoft
    ) {
        ProvideSafeTextToolbar {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
                    .testTag("flip_clock_settings_sheet"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Flip Clock Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = FlipClockPalette.AccentSoft
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("flip_settings_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close Flip Clock settings",
                            tint = FlipClockPalette.SubTextGrey
                        )
                    }
                }

                // DEDICATED SECTION: White Noise & Focus Soundscapes
                SettingsSectionLabel("WHITE NOISE & SOUNDSCAPES")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (settings.ambientSoundType != AmbientSoundType.OFF) {
                                FlipClockPalette.PanelActive
                            } else {
                                FlipClockPalette.PanelElevated
                            }
                        )
                        .border(
                            1.dp,
                            if (settings.ambientSoundType != AmbientSoundType.OFF) {
                                FlipClockPalette.PrimaryCta.copy(alpha = 0.72f)
                            } else {
                                FlipClockPalette.PrimaryCta.copy(alpha = 0.45f)
                            },
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onOpenWhiteNoiseStudio() }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("flip_open_white_noise_studio_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = if (settings.ambientSoundType != AmbientSoundType.OFF) {
                                "White Noise Studio · ${settings.ambientSoundType.label}"
                            } else {
                                "White Noise & Nature Soundscapes"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = FlipClockPalette.AccentSoft
                        )
                        Text(
                            text = if (settings.ambientSoundType != AmbientSoundType.OFF) {
                                "${settings.ambientSoundType.subtitle} · ${(settings.ambientVolume * 100f).roundToInt()}% volume"
                            } else {
                                "White Noise, Soft Rain, Bonfire, Forest Wind, River Waves & more"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = FlipClockPalette.SubTextGrey
                        )
                    }
                    Text(
                        text = if (settings.ambientSoundType != AmbientSoundType.OFF) "Playing" else "Open Studio",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = FlipClockPalette.PrimaryCtaText,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(FlipClockPalette.PrimaryCta)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // SECTION 1: Time & Date Format
                SettingsSectionLabel("TIME & FORMAT")

                SettingsSwitchRow(
                    title = "Use System Time Format",
                    subtitle = "Follow Android's 12-hour or 24-hour system clock setting",
                    checked = settings.useSystemTimeFormat,
                    onCheckedChange = onSetUseSystemTimeFormat,
                    testTag = "flip_setting_system_time"
                )

                SettingsSwitchRow(
                    title = "24-Hour Time Format",
                    subtitle = when {
                        settings.useSystemTimeFormat -> "Controlled by Android System Time setting"
                        settings.use24HourFormat -> "Showing 24-hour clock"
                        else -> "Showing 12-hour clock (AM/PM)"
                    },
                    checked = settings.use24HourFormat,
                    enabled = !settings.useSystemTimeFormat,
                    onCheckedChange = onSetUse24Hour,
                    testTag = "flip_setting_24h"
                )

                SettingsSwitchRow(
                    title = "Hour Leading Zero",
                    subtitle = if (settings.hourLeadingZero) "Shows 09 instead of 9" else "Shows natural single-digit hours (e.g. 9)",
                    checked = settings.hourLeadingZero,
                    onCheckedChange = onSetHourLeadingZero,
                    testTag = "flip_setting_leading_zero"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(FlipClockPalette.PanelElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Date Format",
                        style = MaterialTheme.typography.titleSmall,
                        color = FlipClockPalette.AccentSoft
                    )
                    Text(
                        text = "Choose how the optional date line is formatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = FlipClockPalette.SubTextGrey
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FlipDateFormat.entries.forEach { fmt ->
                            val selected = settings.resolvedDateFormat == fmt
                            FilterChip(
                                selected = selected,
                                onClick = { onSetDateFormatPattern(fmt.pattern) },
                                label = { Text(fmt.label, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FlipClockPalette.PrimaryCta,
                                    selectedLabelColor = FlipClockPalette.PrimaryCtaText,
                                    containerColor = FlipClockPalette.PanelDark,
                                    labelColor = FlipClockPalette.SubTextGrey
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = FlipClockPalette.PanelOutline,
                                    selectedBorderColor = FlipClockPalette.SageLabel.copy(alpha = 0.75f)
                                )
                            )
                        }
                    }
                }

                // SECTION 2: Clock Display & Split Axis
                SettingsSectionLabel("DISPLAY & FLIP AXIS")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(FlipClockPalette.PanelElevated)
                        .border(1.dp, FlipClockPalette.PrimaryCta.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .clickable { onOpenLayoutEditor() }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("flip_open_layout_editor_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "On-Screen Layout Editor",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = FlipClockPalette.AccentSoft
                        )
                        Text(
                            text = "Drag Hour, Minute, AM/PM, Seconds, Date, Weekday, Weather & Battery % directly on screen",
                            style = MaterialTheme.typography.bodySmall,
                            color = FlipClockPalette.SubTextGrey
                        )
                    }
                    Text(
                        text = "Customize",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = FlipClockPalette.PrimaryCtaText,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(FlipClockPalette.PrimaryCta)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                SettingsSwitchRow(
                    title = "Flip Axis Seam",
                    subtitle = "Show central split-flap dividing line through digit midpoint",
                    checked = settings.showFlipAxis,
                    onCheckedChange = onSetShowFlipAxis,
                    testTag = "flip_setting_flip_axis"
                )

                SettingsSwitchRow(
                    title = "Show Seconds",
                    subtitle = "Display live seconds below minutes",
                    checked = settings.showSeconds,
                    onCheckedChange = onSetShowSeconds,
                    testTag = "flip_setting_show_seconds"
                )

                SettingsSwitchRow(
                    title = "Show Weekday",
                    subtitle = "Display day of the week between hour and minute",
                    checked = settings.showWeekday,
                    onCheckedChange = onSetShowWeekday,
                    testTag = "flip_setting_show_weekday"
                )

                SettingsSwitchRow(
                    title = "Date Display",
                    subtitle = "Show subtle formatted date on Clock screen",
                    checked = settings.showDate,
                    onCheckedChange = onSetShowDate,
                    testTag = "flip_setting_show_date"
                )

                // SECTION 3: Live Status & Custom Tag
                SettingsSectionLabel("CLOCK OVERLAY INFO")

                SettingsSwitchRow(
                    title = "Battery Level",
                    subtitle = "Show real device battery percentage and charging state",
                    checked = settings.showBattery,
                    onCheckedChange = onSetShowBattery,
                    testTag = "flip_setting_show_battery"
                )

                SettingsSwitchRow(
                    title = "Upcoming Alarm",
                    subtitle = "Show next scheduled Android system alarm if set",
                    checked = settings.showUpcomingAlarm,
                    onCheckedChange = onSetShowUpcomingAlarm,
                    testTag = "flip_setting_show_alarm"
                )

                SettingsSwitchRow(
                    title = "Weather Info",
                    subtitle = "Show live local weather on the Flip Clock face (no GPS)",
                    checked = settings.showWeather,
                    onCheckedChange = onSetShowWeather,
                    testTag = "flip_setting_show_weather"
                )

                AnimatedVisibility(visible = settings.showWeather) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(FlipClockPalette.PanelElevated)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = weatherCityDraft,
                                onValueChange = { weatherCityDraft = it.take(48) },
                                label = {
                                    Text(
                                        if (settings.weatherAutoDetect) {
                                            "City Override (optional)"
                                        } else {
                                            "City Name (e.g. Dhaka, London)"
                                        }
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        onSetWeatherLocationQuery(weatherCityDraft)
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = FlipClockPalette.AccentSoft,
                                    unfocusedTextColor = FlipClockPalette.AccentSoft,
                                    focusedBorderColor = FlipClockPalette.SageAccent,
                                    unfocusedBorderColor = FlipClockPalette.PanelOutline
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("flip_setting_weather_city_input")
                            )
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    onSetWeatherLocationQuery(weatherCityDraft)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FlipClockPalette.PrimaryCta,
                                    contentColor = FlipClockPalette.PrimaryCtaText
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Set")
                            }
                        }

                        SettingsSwitchRow(
                            title = "Auto-Detect City (No GPS)",
                            subtitle = "Resolves approximate city via network or timezone when city is blank",
                            checked = settings.weatherAutoDetect,
                            onCheckedChange = onSetWeatherAutoDetect,
                            testTag = "flip_setting_weather_auto_detect"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(FlipClockPalette.PanelElevated)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Temperature Unit",
                                style = MaterialTheme.typography.titleSmall,
                                color = FlipClockPalette.AccentSoft
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !settings.weatherUseFahrenheit,
                                    onClick = { onSetWeatherUseFahrenheit(false) },
                                    label = { Text("°C") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = FlipClockPalette.PrimaryCta,
                                        selectedLabelColor = FlipClockPalette.PrimaryCtaText,
                                        containerColor = FlipClockPalette.PanelDark,
                                        labelColor = FlipClockPalette.SubTextGrey
                                    ),
                                    modifier = Modifier.testTag("flip_setting_weather_celsius")
                                )
                                FilterChip(
                                    selected = settings.weatherUseFahrenheit,
                                    onClick = { onSetWeatherUseFahrenheit(true) },
                                    label = { Text("°F") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = FlipClockPalette.PrimaryCta,
                                        selectedLabelColor = FlipClockPalette.PrimaryCtaText,
                                        containerColor = FlipClockPalette.PanelDark,
                                        labelColor = FlipClockPalette.SubTextGrey
                                    ),
                                    modifier = Modifier.testTag("flip_setting_weather_fahrenheit")
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(FlipClockPalette.PanelElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Custom Tag",
                        style = MaterialTheme.typography.titleSmall,
                        color = FlipClockPalette.AccentSoft
                    )
                    Text(
                        text = "Optional short label shown subtly on the Clock face",
                        style = MaterialTheme.typography.bodySmall,
                        color = FlipClockPalette.SubTextGrey
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customTagDraft,
                            onValueChange = { customTagDraft = it.take(28) },
                            placeholder = { Text("e.g. Deep Focus") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    onSetCustomTag(customTagDraft)
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = FlipClockPalette.AccentSoft,
                                unfocusedTextColor = FlipClockPalette.AccentSoft,
                                focusedBorderColor = FlipClockPalette.SageAccent,
                                unfocusedBorderColor = FlipClockPalette.PanelOutline
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("flip_setting_custom_tag_input")
                        )
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onSetCustomTag(customTagDraft)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FlipClockPalette.PrimaryCta,
                                contentColor = FlipClockPalette.PrimaryCtaText
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Apply")
                        }
                    }
                }

                // SECTION 4: Audio, Sleep & Screen
                SettingsSectionLabel("SOUND, SLEEP & SCREEN")

                SettingsSwitchRow(
                    title = "Clock Ticker Sound",
                    subtitle = "Play subtle mechanical click on each second tick",
                    checked = settings.clockTickerSoundEnabled,
                    onCheckedChange = onSetClockTickerSound,
                    testTag = "flip_setting_ticker_sound"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(FlipClockPalette.PanelElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Ambient Focus Sound",
                        style = MaterialTheme.typography.titleSmall,
                        color = FlipClockPalette.AccentSoft
                    )
                    Text(
                        text = "Offline synthesized focus soundscape across Flip Clock & Focus Lock",
                        style = MaterialTheme.typography.bodySmall,
                        color = FlipClockPalette.SubTextGrey
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AmbientSoundType.entries.forEach { sound ->
                            val selected = settings.ambientSoundType == sound
                            FilterChip(
                                selected = selected,
                                onClick = { onSetAmbientSoundType(sound) },
                                label = { Text(sound.label, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FlipClockPalette.PrimaryCta,
                                    selectedLabelColor = FlipClockPalette.PrimaryCtaText,
                                    containerColor = FlipClockPalette.PanelDark,
                                    labelColor = FlipClockPalette.SubTextGrey
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = FlipClockPalette.PanelOutline,
                                    selectedBorderColor = FlipClockPalette.SageLabel.copy(alpha = 0.75f)
                                ),
                                modifier = Modifier.testTag("flip_ambient_${sound.name.lowercase()}")
                            )
                        }
                    }
                    AnimatedVisibility(visible = settings.ambientSoundType != AmbientSoundType.OFF) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Ambient Volume",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = FlipClockPalette.SubTextGrey
                                )
                                Text(
                                    text = "${(settings.ambientVolume * 100f).roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = FlipClockPalette.AccentSoft
                                )
                            }
                            Slider(
                                value = settings.ambientVolume.coerceIn(0.05f, 1f),
                                onValueChange = onSetAmbientVolume,
                                valueRange = 0.05f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = FlipClockPalette.SageLabel,
                                    activeTrackColor = FlipClockPalette.SageAccent,
                                    inactiveTrackColor = FlipClockPalette.PanelDark
                                ),
                                modifier = Modifier.testTag("flip_ambient_volume_slider")
                            )
                        }
                    }
                }

                SettingsSwitchRow(
                    title = "Play Soundscape During Focus Lock",
                    subtitle = "Continue playing the selected ambient sound on the Lock Screen",
                    checked = settings.playAmbientSoundDuringFocusLock,
                    onCheckedChange = onSetPlayAmbientDuringFocusLock,
                    testTag = "flip_setting_ambient_during_focus_lock"
                )

                SettingsSwitchRow(
                    title = "Continue Soundscape in Background",
                    subtitle = "Keep soothing soundscape running even after closing Flip Clock or locking screen",
                    checked = settings.ambientPlayInBackground,
                    onCheckedChange = onSetAmbientPlayInBackground,
                    testTag = "flip_setting_ambient_background"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(FlipClockPalette.PanelElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sleep Timer",
                        style = MaterialTheme.typography.titleSmall,
                        color = FlipClockPalette.AccentSoft
                    )
                    Text(
                        text = if (settings.sleepTimerMinutes > 0) {
                            "Automatically dims sound & closes Flip Clock after ${settings.sleepTimerMinutes} min"
                        } else {
                            "Off — stays active until you exit"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = FlipClockPalette.SubTextGrey
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SLEEP_TIMER_OPTIONS.forEach { mins ->
                            val selected = settings.sleepTimerMinutes == mins
                            FilterChip(
                                selected = selected,
                                onClick = { onSetSleepTimerMinutes(mins) },
                                label = { Text(if (mins == 0) "Off" else "${mins}m") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FlipClockPalette.PrimaryCta,
                                    selectedLabelColor = FlipClockPalette.PrimaryCtaText,
                                    containerColor = FlipClockPalette.PanelDark,
                                    labelColor = FlipClockPalette.SubTextGrey
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = FlipClockPalette.PanelOutline,
                                    selectedBorderColor = FlipClockPalette.SageLabel.copy(alpha = 0.75f)
                                )
                            )
                        }
                    }
                }

                SettingsSwitchRow(
                    title = "Keep Screen Awake",
                    subtitle = "Prevent display sleep while Flip Clock is open",
                    checked = settings.keepScreenAwake,
                    onCheckedChange = onSetKeepScreenAwake,
                    testTag = "flip_setting_keep_awake"
                )

                SettingsSwitchRow(
                    title = "OLED Burn-in Protection",
                    subtitle = "Gently shifts numerals by a few pixels every minute on pure black canvas",
                    checked = settings.oledBurnInProtection,
                    onCheckedChange = onSetOledBurnInProtection,
                    testTag = "flip_setting_oled_shift"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(FlipClockPalette.PanelElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Screen Orientation",
                            style = MaterialTheme.typography.titleSmall,
                            color = FlipClockPalette.AccentSoft
                        )
                        Text(
                            text = "Follows device Portrait & Landscape automatically",
                            style = MaterialTheme.typography.bodySmall,
                            color = FlipClockPalette.SubTextGrey
                        )
                    }
                    Text(
                        text = "Auto",
                        style = MaterialTheme.typography.labelLarge,
                        color = FlipClockPalette.PrimaryCtaText,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(FlipClockPalette.PrimaryCta)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(FlipClockPalette.PanelElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Display Zoom",
                            style = MaterialTheme.typography.titleSmall,
                            color = FlipClockPalette.AccentSoft
                        )
                        Text(
                            text = "Current scale: ${(settings.clampedZoomFraction * 100f).roundToInt()}% (50%–140%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = FlipClockPalette.SubTextGrey
                        )
                    }
                    OutlinedButton(
                        onClick = onResetZoom,
                        enabled = kotlin.math.abs(settings.clampedZoomFraction - FlipClockSettings.DEFAULT_ZOOM_FRACTION) > 0.01f,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, FlipClockPalette.PanelOutline),
                        modifier = Modifier.testTag("flip_setting_zoom_reset_btn")
                    ) {
                        Text("Reset (100%)", color = FlipClockPalette.AccentSoft)
                    }
                }

                // SECTION 5: Timer Settings, Pomodoro & Presets
                SettingsSectionLabel("POMODORO, TIMER & PRESETS")

                SettingsSwitchRow(
                    title = "Pomodoro Focus / Break Cycle",
                    subtitle = "Automatically alternates ${settings.pomodoroFocusMinutes}m focus and ${settings.pomodoroBreakMinutes}m break with chime",
                    checked = settings.pomodoroEnabled,
                    onCheckedChange = onSetPomodoroEnabled,
                    testTag = "flip_setting_pomodoro"
                )

                AnimatedVisibility(visible = settings.pomodoroEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(FlipClockPalette.PanelElevated)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Pomodoro Interval (Focus / Break)",
                            style = MaterialTheme.typography.titleSmall,
                            color = FlipClockPalette.AccentSoft
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            POMODORO_PRESETS.forEach { (focusM, breakM) ->
                                val selected = settings.pomodoroFocusMinutes == focusM &&
                                    settings.pomodoroBreakMinutes == breakM
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSetPomodoroDurations(focusM, breakM) },
                                    label = { Text("${focusM}m / ${breakM}m") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = FlipClockPalette.PrimaryCta,
                                        selectedLabelColor = FlipClockPalette.PrimaryCtaText,
                                        containerColor = FlipClockPalette.PanelDark,
                                        labelColor = FlipClockPalette.SubTextGrey
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = FlipClockPalette.PanelOutline,
                                        selectedBorderColor = FlipClockPalette.SageLabel.copy(alpha = 0.75f)
                                    ),
                                    modifier = Modifier.testTag("flip_pomodoro_${focusM}_${breakM}")
                                )
                            }
                        }
                    }
                }

                SettingsSwitchRow(
                    title = "Timer Auto-Repeat",
                    subtitle = "Automatically restart after the 5s completion message",
                    checked = settings.autoRepeat,
                    enabled = !settings.pomodoroEnabled,
                    onCheckedChange = onSetAutoRepeat,
                    testTag = "flip_setting_auto_repeat"
                )

                SettingsSwitchRow(
                    title = "Sync Flip Timer with Focus Lock",
                    subtitle = "Show live Focus Lock countdown & start Focus Lock from Flip Timer",
                    checked = settings.syncFlipTimerWithFocusLock,
                    onCheckedChange = onSetSyncFlipTimerWithFocusLock,
                    testTag = "flip_setting_sync_flip_timer_lock"
                )

                HorizontalDivider(color = FlipClockPalette.PanelOutline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Timer Presets (${presets.size} / ${FlipTimerPreset.MAX_TOTAL_PRESETS})",
                            style = MaterialTheme.typography.titleMedium,
                            color = FlipClockPalette.AccentSoft
                        )
                        Text(
                            text = "Duplicate durations are allowed up to ${FlipTimerPreset.MAX_TOTAL_PRESETS} total presets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = FlipClockPalette.SubTextGrey
                        )
                    }
                    Button(
                        onClick = {
                            presetError = null
                            showAddPresetDialog = true
                        },
                        enabled = presets.size < FlipTimerPreset.MAX_TOTAL_PRESETS,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FlipClockPalette.PrimaryCta,
                            contentColor = FlipClockPalette.PrimaryCtaText
                        ),
                        modifier = Modifier.testTag("flip_add_preset_btn")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (presets.size >= FlipTimerPreset.MAX_TOTAL_PRESETS) "Max 15" else "Add")
                    }
                }

                if (presets.size >= FlipTimerPreset.MAX_TOTAL_PRESETS) {
                    Text(
                        text = "Maximum limit of ${FlipTimerPreset.MAX_TOTAL_PRESETS} timer presets reached. Delete a preset to add another.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ResolveColors.AmberWarn,
                        modifier = Modifier.testTag("flip_preset_limit_notice")
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(FlipClockPalette.PanelElevated)
                                .border(1.dp, FlipClockPalette.PanelOutline, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = FlipClockPalette.AccentSoft,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatDurationWords(preset.durationSeconds),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FlipClockPalette.SubTextGrey
                                )
                            }
                            IconButton(
                                onClick = {
                                    presetError = null
                                    editingPreset = preset
                                },
                                modifier = Modifier.testTag("flip_edit_preset_${preset.presetId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Edit ${preset.name}",
                                    tint = FlipClockPalette.SubTextGrey
                                )
                            }
                            IconButton(
                                onClick = { onDeletePreset(preset.presetId) },
                                modifier = Modifier.testTag("flip_delete_preset_${preset.presetId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete ${preset.name}",
                                    tint = ResolveColors.ErrorCoral
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = FlipClockPalette.PanelOutline)

                OutlinedButton(
                    onClick = { showResetConfirmDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ResolveColors.ErrorCoral.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("flip_reset_settings_btn")
                ) {
                    Text(
                        text = "Reset Flip Clock Settings to Default",
                        color = ResolveColors.ErrorCoral
                    )
                }
            }
        }
    }

    if (showAddPresetDialog || editingPreset != null) {
        val target = editingPreset
        FlipPresetEditorDialog(
            title = if (target == null) "Add Timer Preset" else "Edit Timer Preset",
            initialName = target?.name ?: "",
            initialSeconds = target?.durationSeconds ?: 300L,
            externalError = presetError,
            onDismiss = {
                showAddPresetDialog = false
                editingPreset = null
                presetError = null
            },
            onConfirm = { name, totalSeconds ->
                val error = onSavePreset(target?.presetId, name, totalSeconds)
                if (error == null) {
                    showAddPresetDialog = false
                    editingPreset = null
                    presetError = null
                } else {
                    presetError = error
                }
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = FlipClockPalette.PanelDark,
            titleContentColor = FlipClockPalette.AccentSoft,
            textContentColor = FlipClockPalette.SubTextGrey,
            title = { Text("Reset Flip Clock Settings?") },
            text = {
                Text(
                    "Restores default 12-hour clock, Flip Axis ON, Date Display OFF, Sleep Timer OFF, Ticker Sound OFF, 100% Zoom, and missing defaults within the 15-preset limit. Your custom Timer Presets stay preserved."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetSettingsOnly()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ResolveColors.ErrorContainer,
                        contentColor = ResolveColors.ErrorCoral
                    ),
                    modifier = Modifier.testTag("flip_reset_settings_confirm_btn")
                ) {
                    Text("Reset Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel", color = FlipClockPalette.SubTextGrey)
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionLabel(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 14.dp)
                .clip(CircleShape)
                .background(FlipClockPalette.SageLabel)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 1.1.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = FlipClockPalette.AccentSoft
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FlipClockPalette.PanelElevated.copy(alpha = if (enabled) 1f else 0.55f))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) FlipClockPalette.AccentSoft else FlipClockPalette.SubTextGrey
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = FlipClockPalette.SubTextGrey
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = FlipClockPalette.SageAccent,
                checkedBorderColor = FlipClockPalette.SageAccent,
                uncheckedThumbColor = FlipClockPalette.SubTextGrey,
                uncheckedTrackColor = FlipClockPalette.PanelDark,
                uncheckedBorderColor = FlipClockPalette.PanelOutline
            )
        )
    }
}

@Composable
fun FlipPresetEditorDialog(
    title: String,
    initialName: String,
    initialSeconds: Long,
    externalError: String? = null,
    requireName: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (name: String, totalSeconds: Long) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var hours by remember(initialSeconds) { mutableIntStateOf((initialSeconds / 3600L).toInt().coerceIn(0, 23)) }
    var minutes by remember(initialSeconds) { mutableIntStateOf(((initialSeconds % 3600L) / 60L).toInt().coerceIn(0, 59)) }
    var seconds by remember(initialSeconds) { mutableIntStateOf((initialSeconds % 60L).toInt().coerceIn(0, 59)) }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FlipClockPalette.PanelDark,
        titleContentColor = FlipClockPalette.AccentSoft,
        textContentColor = FlipClockPalette.SubTextGrey,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (requireName) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it.take(36)
                            localError = null
                        },
                        label = { Text("Preset Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("flip_preset_name_input")
                    )
                }

                Text(
                    text = "Duration (H : M : S)",
                    style = MaterialTheme.typography.labelLarge,
                    color = FlipClockPalette.AccentSoft
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumberStepperField(
                        label = "Hours",
                        value = hours,
                        range = 0..23,
                        onValueChange = { hours = it; localError = null },
                        modifier = Modifier.weight(1f),
                        testTag = "flip_picker_hours"
                    )
                    NumberStepperField(
                        label = "Min",
                        value = minutes,
                        range = 0..59,
                        onValueChange = { minutes = it; localError = null },
                        modifier = Modifier.weight(1f),
                        testTag = "flip_picker_minutes"
                    )
                    NumberStepperField(
                        label = "Sec",
                        value = seconds,
                        range = 0..59,
                        onValueChange = { seconds = it; localError = null },
                        modifier = Modifier.weight(1f),
                        testTag = "flip_picker_seconds"
                    )
                }

                val err = externalError ?: localError
                if (!err.isNullOrBlank()) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = ResolveColors.ErrorCoral
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val total = hours * 3600L + minutes * 60L + seconds
                    if (total <= 0L) {
                        localError = "Select at least 1 second."
                        return@Button
                    }
                    val effectiveName = if (requireName) {
                        if (name.isBlank()) {
                            localError = "Enter a preset name."
                            return@Button
                        }
                        name.trim()
                    } else {
                        "Custom (${formatDurationWords(total)})"
                    }
                    onConfirm(effectiveName, total)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = FlipClockPalette.PrimaryCta,
                    contentColor = FlipClockPalette.PrimaryCtaText
                ),
                modifier = Modifier.testTag("flip_preset_save_btn")
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FlipClockPalette.SubTextGrey)
            }
        }
    )
}

@Composable
private fun NumberStepperField(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(FlipClockPalette.PanelElevated)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = FlipClockPalette.SubTextGrey)
        OutlinedButton(
            onClick = {
                val next = if (value >= range.last) range.first else value + 1
                onValueChange(next)
            },
            shape = CircleShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.size(34.dp).testTag("${testTag}_inc")
        ) {
            Text("+", color = FlipClockPalette.AccentSoft, fontSize = 16.sp)
        }
        Text(
            text = "%02d".format(value),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = FlipClockPalette.AccentSoft,
            modifier = Modifier.testTag(testTag)
        )
        OutlinedButton(
            onClick = {
                val next = if (value <= range.first) range.last else value - 1
                onValueChange(next)
            },
            shape = CircleShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.size(34.dp).testTag("${testTag}_dec")
        ) {
            Text("-", color = FlipClockPalette.AccentSoft, fontSize = 16.sp)
        }
    }
}

fun formatDurationWords(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val h = s / 3600L
    val m = (s % 3600L) / 60L
    val remS = s % 60L
    return buildList {
        if (h > 0L) add("${h}h")
        if (m > 0L) add("${m}m")
        if (remS > 0L || isEmpty()) add("${remS}s")
    }.joinToString(" ")
}
