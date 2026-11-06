package fr.labonnealerte.feature.reglages

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import fr.labonnealerte.core.data.ReceptionState
import fr.labonnealerte.core.push.HealthInputs
import fr.labonnealerte.core.push.NotificationProblem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bandeau des notifications (APP-05, decision E ; APP-06, points 4 et 7) : une cause, son texte
 * exact, le bouton « Corriger » ; invitation positive tant que la permission n'a jamais ete demandee.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationProblemBannerTest {
    @get:Rule val compose = createComposeRule()

    private val titre = "Pourquoi mes notifications n'arrivent pas ?"

    private fun causeAffichee(problem: NotificationProblem, texte: String) {
        var fixed = 0
        compose.setContent { NotificationProblemCard(problem, onFix = { fixed++ }) }
        compose.onNodeWithText(titre).assertIsDisplayed()
        compose.onNodeWithText(texte).assertIsDisplayed()
        compose.onNodeWithText("Corriger").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, fixed)
    }

    @Test
    fun `cause - permission refusee`() = causeAffichee(
        NotificationProblem.PERMISSION,
        "Les notifications de La Bonne Alerte ne sont pas autorisées sur ce téléphone."
    )

    @Test
    fun `cause - canal coupe`() = causeAffichee(
        NotificationProblem.CANAL_COUPE,
        "Un type de notification de La Bonne Alerte est coupé dans les réglages du téléphone."
    )

    @Test
    fun `cause - economie de batterie`() = causeAffichee(
        NotificationProblem.BATTERIE,
        "L'économie de batterie empêche l'application de recevoir vos alertes en arrière-plan."
    )

    @Test
    fun `cause - aucune reception instantanee`() = causeAffichee(
        NotificationProblem.PUSH_MANQUE,
        "Des alertes récentes ne sont pas arrivées tout de suite sur ce téléphone."
    )

    @Test
    fun `cause - releve en retard`() = causeAffichee(
        NotificationProblem.RELEVE_EN_RETARD,
        "L'application n'a pas pu vérifier vos alertes depuis plus de deux heures."
    )

    @Test
    fun `titre annonce comme titre`() {
        compose.setContent { NotificationProblemCard(NotificationProblem.CANAL_COUPE, onFix = {}) }
        compose.onNode(isHeading()).assertIsDisplayed()
    }

    @Test
    fun `invitation positive - nombre d alertes et bouton qui demande la permission`() {
        var asked = 0
        compose.setContent { NotificationInvitationCard(3, onAllow = { asked++ }) }
        compose.onNodeWithText("Autorisez les notifications pour recevoir vos 3 alertes").assertIsDisplayed()
        compose.onNodeWithText("Autoriser les notifications").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, asked)
    }

    @Test
    fun `invitation positive - une seule alerte`() {
        compose.setContent { NotificationInvitationCard(1, onAllow = {}) }
        compose.onNodeWithText("Autorisez les notifications pour recevoir votre alerte").assertIsDisplayed()
    }

    private val sansPermission = HealthInputs(
        permissionGranted = false,
        permissionAsked = false,
        notificationsEnabled = false,
        blockedChannels = 0,
        backgroundRestricted = false,
        distributors = 0,
        reception = ReceptionState(),
        now = 1_800_000_000_000L
    )

    @Test
    fun `nouveau telephone avec abonnements - invitation, pas d erreur`() {
        assertEquals(NotificationBannerState.Invitation(4), NotificationProblemViewModel.bannerState(sansPermission, signedIn = true, subscriptions = 4))
    }

    @Test
    fun `apres un refus - bandeau d erreur`() {
        assertEquals(
            NotificationBannerState.Probleme(NotificationProblem.PERMISSION),
            NotificationProblemViewModel.bannerState(sansPermission.copy(permissionAsked = true), signedIn = true, subscriptions = 4)
        )
    }

    @Test
    fun `rien sans connexion, sans abonnement ou quand tout va bien`() {
        assertEquals(NotificationBannerState.Aucun, NotificationProblemViewModel.bannerState(sansPermission, signedIn = false, subscriptions = 4))
        assertEquals(NotificationBannerState.Aucun, NotificationProblemViewModel.bannerState(sansPermission, signedIn = true, subscriptions = 0))
        val ok = sansPermission.copy(permissionGranted = true, notificationsEnabled = true, reception = ReceptionState(lastPollAtMillis = 1_800_000_000_000L))
        assertEquals(NotificationBannerState.Aucun, NotificationProblemViewModel.bannerState(ok, signedIn = true, subscriptions = 4))
    }
}
