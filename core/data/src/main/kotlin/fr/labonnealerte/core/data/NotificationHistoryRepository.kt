package fr.labonnealerte.core.data

import fr.labonnealerte.core.database.NotificationDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Notification recue, telle que gardee dans l'historique local. */
data class NotificationRecord(
    val id: Long,
    val sourceId: String?,
    val sourceName: String?,
    val kind: String,
    val title: String,
    val text: String,
    val receivedAtMillis: Long,
    val read: Boolean,
    val channel: String
)

/** Historique local de toutes les notifications (Room), filtrable, lu/non lu, badge. */
@Singleton
class NotificationHistoryRepository @Inject constructor(private val dao: NotificationDao, private val clock: Clock) {
    val records: Flow<List<NotificationRecord>> = dao.observe().map { list ->
        list.filter { it.kind == "activated" || it.kind == "test" || it.kind == "echeance" }.map {
            NotificationRecord(it.id, it.sourceId, it.sourceName, it.kind, it.title, it.text, it.receivedAtMillis, it.read, it.channel)
        }
    }

    val unreadCount: Flow<Int> = dao.observeUnreadCount()

    /** Pastille de l'onglet Mes alertes : alertes en cours non vues (APP-09). */
    val activeUnseenCount: Flow<Int> = dao.observeActiveUnseenCount()

    suspend fun markRead(id: Long) = dao.markRead(id)

    suspend fun markAllRead() = dao.markAllRead()

    suspend fun markSourceRead(sourceId: String) = dao.markSourceRead(sourceId)

    /** Garde 180 jours d'historique local. */
    suspend fun purgeOld() = dao.purgeOlderThan(clock.now() - 180L * 24 * 3_600_000)

    suspend fun clear() = dao.clear()
}
