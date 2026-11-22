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
    val errorContainer: Color,
    val surfaceSecondary: Color = ResolveColors.ForestSecondary,
    val surfaceElevated: Color = ResolveColors.ForestSurfaceElevated,
    val divider: Color = ResolveColors.ForestDivider,
    val primaryButton: Color = ResolveColors.MintAccent,
    val primaryButtonPressed: Color = ResolveColors.ForestPrimaryPressed,
    val onPrimaryButton: Color = ResolveColors.TextPrimary,
    val accentSecondary: Color = ResolveColors.SageAccent,
    val success: Color = ResolveColors.SuccessGreen,
    val successContainer: Color = ResolveColors.SuccessContainer,
    val info: Color = ResolveColors.InfoBlue,
    val infoContainer: Color = ResolveColors.InfoContainer,
    val champagne: Color = ResolveColors.Champagne,
    val champagneStrong: Color = ResolveColors.ChampagneStrong,
    val champagneContainer: Color = ResolveColors.ChampagneContainer,
    val slateTeal: Color = ResolveColors.SlateTeal,
    val slateTealStrong: Color = ResolveColors.SlateTealStrong,
    val slateTealContainer: Color = ResolveColors.SlateTealContainer,
    val cobaltSlate: Color = ResolveColors.CobaltSlate,
    val cobaltStrong: Color = ResolveColors.CobaltStrong,
    val cobaltContainer: Color = ResolveColors.CobaltContainer,
    val controlSurface: Color = ResolveColors.GraphiteControl,
    val controlBorder: Color = ResolveColors.GraphiteBorder
)

private val DarkExtendedPalette = ResolveExtendedPalette(
    isDark = true,
    background = ResolveColors.ForestDeep,
    cardTop = ResolveColors.ForestSurface,
    cardBottom = ResolveColors.ForestSecondary,
    cardBorder = ResolveColors.ForestOutline,
    accent = ResolveColors.MintAccent,
    accentStrong = ResolveColors.MintBright,
    accentSoftContainer = ResolveColors.MintSoftContainer,
    onAccent = ResolveColors.TextPrimary,
    textPrimary = ResolveColors.TextPrimary,
    textSecondary = ResolveColors.TextSecondary,
    textMuted = ResolveColors.TextDisabled,
    warn = ResolveColors.AmberWarn,
    warnContainer = ResolveColors.AmberContainer,
    error = ResolveColors.ErrorCoral,
    errorContainer = ResolveColors.ErrorContainer,
    surfaceSecondary = ResolveColors.ForestSecondary,
    surfaceElevated = ResolveColors.ForestSurfaceElevated,
    divider = ResolveColors.ForestDivider,
    primaryButton = ResolveColors.MintAccent,
    primaryButtonPressed = ResolveColors.ForestPrimaryPressed,
    onPrimaryButton = ResolveColors.TextPrimary,
    accentSecondary = ResolveColors.SageAccent,
    success = ResolveColors.SuccessGreen,
    successContainer = ResolveColors.SuccessContainer,
    info = ResolveColors.InfoBlue,
    infoContainer = ResolveColors.InfoContainer,
    champagne = ResolveColors.Champagne,
    champagneStrong = ResolveColors.ChampagneStrong,
    champagneContainer = ResolveColors.ChampagneContainer,
    slateTeal = ResolveColors.SlateTeal,
    slateTealStrong = ResolveColors.SlateTealStrong,
    slateTealContainer = ResolveColors.SlateTealContainer,
    cobaltSlate = ResolveColors.CobaltSlate,
    cobaltStrong = ResolveColors.CobaltStrong,
    cobaltContainer = ResolveColors.CobaltContainer,
    controlSurface = ResolveColors.GraphiteControl,
    controlBorder = ResolveColors.GraphiteBorder
)

val LocalResolveExtendedPalette = staticCompositionLocalOf { DarkExtendedPalette }

private val ResolveDarkColorScheme = darkColorScheme(
    primary = ResolveColors.MintAccent,
    onPrimary = ResolveColors.TextPrimary,
    primaryContainer = ResolveColors.MintSoftContainer,
    onPrimaryContainer = ResolveColors.TextPrimary,
    secondary = ResolveColors.FocusMint,
    onSecondary = ResolveColors.TextPrimary,
    secondaryContainer = ResolveColors.ForestSurfaceElevated,
    onSecondaryContainer = ResolveColors.TextPrimary,
    tertiary = ResolveColors.SageAccent,
    onTertiary = ResolveColors.TextPrimary,
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
    outlineVariant = ResolveColors.ForestDivider,
    error = ResolveColors.ErrorCoral,
    onError = ResolveColors.ForestDeep,
    errorContainer = ResolveColors.ErrorContainer,
    onErrorContainer = ResolveColors.ErrorCoral
)

val ResolveShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
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
