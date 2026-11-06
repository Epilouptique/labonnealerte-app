package fr.labonnealerte.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationModelTest {
    private val groups = mapOf("vigilance-meteo" to "meteo-risques", "bons-plans" to "bons-plans-conso", "status-cloud" to "tech-dev")

    @Test
    fun `vigilance rouge est une urgence`() {
        assertEquals(Severity.URGENT, EventClassifier.severity("🔴 Aujourd'hui : vigilance ROUGE dans Gard : crues"))
        assertEquals(
            NotificationFamily.URGENCES,
            EventClassifier.family(listOf("vigilance-meteo"), SourceType.INTERNAL, "vigilance ROUGE inondation") { groups[it] }
        )
    }

    @Test
    fun `vigilance orange est importante mais pas urgente`() {
        assertEquals(Severity.HIGH, EventClassifier.severity("⛈️ Demain : vigilance ORANGE dans Hautes-Alpes : orages"))
        assertEquals(
            NotificationFamily.METEO_ENVIRONNEMENT,
            EventClassifier.family(listOf("vigilance-meteo"), SourceType.INTERNAL, "vigilance ORANGE") { groups[it] }
        )
    }

    @Test
    fun `bon plan discret`() {
        assertEquals(Severity.NORMAL, EventClassifier.severity("🎮 Jeu gratuit sur l'Epic Games Store"))
        assertEquals(
            NotificationFamily.BONS_PLANS,
            EventClassifier.family(listOf("bons-plans"), SourceType.INTERNAL, "promo") {
                groups[it]
            }
        )
    }

    @Test
    fun `panne et communaute et echeance`() {
        assertEquals(
            NotificationFamily.SERVICES,
            EventClassifier.family(listOf("status-cloud"), SourceType.EXTERNAL, "panne") {
                groups[it]
            }
        )
        assertEquals(NotificationFamily.COMMUNAUTE, EventClassifier.family(emptyList(), SourceType.COMMUNITY, "chat perdu") { null })
        assertEquals(NotificationFamily.ECHEANCES, EventClassifier.family(emptyList(), SourceType.USER_TASK, "a faire") { null })
    }

    @Test
    fun `heures calmes passant minuit`() {
        val q = QuietHours(23, 8)
        assertTrue(q.isQuiet(23))
        assertTrue(q.isQuiet(3))
        assertFalse(q.isQuiet(8))
        assertFalse(q.isQuiet(12))
        assertFalse(QuietHours(23, 8, enabled = false).isQuiet(3))
        assertTrue(QuietHours(13, 15).isQuiet(14))
    }

    @Test
    fun `plafond horaire sauf urgence`() {
        assertTrue(NoiseGuard.allowSound(3, 10, Severity.NORMAL))
        assertFalse(NoiseGuard.allowSound(10, 10, Severity.NORMAL))
        assertTrue(NoiseGuard.allowSound(50, 10, Severity.URGENT))
    }
}
