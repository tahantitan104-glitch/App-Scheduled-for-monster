package com.example.sync

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException

sealed interface GoogleSignInOutcome {
    data class Success(val idToken: String) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Failed(val message: String) : GoogleSignInOutcome
}

object GoogleSignInHelper {

    suspend fun signInInteractive(context: Context): GoogleSignInOutcome {
        val activity = context.findActivity()
            ?: return GoogleSignInOutcome.Failed("Open Settings inside the app to sign in with Google.")

        val webClientId = context.getString(R.string.default_web_client_id).trim()
        if (webClientId.isEmpty()) {
            return GoogleSignInOutcome.Failed("Google Sign-In client ID is not configured yet.")
        }

        val credentialManager = CredentialManager.create(activity)
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        return try {
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
            GoogleSignInOutcome.Failed("No Google account is signed in on this phone yet.")
        } catch (e: GetCredentialException) {
            GoogleSignInOutcome.Failed(
                e.localizedMessage ?: "Google Sign-In unavailable on this device."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GoogleSignInOutcome.Failed(
                e.localizedMessage ?: "Unable to complete Google Sign-In."
            )
        }
    }

    suspend fun clearCredentialState(context: Context) {
        runCatching {
            CredentialManager.create(context.applicationContext)
                .clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
