package fr.labonnealerte.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** POST /api/community-sources (server/routes/community-sources.js). Champs nuls omis. */
@Serializable
data class CreatePublicationBody(
    val type: String,
    val url: String,
    val name: String,
    val categories: List<String>,
    val zone: String? = null,
    val description: String? = null,
    @SerialName("description_long") val descriptionLong: String? = null
)

/** PATCH /api/community-sources/:id : brouillon ou source en test seulement. */
@Serializable
data class PatchPublicationBody(
    val name: String? = null,
    val categories: List<String>? = null,
    val url: String? = null,
    val zone: String? = null,
    val description: String? = null,
    @SerialName("description_long") val descriptionLong: String? = null
)

@Serializable
data class PublicationWriteDto(
    val id: String? = null,
    val status: String? = null,
    val provisional: Boolean = false,
    val previewInvalidated: Boolean = false,
    val message: String? = null
)

@Serializable
data class PublicationListDto(val sources: List<PublicationDto> = emptyList())

@Serializable
data class PublicationDto(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    @SerialName("description_long") val descriptionLong: String? = null,
    val status: String? = null,
    val engine: String? = null,
    @SerialName("endpoint_url") val endpointUrl: String? = null,
    val badge: String? = null,
    @SerialName("status_reason") val statusReason: String? = null,
    val categories: List<String> = emptyList(),
    @SerialName("engine_config") val engineConfig: JsonObject? = null
)

@Serializable
data class PreviewDto(
    val ok: Boolean = false,
    val status: String? = null,
    val unstable: Boolean = false,
    val warning: String? = null,
    val error: String? = null,
    val detected: PreviewDetectedDto? = null
)

@Serializable
data class PreviewDetectedDto(val excerpt: String? = null)

@Serializable
data class SourceReportBody(val reason: String, val comment: String? = null)
