package fr.labonnealerte.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.labonnealerte.core.data.Clock
import fr.labonnealerte.core.data.Echeance
import fr.labonnealerte.core.data.EcheanceReminders
import fr.labonnealerte.core.model.EventKind
import fr.labonnealerte.core.model.IncomingEvent
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rappels locaux des echeances : le matin du jour J a 9 h (heure du telephone), via WorkManager
 * (inexact, aucune permission d'alarme exacte, economise la batterie). Le serveur envoie aussi
 * ses rappels par e-mail ; ceux-ci les completent sur le telephone.
 */
@Singleton
class EcheanceReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock
) : EcheanceReminders {

    override fun reschedule(items: List<Echeance>) {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(TAG)
        val zone = ZoneId.systemDefault()
        val now = clock.now()
        items.filter { !it.counter && it.nextDue != null }.forEach { e ->
            val at = e.nextDue!!.atTime(HOUR, 0).atZone(zone).toInstant().toEpochMilli()
            if (at <= now) return@forEach
            val req = OneTimeWorkRequestBuilder<EcheanceReminderWorker>()
                .setInitialDelay(at - now, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_ID to e.id, KEY_LABEL to e.label, KEY_DUE to e.nextDue.toString()))
                .addTag(TAG)
                .build()
            wm.enqueueUniqueWork("$TAG-${e.id}", ExistingWorkPolicy.REPLACE, req)
        }
    }

    companion object {
        const val TAG = "lba-echeance"
        const val KEY_ID = "id"
        const val KEY_LABEL = "label"
        const val KEY_DUE = "due"
        const val HOUR = 9
    }
}

@HiltWorker
class EcheanceReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val ingestor: EventIngestor,
    private val clock: Clock
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val label = inputData.getString(EcheanceReminderScheduler.KEY_LABEL) ?: return Result.success()
        val due = inputData.getString(EcheanceReminderScheduler.KEY_DUE)
        if (due != null && LocalDate.parse(due) != LocalDate.now()) return Result.success()
        ingestor.ingest(
            listOf(
                IncomingEvent(
                    serverId = null,
                    sourceId = null,
                    sourceName = applicationContext.getString(R.string.canal_echeances),
                    kind = EventKind.ACTIVATED,
                    message = "📅 " + applicationContext.getString(R.string.notif_echeance_texte, label),
                    // La date rend chaque rappel unique (deduplication par contenu).
                    label = due?.let { applicationContext.getString(R.string.notif_echeance_date, it) },
                    categories = emptyList(),
                    url = null,
                    createdAtMillis = clock.now(),
                    origin = IncomingEvent.Origin.ECHEANCE
                )
            )
        )
        return Result.success()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EcheanceModule {
    @Binds
    abstract fun reminders(s: EcheanceReminderScheduler): EcheanceReminders
}
