package fr.labonnealerte.feature.kiosque

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.labonnealerte.core.model.KiosqueMode
import fr.labonnealerte.core.model.KiosqueSort
import fr.labonnealerte.core.model.LePointActive
import fr.labonnealerte.core.model.Source
import fr.labonnealerte.core.model.SplitMessage
import fr.labonnealerte.core.model.Subscription
import fr.labonnealerte.core.ui.Famille
import fr.labonnealerte.core.ui.LocalDisplayLab
import fr.labonnealerte.core.ui.components.AlertCard
import fr.labonnealerte.core.ui.components.BandeauHorsLigne
import fr.labonnealerte.core.ui.components.EmptyState
import fr.labonnealerte.core.ui.components.ListeSkeleton
import fr.labonnealerte.core.ui.rememberReducedMotion
import fr.labonnealerte.core.ui.theme.LbaThemeExtras

@Composable
fun KiosqueRoute(
    onOpenSource: (String) -> Unit,
    initialMode: KiosqueMode? = null,
    /** Categorie a filtrer a l'ouverture (categorie touchee sur la fiche d'une alerte, APP-08). */
    initialCategory: String? = null,
    focusSearch: Boolean = false,
    /** Bandeau affiche au-dessus du kiosque (accueil apres creation de compte, APP-05). */
    topBanner: @Composable () -> Unit = {},
    /** Suivre sans etre connecte : ouvre la connexion (comme la fiche). */
    onRequestLogin: () -> Unit = {},
    viewModel: KiosqueViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Raccourci d application : mode ou recherche demandes a l ouverture.
    androidx.compose.runtime.LaunchedEffect(initialMode) { initialMode?.let(viewModel::onMode) }
    androidx.compose.runtime.LaunchedEffect(initialCategory) { initialCategory?.let(viewModel::onCategory) }
    KiosqueScreen(
        state = state,
        onQuery = viewModel::onQuery,
        onMode = viewModel::onMode,
        onCategory = viewModel::onCategory,
        onGroup = viewModel::onGroup,
        onOnlyActive = viewModel::onOnlyActive,
        onSort = viewModel::onSort,
        onClearFilters = viewModel::clearFilters,
        onRefresh = { viewModel.refresh(true) },
        onOpenSource = onOpenSource,
        focusSearch = focusSearch,
        topBanner = topBanner,
        follow = FollowActions(
            follow = { s -> if (s.isParameterized) onOpenSource(s.id) else viewModel.follow(s) },
            unfollow = viewModel::unfollow,
            undo = viewModel::undo,
            consume = viewModel::consumeMessage,
            login = onRequestLogin
        )
    )
}

/** Abonnement depuis le kiosque (APP-10). Une source a parametres ouvre sa fiche (choix). */
class FollowActions(
    val follow: (Source) -> Unit = {},
    val unfollow: (Source) -> Unit = {},
    val undo: (Subscription) -> Unit = {},
    val consume: () -> Unit = {},
    val login: () -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KiosqueScreen(
    state: KiosqueUiState,
    onQuery: (String) -> Unit,
    onMode: (KiosqueMode) -> Unit,
    onCategory: (String?) -> Unit,
    onGroup: (String?) -> Unit,
    onOnlyActive: (Boolean) -> Unit,
    onSort: (KiosqueSort) -> Unit,
    onClearFilters: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSource: (String) -> Unit,
    focusSearch: Boolean = false,
    topBanner: @Composable () -> Unit = {},
    follow: FollowActions = FollowActions()
) {
    val snackbar = remember { SnackbarHostState() }
    FollowSnackbar(state.message, snackbar, follow)
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()
    val gridState = rememberLazyGridState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (state.offline && state.hasCache) BandeauHorsLigne()
            topBanner()
            when {
                state.loading -> ListeSkeleton(animate = !reducedMotion)
                state.error -> EmptyState(
                    title = stringResource(R.string.erreur_titre),
                    message = stringResource(R.string.erreur_message),
                    actionLabel = stringResource(fr.labonnealerte.core.ui.R.string.reessayer),
                    onAction = onRefresh
                )
                else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 340.dp),
                        state = gridState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Header(state, onQuery, onMode, onCategory, onOnlyActive, onSort, onOpenSheet = {
                                sheetOpen = true
                            }, onGroup = onGroup, focusSearch = focusSearch)
                        }
                        if (state.enCeMoment.isNotEmpty() &&
                            state.filter.query.isBlank() &&
                            state.filter.mode == KiosqueMode.TOUT &&
                            state.filter.category == null &&
                            state.filter.group == null
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) { EnCeMoment(state.enCeMoment, onOpenSource) }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val n = state.sources.size
                            Text(
                                if (n == 1) stringResource(R.string.resultats_un) else stringResource(R.string.resultats_n, n),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.sources.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                if (state.filter.mode == KiosqueMode.FAVORIS && state.filter.query.isBlank()) {
                                    EmptyState(stringResource(R.string.vide_favoris_titre), stringResource(R.string.vide_favoris_message))
                                } else {
                                    EmptyState(
                                        stringResource(R.string.vide_recherche_titre),
                                        stringResource(R.string.vide_recherche_message),
                                        actionLabel = stringResource(R.string.retirer_filtres),
                                        onAction = {
                                            onQuery("")
                                            onClearFilters()
                                        }
                                    )
                                }
                            }
                        }
                        items(state.sources, key = { it.id }, contentType = { "source" }) { s ->
                            val famille = Famille.of(state.allCategories.firstOrNull { it.slug in s.categories }?.group)
                            val followed = state.isFollowed(s.id)
                            SwipeToFollow(followed = followed, onFollow = { follow.follow(s) }, onUnfollow = { follow.unfollow(s) }) {
                                AlertCard(
                                    title = s.name,
                                    subtitle = s.subtitle,
                                    description = s.description,
                                    state = s.state,
                                    badge = s.badge,
                                    subscribed = followed,
                                    favorite = s.id in state.favorites,
                                    onClick = { onOpenSource(s.id) },
                                    // Coeur retire des vignettes (APP-17) ; il reste sur la fiche.
                                    onToggleFavorite = null,
                                    leading = Famille.emojiFor(famille, s.categories),
                                    onToggleFollow = { if (followed) follow.unfollow(s) else follow.follow(s) },
                                    familyColor = famille.color
                                )
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (sheetOpen) {
        // Toute la hauteur de l'ecran (APP-14).
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            CategoriesSheet(state.allCategories, onPick = {
                onCategory(it)
                sheetOpen = false
            }, onPickGroup = {
                onGroup(it)
                sheetOpen = false
            })
        }
    }
}

