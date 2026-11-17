package com.example.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.designsystem.ResolveColors
import com.example.domain.model.DefaultFocusMessages
import com.example.domain.model.DefaultLockReminders
import com.example.domain.model.FocusMessageFrequency
import com.example.domain.model.FocusMessageItem
import com.example.domain.model.LockReminderStyle
import com.example.domain.model.MascotAnimationState
import com.example.domain.model.MascotMicroAnimation
import com.example.domain.model.MascotPreferences
import com.example.domain.model.MascotType
import com.example.presentation.components.FeatureTipNote
import com.example.presentation.components.MascotRenderer
import com.example.presentation.components.ProvideSafeTextToolbar
import com.example.presentation.components.dismissTextInteractions
import com.example.presentation.components.resolveGlassCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MascotAppearanceCard(
    preferences: MascotPreferences,
    onSelectMascot: (MascotType) -> Unit,
    onToggleReducedMotion: (Boolean) -> Unit,
    onOpenFocusMessagesManager: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleLockReminderEnabled: (Boolean) -> Unit = {},
    onOpenLockReminderEditor: () -> Unit = {}
) {
    val activeMascot = preferences.selectedMascot
    val previewScope = rememberCoroutineScope()
    var previewState by remember(activeMascot) { mutableStateOf(MascotAnimationState.IDLE) }
    var previewMicro by remember(activeMascot) { mutableStateOf(MascotMicroAnimation.NONE) }
    var peekProgress by remember(activeMascot) { mutableStateOf(1f) }
    var peekJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    DisposableEffect(activeMascot) {
        onDispose { peekJob?.cancel() }
    }

    LaunchedEffect(activeMascot, preferences.reducedMotionEnabled) {
        if (activeMascot == MascotType.RESOLVE_BUNNY && !preferences.reducedMotionEnabled) {
            peekJob?.cancel()
            previewState = MascotAnimationState.PEEK_ENTRANCE
            peekProgress = 0f
            val steps = 70
            for (i in 1..steps) {
                delay(32L)
                peekProgress = i / steps.toFloat()
            }
            previewState = MascotAnimationState.IDLE
            peekProgress = 1f
        } else {
            previewState = MascotAnimationState.IDLE
            previewMicro = MascotMicroAnimation.NONE
            peekProgress = 1f
        }
    }

    fun triggerPreviewReaction(
        state: MascotAnimationState,
        micro: MascotMicroAnimation = MascotMicroAnimation.NONE
    ) {
        peekJob?.cancel()
        if (preferences.reducedMotionEnabled) {
            previewState = MascotAnimationState.IDLE
            previewMicro = MascotMicroAnimation.NONE
            peekProgress = 1f
            return
        }
        peekJob = previewScope.launch {
            if (state == MascotAnimationState.PEEK_ENTRANCE) {
                previewState = MascotAnimationState.PEEK_ENTRANCE
                previewMicro = MascotMicroAnimation.NONE
                peekProgress = 0f
                val steps = 75
                for (i in 1..steps) {
                    delay(34L)
                    peekProgress = i / steps.toFloat()
                }
                previewState = MascotAnimationState.IDLE
                peekProgress = 1f
            } else {
                previewState = state
                previewMicro = micro
                peekProgress = 1f
                delay(1850L)
                previewState = MascotAnimationState.IDLE
                previewMicro = MascotMicroAnimation.NONE
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .resolveGlassCard(cornerRadius = 22.dp)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "Mascot Companion",
                    style = MaterialTheme.typography.titleMedium,
                    color = ResolveColors.TextPrimary
                )
                Text(
                    text = "Select one companion to appear across Home and Lock Screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ResolveColors.TextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(ResolveColors.ForestDeep.copy(alpha = 0.78f))
                    .border(1.dp, ResolveColors.MintAccent.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                    .testTag("mascot_settings_live_preview"),
                contentAlignment = Alignment.Center
            ) {
                MascotRenderer(
                    mascot = activeMascot,
                    size = 64.dp,
                    showFocusAura = false,
                    animationState = previewState,
                    microAnimation = previewMicro,
                    peekProgress = peekProgress,
                    reducedMotion = preferences.reducedMotionEnabled
                )
            }
        }

        // Mutually exclusive 2-option selector (Baby Cloud OR RESOLVE Bunny, never Both)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MascotType.entries.forEach { option ->
                MascotOptionRow(
                    option = option,
                    selected = activeMascot == option,
                    reducedMotion = preferences.reducedMotionEnabled,
                    onClick = { onSelectMascot(option) }
                )
            }
            FeatureTipNote(
                text = "Tip: One active companion syncs across Home & Lock Screen.",
                icon = Icons.Outlined.Info
            )
        }

        // Interactive Animation Preview strip for active mascot
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ResolveColors.ForestDeep.copy(alpha = 0.45f))
                .border(1.dp, ResolveColors.ForestOutline, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "PREVIEW ANIMATIONS",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.0.sp),
                color = ResolveColors.MintAccent
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (activeMascot == MascotType.RESOLVE_BUNNY) {
                    PreviewActionChip(
                        label = "Peek-a-Boo",
                        active = previewState == MascotAnimationState.PEEK_ENTRANCE,
                        testTag = "mascot_preview_peek",
                        onClick = { triggerPreviewReaction(MascotAnimationState.PEEK_ENTRANCE) }
                    )
                    PreviewActionChip(
                        label = "Curious",
                        active = previewState == MascotAnimationState.CURIOUS,
                        testTag = "mascot_preview_curious",
                        onClick = { triggerPreviewReaction(MascotAnimationState.CURIOUS) }
                    )
                    PreviewActionChip(
                        label = "Happy Dance",
                        active = previewState == MascotAnimationState.HAPPY_DANCE,
                        testTag = "mascot_preview_dance",
                        onClick = { triggerPreviewReaction(MascotAnimationState.HAPPY_DANCE) }
                    )
                    PreviewActionChip(
                        label = "Ear Wiggle",
                        active = previewMicro == MascotMicroAnimation.EAR_WIGGLE,
                        testTag = "mascot_preview_wiggle",
                        onClick = {
                            triggerPreviewReaction(
                                MascotAnimationState.IDLE,
                                MascotMicroAnimation.EAR_WIGGLE
                            )
                        }
                    )
                } else {
                    PreviewActionChip(
                        label = "Pointing",
                        active = previewState == MascotAnimationState.POINTING,
                        testTag = "mascot_preview_pointing",
                        onClick = { triggerPreviewReaction(MascotAnimationState.POINTING) }
                    )
                    PreviewActionChip(
                        label = "Celebrate",
                        active = previewState == MascotAnimationState.CELEBRATING,
                        testTag = "mascot_preview_celebrate",
                        onClick = { triggerPreviewReaction(MascotAnimationState.CELEBRATING) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ResolveColors.ForestDeep.copy(alpha = 0.55f))
                .border(1.dp, ResolveColors.ForestOutline, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "Reduced Motion",
                    style = MaterialTheme.typography.titleSmall,
                    color = ResolveColors.TextPrimary
                )
                Text(
                    text = "Show calm static poses with subtle message display only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ResolveColors.TextSecondary
                )
            }
            Switch(
                checked = preferences.reducedMotionEnabled,
                onCheckedChange = onToggleReducedMotion,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ResolveColors.ForestDeep,
                    checkedTrackColor = ResolveColors.MintAccent,
                    uncheckedThumbColor = ResolveColors.TextSecondary,
                    uncheckedTrackColor = ResolveColors.ForestSurfaceElevated
                ),
                modifier = Modifier.testTag("settings_reduced_motion_switch")
            )
        }

        val freqSummary = if (!preferences.focusMessagesEnabled ||
            preferences.messageFrequency == FocusMessageFrequency.DISABLED
        ) {
            "Disabled"
        } else {
            "${preferences.messages.size} messages • ${preferences.messageFrequency.label}"
        }

        OutlinedButton(
            onClick = onOpenFocusMessagesManager,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ResolveColors.MintAccent),
            border = BorderStroke(1.dp, ResolveColors.MintAccent.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("settings_focus_messages_manage_button")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Focus Messages",
                        style = MaterialTheme.typography.titleSmall,
                        color = ResolveColors.MintAccent
                    )
                    Text(
                        text = freqSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = ResolveColors.TextSecondary
                    )
                }
                Text(
                    text = "Customize",
                    style = MaterialTheme.typography.labelLarge,
                    color = ResolveColors.MintBright
                )
            }
        }

        // Optional Lock Screen Motivational Reminder Anchor
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ResolveColors.ForestDeep.copy(alpha = 0.55f))
                .border(1.dp, ResolveColors.ForestOutline, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Lock Screen Reminder",
                        style = MaterialTheme.typography.titleSmall,
                        color = ResolveColors.TextPrimary
                    )
                    Text(
                        text = "Show a motivational quote under your lock timer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ResolveColors.TextSecondary
                    )
                }
                Switch(
                    checked = preferences.lockReminderEnabled,
                    onCheckedChange = onToggleLockReminderEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ResolveColors.ForestDeep,
                        checkedTrackColor = ResolveColors.MintAccent,
                        uncheckedThumbColor = ResolveColors.TextSecondary,
                        uncheckedTrackColor = ResolveColors.ForestSurfaceElevated
                    ),
                    modifier = Modifier.testTag("settings_lock_reminder_switch")
                )
            }

            AnimatedVisibility(
                visible = preferences.lockReminderEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedButton(
                    onClick = onOpenLockReminderEditor,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ResolveColors.MintAccent),
                    border = BorderStroke(1.dp, ResolveColors.MintAccent.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("settings_lock_reminder_customize_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "\"${preferences.lockReminderText}\"",
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = ResolveColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Style: ${preferences.lockReminderStyle.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ResolveColors.TextSecondary
                            )
                        }
                        Text(
                            text = "Edit",
                            style = MaterialTheme.typography.labelLarge,
                            color = ResolveColors.MintBright
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewActionChip(
    label: String,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = active,
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = ResolveColors.MintSoftContainer,
            selectedLabelColor = ResolveColors.MintBright,
            selectedLeadingIconColor = ResolveColors.MintBright,
            containerColor = ResolveColors.ForestSurface,
            labelColor = ResolveColors.TextSecondary,
            iconColor = ResolveColors.MintAccent
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = active,
            borderColor = ResolveColors.ForestOutline,
            selectedBorderColor = ResolveColors.MintAccent
        ),
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
private fun MascotOptionRow(
    option: MascotType,
    selected: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) ResolveColors.MintAccent else ResolveColors.ForestOutline
    val bgColor = if (selected) {
        ResolveColors.MintSoftContainer.copy(alpha = 0.72f)
    } else {
        ResolveColors.ForestDeep.copy(alpha = 0.55f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(
                role = Role.RadioButton,
                onClick = onClick
            )
            .semantics {
                contentDescription = "${option.displayName}, ${if (selected) "selected" else "not selected"}"
            }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("mascot_option_${option.storageKey}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ResolveColors.ForestSurface.copy(alpha = 0.8f))
                .border(1.dp, ResolveColors.ForestOutline, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            MascotRenderer(
                mascot = option,
                size = 38.dp,
                showFocusAura = false,
                reducedMotion = reducedMotion,
                isPaused = !selected
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.displayName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = ResolveColors.TextPrimary
            )
            Text(
                text = option.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ResolveColors.TextSecondary
            )
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) ResolveColors.MintAccent else ResolveColors.ForestSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = ResolveColors.ForestDeep,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusMessagesManagerSheet(
    preferences: MascotPreferences,
    onDismiss: () -> Unit,
    onToggleMessagesEnabled: (Boolean) -> Unit,
    onSelectFrequency: (FocusMessageFrequency) -> Unit,
    onAddMessage: (String) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onRestoreDefaults: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<FocusMessageItem?>(null) }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ResolveColors.ForestSecondary
    ) {
        ProvideSafeTextToolbar {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Focus Messages",
                            style = MaterialTheme.typography.titleLarge,
                            color = ResolveColors.TextPrimary
                        )
                        Text(
                            text = "Displayed gently in the mascot speech bubble during Lock Mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ResolveColors.TextSecondary
                        )
                    }
                    Switch(
                        checked = preferences.focusMessagesEnabled,
                        onCheckedChange = onToggleMessagesEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ResolveColors.ForestDeep,
                            checkedTrackColor = ResolveColors.MintAccent,
                            uncheckedThumbColor = ResolveColors.TextSecondary,
                            uncheckedTrackColor = ResolveColors.ForestSurfaceElevated
                        ),
                        modifier = Modifier.testTag("focus_messages_enable_switch")
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "MESSAGE FREQUENCY",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                        color = ResolveColors.MintAccent
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FocusMessageFrequency.entries.forEach { freq ->
                            val selected = preferences.messageFrequency == freq
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectFrequency(freq) },
                                label = { Text(freq.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ResolveColors.MintSoftContainer,
                                    selectedLabelColor = ResolveColors.MintBright,
                                    containerColor = ResolveColors.ForestSurface,
                                    labelColor = ResolveColors.TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = ResolveColors.ForestOutline,
                                    selectedBorderColor = ResolveColors.MintAccent
                                ),
                                modifier = Modifier.testTag("focus_message_frequency_${freq.storageKey}")
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ResolveColors.MintAccent,
                            contentColor = ResolveColors.ForestDeep
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("focus_message_add_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Add Message")
                    }

                    OutlinedButton(
                        onClick = onRestoreDefaults,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ResolveColors.MintAccent),
                        border = BorderStroke(1.dp, ResolveColors.ForestOutline),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("focus_message_restore_defaults_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Restore")
                    }
                }

                if (preferences.messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ResolveColors.ForestSurface)
                            .border(1.dp, ResolveColors.ForestOutline, RoundedCornerShape(16.dp))
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No focus messages configured. Add your own or tap Restore.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ResolveColors.TextSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .testTag("focus_messages_list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(preferences.messages, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ResolveColors.ForestSurface)
                                    .border(1.dp, ResolveColors.ForestOutline, RoundedCornerShape(14.dp))
                                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ResolveColors.TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (item.isCustom) "Custom" else "Default",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ResolveColors.TextSecondary
                                    )
                                }
                                IconButton(
                                    onClick = { editingItem = item },
                                    modifier = Modifier.testTag("focus_message_edit_${item.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Edit message",
                                        tint = ResolveColors.MintAccent
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteMessage(item.id) },
                                    modifier = Modifier.testTag("focus_message_delete_${item.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Delete message",
                                        tint = ResolveColors.ErrorCoral
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        FocusMessageEditorDialog(
            title = "Add Focus Message",
            initialText = "",
            onDismiss = { showAddDialog = false },
            onConfirm = { text ->
                onAddMessage(text)
                showAddDialog = false
            }
        )
    }

    editingItem?.let { current ->
        FocusMessageEditorDialog(
            title = "Edit Focus Message",
            initialText = current.text,
            onDismiss = { editingItem = null },
            onConfirm = { text ->
                onEditMessage(current.id, text)
                editingItem = null
            }
        )
    }
}

@Composable
private fun FocusMessageEditorDialog(
    title: String,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    val trimmed = text.trim()
    val canSave = trimmed.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ResolveColors.ForestSecondary,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = ResolveColors.TextPrimary
            )
        },
        text = {
            ProvideSafeTextToolbar {
                val focusManager = LocalFocusManager.current
                val textToolbar = LocalTextToolbar.current
                val keyboardController = LocalSoftwareKeyboardController.current

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { next ->
                            if (next.length <= DefaultFocusMessages.MAX_MESSAGE_LENGTH) {
                                text = next
                            }
                        },
                        singleLine = true,
                        label = { Text("Encouragement message") },
                        placeholder = { Text("Stay calm and keep going!") },
                        supportingText = {
                            Text("${trimmed.length}/${DefaultFocusMessages.MAX_MESSAGE_LENGTH}")
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ResolveColors.MintAccent,
                            unfocusedBorderColor = ResolveColors.ForestOutline,
                            focusedTextColor = ResolveColors.TextPrimary,
                            unfocusedTextColor = ResolveColors.TextPrimary,
                            focusedLabelColor = ResolveColors.MintAccent,
                            unfocusedLabelColor = ResolveColors.TextSecondary,
                            cursorColor = ResolveColors.MintAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("focus_message_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                dismissTextInteractions(focusManager, textToolbar, keyboardController)
                                onDismiss()
                            }
                        ) {
                            Text("Cancel", color = ResolveColors.TextSecondary)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = canSave,
                            onClick = {
                                dismissTextInteractions(focusManager, textToolbar, keyboardController)
                                onConfirm(trimmed)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ResolveColors.MintAccent,
                                contentColor = ResolveColors.ForestDeep
                            ),
                            modifier = Modifier.testTag("focus_message_save_button")
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LockReminderEditorSheet(
    preferences: MascotPreferences,
    onDismiss: () -> Unit,
    onSelectStyle: (LockReminderStyle) -> Unit,
    onSaveText: (String) -> Unit
) {
    var draftText by remember(preferences.lockReminderText) {
        mutableStateOf(preferences.lockReminderText)
    }
    var selectedStyle by remember(preferences.lockReminderStyle) {
        mutableStateOf(preferences.lockReminderStyle)
    }
    val trimmed = draftText.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    val canSave = trimmed.isNotEmpty()
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ResolveColors.ForestSecondary
    ) {
        ProvideSafeTextToolbar {
            val focusManager = LocalFocusManager.current
            val textToolbar = LocalTextToolbar.current
            val keyboardController = LocalSoftwareKeyboardController.current

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Lock Screen Reminder",
                        style = MaterialTheme.typography.titleLarge,
                        color = ResolveColors.TextPrimary
                    )
                    Text(
                        text = "Write your own focus anchor or pick an effective preset sentence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ResolveColors.TextSecondary
                    )

                    // Style selector: Card Box vs Minimal Text (No Box)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "DISPLAY STYLE",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                            color = ResolveColors.MintAccent
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LockReminderStyle.entries.forEach { styleOption ->
                                val selected = selectedStyle == styleOption
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedStyle = styleOption
                                        onSelectStyle(styleOption)
                                    },
                                    label = { Text(styleOption.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ResolveColors.MintSoftContainer,
                                        selectedLabelColor = ResolveColors.MintBright,
                                        containerColor = ResolveColors.ForestSurface,
                                        labelColor = ResolveColors.TextSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = ResolveColors.ForestOutline,
                                        selectedBorderColor = ResolveColors.MintAccent
                                    ),
                                    modifier = Modifier.testTag("lock_reminder_style_${styleOption.storageKey}")
                                )
                            }
                        }
                    }

                    // Custom sentence input
                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { next ->
                            if (next.length <= DefaultLockReminders.MAX_REMINDER_LENGTH) {
                                draftText = next
                            }
                        },
                        maxLines = 2,
                        label = { Text("Your custom reminder sentence") },
                        placeholder = { Text(DefaultLockReminders.DEFAULT_TEXT) },
                        supportingText = {
                            Text("${trimmed.length}/${DefaultLockReminders.MAX_REMINDER_LENGTH}")
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ResolveColors.MintAccent,
                            unfocusedBorderColor = ResolveColors.ForestOutline,
                            focusedTextColor = ResolveColors.TextPrimary,
                            unfocusedTextColor = ResolveColors.TextPrimary,
                            focusedLabelColor = ResolveColors.MintAccent,
                            unfocusedLabelColor = ResolveColors.TextSecondary,
                            cursorColor = ResolveColors.MintAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lock_reminder_input")
                    )

                    // Live Preview Box / Minimal Text
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "PREVIEW ON LOCK SCREEN",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                            color = ResolveColors.MintAccent
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(ResolveColors.ForestDeep)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val previewQuote = trimmed.ifEmpty { DefaultLockReminders.DEFAULT_TEXT }
                            if (selectedStyle == LockReminderStyle.SOFT_BOX) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(ResolveColors.ForestSurface.copy(alpha = 0.78f))
                                        .border(
                                            1.dp,
                                            ResolveColors.MintAccent.copy(alpha = 0.25f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = previewQuote,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontStyle = FontStyle.Italic,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = ResolveColors.TextPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Text(
                                    text = previewQuote,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = ResolveColors.MintBright,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Curated high-impact presets
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "EFFECTIVE PRESETS",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                            color = ResolveColors.MintAccent
                        )
                        DefaultLockReminders.presets.forEachIndexed { idx, preset ->
                            val isSelected = trimmed == preset
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) ResolveColors.MintSoftContainer.copy(alpha = 0.65f)
                                        else ResolveColors.ForestSurface
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) ResolveColors.MintAccent else ResolveColors.ForestOutline,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { draftText = preset }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("lock_reminder_preset_$idx")
                            ) {
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                    color = if (isSelected) ResolveColors.MintBright else ResolveColors.TextPrimary
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            dismissTextInteractions(focusManager, textToolbar, keyboardController)
                            onDismiss()
                        }
                    ) {
                        Text("Cancel", color = ResolveColors.TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = canSave,
                        onClick = {
                            dismissTextInteractions(focusManager, textToolbar, keyboardController)
                            onSelectStyle(selectedStyle)
                            onSaveText(trimmed)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ResolveColors.MintAccent,
                            contentColor = ResolveColors.ForestDeep
                        ),
                        modifier = Modifier.testTag("lock_reminder_save_button")
                    ) {
                        Text("Save Reminder")
                    }
                }
            }
        }
    }
}
