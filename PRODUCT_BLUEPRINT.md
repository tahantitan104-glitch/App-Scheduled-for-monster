# RESOLVE — Product Blueprint

## Identity & Scope
- **Name:** Resolve (`com.resolve.focus`)
- **Developer:** Tahan Official
- **Purpose:** Dedicated focus-control and scheduled device-restriction app. No social features, no cloud accounts, no remote AI, no advertising, no analytics.

## Primary Functions
1. **Instant Lock:** Immediate Lock Task restriction for a selected duration.
2. **Delayed Lock:** Two independent timers — Free Use Window (normal phone usage) followed by automatic foreground transition into Lock Task Mode for the Lock Duration.
3. **Schedule Lock:** One-Time, Daily, and Custom Weekly automated restrictions in the device's local timezone.
4. **Allowed Applications:** Searchable `PackageManager`-backed allowlist of installed launchable apps.
5. **Phone Call Access:** System Dialer + incoming call continuity without altering lock countdowns.
6. **Recovery Code:** User-configured or generated 16-digit (optional 32-digit) cryptographic verifier.
7. **Session History:** Locally stored record of completed, recovered, interrupted, and failed sessions.
8. **Essential Settings:** Strictly live-verified capability status, official Android Settings intents, recovery setup, and application information.
