package fr.labonnealerte.feature.alerte

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.labonnealerte.core.data.AccountError
import fr.labonnealerte.core.data.AccountRepository
import fr.labonnealerte.core.data.CatalogRepository
import fr.labonnealerte.core.data.FavoritesRepository
import fr.labonnealerte.core.data.SubscriptionsRepository
import fr.labonnealerte.core.model.Category
import fr.labonnealerte.core.model.Departement
import fr.labonnealerte.core.model.ParamOption
import fr.labonnealerte.core.model.ParamValidator
import fr.labonnealerte.core.model.Source
import fr.labonnealerte.core.model.SourceHistory
import fr.labonnealerte.core.model.Subscription
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HistoryState {
    data object Loading : HistoryState
    data object Error : HistoryState
    data class Ready(val history: SourceHistory) : HistoryState
}

/** Retour d'une action d'abonnement, affiche en message court. */
sealed interface SubMessage {
    data object Suivie : SubMessage
    data object Retiree : SubMessage
    data object EnPause : SubMessage
    data object Reprise : SubMessage
    data object HorsLigne : SubMessage
    data class Refus(val serverText: String?) : SubMessage
    data object Erreur : SubMessage
}

data class SubState(
    val signedIn: Boolean = false,
    val subscription: Subscription? = null,
    val profileDepartement: String? = null,
    val profileVille: String? = null,
    val departements: List<Departement> = emptyList(),
    val busy: Boolean = false,
    val issues: List<ParamValidator.Issue> = emptyList(),
    val message: SubMessage? = null
) {
    val subscribed: Boolean get() = subscription?.subscribed == true
    val muted: Boolean get() = subscription?.muted == true
}

data class AlerteUiState(
    val loading: Boolean = true,
    val source: Source? = null,
    val categories: List<Category> = emptyList(),
    val favorite: Boolean = false,
    val history: HistoryState = HistoryState.Loading,
    val notFound: Boolean = false,
    val sub: SubState = SubState()
)

@HiltViewModel
class AlerteViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val favorites: FavoritesRepository,
    private val account: AccountRepository,
    private val subscriptions: SubscriptionsRepository,
    private val prefs: fr.labonnealerte.core.data.AppPreferences,
    private val notifications: fr.labonnealerte.core.data.NotificationHistoryRepository
) : ViewModel() {
    /** Identifiant valide depuis la navigation ou un lien profond (voir [AlerteRoutes.sanitizeId]). */
    val sourceId: String = AlerteRoutes.sanitizeId(savedState.get<String>("id")).orEmpty()

    private val history = MutableStateFlow<HistoryState>(HistoryState.Loading)
    private val catalogTried = MutableStateFlow(false)
    private val subLocal = MutableStateFlow(SubState())

    private val subFlow = combine(subLocal, account.signedIn, account.account) { l, signed, acc ->
        l.copy(
            signedIn = signed,
            subscription = acc?.subscriptions?.firstOrNull { it.sourceId == sourceId },
            profileDepartement = acc?.departement ?: l.profileDepartement,
            profileVille = acc?.ville
        )
    }

    private val base =
        combine(catalog.source(sourceId), catalog.categories, favorites.favorites, history, catalogTried) { src, cats, favs, h, tried ->
            AlerteUiState(
                loading = src == null && !tried,
                source = src,
                categories = cats.filter { src != null && it.slug in src.categories },
                favorite = sourceId in favs,
                history = h,
                notFound = src == null && tried
            )
        }

    val state: StateFlow<AlerteUiState> = combine(base, subFlow) { b, s -> b.copy(sub = s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlerteUiState())

    init {
        viewModelScope.launch {
            // Ouverture par lien profond a froid : le cache peut etre vide.
            catalog.refreshSources()
            catalog.refreshCategories()
            catalogTried.value = true
        }
        viewModelScope.launch {
            account.loadCached()
            catalog.geo().onSuccess { g -> subLocal.update { it.copy(departements = g.departements) } }
            prefs.departementPrefere()?.let { d -> subLocal.update { it.copy(profileDepartement = d) } }
        }
        loadHistory()
        // Alerte ouverte = vue : ses notifications quittent le tiroir et la pastille (APP-07).
        if (sourceId.isNotEmpty()) viewModelScope.launch { notifications.markSourceRead(sourceId) }
    }

    fun loadHistory() {
        if (sourceId.isEmpty()) return
        history.value = HistoryState.Loading
        viewModelScope.launch {
            history.value = catalog.history(sourceId).fold({ HistoryState.Ready(it) }, { HistoryState.Error })
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch { favorites.setFavorite(sourceId, !state.value.favorite) }
    }

    fun setSubscribed(on: Boolean) = act(if (on) SubMessage.Suivie else SubMessage.Retiree) {
        subscriptions.setSubscribed(sourceId, on)
    }

    fun setMuted(on: Boolean) = act(if (on) SubMessage.EnPause else SubMessage.Reprise) {
        subscriptions.setMuted(sourceId, on)
    }

    /** Ajoute une instance (zone, critere…) apres validation locale. */
    fun addInstance(raw: Map<String, String>) {
        val src = state.value.source ?: return
        val v = ParamValidator.validate(src.params, raw)
        if (!v.ok) {
            subLocal.update { it.copy(issues = v.issues.ifEmpty { listOf(ParamValidator.Issue.Required(src.params.first().key)) }) }
            return
        }
        subLocal.update { it.copy(issues = emptyList()) }
        act(SubMessage.Suivie) { subscriptions.setInstance(sourceId, v.values, true).map { } }
    }

    fun removeInstance(params: Map<String, String>) {
        val src = state.value.source ?: return
        // Les nombres sont stockes en nombre cote serveur : on renvoie la meme forme.
        val typed: Map<String, Any> = params.mapValues { (k, v) ->
            if (src.params.firstOrNull { it.key == k }?.type == fr.labonnealerte.core.model.ParamType.NUMBER) v.toDoubleOrNull() ?: v else v
        }
        act(SubMessage.Retiree) { subscriptions.setInstance(sourceId, typed, false).map { } }
    }

    suspend fun lookup(q: String): List<ParamOption> =
        catalog.paramLookup(sourceId, q, subLocal.value.profileDepartement ?: account.account.value?.departement).getOrDefault(emptyList())

    fun consumeMessage() = subLocal.update { it.copy(message = null) }

    /** Fenetre systeme de permission montree : un refus donnera le bandeau de probleme (APP-06). */
    fun onPermissionAsked() {
        viewModelScope.launch { prefs.markNotificationPermissionAsked() }
    }

    private fun act(success: SubMessage, block: suspend () -> Result<Unit>) {
        subLocal.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val r = block()
            val err = r.exceptionOrNull()
            // Le nombre de personnes qui suivent vient du catalogue (calcule par le serveur) :
            // relu apres un changement pour qu'il bouge tout de suite (friction APP-03 n° 4).
            if (err == null) launch { catalog.refreshSources(force = true) }
            subLocal.update {
                it.copy(
                    busy = false,
                    message = when {
                        err == null -> success
                        err is AccountError.Offline -> SubMessage.HorsLigne
                        err is AccountError.Rejected && err.httpCode == 400 -> SubMessage.Refus(err.serverMessage)
                        else -> SubMessage.Erreur
                    }
                )
            }
        }
    }
}