@Composable
private fun Header(
    state: KiosqueUiState,
    onQuery: (String) -> Unit,
    onMode: (KiosqueMode) -> Unit,
    onCategory: (String?) -> Unit,
    onOnlyActive: (Boolean) -> Unit,
    onSort: (KiosqueSort) -> Unit,
    onOpenSheet: () -> Unit,
    onGroup: (String?) -> Unit,
    focusSearch: Boolean = false
) {
    val focus = LocalFocusManager.current
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(focusSearch) {
        if (focusSearch) {
            kotlinx.coroutines.delay(300) // le champ doit etre attache avant de recevoir le focus
            runCatching { searchFocus.requestFocus() }
            keyboard?.show()
        }
    }
    Column(Modifier.padding(top = 16.dp)) {
        if (LocalDisplayLab.current.brandHeader) {
            BrandHeader()
        } else {
            Text(
                stringResource(R.string.kiosque_titre),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
        }
        Text(
            stringResource(R.string.kiosque_sous_titre),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f).focusRequester(searchFocus),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.kiosque_recherche)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.filter.query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.kiosque_effacer))
                        }
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() })
            )
            SortMenu(state.filter.sort, onSort)
        }
        Spacer(Modifier.height(8.dp))
        val f = state.filter
        val newNav = LocalDisplayLab.current.newNavigation
        // La puce « En cours » a disparu (APP-14) : un filtre reste actif d'avant serait introuvable.
        androidx.compose.runtime.LaunchedEffect(f.onlyActive) { if (f.onlyActive) onOnlyActive(false) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                fr.labonnealerte.core.ui.components.LbaChip(
                    selected = f.mode == KiosqueMode.TOUT && f.category == null && f.group == null,
                    onClick = { onMode(KiosqueMode.TOUT) },
                    label = { Text(stringResource(R.string.chip_toutes)) }
                )
            }
            item {
                fr.labonnealerte.core.ui.components.LbaChip(
                    f.mode == KiosqueMode.NOUVEAUTES,
                    { onMode(KiosqueMode.NOUVEAUTES) },
                    { Text(stringResource(R.string.chip_nouveautes)) }
                )
            }
            // Ordre (APP-14) : Toutes, Nouveau, 2 premieres categories, Tendance, les autres
            // categories, Toutes les categories. « En cours » ne figure plus dans la rangee.
            items(state.topCategories.take(1), key = { it.slug }) { c -> CategoryChipItem(c, f.category, onCategory) }
            // 4e puce : la famille « Bons plans » (APP-18).
            item(key = BONS_PLANS) {
                fr.labonnealerte.core.ui.components.LbaChip(
                    selected = f.group == BONS_PLANS,
                    onClick = { onGroup(if (f.group == BONS_PLANS) null else BONS_PLANS) },
                    label = { Text(stringResource(R.string.chip_bons_plans)) }
                )
            }
            items(state.topCategories.drop(1).take(1), key = { it.slug }) { c -> CategoryChipItem(c, f.category, onCategory) }
            item {
                fr.labonnealerte.core.ui.components.LbaChip(
                    f.mode == KiosqueMode.POPULAIRES,
                    { onMode(KiosqueMode.POPULAIRES) },
                    { Text(stringResource(R.string.chip_populaires)) }
                )
            }
            // Nouvelle navigation (APP-12) : les favoris quittent la rangee de puces pour Compte.
            if (!newNav || f.mode == KiosqueMode.FAVORIS) {
                item {
                    fr.labonnealerte.core.ui.components.LbaChip(f.mode == KiosqueMode.FAVORIS, { onMode(KiosqueMode.FAVORIS) }, {
                        Text(
                            "♥ " + stringResource(R.string.chip_favoris)
                        )
                    })
                }
            }
            items(state.topCategories.drop(2), key = { it.slug }) { c -> CategoryChipItem(c, f.category, onCategory) }
            item {
                val sel = (f.category != null && state.topCategories.none { it.slug == f.category }) || (f.group != null && f.group != BONS_PLANS)
                fr.labonnealerte.core.ui.components.LbaChip(sel, onOpenSheet, { Text("＋ " + stringResource(R.string.chip_plus)) })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * f) En-tete du kiosque aux couleurs de la marque (option d'affichage, APP-11) : « labonne » en
 * violet, « alerte » en vert, centre. TalkBack lit « La Bonne Alerte, Kiosque » (titre).
 */
private const val BONS_PLANS = "bons-plans-conso"

@Composable
private fun CategoryChipItem(c: CategoryChip, selected: String?, onCategory: (String?) -> Unit) {
    fr.labonnealerte.core.ui.components.LbaChip(
        selected = selected == c.slug,
        onClick = { onCategory(if (selected == c.slug) null else c.slug) },
        label = { Text(stringResource(R.string.chip_categorie, c.label)) }
    )
}

@Composable
internal fun BrandHeader() {
    val violet = MaterialTheme.colorScheme.primary
    val vert = LbaThemeExtras.stateColors.calm
    val description = stringResource(R.string.kiosque_marque_cd)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = violet)) { append("labonne") }
            withStyle(SpanStyle(color = vert)) { append("alerte") }
        },
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics {
            heading()
            contentDescription = description
        }
    )
}

