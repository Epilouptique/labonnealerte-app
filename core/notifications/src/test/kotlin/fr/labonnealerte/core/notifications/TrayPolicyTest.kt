package fr.labonnealerte.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tiroir : une notification reste tant que son alerte n'a pas ete vue (APP-07). */
class TrayPolicyTest {
    @Test
    fun `une notification par source, nombre = alertes non lues`() {
        val unread = listOf(UnreadAlert(1, "vigilance"), UnreadAlert(2, "vigilance"), UnreadAlert(3, null))
        assertEquals(mapOf("vigilance" to 2, "local-3" to 1), TrayPolicy.counts(unread))
    }

    @Test
    fun `seules les notifications entierement vues sont retirees`() {
        val active = listOf("vigilance", "coupure", "local-9")
        val unread = listOf(UnreadAlert(1, "vigilance"))
        assertEquals(listOf("coupure", "local-9"), TrayPolicy.toCancel(active, unread))
        // Rien de vu : rien de retire (ouvrir l'app ne vide pas le tiroir).
        val all = listOf(UnreadAlert(1, "vigilance"), UnreadAlert(2, "coupure"), UnreadAlert(9, null))
        assertEquals(emptyList<String>(), TrayPolicy.toCancel(active, all))
    }

    @Test
    fun `total de la synthese`() {
        val unread = listOf(UnreadAlert(1, "vigilance"), UnreadAlert(2, "vigilance"), UnreadAlert(3, "coupure"))
        assertEquals(3, TrayPolicy.total(listOf("vigilance", "coupure"), unread))
        // Une alerte non lue sans notification affichee (Silence, expiree) n'est pas comptee.
        assertEquals(2, TrayPolicy.total(listOf("vigilance"), unread))
    }
}
