package com.example.flipclock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AmbientSoundType
import com.example.domain.model.FlipClockSettings
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val STUDIO_SLEEP_OPTIONS = listOf(0, 15, 30, 45, 60, 90, 120)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FlipWhiteNoiseStudioSheet(
    settings: FlipClockSettings,
    onDismiss: () -> Unit,
    onSelectSound: (AmbientSoundType) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSetPlayInBackground: (Boolean) -> Unit,
    onSetPlayDuringFocusLock: (Boolean) -> Unit,
    onSetSleepTimerMinutes: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isPlaying = settings.ambientSoundType != AmbientSoundType.OFF

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = FlipClockPalette.PanelOutline)
        },
        containerColor = FlipClockPalette.PanelDark,
        contentColor = FlipClockPalette.AccentSoft
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("white_noise_studio_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "White Noise Studio",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = FlipClockPalette.AccentSoft
                    )
                    Text(
                        text = "Seamless offline 44.1 kHz stereo soundscapes",
                        style = MaterialTheme.typography.bodySmall,
                        color = FlipClockPalette.SubTextGrey
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("white_noise_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close White Noise Studio",
                        tint = FlipClockPalette.SubTextGrey
                    )
                }
            }

            // Now Playing Hero Card with Acoustic Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (isPlaying) {
                                listOf(FlipClockPalette.PanelActive, FlipClockPalette.PanelElevated)
                            } else {
                                listOf(FlipClockPalette.PanelElevated, FlipClockPalette.PanelDark)
                            }
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = if (isPlaying) FlipClockPalette.PrimaryCta.copy(alpha = 0.55f) else FlipClockPalette.PanelOutline,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    StudioVisualizerBars(
                        isPlaying = isPlaying,
                        accentColor = if (isPlaying) FlipClockPalette.PrimaryCta else FlipClockPalette.SubTextGrey
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isPlaying) "NOW PLAYING · ${settings.ambientSoundType.categoryLabel.uppercase()}" else "STUDIO READY",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isPlaying) FlipClockPalette.SageLabel else FlipClockPalette.SubTextGrey
                        )
                        Text(
                            text = if (isPlaying) settings.ambientSoundType.label else "Choose a soothing soundscape",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = FlipClockPalette.AccentSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isPlaying) settings.ambientSoundType.subtitle else "Tap any card below to start buffer-free playback",
                            style = MaterialTheme.typography.bodySmall,
                            color = FlipClockPalette.SubTextGrey,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        onClick = {
                            if (isPlaying) {
                                onSelectSound(AmbientSoundType.OFF)
                            } else {
                                onSelectSound(AmbientSoundType.WHITE_NOISE)
                            }
                        },
                        shape = CircleShape,
                        color = if (isPlaying) FlipClockPalette.PanelDark else FlipClockPalette.PrimaryCta,
                        border = BorderStroke(
                            1.dp,
                            if (isPlaying) FlipClockPalette.PanelOutline else FlipClockPalette.PrimaryCta
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("white_noise_hero_toggle")
                    ) {
                        Text(
                            text = if (isPlaying) "Stop" else "Play",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isPlaying) FlipClockPalette.AccentSoft else FlipClockPalette.PrimaryCtaText,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Master Volume Slider
            AnimatedVisibility(visible = isPlaying) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(FlipClockPalette.PanelElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Master Soundscape Volume",
                            style = MaterialTheme.typography.titleSmall,
                            color = FlipClockPalette.AccentSoft
                        )
                        Text(
                            text = "${(settings.ambientVolume * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = FlipClockPalette.SageLabel
                        )
                    }
                    Slider(
                        value = settings.ambientVolume.coerceIn(0.05f, 1f),
                        onValueChange = onVolumeChange,
                        valueRange = 0.05f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = FlipClockPalette.SageLabel,
                            activeTrackColor = FlipClockPalette.PrimaryCta,
                            inactiveTrackColor = FlipClockPalette.PanelDark
                        ),
                        modifier = Modifier.testTag("white_noise_volume_slider")
                    )
                }
            }

            // Soundscape Catalog Grid (2 columns)
            Text(
                text = "SOUNDSCAPES",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = FlipClockPalette.SageLabel,
                modifier = Modifier.padding(top = 2.dp)
            )

            val pairedSounds = AmbientSoundType.studioDisplayOrder.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                pairedSounds.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { sound ->
                            val selected = settings.ambientSoundType == sound
                            SoundscapeTileCard(
                                sound = sound,
                                selected = selected,
                                onClick = {
                                    if (selected) {
                                        onSelectSound(AmbientSoundType.OFF)
                                    } else {
                                        onSelectSound(sound)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Playback Behavior & Sleep Fade
            Text(
                text = "PLAYBACK & TIMER",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = FlipClockPalette.SageLabel,
                modifier = Modifier.padding(top = 4.dp)
            )

            StudioSwitchRow(
                title = "Continue Playing in Background",
                subtitle = "Keep soothing white noise running even after exiting Flip Clock or turning screen off",
                checked = settings.ambientPlayInBackground,
                onCheckedChange = onSetPlayInBackground,
                testTag = "white_noise_bg_playback_switch"
            )

            StudioSwitchRow(
                title = "Play During Focus Lock",
                subtitle = "Automatically continue playing your selected soundscape on the Lock Screen",
                checked = settings.playAmbientSoundDuringFocusLock,
                onCheckedChange = onSetPlayDuringFocusLock,
                testTag = "white_noise_focus_lock_switch"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FlipClockPalette.PanelElevated)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Sleep Fade-Out Timer",
                    style = MaterialTheme.typography.titleSmall,
                    color = FlipClockPalette.AccentSoft
                )
                Text(
                    text = if (settings.sleepTimerMinutes > 0) {
                        "Smoothly fades out and stops after ${settings.sleepTimerMinutes} min"
                    } else {
                        "Continuous — plays seamlessly as long as you want"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = FlipClockPalette.SubTextGrey
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    STUDIO_SLEEP_OPTIONS.forEach { mins ->
                        val selected = settings.sleepTimerMinutes == mins
                        FilterChip(
                            selected = selected,
                            onClick = { onSetSleepTimerMinutes(mins) },
                            label = { Text(if (mins == 0) "Continuous" else "${mins}m") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FlipClockPalette.PrimaryCta,
                                selectedLabelColor = FlipClockPalette.PrimaryCtaText,
                                containerColor = FlipClockPalette.PanelDark,
                                labelColor = FlipClockPalette.SubTextGrey
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = FlipClockPalette.PanelOutline,
                                selectedBorderColor = FlipClockPalette.SageLabel.copy(alpha = 0.75f)
                            ),
                            modifier = Modifier.testTag("white_noise_sleep_${mins}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundscapeTileCard(
    sound: AmbientSoundType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) FlipClockPalette.PanelActive else FlipClockPalette.PanelElevated,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) FlipClockPalette.PrimaryCta else FlipClockPalette.PanelOutline
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("white_noise_card_${sound.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SoundscapeGlyph(
                    sound = sound,
                    active = selected
                )
                Text(
                    text = if (selected) "PLAYING" else sound.categoryLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) FlipClockPalette.SageLabel else FlipClockPalette.SubTextGrey,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) FlipClockPalette.PrimaryCta.copy(alpha = 0.16f)
                            else FlipClockPalette.PanelDark.copy(alpha = 0.7f)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Text(
                text = sound.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = FlipClockPalette.AccentSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sound.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = FlipClockPalette.SubTextGrey,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2
            )
        }
    }
}

@Composable
private fun StudioSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FlipClockPalette.PanelElevated)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = FlipClockPalette.AccentSoft
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = FlipClockPalette.SubTextGrey
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = FlipClockPalette.PrimaryCtaText,
                checkedTrackColor = FlipClockPalette.PrimaryCta,
                uncheckedThumbColor = FlipClockPalette.SubTextGrey,
                uncheckedTrackColor = FlipClockPalette.PanelDark
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun StudioVisualizerBars(
    isPlaying: Boolean,
    accentColor: Color
) {
    val transition = rememberInfiniteTransition(label = "studio_viz")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "studio_viz_phase"
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(FlipClockPalette.PanelDark)
            .border(1.dp, FlipClockPalette.PanelOutline, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(26.dp)) {
            val barCount = 5
            val gap = size.width / (barCount * 2f)
            val barW = gap
            for (i in 0 until barCount) {
                val frac = if (isPlaying) {
                    (0.35f + 0.55f * ((sin(phase + i * 1.15f) + 1f) * 0.5f)).coerceIn(0.22f, 0.95f)
                } else {
                    0.22f
                }
                val barH = size.height * frac
                val x = i * (barW + gap) + gap * 0.5f
                val y = (size.height - barH) * 0.5f
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(x, y),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barW * 0.5f, barW * 0.5f)
                )
            }
        }
    }
}

