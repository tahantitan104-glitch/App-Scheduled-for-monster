package com.example.flipclock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.designsystem.ResolveColors
import com.example.domain.model.FlipClockSettings
import com.example.domain.model.FlipTimerPreset
import com.example.presentation.components.ProvideSafeTextToolbar
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlipClockSettingsSheet(
    settings: FlipClockSettings,
    presets: List<FlipTimerPreset>,
    onDismiss: () -> Unit,
    onSetUse24Hour: (Boolean) -> Unit,
    onSetKeepScreenAwake: (Boolean) -> Unit,
    onSetShowDate: (Boolean) -> Unit,
    onSetAutoRepeat: (Boolean) -> Unit,
    onResetZoom: () -> Unit,
    onSavePreset: (presetId: String?, name: String, durationSeconds: Long) -> String?,
    onDeletePreset: (presetId: String) -> Unit,
    onResetSettingsOnly: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingPreset by remember { mutableStateOf<FlipTimerPreset?>(null) }
    var showAddPresetDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var presetError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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

                // 1. Time Format
                SettingsSwitchRow(
                    title = "24-Hour Time Format",
                    subtitle = if (settings.use24HourFormat) "Showing 24-hour clock" else "Showing 12-hour clock (AM/PM)",
                    checked = settings.use24HourFormat,
                    onCheckedChange = onSetUse24Hour,
                    testTag = "flip_setting_24h"
                )

                // 2. Orientation Status
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
                        color = ResolveColors.MintAccent,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(ResolveColors.MintSoftContainer)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                // 3. Keep Screen Awake
                SettingsSwitchRow(
                    title = "Keep Screen Awake",
                    subtitle = "Prevent display sleep while Flip Clock is open",
                    checked = settings.keepScreenAwake,
                    onCheckedChange = onSetKeepScreenAwake,
                    testTag = "flip_setting_keep_awake"
                )

                // 4. Date Display
                SettingsSwitchRow(
                    title = "Date & Weekday Display",
                    subtitle = "Show subtle weekday and date on Clock screen",
                    checked = settings.showDate,
                    onCheckedChange = onSetShowDate,
                    testTag = "flip_setting_show_date"
                )

                // 5. Auto-Repeat Timer
                SettingsSwitchRow(
                    title = "Timer Auto-Repeat",
                    subtitle = "Automatically restart after the 5s completion message",
                    checked = settings.autoRepeat,
                    onCheckedChange = onSetAutoRepeat,
                    testTag = "flip_setting_auto_repeat"
                )

                // 6. Zoom Reset
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

                HorizontalDivider(color = FlipClockPalette.PanelOutline)

                // 7. Timer Preset Management (max 15)
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
                            containerColor = ResolveColors.MintSoftContainer,
                            contentColor = ResolveColors.MintBright
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

                // 8. Reset Settings to Default
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
                    "Restores 12-hour format, Date Display OFF, Keep Screen Awake OFF, Auto-repeat OFF, 100% Zoom, and missing defaults within the 15-preset limit. Your custom Timer Presets stay preserved."
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
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FlipClockPalette.PanelElevated)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = FlipClockPalette.AccentSoft
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = FlipClockPalette.SubTextGrey
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = ResolveColors.ForestDeep,
                checkedTrackColor = ResolveColors.MintAccent,
                uncheckedThumbColor = FlipClockPalette.SubTextGrey,
                uncheckedTrackColor = FlipClockPalette.PanelDark
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
                    containerColor = ResolveColors.MintSoftContainer,
                    contentColor = ResolveColors.MintBright
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
