package com.example.presentation.modes

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

enum class VisionDisplaySection {
    DARK_MODE,
    COLOR_INVERSION
}

private enum class ConsentActionTarget {
    ENABLE_IN_APP_INVERSION,
    OPEN_OS_ACCESSIBILITY_SETTINGS
}

/**
 * Genuine mathematical RGB inversion modifier:
 * R' = 255 - R, G' = 255 - G, B' = 255 - B, A' = A.
 * Uses Android API 31+ GPU RenderEffect where supported and ColorMatrix saveLayer fallback on older APIs.
 */
fun Modifier.rgbColorInversion(enabled: Boolean): Modifier = composed {
    if (!enabled) return@composed this
    val colorMatrix = remember { ColorMatrix(ForceDarkCapabilityHelper.RGB_INVERSION_COLOR_MATRIX) }
    val composeEffect = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ForceDarkCapabilityHelper.createInversionRenderEffect()?.asComposeRenderEffect()
        } else {
            null
        }
    }
    if (composeEffect != null) {
        graphicsLayer { renderEffect = composeEffect }
    } else {
        drawWithContent {
            val paint = Paint().apply {
                colorFilter = ColorFilter.colorMatrix(colorMatrix)
            }
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(Offset.Zero, size), paint)
                drawContent()
                canvas.restore()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ModesScreen(
    report: ForceDarkCapabilityReport,
    installedApps: List<InstalledAppInfo>,
    darkModeAppPrefs: Map<String, Boolean>,
    colorInversionAppPrefs: Map<String, Boolean>,
    loadAppIcon: suspend (String) -> ImageBitmap?,
    onEnterFlipClock: () -> Unit,
    onAttemptAppDarkMode: (String, Boolean) -> Unit,
    onAttemptAppColorInversion: (String, Boolean) -> Unit,
    onOpenDisplaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onResetAllModes: () -> Unit,
    modifier: Modifier = Modifier,
    initialDisplaySection: VisionDisplaySection = VisionDisplaySection.DARK_MODE,
    inAppColorInversionEnabled: Boolean = false,
    colorInversionConsentGranted: Boolean = false,
    priorOsColorInversionState: Int = -1,
    onToggleInAppColorInversion: (Boolean) -> Unit = {},
    onGrantConsentAndOpenAccessibility: () -> Unit = {}
) {
    val palette = LocalResolveExtendedPalette.current
    val context = LocalContext.current
    val ownPackageName = context.packageName
    var activeDisplaySection by rememberSaveable { mutableStateOf(initialDisplaySection) }
    var showDarkAppsSheet by remember { mutableStateOf(false) }
    var showInversionAppsSheet by remember { mutableStateOf(false) }
    var pendingConsentAction by remember { mutableStateOf<ConsentActionTarget?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("modes_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .testTag("modes_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
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
                    text = "Enter full-screen Flip Clock mode and manage Dark Mode or Color Inversion independently.",
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
                            cornerRadius = 24.dp,
                            borderColor = palette.accent.copy(alpha = 0.45f)
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
                                .background(palette.accentSoftContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ResolveIcons.FlipClock,
                                contentDescription = null,
                                tint = palette.accent,
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
                            containerColor = palette.accent,
                            contentColor = palette.onAccent
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

            // Segmented two-button toggle: Dark Mode | Color Inversion (clearly separated concepts)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.cardBottom)
                        .border(1.dp, palette.cardBorder, RoundedCornerShape(16.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VisionSegmentTab(
                        label = "Dark Mode",
                        selected = activeDisplaySection == VisionDisplaySection.DARK_MODE,
                        onClick = { activeDisplaySection = VisionDisplaySection.DARK_MODE },
                        testTag = "vision_tab_dark_mode",
                        modifier = Modifier.weight(1f)
                    )
                    VisionSegmentTab(
                        label = "Color Inversion",
                        selected = activeDisplaySection == VisionDisplaySection.COLOR_INVERSION,
                        onClick = { activeDisplaySection = VisionDisplaySection.COLOR_INVERSION },
                        testTag = "vision_tab_color_inversion",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Active Display Capability Section
            item {
                AnimatedContent(
                    targetState = activeDisplaySection,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(160))
                    },
                    label = "modes_display_section_switch"
                ) { section ->
                    when (section) {
                        VisionDisplaySection.DARK_MODE -> {
                            val darkStatus = if (report.isSystemNightModeActive) {
                                DisplayModeAvailability.DARK_MODE_AVAILABLE
                            } else {
                                DisplayModeAvailability.DARK_MODE_UNAVAILABLE
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .resolveGlassCard(cornerRadius = 22.dp)
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
                                        label = darkStatus.badgeLabel,
                                        isPositive = darkStatus == DisplayModeAvailability.DARK_MODE_AVAILABLE
                                    )
                                }

                                Text(
                                    text = "RESOLVE always operates strictly in its native Dark Mode interface. Dark Mode uses true dark palettes and is completely separate from RGB Color Inversion.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary
                                )

                                Text(
                                    text = report.forceDarkSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary
                                )

                                Text(
                                    text = if (report.isSystemNightModeActive) {
                                        "Android System Dark Theme: ON"
                                    } else {
                                        "Android System Dark Theme: OFF"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.accent
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showDarkAppsSheet = true },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, palette.cardBorder),
                                        modifier = Modifier.testTag("modes_dark_check_apps_btn")
                                    ) {
                                        Text("Check App Compatibility", color = palette.textPrimary)
                                    }
                                    OutlinedButton(
                                        onClick = onOpenDisplaySettings,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.accent),
                                        modifier = Modifier.testTag("modes_open_display_settings_btn")
                                    ) {
                                        Text("System Display Settings")
                                    }
                                }
                            }
                        }

                        VisionDisplaySection.COLOR_INVERSION -> {
                            val ownInversionStatus = ForceDarkCapabilityHelper.resolveColorInversionStatus(
                                packageName = ownPackageName,
                                report = report,
                                inAppPreviewActive = inAppColorInversionEnabled,
                                isOwnAppPackage = true
                            )
                            val isActiveAnywhere = ownInversionStatus == DisplayModeAvailability.COLOR_INVERSION_ACTIVE

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .resolveGlassCard(cornerRadius = 22.dp)
                                    .padding(18.dp)
                                    .testTag("modes_color_inversion_card"),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Color Inversion",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = palette.textPrimary
                                    )
                                    ModeStatusBadge(
                                        label = ownInversionStatus.badgeLabel,
                                        isPositive = isActiveAnywhere
                                    )
                                }

                                Text(
                                    text = report.colorInversionSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary
                                )

                                // Interactive In-App Mathematical RGB Color Inversion Toggle (consent-gated)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(palette.background.copy(alpha = 0.55f))
                                        .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 10.dp)
                                    ) {
                                        Text(
                                            text = "In-App Mathematical RGB Inversion",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = palette.textPrimary
                                        )
                                        Text(
                                            text = if (inAppColorInversionEnabled) {
                                                "Active inside RESOLVE (R'=255-R, G'=255-G, B'=255-B)."
                                            } else {
                                                "Invert RESOLVE UI surfaces cleanly via ColorMatrix / RenderEffect."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = palette.textSecondary
                                        )
                                    }
                                    Switch(
                                        checked = inAppColorInversionEnabled,
                                        onCheckedChange = { enable ->
                                            if (enable && !colorInversionConsentGranted) {
                                                pendingConsentAction = ConsentActionTarget.ENABLE_IN_APP_INVERSION
                                            } else {
                                                onToggleInAppColorInversion(enable)
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = palette.onAccent,
                                            checkedTrackColor = palette.accent
                                        ),
                                        modifier = Modifier.testTag("modes_in_app_inversion_switch")
                                    )
                                }

                                // Live Transformation Verification Swatch Card
                                ColorInversionTransformPreviewCard(
                                    inversionActive = inAppColorInversionEnabled
                                )

                                // Honest OS Color Inversion Status & Protected Surface Notice
                                Text(
                                    text = buildString {
                                        append(
                                            if (report.isColorInversionEnabledInOs) {
                                                "Android System Color Inversion: ON"
                                            } else {
                                                "Android System Color Inversion: OFF"
                                            }
                                        )
                                        if (priorOsColorInversionState == 1) {
                                            append(" (Pre-existing OS setting preserved)")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (report.isColorInversionEnabledInOs) palette.accent else palette.textSecondary
                                )

                                Text(
                                    text = report.protectedSurfaceNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textMuted,
                                    modifier = Modifier.testTag("modes_protected_surface_note")
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showInversionAppsSheet = true },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, palette.cardBorder),
                                        modifier = Modifier.testTag("modes_inversion_check_apps_btn")
                                    ) {
                                        Text("Check App Compatibility", color = palette.textPrimary)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (!colorInversionConsentGranted) {
                                                pendingConsentAction = ConsentActionTarget.OPEN_OS_ACCESSIBILITY_SETTINGS
                                            } else {
                                                onGrantConsentAndOpenAccessibility()
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.accent),
                                        modifier = Modifier.testTag("modes_open_accessibility_inversion_btn")
                                    ) {
                                        Text("System Accessibility Settings")
                                    }
                                    if (inAppColorInversionEnabled) {
                                        OutlinedButton(
                                            onClick = { onToggleInAppColorInversion(false) },
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, palette.error.copy(alpha = 0.45f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.error),
                                            modifier = Modifier.testTag("modes_restore_colors_btn")
                                        ) {
                                            Text("Restore Normal Colors")
                                        }
                                    }
                                }
                            }
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
                        text = "Tip: RESOLVE runs strictly in Dark Mode. Color Inversion is independent and always restorable.",
                        icon = Icons.Outlined.Info,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onResetAllModes,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, palette.error.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.error),
                        modifier = Modifier.testTag("modes_reset_all_btn")
                    ) {
                        Text("Reset Modes")
                    }
                }
            }
        }
    }

    pendingConsentAction?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingConsentAction = null },
            containerColor = palette.cardBottom,
            titleContentColor = palette.textPrimary,
            textContentColor = palette.textSecondary,
            title = { Text("Authorize Color Inversion") },
            text = {
                Text(
                    "Color Inversion mathematically reverses RGB channels (R'=255-R, G'=255-G, B'=255-B). " +
                        "Android blocks non-root apps from inverting protected/FLAG_SECURE windows, DRM video streams, " +
                        "or third-party apps without device-wide Accessibility Display Color Inversion. " +
                        "Pre-existing system accessibility preferences are never overwritten."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingConsentAction = null
                        when (target) {
                            ConsentActionTarget.ENABLE_IN_APP_INVERSION ->
                                onToggleInAppColorInversion(true)
                            ConsentActionTarget.OPEN_OS_ACCESSIBILITY_SETTINGS ->
                                onGrantConsentAndOpenAccessibility()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        contentColor = palette.onAccent
                    ),
                    modifier = Modifier.testTag("modes_inversion_consent_confirm")
                ) {
                    Text("Understand & Continue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingConsentAction = null },
                    modifier = Modifier.testTag("modes_inversion_consent_cancel")
                ) {
                    Text("Cancel", color = palette.textSecondary)
                }
            },
            modifier = Modifier.testTag("modes_inversion_consent_dialog")
        )
    }

    if (showDarkAppsSheet) {
        ModePerAppSheet(
            title = "App Dark Mode Compatibility",
            subtitle = "Third-party apps manage Dark Theme independently on non-root Android. Unsupported overrides clearly report Not Supported.",
            installedApps = installedApps,
            activePrefs = darkModeAppPrefs,
            loadAppIcon = loadAppIcon,
            statusResolver = { pkg ->
                ForceDarkCapabilityHelper.resolveDarkModeStatus(pkg, report)
            },
            isSwitchChecked = { pkg, status ->
                status == DisplayModeAvailability.DARK_MODE_AVAILABLE && (darkModeAppPrefs[pkg] == true)
            },
            onToggleApp = onAttemptAppDarkMode,
            onDismiss = { showDarkAppsSheet = false }
        )
    }

    if (showInversionAppsSheet) {
        ModePerAppSheet(
            title = "App Color Inversion Compatibility",
            subtitle = "RESOLVE supports In-App RGB Color Inversion. Other apps can only invert via device-wide Accessibility Display Color Inversion (except FLAG_SECURE/DRM surfaces).",
            installedApps = installedApps,
            activePrefs = colorInversionAppPrefs,
            loadAppIcon = loadAppIcon,
            statusResolver = { pkg ->
                ForceDarkCapabilityHelper.resolveColorInversionStatus(
                    packageName = pkg,
                    report = report,
                    inAppPreviewActive = inAppColorInversionEnabled,
                    isOwnAppPackage = pkg == ownPackageName
                )
            },
            isSwitchChecked = { pkg, _ ->
                if (pkg == ownPackageName) inAppColorInversionEnabled
                else report.isColorInversionEnabledInOs || (colorInversionAppPrefs[pkg] == true)
            },
            onToggleApp = { pkg, wantEnable ->
                if (pkg == ownPackageName) {
                    onToggleInAppColorInversion(wantEnable)
                } else {
                    onAttemptAppColorInversion(pkg, wantEnable)
                }
            },
            onDismiss = { showInversionAppsSheet = false }
        )
    }
}

