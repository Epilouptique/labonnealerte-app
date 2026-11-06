package fr.labonnealerte.feature.comptes

import android.content.Context

/** Issue de la demande d'un jeton d'identite Google (APP-22). */
sealed interface GoogleIdOutcome {
    data class Token(val idToken: String) : GoogleIdOutcome
    data object Cancelled : GoogleIdOutcome
    data object Unavailable : GoogleIdOutcome
}

/**
 * Connexion Google, fournie par l'app selon la distribution : Credential Manager dans « play »,
 * absente dans « foss » (aucune dependance proprietaire). [webClientId] : ID client Web du site.
 */
interface GoogleSignInProvider {
    val webClientId: String
    val enabled: Boolean get() = webClientId.isNotBlank()

    /** [context] : l'activite (la feuille de choix du compte s'y affiche). */
    suspend fun idToken(context: Context): GoogleIdOutcome
}
