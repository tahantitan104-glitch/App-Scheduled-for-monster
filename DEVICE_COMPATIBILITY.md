# RESOLVE — Device Compatibility & Platform Notes

## Minimum & Target SDK
- **Min SDK:** Android 9 (API 28) — supports `DevicePolicyManager.setLockTaskFeatures()`.
- **Target / Compile SDK:** API 36.

## Android Version Behavior
- **Android 9 (API 28):** Exact alarms do not require `SCHEDULE_EXACT_ALARM` special access; Lock Task features and Device Owner APIs are supported.
- **Android 10–15 (API 29–35):** Background Activity Launch (BAL) restrictions apply when transitioning from another foreground app to `LockScreenActivity`. RESOLVE pairs `AlarmManager.setAlarmClock` with an active `ResolveSessionService` foreground service and Device Owner privileges to initiate the foreground lock transition through supported mechanisms.
- **Android 12+ (API 31+):** `canScheduleExactAlarms()` is checked live and surfaced in Settings.
- **Android 13+ (API 33+):** `POST_NOTIFICATIONS` runtime permission is checked and requested in Settings.
- **Android 14+ (API 34+):** `ResolveSessionService` declares `foregroundServiceType="specialUse"` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.

## Reference Device Note (Huawei Y6 — Android 9)
- No physical Huawei Y6 device is connected in this cloud build environment; therefore EMUI-specific background process management, incoming call UI overlay behavior during Lock Task Mode, and post-reboot Lock Task auto-launch remain **unverified on physical hardware**.
- RESOLVE avoids hardcoding any Huawei-specific package names and uses standard `TelecomManager` and `PackageManager` discovery instead.
