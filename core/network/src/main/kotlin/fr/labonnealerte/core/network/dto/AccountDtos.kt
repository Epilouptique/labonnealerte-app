package fr.labonnealerte.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Compte, abonnements, profil (docs/contrat-api-app.md §3.2 a §3.9).

@Serializable
data class EmailBody(val email: String)

@Serializable
data class MessageDto(val message: String? = null, val error: String? = null)

@Serializable
data class OkDto(val ok: Boolean = false)

/** Corps vide « {} » : requis pour les DELETE authentifies (voir contrat §3.8). */
@Serializable
class EmptyBody

@Serializable
data class MyAlertsDto(
    val email: String? = null,
    val token: String? = null,
    val sources: List<MySourceDto> = emptyList(),
    val favorites: List<String> = emptyList(),
    @SerialName("email_enabled") val emailEnabled: Boolean = true,
    @SerialName("push_endpoints_count") val pushEndpointsCount: Int = 0,
    val country: String? = null,
    val departement: String? = null,
    val region: String? = null,
    val ville: String? = null,
    val interests: List<String> = emptyList(),
    @SerialName("display_name") val displayName: String? = null,
    val pseudo: String? = null,
    @SerialName("quiet_start") val quietStart: Int = 23,
    @SerialName("quiet_end") val quietEnd: Int = 8,
    @SerialName("quiet_disabled") val quietDisabled: Boolean = false,
    @SerialName("hide_community_reports") val hideCommunityReports: Boolean = false
)

@Serializable
data class MySourceDto(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("description_long") val descriptionLong: String? = null,
    val badge: String? = null,
    val state: String? = null,
    val subscribed: Boolean = false,
    val muted: Boolean? = null,
    @SerialName("params_schema") val paramsSchema: List<ParamDto>? = null,
    val instances: List<InstanceDto> = emptyList(),
    val tasks: List<TaskDto> = emptyList()
)

@Serializable
data class InstanceDto(
    val params: JsonObject? = null,
    val label: String? = null,
    val state: String? = null,
    val muted: Boolean? = null
)

@Serializable
data class TaskDto(
    val id: Long,
    val label: String,
    @SerialName("tracking_mode") val trackingMode: String? = null,
    @SerialName("next_due") val nextDue: String? = null,
    @SerialName("counter_unit") val counterUnit: String? = null,
    @SerialName("counter_current") val counterCurrent: Double? = null,
    @SerialName("counter_threshold") val counterThreshold: Double? = null
)

@Serializable
data class MyHistoryDto(val events: List<MyHistoryEventDto> = emptyList())

@Serializable
data class MyHistoryEventDto(
    val event: String,
    val message: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_name") val sourceName: String? = null
)

@Serializable
data class ToggleBody(@SerialName("source_id") val sourceId: String, val subscribed: Boolean)

@Serializable
data class ToggleParamBody(
    @SerialName("source_id") val sourceId: String,
    val params: Map<String, JsonElement>,
    val subscribed: Boolean
)

@Serializable
data class ToggleParamDto(
    @SerialName("source_id") val sourceId: String,
    val subscribed: Boolean,
    val params: JsonObject? = null,
    val label: String? = null
)

@Serializable
data class MuteBody(@SerialName("source_id") val sourceId: String, val muted: Boolean)

@Serializable
data class ProfileBody(
    val country: String?,
    val departement: String?,
    val region: String?,
    val ville: String?,
    val interests: List<String>
)

@Serializable
data class ProfileDto(
    val country: String? = null,
    val departement: String? = null,
    val region: String? = null,
    val ville: String? = null,
    val interests: List<String> = emptyList()
)

@Serializable
data class DisplayNameBody(@SerialName("display_name") val displayName: String)

@Serializable
data class DisplayNameDto(@SerialName("display_name") val displayName: String? = null)

@Serializable
data class EmailPrefBody(@SerialName("email_enabled") val emailEnabled: Boolean)

@Serializable
data class QuietHoursBody(val disabled: Boolean, val start: Int, val end: Int)

@Serializable
data class QuietHoursDto(
    @SerialName("quiet_start") val quietStart: Int = 23,
    @SerialName("quiet_end") val quietEnd: Int = 8,
    @SerialName("quiet_disabled") val quietDisabled: Boolean = false
)

