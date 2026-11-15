# RESOLVE — Phone Call Access

## Design
- Outgoing calls launch the device's existing system Dialer via `Intent(Intent.ACTION_DIAL)` (requires zero runtime permissions; no `CALL_PHONE`, `READ_CONTACTS`, or `READ_CALL_LOG`).
- `PhoneAccessManager` queries `TelecomManager.defaultDialerPackage`, `TelecomManager.systemDialerPackage`, `PackageManager.resolveActivity(ACTION_DIAL)`, and checks whether `com.android.server.telecom` is installed on the device.
- No third-party or OEM package names (such as Huawei-specific dialer names) are hardcoded.

## Timer Continuity
Phone calls never pause, reset, extend, or terminate the Lock Duration. Remaining lock time is always computed against persisted `lockExpirationElapsedMs` / `lockExpirationWallMs`.
