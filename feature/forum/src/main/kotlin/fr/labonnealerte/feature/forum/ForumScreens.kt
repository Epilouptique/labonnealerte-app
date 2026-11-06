package fr.labonnealerte.feature.forum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.labonnealerte.core.data.ForumCategory
import fr.labonnealerte.core.data.ForumTopic
import fr.labonnealerte.core.ui.Dates
import fr.labonnealerte.core.ui.components.EmptyState
import fr.labonnealerte.core.ui.components.ListeSkeleton

/**
 * Icone de rubrique. Icones Material plutot qu'emojis : aucun risque de glyphe carre, quelle
 * que soit la police emoji du telephone (APP-10). Les emojis d'origine etaient en plus
 * doublement encodes dans ce fichier (UTF-8 relu en Windows-1252), d'ou trois ou quatre signes etranges a l'ecran.
 */
internal fun categoryIcon(slug: String): ImageVector = when (slug) {
    "discussion" -> Icons.Filled.Forum
    "propositions" -> Icons.Filled.Lightbulb
    "dev" -> Icons.Filled.Build
    "entraide" -> Icons.Filled.Handshake
    "suggestions" -> Icons.Filled.AutoAwesome
    else -> Icons.Filled.ChatBubble
}

@Composable
private fun msgText(m: ForumMsg?, server: String?): String? = when (m) {
    null -> null
    ForumMsg.ENVOYE -> stringResource(R.string.forum_msg_envoye)
    ForumMsg.SIGNALE -> stringResource(R.string.forum_msg_signale)
    ForumMsg.MASQUE -> stringResource(R.string.forum_msg_masque)
    ForumMsg.HORS_LIGNE -> stringResource(R.string.forum_msg_hors_ligne)
    ForumMsg.CONNEXION -> stringResource(R.string.forum_msg_connexion)
    ForumMsg.TROP -> stringResource(R.string.forum_msg_trop)
    ForumMsg.VERROUILLE -> stringResource(R.string.forum_msg_verrouille)
    ForumMsg.REFUSE -> stringResource(R.string.forum_msg_refuse) + (server?.let { " $it" } ?: "")
    ForumMsg.ERREUR -> stringResource(R.string.forum_msg_erreur)
}

/** Accueil du forum (onglet). */
@Composable
fun ForumHomeRoute(
    onOpenCategory: (String) -> Unit,
    onOpenTopic: (String) -> Unit,
    onNewTopic: () -> Unit,
    viewModel: ForumHomeViewModel = hiltViewModel()
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        floatingActionButton = {
            NouveauSujetFab(onNewTopic)
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (val st = s) {
                Load.Loading -> ListeSkeleton(4)
                Load.Error -> EmptyState(
                    stringResource(R.string.forum_erreur),
                    null,
                    actionLabel = stringResource(fr.labonnealerte.core.ui.R.string.reessayer),
                    onAction = viewModel::load
                )
                is Load.Ready -> LazyColumn(
                    Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            stringResource(R.string.forum_titre),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.semantics {
                                heading()
                            }
                        )
                        fr.labonnealerte.core.ui.components.HeaderDividerLine(bleed = 16.dp)
                        Text(
                            stringResource(R.string.forum_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        Text(
                            stringResource(R.string.forum_categories),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics {
                                heading()
                            }
                        )
                    }
                    items(st.value.first, key = { "c-" + it.slug }) { c -> CategoryCard(c) { onOpenCategory(c.slug) } }
                    item {
                        Text(
                            stringResource(R.string.forum_recents),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics {
                                heading()
                            }
                        )
                    }
                    items(
                        fr.labonnealerte.core.model.HiddenContent.filter(st.value.second, hidden) {
                            fr.labonnealerte.core.model.HiddenContent.authorKey(it.authorPseudo, it.author)
                        },
                        key = { "t-" + it.id }
                    ) { t -> TopicCard(t) { onOpenTopic(t.slug) } }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(c: ForumCategory, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(categoryIcon(c.slug), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(c.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.forum_sujets_n, c.count, c.count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TopicCard(t: ForumTopic, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.locked) {
                    Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.forum_verrouille), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            val meta = listOfNotNull(
                t.sourceName,
                stringResource(R.string.forum_par, t.authorPseudo?.let { "@$it" } ?: t.author),
                pluralStringResource(R.plurals.forum_reponses_n, t.replyCount, t.replyCount),
                Dates.day(t.lastReplyAt)
            ).joinToString(" · ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    categoryIcon(t.category),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackScaffold(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
    fab: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            fr.labonnealerte.core.ui.components.HeaderWithDivider {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.forum_retour)) }
                    },
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0)
                )
            }
        },
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        floatingActionButton = fab
    ) { p -> Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.TopCenter) { content() } }
}

