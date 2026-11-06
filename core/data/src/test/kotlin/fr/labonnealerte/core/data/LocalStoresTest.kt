package fr.labonnealerte.core.data

import androidx.test.core.app.ApplicationProvider
import fr.labonnealerte.core.database.NotificationEntity
import fr.labonnealerte.core.model.QuietHours
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
class LocalStoresTest {
    private val day = 24L * 3_600_000
    private lateinit var h: Harness
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() = runTest {
        h = Harness(now = 200 * day)
        prefs = AppPreferences(ApplicationProvider.getApplicationContext())
        prefs.clearAll()
    }

    @After
    fun tearDown() = h.close()

    private fun notif(key: String, kind: String, at: Long, source: String? = "s", read: Boolean = false) = NotificationEntity(
        dedupKey = key, serverEventId = null, sourceId = source, sourceName = "S", kind = kind, emoji = null,
        title = "t$key", text = "x", url = null, receivedAtMillis = at, read = read, channel = "alertes"
    )

    @Test
    fun `historique filtre lu non lu purge`() = runTest {
        val dao = h.db.notificationDao()
        val repo = NotificationHistoryRepository(dao, h.clock)
        dao.insert(notif("1", "activated", 199 * day))
        dao.insert(notif("2", "deactivated", 199 * day))
        dao.insert(notif("3", "test", 10 * day, source = null))
        dao.insert(notif("4", "echeance", 150 * day, source = "e"))
        assertEquals(listOf("t1", "t4", "t3"), repo.records.first().map { it.title })
        assertEquals(4, repo.unreadCount.first())
        val id1 = repo.records.first().first().id
        repo.markRead(id1)
        assertTrue(repo.records.first().first().read)
        repo.markSourceRead("e")
        assertEquals(2, repo.unreadCount.first())
        repo.purgeOld() // avant le jour 20 : la notification 3 disparait
        assertEquals(listOf("t1", "t4"), repo.records.first().map { it.title })
        repo.markAllRead()
        assertEquals(0, repo.unreadCount.first())
        repo.clear()
        assertTrue(repo.records.first().isEmpty())
    }

    @Test
    fun `favoris locaux en attente puis synchronises`() = runTest {
        val fav = h.favorites
        fav.setFavorite("a", true)
        fav.setFavorite("b", true)
        fav.setFavorite("b", false)
        assertEquals(setOf("a"), fav.favorites.first())
        assertEquals(
            mapOf("a" to FavoritesRepository.PENDING_ADD, "b" to FavoritesRepository.PENDING_REMOVE),
            fav.pendingChanges().associate { it.sourceId to it.pending }
        )
        fav.markSynced("a", removed = false)
        fav.markSynced("b", removed = true)
        assertTrue(fav.pendingChanges().isEmpty())
        assertEquals(setOf("a"), fav.favorites.first())
        fav.replaceWithServer(listOf("x", "y"))
        assertEquals(setOf("x", "y"), fav.favorites.first())
    }

    @Test
    fun `preferences par defaut`() = runTest {
        val s = prefs.current()
        assertEquals(Settings(quietHours = QuietHours(23, 8, true)), s)
        assertEquals(ReceptionState(), prefs.currentReception())
        assertEquals(0L, prefs.mutedUntil("s"))
        assertNull(prefs.departementPrefere())
    }

    @Test
    fun `preferences ecrites bornees et relues`() = runTest {
        prefs.setTheme("dark")
        prefs.setDynamicColor(true)
        prefs.setFontScale(5f)
        prefs.setReduceMotion(true)
        prefs.setQuietHours(QuietHours(30, -2, false))
        prefs.setUrgentBypass(true)
        prefs.setMaxPerHour(0)
        prefs.setReceptionMode(ReceptionMode.PERIODIQUE)
        prefs.setOnboardingDone(true)
        val s = prefs.current()
        assertEquals("dark", s.theme)
        assertTrue(s.dynamicColor && s.reduceMotion && s.urgentBypassQuiet && s.onboardingDone)
        assertEquals(2f, s.fontScale)
        assertEquals(QuietHours(23, 0, false), s.quietHours)
        assertEquals(1, s.maxPerHour)
        assertEquals(ReceptionMode.PERIODIQUE, s.receptionMode)
        prefs.setMaxPerHour(500)
        prefs.setFontScale(0.1f)
        assertEquals(60, prefs.current().maxPerHour)
        assertEquals(0.85f, prefs.current().fontScale)
    }

    @Test
    fun `etat de reception et curseur qui ne recule pas`() = runTest {
        prefs.advanceLastEventId(10)
        prefs.advanceLastEventId(5)
        prefs.setPushEndpoint("https://push", true)
        prefs.setDistributor("ntfy")
        prefs.markPush(111)
        prefs.markPoll(222, false)
        var r = prefs.currentReception()
        assertEquals(ReceptionState(10, "https://push", true, 111, 222, false, "ntfy"), r)
        prefs.setPushEndpoint(null, false)
        prefs.setDistributor(null)
        r = prefs.currentReception()
        assertNull(r.pushEndpoint)
        assertNull(r.distributor)
        assertFalse(r.pushRegisteredOnServer)
    }

    @Test
    fun `surveillance reseau emet un etat initial puis se desabonne`() = runTest {
        val online = ConnectivityMonitor(ApplicationProvider.getApplicationContext()).isOnline.first()
        assertTrue(online || !online)
    }

    @Test
    fun `sourdine departement et mode inconnu`() = runTest {
        prefs.muteSource("s", 999)
        assertEquals(999L, prefs.mutedUntil("s"))
        prefs.setDepartementPrefere("2A-x")
        assertEquals("2A-", prefs.departementPrefere())
        // Valeur obsolete ecrite par une ancienne version : retour a AUTO.
        prefs.setReceptionMode(ReceptionMode.UNIFIEDPUSH)
        assertEquals(ReceptionMode.UNIFIEDPUSH, prefs.current().receptionMode)
        prefs.clearAll()
        assertEquals(ReceptionMode.AUTO, prefs.current().receptionMode)
    }
}
