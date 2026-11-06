package fr.labonnealerte.feature.comptes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.labonnealerte.core.data.AccountRepository
import fr.labonnealerte.core.data.CatalogRepository
import fr.labonnealerte.core.data.PreviewResult
import fr.labonnealerte.core.data.Publication
import fr.labonnealerte.core.data.PublicationsRepository
import fr.labonnealerte.core.model.Category
import fr.labonnealerte.core.model.PubType
import fr.labonnealerte.core.model.PublicationDraft
import fr.labonnealerte.core.model.PublicationRules
import fr.labonnealerte.core.ui.Famille
import fr.labonnealerte.core.ui.components.HeaderWithDivider
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Parcours « Proposer une source » (APP-21) : ecran 1 titre + categorie, ecran 2 type + adresse +
// aperçu + presentation + soumission, puis confirmation. Memes regles que /publier du site.
// ---------------------------------------------------------------------------------------------

enum class PubStep { ESSENTIEL, DETAILS, CONFIRME }

data class PublierUiState(
    val draft: PublicationDraft = PublicationDraft(),
    val step: PubStep = PubStep.ESSENTIEL,
    val busy: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val confirmName: String? = null
)

@HiltViewModel
class PublierViewModel @Inject constructor(
    savedState: SavedStateHandle,
    account: AccountRepository,
    catalog: CatalogRepository,
    private val repo: PublicationsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(PublierUiState())
    val state: StateFlow<PublierUiState> = _state.asStateFlow()
    val signedIn: StateFlow<Boolean> = account.signedIn
    val categories: StateFlow<List<Category>> = catalog.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { catalog.refreshCategories() }
        // Modification d'un brouillon depuis « Mes publications ».
        savedState.get<String>("id")?.takeIf { ID.matches(it) }?.let { id ->
            _state.update { it.copy(loading = true) }
            viewModelScope.launch {
                repo.list().fold(
                    { list ->
                        val p = list.firstOrNull { it.id == id }
                        _state.update {
                            if (p != null) it.copy(loading = false, draft = p.toDraft()) else it.copy(loading = false, error = "Brouillon introuvable.")
                        }
                    },
                    { e -> _state.update { it.copy(loading = false, error = e.message) } }
                )
            }
        }
    }

    fun edit(f: (PublicationDraft) -> PublicationDraft) = _state.update { s ->
        val d = f(s.draft)
        // Adresse, zone ou type modifies : l'aperçu ne vaut plus (comme le site).
        val invalid = d.url != s.draft.url || d.zone != s.draft.zone || d.type != s.draft.type
        s.copy(draft = if (invalid) d.copy(previewOk = false, previewExtract = null, previewUnstable = false) else d, error = null, info = null)
    }

    fun toggleCategory(slug: String) = edit { d ->
        when {
            slug in d.categories -> d.copy(categories = d.categories - slug)
            d.categories.size >= PublicationRules.CATEGORIES_MAX -> d
            else -> d.copy(categories = d.categories + slug)
        }
    }

    fun next() {
        val err = PublicationRules.validateEssentiel(_state.value.draft)
        _state.update { if (err != null) it.copy(error = err) else it.copy(step = PubStep.DETAILS, error = null) }
    }

    fun back() = _state.update { it.copy(step = PubStep.ESSENTIEL, error = null, info = null) }

    /** « Vérifier l'adresse » : cree ou met a jour le brouillon, puis lance l'aperçu. */
    fun verify() {
        val d0 = _state.value.draft
        val err = PublicationRules.validateEssentiel(d0) ?: PublicationRules.validateDetails(d0)
        if (err != null) {
            _state.update { it.copy(error = err) }
            return
        }
        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            val saved = repo.save(d0)
            val (id, status) = saved.getOrElse { e ->
                _state.update { it.copy(busy = false, error = e.message) }
                return@launch
            }
            val synced = d0.copy(id = id, status = status, syncedUrl = d0.url.trim(), syncedZone = d0.zone.trim())
            repo.preview(id).fold(
                { r ->
                    _state.update {
                        it.copy(busy = false, draft = synced.withPreview(r), error = if (r.ok) null else r.message, info = if (r.ok) r.message else null)
                    }
                },
                { e -> _state.update { it.copy(busy = false, draft = synced.copy(previewOk = false), error = e.message) } }
            )
        }
    }

    fun submit() {
        val d = _state.value.draft
        PublicationRules.submitBlocker(d)?.let { b ->
            _state.update { it.copy(error = b) }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            // Presentation modifiee apres l'aperçu : on l'envoie avant de soumettre.
            val saved = repo.save(d)
            if (saved.isFailure) {
                _state.update { it.copy(busy = false, error = saved.exceptionOrNull()?.message) }
                return@launch
            }
            repo.submit(d.id!!).fold(
                { _ -> _state.update { PublierUiState(step = PubStep.CONFIRME, confirmName = d.title.trim()) } },
                { e -> _state.update { it.copy(busy = false, error = e.message) } }
            )
        }
    }

    private fun PublicationDraft.withPreview(r: PreviewResult) =
        copy(previewOk = r.ok, previewExtract = r.excerpt, previewUnstable = r.unstable, status = r.status ?: status)

    companion object {
        val ID = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PubScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            HeaderWithDivider {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.connexion_retour))
                        }
                    },
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0)
                )
            }
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)
    ) { p -> Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.TopCenter) { content() } }
}

