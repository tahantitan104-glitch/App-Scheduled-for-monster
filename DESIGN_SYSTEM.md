# RESOLVE — Design System

## Color Tokens (`designsystem/Color.kt`)
- **Primary Background (Deep Forest Green):** `#07110E`
- **Secondary Dark Green:** `#101C17`
- **Surface:** `#17251E`
- **Elevated Surface:** `#1E3027`
- **Outline / Glow Border:** `#283D32` / `#395B4A`
- **Forest Glass Surface:** `#CC12201A`
- **Primary Accent (Muted Mint / Bright Mint):** `#9BE3B4` / `#BEF5D0`
- **Secondary Accent (Soft Sage):** `#6FAE88`
- **Primary Text:** `#F2F5F1`
- **Secondary Text:** `#A0ADA5`
- **Disabled Text:** `#59665F`

## Typography (`designsystem/Type.kt`)
- **UI & Headings:** `Plus Jakarta Sans` (bundled locally in `res/font/plus_jakarta_sans.ttf`)
- **Countdown & Numeric Readouts:** `JetBrains Mono` (bundled locally in `res/font/jetbrains_mono.ttf`, `tnum` tabular figures)

## Signature Lock Screen & Baby Cloud Mascot
- `BabyCloudMascot.kt` renders a volumetric 2D/soft-3D cloud character in Jetpack Compose `Canvas` featuring:
  - Multi-layered radial mint aura, serenity ring, and orbiting starlight sparkles
  - Dynamic ground shadow responding inversely to levitation height
  - 5-lobe cloud silhouette with top specular rim arcs, under-belly bounce shading, glossy dual-catchlight eyes, double-blink keyframes, soft blush cheeks, and gentle tilt/breathing
- `SignatureLockScreen` (`LockScreenActivity.kt`) pairs Baby Cloud with a live sweeping progress halo, a glass countdown capsule (`HH:MM:SS`), staggered entrance animations, and a frosted floating bottom dock (Allowed Apps sheet, Phone Dialer action, and discreet Recovery Code entry).
- Tactile press-scale springs (`ResolveUiEffects.kt`), haptic feedback on wheel snaps and primary CTAs, and smooth `NavHost` slide/fade transitions (`ResolveAppShell.kt`) unify all screens.
