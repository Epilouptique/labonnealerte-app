package fr.labonnealerte.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNormalizerTest {
    @Test
    fun `accents et casse sont ignores`() {
        assertEquals("vigilance meteo", SearchNormalizer.normalize("Vigilance Météo"))
    }

    @Test
    fun `apostrophes droite et typographique sont equivalentes`() {
        assertEquals(SearchNormalizer.normalize("d'eau"), SearchNormalizer.normalize("d’eau"))
    }

    @Test
    fun `deau d'eau et d’eau trouvent la meme source`() {
        val nom = "Coupures d'eau"
        assertTrue(SearchNormalizer.matches(nom, "d'eau"))
        assertTrue(SearchNormalizer.matches(nom, "d’eau"))
        assertTrue(SearchNormalizer.matches(nom, "deau"))
    }

    @Test
    fun `tous les mots doivent etre presents`() {
        assertTrue(SearchNormalizer.matches("Qualité de l'air", "air qualite"))
        assertFalse(SearchNormalizer.matches("Qualité de l'air", "air pollen"))
    }

    @Test
    fun `requete vide correspond a tout`() {
        assertTrue(SearchNormalizer.matches("x", "  "))
    }

    @Test
    fun `ligatures`() {
        assertTrue(SearchNormalizer.matches("Fête du cœur", "coeur"))
    }
}
