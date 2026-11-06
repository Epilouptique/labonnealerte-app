package fr.labonnealerte.core.model

import kotlinx.serialization.Serializable

/**
 * Une source d'alertes du kiosque (element de GET /api/sources).
 * Voir docs/contrat-api-app.md §4.1.
 */
data class Source(
    val id: String,
    val name: String,
    val subtitle: String?,
    val description: String?,
    val descriptionLong: String?,
    val badge: Badge,
    val type: SourceType,
    val linkUrl: String?,
    val categories: List<String>,
    val params: List<ParamDescriptor>,
    val likesCount: Int,
    val createdAt: String?,
    val forumSlug: String?,
    val state: SourceState,
    val subscriberCount: Int,
    val lastActivatedAt: String?,
    val topicCount: Int
) {
    /** Une source sans parametre envoie la meme alerte a tous ses abonnes. */
    val isParameterized: Boolean get() = params.isNotEmpty()
}

enum class Badge { OFFICIAL, VERIFIED, COMMUNITY, UNKNOWN }

enum class SourceType { INTERNAL, EXTERNAL, LINKED, USER_TASK, COMMUNITY, UNKNOWN }

/**
 * Etat affiche. Le serveur ne stocke que active|pending|inactive ;
 * INCIDENT est derive (dernier evenement "failed" de moins de 90 min), NONE pour les sources "linked".
 */
enum class SourceState { CALM, ACTIVE, INCIDENT, NONE }

@Serializable
data class ParamDescriptor(
    val key: String,
    val label: String,
    val type: ParamType,
    val options: List<ParamOption>,
    val multiple: Boolean,
    val required: Boolean,
    val default: String?,
    val min: Double?,
    val max: Double?,
    val maxLength: Int?,
    val pattern: String? = null,
    val lowercase: Boolean = false,
    val placeholder: String? = null,
    val hint: String? = null
)

@Serializable
enum class ParamType { ENUM, STRING, NUMBER, COMMUNE, COMMUNE_COORDS, DYNAMIC_ENUM, UNKNOWN }

@Serializable
data class ParamOption(val value: String, val label: String)

data class Category(val slug: String, val label: String, val group: String)
