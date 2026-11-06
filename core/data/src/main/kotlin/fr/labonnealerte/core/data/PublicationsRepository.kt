package fr.labonnealerte.core.data

import fr.labonnealerte.core.model.PubType
import fr.labonnealerte.core.model.PublicationDraft
import fr.labonnealerte.core.model.PublicationRules
import fr.labonnealerte.core.network.PublicationsApi
import fr.labonnealerte.core.network.dto.CreatePublicationBody
import fr.labonnealerte.core.network.dto.PatchPublicationBody
import fr.labonnealerte.core.network.dto.PublicationDto
import fr.labonnealerte.core.network.dto.SourceReportBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException

/** Une source de l'auteur, telle que listee par « Mes publications ». */
data class Publication(
    val id: String,
    val name: String,
    val status: String?,
    val statusReason: String?,
    val type: PubType?,
    val url: String?,
    val zone: String?,
    val categories: List<String>,
    val description: String?,
    val descriptionLong: String?
) {
    val statusLabel: String get() = PublicationRules.statusLabel(status, statusReason)
    val editable: Boolean get() = PublicationRules.isEditable(status)

    /** Formulaire pre-rempli pour modifier ce brouillon. */
    fun toDraft(): PublicationDraft = PublicationDraft(
        title = name.takeUnless { PublicationRules.isProvisionalName(it) }.orEmpty(),
        categories = categories,
        type = type,
        url = url.orEmpty(),
        zone = zone.orEmpty(),
        description = description.orEmpty(),
        descriptionLong = descriptionLong.orEmpty(),
        id = id,
        status = status,
        syncedUrl = url.orEmpty(),
        syncedZone = zone.orEmpty()
    )
}

/** Erreur lisible : le message du serveur tel quel quand il existe (en francais). */
class PublicationError(val httpCode: Int, override val message: String) : Exception(message)

data class PreviewResult(val ok: Boolean, val status: String?, val excerpt: String?, val unstable: Boolean, val message: String?)

/**
 * Parcours de publication communautaire (APP-21) : memes routes que le site, avec le jeton de
 * session de l'app (Bearer, ajoute par AuthInterceptor).
 */
@Singleton
class PublicationsRepository @Inject constructor(private val api: PublicationsApi) {
    suspend fun list(): Result<List<Publication>> = call { api.publications().sources.map { it.toDomain() } }

    /** Cree le brouillon (nouveau) ou envoie les modifications (existant) ; renvoie l'id et le statut. */
    suspend fun save(d: PublicationDraft): Result<Pair<String, String?>> = call {
        val type = requireNotNull(d.type)
        val zone = d.zone.trim().takeIf { type == PubType.PAGE }
        if (d.id == null) {
            val r = api.createPublication(
                CreatePublicationBody(
                    type = type.id,
                    url = d.url.trim(),
                    name = d.title.trim(),
                    categories = d.categories.take(PublicationRules.CATEGORIES_MAX),
                    zone = zone?.takeIf { it.isNotEmpty() },
                    description = d.description.trim().takeIf { it.isNotEmpty() },
                    descriptionLong = d.descriptionLong.trim().takeIf { it.isNotEmpty() }
                )
            )
            requireNotNull(r.id) to (r.status ?: "draft")
        } else {
            val r = api.patchPublication(
                d.id!!,
                PatchPublicationBody(
                    name = d.title.trim(),
                    categories = d.categories.take(PublicationRules.CATEGORIES_MAX),
                    url = d.url.trim(),
                    zone = zone,
                    description = d.description.trim(),
                    descriptionLong = d.descriptionLong.trim()
                )
            )
            d.id!! to (r.status ?: d.status)
        }
    }

    suspend fun preview(id: String): Result<PreviewResult> = call {
        val r = api.previewPublication(id)
        PreviewResult(r.ok, r.status, r.detected?.excerpt, r.unstable, if (r.ok) r.warning else r.error)
    }

    /** Renvoie le message du serveur (« Envoyée en revue… »). */
    suspend fun submit(id: String): Result<String?> = call { api.submitPublication(id).message }

    suspend fun delete(id: String): Result<String?> = call { api.deletePublication(id).message }

    suspend fun report(sourceId: String, reason: String, comment: String?): Result<String?> =
        call { api.reportSource(sourceId, SourceReportBody(reason, comment?.trim()?.takeIf { it.isNotEmpty() })).message }

    private suspend fun <T> call(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: IOException) {
        Result.failure(PublicationError(0, PublicationRules.errorMessage(0, null)))
    } catch (e: kotlinx.serialization.SerializationException) {
        Result.failure(PublicationError(-1, PublicationRules.errorMessage(-1, null)))
    } catch (e: HttpException) {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        val msg = Regex("\"error\"\\s*:\\s*\"([^\"]{1,300})\"").find(body)?.groupValues?.get(1)
        val duplicate = body.contains("\"existing\"")
        Result.failure(PublicationError(e.code(), PublicationRules.errorMessage(e.code(), msg, duplicate)))
    }
}

private fun PublicationDto.toDomain() = Publication(
    id = id,
    name = name ?: id,
    status = status,
    statusReason = statusReason,
    type = PubType.ofEngine(engine),
    url = endpointUrl,
    zone = (engineConfig?.get("zone") as? JsonPrimitive)?.takeIf { it.isString }?.content,
    categories = categories,
    description = description,
    descriptionLong = descriptionLong
)
