# RESOLVE

Offline-first Android focus control and scheduled device restriction application.

- **Application Name:** Resolve
- **Application Package ID:** `com.resolve.focus`
- **Minimum Android Version:** Android 5.0 Lollipop (API 21)
- **Target / Compile SDK:** API 36
- **Developer:** Tahan Official
- **Runtime:** 100% Offline, Local-First (Room + DataStore + Android Keystore)

## Core Modes
1. **Instant Lock** — Enforces Android Lock Task Mode immediately for a chosen duration.
2. **Delayed Lock** — Provides an unrestricted Free Use Window first, then automatically transitions to Lock Task Mode for an independent Lock Duration.
3. **Schedule Lock** — Automatically triggers Lock Task Mode at configured One-Time, Daily, or Custom Weekly times.

## Key Capabilities
- **Dual-Tier Enforcement:**
  - **Tier 1 — Device Owner Lock Task:** Uses `DevicePolicyManager` (`setLockTaskPackages`, with `setLockTaskFeatures` on Android 9+) when provisioned via ADB.
  - **Tier 2 — Accessibility Guard:** Uses `ResolveAccessibilityService` + `ForegroundEnforcementGuard` (`TYPE_APPLICATION_OVERLAY` / `TYPE_SYSTEM_ALERT` + `UsageStatsManager` fallback) + `ResolveSessionService` so consumer/personal APKs enforce lock mode cleanly on Android 5.0+ without Device Owner provisioning.
- Independent Hours (`0–23`) and Minutes (`0–59`) drag-wheel + direct numeric entry duration selector.
- System Dialer, InCallUI, IME keyboard, and emergency call safelisting (`TelecomManager` + `Intent.ACTION_DIAL`) without pausing or extending lock timers.
- Salted PBKDF2-HMAC-SHA256 (with PBKDF2-HMAC-SHA1 fallback on Android 5–7) + Android Keystore AES-256-GCM (Android 6+) 16-digit (and optional 32-digit) recovery code verification with attempt throttling.
- Core library desugaring (`java.time.*`) and API-level system guards supporting Android 5.0 through Android 16.
- Animated 2D Baby Cloud mascot bundled locally in Jetpack Compose `Canvas`.

## Building
```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

## Permissions & Setup
- **Standard APK (Accessibility Guard):** Grant **Accessibility Service**, **Display Over Other Apps**, **Usage Access** (recommended fallback), **Alarms & Reminders**, **Notifications**, and **Unrestricted Battery** directly inside **Settings -> Access & Permissions**.
- **Optional Device Owner Provisioning:** On a device with no accounts temporarily present:
```bash
adb shell dpm set-device-owner com.resolve.focus/com.example.devicepolicy.ResolveDeviceAdminReceiver
```
