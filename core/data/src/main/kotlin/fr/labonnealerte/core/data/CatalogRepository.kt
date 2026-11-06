package fr.labonnealerte.core.data

import fr.labonnealerte.core.database.CatalogDao
import fr.labonnealerte.core.database.CatalogMetaEntity
import fr.labonnealerte.core.model.Category
import fr.labonnealerte.core.model.Geo
import fr.labonnealerte.core.model.LePoint
import fr.labonnealerte.core.model.ParamOption
import fr.labonnealerte.core.model.Source
import fr.labonnealerte.core.model.SourceHistory
import fr.labonnealerte.core.network.LbaApi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

/** Resultat d'un rafraichissement : l'UI garde toujours le cache et affiche un bandeau si besoin. */
sealed interface RefreshResult {
    data object Updated : RefreshResult
    data object NotModified : RefreshResult
    data object Fresh : RefreshResult
    data object Offline : RefreshResult
    data class Failed(val httpCode: Int?) : RefreshResult
}

/** Horloge injectable (tests). */
fun interface Clock {
    fun now(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}

/**
 * Catalogue hors-ligne d'abord : l'UI observe Room ; [refreshSources] revalide avec l'ETag
 * (304 = rien a telecharger). Aligne sur le Cache-Control serveur (60 s sources, 1 h categories).
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val api: LbaApi,
    private val dao: CatalogDao,
    private val clock: Clock
) {
    private val mutex = Mutex()
    private var geoCache: Geo? = null

    val sources: Flow<List<Source>> = dao.observeSources().map { list -> list.map { it.toDomain() } }

    val categories: Flow<List<Category>> = dao.observeCategories().map { list -> list.map { it.toDomain() } }

    fun source(id: String): Flow<Source?> = dao.observeSource(id).map { it?.toDomain() }

    suspend fun refreshSources(force: Boolean = false): RefreshResult = refresh(RES_SOURCES, SOURCES_TTL_MS, force) { etag ->
        val res = api.sources(etag)
        handle(res.code(), res.headers()["ETag"], res.body()) { body, newEtag ->
            dao.replaceSources(body.mapIndexed { i, s -> s.toEntity(i) }, CatalogMetaEntity(RES_SOURCES, newEtag, clock.now()))
        }
    }

    suspend fun refreshCategories(force: Boolean = false): RefreshResult = refresh(RES_CATEGORIES, CATEGORIES_TTL_MS, force) { etag ->
        val res = api.categories(etag)
        handle(res.code(), res.headers()["ETag"], res.body()) { body, newEtag ->
            dao.replaceCategories(body.mapIndexed { i, c -> c.toEntity(i) }, CatalogMetaEntity(RES_CATEGORIES, newEtag, clock.now()))
        }
    }

    private suspend fun refresh(resource: String, ttl: Long, force: Boolean, call: suspend (String?) -> RefreshResult): RefreshResult =
        mutex.withLock {
            val meta = dao.meta(resource)
            if (!force && meta != null && clock.now() - meta.fetchedAtMillis < ttl) return RefreshResult.Fresh
            try {
                val result = call(meta?.etag)
                if (result == RefreshResult.NotModified && meta != null) dao.upsertMeta(meta.copy(fetchedAtMillis = clock.now()))
                result
            } catch (e: IOException) {
                RefreshResult.Offline
            } catch (e: HttpException) {
                RefreshResult.Failed(e.code())
            } catch (e: kotlinx.serialization.SerializationException) {
                RefreshResult.Failed(null)
            }
        }

    private suspend fun <T> handle(code: Int, etag: String?, body: T?, store: suspend (T, String?) -> Unit): RefreshResult = when {
        code == 304 -> RefreshResult.NotModified
        code in 200..299 && body != null -> {
            store(body, etag)
            RefreshResult.Updated
        }
        else -> RefreshResult.Failed(code)
    }

    /** Historique 90 jours d'une source (ou d'une combinaison de parametres). Reseau uniquement. */
    suspend fun history(sourceId: String, params: Map<String, String> = emptyMap()): Result<SourceHistory> = runCatching {
        api.history(sourceId, params).toDomain()
    }

    suspend fun lePoint(): Result<LePoint> = runCatching { api.lePoint().toDomain() }

    suspend fun geo(): Result<Geo> {
        geoCache?.let { return Result.success(it) }
        return runCatching { api.geo().toDomain() }.onSuccess { geoCache = it }
    }

    suspend fun communes(departement: String): Result<List<String>> = runCatching { api.communes(departement).communes }

    suspend fun paramLookup(sourceId: String, q: String, dept: String?): Result<List<ParamOption>> = runCatching {
        api.paramLookup(sourceId, q, dept).options.map { ParamOption(it.value, it.label) }
    }

    suspend fun clearCache() = dao.clearMeta()

    companion object {
        const val RES_SOURCES = "sources"
        const val RES_CATEGORIES = "categories"
        const val SOURCES_TTL_MS = 60_000L
        const val CATEGORIES_TTL_MS = 3_600_000L
    }
}
