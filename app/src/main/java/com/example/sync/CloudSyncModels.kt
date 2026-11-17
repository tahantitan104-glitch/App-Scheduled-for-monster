package com.example.sync

import android.os.Build
import androidx.annotation.Keep
import com.example.domain.model.LockMode
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CloudUserProfile(
    val userId: String = "",
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
    val deviceName: String = "",
    val platform: String = "android",
    val syncEnabled: Boolean = true,
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudLockCommand(
    val userId: String = "",
    val commandId: String = "",
    val originDeviceId: String = "",
    val originDeviceName: String = "",
    val mode: String = LockMode.INSTANT.name,
    val freeUseDurationMinutes: Long = 0L,
    val lockDurationMinutes: Long = 0L,
    val issuedAtWallMs: Long = 0L,
    val freeUseDeadlineWallMs: Long = 0L,
    val lockExpirationWallMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolveLockMode(): LockMode {
        return LockMode.entries.firstOrNull { it.name == mode } ?: LockMode.INSTANT
    }
}

data class AccountSyncUiState(
    val isSignedIn: Boolean = false,
    val isAuthBusy: Boolean = false,
    val isAvatarUploading: Boolean = false,
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

fun resolveReadableDeviceName(): String {
    val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
    val model = Build.MODEL?.trim().orEmpty()
    val combined = when {
        model.isEmpty() -> "Android Phone"
        manufacturer.isEmpty() -> model
        model.startsWith(manufacturer, ignoreCase = true) -> model
        else -> "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
    }
    return combined.take(80).ifBlank { "Android Phone" }
}