/** Non connecte : on passe par l'ecran de connexion existant, puis retour au parcours. */
@Composable
private fun LoginGate(onLogin: () -> Unit) {
    Column(Modifier.widthIn(max = 640.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.pub_connexion_aide), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.pub_connexion_bouton)) }
    }
}

@Composable
fun PublierFlowRoute(onBack: () -> Unit, onLogin: () -> Unit, onMesPublications: () -> Unit, viewModel: PublierViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val cats by viewModel.categories.collectAsStateWithLifecycle()
    val back = if (s.step == PubStep.DETAILS) viewModel::back else onBack
    androidx.activity.compose.BackHandler(enabled = s.step == PubStep.DETAILS) { viewModel.back() }
    PubScaffold(stringResource(R.string.pub_titre), back) {
        when {
            !signedIn -> LoginGate(onLogin)
            s.loading -> CircularProgressIndicator(Modifier.padding(32.dp))
            s.step == PubStep.CONFIRME -> Confirmation(s.confirmName.orEmpty(), onMesPublications, onBack)
            else -> Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    stringResource(if (s.step == PubStep.ESSENTIEL) R.string.pub_etape1 else R.string.pub_etape2),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                if (s.step == PubStep.ESSENTIEL) Essentiel(s.draft, cats, viewModel) else Details(s, viewModel)
                s.info?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                s.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Essentiel(d: PublicationDraft, cats: List<Category>, vm: PublierViewModel) {
    var picking by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = d.title,
        onValueChange = { v -> vm.edit { it.copy(title = v.take(PublicationRules.TITLE_MAX)) } },
        label = { Text(stringResource(R.string.pub_titre_champ)) },
        supportingText = { Text(PublicationRules.counter(d.title, PublicationRules.TITLE_MAX)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Text(stringResource(R.string.pub_categories), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        d.categories.forEach { slug ->
            val c = cats.firstOrNull { it.slug == slug }
            InputChip(
                selected = true,
                onClick = { vm.toggleCategory(slug) },
                label = { Text(c?.let { categoryPath(it) } ?: slug) },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pub_retirer)) }
            )
        }
    }
    if (d.categories.size < PublicationRules.CATEGORIES_MAX) {
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pub_ajouter_categorie)) }
    }
    Button(onClick = vm::next, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.pub_continuer)) }
    if (picking) CategoryPicker(cats, d.categories, onToggle = vm::toggleCategory, onDismiss = { picking = false })
}

@Composable
private fun categoryPath(c: Category): String = stringResource(Famille.of(c.group).label) + " › " + c.label

