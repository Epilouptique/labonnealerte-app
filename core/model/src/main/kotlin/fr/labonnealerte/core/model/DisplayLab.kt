package fr.labonnealerte.core.model

/**
 * « Options d'affichage (test) » (APP-11) : variantes d'ergonomie a essayer, une par
 * interrupteur. Par defaut : tout active sauf le voyant qui pulse (violet affirme supprime a la demande de Hugo).
 * Stocke uniquement sur le telephone ; un choix enregistre l'emporte toujours sur le defaut.
 */
data class DisplayLab(
    /** a) « Officiel » remplace par une icone de validation sur les vignettes (texte complet sur la fiche). */
    val officialIcon: Boolean = true,
    /** b) Liseré de couleur de la famille sur les vignettes (le pictogramme reste). */
    val categoryColor: Boolean = true,
    /** d) « Alerte en cours » : icone d'alerte dans la pastille au lieu du point. */
    val activeIcon: Boolean = true,
    /** e1) Pulsation douce du voyant d'une alerte en cours (coupee si les animations sont reduites). */
    val activePulse: Boolean = false,
    /** e2) Fond teinte (sans bord) sur la vignette d'une alerte en cours. */
    val activeHalo: Boolean = true,
    /** f) En-tete du kiosque : « labonnealerte » centre, aux couleurs de la marque. */
    val brandHeader: Boolean = true,
    /**
     * APP-12, 1 et 2) Nouvelle navigation : barre Rechercher | Mes alertes | Publier | Forum | Compte
     * et ecran Compte en blocs. Un seul interrupteur pour les deux (pas de combinaison incoherente).
     */
    val newNavigation: Boolean = true,
    /** APP-12, 3) Bouton « Suivre » en pilule pleine, « Suivi » gris avec coche. */
    val pillFollow: Boolean = true,
    /** APP-12, 4) Filet leger sous l'en-tete des ecrans (hors accueil). */
    val headerDivider: Boolean = true,
    /** APP-12, 5) Reglages en lignes avec fleche a droite et filet de separation. */
    val settingsRows: Boolean = true,
    /** APP-14 : sans sous-titre sur les vignettes (desactive par defaut). */
    val hideSubtitle: Boolean = true
) {
    val isDefault: Boolean get() = this == DisplayLab()

    /** Ordre d'affichage des interrupteurs et cles de stockage (stables : ne pas renommer). */
    enum class Option(val key: String) {
        OFFICIAL_ICON("lab_official_icon"),
        CATEGORY_COLOR("lab_category_color"),
        ACTIVE_ICON("lab_active_icon"),
        ACTIVE_PULSE("lab_active_pulse"),
        ACTIVE_HALO("lab_active_halo"),
        BRAND_HEADER("lab_brand_header"),
        NEW_NAVIGATION("lab_new_navigation"),
        PILL_FOLLOW("lab_pill_follow"),
        HEADER_DIVIDER("lab_header_divider"),
        SETTINGS_ROWS("lab_settings_rows"),
        HIDE_SUBTITLE("lab_hide_subtitle")
    }

    /** Reglages en lignes : option 5, ou compris dans la nouvelle navigation (ecran Compte). */
    val rowSettings: Boolean get() = settingsRows || newNavigation

    operator fun get(o: Option): Boolean = when (o) {
        Option.OFFICIAL_ICON -> officialIcon
        Option.CATEGORY_COLOR -> categoryColor
        Option.ACTIVE_ICON -> activeIcon
        Option.ACTIVE_PULSE -> activePulse
        Option.ACTIVE_HALO -> activeHalo
        Option.BRAND_HEADER -> brandHeader
        Option.NEW_NAVIGATION -> newNavigation
        Option.PILL_FOLLOW -> pillFollow
        Option.HEADER_DIVIDER -> headerDivider
        Option.SETTINGS_ROWS -> settingsRows
        Option.HIDE_SUBTITLE -> hideSubtitle
    }

    fun with(o: Option, on: Boolean): DisplayLab = when (o) {
        Option.OFFICIAL_ICON -> copy(officialIcon = on)
        Option.CATEGORY_COLOR -> copy(categoryColor = on)
        Option.ACTIVE_ICON -> copy(activeIcon = on)
        Option.ACTIVE_PULSE -> copy(activePulse = on)
        Option.ACTIVE_HALO -> copy(activeHalo = on)
        Option.BRAND_HEADER -> copy(brandHeader = on)
        Option.NEW_NAVIGATION -> copy(newNavigation = on)
        Option.PILL_FOLLOW -> copy(pillFollow = on)
        Option.HEADER_DIVIDER -> copy(headerDivider = on)
        Option.SETTINGS_ROWS -> copy(settingsRows = on)
        Option.HIDE_SUBTITLE -> copy(hideSubtitle = on)
    }

    companion object {
        /** Tout desactive : le rendu d'avant le laboratoire. */
        val ALL_OFF: DisplayLab = Option.entries.fold(DisplayLab()) { lab, o -> lab.with(o, false) }

        /** Valeur enregistree si elle existe, sinon le defaut (APP-13 : un choix deja fait est conserve). */
        fun of(read: (Option) -> Boolean?): DisplayLab =
            Option.entries.fold(DisplayLab()) { lab, o -> read(o)?.let { lab.with(o, it) } ?: lab }
    }
}
