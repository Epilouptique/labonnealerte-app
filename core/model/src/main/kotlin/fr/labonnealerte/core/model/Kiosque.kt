package fr.labonnealerte.core.model

/** Modes du kiosque, comme les puces speciales du site (public/js/site.js computeSpecialIds). */
enum class KiosqueMode { TOUT, NOUVEAUTES, POPULAIRES, FAVORIS, MES_ALERTES }

enum class KiosqueSort { SITE, NOM, ABONNES, RECENTES }

data class KiosqueFilter(
    val query: String = "",
    val mode: KiosqueMode = KiosqueMode.TOUT,
    val category: String? = null,
    val group: String? = null,
    val onlyActive: Boolean = false,
    val sort: KiosqueSort = KiosqueSort.SITE
)

object Kiosque {
    const val NOUVEAUTES_COUNT = 6
    const val POPULAIRES_COUNT = 12

    /** Texte indexe, comme `searchText` du site : nom, sous-titre, description, slugs et libelles de categories. */
    fun searchText(s: Source, categoryLabel: (String) -> String?): String = buildString {
        append(s.name).append(' ')
        append(s.subtitle.orEmpty()).append(' ')
        append(s.description.orEmpty())
        s.categories.forEach { append(' ').append(it).append(' ').append(categoryLabel(it).orEmpty()) }
    }

    fun nouveautes(sources: List<Source>): Set<String> = sources
        .filter { it.type != SourceType.LINKED }
        .sortedByDescending { it.createdAt.orEmpty() } // ISO 8601 : ordre lexical = chronologique
        .take(NOUVEAUTES_COUNT)
        .map { it.id }
        .toSet()

    /** 12 plus aimees, a egalite l'ordre du site (liste deja triee par le serveur). */
    fun populaires(sources: List<Source>): Set<String> = sources
        .filter { it.type != SourceType.LINKED }
        .withIndex()
        .sortedWith(compareByDescending<IndexedValue<Source>> { it.value.likesCount }.thenBy { it.index })
        .take(POPULAIRES_COUNT)
        .map { it.value.id }
        .toSet()

    fun apply(
        sources: List<Source>,
        filter: KiosqueFilter,
        favorites: Set<String> = emptySet(),
        subscribed: Set<String> = emptySet(),
        groupOf: (String) -> String? = { null },
        categoryLabel: (String) -> String? = { null }
    ): List<Source> {
        val special: Set<String>? = when (filter.mode) {
            KiosqueMode.NOUVEAUTES -> nouveautes(sources)
            KiosqueMode.POPULAIRES -> populaires(sources)
            KiosqueMode.FAVORIS -> favorites
            KiosqueMode.MES_ALERTES -> subscribed
            KiosqueMode.TOUT -> null
        }
        val out = sources.filter { s ->
            (special == null || s.id in special) &&
                (filter.category == null || filter.category in s.categories) &&
                (filter.group == null || s.categories.any { groupOf(it) == filter.group }) &&
                (!filter.onlyActive || s.state == SourceState.ACTIVE) &&
                (filter.query.isBlank() || SearchNormalizer.matches(searchText(s, categoryLabel), filter.query))
        }
        val sorted = when (filter.sort) {
            KiosqueSort.SITE -> if (filter.mode == KiosqueMode.NOUVEAUTES) out.sortedByDescending { it.createdAt.orEmpty() } else out
            KiosqueSort.NOM -> out.sortedBy { SearchNormalizer.normalize(it.name) }
            KiosqueSort.ABONNES -> out.sortedByDescending { it.subscriberCount }
            KiosqueSort.RECENTES -> out.sortedByDescending { it.createdAt.orEmpty() }
        }
        return subscribedLast(sorted, subscribed, filter.mode)
    }

    /**
     * Alertes deja suivies en fin de liste, dans le meme ordre qu'avant (tri stable) : l'arrivee
     * dans le kiosque montre d'abord ce qu'on ne suit pas encore (APP-08). Sans objet en mode
     * « Mes alertes », ou tout est suivi.
     */
    fun subscribedLast(sources: List<Source>, subscribed: Set<String>, mode: KiosqueMode = KiosqueMode.TOUT): List<Source> {
        if (subscribed.isEmpty() || mode == KiosqueMode.MES_ALERTES) return sources
        val (suivies, autres) = sources.partition { it.id in subscribed }
        return autres + suivies
    }

    /** Categories triees par frequence decroissante puis libelle (renderChips du site). */
    fun categoriesByFrequency(sources: List<Source>, label: (String) -> String): List<Pair<String, Int>> {
        val counts = HashMap<String, Int>()
        sources.forEach { s -> s.categories.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { SearchNormalizer.normalize(label(it.key)) })
            .map { it.key to it.value }
    }
}
