package com.example.sync

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import com.example.R
import com.example.domain.model.LockMode
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

interface CloudSyncGateway {
    val authState: Flow<FirebaseUser?>
    val currentUserId: String?
    suspend fun signInWithGoogleIdToken(idToken: String): Result<FirebaseUser>
    suspend fun signOut(): Result<Unit>
    fun observeUserProfile(userId: String): Flow<CloudUserProfile?>
    fun observeDevices(userId: String): Flow<List<CloudDeviceInfo>>
    fun observeLatestLockCommand(userId: String): Flow<CloudLockCommand?>
    fun observeRecentLockCommands(userId: String): Flow<List<CloudLockCommand>> = emptyFlow()
    fun observeDelayedPresets(userId: String): Flow<List<CloudDelayedPreset>> = emptyFlow()
    fun observeSyncedSchedules(userId: String): Flow<List<CloudScheduleRecord>> = emptyFlow()
    fun observeLockProfiles(userId: String): Flow<List<CloudLockProfileSettings>> = emptyFlow()
    fun observeFlipClockSettings(userId: String): Flow<CloudFlipClockSettings?> = emptyFlow()
    fun observeFlipTimerPresets(userId: String): Flow<List<CloudFlipTimerPreset>> = emptyFlow()
    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit>
    suspend fun registerOrUpdateDevice(
        userId: String,
        deviceId: String,
        deviceName: String,
        syncEnabled: Boolean
    ): Result<Unit>
    suspend fun updateDeviceSyncEnabled(
        userId: String,
        deviceId: String,
        deviceName: String,
        syncEnabled: Boolean
    ): Result<Unit>
    suspend fun unlinkDevice(userId: String, deviceId: String): Result<Unit> = Result.success(Unit)
    suspend fun updateAvatarBase64(userId: String, avatarBase64: String): Result<Unit>
    suspend fun updateDisplayName(userId: String, displayName: String): Result<Unit> = Result.success(Unit)
    suspend fun publishLockCommand(
        userId: String,
        originDeviceId: String,
        originDeviceName: String,
        mode: LockMode,
        freeUseDurationMinutes: Long,
        lockDurationMinutes: Long,
        issuedAtWallMs: Long,
        intendedStartWallMs: Long = issuedAtWallMs,
        freeUseDeadlineWallMs: Long,
        lockExpirationWallMs: Long,
        scheduleId: String = "",
        occurrenceId: String = "",
        strictMode: Boolean = false,
        showFlipClockShortcut: Boolean = false
    ): Result<String>
    suspend fun upsertDelayedPreset(userId: String, preset: CloudDelayedPreset): Result<Unit> =
        Result.success(Unit)
    suspend fun upsertSyncedSchedule(userId: String, schedule: CloudScheduleRecord): Result<Unit> =
        Result.success(Unit)
    suspend fun upsertLockProfile(userId: String, profile: CloudLockProfileSettings): Result<Unit> =
        Result.success(Unit)
    suspend fun upsertFlipClockSettings(userId: String, settings: CloudFlipClockSettings): Result<Unit> =
        Result.success(Unit)
    suspend fun upsertFlipTimerPreset(userId: String, preset: CloudFlipTimerPreset): Result<Unit> =
        Result.success(Unit)
}

