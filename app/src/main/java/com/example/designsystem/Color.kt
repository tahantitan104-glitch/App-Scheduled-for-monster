package com.example.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.domain.model.ResolveThemeId

object ResolveColors {
    val NightObsidian = Color(0xFF0A0D0C)
    val CharcoalCard = Color(0xFF121715)
    val ElevatedSlate = Color(0xFF1A221F)
    val MistBorder = Color(0xFF28332E)

    val SageMint = Color(0xFF81E0B6)
    val DeepJade = Color(0xFF0E291F)
    val EmeraldStrong = Color(0xFF62D4A3)
    val EmeraldContainer = Color(0xFF153126)

    val IvoryText = Color(0xFFF3F6F4)
    val CoolGrey = Color(0xFFA7B5AD)
    val DimSlate = Color(0xFF6C7A73)

    val SoftAmber = Color(0xFFF0BE75)
    val CrimsonRose = Color(0xFFEA7D80)
    val TealSuccess = Color(0xFF6BD4A2)
    val SoftSlateTeal = Color(0xFF7BC4D4)
    val SlateTealStrong = Color(0xFF93D6E4)
    val SlateTealContainer = Color(0xFF142B30)
    val WarmChampagne = Color(0xFFE7C78D)
    val ChampagneStrong = Color(0xFFF2D7A5)
    val ChampagneContainer = Color(0xFF2D2518)
    val CoolCobalt = Color(0xFF8FB4FF)
    val CobaltStrong = Color(0xFFABC7FF)
    val CobaltContainer = Color(0xFF19263D)

    val EmeraldTop = Color(0xFF193C2D)
    val EmeraldBottom = Color(0xFF11291F)
    val EmeraldHighlight = Color(0xFF295642)

    // Compatibility tokens used by mascot artwork, startup, and flip clock overlays
    val ForestSurface = CharcoalCard
    val ForestSecondary = ElevatedSlate
    val ForestBrandDeep = DeepJade
    val MintAccent = SageMint
    val SageAccent = EmeraldStrong
    val AmberWarn = SoftAmber
    val AmberContainer = Color(0xFF332614)
    val ErrorCoral = CrimsonRose
    val ErrorContainer = Color(0xFF361719)
    val CloudHighlight = Color(0xFFFFFFFF)
    val CloudBodyTop = Color(0xFFF3F8F6)
    val CloudBodyMid = Color(0xFFE3ECE8)
    val CloudBodyShadow = Color(0xFFC9D8D2)
    val CloudBlush = Color(0xFFF4A8B2)
}

@Immutable
data class ResolveExtendedPalette(
    val themeId: ResolveThemeId = ResolveThemeId.DEEP_FOREST_GREEN,
    val isDark: Boolean = true,
    val background: Color,
    val surfaceSecondary: Color,
    val surfaceElevated: Color,
    val surfaceMuted: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val cardBorder: Color,
    val cardBorderFocused: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSecondary: Color,
    val accentStrong: Color,
    val accentContainer: Color,
    val primaryButton: Color,
    val primaryButtonPressed: Color,
    val onPrimaryButton: Color,
    val controlSurface: Color,
    val controlSurfaceHover: Color,
    val controlBorder: Color,
    val navPillContainer: Color,
    val navPillBorder: Color,
    val navPillContent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val divider: Color,
    val warn: Color,
    val warnContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val success: Color,
    val successContainer: Color,
    val emeraldStrong: Color,
    val emeraldContainer: Color,
    val slateTeal: Color,
    val slateTealStrong: Color,
    val slateTealContainer: Color,
    val champagne: Color,
    val champagneStrong: Color,
    val champagneContainer: Color,
    val cobalt: Color,
    val cobaltStrong: Color,
    val cobaltContainer: Color
) {
    val accentSoftContainer: Color get() = accentContainer
    val cobaltSlate: Color get() = cobalt
    val danger: Color get() = error
    val info: Color get() = cobaltStrong
    val infoContainer: Color get() = cobaltContainer
}

