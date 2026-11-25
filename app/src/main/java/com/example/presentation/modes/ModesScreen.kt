package com.example.presentation.modes

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.designsystem.LocalResolveBottomDockClearance
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.domain.model.InstalledAppInfo
import com.example.platform.DisplayModeAvailability
import com.example.platform.ForceDarkCapabilityHelper
import com.example.platform.ForceDarkCapabilityReport
import com.example.presentation.components.CleanupTextInteractionsOnDispose
import com.example.presentation.components.FeatureTipNote
import com.example.presentation.components.ProvideSafeTextToolbar
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.resolveGlassCard
import com.example.presentation.components.resolveSwitchColors

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ModesScreen(
    report: ForceDarkCapabilityReport,
    installedApps: List<InstalledAppInfo>,
    darkModeAppPrefs: Map<String, Boolean>,
    loadAppIcon: suspend (String) -> ImageBitmap?,
    onEnterFlipClock: () -> Unit,
    onAttemptAppDarkMode: (String, Boolean) -> Unit,
    onOpenDisplaySettings: () -> Unit,
    onResetAllModes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalResolveExtendedPalette.current
    val dockClearance = LocalResolveBottomDockClearance.current
    val context = LocalContext.current
    val ownPackageName = context.packageName
    var showDarkAppsSheet by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("modes_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize()
                .testTag("modes_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 20.dp,
                bottom = 20.dp + dockClearance
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Modes",
                    style = MaterialTheme.typography.headlineLarge,
                    color = palette.textPrimary,
                    modifier = Modifier.testTag("modes_headline")
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Launch full-screen Flip Clock mode and manage Dark Mode display preferences.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
            }

            // Standalone Featured Flip Clock Mode Card with explicit Enter button
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .resolveGlassCard(
                            cornerRadius = 16.dp,
                            borderColor = palette.cardBorder
                        )
                        .padding(18.dp)
                        .testTag("vision_flip_clock_card"),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.champagneContainer)
                                .border(1.dp, palette.champagne.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ResolveIcons.FlipClock,
                                contentDescription = null,
                                tint = palette.champagneStrong,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Flip Clock Mode",
                                style = MaterialTheme.typography.titleLarge,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "Full-screen split-flap Clock, Focus Timer, and Stopwatch.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                        }
                    }

                    Button(
                        onClick = onEnterFlipClock,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.primaryButton,
                            contentColor = palette.onPrimaryButton
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("vision_flip_clock_enter_btn")
                    ) {
                        Icon(
                            imageVector = ResolveIcons.FlipClock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Enter",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            // Native Dark Mode Card (RESOLVE always stays in Dark Mode)
            item {
                val systemDarkSupported = report.androidSdkInt >= Build.VERSION_CODES.Q
                val darkStatus = if (report.isSystemNightModeActive) {
                    DisplayModeAvailability.DARK_MODE_AVAILABLE
                } else {
                    DisplayModeAvailability.DARK_MODE_UNAVAILABLE
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .resolveGlassCard(cornerRadius = 16.dp)
                        .padding(18.dp)
                        .testTag("modes_app_dark_card"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dark Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary
                        )
                        ModeStatusBadge(
                            label = if (systemDarkSupported) {
                                darkStatus.badgeLabel
                            } else {
                                "Native Dark Mode Active"
                            },
                            isPositive = !systemDarkSupported || darkStatus == DisplayModeAvailability.DARK_MODE_AVAILABLE
                        )
                    }

                    Text(
                        text = "RESOLVE always stays in its native Dark Mode interface across every phone and tablet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )

                    Text(
                        text = report.forceDarkSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )

                    if (systemDarkSupported) {
                        Text(
                            text = if (report.isSystemNightModeActive) {
                                "Android System Dark Mode: ON"
                            } else {
                                "Android System Dark Mode: OFF"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (report.isSystemNightModeActive) palette.slateTealStrong else palette.warn
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "System Dark Mode",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.textSecondary
                            )
                            Text(
                                text = "Not Supported on This Device",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = palette.warn,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(palette.warnContainer)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                    .testTag("modes_system_dark_unsupported_badge")
                            )
                        }
                        Text(
                            text = "System-wide Dark Mode requires Android 10 (API 29) or higher.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDarkAppsSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, palette.controlBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = palette.controlSurface,
                                contentColor = palette.textPrimary
                            ),
                            modifier = Modifier.testTag("modes_dark_check_apps_btn")
                        ) {
                            Text("Check App Compatibility", color = palette.textPrimary)
                        }
                        OutlinedButton(
                            onClick = onOpenDisplaySettings,
                            enabled = systemDarkSupported,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (systemDarkSupported) {
                                    palette.slateTeal.copy(alpha = 0.42f)
                                } else {
                                    palette.cardBorder
                                }
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (systemDarkSupported) {
                                    palette.slateTealContainer.copy(alpha = 0.72f)
                                } else {
                                    palette.controlSurface
                                },
                                contentColor = palette.slateTealStrong,
                                disabledContainerColor = palette.controlSurface,
                                disabledContentColor = palette.textMuted
                            ),
                            modifier = Modifier.testTag("modes_open_display_settings_btn")
                        ) {
                            Text(
                                if (systemDarkSupported) {
                                    "Open System Dark Mode"
                                } else {
                                    "System Dark Mode Unavailable"
                                }
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FeatureTipNote(
                        text = "Tip: Enable System Dark Mode in Android Display Settings so other supported apps darken automatically.",
                        icon = Icons.Outlined.Info,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onResetAllModes,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, palette.error.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = palette.errorContainer.copy(alpha = 0.42f),
                            contentColor = palette.error
                        ),
                        modifier = Modifier.testTag("modes_reset_all_btn")
                    ) {
                        Text("Reset Mode Prefs")
                    }
                }
            }
        }
    }

    if (showDarkAppsSheet) {
        ModePerAppSheet(
            title = "App Dark Mode Compatibility",
            subtitle = "RESOLVE runs strictly in native Dark Mode. Other apps follow Android System Dark Mode independently on non-root devices.",
            installedApps = installedApps,
            activePrefs = darkModeAppPrefs,
            ownPackageName = ownPackageName,
            loadAppIcon = loadAppIcon,
            statusResolver = { pkg ->
                ForceDarkCapabilityHelper.resolveDarkModeStatus(
                    packageName = pkg,
                    report = report,
                    ownPackageName = ownPackageName
                )
            },
            onToggleApp = onAttemptAppDarkMode,
            onDismiss = { showDarkAppsSheet = false }
        )
    }
}

