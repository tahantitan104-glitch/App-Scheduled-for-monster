package com.example.presentation.analysis

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.domain.model.CompatibilityItemStatus
import com.example.domain.model.DeviceAnalysisReport
import com.example.domain.model.OverallCompatibilityResult
import com.example.domain.model.ProtectionRecoveryAction
import com.example.domain.scheduling.ScheduleCalculator
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.resolveGlassCard

@Composable
fun DeviceAnalysisScreen(
    report: DeviceAnalysisReport,
    onRunAnalysis: () -> Unit,
    onRefreshCapabilities: () -> Unit,
    onExecuteAction: (ProtectionRecoveryAction) -> Unit,
    onOpenDeviceOwnerGuide: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    focusSection: String = "summary"
) {
    val palette = LocalResolveExtendedPalette.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        if (report.analyzedAtWallMs == 0L) {
            onRunAnalysis()
        }
    }

    LaunchedEffect(focusSection, report.analyzedAtWallMs) {
        val targetKey = when (focusSection.lowercase()) {
            "status", "summary" -> "summary"
            "analysis", "compatibility" -> "compatibility"
            "hardware" -> "hardware"
            "diagnostics", "security" -> "security"
            "repair" -> if (report.setupRepairItems.isNotEmpty()) "repair" else "summary"
            else -> null
        }
        if (targetKey != null && targetKey != "summary") {
            val targetIdx = listState.layoutInfo.totalItemsCount.let {
                val keys = buildList {
                    add("header")
                    add("summary")
                    if (report.setupRepairItems.isNotEmpty()) add("repair")
                    if (report.hardware != null) add("hardware")
                    if (report.compatibilityChecks.isNotEmpty()) add("compatibility")
                    if (report.securityChecks.isNotEmpty()) add("security")
                    if (report.deviceManagement != null) add("management")
                }
                keys.indexOf(targetKey)
            }
            if (targetIdx >= 0) {
                runCatching { listState.animateScrollToItem(targetIdx) }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshCapabilities()
                onRunAnalysis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("device_analysis_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .testTag("device_analysis_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("device_analysis_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = palette.textPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Device Analysis",
                            style = MaterialTheme.typography.headlineMedium,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Real OS hardware & RESOLVE compatibility inspection",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                }
            }

            // Overall Result & Run Trigger Card
            item(key = "summary") {
                val (badgeColor, badgeBg) = when (report.overallResult) {
                    OverallCompatibilityResult.READY -> palette.onAccent to palette.success
                    OverallCompatibilityResult.ACTION_REQUIRED -> palette.error to palette.errorContainer
                    OverallCompatibilityResult.LIMITED_COMPATIBILITY -> palette.warn to palette.warnContainer
                    OverallCompatibilityResult.ANALYSIS_INCOMPLETE -> palette.warn to palette.warnContainer
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .resolveGlassCard(
                            cornerRadius = 16.dp,
                            borderColor = badgeColor.copy(alpha = 0.42f)
                        )
                        .padding(18.dp)
                        .testTag("device_analysis_summary_card"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = report.overallResult.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = badgeColor,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(badgeBg)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                                .testTag("device_analysis_overall_badge")
                        )
                        if (report.analyzedAtWallMs > 0L) {
                            Text(
                                text = ScheduleCalculator.formatTriggerTimestamp(report.analyzedAtWallMs),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = palette.textMuted
                            )
                        }
                    }

                    Text(
                        text = report.summaryMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textPrimary
                    )

                    Button(
                        onClick = onRunAnalysis,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.primaryButton,
                            contentColor = palette.onPrimaryButton
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("run_device_analysis_button")
                    ) {
                        Icon(
                            imageVector = ResolveIcons.Devices,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Run Device Analysis", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            // Section 1: Setup & Repair (Action Required items)
            if (report.setupRepairItems.isNotEmpty()) {
                item(key = "repair") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(
                                cornerRadius = 16.dp,
                                borderColor = palette.warn.copy(alpha = 0.42f)
                            )
                            .padding(16.dp)
                            .testTag("device_analysis_repair_card"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Setup & Repair Actions",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary
                        )
                        report.setupRepairItems.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(palette.surfaceSecondary)
                                    .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = item.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textSecondary
                                    )
                                }
                                if (item.actionLabel != null && item.actionType != ProtectionRecoveryAction.NONE) {
                                    OutlinedButton(
                                        onClick = { onExecuteAction(item.actionType) },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, palette.accent),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = palette.accent,
                                            contentColor = palette.onAccent
                                        ),
                                        modifier = Modifier.testTag("repair_fix_${item.id}")
                                    ) {
                                        Text(item.actionLabel)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Hardware & OS Profile
            report.hardware?.let { hw ->
                item(key = "hardware") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(cornerRadius = 16.dp)
                            .padding(16.dp)
                            .testTag("device_analysis_hardware_card"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Hardware & OS Profile",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary
                        )
                        AnalysisKeyValueRow("Manufacturer & Model", "${hw.manufacturer} ${hw.model}".trim())
                        AnalysisKeyValueRow("Device Name", hw.deviceName)
                        AnalysisKeyValueRow("OS Version", "${hw.androidVersion} (API ${hw.apiLevel})")
                        AnalysisKeyValueRow("Build", hw.buildId)
                        AnalysisKeyValueRow("CPU ABI", hw.cpuAbi)
                        AnalysisKeyValueRow("System Memory", "${hw.totalRamLabel} total • ${hw.availableRamLabel} free")
                        AnalysisKeyValueRow("RESOLVE Version", hw.resolveVersion)
                    }
                }
            }

            // Section 3: Compatibility Checklist
            if (report.compatibilityChecks.isNotEmpty()) {
                item(key = "compatibility") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(cornerRadius = 16.dp)
                            .padding(16.dp)
                            .testTag("device_analysis_compatibility_card"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "RESOLVE Compatibility Checks",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary
                        )
                        report.compatibilityChecks.forEach { check ->
                            AnalysisStatusRow(
                                name = check.name,
                                detail = check.detail,
                                status = check.status
                            )
                        }
                    }
                }
            }

            // Section 4: Factual Device Security Overview
            if (report.securityChecks.isNotEmpty()) {
                item(key = "security") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(cornerRadius = 16.dp)
                            .padding(16.dp)
                            .testTag("device_analysis_security_card"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Security Configuration (Local Read)",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Shows real Android security properties readable without root or hardware attestation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                        report.securityChecks.forEach { sec ->
                            AnalysisStatusRow(
                                name = sec.name,
                                detail = sec.detail,
                                status = sec.status
                            )
                        }
                    }
                }
            }

            // Section 5: Optional Device Management (ADB Device Owner)
            report.deviceManagement?.let { dm ->
                item(key = "management") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(cornerRadius = 16.dp)
                            .padding(16.dp)
                            .testTag("device_analysis_management_card"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Device Management (ADB)",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Optional",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(palette.background.copy(alpha = 0.65f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = dm.statusLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.textPrimary
                        )
                        Text(
                            text = dm.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                        OutlinedButton(
                            onClick = onOpenDeviceOwnerGuide,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, palette.cardBorder),
                            modifier = Modifier.testTag("device_analysis_adb_guide_btn")
                        ) {
                            Text("View Optional ADB Guide", color = palette.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisKeyValueRow(label: String, value: String) {
    val palette = LocalResolveExtendedPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Medium
            ),
            color = palette.textPrimary,
            modifier = Modifier.weight(0.55f)
        )
    }
}

@Composable
private fun AnalysisStatusRow(
    name: String,
    detail: String,
    status: CompatibilityItemStatus
) {
    val palette = LocalResolveExtendedPalette.current
    val (fg, bg) = when (status) {
        CompatibilityItemStatus.ACTIVE -> palette.onAccent to palette.success
        CompatibilityItemStatus.ACTION_REQUIRED -> palette.error to palette.errorContainer
        CompatibilityItemStatus.UNAVAILABLE -> palette.warn to palette.warnContainer
        CompatibilityItemStatus.NOT_REQUIRED -> palette.textSecondary to palette.surfaceSecondary
        CompatibilityItemStatus.NOT_VERIFIED -> palette.textMuted to palette.surfaceSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceSecondary)
            .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary
        )
    }
}
