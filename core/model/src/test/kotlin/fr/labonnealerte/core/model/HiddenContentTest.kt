package fr.labonnealerte.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** APP-23 : masquage local d'auteurs et de signalements. */
class HiddenContentTest {
    private data class Post(val id: Int, val pseudo: String?, val name: String)

    private val posts = listOf(Post(1, "Jean", "Jean D."), Post(2, "marie", "Marie"), Post(3, null, "Invite"))

    @Test
    fun `cle d auteur - pseudo sans casse, sinon nom affiche`() {
        assertEquals("auteur:jean", HiddenContent.authorKey("Jean", "Jean D."))
        assertEquals("nom:Invite", HiddenContent.authorKey(null, "Invite"))
        assertEquals("nom:Invite", HiddenContent.authorKey("  ", " Invite "))
    }

    @Test
    fun `filtre - retire les auteurs masques, garde les autres`() {
        val hidden = setOf(HiddenContent.authorKey("JEAN", "x"), HiddenContent.authorKey(null, "Invite"))
        val left = HiddenContent.filter(posts, hidden) { HiddenContent.authorKey(it.pseudo, it.name) }
        assertEquals(listOf(2), left.map { it.id })
        assertEquals(posts, HiddenContent.filter(posts, emptySet()) { HiddenContent.authorKey(it.pseudo, it.name) })
    }

    @Test
    fun `signalements et libelles`() {
        val k = HiddenContent.reportKey("42")
        assertTrue(HiddenContent.isReport(k))
        assertFalse(HiddenContent.isReport("auteur:jean"))
        assertEquals("@jean", HiddenContent.label("auteur:jean"))
        assertEquals("Invite", HiddenContent.label("nom:Invite"))
        assertEquals(listOf("7"), HiddenContent.filter(listOf("42", "7"), setOf(k)) { HiddenContent.reportKey(it) })
    }
}
