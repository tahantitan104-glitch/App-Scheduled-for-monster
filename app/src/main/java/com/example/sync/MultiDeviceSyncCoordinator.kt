package com.example.sync

import android.content.Context
import android.net.Uri
import com.example.data.preferences.ResolvePreferencesRepository
import com.example.data.repository.AllowlistRepository
import com.example.domain.model.LockMode
import com.example.domain.time.ClockProvider
import com.example.session.EngineStartResult
import com.example.session.LockSessionEngine
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinates optional multi-device Google account linking, synced profile avatars,
 * and cross-device lock triggers while preserving strict per-device preferences and
 * zero unauthenticated Firestore listeners.
 */
class MultiDeviceSyncCoordinator(
    private val appContext: Context,
    private val gateway: CloudSyncGateway,
    private val preferencesRepository: ResolvePreferencesRepository,
    private val allowlistRepository: AllowlistRepository,
    private val sessionEngine: LockSessionEngine,
    private val clockProvider: ClockProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(AccountSyncUiState())
    val uiState: StateFlow<AccountSyncUiState> = _uiState.asStateFlow()

    private val _syncEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val syncEvents: SharedFlow<String> = _syncEvents.asSharedFlow()

    private var started = false
    private var authObserveJob: Job? = null
    private var activeUserListenersJob: Job? = null
    private var activeUid: String? = null
    private var lastAuthTokenEpochMs: Long = 0L

    fun start() {
        if (started) return
        started = true

        scope.launch {
            val deviceId = preferencesRepository.getOrCreateDeviceInstallId()
            val syncEnabled = preferencesRepository.isSyncLockEnabledSnapshot()
            _uiState.update {
                it.copy(
                    thisDeviceId = deviceId,
                    thisDeviceName = resolveReadableDeviceName(),
                    syncLockEnabled = syncEnabled
                )
            }
        }

        scope.launch {
            preferencesRepository.syncLockEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(syncLockEnabled = enabled) }
                val uid = activeUid
                if (!uid.isNullOrBlank()) {
                    val deviceId = preferencesRepository.getOrCreateDeviceInstallId()
                    gateway.updateDeviceSyncEnabled(
                        userId = uid,
                        deviceId = deviceId,
                        deviceName = resolveReadableDeviceName(),
                        syncEnabled = enabled
                    )
                }
            }
        }

        authObserveJob = scope.launch {
            gateway.authState.collect { user ->
                handleAuthUserChanged(user)
            }
        }
    }

    private suspend fun handleAuthUserChanged(user: FirebaseUser?) {
        if (user == null) {
            stopUserListeners()
            activeUid = null
            _uiState.update {
                AccountSyncUiState(
                    isSignedIn = false,
                    isAuthBusy = false,
                    thisDeviceId = it.thisDeviceId,
                    thisDeviceName = it.thisDeviceName,
                    syncLockEnabled = it.syncLockEnabled
                )
            }
            return
        }

        val uid = user.uid
        if (activeUid == uid && activeUserListenersJob?.isActive == true) {
            return
        }

        stopUserListeners()
        activeUid = uid
        val deviceId = preferencesRepository.getOrCreateDeviceInstallId()
        val deviceName = resolveReadableDeviceName()
        val syncEnabled = preferencesRepository.isSyncLockEnabledSnapshot()

        _uiState.update {
            it.copy(
                isSignedIn = true,
                isAuthBusy = false,
                userId = uid,
                displayName = user.displayName?.takeIf { n -> n.isNotBlank() }
                    ?: user.email?.substringBefore('@')
                    ?: "Resolve Focus User",
                email = user.email.orEmpty(),
                photoUrl = user.photoUrl?.toString().orEmpty(),
                thisDeviceId = deviceId,
                thisDeviceName = deviceName,
                syncLockEnabled = syncEnabled,
                errorMessage = null
            )
        }

        // Ensure owner profile and current device record exist in Firestore
        scope.launch {
            gateway.ensureUserProfile(user)
            gateway.registerOrUpdateDevice(
                userId = uid,
                deviceId = deviceId,
                deviceName = deviceName,
                syncEnabled = syncEnabled
            )
        }

        startUserListeners(uid, deviceId)
    }

    private fun startUserListeners(uid: String, localDeviceId: String) {
        activeUserListenersJob = scope.launch {
            // 1. Profile & Synced Avatar stream
            launch {
                gateway.observeUserProfile(uid)
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                        _uiState.update {
                            it.copy(errorMessage = CloudAccountRepository.unwrapFirestoreErrorMessage(error, "Sync interrupted."))
                        }
                    }
                    .collect { profile ->
                        if (profile != null) {
                            _uiState.update {
                                it.copy(
                                    displayName = profile.displayName.ifBlank { it.displayName },
                                    email = profile.email.ifBlank { it.email },
                                    photoUrl = profile.photoUrl.ifBlank { it.photoUrl },
                                    avatarBase64 = profile.avatarBase64,
                                    errorMessage = null
                                )
                            }
                        }
                    }
            }

            // 2. Linked Devices stream
            launch {
                gateway.observeDevices(uid)
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                    }
                    .collect { devices ->
                        _uiState.update { state ->
                            state.copy(connectedDevices = devices.sortedByDescending { it.syncEnabled })
                        }
                    }
            }

            // 3. Cross-Device Lock Command stream
            launch {
                gateway.observeLatestLockCommand(uid)
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                    }
                    .collect { command ->
                        if (command != null) {
                            processIncomingLockCommand(command, localDeviceId)
                        }
                    }
            }
        }
    }

    private suspend fun processIncomingLockCommand(
        command: CloudLockCommand,
        localDeviceId: String
    ) {
        if (command.commandId.isBlank()) return
        if (command.originDeviceId == localDeviceId) return

        val lastProcessed = preferencesRepository.getLastProcessedSyncCommandId()
        if (lastProcessed == command.commandId) return

        // Always record this command ID as seen so stale commands never re-fire later
        preferencesRepository.setLastProcessedSyncCommandId(command.commandId)

        // Respect device-level opt-out toggle ("alada alada" independent operation)
        if (!preferencesRepository.isSyncLockEnabledSnapshot()) return

        // Use THIS phone's own local allowlist & phone-access setting
        val localAllowedPackages = allowlistRepository.getAllowedPackages()
        val localPhoneAccess = preferencesRepository.defaultPhoneAccessFlow.first()

        val result = sessionEngine.applyRemoteLockCommand(
            command = command,
            allowedPackages = localAllowedPackages,
            phoneAccessEnabled = localPhoneAccess
        )
        if (result is EngineStartResult.Started) {
            val fromName = command.originDeviceName.ifBlank { "another device" }
            _uiState.update { it.copy(lastSyncedStatusMessage = "Synced lock started from $fromName") }
            _syncEvents.tryEmit("Lock synced from $fromName")
        } else if (result is EngineStartResult.Blocked && result.reason.isNotBlank()) {
            _syncEvents.tryEmit(result.reason)
        }
    }

    suspend fun signInWithGoogle(activityContext: Context): Boolean {
        _uiState.update { it.copy(isAuthBusy = true, errorMessage = null) }
        return when (val outcome = GoogleSignInHelper.signInInteractive(activityContext)) {
            is GoogleSignInOutcome.Success -> {
                val firebaseResult = gateway.signInWithGoogleIdToken(outcome.idToken)
                _uiState.update { it.copy(isAuthBusy = false) }
                if (firebaseResult.isSuccess) {
                    lastAuthTokenEpochMs = clockProvider.wallClockMs()
                    _syncEvents.tryEmit("Google Account connected for multi-device sync.")
                    true
                } else {
                    val msg = firebaseResult.exceptionOrNull()?.localizedMessage
                        ?: "Could not complete Google authentication."
                    _uiState.update { it.copy(errorMessage = msg) }
                    _syncEvents.tryEmit(msg)
                    false
                }
            }
            GoogleSignInOutcome.Cancelled -> {
                _uiState.update { it.copy(isAuthBusy = false) }
                false
            }
            is GoogleSignInOutcome.Failed -> {
                _uiState.update { it.copy(isAuthBusy = false, errorMessage = outcome.message) }
                _syncEvents.tryEmit(outcome.message)
                false
            }
        }
    }

    suspend fun signOutSafely() {
        _uiState.update { it.copy(isAuthBusy = true, errorMessage = null) }
        withContext(NonCancellable) {
            stopUserListeners()
            activeUid = null
            GoogleSignInHelper.clearCredentialState(appContext)
            gateway.signOut()
            _uiState.update {
                AccountSyncUiState(
                    isSignedIn = false,
                    isAuthBusy = false,
                    thisDeviceId = it.thisDeviceId,
                    thisDeviceName = it.thisDeviceName,
                    syncLockEnabled = it.syncLockEnabled
                )
            }
        }
        _syncEvents.tryEmit("Account disconnected. Resolve continues offline cleanly.")
    }

    suspend fun setDeviceSyncEnabled(enabled: Boolean) {
        preferencesRepository.setSyncLockEnabled(enabled)
        _uiState.update { it.copy(syncLockEnabled = enabled) }
        val uid = activeUid ?: return
        val deviceId = preferencesRepository.getOrCreateDeviceInstallId()
        val result = gateway.updateDeviceSyncEnabled(
            userId = uid,
            deviceId = deviceId,
            deviceName = resolveReadableDeviceName(),
            syncEnabled = enabled
        )
        if (result.isFailure) {
            _syncEvents.tryEmit("Saved locally — cloud sync will update when online.")
        }
    }

    suspend fun updateProfilePhoto(uri: Uri) {
        val uid = activeUid ?: return
        _uiState.update { it.copy(isAvatarUploading = true, errorMessage = null) }
        val encoded = AvatarImageProcessor.encodeAvatarFromUri(appContext, uri)
        if (encoded.isFailure) {
            _uiState.update { it.copy(isAvatarUploading = false) }
            _syncEvents.tryEmit("Could not read selected image.")
            return
        }
        val base64 = encoded.getOrThrow()
        val writeResult = gateway.updateAvatarBase64(uid, base64)
        _uiState.update { it.copy(isAvatarUploading = false) }
        if (writeResult.isSuccess) {
            _uiState.update { it.copy(avatarBase64 = base64) }
            _syncEvents.tryEmit("Profile photo synced across connected devices.")
        } else {
            val msg = writeResult.exceptionOrNull()?.let {
                CloudAccountRepository.unwrapFirestoreErrorMessage(it, "Could not sync profile photo.")
            } ?: "Could not sync profile photo."
            _syncEvents.tryEmit(msg)
        }
    }

    suspend fun clearCustomProfilePhoto() {
        val uid = activeUid ?: return
        _uiState.update { it.copy(isAvatarUploading = true) }
        val writeResult = gateway.updateAvatarBase64(uid, "")
        _uiState.update { it.copy(isAvatarUploading = false) }
        if (writeResult.isSuccess) {
            _uiState.update { it.copy(avatarBase64 = "") }
            _syncEvents.tryEmit("Custom profile photo removed.")
        }
    }

    suspend fun publishLockTriggerIfEnabled(
        mode: LockMode,
        freeUseMinutes: Long,
        lockDurationMinutes: Long,
        freeUseDeadlineWallMs: Long,
        lockExpirationWallMs: Long,
        syncAcrossDevices: Boolean
    ) {
        if (!syncAcrossDevices) return
        val uid = activeUid ?: return
        val deviceId = preferencesRepository.getOrCreateDeviceInstallId()
        val deviceName = resolveReadableDeviceName()
        val nowWall = clockProvider.wallClockMs()

        val result = gateway.publishLockCommand(
            userId = uid,
            originDeviceId = deviceId,
            originDeviceName = deviceName,
            mode = mode,
            freeUseDurationMinutes = freeUseMinutes,
            lockDurationMinutes = lockDurationMinutes,
            issuedAtWallMs = nowWall,
            freeUseDeadlineWallMs = freeUseDeadlineWallMs,
            lockExpirationWallMs = lockExpirationWallMs
        )
        if (result.isSuccess) {
            val commandId = result.getOrNull().orEmpty()
            if (commandId.isNotBlank()) {
                preferencesRepository.setLastProcessedSyncCommandId(commandId)
            }
            _syncEvents.tryEmit("Lock synced to connected devices.")
        } else {
            _syncEvents.tryEmit("Locked this device (cloud sync skipped while offline).")
        }
    }

    private fun stopUserListeners() {
        activeUserListenersJob?.cancel()
        activeUserListenersJob = null
    }
}
