package fr.labonnealerte.core.model

/**
 * Masquage local (APP-23, politique Google sur le contenu des utilisateurs) : auteurs du forum
 * et signalements communautaires caches sur ce telephone, annulable dans Reglages > Confidentialite.
 * Cles : « auteur:<pseudo> » (ou « nom:<nom affiche> » sans pseudo), « signalement:<id> ».
 */
object HiddenContent {
    private const val AUTHOR = "auteur:"
    private const val NAME = "nom:"
    private const val REPORT = "signalement:"

    fun authorKey(pseudo: String?, displayName: String): String =
        pseudo?.trim()?.takeIf { it.isNotEmpty() }?.let { AUTHOR + it.lowercase() } ?: (NAME + displayName.trim())

    fun reportKey(id: String): String = REPORT + id

    /** Libelle lisible dans Reglages : « @pseudo », le nom, ou « Signalement ». */
    fun label(key: String): String = when {
        key.startsWith(AUTHOR) -> "@" + key.removePrefix(AUTHOR)
        key.startsWith(NAME) -> key.removePrefix(NAME)
        else -> "Signalement " + key.removePrefix(REPORT)
    }

    fun isReport(key: String): Boolean = key.startsWith(REPORT)

    fun <T> filter(items: List<T>, hidden: Set<String>, key: (T) -> String): List<T> =
        if (hidden.isEmpty()) items else items.filterNot { key(it) in hidden }
}
