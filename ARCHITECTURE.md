# RESOLVE — Architecture

RESOLVE follows **MVVM + Clean Architecture** in a single cohesive Android `:app` module organized by feature and responsibility under `com.example` (with `applicationId = "com.resolve.focus"`):

- `domain/`:
  - `model/ResolveModels.kt`: `LockMode`, `SessionState`, `DurationSelection`, `ActiveSession`, `LockSchedule`, `SessionHistoryRecord`, `InstalledAppInfo`, `SystemCapabilityStatus`.
  - `time/ClockProvider.kt`: Injectable monotonic (`elapsedRealtime`) + wall-clock (`currentTimeMillis`) + `ZoneId` abstraction.
  - `scheduling/ScheduleCalculator.kt`: Local-timezone trigger computation, in-progress window detection, and overlap conflict validation.
- `data/`:
  - `local/`: Room database (`ResolveDatabase`) with `ActiveSessionDao`, `LockScheduleDao`, `SessionHistoryDao`, and `AllowedAppDao`.
  - `preferences/`: DataStore Preferences (`ResolvePreferencesRepository`) for recovery verifier ciphertext/IV/salt, throttling state, and boot generation.
  - `repository/`: Repository abstractions (`SessionRepository`, `ScheduleRepository`, `HistoryRepository`, `AllowlistRepository`).
- `security/`:
  - `RecoveryCodeManager.kt`: `SecureRandom` code generation, PBKDF2-HMAC-SHA256 derivation, Android Keystore AES-256-GCM wrapping, and exponential cooldown throttling.
- `devicepolicy/`:
  - `ResolveDeviceAdminReceiver.kt` & `DevicePolicyController.kt`: Real `DevicePolicyManager` inspection, `setLockTaskPackages`, `setLockTaskFeatures`, `startLockTask`, and `stopLockTask`.
- `telephony/`:
  - `PhoneAccessManager.kt`: Dynamic default/system Dialer discovery via `TelecomManager` and `Intent.ACTION_DIAL`.
- `platform/`:
  - `InstalledAppsRepository.kt`: `PackageManager` launchable app discovery and icon loading.
  - `SystemCapabilityChecker.kt`: Live Android permission/capability querying and official Settings intent launchers.
- `scheduling/` & `service/` & `receiver/`:
  - `AndroidLockAlarmScheduler.kt`: `AlarmManager.setAlarmClock` exact triggers.
  - `ResolveSessionService.kt`: `specialUse` foreground service monitoring active sessions and deadlines.
  - `LockTransitionAlarmReceiver.kt` & `ResolveSystemReceiver.kt`: Alarm, boot, time/timezone, and exact-alarm state change handlers.
- `session/`:
  - `LockSessionEngine.kt`: Single authoritative `Mutex`-protected session state machine.
- `presentation/` & `designsystem/`:
  - Jetpack Compose M3 screens, `ResolveViewModel` (with `SavedStateHandle`), `LockScreenActivity`, and `ResolveAppShell`.
