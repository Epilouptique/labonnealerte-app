package fr.labonnealerte.core.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.labonnealerte.core.data.AppPreferences
import fr.labonnealerte.core.data.CatalogRepository
import fr.labonnealerte.core.data.Clock
import fr.labonnealerte.core.database.LbaDatabase
import fr.labonnealerte.core.model.EventKind
import fr.labonnealerte.core.model.IncomingEvent
import fr.labonnealerte.core.model.NotificationFamily
import fr.labonnealerte.core.model.QuietHours
import fr.labonnealerte.core.network.LbaApi
import fr.labonnealerte.core.network.lbaRetrofit
import java.util.Calendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EventIngestorTest {
    private val server = MockWebServer()
    private lateinit var app: Application
    private lateinit var db: LbaDatabase
    private lateinit var prefs: AppPreferences
    private lateinit var ingestor: EventIngestor
    private lateinit var shadowNm: ShadowNotificationManager
    private var now = 1_800_000_000_000L

    private val src = "vigilance-meteo"

    @Before
    fun setUp() = runTest {
        server.start()
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Channels.createAll(app)
        val nm = app.getSystemService(NotificationManager::class.java)
        shadowNm = shadowOf(nm)
        db = Room.inMemoryDatabaseBuilder(app, LbaDatabase::class.java).allowMainThreadQueries().build()
        prefs = AppPreferences(app)
        prefs.clearAll()
        prefs.setQuietHours(QuietHours(enabled = false))
        val clock = Clock { now }
        val api = lbaRetrofit(server.url("/").toString(), OkHttpClient()).create(LbaApi::class.java)
        val catalog = CatalogRepository(api, db.catalogDao(), clock)
        ingestor = EventIngestor(app, db.notificationDao(), catalog, prefs, AlertNotifier(app), clock)
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun ev(
        serverId: Long?,
        kind: EventKind = EventKind.ACTIVATED,
        message: String = "Vigilance orange : orages",
        origin: IncomingEvent.Origin = IncomingEvent.Origin.POLL,
        sourceId: String? = src
    ) = IncomingEvent(
        serverId = serverId,
        sourceId = sourceId,
        sourceName = "Vigilance météo",
        kind = kind,
        message = message,
        label = null,
        categories = emptyList(),
        url = "https://labonnealerte.fr/source/$src/statut",
        createdAtMillis = now,
        origin = origin
    )

    /** Notifications d'alerte affichees (hors resume de groupe). */
    private fun shown(): List<Notification> =
        shadowNm.allNotifications.filter { (it.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }

    private suspend fun rows() = db.notificationDao().observe().first()

    @Test
    fun `a - evenement ACTIVATED notifie, historise et avance le curseur`() = runTest {
        assertEquals(1, ingestor.ingest(listOf(ev(10))))
        assertEquals(1, shown().size)
        assertNotNull(shadowNm.getNotification(src, AlertNotifier.NOTIF_ID))
        val r = rows().single()
        assertEquals(10L, r.serverEventId)
        assertEquals("ev:10", r.dedupKey)
        assertEquals(10L, prefs.currentReception().lastEventId)
    }

    @Test
    fun `b - meme serverId reingere ne notifie pas`() = runTest {
        ingestor.ingest(listOf(ev(10)))
        app.getSystemService(NotificationManager::class.java).cancelAll()
        assertEquals(0, ingestor.ingest(listOf(ev(10))))
        assertEquals(0, shown().size)
        assertEquals(1, rows().size)
    }

    @Test
    fun `c - push sans id puis releve avec id une seule notification`() = runTest {
        assertEquals(1, ingestor.ingest(listOf(ev(null, origin = IncomingEvent.Origin.PUSH))))
        app.getSystemService(NotificationManager::class.java).cancelAll()
        assertEquals(0, ingestor.ingest(listOf(ev(77))))
        assertEquals(0, shown().size)
        val r = rows().single()
        assertEquals(77L, r.serverEventId)
        assertEquals(77L, prefs.currentReception().lastEventId)
    }

    @Test
    fun `d - DEACTIVATED annule la notification de la source deja vue`() = runTest {
        ingestor.ingest(listOf(ev(1)))
        assertNotNull(shadowNm.getNotification(src, AlertNotifier.NOTIF_ID))
        db.notificationDao().markSourceRead(src)
        assertEquals(1, ingestor.ingest(listOf(ev(2, kind = EventKind.DEACTIVATED, message = "Fin de vigilance"))))
        assertNull(shadowNm.getNotification(src, AlertNotifier.NOTIF_ID))
        assertEquals(2, rows().size)
    }

    @Test
    fun `e - heures calmes notification presente mais silencieuse`() = runTest {
        val h = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        prefs.setQuietHours(QuietHours(h, (h + 1) % 24, true))
        ingestor.ingest(listOf(ev(5)))
        val n = shadowNm.getNotification(src, AlertNotifier.NOTIF_ID)!!
        // NotificationCompat.setSilent : alerte deleguee au resume, sans son ni vibration.
        assertEquals(Notification.GROUP_ALERT_SUMMARY, n.groupAlertBehavior)
        assertNull(n.sound)
        assertNull(n.vibrate)
    }

    @Test
    fun `e ter - bon plan pendant les heures calmes reste silencieux malgre le canal avec son`() = runTest {
        val h = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        prefs.setQuietHours(QuietHours(h, (h + 1) % 24, true))
        ingestor.ingest(listOf(ev(6))) // sans categorie : famille Bons plans
        val n = shadowNm.getNotification(src, AlertNotifier.NOTIF_ID)!!
        assertEquals("bons_plans_2", n.channelId)
        assertEquals(Notification.GROUP_ALERT_SUMMARY, n.groupAlertBehavior)
        assertNull(n.sound)
    }

    @Test
    fun `e bis - hors heures calmes la notification n est pas silencieuse`() = runTest {
        ingestor.ingest(listOf(ev(5)))
        val n = shadowNm.getNotification(src, AlertNotifier.NOTIF_ID)!!
        assertEquals(Notification.GROUP_ALERT_ALL, n.groupAlertBehavior)
    }

    @Test
    fun `f - sourdine historise sans notifier`() = runTest {
        prefs.muteSource(src, now + 3_600_000L)
        assertEquals(1, ingestor.ingest(listOf(ev(9))))
        assertEquals(0, shown().size)
        assertEquals(1, rows().size)
    }

    @Test
    fun `g - vigilance ROUGE va sur le canal urgences`() = runTest {
        ingestor.ingest(listOf(ev(3, message = "🔴 Vigilance ROUGE : crues")))
        val n = shadowNm.getNotification(src, AlertNotifier.NOTIF_ID)!!
        assertEquals(NotificationFamily.URGENCES.channelId, n.channelId)
        assertEquals(NotificationFamily.URGENCES.channelId, rows().single().channel)
    }

    /**
     * BUG DE PRODUCTION (EventIngestor.handle) : la deduplication par contenu sur 24 h s'applique
     * aussi aux evenements qui ont un id serveur distinct. Une re-activation identique apres un
     * retour au calme (meme message, nouvel id) est avalee sans notification.
     */
    @Test
    fun `re-activation identique avec un nouvel id serveur notifie de nouveau`() = runTest {
        ingestor.ingest(listOf(ev(1)))
        ingestor.ingest(listOf(ev(2, kind = EventKind.DEACTIVATED, message = "Fin")))
        now += 3_600_000L
        assertEquals(1, ingestor.ingest(listOf(ev(3))))
        assertNotNull(shadowNm.getNotification(src, AlertNotifier.NOTIF_ID))
    }

    @Test
    fun `d bis - DEACTIVATED d une alerte pas vue garde la notification, silencieuse, retour au calme`() = runTest {
        ingestor.ingest(listOf(ev(1)))
        ingestor.ingest(listOf(ev(2, kind = EventKind.DEACTIVATED, message = "Fin de vigilance")))
        val n = shadowNm.getNotification(src, AlertNotifier.NOTIF_ID)!!
        assertEquals("Retour au calme", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(Notification.GROUP_ALERT_SUMMARY, n.groupAlertBehavior) // setSilent
        assertEquals(1, n.number)
    }

    @Test
    fun `test - canal dedie audible, pastille et nombre`() = runTest {
        ingestor.ingest(listOf(ev(null, origin = IncomingEvent.Origin.TEST, sourceId = null)))
        val n = shown().single()
        assertEquals(Channels.TEST_CHANNEL_ID, n.channelId)
        assertEquals(Notification.GROUP_ALERT_ALL, n.groupAlertBehavior) // pas silencieuse
        assertEquals(1, n.number)
        // Historique : toujours range dans « Fonctionnement de l'app ».
        assertEquals(NotificationFamily.SYSTEME.channelId, rows().single().channel)
    }

    @Test
    fun `test - heures calmes reelles le rendent silencieux`() = runTest {
        val h = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        prefs.setQuietHours(QuietHours(h, (h + 1) % 24, true))
        ingestor.ingest(listOf(ev(null, origin = IncomingEvent.Origin.TEST, sourceId = null)))
        assertEquals(Notification.GROUP_ALERT_SUMMARY, shown().single().groupAlertBehavior)
    }

    @Test
    fun `nombre = alertes non lues de la source, synthese = total`() = runTest {
        ingestor.ingest(listOf(ev(1, message = "Vigilance orange : orages")))
        now += 60_000
        ingestor.ingest(listOf(ev(2, message = "Vigilance orange : vent")))
        assertEquals(2, shadowNm.getNotification(src, AlertNotifier.NOTIF_ID)!!.number)
        now += 60_000
        ingestor.ingest(listOf(ev(3, sourceId = "coupure-eau")))
        val summary = shadowNm.getNotification(AlertNotifier.SUMMARY_TAG, AlertNotifier.NOTIF_ID)!!
        assertEquals("3 alertes", summary.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `tiroir - la notification reste tant que l alerte n est pas vue`() = runTest {
        val notifier = AlertNotifier(app)
        ingestor.ingest(listOf(ev(1)))
        ingestor.ingest(listOf(ev(2, sourceId = "coupure-eau")))
        fun unread() = kotlinx.coroutines.runBlocking { db.notificationDao().observeUnreadAlerts().first() }
            .map { UnreadAlert(it.id, it.sourceId) }
        // Ouvrir l'app sans lire : rien ne disparait.
        notifier.syncTray(unread())
        assertNotNull(shadowNm.getNotification(src, AlertNotifier.NOTIF_ID))
        assertNotNull(shadowNm.getNotification("coupure-eau", AlertNotifier.NOTIF_ID))
        // L'alerte est vue dans l'app : sa notification quitte le tiroir, l'autre reste.
        db.notificationDao().markSourceRead(src)
        notifier.syncTray(unread())
        assertNull(shadowNm.getNotification(src, AlertNotifier.NOTIF_ID))
        assertNotNull(shadowNm.getNotification("coupure-eau", AlertNotifier.NOTIF_ID))
    }

    @Test
    fun `source absente du catalogue - jamais le titre du test (APP-08)`() = runTest {
        // Nom inconnu partout : titre neutre, pas « Test de La Bonne Alerte ».
        ingestor.ingest(listOf(ev(1, sourceId = "inconnue").copy(sourceName = null)))
        assertEquals("Nouvelle alerte", shadowNm.getNotification("inconnue", AlertNotifier.NOTIF_ID)!!.extras.getString(Notification.EXTRA_TITLE))
        // Nom deja vu dans l'historique : repris quand le catalogue ne le connait pas.
        ingestor.ingest(listOf(ev(2)))
        now += 60_000
        ingestor.ingest(listOf(ev(3, message = "Vigilance orange : vent").copy(sourceName = null)))
        assertEquals("Vigilance météo", shadowNm.getNotification(src, AlertNotifier.NOTIF_ID)!!.extras.getString(Notification.EXTRA_TITLE))
        // Libelle de zone seul : il sert de titre.
        ingestor.ingest(listOf(ev(4, sourceId = "autre").copy(sourceName = null, label = "Gard")))
        assertEquals("Gard", shadowNm.getNotification("autre", AlertNotifier.NOTIF_ID)!!.extras.getString(Notification.EXTRA_TITLE))
        // Un vrai test garde son titre.
        ingestor.ingest(listOf(ev(null, origin = IncomingEvent.Origin.TEST, sourceId = null).copy(sourceName = null)))
        assertEquals(1, shown().count { it.extras.getString(Notification.EXTRA_TITLE) == "Test de La Bonne Alerte" })
    }

    @Test
    fun `alerte de test realiste - chemin des vraies alertes, marquee test, locale (APP-09)`() = runTest {
        val e = TestAlertScheduler.event(now, "Tarif streaming", "Netflix (test)")
        assertEquals(1, ingestor.ingest(listOf(e)))
        val n = shadowNm.getNotification(TestAlertScheduler.SOURCE_ID, AlertNotifier.NOTIF_ID)!!
        // Canal de la famille de la source, pas le canal du test immediat.
        assertEquals(false, n.channelId == Channels.TEST_CHANNEL_ID)
        assertEquals(false, n.channelId == NotificationFamily.SYSTEME.channelId)
        assertEquals(rows().single().channel, n.channelId)
        assertEquals("Tarif streaming · Netflix (test)", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(AlertNotifier.GROUP, n.group)
        assertEquals(1, n.number)
        assertEquals(Notification.GROUP_ALERT_ALL, n.groupAlertBehavior) // sonne (hors heures calmes)
        // Historique : marquee test, sans id serveur ; le curseur serveur ne bouge pas.
        val r = rows().single()
        assertEquals("test", r.kind)
        assertNull(r.serverEventId)
        assertEquals(0L, prefs.currentReception().lastEventId)
        // Un second appui produit une nouvelle alerte (pas de deduplication du test).
        now += 60_000
        assertEquals(1, ingestor.ingest(listOf(TestAlertScheduler.event(now, "Tarif streaming", "Netflix (test)"))))
        assertEquals(2, shadowNm.getNotification(TestAlertScheduler.SOURCE_ID, AlertNotifier.NOTIF_ID)!!.number)
    }

    @Test
    fun `pastille de l onglet - alertes en cours non vues (APP-09)`() = runTest {
        val dao = db.notificationDao()
        suspend fun count() = dao.observeActiveUnseenCount().first()
        ingestor.ingest(listOf(ev(1)))
        ingestor.ingest(listOf(ev(2, sourceId = "coupure-eau")))
        now += 60_000
        ingestor.ingest(listOf(ev(3, sourceId = "coupure-eau", message = "Coupure prolongee")))
        assertEquals(2, count()) // une par source, meme avec deux evenements
        // Retour au calme : plus en cours.
        now += 60_000
        ingestor.ingest(listOf(ev(4, sourceId = "coupure-eau", kind = EventKind.DEACTIVATED, message = "Fin")))
        assertEquals(1, count())
        // Alerte vue (fiche ouverte) : plus comptee.
        dao.markSourceRead(src)
        assertEquals(0, count())
        // Test immediat (sans source) et echeance : non comptes.
        ingestor.ingest(listOf(ev(null, origin = IncomingEvent.Origin.TEST, sourceId = null)))
        ingestor.ingest(listOf(ev(null, origin = IncomingEvent.Origin.ECHEANCE, sourceId = null, message = "Controle technique")))
        assertEquals(0, count())
        // Alerte de test realiste : comptee comme une alerte en cours.
        ingestor.ingest(listOf(TestAlertScheduler.event(now, "Tarif streaming", "Netflix (test)")))
        assertEquals(1, count())
    }
}
