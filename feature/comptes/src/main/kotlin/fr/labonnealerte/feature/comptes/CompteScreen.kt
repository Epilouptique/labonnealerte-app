package fr.labonnealerte.feature.comptes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.labonnealerte.core.model.Account
import fr.labonnealerte.core.model.Geo
import fr.labonnealerte.core.ui.components.DepartementPickerDialog

/** Liens secondaires de l'accueil (fournis par l'app). */
data class CompteLiens(
    val onReglages: () -> Unit,
    val onAPropos: () -> Unit,
    val onConfidentialite: () -> Unit,
    val onCodeSource: () -> Unit,
    val onSoutenir: () -> Unit,
    /** Publication communautaire (APP-21) : entree depuis Compte avec l'ancienne navigation. */
    val onPublier: () -> Unit = {}
)

/**
 * Onglet Compte (APP-05, decision D). Non connecte : accueil simple, bouton principal vers
 * l'ecran de connexion plein ecran, liens secondaires et Reglages ; rien d'autre.
 * Connecte : [problemBanner] (seulement si un probleme de notifications est detecte), profil,
 * pseudo, Reglages, deconnexion, suppression.
 */
@Composable
fun CompteRoute(
    onOpenConnexion: () -> Unit,
    liens: CompteLiens,
    problemBanner: @Composable () -> Unit = {},
    /** Ecran en blocs (option « Nouvelle navigation », APP-12). */
    blocs: CompteBlocsActions = CompteBlocsActions(),
    viewModel: CompteViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val msgText = state.message?.let { stringResource(it.res()) }
    LaunchedEffect(state.message) {
        if (msgText != null) {
            snackbar.showSnackbar(msgText)
            viewModel.consumeMessage()
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)) { p ->
        Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.compte_titre),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics {
                        heading()
                    }
                )
                fr.labonnealerte.core.ui.components.HeaderDividerLine(bleed = 20.dp)
                val acc = state.account
                if (fr.labonnealerte.core.ui.LocalDisplayLab.current.newNavigation) {
                    CompteBlocsContent(state.signedIn, acc, liens, blocs, onOpenConnexion, problemBanner = problemBanner)
                } else if (!state.signedIn) {
                    CompteAccueil(onOpenConnexion, liens)
                } else if (acc != null) {
                    problemBanner()
                    AccountContent(acc, state.geo, state.busy, viewModel, liens.onReglages, liens.onPublier)
                } else {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/** Accueil d'une personne non connectee : un bouton principal, des liens secondaires, Reglages. */
@Composable
fun CompteAccueil(onOpenConnexion: () -> Unit, liens: CompteLiens) {
    SectionCard {
        Text(stringResource(R.string.connexion_intro), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onOpenConnexion, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.connexion_titre), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        LienSecondaire(stringResource(R.string.accueil_a_propos), liens.onAPropos)
        LienSecondaire(stringResource(R.string.accueil_confidentialite), liens.onConfidentialite)
        LienSecondaire(stringResource(R.string.accueil_code_source), liens.onCodeSource)
        LienSecondaire(stringResource(R.string.accueil_soutenir), liens.onSoutenir)
    }
    HorizontalDivider()
    LienSecondaire(stringResource(R.string.accueil_reglages), liens.onReglages, stringResource(R.string.accueil_reglages_aide))
}

@Composable
private fun LienSecondaire(label: String, onClick: () -> Unit, help: String? = null) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            help?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
internal fun SectionCard(content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountContent(acc: Account, geo: Geo?, busy: Boolean, vm: CompteViewModel, onReglages: () -> Unit, onPublier: () -> Unit) {
    var showLogout by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Text(stringResource(R.string.connecte_en_tant_que, acc.email), style = MaterialTheme.typography.bodyMedium)

    ProfileCard(acc, geo, busy, vm)
    PseudoCard(acc, vm)

    OutlinedButton(onClick = onPublier, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.publier_alerte)) }
    OutlinedButton(onClick = onReglages, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.accueil_reglages)) }
    OutlinedButton(onClick = { showLogout = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.deconnexion)) }
    TextButton(
        onClick = { showDelete = true },
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource(R.string.supprimer_compte)) }
    Spacer(Modifier.height(8.dp))

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text(stringResource(R.string.deconnexion_confirmer_titre)) },
            text = { Text(stringResource(R.string.deconnexion_confirmer_texte)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogout = false
                    vm.logout()
                }) { Text(stringResource(R.string.deconnexion)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) { Text(stringResource(fr.labonnealerte.core.ui.R.string.annuler)) }
            }
        )
    }
    if (showDelete) {
        DeleteConfirmDialog(onConfirm = {
            showDelete = false
            vm.deleteAccount()
        }, onDismiss = { showDelete = false })
    }
}

