package fr.labonnealerte.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** APP-21 : regles du parcours de publication (miroir de publier-flow.js du site). */
class PublicationRulesTest {
    private val ok = PublicationDraft(title = "Tarifs du club", categories = listOf("sport"), type = PubType.PAGE, url = "https://exemple.test/tarifs")

    @Test
    fun `titre - longueur et nom provisoire refuse`() {
        assertNotNull(PublicationRules.validateTitle("ab"))
        assertNotNull(PublicationRules.validateTitle("x".repeat(41)))
        assertNotNull(PublicationRules.validateTitle("Brouillon — exemple.test"))
        assertNotNull(PublicationRules.validateTitle("Brouillon — exemple.test"))
        assertNull(PublicationRules.validateTitle("Brouillons de saison"))
        assertNull(PublicationRules.validateTitle("Tarifs du club"))
    }

    @Test
    fun `ecran 1 - une a trois categories`() {
        assertNull(PublicationRules.validateEssentiel(ok))
        assertNotNull(PublicationRules.validateEssentiel(ok.copy(categories = emptyList())))
        assertNotNull(PublicationRules.validateEssentiel(ok.copy(categories = listOf("a", "b", "c", "d"))))
    }

    @Test
    fun `ecran 2 - type, https, zone de page, descriptions`() {
        assertNull(PublicationRules.validateDetails(ok))
        assertNotNull(PublicationRules.validateDetails(ok.copy(type = null)))
        assertNotNull(PublicationRules.validateDetails(ok.copy(url = "http://exemple.test")))
        assertNull(PublicationRules.validateDetails(ok.copy(zone = "#tarifs")))
        assertNotNull(PublicationRules.validateDetails(ok.copy(zone = "div > p")))
        assertNotNull(PublicationRules.validateDetails(ok.copy(type = PubType.RSS, zone = ".prix")))
        assertNotNull(PublicationRules.validateDetails(ok.copy(description = "x".repeat(121))))
        assertNotNull(PublicationRules.validateDetails(ok.copy(descriptionLong = "x".repeat(301))))
    }

    @Test
    fun `soumettre - seulement apres un apercu reussi sur l adresse actuelle`() {
        assertNotNull(PublicationRules.submitBlocker(ok))
        val previewed = ok.copy(id = "tarifs-du-club", previewOk = true, syncedUrl = ok.url, syncedZone = "")
        assertNull(PublicationRules.submitBlocker(previewed))
        // Adresse changee depuis l'apercu : bloque, et l'adresse est a renvoyer.
        val moved = previewed.copy(url = "https://exemple.test/autre")
        assertNotNull(PublicationRules.submitBlocker(moved))
        assertTrue(moved.addressChanged)
        assertFalse(previewed.addressChanged)
        assertNotNull(PublicationRules.submitBlocker(previewed.copy(previewOk = false)))
    }

    @Test
    fun `compteurs et statuts en clair`() {
        assertEquals("3 / 120", PublicationRules.counter(" abc ", 120))
        assertEquals("Brouillon", PublicationRules.statusLabel("draft"))
        assertEquals("À tester", PublicationRules.statusLabel("testing"))
        assertEquals("En revue", PublicationRules.statusLabel("review"))
        assertEquals("Publiée", PublicationRules.statusLabel("published"))
        assertEquals("Suspendue — 3 signalements", PublicationRules.statusLabel("suspended", "3 signalements"))
        assertTrue(PublicationRules.isEditable("testing"))
        assertFalse(PublicationRules.isEditable("review"))
        assertEquals(PubType.AGENDA, PubType.ofEngine("veille-agenda"))
    }

    @Test
    fun `erreurs - message du serveur tel quel, cas particuliers`() {
        assertEquals("Trop d'aperçus, réessayez dans quelques minutes.", PublicationRules.errorMessage(429, "Trop d'aperçus, réessayez dans quelques minutes."))
        assertEquals("Une alerte surveille déjà cette adresse.", PublicationRules.errorMessage(409, "Une source surveille déjà cette URL.", duplicate = true))
        assertEquals("La publication est fermée pour le moment.", PublicationRules.errorMessage(404, "Ressource introuvable"))
        assertEquals("Connexion requise pour publier.", PublicationRules.errorMessage(401, "Compte requis : connectez-vous."))
        assertTrue(PublicationRules.errorMessage(0, null).contains("connexion"))
        assertEquals("Une erreur est survenue, réessayez.", PublicationRules.errorMessage(500, null))
    }
}
