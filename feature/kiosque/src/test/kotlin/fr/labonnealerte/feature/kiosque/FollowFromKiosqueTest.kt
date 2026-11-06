package fr.labonnealerte.feature.kiosque

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.labonnealerte.core.model.Badge
import fr.labonnealerte.core.model.SourceState
import fr.labonnealerte.core.ui.components.AlertCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** APP-10 : suivre depuis le kiosque (bouton, balayage, actions TalkBack). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FollowFromKiosqueTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `balayage - droite suit, gauche retire, sous le seuil rien, rien en double`() {
        assertEquals(SwipeIntent.FOLLOW, swipeIntent(120f, 100f, followed = false))
        assertEquals(SwipeIntent.NONE, swipeIntent(120f, 100f, followed = true))
        assertEquals(SwipeIntent.UNFOLLOW, swipeIntent(-120f, 100f, followed = true))
        assertEquals(SwipeIntent.NONE, swipeIntent(-120f, 100f, followed = false))
        assertEquals(SwipeIntent.NONE, swipeIntent(60f, 100f, followed = false))
        assertEquals(SwipeIntent.NONE, swipeIntent(0f, 100f, followed = true))
    }

    @Test
    fun `bouton visible Suivre puis Suivi`() {
        var clicks = 0
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                AlertCard("Vigilance", null, null, SourceState.CALM, Badge.OFFICIAL, false, false, {}, null, onToggleFollow = { clicks++ })
                AlertCard("Coupures", null, null, SourceState.CALM, Badge.OFFICIAL, true, false, {}, null, onToggleFollow = { clicks++ })
            }
        }
        compose.onNodeWithText("Suivre").performClick()
        compose.onNodeWithText("Suivi").performClick()
        assertEquals(2, clicks)
    }

    private fun hasAction(label: String) = SemanticsMatcher("action $label") { n ->
        n.config.getOrElseNullable(SemanticsActions.CustomActions) { null }?.any { it.label == label } == true
    }

    @Test
    fun `actions TalkBack - Suivre si non suivie, Ne plus suivre si suivie`() {
        var followed = 0
        var unfollowed = 0
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                SwipeToFollow(followed = false, onFollow = { followed++ }, onUnfollow = { unfollowed++ }) { Text("A") }
                SwipeToFollow(followed = true, onFollow = { followed++ }, onUnfollow = { unfollowed++ }) { Text("B") }
            }
        }
        listOf("Suivre", "Ne plus suivre").forEach { label ->
            val actions = compose.onNode(hasAction(label)).fetchSemanticsNode().config[SemanticsActions.CustomActions]
            compose.runOnIdle { actions.first { it.label == label }.action() }
        }
        assertEquals(1, followed)
        assertEquals(1, unfollowed)
    }

    @Test
    fun `ordre fige - une alerte suivie ici ne saute pas en bas avant rafraichissement`() {
        // « a » vient d'etre suivie (avant : non suivie) ; « b » suivie de longue date ; « c » retiree ici.
        val set = KiosqueViewModel.orderingSet(setOf("a", "b"), mapOf("a" to false, "c" to true))
        assertEquals(setOf("b", "c"), set)
        assertEquals(setOf("a", "b"), KiosqueViewModel.orderingSet(setOf("a", "b"), emptyMap()))
    }
}
