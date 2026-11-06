package fr.labonnealerte.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cache du catalogue (GET /api/sources). Etat serveur brut : active|pending|inactive|null. */
@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val subtitle: String?,
    val description: String?,
    val descriptionLong: String?,
    val badge: String?,
    val type: String?,
    val linkUrl: String?,
    /** Slugs separes par « , » (3 au maximum). */
    val categories: String,
    /** List<ParamDescriptor> en JSON, « [] » si aucune. */
    val paramsJson: String,
    val likesCount: Int,
    val createdAt: String?,
    val forumSlug: String?,
    val state: String?,
    val subscriberCount: Int,
    val lastActivatedAt: String?,
    val topicCount: Int,
    /** Rang dans la reponse serveur (ordre du site). */
    val position: Int
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val slug: String,
    val label: String,
    val group: String,
    val position: Int
)

/** ETag et date de derniere mise a jour par ressource (« sources », « categories »). */
@Entity(tableName = "catalog_meta")
data class CatalogMetaEntity(
    @PrimaryKey val resource: String,
    val etag: String?,
    val fetchedAtMillis: Long
)

/** Favoris locaux (le kiosque se consulte sans compte) ; synchronises une fois connecte. */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val sourceId: String,
    val addedAtMillis: Long,
    /** 0 = synchronise, 1 = a ajouter cote serveur, 2 = a retirer cote serveur. */
    val pending: Int
)

/** Historique local de toutes les notifications recues (consultable, lu/non lu). */
@Entity(
    tableName = "notifications",
    indices = [Index(value = ["dedupKey"], unique = true), Index("sourceId"), Index("receivedAtMillis"), Index("contentKey")]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Cle de deduplication : id d'evenement serveur si connu, sinon empreinte du contenu. */
    val dedupKey: String,
    val serverEventId: Long?,
    val sourceId: String?,
    val sourceName: String?,
    /** activated | deactivated | failed | test | echeance | communaute */
    val kind: String,
    val emoji: String?,
    val title: String,
    val text: String,
    val url: String?,
    val receivedAtMillis: Long,
    val read: Boolean,
    /** Famille / canal de notification (voir core:notifications). */
    val channel: String,
    /** Empreinte du contenu (source, type, message) : evite un doublon push puis releve. Ajoute en v2. */
    val contentKey: String? = null
)
