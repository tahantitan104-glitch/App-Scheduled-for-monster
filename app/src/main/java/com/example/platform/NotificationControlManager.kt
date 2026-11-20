package com.example.platform

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import com.example.MainActivity
import com.example.data.preferences.ResolvePreferencesRepository
import com.example.domain.model.NotificationControlConfig
import com.example.domain.model.NotificationProtectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Focus Session Notification Control cleanly using official Android Do Not Disturb
 * policy APIs (`ACCESS_NOTIFICATION_POLICY`), preserving pre-existing user DND preferences,
 * detecting manual user overrides during an active session, and cleanly restoring policy on end.
 */
class NotificationControlManager(
    private val context: Context,
    private val preferencesRepository: ResolvePreferencesRepository,
    private val capabilityChecker: SystemCapabilityChecker
) {
    private val notificationManager: NotificationManager?
        get() = runCatching {
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        }.getOrNull()

    private val _protectionState = MutableStateFlow(NotificationProtectionState.INACTIVE)
    val protectionState: StateFlow<NotificationProtectionState> = _protectionState.asStateFlow()

    suspend fun onSessionEnforcementStarted(sessionId: String): NotificationProtectionState {
        val config = preferencesRepository.getNotificationControlConfigSnapshot()
        if (!config.enabled || !config.autoActivateDuringSessions) {
            _protectionState.value = NotificationProtectionState.INACTIVE
            return NotificationProtectionState.INACTIVE
        }

        if (!capabilityChecker.hasNotificationPolicyAccess()) {
            _protectionState.value = NotificationProtectionState.UNAVAILABLE_NO_PERMISSION
            return NotificationProtectionState.UNAVAILABLE_NO_PERMISSION
        }

        val nm = notificationManager ?: run {
            _protectionState.value = NotificationProtectionState.UNAVAILABLE_NO_PERMISSION
            return NotificationProtectionState.UNAVAILABLE_NO_PERMISSION
        }

        val existingStored = preferencesRepository.getNotificationSessionState()
        if (existingStored != null && existingStored.sessionId == sessionId) {
            return reconcileDuringActiveSession(sessionId)
        }

        val currentFilter = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                nm.currentInterruptionFilter
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
        }.getOrDefault(NotificationManager.INTERRUPTION_FILTER_ALL)

        val wasPreExistingDnd = currentFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        if (wasPreExistingDnd) {
            preferencesRepository.saveNotificationSessionState(
                ResolvePreferencesRepository.StoredNotificationSessionState(
                    sessionId = sessionId,
                    preExistingFilter = currentFilter,
                    appliedFilter = currentFilter,
                    wasPreExistingDnd = true,
                    userOverridden = false,
                    zenRuleId = ""
                )
            )
            _protectionState.value = NotificationProtectionState.PRESERVED_EXISTING_DND
            return NotificationProtectionState.PRESERVED_EXISTING_DND
        }

        val applied = applyFocusDndPolicy(nm, config)
        preferencesRepository.saveNotificationSessionState(
            ResolvePreferencesRepository.StoredNotificationSessionState(
                sessionId = sessionId,
                preExistingFilter = currentFilter,
                appliedFilter = applied.appliedFilter,
                wasPreExistingDnd = false,
                userOverridden = false,
                zenRuleId = applied.zenRuleId
            )
        )
        _protectionState.value = NotificationProtectionState.ACTIVE
        return NotificationProtectionState.ACTIVE
    }

    suspend fun reconcileDuringActiveSession(sessionId: String): NotificationProtectionState {
        val stored = preferencesRepository.getNotificationSessionState()
        if (stored == null || stored.sessionId != sessionId) {
            val config = preferencesRepository.getNotificationControlConfigSnapshot()
            val state = when {
                !config.enabled -> NotificationProtectionState.INACTIVE
                !capabilityChecker.hasNotificationPolicyAccess() ->
                    NotificationProtectionState.UNAVAILABLE_NO_PERMISSION
                else -> NotificationProtectionState.INACTIVE
            }
            _protectionState.value = state
            return state
        }

        if (stored.wasPreExistingDnd) {
            _protectionState.value = NotificationProtectionState.PRESERVED_EXISTING_DND
            return NotificationProtectionState.PRESERVED_EXISTING_DND
        }

        if (stored.userOverridden) {
            _protectionState.value = NotificationProtectionState.OVERRIDDEN_BY_USER
            return NotificationProtectionState.OVERRIDDEN_BY_USER
        }

        val nm = notificationManager
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val currentOsFilter = runCatching { nm.currentInterruptionFilter }.getOrDefault(stored.appliedFilter)
            if (currentOsFilter != stored.appliedFilter) {
                preferencesRepository.markNotificationSessionUserOverridden()
                _protectionState.value = NotificationProtectionState.OVERRIDDEN_BY_USER
                return NotificationProtectionState.OVERRIDDEN_BY_USER
            }
        }

        _protectionState.value = NotificationProtectionState.ACTIVE
        return NotificationProtectionState.ACTIVE
    }

    suspend fun onSessionEnded(sessionId: String?) {
        val stored = preferencesRepository.getNotificationSessionState()
        if (stored == null) {
            _protectionState.value = NotificationProtectionState.INACTIVE
            return
        }
        if (sessionId != null && stored.sessionId != sessionId) {
            return
        }

        val nm = notificationManager
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && capabilityChecker.hasNotificationPolicyAccess()) {
            if (stored.zenRuleId.isNotBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { nm.removeAutomaticZenRule(stored.zenRuleId) }
            }
            if (!stored.wasPreExistingDnd && !stored.userOverridden) {
                runCatching { nm.setInterruptionFilter(stored.preExistingFilter) }
            }
        }

        preferencesRepository.clearNotificationSessionState()
        _protectionState.value = NotificationProtectionState.INACTIVE
    }

    private data class AppliedDndOutcome(
        val appliedFilter: Int,
        val zenRuleId: String
    )

    private fun applyFocusDndPolicy(
        nm: NotificationManager,
        config: NotificationControlConfig
    ): AppliedDndOutcome {
        var categories = 0
        if (config.allowCalls) {
            categories = categories or NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
        }
        if (config.allowAlarms) {
            categories = categories or NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS
        }

        val targetFilter = if (categories == 0) {
            NotificationManager.INTERRUPTION_FILTER_NONE
        } else {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val policy = NotificationManager.Policy(
                    categories,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    NotificationManager.Policy.PRIORITY_SENDERS_STARRED
                )
                nm.notificationPolicy = policy
            }
        }

        var ruleId = ""
        if (Build.VERSION.SDK_INT >= 35) {
            ruleId = runCatching {
                val conditionId = Uri.parse("resolve://focus_session/${System.currentTimeMillis()}")
                val rule = AutomaticZenRule.Builder("RESOLVE Focus Session", conditionId)
                    .setConfigurationActivity(ComponentName(context, MainActivity::class.java))
                    .setInterruptionFilter(targetFilter)
                    .setEnabled(true)
                    .setType(AutomaticZenRule.TYPE_MANAGED)
                    .build()
                val addedId = nm.addAutomaticZenRule(rule).orEmpty()
                if (addedId.isNotBlank()) {
                    nm.setAutomaticZenRuleState(
                        addedId,
                        Condition(conditionId, "RESOLVE Focus Session Active", Condition.STATE_TRUE)
                    )
                }
                addedId
            }.getOrDefault("")
        }

        // Apply priority interruption filter reliably when ZenRule isn't supported or active yet
        val currentAfterRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { nm.currentInterruptionFilter }.getOrDefault(NotificationManager.INTERRUPTION_FILTER_ALL)
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
        if (currentAfterRule == NotificationManager.INTERRUPTION_FILTER_ALL &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        ) {
            runCatching { nm.setInterruptionFilter(targetFilter) }
        }

        val actualFilter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { nm.currentInterruptionFilter }.getOrDefault(targetFilter)
        } else {
            targetFilter
        }
        return AppliedDndOutcome(appliedFilter = actualFilter, zenRuleId = ruleId)
    }
}