/** Localisation du profil (facultative) : pre-remplit les alertes locales. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileCard(acc: Account, geo: Geo?, busy: Boolean, vm: CompteViewModel) {
    // Profil : le departement fait foi, la region en decoule (affichee en lecture seule).
    SectionCard {
        var country by rememberSaveable(acc.country) { mutableStateOf(acc.country ?: "FR") }
        var dept by rememberSaveable(acc.departement) { mutableStateOf(acc.departement) }
        var ville by rememberSaveable(acc.ville) { mutableStateOf(acc.ville.orEmpty()) }
        var pickDept by remember { mutableStateOf(false) }
        var countryOpen by remember { mutableStateOf(false) }

        Text(stringResource(R.string.profil_titre), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.profil_intro), style = MaterialTheme.typography.bodySmall)
        val countries = geo?.countries.orEmpty()
        ExposedDropdownMenuBox(expanded = countryOpen, onExpandedChange = { countryOpen = it }) {
            OutlinedTextField(
                value = countries.firstOrNull { it.code == country }?.name ?: country,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.profil_pays)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(countryOpen) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(expanded = countryOpen, onDismissRequest = { countryOpen = false }) {
                countries.forEach { c ->
                    DropdownMenuItem(text = { Text(c.name) }, onClick = {
                        country = c.code
                        if (c.code != "FR") dept = null
                        countryOpen = false
                    })
                }
            }
        }
        if (country == "FR") {
            val d = geo?.departements?.firstOrNull { it.code == dept }
            OutlinedButton(onClick = { pickDept = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.profil_departement) + "\u00A0: " +
                        (d?.displayLabel ?: stringResource(R.string.profil_departement_aucun))
                )
            }
            d?.region?.let { Text(stringResource(R.string.profil_region, it), style = MaterialTheme.typography.bodySmall) }
        }
        OutlinedTextField(
            value = ville,
            onValueChange = { ville = it.take(80) },
            label = { Text(stringResource(R.string.profil_ville)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { vm.saveProfile(country, dept, ville) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.profil_enregistrer))
        }
        if (pickDept && geo != null) {
            DepartementPickerDialog(geo.departements, onPick = {
                dept = it.code
                pickDept = false
            }, onDismiss = { pickDept = false })
        }
    }
}

/** Pseudo public (forum). */
@Composable
internal fun PseudoCard(acc: Account, vm: CompteViewModel) {
    SectionCard {
        var name by rememberSaveable(acc.displayName) { mutableStateOf(acc.displayName.orEmpty()) }
        Text(stringResource(R.string.pseudo_titre), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.pseudo_aide), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text(stringResource(R.string.pseudo_titre)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = {
            vm.saveDisplayName(name)
        }, enabled = name.isNotBlank() && name != acc.displayName, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pseudo_enregistrer))
        }
    }
}

/** Confirmation finale de la suppression : taper SUPPRIMER (irreversible). */
@Composable
internal fun DeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val word = stringResource(R.string.supprimer_mot)
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(stringResource(R.string.supprimer_titre)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.supprimer_texte))
                OutlinedTextField(value = typed, onValueChange = {
                    typed = it.take(20)
                }, label = { Text(stringResource(R.string.supprimer_saisie)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                },
                enabled = typed.trim().equals(word, ignoreCase = true),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.supprimer_confirmer)) }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) { Text(stringResource(fr.labonnealerte.core.ui.R.string.annuler)) }
        }
    )
}
