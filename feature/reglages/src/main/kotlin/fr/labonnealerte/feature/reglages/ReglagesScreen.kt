package fr.labonnealerte.feature.reglages

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.labonnealerte.core.model.QuietHours
import fr.labonnealerte.core.ui.openExternal
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReglagesRoute(
    versionName: String,
    onBack: () -> Unit,
    onOpenDiagnostic: () -> Unit,
    onOpenLicences: () -> Unit,
    /** Historique des notifications recues sur ce telephone (deplace de Mes alertes, APP-08). */
    onOpenHistorique: () -> Unit = {},
    /** Options d'affichage (test), APP-11. */
    onOpenDisplayLab: () -> Unit = {},
    /** Lien vers les options d'affichage (test) : debug seulement (BuildConfig.SHOW_TEST_OPTIONS de l'app, APP-23). */
    showTestOptions: Boolean = true,
    /**
     * Bas de l'ecran (nouvelle navigation, APP-12) : « Supprimer mon compte », discret. Fourni par
     * l'app (module comptes) ; vide sinon.
     */
    bottomSlot: @Composable () -> Unit = {},
    viewModel: ReglagesViewModel = hiltViewModel()
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val support by viewModel.support.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val doneText = done?.let { stringResource(it) }
    LaunchedEffect(done) {
        if (doneText != null) {
            snackbar.showSnackbar(doneText)
            viewModel.consumeDone()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            fr.labonnealerte.core.ui.components.HeaderWithDivider {
                TopAppBar(
                    title = { Text(stringResource(R.string.reglages_titre)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reglages_retour))
                        }
                    },
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0)
                )
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Section(stringResource(R.string.reglages_apparence))
                Text(stringResource(R.string.reglages_theme), style = MaterialTheme.typography.titleSmall)
                ThemeSelector(s.theme, viewModel::setTheme)
                // Couleurs du systeme (Material You) : Android 12 et plus seulement, masque avant.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow(stringResource(R.string.reglages_couleur_dynamique), null, s.dynamicColor, viewModel::setDynamic)
                }
                var scale by remember(s.fontScale) { mutableFloatStateOf(s.fontScale) }
                Text(
                    stringResource(R.string.reglages_taille_texte) + "\u00A0: " +
                        stringResource(R.string.reglages_taille_texte_valeur, (scale * 100).roundToInt()),
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = scale,
                    onValueChange = { scale = it },
                    onValueChangeFinished = { viewModel.setFontScale(scale) },
                    valueRange = 0.85f..1.6f,
                    steps = 14
                )
                SwitchRow(stringResource(R.string.reglages_animations), null, s.reduceMotion, viewModel::setReduceMotion)
                if (showTestOptions) LinkRow(stringResource(R.string.lab_titre), stringResource(R.string.lab_lien_aide), onOpenDisplayLab)

                Section(stringResource(R.string.reglages_notifications))
                val channel by viewModel.channel.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { viewModel.loadChannel() }
                channel?.let { ChannelSelector(it, viewModel::setChannel) }
                QuietHoursEditor(s.quietHours, viewModel::setQuietHours)
                SwitchRow(
                    stringResource(R.string.reglages_exception_urgences),
                    stringResource(R.string.reglages_exception_urgences_aide),
                    s.urgentBypassQuiet,
                    viewModel::setUrgentBypass
                )
                var max by remember(s.maxPerHour) { mutableFloatStateOf(s.maxPerHour.toFloat()) }
                Text(pluralStringResource(R.plurals.reglages_plafond, max.roundToInt(), max.roundToInt()), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.reglages_plafond_aide), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = max,
                    onValueChange = {
                        max = it
                    },
                    onValueChangeFinished = { viewModel.setMaxPerHour(max.roundToInt()) },
                    valueRange =
                    2f..30f,
                    steps = 27
                )
                LinkRow(stringResource(R.string.reglages_types), stringResource(R.string.reglages_types_aide)) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }

                Section(stringResource(R.string.reglages_confidentialite))
                val hidden by viewModel.hidden.collectAsStateWithLifecycle()
                HiddenList(hidden, viewModel::unhide)

                Section(stringResource(R.string.reglages_donnees))
                LinkRow(stringResource(R.string.reglages_vider_cache), null, viewModel::clearCache)
                LinkRow(stringResource(R.string.reglages_vider_historique), null, viewModel::clearHistory)

                // Diagnostic : aussi ouvert par le bandeau de probleme (APP-05, decision E).
                Section(stringResource(R.string.reglages_aide))
                LinkRow(stringResource(R.string.reglages_diagnostic), null, onOpenDiagnostic)
                LinkRow(stringResource(R.string.test_alerte_titre), stringResource(R.string.test_alerte_aide), viewModel::scheduleTestAlert)
                LinkRow(stringResource(R.string.reglages_historique), stringResource(R.string.reglages_historique_aide), onOpenHistorique)
                LinkRow(stringResource(R.string.reglages_tutoriel), null) { viewModel.replayTutorial() }

                Section(stringResource(R.string.reglages_a_propos))
                Text(stringResource(R.string.reglages_version, versionName), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.reglages_promesse), style = MaterialTheme.typography.bodyMedium)
                support.kofi?.let { url -> LinkRow(stringResource(R.string.reglages_kofi), null) { ctx.openExternal(url) } }
                support.paypal?.let { url -> LinkRow(stringResource(R.string.reglages_paypal), null) { ctx.openExternal(url) } }
                LinkRow(stringResource(R.string.reglages_code_source), null) { ctx.openExternal(SOURCE_URL) }
                LinkRow(stringResource(R.string.reglages_confidentialite), null) { ctx.openExternal(PRIVACY_URL) }
                LinkRow(stringResource(R.string.reglages_licences), null, onOpenLicences)
                bottomSlot()
            }
        }
    }
}

