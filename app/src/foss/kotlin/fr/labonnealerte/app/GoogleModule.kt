package fr.labonnealerte.app

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.labonnealerte.feature.comptes.GoogleIdOutcome
import fr.labonnealerte.feature.comptes.GoogleSignInProvider

/** F-Droid / GitHub : pas de services Google, le bouton reste masque. */
@Module
@InstallIn(SingletonComponent::class)
object GoogleModule {
    @Provides
    fun googleSignIn(): GoogleSignInProvider = object : GoogleSignInProvider {
        override val webClientId: String = ""

        override suspend fun idToken(context: Context): GoogleIdOutcome = GoogleIdOutcome.Unavailable
    }
}
