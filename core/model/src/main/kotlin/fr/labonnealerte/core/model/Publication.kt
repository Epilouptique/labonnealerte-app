package fr.labonnealerte.core.model

/**
 * Publication communautaire (APP-21), miroir de public/js/publier-flow.js du site : memes regles,
 * memes libelles. Logique pure (testee) ; les appels reseau sont dans PublicationsRepository.
 */
enum class PubType(val id: String, val engine: String, val label: String, val example: String) {
    PAGE("page", "veille-page", "Une page web", "Ex. : la page des tarifs d’un service, une fiche produit."),
    RSS("rss", "veille-rss", "Un flux d’actualités (RSS)", "Ex. : le blog ou le fil d’actualité d’une organisation."),
    AGENDA("agenda", "veille-agenda", "Un agenda (iCal)", "Ex. : l’agenda public d’un club ou d’une salle.");

    companion object {
        fun ofEngine(engine: String?): PubType? = entries.firstOrNull { it.engine == engine }
    }
}

/** Etat du formulaire. [syncedUrl]/[syncedZone] : adresse et zone envoyees au serveur. */
data class PublicationDraft(
    val title: String = "",
    val categories: List<String> = emptyList(),
    val type: PubType? = null,
    val url: String = "",
    val zone: String = "",
    val description: String = "",
    val descriptionLong: String = "",
    val id: String? = null,
    val status: String? = null,
    val previewOk: Boolean = false,
    val previewExtract: String? = null,
    val previewUnstable: Boolean = false,
    val syncedUrl: String? = null,
    val syncedZone: String? = null
) {
    /** L'aperçu reussi porte sur l'adresse et la zone actuelles (sinon il faut le relancer). */
    val previewCurrent: Boolean
        get() = previewOk && id != null && syncedUrl == url.trim() && (syncedZone ?: "") == zone.trim()

    /** Adresse ou zone modifiees depuis le dernier envoi au serveur. */
    val addressChanged: Boolean
        get() = id != null && (syncedUrl != url.trim() || (syncedZone ?: "") != zone.trim())
}

/** Resultat d'une validation : null si tout va bien, sinon le message a afficher. */
object PublicationRules {
    const val TITLE_MIN = 3
    const val TITLE_MAX = 40
    const val DESC_SHORT_MAX = 120
    const val DESC_LONG_MAX = 300
    const val CATEGORIES_MAX = 3
    const val SELECTOR_MAX = 64

    /** Prefixe du nom provisoire du serveur (« Brouillon — hote »). */
    const val PROVISIONAL_PREFIX = "Brouillon —"

    private val HTTPS = Regex("^https://\\S{1,300}$", RegexOption.IGNORE_CASE)
    private val SELECTOR = Regex("^(?:[a-zA-Z][a-zA-Z0-9]{0,20})?(?:[#.][A-Za-z][\\w-]{0,50})?$")

    // Espaces ordinaires ou insecables autour du tiret (le serveur met des insecables).
    private val PROVISIONAL = Regex("^Brouillon[\\s ]*—")

    fun isProvisionalName(name: String?): Boolean = PROVISIONAL.containsMatchIn(name.orEmpty().trim())

    fun isValidSelector(sel: String): Boolean {
        val s = sel.trim()
        return s.isNotEmpty() && s.length <= SELECTOR_MAX && SELECTOR.matches(s) && s.any { it.isLetter() }
    }

    /** Le nom provisoire « Brouillon — hote » n'est jamais accepte comme nom definitif. */
    fun validateTitle(title: String): String? {
        val t = title.trim()
        return when {
            t.length < TITLE_MIN -> "Donnez un titre (au moins 3 caractères)."
            t.length > TITLE_MAX -> "Titre trop long ($TITLE_MAX caractères maximum)."
            isProvisionalName(t) -> "Remplacez le nom provisoire par un vrai titre."
            else -> null
        }
    }

    /** Ecran 1 : titre + categorie(s). */
    fun validateEssentiel(d: PublicationDraft): String? = validateTitle(d.title) ?: when {
        d.categories.isEmpty() -> "Choisissez au moins une catégorie."
        d.categories.size > CATEGORIES_MAX -> "3 catégories au maximum."
        else -> null
    }

    /** Ecran 2 : type, adresse, zone, descriptions. */
    fun validateDetails(d: PublicationDraft): String? {
        val zone = d.zone.trim()
        return when {
            d.type == null -> "Choisissez ce que vous voulez surveiller."
            !HTTPS.matches(d.url.trim()) -> "Entrez une adresse https valide (max 300 caractères)."
            zone.isNotEmpty() && d.type != PubType.PAGE -> "La zone ne s’applique qu’à une page web."
            zone.isNotEmpty() && !isValidSelector(zone) -> "Zone invalide : une balise, #identifiant ou .classe simple."
            d.description.trim().length > DESC_SHORT_MAX -> "Description courte trop longue ($DESC_SHORT_MAX caractères max)."
            d.descriptionLong.trim().length > DESC_LONG_MAX -> "Description longue trop longue ($DESC_LONG_MAX caractères max)."
            else -> null
        }
    }

    /** Bouton « Soumettre » : tout est valide ET un aperçu reussi porte sur l'adresse actuelle. */
    fun submitBlocker(d: PublicationDraft): String? =
        validateEssentiel(d) ?: validateDetails(d) ?: if (!d.previewCurrent) "Lancez d’abord un aperçu réussi sur cette adresse." else null

    /** Compteur « 12 / 120 ». */
    fun counter(text: String, max: Int): String = "${text.trim().length} / $max"

    /** Statuts du serveur en francais clair (comme statusLabel du site). */
    fun statusLabel(status: String?, reason: String? = null): String = when (status) {
        "draft" -> "Brouillon"
        "testing" -> "À tester"
        "review" -> "En revue"
        "published" -> "Publiée"
        "suspended" -> if (!reason.isNullOrBlank()) "Suspendue — $reason" else "Suspendue"
        "rejected" -> if (!reason.isNullOrBlank()) "Refusée — $reason" else "Refusée"
        else -> status ?: "—"
    }

    /** Brouillons modifiables et supprimables (comme le serveur). */
    fun isEditable(status: String?): Boolean = status == "draft" || status == "testing"

    /** Motifs de signalement (liste fermee du serveur, libelles du site). */
    val REPORT_REASONS: List<Pair<String, String>> = listOf(
        "contenu-errone" to "Contenu erroné",
        "spam-bruit" to "Spam / trop de bruit",
        "doublon" to "Doublon d’une autre source",
        "inapproprie" to "Contenu inapproprié",
        "droits-donnees" to "Droits / données personnelles"
    )

    /**
     * Message d'erreur d'une creation ou modification (comme mapCreateError du site) : le message
     * du serveur tel quel quand il existe.
     */
    fun errorMessage(httpCode: Int, serverMessage: String?, duplicate: Boolean = false): String = when {
        httpCode == 0 -> "Pas de connexion internet. Réessayez quand vous serez connecté."
        httpCode == 409 && duplicate -> "Une alerte surveille déjà cette adresse."
        httpCode == 401 -> "Connexion requise pour publier."
        httpCode == 404 && serverMessage == "Ressource introuvable" -> "La publication est fermée pour le moment."
        !serverMessage.isNullOrBlank() -> serverMessage
        httpCode == 409 -> "Limite de publications atteinte pour votre compte."
        httpCode == 400 -> "Adresse ou champ refusé."
        else -> "Une erreur est survenue, réessayez."
    }
}
