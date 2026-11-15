# RESOLVE — Instant Lock

## Flow
1. User configures Lock Duration, Allowed Applications, Phone Access, and Recovery Code in `InstantLockSetupScreen`.
2. `LockSessionEngine.startInstantLock()` verifies:
   - Duration >= 1 minute
   - No active non-terminal session exists
   - Recovery code verifier is configured
   - Device Owner capability and Lock Task policy preparation (`DevicePolicyController.prepareLockTaskPolicy`) succeed
3. Session enters `LOCK_TRANSITION_PENDING` and launches `LockScreenActivity`.
4. `LockScreenActivity` calls `startLockTask()` and verifies `ActivityManager.lockTaskModeState == LOCK_TASK_MODE_LOCKED`.
5. Only after confirmation does `LockSessionEngine.confirmLockTaskEntered()` start the Lock Duration countdown (`LOCK_ENFORCEMENT_ACTIVE`) and schedule the exact expiration alarm.
6. On expiration (`onLockExpirationReached`), `stopLockTask()` is called and verified, history is recorded once (`COMPLETED`), and normal device usage resumes.
