package fr.labonnealerte.feature.comptes

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.labonnealerte.core.ui.openExternal
import kotlinx.coroutines.delay

/**
 * Creation de compte ou connexion, en plein ecran (APP-05, decision B) : pas de barre de
 * navigation, pas de reglages ni de diagnostic, seulement une fleche de retour.
 * Adresse -> code -> (creation seulement) conditions. [onSignedIn] : l'app choisit l'ecran suivant.
 * Ouvert aussi par un lien profond labonnealerte://connexion?token=… (confirmation demandee).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnexionRoute(onBack: () -> Unit, onSignedIn: (SignedInEvent) -> Unit, viewModel: CompteViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val createdText = stringResource(R.string.msg_compte_cree)
    LaunchedEffect(state.signedInEvent) {
        state.signedInEvent?.let { event ->
            viewModel.consumeSignedIn()
            if (event.created) android.widget.Toast.makeText(context, createdText, android.widget.Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
            onSignedIn(event)
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val msgText = state.message?.let { stringResource(it.res()) }
    LaunchedEffect(state.message) {
        if (msgText != null && state.signedInEvent == null) {
            snackbar.showSnackbar(msgText)
            viewModel.consumeMessage()
        }
    }
    // Retour : d'abord a l'etape de l'adresse, puis hors de l'ecran.
    val back: () -> Unit = { if (state.step == LoginStep.EMAIL) onBack() else viewModel.backToEmail() }
    BackHandler(enabled = state.step != LoginStep.EMAIL, onBack = viewModel::backToEmail)
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { p ->
        // Paysage : le clavier laisse tres peu de hauteur. Pas de barre fixe en haut (la fleche
        // defile avec le contenu) et imePadding : le champ actif reste visible (APP-06, point 6).
        // Nouvelle etape : on repart du haut (sinon le titre reste hors champ en paysage).
        val scroll = rememberScrollState()
        LaunchedEffect(state.step) { scroll.scrollTo(0) }
        Box(Modifier.padding(p).fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(onClick = back, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.connexion_retour))
                }
                when (state.step) {
                    LoginStep.EMAIL -> {
                        EmailStep(state.email, state.busy, viewModel::onEmail, viewModel::requestCode)
                        if (viewModel.googleEnabled) GoogleButton(state.busy) { viewModel.signInWithGoogle(context) }
                    }
                    LoginStep.CODE -> CodeStep(
                        email = state.email,
                        busy = state.busy,
                        error = state.codeError,
                        attempt = state.codeAttempt,
                        resendAt = state.resendAt,
                        onSubmit = viewModel::verifyCode,
                        onModify = viewModel::backToEmail,
                        onResend = viewModel::requestCode
                    )
                    LoginStep.TERMS -> TermsStep(
                        email = state.email,
                        termsUrl = state.termsUrl,
                        accepted = state.termsAccepted,
                        busy = state.busy,
                        onChecked = viewModel::onTermsChecked,
                        onCreate = viewModel::createAccount,
                        onModify = viewModel::backToEmail,
                        onOpenTerms = { url -> context.openExternal(url) }
                    )
                }
            }
        }
    }
    if (state.pendingLink != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelPendingLink,
            title = { Text(stringResource(R.string.lien_confirmer_titre)) },
            text = { Text(stringResource(R.string.lien_confirmer_texte)) },
            confirmButton = { TextButton(onClick = viewModel::confirmPendingLink) { Text(stringResource(R.string.lien_confirmer_oui)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelPendingLink) { Text(stringResource(fr.labonnealerte.core.ui.R.string.annuler)) } }
        )
    }
}

@Composable
private fun Spinner() {
    val loading = stringResource(fr.labonnealerte.core.ui.R.string.chargement)
    CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = loading })
}

@Composable
private fun StepTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
}

@Composable
fun EmailStep(email: String, busy: Boolean, onEmail: (String) -> Unit, onSubmit: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    StepTitle(stringResource(R.string.connexion_titre))
    Text(stringResource(R.string.connexion_email_aide), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = email,
        onValueChange = onEmail,
        label = { Text(stringResource(R.string.connexion_email)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { if (!busy) onSubmit() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus)
    )
    Button(onClick = onSubmit, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        if (busy) Spinner() else Text(stringResource(R.string.connexion_envoyer))
    }
}

/** APP-22 : « Continuer avec Google », sous le code par e-mail. */
@Composable
fun GoogleButton(busy: Boolean, onClick: () -> Unit) {
    Text(
        stringResource(R.string.connexion_ou),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
    androidx.compose.material3.OutlinedButton(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.connexion_google))
    }
}

/** Ne garde que les chiffres (collage « 123 456 » ou « 123-456 »), 6 au plus. */
fun cleanCode(raw: String): String = raw.filter { it in '0'..'9' }.take(CODE_LENGTH)

