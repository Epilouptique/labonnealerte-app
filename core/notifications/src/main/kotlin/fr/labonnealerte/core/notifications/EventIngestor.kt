package fr.labonnealerte.core.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.labonnealerte.core.data.AppPreferences
import fr.labonnealerte.core.data.CatalogRepository
import fr.labonnealerte.core.data.Clock
import fr.labonnealerte.core.database.NotificationDao
import fr.labonnealerte.core.database.NotificationEntity
import fr.labonnealerte.core.model.EventClassifier
import fr.labonnealerte.core.model.EventKind
import fr.labonnealerte.core.model.IncomingEvent
import fr.labonnealerte.core.model.NoiseGuard
import fr.labonnealerte.core.model.NotificationFamily
import fr.labonnealerte.core.model.Severity
import fr.labonnealerte.core.model.SplitMessage
import java.security.MessageDigest
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Point d'entree unique des evenements (push, releve periodique, simulation) :
 * deduplication → historique Room → regles (sourdine, heures calmes, plafond) → notification.
 */
@Singleton
class EventIngestor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: NotificationDao,
    private val catalog: CatalogRepository,
    private val prefs: AppPreferences,
    private val notifier: AlertNotifier,
    private val clock: Clock
) {
    private val mutex = Mutex()

    /** Renvoie le nombre d'evenements nouveaux (jamais deux notifications identiques). */
    suspend fun ingest(events: List<IncomingEvent>): Int = mutex.withLock {
        var fresh = 0
        for (e in events.sortedBy { it.serverId ?: Long.MAX_VALUE }) {
            if (handle(e)) fresh++
            e.serverId?.let { prefs.advanceLastEventId(it) }
        }
        fresh
    }

    private fun contentKey(e: IncomingEvent): String {
        val raw = listOf(e.sourceId.orEmpty(), e.kind.name, e.message.orEmpty().trim(), e.label.orEmpty()).joinToString("|")
        val h = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        return "c:" + h.take(32)
    }

    private suspend fun handle(e: IncomingEvent): Boolean {
        val now = clock.now()
        val source = e.sourceId?.let { catalog.source(it).first() }
        // Source absente du catalogue charge : nom deja vu dans l'historique (APP-08).
        val sourceName = e.sourceName?.takeIf { it.isNotBlank() } ?: source?.name
            ?: e.sourceId?.let { dao.lastSourceName(it) }
        val title = titleFor(sourceName, e.label, e.origin)
        val split = SplitMessage.of(e.message)

        // Deduplication : par id serveur, et par contenu sur 24 h (un push sans id puis la
        // releve du meme evenement ne doivent pas notifier deux fois).
        // Alerte de test realiste : chaque appui est une nouvelle alerte (cle datee).
        val key = e.serverId?.let { "ev:$it" }
            ?: if (e.origin == IncomingEvent.Origin.SIMULATION) "sim:$now" else contentKey(e)
        // Avec un id serveur : on ne saute que si un push SANS id a deja affiche ce contenu
        // (on lui rattache l id). Une alerte qui revient avec un nouvel id est bien notifiee.
        val ck = contentKey(e)
        if (e.origin != IncomingEvent.Origin.TEST && e.origin != IncomingEvent.Origin.SIMULATION) {
            val sid = e.serverId
            if (sid != null && dao.countUnattached(ck, now - DAY) > 0) {
                dao.attachServerId(ck, sid)
                return false
            }
            if (sid == null && dao.countByKey(ck, now - DAY) > 0) return false
        }

        if (e.kind == EventKind.DEACTIVATED) return calm(key, e, sourceName, now)
        if (e.kind != EventKind.ACTIVATED) return false

        val groups = catalog.categories.first().associate { it.slug to it.group }
        val categories = e.categories.ifEmpty { source?.categories.orEmpty() }
        val family = if (e.origin == IncomingEvent.Origin.TEST && e.sourceId == null) {
            NotificationFamily.SYSTEME
        } else if (e.origin == IncomingEvent.Origin.ECHEANCE) {
            NotificationFamily.ECHEANCES
        } else {
            EventClassifier.family(categories, source?.type, e.message, groups::get)
        }
        val severity = EventClassifier.severity(e.message)
        val text = (split.emoji?.let { "$it " } ?: "") + split.text.ifBlank { context.getString(R.string.notif_par_defaut) }

        val inserted = dao.insert(entity(key, e, sourceName, split.emoji, title, text, family.channelId, now, false, contentKey(e)))
        if (inserted <= 0) return false

        // Sourdine locale (action « Silence 1 h / 24 h ») : historique seulement.
        if (e.sourceId != null && prefs.mutedUntil(e.sourceId!!) > now) return true

        val settings = prefs.current()
        val hour = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val quiet = settings.quietHours.isQuiet(hour) && !(severity == Severity.URGENT && settings.urgentBypassQuiet)
        val sentLastHour = dao.countSince(now - HOUR)
        val silent = quiet || !NoiseGuard.allowSound(sentLastHour - 1, settings.maxPerHour, severity)
        if (silent) Timber.d("Notification discrete (heures calmes ou plafond)")
        notifier.show(
            AlertDisplay(
                localId = inserted,
                sourceId = e.sourceId,
                title = title,
                text = text,
                subText = sourceName?.let { context.getString(R.string.notif_source, it) },
                url = e.url,
                family = family,
                severity = severity,
                silent = silent,
                whenMillis = e.createdAtMillis.takeIf { it > 0 } ?: now,
                timeoutMillis = if (family == NotificationFamily.BONS_PLANS) DAY else null,
                // Test : canal dedie, importance elevee et son par defaut (APP-07).
                channelId = if (family == NotificationFamily.SYSTEME) Channels.TEST_CHANNEL_ID else family.channelId,
                number = e.sourceId?.let { dao.countUnreadForSource(it) }?.coerceAtLeast(1) ?: 1
            )
        )
        return true
    }

    /**
     * Titre de la notification : nom de la source (et libelle de zone). « Test de La Bonne
     * Alerte » seulement pour un vrai test ; sinon un titre neutre (APP-08 : avant, toute source
     * inconnue du catalogue s'affichait comme un test).
     */
    private fun titleFor(sourceName: String?, label: String?, origin: IncomingEvent.Origin): String {
        val t = listOfNotNull(sourceName, label).filter { it.isNotBlank() }.joinToString(" · ")
        return t.ifBlank {
            context.getString(if (origin == IncomingEvent.Origin.TEST) R.string.notif_test_titre else R.string.notif_titre_defaut)
        }
    }

    /**
     * Retour au calme, sans sonner. Alerte deja vue : on retire la notification. Alerte pas
     * encore vue : la notification reste et annonce la fin (APP-07).
     */
    private suspend fun calm(key: String, e: IncomingEvent, sourceName: String?, now: Long): Boolean {
        val inserted = dao.insert(entity(key, e, sourceName, null, context.getString(R.string.notif_retour_calme), "", "", now, true))
        val sid = e.sourceId
        if (inserted > 0 && sid != null) {
            val pending = dao.latestUnreadForSource(sid)
            if (pending == null) {
                notifier.cancelForSource(sid)
            } else {
                val channel = pending.channel.ifBlank { NotificationFamily.SERVICES.channelId }
                notifier.showCalm(sid, sourceName, channel, dao.countUnreadForSource(sid), pending.id)
            }
        }
        return inserted > 0
    }

    private fun entity(
        key: String,
        e: IncomingEvent,
        sourceName: String?,
        emoji: String?,
        title: String,
        text: String,
        channel: String,
        now: Long,
        read: Boolean,
        contentKey: String? = null
    ) = NotificationEntity(
        dedupKey = key,
        serverEventId = e.serverId,
        sourceId = e.sourceId,
        sourceName = sourceName,
        kind = when (e.origin) {
            IncomingEvent.Origin.TEST, IncomingEvent.Origin.SIMULATION -> "test"
            IncomingEvent.Origin.ECHEANCE -> "echeance"
            else -> e.kind.name.lowercase()
        },
        emoji = emoji,
        title = title,
        text = text,
        url = e.url,
        receivedAtMillis = now,
        read = read,
        channel = channel,
        contentKey = contentKey ?: contentKey(e)
    )

    companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
