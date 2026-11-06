package fr.labonnealerte.core.push

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.labonnealerte.core.data.AppPreferences
import fr.labonnealerte.core.data.ReceptionMode
import fr.labonnealerte.core.data.session.SessionStore
import fr.labonnealerte.core.network.AccountApi
import fr.labonnealerte.core.network.ClientInfo
import fr.labonnealerte.core.network.dto.PushEndpointBody
import fr.labonnealerte.core.network.dto.PushKeysDto
import fr.labonnealerte.core.network.dto.PushSubscriptionBody
import fr.labonnealerte.core.network.dto.PushSubscriptionDto
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import org.unifiedpush.android.connector.UnifiedPush
import timber.log.Timber

/** Mode effectivement utilise (pour l'affichage et le diagnostic). */
enum class EffectiveMode { UNIFIEDPUSH, PERIODIQUE, AUCUN }

/**
 * Choisit et maintient le mode de reception :
 *  - UnifiedPush si un distributeur est installe (voie principale, instantanee) ;
 *  - sinon verification periodique (WorkManager, 15 min minimum, reseau requis, backoff).
 * La bascule est automatique (mode AUTO) ; l'utilisateur peut forcer un mode dans les reglages.
 */
@Singleton
class PushRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AccountApi,
    private val prefs: AppPreferences,
    private val session: SessionStore,
    private val client: ClientInfo
) {
    fun distributors(): List<String> = runCatching { UnifiedPush.getDistributors(context) }.getOrDefault(emptyList())

    /** A appeler au demarrage, a la connexion et quand le reglage change. */
    suspend fun ensure(): EffectiveMode {
        if (session.currentToken() == null) {
            stopPolling()
            return EffectiveMode.AUCUN
        }
        val mode = prefs.current().receptionMode
        val dist = distributors()
        val usePush = mode != ReceptionMode.PERIODIQUE && dist.isNotEmpty()
        return if (usePush) {
            registerUnifiedPush(dist)
            stopPolling()
            EffectiveMode.UNIFIEDPUSH
        } else {
            unregisterUnifiedPush()
            schedulePolling()
            EffectiveMode.PERIODIQUE
        }
    }

    private suspend fun registerUnifiedPush(dist: List<String>) {
        val vapid = runCatching { api.vapidKey().key }.getOrNull()
        if (vapid.isNullOrBlank()) {
            Timber.w("Cle VAPID indisponible : releve periodique en attendant")
            schedulePolling()
            return
        }
        // Distributeur deja choisi, sinon celui par defaut, sinon le premier installe.
        val saved = UnifiedPush.getAckDistributor(context) ?: UnifiedPush.getSavedDistributor(context)
        val chosen = saved?.takeIf { it in dist } ?: dist.first()
        UnifiedPush.saveDistributor(context, chosen)
        prefs.setDistributor(chosen)
        UnifiedPush.register(context, INSTANCE, MESSAGE_FOR_DISTRIBUTOR, vapid)
    }

    /** Nouvelle adresse fournie par le distributeur : on la declare au serveur (Web Push). */
    suspend fun onNewEndpoint(url: String, p256dh: String?, auth: String?) {
        if (p256dh == null || auth == null || !acceptableEndpoint(url, client.baseUrl)) {
            Timber.w("Adresse push inutilisable (cles absentes ou non https)")
            onFailure()
            return
        }
        val previous = prefs.currentReception().pushEndpoint
        val ok = runCatching {
            api.pushSubscribe(PushSubscriptionBody(PushSubscriptionDto(url, PushKeysDto(p256dh, auth))))
        }.isSuccess
        prefs.setPushEndpoint(url, onServer = ok)
        if (ok && previous != null && previous != url) runCatching { api.pushUnsubscribe(PushEndpointBody(previous)) }
        if (!ok) schedulePolling() else stopPolling()
    }

    suspend fun onFailure() {
        prefs.setPushEndpoint(null, onServer = false)
        schedulePolling()
    }

    suspend fun onUnregistered() {
        prefs.currentReception().pushEndpoint?.let { ep -> runCatching { api.pushUnsubscribe(PushEndpointBody(ep)) } }
        prefs.setPushEndpoint(null, onServer = false)
        if (session.currentToken() != null) schedulePolling()
    }

    /** Deconnexion : retirer l'adresse du serveur AVANT d'effacer la session. */
    suspend fun onLogout() {
        prefs.currentReception().pushEndpoint?.let { ep -> runCatching { api.pushUnsubscribe(PushEndpointBody(ep)) } }
        unregisterUnifiedPush()
        prefs.setPushEndpoint(null, onServer = false)
        stopPolling()
    }

    private fun unregisterUnifiedPush() {
        runCatching { UnifiedPush.unregister(context, INSTANCE) }
    }

    fun schedulePolling() {
        val req = PeriodicWorkRequestBuilder<PollWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(POLL_WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun stopPolling() {
        WorkManager.getInstance(context).cancelUniqueWork(POLL_WORK)
    }

    /** Releve immediate (ouverture de l'app, bouton « verifier maintenant »). */
    fun syncNow() {
        val req = OneTimeWorkRequestBuilder<PollWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SYNC_NOW_WORK, ExistingWorkPolicy.REPLACE, req)
    }

    companion object {
        const val INSTANCE = "default"
        const val MESSAGE_FOR_DISTRIBUTOR = "La Bonne Alerte"
        const val POLL_WORK = "lba-poll"
        const val SYNC_NOW_WORK = "lba-sync-now"

        /** Distributeurs proposes aux utilisateurs sans distributeur (pages F-Droid officielles). */
        const val NTFY_FDROID = "https://f-droid.org/packages/io.heckel.ntfy/"

        private const val LOOPBACK_HTTP = "http://127.0.0.1:"

        /**
         * Le serveur ne doit pousser que vers du https. Seule exception : l'app parle elle-meme
         * au serveur factice local (build debug, fichier use_fake_api) et le distributeur de
         * test tourne aussi en local (relais tools/fake-ntfy via adb reverse).
         */
        fun acceptableEndpoint(url: String, apiBaseUrl: String): Boolean =
            url.startsWith("https://") || (apiBaseUrl.startsWith(LOOPBACK_HTTP) && url.startsWith(LOOPBACK_HTTP))
    }
}

/** Releve periodique des evenements (repli sans distributeur UnifiedPush). */
@HiltWorker
class PollWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sync: EventSync
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = when (sync.sync()) {
        is SyncResult.Ok, SyncResult.NotSignedIn -> Result.success()
        SyncResult.Failed -> if (runAttemptCount < 3) Result.retry() else Result.success()
    }
}
