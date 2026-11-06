package fr.labonnealerte.feature.communaute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.labonnealerte.core.data.AccountError
import fr.labonnealerte.core.data.AccountRepository
import fr.labonnealerte.core.data.CommunityRepository
import fr.labonnealerte.core.data.Report
import fr.labonnealerte.core.data.ReportType
import fr.labonnealerte.core.ui.Dates
import fr.labonnealerte.core.ui.components.EmptyState
import fr.labonnealerte.core.ui.openExternal
import fr.labonnealerte.core.ui.sendAbuseReport
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ComMsg { OK, REJOINT, HORS_LIGNE, REFUS, ERREUR, MASQUE }

data class ComState(
    val type: ReportType = ReportType.CHAT,
    val signedIn: Boolean = false,
    val reports: List<Report> = emptyList(),
    val loading: Boolean = true,
    val ville: String = "",
    val busy: Boolean = false,
    val message: ComMsg? = null,
    val serverText: String? = null
)

@HiltViewModel
class CommunauteViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: CommunityRepository,
    private val account: AccountRepository,
    private val store: fr.labonnealerte.core.data.HiddenContentStore
) : ViewModel() {
    /** Signalements masques sur ce telephone (APP-23). */
    val hidden: StateFlow<Set<String>> = store.hidden.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun hide(id: Long) {
        viewModelScope.launch {
            store.hide(fr.labonnealerte.core.model.HiddenContent.reportKey(id.toString()))
            _state.update { it.copy(message = ComMsg.MASQUE) }
        }
    }

    private val _state = MutableStateFlow(
        ComState(type = ReportType.of(savedState.get<String>("type")), signedIn = account.signedIn.value, ville = account.account.value?.ville.orEmpty())
    )
    val state: StateFlow<ComState> = _state.asStateFlow()

    init {
        load()
    }

    fun setType(t: ReportType) {
        _state.update { it.copy(type = t) }
        load()
    }

    fun load() {
        if (!account.signedIn.value) {
            _state.update { it.copy(loading = false, signedIn = false) }
            return
        }
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val r = repo.list(_state.value.type)
            _state.update { it.copy(loading = false, reports = r.getOrDefault(it.reports), message = if (r.isFailure) r.exceptionOrNull().toMsg() else null) }
        }
    }

    private fun act(block: suspend () -> Result<*>, ok: ComMsg = ComMsg.OK, onOk: () -> Unit = {}) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = block()
            _state.update {
                it.copy(
                    busy = false,
                    message = if (r.isSuccess) ok else r.exceptionOrNull().toMsg(),
                    serverText = (r.exceptionOrNull() as? AccountError.Rejected)?.serverMessage
                )
            }
            if (r.isSuccess) {
                onOk()
                load()
            }
        }
    }

    fun create(ville: String, radius: Int, description: String, link: String?, onOk: () -> Unit) =
        act({ repo.create(_state.value.type, ville, radius, description, link) }, onOk = onOk)

    fun spot(id: Long, where: String, whenText: String, onOk: () -> Unit) = act({ repo.spot(id, where, whenText) }, onOk = onOk)

    fun extend(id: Long) = act({ repo.extend(id) })

    fun resolve(id: Long) = act({ repo.resolve(id) })

    fun consumeMessage() = _state.update { it.copy(message = null, serverText = null) }

    private fun Throwable?.toMsg() = when (this) {
        is AccountError.Offline -> ComMsg.HORS_LIGNE
        is AccountError.Rejected -> if (httpCode in 400..409) ComMsg.REFUS else ComMsg.ERREUR
        else -> ComMsg.ERREUR
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommunauteRoute(onBack: () -> Unit, onRequestLogin: () -> Unit, viewModel: CommunauteViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val text = when (s.message) {
        null -> null
        ComMsg.OK -> stringResource(R.string.com_msg_ok)
        ComMsg.REJOINT -> stringResource(R.string.com_msg_rejoint)
        ComMsg.HORS_LIGNE -> stringResource(R.string.com_msg_hors_ligne)
        ComMsg.REFUS -> stringResource(R.string.com_msg_refus) + (s.serverText?.let { " $it" } ?: "")
        ComMsg.ERREUR -> stringResource(R.string.com_msg_erreur)
        ComMsg.MASQUE -> stringResource(R.string.com_msg_masque)
    }
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    LaunchedEffect(s.message) {
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }
    var showForm by rememberSaveable { mutableStateOf(false) }
    var spotFor by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            fr.labonnealerte.core.ui.components.HeaderWithDivider {
                TopAppBar(
                    title = { Text(stringResource(R.string.com_titre)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.com_retour)) }
                    },
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (s.signedIn) {
                ExtendedFloatingActionButton(onClick = {
                    showForm = true
                }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.com_signaler)) })
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(stringResource(R.string.com_intro), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.com_vie_privee),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(s.type == ReportType.CHAT, { viewModel.setType(ReportType.CHAT) }, { Text("🐱 " + stringResource(R.string.com_chat)) })
                        FilterChip(s.type == ReportType.CHIEN, { viewModel.setType(ReportType.CHIEN) }, { Text("🐶 " + stringResource(R.string.com_chien)) })
                    }
                }
                when {
                    !s.signedIn -> item {
                        EmptyState(
                            stringResource(R.string.com_titre),
                            stringResource(R.string.com_connexion),
                            actionLabel = stringResource(R.string.com_se_connecter),
                            onAction = onRequestLogin
                        )
                    }
                    s.loading && s.reports.isEmpty() -> item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    s.reports.isEmpty() -> item { EmptyState(stringResource(R.string.com_vide_titre), stringResource(R.string.com_vide_message)) }
                }
                items(
                    fr.labonnealerte.core.model.HiddenContent.filter(s.reports, hidden) {
                        fr.labonnealerte.core.model.HiddenContent.reportKey(it.id.toString())
                    },
                    key = { it.id }
                ) { r ->
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                r.type.emoji + " " + (r.commune ?: stringResource(R.string.com_commune_inconnue)),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.semantics { heading() }
                            )
                            r.radiusKm?.let { Text(stringResource(R.string.com_rayon, it), style = MaterialTheme.typography.bodySmall) }
                            r.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                            val meta = listOfNotNull(
                                if (!r.open) stringResource(R.string.com_resolu) else null,
                                if (r.spotCount > 0) stringResource(R.string.com_vu_n, r.spotCount) else null,
                                Dates.day(r.expiresAt)?.let { stringResource(R.string.com_expire, it) }
                            ).joinToString(" · ")
                            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (r.open &&
                                    !r.isAuthor &&
                                    !r.spotted
                                ) {
                                    Button(onClick = { spotFor = r.id }, enabled = !s.busy) { Text(stringResource(R.string.com_je_l_ai_vu)) }
                                }
                                if (r.open && r.isAuthor) {
                                    OutlinedButton(onClick = { viewModel.extend(r.id) }, enabled = !s.busy) { Text(stringResource(R.string.com_prolonger)) }
                                    Button(onClick = { viewModel.resolve(r.id) }, enabled = !s.busy) { Text(stringResource(R.string.com_retrouve)) }
                                }
                                r.link?.takeIf {
                                    it.startsWith("https://")
                                }?.let { url -> TextButton(onClick = { ctx.openExternal(url) }) { Text(stringResource(R.string.com_voir_lien)) } }
                                // APP-23 (contenu des utilisateurs) : signaler par e-mail (pas de route serveur) et masquer localement.
                                if (!r.isAuthor) {
                                    TextButton(onClick = { ctx.sendAbuseReport(r.id) }) { Text(stringResource(R.string.com_signaler_abus)) }
                                    TextButton(onClick = { viewModel.hide(r.id) }) { Text(stringResource(R.string.com_masquer)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    spotFor?.let { id ->
        var where by remember { mutableStateOf("") }
        var whenText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { spotFor = null },
            title = { Text(stringResource(R.string.com_je_l_ai_vu)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(where, { where = it.take(200) }, label = { Text(stringResource(R.string.com_ou)) })
                    OutlinedTextField(whenText, { whenText = it.take(200) }, label = { Text(stringResource(R.string.com_quand)) })
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.spot(id, where, whenText) { spotFor = null } },
                    enabled = where.trim().length >= 2 && whenText.trim().length >= 2 && !s.busy
                ) { Text(stringResource(R.string.com_envoyer)) }
            },
            dismissButton = { TextButton(onClick = { spotFor = null }) { Text(stringResource(fr.labonnealerte.core.ui.R.string.annuler)) } }
        )
    }

    if (showForm) {
        ModalBottomSheet(onDismissRequest = { showForm = false }) {
            var ville by rememberSaveable { mutableStateOf(s.ville) }
            var radius by rememberSaveable { mutableIntStateOf(s.type.defaultRadius) }
            var desc by rememberSaveable { mutableStateOf("") }
            var link by rememberSaveable { mutableStateOf("") }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    s.type.emoji + " " + stringResource(R.string.com_signaler),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                OutlinedTextField(ville, {
                    ville = it.take(80)
                }, label = { Text(stringResource(R.string.com_commune)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.com_rayon_choix), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.type.radiusChoices.forEach { km -> FilterChip(radius == km, { radius = km }, { Text(stringResource(R.string.com_km, km)) }) }
                }
                OutlinedTextField(desc, {
                    desc = it.take(500)
                }, label = { Text(stringResource(R.string.com_description)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(link, {
                    link = it.take(300)
                }, label = { Text(stringResource(R.string.com_lien)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { viewModel.create(ville, radius, desc, link) { showForm = false } },
                    enabled = ville.isNotBlank() && desc.trim().length >= 5 && !s.busy,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) { Text(stringResource(R.string.com_publier)) }
            }
        }
    }
}
