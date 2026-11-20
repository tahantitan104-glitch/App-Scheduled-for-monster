package com.example.security

import android.os.SystemClock

/**
 * Tracks temporary in-memory App Lock authentication grants and protected-app state
 * while the user is actively interacting with an unlocked app.
 * Grants are cleared automatically on screen-off or when leaving to another protected/non-IME app.
 */
class AppLockSessionGuard {

    @Volatile
    private var cachedProtectedPackages: Set<String> = emptySet()

    @Volatile
    private var cachedEnabled: Boolean = false

    @Volatile
    private var cachedHasCredential: Boolean = false

    @Volatile
    private var temporarilyUnlockedPackage: String? = null

    @Volatile
    private var temporaryUnlockElapsedMs: Long = 0L

    @Volatile
    private var unlockTransitionGraceUntilElapsedMs: Long = 0L

    @Volatile
    private var lastAuthLaunchElapsedMs: Long = 0L

    @Volatile
    private var lastAuthTargetPackage: String? = null

    @Volatile
    var isManagementUnlockedInSession: Boolean = false
        private set

    fun updateState(
        enabled: Boolean,
        hasCredential: Boolean,
        protectedPackages: Set<String>
    ) {
        cachedEnabled = enabled
        cachedHasCredential = hasCredential
        cachedProtectedPackages = protectedPackages
        if (!enabled || !hasCredential) {
            clearAllTemporaryGrants()
        } else {
            val currentUnlocked = temporarilyUnlockedPackage
            if (currentUnlocked != null && !protectedPackages.contains(currentUnlocked)) {
                temporarilyUnlockedPackage = null
                unlockTransitionGraceUntilElapsedMs = 0L
            }
        }
    }

    fun isProtectionActive(): Boolean =
        cachedEnabled && cachedHasCredential && cachedProtectedPackages.isNotEmpty()

    fun isPackageProtected(packageName: String): Boolean =
        isProtectionActive() && cachedProtectedPackages.contains(packageName)

    fun isTemporarilyUnlocked(packageName: String, nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean {
        val current = temporarilyUnlockedPackage ?: return false
        if (current != packageName) return false
        return nowElapsedMs - temporaryUnlockElapsedMs <= SAME_APP_GRACE_WINDOW_MS
    }

    fun grantTemporaryUnlock(packageName: String, nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        temporarilyUnlockedPackage = packageName
        temporaryUnlockElapsedMs = nowElapsedMs
        unlockTransitionGraceUntilElapsedMs = nowElapsedMs + UNLOCK_TRANSITION_GRACE_MS
        lastAuthTargetPackage = null
        lastAuthLaunchElapsedMs = 0L
    }

    fun grantManagementAccess() {
        isManagementUnlockedInSession = true
    }

    fun revokeManagementAccess() {
        isManagementUnlockedInSession = false
    }

    fun onForegroundAppChanged(
        packageName: String,
        nowElapsedMs: Long = SystemClock.elapsedRealtime()
    ) {
        val unlocked = temporarilyUnlockedPackage ?: return
        if (unlocked == packageName) {
            temporaryUnlockElapsedMs = nowElapsedMs
            return
        }
        // Ignore transient OS permission/resolver/GMS overlay windows while in unlocked app
        if (TRANSIENT_SYSTEM_OVERLAY_PACKAGES.contains(packageName)) {
            return
        }
        // During brief activity switch right after unlocking, ignore intermediate window transitions
        if (nowElapsedMs < unlockTransitionGraceUntilElapsedMs) {
            return
        }
        // Re-lock once the user genuinely moves to another app or launcher
        temporarilyUnlockedPackage = null
        temporaryUnlockElapsedMs = 0L
        unlockTransitionGraceUntilElapsedMs = 0L
    }

    fun onScreenOff() {
        clearAllTemporaryGrants()
        isManagementUnlockedInSession = false
    }

    fun shouldLaunchPromptFor(packageName: String, nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean {
        if (!isPackageProtected(packageName)) return false
        if (isTemporarilyUnlocked(packageName, nowElapsedMs)) return false
        if (lastAuthTargetPackage == packageName &&
            nowElapsedMs - lastAuthLaunchElapsedMs < LAUNCH_DEBOUNCE_MS
        ) {
            return false
        }
        lastAuthTargetPackage = packageName
        lastAuthLaunchElapsedMs = nowElapsedMs
        return true
    }

    fun clearAllTemporaryGrants() {
        temporarilyUnlockedPackage = null
        temporaryUnlockElapsedMs = 0L
        unlockTransitionGraceUntilElapsedMs = 0L
        lastAuthTargetPackage = null
        lastAuthLaunchElapsedMs = 0L
    }

    companion object {
        // Long enough for an active foreground app session while still clearing immediately on screen off or app switch
        private const val SAME_APP_GRACE_WINDOW_MS = 30L * 60_000L
        private const val UNLOCK_TRANSITION_GRACE_MS = 2_200L
        private const val LAUNCH_DEBOUNCE_MS = 650L
        private val TRANSIENT_SYSTEM_OVERLAY_PACKAGES = setOf(
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.google.android.gms"
        )
    }
}
