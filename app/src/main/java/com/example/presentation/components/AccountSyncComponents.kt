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
import androidx.compose.runtime.remember
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
import com.example.designsystem.ResolveColors
import com.example.sync.AccountSyncUiState
import com.example.sync.AvatarImageProcessor

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
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            ResolveColors.MintSoftContainer,
                            ResolveColors.ForestSurfaceElevated
                        )
                    )
                )
                .border(1.5.dp, ResolveColors.MintAccent.copy(alpha = 0.55f), CircleShape),
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
                    color = ResolveColors.MintBright
                )
            }

            if (isUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ResolveColors.ForestDeep.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = ResolveColors.MintAccent,
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
                    .background(ResolveColors.MintAccent)
                    .border(1.5.dp, ResolveColors.ForestDeep, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ResolveIcons.AddAPhoto,
                    contentDescription = "Change profile photo",
                    tint = ResolveColors.ForestDeep,
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .resolveGlassCard(
                cornerRadius = 22.dp,
                borderColor = ResolveColors.MintAccent.copy(alpha = 0.35f)
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
                        .background(ResolveColors.MintSoftContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ResolveIcons.Sync,
                        contentDescription = null,
                        tint = ResolveColors.MintAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Multi-Device Lock Sync (Optional)",
                        style = MaterialTheme.typography.titleMedium,
                        color = ResolveColors.TextPrimary
                    )
                    Text(
                        text = "Offline mode works anytime without signing in.",
                        style = MaterialTheme.typography.labelSmall,
                        color = ResolveColors.MintAccent
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
                    tint = ResolveColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Text(
            text = "Connect a Google account on 2+ phones to sync Focus Locks across devices — while keeping Mascots, Allowed Apps & preferences separate on each phone.",
            style = MaterialTheme.typography.bodySmall,
            color = ResolveColors.TextSecondary
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
                    containerColor = ResolveColors.MintAccent,
                    contentColor = ResolveColors.ForestDeep
                ),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("home_sync_connect_button")
            ) {
                if (isAuthBusy) {
                    CircularProgressIndicator(
                        color = ResolveColors.ForestDeep,
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
                border = androidx.compose.foundation.BorderStroke(1.dp, ResolveColors.ForestOutline),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("home_sync_offline_button")
            ) {
                Text(
                    text = "Stay Offline",
                    style = MaterialTheme.typography.labelLarge,
                    color = ResolveColors.TextSecondary
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
    modifier: Modifier = Modifier
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPickAvatarUri(uri)
        }
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
        if (!signedIn) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .resolveGlassCard(
                        cornerRadius = 22.dp,
                        borderColor = ResolveColors.ForestOutline
                    )
                    .padding(18.dp)
                    .testTag("settings_account_signed_out_card"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ResolveColors.MintSoftContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = ResolveIcons.Devices,
                            contentDescription = null,
                            tint = ResolveColors.MintAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connect Multiple Devices",
                            style = MaterialTheme.typography.titleMedium,
                            color = ResolveColors.TextPrimary
                        )
                        Text(
                            text = "Optional Google Account Sync • Free",
                            style = MaterialTheme.typography.labelSmall,
                            color = ResolveColors.MintAccent
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    SyncBenefitBullet("Start a Focus Lock on one phone and sync it automatically to your other phones.")
                    SyncBenefitBullet("Choose \"Sync all devices\" or \"This phone only\" for every lock.")
                    SyncBenefitBullet("Mascot, Allowed Apps & Permissions stay private per device. No account required to use RESOLVE.")
                }

                Button(
                    onClick = onSignInClick,
                    enabled = !state.isAuthBusy,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ResolveColors.MintAccent,
                        contentColor = ResolveColors.ForestDeep
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_google_signin_button")
                ) {
                    if (state.isAuthBusy) {
                        CircularProgressIndicator(
                            color = ResolveColors.ForestDeep,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (state.isAuthBusy) "Connecting Google Account…" else "Sign In with Google",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                FeatureTipNote(
                    text = "Tip: Prefer 100% offline? Skip sign-in — every feature works without an account.",
                    icon = Icons.Outlined.Info
                )
            }
        } else {
            // Connected state: hides unnecessary explanatory pitch & displays crisp profile + synced avatar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .resolveGlassCard(
                        cornerRadius = 22.dp,
                        borderColor = ResolveColors.MintAccent.copy(alpha = 0.42f)
                    )
                    .padding(18.dp)
                    .testTag("settings_account_signed_in_card"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SyncedAvatarBadge(
                        displayName = state.displayName,
                        avatarBase64 = state.avatarBase64,
                        size = 58.dp,
                        isUploading = state.isAvatarUploading,
                        showEditBadge = true,
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("settings_profile_avatar_button")
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.displayName.ifBlank { "Resolve Focus Account" },
                            style = MaterialTheme.typography.titleMedium,
                            color = ResolveColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.email.isNotBlank()) {
                            Text(
                                text = state.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = ResolveColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PulsingStatusDot(color = ResolveColors.MintAccent, size = 5.dp)
                            Text(
                                text = "Connected • ${state.thisDeviceName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ResolveColors.MintAccent,
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
                                color = ResolveColors.TextSecondary
                            )
                        }
                    }
                }

                // Connected Devices Pills
                if (state.connectedDevices.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.connectedDevices.forEach { device ->
                            val isThisPhone = device.deviceId == state.thisDeviceId
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (device.syncEnabled) {
                                            ResolveColors.MintSoftContainer.copy(alpha = 0.7f)
                                        } else {
                                            ResolveColors.ForestSurfaceElevated
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        if (device.syncEnabled) {
                                            ResolveColors.MintAccent.copy(alpha = 0.3f)
                                        } else {
                                            ResolveColors.ForestOutline
                                        },
                                        CircleShape
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = ResolveIcons.Devices,
                                    contentDescription = null,
                                    tint = if (device.syncEnabled) ResolveColors.MintAccent else ResolveColors.TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isThisPhone) "${device.deviceName} (This)" else device.deviceName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ResolveColors.TextPrimary
                                )
                            }
                        }
                    }
                }

                // Sync toggle for this phone
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ResolveColors.ForestDeep.copy(alpha = 0.55f))
                        .border(1.dp, ResolveColors.ForestOutline, RoundedCornerShape(16.dp))
                        .clickable { onToggleSyncEnabled(!state.syncLockEnabled) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sync Lock Across Devices",
                            style = MaterialTheme.typography.titleSmall,
                            color = ResolveColors.TextPrimary
                        )
                        Text(
                            text = if (state.syncLockEnabled) {
                                "Active — locks started on linked devices engage here"
                            } else {
                                "Paused — this phone operates independently"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = ResolveColors.TextSecondary
                        )
                    }
                    Switch(
                        checked = state.syncLockEnabled,
                        onCheckedChange = onToggleSyncEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ResolveColors.ForestDeep,
                            checkedTrackColor = ResolveColors.MintAccent
                        ),
                        modifier = Modifier.testTag("settings_device_sync_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FeatureTipNote(
                        text = "Tap photo to update across devices.",
                        icon = ResolveIcons.AddAPhoto,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedButton(
                        onClick = onSignOutClick,
                        enabled = !state.isAuthBusy,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            ResolveColors.ForestOutline
                        ),
                        modifier = Modifier.testTag("settings_google_signout_button")
                    ) {
                        Icon(
                            imageVector = ResolveIcons.Logout,
                            contentDescription = null,
                            tint = ResolveColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Sign Out",
                            style = MaterialTheme.typography.labelMedium,
                            color = ResolveColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncBenefitBullet(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = ResolveColors.MintAccent,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(15.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = ResolveColors.TextSecondary
        )
    }
}
