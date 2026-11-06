package fr.labonnealerte.feature.alerte

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.labonnealerte.core.data.NotificationHistoryRepository
import fr.labonnealerte.core.data.NotificationRecord
import fr.labonnealerte.core.model.NotificationFamily
import fr.labonnealerte.core.ui.components.EmptyState
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoriqueFilter(val unreadOnly: Boolean = false, val channel: String? = null)

data class HistoriqueUiState(
    val items: List<NotificationRecord> = emptyList(),
    val channels: List<String> = emptyList(),
    val filter: HistoriqueFilter = HistoriqueFilter(),
    val unread: Int = 0
)

@HiltViewModel
class HistoriqueViewModel @Inject constructor(private val repo: NotificationHistoryRepository) : ViewModel() {
    private val filter = MutableStateFlow(HistoriqueFilter())

    val state: StateFlow<HistoriqueUiState> = combine(repo.records, repo.unreadCount, filter) { all, unread, f ->
        HistoriqueUiState(
            items = all.filter { (!f.unreadOnly || !it.read) && (f.channel == null || it.channel == f.channel) },
            channels = all.map { it.channel }.distinct(),
            filter = f,
            unread = unread
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoriqueUiState())

    init {
        viewModelScope.launch { repo.purgeOld() }
    }

    fun toggleUnread() = filter.update { it.copy(unreadOnly = !it.unreadOnly) }
    fun setChannel(c: String?) = filter.update { it.copy(channel = if (it.channel == c) null else c) }
    fun markAllRead() = viewModelScope.launch { repo.markAllRead() }
    fun open(r: NotificationRecord) = viewModelScope.launch { repo.markRead(r.id) }
}

@Composable
fun channelLabel(channel: String): String = when (channel) {
    NotificationFamily.URGENCES.channelId -> stringResource(R.string.hist_urgences)
    NotificationFamily.METEO_ENVIRONNEMENT.channelId -> stringResource(R.string.hist_meteo)
    NotificationFamily.VIE_LOCALE.channelId -> stringResource(R.string.hist_vie_locale)
    NotificationFamily.SERVICES.channelId -> stringResource(R.string.hist_services)
    NotificationFamily.BONS_PLANS.channelId, "bons_plans_culture" -> stringResource(R.string.hist_bons_plans) // ancien id (avant APP-04)
    NotificationFamily.COMMUNAUTE.channelId -> stringResource(R.string.hist_communaute)
    NotificationFamily.ECHEANCES.channelId -> stringResource(R.string.hist_echeances)
    else -> stringResource(R.string.hist_autres)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoriqueRoute(onBack: () -> Unit, onOpenSource: (String) -> Unit, viewModel: HistoriqueViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            fr.labonnealerte.core.ui.components.HeaderWithDivider {
                TopAppBar(
                    title = { Text(stringResource(R.string.hist_titre)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.retour)) }
                    },
                    actions = {
                        if (s.unread > 0) {
                            IconButton(onClick = {
                                viewModel.markAllRead()
                            }) { Icon(Icons.Filled.DoneAll, stringResource(R.string.hist_tout_lu)) }
                        }
                    },
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0)
                )
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(s.filter.unreadOnly, viewModel::toggleUnread, {
                                Text(stringResource(R.string.hist_non_lues, s.unread))
                            })
                        }
                        items(s.channels) { c -> FilterChip(s.filter.channel == c, { viewModel.setChannel(c) }, { Text(channelLabel(c)) }) }
                    }
                }
                if (s.items.isEmpty()) {
                    item { EmptyState(stringResource(R.string.hist_vide_titre), stringResource(R.string.hist_vide_message)) }
                }
                items(s.items, key = { it.id }) { r ->
                    val unreadCd = stringResource(R.string.hist_non_lue)
                    Card(
                        onClick = {
                            viewModel.open(r)
                            r.sourceId?.let(onOpenSource)
                        },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (r.read) {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            if (!r.read) {
                                Box(
                                    Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                                        .semantics { contentDescription = unreadCd }
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    r.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (r.read) FontWeight.Normal else FontWeight.Bold
                                )
                                Text(r.text, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    channelLabel(r.channel) + " · " +
                                        DateFormat.getDateTimeInstance(
                                            DateFormat.MEDIUM,
                                            DateFormat.SHORT
                                        ).format(Date(r.receivedAtMillis)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
