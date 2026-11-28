package com.example.presentation.more

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.designsystem.JetBrainsMonoFamily
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.domain.model.AccountabilityPartnerConfig
import com.example.domain.model.AppUsageQuotaRule
import com.example.domain.model.ContentBlockerConfig
import com.example.domain.model.FocusProfile
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.MindfulPauseConfig
import com.example.domain.model.ResolveThemeId
import com.example.presentation.components.CleanupTextInteractionsOnDispose
import com.example.presentation.components.ProvideSafeTextToolbar
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.resolveGlassCard
import com.example.presentation.components.resolveSwitchColors
import com.example.vpn.ResolveDnsVpnService

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppThemeAndGoalCard(
    appThemeId: ResolveThemeId,
    dailyFocusGoalMinutes: Int,
    showSetupTips: Boolean,
    onSelectAppTheme: (ResolveThemeId) -> Unit,
    onSelectDailyGoalMinutes: (Int) -> Unit,
    onToggleShowSetupTips: (Boolean) -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    val goalPresets = remember { listOf(30, 60, 90, 120, 180, 240) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(cornerRadius = 16.dp)
            .padding(16.dp)
            .testTag("more_appearance_goal_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "App Color Theme",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = palette.textPrimary
            )
            Text(
                text = appThemeId.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResolveThemeId.entries.forEach { theme ->
                    val selected = theme == appThemeId
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectAppTheme(theme) },
                        label = {
                            Text(
                                text = theme.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.accent,
                            selectedLabelColor = palette.onAccent,
                            selectedLeadingIconColor = palette.onAccent,
                            containerColor = palette.controlSurface,
                            labelColor = palette.textPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = palette.controlBorder,
                            selectedBorderColor = palette.accent
                        ),
                        modifier = Modifier.testTag("more_theme_chip_${theme.storageKey}")
                    )
                }
            }
        }

        HorizontalDivider(color = palette.divider)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily Focus Goal",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.textPrimary
                )
                Text(
                    text = "${dailyFocusGoalMinutes}m / day",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                    color = palette.accentStrong
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                goalPresets.forEach { mins ->
                    val selected = dailyFocusGoalMinutes == mins
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectDailyGoalMinutes(mins) },
                        label = {
                            val label = if (mins >= 60 && mins % 60 == 0) "${mins / 60}h" else "${mins}m"
                            Text(label)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.accentSecondary,
                            selectedLabelColor = palette.onAccent,
                            containerColor = palette.controlSurface,
                            labelColor = palette.textPrimary
                        ),
                        modifier = Modifier.testTag("more_goal_chip_$mins")
                    )
                }
            }
        }

        HorizontalDivider(color = palette.divider)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.controlSurface)
                .border(1.dp, palette.controlBorder, RoundedCornerShape(14.dp))
                .clickable { onToggleShowSetupTips(!showSetupTips) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Show Setup Guidance Notes",
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.textPrimary
                )
                Text(
                    text = "Display helpful hints across setup and permission screens",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
            }
            Switch(
                checked = showSetupTips,
                onCheckedChange = onToggleShowSetupTips,
                colors = resolveSwitchColors(),
                modifier = Modifier.testTag("more_setup_tips_switch")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusProfilesAndGuardCard(
    focusProfiles: List<FocusProfile>,
    antiBypassStrictGuard: Boolean,
    emergencyCooldownSeconds: Int,
    installedApps: List<InstalledAppInfo>,
    onSaveCustomProfile: (
        name: String,
        durationMinutes: Long,
        strictMode: Boolean,
        allowSelectedApps: Boolean,
        showFlipClockShortcut: Boolean,
        allowedPackages: List<String>
    ) -> Unit,
    onDeleteCustomProfile: (String) -> Unit,
    onToggleAntiBypassStrictGuard: (Boolean) -> Unit,
    onSelectEmergencyCooldownSeconds: (Int) -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    var showCreateProfileDialog by remember { mutableStateOf(false) }
    val cooldownOptions = remember { listOf(0, 10, 15, 30, 60) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(cornerRadius = 16.dp)
            .padding(16.dp)
            .testTag("more_profiles_guard_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Focus Profiles",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.textPrimary
                )
                Text(
                    text = "Quick presets available inside Instant, Delayed & Schedule setup",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
            }
            OutlinedButton(
                onClick = { showCreateProfileDialog = true },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.55f)),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("more_create_profile_btn")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "New Focus Profile",
                    tint = palette.accentStrong,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "New",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.accentStrong
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            focusProfiles.forEach { profile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.controlSurface)
                        .border(1.dp, palette.controlBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("more_profile_row_${profile.profileId}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (profile.builtIn) "Built-in" else "Custom",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (profile.builtIn) palette.slateTealStrong else palette.accentStrong,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (profile.builtIn) palette.slateTealContainer else palette.accentSoftContainer
                                    )
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = profile.subtitle.ifBlank { "${profile.defaultDurationMinutes}m" },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!profile.builtIn) {
                        IconButton(
                            onClick = { onDeleteCustomProfile(profile.profileId) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("more_delete_profile_${profile.profileId}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete ${profile.name}",
                                tint = palette.danger,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = palette.divider)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.controlSurface)
                .border(1.dp, palette.controlBorder, RoundedCornerShape(14.dp))
                .clickable { onToggleAntiBypassStrictGuard(!antiBypassStrictGuard) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Anti-Bypass Strict Guard",
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.textPrimary
                )
                Text(
                    text = "Re-assert Focus Guard immediately if Settings or Split-Screen is opened during active lock",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
            }
            Switch(
                checked = antiBypassStrictGuard,
                onCheckedChange = onToggleAntiBypassStrictGuard,
                colors = resolveSwitchColors(),
                modifier = Modifier.testTag("more_anti_bypass_switch")
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Emergency Unlock Reflection Delay",
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary
            )
            Text(
                text = if (emergencyCooldownSeconds == 0) {
                    "Instant code entry (no cooldown pause)"
                } else {
                    "Wait ${emergencyCooldownSeconds}s before submitting emergency recovery code"
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cooldownOptions.forEach { sec ->
                    val selected = emergencyCooldownSeconds == sec
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectEmergencyCooldownSeconds(sec) },
                        label = { Text(if (sec == 0) "Off" else "${sec}s") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.accent,
                            selectedLabelColor = palette.onAccent,
                            containerColor = palette.controlSurface,
                            labelColor = palette.textPrimary
                        ),
                        modifier = Modifier.testTag("more_cooldown_chip_$sec")
                    )
                }
            }
        }
    }

    if (showCreateProfileDialog) {
        CustomFocusProfileDialog(
            installedApps = installedApps,
            onDismiss = { showCreateProfileDialog = false },
            onSave = { name, durationMins, strict, allowApps, flipShortcut, packages ->
                onSaveCustomProfile(name, durationMins, strict, allowApps, flipShortcut, packages)
                showCreateProfileDialog = false
            }
        )
    }
}

