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
