package fr.labonnealerte.feature.forum

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Le bouton « Nouveau sujet » doit etre annonce par son texte (arbre fusionne, comme TalkBack). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NouveauSujetSemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun boutonAnnonceParSonTexte() {
        compose.setContent { NouveauSujetFab(onClick = {}) }
        compose.onNodeWithContentDescription("Nouveau sujet").assertHasClickAction()
    }
}
