package com.example.sync

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.example.data.preferences.ResolvePreferencesRepository
import com.example.data.preferences.StoredRestAuthSession
import com.example.domain.model.LockMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * REST-based implementation of [CloudSyncGateway] for Android 5.1.1 (API 22) devices where
 * native Firebase Auth 24.x & Firestore 26.x SDKs require API 23+.
 *
 * Uses Firebase Auth REST API (`identitytoolkit` / `securetoken`) and Cloud Firestore v1 REST API
 * targeting the configured named database (`R.string.firestore_database_id`) with strict
 * `firestore.rules` schema parity and automatic token refresh.
 */
class RestCloudAccountRepository(
    context: Context,
    private val preferencesRepository: ResolvePreferencesRepository
) : CloudSyncGateway {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t ->
            Log.w(TAG, "Recovered in REST sync background loop", t)
        }
    )
    private val tokenMutex = Mutex()
    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    private val fastRefreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    private val serverOffsetMs = AtomicLong(0L)
    private val hasServerClockSample = AtomicBoolean(false)

    private val _authState = MutableStateFlow<CloudAuthSession?>(null)
    override val authState: Flow<CloudAuthSession?> = _authState.asStateFlow()

    @Volatile
    private var cachedSession: StoredRestAuthSession? = null

    @Volatile
    private var initialized = false

    private val initMutex = Mutex()
    private var pollJob: Job? = null
    private var fastLockPollJob: Job? = null

    private val _profileState = MutableStateFlow<Pair<String, CloudUserProfile?>>("" to null)
    private val _devicesState = MutableStateFlow<Pair<String, List<CloudDeviceInfo>>>("" to emptyList())
    private val _latestLockCommandState = MutableStateFlow<Pair<String, CloudLockCommand?>>("" to null)
    private val _recentLockCommandsState = MutableStateFlow<Pair<String, List<CloudLockCommand>>>("" to emptyList())
    private val _delayedPresetsState = MutableStateFlow<Pair<String, List<CloudDelayedPreset>>>("" to emptyList())
    private val _schedulesState = MutableStateFlow<Pair<String, List<CloudScheduleRecord>>>("" to emptyList())
    private val _lockProfilesState = MutableStateFlow<Pair<String, List<CloudLockProfileSettings>>>("" to emptyList())
    private val _flipClockSettingsState = MutableStateFlow<Pair<String, CloudFlipClockSettings?>>("" to null)
    private val _flipTimerPresetsState = MutableStateFlow<Pair<String, List<CloudFlipTimerPreset>>>("" to emptyList())

    override val currentUserId: String?
        get() = cachedSession?.uid ?: _authState.value?.uid

    init {
        scope.launch {
            restorePersistedSessionIfAny()
        }
    }

    private suspend fun restorePersistedSessionIfAny() {
        initMutex.withLock {
            if (initialized) return
            val stored = runCatching { preferencesRepository.getRestAuthSessionSnapshot() }.getOrNull()
            if (stored != null) {
                cachedSession = stored
                _authState.value = stored.toCloudAuthSession()
                startPollingForUser(stored.uid)
            }
            initialized = true
        }
    }

    override fun refreshSyncNow() {
        fastRefreshSignal.tryEmit(Unit)
        refreshSignal.tryEmit(Unit)
    }

    override fun estimatedServerOffsetMs(): Long = serverOffsetMs.get()

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<CloudAuthSession> {
        return withContext(Dispatchers.IO) {
            try {
                Tls12SocketFactory.ensureProviderInstalled(appContext)
                val apiKey = requireApiKey()
                val encodedToken = URLEncoder.encode(idToken, "UTF-8")
                val payload = JSONObject().apply {
                    put("postBody", "id_token=$encodedToken&providerId=google.com")
                    put("requestUri", "http://localhost")
                    put("returnIdpCredential", true)
                    put("returnSecureToken", true)
                }
                val endpoint = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$apiKey"
                val response = executeJsonRequest(
                    urlString = endpoint,
                    method = "POST",
                    bearerToken = null,
                    body = payload.toString()
                )
                if (response.code !in 200..299) {
                    throw mapRestHttpError(response.code, response.body, "Could not verify Google account.")
                }

                val json = JSONObject(response.body)
                val uid = json.optString("localId").trim()
                val firebaseIdToken = json.optString("idToken").trim()
                val refreshToken = json.optString("refreshToken").trim()
                val expiresInSec = json.optString("expiresIn", "3600").toLongOrNull() ?: 3600L
                if (uid.isEmpty() || firebaseIdToken.isEmpty() || refreshToken.isEmpty()) {
                    throw CloudSyncException(
                        CloudSyncException.Kind.OTHER,
                        "Account server returned an incomplete sign-in session."
                    )
                }

                val displayName = json.optString("displayName")
                    .ifBlank { json.optString("fullName") }
                    .trim()
                    .take(80)
                val email = json.optString("email").trim().take(160)
                val photoUrl = json.optString("photoUrl").trim().take(600)

                val stored = StoredRestAuthSession(
                    uid = uid,
                    idToken = firebaseIdToken,
                    refreshToken = refreshToken,
                    expiresAtWallMs = System.currentTimeMillis() + (expiresInSec.coerceAtLeast(60L) * 1000L),
                    email = email,
                    displayName = displayName,
                    photoUrl = photoUrl
                )
                tokenMutex.withLock {
                    cachedSession = stored
                    preferencesRepository.saveRestAuthSession(stored)
                }
                initialized = true
                resetPolledCaches()
                val authSession = stored.toCloudAuthSession()
                _authState.value = authSession
                startPollingForUser(uid)
                fastRefreshSignal.tryEmit(Unit)
                refreshSignal.tryEmit(Unit)
                Result.success(authSession)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                fastLockPollJob?.cancel()
                fastLockPollJob = null
                pollJob?.cancel()
                pollJob = null
                tokenMutex.withLock {
                    cachedSession = null
                    preferencesRepository.clearRestAuthSession()
                }
                resetPolledCaches()
                _authState.value = null
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }

    override fun observeUserProfile(userId: String): Flow<CloudUserProfile?> {
        if (userId.isBlank()) return kotlinx.coroutines.flow.emptyFlow()
        return _profileState.asStateFlow().map { (uid, profile) ->
            if (uid == userId) profile else null
        }
    }

    override fun observeDevices(userId: String): Flow<List<CloudDeviceInfo>> {
        if (userId.isBlank()) return kotlinx.coroutines.flow.emptyFlow()
        return _devicesState.asStateFlow().map { (uid, list) ->
            if (uid == userId) list else emptyList()
        }
    }

    override fun observeLatestLockCommand(userId: String): Flow<CloudLockCommand?> {
        if (userId.isBlank()) return kotlinx.coroutines.flow.emptyFlow()
        return _latestLockCommandState.asStateFlow().map { (uid, cmd) ->
            if (uid == userId) cmd else null
        }
    }

    override fun observeRecentLockCommands(userId: String): Flow<List<CloudLockCommand>> {
        if (userId.isBlank()) return kotlinx.coroutines.flow.emptyFlow()
        return _recentLockCommandsState.asStateFlow().map { (uid, list) ->
            if (uid == userId) list else emptyList()
        }
    }

    override fun observeDelayedPresets(userId: String): Flow<List<CloudDelayedPreset>> {
        if (userId.isBlank()) return kotlinx.coroutines.flow.emptyFlow()
        return _delayedPresetsState.asStateFlow().map { (uid, list) ->
            if (uid == userId) list else emptyList()
        }
    }

    override fun observeSyncedSchedules(userId: String): Flow<List<CloudScheduleRecord>> {
        if (userId.isBlank()) return kotlinx.coroutines.flow.emptyFlow()
        return _schedulesState.asStateFlow().map { (uid, list) ->
            if (uid == userId) list else emptyList()
        }
    }

    override fun observeLockProfiles(userId: String): Flow<List<CloudLockProfileSettings>> {
        if (userId.isBlank()) return kotlinx.coroutines.flow.emptyFlow()
        return _lockProfilesState.asStateFlow().map { (uid, list) ->
            if (uid == userId) list else emptyList()
        }
    }

    override fun observeFlipClockSettings(userId: String): Flow<CloudFlipClockSettings?> {
        if (userId.isBlank()) return kotlinx.coroutines.flow.emptyFlow()
        return _flipClockSettingsState.asStateFlow().map { (uid, settings) ->
            if (uid == userId) settings else null
        }
    }

    override fun observeFlipTimerPresets(userId: String): Flow<List<CloudFlipTimerPreset>> {
        if (userId.isBlank()) return kotlinx.coroutines.flow.emptyFlow()
        return _flipTimerPresetsState.asStateFlow().map { (uid, list) ->
            if (uid == userId) list else emptyList()
        }
    }

    override suspend fun ensureUserProfile(user: CloudAuthSession): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uid = user.uid
            requireActiveSessionMatches(uid)

            val safeName = (user.displayName?.trim().orEmpty())
                .ifBlank { user.email?.substringBefore('@').orEmpty() }
                .ifBlank { "Resolve Focus User" }
                .take(80)
            val safeEmail = user.email?.trim()?.take(160)
            val safePhotoUrl = user.photoUrl?.trim()?.take(600)

            val docPath = "users/${Uri.encode(uid)}"
            val existing = getDocument(docPath)
            if (existing == null) {
                val createFields = linkedMapOf(
                    "userId" to strField(uid),
                    "schemaVersion" to intField(CURRENT_SYNC_SCHEMA_VERSION),
                    "displayName" to strField(safeName)
                ).apply {
                    if (!safeEmail.isNullOrBlank()) put("email", strField(safeEmail))
                    if (!safePhotoUrl.isNullOrBlank()) put("photoUrl", strField(safePhotoUrl))
                }
                commitDocument(
                    relativeDocPath = docPath,
                    fields = createFields,
                    isCreate = true
                )
            } else {
                val existingName = existing.fieldsObj().firestoreString("displayName")
                val updateFields = linkedMapOf(
                    "schemaVersion" to intField(CURRENT_SYNC_SCHEMA_VERSION)
                ).apply {
                    if (existingName.isBlank()) put("displayName", strField(safeName))
                    if (!safeEmail.isNullOrBlank()) put("email", strField(safeEmail))
                    if (!safePhotoUrl.isNullOrBlank()) put("photoUrl", strField(safePhotoUrl))
                }
                commitDocument(
                    relativeDocPath = docPath,
                    fields = updateFields,
                    isCreate = false
                )
            }
            refreshSignal.tryEmit(Unit)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "ensureUserProfile failed", t)
            Result.failure(t)
        }
    }

    override suspend fun registerOrUpdateDevice(
        userId: String,
        deviceId: String,
        deviceName: String,
        syncEnabled: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            requireActiveSessionMatches(userId)
            require(deviceId.isNotBlank()) { "Device ID must not be blank." }

            val safeDeviceName = deviceName.trim().ifBlank { "Android Tablet" }.take(80)
            val safeAppVersion = BuildConfig.VERSION_NAME.take(40)
            val sdkVersion = Build.VERSION.SDK_INT.toLong()
            val docPath = "users/${Uri.encode(userId)}/devices/${Uri.encode(deviceId)}"

            val existing = getDocument(docPath)
            if (existing == null) {
                val createFields = linkedMapOf(
                    "userId" to strField(userId),
                    "schemaVersion" to intField(CURRENT_SYNC_SCHEMA_VERSION),
                    "deviceName" to strField(safeDeviceName),
                    "platform" to strField("android"),
                    "androidVersion" to intField(sdkVersion),
                    "appVersion" to strField(safeAppVersion),
                    "syncEnabled" to boolField(syncEnabled)
                )
                commitDocument(
                    relativeDocPath = docPath,
                    fields = createFields,
                    isCreate = true,
                    createTransforms = listOf("lastSeenAt", "createdAt", "updatedAt")
                )
            } else {
                val updateFields = linkedMapOf(
                    "schemaVersion" to intField(CURRENT_SYNC_SCHEMA_VERSION),
                    "deviceName" to strField(safeDeviceName),
                    "platform" to strField("android"),
                    "androidVersion" to intField(sdkVersion),
                    "appVersion" to strField(safeAppVersion),
                    "syncEnabled" to boolField(syncEnabled)
                )
                commitDocument(
                    relativeDocPath = docPath,
                    fields = updateFields,
                    isCreate = false,
                    updateTransforms = listOf("lastSeenAt", "updatedAt")
                )
            }
            refreshSignal.tryEmit(Unit)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "registerOrUpdateDevice failed", t)
            Result.failure(t)
        }
    }

    override suspend fun updateDeviceSyncEnabled(
        userId: String,
        deviceId: String,
        deviceName: String,
        syncEnabled: Boolean
    ): Result<Unit> = registerOrUpdateDevice(userId, deviceId, deviceName, syncEnabled)

    override suspend fun unlinkDevice(userId: String, deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            requireActiveSessionMatches(userId)
            require(deviceId.isNotBlank()) { "Device ID must not be blank." }
            val docPath = "users/${Uri.encode(userId)}/devices/${Uri.encode(deviceId)}"
            deleteDocument(docPath)
            _devicesState.value = userId to _devicesState.value.second.filterNot { it.deviceId == deviceId }
            refreshSignal.tryEmit(Unit)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun updateAvatarBase64(userId: String, avatarBase64: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val session = requireActiveSessionMatches(userId)
                require(avatarBase64.length <= 160_000) { "Avatar image exceeds sync size limit." }

                val docPath = "users/${Uri.encode(userId)}"
                val existing = getDocument(docPath)
                if (existing == null) {
                    val safeName = session.displayName.trim()
                        .ifBlank { session.email.substringBefore('@') }
                        .ifBlank { "Resolve Focus User" }
                        .take(80)
                    val createFields = linkedMapOf(
                        "userId" to strField(userId),
                        "schemaVersion" to intField(CURRENT_SYNC_SCHEMA_VERSION),
                        "displayName" to strField(safeName),
                        "avatarBase64" to strField(avatarBase64)
                    ).apply {
                        if (session.email.isNotBlank()) put("email", strField(session.email.take(160)))
                        if (session.photoUrl.isNotBlank()) put("photoUrl", strField(session.photoUrl.take(600)))
                    }
                    commitDocument(relativeDocPath = docPath, fields = createFields, isCreate = true)
                } else {
                    val updateFields = linkedMapOf(
                        "schemaVersion" to intField(CURRENT_SYNC_SCHEMA_VERSION),
                        "avatarBase64" to strField(avatarBase64)
                    )
                    commitDocument(relativeDocPath = docPath, fields = updateFields, isCreate = false)
                }
                val currentProfile = _profileState.value.second
                if (currentProfile != null && _profileState.value.first == userId) {
                    _profileState.value = userId to currentProfile.copy(avatarBase64 = avatarBase64)
                }
                refreshSignal.tryEmit(Unit)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    override suspend fun updateDisplayName(userId: String, displayName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val session = requireActiveSessionMatches(userId)
                val safeName = displayName.trim().ifBlank { "Resolve Focus User" }.take(80)

                val docPath = "users/${Uri.encode(userId)}"
                val existing = getDocument(docPath)
                if (existing == null) {
                    val createFields = linkedMapOf(
                        "userId" to strField(userId),
                        "schemaVersion" to intField(CURRENT_SYNC_SCHEMA_VERSION),
                        "displayName" to strField(safeName)
                    ).apply {
                        if (session.email.isNotBlank()) put("email", strField(session.email.take(160)))
                        if (session.photoUrl.isNotBlank()) put("photoUrl", strField(session.photoUrl.take(600)))
                    }
                    commitDocument(relativeDocPath = docPath, fields = createFields, isCreate = true)
                } else {
                    val updateFields = linkedMapOf(
                        "schemaVersion" to intField(CURRENT_SYNC_SCHEMA_VERSION),
                        "displayName" to strField(safeName)
                    )
                    commitDocument(relativeDocPath = docPath, fields = updateFields, isCreate = false)
                }
                val currentProfile = _profileState.value.second
                if (currentProfile != null && _profileState.value.first == userId) {
                    _profileState.value = userId to currentProfile.copy(displayName = safeName)
                }
                refreshSignal.tryEmit(Unit)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Result.failure(t)
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
        showFlipClockShortcut: Boolean,
        allowSelectedApps: Boolean,
        phoneAccessEnabled: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            requireActiveSessionMatches(userId)
            val commandId = "cmd_" + UUID.randomUUID().toString().replace("-", "")
            val safeOriginName = originDeviceName.trim().ifBlank { "Android Tablet" }.take(80)
            val safeFreeMins = freeUseDurationMinutes.coerceIn(0L, 1440L)
            val safeLockMins = lockDurationMinutes.coerceIn(1L, 1440L)
            val safeIssuedMs = issuedAtWallMs.coerceAtLeast(0L)
            val safeIntendedMs = intendedStartWallMs.coerceAtLeast(0L)
            val safeFreeDeadline = freeUseDeadlineWallMs.coerceAtLeast(0L)
            val safeLockExpire = lockExpirationWallMs.coerceAtLeast(0L)
            val safeScheduleId = scheduleId.trim().take(128)
            val safeOccurrenceId = occurrenceId.trim().take(128)

            val createFields = linkedMapOf(
                "userId" to strField(userId),
                "schemaVersion" to intField(CURRENT_SYNC_SCHEMA_VERSION),
                "commandId" to strField(commandId),
                "originDeviceId" to strField(originDeviceId),
                "originDeviceName" to strField(safeOriginName),
                "mode" to strField(mode.name),
                "freeUseDurationMinutes" to intField(safeFreeMins),
                "lockDurationMinutes" to intField(safeLockMins),
                "issuedAtWallMs" to intField(safeIssuedMs),
                "intendedStartWallMs" to intField(safeIntendedMs),
                "freeUseDeadlineWallMs" to intField(safeFreeDeadline),
                "lockExpirationWallMs" to intField(safeLockExpire),
                "scheduleId" to strField(safeScheduleId),
                "occurrenceId" to strField(safeOccurrenceId),
                "strictMode" to boolField(strictMode),
                "showFlipClockShortcut" to boolField(showFlipClockShortcut),
                "allowSelectedApps" to boolField(allowSelectedApps),
                "phoneAccessEnabled" to boolField(phoneAccessEnabled)
            )
            commitDocument(
                relativeDocPath = "users/${Uri.encode(userId)}/lock_commands/${Uri.encode(commandId)}",
                fields = createFields,
                isCreate = true
            )

            val legacyPath = "users/${Uri.encode(userId)}/sync/${CloudAccountRepository.LOCK_COMMAND_DOC_ID}"
            val existingLegacy = getDocument(legacyPath)
            if (existingLegacy == null) {
                commitDocument(relativeDocPath = legacyPath, fields = createFields, isCreate = true)
            } else {
                val updateFields = createFields.filterKeys { it != "userId" }
                commitDocument(relativeDocPath = legacyPath, fields = updateFields, isCreate = false)
            }
            fastRefreshSignal.tryEmit(Unit)
            refreshSignal.tryEmit(Unit)
            Result.success(commandId)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun upsertDelayedPreset(userId: String, preset: CloudDelayedPreset): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                requireActiveSessionMatches(userId)
                val safePresetId = preset.presetId.trim().take(128)
                require(safePresetId.isNotBlank()) { "Preset ID must not be blank." }
                val safeName = preset.name.trim().ifBlank { "Custom Preset" }.take(60)
                val safeFree = preset.freeUseMinutes.coerceIn(1L, 1440L)
                val safeLock = preset.lockDurationMinutes.coerceIn(1L, 1440L)
                val safeUpdatedWall = preset.updatedAtWallMs.coerceAtLeast(0L)

                val docPath = "users/${Uri.encode(userId)}/presets/${Uri.encode(safePresetId)}"
                val existing = getDocument(docPath)
                if (existing == null) {
                    val createFields = linkedMapOf(
                        "userId" to strField(userId),
                        "schemaVersion" to intField(CURRENT_SYNC_SCHEMA_VERSION),
                        "presetId" to strField(safePresetId),
                        "name" to strField(safeName),
                        "freeUseMinutes" to intField(safeFree),
                        "lockDurationMinutes" to intField(safeLock),
                        "originDeviceId" to strField(preset.originDeviceId),
                        "updatedAtWallMs" to intField(safeUpdatedWall),
                        "deleted" to boolField(preset.deleted)
                    )
                    commitDocument(relativeDocPath = docPath, fields = createFields, isCreate = true)
                } else {
                    val updateFields = linkedMapOf(
                        "schemaVersion" to intField(CURRENT_SYNC_SCHEMA_VERSION),
                        "name" to strField(safeName),
                        "freeUseMinutes" to intField(safeFree),
                        "lockDurationMinutes" to intField(safeLock),
                        "originDeviceId" to strField(preset.originDeviceId),
                        "updatedAtWallMs" to intField(safeUpdatedWall),
                        "deleted" to boolField(preset.deleted)
                    )
                    commitDocument(relativeDocPath = docPath, fields = updateFields, isCreate = false)
                }
                refreshSignal.tryEmit(Unit)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    override suspend fun upsertSyncedSchedule(userId: String, schedule: CloudScheduleRecord): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                requireActiveSessionMatches(userId)
                val safeScheduleId = schedule.scheduleId.trim().take(128)
                require(safeScheduleId.isNotBlank()) { "Schedule ID must not be blank." }
                val safeName = schedule.name.trim().ifBlank { "Focus Schedule" }.take(80)

                val docPath = "users/${Uri.encode(userId)}/schedules/${Uri.encode(safeScheduleId)}"
                val existing = getDocument(docPath)
                val mutableFields = linkedMapOf(
                    "schemaVersion" to intField(CURRENT_SYNC_SCHEMA_VERSION),
                    "name" to strField(safeName),
                    "type" to strField(schedule.resolveScheduleType().name),
                    "oneTimeDateEpochDay" to intField(schedule.oneTimeDateEpochDay),
                    "weekdaysMask" to intField(schedule.weekdaysMask.coerceIn(0, 127).toLong()),
                    "startHour" to intField(schedule.startHour.coerceIn(0, 23).toLong()),
                    "startMinute" to intField(schedule.startMinute.coerceIn(0, 59).toLong()),
                    "lockDurationMinutes" to intField(schedule.lockDurationMinutes.coerceIn(1L, 1440L)),
                    "enabled" to boolField(schedule.enabled),
                    "syncAcrossDevices" to boolField(schedule.syncAcrossDevices),
                    "strictMode" to boolField(schedule.strictMode),
                    "allowSelectedApps" to boolField(schedule.allowSelectedApps),
                    "showFlipClockShortcut" to boolField(schedule.showFlipClockShortcut),
                    "originDeviceId" to strField(schedule.originDeviceId),
                    "updatedAtWallMs" to intField(schedule.updatedAtWallMs.coerceAtLeast(0L)),
                    "deleted" to boolField(schedule.deleted)
                )
                if (existing == null) {
                    val createFields = linkedMapOf(
                        "userId" to strField(userId),
                        "scheduleId" to strField(safeScheduleId)
                    ).apply { putAll(mutableFields) }
                    commitDocument(relativeDocPath = docPath, fields = createFields, isCreate = true)
                } else {
                    commitDocument(relativeDocPath = docPath, fields = mutableFields, isCreate = false)
                }
                refreshSignal.tryEmit(Unit)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    override suspend fun upsertLockProfile(userId: String, profile: CloudLockProfileSettings): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                requireActiveSessionMatches(userId)
                val safeMode = profile.resolveLockMode().name
                val safePackages = profile.allowedPackages
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it.length <= 160 }
                    .distinct()
                    .take(200)

                val docPath = "users/${Uri.encode(userId)}/lock_profiles/${Uri.encode(safeMode)}"
                val existing = getDocument(docPath)
                val mutableFields = linkedMapOf(
                    "schemaVersion" to intField(CURRENT_SYNC_SCHEMA_VERSION),
                    "strictMode" to boolField(profile.strictMode),
                    "allowSelectedApps" to boolField(profile.allowSelectedApps),
                    "showFlipClockShortcut" to boolField(profile.showFlipClockShortcut),
                    "allowedPackages" to strArrayField(safePackages),
                    "originDeviceId" to strField(profile.originDeviceId),
                    "updatedAtWallMs" to intField(profile.updatedAtWallMs.coerceAtLeast(0L))
                )
                if (existing == null) {
                    val createFields = linkedMapOf(
                        "userId" to strField(userId),
                        "profileMode" to strField(safeMode)
                    ).apply { putAll(mutableFields) }
                    commitDocument(relativeDocPath = docPath, fields = createFields, isCreate = true)
                } else {
                    commitDocument(relativeDocPath = docPath, fields = mutableFields, isCreate = false)
                }
                refreshSignal.tryEmit(Unit)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    override suspend fun upsertFlipClockSettings(
        userId: String,
        settings: CloudFlipClockSettings
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            requireActiveSessionMatches(userId)
            val safeZoom = settings.zoomFraction.coerceIn(0.5, 1.4)
            val docPath = "users/${Uri.encode(userId)}/sync/${CloudAccountRepository.FLIP_CLOCK_SETTINGS_DOC_ID}"
            val existing = getDocument(docPath)
            val mutableFields = linkedMapOf(
                "schemaVersion" to intField(CURRENT_SYNC_SCHEMA_VERSION),
                "use24HourFormat" to boolField(settings.use24HourFormat),
                "keepScreenAwake" to boolField(settings.keepScreenAwake),
                "showDate" to boolField(settings.showDate),
                "autoRepeat" to boolField(settings.autoRepeat),
                "zoomFraction" to doubleField(safeZoom),
                "originDeviceId" to strField(settings.originDeviceId),
                "updatedAtWallMs" to intField(settings.updatedAtWallMs.coerceAtLeast(0L))
            )
            if (existing == null) {
                val createFields = linkedMapOf(
                    "userId" to strField(userId),
                    "docId" to strField(CloudAccountRepository.FLIP_CLOCK_SETTINGS_DOC_ID)
                ).apply { putAll(mutableFields) }
                commitDocument(relativeDocPath = docPath, fields = createFields, isCreate = true)
            } else {
                commitDocument(relativeDocPath = docPath, fields = mutableFields, isCreate = false)
            }
            refreshSignal.tryEmit(Unit)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun upsertFlipTimerPreset(userId: String, preset: CloudFlipTimerPreset): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                requireActiveSessionMatches(userId)
                val safePresetId = preset.presetId.trim().take(128)
                require(safePresetId.isNotBlank()) { "Preset ID must not be blank." }
                val safeName = preset.name.trim().ifBlank { "Timer Preset" }.take(60)
                val safeDurationSec = preset.durationSeconds.coerceIn(1L, 86_399L)
                val safeSortOrder = preset.sortOrder.coerceIn(0, 1000).toLong()

                val docPath = "users/${Uri.encode(userId)}/flip_timer_presets/${Uri.encode(safePresetId)}"
                val existing = getDocument(docPath)
                val mutableFields = linkedMapOf(
                    "schemaVersion" to intField(CURRENT_SYNC_SCHEMA_VERSION),
                    "name" to strField(safeName),
                    "durationSeconds" to intField(safeDurationSec),
                    "builtIn" to boolField(preset.builtIn),
                    "sortOrder" to intField(safeSortOrder),
                    "originDeviceId" to strField(preset.originDeviceId),
                    "updatedAtWallMs" to intField(preset.updatedAtWallMs.coerceAtLeast(0L)),
                    "deleted" to boolField(preset.deleted)
                )
                if (existing == null) {
                    val createFields = linkedMapOf(
                        "userId" to strField(userId),
                        "presetId" to strField(safePresetId)
                    ).apply { putAll(mutableFields) }
                    commitDocument(relativeDocPath = docPath, fields = createFields, isCreate = true)
                } else {
                    commitDocument(relativeDocPath = docPath, fields = mutableFields, isCreate = false)
                }
                refreshSignal.tryEmit(Unit)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    private fun startPollingForUser(uid: String) {
        fastLockPollJob?.cancel()
        pollJob?.cancel()

        // Lane A: High-frequency lock command listener (2s) with fault-isolated fallback
        fastLockPollJob = scope.launch {
            Tls12SocketFactory.ensureProviderInstalled(appContext)
            var consecutiveFailures = 0
            while (currentUserId == uid) {
                val cycleOk = try {
                    pollLockCommandsOnce(uid)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.w(TAG, "Fast lock command poll error", t)
                    if (t is CloudSyncException && t.kind == CloudSyncException.Kind.UNAUTHENTICATED) {
                        signOut()
                        break
                    }
                    false
                }
                consecutiveFailures = if (cycleOk) 0 else (consecutiveFailures + 1).coerceAtMost(5)
                val waitMs = if (cycleOk) {
                    FAST_LOCK_POLL_INTERVAL_MS
                } else {
                    (FAST_LOCK_POLL_INTERVAL_MS * (consecutiveFailures + 1)).coerceAtMost(15_000L)
                }
                withTimeoutOrNull(waitMs) {
                    fastRefreshSignal.first()
                }
            }
        }

        // Lane B: Metadata & settings poll (10s) with strict per-endpoint fault isolation
        pollJob = scope.launch {
            Tls12SocketFactory.ensureProviderInstalled(appContext)
            var consecutiveFailures = 0
            while (currentUserId == uid) {
                val cycleOk = try {
                    pollMetadataOnce(uid)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.w(TAG, "REST metadata poll failed", t)
                    if (t is CloudSyncException && t.kind == CloudSyncException.Kind.UNAUTHENTICATED) {
                        signOut()
                        break
                    }
                    false
                }
                consecutiveFailures = if (cycleOk) 0 else (consecutiveFailures + 1).coerceAtMost(4)
                val waitMs = if (cycleOk) {
                    METADATA_POLL_INTERVAL_MS
                } else {
                    (METADATA_POLL_INTERVAL_MS * (consecutiveFailures + 1)).coerceAtMost(35_000L)
                }
                withTimeoutOrNull(waitMs) {
                    refreshSignal.first()
                }
            }
        }
    }

    private suspend inline fun <T> pollStep(crossinline block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (t is CloudSyncException && t.kind == CloudSyncException.Kind.UNAUTHENTICATED) {
                throw t
            }
            Log.w(TAG, "REST poll step error: ${t.message}")
            null
        }
    }

    private suspend fun pollLockCommandsOnce(uid: String): Boolean {
        val encodedUid = Uri.encode(uid)
        var anySucceeded = false

        val latestOk = pollStep {
            val latestCmdDoc = getDocument("users/$encodedUid/sync/${CloudAccountRepository.LOCK_COMMAND_DOC_ID}")
            _latestLockCommandState.value = uid to latestCmdDoc?.toCloudLockCommand(uid)
            true
        }
        if (latestOk == true) anySucceeded = true

        val recentOk = pollStep {
            val docs = try {
                queryRecentLockCommands(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (t is CloudSyncException && t.kind == CloudSyncException.Kind.UNAUTHENTICATED) {
                    throw t
                }
                // Resilient fallback when ordered runQuery is temporarily unavailable on older devices
                listCollectionDocuments("users/$encodedUid/lock_commands", pageSize = 30)
            }
            _recentLockCommandsState.value = uid to docs.map { it.toCloudLockCommand(uid) }
            true
        }
        if (recentOk == true) anySucceeded = true

        return anySucceeded
    }

    private suspend fun pollMetadataOnce(uid: String): Boolean {
        val encodedUid = Uri.encode(uid)
        var anySucceeded = false

        pollStep {
            getDocument("users/$encodedUid")?.let { doc ->
                _profileState.value = uid to doc.toCloudUserProfile(uid)
            }
            true
        }?.let { anySucceeded = true }

        pollStep {
            val deviceDocs = listCollectionDocuments("users/$encodedUid/devices", pageSize = 50)
            _devicesState.value = uid to deviceDocs.map { it.toCloudDeviceInfo(uid) }
            true
        }?.let { anySucceeded = true }

        pollStep {
            val presetDocs = listCollectionDocuments("users/$encodedUid/presets", pageSize = 100)
            _delayedPresetsState.value = uid to presetDocs.map { it.toCloudDelayedPreset(uid) }
            true
        }?.let { anySucceeded = true }

        pollStep {
            val scheduleDocs = listCollectionDocuments("users/$encodedUid/schedules", pageSize = 100)
            _schedulesState.value = uid to scheduleDocs.map { it.toCloudScheduleRecord(uid) }
            true
        }?.let { anySucceeded = true }

        pollStep {
            val profileDocs = listCollectionDocuments("users/$encodedUid/lock_profiles", pageSize = 20)
            _lockProfilesState.value = uid to profileDocs.map { it.toCloudLockProfile(uid) }
            true
        }?.let { anySucceeded = true }

        pollStep {
            val flipClockDoc = getDocument("users/$encodedUid/sync/${CloudAccountRepository.FLIP_CLOCK_SETTINGS_DOC_ID}")
            _flipClockSettingsState.value = uid to flipClockDoc?.toCloudFlipClockSettings(uid)
            true
        }?.let { anySucceeded = true }

        pollStep {
            val flipPresetDocs = listCollectionDocuments("users/$encodedUid/flip_timer_presets", pageSize = 100)
            _flipTimerPresetsState.value = uid to flipPresetDocs.map { it.toCloudFlipTimerPreset(uid) }
            true
        }?.let { anySucceeded = true }

        return anySucceeded
    }

    private fun resetPolledCaches() {
        _profileState.value = "" to null
        _devicesState.value = "" to emptyList()
        _latestLockCommandState.value = "" to null
        _recentLockCommandsState.value = "" to emptyList()
        _delayedPresetsState.value = "" to emptyList()
        _schedulesState.value = "" to emptyList()
        _lockProfilesState.value = "" to emptyList()
        _flipClockSettingsState.value = "" to null
        _flipTimerPresetsState.value = "" to emptyList()
    }

    private suspend fun requireActiveSessionMatches(userId: String): StoredRestAuthSession {
        restorePersistedSessionIfAny()
        val session = cachedSession
            ?: throw CloudSyncException(CloudSyncException.Kind.UNAUTHENTICATED, "User must be signed in before syncing.")
        check(session.uid == userId) { "Signed-in user mismatch." }
        return session
    }

    private suspend fun getValidIdToken(forceRefresh: Boolean = false): String {
        return tokenMutex.withLock {
            val session = cachedSession ?: preferencesRepository.getRestAuthSessionSnapshot()?.also {
                cachedSession = it
            } ?: throw CloudSyncException(
                CloudSyncException.Kind.UNAUTHENTICATED,
                "Please sign in with Google again."
            )

            val now = System.currentTimeMillis()
            if (!forceRefresh && session.idToken.isNotBlank() && now + TOKEN_REFRESH_LEAD_MS < session.expiresAtWallMs) {
                return@withLock session.idToken
            }

            val apiKey = requireApiKey()
            val formBody = "grant_type=refresh_token&refresh_token=${URLEncoder.encode(session.refreshToken, "UTF-8")}"
            val response = executeRequest(
                urlString = "https://securetoken.googleapis.com/v1/token?key=$apiKey",
                method = "POST",
                contentType = "application/x-www-form-urlencoded",
                bearerToken = null,
                body = formBody
            )
            if (response.code !in 200..299) {
                val upper = response.body.uppercase()
                if (response.code in 400..403 && (upper.contains("INVALID_REFRESH_TOKEN") ||
                        upper.contains("TOKEN_EXPIRED") ||
                        upper.contains("USER_DISABLED") ||
                        upper.contains("USER_NOT_FOUND"))
                ) {
                    cachedSession = null
                    preferencesRepository.clearRestAuthSession()
                    _authState.value = null
                    throw CloudSyncException(
                        CloudSyncException.Kind.UNAUTHENTICATED,
                        "Session expired. Please sign in with Google again."
                    )
                }
                throw mapRestHttpError(response.code, response.body, "Could not refresh Google session.")
            }

            val json = JSONObject(response.body)
            val newIdToken = json.optString("id_token").trim()
            val newRefreshToken = json.optString("refresh_token").trim().ifBlank { session.refreshToken }
            val expiresInSec = json.optString("expires_in", "3600").toLongOrNull() ?: 3600L
            if (newIdToken.isEmpty()) {
                throw CloudSyncException(CloudSyncException.Kind.UNAUTHENTICATED, "Invalid token refresh response.")
            }

            val updated = session.copy(
                idToken = newIdToken,
                refreshToken = newRefreshToken,
                expiresAtWallMs = System.currentTimeMillis() + (expiresInSec.coerceAtLeast(60L) * 1000L)
            )
            cachedSession = updated
            preferencesRepository.saveRestAuthSession(updated)
            newIdToken
        }
    }

    private suspend fun authorizedRequest(
        urlString: String,
        method: String,
        body: String? = null
    ): RestHttpResponse {
        val token = getValidIdToken(forceRefresh = false)
        val first = executeJsonRequest(urlString, method, token, body)
        if (first.code == 401) {
            val freshToken = getValidIdToken(forceRefresh = true)
            return executeJsonRequest(urlString, method, freshToken, body)
        }
        return first
    }

    private suspend fun getDocument(relativeDocPath: String): JSONObject? {
        val url = "${firestoreBaseUrl()}/$relativeDocPath"
        val response = authorizedRequest(url, "GET")
        if (response.code == 404) return null
        if (response.code !in 200..299) {
            throw mapRestHttpError(response.code, response.body, "Could not read synced data.")
        }
        return JSONObject(response.body)
    }

    private suspend fun deleteDocument(relativeDocPath: String) {
        val url = "${firestoreBaseUrl()}/$relativeDocPath"
        val response = authorizedRequest(url, "DELETE")
        if (response.code != 404 && response.code !in 200..299) {
            throw mapRestHttpError(response.code, response.body, "Could not remove device.")
        }
    }

    private suspend fun listCollectionDocuments(
        relativeCollectionPath: String,
        pageSize: Int = 100
    ): List<JSONObject> {
        val results = mutableListOf<JSONObject>()
        var pageToken: String? = null
        var pages = 0
        do {
            val urlBuilder = StringBuilder("${firestoreBaseUrl()}/$relativeCollectionPath?pageSize=$pageSize")
            if (!pageToken.isNullOrBlank()) {
                urlBuilder.append("&pageToken=").append(Uri.encode(pageToken))
            }
            val response = authorizedRequest(urlBuilder.toString(), "GET")
            if (response.code == 404) return emptyList()
            if (response.code !in 200..299) {
                throw mapRestHttpError(response.code, response.body, "Could not list synced items.")
            }
            val json = JSONObject(response.body)
            val docs = json.optJSONArray("documents")
            if (docs != null) {
                for (i in 0 until docs.length()) {
                    docs.optJSONObject(i)?.let { results.add(it) }
                }
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            pages++
        } while (pageToken != null && pages < 5)
        return results
    }

    private suspend fun queryRecentLockCommands(userId: String): List<JSONObject> {
        val url = "${firestoreBaseUrl()}/users/${Uri.encode(userId)}:runQuery"
        val queryPayload = JSONObject().apply {
            put("structuredQuery", JSONObject().apply {
                put("from", JSONArray().put(JSONObject().put("collectionId", "lock_commands")))
                put(
                    "orderBy",
                    JSONArray().put(
                        JSONObject()
                            .put("field", JSONObject().put("fieldPath", "issuedAtWallMs"))
                            .put("direction", "DESCENDING")
                    )
                )
                put("limit", 20)
            })
        }
        val response = authorizedRequest(url, "POST", queryPayload.toString())
        if (response.code == 404) return emptyList()
        if (response.code !in 200..299) {
            throw mapRestHttpError(response.code, response.body, "Could not fetch recent lock commands.")
        }
        val array = JSONArray(response.body)
        return buildList {
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.optJSONObject("document")?.let { add(it) }
            }
        }
    }

    private suspend fun commitDocument(
        relativeDocPath: String,
        fields: Map<String, JSONObject>,
        isCreate: Boolean,
        createTransforms: List<String> = listOf("createdAt", "updatedAt"),
        updateTransforms: List<String> = listOf("updatedAt")
    ) {
        val parentPath = firestoreDocumentsParentPath()
        val commitUrl = "https://firestore.googleapis.com/v1/$parentPath:commit"

        suspend fun attempt(createFlag: Boolean): RestHttpResponse {
            val fieldsJson = JSONObject()
            fields.forEach { (k, v) -> fieldsJson.put(k, v) }
            val writeObj = JSONObject().apply {
                put("update", JSONObject().apply {
                    put("name", "$parentPath/$relativeDocPath")
                    put("fields", fieldsJson)
                })
                if (!createFlag && fields.isNotEmpty()) {
                    put("updateMask", JSONObject().apply {
                        put("fieldPaths", JSONArray(fields.keys.toList()))
                    })
                }
                val transforms = if (createFlag) createTransforms else updateTransforms
                if (transforms.isNotEmpty()) {
                    put("updateTransforms", JSONArray().apply {
                        transforms.forEach { fieldPath ->
                            put(JSONObject().apply {
                                put("fieldPath", fieldPath)
                                put("setToServerValue", "REQUEST_TIME")
                            })
                        }
                    })
                }
                put("currentDocument", JSONObject().apply {
                    put("exists", !createFlag)
                })
            }
            val payload = JSONObject().put("writes", JSONArray().put(writeObj))
            return authorizedRequest(commitUrl, "POST", payload.toString())
        }

        val response = attempt(isCreate)
        if (isCreate && (response.code == 409 || response.code == 400) &&
            response.body.contains("ALREADY_EXISTS", ignoreCase = true)
        ) {
            // Document raced into existence; retry safely as partial update
            val updateOnlyFields = fields.filterKeys {
                it != "userId" && it != "presetId" && it != "scheduleId" && it != "profileMode" && it != "docId"
            }
            if (updateOnlyFields.isNotEmpty()) {
                val fieldsJson = JSONObject().apply { updateOnlyFields.forEach { (k, v) -> put(k, v) } }
                val retryWrite = JSONObject().apply {
                    put("update", JSONObject().apply {
                        put("name", "$parentPath/$relativeDocPath")
                        put("fields", fieldsJson)
                    })
                    put("updateMask", JSONObject().apply {
                        put("fieldPaths", JSONArray(updateOnlyFields.keys.toList()))
                    })
                    if (updateTransforms.isNotEmpty()) {
                        put("updateTransforms", JSONArray().apply {
                            updateTransforms.forEach { fieldPath ->
                                put(JSONObject().apply {
                                    put("fieldPath", fieldPath)
                                    put("setToServerValue", "REQUEST_TIME")
                                })
                            }
                        })
                    }
                    put("currentDocument", JSONObject().apply { put("exists", true) })
                }
                val retryResp = authorizedRequest(
                    commitUrl,
                    "POST",
                    JSONObject().put("writes", JSONArray().put(retryWrite)).toString()
                )
                if (retryResp.code in 200..299) return
                throw mapRestHttpError(retryResp.code, retryResp.body, "Could not update cloud sync.")
            }
        }
        if (response.code !in 200..299) {
            throw mapRestHttpError(response.code, response.body, "Could not save synced data.")
        }
    }

    private fun firestoreDocumentsParentPath(): String {
        val projectId = appContext.getString(R.string.project_id).trim()
        val databaseId = appContext.getString(R.string.firestore_database_id).trim()
        require(projectId.isNotEmpty() && databaseId.isNotEmpty()) {
            "Firestore project or database configuration is missing."
        }
        return "projects/$projectId/databases/$databaseId/documents"
    }

    private fun firestoreBaseUrl(): String =
        "https://firestore.googleapis.com/v1/${firestoreDocumentsParentPath()}"

    private fun requireApiKey(): String {
        val key = appContext.getString(R.string.google_api_key).trim()
        require(key.isNotEmpty()) { "Cloud API configuration is missing." }
        return key
    }

    private fun executeJsonRequest(
        urlString: String,
        method: String,
        bearerToken: String?,
        body: String?
    ): RestHttpResponse = executeRequest(
        urlString = urlString,
        method = method,
        contentType = "application/json; charset=UTF-8",
        bearerToken = bearerToken,
        body = body
    )

    private fun executeRequest(
        urlString: String,
        method: String,
        contentType: String,
        bearerToken: String?,
        body: String?
    ): RestHttpResponse {
        require(urlString.startsWith("https://")) { "Only HTTPS endpoints are permitted." }
        Tls12SocketFactory.ensureProviderInstalled(appContext)
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            if (this is javax.net.ssl.HttpsURLConnection) {
                Tls12SocketFactory.applyIfNeeded(this)
            }
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 15_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            if (!bearerToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
            }
        }
        return try {
            if (body != null) {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }
            val code = conn.responseCode
            val dateHeaderMs = runCatching { conn.getHeaderFieldDate("Date", 0L) }.getOrDefault(0L)
            if (dateHeaderMs > 1_500_000_000_000L) {
                val offset = dateHeaderMs - System.currentTimeMillis()
                if (!hasServerClockSample.getAndSet(true)) {
                    serverOffsetMs.set(offset)
                } else {
                    val prev = serverOffsetMs.get()
                    serverOffsetMs.set(
                        if (kotlin.math.abs(offset - prev) > 3_000L) offset else (prev + offset) / 2L
                    )
                }
            }
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val responseText = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }.orEmpty()
            RestHttpResponse(code, responseText)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            throw CloudSyncException(
                CloudSyncException.Kind.UNAVAILABLE,
                "Network unavailable — sync will resume automatically online.",
                t
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun mapRestHttpError(code: Int, body: String, fallback: String): CloudSyncException {
        val kind = when (code) {
            401 -> CloudSyncException.Kind.UNAUTHENTICATED
            403 -> CloudSyncException.Kind.PERMISSION_DENIED
            429 -> CloudSyncException.Kind.QUOTA_EXCEEDED
            500, 502, 503, 504 -> CloudSyncException.Kind.UNAVAILABLE
            else -> CloudSyncException.Kind.OTHER
        }
        val serverMsg = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        val readable = when (kind) {
            CloudSyncException.Kind.PERMISSION_DENIED,
            CloudSyncException.Kind.UNAUTHENTICATED ->
                "Account permission check failed. Please sign in again."
            CloudSyncException.Kind.UNAVAILABLE ->
                "Network unavailable — sync will resume automatically online."
            CloudSyncException.Kind.QUOTA_EXCEEDED ->
                "Daily free cloud sync quota reached. Resets tomorrow."
            CloudSyncException.Kind.OTHER ->
                serverMsg.ifBlank { fallback }
        }
        return CloudSyncException(kind, readable)
    }

    private data class RestHttpResponse(val code: Int, val body: String)

    // --- Firestore REST Field Serialization & Deserialization ---

    private fun strField(value: String): JSONObject = JSONObject().put("stringValue", value)

    private fun intField(value: Long): JSONObject = JSONObject().put("integerValue", value.toString())

    private fun boolField(value: Boolean): JSONObject = JSONObject().put("booleanValue", value)

    private fun doubleField(value: Double): JSONObject = JSONObject().put("doubleValue", value)

    private fun strArrayField(items: List<String>): JSONObject {
        val arrayObj = JSONObject()
        if (items.isNotEmpty()) {
            val arr = JSONArray()
            items.forEach { arr.put(strField(it)) }
            arrayObj.put("values", arr)
        }
        return JSONObject().put("arrayValue", arrayObj)
    }

    private fun JSONObject.fieldsObj(): JSONObject = optJSONObject("fields") ?: JSONObject()

    private fun JSONObject.firestoreDocId(): String = optString("name").substringAfterLast('/')

    private fun JSONObject.firestoreString(key: String, default: String = ""): String {
        val field = optJSONObject(key) ?: return default
        return if (field.has("stringValue")) field.optString("stringValue", default) else default
    }

    private fun JSONObject.firestoreLong(key: String, default: Long = 0L): Long {
        val field = optJSONObject(key) ?: return default
        if (field.has("integerValue")) {
            val str = field.optString("integerValue")
            return str.toLongOrNull() ?: field.optLong("integerValue", default)
        }
        if (field.has("doubleValue")) {
            return field.optDouble("doubleValue", default.toDouble()).toLong()
        }
        return default
    }

    private fun JSONObject.firestoreInt(key: String, default: Int = 0): Int =
        firestoreLong(key, default.toLong()).toInt()

    private fun JSONObject.firestoreDouble(key: String, default: Double = 0.0): Double {
        val field = optJSONObject(key) ?: return default
        if (field.has("doubleValue")) return field.optDouble("doubleValue", default)
        if (field.has("integerValue")) {
            return field.optString("integerValue").toDoubleOrNull() ?: default
        }
        return default
    }

    private fun JSONObject.firestoreBoolean(key: String, default: Boolean = false): Boolean {
        val field = optJSONObject(key) ?: return default
        return if (field.has("booleanValue")) field.optBoolean("booleanValue", default) else default
    }

    private fun JSONObject.firestoreStringList(key: String): List<String> {
        val values = optJSONObject(key)?.optJSONObject("arrayValue")?.optJSONArray("values")
            ?: return emptyList()
        return buildList(values.length()) {
            for (i in 0 until values.length()) {
                val str = values.optJSONObject(i)?.optString("stringValue")
                if (!str.isNullOrEmpty()) add(str)
            }
        }
    }

    private fun JSONObject.toCloudUserProfile(uid: String): CloudUserProfile {
        val f = fieldsObj()
        return CloudUserProfile(
            userId = f.firestoreString("userId", uid),
            schemaVersion = f.firestoreLong("schemaVersion", CURRENT_SYNC_SCHEMA_VERSION),
            displayName = f.firestoreString("displayName"),
            email = f.firestoreString("email"),
            photoUrl = f.firestoreString("photoUrl"),
            avatarBase64 = f.firestoreString("avatarBase64")
        )
    }

    private fun JSONObject.toCloudDeviceInfo(uid: String): CloudDeviceInfo {
        val f = fieldsObj()
        return CloudDeviceInfo(
            deviceId = firestoreDocId(),
            userId = f.firestoreString("userId", uid),
            schemaVersion = f.firestoreLong("schemaVersion", CURRENT_SYNC_SCHEMA_VERSION),
            deviceName = f.firestoreString("deviceName", "Android Device"),
            platform = f.firestoreString("platform", "android"),
            androidVersion = f.firestoreLong("androidVersion", 22L),
            appVersion = f.firestoreString("appVersion"),
            syncEnabled = f.firestoreBoolean("syncEnabled", true)
        )
    }

    private fun JSONObject.toCloudLockCommand(uid: String): CloudLockCommand {
        val f = fieldsObj()
        return CloudLockCommand(
            userId = f.firestoreString("userId", uid),
            schemaVersion = f.firestoreLong("schemaVersion", CURRENT_SYNC_SCHEMA_VERSION),
            commandId = f.firestoreString("commandId", firestoreDocId()),
            originDeviceId = f.firestoreString("originDeviceId"),
            originDeviceName = f.firestoreString("originDeviceName"),
            mode = f.firestoreString("mode", LockMode.INSTANT.name),
            freeUseDurationMinutes = f.firestoreLong("freeUseDurationMinutes", 0L),
            lockDurationMinutes = f.firestoreLong("lockDurationMinutes", 0L),
            issuedAtWallMs = f.firestoreLong("issuedAtWallMs", 0L),
            intendedStartWallMs = f.firestoreLong("intendedStartWallMs", 0L),
            freeUseDeadlineWallMs = f.firestoreLong("freeUseDeadlineWallMs", 0L),
            lockExpirationWallMs = f.firestoreLong("lockExpirationWallMs", 0L),
            scheduleId = f.firestoreString("scheduleId"),
            occurrenceId = f.firestoreString("occurrenceId"),
            strictMode = f.firestoreBoolean("strictMode", false),
            showFlipClockShortcut = f.firestoreBoolean("showFlipClockShortcut", false),
            allowSelectedApps = f.firestoreBoolean("allowSelectedApps", true),
            phoneAccessEnabled = f.firestoreBoolean("phoneAccessEnabled", true)
        )
    }

    private fun JSONObject.toCloudDelayedPreset(uid: String): CloudDelayedPreset {
        val f = fieldsObj()
        return CloudDelayedPreset(
            userId = f.firestoreString("userId", uid),
            schemaVersion = f.firestoreLong("schemaVersion", CURRENT_SYNC_SCHEMA_VERSION),
            presetId = f.firestoreString("presetId", firestoreDocId()),
            name = f.firestoreString("name"),
            freeUseMinutes = f.firestoreLong("freeUseMinutes", 20L),
            lockDurationMinutes = f.firestoreLong("lockDurationMinutes", 30L),
            originDeviceId = f.firestoreString("originDeviceId"),
            updatedAtWallMs = f.firestoreLong("updatedAtWallMs", 0L),
            deleted = f.firestoreBoolean("deleted", false)
        )
    }

    private fun JSONObject.toCloudScheduleRecord(uid: String): CloudScheduleRecord {
        val f = fieldsObj()
        return CloudScheduleRecord(
            userId = f.firestoreString("userId", uid),
            schemaVersion = f.firestoreLong("schemaVersion", CURRENT_SYNC_SCHEMA_VERSION),
            scheduleId = f.firestoreString("scheduleId", firestoreDocId()),
            name = f.firestoreString("name"),
            type = f.firestoreString("type", "DAILY"),
            oneTimeDateEpochDay = f.firestoreLong("oneTimeDateEpochDay", -1L),
            weekdaysMask = f.firestoreInt("weekdaysMask", 0),
            startHour = f.firestoreInt("startHour", 0),
            startMinute = f.firestoreInt("startMinute", 0),
            lockDurationMinutes = f.firestoreLong("lockDurationMinutes", 60L),
            enabled = f.firestoreBoolean("enabled", true),
            syncAcrossDevices = f.firestoreBoolean("syncAcrossDevices", true),
            strictMode = f.firestoreBoolean("strictMode", false),
            allowSelectedApps = f.firestoreBoolean("allowSelectedApps", true),
            showFlipClockShortcut = f.firestoreBoolean("showFlipClockShortcut", false),
            originDeviceId = f.firestoreString("originDeviceId"),
            updatedAtWallMs = f.firestoreLong("updatedAtWallMs", 0L),
            deleted = f.firestoreBoolean("deleted", false)
        )
    }

    private fun JSONObject.toCloudLockProfile(uid: String): CloudLockProfileSettings {
        val f = fieldsObj()
        return CloudLockProfileSettings(
            userId = f.firestoreString("userId", uid),
            schemaVersion = f.firestoreLong("schemaVersion", CURRENT_SYNC_SCHEMA_VERSION),
            profileMode = f.firestoreString("profileMode", firestoreDocId()),
            strictMode = f.firestoreBoolean("strictMode", false),
            allowSelectedApps = f.firestoreBoolean("allowSelectedApps", true),
            showFlipClockShortcut = f.firestoreBoolean("showFlipClockShortcut", false),
            allowedPackages = f.firestoreStringList("allowedPackages"),
            originDeviceId = f.firestoreString("originDeviceId"),
            updatedAtWallMs = f.firestoreLong("updatedAtWallMs", 0L)
        )
    }

    private fun JSONObject.toCloudFlipClockSettings(uid: String): CloudFlipClockSettings {
        val f = fieldsObj()
        return CloudFlipClockSettings(
            userId = f.firestoreString("userId", uid),
            schemaVersion = f.firestoreLong("schemaVersion", CURRENT_SYNC_SCHEMA_VERSION),
            docId = f.firestoreString("docId", CloudAccountRepository.FLIP_CLOCK_SETTINGS_DOC_ID),
            use24HourFormat = f.firestoreBoolean("use24HourFormat", false),
            keepScreenAwake = f.firestoreBoolean("keepScreenAwake", false),
            showDate = f.firestoreBoolean("showDate", false),
            autoRepeat = f.firestoreBoolean("autoRepeat", false),
            zoomFraction = f.firestoreDouble("zoomFraction", 1.0),
            originDeviceId = f.firestoreString("originDeviceId"),
            updatedAtWallMs = f.firestoreLong("updatedAtWallMs", 0L)
        )
    }

    private fun JSONObject.toCloudFlipTimerPreset(uid: String): CloudFlipTimerPreset {
        val f = fieldsObj()
        return CloudFlipTimerPreset(
            userId = f.firestoreString("userId", uid),
            schemaVersion = f.firestoreLong("schemaVersion", CURRENT_SYNC_SCHEMA_VERSION),
            presetId = f.firestoreString("presetId", firestoreDocId()),
            name = f.firestoreString("name"),
            durationSeconds = f.firestoreLong("durationSeconds", 60L),
            builtIn = f.firestoreBoolean("builtIn", false),
            sortOrder = f.firestoreInt("sortOrder", 0),
            originDeviceId = f.firestoreString("originDeviceId"),
            updatedAtWallMs = f.firestoreLong("updatedAtWallMs", 0L),
            deleted = f.firestoreBoolean("deleted", false)
        )
    }

    companion object {
        private const val TAG = "RestCloudSync"
        private const val FAST_LOCK_POLL_INTERVAL_MS = 2_000L
        private const val METADATA_POLL_INTERVAL_MS = 10_000L
        private const val TOKEN_REFRESH_LEAD_MS = 120_000L
    }
}
