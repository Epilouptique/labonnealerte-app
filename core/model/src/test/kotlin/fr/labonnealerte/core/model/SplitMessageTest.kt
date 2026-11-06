package fr.labonnealerte.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitMessageTest {
    @Test
    fun `emoji de tete separe du texte`() {
        val s = SplitMessage.of("⛈️ Aujourd'hui : vigilance ORANGE dans Hautes-Alpes : orages")
        assertEquals("⛈️", s.emoji)
        assertEquals("Aujourd'hui : vigilance ORANGE dans Hautes-Alpes : orages", s.text)
    }

    @Test
    fun `emoji du plan astral`() {
        val s = SplitMessage.of("🎮 System Shock 2 est gratuit")
        assertEquals("🎮", s.emoji)
        assertEquals("System Shock 2 est gratuit", s.text)
    }

    @Test
    fun `sans emoji`() {
        val s = SplitMessage.of("Panne en cours")
        assertNull(s.emoji)
        assertEquals("Panne en cours", s.text)
    }

    @Test
    fun `null et emoji seul`() {
        assertEquals("", SplitMessage.of(null).text)
        assertNull(SplitMessage.of("⚠️").emoji)
    }

    @Test
    fun `libelle de departement`() {
        assertEquals("05 - Hautes-Alpes", Departement("05", "Hautes-Alpes", "Provence-Alpes-Côte d'Azur").displayLabel)
    }
}