class CloudAccountRepository(
    private val dbProvider: () -> FirebaseFirestore?,
    private val authProvider: () -> FirebaseAuth?
) : CloudSyncGateway {

    constructor(context: Context) : this(
        dbProvider = {
            val appContext = context.applicationContext
            if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                val databaseId = appContext.getString(R.string.firestore_database_id)
                FirebaseFirestore.getInstance(databaseId)
            } else {
                null
            }
        },
        authProvider = {
            if (FirebaseApp.getApps(context.applicationContext).isNotEmpty()) {
                FirebaseAuth.getInstance()
            } else {
                null
            }
        }
    )

    override val authState: Flow<FirebaseUser?>
        get() = authProvider()?.authStateFlow() ?: emptyFlow()

    override val currentUserId: String?
        get() = authProvider()?.currentUser?.uid

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<FirebaseUser> {
        return try {
            val auth = authProvider() ?: error("Firebase Authentication is not initialized.")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: auth.currentUser
                ?: error("Signed in, but Firebase user session was empty.")
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            authProvider()?.signOut()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeUserProfile(userId: String): Flow<CloudUserProfile?> {
        if (userId.isBlank()) return emptyFlow()
        val db = dbProvider() ?: return emptyFlow()
        val auth = authProvider()
        if (auth?.currentUser?.uid != userId) return emptyFlow()

        return callbackFlow {
            val docRef = db.collection("users").document(userId)
            val registration = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, docRef.path)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(
                        snapshot.toObject(
                            CloudUserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    )
                } else {
                    trySend(null)
                }
            }
            awaitClose { registration.remove() }
        }
    }

    override fun observeDevices(userId: String): Flow<List<CloudDeviceInfo>> {
        if (userId.isBlank()) return emptyFlow()
        val db = dbProvider() ?: return emptyFlow()
        val auth = authProvider()
        if (auth?.currentUser?.uid != userId) return emptyFlow()

        return callbackFlow {
            val colRef = db.collection("users").document(userId).collection("devices")
            val registration = colRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, colRef.path)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(
                    CloudDeviceInfo::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).orEmpty()
                trySend(list)
            }
            awaitClose { registration.remove() }
        }
    }

    override fun observeLatestLockCommand(userId: String): Flow<CloudLockCommand?> {
        if (userId.isBlank()) return emptyFlow()
        val db = dbProvider() ?: return emptyFlow()
        val auth = authProvider()
        if (auth?.currentUser?.uid != userId) return emptyFlow()

        return callbackFlow {
            val docRef = db.collection("users").document(userId)
                .collection("sync").document(LOCK_COMMAND_DOC_ID)
            val registration = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, docRef.path)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(
                        snapshot.toObject(
                            CloudLockCommand::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    )
                } else {
                    trySend(null)
                }
            }
            awaitClose { registration.remove() }
        }
    }

    override fun observeRecentLockCommands(userId: String): Flow<List<CloudLockCommand>> {
        if (userId.isBlank()) return emptyFlow()
        val db = dbProvider() ?: return emptyFlow()
        val auth = authProvider()
        if (auth?.currentUser?.uid != userId) return emptyFlow()

        return callbackFlow {
            val colRef = db.collection("users").document(userId)
                .collection("lock_commands")
                .orderBy("issuedAtWallMs", Query.Direction.DESCENDING)
                .limit(20L)
            val registration = colRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "users/$userId/lock_commands")
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(
                    CloudLockCommand::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).orEmpty()
                trySend(list)
            }
            awaitClose { registration.remove() }
        }
    }

    override fun observeDelayedPresets(userId: String): Flow<List<CloudDelayedPreset>> {
        if (userId.isBlank()) return emptyFlow()
        val db = dbProvider() ?: return emptyFlow()
        val auth = authProvider()
        if (auth?.currentUser?.uid != userId) return emptyFlow()

        return callbackFlow {
            val colRef = db.collection("users").document(userId).collection("presets")
            val registration = colRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, colRef.path)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(
                    CloudDelayedPreset::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).orEmpty()
                trySend(list)
            }
            awaitClose { registration.remove() }
        }
    }

    override fun observeSyncedSchedules(userId: String): Flow<List<CloudScheduleRecord>> {
        if (userId.isBlank()) return emptyFlow()
        val db = dbProvider() ?: return emptyFlow()
        val auth = authProvider()
        if (auth?.currentUser?.uid != userId) return emptyFlow()

        return callbackFlow {
            val colRef = db.collection("users").document(userId).collection("schedules")
            val registration = colRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, colRef.path)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(
                    CloudScheduleRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).orEmpty()
                trySend(list)
            }
            awaitClose { registration.remove() }
        }
    }

    override fun observeLockProfiles(userId: String): Flow<List<CloudLockProfileSettings>> {
        if (userId.isBlank()) return emptyFlow()
        val db = dbProvider() ?: return emptyFlow()
        val auth = authProvider()
        if (auth?.currentUser?.uid != userId) return emptyFlow()

        return callbackFlow {
            val colRef = db.collection("users").document(userId).collection("lock_profiles")
            val registration = colRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, colRef.path)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(
                    CloudLockProfileSettings::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).orEmpty()
                trySend(list)
            }
            awaitClose { registration.remove() }
        }
    }

    override fun observeFlipClockSettings(userId: String): Flow<CloudFlipClockSettings?> {
        if (userId.isBlank()) return emptyFlow()
        val db = dbProvider() ?: return emptyFlow()
        val auth = authProvider()
        if (auth?.currentUser?.uid != userId) return emptyFlow()

        return callbackFlow {
            val docRef = db.collection("users").document(userId)
                .collection("sync").document(FLIP_CLOCK_SETTINGS_DOC_ID)
            val registration = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, docRef.path)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(
                        snapshot.toObject(
                            CloudFlipClockSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    )
                } else {
                    trySend(null)
                }
            }
            awaitClose { registration.remove() }
        }
    }

    override fun observeFlipTimerPresets(userId: String): Flow<List<CloudFlipTimerPreset>> {
        if (userId.isBlank()) return emptyFlow()
        val db = dbProvider() ?: return emptyFlow()
        val auth = authProvider()
        if (auth?.currentUser?.uid != userId) return emptyFlow()

        return callbackFlow {
            val colRef = db.collection("users").document(userId).collection("flip_timer_presets")
            val registration = colRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, colRef.path)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(
                    CloudFlipTimerPreset::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).orEmpty()
                trySend(list)
            }
            awaitClose { registration.remove() }
        }
    }

    override suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val path = "users/$uid"
        return try {
            val db = dbProvider() ?: error("Cloud sync is unavailable.")
            val activeUid = authProvider()?.currentUser?.uid
                ?: error("User must be signed in before writing.")
            check(activeUid == uid) { "Signed-in user mismatch." }

            val safeName = (user.displayName?.trim().orEmpty())
                .ifBlank { user.email?.substringBefore('@').orEmpty() }
                .ifBlank { "Resolve Focus User" }
                .take(80)
            val safeEmail = user.email?.trim()?.take(160)
            val safePhotoUrl = user.photoUrl?.toString()?.trim()?.take(600)

            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val createMap = buildMap<String, Any> {
                    put("userId", uid)
                    put("schemaVersion", CURRENT_SYNC_SCHEMA_VERSION)
                    put("displayName", safeName)
                    if (!safeEmail.isNullOrBlank()) put("email", safeEmail)
                    if (!safePhotoUrl.isNullOrBlank()) put("photoUrl", safePhotoUrl)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(createMap).await()
            } else {
                val updateMap = buildMap<String, Any> {
                    put("schemaVersion", CURRENT_SYNC_SCHEMA_VERSION)
                    put("displayName", safeName)
                    if (!safeEmail.isNullOrBlank()) put("email", safeEmail)
                    if (!safePhotoUrl.isNullOrBlank()) put("photoUrl", safePhotoUrl)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun registerOrUpdateDevice(
        userId: String,
        deviceId: String,
        deviceName: String,
        syncEnabled: Boolean
    ): Result<Unit> {
        val path = "users/$userId/devices/$deviceId"
        return try {
            val db = dbProvider() ?: error("Cloud sync is unavailable.")
            val activeUid = authProvider()?.currentUser?.uid
                ?: error("User must be signed in before writing.")
            check(activeUid == userId) { "Signed-in user mismatch." }
            require(deviceId.isNotBlank()) { "Device ID must not be blank." }

            val safeDeviceName = deviceName.trim().ifBlank { "Android Phone" }.take(80)
            val safeAppVersion = BuildConfig.VERSION_NAME.take(40)
            val sdkVersion = Build.VERSION.SDK_INT.toLong()
            val docRef = db.collection("users").document(userId)
                .collection("devices").document(deviceId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val createMap = mapOf(
                    "userId" to userId,
                    "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                    "deviceName" to safeDeviceName,
                    "platform" to "android",
                    "androidVersion" to sdkVersion,
                    "appVersion" to safeAppVersion,
                    "syncEnabled" to syncEnabled,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createMap).await()
            } else {
                val updateMap = mapOf(
                    "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                    "deviceName" to safeDeviceName,
                    "platform" to "android",
                    "androidVersion" to sdkVersion,
                    "appVersion" to safeAppVersion,
                    "syncEnabled" to syncEnabled,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateDeviceSyncEnabled(
        userId: String,
        deviceId: String,
        deviceName: String,
        syncEnabled: Boolean
    ): Result<Unit> = registerOrUpdateDevice(userId, deviceId, deviceName, syncEnabled)

    override suspend fun unlinkDevice(userId: String, deviceId: String): Result<Unit> {
        val path = "users/$userId/devices/$deviceId"
        return try {
            val db = dbProvider() ?: error("Cloud sync is unavailable.")
            val activeUid = authProvider()?.currentUser?.uid
                ?: error("User must be signed in before unlinking.")
            check(activeUid == userId) { "Signed-in user mismatch." }
            require(deviceId.isNotBlank()) { "Device ID must not be blank." }
            db.collection("users").document(userId)
                .collection("devices").document(deviceId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateAvatarBase64(userId: String, avatarBase64: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val db = dbProvider() ?: error("Cloud sync is unavailable.")
            val activeUid = authProvider()?.currentUser?.uid
                ?: error("User must be signed in before writing.")
            check(activeUid == userId) { "Signed-in user mismatch." }
            require(avatarBase64.length <= 160_000) { "Avatar image exceeds sync size limit." }

            val docRef = db.collection("users").document(userId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val user = authProvider()?.currentUser
                val safeName = (user?.displayName?.trim().orEmpty()).ifBlank { "Resolve Focus User" }.take(80)
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                        "displayName" to safeName,
                        "avatarBase64" to avatarBase64,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                        "avatarBase64" to avatarBase64,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateDisplayName(userId: String, displayName: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val db = dbProvider() ?: error("Cloud sync is unavailable.")
            val activeUid = authProvider()?.currentUser?.uid
                ?: error("User must be signed in before writing.")
            check(activeUid == userId) { "Signed-in user mismatch." }
            val safeName = displayName.trim().ifBlank { "Resolve Focus User" }.take(80)

            val docRef = db.collection("users").document(userId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                        "displayName" to safeName,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                        "displayName" to safeName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun publishLockCommand(
        userId: String,
        originDeviceId: String,
        originDeviceName: String,
        mode: LockMode,
        freeUseDurationMinutes: Long,
        lockDurationMinutes: Long,
        issuedAtWallMs: Long,
        intendedStartWallMs: Long,
        freeUseDeadlineWallMs: Long,
        lockExpirationWallMs: Long,
        scheduleId: String,
        occurrenceId: String,
        strictMode: Boolean,
        showFlipClockShortcut: Boolean
    ): Result<String> {
        val path = "users/$userId/lock_commands"
        return try {
            val db = dbProvider() ?: error("Cloud sync is unavailable.")
            val activeUid = authProvider()?.currentUser?.uid
                ?: error("User must be signed in before writing.")
            check(activeUid == userId) { "Signed-in user mismatch." }

            val commandId = "cmd_" + UUID.randomUUID().toString().replace("-", "")
            val safeOriginName = originDeviceName.trim().ifBlank { "Android Phone" }.take(80)
            val safeFreeMins = freeUseDurationMinutes.coerceIn(0L, 1440L)
            val safeLockMins = lockDurationMinutes.coerceIn(1L, 1440L)
            val safeIssuedMs = issuedAtWallMs.coerceAtLeast(0L)
            val safeIntendedMs = intendedStartWallMs.coerceAtLeast(0L)
            val safeFreeDeadline = freeUseDeadlineWallMs.coerceAtLeast(0L)
            val safeLockExpire = lockExpirationWallMs.coerceAtLeast(0L)
            val safeScheduleId = scheduleId.trim().take(128)
            val safeOccurrenceId = occurrenceId.trim().take(128)

            // 1. Append to versioned lock_commands/{commandId} collection
            val cmdDocRef = db.collection("users").document(userId)
                .collection("lock_commands").document(commandId)
            val commandCreateMap = mapOf(
                "userId" to userId,
                "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                "commandId" to commandId,
                "originDeviceId" to originDeviceId,
                "originDeviceName" to safeOriginName,
                "mode" to mode.name,
                "freeUseDurationMinutes" to safeFreeMins,
                "lockDurationMinutes" to safeLockMins,
                "issuedAtWallMs" to safeIssuedMs,
                "intendedStartWallMs" to safeIntendedMs,
                "freeUseDeadlineWallMs" to safeFreeDeadline,
                "lockExpirationWallMs" to safeLockExpire,
                "scheduleId" to safeScheduleId,
                "occurrenceId" to safeOccurrenceId,
                "strictMode" to strictMode,
                "showFlipClockShortcut" to showFlipClockShortcut,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            cmdDocRef.set(commandCreateMap).await()

            // 2. Also update legacy deterministic sync/lock_command doc for cross-version parity
            val legacyDocRef = db.collection("users").document(userId)
                .collection("sync").document(LOCK_COMMAND_DOC_ID)
            val existingLegacy = legacyDocRef.get().await()
            if (!existingLegacy.exists()) {
                legacyDocRef.set(commandCreateMap).await()
            } else {
                val updateMap = mapOf(
                    "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                    "commandId" to commandId,
                    "originDeviceId" to originDeviceId,
                    "originDeviceName" to safeOriginName,
                    "mode" to mode.name,
                    "freeUseDurationMinutes" to safeFreeMins,
                    "lockDurationMinutes" to safeLockMins,
                    "issuedAtWallMs" to safeIssuedMs,
                    "intendedStartWallMs" to safeIntendedMs,
                    "freeUseDeadlineWallMs" to safeFreeDeadline,
                    "lockExpirationWallMs" to safeLockExpire,
                    "scheduleId" to safeScheduleId,
                    "occurrenceId" to safeOccurrenceId,
                    "strictMode" to strictMode,
                    "showFlipClockShortcut" to showFlipClockShortcut,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                legacyDocRef.update(updateMap).await()
            }
            Result.success(commandId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun upsertDelayedPreset(userId: String, preset: CloudDelayedPreset): Result<Unit> {
        val safePresetId = preset.presetId.trim().take(128)
        val path = "users/$userId/presets/$safePresetId"
        return try {
            val db = dbProvider() ?: error("Cloud sync is unavailable.")
            val activeUid = authProvider()?.currentUser?.uid
                ?: error("User must be signed in before writing.")
            check(activeUid == userId) { "Signed-in user mismatch." }
            require(safePresetId.isNotBlank()) { "Preset ID must not be blank." }

            val safeName = preset.name.trim().ifBlank { "Custom Preset" }.take(60)
            val safeFree = preset.freeUseMinutes.coerceIn(1L, 1440L)
            val safeLock = preset.lockDurationMinutes.coerceIn(1L, 1440L)
            val docRef = db.collection("users").document(userId)
                .collection("presets").document(safePresetId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                        "presetId" to safePresetId,
                        "name" to safeName,
                        "freeUseMinutes" to safeFree,
                        "lockDurationMinutes" to safeLock,
                        "originDeviceId" to preset.originDeviceId,
                        "updatedAtWallMs" to preset.updatedAtWallMs.coerceAtLeast(0L),
                        "deleted" to preset.deleted,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                        "name" to safeName,
                        "freeUseMinutes" to safeFree,
                        "lockDurationMinutes" to safeLock,
                        "originDeviceId" to preset.originDeviceId,
                        "updatedAtWallMs" to preset.updatedAtWallMs.coerceAtLeast(0L),
                        "deleted" to preset.deleted,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun upsertSyncedSchedule(userId: String, schedule: CloudScheduleRecord): Result<Unit> {
        val safeScheduleId = schedule.scheduleId.trim().take(128)
        val path = "users/$userId/schedules/$safeScheduleId"
        return try {
            val db = dbProvider() ?: error("Cloud sync is unavailable.")
            val activeUid = authProvider()?.currentUser?.uid
                ?: error("User must be signed in before writing.")
            check(activeUid == userId) { "Signed-in user mismatch." }
            require(safeScheduleId.isNotBlank()) { "Schedule ID must not be blank." }

            val safeName = schedule.name.trim().ifBlank { "Focus Schedule" }.take(80)
            val docRef = db.collection("users").document(userId)
                .collection("schedules").document(safeScheduleId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                        "scheduleId" to safeScheduleId,
                        "name" to safeName,
                        "type" to schedule.resolveScheduleType().name,
                        "oneTimeDateEpochDay" to schedule.oneTimeDateEpochDay,
                        "weekdaysMask" to schedule.weekdaysMask.coerceIn(0, 127),
                        "startHour" to schedule.startHour.coerceIn(0, 23),
                        "startMinute" to schedule.startMinute.coerceIn(0, 59),
                        "lockDurationMinutes" to schedule.lockDurationMinutes.coerceIn(1L, 1440L),
                        "enabled" to schedule.enabled,
                        "syncAcrossDevices" to schedule.syncAcrossDevices,
                        "strictMode" to schedule.strictMode,
                        "allowSelectedApps" to schedule.allowSelectedApps,
                        "showFlipClockShortcut" to schedule.showFlipClockShortcut,
                        "originDeviceId" to schedule.originDeviceId,
                        "updatedAtWallMs" to schedule.updatedAtWallMs.coerceAtLeast(0L),
                        "deleted" to schedule.deleted,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                        "name" to safeName,
                        "type" to schedule.resolveScheduleType().name,
                        "oneTimeDateEpochDay" to schedule.oneTimeDateEpochDay,
                        "weekdaysMask" to schedule.weekdaysMask.coerceIn(0, 127),
                        "startHour" to schedule.startHour.coerceIn(0, 23),
                        "startMinute" to schedule.startMinute.coerceIn(0, 59),
                        "lockDurationMinutes" to schedule.lockDurationMinutes.coerceIn(1L, 1440L),
                        "enabled" to schedule.enabled,
                        "syncAcrossDevices" to schedule.syncAcrossDevices,
                        "strictMode" to schedule.strictMode,
                        "allowSelectedApps" to schedule.allowSelectedApps,
                        "showFlipClockShortcut" to schedule.showFlipClockShortcut,
                        "originDeviceId" to schedule.originDeviceId,
                        "updatedAtWallMs" to schedule.updatedAtWallMs.coerceAtLeast(0L),
                        "deleted" to schedule.deleted,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun upsertLockProfile(userId: String, profile: CloudLockProfileSettings): Result<Unit> {
        val safeMode = profile.resolveLockMode().name
        val path = "users/$userId/lock_profiles/$safeMode"
        return try {
            val db = dbProvider() ?: error("Cloud sync is unavailable.")
            val activeUid = authProvider()?.currentUser?.uid
                ?: error("User must be signed in before writing.")
            check(activeUid == userId) { "Signed-in user mismatch." }

            val safePackages = profile.allowedPackages
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.length <= 160 }
                .distinct()
                .take(200)
            val docRef = db.collection("users").document(userId)
                .collection("lock_profiles").document(safeMode)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                        "profileMode" to safeMode,
                        "strictMode" to profile.strictMode,
                        "allowSelectedApps" to profile.allowSelectedApps,
                        "showFlipClockShortcut" to profile.showFlipClockShortcut,
                        "allowedPackages" to safePackages,
                        "originDeviceId" to profile.originDeviceId,
                        "updatedAtWallMs" to profile.updatedAtWallMs.coerceAtLeast(0L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                        "strictMode" to profile.strictMode,
                        "allowSelectedApps" to profile.allowSelectedApps,
                        "showFlipClockShortcut" to profile.showFlipClockShortcut,
                        "allowedPackages" to safePackages,
                        "originDeviceId" to profile.originDeviceId,
                        "updatedAtWallMs" to profile.updatedAtWallMs.coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun upsertFlipClockSettings(userId: String, settings: CloudFlipClockSettings): Result<Unit> {
        val path = "users/$userId/sync/$FLIP_CLOCK_SETTINGS_DOC_ID"
        return try {
            val db = dbProvider() ?: error("Cloud sync is unavailable.")
            val activeUid = authProvider()?.currentUser?.uid
                ?: error("User must be signed in before writing.")
            check(activeUid == userId) { "Signed-in user mismatch." }

            val safeZoom = settings.zoomFraction.coerceIn(0.5, 1.4)
            val docRef = db.collection("users").document(userId)
                .collection("sync").document(FLIP_CLOCK_SETTINGS_DOC_ID)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                        "docId" to FLIP_CLOCK_SETTINGS_DOC_ID,
                        "use24HourFormat" to settings.use24HourFormat,
                        "keepScreenAwake" to settings.keepScreenAwake,
                        "showDate" to settings.showDate,
                        "autoRepeat" to settings.autoRepeat,
                        "zoomFraction" to safeZoom,
                        "originDeviceId" to settings.originDeviceId,
                        "updatedAtWallMs" to settings.updatedAtWallMs.coerceAtLeast(0L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                        "use24HourFormat" to settings.use24HourFormat,
                        "keepScreenAwake" to settings.keepScreenAwake,
                        "showDate" to settings.showDate,
                        "autoRepeat" to settings.autoRepeat,
                        "zoomFraction" to safeZoom,
                        "originDeviceId" to settings.originDeviceId,
                        "updatedAtWallMs" to settings.updatedAtWallMs.coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun upsertFlipTimerPreset(userId: String, preset: CloudFlipTimerPreset): Result<Unit> {
        val safePresetId = preset.presetId.trim().take(128)
        val path = "users/$userId/flip_timer_presets/$safePresetId"
        return try {
            val db = dbProvider() ?: error("Cloud sync is unavailable.")
            val activeUid = authProvider()?.currentUser?.uid
                ?: error("User must be signed in before writing.")
            check(activeUid == userId) { "Signed-in user mismatch." }
            require(safePresetId.isNotBlank()) { "Preset ID must not be blank." }

            val safeName = preset.name.trim().ifBlank { "Timer Preset" }.take(60)
            val safeDurationSec = preset.durationSeconds.coerceIn(1L, 86_399L)
            val safeSortOrder = preset.sortOrder.coerceIn(0, 1000)
            val docRef = db.collection("users").document(userId)
                .collection("flip_timer_presets").document(safePresetId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                        "presetId" to safePresetId,
                        "name" to safeName,
                        "durationSeconds" to safeDurationSec,
                        "builtIn" to preset.builtIn,
                        "sortOrder" to safeSortOrder,
                        "originDeviceId" to preset.originDeviceId,
                        "updatedAtWallMs" to preset.updatedAtWallMs.coerceAtLeast(0L),
                        "deleted" to preset.deleted,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "schemaVersion" to CURRENT_SYNC_SCHEMA_VERSION,
                        "name" to safeName,
                        "durationSeconds" to safeDurationSec,
                        "builtIn" to preset.builtIn,
                        "sortOrder" to safeSortOrder,
                        "originDeviceId" to preset.originDeviceId,
                        "updatedAtWallMs" to preset.updatedAtWallMs.coerceAtLeast(0L),
                        "deleted" to preset.deleted,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    companion object {
        const val LOCK_COMMAND_DOC_ID = "lock_command"
        const val FLIP_CLOCK_SETTINGS_DOC_ID = "flip_clock_settings"

        fun unwrapFirestoreErrorMessage(error: Throwable, fallback: String): String {
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            return when (fsError?.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Account permission check failed. Please sign in again."
                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "Network unavailable — sync will resume automatically online."
                FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                    "Daily free cloud sync quota reached. Resets tomorrow."
                else -> fsError?.localizedMessage ?: error.localizedMessage ?: fallback
            }
        }
    }
}
