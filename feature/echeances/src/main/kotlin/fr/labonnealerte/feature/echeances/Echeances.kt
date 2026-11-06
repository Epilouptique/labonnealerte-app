package fr.labonnealerte.feature.echeances

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.labonnealerte.core.data.AccountError
import fr.labonnealerte.core.data.AccountRepository
import fr.labonnealerte.core.data.Echeance
import fr.labonnealerte.core.data.EcheancesRepository
import fr.labonnealerte.core.data.Periodicite
import fr.labonnealerte.core.ui.components.EmptyState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EchMsg { OK, HORS_LIGNE, REFUS, ERREUR }

data class EchState(
    val signedIn: Boolean = false,
    val items: List<Echeance> = emptyList(),
    val busy: Boolean = false,
    val message: EchMsg? = null,
    val serverText: String? = null
)

@HiltViewModel
class EcheancesViewModel @Inject constructor(private val repo: EcheancesRepository, account: AccountRepository) : ViewModel() {
    private val local = MutableStateFlow(EchState())
    val state: StateFlow<EchState> = combine(local, repo.items, account.signedIn) { l, items, signed -> l.copy(items = items, signedIn = signed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EchState())

    init {
        viewModelScope.launch { repo.refresh() }
    }

    private fun act(block: suspend () -> Result<Unit>, onOk: () -> Unit = {}) {
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = block()
            val e = r.exceptionOrNull()
            local.update {
                it.copy(
                    busy = false,
                    message = when {
                        e == null -> EchMsg.OK
                        e is AccountError.Offline -> EchMsg.HORS_LIGNE
                        e is AccountError.Rejected && e.httpCode in 400..410 -> EchMsg.REFUS
                        else -> EchMsg.ERREUR
                    },
                    serverText = (e as? AccountError.Rejected)?.serverMessage
                )
            }
            if (e == null) onOk()
        }
    }

    fun confirm(id: Long) = act({ repo.confirm(id) })
    fun delete(id: Long) = act({ repo.delete(id) })
    fun create(label: String, anchor: LocalDate, value: Int, unit: Periodicite, announce: Int, onOk: () -> Unit) =
        act({ repo.create(label, anchor, value, unit, announce) }, onOk)

    fun consumeMessage() = local.update { it.copy(message = null, serverText = null) }
}

