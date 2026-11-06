package fr.labonnealerte.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.labonnealerte.core.database.LbaDatabase
import fr.labonnealerte.core.model.Badge
import fr.labonnealerte.core.model.ParamType
import fr.labonnealerte.core.model.SourceState
import fr.labonnealerte.core.network.LbaApi
import fr.labonnealerte.core.network.lbaRetrofit
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogRepositoryTest {
    private val server = MockWebServer()
    private lateinit var db: LbaDatabase
    private lateinit var repo: CatalogRepository
    private var now = 1_000_000L

    /** Fixture reelle enregistree depuis la production (tools/fake-api/fixtures). */
    private val sourcesJson: String by lazy {
        val f = listOf("../../tools/fake-api/fixtures/sources.json", "tools/fake-api/fixtures/sources.json")
            .map { File(it) }.first { it.exists() }
        f.readText()
    }

    @Before
    fun setUp() {
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LbaDatabase::class.java)
            .allowMainThreadQueries().build()
        val api = lbaRetrofit(server.url("/").toString(), OkHttpClient()).create(LbaApi::class.java)
        repo = CatalogRepository(api, db.catalogDao()) { now }
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun ok(body: String, etag: String) =
        MockResponse.Builder().code(200).addHeader("ETag", etag).addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun `200 remplit le cache avec les 267 sources reelles`() = runTest {
        server.enqueue(ok(sourcesJson, "W/\"abc\""))
        assertEquals(RefreshResult.Updated, repo.refreshSources())
        val list = repo.sources.first()
        assertEquals(267, list.size)
        val vigilance = list.first { it.id == "vigilance-meteo" }
        assertEquals(Badge.OFFICIAL, vigilance.badge)
        assertEquals(ParamType.ENUM, vigilance.params.first().type)
        assertTrue(vigilance.params.first().options.any { it.value == "05" && it.label == "Hautes-Alpes" })
        assertTrue(list.all { it.state == SourceState.CALM || it.state == SourceState.ACTIVE || it.state == SourceState.NONE })
    }

    @Test
    fun `If-None-Match envoye et 304 conserve le cache`() = runTest {
        server.enqueue(ok(sourcesJson, "W/\"abc\""))
        repo.refreshSources()
        server.takeRequest()
        server.enqueue(MockResponse.Builder().code(304).build())
        assertEquals(RefreshResult.NotModified, repo.refreshSources(force = true))
        assertEquals("W/\"abc\"", server.takeRequest().headers["If-None-Match"])
        assertEquals(267, repo.sources.first().size)
    }

    @Test
    fun `cache frais pas de requete avant 60 s`() = runTest {
        server.enqueue(ok(sourcesJson, "W/\"abc\""))
        repo.refreshSources()
        now += 30_000
        assertEquals(RefreshResult.Fresh, repo.refreshSources())
        assertEquals(1, server.requestCount)
        now += 31_000
        server.enqueue(MockResponse.Builder().code(304).build())
        assertEquals(RefreshResult.NotModified, repo.refreshSources())
    }

    @Test
    fun `hors ligne garde le cache`() = runTest {
        server.enqueue(ok(sourcesJson, "W/\"abc\""))
        repo.refreshSources()
        server.close()
        assertEquals(RefreshResult.Offline, repo.refreshSources(force = true))
        assertEquals(267, repo.sources.first().size)
    }

    @Test
    fun `erreur serveur signalee sans vider le cache`() = runTest {
        server.enqueue(ok(sourcesJson, "W/\"abc\""))
        repo.refreshSources()
        server.enqueue(MockResponse.Builder().code(503).build())
        assertEquals(RefreshResult.Failed(503), repo.refreshSources(force = true))
        assertEquals(267, repo.sources.first().size)
    }
}