/** Depot du site (le depot de l'app sera cree par Hugo : a mettre a jour). */
const val SOURCE_URL = "https://github.com/Epilouptique/labonnealerte"
const val PRIVACY_URL = "https://labonnealerte.fr/confidentialite"

/** Ordre du selecteur, comme sur le site : Clair | Auto | Sombre. */
internal val THEME_OPTIONS =
    listOf("light" to R.string.reglages_theme_clair, "system" to R.string.reglages_theme_auto, "dark" to R.string.reglages_theme_sombre)

/** Selecteur sur une ligne « Clair | Auto | Sombre » (APP-08), a la place de la liste a puces. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeSelector(current: String, onPick: (String) -> Unit) {
    // Une ligne de trois segments : taille plafonnee a 130 % (comme les onglets), sinon « Sombre »
    // est coupe a 200 %. Le reste de l'ecran suit la taille choisie sans plafond.
    val density = LocalDensity.current
    val capped = Density(density.density, minOf(density.fontScale, MAX_SEGMENT_FONT_SCALE))
    CompositionLocalProvider(LocalDensity provides capped) { ThemeSegments(current, onPick) }
}

private const val MAX_SEGMENT_FONT_SCALE = 1.3f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSegments(current: String, onPick: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        THEME_OPTIONS.forEachIndexed { i, (v, label) ->
            SegmentedButton(
                selected = current == v,
                onClick = { onPick(v) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = THEME_OPTIONS.size),
                modifier = Modifier.heightIn(min = 48.dp),
                // Sans coche : elle prenait la place du libelle ; le fond suffit a marquer le choix.
                icon = {}
            ) { Text(stringResource(label), maxLines = 1, softWrap = false) }
        }
    }
}

@Composable
private fun Section(title: String) {
    // Plus de trait au-dessus des sections : il doublait le filet de l'en-tete (APP-16).
    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp).semantics { heading() })
}

/** APP-23 : auteurs et signalements masques ; « Afficher » les remet dans les listes. */
@Composable
private fun HiddenList(hidden: Set<String>, onShow: (String) -> Unit) {
    if (hidden.isEmpty()) {
        Text(stringResource(R.string.reglages_masques_aucun), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text(stringResource(R.string.reglages_masques), style = MaterialTheme.typography.titleSmall)
    hidden.sorted().forEach { k ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(fr.labonnealerte.core.model.HiddenContent.label(k), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = { onShow(k) }) { Text(stringResource(R.string.reglages_afficher)) }
        }
    }
}

/** APP-22 : Notification / E-mail / Les deux (compte, GET/PUT /api/app/notify-channel). */
@Composable
private fun ChannelSelector(current: fr.labonnealerte.core.model.NotifyChannel, onChange: (fr.labonnealerte.core.model.NotifyChannel) -> Unit) {
    Text(stringResource(R.string.reglages_canal), style = MaterialTheme.typography.titleSmall)
    Column(Modifier.selectableGroup()) {
        listOf(
            fr.labonnealerte.core.model.NotifyChannel.PUSH to R.string.reglages_canal_push,
            fr.labonnealerte.core.model.NotifyChannel.EMAIL to R.string.reglages_canal_email,
            fr.labonnealerte.core.model.NotifyChannel.BOTH to R.string.reglages_canal_both
        ).forEach { (c, label) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = c == current, role = Role.RadioButton) { onChange(c) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.RadioButton(selected = c == current, onClick = null)
                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, help: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            help?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun LinkRow(title: String, help: String?, onClick: () -> Unit) {
    // Option 5 ou nouvelle navigation (APP-12) : ligne avec fleche a droite et filet.
    if (fr.labonnealerte.core.ui.LocalDisplayLab.current.rowSettings) {
        fr.labonnealerte.core.ui.components.NavRow(title, onClick, subtitle = help)
        return
    }
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        help?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun QuietHoursEditor(q: QuietHours, onChange: (QuietHours) -> Unit) {
    SwitchRow(stringResource(R.string.reglages_heures_calmes), stringResource(R.string.reglages_heures_calmes_aide), q.enabled) {
        onChange(q.copy(enabled = it))
    }
    if (q.enabled) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HourStepper(
                stringResource(R.string.reglages_de, q.start),
                stringResource(R.string.reglages_debut_plus_tot),
                stringResource(R.string.reglages_debut_plus_tard),
                { onChange(q.copy(start = (q.start + 23) % 24)) },
                { onChange(q.copy(start = (q.start + 1) % 24)) }
            )
            HourStepper(
                stringResource(R.string.reglages_a, q.end),
                stringResource(R.string.reglages_fin_plus_tot),
                stringResource(R.string.reglages_fin_plus_tard),
                { onChange(q.copy(end = (q.end + 23) % 24)) },
                { onChange(q.copy(end = (q.end + 1) % 24)) }
            )
        }
    }
}

@Composable
private fun HourStepper(label: String, minusDescription: String, plusDescription: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onMinus, modifier = Modifier.semantics { contentDescription = minusDescription }) { Text("−") }
        Text(label, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onPlus, modifier = Modifier.semantics { contentDescription = plusDescription }) { Text("+") }
    }
}
