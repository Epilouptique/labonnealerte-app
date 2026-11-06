package fr.labonnealerte.core.data

import fr.labonnealerte.core.network.AccountApi
import fr.labonnealerte.core.network.dto.CreateReportBody
import fr.labonnealerte.core.network.dto.ReportDto
import fr.labonnealerte.core.network.dto.SpotBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

/** Types connus (server/community-types.js) : liste blanche cote serveur. */
enum class ReportType(val id: String, val emoji: String, val radiusChoices: List<Int>, val defaultRadius: Int) {
    CHAT("chat-perdu", "🐱", listOf(10, 15, 20, 30, 50), 15),
    CHIEN("chien-perdu", "🐶", listOf(10, 15, 20, 30, 50), 15);

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: CHAT
    }
}

data class Report(
    val id: Long,
    val type: ReportType,
    val commune: String?,
    val radiusKm: Int?,
    val description: String?,
    val link: String?,
    val createdAt: String?,
    val expiresAt: String?,
    val extensions: Int,
    val status: String?,
    val spotCount: Int,
    val subscribed: Boolean,
    val spotted: Boolean,
    val isAuthor: Boolean
) {
    val open: Boolean get() = status == null || status == "active"
}

/**
 * Signalements communautaires (animaux perdus). « Autour de moi » est calcule par le serveur
 * a partir de la commune du profil : aucune position GPS n'est demandee (DECISIONS.md D019).
 */
@Singleton
class CommunityRepository @Inject constructor(private val api: AccountApi) {

    suspend fun list(type: ReportType): Result<List<Report>> = call { api.reports(type.id).map { it.toDomain() } }

    suspend fun create(type: ReportType, ville: String, radiusKm: Int, description: String, link: String?): Result<Report?> = call {
        api.createReport(CreateReportBody(type.id, ville.trim(), radiusKm, description.trim(), link?.trim()?.ifBlank { null })).report?.toDomain()
    }

    suspend fun spot(id: Long, where: String, whenText: String): Result<Unit> = call { api.spotReport(id, SpotBody(where.trim(), whenText.trim())) }.map { }

    suspend fun extend(id: Long): Result<Unit> = call { api.extendReport(id) }.map { }

    suspend fun resolve(id: Long): Result<Unit> = call { api.resolveReport(id) }.map { }

    private suspend fun <T> call(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: IOException) {
        Result.failure(AccountError.Offline)
    } catch (e: kotlinx.serialization.SerializationException) {
        Result.failure(AccountError.Rejected(0, null))
    } catch (e: HttpException) {
        val msg = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            ?.let { Regex("\"error\"\\s*:\\s*\"([^\"]{1,200})\"").find(it)?.groupValues?.get(1) }
        Result.failure(AccountError.Rejected(e.code(), msg))
    }
}

private fun ReportDto.toDomain() = Report(
    id, ReportType.of(type), communeNom, radiusKm, description, link, createdAt, expiresAt, extensionsCount, status, spotCount, subscribed, spotted, isAuthor
)
