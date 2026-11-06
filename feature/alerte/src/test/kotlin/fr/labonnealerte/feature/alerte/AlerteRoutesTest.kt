package fr.labonnealerte.feature.alerte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlerteRoutesTest {
    @Test
    fun `identifiants valides`() {
        assertEquals("vigilance-meteo", AlerteRoutes.sanitizeId("vigilance-meteo"))
        assertEquals("vigilance-meteo", AlerteRoutes.sanitizeId(" Vigilance-Meteo "))
    }

    @Test
    fun `identifiants refuses`() {
        assertNull(AlerteRoutes.sanitizeId(null))
        assertNull(AlerteRoutes.sanitizeId(""))
        assertNull(AlerteRoutes.sanitizeId("../etc/passwd"))
        assertNull(AlerteRoutes.sanitizeId("a b"))
        assertNull(AlerteRoutes.sanitizeId("x".repeat(65)))
        assertNull(AlerteRoutes.sanitizeId("<script>"))
    }
}
