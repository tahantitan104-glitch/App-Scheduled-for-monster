package com.example.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.ResolveColors
import com.example.domain.model.DurationSelection
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class DurationPreset(val label: String, val totalMinutes: Long)

private val QUICK_PRESETS = listOf(
    DurationPreset("10m", 10L),
    DurationPreset("15m", 15L),
    DurationPreset("30m", 30L),
    DurationPreset("45m", 45L),
    DurationPreset("1h", 60L),
    DurationPreset("2h", 120L),
    DurationPreset("3h", 180L),
    DurationPreset("4h", 240L)
)

@Composable
fun DurationSelector(
    label: String,
    duration: DurationSelection,
    onHoursChanged: (Int) -> Unit,
    onMinutesChanged: (Int) -> Unit,
    onDurationReplaced: (DurationSelection) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "duration"
) {
    var showNumericDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val displayInteraction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .resolveGlassCard(
                cornerRadius = 24.dp,
                borderColor = ResolveColors.ForestBorderGlow.copy(alpha = 0.65f)
            )
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ResolveColors.MintSoftContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ResolveIcons.Timer,
                        contentDescription = null,
                        tint = ResolveColors.MintAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = ResolveColors.TextPrimary
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ResolveColors.MintSoftContainer)
                    .border(1.dp, ResolveColors.MintAccent.copy(alpha = 0.35f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = duration.formattedCompact(),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                    color = ResolveColors.MintAccent
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Glowing digital duration card with direct numeric entry affordance
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(displayInteraction, pressedScale = 0.985f)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ResolveColors.ForestDeep,
                            ResolveColors.ForestSecondary
                        )
                    )
                )
                .border(1.dp, ResolveColors.MintAccent.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = displayInteraction,
                    indication = androidx.compose.material3.ripple()
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showNumericDialog = true
                }
                .padding(vertical = 14.dp, horizontal = 18.dp)
                .testTag("${testTagPrefix}_display"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.width(34.dp))
                Text(
                    text = duration.formattedDisplay(),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = ResolveColors.TextPrimary
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ResolveColors.MintSoftContainer.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit duration directly",
                        tint = ResolveColors.MintAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Independent Hours & Minutes Drag Wheels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IndependentWheelPicker(
                title = "HOURS",
                range = 0..DurationSelection.MAX_HOURS,
                selectedValue = duration.normalizedHours,
                onValueSelected = onHoursChanged,
                modifier = Modifier
                    .weight(1f)
                    .testTag("${testTagPrefix}_hours_wheel")
            )

            Text(
                text = ":",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = ResolveColors.MintAccent.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 16.dp)
            )

            IndependentWheelPicker(
                title = "MINUTES",
                range = 0..DurationSelection.MAX_MINUTES,
                selectedValue = duration.normalizedMinutes,
                onValueSelected = onMinutesChanged,
                modifier = Modifier
                    .weight(1f)
                    .testTag("${testTagPrefix}_minutes_wheel")
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Presets with smooth color transition & haptic feedback
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(QUICK_PRESETS, key = { it.label }) { preset ->
                val isSelected = duration.totalMinutes == preset.totalMinutes
                val chipContainer by animateColorAsState(
                    targetValue = if (isSelected) ResolveColors.MintSoftContainer else ResolveColors.ForestSecondary,
                    animationSpec = tween(200),
                    label = "preset_bg"
                )
                val chipLabel by animateColorAsState(
                    targetValue = if (isSelected) ResolveColors.MintAccent else ResolveColors.TextSecondary,
                    animationSpec = tween(200),
                    label = "preset_fg"
                )

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDurationReplaced(DurationSelection.fromTotalMinutes(preset.totalMinutes))
                    },
                    label = {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = chipContainer,
                        labelColor = chipLabel,
                        selectedContainerColor = chipContainer,
                        selectedLabelColor = chipLabel
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = ResolveColors.ForestOutline,
                        selectedBorderColor = ResolveColors.MintAccent.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.testTag("${testTagPrefix}_preset_${preset.label}")
                )
            }
        }
    }

    if (showNumericDialog) {
        DirectDurationEntryDialog(
            initialDuration = duration,
            onDismiss = { showNumericDialog = false },
            onConfirm = { newDuration ->
                onDurationReplaced(newDuration)
                showNumericDialog = false
            }
        )
    }
}

