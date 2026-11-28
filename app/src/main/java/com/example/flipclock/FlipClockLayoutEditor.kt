package com.example.flipclock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.FlipBatteryStyle
import com.example.domain.model.FlipClockElementAlign
import com.example.domain.model.FlipClockElementId
import com.example.domain.model.FlipClockOrientationLayout
import com.example.domain.model.FlipDateFormat
import com.example.domain.model.FlipElementPlacement
import com.example.domain.model.FlipWeatherStyle
import com.example.domain.model.FlipWeekdayStyle
import kotlin.math.roundToInt

@Composable
fun FlipDraggableElementBox(
    elementId: FlipClockElementId,
    isEditMode: Boolean,
    isSelected: Boolean,
    placement: FlipElementPlacement,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    onSelect: () -> Unit,
    onDragFractionDelta: (dxFrac: Float, dyFrac: Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val latestOnDrag by rememberUpdatedState(onDragFractionDelta)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val latestWidth by rememberUpdatedState(canvasWidthPx.coerceAtLeast(1f))
    val latestHeight by rememberUpdatedState(canvasHeightPx.coerceAtLeast(1f))

    val xPx = (placement.offsetXFrac * canvasWidthPx).roundToInt()
    val yPx = (placement.offsetYFrac * canvasHeightPx).roundToInt()

    Box(
        modifier = modifier
            .offset { IntOffset(x = xPx, y = yPx) }
            .then(
                if (isEditMode) {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) FlipClockPalette.PrimaryCta.copy(alpha = 0.14f)
                            else FlipClockPalette.PanelDark.copy(alpha = 0.35f)
                        )
                        .border(
                            width = if (isSelected) 1.6.dp else 1.dp,
                            color = if (isSelected) FlipClockPalette.PrimaryCta
                            else FlipClockPalette.PanelOutline.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .pointerInput(elementId) {
                            detectDragGestures(
                                onDragStart = { latestOnSelect() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    latestOnDrag(
                                        dragAmount.x / latestWidth,
                                        dragAmount.y / latestHeight
                                    )
                                }
                            )
                        }
                        .clickable { latestOnSelect() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("flip_editor_draggable_${elementId.name.lowercase()}")
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun FlipLayoutEditorTopBar(
    isLandscape: Boolean,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("flip_layout_editor_top_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            onClick = onCancel,
            shape = CircleShape,
            color = FlipClockPalette.PanelDark.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, FlipClockPalette.PanelOutline),
            modifier = Modifier.testTag("flip_layout_cancel_btn")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Cancel Layout Edit",
                    tint = FlipClockPalette.SubTextGrey,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = FlipClockPalette.AccentSoft
                )
            }
        }

        if (!isLandscape) {
            Text(
                text = "Drag & Customize",
                style = MaterialTheme.typography.labelMedium,
                color = FlipClockPalette.SubTextGrey,
                maxLines = 1
            )
        } else {
            Text(
                text = "Landscape Layout Editor — Drag Elements",
                style = MaterialTheme.typography.labelMedium,
                color = FlipClockPalette.SubTextGrey,
                maxLines = 1
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onReset,
                shape = CircleShape,
                border = BorderStroke(1.dp, FlipClockPalette.PanelOutline),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = FlipClockPalette.PanelDark.copy(alpha = 0.92f),
                    contentColor = FlipClockPalette.AccentSoft
                ),
                modifier = Modifier.testTag("flip_layout_reset_btn")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Reset Layout",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Reset", style = MaterialTheme.typography.labelLarge)
            }

            Button(
                onClick = onConfirm,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FlipClockPalette.PrimaryCta,
                    contentColor = FlipClockPalette.PrimaryCtaText
                ),
                modifier = Modifier.testTag("flip_layout_confirm_btn")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Confirm Layout",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Confirm",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlipLayoutEditorInspectorBar(
    selectedElement: FlipClockElementId,
    orientationLayout: FlipClockOrientationLayout,
    effective24Hour: Boolean,
    showSeconds: Boolean,
    showDate: Boolean,
    showWeekday: Boolean,
    showWeather: Boolean,
    showBattery: Boolean,
    dateFormatPattern: String,
    onSelectElement: (FlipClockElementId) -> Unit,
    onUpdateOrientationLayout: (FlipClockOrientationLayout) -> Unit,
    onSetShowSeconds: (Boolean) -> Unit,
    onSetShowDate: (Boolean) -> Unit,
    onSetShowWeekday: (Boolean) -> Unit,
    onSetShowWeather: (Boolean) -> Unit,
    onSetShowBattery: (Boolean) -> Unit,
    onSetDateFormatPattern: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = FlipClockPalette.PanelDark.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, FlipClockPalette.PanelOutline),
        modifier = modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("flip_layout_editor_inspector")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FlipClockElementId.entries.forEach { element ->
                    val selected = selectedElement == element
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectElement(element) },
                        label = { Text(element.label, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FlipClockPalette.PrimaryCta,
                            selectedLabelColor = FlipClockPalette.PrimaryCtaText,
                            containerColor = FlipClockPalette.PanelElevated,
                            labelColor = FlipClockPalette.SubTextGrey
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = FlipClockPalette.PanelOutline,
                            selectedBorderColor = FlipClockPalette.PrimaryCta
                        ),
                        modifier = Modifier.testTag("flip_editor_chip_${element.name.lowercase()}")
                    )
                }
            }

            when (selectedElement) {
                FlipClockElementId.HOUR -> {
                    ScaleSliderRow(
                        label = "Hour Size",
                        value = orientationLayout.hourScale,
                        onValueChange = {
                            onUpdateOrientationLayout(
                                orientationLayout.copy(enabled = true, hourScale = it)
                            )
                        },
                        testTag = "flip_editor_hour_size_slider"
                    )
                }

                FlipClockElementId.MINUTE -> {
                    ScaleSliderRow(
                        label = "Minute Size",
                        value = orientationLayout.minuteScale,
                        onValueChange = {
                            onUpdateOrientationLayout(
                                orientationLayout.copy(enabled = true, minuteScale = it)
                            )
                        },
                        testTag = "flip_editor_minute_size_slider"
                    )
                }

                FlipClockElementId.AM_PM -> {
                    if (effective24Hour) {
                        Text(
                            text = "AM/PM is hidden while 24-Hour Time Format is active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = FlipClockPalette.SubTextGrey
                        )
                    } else {
                        AlignmentSelectorRow(
                            label = "AM/PM Alignment",
                            selected = orientationLayout.amPmAlign,
                            onSelect = {
                                onUpdateOrientationLayout(
                                    orientationLayout.copy(enabled = true, amPmAlign = it)
                                )
                            }
                        )
                    }
                }

                FlipClockElementId.SECONDS -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EditorVisibilityToggle(
                            label = "Show Seconds",
                            checked = showSeconds,
                            onCheckedChange = onSetShowSeconds,
                            testTag = "flip_editor_toggle_seconds"
                        )
                    }
                    if (showSeconds) {
                        AlignmentSelectorRow(
                            label = "Seconds Align",
                            selected = orientationLayout.secondsAlign,
                            onSelect = {
                                onUpdateOrientationLayout(
                                    orientationLayout.copy(enabled = true, secondsAlign = it)
                                )
                            }
                        )
                    }
                }

                FlipClockElementId.DATE -> {
                    EditorVisibilityToggle(
                        label = "Show Date",
                        checked = showDate,
                        onCheckedChange = onSetShowDate,
                        testTag = "flip_editor_toggle_date"
                    )
                    if (showDate) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FlipDateFormat.entries.forEach { fmt ->
                                val selected = dateFormatPattern == fmt.pattern
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSetDateFormatPattern(fmt.pattern) },
                                    label = { Text(fmt.label, maxLines = 1) },
                                    colors = editorChipColors()
                                )
                            }
                        }
                    }
                }

                FlipClockElementId.WEEKDAY -> {
                    EditorVisibilityToggle(
                        label = "Show Weekday",
                        checked = showWeekday,
                        onCheckedChange = onSetShowWeekday,
                        testTag = "flip_editor_toggle_weekday"
                    )
                    if (showWeekday) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FlipWeekdayStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = orientationLayout.weekdayStyle == style,
                                    onClick = {
                                        onUpdateOrientationLayout(
                                            orientationLayout.copy(enabled = true, weekdayStyle = style)
                                        )
                                    },
                                    label = { Text(style.label) },
                                    colors = editorChipColors()
                                )
                            }
                        }
                    }
                }

                FlipClockElementId.WEATHER -> {
                    EditorVisibilityToggle(
                        label = "Show Weather",
                        checked = showWeather,
                        onCheckedChange = onSetShowWeather,
                        testTag = "flip_editor_toggle_weather"
                    )
                    if (showWeather) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FlipWeatherStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = orientationLayout.weatherStyle == style,
                                    onClick = {
                                        onUpdateOrientationLayout(
                                            orientationLayout.copy(enabled = true, weatherStyle = style)
                                        )
                                    },
                                    label = { Text(style.label) },
                                    colors = editorChipColors()
                                )
                            }
                        }
                    }
                }

                FlipClockElementId.BATTERY -> {
                    EditorVisibilityToggle(
                        label = "Show Battery %",
                        checked = showBattery,
                        onCheckedChange = onSetShowBattery,
                        testTag = "flip_editor_toggle_battery"
                    )
                    if (showBattery) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FlipBatteryStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = orientationLayout.batteryStyle == style,
                                    onClick = {
                                        onUpdateOrientationLayout(
                                            orientationLayout.copy(enabled = true, batteryStyle = style)
                                        )
                                    },
                                    label = { Text(style.label) },
                                    colors = editorChipColors()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScaleSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "$label (${(value * 100).roundToInt()}%)",
            style = MaterialTheme.typography.labelMedium,
            color = FlipClockPalette.AccentSoft,
            modifier = Modifier.width(120.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Slider(
            value = value.coerceIn(
                FlipClockOrientationLayout.MIN_DIGIT_SCALE,
                FlipClockOrientationLayout.MAX_DIGIT_SCALE
            ),
            onValueChange = onValueChange,
            valueRange = FlipClockOrientationLayout.MIN_DIGIT_SCALE..FlipClockOrientationLayout.MAX_DIGIT_SCALE,
            colors = SliderDefaults.colors(
                thumbColor = FlipClockPalette.PrimaryCta,
                activeTrackColor = FlipClockPalette.PrimaryCta,
                inactiveTrackColor = FlipClockPalette.PanelOutline
            ),
            modifier = Modifier
                .weight(1f)
                .testTag(testTag)
        )
    }
}

@Composable
private fun AlignmentSelectorRow(
    label: String,
    selected: FlipClockElementAlign,
    onSelect: (FlipClockElementAlign) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = FlipClockPalette.SubTextGrey,
            maxLines = 1
        )
        FlipClockElementAlign.entries.forEach { align ->
            FilterChip(
                selected = selected == align,
                onClick = { onSelect(align) },
                label = { Text(align.label, fontSize = 12.sp) },
                colors = editorChipColors()
            )
        }
    }
}

@Composable
private fun EditorVisibilityToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = FlipClockPalette.AccentSoft
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = FlipClockPalette.SageAccent,
                uncheckedThumbColor = FlipClockPalette.SubTextGrey,
                uncheckedTrackColor = FlipClockPalette.PanelElevated
            )
        )
    }
}

@Composable
private fun editorChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = FlipClockPalette.SageAccent,
    selectedLabelColor = FlipClockPalette.AccentSoft,
    containerColor = FlipClockPalette.PanelElevated,
    labelColor = FlipClockPalette.SubTextGrey
)