@Composable
private fun SortMenu(current: KiosqueSort, onSort: (KiosqueSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.trier))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                KiosqueSort.SITE to R.string.tri_site,
                KiosqueSort.NOM to R.string.tri_nom,
                KiosqueSort.ABONNES to R.string.tri_abonnes,
                KiosqueSort.RECENTES to R.string.tri_recentes
            ).forEach { (s, label) ->
                DropdownMenuItem(
                    text = { Text((if (s == current) "✓ " else "") + stringResource(label)) },
                    onClick = {
                        onSort(s)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EnCeMoment(items: List<LePointActive>, onOpenSource: (String) -> Unit) {
    Column {
        Text(
            stringResource(R.string.en_ce_moment),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.sourceId }) { a ->
                val msg = SplitMessage.of(a.message)
                Card(
                    onClick = { onOpenSource(a.sourceId) },
                    modifier = Modifier.widthIn(min = 260.dp, max = 320.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            (msg.emoji?.let { "$it " } ?: "") + a.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        val text = if (a.count != null) {
                            pluralStringResource(R.plurals.en_ce_moment_zones, a.count!!, a.count!!) + "\u00A0: " + a.labels.joinToString(", ")
                        } else {
                            msg.text
                        }
                        Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CategoriesSheet(all: List<CategoryChip>, @Suppress("UNUSED_PARAMETER") onPick: (String) -> Unit, onPickGroup: (String) -> Unit) {
    val byGroup = remember(all) {
        all.groupBy { it.group ?: "autre" }.toList().sortedByDescending { (_, l) -> l.sumOf { it.count } }
    }
    Text(
        stringResource(R.string.feuille_categories_titre),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() }
    )
    LazyColumn(Modifier.fillMaxWidth().widthIn(max = 640.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
        byGroup.forEach { (group, cats) ->
            val fam = Famille.of(group)
            item(key = "g-$group") {
                val label = stringResource(fam.label)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button) { onPickGroup(group) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(fam.emoji, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    // Titre et icone seulement (APP-14).
                    Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
            }
            // Categories principales seulement (APP-13) : les sous-categories ne sont plus listees.
            item(key = "d-$group") { HorizontalDivider(Modifier.background(MaterialTheme.colorScheme.outlineVariant)) }
        }
    }
}
