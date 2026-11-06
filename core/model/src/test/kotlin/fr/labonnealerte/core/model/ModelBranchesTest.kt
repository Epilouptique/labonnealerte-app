package fr.labonnealerte.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelBranchesTest {
    @Test
    fun `compte serialise puis relu a l identique`() {
        val acc = Account(
            email = "a@b.fr", displayName = null, country = "FR", departement = "05", region = "PACA", ville = "Gap",
            interests = listOf("meteo"), emailEnabled = true, pushEndpointsCount = 1, quietStart = 23, quietEnd = 8,
            quietDisabled = false, hideCommunityReports = true, favorites = listOf("s"),
            subscriptions = listOf(
                Subscription(
                    "s",
                    "S",
                    "active",
                    subscribed = true,
                    muted = false,
                    instances = listOf(SubscriptionInstance(mapOf("departement" to "05"), "HA", "active", false)),
                    tasks = listOf(UserTask(1, "Vidange", "time", "2027-01-01"))
                )
            )
        )
        val raw = Json.encodeToString(Account.serializer(), acc)
        assertEquals(acc, Json.decodeFromString(Account.serializer(), raw))
        val ev = AccountEvent(EventKind.ACTIVATED, null, "t", "s", null)
        assertEquals("s", ev.sourceId)
        assertTrue(LoginResult.Success != LoginResult.InvalidCode)
    }

    @Test
    fun `objets du catalogue`() {
        val d = Departement("05", "Hautes-Alpes", "PACA")
        assertEquals("05 - Hautes-Alpes", d.displayLabel)
        val geo = Geo(listOf(GeoItem("FR", "France")), listOf(d), emptyList())
        assertEquals("PACA", geo.departements.single().region)
        val p = LePoint("t", listOf(LePointActive("s", "S", null, null, null, 3, listOf("a"))), listOf(LePointUpcoming("u", null, null, null)))
        assertEquals(3, p.active.single().count)
        val h = SourceHistory(listOf(HistoryEvent(EventKind.FAILED, "m", "t")), listOf(HistoryDay("d", DayStatus.NODATA)))
        assertEquals(DayStatus.NODATA, h.days.single().status)
        assertEquals("grp", Category("c", "C", "grp").group)
    }

    @Test
    fun `heures calmes dans les deux sens et desactivees`() {
        val night = QuietHours()
        assertTrue(night.isQuiet(23))
        assertTrue(night.isQuiet(2))
        assertFalse(night.isQuiet(8))
        val day = QuietHours(12, 14)
        assertTrue(day.isQuiet(13))
        assertFalse(day.isQuiet(14))
        assertFalse(QuietHours(1, 5, enabled = false).isQuiet(2))
        assertFalse(QuietHours(5, 5).isQuiet(5))
    }

    @Test
    fun `anti bruit et familles particulieres`() {
        assertTrue(NoiseGuard.allowSound(100, 10, Severity.URGENT))
        assertFalse(NoiseGuard.allowSound(10, 10, Severity.NORMAL))
        assertTrue(NoiseGuard.allowSound(9, 10, Severity.LOW))
        assertEquals(NotificationFamily.COMMUNAUTE, EventClassifier.family(emptyList(), SourceType.COMMUNITY, "chat perdu") { null })
        assertEquals(NotificationFamily.ECHEANCES, EventClassifier.family(emptyList(), SourceType.USER_TASK, null) { null })
        assertEquals(NotificationFamily.BONS_PLANS, EventClassifier.family(listOf("x"), null, null) { null })
        assertEquals(NotificationFamily.SERVICES, EventClassifier.family(listOf("x"), null, "ok") { "energie" })
        assertEquals(Severity.NORMAL, EventClassifier.severity(null))
        assertEquals("urgences", NotificationFamily.URGENCES.channelId)
        val e = IncomingEvent(1, "s", "S", EventKind.ACTIVATED, "m", null, emptyList(), null, 0, IncomingEvent.Origin.POLL)
        assertEquals(IncomingEvent.Origin.POLL, e.origin)
    }

    @Test
    fun `source parametree ou non`() {
        val base = Source(
            "s", "S", null, null, null, Badge.UNKNOWN, SourceType.INTERNAL, null, emptyList(), emptyList(),
            0, null, null, SourceState.CALM, 0, null, 0
        )
        assertFalse(base.isParameterized)
        val p = ParamDescriptor("k", "K", ParamType.ENUM, listOf(ParamOption("1", "Un")), false, true, null, null, null, null)
        assertTrue(base.copy(params = listOf(p)).isParameterized)
        assertNull(p.hint)
    }
}
