package fr.labonnealerte.feature.reglages

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import fr.labonnealerte.core.model.DisplayLab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** APP-11 : un interrupteur par option, desactives par defaut, « Tout remettre par defaut ». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DisplayLabScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `onze interrupteurs aux defauts APP-13, remise a zero inactive`() {
        compose.setContent { DisplayLabContent(DisplayLab(), { _, _ -> }, {}) }
        listOf(
            "Couleur par catégorie",
            "Halo des alertes en cours",
            "En-tête de marque",
            "Nouvelle navigation (test)",
            "Bouton « Suivre » en pilule",
            "Filet sous l'en-tête",
            "Réglages en lignes avec flèche"
        ).forEach { compose.onNodeWithText(it).performScrollTo().assertIsOn() }
        listOf("Voyant qui pulse").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsOff()
        }
        compose.onNodeWithText("Tout remettre par défaut").performScrollTo().assertIsNotEnabled()
        assertEquals(11, DisplayLab.Option.entries.size)
    }

    @Test
    fun `un appui active l option, la remise a zero redevient possible`() {
        var set: Pair<DisplayLab.Option, Boolean>? = null
        var reset = 0
        compose.setContent { DisplayLabContent(DisplayLab.ALL_OFF.copy(hideSubtitle = true), { o, v -> set = o to v }, { reset++ }) }
        compose.onNodeWithText("Vignettes sans sous-titre").performScrollTo().assertIsOn()
        compose.onNodeWithText("Voyant qui pulse").performScrollTo().performClick()
        assertEquals(DisplayLab.Option.ACTIVE_PULSE to true, set)
        compose.onNodeWithText("Tout remettre par défaut").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, reset)
    }
}
