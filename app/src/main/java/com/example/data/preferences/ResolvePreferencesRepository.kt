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
import com.example.domain.model.AccountabilityPartnerConfig
import com.example.domain.model.AmbientSoundType
import com.example.domain.model.AppLockAuthMethod
import com.example.domain.model.AppLockPinLength
import com.example.domain.model.AppUsageQuotaRule
import com.example.domain.model.ContentBlockerConfig
import com.example.domain.model.FlipBatteryStyle
import com.example.domain.model.FlipClockCustomLayout
import com.example.domain.model.FlipClockElementAlign
import com.example.domain.model.FlipClockOrientationLayout
import com.example.domain.model.FlipClockSettings
import com.example.domain.model.FlipElementPlacement
import com.example.domain.model.FlipStopwatchState
import com.example.domain.model.FlipTimerState
import com.example.domain.model.FlipWeekdayStyle
import com.example.domain.model.FlipWeatherStyle
import com.example.domain.model.FocusProfile
import com.example.domain.model.ForceDarkModeApproach
import com.example.domain.model.ForceDarkPreferences
import com.example.domain.model.LockMode
import com.example.domain.model.LockProfileSettings
import com.example.domain.model.MindfulPauseConfig
import com.example.domain.model.NotificationControlConfig
import com.example.domain.model.ResolveThemeId
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

data class StoredPartnerPinRecord(
    val verifierBase64: String,
    val saltBase64: String,
    val ivBase64: String?,
    val isKeystoreWrapped: Boolean
)

