package fr.labonnealerte.feature.comptes

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Flux de connexion plein ecran (APP-05, decision B) et accueil de l'onglet Compte (decision D). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnexionFlowTest {
    @get:Rule val compose = createComposeRule()

    private val now = 1_000_000_000L

    private fun codeStep(
        error: CompteMessage? = null,
        resendAt: Long = now - 1,
        submitted: MutableList<String> = mutableListOf(),
        events: MutableList<String> = mutableListOf()
    ) {
        compose.setContent {
            Column {
                CodeStep(
                    email = "nouveau@test.fr",
                    busy = false,
                    error = error,
                    attempt = 0,
                    resendAt = resendAt,
                    onSubmit = { submitted += it },
                    onModify = { events += "modifier" },
                    onResend = { events += "renvoyer" },
                    now = { now }
                )
            }
        }
    }

    @Test
    fun `etape adresse - titre, champ, bouton principal`() {
        var sent = 0
        compose.setContent { Column { EmailStep("", busy = false, onEmail = {}, onSubmit = { sent++ }) } }
        compose.onNodeWithText("Créer mon compte ou me connecter").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithText("Recevoir mon code").performClick()
        assertEquals(1, sent)
    }

    @Test
    fun `etape code - adresse et lien Modifier a cote, bouton Valider le code`() {
        val events = mutableListOf<String>()
        codeStep(events = events)
        compose.onNodeWithText("Vérifiez vos e-mails").assertIsDisplayed()
        compose.onNodeWithText("Code envoyé à nouveau@test.fr").assertIsDisplayed()
        compose.onNodeWithContentDescription("Modifier l'adresse e-mail").assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("modifier"), events)
        compose.onNodeWithText("Valider le code").assertIsNotEnabled()
    }

    @Test
    fun `etape code - plus aucune option de lien a coller`() {
        codeStep()
        compose.onAllNodes(hasText("lien", substring = true, ignoreCase = true)).assertCountEquals(0)
    }

    @Test
    fun `collage avec espaces - nettoye et valide automatiquement une seule fois`() {
        val submitted = mutableListOf<String>()
        codeStep(submitted = submitted)
        compose.onNode(hasSetTextAction()).performTextReplacement("123 456")
        compose.waitForIdle()
        assertEquals(listOf("123456"), submitted)
    }

    @Test
    fun `saisie chiffre par chiffre - validation automatique au 6e`() {
        val submitted = mutableListOf<String>()
        codeStep(submitted = submitted)
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("12345")
        assertEquals(emptyList<String>(), submitted)
        field.performTextInput("6")
        compose.waitForIdle()
        assertEquals(listOf("123456"), submitted)
    }

    @Test
    fun `renvoyer le code - decompte puis reactivation`() {
        // Horloge figee : le decompte ne doit pas tourner en boucle pendant le test.
        compose.mainClock.autoAdvance = false
        codeStep(resendAt = now + 45_000)
        compose.onNodeWithText("Renvoyer le code (45 s)").assertIsNotEnabled()
    }

    @Test
    fun `renvoyer le code - disponible apres le decompte`() {
        val events = mutableListOf<String>()
        codeStep(resendAt = now - 1, events = events)
        compose.onNodeWithText("Renvoyer le code").assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("renvoyer"), events)
    }

    @Test
    fun `messages d erreur clairs - faux, expire, trop d essais`() {
        codeStep(error = CompteMessage.CODE_EXPIRE)
        compose.onNodeWithText("Ce code a expiré. Demandez-en un nouveau avec « Renvoyer le code ».").assertIsDisplayed()
    }

    @Test
    fun `message code faux`() {
        codeStep(error = CompteMessage.CODE_INVALIDE)
        compose.onNodeWithText("Ce code ne fonctionne pas. Vérifiez-le ou demandez-en un nouveau.").assertIsDisplayed()
    }

    @Test
    fun `message trop d essais`() {
        codeStep(error = CompteMessage.TROP_ESSAIS)
        compose.onNodeWithText("Trop d'essais. Patientez un peu avant de réessayer.").assertIsDisplayed()
    }

    @Test
    fun `conditions - bouton inactif tant que la case n est pas cochee`() {
        var created = 0
        compose.setContent {
            Column {
                TermsStep("nouveau@test.fr", "https://labonnealerte.fr/confidentialite", accepted = false, busy = false, {}, { created++ }, {}, {})
            }
        }
        compose.onNodeWithText("Créer mon compte").assertIsNotEnabled()
    }

    @Test
    fun `accueil non connecte - bouton principal, liens secondaires, reglages, pas de diagnostic`() {
        val clicks = mutableListOf<String>()
        compose.setContent {
            Column {
                CompteAccueil(
                    onOpenConnexion = { clicks += "connexion" },
                    liens = CompteLiens(
                        onReglages = { clicks += "reglages" },
                        onAPropos = { clicks += "a-propos" },
                        onConfidentialite = { clicks += "confidentialite" },
                        onCodeSource = { clicks += "source" },
                        onSoutenir = { clicks += "soutenir" }
                    )
                )
            }
        }
        compose.onNodeWithText("Créer mon compte ou me connecter").performClick()
        listOf("À propos", "Confidentialité", "Code source", "Soutenir le projet", "Réglages").forEach {
            compose.onNodeWithText(it).assertHeightIsAtLeast(48.dp).performClick()
        }
        assertEquals(listOf("connexion", "a-propos", "confidentialite", "source", "soutenir", "reglages"), clicks)
        compose.onAllNodes(hasText("notifications", substring = true, ignoreCase = true)).assertCountEquals(0)
    }
}
