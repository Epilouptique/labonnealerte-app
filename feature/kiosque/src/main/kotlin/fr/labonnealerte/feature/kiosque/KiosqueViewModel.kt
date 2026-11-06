package fr.labonnealerte.feature.kiosque

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.labonnealerte.core.data.AccountError
import fr.labonnealerte.core.data.AccountRepository
import fr.labonnealerte.core.data.CatalogRepository
import fr.labonnealerte.core.data.ConnectivityMonitor
import fr.labonnealerte.core.data.FavoritesRepository
import fr.labonnealerte.core.data.RefreshResult
import fr.labonnealerte.core.data.SubscriptionsRepository
import fr.labonnealerte.core.model.Category
import fr.labonnealerte.core.model.Kiosque
import fr.labonnealerte.core.model.KiosqueFilter
import fr.labonnealerte.core.model.KiosqueMode
import fr.labonnealerte.core.model.KiosqueSort
import fr.labonnealerte.core.model.LePointActive
import fr.labonnealerte.core.model.ParamType
import fr.labonnealerte.core.model.Source
import fr.labonnealerte.core.model.Subscription
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Retour d'un abonnement fait depuis le kiosque (APP-10), affiche en snackbar. */
sealed interface FollowMessage {
    data class Suivie(val name: String) : FollowMessage

    /** Desabonnement : [undo] remet exactement l'abonnement precedent (instances, pause). */
    data class Retiree(val name: String, val undo: Subscription) : FollowMessage
    data object Connexion : FollowMessage
    data object HorsLigne : FollowMessage
    data class Refus(val serverText: String?) : FollowMessage
    data object Erreur : FollowMessage
}

data class CategoryChip(val slug: String, val label: String, val count: Int, val group: String?)

data class KiosqueUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val sources: List<Source> = emptyList(),
    val totalCount: Int = 0,
    val filter: KiosqueFilter = KiosqueFilter(),
    val topCategories: List<CategoryChip> = emptyList(),
    val allCategories: List<CategoryChip> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val subscribed: Set<String> = emptySet(),
    val enCeMoment: List<LePointActive> = emptyList(),
    val offline: Boolean = false,
    val signedIn: Boolean = false,
    /** Abonnements en cours d'envoi : affiches tout de suite, avant la reponse du serveur. */
    val pendingFollow: Map<String, Boolean> = emptyMap(),
    val message: FollowMessage? = null,
    /** Aucune donnee en cache et echec reseau : ecran d'erreur plein. */
    val error: Boolean = false
) {
    val hasCache: Boolean get() = totalCount > 0

    fun isFollowed(id: String): Boolean = pendingFollow[id] ?: (id in subscribed)
}

