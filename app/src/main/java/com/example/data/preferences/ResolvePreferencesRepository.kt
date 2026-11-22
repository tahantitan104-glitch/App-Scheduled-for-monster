package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AppLockAuthMethod
import com.example.domain.model.AppLockPinLength
import com.example.domain.model.FlipClockSettings
import com.example.domain.model.FlipStopwatchState
import com.example.domain.model.FlipTimerState
import com.example.domain.model.ForceDarkModeApproach
import com.example.domain.model.ForceDarkPreferences
import com.example.domain.model.LockMode
import com.example.domain.model.LockProfileSettings
import com.example.domain.model.NotificationControlConfig
import com.example.domain.startup.StartupPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.resolveDataStore: DataStore<Preferences> by preferencesDataStore(name = "resolve_prefs")

data class StoredRecoveryRecord(
    val verifierBase64: String,
    val saltBase64: String,
    val ivBase64: String?,
    val isKeystoreWrapped: Boolean,
    val codeLength: Int
)

data class StoredAppLockCredentialRecord(
    val method: AppLockAuthMethod,
    val pinLength: AppLockPinLength,
    val verifierBase64: String,
    val saltBase64: String,
    val ivBase64: String?,
    val isKeystoreWrapped: Boolean
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
        val PROCESSED_SYNC_COMMAND_IDS = stringPreferencesKey("processed_sync_command_ids")
        val PROCESSED_OCCURRENCE_IDS = stringPreferencesKey("processed_schedule_occurrence_ids")

        // App Lock local-only security settings
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_METHOD = stringPreferencesKey("app_lock_method")
        val APP_LOCK_PIN_LENGTH = intPreferencesKey("app_lock_pin_length")
        val APP_LOCK_VERIFIER = stringPreferencesKey("app_lock_verifier_b64")
        val APP_LOCK_SALT = stringPreferencesKey("app_lock_salt_b64")
        val APP_LOCK_IV = stringPreferencesKey("app_lock_iv_b64")
        val APP_LOCK_KEYSTORE_WRAPPED = booleanPreferencesKey("app_lock_keystore_wrapped")
        val APP_LOCK_FAILED_ATTEMPTS = intPreferencesKey("app_lock_failed_attempts")
        val APP_LOCK_LOCKOUT_UNTIL_WALL_MS = longPreferencesKey("app_lock_lockout_until_wall_ms")

        // Force Dark Mode local-only legacy + cleanly separated Modes / Vision preferences
        val FORCE_DARK_ENABLED = booleanPreferencesKey("force_dark_enabled")
        val FORCE_DARK_APPROACH = stringPreferencesKey("force_dark_approach")
        val FORCE_DARK_APP_OVERRIDES_JSON = stringPreferencesKey("force_dark_app_overrides_json")
        val DARK_MODE_APP_PREFS_JSON = stringPreferencesKey("mode_dark_app_prefs_json")

        // Focus Guard Landscape Tilt Mode & Offline Profile customization
        val LANDSCAPE_TILT_MODE = booleanPreferencesKey("lock_screen_landscape_tilt_mode")
        val LOCAL_PROFILE_NAME = stringPreferencesKey("local_profile_name")
        val LOCAL_AVATAR_BASE64 = stringPreferencesKey("local_profile_avatar_b64")

        // Focus Session Notification Control & DND restore tracking
        val NOTIF_CONTROL_ENABLED = booleanPreferencesKey("notif_control_enabled")
        val NOTIF_CONTROL_AUTO_ACTIVATE = booleanPreferencesKey("notif_control_auto_activate")
        val NOTIF_CONTROL_ALLOW_CALLS = booleanPreferencesKey("notif_control_allow_calls")
        val NOTIF_CONTROL_ALLOW_ALARMS = booleanPreferencesKey("notif_control_allow_alarms")
        val NOTIF_SESSION_ACTIVE_ID = stringPreferencesKey("notif_session_active_id")
        val NOTIF_PREEXISTING_FILTER = intPreferencesKey("notif_preexisting_filter")
        val NOTIF_APPLIED_FILTER = intPreferencesKey("notif_applied_filter")
        val NOTIF_WAS_PREEXISTING_DND = booleanPreferencesKey("notif_was_preexisting_dnd")
        val NOTIF_USER_OVERRIDDEN = booleanPreferencesKey("notif_user_overridden")
        val NOTIF_ACTIVE_ZEN_RULE_ID = stringPreferencesKey("notif_active_zen_rule_id")
        val LAST_RECOVERY_TIMESTAMP_WALL_MS = longPreferencesKey("last_recovery_timestamp_wall_ms")

        // Flip Clock Settings (synced + local hardware brightness & sleep timer)
        val FLIP_USE_24H = booleanPreferencesKey("flip_clock_use_24h")
        val FLIP_USE_SYSTEM_TIME_FORMAT = booleanPreferencesKey("flip_clock_use_system_time_format")
        val FLIP_HOUR_LEADING_ZERO = booleanPreferencesKey("flip_clock_hour_leading_zero")
        val FLIP_DATE_FORMAT_PATTERN = stringPreferencesKey("flip_clock_date_format_pattern")
        val FLIP_KEEP_AWAKE = booleanPreferencesKey("flip_clock_keep_awake")
        val FLIP_SHOW_DATE = booleanPreferencesKey("flip_clock_show_date")
        val FLIP_SHOW_WEEKDAY = booleanPreferencesKey("flip_clock_show_weekday")
        val FLIP_SHOW_SECONDS = booleanPreferencesKey("flip_clock_show_seconds")
        val FLIP_SHOW_AXIS = booleanPreferencesKey("flip_clock_show_axis")
        val FLIP_SHOW_UPCOMING_ALARM = booleanPreferencesKey("flip_clock_show_upcoming_alarm")
        val FLIP_SHOW_BATTERY = booleanPreferencesKey("flip_clock_show_battery")
        val FLIP_SHOW_WEATHER = booleanPreferencesKey("flip_clock_show_weather")
        val FLIP_WEATHER_CITY = stringPreferencesKey("flip_clock_weather_city")
        val FLIP_SLEEP_TIMER_MINUTES = intPreferencesKey("flip_clock_sleep_timer_minutes")
        val FLIP_CLOCK_TICKER_SOUND = booleanPreferencesKey("flip_clock_ticker_sound")
        val FLIP_CUSTOM_TAG = stringPreferencesKey("flip_clock_custom_tag")
        val FLIP_AUTO_REPEAT = booleanPreferencesKey("flip_clock_auto_repeat")
        val FLIP_ZOOM_FRACTION = floatPreferencesKey("flip_clock_zoom_fraction")
        val FLIP_BRIGHTNESS_PERCENT = floatPreferencesKey("flip_clock_brightness_percent")
        val FLIP_SETTINGS_UPDATED_WALL_MS = longPreferencesKey("flip_clock_settings_updated_wall_ms")
        val FLIP_PRESETS_SEEDED = booleanPreferencesKey("flip_clock_presets_seeded")
        val FLIP_PRESET_TOMBSTONES_JSON = stringPreferencesKey("flip_clock_preset_tombstones_json")

        // Flip Timer State (target-timestamp based for background & Force Stop recovery)
        val FLIP_TIMER_PRESET_ID = stringPreferencesKey("flip_timer_preset_id")
        val FLIP_TIMER_NAME = stringPreferencesKey("flip_timer_name")
        val FLIP_TIMER_DURATION_MS = longPreferencesKey("flip_timer_duration_ms")
        val FLIP_TIMER_TARGET_END_WALL_MS = longPreferencesKey("flip_timer_target_end_wall_ms")
        val FLIP_TIMER_PAUSED_REMAINING_MS = longPreferencesKey("flip_timer_paused_remaining_ms")
        val FLIP_TIMER_IS_RUNNING = booleanPreferencesKey("flip_timer_is_running")
        val FLIP_TIMER_IS_PAUSED = booleanPreferencesKey("flip_timer_is_paused")
        val FLIP_TIMER_FINISHED_AT_WALL_MS = longPreferencesKey("flip_timer_finished_at_wall_ms")
        val FLIP_TIMER_MSG_UNTIL_WALL_MS = longPreferencesKey("flip_timer_msg_until_wall_ms")
        val FLIP_TIMER_AUTO_REPEAT_AT_WALL_MS = longPreferencesKey("flip_timer_auto_repeat_at_wall_ms")
        val FLIP_TIMER_NOTIF_REQUESTED = booleanPreferencesKey("flip_timer_notif_requested")
        val FLIP_TIMER_NOTIF_DENIED_NOTICE = booleanPreferencesKey("flip_timer_notif_denied_notice")

        // Flip Stopwatch State (monotonic elapsedRealtime + wall fallback)
        val FLIP_SW_IS_RUNNING = booleanPreferencesKey("flip_sw_is_running")
        val FLIP_SW_ACCUMULATED_MS = longPreferencesKey("flip_sw_accumulated_ms")
        val FLIP_SW_STARTED_ELAPSED_MS = longPreferencesKey("flip_sw_started_elapsed_ms")
        val FLIP_SW_STARTED_WALL_MS = longPreferencesKey("flip_sw_started_wall_ms")

        fun profileStrictKey(mode: LockMode) = booleanPreferencesKey("lock_profile_${mode.name.lowercase()}_strict")
        fun profileAllowSelectedKey(mode: LockMode) = booleanPreferencesKey("lock_profile_${mode.name.lowercase()}_allow_selected")
        fun profileFlipShortcutKey(mode: LockMode) = booleanPreferencesKey("lock_profile_${mode.name.lowercase()}_flip_shortcut")
        fun profileAllowedPackagesKey(mode: LockMode) = stringPreferencesKey("lock_profile_${mode.name.lowercase()}_allowed_pkgs")
        fun profileUpdatedWallKey(mode: LockMode) = longPreferencesKey("lock_profile_${mode.name.lowercase()}_updated_wall_ms")
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

    val appLockEnabledFlow: Flow<Boolean> = context.resolveDataStore.data.map { prefs ->
        prefs[Keys.APP_LOCK_ENABLED] ?: false
    }

    val hasAppLockCredentialFlow: Flow<Boolean> = context.resolveDataStore.data.map { prefs ->
        !prefs[Keys.APP_LOCK_VERIFIER].isNullOrBlank() && !prefs[Keys.APP_LOCK_SALT].isNullOrBlank()
    }

    val appLockMethodFlow: Flow<AppLockAuthMethod> = context.resolveDataStore.data.map { prefs ->
        val raw = prefs[Keys.APP_LOCK_METHOD]
        AppLockAuthMethod.entries.firstOrNull { it.name == raw } ?: AppLockAuthMethod.PIN
    }

    val appLockPinLengthFlow: Flow<AppLockPinLength> = context.resolveDataStore.data.map { prefs ->
        val len = prefs[Keys.APP_LOCK_PIN_LENGTH] ?: 4
        if (len == 6) AppLockPinLength.SIX else AppLockPinLength.FOUR
    }

    val forceDarkPreferencesFlow: Flow<ForceDarkPreferences> = context.resolveDataStore.data.map { prefs ->
        val enabled = prefs[Keys.FORCE_DARK_ENABLED] ?: false
        val approachRaw = prefs[Keys.FORCE_DARK_APPROACH]
        val approach = ForceDarkModeApproach.entries.firstOrNull { it.name == approachRaw }
            ?: ForceDarkModeApproach.FORCE_DARK
        val overrides = decodeBooleanMap(prefs[Keys.FORCE_DARK_APP_OVERRIDES_JSON].orEmpty())
        ForceDarkPreferences(
            enabled = enabled,
            approach = approach,
            appOverrides = overrides
        )
    }

    val darkModeAppPrefsFlow: Flow<Map<String, Boolean>> = context.resolveDataStore.data.map { prefs ->
        decodeBooleanMap(prefs[Keys.DARK_MODE_APP_PREFS_JSON].orEmpty())
    }

    val landscapeTiltModeFlow: Flow<Boolean> = context.resolveDataStore.data.map { prefs ->
        prefs[Keys.LANDSCAPE_TILT_MODE] ?: false
    }

    val localProfileNameFlow: Flow<String> = context.resolveDataStore.data.map { prefs ->
        prefs[Keys.LOCAL_PROFILE_NAME].orEmpty()
    }

    val localAvatarBase64Flow: Flow<String> = context.resolveDataStore.data.map { prefs ->
        prefs[Keys.LOCAL_AVATAR_BASE64].orEmpty()
    }

    val flipClockSettingsFlow: Flow<FlipClockSettings> = context.resolveDataStore.data.map { prefs ->
        mapFlipClockSettings(prefs)
    }

    val flipTimerStateFlow: Flow<FlipTimerState> = context.resolveDataStore.data.map { prefs ->
        mapFlipTimerState(prefs)
    }

    val flipStopwatchStateFlow: Flow<FlipStopwatchState> = context.resolveDataStore.data.map { prefs ->
        mapFlipStopwatchState(prefs)
    }

    fun observeLockProfileSettings(mode: LockMode): Flow<LockProfileSettings> =
        context.resolveDataStore.data.map { prefs -> mapLockProfileSettings(prefs, mode) }

    val notificationControlConfigFlow: Flow<NotificationControlConfig> = context.resolveDataStore.data.map { prefs ->
        NotificationControlConfig(
            enabled = prefs[Keys.NOTIF_CONTROL_ENABLED] ?: false,
            autoActivateDuringSessions = prefs[Keys.NOTIF_CONTROL_AUTO_ACTIVATE] ?: true,
            allowCalls = prefs[Keys.NOTIF_CONTROL_ALLOW_CALLS] ?: true,
            allowAlarms = prefs[Keys.NOTIF_CONTROL_ALLOW_ALARMS] ?: true
        )
    }

    val lastRecoveryWallMsFlow: Flow<Long> = context.resolveDataStore.data.map { prefs ->
        prefs[Keys.LAST_RECOVERY_TIMESTAMP_WALL_MS] ?: 0L
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
        recordProcessedSyncCommandId(commandId)
    }

    suspend fun hasProcessedSyncCommandId(commandId: String): Boolean {
        if (commandId.isBlank()) return false
        val prefs = context.resolveDataStore.data.first()
        if (prefs[Keys.LAST_PROCESSED_SYNC_COMMAND_ID] == commandId) return true
        return decodeIdList(prefs[Keys.PROCESSED_SYNC_COMMAND_IDS]).contains(commandId)
    }

    suspend fun recordProcessedSyncCommandId(commandId: String) {
        if (commandId.isBlank()) return
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.LAST_PROCESSED_SYNC_COMMAND_ID] = commandId
            val updated = appendBoundedId(decodeIdList(prefs[Keys.PROCESSED_SYNC_COMMAND_IDS]), commandId)
            prefs[Keys.PROCESSED_SYNC_COMMAND_IDS] = encodeIdList(updated)
        }
    }

    suspend fun hasProcessedOccurrenceId(occurrenceId: String): Boolean {
        if (occurrenceId.isBlank()) return false
        val prefs = context.resolveDataStore.data.first()
        return decodeIdList(prefs[Keys.PROCESSED_OCCURRENCE_IDS]).contains(occurrenceId)
    }

    suspend fun recordProcessedOccurrenceId(occurrenceId: String) {
        if (occurrenceId.isBlank()) return
        context.resolveDataStore.edit { prefs ->
            val updated = appendBoundedId(decodeIdList(prefs[Keys.PROCESSED_OCCURRENCE_IDS]), occurrenceId)
            prefs[Keys.PROCESSED_OCCURRENCE_IDS] = encodeIdList(updated)
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

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_ENABLED] = enabled
        }
    }

    suspend fun isAppLockEnabledSnapshot(): Boolean = appLockEnabledFlow.first()

    suspend fun getAppLockCredentialRecord(): StoredAppLockCredentialRecord? {
        val prefs = context.resolveDataStore.data.first()
        val verifier = prefs[Keys.APP_LOCK_VERIFIER] ?: return null
        val salt = prefs[Keys.APP_LOCK_SALT] ?: return null
        val method = AppLockAuthMethod.entries.firstOrNull { it.name == prefs[Keys.APP_LOCK_METHOD] }
            ?: AppLockAuthMethod.PIN
        val pinLength = if ((prefs[Keys.APP_LOCK_PIN_LENGTH] ?: 4) == 6) {
            AppLockPinLength.SIX
        } else {
            AppLockPinLength.FOUR
        }
        return StoredAppLockCredentialRecord(
            method = method,
            pinLength = pinLength,
            verifierBase64 = verifier,
            saltBase64 = salt,
            ivBase64 = prefs[Keys.APP_LOCK_IV],
            isKeystoreWrapped = prefs[Keys.APP_LOCK_KEYSTORE_WRAPPED] ?: false
        )
    }

    suspend fun saveAppLockCredentialRecord(record: StoredAppLockCredentialRecord) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_METHOD] = record.method.name
            prefs[Keys.APP_LOCK_PIN_LENGTH] = record.pinLength.digits
            prefs[Keys.APP_LOCK_VERIFIER] = record.verifierBase64
            prefs[Keys.APP_LOCK_SALT] = record.saltBase64
            if (record.ivBase64 != null) {
                prefs[Keys.APP_LOCK_IV] = record.ivBase64
            } else {
                prefs.remove(Keys.APP_LOCK_IV)
            }
            prefs[Keys.APP_LOCK_KEYSTORE_WRAPPED] = record.isKeystoreWrapped
            prefs[Keys.APP_LOCK_FAILED_ATTEMPTS] = 0
            prefs[Keys.APP_LOCK_LOCKOUT_UNTIL_WALL_MS] = 0L
        }
    }

    suspend fun getAppLockThrottleState(): RecoveryThrottleState {
        val prefs = context.resolveDataStore.data.first()
        return RecoveryThrottleState(
            failedAttempts = prefs[Keys.APP_LOCK_FAILED_ATTEMPTS] ?: 0,
            lockoutUntilWallMs = prefs[Keys.APP_LOCK_LOCKOUT_UNTIL_WALL_MS] ?: 0L
        )
    }

    suspend fun updateAppLockThrottleState(failedAttempts: Int, lockoutUntilWallMs: Long) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_FAILED_ATTEMPTS] = failedAttempts
            prefs[Keys.APP_LOCK_LOCKOUT_UNTIL_WALL_MS] = lockoutUntilWallMs
        }
    }

    suspend fun setForceDarkEnabled(enabled: Boolean) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.FORCE_DARK_ENABLED] = enabled
        }
    }

    suspend fun setForceDarkApproach(approach: ForceDarkModeApproach) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.FORCE_DARK_APPROACH] = approach.name
        }
    }

    suspend fun setForceDarkAppOverride(packageName: String, enabled: Boolean) {
        if (packageName.isBlank()) return
        context.resolveDataStore.edit { prefs ->
            val current = decodeBooleanMap(prefs[Keys.FORCE_DARK_APP_OVERRIDES_JSON].orEmpty()).toMutableMap()
            current[packageName] = enabled
            prefs[Keys.FORCE_DARK_APP_OVERRIDES_JSON] = encodeBooleanMap(current)
        }
    }

    suspend fun resetForceDarkAppOverride(packageName: String) {
        context.resolveDataStore.edit { prefs ->
            val current = decodeBooleanMap(prefs[Keys.FORCE_DARK_APP_OVERRIDES_JSON].orEmpty()).toMutableMap()
            current.remove(packageName)
            prefs[Keys.FORCE_DARK_APP_OVERRIDES_JSON] = encodeBooleanMap(current)
        }
    }

    suspend fun resetAllForceDarkPreferences() {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.FORCE_DARK_ENABLED] = false
            prefs[Keys.FORCE_DARK_APPROACH] = ForceDarkModeApproach.FORCE_DARK.name
            prefs.remove(Keys.FORCE_DARK_APP_OVERRIDES_JSON)
        }
    }

    suspend fun getLockProfileSettingsSnapshot(mode: LockMode): LockProfileSettings =
        mapLockProfileSettings(context.resolveDataStore.data.first(), mode)

    suspend fun getAllLockProfileSettingsSnapshot(): Map<LockMode, LockProfileSettings> {
        val prefs = context.resolveDataStore.data.first()
        return LockMode.entries.associateWith { mapLockProfileSettings(prefs, it) }
    }

    suspend fun saveLockProfileSettings(settings: LockProfileSettings) {
        val mode = settings.mode
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.profileStrictKey(mode)] = settings.strictMode
            prefs[Keys.profileAllowSelectedKey(mode)] = settings.allowSelectedApps
            prefs[Keys.profileFlipShortcutKey(mode)] = settings.showFlipClockShortcut
            prefs[Keys.profileAllowedPackagesKey(mode)] = encodeIdList(settings.allowedPackages.distinct())
            prefs[Keys.profileUpdatedWallKey(mode)] = settings.updatedAtWallMs
        }
    }

    suspend fun getFlipClockSettingsSnapshot(): FlipClockSettings =
        mapFlipClockSettings(context.resolveDataStore.data.first())

    suspend fun saveFlipClockSettings(settings: FlipClockSettings) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.FLIP_USE_24H] = settings.use24HourFormat
            prefs[Keys.FLIP_USE_SYSTEM_TIME_FORMAT] = settings.useSystemTimeFormat
            prefs[Keys.FLIP_HOUR_LEADING_ZERO] = settings.hourLeadingZero
            prefs[Keys.FLIP_DATE_FORMAT_PATTERN] = settings.resolvedDateFormat.pattern
            prefs[Keys.FLIP_KEEP_AWAKE] = settings.keepScreenAwake
            prefs[Keys.FLIP_SHOW_DATE] = settings.showDate
            prefs[Keys.FLIP_SHOW_WEEKDAY] = settings.showWeekday
            prefs[Keys.FLIP_SHOW_SECONDS] = settings.showSeconds
            prefs[Keys.FLIP_SHOW_AXIS] = settings.showFlipAxis
            prefs[Keys.FLIP_SHOW_UPCOMING_ALARM] = settings.showUpcomingAlarm
            prefs[Keys.FLIP_SHOW_BATTERY] = settings.showBattery
            prefs[Keys.FLIP_SHOW_WEATHER] = settings.showWeather
            prefs[Keys.FLIP_WEATHER_CITY] = settings.weatherLocationQuery.trim().take(48)
            prefs[Keys.FLIP_SLEEP_TIMER_MINUTES] = settings.sleepTimerMinutes.coerceIn(0, 240)
            prefs[Keys.FLIP_CLOCK_TICKER_SOUND] = settings.clockTickerSoundEnabled
            prefs[Keys.FLIP_CUSTOM_TAG] = settings.customTag.take(28)
            prefs[Keys.FLIP_AUTO_REPEAT] = settings.autoRepeat
            prefs[Keys.FLIP_ZOOM_FRACTION] = settings.clampedZoomFraction
            if (settings.lastBrightnessPercent != null) {
                prefs[Keys.FLIP_BRIGHTNESS_PERCENT] = settings.lastBrightnessPercent.coerceIn(
                    FlipClockSettings.MIN_BRIGHTNESS_PERCENT,
                    FlipClockSettings.MAX_BRIGHTNESS_PERCENT
                )
            } else {
                prefs.remove(Keys.FLIP_BRIGHTNESS_PERCENT)
            }
            prefs[Keys.FLIP_SETTINGS_UPDATED_WALL_MS] = settings.updatedAtWallMs
        }
    }

    suspend fun resetFlipClockSettingsOnly(nowWallMs: Long) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.FLIP_USE_24H] = false
            prefs[Keys.FLIP_USE_SYSTEM_TIME_FORMAT] = false
            prefs[Keys.FLIP_HOUR_LEADING_ZERO] = false
            prefs[Keys.FLIP_DATE_FORMAT_PATTERN] = com.example.domain.model.FlipDateFormat.DD_MM_YY.pattern
            prefs[Keys.FLIP_KEEP_AWAKE] = false
            prefs[Keys.FLIP_SHOW_DATE] = false
            prefs[Keys.FLIP_SHOW_WEEKDAY] = true
            prefs[Keys.FLIP_SHOW_SECONDS] = true
            prefs[Keys.FLIP_SHOW_AXIS] = true
            prefs[Keys.FLIP_SHOW_UPCOMING_ALARM] = false
            prefs[Keys.FLIP_SHOW_BATTERY] = false
            prefs[Keys.FLIP_SHOW_WEATHER] = false
            prefs[Keys.FLIP_WEATHER_CITY] = ""
            prefs[Keys.FLIP_SLEEP_TIMER_MINUTES] = 0
            prefs[Keys.FLIP_CLOCK_TICKER_SOUND] = false
            prefs[Keys.FLIP_CUSTOM_TAG] = ""
            prefs[Keys.FLIP_AUTO_REPEAT] = false
            prefs[Keys.FLIP_ZOOM_FRACTION] = FlipClockSettings.DEFAULT_ZOOM_FRACTION
            prefs[Keys.FLIP_SETTINGS_UPDATED_WALL_MS] = nowWallMs
        }
    }

    suspend fun getFlipTimerStateSnapshot(): FlipTimerState =
        mapFlipTimerState(context.resolveDataStore.data.first())

    suspend fun saveFlipTimerState(state: FlipTimerState) {
        context.resolveDataStore.edit { prefs ->
            if (state.activePresetId != null) {
                prefs[Keys.FLIP_TIMER_PRESET_ID] = state.activePresetId
            } else {
                prefs.remove(Keys.FLIP_TIMER_PRESET_ID)
            }
            prefs[Keys.FLIP_TIMER_NAME] = state.timerName
            prefs[Keys.FLIP_TIMER_DURATION_MS] = state.selectedDurationMs.coerceAtLeast(1_000L)
            if (state.targetEndWallMs != null) {
                prefs[Keys.FLIP_TIMER_TARGET_END_WALL_MS] = state.targetEndWallMs
            } else {
                prefs.remove(Keys.FLIP_TIMER_TARGET_END_WALL_MS)
            }
            if (state.remainingWhenPausedMs != null) {
                prefs[Keys.FLIP_TIMER_PAUSED_REMAINING_MS] = state.remainingWhenPausedMs
            } else {
                prefs.remove(Keys.FLIP_TIMER_PAUSED_REMAINING_MS)
            }
            prefs[Keys.FLIP_TIMER_IS_RUNNING] = state.isRunning
            prefs[Keys.FLIP_TIMER_IS_PAUSED] = state.isPaused
            if (state.finishedAtWallMs != null) {
                prefs[Keys.FLIP_TIMER_FINISHED_AT_WALL_MS] = state.finishedAtWallMs
            } else {
                prefs.remove(Keys.FLIP_TIMER_FINISHED_AT_WALL_MS)
            }
            if (state.completionMessageUntilWallMs != null) {
                prefs[Keys.FLIP_TIMER_MSG_UNTIL_WALL_MS] = state.completionMessageUntilWallMs
            } else {
                prefs.remove(Keys.FLIP_TIMER_MSG_UNTIL_WALL_MS)
            }
            if (state.autoRepeatScheduledAtWallMs != null) {
                prefs[Keys.FLIP_TIMER_AUTO_REPEAT_AT_WALL_MS] = state.autoRepeatScheduledAtWallMs
            } else {
                prefs.remove(Keys.FLIP_TIMER_AUTO_REPEAT_AT_WALL_MS)
            }
            prefs[Keys.FLIP_TIMER_NOTIF_REQUESTED] = state.notificationPermissionRequestedOnce
            prefs[Keys.FLIP_TIMER_NOTIF_DENIED_NOTICE] = state.notificationPermissionDeniedNotice
        }
    }

    suspend fun getFlipStopwatchStateSnapshot(): FlipStopwatchState =
        mapFlipStopwatchState(context.resolveDataStore.data.first())

    suspend fun saveFlipStopwatchState(state: FlipStopwatchState) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.FLIP_SW_IS_RUNNING] = state.isRunning
            prefs[Keys.FLIP_SW_ACCUMULATED_MS] = state.accumulatedMs.coerceAtLeast(0L)
            if (state.startedElapsedMs != null) {
                prefs[Keys.FLIP_SW_STARTED_ELAPSED_MS] = state.startedElapsedMs
            } else {
                prefs.remove(Keys.FLIP_SW_STARTED_ELAPSED_MS)
            }
            if (state.startedWallMs != null) {
                prefs[Keys.FLIP_SW_STARTED_WALL_MS] = state.startedWallMs
            } else {
                prefs.remove(Keys.FLIP_SW_STARTED_WALL_MS)
            }
        }
    }

    suspend fun hasSeededDefaultFlipPresets(): Boolean =
        context.resolveDataStore.data.first()[Keys.FLIP_PRESETS_SEEDED] ?: false

    suspend fun markDefaultFlipPresetsSeeded() {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.FLIP_PRESETS_SEEDED] = true
        }
    }

    suspend fun getFlipPresetTombstones(): Map<String, Long> =
        decodeLongMap(context.resolveDataStore.data.first()[Keys.FLIP_PRESET_TOMBSTONES_JSON].orEmpty())

    suspend fun recordFlipPresetTombstone(presetId: String, deletedAtWallMs: Long) {
        if (presetId.isBlank()) return
        context.resolveDataStore.edit { prefs ->
            val current = decodeLongMap(prefs[Keys.FLIP_PRESET_TOMBSTONES_JSON].orEmpty()).toMutableMap()
            current[presetId] = deletedAtWallMs
            prefs[Keys.FLIP_PRESET_TOMBSTONES_JSON] = encodeLongMap(current)
        }
    }

    suspend fun clearFlipPresetTombstone(presetId: String) {
        if (presetId.isBlank()) return
        context.resolveDataStore.edit { prefs ->
            val current = decodeLongMap(prefs[Keys.FLIP_PRESET_TOMBSTONES_JSON].orEmpty()).toMutableMap()
            if (current.remove(presetId) != null) {
                prefs[Keys.FLIP_PRESET_TOMBSTONES_JSON] = encodeLongMap(current)
            }
        }
    }

    suspend fun setAppDarkModePreference(packageName: String, enabled: Boolean) {
        if (packageName.isBlank()) return
        context.resolveDataStore.edit { prefs ->
            val current = decodeBooleanMap(prefs[Keys.DARK_MODE_APP_PREFS_JSON].orEmpty()).toMutableMap()
            current[packageName] = enabled
            prefs[Keys.DARK_MODE_APP_PREFS_JSON] = encodeBooleanMap(current)
        }
    }

    suspend fun isLandscapeTiltModeSnapshot(): Boolean = landscapeTiltModeFlow.first()

    suspend fun setLandscapeTiltMode(enabled: Boolean) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.LANDSCAPE_TILT_MODE] = enabled
        }
    }

    suspend fun getLocalProfileNameSnapshot(): String = localProfileNameFlow.first()

    suspend fun setLocalProfileName(name: String) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.LOCAL_PROFILE_NAME] = name.trim().take(80)
        }
    }

    suspend fun getLocalAvatarBase64Snapshot(): String = localAvatarBase64Flow.first()

    suspend fun setLocalAvatarBase64(base64: String) {
        context.resolveDataStore.edit { prefs ->
            if (base64.isBlank()) {
                prefs.remove(Keys.LOCAL_AVATAR_BASE64)
            } else {
                prefs[Keys.LOCAL_AVATAR_BASE64] = base64
            }
        }
    }

    suspend fun resetAllModeAppPreferences() {
        context.resolveDataStore.edit { prefs ->
            prefs.remove(Keys.DARK_MODE_APP_PREFS_JSON)
            prefs[Keys.FORCE_DARK_ENABLED] = false
            prefs.remove(Keys.FORCE_DARK_APP_OVERRIDES_JSON)
        }
    }

    suspend fun getNotificationControlConfigSnapshot(): NotificationControlConfig =
        notificationControlConfigFlow.first()

    suspend fun setNotificationControlEnabled(enabled: Boolean) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.NOTIF_CONTROL_ENABLED] = enabled
        }
    }

    suspend fun setNotificationControlAutoActivate(autoActivate: Boolean) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.NOTIF_CONTROL_AUTO_ACTIVATE] = autoActivate
        }
    }

    suspend fun setNotificationControlAllowCalls(allow: Boolean) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.NOTIF_CONTROL_ALLOW_CALLS] = allow
        }
    }

    suspend fun setNotificationControlAllowAlarms(allow: Boolean) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.NOTIF_CONTROL_ALLOW_ALARMS] = allow
        }
    }

    data class StoredNotificationSessionState(
        val sessionId: String,
        val preExistingFilter: Int,
        val appliedFilter: Int,
        val wasPreExistingDnd: Boolean,
        val userOverridden: Boolean,
        val zenRuleId: String
    )

    suspend fun getNotificationSessionState(): StoredNotificationSessionState? {
        val prefs = context.resolveDataStore.data.first()
        val sessionId = prefs[Keys.NOTIF_SESSION_ACTIVE_ID] ?: return null
        if (sessionId.isBlank()) return null
        return StoredNotificationSessionState(
            sessionId = sessionId,
            preExistingFilter = prefs[Keys.NOTIF_PREEXISTING_FILTER] ?: 1,
            appliedFilter = prefs[Keys.NOTIF_APPLIED_FILTER] ?: 1,
            wasPreExistingDnd = prefs[Keys.NOTIF_WAS_PREEXISTING_DND] ?: false,
            userOverridden = prefs[Keys.NOTIF_USER_OVERRIDDEN] ?: false,
            zenRuleId = prefs[Keys.NOTIF_ACTIVE_ZEN_RULE_ID].orEmpty()
        )
    }

    suspend fun saveNotificationSessionState(state: StoredNotificationSessionState) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.NOTIF_SESSION_ACTIVE_ID] = state.sessionId
            prefs[Keys.NOTIF_PREEXISTING_FILTER] = state.preExistingFilter
            prefs[Keys.NOTIF_APPLIED_FILTER] = state.appliedFilter
            prefs[Keys.NOTIF_WAS_PREEXISTING_DND] = state.wasPreExistingDnd
            prefs[Keys.NOTIF_USER_OVERRIDDEN] = state.userOverridden
            prefs[Keys.NOTIF_ACTIVE_ZEN_RULE_ID] = state.zenRuleId
        }
    }

    suspend fun markNotificationSessionUserOverridden() {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.NOTIF_USER_OVERRIDDEN] = true
        }
    }

    suspend fun clearNotificationSessionState() {
        context.resolveDataStore.edit { prefs ->
            prefs.remove(Keys.NOTIF_SESSION_ACTIVE_ID)
            prefs.remove(Keys.NOTIF_PREEXISTING_FILTER)
            prefs.remove(Keys.NOTIF_APPLIED_FILTER)
            prefs.remove(Keys.NOTIF_WAS_PREEXISTING_DND)
            prefs.remove(Keys.NOTIF_USER_OVERRIDDEN)
            prefs.remove(Keys.NOTIF_ACTIVE_ZEN_RULE_ID)
        }
    }

    suspend fun recordSessionRecoveryTimestamp(wallMs: Long) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.LAST_RECOVERY_TIMESTAMP_WALL_MS] = wallMs
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

    private fun decodeIdList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val value = array.optString(i)
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun appendBoundedId(existing: List<String>, newId: String, maxSize: Int = 100): List<String> {
        val filtered = existing.filter { it != newId }
        val combined = filtered + newId
        return if (combined.size > maxSize) combined.takeLast(maxSize) else combined
    }

    private fun encodeIdList(ids: List<String>): String {
        val array = JSONArray()
        ids.forEach { array.put(it) }
        return array.toString()
    }

    private fun decodeBooleanMap(raw: String): Map<String, Boolean> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, json.optBoolean(key, false))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeBooleanMap(map: Map<String, Boolean>): String {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        return json.toString()
    }

    private fun decodeLongMap(raw: String): Map<String, Long> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, json.optLong(key, 0L))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeLongMap(map: Map<String, Long>): String {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        return json.toString()
    }

    private fun mapLockProfileSettings(prefs: Preferences, mode: LockMode): LockProfileSettings {
        val strict = prefs[Keys.profileStrictKey(mode)] ?: false
        val allowSelected = prefs[Keys.profileAllowSelectedKey(mode)] ?: !strict
        val flipShortcut = prefs[Keys.profileFlipShortcutKey(mode)] ?: false
        val packages = decodeIdList(prefs[Keys.profileAllowedPackagesKey(mode)])
        val updatedWall = prefs[Keys.profileUpdatedWallKey(mode)] ?: 0L
        return LockProfileSettings(
            mode = mode,
            strictMode = strict,
            allowSelectedApps = allowSelected,
            showFlipClockShortcut = flipShortcut,
            allowedPackages = packages,
            updatedAtWallMs = updatedWall
        )
    }

    private fun mapFlipClockSettings(prefs: Preferences): FlipClockSettings =
        FlipClockSettings(
            use24HourFormat = prefs[Keys.FLIP_USE_24H] ?: false,
            useSystemTimeFormat = prefs[Keys.FLIP_USE_SYSTEM_TIME_FORMAT] ?: false,
            hourLeadingZero = prefs[Keys.FLIP_HOUR_LEADING_ZERO] ?: false,
            dateFormatPattern = com.example.domain.model.FlipDateFormat.fromPattern(
                prefs[Keys.FLIP_DATE_FORMAT_PATTERN]
            ).pattern,
            keepScreenAwake = prefs[Keys.FLIP_KEEP_AWAKE] ?: false,
            showDate = prefs[Keys.FLIP_SHOW_DATE] ?: false,
            showWeekday = prefs[Keys.FLIP_SHOW_WEEKDAY] ?: true,
            showSeconds = prefs[Keys.FLIP_SHOW_SECONDS] ?: true,
            showFlipAxis = prefs[Keys.FLIP_SHOW_AXIS] ?: true,
            showUpcomingAlarm = prefs[Keys.FLIP_SHOW_UPCOMING_ALARM] ?: false,
            showBattery = prefs[Keys.FLIP_SHOW_BATTERY] ?: false,
            showWeather = prefs[Keys.FLIP_SHOW_WEATHER] ?: false,
            weatherLocationQuery = prefs[Keys.FLIP_WEATHER_CITY] ?: "",
            sleepTimerMinutes = (prefs[Keys.FLIP_SLEEP_TIMER_MINUTES] ?: 0).coerceIn(0, 240),
            clockTickerSoundEnabled = prefs[Keys.FLIP_CLOCK_TICKER_SOUND] ?: false,
            customTag = prefs[Keys.FLIP_CUSTOM_TAG] ?: "",
            autoRepeat = prefs[Keys.FLIP_AUTO_REPEAT] ?: false,
            zoomFraction = (prefs[Keys.FLIP_ZOOM_FRACTION] ?: FlipClockSettings.DEFAULT_ZOOM_FRACTION)
                .coerceIn(FlipClockSettings.MIN_ZOOM_FRACTION, FlipClockSettings.MAX_ZOOM_FRACTION),
            lastBrightnessPercent = prefs[Keys.FLIP_BRIGHTNESS_PERCENT]?.coerceIn(
                FlipClockSettings.MIN_BRIGHTNESS_PERCENT,
                FlipClockSettings.MAX_BRIGHTNESS_PERCENT
            ),
            updatedAtWallMs = prefs[Keys.FLIP_SETTINGS_UPDATED_WALL_MS] ?: 0L
        )

    private fun mapFlipTimerState(prefs: Preferences): FlipTimerState =
        FlipTimerState(
            activePresetId = prefs[Keys.FLIP_TIMER_PRESET_ID],
            timerName = prefs[Keys.FLIP_TIMER_NAME] ?: "25 Minutes Timer",
            selectedDurationMs = (prefs[Keys.FLIP_TIMER_DURATION_MS] ?: (25L * 60_000L)).coerceAtLeast(1_000L),
            targetEndWallMs = prefs[Keys.FLIP_TIMER_TARGET_END_WALL_MS],
            remainingWhenPausedMs = prefs[Keys.FLIP_TIMER_PAUSED_REMAINING_MS],
            isRunning = prefs[Keys.FLIP_TIMER_IS_RUNNING] ?: false,
            isPaused = prefs[Keys.FLIP_TIMER_IS_PAUSED] ?: false,
            finishedAtWallMs = prefs[Keys.FLIP_TIMER_FINISHED_AT_WALL_MS],
            completionMessageUntilWallMs = prefs[Keys.FLIP_TIMER_MSG_UNTIL_WALL_MS],
            autoRepeatScheduledAtWallMs = prefs[Keys.FLIP_TIMER_AUTO_REPEAT_AT_WALL_MS],
            notificationPermissionRequestedOnce = prefs[Keys.FLIP_TIMER_NOTIF_REQUESTED] ?: false,
            notificationPermissionDeniedNotice = prefs[Keys.FLIP_TIMER_NOTIF_DENIED_NOTICE] ?: false
        )

    private fun mapFlipStopwatchState(prefs: Preferences): FlipStopwatchState =
        FlipStopwatchState(
            isRunning = prefs[Keys.FLIP_SW_IS_RUNNING] ?: false,
            accumulatedMs = prefs[Keys.FLIP_SW_ACCUMULATED_MS] ?: 0L,
            startedElapsedMs = prefs[Keys.FLIP_SW_STARTED_ELAPSED_MS],
            startedWallMs = prefs[Keys.FLIP_SW_STARTED_WALL_MS]
        )
}
