package com.grocer.billing.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

data class GoogleUserData(
    val idToken: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val phoneNumber: String? = null
)

class GoogleAuthCancellationException(message: String = "Sign-in was cancelled.") : Exception(message)

class GoogleAuthManager(
    private val serverClientId: String = GOOGLE_SERVER_CLIENT_ID
) {
    companion object {
        const val GOOGLE_SERVER_CLIENT_ID = "190960573906-n4jm63dv5fvpf1v5q65gogumchjdb5r9.apps.googleusercontent.com"
    }

    /**
     * Triggers the modern Android Credential Manager Google Sign-In bottom sheet / prompt.
     */
    suspend fun signIn(context: Context): Result<GoogleUserData> {
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = credentialManager.getCredential(
                context = context,
                request = request
            )

            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                
                val rawName = googleIdTokenCredential.displayName
                    ?: listOfNotNull(googleIdTokenCredential.givenName, googleIdTokenCredential.familyName)
                        .joinToString(" ")
                        .takeIf { it.isNotBlank() }
                
                val cleanName = rawName ?: googleIdTokenCredential.id
                    .substringBefore("@")
                    .replace(".", " ")
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                    .ifBlank { "Store Owner" }

                Result.success(
                    GoogleUserData(
                        idToken = googleIdTokenCredential.idToken,
                        email = googleIdTokenCredential.id,
                        displayName = cleanName,
                        photoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                        phoneNumber = googleIdTokenCredential.phoneNumber
                    )
                )
            } else {
                Result.failure(Exception("Unsupported credential type returned from Credential Manager."))
            }
        } catch (_: GetCredentialCancellationException) {
            Result.failure(GoogleAuthCancellationException("Google sign-in was cancelled."))
        } catch (_: NoCredentialException) {
            Result.failure(Exception("No Google account found. Please sign in to a Google account in your device settings."))
        } catch (e: GoogleIdTokenParsingException) {
            Result.failure(Exception("Failed to verify Google token: ${e.message}"))
        } catch (e: GetCredentialException) {
            Result.failure(Exception("Google Sign-In failed: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Google Sign-In error: ${e.localizedMessage ?: "Unknown error occurred"}"))
        }
    }
}
