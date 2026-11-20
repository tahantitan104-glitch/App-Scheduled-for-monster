package com.example.presentation.insights

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.domain.model.DailyAppUsageSummary
import com.example.domain.model.LockSchedule
import com.example.domain.model.SessionHistoryRecord
import com.example.presentation.history.HistoryScreen
import com.example.presentation.schedule.ScheduleScreen

enum class InsightsSection {
    HISTORY,
    SCHEDULE
}

@Composable
fun InsightsScreen(
    records: List<SessionHistoryRecord>,
    schedules: List<LockSchedule>,
    dailyAppUsage: DailyAppUsageSummary,
    onGrantUsageAccess: () -> Unit,
    onAddSchedule: () -> Unit,
    onEditSchedule: (LockSchedule) -> Unit,
    onToggleSchedule: (LockSchedule, Boolean) -> Unit,
    onDeleteSchedule: (Long) -> Unit,
    modifier: Modifier = Modifier,
    initialSection: InsightsSection = InsightsSection.HISTORY
) {
    val palette = LocalResolveExtendedPalette.current
    var activeSection by rememberSaveable { mutableStateOf(initialSection) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("insights_hub_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Segmented toggle organizing History & Schedule cleanly under Insights
        Box(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.cardBottom)
                    .border(1.dp, palette.cardBorder, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                InsightsSegmentTab(
                    label = "History (${records.size})",
                    selected = activeSection == InsightsSection.HISTORY,
                    onClick = { activeSection = InsightsSection.HISTORY },
                    testTag = "insights_tab_history",
                    modifier = Modifier.weight(1f)
                )
                InsightsSegmentTab(
                    label = "Schedule (${schedules.size})",
                    selected = activeSection == InsightsSection.SCHEDULE,
                    onClick = { activeSection = InsightsSection.SCHEDULE },
                    testTag = "insights_tab_schedule",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        AnimatedContent(
            targetState = activeSection,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(160))
            },
            label = "insights_section_switch",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { section ->
            when (section) {
                InsightsSection.HISTORY -> {
                    HistoryScreen(
                        records = records,
                        dailyAppUsage = dailyAppUsage,
                        onGrantUsageAccess = onGrantUsageAccess
                    )
                }
                InsightsSection.SCHEDULE -> {
                    ScheduleScreen(
                        schedules = schedules,
                        onAddSchedule = onAddSchedule,
                        onEditSchedule = onEditSchedule,
                        onToggleSchedule = onToggleSchedule,
                        onDeleteSchedule = onDeleteSchedule
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightsSegmentTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalResolveExtendedPalette.current
    val bg by animateColorAsState(
        targetValue = if (selected) palette.accentSoftContainer else Color.Transparent,
        animationSpec = tween(180),
        label = "insights_tab_bg_$testTag"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) palette.accent else palette.textSecondary,
        animationSpec = tween(180),
        label = "insights_tab_fg_$testTag"
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) palette.accent.copy(alpha = 0.35f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = fg,
            maxLines = 1
        )
    }
}
