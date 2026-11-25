package com.example.presentation.history

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.LocalResolveBottomDockClearance
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.domain.model.DailyAppUsageSummary
import com.example.domain.model.DurationSelection
import com.example.domain.model.LockMode
import com.example.domain.model.SessionHistoryRecord
import com.example.domain.model.SessionOutcome
import com.example.domain.scheduling.ScheduleCalculator
import com.example.presentation.components.FeatureTipNote
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.resolveGlassCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private data class DayFocusStat(
    val date: LocalDate,
    val dayLabel: String,
    val focusMinutes: Long
)

@Composable
fun HistoryScreen(
    records: List<SessionHistoryRecord>,
    modifier: Modifier = Modifier,
    dailyAppUsage: DailyAppUsageSummary = DailyAppUsageSummary(),
    onGrantUsageAccess: () -> Unit = {}
) {
    val completedCount = remember(records) { records.count { it.outcome == SessionOutcome.COMPLETED } }
    val totalFocusMinutes = remember(records) {
        records.filter { it.outcome == SessionOutcome.COMPLETED }.sumOf { it.lockDurationMinutes }
    }
    val completionRatePct = remember(records, completedCount) {
        if (records.isEmpty()) 0 else ((completedCount * 100f) / records.size).toInt().coerceIn(0, 100)
    }

    val zoneId = remember { ZoneId.systemDefault() }
    val weeklyStats = remember(records, zoneId) {
        val today = LocalDate.now(zoneId)
        val byDate = records
            .filter { it.outcome == SessionOutcome.COMPLETED }
            .groupBy { Instant.ofEpochMilli(it.endedAtWallMs).atZone(zoneId).toLocalDate() }
        (6 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val mins = byDate[date]?.sumOf { it.lockDurationMinutes } ?: 0L
            val label = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
            DayFocusStat(date = date, dayLabel = label, focusMinutes = mins)
        }
    }

    val instantCount = remember(records) { records.count { it.mode == LockMode.INSTANT } }
    val delayedCount = remember(records) { records.count { it.mode == LockMode.DELAYED } }
    val scheduleCount = remember(records) { records.count { it.mode == LockMode.SCHEDULED } }

    val palette = LocalResolveExtendedPalette.current
    val dockClearance = LocalResolveBottomDockClearance.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("insights_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        val useTwoColumnAnalytics = maxWidth >= 640.dp
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize()
                .testTag("insights_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 16.dp + dockClearance
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Focus History",
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.textPrimary,
                    modifier = Modifier.testTag("insights_header")
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Local focus analytics, usage patterns & session log.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
                Spacer(Modifier.height(6.dp))
                FeatureTipNote(
                    text = "Note: All focus analytics and session logs remain private and local on your device.",
                    icon = ResolveIcons.Shield
                )
            }

            if (records.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(
                                cornerRadius = 16.dp,
                                borderColor = palette.cardBorder
                            )
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        HistoryStatColumn(label = "SESSIONS", value = records.size.toString())
                        HistoryStatColumn(label = "COMPLETED", value = "$completionRatePct%")
                        HistoryStatColumn(
                            label = "FOCUS TIME",
                            value = if (totalFocusMinutes > 0L) {
                                DurationSelection.fromTotalMinutes(totalFocusMinutes).formattedCompact()
                            } else "0m"
                        )
                    }
                }
            }

            if (useTwoColumnAnalytics && records.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        WeeklyFocusChartCard(
                            weeklyStats = weeklyStats,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        ModeSplitInsightsCard(
                            total = records.size,
                            instantCount = instantCount,
                            delayedCount = delayedCount,
                            scheduleCount = scheduleCount,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            } else {
                // 7-Day Focus Trend Bar Chart
                item {
                    WeeklyFocusChartCard(weeklyStats = weeklyStats)
                }

                // Focus Mode Mix Distribution
                if (records.isNotEmpty()) {
                    item {
                        ModeSplitInsightsCard(
                            total = records.size,
                            instantCount = instantCount,
                            delayedCount = delayedCount,
                            scheduleCount = scheduleCount
                        )
                    }
                }
            }

            // Today's Screen Time Distribution
            item {
                InsightsUsageDistributionCard(
                    summary = dailyAppUsage,
                    onGrantUsageAccess = onGrantUsageAccess
                )
            }

            item {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "SESSION LOG",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.25.sp),
                    color = palette.textSecondary
                )
            }

            if (records.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(cornerRadius = 16.dp)
                            .padding(horizontal = 24.dp, vertical = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(palette.surfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ResolveIcons.History,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "No sessions recorded yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Completed and recovered sessions appear here automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
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
private fun WeeklyFocusChartCard(
    weeklyStats: List<DayFocusStat>,
    modifier: Modifier = Modifier
) {
    val palette = LocalResolveExtendedPalette.current
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(weeklyStats) {
        revealed = true
    }
    val maxMins = remember(weeklyStats) {
        weeklyStats.maxOfOrNull { it.focusMinutes }?.coerceAtLeast(30L) ?: 30L
    }
    val weeklyTotal = remember(weeklyStats) { weeklyStats.sumOf { it.focusMinutes } }

    val peakMinutes = remember(weeklyStats) {
        weeklyStats.maxOfOrNull { it.focusMinutes }?.takeIf { it > 0L }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .resolveGlassCard(
                cornerRadius = 16.dp,
                borderColor = palette.cardBorder
            )
            .padding(16.dp)
            .testTag("insights_weekly_chart_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "7-Day Focus Trend",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary
                )
                Text(
                    text = "Completed minutes per day",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
            }
            Text(
                text = if (weeklyTotal > 0L) {
                    DurationSelection.fromTotalMinutes(weeklyTotal).formattedCompact()
                } else "0m",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = palette.textPrimary
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(106.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            weeklyStats.forEachIndexed { index, day ->
                val targetRatio = if (revealed && day.focusMinutes > 0L) {
                    (day.focusMinutes.toFloat() / maxMins.toFloat()).coerceIn(0.10f, 1f)
                } else {
                    0.05f
                }
                val barHeightFraction by animateFloatAsState(
                    targetValue = targetRatio,
                    animationSpec = tween(
                        durationMillis = 480 + index * 45,
                        easing = FastOutSlowInEasing
                    ),
                    label = "weekly_bar_$index"
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val isPeakDay = peakMinutes != null && day.focusMinutes == peakMinutes
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.56f)
                                .fillMaxHeight(barHeightFraction)
                                .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                .background(
                                    when {
                                        isPeakDay -> palette.champagne
                                        day.focusMinutes > 0L -> palette.accentStrong
                                        else -> palette.divider
                                    }
                                )
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = day.dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            peakMinutes != null && day.focusMinutes == peakMinutes -> palette.champagneStrong
                            day.focusMinutes > 0L -> palette.textPrimary
                            else -> palette.textSecondary
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeSplitInsightsCard(
    total: Int,
    instantCount: Int,
    delayedCount: Int,
    scheduleCount: Int,
    modifier: Modifier = Modifier
) {
    val palette = LocalResolveExtendedPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .resolveGlassCard(cornerRadius = 16.dp)
            .padding(16.dp)
            .testTag("insights_mode_split_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Focus Mode Mix",
            style = MaterialTheme.typography.titleMedium,
            color = palette.textPrimary
        )

        ModeSplitBarRow("Instant Lock", instantCount, total, barColor = palette.accentStrong)
        ModeSplitBarRow("Delayed Lock", delayedCount, total, barColor = palette.slateTeal)
        ModeSplitBarRow("Schedule Lock", scheduleCount, total, barColor = palette.champagne)
    }
}

@Composable
private fun ModeSplitBarRow(
    label: String,
    count: Int,
    total: Int,
    barColor: Color
) {
    val palette = LocalResolveExtendedPalette.current
    val ratio = if (total <= 0) 0f else (count.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val animatedRatio by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "mode_split_$label"
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
            Text(
                text = "$count (${(ratio * 100).toInt()}%)",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = palette.textPrimary
            )
        }
        LinearProgressIndicator(
            progress = { animatedRatio },
            color = barColor,
            trackColor = palette.divider,
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
        )
    }
}

@Composable
private fun InsightsUsageDistributionCard(
    summary: DailyAppUsageSummary,
    onGrantUsageAccess: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(cornerRadius = 16.dp)
            .padding(16.dp)
            .testTag("insights_usage_summary_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "App Usage Share",
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary
            )
            if (summary.hasUsagePermission) {
                Text(
                    text = formatInsightsDuration(summary.totalScreenTimeMs),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                    color = palette.slateTealStrong
                )
            }
        }

        if (!summary.hasUsagePermission) {
            Text(
                text = "Enable Usage Access to include real app usage analytics in Insights.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
            OutlinedButton(
                onClick = onGrantUsageAccess,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.slateTeal.copy(alpha = 0.48f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = palette.slateTealContainer.copy(alpha = 0.72f),
                    contentColor = palette.slateTealStrong
                ),
                modifier = Modifier.testTag("insights_grant_usage_button")
            ) {
                Text("Grant Usage Access")
            }
        } else if (summary.topApps.isEmpty()) {
            Text(
                text = "No other app usage recorded today.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        } else {
            val totalMs = summary.totalScreenTimeMs.coerceAtLeast(1L)
            summary.topApps.take(5).forEach { app ->
                val pct = ((app.foregroundTimeMs * 100f) / totalMs).toInt().coerceIn(0, 100)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${formatInsightsDuration(app.foregroundTimeMs)} ($pct%)",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = palette.slateTealStrong
                    )
                }
            }
        }
    }
}

private fun formatInsightsDuration(durationMs: Long): String {
    val mins = (durationMs / 60_000L).coerceAtLeast(0L)
    val h = mins / 60L
    val m = mins % 60L
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

@Composable
private fun HistoryStatColumn(label: String, value: String) {
    val palette = LocalResolveExtendedPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily),
            color = palette.textPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
    }
}

@Composable
private fun HistoryCard(record: SessionHistoryRecord) {
    val palette = LocalResolveExtendedPalette.current
    val modeLabel = when (record.mode) {
        LockMode.INSTANT -> "Instant Lock"
        LockMode.DELAYED -> "Delayed Lock"
        LockMode.SCHEDULED -> "Schedule Lock"
    }
    val (statusLabel, statusColor, statusBg) = when (record.outcome) {
        SessionOutcome.COMPLETED -> Triple("Completed", palette.onAccent, palette.success)
        SessionOutcome.RECOVERED -> Triple("Recovered", palette.warn, palette.warnContainer)
        SessionOutcome.INTERRUPTED -> Triple("Interrupted", palette.textSecondary, palette.cardBottom)
        SessionOutcome.ENFORCEMENT_FAILED -> Triple("Failed", palette.error, palette.errorContainer)
    }

    val lockDuration = DurationSelection.fromTotalMinutes(record.lockDurationMinutes).formattedCompact()
    val freeUseDuration = DurationSelection.fromTotalMinutes(record.freeUseDurationMinutes).formattedCompact()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(cornerRadius = 16.dp)
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
                color = palette.textPrimary
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
            color = palette.textSecondary
        )

        val detail = if (record.mode == LockMode.DELAYED) {
            "Free Use: $freeUseDuration  •  Lock: $lockDuration  •  ${record.allowedAppCount} apps"
        } else {
            "Restricted: $lockDuration  •  ${record.allowedAppCount} apps"
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary
        )
    }
}
