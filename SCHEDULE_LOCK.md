# RESOLVE — Schedule Lock

## Supported Schedule Types
- **One-Time**: Specific local `LocalDate` + start time (`HH:mm`). Automatically disables after firing or if expired.
- **Daily**: Repeats every day at the configured local time.
- **Custom Weekly**: Repeats on selected `DayOfWeek` bitmask values.

## Timezone, Conflict & Missed-Schedule Rules
- All triggers are computed using `ZoneId.systemDefault()` via `ScheduleCalculator`.
- Before saving or enabling, `ScheduleCalculator.detectConflict()` blocks overlapping active time windows.
- If a schedule triggers while another session is already active, the active session is preserved and recurring schedules advance to their next occurrence without creating duplicate session engines.
- After reboot (`BOOT_COMPLETED`) or time/timezone changes (`TIME_SET`, `TIMEZONE_CHANGED`), expired schedules are skipped, in-progress windows restore under the restart policy, and upcoming alarms are rescheduled.
