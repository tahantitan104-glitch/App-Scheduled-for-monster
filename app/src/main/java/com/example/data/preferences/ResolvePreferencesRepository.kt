package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.startup.StartupPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.resolveDataStore: DataStore<Preferences> by preferencesDataStore(name = "resolve_prefs")

data class StoredRecoveryRecord(
    val verifierBase64: String,
    val saltBase64: String,
    val ivBase64: String?,
    val isKeystoreWrapped: Boolean,
    val codeLength: Int
)

data class RecoveryThrottleState(
    val failedAttempts: Int,
    val lockoutUntilWallMs: Long
)

class ResolvePreferencesRepository(private val context: Context) {

    private object Keys {
        val RECOVERY_VERIFIER = stringPreferencesKey("recovery_verifier_b64")
        val RECOVERY_SALT = stringPreferencesKey("recovery_salt_b64")
        val RECOVERY_IV = stringPreferencesKey("recovery_iv_b64")
        val RECOVERY_KEYSTORE_WRAPPED = booleanPreferencesKey("recovery_keystore_wrapped")
        val RECOVERY_LENGTH = intPreferencesKey("recovery_code_length")
        val RECOVERY_FAILED_ATTEMPTS = intPreferencesKey("recovery_failed_attempts")
        val RECOVERY_LOCKOUT_UNTIL_WALL_MS = longPreferencesKey("recovery_lockout_until_wall_ms")
        val DEFAULT_PHONE_ACCESS = booleanPreferencesKey("default_phone_access")
        val BOOT_GENERATION = intPreferencesKey("boot_generation")
        val FIRST_LAUNCH_WELCOME_SEEN = booleanPreferencesKey("first_launch_welcome_seen")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DEVICE_INSTALL_ID = stringPreferencesKey("device_install_id")
        val SYNC_LOCK_ENABLED = booleanPreferencesKey("sync_lock_enabled")
        val SYNC_PROMO_DISMISSED = booleanPreferencesKey("sync_promo_dismissed")
        val LAST_PROCESSED_SYNC_COMMAND_ID = stringPreferencesKey("last_processed_sync_command_id")
    }

    val hasRecoveryCodeFlow: Flow<Boolean> = context.resolveDataStore.data.map { prefs ->
        !prefs[Keys.RECOVERY_VERIFIER].isNullOrBlank() && !prefs[Keys.RECOVERY_SALT].isNullOrBlank()
    }

    val recoveryCodeLengthFlow: Flow<Int> = context.resolveDataStore.data.map { prefs ->
        prefs[Keys.RECOVERY_LENGTH] ?: 16
    }

