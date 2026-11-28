package com.example.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.ResolveThemeId

val LocalResolveExtendedPalette = staticCompositionLocalOf { DeepForestGreenPalette }
val LocalResolveBottomDockClearance = staticCompositionLocalOf { 0.dp }
val LocalShowSetupTips = staticCompositionLocalOf { false }

private fun buildMaterialColorScheme(palette: ResolveExtendedPalette) =
    if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.accentContainer,
            onPrimaryContainer = palette.accentStrong,
            secondary = palette.slateTeal,
            onSecondary = palette.onAccent,
            secondaryContainer = palette.slateTealContainer,
            onSecondaryContainer = palette.slateTealStrong,
            tertiary = palette.champagne,
            onTertiary = palette.onAccent,
            tertiaryContainer = palette.champagneContainer,
            onTertiaryContainer = palette.champagneStrong,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surfaceSecondary,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.cardBorder,
            outlineVariant = palette.divider,
            error = palette.error,
            onError = palette.onAccent,
            errorContainer = palette.errorContainer,
            onErrorContainer = palette.error
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.accentContainer,
            onPrimaryContainer = palette.accentStrong,
            secondary = palette.slateTeal,
            onSecondary = palette.onAccent,
            secondaryContainer = palette.slateTealContainer,
            onSecondaryContainer = palette.slateTealStrong,
            tertiary = palette.champagne,
            onTertiary = palette.onAccent,
            tertiaryContainer = palette.champagneContainer,
            onTertiaryContainer = palette.champagneStrong,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surfaceSecondary,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.cardBorder,
            outlineVariant = palette.divider,
            error = palette.error,
            onError = palette.onAccent,
            errorContainer = palette.errorContainer,
            onErrorContainer = palette.error
        )
    }

private val ResolveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ResolveTheme(
    themeId: ResolveThemeId = ResolveThemeId.DEEP_FOREST_GREEN,
    showSetupTips: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = paletteForTheme(themeId)
    CompositionLocalProvider(
        LocalResolveExtendedPalette provides palette,
        LocalShowSetupTips provides showSetupTips
    ) {
        MaterialTheme(
            colorScheme = buildMaterialColorScheme(palette),
            typography = ResolveTypography,
            shapes = ResolveShapes,
            content = content
        )
    }
}