const val CODE_LENGTH = 6

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CodeStep(
    email: String,
    busy: Boolean,
    error: CompteMessage?,
    attempt: Int,
    resendAt: Long,
    onSubmit: (String) -> Unit,
    onModify: () -> Unit,
    onResend: () -> Unit,
    now: () -> Long = System::currentTimeMillis
) {
    // Nouvel essai apres une erreur : champ vide et focus remis dessus.
    var code by rememberSaveable(attempt) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(attempt) { runCatching { focus.requestFocus() } }

    StepTitle(stringResource(R.string.connexion_code_titre))
    FlowRow(verticalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.connexion_code_envoye, email), style = MaterialTheme.typography.bodyMedium)
        val modifyDescription = stringResource(R.string.connexion_modifier_description)
        TextButton(
            onClick = onModify,
            enabled = !busy,
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = modifyDescription }
        ) { Text(stringResource(R.string.connexion_modifier)) }
    }
    Text(stringResource(R.string.connexion_code_validite), style = MaterialTheme.typography.bodySmall)
    val errorText = error?.let { stringResource(it.res()) }
    OutlinedTextField(
        value = code,
        onValueChange = { v ->
            code = cleanCode(v)
            if (code.length == CODE_LENGTH && !busy) onSubmit(code)
        },
        label = { Text(stringResource(R.string.connexion_code)) },
        singleLine = true,
        isError = errorText != null,
        supportingText = errorText?.let { t ->
            {
                Text(
                    t,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        error(t)
                    }
                )
            }
        },
        textStyle = MaterialTheme.typography.headlineSmall,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (code.length == CODE_LENGTH && !busy) onSubmit(code) }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus)
    )
    Button(
        onClick = { onSubmit(code) },
        enabled = code.length == CODE_LENGTH && !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    ) {
        if (busy) Spinner() else Text(stringResource(R.string.connexion_valider))
    }
    ResendButton(resendAt, busy, onResend, now)
}

/** « Renvoyer le code », reactive apres un decompte (le serveur limite les envois). */
@Composable
private fun ResendButton(resendAt: Long, busy: Boolean, onResend: () -> Unit, now: () -> Long) {
    var current by remember { mutableLongStateOf(now()) }
    LaunchedEffect(resendAt) {
        current = now()
        while (current < resendAt) {
            delay(TICK_MS)
            current = now()
        }
    }
    val seconds = ((resendAt - current + TICK_MS - 1) / TICK_MS).coerceAtLeast(0)
    TextButton(
        onClick = onResend,
        enabled = seconds == 0L && !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        Text(
            if (seconds > 0) {
                stringResource(R.string.connexion_renvoyer_dans, seconds.toInt())
            } else {
                stringResource(R.string.connexion_renvoyer)
            }
        )
    }
}

private const val TICK_MS = 1_000L

/** Creation de compte : adresse sans compte, code correct. Conditions a accepter (case + lien). */
@Composable
fun TermsStep(
    email: String,
    termsUrl: String?,
    accepted: Boolean,
    busy: Boolean,
    onChecked: (Boolean) -> Unit,
    onCreate: () -> Unit,
    onModify: () -> Unit,
    onOpenTerms: (String) -> Unit
) {
    StepTitle(stringResource(R.string.creation_titre))
    Text(stringResource(R.string.creation_intro, email), style = MaterialTheme.typography.bodyMedium)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = accepted, role = Role.Checkbox, onValueChange = onChecked)
    ) {
        Checkbox(checked = accepted, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.creation_accepter), style = MaterialTheme.typography.bodyMedium)
    }
    termsUrl?.let { url ->
        TextButton(onClick = { onOpenTerms(url) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.creation_lire)) }
    }
    Button(
        onClick = onCreate,
        enabled = accepted && !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    ) {
        if (busy) Spinner() else Text(stringResource(R.string.creation_valider))
    }
    TextButton(onClick = onModify, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.connexion_autre_email))
    }
}

@Suppress("CyclomaticComplexMethod") // simple table message -> chaine
internal fun CompteMessage.res(): Int = when (this) {
    CompteMessage.CODE_INVALIDE -> R.string.msg_code_invalide
    CompteMessage.CODE_EXPIRE -> R.string.msg_code_expire
    CompteMessage.TROP_ESSAIS -> R.string.msg_trop_essais
    CompteMessage.HORS_LIGNE -> R.string.msg_hors_ligne
    CompteMessage.ERREUR_SERVEUR -> R.string.msg_erreur_serveur
    CompteMessage.EMAIL_INVALIDE -> R.string.msg_email_invalide
    CompteMessage.LIEN_INVALIDE -> R.string.msg_lien_invalide
    CompteMessage.PROFIL_ENREGISTRE -> R.string.msg_profil_enregistre
    CompteMessage.PSEUDO_PRIS -> R.string.msg_pseudo_pris
    CompteMessage.PSEUDO_LIMITE -> R.string.msg_pseudo_limite
    CompteMessage.SESSION_EXPIREE -> R.string.msg_session_expiree
    CompteMessage.COMPTE_SUPPRIME -> R.string.msg_compte_supprime
    CompteMessage.COMPTE_CREE -> R.string.msg_compte_cree
    CompteMessage.GOOGLE_INDISPONIBLE -> R.string.msg_google_indisponible
    CompteMessage.GOOGLE_AUDIENCE -> R.string.msg_google_audience
    CompteMessage.GOOGLE_REFUSE -> R.string.msg_google_refuse
}
