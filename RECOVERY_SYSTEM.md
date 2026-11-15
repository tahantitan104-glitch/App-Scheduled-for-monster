# RESOLVE — Recovery System

## Cryptographic Design (`RecoveryCodeManager.kt`)
- **Code Format:** Primary 16-digit numeric code (default) and optional 32-digit numeric code, generated via `java.security.SecureRandom` or entered and confirmed by the user.
- **Derivation:** `PBKDF2WithHmacSHA256` with 120,000 iterations, 256-bit output, and a unique 16-byte `SecureRandom` salt.
- **Storage:** Verifier bytes are wrapped with an AES-256-GCM key in `AndroidKeyStore` (`resolve_recovery_verifier_aes_key`) and stored in DataStore. Plaintext recovery codes are never stored or logged.
- **Comparison:** Constant-time comparison via `MessageDigest.isEqual`.
- **Throttling:** 5 consecutive incorrect attempts trigger a persistent cooldown starting at 30 seconds and escalating (`30s -> 60s -> 120s -> 240s`).

## Termination Flow
1. User enters the recovery code in `RecoveryCodeVerifySheet` on `LockScreenActivity`.
2. `LockSessionEngine.submitRecoveryCode()` verifies the code and moves state to `RECOVERY_IN_PROGRESS`.
3. `LockScreenActivity` calls `DevicePolicyController.exitLockTask()` (`stopLockTask()` + clears temporary Lock Task packages/features) and verifies `LOCK_TASK_MODE_NONE`.
4. `finalizeLockExit()` records `SessionOutcome.RECOVERED` in Room history once and stops `ResolveSessionService`.
