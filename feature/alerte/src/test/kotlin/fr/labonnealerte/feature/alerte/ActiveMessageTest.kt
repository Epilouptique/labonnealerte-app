package fr.labonnealerte.feature.alerte

import fr.labonnealerte.core.model.AccountEvent
import fr.labonnealerte.core.model.EventKind
import fr.labonnealerte.core.ui.Famille
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** APP-13 : texte de la notification dans la carte d'une alerte en cours ; goutte pour l'eau. */
class ActiveMessageTest {
    private fun ev(src: String, kind: EventKind, msg: String?, at: String) = AccountEvent(kind, msg, at, src, null)

    @Test
    fun `dernier message de la source, comme la notification`() {
        val events = listOf(
            ev("epic", EventKind.ACTIVATED, "Ancien jeu", "2026-10-01T10:00:00Z"),
            ev("epic", EventKind.ACTIVATED, "Le jeu Skyrim est gratuit", "2026-10-09T10:00:00Z"),
            ev("autre", EventKind.ACTIVATED, "Sans rapport", "2026-10-10T10:00:00Z")
        )
        assertEquals("Le jeu Skyrim est gratuit", activeMessage("epic", events))
    }

    @Test
    fun `rien si fin d alerte, message vide ou aucun evenement`() {
        assertNull(activeMessage("epic", listOf(ev("epic", EventKind.DEACTIVATED, "Fin", "2026-10-09T10:00:00Z"))))
        assertNull(activeMessage("epic", listOf(ev("epic", EventKind.ACTIVATED, "  ", "2026-10-09T10:00:00Z"))))
        assertNull(activeMessage("epic", emptyList()))
    }

    @Test
    fun `categorie eau - goutte, sinon pictogramme de la famille`() {
        assertEquals("💧", Famille.emojiFor(Famille.ENERGIE, listOf("energie", "coupures-eau")))
        assertEquals(Famille.ENERGIE.emoji, Famille.emojiFor(Famille.ENERGIE, listOf("tension-reseau")))
    }
}
