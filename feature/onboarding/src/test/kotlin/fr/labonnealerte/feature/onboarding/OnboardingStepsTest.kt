package fr.labonnealerte.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Une etape dont l'objectif est deja atteint ne s'affiche pas (APP-06, points 1 a 3). */
class OnboardingStepsTest {
    private val rien = SetupState(permissionGranted = false, notificationsReady = false, distributorInstalled = false)
    private val tout = SetupState(permissionGranted = true, notificationsReady = true, distributorInstalled = true)

    @Test
    fun `rien n est pret - les quatre etapes`() {
        assertEquals(OnboardingStep.entries, stepsToShow(rien))
    }

    @Test
    fun `notifications deja pretes - l etape notifications est sautee`() {
        val s = rien.copy(permissionGranted = true, notificationsReady = true)
        assertEquals(listOf(OnboardingStep.BIENVENUE, OnboardingStep.THEMES, OnboardingStep.RECEPTION), stepsToShow(s))
        assertEquals(OnboardingStep.RECEPTION, nextStep(OnboardingStep.THEMES, s))
    }

    @Test
    fun `permission accordee mais categorie coupee - l etape notifications reste`() {
        val s = rien.copy(permissionGranted = true, notificationsReady = false)
        assertEquals(OnboardingStep.NOTIFICATIONS, nextStep(OnboardingStep.THEMES, s))
    }

    @Test
    fun `distributeur deja installe - l etape reception est sautee`() {
        val s = rien.copy(distributorInstalled = true)
        assertEquals(listOf(OnboardingStep.BIENVENUE, OnboardingStep.THEMES, OnboardingStep.NOTIFICATIONS), stepsToShow(s))
        assertNull(nextStep(OnboardingStep.NOTIFICATIONS, s))
    }

    @Test
    fun `tout est pret - accueil reduit a deux etapes, puis fini`() {
        assertEquals(listOf(OnboardingStep.BIENVENUE, OnboardingStep.THEMES), stepsToShow(tout))
        assertNull(nextStep(OnboardingStep.THEMES, tout))
    }

    @Test
    fun `permission accordee pendant l etape - on passe a la suivante sans ecran intermediaire`() {
        val apres = rien.copy(permissionGranted = true, notificationsReady = true)
        assertEquals(OnboardingStep.RECEPTION, currentStep(OnboardingStep.NOTIFICATIONS, apres))
    }

    @Test
    fun `retour de l installation de ntfy - distributeur detecte, accueil termine`() {
        val apres = tout
        assertNull(currentStep(OnboardingStep.RECEPTION, apres))
    }

    @Test
    fun `etape encore utile - elle reste affichee`() {
        assertEquals(OnboardingStep.NOTIFICATIONS, currentStep(OnboardingStep.NOTIFICATIONS, rien))
        assertEquals(OnboardingStep.BIENVENUE, currentStep(OnboardingStep.BIENVENUE, tout))
    }
}
