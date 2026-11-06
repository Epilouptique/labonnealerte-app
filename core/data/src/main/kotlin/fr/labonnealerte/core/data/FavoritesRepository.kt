package fr.labonnealerte.core.data

import fr.labonnealerte.core.database.FavoriteDao
import fr.labonnealerte.core.database.FavoriteEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Favoris (coeur) : locaux d'abord, pour fonctionner sans compte.
 * Une fois connecte, [pendingChanges] est pousse vers le serveur (AccountRepository.syncFavorites).
 */
@Singleton
class FavoritesRepository @Inject constructor(
    private val dao: FavoriteDao,
    private val clock: Clock
) {
    val favorites: Flow<Set<String>> = dao.observe().map { list -> list.map { it.sourceId }.toSet() }

    suspend fun setFavorite(sourceId: String, favorite: Boolean) {
        if (favorite) {
            dao.upsert(FavoriteEntity(sourceId, clock.now(), PENDING_ADD))
        } else {
            dao.upsert(FavoriteEntity(sourceId, clock.now(), PENDING_REMOVE))
        }
    }

    suspend fun pendingChanges(): List<FavoriteEntity> = dao.pending()

    /** Etat serveur fait foi apres synchronisation. */
    suspend fun replaceWithServer(ids: List<String>) = dao.replaceAll(ids, clock.now())

    suspend fun markSynced(sourceId: String, removed: Boolean) {
        if (removed) dao.delete(sourceId) else dao.upsert(FavoriteEntity(sourceId, clock.now(), SYNCED))
    }

    companion object {
        const val SYNCED = 0
        const val PENDING_ADD = 1
        const val PENDING_REMOVE = 2
    }
}
