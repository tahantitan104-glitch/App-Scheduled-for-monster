package com.example.presentation.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.designsystem.LocalResolveExtendedPalette
import com.example.sync.AccountSyncUiState
import com.example.sync.AvatarImageProcessor
import com.example.sync.CloudDeviceInfo

@Composable
fun SyncedAvatarBadge(
    displayName: String,
    avatarBase64: String,
    size: Dp = 56.dp,
    isUploading: Boolean = false,
    showEditBadge: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalResolveExtendedPalette.current
    val decodedBitmap = remember(avatarBase64) {
        AvatarImageProcessor.decodeAvatarBitmap(avatarBase64)?.asImageBitmap()
    }
    val initials = remember(displayName) {
        val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "RF"
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .let { base ->
                if (onClick != null) {
                    base
                        .minimumInteractiveComponentSize()
                        .clickable(role = Role.Button, onClick = onClick)
                } else {
                    base
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(palette.accentSoftContainer)
                .border(1.5.dp, palette.accent.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (decodedBitmap != null) {
                Image(
                    bitmap = decodedBitmap,
                    contentDescription = "$displayName profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.34f).sp
                    ),
                    color = palette.textPrimary
                )
            }

            if (isUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.background.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = palette.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(size * 0.42f)
                    )
                }
            }
        }

        if (showEditBadge && !isUploading) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(palette.primaryButton)
                    .border(1.5.dp, palette.background, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ResolveIcons.AddAPhoto,
                    contentDescription = "Change profile photo",
                    tint = palette.onPrimaryButton,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * Optional, non-blocking Home card shown ONLY while signed out and not dismissed.
 * Once the user links a Google account, Home hides all promo copy automatically.
 */
@Composable
fun HomeOptionalSyncBanner(
    isAuthBusy: Boolean,
    onConnectGoogle: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalResolveExtendedPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .resolveGlassCard(
                cornerRadius = 16.dp,
                borderColor = palette.cardBorder
            )
            .padding(16.dp)
            .testTag("home_optional_sync_banner"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.cobaltContainer)
                        .border(1.dp, palette.cobaltSlate.copy(alpha = 0.30f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ResolveIcons.Sync,
                        contentDescription = null,
                        tint = palette.cobaltStrong,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Multi-Device Lock Sync (Optional)",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "Offline mode works anytime without signing in.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.cobaltStrong
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("home_sync_dismiss_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Stay offline and hide sync prompt",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Text(
            text = "Connect a Google account on 2+ phones to sync Focus Locks across devices — while keeping Mascots, Allowed Apps & preferences separate on each phone.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onConnectGoogle,
                enabled = !isAuthBusy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primaryButton,
                    contentColor = palette.onPrimaryButton
                ),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("home_sync_connect_button")
            ) {
                if (isAuthBusy) {
                    CircularProgressIndicator(
                        color = palette.onPrimaryButton,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (isAuthBusy) "Connecting…" else "Connect Google",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("home_sync_offline_button")
            ) {
                Text(
                    text = "Stay Offline",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.textSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsAccountSyncCard(
    state: AccountSyncUiState,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onToggleSyncEnabled: (Boolean) -> Unit,
    onPickAvatarUri: (Uri) -> Unit,
    onClearAvatar: () -> Unit,
    modifier: Modifier = Modifier,
    onUnlinkDevice: (String) -> Unit = {},
    onUpdateProfileName: (String) -> Unit = {}
) {
    val palette = LocalResolveExtendedPalette.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPickAvatarUri(uri)
        }
    }
    var pendingUnlinkDevice by remember { mutableStateOf<CloudDeviceInfo?>(null) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var draftName by remember(state.displayName) {
        mutableStateOf(state.displayName.ifBlank { "Focus Explorer" })
    }

    AnimatedContent(
        targetState = state.isSignedIn,
        transitionSpec = {
            fadeIn(tween(240, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(tween(180, easing = FastOutSlowInEasing))
        },
        label = "settings_account_sync_card",
        modifier = modifier.fillMaxWidth()
    ) { signedIn ->
        val resolvedName = state.displayName.ifBlank {
            if (signedIn) "Resolve Focus Account" else "Focus Explorer"
        }
        val launchPhotoPicker: () -> Unit = {
            runCatching {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }

        if (!signedIn) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .resolveGlassCard(
                        cornerRadius = 16.dp,
                        borderColor = palette.cardBorder
                    )
                    .padding(16.dp)
                    .testTag("settings_account_signed_out_card"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Compact Profile Header (Picture, Name, Connected Account, Sync Status)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SyncedAvatarBadge(
                        displayName = resolvedName,
                        avatarBase64 = state.avatarBase64,
                        size = 54.dp,
                        isUploading = state.isAvatarUploading,
                        showEditBadge = true,
                        onClick = launchPhotoPicker,
                        modifier = Modifier.testTag("settings_profile_avatar_button")
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = resolvedName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            IconButton(
                                onClick = {
                                    draftName = resolvedName
                                    showEditNameDialog = true
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("settings_profile_edit_name_button")
                            ) {
                                Icon(
                                    imageVector = ResolveIcons.Edit,
                                    contentDescription = "Edit profile name",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (state.avatarBase64.isNotBlank()) {
                                TextButton(
                                    onClick = onClearAvatar,
                                    enabled = !state.isAvatarUploading,
                                    modifier = Modifier.testTag("settings_clear_avatar_button")
                                ) {
                                    Text(
                                        text = "Reset",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textSecondary
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Google Account: Not Connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Sync Status: Offline Mode • Works without an account",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.slateTealStrong,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = onSignInClick,
                    enabled = !state.isAuthBusy,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryButton,
                        contentColor = palette.onPrimaryButton
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_google_signin_button")
                ) {
                    if (state.isAuthBusy) {
                        CircularProgressIndicator(
                            color = palette.onPrimaryButton,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (state.isAuthBusy) "Connecting Google Account…" else "Sign In with Google for Sync",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        } else {
            // Connected state: compact profile + Google account + Sync status + Linked Devices
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .resolveGlassCard(
                        cornerRadius = 16.dp,
                        borderColor = palette.accentSecondary.copy(alpha = 0.45f)
                    )
                    .padding(16.dp)
                    .testTag("settings_account_signed_in_card"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SyncedAvatarBadge(
                        displayName = resolvedName,
                        avatarBase64 = state.avatarBase64,
                        size = 56.dp,
                        isUploading = state.isAvatarUploading,
                        showEditBadge = true,
                        onClick = launchPhotoPicker,
                        modifier = Modifier.testTag("settings_profile_avatar_button")
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = resolvedName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            IconButton(
                                onClick = {
                                    draftName = resolvedName
                                    showEditNameDialog = true
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("settings_profile_edit_name_button")
                            ) {
                                Icon(
                                    imageVector = ResolveIcons.Edit,
                                    contentDescription = "Edit profile name",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (state.email.isNotBlank()) {
                            Text(
                                text = state.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PulsingStatusDot(
                                color = if (state.syncLockEnabled) palette.accentStrong else palette.accentSecondary,
                                size = 5.dp
                            )
                            val syncLabel = if (state.syncLockEnabled) {
                                "Google Sync Active • ${state.activeSyncedDeviceCount.coerceAtLeast(1)} device(s)"
                            } else {
                                "Google Connected • Sync Paused"
                            }
                            Text(
                                text = syncLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.slateTealStrong,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (state.avatarBase64.isNotBlank()) {
                        TextButton(
                            onClick = onClearAvatar,
                            enabled = !state.isAvatarUploading,
                            modifier = Modifier.testTag("settings_clear_avatar_button")
                        ) {
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary
                            )
                        }
                    }
                }

                // Connected Devices Pills with safe Unlink trigger
                if (state.connectedDevices.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Connected Devices (tap to unlink safely)",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            state.connectedDevices.forEach { device ->
                                val isThisPhone = device.deviceId == state.thisDeviceId
                                val isUnlinking = state.unlinkingDeviceId == device.deviceId
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (device.syncEnabled) {
                                                palette.surfaceElevated
                                            } else {
                                                palette.surfaceSecondary
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (device.syncEnabled) {
                                                palette.accentSecondary.copy(alpha = 0.45f)
                                            } else {
                                                palette.cardBorder
                                            },
                                            CircleShape
                                        )
                                        .clickable(enabled = !isUnlinking) {
                                            pendingUnlinkDevice = device
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                        .testTag("linked_device_chip_${device.deviceId}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (isUnlinking) {
                                        CircularProgressIndicator(
                                            color = palette.slateTealStrong,
                                            strokeWidth = 1.5.dp,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = ResolveIcons.Devices,
                                            contentDescription = null,
                                            tint = if (device.syncEnabled) palette.slateTealStrong else palette.textSecondary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isThisPhone) "${device.deviceName} (This)" else device.deviceName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textPrimary
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Unlink ${device.deviceName}",
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Sync toggle for this phone
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.surfaceSecondary)
                        .border(1.dp, palette.cardBorder, RoundedCornerShape(16.dp))
                        .clickable { onToggleSyncEnabled(!state.syncLockEnabled) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sync Lock Across Devices",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.textPrimary
                        )
                        Text(
                            text = if (state.syncLockEnabled) {
                                "Active — eligible remote locks can trigger here"
                            } else {
                                "Paused — this phone operates independently"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                    Switch(
                        checked = state.syncLockEnabled,
                        onCheckedChange = onToggleSyncEnabled,
                        colors = resolveSwitchColors(),
                        modifier = Modifier.testTag("settings_device_sync_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FeatureTipNote(
                        text = "Tap photo or edit icon anytime.",
                        icon = ResolveIcons.AddAPhoto,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedButton(
                        onClick = onSignOutClick,
                        enabled = !state.isAuthBusy,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            palette.cardBorder
                        ),
                        modifier = Modifier.testTag("settings_google_signout_button")
                    ) {
                        Icon(
                            imageVector = ResolveIcons.Logout,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Sign Out",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textSecondary
                        )
                    }
                }
            }
        }
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            containerColor = palette.surfaceSecondary,
            title = {
                Text(
                    text = "Profile Name",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textPrimary
                )
            },
            text = {
                ProvideSafeTextToolbar {
                    androidx.compose.material3.OutlinedTextField(
                        value = draftName,
                        onValueChange = { if (it.length <= 48) draftName = it },
                        singleLine = true,
                        label = { Text("Display Name") },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.cardBorder,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_profile_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateProfileName(draftName)
                        showEditNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryButton,
                        contentColor = palette.onPrimaryButton
                    ),
                    modifier = Modifier.testTag("settings_profile_name_save_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            }
        )
    }

    pendingUnlinkDevice?.let { device ->
        val isThis = device.deviceId == state.thisDeviceId
        AlertDialog(
            onDismissRequest = { pendingUnlinkDevice = null },
            containerColor = palette.surfaceSecondary,
            title = {
                Text(
                    text = "Unlink ${device.deviceName}?",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textPrimary
                )
            },
            text = {
                Text(
                    text = if (isThis) {
                        "This disconnects this phone from cloud sync and signs out cleanly. All local focus timers, mascots, App Lock, and history stay untouched on this phone."
                    } else {
                        "Removes ${device.deviceName} from your synced device list without erasing or modifying any local data on that phone."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = device.deviceId
                        pendingUnlinkDevice = null
                        onUnlinkDevice(id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryButton,
                        contentColor = palette.onPrimaryButton
                    ),
                    modifier = Modifier.testTag("confirm_unlink_device_button")
                ) {
                    Text("Unlink Device")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnlinkDevice = null }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun SyncBenefitBullet(text: String) {
    val palette = LocalResolveExtendedPalette.current
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = palette.success,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(15.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary
        )
    }
}
