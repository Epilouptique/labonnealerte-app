package fr.labonnealerte.feature.alerte

import fr.labonnealerte.core.model.EventKind
import fr.labonnealerte.core.model.HistoryEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class CollapseTest {
    private fun e(k: EventKind) = HistoryEvent(k, null, "2026-01-01T00:00:00Z")

    @Test
    fun `incidents consecutifs regroupes`() {
        val r = collapseFailures(
            listOf(e(EventKind.DEACTIVATED), e(EventKind.FAILED), e(EventKind.FAILED), e(EventKind.FAILED), e(EventKind.ACTIVATED))
        )
        assertEquals(listOf(1, 3, 1), r.map { it.second })
    }
}
