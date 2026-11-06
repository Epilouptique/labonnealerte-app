package fr.labonnealerte.feature.alerte

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.labonnealerte.core.model.DayStatus
import fr.labonnealerte.core.model.EventKind
import fr.labonnealerte.core.model.HistoryDay
import fr.labonnealerte.core.model.HistoryEvent
import fr.labonnealerte.core.model.Source
import fr.labonnealerte.core.model.SourceType
import fr.labonnealerte.core.model.SplitMessage
import fr.labonnealerte.core.ui.Dates
import fr.labonnealerte.core.ui.Famille
import fr.labonnealerte.core.ui.components.EmptyState
import fr.labonnealerte.core.ui.components.ListeSkeleton
import fr.labonnealerte.core.ui.components.StatePill
import fr.labonnealerte.core.ui.components.badgeLabel
import fr.labonnealerte.core.ui.openExternal
import fr.labonnealerte.core.ui.rememberReducedMotion
import fr.labonnealerte.core.ui.shareText
import fr.labonnealerte.core.ui.theme.LbaThemeExtras

/**
 * Detail plein ecran. [subscribeSlot] est fourni par l'app (bloc d'abonnement, M6) pour
 * eviter une dependance entre modules d'ecran.
 */
@Composable
fun AlerteRoute(
    onBack: () -> Unit,
    onRequestLogin: () -> Unit = {},
    onOpenForum: (String) -> Unit = {},
    /** Categorie touchee : kiosque filtre sur cette categorie (APP-08). Null : non cliquable. */
    onOpenCategory: ((String) -> Unit)? = null,
    /** Signaler une source communautaire (APP-21). */
    onReport: (String) -> Unit = {},
    viewModel: AlerteViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val text = state.sub.message?.let { m ->
        when (m) {
            SubMessage.Suivie -> stringResource(R.string.sub_msg_suivie)
            SubMessage.Retiree -> stringResource(R.string.sub_msg_retiree)
            SubMessage.EnPause -> stringResource(R.string.sub_msg_pause)
            SubMessage.Reprise -> stringResource(R.string.sub_msg_reprise)
            SubMessage.HorsLigne -> stringResource(R.string.sub_msg_hors_ligne)
            is SubMessage.Refus -> listOfNotNull(stringResource(R.string.sub_msg_refus), m.serverText).joinToString(" ")
            SubMessage.Erreur -> stringResource(R.string.sub_msg_erreur)
        }
    }
    LaunchedEffect(state.sub.message) {
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }
    // Permission de notification demandee au bon moment : juste apres le premier suivi, avec explication.
    val ctx = LocalContext.current
    var askPermission by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onPermissionAsked() }
    LaunchedEffect(state.sub.message) {
        if (state.sub.message == SubMessage.Suivie &&
            android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            askPermission = true
        }
    }
    if (askPermission) {
        AlertDialog(
            onDismissRequest = { askPermission = false },
            title = { Text(stringResource(R.string.perm_titre)) },
            text = { Text(stringResource(R.string.perm_texte)) },
            confirmButton = {
                TextButton(onClick = {
                    askPermission = false
                    permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.perm_ok)) }
            },
            dismissButton = { TextButton(onClick = { askPermission = false }) { Text(stringResource(R.string.perm_plus_tard)) } }
        )
    }
    AlerteScreen(state, onBack, viewModel::toggleFavorite, viewModel::loadHistory, snackbar, onOpenForum, onOpenCategory, onReport) { src ->
        AbonnementBlock(
            source = src,
            sub = state.sub,
            onRequestLogin = onRequestLogin,
            onSubscribe = viewModel::setSubscribed,
            onMute = viewModel::setMuted,
            onAdd = viewModel::addInstance,
            onRemove = viewModel::removeInstance,
            lookup = viewModel::lookup
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlerteScreen(
    state: AlerteUiState,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRetryHistory: () -> Unit,
    snackbar: SnackbarHostState,
    onOpenForum: (String) -> Unit,
    onOpenCategory: ((String) -> Unit)? = null,
    onReport: (String) -> Unit = {},
    subscribeSlot: @Composable (Source) -> Unit
) {
    val ctx = LocalContext.current
    val src = state.source
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            fr.labonnealerte.core.ui.components.HeaderWithDivider {
                TopAppBar(
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.retour))
                        }
                    },
                    actions = {
                        if (src != null) {
                            IconButton(onClick = onToggleFavorite) {
                                Icon(
                                    if (state.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = stringResource(
                                        if (state.favorite) {
                                            fr.labonnealerte.core.ui.R.string.cd_favori_retirer
                                        } else {
                                            fr.labonnealerte.core.ui.R.string.cd_favori_ajouter
                                        }
                                    ),
                                    tint = if (state.favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            val shareText = stringResource(R.string.partage_texte, src.name, AlerteRoutes.statusUrl(src.id))
                            IconButton(onClick = { ctx.shareText(src.name, shareText) }) {
                                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.partager))
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                state.loading -> ListeSkeleton(count = 3, animate = !rememberReducedMotion())
                src == null -> EmptyState(stringResource(R.string.introuvable_titre), stringResource(R.string.introuvable_message))
                else -> LazyColumn(
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Entete(src, state) }
                    item { subscribeSlot(src) }
                    item { Description(src) }
                    if (state.categories.isNotEmpty()) {
                        item {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.categories.forEach { c -> CategoryTag("${Famille.of(c.group).emoji} ${c.label}", c.slug, onOpenCategory) }
                            }
                        }
                    }
                    if (src.type != SourceType.LINKED) {
                        when (val h = state.history) {
                            HistoryState.Loading -> item { ListeSkeleton(count = 1, animate = false) }
                            HistoryState.Error -> item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.historique_erreur), modifier = Modifier.weight(1f))
                                    TextButton(onClick = onRetryHistory) {
                                        Text(stringResource(fr.labonnealerte.core.ui.R.string.reessayer))
                                    }
                                }
                            }
                            is HistoryState.Ready -> {
                                if (h.history.days.isNotEmpty()) item { Frise(h.history.days) }
                                item {
                                    Text(
                                        stringResource(R.string.historique_titre),
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.semantics { heading() }
                                    )
                                }
                                val events = collapseFailures(h.history.events).take(MAX_EVENTS)
                                if (events.isEmpty()) {
                                    item {
                                        Text(stringResource(R.string.historique_vide), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                items(events) { (e, n) -> EventRow(e, n) }
                            }
                        }
                    }
                    item { Liens(src, onOpenForum, onReport) }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

private const val MAX_EVENTS = 20

@Composable
private fun Entete(src: Source, state: AlerteUiState) {
    val fam = Famille.of(state.categories.firstOrNull()?.group)
    Column {
        Text(fam.emoji, style = MaterialTheme.typography.displaySmall)
        Text(src.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        if (!src.subtitle.isNullOrBlank()) {
            Text(src.subtitle!!, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatePill(src.state)
            badgeLabel(src.badge)?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        }
        if (src.subscriberCount > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                pluralStringResource(R.plurals.abonnes, src.subscriberCount, src.subscriberCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Description(src: Source) {
    Column {
        Text(
            stringResource(R.string.a_propos_alerte),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics {
                heading()
            }
        )
        Spacer(Modifier.height(6.dp))
        // Texte officiel tel quel : il porte les mentions de source (ex. « via Dealabs »).
        src.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Spacer(Modifier.height(8.dp))
        val long = src.descriptionLong
        if (!long.isNullOrBlank() && long != src.description) {
            Text(long, style = MaterialTheme.typography.bodyLarge)
        } else if (src.description.isNullOrBlank()) {
            Text(stringResource(R.string.sans_description_longue), style = MaterialTheme.typography.bodyLarge)
        }
        if (src.isParameterized) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.parametree),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun dayColor(s: DayStatus): Color {
    val c = LbaThemeExtras.stateColors
    return when (s) {
        DayStatus.CALM -> c.calm.copy(alpha = 0.55f)
        DayStatus.ACTIVE -> MaterialTheme.colorScheme.secondary
        DayStatus.FAILED -> c.incident
        DayStatus.NODATA -> MaterialTheme.colorScheme.outlineVariant
    }
}

/** Frise 90 jours : une barre par jour, du plus ancien (gauche) au plus recent. */
@Composable
private fun Frise(days: List<HistoryDay>) {
    val actives = days.count { it.status == DayStatus.ACTIVE }
    val incidents = days.count { it.status == DayStatus.FAILED }
    val resume = stringResource(
        R.string.frise_resume,
        pluralStringResource(R.plurals.frise_jours_alerte, actives, actives),
        pluralStringResource(R.plurals.frise_jours_incident, incidents, incidents)
    )
    Column {
        Text(stringResource(R.string.frise_titre), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(36.dp).clearAndSetSemantics { contentDescription = resume },
            horizontalArrangement = Arrangement.spacedBy(1.5.dp)
        ) {
            days.forEach { d ->
                Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(2.dp)).background(dayColor(d.status)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(resume, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                DayStatus.CALM to R.string.frise_legende_calme,
                DayStatus.ACTIVE to R.string.frise_legende_active,
                DayStatus.FAILED to R.string.frise_legende_incident
            ).forEach { (s, l) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(dayColor(s)))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(l), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun EventRow(e: HistoryEvent, repeat: Int = 1) {
    val msg = SplitMessage.of(e.message)
    val (fallback, color) = when (e.kind) {
        EventKind.ACTIVATED -> stringResource(R.string.evt_active) to MaterialTheme.colorScheme.secondary
        EventKind.DEACTIVATED -> stringResource(R.string.evt_fin) to LbaThemeExtras.stateColors.calm
        EventKind.FAILED -> stringResource(R.string.evt_incident) to LbaThemeExtras.stateColors.incident
        EventKind.UNKNOWN -> "" to MaterialTheme.colorScheme.outline
    }
    val repeatText = if (repeat > 1) " " + stringResource(R.string.evt_repetition, repeat) else ""
    Row(
        verticalAlignment = Alignment.Top,
        modifier = if (fallback.isNotEmpty()) Modifier.semantics(mergeDescendants = true) { stateDescription = fallback } else Modifier
    ) {
        Box(Modifier.padding(top = 6.dp).size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                (msg.emoji?.let { "$it " } ?: "") + msg.text.ifBlank { fallback } + repeatText,
                style = MaterialTheme.typography.bodyLarge
            )
            Dates.full(e.createdAt)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Categorie de la fiche ; cliquable (kiosque filtre) quand [onOpen] est fourni (APP-08). */
@Composable
internal fun CategoryTag(label: String, slug: String, onOpen: ((String) -> Unit)?) {
    val shape = MaterialTheme.shapes.small
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val content: @Composable () -> Unit = {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
    }
    if (onOpen == null) {
        Surface(shape = shape, border = border, content = content)
    } else {
        Surface(
            onClick = { onOpen(slug) },
            shape = shape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.heightIn(min = 48.dp),
            content = content
        )
    }
}

@Composable
private fun Liens(src: Source, onOpenForum: (String) -> Unit, onReport: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (src.badge == fr.labonnealerte.core.model.Badge.COMMUNITY) {
            androidx.compose.material3.TextButton(onClick = { onReport(src.id) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.signaler_source))
            }
        }
        if (src.type == SourceType.LINKED && src.linkUrl != null) {
            OutlinedButton(onClick = { ctx.openExternal(src.linkUrl!!) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.voir_partenaire))
            }
        }
        src.forumSlug?.let { _ ->
            OutlinedButton(onClick = { onOpenForum(src.id) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Forum, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (src.topicCount >
                        0
                    ) {
                        stringResource(R.string.voir_forum, src.topicCount)
                    } else {
                        stringResource(R.string.voir_forum_vide)
                    }
                )
            }
        }
        OutlinedButton(onClick = { ctx.openExternal(AlerteRoutes.statusUrl(src.id)) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.voir_site))
        }
    }
}

/** Regroupe les incidents consecutifs (un par heure cote serveur) en une seule ligne « ×N ». */
internal fun collapseFailures(events: List<HistoryEvent>): List<Pair<HistoryEvent, Int>> {
    val out = mutableListOf<Pair<HistoryEvent, Int>>()
    for (e in events) {
        val last = out.lastOrNull()
        if (last != null && e.kind == EventKind.FAILED && last.first.kind == EventKind.FAILED) {
            out[out.lastIndex] = last.first to last.second + 1
        } else {
            out += e to 1
        }
    }
    return out
}
