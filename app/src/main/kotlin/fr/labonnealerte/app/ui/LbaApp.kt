package fr.labonnealerte.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import fr.labonnealerte.app.R
import fr.labonnealerte.core.ui.components.EmptyState
import fr.labonnealerte.core.ui.openExternal
import fr.labonnealerte.feature.alerte.AlerteRoute
import fr.labonnealerte.feature.alerte.AlerteRoutes
import fr.labonnealerte.feature.alerte.HistoriqueRoute
import fr.labonnealerte.feature.alerte.MesAlertesRoute
import fr.labonnealerte.feature.communaute.CommunauteRoute
import fr.labonnealerte.feature.comptes.CompteLiens
import fr.labonnealerte.feature.comptes.CompteRoute
import fr.labonnealerte.feature.comptes.ConnexionRoute
import fr.labonnealerte.feature.comptes.PostLoginDestination
import fr.labonnealerte.feature.comptes.postLoginDestination
import fr.labonnealerte.feature.echeances.EcheancesRoute
import fr.labonnealerte.feature.forum.ForumHomeRoute
import fr.labonnealerte.feature.forum.NewTopicRoute
import fr.labonnealerte.feature.forum.ThreadRoute
import fr.labonnealerte.feature.forum.TopicListRoute
import fr.labonnealerte.feature.kiosque.KiosqueRoute
import fr.labonnealerte.feature.reglages.DiagnosticRoute
import fr.labonnealerte.feature.reglages.LicencesRoute
import fr.labonnealerte.feature.reglages.NotificationProblemBanner
import fr.labonnealerte.feature.reglages.PRIVACY_URL
import fr.labonnealerte.feature.reglages.ReglagesRoute
import fr.labonnealerte.feature.reglages.SOURCE_URL

enum class Onglet(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    KIOSQUE("kiosque", R.string.onglet_kiosque, Icons.Filled.Storefront),
    MES_ALERTES("mes-alertes", R.string.onglet_mes_alertes, Icons.Filled.Notifications),

    /** Nouvelle navigation seulement (APP-12). */
    PUBLIER(PUBLIER_ROUTE, R.string.onglet_publier, Icons.Filled.AddCircleOutline),
    FORUM("forum", R.string.onglet_forum, Icons.Filled.Forum),
    COMPTE("compte", R.string.onglet_compte, Icons.Filled.AccountCircle);

    /** Libelle : le kiosque devient « Rechercher » dans la nouvelle navigation. */
    @StringRes
    fun labelFor(newNavigation: Boolean): Int = if (newNavigation && this == KIOSQUE) R.string.onglet_rechercher else label
}

/**
 * Onglets affiches. Nouvelle navigation (option d'affichage, APP-12) :
 * Rechercher | Mes alertes | Publier | Forum | Compte. Sinon la barre habituelle, sans Publier.
 */
fun ongletsFor(newNavigation: Boolean): List<Onglet> =
    if (newNavigation) Onglet.entries.toList() else Onglet.entries.filter { it != Onglet.PUBLIER }

