package com.example.designsystem

import androidx.compose.ui.graphics.Color

object ResolveColors {
    // Rich Dark Forest Green base & layered surface hierarchy
    val ForestDeep = Color(0xFF0A1713)          // Primary app background
    val ForestSecondary = Color(0xFF10221C)     // Secondary dark forest / sheets / nav dock
    val ForestBrandDeep = Color(0xFF10251F)     // Splash & brand intro canvas matching app icon
    val ForestSurface = Color(0xFF152B23)       // Primary card surface top
    val ForestSurfaceElevated = Color(0xFF1B362C) // Active/selected tiles & elevated surfaces
    val ForestOutline = Color(0xFF28483B)       // 1.dp hairline card border
    val ForestDivider = Color(0xFF1D362D)       // Subtle section & list divider
    val ForestBorderGlow = Color(0xFF345E4E)    // Emphasized active border
    val ForestGlass = Color(0xCC10221C)         // Translucent frosted modal/bar surface

    // Dark Teal Green (#176B5B) for settings, switches & subtle active badges
    val MintAccent = Color(0xFF176B5B)
    val FocusMint = Color(0xFF176B5B)
    val MintBright = Color(0xFF176B5B)
    val SageAccent = Color(0xFF176B5B)
    val MintSoftContainer = Color(0xFF112C24)
    val MintGlow = Color(0x40176B5B)

    // Tasteful Mint Green for hero primary CTA buttons
    val PrimaryMintCta = Color(0xFF5BD1A4)
    val PrimaryMintCtaPressed = Color(0xFF47B88E)
    val OnPrimaryMintCta = Color(0xFF071A14)

    // Unique Instant Lock emerald pair matching Delayed/Schedule/App Lock card system
    val InstantEmeraldStrong = Color(0xFF83E5BE)
    val InstantEmeraldContainer = Color(0xFF123228)

    // Muted dark-mint active navigation pill (calm opacity, crisp label & icon contrast)
    val NavMintContainer = Color(0xFF173E32)
    val NavMintBorder = Color(0xFF337860)
    val NavMintContent = Color(0xFF8BE8C3)

    // Variant 1: Warm Champagne Gold (Flip Clock, schedules, milestones, version pills)
    val Champagne = Color(0xFFD4B781)
    val ChampagneStrong = Color(0xFFEBD4A7)
    val ChampagneContainer = Color(0xFF272115)

    // Variant 2: Cool Arctic Slate Teal (Delayed Lock, screen time telemetry, system health, tilt)
    val SlateTeal = Color(0xFF6EC2C8)
    val SlateTealStrong = Color(0xFFA5E3E8)
    val SlateTealContainer = Color(0xFF13292D)

    // Variant 3: Misty Cobalt Slate (App Lock shield, cloud account sync, protection badges)
    val CobaltSlate = Color(0xFF8AA8E6)
    val CobaltStrong = Color(0xFFB8CEFA)
    val CobaltContainer = Color(0xFF172236)

    // Variant 4: Graphite Slate Neutrals (secondary buttons, inactive toggles, nested rows)
    val GraphiteControl = Color(0xFF162822)
    val GraphiteBorder = Color(0xFF2B473C)

    // Primary button & compatibility aliases
    val ForestPrimary = PrimaryMintCta
    val ForestPrimaryPressed = PrimaryMintCtaPressed
    val SagePrimary = MintAccent
    val SageSecondary = SageAccent
    val SageSoft = MintBright

    // Mascot illustration tones
    val CloudHighlight = Color(0xFFF6F9F6)
    val CloudBodyTop = Color(0xFFEEF4EF)
    val CloudBodyMid = Color(0xFFD5E4D8)
    val CloudBodyShadow = Color(0xFFACC2B2)
    val CloudBlush = Color(0xFFE59D9A)

    // Neutral text hierarchy
    val TextPrimary = Color(0xFFF2F5F1)
    val TextSecondary = Color(0xFFA0ADA5)
    val TextDisabled = Color(0xFF5D6B64)

    // Semantic statuses
    val SuccessGreen = FocusMint
    val SuccessContainer = Color(0xFF112C24)
    val AmberWarn = Color(0xFFE8BA6B)
    val AmberContainer = Color(0xFF2C2214)
    val ErrorCoral = Color(0xFFEC7A7A)
    val ErrorContainer = Color(0xFF33191D)
    val InfoBlue = SlateTeal
    val InfoContainer = SlateTealContainer
}
