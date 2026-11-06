package fr.labonnealerte.core.data

import fr.labonnealerte.core.model.LoginResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountRepositoryBranchesTest {
    private val hook = RecordingHook(fail = true)
    private lateinit var h: Harness
    private val repo get() = h.account
    private val server get() = h.server

    @Before
    fun setUp() = runTest {
        h = Harness(setOf(hook))
        h.session.currentToken()
        h.session.clear()
    }

    @After
    fun tearDown() = h.close()

    private suspend fun signIn() {
        h.session.save("S", "a@b.fr")
        server.enqueue(json(200, MY_ALERTS))
        repo.refresh().getOrThrow()
    }

    @Test
    fun `verifyCode hors ligne puis erreur serveur puis jeton vide`() = runTest {
        server.close()
        assertEquals(LoginResult.Offline, repo.verifyCode("a@b.fr", "1"))
        assertTrue(repo.requestLogin("a@b.fr").exceptionOrNull() is AccountError.Offline)
    }

    @Test
    fun `verifyCode 500 et jeton absent`() = runTest {
        server.enqueue(json(500, "{}"))
        assertEquals(LoginResult.ServerError, repo.verifyCode("a@b.fr", "1"))
        server.enqueue(json(200, """{"error":"x"}"""))
        assertEquals(LoginResult.InvalidCode, repo.verifyCode("a@b.fr", "1"))
        assertFalse(repo.signedIn.value)
    }

    @Test
    fun `verifyCode reussi declenche onSignedIn`() = runTest {
        server.enqueue(json(200, """{"token":"T","email":"a@b.fr"}"""))
        server.enqueue(json(200, MY_ALERTS))
        assertEquals(LoginResult.Success, repo.verifyCode("a@b.fr", "123456"))
        assertEquals(listOf("in"), hook.calls)
        assertEquals("a@b.fr", h.session.email())
    }

    @Test
    fun `lien magique 401 500 jeton absent et hors ligne`() = runTest {
        val magic = "0".repeat(64)
        server.enqueue(json(401, "{}"))
        assertEquals(LoginResult.InvalidCode, repo.consumeMagicToken(magic))
        server.enqueue(json(500, "{}"))
        assertEquals(LoginResult.ServerError, repo.consumeMagicToken(magic))
        server.enqueue(json(200, """{"email":"a@b.fr"}"""))
        assertEquals(LoginResult.InvalidCode, repo.consumeMagicToken(magic))
        server.close()
        assertEquals(LoginResult.Offline, repo.consumeMagicToken(magic))
        assertNull(h.session.currentToken())
    }

    @Test
    fun `refresh sans session et mises a jour sans compte`() = runTest {
        assertEquals(AccountError.NotSignedIn, repo.refresh().exceptionOrNull())
        assertEquals(AccountError.NotSignedIn, repo.updateProfile("FR", "05", "Gap").exceptionOrNull())
        assertEquals(AccountError.NotSignedIn, repo.updateDisplayName("x").exceptionOrNull())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `mapping du compte et identifiants abonnes`() = runTest {
        signIn()
        val acc = repo.account.value!!
        assertEquals("hugo", acc.displayName)
        val inst = acc.subscriptions.first().instances.single()
        assertEquals("10", inst.params["rayon"])
        assertEquals("[\"a\"]", inst.params["liste"])
        assertTrue(acc.subscriptions.first().muted)
        assertEquals(setOf("vigilance-meteo"), repo.subscribedIds.first())
    }

    @Test
    fun `rejet serveur avec message et corps non JSON`() = runTest {
        signIn()
        server.enqueue(json(400, """{"error":"Pseudo deja pris"}"""))
        assertEquals(AccountError.Rejected(400, "Pseudo deja pris"), repo.updateDisplayName("x").exceptionOrNull())
        server.enqueue(json(502, "<html>"))
        assertEquals(AccountError.Rejected(502, null), repo.updateDisplayName("x").exceptionOrNull())
    }

    @Test
    fun `nom affiche et profil France envoient les bons champs`() = runTest {
        signIn()
        server.takeRequest()
        server.enqueue(json(200, """{"display_name":"Hugo"}"""))
        assertEquals("Hugo", repo.updateDisplayName("  Hugo ").getOrThrow().displayName)
        assertEquals("""{"display_name":"Hugo"}""", server.takeRequest().body!!.utf8())

        server.enqueue(json(200, """{"country":"FR","departement":"38","region":"ARA","ville":null,"interests":["meteo"]}"""))
        val acc = repo.updateProfile("FR", "38", "   ").getOrThrow()
        assertEquals("ARA", acc.region)
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body, body.contains("\"departement\":\"38\"") && !body.contains("PACA"))

        server.enqueue(json(200, """{"country":"FR","interests":[]}"""))
        repo.updateProfile("FR", null, "Gap")
        assertTrue(server.takeRequest().body!!.utf8().contains("\"region\":\"ARA\""))
    }

    @Test
    fun `cache relu apres redemarrage si session presente`() = runTest {
        signIn()
        val again = AccountRepository(h.ctx, h.accountApi, h.session, h.favorites, emptySet())
        again.loadCached()
        assertEquals("a@b.fr", again.account.value!!.email)
        again.loadCached() // deja charge : sans effet
        h.session.clear()
        val signedOut = AccountRepository(h.ctx, h.accountApi, h.session, h.favorites, emptySet())
        signedOut.loadCached()
        assertNull(signedOut.account.value)
    }

    @Test
    fun `deconnexion appelle les hooks meme en echec et efface tout`() = runTest {
        signIn()
        server.enqueue(json(500, "{}"))
        repo.logout()
        assertEquals(listOf("before", "after"), hook.calls)
        assertNull(repo.account.value)
        assertNull(h.session.currentToken())
    }

    @Test
    fun `suppression refusee garde la session`() = runTest {
        signIn()
        server.enqueue(json(403, """{"error":"non"}"""))
        assertEquals(AccountError.Rejected(403, "non"), repo.deleteAccount().exceptionOrNull())
        assertEquals("S", h.session.currentToken())
    }

    @Test
    fun `favoris locaux pousses vers le serveur puis etat serveur`() = runTest {
        h.favorites.setFavorite("nouveau", true)
        h.favorites.setFavorite("vigilance-meteo", false)
        h.session.save("S", null)
        server.enqueue(json(200, MY_ALERTS))
        server.enqueue(json(200, """{"message":"ok"}"""))
        server.enqueue(json(200, """{"message":"ok"}"""))
        repo.refresh().getOrThrow()
        server.takeRequest()
        val sync = server.takeRequest()
        assertEquals("/api/favorites/sync", sync.url.encodedPath)
        assertEquals("""{"ids":["vigilance-meteo","nouveau"]}""", sync.body!!.utf8())
        val del = server.takeRequest()
        assertEquals("DELETE", del.method)
        assertEquals("""{"source_id":"vigilance-meteo"}""", del.body!!.utf8())
        assertEquals(setOf("nouveau"), h.favorites.favorites.first())
        assertTrue(h.favorites.pendingChanges().isEmpty())
    }

    @Test
    fun `echec de synchro des favoris reporte sans echec du rafraichissement`() = runTest {
        h.favorites.setFavorite("nouveau", true)
        h.session.save("S", null)
        server.enqueue(json(200, MY_ALERTS))
        server.enqueue(json(500, "{}"))
        assertTrue(repo.refresh().isSuccess)
        assertEquals(1, h.favorites.pendingChanges().size)
    }
}
