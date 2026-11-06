package fr.labonnealerte.core.push

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.labonnealerte.core.data.AppPreferences
import fr.labonnealerte.core.data.CatalogRepository
import fr.labonnealerte.core.data.Clock
import fr.labonnealerte.core.data.session.SessionStore
import fr.labonnealerte.core.data.session.TokenCipher
import fr.labonnealerte.core.database.LbaDatabase
import fr.labonnealerte.core.model.QuietHours
import fr.labonnealerte.core.network.AccountApi
import fr.labonnealerte.core.network.AuthInterceptor
import fr.labonnealerte.core.network.LbaApi
import fr.labonnealerte.core.network.lbaRetrofit
import fr.labonnealerte.core.notifications.AlertNotifier
import fr.labonnealerte.core.notifications.Channels
import fr.labonnealerte.core.notifications.EventIngestor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private class FakeCipher : TokenCipher {
    override fun encrypt(plain: String) = "x:" + plain.reversed()
    override fun decrypt(encoded: String) = encoded.removePrefix("x:").reversed()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EventSyncTest {
    private val server = MockWebServer()
    private lateinit var app: Application
    private lateinit var db: LbaDatabase
    private lateinit var prefs: AppPreferences
    private lateinit var session: SessionStore
    private lateinit var sync: EventSync
    private val now = 1_800_000_000_000L

    @Before
    fun setUp() = runTest {
        server.start()
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Channels.createAll(app)
        db = Room.inMemoryDatabaseBuilder(app, LbaDatabase::class.java).allowMainThreadQueries().build()
        prefs = AppPreferences(app)
        prefs.clearAll()
        prefs.setQuietHours(QuietHours(enabled = false))
        session = SessionStore(app, FakeCipher())
        session.clear()
        session.save("SESSION123", "a@b.fr")
        val clock = Clock { now }
        val base = server.url("/").toString()
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(session, base)).build()
        val retrofit = lbaRetrofit(base, client)
        val catalog = CatalogRepository(retrofit.create(LbaApi::class.java), db.catalogDao(), clock)
        val ingestor = EventIngestor(app, db.notificationDao(), catalog, prefs, AlertNotifier(app), clock)
        sync = EventSync(retrofit.create(AccountApi::class.java), session, prefs, ingestor, clock)
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private fun event(id: Long, msg: String) =
        """{"id":$id,"source_id":"vigilance-meteo","source_name":"Vigilance météo","event":"activated",""" +
            """"message":"$msg","categories":[],"created_at":"2026-10-08T10:00:00Z"}"""

    @Test
    fun `releve paginee via since_id avance le curseur et envoie le Bearer`() = runTest {
        server.enqueue(json(200, """{"events":[${event(1, "Orages A")},${event(2, "Orages B")}],"last_id":2,"has_more":true}"""))
        server.enqueue(json(200, """{"events":[${event(3, "Orages C")}],"last_id":3,"has_more":false}"""))

        val r = sync.sync()

        assertEquals(SyncResult.Ok(3), r)
        assertEquals(2, server.requestCount)
        val p1 = server.takeRequest()
        assertEquals("/api/app/events", p1.url.encodedPath)
        assertEquals("0", p1.url.queryParameter("since_id"))
        assertEquals("Bearer SESSION123", p1.headers["Authorization"])
        val p2 = server.takeRequest()
        assertEquals("2", p2.url.queryParameter("since_id"))
        assertEquals("Bearer SESSION123", p2.headers["Authorization"])
        val rec = prefs.currentReception()
        assertEquals(3L, rec.lastEventId)
        assertTrue(rec.lastPollOk)
        assertEquals(3, db.notificationDao().observe().first().size)
    }

    @Test
    fun `push avec serveur en 503 affiche la notification de repli`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        val url = "https://labonnealerte.fr/source/vigilance-meteo/statut?departement=05"
        val payload = """{"title":"Vigilance météo","body":"Vigilance orange : orages","url":"$url"}"""

        sync.onPush(payload.toByteArray())

        val row = db.notificationDao().observe().first().single()
        assertEquals("vigilance-meteo", row.sourceId)
        assertNull(row.serverEventId)
        assertEquals(url, row.url)
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        assertNotNull(nm.getNotification("vigilance-meteo", AlertNotifier.NOTIF_ID))
        val rec = prefs.currentReception()
        assertEquals(now, rec.lastPushAtMillis)
        assertEquals(false, rec.lastPollOk)
    }

    private fun eventAt(id: Long, msg: String, createdAt: String) =
        """{"id":$id,"source_id":"vigilance-meteo","source_name":"Vigilance météo","event":"activated",""" +
            """"message":"$msg","categories":[],"created_at":"$createdAt"}"""

    private suspend fun pushActif(cursor: Long) {
        prefs.setPushEndpoint("https://ntfy.example/up123", onServer = true)
        prefs.advanceLastEventId(cursor)
    }

    @Test
    fun `push actif - evenement ancien trouve a l ouverture - push manque note`() = runTest {
        pushActif(5)
        server.enqueue(json(200, """{"events":[${event(6, "Orages A")}],"last_id":6,"has_more":false}"""))
        sync.sync()
        assertEquals(now, prefs.currentReception().missedPushAtMillis)
    }

    @Test
    fun `push actif - evenement recent - pas de push manque`() = runTest {
        pushActif(5)
        val recent = java.time.Instant.ofEpochMilli(now - 5 * 60_000).toString()
        server.enqueue(json(200, """{"events":[${eventAt(6, "Orages A", recent)}],"last_id":6,"has_more":false}"""))
        sync.sync()
        assertEquals(0L, prefs.currentReception().missedPushAtMillis)
    }

    @Test
    fun `premiere releve apres connexion - l historique n est pas un push manque`() = runTest {
        pushActif(0)
        server.enqueue(json(200, """{"events":[${event(1, "Orages A")}],"last_id":1,"has_more":false}"""))
        sync.sync()
        assertEquals(0L, prefs.currentReception().missedPushAtMillis)
    }

    @Test
    fun `releve declenchee par un push - pas de push manque`() = runTest {
        pushActif(5)
        server.enqueue(json(200, """{"events":[${event(6, "Orages A")}],"last_id":6,"has_more":false}"""))
        sync.onPush(null)
        assertEquals(0L, prefs.currentReception().missedPushAtMillis)
    }

    @Test
    fun `releve periodique sans push - pas de push manque`() = runTest {
        prefs.advanceLastEventId(5)
        server.enqueue(json(200, """{"events":[${event(6, "Orages A")}],"last_id":6,"has_more":false}"""))
        sync.sync()
        assertEquals(0L, prefs.currentReception().missedPushAtMillis)
    }

    @Test
    fun `sourceIdFromUrl n accepte que le site officiel`() {
        assertEquals("vigilance-meteo", EventSync.sourceIdFromUrl("https://labonnealerte.fr/source/vigilance-meteo/statut?departement=05"))
        assertEquals("vigilance-meteo", EventSync.sourceIdFromUrl("https://www.labonnealerte.fr/source/vigilance-meteo/statut"))
        assertNull(EventSync.sourceIdFromUrl("https://evil.example/source/vigilance-meteo/statut"))
        assertNull(EventSync.sourceIdFromUrl("https://labonnealerte.fr.evil.example/source/vigilance-meteo/statut"))
        assertNull(EventSync.sourceIdFromUrl("http://labonnealerte.fr/source/vigilance-meteo/statut"))
        assertNull(EventSync.sourceIdFromUrl(null))
    }
}
