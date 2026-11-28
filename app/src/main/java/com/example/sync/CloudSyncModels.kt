package com.example.sync

import android.os.Build
import androidx.annotation.Keep
import com.example.BuildConfig
import com.example.domain.model.LockMode
import com.example.domain.model.ScheduleType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val CURRENT_SYNC_SCHEMA_VERSION = 1L

data class CloudAuthSession(
    val uid: String,
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null
)

class CloudSyncException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    enum class Kind {
        UNAUTHENTICATED,
        PERMISSION_DENIED,
        UNAVAILABLE,
        QUOTA_EXCEEDED,
        OTHER
    }
}

@Keep
@IgnoreExtraProperties
data class CloudUserProfile(
    val userId: String = "",
    val schemaVersion: Long = CURRENT_SYNC_SCHEMA_VERSION,
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val avatarBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudDeviceInfo(
    @DocumentId val deviceId: String = "",
    val userId: String = "",
    val schemaVersion: Long = CURRENT_SYNC_SCHEMA_VERSION,
    val deviceName: String = "",
    val platform: String = "android",
    val androidVersion: Long = Build.VERSION.SDK_INT.toLong(),
    val appVersion: String = BuildConfig.VERSION_NAME,
    val syncEnabled: Boolean = true,
    val activeLockMode: String = "",
    val activeLockExpiresWallMs: Long = 0L,
    val activeLockFreeUseWallMs: Long = 0L,
    val activeLockUpdatedWallMs: Long = 0L,
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudLockCommand(
    val userId: String = "",
    val schemaVersion: Long = CURRENT_SYNC_SCHEMA_VERSION,
    val commandId: String = "",
    val originDeviceId: String = "",
    val originDeviceName: String = "",
    val commandAction: String = "START",
    val targetDeviceId: String = "",
    val extendMinutes: Long = 0L,
    val mode: String = LockMode.INSTANT.name,
    val freeUseDurationMinutes: Long = 0L,
    val lockDurationMinutes: Long = 0L,
    val issuedAtWallMs: Long = 0L,
    val intendedStartWallMs: Long = 0L,
    val freeUseDeadlineWallMs: Long = 0L,
    val lockExpirationWallMs: Long = 0L,
    val scheduleId: String = "",
    val occurrenceId: String = "",
    val strictMode: Boolean = false,
    val showFlipClockShortcut: Boolean = false,
    val allowSelectedApps: Boolean = true,
    val phoneAccessEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolveLockMode(): LockMode {
        return LockMode.entries.firstOrNull { it.name == mode } ?: LockMode.INSTANT
    }
}

@Keep
@IgnoreExtraProperties
data class CloudDelayedPreset(
    val userId: String = "",
    val schemaVersion: Long = CURRENT_SYNC_SCHEMA_VERSION,
    val presetId: String = "",
    val name: String = "",
    val freeUseMinutes: Long = 20L,
    val lockDurationMinutes: Long = 30L,
    val originDeviceId: String = "",
    val updatedAtWallMs: Long = 0L,
    val deleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudScheduleRecord(
    val userId: String = "",
    val schemaVersion: Long = CURRENT_SYNC_SCHEMA_VERSION,
    val scheduleId: String = "",
    val name: String = "",
    val type: String = ScheduleType.DAILY.name,
    val oneTimeDateEpochDay: Long = -1L,
    val weekdaysMask: Int = 0,
    val startHour: Int = 0,
    val startMinute: Int = 0,
    val lockDurationMinutes: Long = 60L,
    val enabled: Boolean = true,
    val syncAcrossDevices: Boolean = true,
    val strictMode: Boolean = false,
    val allowSelectedApps: Boolean = true,
    val showFlipClockShortcut: Boolean = false,
    val originDeviceId: String = "",
    val updatedAtWallMs: Long = 0L,
    val deleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolveScheduleType(): ScheduleType {
        return ScheduleType.entries.firstOrNull { it.name == type } ?: ScheduleType.DAILY
    }
}

@Keep
@IgnoreExtraProperties
data class CloudLockProfileSettings(
    val userId: String = "",
    val schemaVersion: Long = CURRENT_SYNC_SCHEMA_VERSION,
    val profileMode: String = LockMode.INSTANT.name,
    val strictMode: Boolean = false,
    val allowSelectedApps: Boolean = true,
    val showFlipClockShortcut: Boolean = false,
    val allowedPackages: List<String> = emptyList(),
    val originDeviceId: String = "",
    val updatedAtWallMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolveLockMode(): LockMode {
        return LockMode.entries.firstOrNull { it.name == profileMode } ?: LockMode.INSTANT
    }
}

@Keep
@IgnoreExtraProperties
data class CloudFlipClockSettings(
    val userId: String = "",
    val schemaVersion: Long = CURRENT_SYNC_SCHEMA_VERSION,
    val docId: String = "flip_clock_settings",
    val use24HourFormat: Boolean = false,
    val useSystemTimeFormat: Boolean = false,
    val hourLeadingZero: Boolean = false,
    val dateFormatPattern: String = "dd/MM/yy",
    val keepScreenAwake: Boolean = false,
    val showDate: Boolean = false,
    val showWeekday: Boolean = true,
    val showSeconds: Boolean = true,
    val showFlipAxis: Boolean = true,
    val showUpcomingAlarm: Boolean = false,
    val showBattery: Boolean = false,
    val showWeather: Boolean = false,
    val weatherLocationQuery: String = "",
    val weatherUseFahrenheit: Boolean = false,
    val weatherAutoDetect: Boolean = false,
    val clockTickerSoundEnabled: Boolean = false,
    val customTag: String = "",
    val autoRepeat: Boolean = false,
    val zoomFraction: Double = 1.0,
    val pomodoroEnabled: Boolean = false,
    val pomodoroFocusMinutes: Int = 25,
    val pomodoroBreakMinutes: Int = 5,
    val ambientSoundType: String = "off",
    val ambientVolume: Double = 0.55,
    val oledBurnInProtection: Boolean = false,
    val syncFlipTimerWithFocusLock: Boolean = false,
    val playAmbientSoundDuringFocusLock: Boolean = false,
    val ambientPlayInBackground: Boolean = false,
    val originDeviceId: String = "",
    val updatedAtWallMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudFlipTimerPreset(
    val userId: String = "",
    val schemaVersion: Long = CURRENT_SYNC_SCHEMA_VERSION,
    val presetId: String = "",
    val name: String = "",
    val durationSeconds: Long = 60L,
    val builtIn: Boolean = false,
    val sortOrder: Int = 0,
    val originDeviceId: String = "",
    val updatedAtWallMs: Long = 0L,
    val deleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class AccountSyncUiState(
    val isSignedIn: Boolean = false,
    val isAuthBusy: Boolean = false,
    val isAvatarUploading: Boolean = false,
    val isSyncSupported: Boolean = true,
    val syncUnsupportedReason: String? = null,
    val unlinkingDeviceId: String? = null,
    val userId: String? = null,
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val avatarBase64: String = "",
    val thisDeviceId: String = "",
    val thisDeviceName: String = resolveReadableDeviceName(),
    val syncLockEnabled: Boolean = true,
    val connectedDevices: List<CloudDeviceInfo> = emptyList(),
    val lastSyncedStatusMessage: String? = null,
    val errorMessage: String? = null
) {
    val activeSyncedDeviceCount: Int
        get() = connectedDevices.count { it.syncEnabled }
}

fun CloudDeviceInfo.resolvedDisplayName(): String {
    val clean = deviceName.trim()
    return if (clean.isEmpty() || clean.equals("unknown", ignoreCase = true)) {
        "Android Device"
    } else {
        clean
    }
}

fun CloudDeviceInfo.hasActiveRemoteLock(
    nowWallMs: Long = System.currentTimeMillis(),
    serverOffsetMs: Long = 0L
): Boolean {
    if (activeLockMode.isBlank()) return false
    return activeLockExpiresWallMs <= 0L || activeLockExpiresWallMs > (nowWallMs + serverOffsetMs)
}

private fun cleanDeviceToken(raw: String?): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return ""
    if (trimmed.equals("unknown", ignoreCase = true)) return ""
    if (trimmed.equals("generic", ignoreCase = true)) return ""
    return trimmed
}

fun resolveReadableDeviceName(): String {
    val manufacturer = cleanDeviceToken(Build.MANUFACTURER)
    val brand = cleanDeviceToken(Build.BRAND)
    val model = cleanDeviceToken(Build.MODEL)
    val device = cleanDeviceToken(Build.DEVICE)
    val vendor = manufacturer.ifEmpty { brand }
    val hardware = model.ifEmpty { device }
    val combined = when {
        hardware.isEmpty() && vendor.isEmpty() -> "Android Device"
        hardware.isEmpty() -> vendor.replaceFirstChar { it.uppercase() }
        vendor.isEmpty() -> hardware
        hardware.startsWith(vendor, ignoreCase = true) -> hardware
        else -> "${vendor.replaceFirstChar { it.uppercase() }} $hardware"
    }
    return combined.take(80).ifBlank { "Android Device" }
}
