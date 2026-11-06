package fr.labonnealerte.feature.alerte

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.labonnealerte.core.data.AccountRepository
import fr.labonnealerte.core.data.CatalogRepository
import fr.labonnealerte.core.data.SubscriptionsRepository
import fr.labonnealerte.core.model.AccountEvent
import fr.labonnealerte.core.model.EventKind
import fr.labonnealerte.core.model.Source
import fr.labonnealerte.core.model.SourceState
import fr.labonnealerte.core.model.SplitMessage
import fr.labonnealerte.core.model.Subscription
import fr.labonnealerte.core.ui.Dates
import fr.labonnealerte.core.ui.components.EmptyState
import fr.labonnealerte.core.ui.components.StatePill
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MesAlertesItem(val subscription: Subscription, val source: Source?, val emoji: String?)

data class MesAlertesUiState(
    val signedIn: Boolean = false,
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val items: List<MesAlertesItem> = emptyList(),
    val events: List<AccountEvent> = emptyList()
)

@HiltViewModel
class MesAlertesViewModel @Inject constructor(
    private val account: AccountRepository,
    private val subscriptions: SubscriptionsRepository,
    catalog: CatalogRepository
) : ViewModel() {
    private val events = MutableStateFlow<List<AccountEvent>>(emptyList())
    private val refreshing = MutableStateFlow(false)

    val state: StateFlow<MesAlertesUiState> = combine(account.signedIn, account.account, catalog.sources, events, refreshing) {
            signed,
            acc,
            sources,
            ev,
            r
        ->
        val byId = sources.associateBy { it.id }
        MesAlertesUiState(
            signedIn = signed,
            loaded = acc != null,
            refreshing = r,
            // Les echeances (sources de taches) ont leur propre ecran.
            items = acc?.subscriptions.orEmpty().filter {
                it.subscribed &&
                    it.tasks.isEmpty() &&
                    byId[it.sourceId]?.type != fr.labonnealerte.core.model.SourceType.USER_TASK
            }.map { s ->
                MesAlertesItem(s, byId[s.sourceId], null)
            },
            events = ev
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MesAlertesUiState())

    init {
        viewModelScope.launch {
            account.loadCached()
            refresh()
        }
    }

    fun refresh() {
        if (!account.signedIn.value) return
        viewModelScope.launch {
            refreshing.value = true
            account.refresh()
            subscriptions.recentEvents().onSuccess { events.value = it }
            refreshing.value = false
        }
    }
}

@Composable
fun MesAlertesRoute(
    onOpenSource: (String) -> Unit,
    onRequestLogin: () -> Unit,
    onOpenKiosque: () -> Unit,
    historySlot: @Composable () -> Unit = {},
    /** Bandeau sous le titre (probleme de notifications detecte, APP-05). */
    headerSlot: @Composable () -> Unit = {},
    viewModel: MesAlertesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MesAlertesScreen(state, viewModel::refresh, onOpenSource, onRequestLogin, onOpenKiosque, historySlot, headerSlot)
}

@Composable
fun MesAlertesScreen(
    state: MesAlertesUiState,
    onRefresh: () -> Unit,
    onOpenSource: (String) -> Unit,
    onRequestLogin: () -> Unit,
    onOpenKiosque: () -> Unit,
    historySlot: @Composable () -> Unit,
    headerSlot: @Composable () -> Unit = {}
) {
    if (!state.signedIn) {
        EmptyState(
            stringResource(R.string.mes_titre_deconnecte),
            stringResource(R.string.mes_message_deconnecte),
            actionLabel = stringResource(R.string.sub_se_connecter),
            onAction = onRequestLogin
        )
        return
    }
    var eventsOpen by rememberSaveable { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            Modifier.widthIn(max = 840.dp).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.mes_titre),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics {
                        heading()
                    }
                )
                fr.labonnealerte.core.ui.components.HeaderDividerLine(bleed = 16.dp)
            }
            item { headerSlot() }
            if (state.loaded && state.items.isEmpty()) {
                item {
                    EmptyState(
                        stringResource(R.string.mes_vide_titre),
                        stringResource(R.string.mes_vide_message),
                        actionLabel = stringResource(R.string.mes_vers_kiosque),
                        onAction = onOpenKiosque
                    )
                }
            }
            val active = state.items.count { it.subscription.state == "active" }
            if (state.items.isNotEmpty()) {
                item {
                    Text(
                        if (active ==
                            0
                        ) {
                            androidx.compose.ui.res.pluralStringResource(R.plurals.mes_resume_calme, state.items.size, state.items.size)
                        } else {
                            pluralStringResource(R.plurals.mes_resume_actives, state.items.size, active, state.items.size)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Les alertes en cours d'abord.
            items(state.items.sortedByDescending { it.subscription.state == "active" }, key = { it.subscription.sourceId }) { item ->
                SubscriptionCard(item, onOpenSource, activeMessage(item.subscription.sourceId, state.events))
            }
            item { historySlot() }
            if (state.events.isNotEmpty()) {
                // Ligne repliee par defaut : la liste ne remplit plus l'ecran a l'arrivee (APP-08).
                item(key = "evenements") {
                    EventsToggle(count = state.events.take(MAX_RECENT_EVENTS).size, expanded = eventsOpen, onToggle = { eventsOpen = !eventsOpen })
                }
            }
            if (state.events.isNotEmpty() && eventsOpen) {
                items(state.events.take(MAX_RECENT_EVENTS)) { e ->
                    val m = SplitMessage.of(e.message)
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(e.sourceName ?: e.sourceId, style = MaterialTheme.typography.titleSmall)
                        Text(
                            (m.emoji?.let { "$it " } ?: "") + m.text.ifBlank {
                                stringResource(if (e.kind == EventKind.DEACTIVATED) R.string.evt_fin else R.string.evt_active)
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Dates.full(e.createdAt)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Message de la derniere notification d'une alerte (meme champ `message` et meme mise en forme que
 * la notification, cf. EventIngestor) ; null si le dernier evenement est une fin d'alerte. APP-13.
 */
internal fun activeMessage(sourceId: String, events: List<AccountEvent>): String? {
    val last = events.filter { it.sourceId == sourceId }.maxByOrNull { it.createdAt } ?: return null
    if (last.kind == EventKind.DEACTIVATED) return null
    val m = SplitMessage.of(last.message)
    return m.text.takeIf { it.isNotBlank() }?.let { (m.emoji?.let { e -> "$e " } ?: "") + it }
}

/** Nombre maximal d'evenements recents affiches. */
internal const val MAX_RECENT_EVENTS = 20

/** « Derniers evenements (n) », replie ou deplie ; lu comme un bouton qui deplie (TalkBack). */
@Composable
internal fun EventsToggle(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val action = stringResource(if (expanded) R.string.mes_evenements_replier else R.string.mes_evenements_deplier)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = action, onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Meme ligne que « Reglages » dans Mon compte : icone, titre, fleche (APP-15).
        Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(R.string.mes_evenements_n, count),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SubscriptionCard(item: MesAlertesItem, onOpenSource: (String) -> Unit, message: String? = null) {
    val s = item.subscription
    Card(
        onClick = { onOpenSource(s.sourceId) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        // Meme ombre que les vignettes de Rechercher (APP-13).
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.source?.name ?: s.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (s.muted) {
                    Text(
                        stringResource(R.string.sub_en_pause),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (s.instances.isNotEmpty()) {
                s.instances.forEach { i ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            i.label ?: i.params.values.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        StatePill(if (i.state == "active") SourceState.ACTIVE else SourceState.CALM)
                    }
                }
            } else {
                StatePill(if (s.state == "active") SourceState.ACTIVE else SourceState.CALM)
            }
            // Alerte en cours : le texte recu dans la notification (APP-13).
            if (s.state == "active" && message != null) {
                Text(message, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