/** Sujets d'une rubrique, d'une alerte ou d'un membre. */
@Composable
fun TopicListRoute(
    onBack: () -> Unit,
    onOpenTopic: (String) -> Unit,
    onNewTopic: (String?) -> Unit,
    viewModel: TopicListViewModel = hiltViewModel()
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val title = when {
        viewModel.category != null -> stringResource(R.string.forum_titre)
        viewModel.sourceId != null -> stringResource(R.string.forum_sujet_alerte)
        else -> stringResource(R.string.forum_sujets_de, "@" + viewModel.author.orEmpty())
    }
    BackScaffold(title, onBack, fab = {
        if (viewModel.author == null) {
            ExtendedFloatingActionButton(onClick = {
                onNewTopic(viewModel.sourceId)
            }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.forum_nouveau)) })
        }
    }) {
        LazyColumn(
            Modifier.widthIn(max = 840.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!s.loading && !s.error && s.topics.isEmpty()) {
                item { EmptyState(stringResource(R.string.forum_vide_titre), stringResource(R.string.forum_vide_message)) }
            }
            items(
                fr.labonnealerte.core.model.HiddenContent.filter(s.topics, hidden) {
                    fr.labonnealerte.core.model.HiddenContent.authorKey(it.authorPseudo, it.author)
                },
                key = { it.id }
            ) { t -> TopicCard(t) { onOpenTopic(t.slug) } }
            item {
                when {
                    s.loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    s.error -> TextButton(onClick = viewModel::loadMore) {
                        Text(stringResource(fr.labonnealerte.core.ui.R.string.reessayer))
                    }
                    s.hasMore -> OutlinedButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.forum_charger_plus))
                    }
                }
            }
        }
    }
}

