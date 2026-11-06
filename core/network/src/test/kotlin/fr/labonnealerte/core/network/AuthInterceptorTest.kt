package fr.labonnealerte.core.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class FakeTokens(var token: String?) : SessionTokenProvider {
    val unauthorized = mutableListOf<String>()
    override fun currentToken() = token
    override fun onUnauthorized(sentToken: String) {
        unauthorized += sentToken
    }
}

class AuthInterceptorTest {
    private val server = MockWebServer()
    private val other = MockWebServer()
    private val tokens = FakeTokens("TOK")
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.start()
        other.start()
        // Meme hote (127.0.0.1) pour le serveur ; l'autre serveur est joint par « localhost » (hote different).
        client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokens, server.url("/").toString())).build()
    }

    @After
    fun tearDown() {
        server.close()
        other.close()
    }

    private fun call(url: okhttp3.HttpUrl, auth: String? = null): Int {
        val b = Request.Builder().url(url)
        if (auth != null) b.header("Authorization", auth)
        return client.newCall(b.build()).execute().use { it.code }
    }

    @Test
    fun `Bearer ajoute vers l hote du service`() {
        server.enqueue(MockResponse.Builder().code(200).build())
        call(server.url("/api/x"))
        assertEquals("Bearer TOK", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `aucun Bearer vers un autre hote et 401 tiers ignore`() {
        other.enqueue(MockResponse.Builder().code(401).build())
        val url = other.url("/x").newBuilder().host(if (server.hostName == "localhost") "127.0.0.1" else "localhost").build()
        assertEquals(401, call(url))
        assertNull(other.takeRequest().headers["Authorization"])
        assertEquals(emptyList<String>(), tokens.unauthorized)
    }

    @Test
    fun `Authorization explicite non ecrase et 401 non signale`() {
        server.enqueue(MockResponse.Builder().code(401).build())
        call(server.url("/api/my-alerts"), "Bearer MAGIC")
        assertEquals("Bearer MAGIC", server.takeRequest().headers["Authorization"])
        assertEquals(emptyList<String>(), tokens.unauthorized)
    }

    @Test
    fun `401 signale avec le jeton envoye`() {
        server.enqueue(MockResponse.Builder().code(401).build())
        call(server.url("/api/x"))
        assertEquals(listOf("TOK"), tokens.unauthorized)
    }

    @Test
    fun `sans jeton ni en-tete ni signalement`() {
        tokens.token = null
        server.enqueue(MockResponse.Builder().code(401).build())
        call(server.url("/api/x"))
        assertNull(server.takeRequest().headers["Authorization"])
        assertEquals(emptyList<String>(), tokens.unauthorized)
    }

    @Test
    fun `URL de base invalide jamais de Bearer`() {
        val c = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokens, "pas une url")).build()
        server.enqueue(MockResponse.Builder().code(200).build())
        c.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
        assertNull(server.takeRequest().headers["Authorization"])
    }
}
