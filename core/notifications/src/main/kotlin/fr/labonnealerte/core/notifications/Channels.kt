package fr.labonnealerte.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.provider.Settings
import fr.labonnealerte.core.model.NotificationFamily

/** Canaux Android, un par famille : l'utilisateur les regle aussi dans les reglages du systeme. */
object Channels {
    /** Vibration distincte des urgences (longue, repetee). */
    val URGENT_VIBRATION = longArrayOf(0, 600, 250, 600, 250, 900)

    fun importance(f: NotificationFamily): Int = when (f) {
        NotificationFamily.URGENCES -> NotificationManager.IMPORTANCE_HIGH
        // DEFAULT = avec son, sans bandeau en surimpression (heads-up = HIGH, reserve aux urgences).
        // Bons plans : avec son depuis APP-04 (decision de Hugo), reglable dans Android.
        NotificationFamily.METEO_ENVIRONNEMENT, NotificationFamily.SERVICES, NotificationFamily.COMMUNAUTE,
        NotificationFamily.ECHEANCES, NotificationFamily.VIE_LOCALE, NotificationFamily.BONS_PLANS -> NotificationManager.IMPORTANCE_DEFAULT
        NotificationFamily.SYSTEME -> NotificationManager.IMPORTANCE_LOW
    }

    private fun names(f: NotificationFamily): Pair<Int, Int> = when (f) {
        NotificationFamily.URGENCES -> R.string.canal_urgences to R.string.canal_urgences_desc
        NotificationFamily.METEO_ENVIRONNEMENT -> R.string.canal_meteo to R.string.canal_meteo_desc
        NotificationFamily.VIE_LOCALE -> R.string.canal_vie_locale to R.string.canal_vie_locale_desc
        NotificationFamily.SERVICES -> R.string.canal_services to R.string.canal_services_desc
        NotificationFamily.BONS_PLANS -> R.string.canal_bons_plans to R.string.canal_bons_plans_desc
        NotificationFamily.COMMUNAUTE -> R.string.canal_communaute to R.string.canal_communaute_desc
        NotificationFamily.ECHEANCES -> R.string.canal_echeances to R.string.canal_echeances_desc
        NotificationFamily.SYSTEME -> R.string.canal_systeme to R.string.canal_systeme_desc
    }

    /**
     * Canal de la notification de test (Reglages > Aide). Importance elevee et son par defaut :
     * le test doit sonner comme une vraie alerte (APP-07). Avant, il passait par « systeme »
     * (importance basse : ni son ni pastille).
     */
    const val TEST_CHANNEL_ID = "test_reception"

    /** Canaux qui affichent la pastille sur l'icone : tous sauf « systeme » (resume de groupe). */
    fun showsBadge(channelId: String): Boolean = channelId != NotificationFamily.SYSTEME.channelId

    /** Anciens identifiants remplaces par un nouveau canal (importance changee) : supprimes au demarrage. */
    val LEGACY_IDS = listOf("bons_plans_culture")

    /** Idempotent : peut etre appele a chaque demarrage (les reglages de l'utilisateur sont conserves). */
    fun createAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        LEGACY_IDS.forEach { id -> if (nm.getNotificationChannel(id) != null) nm.deleteNotificationChannel(id) }
        val channels = NotificationFamily.entries.map { f ->
            val (name, desc) = names(f)
            NotificationChannel(f.channelId, context.getString(name), importance(f)).apply {
                description = context.getString(desc)
                setShowBadge(showsBadge(f.channelId))
                if (f == NotificationFamily.URGENCES) {
                    enableVibration(true)
                    vibrationPattern = URGENT_VIBRATION
                    setSound(
                        Settings.System.DEFAULT_ALARM_ALERT_URI ?: Settings.System.DEFAULT_NOTIFICATION_URI,
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
                    )
                }
            }
        }
        val test = NotificationChannel(TEST_CHANNEL_ID, context.getString(R.string.canal_test), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.canal_test_desc)
            setShowBadge(true)
            // Son et vibration par defaut du telephone (ceux d'une notification ordinaire).
            enableVibration(true)
        }
        nm.createNotificationChannels(channels + test)
    }
}
