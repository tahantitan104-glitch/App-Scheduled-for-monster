# RESOLVE — Device Compatibility & Platform Notes

## Minimum & Target SDK
- **Min SDK:** Android 5.0 Lollipop (API 21) — uses core library desugaring (`desugar_jdk_libs`) and versioned platform fallbacks across Android 5.0 through Android 16.
- **Target / Compile SDK:** API 36.

## Android Version Behavior
- **Android 5.0–5.1 (API 21–22):** `SYSTEM_ALERT_WINDOW` and battery permissions are granted at install time; `LockAlarmScheduler` uses `setAlarmClock` / `setExact` without `FLAG_IMMUTABLE`; `SafeAndroidTextToolbar` falls back from floating `ActionMode.Callback2` to standard `ActionMode.Callback`; `RecoveryCodeManager` uses software-salted PBKDF2-HMAC-SHA1 without Android Keystore `KeyGenParameterSpec`; `DevicePolicyController` checks `isInLockTaskMode` and omits API 28 `setLockTaskFeatures`. APK is signed with both v1 (JAR) and v2 signature schemes for Lollipop/Marshmallow installation.
- **Android 6.0–7.1 (API 23–25):** Runtime overlay (`Settings.canDrawOverlays`) and battery optimization (`isIgnoringBatteryOptimizations`) checks enabled; hardware-backed Android Keystore AES-GCM wrapping enabled; PBKDF2-HMAC-SHA1 fallback active.
- **Android 8.0–8.1 (API 26–27):** Notification channels (`ResolveSessionService`), `startForegroundService`, `TYPE_APPLICATION_OVERLAY`, PBKDF2-HMAC-SHA256, and `setShowWhenLocked`/`setTurnScreenOn` (API 27+) active.
- **Android 9 (API 28):** Exact alarms do not require `SCHEDULE_EXACT_ALARM` special access; Lock Task features (`setLockTaskFeatures`) and Device Owner APIs are supported.
- **Android 10–15 (API 29–35):** Background Activity Launch (BAL) restrictions apply when transitioning from another foreground app to `LockScreenActivity`. RESOLVE pairs `AlarmManager.setAlarmClock` with an active `ResolveSessionService` foreground service and Device Owner privileges to initiate the foreground lock transition through supported mechanisms.
- **Android 12+ (API 31+):** `canScheduleExactAlarms()` is checked live and surfaced in Settings.
- **Android 13+ (API 33+):** `POST_NOTIFICATIONS` runtime permission is checked and requested in Settings.
- **Android 14+ (API 34+):** `ResolveSessionService` declares `foregroundServiceType="specialUse"` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.

## Reference Device Note (Huawei Y6 — Android 9)
- No physical Huawei Y6 device is connected in this cloud build environment; therefore EMUI-specific background process management, incoming call UI overlay behavior during Lock Task Mode, and post-reboot Lock Task auto-launch remain **unverified on physical hardware**.
- RESOLVE avoids hardcoding any Huawei-specific package names and uses standard `TelecomManager` and `PackageManager` discovery instead.
