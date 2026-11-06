package fr.labonnealerte.core.network

import fr.labonnealerte.core.network.dto.AppConfigDto
import fr.labonnealerte.core.network.dto.AppEventDto
import fr.labonnealerte.core.network.dto.AppEventsDto
import fr.labonnealerte.core.network.dto.AppLoginDto
import fr.labonnealerte.core.network.dto.CategoryDto
import fr.labonnealerte.core.network.dto.CommunesDto
import fr.labonnealerte.core.network.dto.ConfirmTaskDto
import fr.labonnealerte.core.network.dto.CreateReportBody
import fr.labonnealerte.core.network.dto.CreateReportDto
import fr.labonnealerte.core.network.dto.CreateTaskBody
import fr.labonnealerte.core.network.dto.CreateTaskDto
import fr.labonnealerte.core.network.dto.DepartementDto
import fr.labonnealerte.core.network.dto.DisplayNameBody
import fr.labonnealerte.core.network.dto.DisplayNameDto
import fr.labonnealerte.core.network.dto.EmailBody
import fr.labonnealerte.core.network.dto.EmailPrefBody
import fr.labonnealerte.core.network.dto.FavoriteBody
import fr.labonnealerte.core.network.dto.FavoriteSyncBody
import fr.labonnealerte.core.network.dto.ForumCategoryDto
import fr.labonnealerte.core.network.dto.ForumHomeDto
import fr.labonnealerte.core.network.dto.ForumMemberDto
import fr.labonnealerte.core.network.dto.ForumPostDto
import fr.labonnealerte.core.network.dto.ForumReplyBody
import fr.labonnealerte.core.network.dto.ForumReportBody
import fr.labonnealerte.core.network.dto.ForumThreadDto
import fr.labonnealerte.core.network.dto.ForumTopicBody
import fr.labonnealerte.core.network.dto.ForumTopicCreatedDto
import fr.labonnealerte.core.network.dto.ForumTopicDto
import fr.labonnealerte.core.network.dto.ForumTopicsDto
import fr.labonnealerte.core.network.dto.GeoDto
import fr.labonnealerte.core.network.dto.GeoItemDto
import fr.labonnealerte.core.network.dto.HistoryDayDto
import fr.labonnealerte.core.network.dto.HistoryDto
import fr.labonnealerte.core.network.dto.HistoryEventDto
import fr.labonnealerte.core.network.dto.InstanceDto
import fr.labonnealerte.core.network.dto.LePointActiveDto
import fr.labonnealerte.core.network.dto.LePointDto
import fr.labonnealerte.core.network.dto.LePointUpcomingDto
import fr.labonnealerte.core.network.dto.LikeDto
import fr.labonnealerte.core.network.dto.MessageDto
import fr.labonnealerte.core.network.dto.MuteBody
import fr.labonnealerte.core.network.dto.MyAlertsDto
import fr.labonnealerte.core.network.dto.MyHistoryDto
import fr.labonnealerte.core.network.dto.MyHistoryEventDto
import fr.labonnealerte.core.network.dto.MySourceDto
import fr.labonnealerte.core.network.dto.OkDto
import fr.labonnealerte.core.network.dto.ParamDto
import fr.labonnealerte.core.network.dto.ParamLookupDto
import fr.labonnealerte.core.network.dto.ParamValueDto
import fr.labonnealerte.core.network.dto.ProfileBody
import fr.labonnealerte.core.network.dto.ProfileDto
import fr.labonnealerte.core.network.dto.PushEndpointBody
import fr.labonnealerte.core.network.dto.PushKeysDto
import fr.labonnealerte.core.network.dto.PushSubscriptionBody
import fr.labonnealerte.core.network.dto.PushSubscriptionDto
import fr.labonnealerte.core.network.dto.QuietHoursBody
import fr.labonnealerte.core.network.dto.QuietHoursDto
import fr.labonnealerte.core.network.dto.ReportDto
import fr.labonnealerte.core.network.dto.SourceDto
import fr.labonnealerte.core.network.dto.SpotBody
import fr.labonnealerte.core.network.dto.SupportLinksDto
import fr.labonnealerte.core.network.dto.TaskDto
import fr.labonnealerte.core.network.dto.ToggleBody
import fr.labonnealerte.core.network.dto.ToggleParamBody
import fr.labonnealerte.core.network.dto.ToggleParamDto
import fr.labonnealerte.core.network.dto.VapidKeyDto
import fr.labonnealerte.core.network.dto.VerifyCodeBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Valeurs par defaut du contrat quand l'app construit elle-meme un DTO (constructeur Kotlin,
 * pas le deserialiseur) ; chaque accesseur est lu pour verifier qu'aucun ne leve d'exception.
 */
