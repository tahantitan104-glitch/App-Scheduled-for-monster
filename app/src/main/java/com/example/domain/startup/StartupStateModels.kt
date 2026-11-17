package com.example.domain.startup

import com.example.domain.model.ActiveSession
import com.example.domain.model.SessionState

data class StartupPreferences(
    val firstLaunchWelcomeSeen: Boolean = false,
    val onboardingCompleted: Boolean = false
)

enum class StartupStage {
    RESOLVING,
    RESTORE_LOCK,
    BRAND_INTRO,
    FIRST_TIME_WELCOME,
    REQUIRED_SETUP,
    HOME
}

object StartupDecisionResolver {

    fun shouldRestoreLockImmediately(activeSession: ActiveSession?): Boolean {
        val state = activeSession?.state ?: return false
        return state == SessionState.LOCK_TRANSITION_PENDING ||
            state == SessionState.LOCK_ENFORCEMENT_ACTIVE ||
            state == SessionState.PHONE_CALL_ACTIVE ||
            state == SessionState.RECOVERY_IN_PROGRESS
    }

    fun resolveInitialStage(
        activeSession: ActiveSession?,
        startupPreferences: StartupPreferences,
        isRequiredSetupComplete: Boolean
    ): StartupStage {
        if (shouldRestoreLockImmediately(activeSession)) {
            return StartupStage.RESTORE_LOCK
        }
        return StartupStage.BRAND_INTRO
    }

    fun resolvePostIntroStage(
        startupPreferences: StartupPreferences,
        isRequiredSetupComplete: Boolean
    ): StartupStage {
        return when {
            !startupPreferences.firstLaunchWelcomeSeen && !startupPreferences.onboardingCompleted ->
                StartupStage.FIRST_TIME_WELCOME
            !startupPreferences.onboardingCompleted && !isRequiredSetupComplete ->
                StartupStage.REQUIRED_SETUP
            else -> StartupStage.HOME
        }
    }
}