@Composable
fun LbaApp(windowSizeClass: WindowSizeClass, unread: Int = 0, accountBadge: AccountBadge = AccountBadge()) {
    val newNav = fr.labonnealerte.core.ui.LocalDisplayLab.current.newNavigation
    val tabs = ongletsFor(newNav)
    val nav = rememberNavController()
    // Liens profonds recus alors que l'app est deja ouverte (launchMode singleTop).
    val activity = androidx.activity.compose.LocalActivity.current as? androidx.activity.ComponentActivity
    androidx.compose.runtime.DisposableEffect(activity, nav) {
        val listener = androidx.core.util.Consumer<android.content.Intent> { intent -> nav.handleDeepLink(intent) }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }
    val large = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    val onSelect: (Onglet) -> Unit = { o ->
        nav.navigate(o.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    // Fiche d'alerte d'ou l'utilisateur est parti se connecter (APP-04) : rouverte une fois connecte.
    val returnTo = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    fun selected(o: Onglet) = current?.hierarchy?.any { it.route == o.route } == true

    // Connexion / creation : plein ecran, sans navigation (APP-05, decision B).
    if (current?.route == CONNEXION_ROUTE) {
        Scaffold { p -> LbaNavHost(nav, onSelect, Modifier.padding(p), returnTo, unread = unread) }
        return
    }

    if (large) {
        // Tablette / pliable / paysage : rail de navigation lateral.
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                tabs.forEach { o ->
                    NavigationRailItem(
                        selected = selected(o),
                        onClick = { onSelect(o) },
                        icon = { TabIcon(o, unread, newNav, accountBadge) },
                        label = { TabLabel(stringResource(o.labelFor(newNav))) }
                    )
                }
            }
            Scaffold { p ->
                LbaNavHost(
                    nav,
                    onSelect,
                    Modifier.padding(p),
                    returnTo,
                    twoPane = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded,
                    unread = unread
                )
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                // APP-13 : un peu d'air a gauche de « Rechercher » ; pas de fond sous l'onglet choisi.
                NavigationBar(
                    windowInsets = androidx.compose.material3.NavigationBarDefaults.windowInsets
                        .add(androidx.compose.foundation.layout.WindowInsets(left = 8.dp, right = 8.dp))
                ) {
                    tabs.forEach { o ->
                        NavigationBarItem(
                            selected = selected(o),
                            onClick = { onSelect(o) },
                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                            ),
                            icon = { TabIcon(o, unread, newNav, accountBadge) },
                            label = { TabLabel(stringResource(o.labelFor(newNav)), compact = newNav) }
                        )
                    }
                }
            }
        ) { p -> LbaNavHost(nav, onSelect, Modifier.padding(p), returnTo, unread = unread) }
    }
}

