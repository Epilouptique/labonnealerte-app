package fr.labonnealerte.core.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.labonnealerte.core.model.NotificationFamily
import fr.labonnealerte.core.model.Severity
import javax.inject.Inject
import javax.inject.Singleton

/** Ce qu'il faut afficher, decide par [EventIngestor]. */
data class AlertDisplay(
    val localId: Long,
    val sourceId: String?,
    val title: String,
    val text: String,
    val subText: String?,
    val url: String?,
    val family: NotificationFamily,
    val severity: Severity,
    /** Heures calmes ou plafond atteint : affichee sans son ni vibration. */
    val silent: Boolean,
    val whenMillis: Long,
    val timeoutMillis: Long?,
    /** Canal Android ; par defaut celui de la famille (le test a son propre canal). */
    val channelId: String = family.channelId,
    /** Alertes non lues de cette notification (setNumber) : le lanceur additionne pour le total. */
    val number: Int = 1
)

/**
 * Notifications Android riches : BigText, groupe par alerte (tag = id de source, un nouvel
 * evenement remplace l'ancien), resume de groupe, actions, expiration, couleur d'accent.
 */
@Singleton
class AlertNotifier @Inject constructor(@ApplicationContext private val context: Context) {
    private val nm = NotificationManagerCompat.from(context)

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return nm.areNotificationsEnabled()
    }

    private fun openIntent(sourceId: String?, localId: Long): PendingIntent {
        val uri = if (sourceId != null) "labonnealerte://alert/$sourceId" else "labonnealerte://alert/"
        val i = Intent(Intent.ACTION_VIEW, uri.toUri()).setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(NotificationActions.EXTRA_LOCAL_ID, localId)
        return PendingIntent.getActivity(context, localId.toInt(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun action(actionName: String, d: AlertDisplay, req: Int): PendingIntent {
        val i = Intent(context, NotificationActions::class.java).setAction(actionName)
            .putExtra(NotificationActions.EXTRA_SOURCE_ID, d.sourceId)
            .putExtra(NotificationActions.EXTRA_LOCAL_ID, d.localId)
        return PendingIntent.getBroadcast(
            context,
            (d.localId * 10 + req).toInt(),
            i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Partage : activite directe (Android interdit de lancer une activite depuis un recepteur). */
    private fun shareIntent(d: AlertDisplay): PendingIntent {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, d.title + "\u00A0: " + d.text + (d.url?.let { "\n$it" } ?: ""))
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            (d.localId * 10 + 3).toInt(),
            chooser,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun show(d: AlertDisplay) {
        if (!canNotify()) return
        val b = NotificationCompat.Builder(context, d.channelId)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ACCENT)
            .setContentTitle(d.title)
            .setContentText(d.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(d.text).setSummaryText(d.subText))
            .setSubText(d.subText)
            .setWhen(d.whenMillis)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(openIntent(d.sourceId, d.localId))
            .setGroup(GROUP)
            .setCategory(if (d.severity == Severity.URGENT) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_EVENT)
            .setPriority(
                when (d.severity) {
                    Severity.URGENT -> NotificationCompat.PRIORITY_MAX
                    Severity.HIGH -> NotificationCompat.PRIORITY_DEFAULT
                    else -> NotificationCompat.PRIORITY_LOW
                }
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(d.silent)
            .setNumber(d.number)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
        if (d.sourceId != null) {
            b.addAction(0, context.getString(R.string.notif_action_sourdine_1h), action(NotificationActions.ACTION_MUTE_1H, d, 1))
            b.addAction(0, context.getString(R.string.notif_action_sourdine_24h), action(NotificationActions.ACTION_MUTE_24H, d, 2))
        }
        b.addAction(0, context.getString(R.string.notif_action_partager), shareIntent(d))
        if (d.severity == Severity.URGENT) b.setVibrate(Channels.URGENT_VIBRATION)
        d.timeoutMillis?.let { b.setTimeoutAfter(it) }
        post(tagOf(d.sourceId, d.localId), b.build())
        updateSummary()
    }

    /** « Retour au calme » : retire la notification d'alerte de cette source. */
    fun cancelForSource(sourceId: String) {
        nm.cancel(sourceId, NOTIF_ID)
        updateSummary()
    }

    /**
     * « Retour au calme » d'une alerte pas encore vue : la notification reste (l'alerte est en
     * attente) mais annonce la fin, sans son ni vibration (APP-07).
     */
    fun showCalm(sourceId: String, sourceName: String?, channelId: String, number: Int, localId: Long) {
        if (!canNotify()) return
        val name = sourceName ?: context.getString(R.string.notif_titre_defaut)
        val text = context.getString(R.string.notif_retour_calme_texte, name)
        val n = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ACCENT)
            .setContentTitle(context.getString(R.string.notif_retour_calme))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openIntent(sourceId, localId))
            .setGroup(GROUP)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setNumber(number)
            .build()
        post(sourceId, n)
        updateSummary()
    }

    /**
     * Aligne le tiroir sur l'historique : retire les notifications dont toutes les alertes ont
     * ete vues dans l'app (ouverture, « Tout marquer lu », action Silence).
     */
    fun syncTray(unread: List<UnreadAlert>) {
        val sys = context.getSystemService(NotificationManager::class.java)
        val active = sys.activeNotifications.filter { it.notification.group == GROUP && it.tag != SUMMARY_TAG }.mapNotNull { it.tag }
        val gone = TrayPolicy.toCancel(active, unread)
        if (gone.isEmpty()) return
        gone.forEach { nm.cancel(it, NOTIF_ID) }
        updateSummary()
    }

    fun cancelAll() = nm.cancelAll()

    private fun tagOf(sourceId: String?, localId: Long) = TrayPolicy.tagOf(sourceId, localId)

    private fun post(tag: String, n: Notification) {
        try {
            nm.notify(tag, NOTIF_ID, n)
        } catch (e: SecurityException) {
            // Permission retiree entre la verification et l'envoi : rien a faire.
        }
    }

    /** Resume de groupe quand au moins deux alertes sont affichees. */
    private fun updateSummary() {
        val sys = context.getSystemService(NotificationManager::class.java)
        val children = sys.activeNotifications.filter { it.notification.group == GROUP && it.tag != SUMMARY_TAG }
        val active = children.size
        // Total des alertes en attente (une notification peut en regrouper plusieurs).
        val total = children.sumOf { maxOf(1, it.notification.number) }
        if (active < 2) {
            nm.cancel(SUMMARY_TAG, NOTIF_ID)
            return
        }
        val summary = NotificationCompat.Builder(context, NotificationFamily.SYSTEME.channelId)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ACCENT)
            .setContentTitle(context.getString(R.string.notif_groupe_titre, total))
            .setContentText(context.getString(R.string.notif_groupe_texte))
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setSilent(true)
            .setAutoCancel(true)
            .build()
        post(SUMMARY_TAG, summary)
    }

    companion object {
        const val GROUP = "lba_alertes"
        const val NOTIF_ID = 1
        const val SUMMARY_TAG = "lba_summary"
        const val ACCENT = 0xFFA567E3.toInt()
    }
}
