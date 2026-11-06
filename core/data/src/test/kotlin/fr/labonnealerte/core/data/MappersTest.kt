package fr.labonnealerte.core.data

import fr.labonnealerte.core.database.SourceEntity
import fr.labonnealerte.core.model.Badge
import fr.labonnealerte.core.model.DayStatus
import fr.labonnealerte.core.model.EventKind
import fr.labonnealerte.core.model.ParamType
import fr.labonnealerte.core.model.SourceState
import fr.labonnealerte.core.model.SourceType
import fr.labonnealerte.core.model.UserTask
import fr.labonnealerte.core.network.dto.CategoryDto
import fr.labonnealerte.core.network.dto.DepartementDto
import fr.labonnealerte.core.network.dto.GeoDto
import fr.labonnealerte.core.network.dto.GeoItemDto
import fr.labonnealerte.core.network.dto.HistoryDayDto
import fr.labonnealerte.core.network.dto.HistoryDto
import fr.labonnealerte.core.network.dto.HistoryEventDto
import fr.labonnealerte.core.network.dto.LePointActiveDto
import fr.labonnealerte.core.network.dto.LePointDto
import fr.labonnealerte.core.network.dto.LePointUpcomingDto
import fr.labonnealerte.core.network.dto.ParamDto
import fr.labonnealerte.core.network.dto.ParamValueDto
import fr.labonnealerte.core.network.dto.SourceDto
import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fonctions de conversion pures : pas besoin de Robolectric. */
class MappersTest {
    @Test
    fun `types de parametres et valeurs par defaut`() {
        val types = listOf("enum", "string", "number", "commune", "commune-coords", "dynamic-enum", "date")
        assertEquals(
            listOf(ParamType.ENUM, ParamType.STRING, ParamType.NUMBER, ParamType.COMMUNE, ParamType.COMMUNE_COORDS, ParamType.DYNAMIC_ENUM, ParamType.UNKNOWN),
            types.map { ParamDto("k", type = it).toDomain().type }
        )
        assertNull(ParamDto("k").toDomain().default)
        assertNull(ParamDto("k", default = JsonNull).toDomain().default)
        assertEquals("5", ParamDto("k", default = JsonPrimitive(5)).toDomain().default)
        assertEquals("a,b,", ParamDto("k", default = JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"), JsonObject(emptyMap())))).toDomain().default)
        assertNull(ParamDto("k", default = JsonObject(emptyMap())).toDomain().default)
        val p = ParamDto(
            "d", "Dept", "enum",
            listOf(
                ParamValueDto("05", "HA")
            ),
            multiple = true, required = true, min = 1.0, max = 2.0, maxLength = 3, pattern = "x", lowercase = true, placeholder = "p", hint = "h"
        ).toDomain()
        assertEquals("HA", p.options.single().label)
        assertTrue(p.multiple && p.required && p.lowercase)
        assertEquals("h", p.hint)
    }

    @Test
    fun `source aller retour par l entite`() {
        val dto = SourceDto(
            id = "s",
            name = "S",
            badge = "verified",
            type = "external",
            categories = listOf("a", "b"),
            paramsSchema = listOf(ParamDto("d", type = "enum")),
            state = "active",
            likesCount = 3
        )
        val e = dto.toEntity(7)
        assertEquals(7, e.position)
        assertEquals("a,b", e.categories)
        val s = e.toDomain()
        assertEquals(Badge.VERIFIED, s.badge)
        assertEquals(SourceType.EXTERNAL, s.type)
        assertEquals(SourceState.ACTIVE, s.state)
        assertEquals(ParamType.ENUM, s.params.single().type)
        assertEquals(listOf("a", "b"), s.categories)
    }

    @Test
    fun `badges types etats et json de parametres corrompu`() {
        val base = SourceDto(id = "s", name = "S").toEntity(0)
        fun ent(badge: String?, type: String?) = base.copy(badge = badge, type = type)
        assertEquals(
            listOf(Badge.OFFICIAL, Badge.COMMUNITY, Badge.UNKNOWN),
            listOf("official", "community", null).map { ent(it, null).toDomain().badge }
        )
        assertEquals(
            listOf(SourceType.INTERNAL, SourceType.LINKED, SourceType.USER_TASK, SourceType.COMMUNITY, SourceType.UNKNOWN),
            listOf("internal", "linked", "user-task", "community", "x").map { ent(null, it).toDomain().type }
        )
        assertEquals(SourceState.NONE, serverState("active", "linked"))
        assertEquals(SourceState.NONE, serverState(null, "internal"))
        assertEquals(SourceState.CALM, serverState("pending", "internal"))
        val broken: SourceEntity = base.copy(paramsJson = "{pas du json", categories = "")
        assertTrue(broken.toDomain().params.isEmpty())
        assertTrue(broken.toDomain().categories.isEmpty())
    }

    @Test
    fun `categories historique le point geo`() {
        val c = CategoryDto("m", "Meteo", "g").toEntity(2)
        assertEquals(2, c.position)
        assertEquals("Meteo", c.toDomain().label)
        assertEquals(
            listOf(EventKind.ACTIVATED, EventKind.DEACTIVATED, EventKind.FAILED, EventKind.UNKNOWN),
            listOf("activated", "deactivated", "failed", "?").map {
                eventKind(it)
            }
        )
        val h = HistoryDto(
            listOf(HistoryEventDto("failed", "m", "t")),
            listOf("active", "failed", "calm", "nodata").map { HistoryDayDto("d", it) }
        ).toDomain()
        assertEquals(listOf(DayStatus.ACTIVE, DayStatus.FAILED, DayStatus.CALM, DayStatus.NODATA), h.days.map { it.status })
        assertEquals(EventKind.FAILED, h.events.single().kind)
        val p = LePointDto(
            "t",
            listOf(LePointActiveDto("param", "a", "A", count = 4), LePointActiveDto("simple", "b", "B", count = 4)),
            listOf(LePointUpcomingDto("u", "s"))
        ).toDomain()
        assertEquals(listOf(4, null), p.active.map { it.count })
        assertEquals("s", p.upcoming.single().start)
        val g = GeoDto(listOf(GeoItemDto("FR", "France")), listOf(DepartementDto("05", "HA", "PACA")), listOf(GeoItemDto("93", "PACA"))).toDomain()
        assertEquals("05 - HA", g.departements.single().displayLabel)
        assertEquals("PACA", g.regions.single().name)
    }

    @Test
    fun `tache vers echeance`() {
        val e = UserTask(1, "X", null, "2026-12-31T10:00:00Z").toEcheance()
        assertEquals(LocalDate.of(2026, 12, 31), e.nextDue)
        assertFalse(e.counter)
        assertFalse(UserTask(1, "X", "time", null).toEcheance().counter)
        assertTrue(UserTask(1, "X", "counter", "bad").toEcheance().let { it.counter && it.nextDue == null })
    }
}