@Composable
private fun CategoryPicker(cats: List<Category>, chosen: List<String>, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = cats.filter { q.isBlank() || it.label.contains(q.trim(), ignoreCase = true) }.sortedBy { it.group + it.label }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pub_categories)) },
        text = {
            Column {
                OutlinedTextField(q, { q = it.take(40) }, label = { Text(stringResource(R.string.pub_chercher)) }, singleLine = true)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.slug }) { c ->
                        val on = c.slug in chosen
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(
                                enabled = on || chosen.size < PublicationRules.CATEGORIES_MAX,
                                role = Role.Checkbox
                            ) { onToggle(c.slug) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(categoryPath(c), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pub_ok)) } }
    )
}

@Composable
private fun Details(s: PublierUiState, vm: PublierViewModel) {
    val d = s.draft
    Text(stringResource(R.string.pub_type), style = MaterialTheme.typography.titleSmall)
    PubType.entries.forEach { t ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = d.type == t, role = Role.RadioButton) { vm.edit { it.copy(type = t) } },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = d.type == t, onClick = null)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(t.label, style = MaterialTheme.typography.bodyLarge)
                Text(t.example, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    OutlinedTextField(
        value = d.url,
        onValueChange = { v -> vm.edit { it.copy(url = v.take(300)) } },
        label = { Text(stringResource(R.string.pub_adresse)) },
        placeholder = { Text("https://…") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    if (d.type == PubType.PAGE) {
        OutlinedTextField(
            value = d.zone,
            onValueChange = { v -> vm.edit { it.copy(zone = v.take(64)) } },
            label = { Text(stringResource(R.string.pub_zone)) },
            supportingText = { Text(stringResource(R.string.pub_zone_aide)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
    OutlinedButton(onClick = vm::verify, enabled = !s.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.pub_verifier))
    }
    if (s.busy) CircularProgressIndicator()
    if (d.previewCurrent) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.pub_apercu_ok), style = MaterialTheme.typography.titleSmall)
                d.previewExtract?.takeIf { it.isNotBlank() }?.let { Text(it.take(280), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    Text(stringResource(R.string.pub_presentation), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = d.description,
        onValueChange = { v -> vm.edit { it.copy(description = v.take(PublicationRules.DESC_SHORT_MAX)) } },
        label = { Text(stringResource(R.string.pub_desc_courte)) },
        supportingText = { Text(PublicationRules.counter(d.description, PublicationRules.DESC_SHORT_MAX)) },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = d.descriptionLong,
        onValueChange = { v -> vm.edit { it.copy(descriptionLong = v.take(PublicationRules.DESC_LONG_MAX)) } },
        label = { Text(stringResource(R.string.pub_desc_longue)) },
        supportingText = { Text(PublicationRules.counter(d.descriptionLong, PublicationRules.DESC_LONG_MAX)) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth()
    )
    val blocker = PublicationRules.submitBlocker(d)
    Button(onClick = vm::submit, enabled = blocker == null && !s.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.pub_soumettre))
    }
    blocker?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun Confirmation(name: String, onMesPublications: () -> Unit, onDone: () -> Unit) {
    Column(Modifier.widthIn(max = 640.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.pub_merci_titre), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.pub_merci_texte, name), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onMesPublications, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.pub_mes_publications)) }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pub_terminer)) }
    }
}

// ---------------------------------------------------------------------------------------------
// « Mes publications » : brouillons et sources soumises, statut en clair, modifier / supprimer.
// ---------------------------------------------------------------------------------------------

data class MesPublicationsUiState(val loading: Boolean = true, val items: List<Publication> = emptyList(), val error: String? = null, val info: String? = null)

@HiltViewModel
class MesPublicationsViewModel @Inject constructor(account: AccountRepository, private val repo: PublicationsRepository) : ViewModel() {
    private val _state = MutableStateFlow(MesPublicationsUiState())
    val state: StateFlow<MesPublicationsUiState> = _state.asStateFlow()
    val signedIn: StateFlow<Boolean> = account.signedIn

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.list().fold(
                { l -> _state.update { it.copy(loading = false, items = l) } },
                { e -> _state.update { it.copy(loading = false, error = e.message) } }
            )
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repo.delete(id).fold(
                { m -> _state.update { s -> s.copy(items = s.items.filterNot { it.id == id }, info = m ?: "Brouillon supprimé.") } },
                { e -> _state.update { it.copy(error = e.message) } }
            )
        }
    }
}

