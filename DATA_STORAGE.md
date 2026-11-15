# RESOLVE — Local Data Storage

RESOLVE is 100% offline-first with `android:allowBackup="false"` and zero `INTERNET` permission.

## Room Database (`resolve_local.db`, Version 1)
- `active_session`: Single-slot authoritative persisted session state, deadlines (`wall` + `elapsed`), mode, allowlist JSON, phone setting, and boot generation.
- `lock_schedules`: Persisted One-Time, Daily, and Custom Weekly schedules.
- `session_history`: Append-only outcomes (`COMPLETED`, `RECOVERED`, `INTERRUPTED`, `ENFORCEMENT_FAILED`), deduplicated per `sessionId`.
- `allowed_apps`: User-selected package names and display labels.

## DataStore (`resolve_prefs`)
- Keystore-wrapped PBKDF2 recovery verifier, salt, IV, length (`16` or `32`), failed attempt counter, lockout deadline, and boot generation counter.
