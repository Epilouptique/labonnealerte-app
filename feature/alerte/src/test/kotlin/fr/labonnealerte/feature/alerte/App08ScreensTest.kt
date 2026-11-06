package fr.labonnealerte.feature.alerte

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.labonnealerte.core.model.AccountEvent
import fr.labonnealerte.core.model.EventKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** APP-08 : derniers evenements replies, categories cliquables. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class App08ScreensTest {
    @get:Rule val compose = createComposeRule()

    private val events = listOf(
        AccountEvent(EventKind.ACTIVATED, "Coupure rue des Lilas", "2026-10-09T08:00:00Z", "eau", "Coupures d'eau"),
        AccountEvent(EventKind.DEACTIVATED, null, "2026-10-09T09:00:00Z", "eau", "Coupures d'eau")
    )

    @Test
    fun `derniers evenements replies par defaut, depliables`() {
        compose.setContent {
            MesAlertesScreen(
                state = MesAlertesUiState(signedIn = true, loaded = false, events = events),
                onRefresh = {},
                onOpenSource = {},
                onRequestLogin = {},
                onOpenKiosque = {},
                historySlot = {}
            )
        }
        compose.onNodeWithText("Derniers événements (2)").assertHasClickAction()
        compose.onNodeWithText("Coupure rue des Lilas").assertDoesNotExist()
        compose.onNodeWithText("Derniers événements (2)").performClick()
        compose.onNodeWithText("Coupure rue des Lilas").assertExists()
        compose.onNodeWithText("Derniers événements (2)").performClick()
        compose.onNodeWithText("Coupure rue des Lilas").assertDoesNotExist()
    }

    @Test
    fun `categorie de la fiche ouvre le kiosque filtre`() {
        var opened: String? = null
        compose.setContent { CategoryTag("💧 Eau", "eau", onOpen = { opened = it }) }
        compose.onNodeWithText("💧 Eau").performClick()
        assertEquals("eau", opened)
    }

    @Test
    fun `categorie non cliquable sans destination (deux volets)`() {
        compose.setContent { CategoryTag("💧 Eau", "eau", onOpen = null) }
        compose.onNodeWithText("💧 Eau").assertHasNoClickAction()
    }
}
