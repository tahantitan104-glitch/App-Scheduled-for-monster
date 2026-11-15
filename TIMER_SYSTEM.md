# RESOLVE — Timer & Duration Selection System

## Authoritative Model (`DurationSelection`)
To eliminate the previous cross-field reset bug (where changing hours altered minutes or snapped to 59), `DurationSelection` stores independent, bounded `hours` (`0..23`) and `minutes` (`0..59`) fields with:
- `withHours(newHours)`: updates `hours` while preserving `minutes` untouched.
- `withMinutes(newMinutes)`: updates `minutes` while preserving `hours` untouched.
- `totalMinutes = normalizedHours * 60L + normalizedMinutes`.

## Reusable UI Component (`DurationSelector.kt`)
Used identically across **Instant Lock**, **Delayed Lock** (both Free Use Window and Lock Duration), and **Schedule Lock**:
1. **Large Readout (`03 : 40`)**: Displays exact `HH : MM`; tapping opens a direct numeric entry dialog (`Hours` `0–23`, `Minutes` `0–59`).
2. **Independent Drag Wheels**: Separate `LazyColumn` + `rememberSnapFlingBehavior` wheels for Hours (`00..23`) and Minutes (`00..59` in 1-minute increments). Each wheel emits callbacks only for its own axis.
3. **Quick Presets**: Compact chips (`10m`, `15m`, `30m`, `45m`, `1h`, `2h`, `3h`, `4h`).
4. **State Survival**: All setup durations are backed by `SavedStateHandle` in `ResolveViewModel` to survive Activity recreation.
