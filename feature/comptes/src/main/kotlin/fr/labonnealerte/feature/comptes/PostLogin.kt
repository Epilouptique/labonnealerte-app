package fr.labonnealerte.feature.comptes

/** Connexion ou creation reussie : ce qu'il faut savoir pour choisir l'ecran suivant. */
data class SignedInEvent(val created: Boolean, val hasSubscriptions: Boolean)

/** Ecran ouvert apres une connexion ou une creation de compte. Jamais l'onglet Compte. */
sealed interface PostLoginDestination {
    /** Fiche d'alerte (ou lien profond) d'ou la personne est partie se connecter. */
    data class Back(val route: String) : PostLoginDestination

    /** Kiosque ; [welcome] : bandeau d'accueil « Choisissez vos premieres alertes ». */
    data class Kiosque(val welcome: Boolean) : PostLoginDestination

    data object MesAlertes : PostLoginDestination
}

/**
 * Regle de Hugo (APP-05, decision A), dans cet ordre :
 * 1. retour a la fiche ou au lien profond d'origine ;
 * 2. compte tout juste cree : Kiosque avec bandeau d'accueil (Mes alertes serait vide) ;
 * 3. compte existant : Mes alertes s'il a des abonnements, sinon Kiosque.
 */
fun postLoginDestination(returnTo: String?, event: SignedInEvent): PostLoginDestination = when {
    returnTo != null -> PostLoginDestination.Back(returnTo)
    event.created -> PostLoginDestination.Kiosque(welcome = true)
    event.hasSubscriptions -> PostLoginDestination.MesAlertes
    else -> PostLoginDestination.Kiosque(welcome = false)
}
