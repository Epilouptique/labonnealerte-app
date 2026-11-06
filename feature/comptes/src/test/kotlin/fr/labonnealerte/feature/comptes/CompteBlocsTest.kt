package fr.labonnealerte.feature.comptes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import fr.labonnealerte.core.model.Account
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** APP-12 : ecran Compte en blocs (nouvelle navigation) et suppression en deux temps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CompteBlocsTest {
    @get:Rule val compose = createComposeRule()

    private val account = Account(
        email = "camille@exemple.test", displayName = "camille", country = "FR", departement = null, region = null, ville = null,
        interests = emptyList(), emailEnabled = true, pushEndpointsCount = 0, quietStart = 22, quietEnd = 7, quietDisabled = false,
        hideCommunityReports = false, subscriptions = emptyList(), favorites = emptyList()
    )

    private fun show(signedIn: Boolean, events: MutableList<String>, unread: Int = 0) {
        val liens = CompteLiens({ events += "reglages" }, {}, {}, {}, {})
        val actions = CompteBlocsActions(
            onOpenProfil = { events += "profil" },
            onOpenMesAlertes = { events += "mes-alertes" },
            onOpenFavoris = { events += "favoris" },
            unread = unread
        )
        compose.setContent {
            Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                CompteBlocsContent(signedIn, account.takeIf { signedIn }, liens, actions, { events += "connexion" })
            }
        }
    }

    @Test
    fun `connecte - carte profil, tuiles, lignes, aucune demande de departement`() {
        val events = mutableListOf<String>()
        show(signedIn = true, events = events, unread = 2)
        compose.onNodeWithText("camille").performClick()
        compose.onNodeWithText("2 alertes en cours non vues").assertExists()
        compose.onNodeWithText("Mes alertes").performClick()
        compose.onNodeWithText("Favoris").performClick()
        compose.onNodeWithText("Réglages").performScrollTo().performClick()
        compose.onNodeWithText("Code source").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onAllNodesWithText("Se déconnecter").assertCountEquals(0)
        assertEquals(listOf("profil", "mes-alertes", "favoris", "reglages"), events)
        // La localisation n'est plus demandee a l'arrivee (elle est dans « Mon profil »).
        compose.onAllNodesWithText("Où êtes-vous ?").assertCountEquals(0)
        compose.onAllNodesWithText("Département", substring = true).assertCountEquals(0)
    }

    @Test
    fun `non connecte - carte de connexion, pas de deconnexion`() {
        val events = mutableListOf<String>()
        show(signedIn = false, events = events)
        compose.onNodeWithText("Se connecter").performClick()
        compose.onNodeWithText("Vos abonnements").assertExists()
        compose.onAllNodesWithText("Se déconnecter").assertCountEquals(0)
        compose.onAllNodesWithText("Mes échéances").assertCountEquals(0)
        assertEquals(listOf("connexion"), events)
    }

    @Test
    fun `supprimer mon compte - deux temps, puis saisie du mot`() {
        var deleted = 0
        compose.setContent { SupprimerCompteContent(busy = false, onDelete = { deleted++ }) }
        compose.onNodeWithText("Supprimer mon compte").assertHeightIsAtLeast(48.dp).performClick()
        // Premier temps : explication, rien n'est supprime.
        compose.onNodeWithText("Supprimer votre compte ?").assertExists()
        compose.onNodeWithText("Continuer").performClick()
        // Second temps : saisie de SUPPRIMER obligatoire.
        compose.onNodeWithText("Supprimer pour toujours").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("supprimer")
        compose.onNode(hasText("Supprimer pour toujours")).assertIsEnabled().performClick()
        assertEquals(1, deleted)
    }

    @Test
    fun `supprimer - annuler au premier temps ne supprime rien`() {
        var deleted = 0
        compose.setContent { SupprimerCompteContent(busy = false, onDelete = { deleted++ }) }
        compose.onNodeWithText("Supprimer mon compte").performClick()
        compose.onNodeWithText("Annuler").performClick()
        compose.onAllNodesWithText("Supprimer votre compte ?").assertCountEquals(0)
        assertEquals(0, deleted)
    }
}
