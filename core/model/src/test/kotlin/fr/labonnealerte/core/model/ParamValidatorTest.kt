package fr.labonnealerte.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParamValidatorTest {
    private fun p(
        key: String,
        type: ParamType,
        required: Boolean = true,
        options: List<ParamOption> = emptyList(),
        min: Double? = null,
        pattern: String? = null,
        lowercase: Boolean = false
    ) =
        ParamDescriptor(
            key, key, type, options, multiple = true, required = required, default = null,
            min = min, max = null, maxLength = null, pattern = pattern, lowercase = lowercase
        )

    private val dept =
        p("departement", ParamType.ENUM, options = listOf(ParamOption("05", "Hautes-Alpes"), ParamOption("2A", "Corse-du-Sud")))

    @Test
    fun `enum valide`() {
        val r = ParamValidator.validate(listOf(dept), mapOf("departement" to "2A"))
        assertTrue(r.ok)
        assertEquals("2A", r.values["departement"])
    }

    @Test
    fun `enum hors liste et requis manquant`() {
        assertEquals(
            listOf(ParamValidator.Issue.NotAllowed("departement")),
            ParamValidator.validate(
                listOf(dept),
                mapOf(
                    "departement" to "99"
                )
            ).issues
        )
        assertEquals(listOf(ParamValidator.Issue.Required("departement")), ParamValidator.validate(listOf(dept), emptyMap()).issues)
    }

    @Test
    fun `nombre avec virgule et minimum`() {
        val seuil = p("seuil", ParamType.NUMBER, min = 0.0)
        assertEquals(1500.5, ParamValidator.validate(listOf(seuil), mapOf("seuil" to "1500,5")).values["seuil"])
        assertFalse(ParamValidator.validate(listOf(seuil), mapOf("seuil" to "-1")).ok)
        assertFalse(ParamValidator.validate(listOf(seuil), mapOf("seuil" to "abc")).ok)
    }

    @Test
    fun `texte avec motif et minuscules`() {
        val station = p("station", ParamType.STRING, pattern = "^[A-Za-z0-9]{6,12}$", lowercase = true)
        assertEquals("o972001001", ParamValidator.validate(listOf(station), mapOf("station" to "O972001001")).values["station"])
        assertFalse(ParamValidator.validate(listOf(station), mapOf("station" to "x")).ok)
    }

    @Test
    fun `aucun parametre fourni n est pas valide`() {
        assertFalse(ParamValidator.validate(listOf(p("x", ParamType.STRING, required = false)), emptyMap()).ok)
    }
}
