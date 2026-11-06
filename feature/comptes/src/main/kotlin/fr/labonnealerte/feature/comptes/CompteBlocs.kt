package fr.labonnealerte.feature.comptes

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.labonnealerte.core.model.Account
import fr.labonnealerte.core.ui.components.HeaderWithDivider
import fr.labonnealerte.core.ui.components.InitialAvatar
import fr.labonnealerte.core.ui.components.NavRow
import fr.labonnealerte.core.ui.components.initialOf

/** Destinations de l'ecran Compte en blocs (nouvelle navigation, APP-12). */
class CompteBlocsActions(
    val onOpenProfil: () -> Unit = {},
    val onOpenMesAlertes: () -> Unit = {},
    val onOpenFavoris: () -> Unit = {},
    val onOpenEcheances: () -> Unit = {},
    val onOpenCommunaute: () -> Unit = {},
    val onOpenDiagnostic: () -> Unit = {},
    /** Dons (memes liens que Reglages > A propos) ; null : lien indisponible. */
    val onDonKofi: (() -> Unit)? = null,
    val onDonPaypal: (() -> Unit)? = null,
    /** Rubrique du forum pour les idees et les bugs. */
    val onSuggestions: () -> Unit = {},
    /** Alertes en cours non vues : meme nombre que la pastille de l'onglet Mes alertes. */
    val unread: Int = 0
)

/**
 * Ecran Compte en blocs (APP-12, option « Nouvelle navigation »), schema inspire de l'ecran
 * « Mon compte » d'une app de petites annonces : carte profil, deux tuiles cote a cote, puis
 * rubriques en lignes. Ne demande ni departement ni ville : la localisation est dans « Mon
 * profil », facultative.
 */
@Composable
internal fun CompteBlocsContent(
    signedIn: Boolean,
    account: Account?,
    liens: CompteLiens,
    actions: CompteBlocsActions,
    onOpenConnexion: () -> Unit,
    problemBanner: @Composable () -> Unit = {}
) {
    if (signedIn) problemBanner()
    when {
        !signedIn -> ProfileHeaderCard(
            initial = null,
            title = stringResource(R.string.blocs_connexion_titre),
            subtitle = stringResource(R.string.blocs_connexion_aide),
            onClick = onOpenConnexion
        )
        account != null -> ProfileHeaderCard(
            initial = initialOf(account.displayName, account.email),
            title = account.displayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.blocs_sans_pseudo),
            subtitle = stringResource(R.string.blocs_profil_modifier),
            onClick = actions.onOpenProfil
        )
        else -> CircularProgressIndicator()
    }
    // Grande police (150 % et plus) : tuiles l'une sous l'autre, sinon les mots se coupent.
    val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale >= STACK_FONT_SCALE
    TilesLayout(stacked) { tileModifier ->
        fr.labonnealerte.core.ui.components.LbaTile(
            icon = Icons.Filled.Notifications,
            title = stringResource(R.string.tuile_mes_alertes),
            subtitle = if (actions.unread > 0) {
                pluralStringResource(R.plurals.tuile_en_cours, actions.unread, actions.unread)
            } else {
                stringResource(R.string.tuile_mes_alertes_aide)
            },
            badge = actions.unread,
            onClick = actions.onOpenMesAlertes,
            modifier = tileModifier
        )
        fr.labonnealerte.core.ui.components.LbaTile(
            icon = Icons.Filled.Favorite,
            title = stringResource(R.string.tuile_favoris),
            subtitle = stringResource(R.string.tuile_favoris_aide),
            onClick = actions.onOpenFavoris,
            modifier = tileModifier
        )
    }
    // APP-13 : doublons retires (diagnostic, echeances, animaux, a propos, confidentialite, soutenir).
    Column(Modifier.fillMaxWidth()) {
        NavRow(
            stringResource(R.string.accueil_reglages),
            liens.onReglages,
            stringResource(R.string.ligne_reglages_aide),
            Icons.Filled.Settings,
            divider = false
        )
        // APP-15 : Code source dans « Nous soutenir », deconnexion dans « Mon profil ».
    }
    NousSoutenir(actions, liens.onCodeSource)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun TilesLayout(stacked: Boolean, content: @Composable (Modifier) -> Unit) {
    if (stacked) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { content(Modifier.fillMaxWidth()) }
    } else {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            content(Modifier.weight(1f))
        }
    }
}

private const val STACK_FONT_SCALE = 1.5f