@Composable
fun AppUsageControlsAndWellbeingCard(
    excludedPackages: Set<String>,
    installedApps: List<InstalledAppInfo>,
    dailyQuotaRules: List<AppUsageQuotaRule>,
    mindfulPauseConfig: MindfulPauseConfig,
    contentBlockerConfig: ContentBlockerConfig,
    accountabilityPartnerConfig: AccountabilityPartnerConfig,
    onToggleExcludedPackage: (String) -> Unit,
    onSaveDailyQuotaRule: (AppUsageQuotaRule) -> Unit,
    onDeleteDailyQuotaRule: (String) -> Unit,
    onSaveMindfulPauseConfig: (MindfulPauseConfig) -> Unit,
    onSaveContentBlockerConfig: (ContentBlockerConfig) -> Unit,
    onSaveAccountabilityPartner: (
        enabled: Boolean,
        partnerName: String,
        newPinOrNull: String?,
        requireForStrictUnlock: Boolean,
        requireForQuotaOverride: Boolean
    ) -> Unit,
    onClearAccountabilityPartnerPin: () -> Unit,
    dnsBlockedCount: Int = 0,
    isFocusLockActive: Boolean = false,
    onResetDnsBlockedCount: () -> Unit = {}
) {
    val palette = LocalResolveExtendedPalette.current
    var showExcludedAppsDialog by remember { mutableStateOf(false) }
    var showQuotasDialog by remember { mutableStateOf(false) }
    var showMindfulDialog by remember { mutableStateOf(false) }
    var showContentBlockerDialog by remember { mutableStateOf(false) }
    var showPartnerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .resolveGlassCard(cornerRadius = 16.dp)
            .padding(16.dp)
            .testTag("more_wellbeing_controls_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        WellbeingActionRow(
            title = "Excluded Screen-Time Apps",
            subtitle = if (excludedPackages.isEmpty()) {
                "All other apps count toward daily screen time"
            } else {
                "${excludedPackages.size} app(s) hidden from analytics & charts"
            },
            onClick = { showExcludedAppsDialog = true },
            testTag = "more_excluded_apps_row"
        )

        WellbeingActionRow(
            title = "Daily App Quotas",
            subtitle = if (dailyQuotaRules.isEmpty()) {
                "Set daily usage caps per distracting app"
            } else {
                "${dailyQuotaRules.count { it.enabled }} active quota rule(s)"
            },
            onClick = { showQuotasDialog = true },
            testTag = "more_daily_quotas_row"
        )

        WellbeingActionRow(
            title = "Mindful Breathing Pause",
            subtitle = if (mindfulPauseConfig.enabled) {
                "Active · ${mindfulPauseConfig.clampedDelaySeconds}s pause across ${mindfulPauseConfig.targetPackages.size} app(s)"
            } else {
                "Off · Take a calm breath before opening selected apps"
            },
            onClick = { showMindfulDialog = true },
            testTag = "more_mindful_pause_row"
        )

        WellbeingActionRow(
            title = "Short-Video & Site Blocker",
            subtitle = if (contentBlockerConfig.enabled) {
                val flags = buildList {
                    if (contentBlockerConfig.vpnDnsBlockerEnabled) add("VPN DNS")
                    if (contentBlockerConfig.blockYouTubeShorts) add("Shorts")
                    if (contentBlockerConfig.blockInstagramReels) add("Reels")
                    if (contentBlockerConfig.blockedDomains.isNotEmpty()) add("${contentBlockerConfig.blockedDomains.size} sites")
                    if (dnsBlockedCount > 0) add("$dnsBlockedCount blocked")
                }.joinToString(" · ")
                "Active · ${flags.ifBlank { "Configured" }}"
            } else {
                "Off · Block Shorts, Reels & distracting domains via VPN DNS"
            },
            onClick = { showContentBlockerDialog = true },
            testTag = "more_content_blocker_row"
        )

        WellbeingActionRow(
            title = "Accountability Partner PIN",
            subtitle = when {
                accountabilityPartnerConfig.enabled && accountabilityPartnerConfig.hasPartnerPin ->
                    "Protected by ${accountabilityPartnerConfig.partnerName.ifBlank { "Partner" }}"
                accountabilityPartnerConfig.hasPartnerPin -> "PIN configured (currently paused)"
                else -> "Optional trusted partner PIN for overrides"
            },
            onClick = { showPartnerDialog = true },
            testTag = "more_accountability_partner_row"
        )
    }

    if (showExcludedAppsDialog) {
        ExcludedScreenTimeAppsDialog(
            installedApps = installedApps,
            excludedPackages = excludedPackages,
            onTogglePackage = onToggleExcludedPackage,
            onDismiss = { showExcludedAppsDialog = false }
        )
    }

    if (showQuotasDialog) {
        DailyAppQuotasDialog(
            installedApps = installedApps,
            rules = dailyQuotaRules,
            onSaveRule = onSaveDailyQuotaRule,
            onDeleteRule = onDeleteDailyQuotaRule,
            onDismiss = { showQuotasDialog = false }
        )
    }

    if (showMindfulDialog) {
        MindfulPauseConfigDialog(
            config = mindfulPauseConfig,
            installedApps = installedApps,
            onSave = {
                onSaveMindfulPauseConfig(it)
                showMindfulDialog = false
            },
            onDismiss = { showMindfulDialog = false }
        )
    }

    if (showContentBlockerDialog) {
        ContentBlockerConfigDialog(
            config = contentBlockerConfig,
            dnsBlockedCount = dnsBlockedCount,
            isFocusLockActive = isFocusLockActive,
            onResetDnsBlockedCount = onResetDnsBlockedCount,
            onSave = {
                onSaveContentBlockerConfig(it)
                showContentBlockerDialog = false
            },
            onDismiss = { showContentBlockerDialog = false }
        )
    }

    if (showPartnerDialog) {
        AccountabilityPartnerDialog(
            config = accountabilityPartnerConfig,
            onSave = { enabled, name, pin, strictUnlock, quotaOverride ->
                onSaveAccountabilityPartner(enabled, name, pin, strictUnlock, quotaOverride)
                showPartnerDialog = false
            },
            onClearPin = {
                onClearAccountabilityPartnerPin()
                showPartnerDialog = false
            },
            onDismiss = { showPartnerDialog = false }
        )
    }
}

