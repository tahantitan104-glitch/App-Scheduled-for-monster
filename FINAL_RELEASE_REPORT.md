# RESOLVE — Final Release & Audit Report

## Project Audit Note
This workspace started from the clean Android template (`My Application`, `com.example`), with no prior `EXIST` source repository present to back up or migrate. Accordingly, no existing Device Owner installation was modified or removed, and no factory reset was initiated.

## Build & Artifact Status
- **Application Name:** Resolve
- **Package ID:** `com.resolve.focus`
- **Minimum SDK:** 21 (Android 5.0 Lollipop)
- **Target / Compile SDK:** 36
- **Android Lint (`gradle :app:lintDebug`):** Passed with 0 errors.
- **Debug APK Built:** `app/build/outputs/apk/debug/app-debug.apk`, copied to `.build-outputs/app-debug.apk` and `APK_DOWNLOAD/app-debug.apk` (~12.6 MiB / 13,180,522 bytes), signed with both **v1 (JAR)** and **v2** signature schemes.
- **GitHub Actions Workflow:** `.github/workflows/build-apk.yml` builds `Resolve-debug.apk` cleanly with Gradle 9.3.1 + JDK 21 and attaches it to GitHub Releases & Actions Artifacts.
- **Release APK:** Not generated in this environment because release keystore credentials (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) are intentionally not committed.
- **Automated Tests:** 25/25 JVM + Robolectric tests passed (`gradle :app:testDebugUnitTest`).

## Subsystem Status Summary
- **COMPLETED AND VERIFIED (Logic / Build / Lint / Robolectric UI):**
  - Resolve branding (`Resolve`, `com.resolve.focus`), custom adaptive launcher icon, Forest Green atmospheric glass design system, bundled fonts (`Plus Jakarta Sans`, `JetBrains Mono`).
  - Android 5.0+ (API 21+) compatibility: core library desugaring (`com.android.tools:desugar_jdk_libs:2.1.5` for `java.time.*`), `androidx.core:core-ktx:1.16.0`, legacy `ActionMode.Callback` fallback in `SafeAndroidTextToolbar`, `PBKDF2WithHmacSHA1` fallback on `< API 26` + `@RequiresApi(M)` Keystore isolation in `RecoveryCodeManager`, `TYPE_SYSTEM_ALERT` / pre-M overlay & battery guards, pre-O notification/foreground-service/alarm guards, `FLAG_SHOW_WHEN_LOCKED`/`FLAG_TURN_SCREEN_ON` for `< API 27`, and `DevicePolicyController` `isInLockTaskMode` / API 26 `getLockTaskPackages` / API 28 `setLockTaskFeatures` guards.
  - Upgraded `BabyCloudMascot` and `SignatureLockScreen` with sweeping progress arc, tabular countdown capsule, frosted bottom dock, and smooth `graphicsLayer` entrance.
  - Window-isolated `SafeAndroidTextToolbar` (`ProvideSafeTextToolbar`) + explicit focus/keyboard/toolbar cleanup across `MainActivity`, `LockScreenActivity`, dialogs, bottom sheets, and `ScheduleCreateScreen`, preventing `DecorView` `TYPE_FLOATING` `ActionMode` lifecycle desync errors.
  - Centralized `LockSessionEngine` with Dual-Tier Enforcement (`DEVICE_OWNER_LOCK_TASK` and `ACCESSIBILITY_GUARD`), Room v2 (`MIGRATION_1_2`) + DataStore persistence, `ScheduleCalculator`, salted PBKDF2 + Android Keystore recovery verifier with throttling, and 25/25 automated JVM/Robolectric tests across SDK 36 and legacy SDK 23.
- **IMPLEMENTED BUT NOT PHYSICALLY VERIFIED:**
  - Physical hardware verification on Android 5.0–5.1 (API 21–22), Android 9 Huawei Y6, or Android 10–15 devices (including live `ResolveAccessibilityService` window/recents interception, `ForegroundEnforcementGuard` overlay/UsageStats fallback, Device Owner Lock Task provisioning, background-to-foreground launch behavior, live Dialer/InCallUI behavior, and reboot broadcast restoration).
