package fr.labonnealerte.feature.reglages

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.labonnealerte.core.data.AppPreferences
import fr.labonnealerte.core.data.Clock
import fr.labonnealerte.core.data.ReceptionMode
import fr.labonnealerte.core.data.ReceptionState
import fr.labonnealerte.core.data.session.SessionStore
import fr.labonnealerte.core.database.NotificationDao
import fr.labonnealerte.core.model.EventKind
import fr.labonnealerte.core.model.IncomingEvent
import fr.labonnealerte.core.model.NotificationFamily
import fr.labonnealerte.core.notifications.EventIngestor
import fr.labonnealerte.core.push.EffectiveMode
import fr.labonnealerte.core.push.PushRegistrar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiagnosticState(
    val signedIn: Boolean = false,
    val permissionGranted: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val blockedChannels: List<String> = emptyList(),
    val batteryOptimized: Boolean = false,
    val distributors: List<String> = emptyList(),
    val mode: ReceptionMode = ReceptionMode.AUTO,
    val effective: EffectiveMode = EffectiveMode.AUCUN,
    val reception: ReceptionState = ReceptionState(),
    val lastNotificationAt: Long = 0,
    val checking: Boolean = false
) {
    val allGood: Boolean
        get() = signedIn &&
            permissionGranted &&
            notificationsEnabled &&
            blockedChannels.isEmpty() &&
            (effective == EffectiveMode.UNIFIEDPUSH && reception.pushRegisteredOnServer || effective == EffectiveMode.PERIODIQUE)
}

@HiltViewModel
class DiagnosticViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val registrar: PushRegistrar,
    private val session: SessionStore,
    private val dao: NotificationDao,
    private val ingestor: EventIngestor,
    private val clock: Clock
) : ViewModel() {
    private val system = MutableStateFlow(DiagnosticState())

    val state: StateFlow<DiagnosticState> = combine(system, prefs.reception, prefs.settings, session.signedIn) { s, r, settings, signed ->
        s.copy(reception = r, mode = settings.receptionMode, signedIn = signed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticState())

    init {
        refresh()
    }

    /** Relit l'etat du systeme (a appeler au retour des reglages Android). */
    fun refresh() {
        viewModelScope.launch {
            val nm = context.getSystemService(NotificationManager::class.java)
            val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val blocked = NotificationFamily.entries.filter { it != NotificationFamily.SYSTEME }.mapNotNull { f ->
                nm.getNotificationChannel(f.channelId)?.takeIf { it.importance == NotificationManager.IMPORTANCE_NONE }?.name?.toString()
            }
            val pm = context.getSystemService(PowerManager::class.java)
            val effective = if (session.currentToken() == null) EffectiveMode.AUCUN else registrar.ensure()
            system.value = system.value.copy(
                permissionGranted = permission,
                notificationsEnabled = nm.areNotificationsEnabled(),
                blockedChannels = blocked,
                batteryOptimized = !pm.isIgnoringBatteryOptimizations(context.packageName),
                distributors = registrar.distributors(),
                effective = effective,
                lastNotificationAt = dao.latest()?.receivedAtMillis ?: 0
            )
        }
    }

    /** Fenetre systeme de permission montree : memorise pour le bandeau (APP-06), puis relit. */
    fun onPermissionAsked() {
        viewModelScope.launch {
            prefs.markNotificationPermissionAsked()
            refresh()
        }
    }

    fun setMode(m: ReceptionMode) {
        viewModelScope.launch {
            prefs.setReceptionMode(m)
            refresh()
        }
    }

    fun checkNow() {
        system.value = system.value.copy(checking = true)
        registrar.syncNow()
        viewModelScope.launch {
            kotlinx.coroutines.delay(2_500)
            system.value = system.value.copy(checking = false)
            refresh()
        }
    }

    /** Notification locale de test : verifie l'affichage sans serveur. */
    fun sendTest(title: String, text: String) {
        viewModelScope.launch {
            ingestor.ingest(
                listOf(
                    IncomingEvent(
                        serverId = null, sourceId = null, sourceName = title, kind = EventKind.ACTIVATED,
                        message = text + " (" + clock.now() + ")", label = null, categories = emptyList(), url = null,
                        createdAtMillis = clock.now(), origin = IncomingEvent.Origin.TEST
                    )
                )
            )
            refresh()
        }
    }

    /** Debug seulement : simule un evenement d'une vraie source, sans serveur. */
    fun simulate(sourceId: String, message: String, deactivate: Boolean = false) {
        viewModelScope.launch {
            ingestor.ingest(
                listOf(
                    IncomingEvent(
                        serverId = null, sourceId = sourceId, sourceName = null,
                        kind = if (deactivate) EventKind.DEACTIVATED else EventKind.ACTIVATED,
                        message = "$message · ${clock.now() % 100_000}", label = null, categories = emptyList(),
                        url = "https://labonnealerte.fr/source/$sourceId/statut", createdAtMillis = clock.now(),
                        origin = IncomingEvent.Origin.POLL
                    )
                )
            )
            refresh()
        }
    }
}
