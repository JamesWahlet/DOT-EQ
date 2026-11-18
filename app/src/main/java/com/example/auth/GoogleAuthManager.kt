package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "GoogleAuthManager"

data class AuthenticatedUser(
    val uid: String,
    val displayName: String?,
    val email: String?
)

class GoogleAuthManager(private val context: Context) {

    fun currentUser(): AuthenticatedUser? {
        val user = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return null
        return user.toAuthenticatedUser()
    }

    suspend fun attemptSilentSignIn(): AuthenticatedUser? {
        currentUser()?.let { return it }
        val webClientId = resolveWebClientId() ?: return null
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(webClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            signInToFirebase(result)
        } catch (_: NoCredentialException) {
            null
        } catch (e: Exception) {
            Log.d(TAG, "Silent Google sign-in skipped: ${e.message}")
            null
        }
    }

    suspend fun signInInteractive(activityContext: Context): Result<AuthenticatedUser> {
        val webClientId = resolveWebClientId()
            ?: return Result.failure(IllegalStateException("Google Web-Client-ID fehlt in google-services.json."))

        val credentialManager = CredentialManager.create(activityContext)
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activityContext, request)
            val user = signInToFirebase(result)
                ?: return Result.failure(IllegalStateException("Google-Konto konnte nicht verknüpft werden."))
            Result.success(user)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google sign-in cancelled by user or play services config", e)
            Result.failure(IllegalStateException("Anmeldung abgebrochen. Bitte Konto auswählen."))
        } catch (e: GetCredentialException) {
            Log.e(TAG, "CredentialManager error during Google sign-in", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Google-Anmeldung fehlgeschlagen."))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during Google sign-in", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Google-Anmeldung fehlgeschlagen."))
        }
    }

    suspend fun signOut(activityContext: Context) {
        withContext(NonCancellable) {
            runCatching { Firebase.auth.signOut() }
            runCatching {
                CredentialManager.create(activityContext).clearCredentialState(ClearCredentialStateRequest())
            }
        }
    }

    private suspend fun signInToFirebase(result: GetCredentialResponse): AuthenticatedUser? {
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = Firebase.auth.signInWithCredential(firebaseCredential).await()
            return authResult.user?.toAuthenticatedUser()
        }
        return null
    }

    private fun resolveWebClientId(): String? {
        return runCatching {
            val id = context.getString(R.string.default_web_client_id)
            id.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun FirebaseUser.toAuthenticatedUser(): AuthenticatedUser {
        return AuthenticatedUser(
            uid = uid,
            displayName = displayName?.takeIf { it.isNotBlank() },
            email = email?.takeIf { it.isNotBlank() }
        )
    }
}
