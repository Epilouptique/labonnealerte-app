package fr.labonnealerte.core.data

/**
 * Reactions aux changements de session, fournies par d'autres modules (push, notifications)
 * via le multibinding Hilt : evite une dependance de core:data vers ces modules.
 */
interface SessionHook {
    suspend fun onSignedIn() {}

    /** Appele AVANT l'effacement de la session (le jeton est encore valable). */
    suspend fun beforeSignOut() {}

    /** Appele apres l'effacement des donnees locales du compte. */
    suspend fun afterSignOut() {}
}