data class StoredRestAuthSession(
    val uid: String,
    val idToken: String,
    val refreshToken: String,
    val expiresAtWallMs: Long,
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = ""
) {
    fun toCloudAuthSession(): com.example.sync.CloudAuthSession = com.example.sync.CloudAuthSession(
        uid = uid,
        displayName = displayName.takeIf { it.isNotBlank() },
        email = email.takeIf { it.isNotBlank() },
        photoUrl = photoUrl.takeIf { it.isNotBlank() }
    )
}

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
        val SYNC_INSTANT_LOCK_DEVICES = booleanPreferencesKey("sync_instant_lock_devices")
        val SYNC_DELAYED_LOCK_DEVICES = booleanPreferencesKey("sync_delayed_lock_devices")
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

        // REST Auth Session for Android 5.1.1 (API 22) Google Sign-In + Firestore REST Sync
        val REST_AUTH_UID = stringPreferencesKey("rest_auth_uid")
        val REST_AUTH_ID_TOKEN = stringPreferencesKey("rest_auth_id_token")
        val REST_AUTH_REFRESH_TOKEN = stringPreferencesKey("rest_auth_refresh_token")
        val REST_AUTH_EXPIRES_AT_WALL_MS = longPreferencesKey("rest_auth_expires_at_wall_ms")
        val REST_AUTH_EMAIL = stringPreferencesKey("rest_auth_email")
        val REST_AUTH_DISPLAY_NAME = stringPreferencesKey("rest_auth_display_name")
        val REST_AUTH_PHOTO_URL = stringPreferencesKey("rest_auth_photo_url")

        // v1.0.9 International Upgrade Settings & Guard Config
        val APP_THEME_ID = stringPreferencesKey("app_theme_id")
        val DAILY_FOCUS_GOAL_MINUTES = intPreferencesKey("daily_focus_goal_minutes")
        val SHOW_SETUP_TIPS = booleanPreferencesKey("show_setup_tips")
        val EXCLUDED_SCREEN_TIME_PACKAGES_JSON = stringPreferencesKey("excluded_screen_time_packages_json")
        val CUSTOM_FOCUS_PROFILES_JSON = stringPreferencesKey("custom_focus_profiles_json")
        val ANTI_BYPASS_STRICT_GUARD = booleanPreferencesKey("anti_bypass_strict_guard")
        val EMERGENCY_COOLDOWN_SECONDS = intPreferencesKey("emergency_cooldown_seconds")
        val MINDFUL_PAUSE_CONFIG_JSON = stringPreferencesKey("mindful_pause_config_json")
        val MINDFUL_PASS_EXPIRATIONS_JSON = stringPreferencesKey("mindful_pass_expirations_json")
        val DAILY_QUOTA_RULES_JSON = stringPreferencesKey("daily_quota_rules_json")
        val QUOTA_OVERRIDE_DAYS_JSON = stringPreferencesKey("quota_override_days_json")
        val CONTENT_BLOCKER_CONFIG_JSON = stringPreferencesKey("content_blocker_config_json")
        val INTERCEPTED_APP_COUNTS_JSON = stringPreferencesKey("intercepted_app_counts_json")

        // Accountability Partner PBKDF2 security
        val PARTNER_ENABLED = booleanPreferencesKey("partner_enabled")
        val PARTNER_NAME = stringPreferencesKey("partner_name")
        val PARTNER_REQUIRE_STRICT_UNLOCK = booleanPreferencesKey("partner_require_strict_unlock")
        val PARTNER_REQUIRE_QUOTA_OVERRIDE = booleanPreferencesKey("partner_require_quota_override")
        val PARTNER_PIN_VERIFIER = stringPreferencesKey("partner_pin_verifier_b64")
        val PARTNER_PIN_SALT = stringPreferencesKey("partner_pin_salt_b64")
        val PARTNER_PIN_IV = stringPreferencesKey("partner_pin_iv_b64")
        val PARTNER_PIN_KEYSTORE_WRAPPED = booleanPreferencesKey("partner_pin_keystore_wrapped")
        val PARTNER_PIN_FAILED_ATTEMPTS = intPreferencesKey("partner_pin_failed_attempts")
        val PARTNER_PIN_LOCKOUT_UNTIL_WALL_MS = longPreferencesKey("partner_pin_lockout_until_wall_ms")

        // Flip Clock v1.0.9 Productivity Upgrades
        val FLIP_POMODORO_ENABLED = booleanPreferencesKey("flip_pomodoro_enabled")
        val FLIP_POMODORO_FOCUS_MINUTES = intPreferencesKey("flip_pomodoro_focus_minutes")
        val FLIP_POMODORO_BREAK_MINUTES = intPreferencesKey("flip_pomodoro_break_minutes")
        val FLIP_AMBIENT_SOUND_TYPE = stringPreferencesKey("flip_ambient_sound_type")
        val FLIP_AMBIENT_VOLUME = floatPreferencesKey("flip_ambient_volume")
        val FLIP_OLED_BURN_IN_PROTECTION = booleanPreferencesKey("flip_oled_burn_in_protection")
        val FLIP_CUSTOM_LAYOUT_JSON = stringPreferencesKey("flip_custom_layout_json")
        val FLIP_WEATHER_USE_FAHRENHEIT = booleanPreferencesKey("flip_clock_weather_use_fahrenheit")
        val FLIP_WEATHER_AUTO_DETECT = booleanPreferencesKey("flip_clock_weather_auto_detect")
        val FLIP_SYNC_TIMER_WITH_FOCUS_LOCK = booleanPreferencesKey("flip_clock_sync_timer_with_focus_lock")
        val FLIP_PLAY_AMBIENT_DURING_FOCUS_LOCK = booleanPreferencesKey("flip_clock_play_ambient_during_focus_lock")
        val FLIP_AMBIENT_PLAY_IN_BACKGROUND = booleanPreferencesKey("flip_clock_ambient_play_in_background")

        // Offline Sync Outbox & Local VPN DNS Blocker metrics
        val PENDING_LOCK_COMMANDS_JSON = stringPreferencesKey("pending_sync_lock_commands_json")
        val DNS_BLOCKED_COUNT = intPreferencesKey("dns_blocked_query_count")
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

    val appThemeIdFlow: Flow<ResolveThemeId> = context.resolveDataStore.data.map { prefs ->
        ResolveThemeId.fromStorageKey(prefs[Keys.APP_THEME_ID])
    }

    val dailyFocusGoalMinutesFlow: Flow<Int> = context.resolveDataStore.data.map { prefs ->
        (prefs[Keys.DAILY_FOCUS_GOAL_MINUTES] ?: 120).coerceIn(15, 720)
    }

    val showSetupTipsFlow: Flow<Boolean> = context.resolveDataStore.data.map { prefs ->
        prefs[Keys.SHOW_SETUP_TIPS] ?: false
    }

    val excludedScreenTimePackagesFlow: Flow<Set<String>> = context.resolveDataStore.data.map { prefs ->
        decodeIdList(prefs[Keys.EXCLUDED_SCREEN_TIME_PACKAGES_JSON]).toSet()
    }

    val customFocusProfilesFlow: Flow<List<FocusProfile>> = context.resolveDataStore.data.map { prefs ->
        decodeFocusProfiles(prefs[Keys.CUSTOM_FOCUS_PROFILES_JSON])
    }

    val antiBypassStrictGuardFlow: Flow<Boolean> = context.resolveDataStore.data.map { prefs ->
        prefs[Keys.ANTI_BYPASS_STRICT_GUARD] ?: true
    }

    val emergencyCooldownSecondsFlow: Flow<Int> = context.resolveDataStore.data.map { prefs ->
        (prefs[Keys.EMERGENCY_COOLDOWN_SECONDS] ?: 15).coerceIn(0, 60)
    }

    val mindfulPauseConfigFlow: Flow<MindfulPauseConfig> = context.resolveDataStore.data.map { prefs ->
        decodeMindfulPauseConfig(prefs[Keys.MINDFUL_PAUSE_CONFIG_JSON])
    }

    val dailyQuotaRulesFlow: Flow<List<AppUsageQuotaRule>> = context.resolveDataStore.data.map { prefs ->
        decodeQuotaRules(prefs[Keys.DAILY_QUOTA_RULES_JSON])
    }

    val contentBlockerConfigFlow: Flow<ContentBlockerConfig> = context.resolveDataStore.data.map { prefs ->
        decodeContentBlockerConfig(prefs[Keys.CONTENT_BLOCKER_CONFIG_JSON])
    }

    val dnsBlockedCountFlow: Flow<Int> = context.resolveDataStore.data.map { prefs ->
        (prefs[Keys.DNS_BLOCKED_COUNT] ?: 0).coerceAtLeast(0)
    }

    val accountabilityPartnerConfigFlow: Flow<AccountabilityPartnerConfig> = context.resolveDataStore.data.map { prefs ->
        val hasPin = !prefs[Keys.PARTNER_PIN_VERIFIER].isNullOrBlank() &&
            !prefs[Keys.PARTNER_PIN_SALT].isNullOrBlank()
        AccountabilityPartnerConfig(
            enabled = prefs[Keys.PARTNER_ENABLED] ?: false,
            partnerName = prefs[Keys.PARTNER_NAME].orEmpty(),
            hasPartnerPin = hasPin,
            requireForStrictUnlock = prefs[Keys.PARTNER_REQUIRE_STRICT_UNLOCK] ?: true,
            requireForQuotaOverride = prefs[Keys.PARTNER_REQUIRE_QUOTA_OVERRIDE] ?: true
        )
    }

    val interceptedAppCountsFlow: Flow<Map<String, Int>> = context.resolveDataStore.data.map { prefs ->
        decodeIntMap(prefs[Keys.INTERCEPTED_APP_COUNTS_JSON])
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

    val syncInstantLockToDevicesFlow: Flow<Boolean> = context.resolveDataStore.data.map { prefs ->
        prefs[Keys.SYNC_INSTANT_LOCK_DEVICES] ?: true
    }

    val syncDelayedLockToDevicesFlow: Flow<Boolean> = context.resolveDataStore.data.map { prefs ->
        prefs[Keys.SYNC_DELAYED_LOCK_DEVICES] ?: true
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

    suspend fun setSyncInstantLockToDevices(enabled: Boolean) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.SYNC_INSTANT_LOCK_DEVICES] = enabled
        }
    }

    suspend fun setSyncDelayedLockToDevices(enabled: Boolean) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.SYNC_DELAYED_LOCK_DEVICES] = enabled
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
            prefs[Keys.FLIP_WEATHER_USE_FAHRENHEIT] = settings.weatherUseFahrenheit
            prefs[Keys.FLIP_WEATHER_AUTO_DETECT] = settings.weatherAutoDetect
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
            prefs[Keys.FLIP_POMODORO_ENABLED] = settings.pomodoroEnabled
            prefs[Keys.FLIP_POMODORO_FOCUS_MINUTES] = settings.pomodoroFocusMinutes.coerceIn(5, 90)
            prefs[Keys.FLIP_POMODORO_BREAK_MINUTES] = settings.pomodoroBreakMinutes.coerceIn(1, 30)
            prefs[Keys.FLIP_AMBIENT_SOUND_TYPE] = settings.ambientSoundType.storageKey
            prefs[Keys.FLIP_AMBIENT_VOLUME] = settings.ambientVolume.coerceIn(0.05f, 1f)
            prefs[Keys.FLIP_OLED_BURN_IN_PROTECTION] = settings.oledBurnInProtection
            prefs[Keys.FLIP_SYNC_TIMER_WITH_FOCUS_LOCK] = settings.syncFlipTimerWithFocusLock
            prefs[Keys.FLIP_PLAY_AMBIENT_DURING_FOCUS_LOCK] = settings.playAmbientSoundDuringFocusLock
            prefs[Keys.FLIP_AMBIENT_PLAY_IN_BACKGROUND] = settings.ambientPlayInBackground
            prefs[Keys.FLIP_CUSTOM_LAYOUT_JSON] = encodeFlipCustomLayout(settings.customLayout)
            prefs[Keys.FLIP_SETTINGS_UPDATED_WALL_MS] = settings.updatedAtWallMs
        }
    }

    suspend fun saveFlipClockCustomLayout(customLayout: FlipClockCustomLayout) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.FLIP_CUSTOM_LAYOUT_JSON] = encodeFlipCustomLayout(customLayout)
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
            prefs[Keys.FLIP_WEATHER_USE_FAHRENHEIT] = false
            prefs[Keys.FLIP_WEATHER_AUTO_DETECT] = false
            prefs[Keys.FLIP_SLEEP_TIMER_MINUTES] = 0
            prefs[Keys.FLIP_CLOCK_TICKER_SOUND] = false
            prefs[Keys.FLIP_CUSTOM_TAG] = ""
            prefs[Keys.FLIP_AUTO_REPEAT] = false
            prefs[Keys.FLIP_ZOOM_FRACTION] = FlipClockSettings.DEFAULT_ZOOM_FRACTION
            prefs[Keys.FLIP_POMODORO_ENABLED] = false
            prefs[Keys.FLIP_POMODORO_FOCUS_MINUTES] = 25
            prefs[Keys.FLIP_POMODORO_BREAK_MINUTES] = 5
            prefs[Keys.FLIP_AMBIENT_SOUND_TYPE] = AmbientSoundType.OFF.storageKey
            prefs[Keys.FLIP_AMBIENT_VOLUME] = 0.45f
            prefs[Keys.FLIP_OLED_BURN_IN_PROTECTION] = false
            prefs[Keys.FLIP_SYNC_TIMER_WITH_FOCUS_LOCK] = false
            prefs[Keys.FLIP_PLAY_AMBIENT_DURING_FOCUS_LOCK] = false
            prefs[Keys.FLIP_AMBIENT_PLAY_IN_BACKGROUND] = false
            prefs.remove(Keys.FLIP_CUSTOM_LAYOUT_JSON)
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

    val restAuthSessionFlow: Flow<StoredRestAuthSession?> = context.resolveDataStore.data.map { prefs ->
        mapStoredRestAuthSession(prefs)
    }

    suspend fun getRestAuthSessionSnapshot(): StoredRestAuthSession? =
        mapStoredRestAuthSession(context.resolveDataStore.data.first())

    suspend fun saveRestAuthSession(session: StoredRestAuthSession) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.REST_AUTH_UID] = session.uid
            prefs[Keys.REST_AUTH_ID_TOKEN] = session.idToken
            prefs[Keys.REST_AUTH_REFRESH_TOKEN] = session.refreshToken
            prefs[Keys.REST_AUTH_EXPIRES_AT_WALL_MS] = session.expiresAtWallMs
            prefs[Keys.REST_AUTH_EMAIL] = session.email
            prefs[Keys.REST_AUTH_DISPLAY_NAME] = session.displayName
            prefs[Keys.REST_AUTH_PHOTO_URL] = session.photoUrl
        }
    }

    suspend fun clearRestAuthSession() {
        context.resolveDataStore.edit { prefs ->
            prefs.remove(Keys.REST_AUTH_UID)
            prefs.remove(Keys.REST_AUTH_ID_TOKEN)
            prefs.remove(Keys.REST_AUTH_REFRESH_TOKEN)
            prefs.remove(Keys.REST_AUTH_EXPIRES_AT_WALL_MS)
            prefs.remove(Keys.REST_AUTH_EMAIL)
            prefs.remove(Keys.REST_AUTH_DISPLAY_NAME)
            prefs.remove(Keys.REST_AUTH_PHOTO_URL)
        }
    }

    private fun mapStoredRestAuthSession(prefs: Preferences): StoredRestAuthSession? {
        val uid = prefs[Keys.REST_AUTH_UID]?.trim().orEmpty()
        val refreshToken = prefs[Keys.REST_AUTH_REFRESH_TOKEN]?.trim().orEmpty()
        if (uid.isEmpty() || refreshToken.isEmpty()) return null
        return StoredRestAuthSession(
            uid = uid,
            idToken = prefs[Keys.REST_AUTH_ID_TOKEN].orEmpty(),
            refreshToken = refreshToken,
            expiresAtWallMs = prefs[Keys.REST_AUTH_EXPIRES_AT_WALL_MS] ?: 0L,
            email = prefs[Keys.REST_AUTH_EMAIL].orEmpty(),
            displayName = prefs[Keys.REST_AUTH_DISPLAY_NAME].orEmpty(),
            photoUrl = prefs[Keys.REST_AUTH_PHOTO_URL].orEmpty()
        )
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
            weatherUseFahrenheit = prefs[Keys.FLIP_WEATHER_USE_FAHRENHEIT] ?: false,
            weatherAutoDetect = prefs[Keys.FLIP_WEATHER_AUTO_DETECT] ?: false,
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
            pomodoroEnabled = prefs[Keys.FLIP_POMODORO_ENABLED] ?: false,
            pomodoroFocusMinutes = (prefs[Keys.FLIP_POMODORO_FOCUS_MINUTES] ?: 25).coerceIn(5, 90),
            pomodoroBreakMinutes = (prefs[Keys.FLIP_POMODORO_BREAK_MINUTES] ?: 5).coerceIn(1, 30),
            ambientSoundType = AmbientSoundType.fromKey(prefs[Keys.FLIP_AMBIENT_SOUND_TYPE]),
            ambientVolume = (prefs[Keys.FLIP_AMBIENT_VOLUME] ?: 0.45f).coerceIn(0.05f, 1f),
            oledBurnInProtection = prefs[Keys.FLIP_OLED_BURN_IN_PROTECTION] ?: false,
            syncFlipTimerWithFocusLock = prefs[Keys.FLIP_SYNC_TIMER_WITH_FOCUS_LOCK] ?: false,
            playAmbientSoundDuringFocusLock = prefs[Keys.FLIP_PLAY_AMBIENT_DURING_FOCUS_LOCK] ?: false,
            ambientPlayInBackground = prefs[Keys.FLIP_AMBIENT_PLAY_IN_BACKGROUND] ?: false,
            customLayout = decodeFlipCustomLayout(prefs[Keys.FLIP_CUSTOM_LAYOUT_JSON]),
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

    // --- v1.0.9 Persistence Methods ---

    suspend fun setAppThemeId(themeId: ResolveThemeId) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.APP_THEME_ID] = themeId.storageKey
        }
    }

    suspend fun setDailyFocusGoalMinutes(minutes: Int) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.DAILY_FOCUS_GOAL_MINUTES] = minutes.coerceIn(15, 720)
        }
    }

    suspend fun setShowSetupTips(show: Boolean) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.SHOW_SETUP_TIPS] = show
        }
    }

    suspend fun getExcludedScreenTimePackagesSnapshot(): Set<String> =
        excludedScreenTimePackagesFlow.first()

    suspend fun setExcludedScreenTimePackages(packages: Set<String>) {
        val clean = packages.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.EXCLUDED_SCREEN_TIME_PACKAGES_JSON] = encodeIdList(clean)
        }
    }

    suspend fun saveCustomFocusProfile(profile: FocusProfile) {
        context.resolveDataStore.edit { prefs ->
            val current = decodeFocusProfiles(prefs[Keys.CUSTOM_FOCUS_PROFILES_JSON]).toMutableList()
            val idx = current.indexOfFirst { it.profileId == profile.profileId }
            val saved = profile.copy(builtIn = false)
            if (idx >= 0) current[idx] = saved else current.add(saved)
            prefs[Keys.CUSTOM_FOCUS_PROFILES_JSON] = encodeFocusProfiles(current)
        }
    }

    suspend fun deleteCustomFocusProfile(profileId: String) {
        context.resolveDataStore.edit { prefs ->
            val updated = decodeFocusProfiles(prefs[Keys.CUSTOM_FOCUS_PROFILES_JSON])
                .filterNot { it.profileId == profileId }
            prefs[Keys.CUSTOM_FOCUS_PROFILES_JSON] = encodeFocusProfiles(updated)
        }
    }

    suspend fun isAntiBypassStrictGuardEnabledSnapshot(): Boolean =
        antiBypassStrictGuardFlow.first()

    suspend fun setAntiBypassStrictGuard(enabled: Boolean) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.ANTI_BYPASS_STRICT_GUARD] = enabled
        }
    }

    suspend fun setEmergencyCooldownSeconds(seconds: Int) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.EMERGENCY_COOLDOWN_SECONDS] = seconds.coerceIn(0, 60)
        }
    }

    suspend fun getMindfulPauseConfigSnapshot(): MindfulPauseConfig =
        mindfulPauseConfigFlow.first()

    suspend fun saveMindfulPauseConfig(config: MindfulPauseConfig) {
        val cleanPkgs = config.targetPackages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val normalized = config.copy(
            delaySeconds = config.clampedDelaySeconds,
            passMinutes = config.clampedPassMinutes,
            targetPackages = cleanPkgs
        )
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.MINDFUL_PAUSE_CONFIG_JSON] = encodeMindfulPauseConfig(normalized)
        }
    }

    suspend fun grantMindfulPass(packageName: String, durationMs: Long, nowWallMs: Long) {
        context.resolveDataStore.edit { prefs ->
            val map = decodeLongMap(prefs[Keys.MINDFUL_PASS_EXPIRATIONS_JSON].orEmpty()).toMutableMap()
            map[packageName] = nowWallMs + durationMs
            // Prune expired passes cleanly
            val pruned = map.filterValues { it > nowWallMs }
            prefs[Keys.MINDFUL_PASS_EXPIRATIONS_JSON] = encodeLongMap(pruned)
        }
    }

    suspend fun hasActiveMindfulPass(packageName: String, nowWallMs: Long): Boolean {
        val raw = context.resolveDataStore.data.first()[Keys.MINDFUL_PASS_EXPIRATIONS_JSON].orEmpty()
        val map = decodeLongMap(raw)
        return (map[packageName] ?: 0L) > nowWallMs
    }

    suspend fun getDailyQuotaRulesSnapshot(): List<AppUsageQuotaRule> =
        dailyQuotaRulesFlow.first()

    suspend fun saveDailyQuotaRule(rule: AppUsageQuotaRule) {
        val normalized = rule.copy(dailyLimitMinutes = rule.dailyLimitMinutes.coerceIn(5, 600))
        context.resolveDataStore.edit { prefs ->
            val list = decodeQuotaRules(prefs[Keys.DAILY_QUOTA_RULES_JSON]).toMutableList()
            val idx = list.indexOfFirst { it.packageName == normalized.packageName }
            if (idx >= 0) list[idx] = normalized else list.add(normalized)
            prefs[Keys.DAILY_QUOTA_RULES_JSON] = encodeQuotaRules(list)
        }
    }

    suspend fun deleteDailyQuotaRule(packageName: String) {
        context.resolveDataStore.edit { prefs ->
            val updated = decodeQuotaRules(prefs[Keys.DAILY_QUOTA_RULES_JSON])
                .filterNot { it.packageName == packageName }
            prefs[Keys.DAILY_QUOTA_RULES_JSON] = encodeQuotaRules(updated)
        }
    }

    suspend fun grantQuotaOverrideForDay(packageName: String, epochDay: Long) {
        context.resolveDataStore.edit { prefs ->
            val map = decodeLongMap(prefs[Keys.QUOTA_OVERRIDE_DAYS_JSON].orEmpty()).toMutableMap()
            map[packageName] = epochDay
            val pruned = map.filterValues { it >= epochDay }
            prefs[Keys.QUOTA_OVERRIDE_DAYS_JSON] = encodeLongMap(pruned)
        }
    }

    suspend fun isQuotaOverriddenForDay(packageName: String, epochDay: Long): Boolean {
        val raw = context.resolveDataStore.data.first()[Keys.QUOTA_OVERRIDE_DAYS_JSON].orEmpty()
        val map = decodeLongMap(raw)
        return (map[packageName] ?: -1L) == epochDay
    }

    suspend fun getContentBlockerConfigSnapshot(): ContentBlockerConfig =
        contentBlockerConfigFlow.first()

    suspend fun saveContentBlockerConfig(config: ContentBlockerConfig) {
        val cleanDomains = config.blockedDomains
            .map { it.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/') }
            .filter { it.contains('.') && it.length >= 4 }
            .distinct()
        val normalized = config.copy(blockedDomains = cleanDomains)
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.CONTENT_BLOCKER_CONFIG_JSON] = encodeContentBlockerConfig(normalized)
        }
    }

    suspend fun incrementInterceptedAppCount(packageName: String) {
        if (packageName.isBlank()) return
        context.resolveDataStore.edit { prefs ->
            val map = decodeIntMap(prefs[Keys.INTERCEPTED_APP_COUNTS_JSON]).toMutableMap()
            map[packageName] = (map[packageName] ?: 0) + 1
            prefs[Keys.INTERCEPTED_APP_COUNTS_JSON] = encodeIntMap(map)
        }
    }

    suspend fun clearInterceptedAppCounts() {
        context.resolveDataStore.edit { prefs ->
            prefs.remove(Keys.INTERCEPTED_APP_COUNTS_JSON)
        }
    }

    suspend fun getPartnerPinRecord(): StoredPartnerPinRecord? {
        val prefs = context.resolveDataStore.data.first()
        val verifier = prefs[Keys.PARTNER_PIN_VERIFIER] ?: return null
        val salt = prefs[Keys.PARTNER_PIN_SALT] ?: return null
        return StoredPartnerPinRecord(
            verifierBase64 = verifier,
            saltBase64 = salt,
            ivBase64 = prefs[Keys.PARTNER_PIN_IV],
            isKeystoreWrapped = prefs[Keys.PARTNER_PIN_KEYSTORE_WRAPPED] ?: false
        )
    }

    suspend fun savePartnerPinRecord(record: StoredPartnerPinRecord) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.PARTNER_PIN_VERIFIER] = record.verifierBase64
            prefs[Keys.PARTNER_PIN_SALT] = record.saltBase64
            if (record.ivBase64 != null) {
                prefs[Keys.PARTNER_PIN_IV] = record.ivBase64
            } else {
                prefs.remove(Keys.PARTNER_PIN_IV)
            }
            prefs[Keys.PARTNER_PIN_KEYSTORE_WRAPPED] = record.isKeystoreWrapped
            prefs[Keys.PARTNER_PIN_FAILED_ATTEMPTS] = 0
            prefs[Keys.PARTNER_PIN_LOCKOUT_UNTIL_WALL_MS] = 0L
        }
    }

    suspend fun clearPartnerPinRecord() {
        context.resolveDataStore.edit { prefs ->
            prefs.remove(Keys.PARTNER_PIN_VERIFIER)
            prefs.remove(Keys.PARTNER_PIN_SALT)
            prefs.remove(Keys.PARTNER_PIN_IV)
            prefs.remove(Keys.PARTNER_PIN_KEYSTORE_WRAPPED)
            prefs[Keys.PARTNER_ENABLED] = false
        }
    }

    suspend fun saveAccountabilityPartnerMetadata(
        enabled: Boolean,
        partnerName: String,
        requireForStrictUnlock: Boolean,
        requireForQuotaOverride: Boolean
    ) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.PARTNER_ENABLED] = enabled
            prefs[Keys.PARTNER_NAME] = partnerName
            prefs[Keys.PARTNER_REQUIRE_STRICT_UNLOCK] = requireForStrictUnlock
            prefs[Keys.PARTNER_REQUIRE_QUOTA_OVERRIDE] = requireForQuotaOverride
        }
    }

    suspend fun getPartnerPinThrottleState(): RecoveryThrottleState {
        val prefs = context.resolveDataStore.data.first()
        return RecoveryThrottleState(
            failedAttempts = prefs[Keys.PARTNER_PIN_FAILED_ATTEMPTS] ?: 0,
            lockoutUntilWallMs = prefs[Keys.PARTNER_PIN_LOCKOUT_UNTIL_WALL_MS] ?: 0L
        )
    }

    suspend fun updatePartnerPinThrottleState(failedAttempts: Int, lockoutUntilWallMs: Long) {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.PARTNER_PIN_FAILED_ATTEMPTS] = failedAttempts
            prefs[Keys.PARTNER_PIN_LOCKOUT_UNTIL_WALL_MS] = lockoutUntilWallMs
        }
    }

    suspend fun updateFlipClockProductivitySettings(
        pomodoroEnabled: Boolean? = null,
        pomodoroFocusMinutes: Int? = null,
        pomodoroBreakMinutes: Int? = null,
        ambientSoundType: AmbientSoundType? = null,
        ambientVolume: Float? = null,
        oledBurnInProtection: Boolean? = null,
        updatedAtWallMs: Long
    ) {
        context.resolveDataStore.edit { prefs ->
            pomodoroEnabled?.let { prefs[Keys.FLIP_POMODORO_ENABLED] = it }
            pomodoroFocusMinutes?.let { prefs[Keys.FLIP_POMODORO_FOCUS_MINUTES] = it.coerceIn(5, 90) }
            pomodoroBreakMinutes?.let { prefs[Keys.FLIP_POMODORO_BREAK_MINUTES] = it.coerceIn(1, 30) }
            ambientSoundType?.let { prefs[Keys.FLIP_AMBIENT_SOUND_TYPE] = it.storageKey }
            ambientVolume?.let { prefs[Keys.FLIP_AMBIENT_VOLUME] = it.coerceIn(0.05f, 1f) }
            oledBurnInProtection?.let { prefs[Keys.FLIP_OLED_BURN_IN_PROTECTION] = it }
            prefs[Keys.FLIP_SETTINGS_UPDATED_WALL_MS] = updatedAtWallMs
        }
    }

    private fun decodeFocusProfiles(raw: String?): List<FocusProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("profileId")
                    val name = obj.optString("name")
                    if (id.isBlank() || name.isBlank()) continue
                    val pkgs = mutableListOf<String>()
                    val pkgsArr = obj.optJSONArray("allowedPackages")
                    if (pkgsArr != null) {
                        for (j in 0 until pkgsArr.length()) {
                            pkgsArr.optString(j).takeIf { it.isNotBlank() }?.let { pkgs.add(it) }
                        }
                    }
                    add(
                        FocusProfile(
                            profileId = id,
                            name = name,
                            subtitle = obj.optString("subtitle"),
                            defaultDurationMinutes = obj.optLong("defaultDurationMinutes", 30L).coerceAtLeast(1L),
                            strictMode = obj.optBoolean("strictMode", false),
                            allowSelectedApps = obj.optBoolean("allowSelectedApps", true),
                            phoneAccessEnabled = obj.optBoolean("phoneAccessEnabled", true),
                            showFlipClockShortcut = obj.optBoolean("showFlipClockShortcut", false),
                            allowedPackages = pkgs,
                            builtIn = false
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeFlipCustomLayout(layout: FlipClockCustomLayout): String =
        JSONObject().apply {
            put("portrait", encodeOrientationLayout(layout.portrait))
            put("landscape", encodeOrientationLayout(layout.landscape))
        }.toString()

    private fun encodeOrientationLayout(layout: FlipClockOrientationLayout): JSONObject =
        JSONObject().apply {
            put("enabled", layout.enabled)
            put("hourX", layout.hourPlacement.offsetXFrac.toDouble())
            put("hourY", layout.hourPlacement.offsetYFrac.toDouble())
            put("minX", layout.minutePlacement.offsetXFrac.toDouble())
            put("minY", layout.minutePlacement.offsetYFrac.toDouble())
            put("ampmX", layout.amPmPlacement.offsetXFrac.toDouble())
            put("ampmY", layout.amPmPlacement.offsetYFrac.toDouble())
            put("secX", layout.secondsPlacement.offsetXFrac.toDouble())
            put("secY", layout.secondsPlacement.offsetYFrac.toDouble())
            put("dateX", layout.datePlacement.offsetXFrac.toDouble())
            put("dateY", layout.datePlacement.offsetYFrac.toDouble())
            put("weekX", layout.weekdayPlacement.offsetXFrac.toDouble())
            put("weekY", layout.weekdayPlacement.offsetYFrac.toDouble())
            put("weatherX", layout.weatherPlacement.offsetXFrac.toDouble())
            put("weatherY", layout.weatherPlacement.offsetYFrac.toDouble())
            put("batX", layout.batteryPlacement.offsetXFrac.toDouble())
            put("batY", layout.batteryPlacement.offsetYFrac.toDouble())
            put("hourScale", layout.hourScale.toDouble())
            put("minuteScale", layout.minuteScale.toDouble())
            put("amPmAlign", layout.amPmAlign.name)
            put("secondsAlign", layout.secondsAlign.name)
            put("weekdayStyle", layout.weekdayStyle.name)
            put("batteryStyle", layout.batteryStyle.name)
            put("weatherStyle", layout.weatherStyle.name)
        }

    private fun decodeFlipCustomLayout(raw: String?): FlipClockCustomLayout {
        if (raw.isNullOrBlank()) return FlipClockCustomLayout()
        return runCatching {
            val root = JSONObject(raw)
            FlipClockCustomLayout(
                portrait = decodeOrientationLayout(
                    root.optJSONObject("portrait"),
                    FlipClockOrientationLayout.defaultPortrait()
                ),
                landscape = decodeOrientationLayout(
                    root.optJSONObject("landscape"),
                    FlipClockOrientationLayout.defaultLandscape()
                )
            )
        }.getOrDefault(FlipClockCustomLayout())
    }

    private fun decodeOrientationLayout(
        obj: JSONObject?,
        fallback: FlipClockOrientationLayout
    ): FlipClockOrientationLayout {
        if (obj == null) return fallback
        fun placement(xKey: String, yKey: String, def: FlipElementPlacement): FlipElementPlacement =
            FlipElementPlacement(
                offsetXFrac = obj.optDouble(xKey, def.offsetXFrac.toDouble()).toFloat().coerceIn(-0.44f, 0.44f),
                offsetYFrac = obj.optDouble(yKey, def.offsetYFrac.toDouble()).toFloat().coerceIn(-0.44f, 0.44f)
            )
        fun <T : Enum<T>> enumOrDefault(key: String, entries: Array<T>, def: T): T {
            val name = obj.optString(key)
            return entries.firstOrNull { it.name == name } ?: def
        }
        return FlipClockOrientationLayout(
            enabled = obj.optBoolean("enabled", fallback.enabled),
            hourPlacement = placement("hourX", "hourY", fallback.hourPlacement),
            minutePlacement = placement("minX", "minY", fallback.minutePlacement),
            amPmPlacement = placement("ampmX", "ampmY", fallback.amPmPlacement),
            secondsPlacement = placement("secX", "secY", fallback.secondsPlacement),
            datePlacement = placement("dateX", "dateY", fallback.datePlacement),
            weekdayPlacement = placement("weekX", "weekY", fallback.weekdayPlacement),
            weatherPlacement = placement("weatherX", "weatherY", fallback.weatherPlacement),
            batteryPlacement = placement("batX", "batY", fallback.batteryPlacement),
            hourScale = obj.optDouble("hourScale", fallback.hourScale.toDouble()).toFloat()
                .coerceIn(FlipClockOrientationLayout.MIN_DIGIT_SCALE, FlipClockOrientationLayout.MAX_DIGIT_SCALE),
            minuteScale = obj.optDouble("minuteScale", fallback.minuteScale.toDouble()).toFloat()
                .coerceIn(FlipClockOrientationLayout.MIN_DIGIT_SCALE, FlipClockOrientationLayout.MAX_DIGIT_SCALE),
            amPmAlign = enumOrDefault("amPmAlign", FlipClockElementAlign.entries.toTypedArray(), fallback.amPmAlign),
            secondsAlign = enumOrDefault("secondsAlign", FlipClockElementAlign.entries.toTypedArray(), fallback.secondsAlign),
            weekdayStyle = enumOrDefault("weekdayStyle", FlipWeekdayStyle.entries.toTypedArray(), fallback.weekdayStyle),
            batteryStyle = enumOrDefault("batteryStyle", FlipBatteryStyle.entries.toTypedArray(), fallback.batteryStyle),
            weatherStyle = enumOrDefault("weatherStyle", FlipWeatherStyle.entries.toTypedArray(), fallback.weatherStyle)
        )
    }

    private fun encodeFocusProfiles(profiles: List<FocusProfile>): String {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(JSONObject().apply {
                put("profileId", p.profileId)
                put("name", p.name)
                put("subtitle", p.subtitle)
                put("defaultDurationMinutes", p.defaultDurationMinutes)
                put("strictMode", p.strictMode)
                put("allowSelectedApps", p.allowSelectedApps)
                put("phoneAccessEnabled", p.phoneAccessEnabled)
                put("showFlipClockShortcut", p.showFlipClockShortcut)
                put("allowedPackages", JSONArray(p.allowedPackages))
            })
        }
        return arr.toString()
    }

    private fun decodeMindfulPauseConfig(raw: String?): MindfulPauseConfig {
        if (raw.isNullOrBlank()) return MindfulPauseConfig()
        return runCatching {
            val obj = JSONObject(raw)
            val pkgsArr = obj.optJSONArray("targetPackages")
            val pkgs = buildList {
                if (pkgsArr != null) {
                    for (i in 0 until pkgsArr.length()) {
                        pkgsArr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            }
            MindfulPauseConfig(
                enabled = obj.optBoolean("enabled", false),
                delaySeconds = obj.optInt("delaySeconds", 10).coerceIn(5, 15),
                passMinutes = obj.optInt("passMinutes", 5).coerceIn(1, 30),
                targetPackages = pkgs
            )
        }.getOrDefault(MindfulPauseConfig())
    }

    private fun encodeMindfulPauseConfig(cfg: MindfulPauseConfig): String = JSONObject().apply {
        put("enabled", cfg.enabled)
        put("delaySeconds", cfg.clampedDelaySeconds)
        put("passMinutes", cfg.clampedPassMinutes)
        put("targetPackages", JSONArray(cfg.targetPackages))
    }.toString()

    private fun decodeQuotaRules(raw: String?): List<AppUsageQuotaRule> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val pkg = obj.optString("packageName")
                    if (pkg.isBlank()) continue
                    add(
                        AppUsageQuotaRule(
                            packageName = pkg,
                            appName = obj.optString("appName", pkg),
                            dailyLimitMinutes = obj.optInt("dailyLimitMinutes", 30).coerceIn(5, 600),
                            enabled = obj.optBoolean("enabled", true)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeQuotaRules(rules: List<AppUsageQuotaRule>): String {
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(JSONObject().apply {
                put("packageName", r.packageName)
                put("appName", r.appName)
                put("dailyLimitMinutes", r.dailyLimitMinutes)
                put("enabled", r.enabled)
            })
        }
        return arr.toString()
    }

    private fun decodeContentBlockerConfig(raw: String?): ContentBlockerConfig {
        if (raw.isNullOrBlank()) return ContentBlockerConfig()
        return runCatching {
            val obj = JSONObject(raw)
            val domainsArr = obj.optJSONArray("blockedDomains")
            val domains = buildList {
                if (domainsArr != null) {
                    for (i in 0 until domainsArr.length()) {
                        domainsArr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            }
            ContentBlockerConfig(
                enabled = obj.optBoolean("enabled", false),
                blockYouTubeShorts = obj.optBoolean("blockYouTubeShorts", true),
                blockInstagramReels = obj.optBoolean("blockInstagramReels", true),
                blockedDomains = domains,
                onlyDuringFocusLock = obj.optBoolean("onlyDuringFocusLock", false),
                vpnDnsBlockerEnabled = obj.optBoolean("vpnDnsBlockerEnabled", false),
                blockAdultDomains = obj.optBoolean("blockAdultDomains", false),
                blockShortVideoDomains = obj.optBoolean("blockShortVideoDomains", false),
                blockSocialDomains = obj.optBoolean("blockSocialDomains", false)
            )
        }.getOrDefault(ContentBlockerConfig())
    }

    private fun encodeContentBlockerConfig(cfg: ContentBlockerConfig): String = JSONObject().apply {
        put("enabled", cfg.enabled)
        put("blockYouTubeShorts", cfg.blockYouTubeShorts)
        put("blockInstagramReels", cfg.blockInstagramReels)
        put("blockedDomains", JSONArray(cfg.blockedDomains))
        put("onlyDuringFocusLock", cfg.onlyDuringFocusLock)
        put("vpnDnsBlockerEnabled", cfg.vpnDnsBlockerEnabled)
        put("blockAdultDomains", cfg.blockAdultDomains)
        put("blockShortVideoDomains", cfg.blockShortVideoDomains)
        put("blockSocialDomains", cfg.blockSocialDomains)
    }.toString()

    suspend fun getPendingLockCommandsRaw(): String? =
        context.resolveDataStore.data.first()[Keys.PENDING_LOCK_COMMANDS_JSON]

    suspend fun savePendingLockCommandsRaw(json: String?) {
        context.resolveDataStore.edit { prefs ->
            if (json.isNullOrBlank()) {
                prefs.remove(Keys.PENDING_LOCK_COMMANDS_JSON)
            } else {
                prefs[Keys.PENDING_LOCK_COMMANDS_JSON] = json
            }
        }
    }

    suspend fun incrementDnsBlockedCount() {
        context.resolveDataStore.edit { prefs ->
            val current = (prefs[Keys.DNS_BLOCKED_COUNT] ?: 0).coerceAtLeast(0)
            prefs[Keys.DNS_BLOCKED_COUNT] = current + 1
        }
    }

    suspend fun resetDnsBlockedCount() {
        context.resolveDataStore.edit { prefs ->
            prefs[Keys.DNS_BLOCKED_COUNT] = 0
        }
    }

    private fun decodeIntMap(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, json.optInt(key, 0))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeIntMap(map: Map<String, Int>): String {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        return json.toString()
    }
}
