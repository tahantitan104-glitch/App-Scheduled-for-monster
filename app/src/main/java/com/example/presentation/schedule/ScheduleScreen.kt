package com.example.presentation.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.LocalResolveBottomDockClearance
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.domain.model.DurationSelection
import com.example.domain.model.LockSchedule
import com.example.domain.model.ScheduleType
import com.example.domain.scheduling.ScheduleCalculator
import com.example.presentation.components.FeatureTipNote
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.pressScale
import com.example.presentation.components.resolveGlassCard
import com.example.presentation.components.resolveSwitchColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ScheduleScreen(
    schedules: List<LockSchedule>,
    onAddSchedule: () -> Unit,
    onEditSchedule: (LockSchedule) -> Unit,
    onToggleSchedule: (LockSchedule, Boolean) -> Unit,
    onDeleteSchedule: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalResolveExtendedPalette.current
    val dockClearance = LocalResolveBottomDockClearance.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("schedule_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 6.dp,
                bottom = 92.dp + dockClearance
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Schedules",
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.textPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Automatic restriction start times in local timezone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
                Spacer(Modifier.height(6.dp))
                FeatureTipNote(
                    text = "Tip: Tap any schedule to edit; overlaps are checked automatically.",
                    icon = ResolveIcons.Schedule
                )
            }

            if (schedules.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(cornerRadius = 16.dp)
                            .padding(horizontal = 24.dp, vertical = 42.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(palette.surfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ResolveIcons.EventBusy,
                                contentDescription = null,
                                tint = palette.accentStrong,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Text(
                            text = "No schedules yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Tap + to configure an automated One-Time, Daily, or Weekly Lock.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                    }
                }
            } else {
                items(schedules, key = { it.id }) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        onEdit = { onEditSchedule(schedule) },
                        onToggle = { enabled -> onToggleSchedule(schedule, enabled) },
                        onDelete = { onDeleteSchedule(schedule.id) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onAddSchedule,
            containerColor = palette.primaryButton,
            contentColor = palette.onPrimaryButton,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp + dockClearance)
                .border(1.dp, palette.accentSecondary.copy(alpha = 0.42f), CircleShape)
                .testTag("schedule_add_fab")
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Create schedule")
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: LockSchedule,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val durationLabel = DurationSelection.fromTotalMinutes(schedule.lockDurationMinutes).formattedCompact()
    val cadenceLabel = when (schedule.type) {
        ScheduleType.ONE_TIME ->
            schedule.oneTimeDateEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "One-time"
        ScheduleType.DAILY -> "Every day"
        ScheduleType.CUSTOM_WEEKLY -> schedule.selectedDays()
            .sortedBy { it.value }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource, 0.98f)
            .resolveGlassCard(
                cornerRadius = 16.dp,
                borderColor = if (schedule.isEnabled) {
                    palette.champagne.copy(alpha = 0.45f)
                } else {
                    palette.cardBorder
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onEdit
            )
            .padding(18.dp)
            .testTag("schedule_card_${schedule.id}"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (schedule.isEnabled) palette.champagneContainer
                            else palette.controlSurface
                        )
                        .border(
                            1.dp,
                            if (schedule.isEnabled) palette.champagne.copy(alpha = 0.28f) else palette.controlBorder,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ResolveIcons.EventAvailable,
                        contentDescription = null,
                        tint = if (schedule.isEnabled) palette.champagneStrong else palette.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = schedule.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "%02d:%02d • $durationLabel • $cadenceLabel".format(
                            schedule.startHour,
                            schedule.startMinute
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                        color = if (schedule.isEnabled) palette.champagneStrong else palette.textSecondary
                    )
                }
            }

            Switch(
                checked = schedule.isEnabled,
                onCheckedChange = onToggle,
                colors = resolveSwitchColors()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = schedule.nextTriggerWallMs?.let {
                    "Next: ${ScheduleCalculator.formatTriggerTimestamp(it)}"
                } ?: "Paused",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )

            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit schedule",
                        tint = palette.textSecondary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete schedule",
                        tint = palette.error
                    )
                }
            }
        }
    }
}