@HiltViewModel
class KiosqueViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val favoritesRepo: FavoritesRepository,
    private val account: AccountRepository,
    private val subscriptions: SubscriptionsRepository,
    connectivity: ConnectivityMonitor
) : ViewModel() {

    private val filter = MutableStateFlow(KiosqueFilter())
    private val status = MutableStateFlow(Status())
    private val enCeMoment = MutableStateFlow<List<LePointActive>>(emptyList())
    private val follow = MutableStateFlow(FollowState())

    /**
     * [frozen] : etat d'abonnement AVANT un changement fait dans le kiosque. L'ordre (alertes
     * suivies en fin de liste, APP-08) l'utilise jusqu'au prochain rafraichissement : une vignette
     * qu'on vient de suivre ne saute pas en bas de la liste sous le doigt (APP-10).
     */
    private data class FollowState(
        val pending: Map<String, Boolean> = emptyMap(),
        val message: FollowMessage? = null,
        val frozen: Map<String, Boolean> = emptyMap()
    )

    private data class Status(val refreshing: Boolean = false, val lastResult: RefreshResult? = null)

    private data class Catalog(
        val sources: List<Source>,
        val categories: List<Category>,
        val favorites: Set<String>,
        val subscribed: Set<String>
    )

    private val catalogFlow =
        combine(catalog.sources, catalog.categories, favoritesRepo.favorites, account.subscribedIds) { s, c, f, sub ->
            Catalog(s, c, f, sub)
        }

    private val extras = combine(enCeMoment, connectivity.isOnline, account.signedIn, follow) { p, o, s, fo -> Extras(p, o, s, fo) }

    private data class Extras(val point: List<LePointActive>, val online: Boolean, val signedIn: Boolean, val follow: FollowState)

    val state: StateFlow<KiosqueUiState> = combine(catalogFlow, filter, status, extras) {
            cat,
            f,
            st,
            ex
        ->
        val point = ex.point
        val online = ex.online
        val labels = cat.categories.associate { it.slug to it.label }
        val groups = cat.categories.associate { it.slug to it.group }
        val freq = Kiosque.categoriesByFrequency(cat.sources) { labels[it] ?: it }
            .map { (slug, n) -> CategoryChip(slug, labels[slug] ?: slug, n, groups[slug]) }
        val result = Kiosque.apply(
            cat.sources,
            f,
            favorites = cat.favorites,
            subscribed = orderingSet(cat.subscribed, ex.follow.frozen),
            groupOf = { groups[it] },
            categoryLabel = { labels[it] }
        )
        val failedWithoutCache = cat.sources.isEmpty() &&
            (st.lastResult is RefreshResult.Offline || st.lastResult is RefreshResult.Failed)
        KiosqueUiState(
            loading = cat.sources.isEmpty() && st.lastResult == null,
            refreshing = st.refreshing,
            sources = result,
            totalCount = cat.sources.size,
            filter = f,
            topCategories = freq.take(TOP_CATEGORIES),
            allCategories = freq,
            favorites = cat.favorites,
            subscribed = cat.subscribed,
            enCeMoment = point,
            offline = !online || st.lastResult is RefreshResult.Offline,
            signedIn = ex.signedIn,
            pendingFollow = ex.follow.pending,
            message = ex.follow.message,
            error = failedWithoutCache
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KiosqueUiState())

    init {
        refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        follow.update { it.copy(frozen = emptyMap()) } // rafraichir : l'ordre normal reprend
        viewModelScope.launch {
            status.update { it.copy(refreshing = force) }
            catalog.refreshCategories()
            val r = catalog.refreshSources(force)
            status.value = Status(refreshing = false, lastResult = r)
            catalog.lePoint().onSuccess { p -> enCeMoment.value = p.active }
        }
    }

    fun onQuery(q: String) = filter.update { it.copy(query = q.take(MAX_QUERY)) }

    fun onMode(mode: KiosqueMode) = filter.update { it.copy(mode = mode, category = null, group = null) }

    fun onCategory(slug: String?) = filter.update { it.copy(category = slug, group = null, mode = KiosqueMode.TOUT) }

    fun onGroup(group: String?) = filter.update { it.copy(group = group, category = null, mode = KiosqueMode.TOUT) }

    fun onOnlyActive(on: Boolean) = filter.update { it.copy(onlyActive = on) }

    fun onSort(sort: KiosqueSort) = filter.update { it.copy(sort = sort) }

    fun clearFilters() = filter.update { KiosqueFilter(query = it.query) }

    /**
     * Suivre une source sans parametre depuis le kiosque (bouton, balayage a droite, action
     * TalkBack). Une source a parametres ouvre sa fiche, ou se trouve le choix (fait par l'ecran).
     */
    fun follow(source: Source) {
        if (!account.signedIn.value) return say(FollowMessage.Connexion)
        send(source.id, true, FollowMessage.Suivie(source.name)) { subscriptions.setSubscribed(source.id, true) }
    }

    /** Ne plus suivre (bouton « Suivi », balayage a gauche) ; « Annuler » remet l'abonnement. */
    fun unfollow(source: Source) {
        if (!account.signedIn.value) return say(FollowMessage.Connexion)
        val before = account.account.value?.subscriptions?.firstOrNull { it.sourceId == source.id && it.subscribed }
            ?: Subscription(source.id, source.name, null, true, false, emptyList(), emptyList())
        send(source.id, false, FollowMessage.Retiree(source.name, before)) { subscriptions.setSubscribed(source.id, false) }
    }

    fun undo(previous: Subscription) {
        val src = state.value.sources.firstOrNull { it.id == previous.sourceId }
        send(previous.sourceId, true, null) {
            val r = if (previous.instances.isEmpty()) {
                subscriptions.setSubscribed(previous.sourceId, true)
            } else {
                previous.instances.map { i -> subscriptions.setInstance(previous.sourceId, typed(src, i.params), true) }
                    .firstOrNull { it.isFailure }?.map { } ?: Result.success(Unit)
            }
            if (r.isSuccess && previous.muted) subscriptions.setMuted(previous.sourceId, true) else r
        }
    }

    fun consumeMessage() = follow.update { it.copy(message = null) }

    private fun say(m: FollowMessage) = follow.update { it.copy(message = m) }

    private fun send(id: String, on: Boolean, success: FollowMessage?, block: suspend () -> Result<Unit>) {
        follow.update {
            val before = it.frozen[id] ?: (id in state.value.subscribed)
            it.copy(pending = it.pending + (id to on), message = null, frozen = it.frozen + (id to before))
        }
        viewModelScope.launch {
            val err = block().exceptionOrNull()
            if (err == null) launch { catalog.refreshSources(force = true) }
            follow.update {
                it.copy(
                    pending = it.pending - id,
                    message = when {
                        err == null -> success
                        err is AccountError.Offline -> FollowMessage.HorsLigne
                        err is AccountError.Rejected && err.httpCode == HTTP_BAD_REQUEST -> FollowMessage.Refus(err.serverMessage)
                        else -> FollowMessage.Erreur
                    }
                )
            }
        }
    }

    /** Les nombres sont stockes en nombre cote serveur : on renvoie la meme forme (comme la fiche). */
    private fun typed(src: Source?, params: Map<String, String>): Map<String, Any> = params.mapValues { (k, v) ->
        if (src?.params?.firstOrNull { it.key == k }?.type == ParamType.NUMBER) v.toDoubleOrNull() ?: v else v
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { favoritesRepo.setFavorite(id, id !in state.value.favorites) }
    }

    companion object {
        /** Abonnements servant a l'ordre : l'etat d'avant pour les alertes changees ici. */
        fun orderingSet(subscribed: Set<String>, frozen: Map<String, Boolean>): Set<String> =
            subscribed.filterNot { it in frozen }.toSet() + frozen.filterValues { it }.keys

        const val TOP_CATEGORIES = 5
        const val MAX_QUERY = 80
        private const val HTTP_BAD_REQUEST = 400
    }
}
