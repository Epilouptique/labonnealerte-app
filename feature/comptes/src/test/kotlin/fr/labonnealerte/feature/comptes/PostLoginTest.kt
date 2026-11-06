package fr.labonnealerte.feature.comptes

import fr.labonnealerte.core.model.LoginResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Regle de destination apres connexion (APP-05, decision A) et messages de l'etape du code. */
class PostLoginTest {
    private val fiche = "alerte/vigilance-meteo"

    @Test
    fun `venu d une fiche - retour a la fiche, meme apres une creation`() {
        assertEquals(PostLoginDestination.Back(fiche), postLoginDestination(fiche, SignedInEvent(created = true, hasSubscriptions = false)))
        assertEquals(PostLoginDestination.Back(fiche), postLoginDestination(fiche, SignedInEvent(created = false, hasSubscriptions = true)))
    }

    @Test
    fun `creation de compte - kiosque avec bandeau d accueil`() {
        assertEquals(PostLoginDestination.Kiosque(welcome = true), postLoginDestination(null, SignedInEvent(created = true, hasSubscriptions = false)))
    }

    @Test
    fun `compte existant avec abonnements - mes alertes`() {
        assertEquals(PostLoginDestination.MesAlertes, postLoginDestination(null, SignedInEvent(created = false, hasSubscriptions = true)))
    }

    @Test
    fun `compte existant sans abonnement - kiosque sans bandeau`() {
        assertEquals(PostLoginDestination.Kiosque(welcome = false), postLoginDestination(null, SignedInEvent(created = false, hasSubscriptions = false)))
    }

    @Test
    fun `collage du code - espaces, tirets et texte retires, 6 chiffres au plus`() {
        assertEquals("123456", cleanCode("123 456"))
        assertEquals("123456", cleanCode("123-456"))
        assertEquals("123456", cleanCode(" Code : 12 34 56 "))
        assertEquals("123456", cleanCode("1234567"))
        assertEquals("", cleanCode("abc"))
        assertEquals("", cleanCode("１２")) // chiffres pleine chasse : refuses, comme cote serveur
    }

    @Test
    fun `code faux, expire, trop d essais`() {
        val sent = 1_000_000L
        assertEquals(CompteMessage.CODE_INVALIDE, codeErrorMessage(LoginResult.InvalidCode, sent, sent + 60_000))
        assertEquals(CompteMessage.CODE_EXPIRE, codeErrorMessage(LoginResult.InvalidCode, sent, sent + CompteViewModel.CODE_TTL_MS))
        assertEquals(CompteMessage.CODE_INVALIDE, codeErrorMessage(LoginResult.InvalidCode, 0, sent))
        assertEquals(CompteMessage.TROP_ESSAIS, codeErrorMessage(LoginResult.TooManyAttempts, sent, sent))
        assertEquals(CompteMessage.HORS_LIGNE, codeErrorMessage(LoginResult.Offline, sent, sent))
        assertEquals(CompteMessage.ERREUR_SERVEUR, codeErrorMessage(LoginResult.ServerError, sent, sent))
        assertNull(codeErrorMessage(LoginResult.Success, sent, sent))
    }
}