@Composable
private fun WellbeingActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalResolveExtendedPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.controlSurface)
            .border(1.dp, palette.controlBorder, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.slateTealContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = null,
                tint = palette.slateTealStrong,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = title,
            tint = palette.textMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomFocusProfileDialog(
    installedApps: List<InstalledAppInfo>,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        durationMinutes: Long,
        strictMode: Boolean,
        allowSelectedApps: Boolean,
        showFlipClockShortcut: Boolean,
        allowedPackages: List<String>
    ) -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    var name by remember { mutableStateOf("") }
    var durationMinutes by remember { mutableIntStateOf(45) }
    var strictMode by remember { mutableStateOf(false) }
    var allowSelectedApps by remember { mutableStateOf(true) }
    var showFlipClockShortcut by remember { mutableStateOf(true) }
    var selectedPackages by remember { mutableStateOf(setOf<String>()) }
    val durationOptions = remember { listOf(25, 45, 60, 90, 120) }

    CleanupTextInteractionsOnDispose()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceSecondary,
        title = { Text("Create Focus Profile", color = palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProvideSafeTextToolbar {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 40) name = it },
                        label = { Text("Profile Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.cardBorder,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_profile_name_input")
                    )
                }
                Text("Default Duration", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    durationOptions.forEach { mins ->
                        FilterChip(
                            selected = durationMinutes == mins,
                            onClick = { durationMinutes = mins },
                            label = { Text("${mins}m") }
                        )
                    }
                }
                DialogSwitchRow("Strict Mode (no early unlock)", strictMode) { strictMode = it }
                DialogSwitchRow("Show Flip Clock Shortcut", showFlipClockShortcut) { showFlipClockShortcut = it }
                DialogSwitchRow("Allow Selected Apps", allowSelectedApps) { allowSelectedApps = it }
                if (allowSelectedApps && installedApps.isNotEmpty()) {
                    Text(
                        text = "Allowed apps (${selectedPackages.size} selected):",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                        items(installedApps.take(40), key = { it.packageName }) { app ->
                            val checked = selectedPackages.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPackages = if (checked) {
                                            selectedPackages - app.packageName
                                        } else {
                                            selectedPackages + app.packageName
                                        }
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        durationMinutes.toLong(),
                        strictMode,
                        allowSelectedApps,
                        showFlipClockShortcut,
                        selectedPackages.toList()
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("custom_profile_save_btn")
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) }
        }
    )
}

