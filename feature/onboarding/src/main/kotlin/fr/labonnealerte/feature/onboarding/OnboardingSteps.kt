package fr.labonnealerte.feature.onboarding

/** Etapes de l'accueil, dans l'ordre. */
enum class OnboardingStep { BIENVENUE, THEMES, NOTIFICATIONS, RECEPTION }

/**
 * Etat REEL du telephone, relu au moment ou l'etape s'affiche (et a chaque retour sur l'app),
 * jamais un indicateur memorise (APP-06, point 1).
 */
data class SetupState(
    /** Permission accordee (ou inutile avant Android 13). */
    val permissionGranted: Boolean,
    /** Permission accordee, notifications de l'app actives et aucune categorie coupee. */
    val notificationsReady: Boolean,
    /** Un distributeur UnifiedPush (ntfy…) est installe. */
    val distributorInstalled: Boolean
)

/** Une etape qui n'a plus rien a demander ne s'affiche pas. */
fun stepsToShow(s: SetupState): List<OnboardingStep> = OnboardingStep.entries.filter { step ->
    when (step) {
        OnboardingStep.NOTIFICATIONS -> !s.notificationsReady
        OnboardingStep.RECEPTION -> !s.distributorInstalled
        OnboardingStep.BIENVENUE, OnboardingStep.THEMES -> true
    }
}

/**
 * Etape a montrer quand on veut [wanted] : elle-meme si elle reste utile, sinon la suivante utile
 * (objectif atteint pendant l'etape, ou au retour de l'installation de ntfy). Null : accueil fini.
 */
fun currentStep(wanted: OnboardingStep, s: SetupState): OnboardingStep? = stepsToShow(s).firstOrNull { it >= wanted }

/** Etape qui suit [after], ou null si c'etait la derniere utile. */
fun nextStep(after: OnboardingStep, s: SetupState): OnboardingStep? = stepsToShow(s).firstOrNull { it > after }
