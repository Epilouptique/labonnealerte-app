package fr.labonnealerte.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayLabTest {
    @Test
    fun `defaut - tout active sauf le voyant qui pulse`() {
        val d = DisplayLab()
        assertTrue(d.isDefault)
        DisplayLab.Option.entries.forEach { assertEquals(it.name, it != DisplayLab.Option.ACTIVE_PULSE, d[it]) }
        assertEquals(DisplayLab(), DisplayLab.of { null })
    }

    @Test
    fun `un choix enregistre l emporte sur le nouveau defaut`() {
        // Utilisateur existant : il avait coupe la pilule et le sous-titre.
        val stored = mapOf(DisplayLab.Option.PILL_FOLLOW to false, DisplayLab.Option.HIDE_SUBTITLE to false)
        val d = DisplayLab.of { stored[it] }
        assertFalse(d.pillFollow)
        assertFalse(d.hideSubtitle)
        assertTrue(d.newNavigation) // jamais touche : nouveau defaut
    }

    @Test
    fun `chaque option independante, tout desactive possible`() {
        DisplayLab.Option.entries.forEach { o ->
            val one = DisplayLab.ALL_OFF.with(o, true)
            assertTrue(one[o])
            DisplayLab.Option.entries.filter { it != o }.forEach { assertFalse("${o.name} active ${it.name}", one[it]) }
            assertEquals(one, DisplayLab.of { it == o })
        }
        DisplayLab.Option.entries.forEach { assertFalse(DisplayLab.ALL_OFF[it]) }
    }

    @Test
    fun `cles de stockage uniques`() {
        val keys = DisplayLab.Option.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all { it.startsWith("lab_") })
    }
}
