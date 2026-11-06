package fr.labonnealerte.core.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointRuleTest {
    private val prod = "https://labonnealerte.fr/"
    private val fake = "http://127.0.0.1:8787/"

    @Test
    fun httpsToujoursAccepte() {
        assertTrue(PushRegistrar.acceptableEndpoint("https://ntfy.sh/upAbc?up=1", prod))
        assertTrue(PushRegistrar.acceptableEndpoint("https://ntfy.sh/upAbc?up=1", fake))
    }

    @Test
    fun httpRefuseContreLaProduction() {
        assertFalse(PushRegistrar.acceptableEndpoint("http://127.0.0.1:8090/upAbc?up=1", prod))
        assertFalse(PushRegistrar.acceptableEndpoint("http://ntfy.example/upAbc", prod))
    }

    @Test
    fun httpLocalSeulementAvecLeServeurFactice() {
        assertTrue(PushRegistrar.acceptableEndpoint("http://127.0.0.1:8090/upAbc?up=1", fake))
        assertFalse(PushRegistrar.acceptableEndpoint("http://ntfy.example/upAbc", fake))
        assertFalse(PushRegistrar.acceptableEndpoint("http://127.0.0.1.evil.example:80/x", fake))
    }
}