@Composable
private fun LbaNavHost(
    nav: NavHostController,
    onSelectTab: (Onglet) -> Unit,
    modifier: Modifier,
    returnTo: androidx.compose.runtime.MutableState<String?>,
    twoPane: Boolean = false,
    unread: Int = 0
) {
    val accueil: AccueilViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
    val welcomePending by accueil.welcomePending.collectAsState()
    // Bandeau d'accueil : pris une fois (le drapeau persistant est efface aussitot), puis visible
    // jusqu'a sa fermeture ou jusqu'a ce que la personne quitte le kiosque.
    var showWelcome by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(welcomePending) {
        if (welcomePending) {
            showWelcome = true
            accueil.consume()
        }
    }
    val openConnexion: () -> Unit = { nav.navigate(CONNEXION_ROUTE_NAV) }
    val problemBanner: @Composable () -> Unit = {
        NotificationProblemBanner(onFix = { nav.navigate("diagnostic") })
    }
    NavHost(nav, startDestination = Onglet.KIOSQUE.route, modifier = modifier) {
        composable(Onglet.KIOSQUE.route) {
            DisposableEffect(Unit) { onDispose { showWelcome = false } }
            val banner: @Composable () -> Unit = { if (showWelcome) BandeauAccueil(onDismiss = { showWelcome = false }) }
            if (twoPane) {
                KiosqueDeuxVolets(openConnexion, banner)
            } else {
                KiosqueRoute(onOpenSource = { id -> nav.navigate(AlerteRoutes.route(id)) }, topBanner = banner, onRequestLogin = openConnexion)
            }
        }
        composable("raccourci/{cible}", deepLinks = listOf(navDeepLink { uriPattern = "labonnealerte://raccourci/{cible}" })) { entry ->
            when (entry.arguments?.getString("cible")) {
                "mes-alertes" -> androidx.compose.runtime.LaunchedEffect(Unit) { onSelectTab(Onglet.MES_ALERTES) }
                "favoris" -> KiosqueRoute(
                    onOpenSource = { id -> nav.navigate(AlerteRoutes.route(id)) },
                    initialMode = fr.labonnealerte.core.model.KiosqueMode.FAVORIS,
                    onRequestLogin = openConnexion
                )
                else -> KiosqueRoute(onOpenSource = { id -> nav.navigate(AlerteRoutes.route(id)) }, focusSearch = true, onRequestLogin = openConnexion)
            }
        }
        composable(Onglet.COMPTE.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val reglagesVm: fr.labonnealerte.feature.reglages.ReglagesViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
            val support by reglagesVm.support.collectAsState()
            CompteRoute(
                onOpenConnexion = openConnexion,
                liens = CompteLiens(
                    onReglages = { nav.navigate("reglages") },
                    onPublier = { nav.navigate(PUBLIER_ROUTE) },
                    onAPropos = { context.openExternal(ABOUT_URL) },
                    onConfidentialite = { context.openExternal(PRIVACY_URL) },
                    onCodeSource = { context.openExternal(SOURCE_URL) },
                    onSoutenir = { context.openExternal(SUPPORT_URL) }
                ),
                problemBanner = problemBanner,
                blocs = fr.labonnealerte.feature.comptes.CompteBlocsActions(
                    onOpenProfil = { nav.navigate(PROFIL_ROUTE) },
                    onOpenMesAlertes = { onSelectTab(Onglet.MES_ALERTES) },
                    onOpenFavoris = { nav.navigate(FAVORIS_ROUTE) },
                    onOpenEcheances = { nav.navigate("echeances") },
                    onOpenCommunaute = { nav.navigate("communaute") },
                    onOpenDiagnostic = { nav.navigate("diagnostic") },
                    unread = unread,
                    onDonKofi = support.kofi?.let { url -> { context.openExternal(url) } },
                    onDonPaypal = support.paypal?.let { url -> { context.openExternal(url) } },
                    onSuggestions = { nav.navigate("forum/c/suggestions") }
                )
            )
        }
        composable(PROFIL_ROUTE) { fr.labonnealerte.feature.comptes.ProfilRoute(onBack = { nav.popBackStack() }) }
        // Favoris, ouverts depuis Compte (nouvelle navigation, APP-12) : kiosque filtre, retour au compte.
        // « Mes favoris » : page a part depuis Compte (APP-20).
        composable(FAVORIS_ROUTE) {
            fr.labonnealerte.feature.kiosque.FavorisRoute(
                onBack = { nav.popBackStack() },
                onOpenSource = { id -> nav.navigate(AlerteRoutes.route(id)) }
            )
        }
        // Publication communautaire (APP-21) : accueil, parcours, mes publications, signalement.
        val loginThen: (String) -> Unit = { route ->
            returnTo.value = route
            openConnexion()
        }
        composable(PUBLIER_ROUTE) {
            PublierScreen(
                onPropose = { nav.navigate(publierFlowRoute()) },
                onMesPublications = { nav.navigate(MES_PUBLICATIONS_ROUTE) },
                onIdees = { nav.navigate("forum/c/suggestions") }
            )
        }
        composable(
            PUBLIER_FLOW_ROUTE,
            arguments = listOf(
                androidx.navigation.navArgument("id") {
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            fr.labonnealerte.feature.comptes.PublierFlowRoute(
                onBack = { nav.popBackStack() },
                onLogin = { loginThen(publierFlowRoute(entry.arguments?.getString("id"))) },
                onMesPublications = {
                    nav.navigate(MES_PUBLICATIONS_ROUTE) { popUpTo(PUBLIER_FLOW_ROUTE) { inclusive = true } }
                }
            )
        }
        composable(MES_PUBLICATIONS_ROUTE) {
            fr.labonnealerte.feature.comptes.MesPublicationsRoute(
                onBack = { nav.popBackStack() },
                onLogin = { loginThen(MES_PUBLICATIONS_ROUTE) },
                onEdit = { id -> nav.navigate(publierFlowRoute(id)) },
                onNew = { nav.navigate(publierFlowRoute()) }
            )
        }
        composable(SIGNALER_ROUTE) { entry ->
            fr.labonnealerte.feature.comptes.SignalerSourceRoute(
                onBack = { nav.popBackStack() },
                onLogin = { loginThen("signaler/" + android.net.Uri.encode(entry.arguments?.getString("id").orEmpty())) }
            )
        }
        composable("reglages") {
            ReglagesRoute(
                versionName = fr.labonnealerte.app.BuildConfig.VERSION_NAME,
                onBack = { nav.popBackStack() },
                onOpenDiagnostic = { nav.navigate("diagnostic") },
                onOpenLicences = { nav.navigate("licences") },
                onOpenHistorique = { nav.navigate("historique") },
                onOpenDisplayLab = { nav.navigate("affichage-test") },
                showTestOptions = fr.labonnealerte.app.BuildConfig.SHOW_TEST_OPTIONS,
                bottomSlot = {
                    if (fr.labonnealerte.core.ui.LocalDisplayLab.current.newNavigation) {
                        fr.labonnealerte.feature.comptes.SupprimerCompteLien()
                    }
                }
            )
        }
        composable("affichage-test") { fr.labonnealerte.feature.reglages.DisplayLabRoute(onBack = { nav.popBackStack() }) }
        composable("licences") { LicencesRoute(onBack = { nav.popBackStack() }) }
        composable("diagnostic") {
            DiagnosticRoute(onBack = { nav.popBackStack() }, showDebugTools = fr.labonnealerte.app.BuildConfig.DEBUG)
        }
        composable(Onglet.MES_ALERTES.route) {
            MesAlertesRoute(
                onOpenSource = { id -> nav.navigate(AlerteRoutes.route(id)) },
                onRequestLogin = openConnexion,
                onOpenKiosque = { onSelectTab(Onglet.KIOSQUE) },
                headerSlot = problemBanner,
                historySlot = {
                    // Tuiles comme « Mes alertes » / « Favoris » de l'ecran Compte (APP-13).
                    Row(
                        Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                    ) {
                        fr.labonnealerte.core.ui.components.LbaTile(
                            Icons.Filled.Event,
                            stringResource(R.string.tuile_echeances),
                            stringResource(R.string.tuile_echeances_aide),
                            { nav.navigate("echeances") },
                            Modifier.weight(1f).fillMaxHeight()
                        )
                        fr.labonnealerte.core.ui.components.LbaTile(
                            Icons.Filled.Pets,
                            stringResource(R.string.tuile_animaux),
                            stringResource(R.string.tuile_animaux_aide),
                            { nav.navigate("communaute") },
                            Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            )
        }
        composable(
            "communaute?type={type}",
            arguments = listOf(
                androidx.navigation.navArgument("type") {
                    nullable = true
                    defaultValue = null
                }
            )
        ) { CommunauteRoute(onBack = { nav.popBackStack() }, onRequestLogin = openConnexion) }
        composable("echeances") { EcheancesRoute(onBack = { nav.popBackStack() }, onRequestLogin = openConnexion) }
        composable("historique") {
            HistoriqueRoute(onBack = { nav.popBackStack() }, onOpenSource = { id -> nav.navigate(AlerteRoutes.route(id)) })
        }
        // Connexion / creation plein ecran. Liens profonds conserves (lien de l'e-mail, jamais
        // consomme sans confirmation) mais plus presentes a l'ecran (APP-05, decision C).
        composable(
            CONNEXION_ROUTE,
            arguments = listOf(
                androidx.navigation.navArgument("token") {
                    nullable = true
                    defaultValue = null
                }
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "labonnealerte://connexion?token={token}" },
                navDeepLink { uriPattern = "https://labonnealerte.fr/connexion?token={token}" }
            )
        ) {
            // Quitter l'ecran sans se connecter : la fiche memorisee ne doit pas resurgir plus tard.
            DisposableEffect(Unit) { onDispose { returnTo.value = null } }
            ConnexionRoute(
                onBack = { if (!nav.popBackStack()) onSelectTab(Onglet.KIOSQUE) },
                onSignedIn = { event ->
                    val dest = postLoginDestination(returnTo.value, event)
                    returnTo.value = null
                    when (dest) {
                        // L'ecran de connexion a ete ouvert depuis la fiche : elle est juste dessous.
                        is PostLoginDestination.Back -> if (!nav.popBackStack()) nav.navigate(dest.route)
                        is PostLoginDestination.Kiosque -> {
                            if (dest.welcome) accueil.markPending()
                            nav.popBackStack()
                            onSelectTab(Onglet.KIOSQUE)
                        }
                        PostLoginDestination.MesAlertes -> {
                            nav.popBackStack()
                            onSelectTab(Onglet.MES_ALERTES)
                        }
                    }
                }
            )
        }
        composable(Onglet.FORUM.route) {
            ForumHomeRoute(
                onOpenCategory = { nav.navigate("forum/c/$it") },
                onOpenTopic = { nav.navigate("forum/t/$it") },
                onNewTopic = { nav.navigate("forum/nouveau") }
            )
        }
        val back: () -> Unit = { nav.popBackStack() }
        val openTopic: (String) -> Unit = { nav.navigate("forum/t/$it") }
        val newTopic: (String?) -> Unit = { src -> nav.navigate(if (src != null) "forum/nouveau?source=$src" else "forum/nouveau") }
        composable("forum/c/{category}") { TopicListRoute(back, openTopic, newTopic) }
        composable("forum/s/{source}") { TopicListRoute(back, openTopic, newTopic) }
        composable("forum/u/{pseudo}") { TopicListRoute(back, openTopic, newTopic) }
        composable("forum/t/{slug}") {
            ThreadRoute(back, onRequestLogin = openConnexion, onOpenMember = { nav.navigate("forum/u/$it") })
        }
        composable(
            NEW_TOPIC_ROUTE,
            arguments = listOf(
                androidx.navigation.navArgument("source") {
                    nullable = true
                    defaultValue = null
                },
                // Rubrique pre-choisie (« Publier » ouvre « Propositions », APP-12).
                androidx.navigation.navArgument("categorie") {
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            NewTopicRoute(back, onCreated = { slug ->
                nav.navigate("forum/t/$slug") {
                    popUpTo(NEW_TOPIC_ROUTE) {
                        inclusive =
                            true
                    }
                }
            })
        }
        composable(
            AlerteRoutes.ROUTE,
            deepLinks = listOf(
                navDeepLink { uriPattern = AlerteRoutes.DEEP_LINK_APP },
                navDeepLink { uriPattern = AlerteRoutes.DEEP_LINK_WEB }
            )
        ) { entry ->
            val id = AlerteRoutes.sanitizeId(entry.arguments?.getString("id"))
            AlerteRoute(
                onBack = { if (!nav.popBackStack()) nav.navigate(Onglet.KIOSQUE.route) },
                onRequestLogin = {
                    returnTo.value = id?.let { AlerteRoutes.route(it) }
                    openConnexion()
                },
                onOpenForum = { src -> nav.navigate("forum/s/$src") },
                onOpenCategory = { slug -> nav.navigate(kiosqueCategorieRoute(slug)) },
                onReport = { src -> nav.navigate("signaler/" + android.net.Uri.encode(src)) }
            )
        }
        // Kiosque filtre sur une categorie, ouvert depuis la fiche d'une alerte : le retour
        // (fleche ou geste) ramene a la fiche (APP-08).
        composable(KIOSQUE_CATEGORIE_ROUTE) { entry ->
            val slug = entry.arguments?.getString("slug")?.takeIf { CATEGORY_SLUG.matches(it) }
            KiosqueRoute(
                onOpenSource = { id -> nav.navigate(AlerteRoutes.route(id)) },
                initialCategory = slug,
                onRequestLogin = openConnexion,
                topBanner = {
                    androidx.compose.material3.TextButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Text(stringResource(R.string.retour_fiche), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            )
        }
    }
}

const val KIOSQUE_CATEGORIE_ROUTE = "kiosque/categorie/{slug}"
private const val NEW_TOPIC_ROUTE = "forum/nouveau?source={source}&categorie={categorie}"
private const val PROFIL_ROUTE = "compte/profil"
private const val FAVORIS_ROUTE = "compte/favoris"
private val CATEGORY_SLUG = Regex("^[a-z0-9][a-z0-9-]{0,63}$")

fun kiosqueCategorieRoute(slug: String) = "kiosque/categorie/" + android.net.Uri.encode(slug)

/**
 * Icone d onglet, avec pastille du nombre de notifications non lues sur « Mes alertes ».
 * Nouvelle navigation (APP-12) : loupe pour Rechercher ; pour Compte, pastille violette a
 * l'initiale du pseudo si connecte (silhouette neutre sinon). Le libelle reste sous l'icone.
 */
@Composable
private fun TabIcon(o: Onglet, unread: Int, newNav: Boolean = false, accountBadge: AccountBadge = AccountBadge()) {
    if (newNav && o == Onglet.KIOSQUE) {
        Icon(Icons.Filled.Search, contentDescription = null)
    } else if (newNav && o == Onglet.COMPTE) {
        val cd = stringResource(R.string.onglet_compte_connecte)
        fr.labonnealerte.core.ui.components.InitialAvatar(
            accountBadge.initial.takeIf { accountBadge.signedIn },
            modifier = if (accountBadge.signedIn) Modifier.semantics { contentDescription = cd } else Modifier
        )
    } else if (o == Onglet.MES_ALERTES && unread > 0) {
        val cd = androidx.compose.ui.res.pluralStringResource(R.plurals.non_lues, unread, unread)
        androidx.compose.material3.BadgedBox(badge = {
            androidx.compose.material3.Badge { Text(if (unread > 99) stringResource(R.string.badge_plus_de_99) else unread.toString()) }
        }, modifier = Modifier.semantics { contentDescription = cd }) { Icon(o.icon, contentDescription = null) }
    } else {
        Icon(o.icon, contentDescription = null)
    }
}

/**
 * Tablette / grand ecran : le kiosque a gauche (grandes cartes), le detail a droite dans une
 * colonne plus etroite, equivalent du mode « mixte » du site. Le detail a son propre NavHost.
 */
@Composable
private fun KiosqueDeuxVolets(onRequestLogin: () -> Unit, topBanner: @Composable () -> Unit) {
    val detailNav = rememberNavController()
    Row(Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Box(Modifier.weight(0.58f)) {
            KiosqueRoute(onOpenSource = { id ->
                detailNav.navigate(AlerteRoutes.route(id)) { popUpTo("vide") { inclusive = false } }
            }, topBanner = topBanner, onRequestLogin = onRequestLogin)
        }
        androidx.compose.material3.VerticalDivider()
        androidx.compose.foundation.layout.Box(Modifier.weight(0.42f)) {
            NavHost(detailNav, startDestination = "vide") {
                composable("vide") {
                    EmptyState(title = stringResource(R.string.deux_volets_titre), message = stringResource(R.string.deux_volets_message))
                }
                composable(AlerteRoutes.ROUTE) {
                    AlerteRoute(onBack = { detailNav.popBackStack() }, onRequestLogin = onRequestLogin)
                }
            }
        }
    }
}

/**
 * Libelle d'onglet : une seule ligne, et taille plafonnee a 130 % (comme les barres systeme) ;
 * a 200 % les libelles se coupaient en plein mot (« Kiosqu/e », APP-03). Le contenu des ecrans,
 * lui, suit la taille de police choisie sans plafond.
 */
@Composable
private fun TabLabel(text: String, compact: Boolean = false) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Cinq onglets (nouvelle navigation, APP-12) : plafond a 100 %, sinon « Mes alertes » est coupe.
    val max = if (compact) MAX_TAB_FONT_SCALE_5 else MAX_TAB_FONT_SCALE
    val capped = androidx.compose.ui.unit.Density(density.density, minOf(density.fontScale, max))
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides capped) {
        Text(text, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

private const val MAX_TAB_FONT_SCALE = 1.3f
private const val MAX_TAB_FONT_SCALE_5 = 1.0f

/** Ecran de connexion : route declaree (jeton facultatif du lien profond) et route a ouvrir. */
private const val CONNEXION_ROUTE = "connexion?token={token}"
private const val CONNEXION_ROUTE_NAV = "connexion"

private const val ABOUT_URL = "https://labonnealerte.fr/a-propos"
private const val SUPPORT_URL = "https://labonnealerte.fr/soutenir"