@Composable
private fun dueText(e: Echeance): String {
    val d = e.daysLeft(LocalDate.now()) ?: return stringResource(R.string.ech_compteur)
    return when {
        e.counter -> stringResource(R.string.ech_compteur)
        d < 0 -> pluralStringResource(R.plurals.ech_en_retard, -d.toInt(), -d.toInt())
        d == 0L -> stringResource(R.string.ech_aujourdhui)
        d == 1L -> stringResource(R.string.ech_demain)
        else -> pluralStringResource(R.plurals.ech_dans, d.toInt(), d.toInt(), fr.labonnealerte.core.ui.Dates.day(e.nextDue!!))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EcheancesRoute(onBack: () -> Unit, onRequestLogin: () -> Unit, viewModel: EcheancesViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val text = when (s.message) {
        null -> null
        EchMsg.OK -> stringResource(R.string.ech_msg_ok)
        EchMsg.HORS_LIGNE -> stringResource(R.string.ech_msg_hors_ligne)
        EchMsg.REFUS -> stringResource(R.string.ech_msg_refus) + (s.serverText?.let { " $it" } ?: "")
        EchMsg.ERREUR -> stringResource(R.string.ech_msg_erreur)
    }
    LaunchedEffect(s.message) {
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }
    var showForm by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Echeance?>(null) }

    Scaffold(
        topBar = {
            fr.labonnealerte.core.ui.components.HeaderWithDivider {
                TopAppBar(
                    title = { Text(stringResource(R.string.ech_titre)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ech_retour)) }
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
                }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.ech_ajouter)) })
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Text(stringResource(R.string.ech_intro), style = MaterialTheme.typography.bodyMedium) }
                when {
                    !s.signedIn -> item {
                        EmptyState(
                            stringResource(R.string.ech_titre),
                            stringResource(R.string.ech_connexion),
                            actionLabel = stringResource(R.string.ech_se_connecter),
                            onAction = onRequestLogin
                        )
                    }
                    s.items.isEmpty() -> item { EmptyState(stringResource(R.string.ech_vide_titre), stringResource(R.string.ech_vide_message)) }
                }
                items(s.items, key = { it.id }) { e ->
                    val late = (e.daysLeft(LocalDate.now()) ?: 1) <= 0
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = if (late &&
                                !e.counter
                            ) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("📅 " + e.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                            if (late && !e.counter) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = stringResource(R.string.ech_attention),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        dueText(e),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                Text(dueText(e), style = MaterialTheme.typography.bodyMedium)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!e.counter) Button(onClick = { viewModel.confirm(e.id) }, enabled = !s.busy) { Text(stringResource(R.string.ech_fait)) }
                                TextButton(onClick = { toDelete = e }, enabled = !s.busy) { Text(stringResource(R.string.ech_supprimer)) }
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            text = { Text(stringResource(R.string.ech_supprimer_confirmer, e.label)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(e.id)
                    toDelete = null
                }) { Text(stringResource(R.string.ech_supprimer)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(fr.labonnealerte.core.ui.R.string.annuler)) } }
        )
    }

    if (showForm) {
        ModalBottomSheet(onDismissRequest = { showForm = false }) {
            EcheanceForm(busy = s.busy) { label, anchor, value, unit, announce ->
                viewModel.create(label, anchor, value, unit, announce) { showForm = false }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EcheanceForm(busy: Boolean, onSave: (String, LocalDate, Int, Periodicite, Int) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var value by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf(Periodicite.AN) }
    var announce by remember { mutableFloatStateOf(7f) }
    var pickDate by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.ech_ajouter), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        OutlinedTextField(label, {
            label = it.take(120)
        }, label = { Text(stringResource(R.string.ech_libelle)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.ech_derniere_fois), style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { pickDate = true }) { Text(fr.labonnealerte.core.ui.Dates.long(anchor)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ech_tous_les))
            Spacer8()
            OutlinedTextField(
                value,
                { v -> value = v.filter { it.isDigit() }.take(4) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(88.dp)
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Periodicite.JOUR to R.string.ech_jours,
                Periodicite.SEMAINE to R.string.ech_semaines,
                Periodicite.MOIS to R.string.ech_mois,
                Periodicite.AN to R.string.ech_ans
            )
                .forEach { (u, l) -> FilterChip(unit == u, { unit = u }, { Text(stringResource(l)) }) }
        }
        Text(pluralStringResource(R.plurals.ech_prevenir, announce.roundToInt(), announce.roundToInt()), style = MaterialTheme.typography.titleSmall)
        val announceState = pluralStringResource(R.plurals.ech_prevenir_etat, announce.roundToInt(), announce.roundToInt())
        Slider(
            announce,
            { announce = it },
            valueRange = 0f..30f,
            steps = 29,
            modifier = Modifier.semantics { stateDescription = announceState }
        )
        val v = value.toIntOrNull() ?: 0
        Button(
            onClick = { onSave(label, anchor, v, unit, announce.roundToInt()) },
            enabled = label.isNotBlank() && v in 1..1000 && !busy,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) { Text(stringResource(R.string.ech_enregistrer)) }
    }
    if (pickDate) {
        // Le serveur refuse une date dans le futur : on ne propose que le passe et aujourd'hui.
        val st = rememberDatePickerState(
            initialSelectedDateMillis = anchor.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            }
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { anchor = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text(stringResource(fr.labonnealerte.core.ui.R.string.valider)) }
            }
        ) { DatePicker(st) }
    }
}

@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