@Composable
private fun SoundscapeGlyph(
    sound: AmbientSoundType,
    active: Boolean
) {
    val tint = if (active) FlipClockPalette.PrimaryCta else FlipClockPalette.SageLabel.copy(alpha = 0.85f)
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            when (sound) {
                AmbientSoundType.WHITE_NOISE, AmbientSoundType.BROWN_NOISE -> {
                    val heights = if (sound == AmbientSoundType.WHITE_NOISE) {
                        listOf(0.42f, 0.85f, 0.58f, 0.92f, 0.48f)
                    } else {
                        listOf(0.65f, 0.92f, 0.78f, 0.62f, 0.45f)
                    }
                    val step = w / heights.size
                    heights.forEachIndexed { idx, frac ->
                        val x = idx * step + step * 0.5f
                        val half = (h * frac) * 0.45f
                        drawLine(tint, Offset(x, h * 0.5f - half), Offset(x, h * 0.5f + half), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                AmbientSoundType.RAIN, AmbientSoundType.THUNDERSTORM -> {
                    drawLine(tint, Offset(w * 0.25f, h * 0.2f), Offset(w * 0.15f, h * 0.62f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.55f, h * 0.15f), Offset(w * 0.45f, h * 0.72f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.82f, h * 0.25f), Offset(w * 0.72f, h * 0.68f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    if (sound == AmbientSoundType.THUNDERSTORM) {
                        val bolt = Path().apply {
                            moveTo(w * 0.6f, h * 0.42f)
                            lineTo(w * 0.42f, h * 0.68f)
                            lineTo(w * 0.58f, h * 0.68f)
                            lineTo(w * 0.45f, h * 0.95f)
                        }
                        drawPath(bolt, color = tint, style = stroke)
                    }
                }
                AmbientSoundType.BONFIRE -> {
                    val flame = Path().apply {
                        moveTo(w * 0.5f, h * 0.08f)
                        cubicTo(w * 0.86f, h * 0.42f, w * 0.82f, h * 0.86f, w * 0.5f, h * 0.90f)
                        cubicTo(w * 0.18f, h * 0.86f, w * 0.14f, h * 0.42f, w * 0.5f, h * 0.08f)
                    }
                    drawPath(flame, color = tint, style = stroke)
                }
                AmbientSoundType.RIVER_WAVES, AmbientSoundType.OCEAN_WAVES -> {
                    for (row in 0..1) {
                        val y = h * (if (row == 0) 0.36f else 0.68f)
                        val wave = Path().apply {
                            moveTo(w * 0.08f, y)
                            quadraticTo(w * 0.30f, y - h * 0.18f, w * 0.52f, y)
                            quadraticTo(w * 0.74f, y + h * 0.18f, w * 0.92f, y)
                        }
                        drawPath(wave, color = tint, style = stroke)
                    }
                }
                AmbientSoundType.FOREST_WIND -> {
                    val leaf = Path().apply {
                        moveTo(w * 0.18f, h * 0.82f)
                        quadraticTo(w * 0.22f, h * 0.20f, w * 0.85f, h * 0.15f)
                        quadraticTo(w * 0.82f, h * 0.78f, w * 0.18f, h * 0.82f)
                    }
                    drawPath(leaf, color = tint, style = stroke)
                }
                AmbientSoundType.WARM_HUM, AmbientSoundType.FOCUS_PAD, AmbientSoundType.OFF -> {
                    drawCircle(tint, radius = w * 0.40f, style = stroke)
                    drawCircle(tint, radius = w * 0.18f, style = stroke)
                }
            }
        }
    }
}
