package fr.labonnealerte.core.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import fr.labonnealerte.core.model.DisplayLab
import fr.labonnealerte.core.ui.components.FollowButton
import fr.labonnealerte.core.ui.components.NavRow
import fr.labonnealerte.core.ui.components.initialOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** APP-12 : bouton « Suivre » en pilule, lignes avec fleche, initiale de la pastille. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationLabTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `pilule - texte conserve, hauteur 36 dp choisie par Hugo, meme action`() {
        var clicks = 0
        compose.setContent {
            CompositionLocalProvider(LocalDisplayLab provides DisplayLab(pillFollow = true)) {
                androidx.compose.foundation.layout.Column {
                    FollowButton(false) { clicks++ }
                    FollowButton(true) { clicks++ }
                }
            }
        }
        compose.onNodeWithText("Suivre").assertHeightIsAtLeast(36.dp).performClick()
        compose.onNodeWithText("Suivi").assertHeightIsAtLeast(36.dp).performClick()
        assertEquals(2, clicks)
    }

    @Test
    fun `ligne avec fleche - toute la ligne cliquable`() {
        var clicks = 0
        compose.setContent { NavRow("Réglages", { clicks++ }, subtitle = "Apparence") }
        compose.onNodeWithText("Réglages").assertHeightIsAtLeast(20.dp).performClick()
        compose.onNodeWithText("Apparence").performClick()
        assertEquals(2, clicks)
        compose.onAllNodesWithText(">").assertCountEquals(0)
    }

    @Test
    fun `initiale - pseudo, sinon adresse, sinon rien`() {
        assertEquals('C', initialOf("camille", "x@exemple.test"))
        assertEquals('É', initialOf("  élise", null))
        assertEquals('X', initialOf(null, "x@exemple.test"))
        assertEquals('X', initialOf(" ", "x@exemple.test"))
        assertNull(initialOf(null, null))
    }

    @Test
    fun `reglages en lignes - option 5 ou nouvelle navigation`() {
        assertEquals(false, DisplayLab.ALL_OFF.rowSettings)
        assertEquals(true, DisplayLab.ALL_OFF.copy(settingsRows = true).rowSettings)
        assertEquals(true, DisplayLab.ALL_OFF.copy(newNavigation = true).rowSettings)
    }
}
