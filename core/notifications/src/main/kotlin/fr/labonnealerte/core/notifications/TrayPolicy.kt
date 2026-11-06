package fr.labonnealerte.core.notifications

/** Alerte non lue de l'historique, vue par le tiroir de notifications. */
data class UnreadAlert(val localId: Long, val sourceId: String?)

/**
 * Regle du tiroir (APP-07) : une notification reste tant que son alerte n'a pas ete vue dans
 * l'app. Elle disparait quand on la touche (ouverture de l'alerte), quand l'alerte est lue dans
 * l'app, ou par une action (Silence). Le nombre de chaque notification est son nombre
 * d'alertes non lues ; le lanceur additionne ces nombres pour le total.
 */
object TrayPolicy {
    /** Etiquette de la notification : une par source (le dernier evenement remplace l'ancien). */
    fun tagOf(sourceId: String?, localId: Long): String = sourceId ?: "local-$localId"

    /** Nombre d'alertes non lues par notification. */
    fun counts(unread: List<UnreadAlert>): Map<String, Int> = unread.groupingBy { tagOf(it.sourceId, it.localId) }.eachCount()

    /** Notifications affichees dont plus aucune alerte n'est en attente : a retirer. */
    fun toCancel(activeTags: Collection<String>, unread: List<UnreadAlert>): List<String> {
        val keep = counts(unread).keys
        return activeTags.filter { it !in keep }
    }

    /** Total pour la notification de synthese (somme des alertes en attente affichees). */
    fun total(activeTags: Collection<String>, unread: List<UnreadAlert>): Int {
        val c = counts(unread)
        return activeTags.sumOf { c[it] ?: 0 }
    }
}
