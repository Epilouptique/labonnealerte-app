package fr.labonnealerte.core.push

import fr.labonnealerte.core.data.AppPreferences
import fr.labonnealerte.core.data.Clock
import fr.labonnealerte.core.data.session.SessionStore
import fr.labonnealerte.core.model.EventKind
import fr.labonnealerte.core.model.IncomingEvent
import fr.labonnealerte.core.network.AccountApi
import fr.labonnealerte.core.network.LbaJson
import fr.labonnealerte.core.network.dto.AppEventDto
import fr.labonnealerte.core.notifications.EventIngestor
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import timber.log.Timber

/** Charge utile Web Push envoyee aujourd'hui par le serveur (server/webpush.js) : {title, body, url}. */
@Serializable
data class PushPayload(val title: String? = null, val body: String? = null, val url: String? = null)

sealed interface SyncResult {
    data class Ok(val newEvents: Int) : SyncResult
    data object NotSignedIn : SyncResult
    data object Failed : SyncResult
}

/** Recupere les evenements depuis le dernier id connu (GET /api/app/events) et les injecte. */
@Singleton
class EventSync @Inject constructor(
    private val api: AccountApi,
    private val session: SessionStore,
    private val prefs: AppPreferences,
    private val ingestor: EventIngestor,
    private val clock: Clock
) {
    /**
     * [fromPush] : releve declenchee par un push recu. Sinon (ouverture de l'app, releve
     * periodique), des evenements anciens jamais vus alors que le push etait actif signalent un
     * push qui n'arrive plus : on le note pour le bandeau de diagnostic (APP-05, decision E).
     */
    suspend fun sync(fromPush: Boolean = false): SyncResult {
        if (session.currentToken() == null) return SyncResult.NotSignedIn
        val reception = prefs.currentReception()
        var since = reception.lastEventId
        // Premiere releve (curseur a zero) : l'historique arrive d'un coup, ce n'est pas un push manque.
        val watchMissed = !fromPush && reception.pushRegisteredOnServer && since > 0
        var total = 0
        var missed = false
        return try {
            // Pagination bornee (10 pages x 100) : pas de boucle infinie meme si le serveur deraille.
            repeat(MAX_PAGES) {
                val page = api.appEvents(since, PAGE)
                val cutoff = clock.now() - MISSED_PUSH_AGE_MS
                val (old, recent) = page.events.map { it.toIncoming() }.partition { it.createdAtMillis in 1 until cutoff }
                val freshOld = ingestor.ingest(old)
                total += freshOld + ingestor.ingest(recent)
                if (watchMissed && freshOld > 0) missed = true
                if (page.lastId > since) prefs.advanceLastEventId(page.lastId)
                if (!page.hasMore || page.lastId <= since) return finish(total, missed)
                since = page.lastId
            }
            finish(total, missed)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // travail annule (WorkManager, fin d'app) : ce n'est pas un echec de releve
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Timber.w("Releve des evenements impossible (${e.javaClass.simpleName})")
            prefs.markPoll(clock.now(), ok = false)
            SyncResult.Failed
        }
    }

    private suspend fun finish(total: Int, missed: Boolean): SyncResult {
        prefs.markPoll(clock.now(), ok = true)
        if (missed) prefs.markMissedPush(clock.now())
        return SyncResult.Ok(total)
    }

    /** Push recu : on tente d'abord la releve structuree ; a defaut, on affiche le contenu du push. */
    suspend fun onPush(content: ByteArray?) {
        prefs.markPush(clock.now())
        val r = sync(fromPush = true)
        if (r is SyncResult.Ok && r.newEvents > 0) return
        val payload =
            content?.let { runCatching { LbaJson.decodeFromString(PushPayload.serializer(), String(it, Charsets.UTF_8)) }.getOrNull() }
                ?: return
        if (r is SyncResult.Ok) return // releve reussie sans nouveaute : le push etait deja connu
        ingestor.ingest(listOf(payload.toIncoming(clock.now())))
    }

    companion object {
        const val PAGE = 100
        const val MAX_PAGES = 10

        /** Un evenement plus vieux que ca, decouvert hors push alors que le push est actif, a ete manque. */
        const val MISSED_PUSH_AGE_MS = 30 * 60_000L

        private val STATUS_URL = Regex("^https://(?:www\\.)?labonnealerte\\.fr/source/([a-z0-9][a-z0-9-]{0,63})/statut")

        fun sourceIdFromUrl(url: String?): String? = url?.let { STATUS_URL.find(it)?.groupValues?.get(1) }

        fun AppEventDto.toIncoming() = IncomingEvent(
            serverId = id,
            sourceId = sourceId,
            sourceName = sourceName,
            kind = when (event) {
                "activated" -> EventKind.ACTIVATED
                "deactivated" -> EventKind.DEACTIVATED
                "failed" -> EventKind.FAILED
                else -> EventKind.UNKNOWN
            },
            message = message,
            label = label,
            categories = categories,
            url = url,
            createdAtMillis = runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrDefault(0L),
            origin = IncomingEvent.Origin.POLL
        )

        fun PushPayload.toIncoming(now: Long) = IncomingEvent(
            serverId = null,
            sourceId = sourceIdFromUrl(url),
            sourceName = title,
            kind = EventKind.ACTIVATED,
            message = body,
            label = null,
            categories = emptyList(),
            url = url,
            createdAtMillis = now,
            origin = IncomingEvent.Origin.PUSH
        )
    }
}
