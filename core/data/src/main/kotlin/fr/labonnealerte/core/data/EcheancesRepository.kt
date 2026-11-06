package fr.labonnealerte.core.data

import fr.labonnealerte.core.model.UserTask
import fr.labonnealerte.core.network.AccountApi
import fr.labonnealerte.core.network.dto.CreateTaskBody
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException

enum class Periodicite(val api: String) { JOUR("day"), SEMAINE("week"), MOIS("month"), AN("year") }

/** Echeance avec sa date (premiers 10 caracteres de next_due : AAAA-MM-JJ). */
data class Echeance(val id: Long, val label: String, val nextDue: LocalDate?, val counter: Boolean) {
    fun daysLeft(today: LocalDate): Long? = nextDue?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) }
}

/** Planificateur de rappels locaux, fourni par core:notifications (inversion de dependance). */
interface EcheanceReminders {
    fun reschedule(items: List<Echeance>)
}

/**
 * Echeances (taches a echeance glissante). Lecture via GET /api/my-alerts (sources[].tasks),
 * ecritures via /api/user-tasks. Coherence : le compte est relu apres chaque action.
 */
@Singleton
class EcheancesRepository @Inject constructor(
    private val api: AccountApi,
    private val account: AccountRepository,
    private val reminders: EcheanceReminders
) {
    val items: Flow<List<Echeance>> = account.account.map { a ->
        a?.subscriptions.orEmpty().flatMap { it.tasks }.distinctBy { it.id }.map { it.toEcheance() }.sortedBy { it.nextDue ?: LocalDate.MAX }
    }

    suspend fun refresh(): Result<Unit> = account.refresh().map { a ->
        reminders.reschedule(a.subscriptions.flatMap { it.tasks }.distinctBy { it.id }.map { it.toEcheance() })
    }

    suspend fun create(label: String, anchor: LocalDate, value: Int, unit: Periodicite, announceDays: Int?): Result<Unit> = call {
        api.createTask(CreateTaskBody(label.trim(), anchor.toString(), value, unit.api, announceDays))
    }

    suspend fun confirm(id: Long): Result<Unit> = call { api.confirmTask(id) }

    suspend fun delete(id: Long): Result<Unit> = call { api.deleteTask(id) }

    private suspend fun call(block: suspend () -> Any?): Result<Unit> = try {
        block()
        refresh()
        Result.success(Unit)
    } catch (e: IOException) {
        Result.failure(AccountError.Offline)
    } catch (e: kotlinx.serialization.SerializationException) {
        Result.failure(AccountError.Rejected(0, null))
    } catch (e: HttpException) {
        val msg = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            ?.let { Regex("\"error\"\\s*:\\s*\"([^\"]{1,200})\"").find(it)?.groupValues?.get(1) }
        Result.failure(AccountError.Rejected(e.code(), msg))
    }
}

internal fun UserTask.toEcheance() = Echeance(
    id = id,
    label = label,
    nextDue = nextDue?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    counter = trackingMode != null && trackingMode != "time"
)