/** Lecture d'un sujet, reponse et signalement. */
@Composable
fun ThreadRoute(
    onBack: () -> Unit,
    onRequestLogin: () -> Unit,
    onOpenMember: (String) -> Unit,
    viewModel: ThreadViewModel = hiltViewModel()
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val text = msgText(s.message, s.serverText)
    LaunchedEffect(s.message) {
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }
    var reportId by remember { mutableStateOf<Long?>(null) }
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    BackScaffold(s.topic?.title ?: stringResource(R.string.forum_titre), onBack, snackbar) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxSize().imePadding()) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                s.topic?.let { t ->
                    item { Text(t.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }) }
                }
                items(
                    fr.labonnealerte.core.model.HiddenContent.filter(s.posts, hidden) {
                        fr.labonnealerte.core.model.HiddenContent.authorKey(it.authorPseudo, it.author)
                    },
                    key = { it.id }
                ) { post ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val who = post.authorPseudo?.let { "@$it" } ?: post.author
                                TextButton(onClick = {
                                    post.authorPseudo?.let(onOpenMember)
                                }, enabled = post.authorPseudo != null) { Text(who) }
                                Text(
                                    Dates.full(post.createdAt).orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                var menu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = {
                                        menu = true
                                    }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.forum_plus)) }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.forum_signaler)) }, onClick = {
                                            menu = false
                                            reportId = post.id
                                        })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.forum_masquer_auteur)) }, onClick = {
                                            menu = false
                                            viewModel.hideAuthor(fr.labonnealerte.core.model.HiddenContent.authorKey(post.authorPseudo, post.author))
                                        })
                                    }
                                }
                            }
                            // Texte brut : aucun HTML interprete (protection contre l'injection).
                            Text(post.body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 12.dp))
                        }
                    }
                }
                item {
                    when {
                        s.loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        s.error -> TextButton(onClick = viewModel::loadMore) {
                            Text(stringResource(fr.labonnealerte.core.ui.R.string.reessayer))
                        }
                        s.hasMore -> OutlinedButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.forum_charger_plus))
                        }
                    }
                }
            }
            val topic = s.topic
            when {
                topic == null -> Unit
                topic.locked -> Text(stringResource(R.string.forum_msg_verrouille), modifier = Modifier.padding(16.dp))
                !s.signedIn -> Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.forum_connexion_requise), modifier = Modifier.weight(1f))
                    Button(onClick = onRequestLogin) { Text(stringResource(R.string.forum_se_connecter)) }
                }
                else -> {
                    var reply by rememberSaveable { mutableStateOf("") }
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(
                            value = reply,
                            onValueChange = { reply = it.take(ForumLimits.BODY_MAX) },
                            label = { Text(stringResource(R.string.forum_repondre)) },
                            modifier = Modifier.weight(1f).heightIn(max = 200.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { viewModel.reply(reply) { reply = "" } }, enabled = reply.isNotBlank() && !s.sending) {
                            Text(stringResource(R.string.forum_envoyer))
                        }
                    }
                }
            }
        }
    }
    reportId?.let { id ->
        AlertDialog(
            onDismissRequest = { reportId = null },
            text = { Text(stringResource(R.string.forum_signaler_confirmer)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.report(id)
                    reportId = null
                }) { Text(stringResource(R.string.forum_signaler)) }
            },
            dismissButton = {
                TextButton(onClick = { reportId = null }) { Text(stringResource(fr.labonnealerte.core.ui.R.string.annuler)) }
            }
        )
    }
}

/** Nouveau sujet (rubrique, titre, message ; lie a une alerte si ouvert depuis son detail). */
@Composable
fun NewTopicRoute(onBack: () -> Unit, onCreated: (String) -> Unit, viewModel: NewTopicViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val text = msgText(s.message, s.serverText)
    LaunchedEffect(s.message) {
        if (text != null && s.message != ForumMsg.ENVOYE) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(s.createdSlug) { s.createdSlug?.let { if (it.isNotEmpty()) onCreated(it) else onBack() } }
    var category by rememberSaveable { mutableStateOf(viewModel.initialCategory ?: "entraide") }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    BackScaffold(stringResource(R.string.forum_nouveau), onBack, snackbar) {
        Column(
            Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.forum_rubrique), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    s.categories.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { c ->
                                FilterChip(
                                    category == c.slug,
                                    { category = c.slug },
                                    { Text(c.label) },
                                    leadingIcon = { Icon(categoryIcon(c.slug), contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(ForumLimits.TITLE_MAX) },
                label = { Text(stringResource(R.string.forum_titre_sujet)) },
                supportingText = { Text(stringResource(R.string.forum_compteur, title.length, ForumLimits.TITLE_MAX)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it.take(ForumLimits.BODY_MAX) },
                label = { Text(stringResource(R.string.forum_message)) },
                supportingText = { Text(stringResource(R.string.forum_compteur, body.length, ForumLimits.BODY_MAX)) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.send(category, title, body) },
                enabled = title.trim().length >= ForumLimits.TITLE_MIN && body.isNotBlank() && !s.sending,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.forum_publier)) }
        }
    }
}

/** Bouton flottant « Nouveau sujet » (isole pour le test d'accessibilite). */
@Composable
internal fun NouveauSujetFab(onClick: () -> Unit) {
    val label = stringResource(R.string.forum_nouveau)
    // Libelle explicite : le texte du bouton etendu n'est pas fusionne sur le noeud cliquable (APP-03).
    ExtendedFloatingActionButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }, icon = {
        Icon(Icons.Filled.Add, null)
    }, text = { Text(label) })
}
