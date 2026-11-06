package fr.labonnealerte.core.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color

/**
 * Familles de categories (server/categories.js GROUPS) : libelle, pictogramme et couleur.
 * Couleurs reprises de public/css/site.css l.1525-1550 ([data-group] --tag-c) quand elles existent.
 */
enum class Famille(val slug: String, @StringRes val label: Int, val emoji: String, val color: Color) {
    BONS_PLANS("bons-plans-conso", R.string.famille_bons_plans_conso, "🏷️", Color(0xFFD9467A)),
    SHOPPING("shopping", R.string.famille_shopping, "🛍️", Color(0xFFC0569B)),
    BILLETTERIE("billetterie-sorties", R.string.famille_billetterie_sorties, "🎟️", Color(0xFF9B5DE5)),
    VOYAGES("voyages", R.string.famille_voyages, "✈️", Color(0xFF3A9AD9)),
    METEO("meteo-risques", R.string.famille_meteo_risques, "⛈️", Color(0xFFE3823F)),
    ENVIRONNEMENT("environnement-nature", R.string.famille_environnement_nature, "🌿", Color(0xFF3FA34D)),
    ENERGIE("energie", R.string.famille_energie, "⚡", Color(0xFFE0A526)),
    TRANSPORTS("transports", R.string.famille_transports, "🚆", Color(0xFF4F7CAC)),
    TECH("tech-dev", R.string.famille_tech_dev, "💻", Color(0xFF5B7DE3)),
    SECURITE("securite-cyber", R.string.famille_securite_cyber, "🛡️", Color(0xFFB4473F)),
    ADMINISTRATION("administration", R.string.famille_administration, "🏛️", Color(0xFF6F86A8)),
    PRO("pro-marches", R.string.famille_pro_marches, "💼", Color(0xFF7A6F5A)),
    EMPLOI("emploi-formation", R.string.famille_emploi_formation, "🎓", Color(0xFF4C8C8A)),
    FINANCE("finance", R.string.famille_finance, "💶", Color(0xFF2F9E7A)),
    SANTE("sante", R.string.famille_sante, "🩺", Color(0xFFD9534F)),
    IMMOBILIER("immobilier", R.string.famille_immobilier, "🏠", Color(0xFF8C6D4F)),
    CULTURE("culture-medias", R.string.famille_culture_medias, "🎭", Color(0xFFA567E3)),
    GAMING("gaming", R.string.famille_gaming, "🎮", Color(0xFF6C5CE7)),
    SPORT("sport", R.string.famille_sport, "⚽", Color(0xFF2E8B57)),
    VIE_LOCALE("vie-locale", R.string.famille_vie_locale, "🏘️", Color(0xFFB07D3A)),
    ALIMENTATION("alimentation", R.string.famille_alimentation, "🍽️", Color(0xFFE36767)),
    AGRICULTURE("agriculture-jardin", R.string.famille_agriculture_jardin, "🌱", Color(0xFF6A994E)),
    AUTO("auto-moto", R.string.famille_auto_moto, "🚗", Color(0xFF5D6D7E)),
    ANIMAUX("animaux", R.string.famille_animaux, "🐾", Color(0xFFC08552)),
    ESPACE("espace-science", R.string.famille_espace_science, "🔭", Color(0xFF3D5A80)),
    SOLIDARITE("solidarite", R.string.famille_solidarite, "🤝", Color(0xFFE07A5F)),
    FRANCOPHONIE("francophonie-international", R.string.famille_francophonie_international, "🌍", Color(0xFF3A86FF)),
    AUTRE("autre", R.string.famille_autre, "✨", Color(0xFF8D8D8D));

    companion object {
        /** Categories d'eau : goutte au lieu de l'eclair de la famille Energie (APP-13). */
        private val WATER = setOf("eau", "coupures-eau")

        /** Pictogramme d'une alerte : celui de sa famille, sauf l'eau. */
        fun emojiFor(famille: Famille, categories: Collection<String>): String =
            if (categories.any { it in WATER }) "💧" else famille.emoji

        private val bySlug = entries.associateBy { it.slug }
        fun of(slug: String?): Famille = bySlug[slug] ?: AUTRE
    }
}
