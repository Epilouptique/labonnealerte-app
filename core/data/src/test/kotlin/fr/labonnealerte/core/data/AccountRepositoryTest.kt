package fr.labonnealerte.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.labonnealerte.core.data.session.SessionStore
import fr.labonnealerte.core.data.session.TokenCipher
import fr.labonnealerte.core.database.LbaDatabase
import fr.labonnealerte.core.model.LoginResult
import fr.labonnealerte.core.network.AccountApi
import fr.labonnealerte.core.network.AuthInterceptor
import fr.labonnealerte.core.network.lbaRetrofit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Chiffrement factice reversible (Robolectric n'a pas d'AndroidKeyStore). */
private class FakeCipher : TokenCipher {
    override fun encrypt(plain: String) = "x:" + plain.reversed()
    override fun decrypt(encoded: String) = encoded.removePrefix("x:").reversed()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountRepositoryTest {
    private val server = MockWebServer()
    private lateinit var db: LbaDatabase
    private lateinit var session: SessionStore
    private lateinit var repo: AccountRepository

    private val myAlerts = """{"email":"a@b.fr","token":"SESSION123","sources":[{"id":"vigilance-meteo","name":"Vigilance météo",
        "subscribed":true,"muted":false,"instances":[{"params":{"departement":"05"},"label":"Hautes-Alpes","state":"inactive","muted":false}]}],
        "favorites":["vigilance-meteo"],"country":"FR","departement":"05","region":"Provence-Alpes-Côte d'Azur","ville":"Gap",
        "interests":[],"quiet_start":23,"quiet_end":8,"quiet_disabled":false,"points_balance":0,"dashboard_skin":"x"}"""

    @Before
    fun setUp() = runTest {
        server.start()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, LbaDatabase::class.java).allowMainThreadQueries().build()
        session = SessionStore(ctx, FakeCipher())
        session.clear()
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(session, server.url("/").toString())).build()
        val api = lbaRetrofit(server.url("/").toString(), client).create(AccountApi::class.java)
        repo = AccountRepository(ctx, api, session, FavoritesRepository(db.favoriteDao(), Clock.System), emptySet())
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun json(code: Int, body: String) = MockResponse.Builder().code(
        code
    ).addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun `bon code enregistre la session et charge le compte`() = runTest {
        server.enqueue(json(200, """{"token":"SESSION123","email":"a@b.fr"}"""))
        server.enqueue(json(200, myAlerts))
        assertEquals(LoginResult.Success, repo.verifyCode(" a@b.fr ", "123 456"))
        assertEquals("SESSION123", session.currentToken())
        val verify = server.takeRequest()
        assertTrue(verify.body!!.utf8().contains("\"code\":\"123456\""))
        assertEquals("Bearer SESSION123", server.takeRequest().headers["Authorization"])
        val acc = repo.account.value!!
        assertEquals("05", acc.departement)
        assertEquals(mapOf("departement" to "05"), acc.subscriptions.first().instances.first().params)
    }

    @Test
    fun `mauvais code`() = runTest {
        server.enqueue(json(400, """{"error":"Code incorrect ou expiré"}"""))
        assertEquals(LoginResult.InvalidCode, repo.verifyCode("a@b.fr", "000000"))
        assertNull(session.currentToken())
    }

    @Test
    fun `adresse sans compte - conditions demandees, rien n est enregistre`() = runTest {
        server.enqueue(
            json(
                409,
                """{"needs_terms":true,"terms_version":"confidentialite-2026-10","terms_url":"https://labonnealerte.fr/confidentialite"}"""
            )
        )
        val r = repo.verifyCode("nouveau@b.fr", "123456")
        assertEquals(LoginResult.TermsRequired("confidentialite-2026-10", "https://labonnealerte.fr/confidentialite"), r)
        assertNull(session.currentToken())
        val body = server.takeRequest().body!!.utf8()
        assertTrue("sans acceptation, le corps ne porte pas accept_terms", !body.contains("accept_terms"))
    }

    @Test
    fun `conditions acceptees - compte cree et session ouverte`() = runTest {
        server.enqueue(json(200, """{"token":"SESSION123","email":"nouveau@b.fr","created":true}"""))
        server.enqueue(json(200, myAlerts))
        assertEquals(LoginResult.Created, repo.verifyCode("nouveau@b.fr", "123456", "confidentialite-2026-10"))
        assertEquals("SESSION123", session.currentToken())
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("\"accept_terms\":true") && body.contains("\"terms_version\":\"confidentialite-2026-10\""))
    }

    @Test
    fun `lien des conditions hors du site refuse`() = runTest {
        server.enqueue(json(409, """{"needs_terms":true,"terms_version":"v1","terms_url":"https://exemple.invalid/piege"}"""))
        assertEquals(LoginResult.ServerError, repo.verifyCode("nouveau@b.fr", "123456"))
    }

    @Test
    fun `trop d essais`() = runTest {
        server.enqueue(json(429, """{"error":"x"}"""))
        assertEquals(LoginResult.TooManyAttempts, repo.verifyCode("a@b.fr", "000000"))
    }

    @Test
    fun `lien magique echange contre une session avec le jeton explicite`() = runTest {
        server.enqueue(json(200, myAlerts))
        val magic = "a".repeat(64)
        assertEquals(LoginResult.Success, repo.consumeMagicToken(magic))
        assertEquals("Bearer $magic", server.takeRequest().headers["Authorization"])
        assertEquals("SESSION123", session.currentToken())
    }

    @Test
    fun `lien magique mal forme refuse sans appel reseau`() = runTest {
        assertEquals(LoginResult.InvalidCode, repo.consumeMagicToken("../../x"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `401 sur un appel authentifie deconnecte`() = runTest {
        session.save("OLD", "a@b.fr")
        server.enqueue(json(401, """{"error":"Session invalide"}"""))
        assertTrue(repo.refresh().isFailure)
        assertTrue(session.expired.value)
    }

    @Test
    fun `profil hors France n envoie pas de departement`() = runTest {
        server.enqueue(json(200, """{"token":"S","email":"a@b.fr"}"""))
        server.enqueue(json(200, myAlerts))
        repo.verifyCode("a@b.fr", "123456")
        server.takeRequest()
        server.takeRequest()
        server.enqueue(json(200, """{"country":"BE","departement":null,"region":null,"ville":"Liège","interests":[]}"""))
        val r = repo.updateProfile("BE", "05", "Liège")
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body, body.contains("\"departement\":null") || !body.contains("departement\":\"05"))
        assertEquals("BE", r.getOrThrow().country)
    }

    @Test
    fun `suppression de compte envoie un corps vide et efface la session`() = runTest {
        session.save("S", "a@b.fr")
        server.enqueue(json(200, """{"ok":true}"""))
        assertTrue(repo.deleteAccount().isSuccess)
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("{}", req.body!!.utf8())
        assertNull(session.currentToken())
    }
}