val EmeraldForestPalette = ResolveExtendedPalette(
    themeId = ResolveThemeId.EMERALD_FOREST,
    isDark = true,
    background = ResolveColors.NightObsidian,
    surfaceSecondary = ResolveColors.CharcoalCard,
    surfaceElevated = ResolveColors.ElevatedSlate,
    surfaceMuted = Color(0xFF101513),
    cardTop = Color(0xFF161D1A),
    cardBottom = Color(0xFF101513),
    cardBorder = Color(0xFF28332E),
    cardBorderFocused = Color(0xFF81E0B6),
    accent = ResolveColors.SageMint,
    onAccent = ResolveColors.DeepJade,
    accentSecondary = Color(0xFF66C79D),
    accentStrong = Color(0xFF9DF0CA),
    accentContainer = Color(0xFF173629),
    primaryButton = ResolveColors.SageMint,
    primaryButtonPressed = Color(0xFF62C89C),
    onPrimaryButton = ResolveColors.DeepJade,
    controlSurface = Color(0xFF18201D),
    controlSurfaceHover = Color(0xFF212D28),
    controlBorder = Color(0xFF2D3C36),
    navPillContainer = Color(0xFF1B352A),
    navPillBorder = Color(0xFF396F58),
    navPillContent = Color(0xFF95F0C7),
    textPrimary = ResolveColors.IvoryText,
    textSecondary = ResolveColors.CoolGrey,
    textMuted = ResolveColors.DimSlate,
    divider = Color(0xFF232D28),
    warn = ResolveColors.SoftAmber,
    warnContainer = Color(0xFF332614),
    error = ResolveColors.CrimsonRose,
    errorContainer = Color(0xFF361719),
    success = ResolveColors.TealSuccess,
    successContainer = Color(0xFF143125),
    emeraldStrong = ResolveColors.EmeraldStrong,
    emeraldContainer = ResolveColors.EmeraldContainer,
    slateTeal = ResolveColors.SoftSlateTeal,
    slateTealStrong = ResolveColors.SlateTealStrong,
    slateTealContainer = ResolveColors.SlateTealContainer,
    champagne = ResolveColors.WarmChampagne,
    champagneStrong = ResolveColors.ChampagneStrong,
    champagneContainer = ResolveColors.ChampagneContainer,
    cobalt = ResolveColors.CoolCobalt,
    cobaltStrong = ResolveColors.CobaltStrong,
    cobaltContainer = ResolveColors.CobaltContainer
)

val MidnightObsidianPalette = EmeraldForestPalette.copy(
    themeId = ResolveThemeId.MIDNIGHT_OBSIDIAN,
    isDark = true,
    background = Color(0xFF000000),
    surfaceSecondary = Color(0xFF090B0A),
    surfaceElevated = Color(0xFF121614),
    surfaceMuted = Color(0xFF060807),
    cardTop = Color(0xFF0E1210),
    cardBottom = Color(0xFF080A09),
    cardBorder = Color(0xFF202824),
    cardBorderFocused = Color(0xFF7BE8B9),
    accent = Color(0xFF7BE8B9),
    accentStrong = Color(0xFFA7F7D5),
    primaryButton = Color(0xFF7BE8B9),
    controlSurface = Color(0xFF111513),
    controlSurfaceHover = Color(0xFF19201D),
    controlBorder = Color(0xFF26302B),
    navPillContainer = Color(0xFF142921),
    divider = Color(0xFF1A201D)
)

