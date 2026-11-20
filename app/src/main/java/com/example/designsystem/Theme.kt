package com.example.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class ResolveExtendedPalette(
    val isDark: Boolean,
    val background: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val cardBorder: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentSoftContainer: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val warn: Color,
    val warnContainer: Color,
    val error: Color,
    val errorContainer: Color
)

private val DarkExtendedPalette = ResolveExtendedPalette(
    isDark = true,
    background = ResolveColors.ForestDeep,
    cardTop = ResolveColors.ForestSurfaceElevated.copy(alpha = 0.85f),
    cardBottom = ResolveColors.ForestSurface,
    cardBorder = ResolveColors.ForestOutline,
    accent = ResolveColors.MintAccent,
    accentStrong = ResolveColors.MintBright,
    accentSoftContainer = ResolveColors.MintSoftContainer,
    onAccent = ResolveColors.ForestDeep,
    textPrimary = ResolveColors.TextPrimary,
    textSecondary = ResolveColors.TextSecondary,
    textMuted = ResolveColors.TextDisabled,
    warn = ResolveColors.AmberWarn,
    warnContainer = ResolveColors.AmberContainer,
    error = ResolveColors.ErrorCoral,
    errorContainer = ResolveColors.ErrorContainer
)

val LocalResolveExtendedPalette = staticCompositionLocalOf { DarkExtendedPalette }

private val ResolveDarkColorScheme = darkColorScheme(
    primary = ResolveColors.MintAccent,
    onPrimary = ResolveColors.ForestDeep,
    primaryContainer = ResolveColors.MintSoftContainer,
    onPrimaryContainer = ResolveColors.MintAccent,
    secondary = ResolveColors.SageAccent,
    onSecondary = ResolveColors.ForestDeep,
    secondaryContainer = ResolveColors.ForestSurfaceElevated,
    onSecondaryContainer = ResolveColors.TextPrimary,
    tertiary = ResolveColors.SageAccent,
    onTertiary = ResolveColors.ForestDeep,
    background = ResolveColors.ForestDeep,
    onBackground = ResolveColors.TextPrimary,
    surface = ResolveColors.ForestSecondary,
    onSurface = ResolveColors.TextPrimary,
    surfaceVariant = ResolveColors.ForestSurface,
    onSurfaceVariant = ResolveColors.TextSecondary,
    surfaceContainerLowest = ResolveColors.ForestDeep,
    surfaceContainerLow = ResolveColors.ForestSecondary,
    surfaceContainer = ResolveColors.ForestSurface,
    surfaceContainerHigh = ResolveColors.ForestSurfaceElevated,
    outline = ResolveColors.ForestOutline,
    outlineVariant = ResolveColors.ForestSurfaceElevated,
    error = ResolveColors.ErrorCoral,
    onError = ResolveColors.ForestDeep,
    errorContainer = ResolveColors.ErrorContainer,
    onErrorContainer = ResolveColors.ErrorCoral
)

val ResolveShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ResolveTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalResolveExtendedPalette provides DarkExtendedPalette) {
        MaterialTheme(
            colorScheme = ResolveDarkColorScheme,
            typography = ResolveTypography,
            shapes = ResolveShapes,
            content = content
        )
    }
}
