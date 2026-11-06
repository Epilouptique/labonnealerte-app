package fr.labonnealerte.app.ui

import fr.labonnealerte.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** APP-12 : la barre du bas selon l'option « Nouvelle navigation ». */
class OngletsTest {
    @Test
    fun `par defaut la barre habituelle, sans Publier`() {
        assertEquals(listOf(Onglet.KIOSQUE, Onglet.MES_ALERTES, Onglet.FORUM, Onglet.COMPTE), ongletsFor(false))
        assertEquals(R.string.onglet_kiosque, Onglet.KIOSQUE.labelFor(false))
    }

    @Test
    fun `nouvelle navigation Rechercher, Mes alertes, Publier, Forum, Compte`() {
        assertEquals(listOf(Onglet.KIOSQUE, Onglet.MES_ALERTES, Onglet.PUBLIER, Onglet.FORUM, Onglet.COMPTE), ongletsFor(true))
        assertEquals(R.string.onglet_rechercher, Onglet.KIOSQUE.labelFor(true))
        assertEquals(R.string.onglet_compte, Onglet.COMPTE.labelFor(true))
        assertEquals(PUBLIER_ROUTE, Onglet.PUBLIER.route)
    }
}