/** Bloc « Nous soutenir » : dons et idees ou bugs (APP-13). */
@Composable
private fun NousSoutenir(actions: CompteBlocsActions, onCodeSource: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.soutenir_titre), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.soutenir_texte), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        actions.onDonKofi?.let { NavRow(stringResource(R.string.soutenir_kofi), it, icon = Icons.Filled.VolunteerActivism) }
        actions.onDonPaypal?.let { NavRow(stringResource(R.string.soutenir_paypal), it, icon = Icons.Filled.VolunteerActivism) }
        NavRow(
            stringResource(R.string.soutenir_suggestions),
            actions.onSuggestions,
            stringResource(R.string.soutenir_suggestions_aide),
            Icons.Filled.Lightbulb
        )
        NavRow(stringResource(R.string.accueil_code_source), onCodeSource, icon = Icons.Filled.Code, divider = false)
    }
}

/** Carte profil : pastille a initiale (ou silhouette), titre, sous-titre, fleche. */
@Composable
private fun ProfileHeaderCard(initial: Char?, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            InitialAvatar(initial, size = 48.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * « Mon profil » (APP-12) : pseudo et localisation. La localisation est facultative ; elle ne
 * sert qu'a pre-remplir les alertes locales (meteo, eau, etc.), qui la redemandent au besoin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilRoute(onBack: () -> Unit, viewModel: CompteViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val msgText = state.message?.let { stringResource(it.res()) }
    LaunchedEffect(state.message) {
        if (msgText != null) {
            snackbar.showSnackbar(msgText)
            viewModel.consumeMessage()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            HeaderWithDivider {
                TopAppBar(
                    title = { Text(stringResource(R.string.profil_ecran_titre)) },
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
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val acc = state.account
                if (acc == null) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(R.string.connecte_en_tant_que, acc.email), style = MaterialTheme.typography.bodyMedium)
                    PseudoCard(acc, viewModel)
                    ProfileCard(acc, state.geo, state.busy, viewModel)
                    LogoutRow {
                        viewModel.logout()
                        onBack()
                    }
                }
            }
        }
    }
}

/**
 * « Supprimer mon compte » en bas des Reglages (nouvelle navigation, APP-12) : texte simple,
 * couleur sobre, confirmation en deux temps (explication, puis saisie de SUPPRIMER). Rien si
 * personne n'est connecte.
 */
@Composable
fun SupprimerCompteLien(viewModel: CompteViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val msgText = state.message?.let { stringResource(it.res()) }
    LaunchedEffect(state.message) {
        if (msgText != null) {
            Toast.makeText(ctx, msgText, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }
    if (state.signedIn) SupprimerCompteContent(busy = state.busy, onDelete = viewModel::deleteAccount)
}

/** Lien discret et confirmation en deux temps (sans ViewModel, pour les tests). */
@Composable
internal fun SupprimerCompteContent(busy: Boolean, onDelete: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val label = stringResource(R.string.supprimer_compte)
    TextButton(
        onClick = { step = 1 },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 24.dp).semantics { contentDescription = label }
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    when (step) {
        1 -> AlertDialog(
            onDismissRequest = { step = 0 },
            title = { Text(stringResource(R.string.supprimer_etape1_titre)) },
            text = { Text(stringResource(R.string.supprimer_etape1_texte)) },
            confirmButton = { TextButton(onClick = { step = 2 }) { Text(stringResource(R.string.supprimer_continuer)) } },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text(stringResource(fr.labonnealerte.core.ui.R.string.annuler)) } }
        )
        2 -> DeleteConfirmDialog(onConfirm = {
            step = 0
            onDelete()
        }, onDismiss = { step = 0 })
    }
}

/** « Se deconnecter » avec confirmation (dans « Mon profil », APP-15). */
@Composable
private fun LogoutRow(onLogout: () -> Unit) {
    var showLogout by remember { mutableStateOf(false) }
    NavRow(stringResource(R.string.deconnexion), { showLogout = true }, icon = Icons.AutoMirrored.Filled.Logout, divider = false)
    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text(stringResource(R.string.deconnexion_confirmer_titre)) },
            text = { Text(stringResource(R.string.deconnexion_confirmer_texte)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogout = false
                    onLogout()
                }) { Text(stringResource(R.string.deconnexion)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) { Text(stringResource(fr.labonnealerte.core.ui.R.string.annuler)) }
            }
        )
    }
}