@Composable
private fun ModeStatusBadge(
    label: String,
    isPositive: Boolean
) {
    val palette = LocalResolveExtendedPalette.current
    val fg = if (isPositive) palette.accent else palette.warn
    val bg = if (isPositive) palette.accentSoftContainer else palette.warnContainer
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

@Composable
private fun ColorInversionTransformPreviewCard(
    inversionActive: Boolean
) {
    val palette = LocalResolveExtendedPalette.current
    val sampleBlack = Color(0xFF000000)
    val sampleRed = Color(0xFFFF0000)
    val sampleBlue = Color(0xFF0000FF)
    val sampleGreen = Color(0xFF00FF00)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.background.copy(alpha = 0.65f))
            .border(1.dp, palette.cardBorder, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .testTag("modes_inversion_preview_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (inversionActive) {
                "RGB Inversion Active: Black↔White • Red↔Cyan • Blue↔Yellow"
            } else {
                "Mathematical RGB Inversion Reference (255 - Channel)"
            },
            style = MaterialTheme.typography.labelMedium,
            color = palette.textPrimary
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .rgbColorInversion(enabled = inversionActive)
                .background(Color(0xFF121614))
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "Black" to sampleBlack,
                "Red" to sampleRed,
                "Blue" to sampleBlue,
                "Green" to sampleGreen
            ).forEach { (label, swatchColor) ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun VisionSegmentTab(
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
        label = "modes_tab_bg_$testTag"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) palette.accent else palette.textSecondary,
        animationSpec = tween(180),
        label = "modes_tab_fg_$testTag"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModePerAppSheet(
    title: String,
    subtitle: String,
    installedApps: List<InstalledAppInfo>,
    activePrefs: Map<String, Boolean>,
    loadAppIcon: suspend (String) -> ImageBitmap?,
    statusResolver: (String) -> DisplayModeAvailability,
    isSwitchChecked: (String, DisplayModeAvailability) -> Boolean,
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
        containerColor = palette.cardBottom
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
                        val isSupported = status != DisplayModeAvailability.NOT_SUPPORTED &&
                            status != DisplayModeAvailability.DARK_MODE_UNAVAILABLE
                        val checked = isSwitchChecked(app.packageName, status) &&
                            (activePrefs[app.packageName] != false || checkedOverride(status))
                        var icon by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }
                        LaunchedEffect(app.packageName) {
                            icon = loadAppIcon(app.packageName)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.background.copy(alpha = 0.55f))
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
                                    color = if (isSupported) palette.accent else palette.textMuted
                                )
                            }
                            Switch(
                                checked = checked,
                                onCheckedChange = { wantEnable ->
                                    onToggleApp(app.packageName, wantEnable)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = palette.onAccent,
                                    checkedTrackColor = palette.accent
                                ),
                                modifier = Modifier.testTag("mode_app_switch_${app.packageName}")
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun checkedOverride(status: DisplayModeAvailability): Boolean =
    status == DisplayModeAvailability.COLOR_INVERSION_ACTIVE
