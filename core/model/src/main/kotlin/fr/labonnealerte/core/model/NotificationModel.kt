package fr.labonnealerte.core.model

/** Familles de notifications = canaux Android (reglables un par un par l'utilisateur). */
enum class NotificationFamily(val channelId: String) {
    URGENCES("urgences"),
    METEO_ENVIRONNEMENT("meteo_environnement"),
    VIE_LOCALE("vie_locale"),
    SERVICES("services_pannes"),

    // « _2 » : canal recree avec son par defaut (APP-04). Un canal Android ne change plus
    // d'importance apres creation ; l'ancien identifiant est supprime (Channels.LEGACY_IDS).
    BONS_PLANS("bons_plans_2"),
    COMMUNAUTE("communaute"),
    ECHEANCES("echeances"),
    SYSTEME("systeme")
}

enum class Severity { URGENT, HIGH, NORMAL, LOW }

/** Evenement entrant (push, releve periodique, simulation) avant deduplication et affichage. */
data class IncomingEvent(
    /** Id serveur (GET /api/app/events) ; null pour un push sans id. */
    val serverId: Long?,
    val sourceId: String?,
    val sourceName: String?,
    val kind: EventKind,
    val message: String?,
    val label: String?,
    val categories: List<String>,
    val url: String?,
    val createdAtMillis: Long,
    val origin: Origin
) {
    /**
     * SIMULATION : alerte de test realiste, creee localement (Reglages > Aide, APP-09). Elle suit
     * le chemin d'une vraie alerte (famille, canal, pastille) mais reste marquee « test » dans
     * l'historique et ne touche jamais le serveur.
     */
    enum class Origin { PUSH, POLL, TEST, ECHEANCE, SIMULATION }
}

/**
 * Classement d'un evenement. Le serveur n'a ni gravite ni famille (DECISIONS.md D008) :
 * on deduit la famille des categories (groupes de server/categories.js) et la gravite
 * des mots du message (« vigilance rouge », « alerte attentat »…). HYPOTHESE documentee.
 */
object EventClassifier {
    private val urgentWords = listOf(
        "vigilance rouge", "rouge :", "alerte rouge", "evacuation", "evacuez", "alerte attentat", "alerte enlevement",
        "danger immediat", "confinement", "tsunami", "crue majeure", "alerte nucleaire"
    )
    private val highWords = listOf("vigilance orange", "orange :", "alerte", "panne majeure", "coupure", "rupture")

    private val groupFamily = mapOf(
        "meteo-risques" to NotificationFamily.METEO_ENVIRONNEMENT,
        "environnement-nature" to NotificationFamily.METEO_ENVIRONNEMENT,
        "agriculture-jardin" to NotificationFamily.METEO_ENVIRONNEMENT,
        "espace-science" to NotificationFamily.METEO_ENVIRONNEMENT,
        "vie-locale" to NotificationFamily.VIE_LOCALE,
        "solidarite" to NotificationFamily.VIE_LOCALE,
        "administration" to NotificationFamily.VIE_LOCALE,
        "sante" to NotificationFamily.VIE_LOCALE,
        "animaux" to NotificationFamily.COMMUNAUTE,
        "energie" to NotificationFamily.SERVICES,
        "transports" to NotificationFamily.SERVICES,
        "tech-dev" to NotificationFamily.SERVICES,
        "securite-cyber" to NotificationFamily.SERVICES,
        "auto-moto" to NotificationFamily.SERVICES,
        "immobilier" to NotificationFamily.VIE_LOCALE,
        "emploi-formation" to NotificationFamily.VIE_LOCALE,
        "pro-marches" to NotificationFamily.SERVICES,
        "finance" to NotificationFamily.BONS_PLANS
    )

    fun severity(message: String?): Severity {
        val m = SearchNormalizer.normalize(message).let { " $it " }
        val raw = message.orEmpty().lowercase()
        return when {
            urgentWords.any { m.contains(SearchNormalizer.normalize(it)) } || raw.contains("🔴") -> Severity.URGENT
            highWords.any { m.contains(" " + SearchNormalizer.normalize(it)) } || raw.contains("🟠") || raw.contains("⚠") -> Severity.HIGH
            else -> Severity.NORMAL
        }
    }

    /** [groupOf] : categorie → groupe (catalogue local). */
    fun family(categories: List<String>, sourceType: SourceType?, message: String?, groupOf: (String) -> String?): NotificationFamily {
        if (severity(message) == Severity.URGENT) return NotificationFamily.URGENCES
        if (sourceType == SourceType.COMMUNITY) return NotificationFamily.COMMUNAUTE
        if (sourceType == SourceType.USER_TASK) return NotificationFamily.ECHEANCES
        val families = categories.mapNotNull { groupOf(it) }.mapNotNull { groupFamily[it] }
        return families.firstOrNull() ?: NotificationFamily.BONS_PLANS
    }
}

/** Heures calmes (23 h → 8 h par defaut, comme server/quiet-hours.js), plage pouvant passer minuit. */
data class QuietHours(val start: Int = 23, val end: Int = 8, val enabled: Boolean = true) {
    fun isQuiet(hour: Int): Boolean {
        if (!enabled || start == end) return false
        return if (start < end) hour in start until end else hour >= start || hour < end
    }
}

/** Anti-bruit : au plus [maxPerHour] notifications sonores par heure (urgences exceptees). */
object NoiseGuard {
    fun allowSound(sentLastHour: Int, maxPerHour: Int, severity: Severity): Boolean =
        severity == Severity.URGENT || sentLastHour < maxPerHour
}