@Composable
private fun IndependentWheelPicker(
    title: String,
    range: IntRange,
    selectedValue: Int,
    onValueSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val values = remember(range) { range.toList() }
    val itemHeight = 46.dp
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedValue.coerceIn(0, values.lastIndex))
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val latestOnSelected by rememberUpdatedState(onValueSelected)
    val latestSelectedValue by rememberUpdatedState(selectedValue)

    val centeredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                latestSelectedValue
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2
                    abs(itemCenter - viewportCenter)
                }?.index ?: latestSelectedValue
            }
        }
    }

    // Subtle haptic tick when centered item shifts while user drags
    LaunchedEffect(centeredIndex) {
        if (listState.isScrollInProgress) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Sync external state changes (presets or direct numeric input) into this wheel only
    LaunchedEffect(selectedValue) {
        if (!listState.isScrollInProgress && centeredIndex != selectedValue) {
            runCatching {
                listState.animateScrollToItem(selectedValue.coerceIn(0, values.lastIndex))
            }
        }
    }

    // Emit changes from user drag/fling for THIS wheel only (never affects sibling wheel)
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to centeredIndex }
            .distinctUntilChanged()
            .collect { (scrolling, index) ->
                val clamped = index.coerceIn(0, values.lastIndex)
                if (!scrolling && clamped != latestSelectedValue) {
                    latestOnSelected(values[clamped])
                }
            }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = ResolveColors.TextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight * 3)
                .clip(RoundedCornerShape(18.dp))
                .background(ResolveColors.ForestDeep.copy(alpha = 0.72f))
                .border(1.dp, ResolveColors.ForestOutline, RoundedCornerShape(18.dp))
        ) {
            // Center selection lens band
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(itemHeight)
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                ResolveColors.MintSoftContainer.copy(alpha = 0.65f),
                                ResolveColors.MintSoftContainer.copy(alpha = 0.92f),
                                ResolveColors.MintSoftContainer.copy(alpha = 0.65f)
                            )
                        )
                    )
                    .border(1.dp, ResolveColors.MintAccent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            )

            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(vertical = itemHeight),
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(values, key = { it }) { value ->
                    val distance = abs(value - centeredIndex)
                    val isCenter = distance == 0
                    val itemAlpha = when (distance) {
                        0 -> 1f
                        1 -> 0.58f
                        else -> 0.30f
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                coroutineScope.launch {
                                    runCatching { listState.animateScrollToItem(value) }
                                    latestOnSelected(value)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "%02d".format(value),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = if (isCenter) 23.sp else 16.sp,
                                fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                                fontFeatureSettings = "tnum"
                            ),
                            color = if (isCenter) ResolveColors.MintBright else ResolveColors.TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.alpha(itemAlpha)
                        )
                    }
                }
            }

            // Top and bottom depth vignette fade
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(ResolveColors.ForestDeep.copy(alpha = 0.95f), Color.Transparent)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, ResolveColors.ForestDeep.copy(alpha = 0.95f))
                        )
                    )
            )
        }
    }
}

@Composable
private fun DirectDurationEntryDialog(
    initialDuration: DurationSelection,
    onDismiss: () -> Unit,
    onConfirm: (DurationSelection) -> Unit
) {
    var hoursText by remember { mutableStateOf(initialDuration.normalizedHours.toString()) }
    var minutesText by remember { mutableStateOf(initialDuration.normalizedMinutes.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ResolveColors.ForestSurface,
        title = {
            Text(
                text = "Set Duration",
                style = MaterialTheme.typography.titleLarge,
                color = ResolveColors.TextPrimary
            )
        },
        text = {
            ProvideSafeTextToolbar {
                CleanupTextInteractionsOnDispose()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter hours (0–23) and minutes (0–59).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ResolveColors.TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = hoursText,
                            onValueChange = { input ->
                                if (input.length <= 2 && input.all { it.isDigit() }) {
                                    hoursText = input
                                    errorMessage = null
                                }
                            },
                            label = { Text("Hours") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("direct_hours_input")
                        )
                        OutlinedTextField(
                            value = minutesText,
                            onValueChange = { input ->
                                if (input.length <= 2 && input.all { it.isDigit() }) {
                                    minutesText = input
                                    errorMessage = null
                                }
                            },
                            label = { Text("Minutes") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("direct_minutes_input")
                        )
                    }
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ResolveColors.ErrorCoral
                        )
                    }
                }
            }
        },
        confirmButton = {
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            val textToolbar = androidx.compose.ui.platform.LocalTextToolbar.current
            val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            TextButton(
                onClick = {
                    val h = hoursText.ifBlank { "0" }.toIntOrNull()
                    val m = minutesText.ifBlank { "0" }.toIntOrNull()
                    when {
                        h == null || h !in 0..DurationSelection.MAX_HOURS -> {
                            errorMessage = "Hours must be between 0 and 23."
                        }
                        m == null || m !in 0..DurationSelection.MAX_MINUTES -> {
                            errorMessage = "Minutes must be between 0 and 59."
                        }
                        h == 0 && m == 0 -> {
                            errorMessage = "Duration must be at least 1 minute."
                        }
                        else -> {
                            dismissTextInteractions(focusManager, textToolbar, keyboardController)
                            onConfirm(DurationSelection(hours = h, minutes = m))
                        }
                    }
                },
                modifier = Modifier.testTag("direct_duration_confirm")
            ) {
                Text("Apply", color = ResolveColors.MintAccent)
            }
        },
        dismissButton = {
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            val textToolbar = androidx.compose.ui.platform.LocalTextToolbar.current
            val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            TextButton(
                onClick = {
                    dismissTextInteractions(focusManager, textToolbar, keyboardController)
                    onDismiss()
                }
            ) {
                Text("Cancel", color = ResolveColors.TextSecondary)
            }
        }
    )
}
