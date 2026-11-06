package fr.labonnealerte.feature.alerte

/** Routes et liens de l'ecran detail. */
object AlerteRoutes {
    const val ROUTE = "alerte/{id}"
    const val SITE = "https://labonnealerte.fr"

    fun route(id: String) = "alerte/$id"

    /** Liens profonds acceptes : labonnealerte://alert/<id> et https://labonnealerte.fr/source/<id>/statut */
    const val DEEP_LINK_APP = "labonnealerte://alert/{id}"
    const val DEEP_LINK_WEB = "$SITE/source/{id}/statut"

    private val validId = Regex("^[a-z0-9][a-z0-9-]{0,63}$")

    /** Un identifiant venant d'un lien externe est valide strictement (64 caracteres, [a-z0-9-]). */
    fun sanitizeId(raw: String?): String? = raw?.trim()?.lowercase()?.takeIf { validId.matches(it) }

    fun statusUrl(id: String) = "$SITE/source/$id/statut"

    fun forumUrl(forumSlug: String) = "$SITE/forum/source/$forumSlug"
}
