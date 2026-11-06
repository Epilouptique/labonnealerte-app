package fr.labonnealerte.core.data

import fr.labonnealerte.core.model.AccountEvent
import fr.labonnealerte.core.network.AccountApi
import fr.labonnealerte.core.network.dto.MuteBody
import fr.labonnealerte.core.network.dto.ToggleBody
import fr.labonnealerte.core.network.dto.ToggleParamBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException

/**
 * Abonnements (synchro bidirectionnelle) : chaque action part au serveur, puis le compte est
 * relu (le serveur fait foi : libelles canoniques, communes resolues).
 */
@Singleton
class SubscriptionsRepository @Inject constructor(
    private val api: AccountApi,
    private val account: AccountRepository
) {
    suspend fun setSubscribed(sourceId: String, subscribed: Boolean): Result<Unit> = run {
        api.toggle(ToggleBody(sourceId, subscribed))
    }

    /** Ajoute ou retire une instance parametree. Valeurs deja validees (ParamValidator). */
    suspend fun setInstance(sourceId: String, params: Map<String, Any>, subscribed: Boolean): Result<String?> = runR {
        val body = params.mapValues { (_, v) -> toJson(v) }
        api.toggleParam(ToggleParamBody(sourceId, body, subscribed)).label
    }

    /** Pause : s'applique a toutes les instances de la source (comportement serveur). */
    suspend fun setMuted(sourceId: String, muted: Boolean): Result<Unit> = run { api.toggleMute(MuteBody(sourceId, muted)) }

    suspend fun recentEvents(): Result<List<AccountEvent>> = runCatching {
        api.myHistory().events.map { AccountEvent(eventKind(it.event), it.message, it.createdAt, it.sourceId, it.sourceName) }
    }

    private fun toJson(v: Any): JsonElement = when (v) {
        is Double -> if (v % 1.0 == 0.0) JsonPrimitive(v.toLong()) else JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }

    private suspend fun run(block: suspend () -> Any?): Result<Unit> = runR { block() }.map { }

    private suspend fun <T> runR(block: suspend () -> T): Result<T> = try {
        val r = block()
        account.refresh()
        Result.success(r)
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
