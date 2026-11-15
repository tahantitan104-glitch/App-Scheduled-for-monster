package com.example.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

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
fun ResolveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ResolveDarkColorScheme,
        typography = ResolveTypography,
        shapes = ResolveShapes,
        content = content
    )
}
