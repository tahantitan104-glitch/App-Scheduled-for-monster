package com.example.sync

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface GoogleSignInOutcome {
    data class Success(val idToken: String) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Failed(val message: String) : GoogleSignInOutcome
}

data class SyncSupportStatus(
    val isSupported: Boolean,
    val unsupportedReason: String? = null
)

object GoogleSignInHelper {

    private const val MIN_PLAY_SERVICES_CREDENTIAL_VERSION = 243100000L

    fun checkSyncSupport(context: Context): SyncSupportStatus {
        val appContext = context.applicationContext
        val webClientId = runCatching {
            appContext.getString(R.string.default_web_client_id).trim()
        }.getOrDefault("")
        if (webClientId.isEmpty()) {
            return SyncSupportStatus(
                isSupported = false,
                unsupportedReason = "Google Sign-In configuration is missing on this build."
            )
        }

        val playServicesStatus = try {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext)
        } catch (_: Throwable) {
            ConnectionResult.SERVICE_MISSING
        }

        if (playServicesStatus != ConnectionResult.SUCCESS) {
            val detail = when (playServicesStatus) {
                ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
                ConnectionResult.SERVICE_UPDATING ->
                    "Update Google Play Services on this device to enable Google Sign-In and cloud sync."
                ConnectionResult.SERVICE_DISABLED ->
                    "Google Play Services is disabled on this device."
                else ->
                    "Requires Google Play Services for Google Sign-In and cloud sync."
            }
            return SyncSupportStatus(
                isSupported = false,
                unsupportedReason = detail
            )
        }

        // Android 5.1.1 (API 22): Use legacy Play Services GoogleSignIn + Firebase Auth/Firestore REST APIs
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            val apiKey = runCatching { appContext.getString(R.string.google_api_key).trim() }.getOrDefault("")
            val projectId = runCatching { appContext.getString(R.string.project_id).trim() }.getOrDefault("")
            val dbId = runCatching { appContext.getString(R.string.firestore_database_id).trim() }.getOrDefault("")
            if (apiKey.isEmpty() || projectId.isEmpty() || dbId.isEmpty()) {
                return SyncSupportStatus(
                    isSupported = false,
                    unsupportedReason = "Cloud sync project configuration is incomplete on this build."
                )
            }
            val legacyClientReady = runCatching {
                @Suppress("DEPRECATION")
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build()
                @Suppress("DEPRECATION")
                GoogleSignIn.getClient(appContext, gso)
                true
            }.getOrDefault(false)
            return if (legacyClientReady) {
                SyncSupportStatus(isSupported = true, unsupportedReason = null)
            } else {
                SyncSupportStatus(
                    isSupported = false,
                    unsupportedReason = "Google Sign-In service is unavailable on this Android 5.1.1 device."
                )
            }
        }

        // Android 6.0+ (API 23+): CredentialManager + FirebaseAuth/Firestore SDK
        val gmsVersionCode = try {
            @Suppress("DEPRECATION")
            val pkgInfo = appContext.packageManager.getPackageInfo("com.google.android.gms", 0)
            PackageInfoCompat.getLongVersionCode(pkgInfo)
        } catch (_: Throwable) {
            0L
        }
        if (gmsVersionCode in 1L until MIN_PLAY_SERVICES_CREDENTIAL_VERSION) {
            return SyncSupportStatus(
                isSupported = false,
                unsupportedReason = "Update Google Play Services to use Google Sign-In and multi-device sync."
            )
        }

        val authAvailable = try {
            FirebaseApp.getApps(appContext).isNotEmpty() && FirebaseAuth.getInstance() != null
        } catch (_: Throwable) {
            false
        }
        if (!authAvailable) {
            return SyncSupportStatus(
                isSupported = false,
                unsupportedReason = "Account authentication service is unavailable on this device."
            )
        }

        val credentialManagerReady = try {
            CredentialManager.create(appContext)
            true
        } catch (_: Throwable) {
            false
        }
        if (!credentialManagerReady) {
            return SyncSupportStatus(
                isSupported = false,
                unsupportedReason = "Credential Manager is unavailable on this device."
            )
        }

        return SyncSupportStatus(isSupported = true, unsupportedReason = null)
    }

    suspend fun signInInteractive(context: Context): GoogleSignInOutcome {
        val support = checkSyncSupport(context)
        if (!support.isSupported) {
            return GoogleSignInOutcome.Failed(
                support.unsupportedReason ?: "Google Sign-In is not supported on this device."
            )
        }
        val activity = context.findActivity()
            ?: return GoogleSignInOutcome.Failed("Open Settings inside the app to sign in with Google.")
        if (activity.isFinishing || activity.isDestroyed) {
            return GoogleSignInOutcome.Cancelled
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return signInLegacyPlayServices(activity)
        }

        return try {
            val webClientId = activity.getString(R.string.default_web_client_id).trim()
            if (webClientId.isEmpty()) {
                return GoogleSignInOutcome.Failed("Google Sign-In configuration is missing on this build.")
            }
            val credentialManager = CredentialManager.create(activity)
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val response = credentialManager.getCredential(
                context = activity,
                request = request
            )
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleSignInOutcome.Success(googleIdTokenCredential.idToken)
            } else {
                GoogleSignInOutcome.Failed("Unsupported credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            GoogleSignInOutcome.Cancelled
        } catch (e: NoCredentialException) {
            GoogleSignInOutcome.Failed("No Google account is signed in on this device yet.")
        } catch (e: GetCredentialException) {
            GoogleSignInOutcome.Failed(
                e.localizedMessage ?: "Google Sign-In unavailable on this device."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w("Auth", "Google Sign-In failed safely: ${t.message}", t)
            GoogleSignInOutcome.Failed(
                t.localizedMessage ?: "Google Sign-In is not supported on this device."
            )
        }
    }

    private suspend fun signInLegacyPlayServices(activity: Activity): GoogleSignInOutcome {
        return try {
            withContext(Dispatchers.IO) {
                Tls12SocketFactory.ensureProviderInstalled(activity.applicationContext)
            }
            val deferred = PendingLegacySignInBridge.createNew()
            val intent = Intent(activity, LegacyGoogleSignInActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            activity.startActivity(intent)
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)

            withTimeoutOrNull(120_000L) {
                deferred.await()
            } ?: run {
                PendingLegacySignInBridge.cancelIfPending()
                GoogleSignInOutcome.Failed("Google Sign-In timed out. Please try again.")
            }
        } catch (e: CancellationException) {
            PendingLegacySignInBridge.cancelIfPending()
            throw e
        } catch (t: Throwable) {
            PendingLegacySignInBridge.cancelIfPending()
            Log.w("Auth", "Legacy Google Sign-In failed safely: ${t.message}", t)
            GoogleSignInOutcome.Failed(
                t.localizedMessage ?: "Could not start Google Sign-In on this device."
            )
        }
    }

    suspend fun clearCredentialState(context: Context) {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            val webClientId = runCatching {
                appContext.getString(R.string.default_web_client_id).trim()
            }.getOrDefault("")
            if (webClientId.isNotEmpty()) {
                withTimeoutOrNull(4_000L) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(webClientId)
                            .requestEmail()
                            .build()
                        @Suppress("DEPRECATION")
                        GoogleSignIn.getClient(appContext, gso).signOut().await()
                    }
                }
            }
            return
        }
        runCatching {
            CredentialManager.create(appContext)
                .clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
