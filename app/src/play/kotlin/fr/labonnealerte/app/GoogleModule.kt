package fr.labonnealerte.app

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.labonnealerte.feature.comptes.GoogleIdOutcome
import fr.labonnealerte.feature.comptes.GoogleSignInProvider

/**
 * Play Store : « Continuer avec Google » par Credential Manager. Le jeton demande vise l'ID client
 * Web du site (serverClientId), seule audience acceptee par POST /api/app/login/google.
 */
class CredentialManagerGoogleSignIn : GoogleSignInProvider {
    override val webClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID

    override suspend fun idToken(context: Context): GoogleIdOutcome {
        if (!enabled) return GoogleIdOutcome.Unavailable
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        return try {
            val cred = CredentialManager.create(context).getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdOutcome.Token(GoogleIdTokenCredential.createFrom(cred.data).idToken)
            } else {
                GoogleIdOutcome.Unavailable
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleIdOutcome.Cancelled
        } catch (e: GetCredentialException) {
            timber.log.Timber.w("Credential Manager : %s", e.javaClass.simpleName)
            GoogleIdOutcome.Unavailable
        } catch (e: GoogleIdTokenParsingException) {
            GoogleIdOutcome.Unavailable
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object GoogleModule {
    @Provides
    fun googleSignIn(): GoogleSignInProvider = CredentialManagerGoogleSignIn()
}
