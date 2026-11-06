package fr.labonnealerte.core.model

import kotlinx.serialization.Serializable

/** Compte connecte (GET /api/my-alerts), sans le jeton. */
@Serializable
data class Account(
    val email: String,
    val displayName: String?,
    val country: String?,
    /** Fait autorite en France ; la region en decoule cote serveur (server/geo.js). */
    val departement: String?,
    val region: String?,
    val ville: String?,
    val interests: List<String>,
    val emailEnabled: Boolean,
    val pushEndpointsCount: Int,
    val quietStart: Int,
    val quietEnd: Int,
    val quietDisabled: Boolean,
    val hideCommunityReports: Boolean,
    val subscriptions: List<Subscription>,
    val favorites: List<String>
)

/** Abonnement a une source, avec ses instances parametrees eventuelles. */
@Serializable
data class Subscription(
    val sourceId: String,
    val name: String,
    /** Pire etat des instances : active > pending > inactive. */
    val state: String?,
    val subscribed: Boolean,
    val muted: Boolean,
    val instances: List<SubscriptionInstance>,
    val tasks: List<UserTask>
)

@Serializable
data class SubscriptionInstance(
    /** Parametres canoniques (ex. departement=05), valeurs en texte. */
    val params: Map<String, String>,
    val label: String?,
    val state: String?,
    val muted: Boolean
)

@Serializable
data class UserTask(
    val id: Long,
    val label: String,
    val trackingMode: String?,
    val nextDue: String?
)

sealed interface LoginResult {
    data object Success : LoginResult

    /** Compte cree a l'instant (adresse inconnue, code correct, conditions acceptees). */
    data object Created : LoginResult

    /** Code correct pour une adresse sans compte : accepter les conditions [version] pour le creer. */
    data class TermsRequired(val version: String, val url: String) : LoginResult
    data object InvalidCode : LoginResult
    data object TooManyAttempts : LoginResult
    data object Offline : LoginResult
    data object ServerError : LoginResult
}

/** Evenement recent d'une alerte suivie (GET /api/my-alerts/history). */
data class AccountEvent(
    val kind: EventKind,
    val message: String?,
    val createdAt: String,
    val sourceId: String,
    val sourceName: String?
)
