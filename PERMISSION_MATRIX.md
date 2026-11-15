# RESOLVE — Permission & Capability Matrix

| Capability / Permission | Mechanism | Why Needed | Requested / Queried |
|---|---|---|---|
| Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`) | `ResolveAccessibilityService` | Real-time foreground app detection and restriction during Lock Mode without requiring Device Owner | Queried via `AccessibilityManager` + `ENABLED_ACCESSIBILITY_SERVICES`; opens `ACTION_ACCESSIBILITY_SETTINGS` |
| Display Over Other Apps (`SYSTEM_ALERT_WINDOW`) | `ForegroundEnforcementGuard` | Allows Lock Screen to launch over any foreground app on Android 10+ and provides brief barrier protection | Queried via `Settings.canDrawOverlays()`; opens `ACTION_MANAGE_OVERLAY_PERMISSION` |
| Usage Access (`PACKAGE_USAGE_STATS`) | `UsageStatsManager.queryEvents()` | Fallback foreground app monitor if OEM throttles Accessibility window events | Queried via `AppOpsManager`; opens `ACTION_USAGE_ACCESS_SETTINGS` |
| Exact Alarms (`SCHEDULE_EXACT_ALARM`) | `AlarmManager.setAlarmClock` | Precise Delayed Lock & Schedule Lock transitions on API 31+ | Queried via `canScheduleExactAlarms()`; opens `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` |
| Notifications (`POST_NOTIFICATIONS`) | Runtime permission (API 33+) | Ongoing foreground monitor service notification | Requested via `RequestPermission` or app notification settings |
| Unrestricted Battery (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) | `PowerManager` | Prevents OEM battery managers from killing timers or Accessibility guard in the background | Queried via `isIgnoringBatteryOptimizations()`; opens `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| Device Owner (`BIND_DEVICE_ADMIN`) | `DevicePolicyManager` + `ResolveDeviceAdminReceiver` | Optional OS-level Lock Task kiosk enforcement via ADB | Queried live; ADB setup guide shown |
| Foreground Service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`) | `ResolveSessionService` | Reliable active session countdown & fallback monitor | Install-time manifest declaration |
| Boot & Time Receivers (`RECEIVE_BOOT_COMPLETED`) | `ResolveSystemReceiver` | Restore sessions and schedules across reboot/time/timezone changes | Install-time manifest declaration |
| Package Visibility (`<queries>`) | `ACTION_MAIN`, `CATEGORY_HOME`, `ACTION_DIAL`, `InCallService` | Discover installed apps, launcher, and Dialer/InCallUI without `QUERY_ALL_PACKAGES` | Manifest `<queries>` only |

**Explicitly Omitted Permissions:** `INTERNET`, `READ_CONTACTS`, `READ_CALL_LOG`, `CALL_PHONE`, `READ_EXTERNAL_STORAGE`, `READ_MEDIA_*`, `QUERY_ALL_PACKAGES`.
