package com.example.mascot

import com.example.data.preferences.MascotPreferencesRepository
import com.example.domain.model.LockReminderStyle
import com.example.domain.model.MascotPreferences
import com.example.domain.model.MascotType
import kotlinx.coroutines.flow.Flow

/**
 * Single-active mascot render plan guaranteeing mutual exclusivity at the logic level:
 * at most one of [showBabyCloud] or [showResolveBunny] can ever be true.
 */
data class MascotRenderPlan(
    val activeMascot: MascotType
) {
    val showBabyCloud: Boolean get() = activeMascot == MascotType.BABY_CLOUD
    val showResolveBunny: Boolean get() = activeMascot == MascotType.RESOLVE_BUNNY

    init {
        check(showBabyCloud xor showResolveBunny) {
            "Mutual exclusivity violated: exactly one mascot must be active."
        }
    }
}

/**
 * Single source of truth coordinating mutually exclusive mascot selection and persistence.
 * Selecting one mascot automatically deactivates the other without requiring an app restart.
 */
class MascotSelectionManager(
    private val preferencesRepository: MascotPreferencesRepository
) {
    val preferencesFlow: Flow<MascotPreferences> = preferencesRepository.mascotPreferencesFlow

    suspend fun selectMascot(mascot: MascotType) {
        preferencesRepository.setSelectedMascot(mascot)
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        preferencesRepository.setReducedMotionEnabled(enabled)
    }

    suspend fun markBunnyPeekEntranceSeen(seen: Boolean = true) {
        preferencesRepository.markBunnyPeekEntranceSeen(seen)
    }

    suspend fun setLockReminderEnabled(enabled: Boolean) {
        preferencesRepository.setLockReminderEnabled(enabled)
    }

    suspend fun setLockReminderText(text: String) {
        preferencesRepository.setLockReminderText(text)
    }

    suspend fun setLockReminderStyle(style: LockReminderStyle) {
        preferencesRepository.setLockReminderStyle(style)
    }

    fun buildRenderPlan(selectedMascot: MascotType): MascotRenderPlan =
        MascotRenderPlan(activeMascot = selectedMascot)
}
