package fr.labonnealerte.feature.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Etape « mode de reception » (APP-06, point 3) : deux phrases, deux choix, « Plus tard ». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReceptionStepTest {
    @get:Rule val compose = createComposeRule()

    private val clics = mutableListOf<String>()

    private fun etape(fdroid: Boolean) = compose.setContent {
        Column {
            ReceptionStep(
                fdroidInstalled = fdroid,
                onInstall = { clics += "installer" },
                onPeriodic = { clics += "periodique" },
                onLater = { clics += "plus tard" }
            )
        }
    }

    @Test
    fun `sans F-Droid - le mot F-Droid n apparait nulle part`() {
        etape(fdroid = false)
        compose.onNodeWithText("Installer ntfy").assertIsDisplayed()
        compose.onAllNodes(hasText("F-Droid", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `avec F-Droid - installation proposee dans F-Droid`() {
        etape(fdroid = true)
        compose.onNodeWithText("Installer ntfy avec F-Droid").assertIsDisplayed()
    }

    @Test
    fun `deux phrases et deux choix clairs, plus un Plus tard discret`() {
        etape(fdroid = false)
        compose.onNodeWithText(
            "Un petit relais est nécessaire pour recevoir vos alertes à la seconde. " +
                "ntfy, gratuit et libre, joue ce rôle."
        ).assertIsDisplayed()
        compose.onNodeWithText("Installer ntfy").performClick()
        compose.onNodeWithText("Vérification périodique (un peu moins instantanée)").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Plus tard").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("installer", "periodique", "plus tard"), clics)
    }
}
