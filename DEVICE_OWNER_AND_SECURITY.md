# RESOLVE — Dual-Tier Enforcement & Security

## Honest Dual-Tier Enforcement Model
RESOLVE supports two genuine Android enforcement tiers depending on device capabilities:
1. **Tier 1 — Device Owner Lock Task (`EnforcementMode.DEVICE_OWNER_LOCK_TASK`):**
   - Active when `dpm.isDeviceOwnerApp("com.resolve.focus") == true`.
   - Uses `DevicePolicyManager` (`ResolveDeviceAdminReceiver`, `setLockTaskPackages`, `setLockTaskFeatures(LOCK_TASK_FEATURE_GLOBAL_ACTIONS)`) and `Activity.startLockTask()`.
2. **Tier 2 — Accessibility Guard (`EnforcementMode.ACCESSIBILITY_GUARD`):**
   - Active for standard APK installations when **Accessibility Service** (`ResolveAccessibilityService`) is enabled (without requiring ADB Device Owner).
   - Pairs `ResolveAccessibilityService` (`TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOWS_CHANGED`, `TYPE_WINDOW_CONTENT_CHANGED` with 320ms debounce) with `ForegroundEnforcementGuard` (`SYSTEM_ALERT_WINDOW` brief barrier + `UsageStatsManager` 850ms fallback loop inside `ResolveSessionService`).
   - Never invokes `startLockTask()` in `ACCESSIBILITY_GUARD` mode, preventing unwanted OS Screen Pinning confirmation prompts.
   - If neither Device Owner nor Accessibility Service is enabled, RESOLVE reports `ENFORCEMENT_UNAVAILABLE` and guides the user to **Settings -> Access & Permissions**.

## Crash-Prevention & Critical Safelists (Accessibility Guard)
During active `ACCESSIBILITY_GUARD` locks, `ResolveAccessibilityService` protects system stability and essential functions:
- Wraps OEM accessibility events, IME queries, and fallback `Intent` launches in `runCatching` blocks to prevent crashes across Android 9–15 OEMs.
- Always allows `com.resolve.focus`, user-approved launchable packages, active IME keyboards (`InputMethodManager.enabledInputMethodList`), system Dialer / Telecom / dynamically queried `android.telecom.InCallService` UIs, emergency packages, power/volume dialogs, and keyguard/lockscreen windows.
- Intercepts Recents/Overview and Notification Shade interactions when a lock is active and safely returns the user to `LockScreenActivity` with main-thread barrier cleanup.

## Package Migration Note (`EXIST` -> `com.resolve.focus`)
Changing `applicationId` to `com.resolve.focus` creates a separate Android application identity. Any prior Device Owner provisioning or local database belonging to a different package ID does **not** transfer automatically. No factory reset or automatic Device Owner removal is initiated by RESOLVE.
