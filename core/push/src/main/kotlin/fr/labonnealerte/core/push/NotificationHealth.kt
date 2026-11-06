package fr.labonnealerte.core.push

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.labonnealerte.core.data.AppPreferences
import fr.labonnealerte.core.data.Clock
import fr.labonnealerte.core.data.ReceptionState
import fr.labonnealerte.core.model.NotificationFamily
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cause detectee qui empeche les notifications d'arriver (APP-05, decision E). Une seule cause
 * est affichee : la premiere de cet ordre, car elle masque les suivantes.
 */
enum class NotificationProblem {
    /** Permission refusee, ou notifications de l'app coupees dans Android. */
    PERMISSION,

    /** Au moins une categorie de notifications coupee. */
    CANAL_COUPE,

    /** Android restreint l'app en arriere-plan (economie de batterie). */
    BATTERIE,

    /** Pas de distributeur push et la releve periodique ne passe plus. */
    RELEVE_EN_RETARD,

    /** Push actif, mais des evenements ont ete trouves sans etre jamais arrives par push. */
    PUSH_MANQUE
}

/** Etat du systeme et de la reception, lu une fois ; [NotificationHealth.diagnose] est pur. */
data class HealthInputs(
    val permissionGranted: Boolean,
    /** La fenetre systeme de permission a deja ete montree (refus possible). */
    val permissionAsked: Boolean = true,
    val notificationsEnabled: Boolean,
    val blockedChannels: Int,
    val backgroundRestricted: Boolean,
    val distributors: Int,
    val reception: ReceptionState,
    val now: Long
)

@Singleton
class NotificationHealth @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val registrar: PushRegistrar,
    private val clock: Clock
) {
    /** Lit l'etat courant. Aucun effet de bord (pas d'enregistrement push, pas de releve). */
    suspend fun read(): HealthInputs {
        val nm = context.getSystemService(NotificationManager::class.java)
        val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val blocked = NotificationFamily.entries.filter { it != NotificationFamily.SYSTEME }.count { f ->
            nm.getNotificationChannel(f.channelId)?.importance == NotificationManager.IMPORTANCE_NONE
        }
        val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
        return HealthInputs(
            permissionGranted = permission,
            permissionAsked = prefs.notificationPermissionAsked(),
            notificationsEnabled = nm.areNotificationsEnabled(),
            blockedChannels = blocked,
            backgroundRestricted = restricted,
            distributors = registrar.distributors().size,
            reception = prefs.currentReception(),
            now = clock.now()
        )
    }

    companion object {
        /** Releve periodique toutes les 15 min : au-dela de 2 h sans releve reussie, elle est en retard. */
        const val POLL_LATE_MS = 2 * 60 * 60_000L

        /**
         * Permission jamais demandee (nouveau telephone, abonnements deja faits ailleurs) : pas
         * d'erreur, une invitation positive qui ouvre la fenetre systeme (APP-06, point 4).
         */
        fun needsInvitation(i: HealthInputs): Boolean = !i.permissionGranted && !i.permissionAsked

        fun diagnose(i: HealthInputs): NotificationProblem? {
            if (needsInvitation(i)) return null
            val r = i.reception
            // Retard mesure depuis la derniere releve REUSSIE (des echecs repetes comptent). Aucune
            // reussite encore enregistree (installation fraiche, ou mise a jour depuis une version qui
            // ne la notait pas) : on se fie a la derniere tentative, pour ne pas alarmer a tort.
            val lastOk = if (r.lastPollSuccessAtMillis > 0) r.lastPollSuccessAtMillis else r.lastPollAtMillis
            val pollLate = r.lastPollAtMillis > 0 && i.now - lastOk > POLL_LATE_MS
            return when {
                !i.permissionGranted || !i.notificationsEnabled -> NotificationProblem.PERMISSION
                i.blockedChannels > 0 -> NotificationProblem.CANAL_COUPE
                i.backgroundRestricted -> NotificationProblem.BATTERIE
                !r.pushRegisteredOnServer && i.distributors == 0 && pollLate -> NotificationProblem.RELEVE_EN_RETARD
                r.pushRegisteredOnServer && r.missedPushAtMillis > r.lastPushAtMillis -> NotificationProblem.PUSH_MANQUE
                else -> null
            }
        }
    }
}
