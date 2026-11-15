# RESOLVE — Delayed Lock

## Two Independent Phases
1. **Free Use Window (`DELAYED_FREE_USE_ACTIVE`)**:
   - Begins when the user starts Delayed Lock.
   - All installed apps remain normally usable; Lock Task Mode is NOT active yet.
   - `ResolveSessionService` (foreground `specialUse` service) and `AlarmManager.setAlarmClock` monitor the persisted deadline (`freeUseDeadlineWallMs` / `freeUseDeadlineElapsedMs`).
2. **Automatic Transition (`LOCK_TRANSITION_PENDING` -> `LOCK_ENFORCEMENT_ACTIVE`)**:
   - When the Free Use Window expires, `onDelayedFreeUseDeadlineReached` prepares Lock Task packages and launches `LockScreenActivity`.
   - Once `LockScreenActivity` confirms `LOCK_TASK_MODE_LOCKED`, the **independent** Lock Duration countdown starts (`LOCK_ENFORCEMENT_ACTIVE`).
   - The Free Use Window is never subtracted from the Lock Duration.

## Reboot During Delayed Lock
- If the device restarts while `DELAYED_FREE_USE_ACTIVE`, Resolve restores the original `freeUseDeadlineWallMs` (never granting a fresh Free Use Window).
- If the deadline passed while powered off, Resolve immediately initiates the Lock Mode transition on boot/startup.
