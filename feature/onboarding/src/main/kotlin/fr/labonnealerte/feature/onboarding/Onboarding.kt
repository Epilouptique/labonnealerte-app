package fr.labonnealerte.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.labonnealerte.core.data.AppPreferences
import fr.labonnealerte.core.data.CatalogRepository
import fr.labonnealerte.core.data.FavoritesRepository
import fr.labonnealerte.core.data.ReceptionMode
import fr.labonnealerte.core.model.Departement
import fr.labonnealerte.core.model.Source
import fr.labonnealerte.core.model.SourceType
import fr.labonnealerte.core.push.NotificationHealth
import fr.labonnealerte.core.push.NtfyInstall
import fr.labonnealerte.core.ui.Famille
import fr.labonnealerte.core.ui.components.DepartementPickerDialog
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Familles proposees comme themes (les plus parlantes pour le grand public). */
val THEMES = listOf(
    Famille.METEO, Famille.ENERGIE, Famille.VIE_LOCALE, Famille.TRANSPORTS, Famille.SANTE,
    Famille.BONS_PLANS, Famille.ENVIRONNEMENT, Famille.ANIMAUX, Famille.TECH, Famille.CULTURE
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val catalog: CatalogRepository,
    private val favorites: FavoritesRepository,
    private val health: NotificationHealth
) : ViewModel() {
    private val _setup = MutableStateFlow<SetupState?>(null)

    /** Etat reel du telephone ; null tant qu'il n'a pas ete lu. */
    val setup: StateFlow<SetupState?> = _setup.asStateFlow()

    private val _departements = MutableStateFlow<List<Departement>>(emptyList())
    val departements: StateFlow<List<Departement>> = _departements.asStateFlow()

    private val _added = MutableStateFlow(0)
    val added: StateFlow<Int> = _added.asStateFlow()

    init {
        viewModelScope.launch {
            catalog.refreshSources()
            catalog.refreshCategories()
            catalog.geo().onSuccess { _departements.value = it.departements }
        }
    }

    /** Suggestions : par theme, les sources les plus suivies (3 par theme), mises en favoris. */
    fun applySuggestions(themes: Set<String>) {
        viewModelScope.launch {
            val sources = catalog.sources.first()
            val groups = catalog.categories.first().associate { it.slug to it.group }
            val picked = suggestions(sources, themes) { groups[it] }
            picked.forEach { favorites.setFavorite(it.id, true) }
            _added.value += picked.size
        }
    }

    /** Relit l'etat reel : au lancement, a chaque retour sur l'app, apres la fenetre de permission. */
    fun refresh() {
        viewModelScope.launch {
            val i = health.read()
            _setup.value = SetupState(
                permissionGranted = i.permissionGranted,
                notificationsReady = i.permissionGranted && i.notificationsEnabled && i.blockedChannels == 0,
                distributorInstalled = i.distributors > 0
            )
        }
    }

    fun onPermissionAsked() {
        viewModelScope.launch {
            prefs.markNotificationPermissionAsked()
            refresh()
        }
    }

    fun setDepartement(code: String) {
        viewModelScope.launch { prefs.setDepartementPrefere(code) }
    }

    fun finish(mode: ReceptionMode?) {
        viewModelScope.launch {
            mode?.let { prefs.setReceptionMode(it) }
            prefs.setOnboardingDone(true)
        }
    }

    companion object {
        fun suggestions(sources: List<Source>, themes: Set<String>, groupOf: (String) -> String?): List<Source> =
            themes.flatMap { theme ->
                sources.filter { s -> s.type != SourceType.LINKED && s.type != SourceType.USER_TASK && s.categories.any { groupOf(it) == theme } }
                    .sortedByDescending { it.subscriberCount }
                    .take(3)
            }.distinctBy { it.id }
    }
}