    val defaultPhoneAccessFlow: Flow<Boolean> = context.resolveDataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_PHONE_ACCESS] ?: true
    }

    val startupPreferencesFlow: Flow<StartupPreferences> = context.resolveDataStore.data.map { prefs ->
        StartupPreferences(
            firstLaunchWelcomeSeen = prefs[Keys.FIRST_LAUNCH_WELCOME_SEEN] ?: false,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false
        )
    }

    val syncLockEnabledFlow: Flow<Boolean> = context.resolveDataStore.data.map { prefs ->
        prefs[Keys.SYNC_LOCK_ENABLED] ?: true
    }

    val syncPromoDismissedFlow: Flow<Boolean> = context.resolveDataStore.data.map { prefs ->
        prefs[Keys.SYNC_PROMO_DISMISSED] ?: false
    }

    suspend fun getOrCreateDeviceInstallId(): String {
        val current = context.resolveDataStore.data.first()[Keys.DEVICE_INSTALL_ID]
        if (!current.isNullOrBlank()) return current
        val generated = "dev_" + java.util.UUID.randomUUID().toString().replace("-", "").take(20)
        context.resolveDataStore.edit { prefs ->
            if (prefs[Keys.DEVICE_INSTALL_ID].isNullOrBlank()) {
                prefs[Keys.DEVICE_INSTALL_ID] = generated
            }
        }
        return context.resolveDataStore.data.first()[Keys.DEVICE_INSTALL_ID] ?: generated
    }

    suspend fun isSyncLockEnabledSnapshot(): Boolean = syncLockEnabledFlow.first()

    suspend fun setSyncLockEnabled(enabled: Boolean) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.SYNC_LOCK_ENABLED] = enabled
        }
    }

    suspend fun setSyncPromoDismissed(dismissed: Boolean) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.SYNC_PROMO_DISMISSED] = dismissed
        }
    }

    suspend fun getLastProcessedSyncCommandId(): String? {
        return context.resolveDataStore.data.first()[Keys.LAST_PROCESSED_SYNC_COMMAND_ID]
    }

    suspend fun setLastProcessedSyncCommandId(commandId: String) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.LAST_PROCESSED_SYNC_COMMAND_ID] = commandId
        }
    }

    suspend fun getStartupPreferencesSnapshot(): StartupPreferences {
        return startupPreferencesFlow.first()
    }

    suspend fun markFirstLaunchWelcomeSeen() {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.FIRST_LAUNCH_WELCOME_SEEN] = true
        }
    }

    suspend fun markOnboardingCompleted() {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.FIRST_LAUNCH_WELCOME_SEEN] = true
            prefs[Keys.ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun resetStartupStateForTesting() {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.FIRST_LAUNCH_WELCOME_SEEN] = false
            prefs[Keys.ONBOARDING_COMPLETED] = false
        }
    }

    suspend fun getRecoveryRecord(): StoredRecoveryRecord? {
        val prefs = context.resolveDataStore.data.first()
        val verifier = prefs[Keys.RECOVERY_VERIFIER] ?: return null
        val salt = prefs[Keys.RECOVERY_SALT] ?: return null
        return StoredRecoveryRecord(
            verifierBase64 = verifier,
            saltBase64 = salt,
            ivBase64 = prefs[Keys.RECOVERY_IV],
            isKeystoreWrapped = prefs[Keys.RECOVERY_KEYSTORE_WRAPPED] ?: false,
            codeLength = prefs[Keys.RECOVERY_LENGTH] ?: 16
        )
    }

    suspend fun saveRecoveryRecord(record: StoredRecoveryRecord) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.RECOVERY_VERIFIER] = record.verifierBase64
            prefs[Keys.RECOVERY_SALT] = record.saltBase64
            if (record.ivBase64 != null) {
                prefs[Keys.RECOVERY_IV] = record.ivBase64
            } else {
                prefs.remove(Keys.RECOVERY_IV)
            }
            prefs[Keys.RECOVERY_KEYSTORE_WRAPPED] = record.isKeystoreWrapped
            prefs[Keys.RECOVERY_LENGTH] = record.codeLength
            prefs[Keys.RECOVERY_FAILED_ATTEMPTS] = 0
            prefs[Keys.RECOVERY_LOCKOUT_UNTIL_WALL_MS] = 0L
        }
    }

    suspend fun getThrottleState(): RecoveryThrottleState {
        val prefs = context.resolveDataStore.data.first()
        return RecoveryThrottleState(
            failedAttempts = prefs[Keys.RECOVERY_FAILED_ATTEMPTS] ?: 0,
            lockoutUntilWallMs = prefs[Keys.RECOVERY_LOCKOUT_UNTIL_WALL_MS] ?: 0L
        )
    }

    suspend fun updateThrottleState(failedAttempts: Int, lockoutUntilWallMs: Long) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.RECOVERY_FAILED_ATTEMPTS] = failedAttempts
            prefs[Keys.RECOVERY_LOCKOUT_UNTIL_WALL_MS] = lockoutUntilWallMs
        }
    }

    suspend fun setDefaultPhoneAccess(enabled: Boolean) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_PHONE_ACCESS] = enabled
        }
    }

    suspend fun getBootGeneration(): Int {
        return context.resolveDataStore.data.first()[Keys.BOOT_GENERATION] ?: 0
    }

    suspend fun incrementBootGeneration(): Int {
        var next = 1
        context.resolveDataStore.edit { prefs ->
            next = (prefs[Keys.BOOT_GENERATION] ?: 0) + 1
            prefs[Keys.BOOT_GENERATION] = next
        }
        return next
    }
}
