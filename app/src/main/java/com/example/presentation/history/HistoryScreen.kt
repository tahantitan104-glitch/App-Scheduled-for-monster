package com.example.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.ResolveColors
import com.example.domain.model.DurationSelection
import com.example.domain.model.LockMode
import com.example.domain.model.SessionHistoryRecord
import com.example.domain.model.SessionOutcome
import com.example.domain.scheduling.ScheduleCalculator
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.resolveGlassCard

@Composable
fun HistoryScreen(
    records: List<SessionHistoryRecord>,
    modifier: Modifier = Modifier
) {
    val completedCount = remember(records) { records.count { it.outcome == SessionOutcome.COMPLETED } }
    val totalFocusMinutes = remember(records) {
        records.filter { it.outcome == SessionOutcome.COMPLETED }.sumOf { it.lockDurationMinutes }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ResolveColors.ForestDeep),
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
                    text = "History",
                    style = MaterialTheme.typography.headlineLarge,
                    color = ResolveColors.TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Locally recorded session outcomes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ResolveColors.TextSecondary
                )
            }

            if (records.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(
                                cornerRadius = 20.dp,
                                borderColor = ResolveColors.MintAccent.copy(alpha = 0.25f)
                            )
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        HistoryStatColumn(label = "TOTAL", value = records.size.toString())
                        HistoryStatColumn(label = "COMPLETED", value = completedCount.toString())
                        HistoryStatColumn(
                            label = "FOCUS TIME",
                            value = if (totalFocusMinutes > 0L) {
                                DurationSelection.fromTotalMinutes(totalFocusMinutes).formattedCompact()
                            } else "0m"
                        )
                    }
                }
            }

            if (records.isEmpty()) {
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
                                imageVector = ResolveIcons.History,
                                contentDescription = null,
                                tint = ResolveColors.MintAccent,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Text(
                            text = "No sessions recorded yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = ResolveColors.TextPrimary
                        )
                        Text(
                            text = "Completed and recovered sessions appear here automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ResolveColors.TextSecondary
                        )
                    }
                }
            } else {
                items(records, key = { it.id }) { record ->
                    HistoryCard(record)
                }
            }
        }
    }
}

@Composable
private fun HistoryStatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily),
            color = ResolveColors.MintAccent
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ResolveColors.TextSecondary
        )
    }
}

@Composable
private fun HistoryCard(record: SessionHistoryRecord) {
    val modeLabel = when (record.mode) {
        LockMode.INSTANT -> "Instant Lock"
        LockMode.DELAYED -> "Delayed Lock"
        LockMode.SCHEDULED -> "Schedule Lock"
    }
    val (statusLabel, statusColor, statusBg) = when (record.outcome) {
        SessionOutcome.COMPLETED -> Triple("Completed", ResolveColors.MintAccent, ResolveColors.MintSoftContainer)
        SessionOutcome.RECOVERED -> Triple("Recovered", ResolveColors.AmberWarn, ResolveColors.AmberContainer)
        SessionOutcome.INTERRUPTED -> Triple("Interrupted", ResolveColors.TextSecondary, ResolveColors.ForestSurfaceElevated)
        SessionOutcome.ENFORCEMENT_FAILED -> Triple("Failed", ResolveColors.ErrorCoral, ResolveColors.ErrorContainer)
    }

    val lockDuration = DurationSelection.fromTotalMinutes(record.lockDurationMinutes).formattedCompact()
    val freeUseDuration = DurationSelection.fromTotalMinutes(record.freeUseDurationMinutes).formattedCompact()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(cornerRadius = 20.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = modeLabel,
                style = MaterialTheme.typography.titleMedium,
                color = ResolveColors.TextPrimary
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(statusBg)
                    .border(1.dp, statusColor.copy(alpha = 0.25f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        Text(
            text = ScheduleCalculator.formatTriggerTimestamp(record.startedAtWallMs),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
            color = ResolveColors.TextSecondary
        )

        val detail = if (record.mode == LockMode.DELAYED) {
            "Free Use: $freeUseDuration  •  Lock: $lockDuration  •  ${record.allowedAppCount} apps"
        } else {
            "Restricted: $lockDuration  •  ${record.allowedAppCount} apps"
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = ResolveColors.TextSecondary
        )
    }
}
