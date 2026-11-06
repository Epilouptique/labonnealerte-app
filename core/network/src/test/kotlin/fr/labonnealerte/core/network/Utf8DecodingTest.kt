package fr.labonnealerte.core.network

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * APP-10 : rubriques du forum affichees en signes etranges (double encodage). Une reponse UTF-8 doit etre decodee en
 * UTF-8 meme si le serveur n'annonce pas de charset (reproduction du cas « charset retire »).
 */
class Utf8DecodingTest {
    private val server = MockWebServer()
    private val label = "Arts visuels \u00b7 caf\u00e9 \uD83D\uDCAC \u2728 \u00ab ok \u00bb"
    private val json = """{"categories":[{"slug":"discussion","label":"$label","count":1}],"recent":[]}"""

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun serve(contentType: String) {
        val bytes = Buffer().write(json.toByteArray(Charsets.UTF_8))
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", contentType).body(bytes).build())
    }

    private suspend fun fetch(client: OkHttpClient): String =
        lbaRetrofit(server.url("/").toString(), client).create(LbaApi::class.java).forumHome().categories.single().label

    @Test
    fun `sans charset - decode en UTF-8, avec ou sans intercepteur`() = runTest {
        serve("application/json")
        assertEquals(label, fetch(OkHttpClient()))
        serve("application/json")
        assertEquals(label, fetch(OkHttpClient.Builder().addInterceptor(Utf8JsonInterceptor()).build()))
    }

    @Test
    fun `charset utf-8 annonce - identique`() = runTest {
        serve("application/json; charset=utf-8")
        val decoded = fetch(OkHttpClient.Builder().addInterceptor(Utf8JsonInterceptor()).build())
        assertEquals(label, decoded)
        listOf("\u00c2\u00b7", "\u00f0\u0178", "\u00e2\u20ac").forEach { assertFalse(it, decoded.contains(it)) }
    }
}