@Composable
private fun ModeStatusBadge(
    label: String,
    isPositive: Boolean
) {
    val palette = LocalResolveExtendedPalette.current
    val fg = if (isPositive) palette.onAccent else palette.warn
    val bg = if (isPositive) palette.success else palette.warnContainer
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("mode_status_badge_${label.lowercase().replace(' ', '_')}")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModePerAppSheet(
    title: String,
    subtitle: String,
    installedApps: List<InstalledAppInfo>,
    activePrefs: Map<String, Boolean>,
    ownPackageName: String,
    loadAppIcon: suspend (String) -> ImageBitmap?,
    statusResolver: (String) -> DisplayModeAvailability,
    onToggleApp: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(installedApps, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) installedApps else installedApps.filter {
            it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surfaceSecondary
    ) {
        ProvideSafeTextToolbar {
            CleanupTextInteractionsOnDispose()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val status = statusResolver(app.packageName)
                        val isOwnApp = app.packageName == ownPackageName
                        val isSupported = status == DisplayModeAvailability.DARK_MODE_AVAILABLE
                        val checked = isOwnApp || (isSupported && activePrefs[app.packageName] == true)
                        var icon by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }
                        LaunchedEffect(app.packageName) {
                            icon = loadAppIcon(app.packageName)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.cardBottom)
                                .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (icon != null) {
                                Image(
                                    bitmap = icon!!,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = ResolveIcons.Apps,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = status.badgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSupported) palette.slateTealStrong else palette.textMuted
                                )
                            }
                            Switch(
                                checked = checked,
                                enabled = !isOwnApp,
                                onCheckedChange = { wantEnable ->
                                    onToggleApp(app.packageName, wantEnable)
                                },
                                colors = resolveSwitchColors(),
                                modifier = Modifier.testTag("mode_app_switch_${app.packageName}")
                            )
                        }
                    }
                }
            }
        }
    }
}