@Serializable
data class FavoriteBody(@SerialName("source_id") val sourceId: String)

@Serializable
data class FavoriteSyncBody(val ids: List<String>)

@Serializable
data class LikeDto(val id: String, @SerialName("likes_count") val likesCount: Int = 0)

@Serializable
data class PushSubscriptionBody(val subscription: PushSubscriptionDto)

@Serializable
data class PushSubscriptionDto(val endpoint: String, val keys: PushKeysDto)

@Serializable
data class PushKeysDto(val p256dh: String, val auth: String)

@Serializable
data class PushEndpointBody(val endpoint: String)

@Serializable
data class VapidKeyDto(val key: String? = null, val enabled: Boolean = false)

@Serializable
data class CreateTaskBody(
    val label: String,
    @SerialName("anchor_date") val anchorDate: String,
    @SerialName("periodicity_value") val periodicityValue: Int,
    @SerialName("periodicity_unit") val periodicityUnit: String,
    @SerialName("announce_days") val announceDays: Int? = null
)

@Serializable
data class CreateTaskDto(val task: TaskDto? = null)

@Serializable
data class ConfirmTaskDto(val id: Long, @SerialName("next_due") val nextDue: String? = null, val message: String? = null)

@Serializable
data class ReportDto(
    val id: Long,
    val type: String,
    @SerialName("commune_nom") val communeNom: String? = null,
    @SerialName("commune_insee") val communeInsee: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("radius_km") val radiusKm: Int? = null,
    val description: String? = null,
    val link: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("extensions_count") val extensionsCount: Int = 0,
    val status: String? = null,
    @SerialName("spot_count") val spotCount: Int = 0,
    val subscribed: Boolean = false,
    val spotted: Boolean = false,
    @SerialName("is_author") val isAuthor: Boolean = false
)

@Serializable
data class CreateReportBody(
    val type: String,
    val ville: String,
    @SerialName("radius_km") val radiusKm: Int?,
    val description: String?,
    val link: String?
)

@Serializable
data class CreateReportDto(val joined: Boolean = false, val created: Boolean = false, val report: ReportDto? = null)

@Serializable
data class SpotBody(val where: String, val `when`: String)

@Serializable
data class ForumTopicBody(
    val category: String,
    val title: String,
    val body: String,
    @SerialName("source_id") val sourceId: String? = null
)

@Serializable
data class ForumTopicCreatedDto(val slug: String? = null, val url: String? = null)

@Serializable
data class ForumReplyBody(val body: String)

@Serializable
data class ForumReportBody(val reason: String?)

/** Ajouts serveur pour l'app (server/routes/app-api.js) : voir docs/contrat-api-app.md §10. */
@Serializable
data class AppConfigDto(
    @SerialName("min_version_code") val minVersionCode: Int = 0,
    @SerialName("latest_version_code") val latestVersionCode: Int = 0,
    val maintenance: Boolean = false,
    val message: String? = null,
    @SerialName("vapid_public_key") val vapidPublicKey: String? = null,
    @SerialName("terms_version") val termsVersion: String? = null,
    @SerialName("terms_url") val termsUrl: String? = null
)

@Serializable
data class AppEventsDto(
    val events: List<AppEventDto> = emptyList(),
    @SerialName("last_id") val lastId: Long = 0,
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
data class AppEventDto(
    val id: Long,
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_name") val sourceName: String? = null,
    val event: String,
    val message: String? = null,
    val label: String? = null,
    val categories: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String,
    val url: String? = null
)

@Serializable
data class VerifyCodeBody(
    val email: String,
    val code: String,
    /** Creation de compte (APP-04) : true seulement apres la case cochee ; absent sinon. */
    @SerialName("accept_terms") val acceptTerms: Boolean? = null,
    @SerialName("terms_version") val termsVersion: String? = null
)

@Serializable
data class AppLoginDto(
    val token: String? = null,
    val email: String? = null,
    val error: String? = null,
    /** true si le compte vient d'etre cree (APP-04). */
    val created: Boolean = false,
    /** Reponse 409 : adresse sans compte, conditions a accepter. */
    @SerialName("needs_terms") val needsTerms: Boolean = false,
    @SerialName("terms_version") val termsVersion: String? = null,
    @SerialName("terms_url") val termsUrl: String? = null
)
