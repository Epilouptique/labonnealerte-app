package fr.labonnealerte.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.labonnealerte.core.data.Clock
import fr.labonnealerte.core.model.EventKind
import fr.labonnealerte.core.model.IncomingEvent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * « Tester une alerte dans 30 secondes » (Reglages > Aide, APP-09) : une alerte de test realiste,
 * programmee par WorkManager pour arriver app fermee et telephone verrouille.
 *
 * WorkManager plutot qu'une alarme exacte : aucune permission « alarmes et rappels » a demander
 * (refusee par defaut depuis Android 14), le travail survit a la fermeture de l'app depuis les
 * applis recentes et a un redemarrage. Le delai est inexact : si Android est en veille profonde
 * (Doze), l'alerte peut arriver un peu plus tard, comme tout travail en arriere-plan.
 */
@Singleton
class TestAlertScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun schedule(delaySeconds: Long = DELAY_SECONDS) {
        val req = OneTimeWorkRequestBuilder<TestAlertWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        // Un seul test en attente : un nouvel appui remplace le precedent.
        WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, req)
    }

    companion object {
        const val TAG = "lba-test-alerte"
        const val DELAY_SECONDS = 30L

        /** Source reelle du catalogue, choisie car sans danger (aucune urgence simulee). */
        const val SOURCE_ID = "hausse-tarif-streaming"

        /** Evenement realiste, au format du serveur (server/sources/hausse-tarif-streaming.js). */
        fun event(now: Long, sourceName: String, label: String) = IncomingEvent(
            serverId = null,
            sourceId = SOURCE_ID,
            sourceName = sourceName,
            kind = EventKind.ACTIVATED,
            message = "💶 Changement détecté sur la page des tarifs Netflix. Vérifiez si une évolution de prix vous concerne.",
            label = label,
            categories = listOf("streaming", "consommation"),
            url = "https://labonnealerte.fr/source/$SOURCE_ID/statut",
            createdAtMillis = now,
            origin = IncomingEvent.Origin.SIMULATION
        )
    }
}

@HiltWorker
class TestAlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val ingestor: EventIngestor,
    private val clock: Clock
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        ingestor.ingest(
            listOf(TestAlertScheduler.event(clock.now(), ctx.getString(R.string.test_alerte_source), ctx.getString(R.string.test_alerte_label)))
        )
        return Result.success()
    }
}
