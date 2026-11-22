package com.example.designsystem

import androidx.compose.ui.graphics.Color

object ResolveColors {
    // Deep Dark Forest Green base & layered surface hierarchy
    val ForestDeep = Color(0xFF040B08)          // Primary app background
    val ForestSecondary = Color(0xFF09120E)     // Secondary dark forest / sheets / nav dock
    val ForestBrandDeep = Color(0xFF06130F)     // Splash & brand intro canvas
    val ForestSurface = Color(0xFF0E1A15)       // Primary card surface top
    val ForestSurfaceElevated = Color(0xFF14241D) // Active/selected tiles & elevated surfaces
    val ForestOutline = Color(0xFF1E3027)       // 1.dp hairline card border
    val ForestDivider = Color(0xFF15241D)       // Subtle section & list divider
    val ForestBorderGlow = Color(0xFF294437)    // Emphasized active border
    val ForestGlass = Color(0xCC09120E)         // Translucent frosted modal/bar surface

    // Rich Dark Teal Green accents (replacing Mint Green across focus & interactive actions)
    val MintAccent = Color(0xFF176B5B)          // Primary CTA & active highlight (#176B5B)
    val FocusMint = Color(0xFF176B5B)           // Success & active status highlight (#176B5B)
    val MintBright = Color(0xFF2B9C86)          // Legible teal highlight for dark-surface text/icons
    val SageAccent = Color(0xFF176B5B)          // Secondary teal accent (#176B5B)
    val MintSoftContainer = Color(0xFF0B241E)   // Soft dark-teal container
    val MintGlow = Color(0x40176B5B)

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
    val GraphiteControl = Color(0xFF121C19)
    val GraphiteBorder = Color(0xFF23332F)

    // Primary button & compatibility aliases
    val ForestPrimary = MintAccent
    val ForestPrimaryPressed = Color(0xFF125548)
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
    val SuccessContainer = Color(0xFF0B241E)
    val AmberWarn = Color(0xFFE8BA6B)
    val AmberContainer = Color(0xFF2C2214)
    val ErrorCoral = Color(0xFFEC7A7A)
    val ErrorContainer = Color(0xFF33191D)
    val InfoBlue = SlateTeal
    val InfoContainer = SlateTealContainer
}
