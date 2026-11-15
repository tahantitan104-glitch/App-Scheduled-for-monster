# RESOLVE — Test Report

## Executed Automated Tests (`gradle :app:testDebugUnitTest`)

### 1. Unit Tests (Pure JVM)
- **`com.example.DurationSelectionTest`** — **4 passed, 0 failed, 0 skipped**
  - Selecting hours then minutes (`00:00` -> `03:00` -> `03:40`) without resetting minutes.
  - Reverse selection order (`00:00` -> `00:40` -> `03:40`).
  - Preserving single-minute values (`17`, `57`) when changing hours.
  - Validating zero vs positive durations.
- **`com.example.ScheduleCalculatorTest`** — **4 passed, 0 failed, 0 skipped**
  - Daily next trigger calculation in local timezone when today's start time already passed.
  - Expired one-time schedule detection (`null` next trigger).
  - Overlapping daily schedule conflict detection.
  - Custom weekly weekday filtering.

### 2. Integration & Compose UI Tests (Robolectric SDK 36, In-Memory Room + DataStore + Fakes)
- **`com.example.ResolveRobolectricTest`** — **13 passed, 0 failed, 0 skipped**
  - App name (`Resolve`) and package ID (`com.resolve.focus`) verification.
  - Recovery code 16-digit generation, salted PBKDF2-HMAC-SHA256 verification, 5-attempt throttling, and post-cooldown unlock.
  - Instant Lock refusal (`ENFORCEMENT_UNAVAILABLE`) when neither Device Owner nor Accessibility Service is enabled.
  - Delayed Lock two-phase flow with Device Owner: independent Free Use Window -> automatic transition -> Lock Duration started after confirmation.
  - Accessibility Guard mode (`EnforcementMode.ACCESSIBILITY_GUARD`): Delayed Lock transition and recovery verification without Device Owner.
  - Reboot restoration during active lock restarting full configured Lock Duration.
  - Recovery code verification ending active lock session and recording `RECOVERED` history exactly once.
  - Home screen Compose UI rendering `RESOLVE` and all three mode cards.
  - Settings screen Compose UI displaying Accessibility Service, Overlay, Usage Access, Battery permissions, and `Developed by "Tahan Official"`.
  - `DurationSelector` direct numeric entry (`3h 40m` -> `03 : 40`).
  - Platform guards (`ForegroundEnforcementGuard`, `LockAlarmScheduler`, `PhoneAccessManager`) executing defensively without crashes.
  - Upgraded `BabyCloudMascot` and `SignatureLockScreen` rendering cleanly without crashes.
  - `SafeAndroidTextToolbar` + `RecoveryCodeSetupDialog` input, idempotent hide, and clean dismissal without `TYPE_FLOATING` `ActionMode` lifecycle errors.

### 3. Static Analysis & Build Verification
- **`gradle :app:lintDebug`:** Passed (`BUILD SUCCESSFUL`, 0 errors).
- **`gradle :app:assembleDebug`:** Passed (`app/build/outputs/apk/debug/app-debug.apk`).

**Total Automated JVM + Robolectric Tests:** **21 / 21 Passed**.

## Not Executed in This Environment
- **Instrumentation / Emulator Tests (`androidTest`):** No Android emulator or ADB-connected device is available in the cloud container.
- **Physical Device Tests (Huawei Y6 — Android 9 / Android 10–15 devices):** Not executed here. Real Device Owner provisioning (`dpm set-device-owner`), physical Accessibility / Lock Task navigation blocking, OEM background activity launch behavior during Delayed Lock, incoming/outgoing phone calls, live battery/animation frame pacing, and physical device reboot restoration require verification on target hardware.