@Composable
fun MesPublicationsRoute(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
    viewModel: MesPublicationsViewModel = hiltViewModel()
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleResumeEffect(signedIn) {
        if (signedIn) viewModel.load()
        onPauseOrDispose { }
    }
    var toDelete by remember { mutableStateOf<Publication?>(null) }
    PubScaffold(stringResource(R.string.pub_mes_publications), onBack) {
        when {
            !signedIn -> LoginGate(onLogin)
            s.loading -> CircularProgressIndicator(Modifier.padding(32.dp))
            else -> LazyColumn(
                Modifier.widthIn(max = 840.dp).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                s.error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
                s.info?.let { m -> item { Text(m, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                if (s.items.isEmpty() && s.error == null) {
                    item { Text(stringResource(R.string.pub_aucune), style = MaterialTheme.typography.bodyLarge) }
                }
                items(s.items, key = { it.id }) { p -> PublicationCard(p, onEdit = { onEdit(p.id) }, onDelete = { toDelete = p }) }
                item {
                    Button(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.pub_proposer)) }
                }
            }
        }
    }
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.pub_supprimer_titre)) },
            text = { Text(p.name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(p.id)
                    toDelete = null
                }) { Text(stringResource(R.string.pub_supprimer)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(fr.labonnealerte.core.ui.R.string.annuler)) } }
        )
    }
}

@Composable
private fun PublicationCard(p: Publication, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium)
            Text(p.statusLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            p.url?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            if (p.editable) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.pub_modifier)) }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.pub_supprimer)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Signaler une source communautaire (motif obligatoire), depuis sa fiche.
// ---------------------------------------------------------------------------------------------

data class SignalerUiState(val reason: String? = null, val comment: String = "", val busy: Boolean = false, val done: String? = null, val error: String? = null)

@HiltViewModel
class SignalerSourceViewModel @Inject constructor(
    savedState: SavedStateHandle,
    account: AccountRepository,
    private val repo: PublicationsRepository
) : ViewModel() {
    private val sourceId: String? = savedState.get<String>("id")?.takeIf { PublierViewModel.ID.matches(it) }
    private val _state = MutableStateFlow(SignalerUiState())
    val state: StateFlow<SignalerUiState> = _state.asStateFlow()
    val signedIn: StateFlow<Boolean> = account.signedIn

    fun setReason(r: String) = _state.update { it.copy(reason = r, error = null) }
    fun setComment(c: String) = _state.update { it.copy(comment = c.take(COMMENT_MAX)) }

    fun send() {
        val s = _state.value
        val id = sourceId ?: return
        val reason = s.reason ?: run {
            _state.update { it.copy(error = "Choisissez un motif.") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            repo.report(id, reason, s.comment).fold(
                { m -> _state.update { it.copy(busy = false, done = m ?: "Signalement enregistré. Merci.") } },
                { e -> _state.update { it.copy(busy = false, error = e.message) } }
            )
        }
    }

    companion object {
        const val COMMENT_MAX = 500
    }
}

@Composable
fun SignalerSourceRoute(onBack: () -> Unit, onLogin: () -> Unit, viewModel: SignalerSourceViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    PubScaffold(stringResource(R.string.pub_signaler_titre), onBack) {
        when {
            !signedIn -> LoginGate(onLogin)
            s.done != null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(s.done!!, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onBack) { Text(stringResource(R.string.pub_terminer)) }
            }
            else -> Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.pub_signaler_motif), style = MaterialTheme.typography.titleSmall)
                PublicationRules.REPORT_REASONS.forEach { (v, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = s.reason == v, role = Role.RadioButton) { viewModel.setReason(v) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = s.reason == v, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
                OutlinedTextField(
                    value = s.comment,
                    onValueChange = viewModel::setComment,
                    label = { Text(stringResource(R.string.pub_signaler_commentaire)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = viewModel::send, enabled = s.reason != null && !s.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.pub_signaler_envoyer))
                }
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
