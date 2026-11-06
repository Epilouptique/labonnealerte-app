package fr.labonnealerte.core.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import fr.labonnealerte.core.model.Badge
import fr.labonnealerte.core.model.DisplayLab
import fr.labonnealerte.core.model.SourceState
import fr.labonnealerte.core.ui.components.AlertCard
import fr.labonnealerte.core.ui.theme.LbaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Vignette : textes d'etat conserves quelles que soient les options ; APP-17 : badge et sous-titre. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DisplayLabCardTest {
    @get:Rule val compose = createComposeRule()

    private fun card(lab: DisplayLab, state: SourceState = SourceState.ACTIVE) {
        compose.setContent {
            LbaTheme {
                CompositionLocalProvider(LocalDisplayLab provides lab, LocalReduceMotionPref provides true) {
                    AlertCard(
                        "Vigilance", "Sous-titre", null, state, Badge.OFFICIAL, false, false, {}, null,
                        leading = "⛈️", familyColor = Color(0xFFE3823F)
                    )
                }
            }
        }
    }

    @Test
    fun `badge Officiel masque sur les vignettes, sous-titre masque par defaut`() {
        card(DisplayLab())
        compose.onNodeWithText("Officiel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Officiel").assertDoesNotExist()
        compose.onNodeWithText("Sous-titre").assertDoesNotExist()
        compose.onNodeWithText("Alerte en cours").assertExists()
    }

    @Test
    fun `tout desactive - sous-titre visible, etat lisible`() {
        card(DisplayLab.ALL_OFF)
        compose.onNodeWithText("Sous-titre").assertExists()
        compose.onNodeWithText("Alerte en cours").assertExists()
    }

    @Test
    fun `alerte calme - rendu calme intact`() {
        card(DisplayLab(), state = SourceState.CALM)
        compose.onNodeWithText("Rien à signaler").assertExists()
        compose.onNodeWithText("Vigilance").assertExists()
    }
}
