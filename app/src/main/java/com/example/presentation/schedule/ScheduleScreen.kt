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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.ResolveColors
import com.example.domain.model.DurationSelection
import com.example.domain.model.LockSchedule
import com.example.domain.model.ScheduleType
import com.example.domain.scheduling.ScheduleCalculator
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.pressScale
import com.example.presentation.components.resolveGlassCard
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
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ResolveColors.ForestDeep,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddSchedule,
                containerColor = ResolveColors.MintAccent,
                contentColor = ResolveColors.ForestDeep,
                shape = CircleShape,
                modifier = Modifier.testTag("schedule_add_fab")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Create schedule")
            }
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
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Schedules",
                        style = MaterialTheme.typography.headlineLarge,
                        color = ResolveColors.TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Automatic restriction start times in local timezone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ResolveColors.TextSecondary
                    )
                }

                if (schedules.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .resolveGlassCard(cornerRadius = 24.dp)
                                .padding(horizontal = 24.dp, vertical = 42.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(ResolveColors.MintSoftContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = ResolveIcons.EventBusy,
                                    contentDescription = null,
                                    tint = ResolveColors.MintAccent,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Text(
                                text = "No schedules yet",
                                style = MaterialTheme.typography.titleLarge,
                                color = ResolveColors.TextPrimary
                            )
                            Text(
                                text = "Tap + to configure an automated One-Time, Daily, or Weekly Lock.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ResolveColors.TextSecondary
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
                cornerRadius = 22.dp,
                borderColor = if (schedule.isEnabled) {
                    ResolveColors.MintAccent.copy(alpha = 0.32f)
                } else {
                    ResolveColors.ForestOutline
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
                            if (schedule.isEnabled) ResolveColors.MintSoftContainer
                            else ResolveColors.ForestSecondary
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ResolveIcons.EventAvailable,
                        contentDescription = null,
                        tint = if (schedule.isEnabled) ResolveColors.MintAccent else ResolveColors.TextDisabled,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = schedule.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = ResolveColors.TextPrimary
                    )
                    Text(
                        text = "%02d:%02d • $durationLabel • $cadenceLabel".format(
                            schedule.startHour,
                            schedule.startMinute
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                        color = if (schedule.isEnabled) ResolveColors.MintAccent else ResolveColors.TextSecondary
                    )
                }
            }

            Switch(
                checked = schedule.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ResolveColors.ForestDeep,
                    checkedTrackColor = ResolveColors.MintAccent
                )
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
                color = ResolveColors.TextSecondary
            )

            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit schedule",
                        tint = ResolveColors.TextSecondary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete schedule",
                        tint = ResolveColors.ErrorCoral
                    )
                }
            }
        }
    }
}
