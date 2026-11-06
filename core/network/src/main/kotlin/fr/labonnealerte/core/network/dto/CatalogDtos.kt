package fr.labonnealerte.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Formes exactes renvoyees par le serveur (docs/contrat-api-app.md §3.1, §4).
// Champs inconnus ignores : le serveur peut evoluer sans casser l'app.

@Serializable
data class SourceDto(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val description: String? = null,
    @SerialName("description_long") val descriptionLong: String? = null,
    val badge: String? = null,
    val type: String? = null,
    @SerialName("link_url") val linkUrl: String? = null,
    val categories: List<String> = emptyList(),
    @SerialName("params_schema") val paramsSchema: List<ParamDto>? = null,
    @SerialName("likes_count") val likesCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("forum_slug") val forumSlug: String? = null,
    val state: String? = null,
    @SerialName("subscriber_count") val subscriberCount: Int = 0,
    @SerialName("last_activated_at") val lastActivatedAt: String? = null,
    @SerialName("topic_count") val topicCount: Int = 0
)

@Serializable
data class ParamDto(
    val key: String,
    val label: String = "",
    val type: String = "string",
    val values: List<ParamValueDto>? = null,
    val multiple: Boolean = false,
    val required: Boolean = false,
    val default: JsonElement? = null,
    val min: Double? = null,
    val max: Double? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val lowercase: Boolean = false,
    val placeholder: String? = null,
    val hint: String? = null
)

@Serializable
data class ParamValueDto(val value: String, val label: String)

@Serializable
data class CategoryDto(val slug: String, val label: String, val group: String)

@Serializable
data class HistoryDto(
    val events: List<HistoryEventDto> = emptyList(),
    val days: List<HistoryDayDto> = emptyList()
)

@Serializable
data class HistoryEventDto(
    val event: String,
    val message: String? = null,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class HistoryDayDto(val date: String, val status: String)

@Serializable
data class LePointDto(
    @SerialName("generated_at") val generatedAt: String? = null,
    val active: List<LePointActiveDto> = emptyList(),
    val upcoming: List<LePointUpcomingDto> = emptyList()
)

@Serializable
data class LePointActiveDto(
    val kind: String,
    val id: String,
    val name: String,
    val message: String? = null,
    val url: String? = null,
    val since: String? = null,
    val count: Int? = null,
    val labels: List<String> = emptyList()
)

@Serializable
data class LePointUpcomingDto(
    val id: String,
    val start: String? = null,
    val message: String? = null,
    val url: String? = null
)

@Serializable
data class GeoDto(
    val countries: List<GeoItemDto> = emptyList(),
    val departements: List<DepartementDto> = emptyList(),
    val regions: List<GeoItemDto> = emptyList()
)

@Serializable
data class GeoItemDto(val code: String, val name: String)

@Serializable
data class DepartementDto(val code: String, val name: String, val region: String? = null)

@Serializable
data class CommunesDto(val departement: String? = null, val communes: List<String> = emptyList())

@Serializable
data class SupportLinksDto(val paypal: String? = null, val kofi: String? = null)

@Serializable
data class ParamLookupDto(val options: List<ParamValueDto> = emptyList())

// Forum en lecture (server/routes/app-api.js, ajout APP-01)
@Serializable
data class ForumHomeDto(val categories: List<ForumCategoryDto> = emptyList(), val recent: List<ForumTopicDto> = emptyList())

@Serializable
data class ForumCategoryDto(val slug: String, val label: String, val count: Int = 0)

@Serializable
data class ForumTopicDto(
    val id: Long,
    val slug: String,
    val title: String,
    val category: String,
    @SerialName("reply_count") val replyCount: Int = 0,
    @SerialName("last_reply_at") val lastReplyAt: String? = null,
    val locked: Boolean = false,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("source_name") val sourceName: String? = null,
    val author: String = "Membre",
    @SerialName("author_pseudo") val authorPseudo: String? = null
)

@Serializable
data class ForumTopicsDto(
    val topics: List<ForumTopicDto> = emptyList(),
    val page: Int = 1,
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
data class ForumPostDto(
    val id: Long,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    val author: String = "Membre",
    @SerialName("author_pseudo") val authorPseudo: String? = null
)

@Serializable
data class ForumThreadDto(
    val topic: ForumTopicDto,
    val posts: List<ForumPostDto> = emptyList(),
    val page: Int = 1,
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
data class ForumMemberDto(val pseudo: String, @SerialName("display_name") val displayName: String? = null)
