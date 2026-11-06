package fr.labonnealerte.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT * FROM sources ORDER BY position")
    fun observeSources(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE id = :id")
    fun observeSource(id: String): Flow<SourceEntity?>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun source(id: String): SourceEntity?

    @Query("SELECT * FROM categories ORDER BY position")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM catalog_meta WHERE resource = :resource")
    suspend fun meta(resource: String): CatalogMetaEntity?

    @Upsert
    suspend fun upsertMeta(meta: CatalogMetaEntity)

    @Query("DELETE FROM sources")
    suspend fun clearSources()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(items: List<SourceEntity>)

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<CategoryEntity>)

    /** Remplacement atomique : une source retiree du serveur disparait du cache. */
    @Transaction
    suspend fun replaceSources(items: List<SourceEntity>, meta: CatalogMetaEntity) {
        clearSources()
        insertSources(items)
        upsertMeta(meta)
    }

    @Transaction
    suspend fun replaceCategories(items: List<CategoryEntity>, meta: CatalogMetaEntity) {
        clearCategories()
        insertCategories(items)
        upsertMeta(meta)
    }

    @Query("DELETE FROM catalog_meta")
    suspend fun clearMeta()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE pending != 2 ORDER BY addedAtMillis DESC")
    fun observe(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE pending != 0")
    suspend fun pending(): List<FavoriteEntity>

    @Upsert
    suspend fun upsert(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE sourceId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM favorites")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(ids: List<String>, now: Long) {
        clear()
        ids.forEach { upsert(FavoriteEntity(it, now, 0)) }
    }
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY receivedAtMillis DESC LIMIT :limit")
    fun observe(limit: Int = 500): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE read = 0")
    fun observeUnreadCount(): Flow<Int>

    /**
     * Pastille de l'onglet Mes alertes (APP-09) : alertes en cours non vues, une par source. Une
     * alerte suivie d'un retour au calme n'est plus « en cours ». Echeances et notifications de
     * test sans source exclues (elles ont leur propre ecran).
     */
    @Query(
        "SELECT COUNT(DISTINCT n.sourceId) FROM notifications n WHERE n.read = 0 AND n.sourceId IS NOT NULL " +
            "AND n.kind IN ('activated', 'test') AND NOT EXISTS (SELECT 1 FROM notifications d WHERE d.sourceId = n.sourceId " +
            "AND d.kind = 'deactivated' AND (d.receivedAtMillis > n.receivedAtMillis OR (d.receivedAtMillis = n.receivedAtMillis AND d.id > n.id)))"
    )
    fun observeActiveUnseenCount(): Flow<Int>

    /** Alertes non lues (pour le tiroir de notifications et la pastille). */
    @Query("SELECT * FROM notifications WHERE read = 0 AND kind IN ('activated', 'test', 'echeance')")
    fun observeUnreadAlerts(): Flow<List<NotificationEntity>>

    /** Derniere alerte non lue d'une source (null si tout a ete vu). */
    @Query(
        "SELECT * FROM notifications WHERE read = 0 AND sourceId = :sourceId AND kind IN ('activated', 'test', 'echeance') " +
            "ORDER BY receivedAtMillis DESC, id DESC LIMIT 1"
    )
    suspend fun latestUnreadForSource(sourceId: String): NotificationEntity?

    /** Dernier nom connu d'une source (repli quand elle manque au catalogue charge). */
    @Query(
        "SELECT sourceName FROM notifications WHERE sourceId = :sourceId AND sourceName IS NOT NULL AND sourceName != '' " +
            "ORDER BY receivedAtMillis DESC LIMIT 1"
    )
    suspend fun lastSourceName(sourceId: String): String?

    @Query("SELECT COUNT(*) FROM notifications WHERE read = 0 AND sourceId = :sourceId AND kind IN ('activated', 'test', 'echeance')")
    suspend fun countUnreadForSource(sourceId: String): Int

    /** Renvoie -1 si la cle de deduplication existe deja (jamais deux notifications identiques). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(n: NotificationEntity): Long

    @Query("SELECT COUNT(*) FROM notifications WHERE receivedAtMillis >= :since AND kind = 'activated'")
    suspend fun countSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM notifications WHERE contentKey = :contentKey AND receivedAtMillis >= :since")
    suspend fun countByKey(contentKey: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM notifications WHERE contentKey = :contentKey AND serverEventId IS NULL AND receivedAtMillis >= :since")
    suspend fun countUnattached(contentKey: String, since: Long): Int

    @Query("UPDATE notifications SET serverEventId = :id WHERE contentKey = :contentKey AND serverEventId IS NULL")
    suspend fun attachServerId(contentKey: String, id: Long)

    @Query("SELECT * FROM notifications ORDER BY receivedAtMillis DESC LIMIT 1")
    suspend fun latest(): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE id = :id")
    suspend fun byId(id: Long): NotificationEntity?

    @Query("UPDATE notifications SET read = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE notifications SET read = 1 WHERE sourceId = :sourceId")
    suspend fun markSourceRead(sourceId: String)

    @Query("UPDATE notifications SET read = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM notifications WHERE receivedAtMillis < :before")
    suspend fun purgeOlderThan(before: Long)

    @Query("DELETE FROM notifications")
    suspend fun clear()
}
