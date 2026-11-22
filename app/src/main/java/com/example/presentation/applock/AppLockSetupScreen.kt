package com.example.presentation.applock

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.domain.model.AppLockAuthMethod
import com.example.domain.model.AppLockPinLength
import com.example.domain.model.AppLockVerificationResult
import com.example.domain.model.DeviceOwnerState
import com.example.domain.model.InstalledAppInfo
import com.example.domain.model.SystemCapabilityStatus
import com.example.presentation.components.CleanupTextInteractionsOnDispose
import com.example.presentation.components.FeatureTipNote
import com.example.presentation.components.ProvideSafeTextToolbar
import com.example.presentation.components.ResolveIcons
import com.example.presentation.components.dismissTextInteractions
import com.example.presentation.components.resolveGlassCard
import com.example.presentation.components.resolveSwitchColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppLockSetupScreen(
    appLockEnabled: Boolean,
    hasCredential: Boolean,
    configuredMethod: AppLockAuthMethod,
    configuredPinLength: AppLockPinLength,
    protectedPackages: List<String>,
    installedApps: List<InstalledAppInfo>,
    managementUnlocked: Boolean,
    capabilities: SystemCapabilityStatus,
    loadIcon: suspend (String) -> ImageBitmap?,
    onVerifyManagementAccess: suspend (String) -> AppLockVerificationResult,
    onToggleAppLockEnabled: (Boolean) -> Unit,
    onSaveCredential: (AppLockAuthMethod, AppLockPinLength, String) -> Unit,
    onSaveProtectedPackages: (Set<String>) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenDeviceOwnerGuide: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val palette = LocalResolveExtendedPalette.current
    val scope = rememberCoroutineScope()
    var showProtectedAppsPicker by remember { mutableStateOf(false) }

    // Credential wizard state
    var showCredentialWizard by remember(hasCredential) { mutableStateOf(!hasCredential) }
    var draftMethod by remember(configuredMethod) { mutableStateOf(configuredMethod) }
    var draftPinLength by remember(configuredPinLength) { mutableStateOf(configuredPinLength) }
    var firstStepSecret by remember { mutableStateOf<String?>(null) }
    var setupError by remember { mutableStateOf<String?>(null) }
    var setupResetKey by remember { mutableStateOf(0) }

    // Management gate state
    var gateError by remember { mutableStateOf<String?>(null) }
    var gateResetKey by remember { mutableStateOf(0) }
    var throttleSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(throttleSeconds) {
        if (throttleSeconds > 0L) {
            delay(1000L)
            throttleSeconds = (throttleSeconds - 1L).coerceAtLeast(0L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize()
                .testTag("applock_setup_list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("applock_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = palette.textPrimary
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "App Lock",
                            style = MaterialTheme.typography.headlineMedium,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Protect chosen apps with Pattern, PIN, or 6-Cell Sequence.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                }
            }

            // Management auth gate whenever a credential is already configured and not yet unlocked in session
            if (hasCredential && !managementUnlocked) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(
                                cornerRadius = 16.dp,
                                borderColor = palette.accent.copy(alpha = 0.35f)
                            )
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Authenticate to Manage App Lock",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary
                            )
                        }
                        val activePrompt = when (configuredMethod) {
                            AppLockAuthMethod.PATTERN -> "Draw your current pattern"
                            AppLockAuthMethod.PIN -> "Enter your ${configuredPinLength.digits}-digit PIN"
                            AppLockAuthMethod.SIX_CELL_SEQUENTIAL -> "Tap your secret sequence"
                        }
                        val effectiveError = if (throttleSeconds > 0L) {
                            "Too many incorrect attempts. Try again in ${throttleSeconds}s."
                        } else {
                            gateError
                        }
                        AppLockAuthChallengePanel(
                            method = configuredMethod,
                            pinLength = configuredPinLength,
                            isSetupMode = false,
                            promptMessage = activePrompt,
                            errorMessage = effectiveError,
                            resetKey = gateResetKey,
                            onCredentialSubmitted = { raw ->
                                if (throttleSeconds > 0L) return@AppLockAuthChallengePanel
                                scope.launch {
                                    when (val res = onVerifyManagementAccess(raw)) {
                                        AppLockVerificationResult.Verified -> {
                                            gateError = null
                                        }
                                        is AppLockVerificationResult.InvalidCredential -> {
                                            gateError = "Incorrect credential (${res.remainingAttemptsBeforeCooldown} attempts before cooldown)"
                                            gateResetKey++
                                        }
                                        is AppLockVerificationResult.Throttled -> {
                                            throttleSeconds = res.retryAfterSeconds
                                            gateError = null
                                            gateResetKey++
                                        }
                                        AppLockVerificationResult.NotConfigured -> {
                                            gateError = null
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                // Main App Lock ON/OFF card
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(
                                cornerRadius = 16.dp,
                                borderColor = if (appLockEnabled) {
                                    palette.accent.copy(alpha = 0.45f)
                                } else {
                                    palette.cardBorder
                                }
                            )
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "App Lock Protection",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary
                            )
                            Text(
                                text = if (!hasCredential) {
                                    "Set up an authentication method below first"
                                } else if (appLockEnabled) {
                                    "Active • ${protectedPackages.size} apps protected"
                                } else {
                                    "Paused • ${protectedPackages.size} apps selected"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (appLockEnabled) palette.accentStrong else palette.textSecondary
                            )
                        }
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = onToggleAppLockEnabled,
                            colors = resolveSwitchColors(),
                            modifier = Modifier.testTag("applock_enable_switch")
                        )
                    }
                }

                // Protected Apps picker summary card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(cornerRadius = 16.dp)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Protected Applications",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = "${protectedPackages.size} installed apps locked on this device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary
                                )
                            }
                            OutlinedButton(
                                onClick = { showProtectedAppsPicker = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = palette.cobaltContainer.copy(alpha = 0.72f),
                                    contentColor = palette.cobaltStrong
                                ),
                                border = BorderStroke(1.dp, palette.cobaltSlate.copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("applock_choose_apps_button")
                            ) {
                                Text(if (protectedPackages.isEmpty()) "Choose Apps" else "Edit Apps")
                            }
                        }

                        if (protectedPackages.isNotEmpty()) {
                            val nameMap = remember(installedApps) {
                                installedApps.associate { it.packageName to it.appName }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                protectedPackages.take(8).forEach { pkg ->
                                    Text(
                                        text = nameMap[pkg] ?: pkg.substringAfterLast('.'),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.cobaltStrong,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(palette.cobaltContainer)
                                            .border(1.dp, palette.cobaltSlate.copy(alpha = 0.25f), CircleShape)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                if (protectedPackages.size > 8) {
                                    Text(
                                        text = "+${protectedPackages.size - 8} more",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Authentication Method & Credential Setup card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(cornerRadius = 16.dp)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Authentication Method",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.textPrimary
                                )
                                val currentLabel = when (configuredMethod) {
                                    AppLockAuthMethod.PATTERN -> "Pattern Lock"
                                    AppLockAuthMethod.PIN -> "${configuredPinLength.digits}-Digit PIN"
                                    AppLockAuthMethod.SIX_CELL_SEQUENTIAL -> "Six-Cell Sequential"
                                }
                                Text(
                                    text = if (hasCredential) "Active: $currentLabel" else "Not configured yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasCredential) palette.accentStrong else palette.warn
                                )
                            }
                            if (hasCredential) {
                                OutlinedButton(
                                    onClick = {
                                        showCredentialWizard = !showCredentialWizard
                                        firstStepSecret = null
                                        setupError = null
                                        setupResetKey++
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = palette.controlSurface,
                                        contentColor = palette.textPrimary
                                    ),
                                    border = BorderStroke(1.dp, palette.controlBorder),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("applock_change_credential_button")
                                ) {
                                    Text(
                                        text = if (showCredentialWizard) "Close" else "Change",
                                        color = palette.textPrimary
                                    )
                                }
                            }
                        }

                        if (showCredentialWizard || !hasCredential) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AppLockAuthMethod.entries.forEach { method ->
                                    val label = when (method) {
                                        AppLockAuthMethod.PATTERN -> "Pattern"
                                        AppLockAuthMethod.PIN -> "PIN"
                                        AppLockAuthMethod.SIX_CELL_SEQUENTIAL -> "6-Cell Order"
                                    }
                                    val selected = draftMethod == method
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            draftMethod = method
                                            firstStepSecret = null
                                            setupError = null
                                            setupResetKey++
                                        },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = palette.surfaceElevated,
                                            selectedLabelColor = palette.accentStrong,
                                            containerColor = palette.surfaceSecondary,
                                            labelColor = palette.textSecondary
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = selected,
                                            borderColor = palette.cardBorder,
                                            selectedBorderColor = palette.accentSecondary
                                        ),
                                        modifier = Modifier.testTag("applock_method_${method.name.lowercase()}")
                                    )
                                }
                            }

                            if (draftMethod == AppLockAuthMethod.PIN) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppLockPinLength.entries.forEach { len ->
                                        val selected = draftPinLength == len
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                draftPinLength = len
                                                firstStepSecret = null
                                                setupError = null
                                                setupResetKey++
                                            },
                                            label = { Text("${len.digits}-Digit PIN") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = palette.surfaceElevated,
                                                selectedLabelColor = palette.accentStrong,
                                                containerColor = palette.surfaceSecondary,
                                                labelColor = palette.textSecondary
                                            ),
                                            modifier = Modifier.testTag("applock_pin_length_${len.digits}")
                                        )
                                    }
                                }
                            }

                            val setupPrompt = if (firstStepSecret == null) {
                                when (draftMethod) {
                                    AppLockAuthMethod.PATTERN -> "Step 1/2: Draw your new pattern"
                                    AppLockAuthMethod.PIN -> "Step 1/2: Enter a ${draftPinLength.digits}-digit PIN"
                                    AppLockAuthMethod.SIX_CELL_SEQUENTIAL -> "Step 1/2: Create your 4–6 cell tap order"
                                }
                            } else {
                                when (draftMethod) {
                                    AppLockAuthMethod.PATTERN -> "Step 2/2: Confirm your pattern"
                                    AppLockAuthMethod.PIN -> "Step 2/2: Re-enter your ${draftPinLength.digits}-digit PIN"
                                    AppLockAuthMethod.SIX_CELL_SEQUENTIAL -> "Step 2/2: Repeat the exact tap order"
                                }
                            }

                            AppLockAuthChallengePanel(
                                method = draftMethod,
                                pinLength = draftPinLength,
                                isSetupMode = true,
                                promptMessage = setupPrompt,
                                errorMessage = setupError,
                                resetKey = setupResetKey,
                                onCredentialSubmitted = { entered ->
                                    if (firstStepSecret == null) {
                                        firstStepSecret = entered
                                        setupError = null
                                        setupResetKey++
                                    } else if (firstStepSecret == entered) {
                                        onSaveCredential(draftMethod, draftPinLength, entered)
                                        firstStepSecret = null
                                        setupError = null
                                        showCredentialWizard = false
                                        setupResetKey++
                                    } else {
                                        firstStepSecret = null
                                        setupError = "Inputs did not match. Start Step 1 again."
                                        setupResetKey++
                                    }
                                }
                            )

                            FeatureTipNote(
                                text = "Credentials are stored as Android Keystore-wrapped PBKDF2 verifiers locally and never synced.",
                                icon = ResolveIcons.Shield
                            )
                        }
                    }
                }

                // Genuine Platform Enforcement Honesty Card
                item {
                    val isAccOn = capabilities.isAccessibilityServiceEnabled
                    val isOwner = capabilities.deviceOwnerState == DeviceOwnerState.DEVICE_OWNER_ACTIVE
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .resolveGlassCard(
                                cornerRadius = 16.dp,
                                borderColor = if (isAccOn) palette.cardBorder
                                else palette.warn.copy(alpha = 0.42f)
                            )
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = if (isAccOn) palette.accentStrong else palette.warn,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = when {
                                    isOwner && isAccOn -> "Enforcement: Device Owner + Accessibility Guard"
                                    isAccOn -> "Enforcement: Accessibility Foreground Guard"
                                    else -> "Action Needed: Enable Accessibility Service"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.textPrimary
                            )
                        }
                        Text(
                            text = if (isOwner) {
                                "Accessibility Guard locks chosen apps when opened, and Device Owner is active for Lock Task focus sessions. Emergency calls remain available."
                            } else {
                                "On standard devices without Device Owner, App Lock uses Accessibility foreground detection + Resolve full-screen lock prompt. This provides practical app gating, not bypass-proof OS sandboxing (users can revoke Accessibility in Android Settings)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isAccOn) {
                                OutlinedButton(
                                    onClick = onOpenAccessibilitySettings,
                                    border = BorderStroke(1.dp, palette.warn.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("applock_enable_accessibility_button")
                                ) {
                                    Text("Enable Accessibility", color = palette.warn)
                                }
                            }
                            if (!isOwner) {
                                TextButton(onClick = onOpenDeviceOwnerGuide) {
                                    Text("Device Owner Info", color = palette.accentStrong)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProtectedAppsPicker) {
        val eligibleApps = remember(installedApps) {
            installedApps.filter { !it.isDialerOrTelecom }
        }
        ProtectedAppsPickerSheet(
            installedApps = eligibleApps,
            initialSelectedPackages = protectedPackages.toSet(),
            loadIcon = loadIcon,
            onDismiss = { showProtectedAppsPicker = false },
            onSaveSelection = { selected ->
                onSaveProtectedPackages(selected)
                showProtectedAppsPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtectedAppsPickerSheet(
    installedApps: List<InstalledAppInfo>,
    initialSelectedPackages: Set<String>,
    loadIcon: suspend (String) -> ImageBitmap?,
    onDismiss: () -> Unit,
    onSaveSelection: (Set<String>) -> Unit
) {
    val palette = LocalResolveExtendedPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var selectedPackages by remember(initialSelectedPackages) {
        mutableStateOf(initialSelectedPackages)
    }
    val filteredApps = remember(installedApps, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) installedApps
        else installedApps.filter {
            it.appName.contains(q, ignoreCase = true) ||
                it.packageName.contains(q, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surfaceSecondary
    ) {
        ProvideSafeTextToolbar {
            CleanupTextInteractionsOnDispose()
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            val textToolbar = androidx.compose.ui.platform.LocalTextToolbar.current
            val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Select Protected Apps",
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "${selectedPackages.size} apps will require authentication",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                    if (selectedPackages.isNotEmpty()) {
                        TextButton(onClick = { selectedPackages = emptySet() }) {
                            Text("Clear", color = palette.accentStrong)
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val selected = selectedPackages.contains(app.packageName)
                        var iconBitmap by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }
                        LaunchedEffect(app.packageName) {
                            iconBitmap = loadIcon(app.packageName)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) palette.surfaceElevated
                                    else palette.cardBottom
                                )
                                .clickable {
                                    selectedPackages = if (selected) {
                                        selectedPackages - app.packageName
                                    } else {
                                        selectedPackages + app.packageName
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("protected_app_row_${app.packageName}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap!!,
                                    contentDescription = "${app.appName} icon",
                                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(palette.surfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = ResolveIcons.Apps,
                                        contentDescription = null,
                                        tint = palette.textSecondary
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = if (selected) Icons.Outlined.CheckCircle else ResolveIcons.RadioButtonUnchecked,
                                contentDescription = if (selected) "Protected" else "Not protected",
                                tint = if (selected) palette.success else palette.textMuted
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        dismissTextInteractions(focusManager, textToolbar, keyboardController)
                        onSaveSelection(selectedPackages)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryButton,
                        contentColor = palette.onPrimaryButton
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("applock_save_apps_button")
                ) {
                    Text("Save Protected Apps (${selectedPackages.size})")
                }
            }
        }
    }
}
