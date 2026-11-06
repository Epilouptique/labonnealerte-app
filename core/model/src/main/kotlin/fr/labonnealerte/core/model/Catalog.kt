package fr.labonnealerte.core.model

/** Historique public d'une source (GET /api/sources/:id/history). */
data class SourceHistory(
    val events: List<HistoryEvent>,
    val days: List<HistoryDay>
)

data class HistoryEvent(
    val kind: EventKind,
    val message: String?,
    val createdAt: String
)

enum class EventKind { ACTIVATED, DEACTIVATED, FAILED, UNKNOWN }

data class HistoryDay(val date: String, val status: DayStatus)

enum class DayStatus { CALM, ACTIVE, FAILED, NODATA }

/** « En ce moment » (GET /api/le-point). */
data class LePoint(
    val generatedAt: String?,
    val active: List<LePointActive>,
    val upcoming: List<LePointUpcoming>
)

data class LePointActive(
    val sourceId: String,
    val name: String,
    val message: String?,
    val url: String?,
    val since: String?,
    /** Nombre de zones/elements concernes pour une source parametree, null sinon. */
    val count: Int?,
    val labels: List<String>
)

data class LePointUpcoming(val sourceId: String, val start: String?, val message: String?, val url: String?)

data class Geo(
    val countries: List<GeoItem>,
    val departements: List<Departement>,
    val regions: List<GeoItem>
)

data class GeoItem(val code: String, val name: String)

/** Departement : le code fait autorite, la region en decoule (server/geo.js). */
data class Departement(val code: String, val name: String, val region: String?) {
    /** Libelle affiche « 05 - Hautes-Alpes ». */
    val displayLabel: String get() = "$code - $name"
}

/**
 * Message d'alerte separe de son emoji de tete (« ⛈️ Aujourd'hui : vigilance ORANGE… »),
 * pour afficher l'emoji comme icone et lire le texte seul (TalkBack, notifications).
 */
data class SplitMessage(val emoji: String?, val text: String) {
    companion object {
        // Emoji de tete : pictogrammes + selecteurs de variante / ZWJ / modificateurs.
        private val leading = Regex(
            "^((?:[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2190}-\\x{21FF}\\x{2300}-\\x{23FF}\\x{2B00}-\\x{2BFF}" +
                "\\x{3030}\\x{303D}\\x{3297}\\x{3299}\\x{00A9}\\x{00AE}\\x{203C}\\x{2049}\\x{2122}\\x{2139}]" +
                "[\\x{FE0F}\\x{200D}\\x{1F3FB}-\\x{1F3FF}\\x{20E3}]*)+)\\s*"
        )

        fun of(message: String?): SplitMessage {
            val m = message?.trim().orEmpty()
            val match = leading.find(m) ?: return SplitMessage(null, m)
            val emoji = match.groupValues[1]
            val rest = m.substring(match.range.last + 1).trim()
            return if (rest.isEmpty()) SplitMessage(null, m) else SplitMessage(emoji, rest)
        }
    }
}
