package fr.labonnealerte.feature.reglages

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** APP-08 : theme en selecteur sur une ligne « Clair | Auto | Sombre », comme sur le site. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeSelectorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `ordre clair auto sombre`() {
        assertEquals(listOf("light", "system", "dark"), THEME_OPTIONS.map { it.first })
    }

    @Test
    fun `auto selectionne pour le theme systeme, un appui change le theme`() {
        var picked: String? = null
        compose.setContent { ThemeSelector("system") { picked = it } }
        compose.onNodeWithText("Auto").assertIsSelected()
        compose.onNodeWithText("Sombre").performClick()
        assertEquals("dark", picked)
    }
}
