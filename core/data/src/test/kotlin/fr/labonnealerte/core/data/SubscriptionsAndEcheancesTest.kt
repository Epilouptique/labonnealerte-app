package fr.labonnealerte.core.data

import fr.labonnealerte.core.model.EventKind
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeReminders : EcheanceReminders {
    val scheduled = mutableListOf<List<Echeance>>()
    override fun reschedule(items: List<Echeance>) {
        scheduled += items
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubscriptionsAndEcheancesTest {
    private lateinit var h: Harness
    private lateinit var subs: SubscriptionsRepository
    private lateinit var echeances: EcheancesRepository
    private val reminders = FakeReminders()

    @Before
    fun setUp() = runTest {
        h = Harness()
        h.session.currentToken()
        h.session.save("S", "a@b.fr")
        subs = SubscriptionsRepository(h.accountApi, h.account)
        echeances = EcheancesRepository(h.accountApi, h.account, reminders)
    }

    @After
    fun tearDown() = h.close()

    private fun body() = h.server.takeRequest().body!!.utf8()

    @Test
    fun `abonnement puis relecture du compte`() = runTest {
        h.server.enqueue(json(200, """{"source_id":"s","subscribed":true}"""))
        h.server.enqueue(json(200, MY_ALERTS))
        assertTrue(subs.setSubscribed("s", true).isSuccess)
        assertEquals("""{"source_id":"s","subscribed":true}""", body())
        assertEquals("/api/my-alerts", h.server.takeRequest().url.encodedPath)
        assertEquals("a@b.fr", h.account.account.value!!.email)
    }

    @Test
    fun `instance parametree convertit les nombres`() = runTest {
        h.server.enqueue(json(200, """{"source_id":"s","subscribed":true,"label":"Gap (05)"}"""))
        h.server.enqueue(json(200, MY_ALERTS))
        val r = subs.setInstance("s", mapOf("rayon" to 10.0, "seuil" to 2.5, "n" to 3, "d" to "05"), true)
        assertEquals("Gap (05)", r.getOrThrow())
        assertTrue(body().contains("\"params\":{\"rayon\":10,\"seuil\":2.5,\"n\":3,\"d\":\"05\"}"))
    }

    @Test
    fun `pause hors ligne et refus serveur`() = runTest {
        h.server.enqueue(json(429, """{"error":"Trop de requetes"}"""))
        assertEquals(AccountError.Rejected(429, "Trop de requetes"), subs.setMuted("s", true).exceptionOrNull())
        h.server.enqueue(json(500, "oops"))
        assertEquals(AccountError.Rejected(500, null), subs.setMuted("s", false).exceptionOrNull())
        h.server.close()
        assertEquals(AccountError.Offline, subs.setMuted("s", true).exceptionOrNull())
    }

    @Test
    fun `evenements recents`() = runTest {
        h.server.enqueue(
            json(
                200,
                """{"events":[{"event":"activated","created_at":"t","source_id":"s","source_name":"S"},{"event":"zz","created_at":"t","source_id":"s"}]}"""
            )
        )
        val ev = subs.recentEvents().getOrThrow()
        assertEquals(listOf(EventKind.ACTIVATED, EventKind.UNKNOWN), ev.map { it.kind })
        h.server.enqueue(json(500, "{}"))
        assertTrue(subs.recentEvents().isFailure)
    }

    @Test
    fun `echeances dedoublonnees triees et rappels replanifies`() = runTest {
        h.server.enqueue(json(200, MY_ALERTS))
        assertTrue(echeances.refresh().isSuccess)
        val items = echeances.items.first()
        assertEquals(listOf(3L, 2L, 1L, 4L), items.map { it.id })
        assertEquals(LocalDate.of(2027, 3, 1), items[1].nextDue)
        assertTrue(items.first { it.id == 1L }.counter)
        assertNull(items.first { it.id == 4L }.nextDue)
        assertEquals(4, reminders.scheduled.single().size)
        assertEquals(30L, items.first().daysLeft(LocalDate.of(2026, 10, 2)))
        assertNull(items.last().daysLeft(LocalDate.of(2026, 10, 2)))
    }

    @Test
    fun `creer confirmer supprimer une echeance`() = runTest {
        h.server.enqueue(json(200, """{"task":{"id":9,"label":"X"}}"""))
        h.server.enqueue(json(200, MY_ALERTS))
        assertTrue(echeances.create(" Vidange ", LocalDate.of(2026, 1, 31), 6, Periodicite.MOIS, 7).isSuccess)
        val b = body()
        assertTrue(b, b.contains("\"label\":\"Vidange\"") && b.contains("\"anchor_date\":\"2026-01-31\"") && b.contains("\"periodicity_unit\":\"month\""))
        h.server.takeRequest()

        h.server.enqueue(json(200, """{"id":9}"""))
        h.server.enqueue(json(200, MY_ALERTS))
        assertTrue(echeances.confirm(9).isSuccess)
        assertEquals("/api/user-tasks/9/confirm", h.server.takeRequest().url.encodedPath)
        h.server.takeRequest()

        h.server.enqueue(json(404, """{"error":"Introuvable"}"""))
        assertEquals(AccountError.Rejected(404, "Introuvable"), echeances.delete(9).exceptionOrNull())
        h.server.enqueue(json(500, "x"))
        assertEquals(AccountError.Rejected(500, null), echeances.delete(9).exceptionOrNull())
        h.server.close()
        assertEquals(AccountError.Offline, echeances.delete(9).exceptionOrNull())
    }

    @Test
    fun `periodicites api`() {
        assertEquals(listOf("day", "week", "month", "year"), Periodicite.entries.map { it.api })
    }
}