class DtoDefaultsTest {
    private val topic = ForumTopicDto(1, "s", "T", "c")

    private val all: List<Any> = listOf(
        EmailBody("a"), MessageDto(), OkDto(), MyAlertsDto(), MySourceDto("s", "S"), InstanceDto(), TaskDto(1, "t"),
        MyHistoryDto(), MyHistoryEventDto("e", createdAt = "t", sourceId = "s"), ToggleBody("s", true),
        ToggleParamBody("s", emptyMap(), true), ToggleParamDto("s", true), MuteBody("s", true),
        ProfileBody(null, null, null, null, emptyList()), ProfileDto(), DisplayNameBody("d"), DisplayNameDto(),
        EmailPrefBody(true), QuietHoursBody(false, 1, 2), QuietHoursDto(), FavoriteBody("s"), FavoriteSyncBody(emptyList()),
        LikeDto("s"), PushSubscriptionBody(PushSubscriptionDto("e", PushKeysDto("p", "a"))), PushEndpointBody("e"), VapidKeyDto(),
        CreateTaskBody("l", "d", 1, "day"), CreateTaskDto(), ConfirmTaskDto(1), ReportDto(1, "chat-perdu"),
        CreateReportBody("t", "v", null, null, null), CreateReportDto(), SpotBody("w", "w"), ForumTopicBody("c", "t", "b"),
        ForumTopicCreatedDto(), ForumReplyBody("b"), ForumReportBody(null), AppConfigDto(), AppEventsDto(),
        AppEventDto(1, "s", event = "e", createdAt = "t"), VerifyCodeBody("e", "c"), AppLoginDto(),
        SourceDto("s", "S"), ParamDto("k"), ParamValueDto("v", "l"), CategoryDto("s", "l", "g"), HistoryDto(),
        HistoryEventDto("e", createdAt = "t"), HistoryDayDto("d", "s"), LePointDto(), LePointActiveDto("k", "i", "n"),
        LePointUpcomingDto("u"), GeoDto(), GeoItemDto("c", "n"), DepartementDto("c", "n"), CommunesDto(), SupportLinksDto(),
        ParamLookupDto(), ForumHomeDto(), ForumCategoryDto("s", "l"), topic, ForumTopicsDto(), ForumPostDto(1, "b", "t"),
        ForumThreadDto(topic), ForumMemberDto("p")
    )

    @Test
    fun `valeurs par defaut du contrat`() {
        val a = MyAlertsDto()
        assertTrue(a.emailEnabled)
        assertEquals(23, a.quietStart)
        assertEquals(8, a.quietEnd)
        assertEquals("string", ParamDto("k").type)
        assertEquals("Membre", topic.author)
        assertEquals(1, ForumTopicsDto().page)
        assertFalse(VapidKeyDto().enabled)
        assertEquals(0, ReportDto(1, "x").extensionsCount)
    }

    @Test
    fun `chaque accesseur de chaque DTO est lisible`() {
        var read = 0
        for (dto in all) {
            for (m in dto.javaClass.methods) {
                val n = m.name
                if (m.parameterCount == 0 && m.declaringClass == dto.javaClass && (n.startsWith("get") || n.startsWith("is") || n.startsWith("component"))) {
                    m.invoke(dto)
                    read++
                }
            }
            assertNotNull(dto.toString())
            assertEquals(dto.hashCode(), dto.hashCode())
        }
        assertTrue("accesseurs lus : $read", read > 300)
    }
}
