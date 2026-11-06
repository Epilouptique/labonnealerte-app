package fr.labonnealerte.feature.reglages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.labonnealerte.core.data.AccountRepository
import fr.labonnealerte.core.data.AppPreferences
import fr.labonnealerte.core.data.CatalogRepository
import fr.labonnealerte.core.data.NotificationHistoryRepository
import fr.labonnealerte.core.data.Settings
import fr.labonnealerte.core.model.QuietHours
import fr.labonnealerte.core.network.AccountApi
import fr.labonnealerte.core.network.LbaApi
import fr.labonnealerte.core.network.dto.QuietHoursBody
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SupportLinks(val kofi: String?, val paypal: String?)

@HiltViewModel
class ReglagesViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val account: AccountRepository,
    private val catalog: CatalogRepository,
    private val history: NotificationHistoryRepository,
    private val accountApi: AccountApi,
    private val api: LbaApi,
    private val testAlert: fr.labonnealerte.core.notifications.TestAlertScheduler,
    private val lab: fr.labonnealerte.core.data.DisplayLabStore,
    private val extras: fr.labonnealerte.core.data.AppExtrasRepository,
    private val hiddenStore: fr.labonnealerte.core.data.HiddenContentStore
) : ViewModel() {
    val settings: StateFlow<Settings> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _support = MutableStateFlow(SupportLinks(null, null))
    val support: StateFlow<SupportLinks> = _support.asStateFlow()

    private val _done = MutableStateFlow<Int?>(null)

    /** Message court apres une action (ressource de chaine). */
    val done: StateFlow<Int?> = _done.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { api.supportLinks() }.onSuccess { _support.value = SupportLinks(it.kofi, it.paypal) }
            // Heures calmes : le profil serveur fait foi au premier affichage s'il est connu.
            account.account.value?.let { a ->
                val local = prefs.current().quietHours
                val server = QuietHours(a.quietStart, a.quietEnd, !a.quietDisabled)
                if (local != server) prefs.setQuietHours(server)
            }
        }
    }

    fun setTheme(v: String) = viewModelScope.launch { prefs.setTheme(v) }
    fun setDynamic(v: Boolean) = viewModelScope.launch { prefs.setDynamicColor(v) }
    fun setFontScale(v: Float) = viewModelScope.launch { prefs.setFontScale(v) }
    fun setReduceMotion(v: Boolean) = viewModelScope.launch { prefs.setReduceMotion(v) }
    fun setUrgentBypass(v: Boolean) = viewModelScope.launch { prefs.setUrgentBypass(v) }
    fun setMaxPerHour(v: Int) = viewModelScope.launch { prefs.setMaxPerHour(v) }

    /** Heures calmes locales, recopiees sur le profil serveur (memes heures pour l'e-mail). */
    fun setQuietHours(q: QuietHours) = viewModelScope.launch {
        prefs.setQuietHours(q)
        if (account.signedIn.value && q.start != q.end) {
            runCatching { accountApi.quietHours(QuietHoursBody(disabled = !q.enabled, start = q.start, end = q.end)) }
        }
    }

    private val _channel = MutableStateFlow<fr.labonnealerte.core.model.NotifyChannel?>(null)

    /** Canal de reception du compte (APP-22) ; null : deconnecte ou pas encore lu. */
    val channel: StateFlow<fr.labonnealerte.core.model.NotifyChannel?> = _channel.asStateFlow()

    /** Auteurs et signalements masques (APP-23), reaffichables un par un. */
    val hidden: StateFlow<Set<String>> = hiddenStore.hidden.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun unhide(key: String) = viewModelScope.launch { hiddenStore.show(key) }

    fun loadChannel() = viewModelScope.launch {
        _channel.value = if (account.signedIn.value) extras.notifyChannel().getOrNull() else null
    }

    fun setChannel(c: fr.labonnealerte.core.model.NotifyChannel) = viewModelScope.launch {
        val before = _channel.value
        _channel.value = c
        extras.setNotifyChannel(c)
            .onSuccess { _channel.value = it }
            .onFailure {
                _channel.value = before
                _done.value = R.string.reglages_canal_erreur
            }
    }

    /** « Revoir le tutoriel » : l'app le reaffiche aussitot (MainActivity lit la preference). */
    fun replayTutorial() = viewModelScope.launch { prefs.setTutorialSeen(false) }

    fun clearCache() = viewModelScope.launch {
        catalog.clearCache()
        catalog.refreshSources(force = true)
        _done.value = R.string.reglages_cache_vide
    }

    /** Alerte de test realiste dans 30 s, app fermee et telephone verrouille (APP-09). */
    fun scheduleTestAlert() {
        testAlert.schedule()
        _done.value = R.string.test_alerte_programmee
    }

    fun setLabOption(o: fr.labonnealerte.core.model.DisplayLab.Option, on: Boolean) {
        viewModelScope.launch { lab.set(o, on) }
    }

    fun resetLab() {
        viewModelScope.launch { lab.reset() }
    }

    fun clearHistory() = viewModelScope.launch {
        history.clear()
        _done.value = R.string.reglages_historique_vide
    }

    fun consumeDone() {
        _done.value = null
    }
}