val DeepForestGreenPalette = ResolveExtendedPalette(
    themeId = ResolveThemeId.DEEP_FOREST_GREEN,
    isDark = true,
    background = Color(0xFF0A1713),
    surfaceSecondary = Color(0xFF10221C),
    surfaceElevated = Color(0xFF152B23),
    surfaceMuted = Color(0xFF0E1E19),
    cardTop = Color(0xFF162F26),
    cardBottom = Color(0xFF10221C),
    cardBorder = Color(0xFF24473A),
    cardBorderFocused = ResolveColors.SageMint,
    accent = ResolveColors.SageMint,
    onAccent = Color(0xFF091D16),
    accentSecondary = Color(0xFF56C99B),
    accentStrong = Color(0xFF9DF0CA),
    accentContainer = Color(0xFF174436),
    primaryButton = ResolveColors.SageMint,
    primaryButtonPressed = Color(0xFF5CCB9C),
    onPrimaryButton = Color(0xFF091D16),
    controlSurface = Color(0xFF132921),
    controlSurfaceHover = Color(0xFF1B382E),
    controlBorder = Color(0xFF284D3F),
    navPillContainer = Color(0xFF194235),
    navPillBorder = Color(0xFF397C63),
    navPillContent = Color(0xFF95F0C7),
    textPrimary = Color(0xFFF2F5F1),
    textSecondary = Color(0xFFA9BEB4),
    textMuted = Color(0xFF6F8A7E),
    divider = Color(0xFF1E3C31),
    warn = ResolveColors.SoftAmber,
    warnContainer = Color(0xFF332614),
    error = ResolveColors.CrimsonRose,
    errorContainer = Color(0xFF361719),
    success = ResolveColors.TealSuccess,
    successContainer = Color(0xFF14382A),
    emeraldStrong = ResolveColors.EmeraldStrong,
    emeraldContainer = Color(0xFF153B2D),
    slateTeal = Color(0xFF6EC2C8),
    slateTealStrong = Color(0xFF8BD8DE),
    slateTealContainer = Color(0xFF132F34),
    champagne = Color(0xFFD4B781),
    champagneStrong = Color(0xFFEBD2A2),
    champagneContainer = Color(0xFF2C2618),
    cobalt = ResolveColors.CoolCobalt,
    cobaltStrong = ResolveColors.CobaltStrong,
    cobaltContainer = Color(0xFF18283E)
)

val CobaltStudioPalette = ResolveExtendedPalette(
    themeId = ResolveThemeId.COBALT_STUDIO,
    isDark = true,
    background = Color(0xFF0A0E17),
    surfaceSecondary = Color(0xFF111827),
    surfaceElevated = Color(0xFF192235),
    surfaceMuted = Color(0xFF0E1420),
    cardTop = Color(0xFF151E30),
    cardBottom = Color(0xFF101725),
    cardBorder = Color(0xFF26334D),
    cardBorderFocused = Color(0xFF7CA7FF),
    accent = Color(0xFF7CA7FF),
    onAccent = Color(0xFF081733),
    accentSecondary = Color(0xFF6594F5),
    accentStrong = Color(0xFFA9C5FF),
    accentContainer = Color(0xFF182A4D),
    primaryButton = Color(0xFF7CA7FF),
    primaryButtonPressed = Color(0xFF5D8CEB),
    onPrimaryButton = Color(0xFF081733),
    controlSurface = Color(0xFF162033),
    controlSurfaceHover = Color(0xFF1E2B44),
    controlBorder = Color(0xFF2D3E5E),
    navPillContainer = Color(0xFF1B2C4D),
    navPillBorder = Color(0xFF4267B2),
    navPillContent = Color(0xFFB4CDFF),
    textPrimary = Color(0xFFF1F5FC),
    textSecondary = Color(0xFFA5B3CC),
    textMuted = Color(0xFF6B7B99),
    divider = Color(0xFF212D44),
    warn = Color(0xFFF0BE75),
    warnContainer = Color(0xFF332614),
    error = Color(0xFFEA7D80),
    errorContainer = Color(0xFF361719),
    success = Color(0xFF6BD4A2),
    successContainer = Color(0xFF143125),
    emeraldStrong = Color(0xFF62D4A3),
    emeraldContainer = Color(0xFF153126),
    slateTeal = Color(0xFF7BC4D4),
    slateTealStrong = Color(0xFF93D6E4),
    slateTealContainer = Color(0xFF142B30),
    champagne = Color(0xFFE7C78D),
    champagneStrong = Color(0xFFF2D7A5),
    champagneContainer = Color(0xFF2D2518),
    cobalt = Color(0xFF8FB4FF),
    cobaltStrong = Color(0xFFABC7FF),
    cobaltContainer = Color(0xFF19263D)
)

fun paletteForTheme(themeId: ResolveThemeId): ResolveExtendedPalette = when (themeId) {
    ResolveThemeId.DEEP_FOREST_GREEN -> DeepForestGreenPalette
    ResolveThemeId.EMERALD_FOREST -> EmeraldForestPalette
    ResolveThemeId.MIDNIGHT_OBSIDIAN -> MidnightObsidianPalette
    ResolveThemeId.COBALT_STUDIO -> CobaltStudioPalette
}

// Keep DarkResolveExtendedPalette as primary alias so existing references stay intact
val DarkResolveExtendedPalette = DeepForestGreenPalette