@Composable
private fun ExcludedScreenTimeAppsDialog(
    installedApps: List<InstalledAppInfo>,
    excludedPackages: Set<String>,
    onTogglePackage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    var filter by remember { mutableStateOf("") }
    val filteredApps = remember(installedApps, filter) {
        val q = filter.trim().lowercase()
        if (q.isEmpty()) installedApps else installedApps.filter {
            it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    CleanupTextInteractionsOnDispose()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceSecondary,
        title = { Text("Excluded Screen-Time Apps", color = palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Excluded apps won't appear in daily usage charts or total screen time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
                ProvideSafeTextToolbar {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        label = { Text("Search apps") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    if (filteredApps.isEmpty()) {
                        item {
                            Text(
                                text = "No matching installed apps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    } else {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isExcluded = excludedPackages.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTogglePackage(app.packageName) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isExcluded, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyAppQuotasDialog(
    installedApps: List<InstalledAppInfo>,
    rules: List<AppUsageQuotaRule>,
    onSaveRule: (AppUsageQuotaRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    var selectedApp by remember(installedApps) { mutableStateOf(installedApps.firstOrNull()) }
    var dailyLimitMinutes by remember { mutableIntStateOf(45) }
    val limitOptions = remember { listOf(15, 30, 45, 60, 90, 120) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceSecondary,
        title = { Text("Daily App Quotas", color = palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (rules.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(rules, key = { it.packageName }) { rule ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.controlSurface)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.appName, style = MaterialTheme.typography.labelLarge, color = palette.textPrimary)
                                    Text("${rule.dailyLimitMinutes}m / day", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                                }
                                IconButton(onClick = { onDeleteRule(rule.packageName) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete quota", tint = palette.danger)
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No daily quotas configured yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                }

                HorizontalDivider(color = palette.divider)
                Text("Add / Update Quota", style = MaterialTheme.typography.labelMedium, color = palette.textPrimary)
                if (installedApps.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                        items(installedApps.take(30), key = { it.packageName }) { app ->
                            val isChosen = selectedApp?.packageName == app.packageName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isChosen) palette.accentSoftContainer else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { selectedApp = app }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    limitOptions.forEach { mins ->
                        FilterChip(
                            selected = dailyLimitMinutes == mins,
                            onClick = { dailyLimitMinutes = mins },
                            label = { Text("${mins}m") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedApp != null,
                onClick = {
                    selectedApp?.let { app ->
                        onSaveRule(
                            AppUsageQuotaRule(
                                packageName = app.packageName,
                                appName = app.appName,
                                dailyLimitMinutes = dailyLimitMinutes,
                                enabled = true
                            )
                        )
                    }
                }
            ) {
                Text("Save Quota")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = palette.textSecondary) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MindfulPauseConfigDialog(
    config: MindfulPauseConfig,
    installedApps: List<InstalledAppInfo>,
    onSave: (MindfulPauseConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var delaySeconds by remember(config) { mutableIntStateOf(config.clampedDelaySeconds) }
    var passMinutes by remember(config) { mutableIntStateOf(config.clampedPassMinutes) }
    var targetPackages by remember(config) { mutableStateOf(config.targetPackages.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceSecondary,
        title = { Text("Mindful Breathing Pause", color = palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogSwitchRow("Enable Mindful Breathing Prompt", enabled) { enabled = it }
                Text("Breathing Countdown", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 15).forEach { sec ->
                        FilterChip(selected = delaySeconds == sec, onClick = { delaySeconds = sec }, label = { Text("${sec}s") })
                    }
                }
                Text("Unlock Pass Window", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 3, 5, 10).forEach { mins ->
                        FilterChip(selected = passMinutes == mins, onClick = { passMinutes = mins }, label = { Text("${mins}m") })
                    }
                }
                Text("Target Apps (${targetPackages.size} selected)", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                    items(installedApps.take(40), key = { it.packageName }) { app ->
                        val checked = targetPackages.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    targetPackages = if (checked) targetPackages - app.packageName else targetPackages + app.packageName
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.width(6.dp))
                            Text(app.appName, style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        MindfulPauseConfig(
                            enabled = enabled,
                            delaySeconds = delaySeconds,
                            passMinutes = passMinutes,
                            targetPackages = targetPackages.toList()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContentBlockerConfigDialog(
    config: ContentBlockerConfig,
    dnsBlockedCount: Int = 0,
    isFocusLockActive: Boolean = false,
    onResetDnsBlockedCount: () -> Unit = {},
    onSave: (ContentBlockerConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    val context = LocalContext.current
    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var shorts by remember(config) { mutableStateOf(config.blockYouTubeShorts) }
    var reels by remember(config) { mutableStateOf(config.blockInstagramReels) }
    var vpnDns by remember(config) { mutableStateOf(config.vpnDnsBlockerEnabled) }
    var blockShortVideo by remember(config) { mutableStateOf(config.blockShortVideoDomains) }
    var blockSocial by remember(config) { mutableStateOf(config.blockSocialDomains) }
    var blockAdult by remember(config) { mutableStateOf(config.blockAdultDomains) }
    var onlyDuringLock by remember(config) { mutableStateOf(config.onlyDuringFocusLock) }
    var domains by remember(config) { mutableStateOf(config.blockedDomains) }
    var domainInput by remember { mutableStateOf("") }

    val vpnConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || ResolveDnsVpnService.isVpnConsentGranted(context)) {
            vpnDns = true
            enabled = true
        } else {
            vpnDns = false
        }
    }

    CleanupTextInteractionsOnDispose()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceSecondary,
        title = { Text("Short-Video & Site Blocker", color = palette.textPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DialogSwitchRow("Enable Content Blocker", enabled) { enabled = it }
                DialogSwitchRow("Local VPN DNS Blocker", vpnDns) { checked ->
                    if (checked) {
                        val consentIntent = ResolveDnsVpnService.prepareVpnIntent(context)
                        if (consentIntent != null) {
                            vpnConsentLauncher.launch(consentIntent)
                        } else {
                            vpnDns = true
                            enabled = true
                        }
                    } else {
                        vpnDns = false
                    }
                }
                if (vpnDns) {
                    Text(
                        text = if (onlyDuringLock && !isFocusLockActive) {
                            "Local DNS filter armed — activates automatically when Focus Lock starts."
                        } else {
                            "Local DNS filter inspects DNS lookups on-device only; regular traffic stays untouched."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.emeraldStrong
                    )
                }
                DialogSwitchRow("Block YouTube Shorts", shorts) { shorts = it }
                DialogSwitchRow("Block Instagram Reels", reels) { reels = it }
                DialogSwitchRow("Block Short-Video Domains (TikTok, etc.)", blockShortVideo) {
                    blockShortVideo = it
                }
                DialogSwitchRow("Block Social Media Domains", blockSocial) {
                    blockSocial = it
                }
                DialogSwitchRow("Block Adult Domains", blockAdult) {
                    blockAdult = it
                }
                DialogSwitchRow("Only During Focus Lock", onlyDuringLock) { onlyDuringLock = it }

                if (dnsBlockedCount > 0 || vpnDns) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceElevated)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Blocked via DNS: $dnsBlockedCount queries",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textPrimary
                        )
                        if (dnsBlockedCount > 0) {
                            TextButton(
                                onClick = onResetDnsBlockedCount,
                                modifier = Modifier.testTag("more_dns_reset_count_btn")
                            ) {
                                Text("Reset", color = palette.emeraldStrong)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProvideSafeTextToolbar {
                        OutlinedTextField(
                            value = domainInput,
                            onValueChange = { domainInput = it },
                            label = { Text("Add domain (e.g. reddit.com)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val clean = com.example.vpn.DnsBlocklistMatcher.normalizeDomain(domainInput)
                            if (clean.contains('.') && !domains.contains(clean)) {
                                domains = domains + clean
                                domainInput = ""
                            }
                        }
                    ) { Text("Add") }
                }
                if (domains.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        domains.forEach { domain ->
                            FilterChip(
                                selected = true,
                                onClick = { domains = domains - domain },
                                label = { Text(domain) },
                                trailingIcon = {
                                    Icon(Icons.Outlined.Close, contentDescription = "Remove $domain", modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ContentBlockerConfig(
                            enabled = enabled,
                            blockYouTubeShorts = shorts,
                            blockInstagramReels = reels,
                            blockedDomains = domains,
                            onlyDuringFocusLock = onlyDuringLock,
                            vpnDnsBlockerEnabled = vpnDns,
                            blockAdultDomains = blockAdult,
                            blockShortVideoDomains = blockShortVideo,
                            blockSocialDomains = blockSocial
                        )
                    )
                },
                modifier = Modifier.testTag("more_content_blocker_save_btn")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) }
        }
    )
}

@Composable
private fun AccountabilityPartnerDialog(
    config: AccountabilityPartnerConfig,
    onSave: (
        enabled: Boolean,
        partnerName: String,
        newPinOrNull: String?,
        requireForStrictUnlock: Boolean,
        requireForQuotaOverride: Boolean
    ) -> Unit,
    onClearPin: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var partnerName by remember(config) { mutableStateOf(config.partnerName) }
    var newPin by remember { mutableStateOf("") }
    var requireStrict by remember(config) { mutableStateOf(config.requireForStrictUnlock) }
    var requireQuota by remember(config) { mutableStateOf(config.requireForQuotaOverride) }

    val pinValid = if (newPin.isEmpty()) config.hasPartnerPin || !enabled else newPin.length in 4..8 && newPin.all { it.isDigit() }

    CleanupTextInteractionsOnDispose()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceSecondary,
        title = { Text("Accountability Partner", color = palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogSwitchRow("Enable Partner Protection", enabled) { enabled = it }
                ProvideSafeTextToolbar {
                    OutlinedTextField(
                        value = partnerName,
                        onValueChange = { if (it.length <= 36) partnerName = it },
                        label = { Text("Partner Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) newPin = it },
                        label = { Text(if (config.hasPartnerPin) "New PIN (leave blank to keep)" else "Partner PIN (4-8 digits)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                DialogSwitchRow("Require for Strict Unlock", requireStrict) { requireStrict = it }
                DialogSwitchRow("Require for Quota Override", requireQuota) { requireQuota = it }
                if (config.hasPartnerPin) {
                    TextButton(onClick = onClearPin) {
                        Text("Clear Existing Partner PIN", color = palette.danger)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = pinValid,
                onClick = {
                    onSave(
                        enabled,
                        partnerName.trim(),
                        newPin.takeIf { it.isNotBlank() },
                        requireStrict,
                        requireQuota
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) }
        }
    )
}

@Composable
private fun DialogSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textPrimary,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = resolveSwitchColors())
    }
}
