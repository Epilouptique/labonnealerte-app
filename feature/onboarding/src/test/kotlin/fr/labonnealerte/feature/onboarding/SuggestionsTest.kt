package fr.labonnealerte.feature.onboarding

import fr.labonnealerte.core.model.Badge
import fr.labonnealerte.core.model.Source
import fr.labonnealerte.core.model.SourceState
import fr.labonnealerte.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionsTest {
    private fun s(id: String, cat: String, subs: Int, type: SourceType = SourceType.INTERNAL) =
        Source(id, id, null, null, null, Badge.OFFICIAL, type, null, listOf(cat), emptyList(), 0, null, null, SourceState.CALM, subs, null, 0)

    private val groups = mapOf("meteo" to "meteo-risques", "eau" to "energie")

    @Test
    fun `trois plus suivies par theme, sans partenaires ni taches`() {
        val list = listOf(
            s("a", "meteo", 5),
            s("b", "meteo", 50),
            s("c", "meteo", 20),
            s("d", "meteo", 10),
            s("e", "meteo", 999, SourceType.LINKED),
            s("f", "eau", 3)
        )
        val r = OnboardingViewModel.suggestions(list, setOf("meteo-risques")) { groups[it] }
        assertEquals(listOf("b", "c", "d"), r.map { it.id })
    }

    @Test
    fun `plusieurs themes sans doublon`() {
        val list = listOf(s("a", "meteo", 5), s("f", "eau", 3))
        val r = OnboardingViewModel.suggestions(list, setOf("meteo-risques", "energie")) { groups[it] }
        assertEquals(setOf("a", "f"), r.map { it.id }.toSet())
    }
}
