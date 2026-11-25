package com.example.sync

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

internal object PendingLegacySignInBridge {
    private val deferredRef = AtomicReference<CompletableDeferred<GoogleSignInOutcome>?>(null)

    fun createNew(): CompletableDeferred<GoogleSignInOutcome> {
        val deferred = CompletableDeferred<GoogleSignInOutcome>()
        deferredRef.getAndSet(deferred)?.complete(GoogleSignInOutcome.Cancelled)
        return deferred
    }

    fun complete(outcome: GoogleSignInOutcome) {
        deferredRef.getAndSet(null)?.complete(outcome)
    }

    fun cancelIfPending() {
        deferredRef.getAndSet(null)?.complete(GoogleSignInOutcome.Cancelled)
    }
}

/**
 * Lightweight transparent Activity used exclusively on Android 5.1.1 (API 22)
 * where Jetpack CredentialManager is unavailable, launching Play Services GoogleSignIn
 * and returning an ID token cleanly to [GoogleSignInHelper].
 */
@Suppress("DEPRECATION")
class LegacyGoogleSignInActivity : ComponentActivity() {

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data: Intent? = result.data
        val outcome = if (result.resultCode == Activity.RESULT_CANCELED && data == null) {
            GoogleSignInOutcome.Cancelled
        } else {
            parseSignInIntentResult(data)
        }
        PendingLegacySignInBridge.complete(outcome)
        finishSafe()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return

        val webClientId = runCatching {
            getString(R.string.default_web_client_id).trim()
        }.getOrDefault("")
        if (webClientId.isEmpty()) {
            PendingLegacySignInBridge.complete(
                GoogleSignInOutcome.Failed("Google Sign-In configuration is missing on this build.")
            )
            finishSafe()
            return
        }

        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .requestProfile()
                .build()
            val client = GoogleSignIn.getClient(this, gso)
            signInLauncher.launch(client.signInIntent)
        } catch (t: Throwable) {
            PendingLegacySignInBridge.complete(
                GoogleSignInOutcome.Failed(
                    t.localizedMessage ?: "Could not open Google account picker on this device."
                )
            )
            finishSafe()
        }
    }

    private fun parseSignInIntentResult(data: Intent?): GoogleSignInOutcome {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken?.trim().orEmpty()
            if (idToken.isNotEmpty()) {
                GoogleSignInOutcome.Success(idToken)
            } else {
                GoogleSignInOutcome.Failed(
                    "Google Sign-In succeeded without an ID token. Check Play Services & network connection."
                )
            }
        } catch (e: ApiException) {
            when (e.statusCode) {
                CommonStatusCodes.CANCELED,
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> GoogleSignInOutcome.Cancelled
                GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS ->
                    GoogleSignInOutcome.Failed("Google Sign-In is already in progress. Try again in a moment.")
                CommonStatusCodes.NETWORK_ERROR ->
                    GoogleSignInOutcome.Failed("Network error contacting Google. Check Wi-Fi and try again.")
                GoogleSignInStatusCodes.DEVELOPER_ERROR ->
                    GoogleSignInOutcome.Failed("Google OAuth configuration error (${e.statusCode}).")
                else ->
                    GoogleSignInOutcome.Failed("Google Sign-In could not complete (status ${e.statusCode}).")
            }
        } catch (t: Throwable) {
            GoogleSignInOutcome.Failed(t.localizedMessage ?: "Google Sign-In failed on this device.")
        }
    }

    private fun finishSafe() {
        if (!isFinishing) {
            finish()
        }
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            PendingLegacySignInBridge.cancelIfPending()
        }
        super.onDestroy()
    }
}
