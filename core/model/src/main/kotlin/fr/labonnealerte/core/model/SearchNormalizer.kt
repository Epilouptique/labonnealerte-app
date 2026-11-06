package fr.labonnealerte.core.model

import java.text.Normalizer

/**
 * Recherche insensible a la casse, aux accents et aux apostrophes.
 *
 * Reprend `normalizeSearch` du site (public/js/cards.js l.874) : minuscules, accents retires,
 * tout ce qui n'est pas [a-z0-9] devient un espace. On ajoute une comparaison « collee »
 * (sans espaces) pour que « deau » trouve « d'eau » (DECISIONS.md D004).
 */
object SearchNormalizer {
    private val diacritics = Regex("\\p{Mn}+")
    private val nonAlnum = Regex("[^a-z0-9]+")

    fun normalize(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        val lower = input.lowercase()
        val noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(diacritics, "")
        // Ligatures courantes en francais, absentes de la decomposition NFD.
        val expanded = noAccents.replace("œ", "oe").replace("æ", "ae")
        return expanded.replace(nonAlnum, " ").trim()
    }

    /** Vrai si chaque mot de [query] est contenu dans [haystack] (forme normale ou collee). */
    fun matches(haystack: String, query: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        val h = normalize(haystack)
        val hCompact = h.replace(" ", "")
        return q.split(' ').all { word -> h.contains(word) || hCompact.contains(word) } ||
            hCompact.contains(q.replace(" ", ""))
    }
}