@Composable
fun OnboardingRoute(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    // Etat reel relu a chaque retour sur l'app (retour des reglages, de l'installation de ntfy).
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    var wanted by rememberSaveable { mutableStateOf(OnboardingStep.BIENVENUE) }
    val ctx = LocalContext.current
    val finish: (ReceptionMode?) -> Unit = { m ->
        viewModel.finish(m)
        onDone()
    }
    val s = setup
    // Une etape dont l'objectif est atteint ne s'affiche pas : on passe a la suivante utile.
    val step = s?.let { currentStep(wanted, it) }
    LaunchedEffect(s, step) { if (s != null && step == null) finish(null) }
    Surface(Modifier.fillMaxSize()) {
        if (s == null || step == null) return@Surface
        val steps = stepsToShow(s)
        val laterText = stringResource(R.string.ob4_plus_tard_message)
        // Toute la page defile (en-tete, contenu, bouton) : en paysage ou a 200 %, le bouton du bas ne
        // masque plus le contenu (APP-06, point 6). En portrait, la page remplit l'ecran comme avant.
        BoxWithConstraints(Modifier.safeDrawingPadding().fillMaxSize()) {
            val minHeight = maxHeight
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = minHeight).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.ob_etape, steps.indexOf(step) + 1, steps.size),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    // Derniere etape : « Passer » ferait doublon avec « C'est parti » (APP-03).
                    if (step != steps.last()) TextButton(onClick = { finish(null) }) { Text(stringResource(R.string.ob_passer)) }
                }
                Box(Modifier.widthIn(max = 560.dp).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (step) {
                            OnboardingStep.BIENVENUE -> {
                                Image(
                                    painterResource(fr.labonnealerte.core.ui.R.drawable.mascotte),
                                    contentDescription = stringResource(fr.labonnealerte.core.ui.R.string.cd_mascotte),
                                    modifier = Modifier.height(200.dp)
                                )
                                Titre(stringResource(R.string.ob1_titre))
                                Texte(stringResource(R.string.ob1_texte))
                            }
                            OnboardingStep.THEMES -> Suggestions(viewModel)
                            OnboardingStep.NOTIFICATIONS -> PermissionStep(s.permissionGranted, viewModel::onPermissionAsked)
                            OnboardingStep.RECEPTION -> ReceptionStep(
                                fdroidInstalled = NtfyInstall.fdroidInstalled(ctx),
                                onInstall = { NtfyInstall.open(ctx) },
                                onPeriodic = { finish(ReceptionMode.PERIODIQUE) },
                                onLater = {
                                    android.widget.Toast.makeText(ctx, laterText, android.widget.Toast.LENGTH_LONG).show()
                                    finish(ReceptionMode.PERIODIQUE)
                                }
                            )
                        }
                    }
                }
                // Etape de reception : ses propres choix suffisent (pas de bouton general en plus).
                if (step != OnboardingStep.RECEPTION) {
                    val next = nextStep(step, s)
                    Button(
                        onClick = { if (next != null) wanted = next else finish(null) },
                        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).heightIn(min = 52.dp)
                    ) { Text(stringResource(if (next != null) R.string.ob_suivant else R.string.ob_commencer)) }
                } else {
                    Spacer(Modifier.height(1.dp))
                }
            }
        }
    }
}

@Composable
private fun Titre(
    t: String
) = Text(t, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })

@Composable
private fun Texte(t: String) = Text(t, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(vm: OnboardingViewModel) {
    var chosen by rememberSaveable { mutableStateOf(setOf<String>()) }
    var dept by rememberSaveable { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf(false) }
    val departements by vm.departements.collectAsStateWithLifecycle()
    val added by vm.added.collectAsStateWithLifecycle()
    Titre(stringResource(R.string.ob2_titre))
    Texte(stringResource(R.string.ob2_texte))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        THEMES.forEach { f ->
            FilterChip(
                selected = f.slug in chosen,
                onClick = {
                    chosen = if (f.slug in chosen) chosen - f.slug else chosen + f.slug
                    vm.applySuggestions(setOf(f.slug).filter { it in chosen }.toSet())
                },
                label = { Text(f.emoji + " " + stringResource(f.label)) }
            )
        }
    }
    if (added > 0) Text(pluralStringResource(R.plurals.ob2_n_suggestions, added, added), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.ob2_departement), style = MaterialTheme.typography.titleSmall)
    OutlinedButton(onClick = { pick = true }, enabled = departements.isNotEmpty()) {
        Text(departements.firstOrNull { it.code == dept }?.displayLabel ?: stringResource(R.string.ob2_choisir))
    }
    if (pick) {
        DepartementPickerDialog(departements, onPick = {
            dept = it.code
            vm.setDepartement(it.code)
            pick = false
        }, onDismiss = { pick = false })
    }
}

/**
 * Autoriser les notifications. N'apparait que si elles ne sont pas pretes ; une fois accordees,
 * l'etape disparait d'elle-meme (pas d'ecran « c'est deja fait »).
 */
@Composable
fun PermissionStep(permissionGranted: Boolean, onAsked: () -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onAsked() }
    Text("🔔", style = MaterialTheme.typography.displayLarge)
    Titre(stringResource(R.string.ob3_titre))
    Texte(stringResource(R.string.ob3_texte))
    if (!permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
            Text(stringResource(R.string.ob3_autoriser), textAlign = TextAlign.Center)
        }
    } else {
        // Permission deja accordee mais notifications ou categorie coupees : reglages Android.
        Button(onClick = {
            runCatching {
                ctx.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }) { Text(stringResource(R.string.ob3_reglages), textAlign = TextAlign.Center) }
    }
}

/**
 * Mode de reception, seulement sans distributeur (APP-06, point 3) : deux phrases, deux choix
 * clairs, et un « Plus tard » discret. Au retour de l'installation, le distributeur est detecte
 * et l'accueil continue tout seul (etape devenue inutile).
 */
@Composable
fun ReceptionStep(fdroidInstalled: Boolean, onInstall: () -> Unit, onPeriodic: () -> Unit, onLater: () -> Unit) {
    Text("📡", style = MaterialTheme.typography.displayLarge)
    Titre(stringResource(R.string.ob4_titre))
    Texte(stringResource(R.string.ob4_texte))
    Button(onClick = onInstall, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(if (fdroidInstalled) R.string.ob4_ntfy_fdroid else R.string.ob4_ntfy), textAlign = TextAlign.Center)
    }
    OutlinedButton(onClick = onPeriodic, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.ob4_periodique), textAlign = TextAlign.Center)
    }
    TextButton(onClick = onLater, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.ob4_plus_tard)) }
}
