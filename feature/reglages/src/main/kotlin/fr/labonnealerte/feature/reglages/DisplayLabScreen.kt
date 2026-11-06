package fr.labonnealerte.feature.reglages

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.labonnealerte.core.model.DisplayLab

/** Libelle et aide de chaque option, dans l'ordre de [DisplayLab.Option]. */
internal fun labTexts(o: DisplayLab.Option): Pair<Int, Int> = when (o) {
    DisplayLab.Option.OFFICIAL_ICON -> R.string.lab_officiel to R.string.lab_officiel_aide
    DisplayLab.Option.CATEGORY_COLOR -> R.string.lab_couleur to R.string.lab_couleur_aide
    DisplayLab.Option.ACTIVE_ICON -> R.string.lab_icone_en_cours to R.string.lab_icone_en_cours_aide
    DisplayLab.Option.ACTIVE_PULSE -> R.string.lab_pulsation to R.string.lab_pulsation_aide
    DisplayLab.Option.ACTIVE_HALO -> R.string.lab_halo to R.string.lab_halo_aide
    DisplayLab.Option.BRAND_HEADER -> R.string.lab_marque to R.string.lab_marque_aide
    DisplayLab.Option.NEW_NAVIGATION -> R.string.lab_navigation to R.string.lab_navigation_aide
    DisplayLab.Option.PILL_FOLLOW -> R.string.lab_pilule to R.string.lab_pilule_aide
    DisplayLab.Option.HEADER_DIVIDER -> R.string.lab_filet to R.string.lab_filet_aide
    DisplayLab.Option.SETTINGS_ROWS -> R.string.lab_lignes to R.string.lab_lignes_aide
    DisplayLab.Option.HIDE_SUBTITLE -> R.string.lab_sans_sous_titre to R.string.lab_sans_sous_titre_aide
}

/** Reglages > Apparence > « Options d'affichage (test) » (APP-11). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayLabRoute(onBack: () -> Unit, viewModel: ReglagesViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            fr.labonnealerte.core.ui.components.HeaderWithDivider {
                TopAppBar(
                    title = { Text(stringResource(R.string.lab_titre)) },
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
            DisplayLabContent(s.lab, viewModel::setLabOption, viewModel::resetLab)
        }
    }
}

@Composable
internal fun DisplayLabContent(lab: DisplayLab, onSet: (DisplayLab.Option, Boolean) -> Unit, onReset: () -> Unit) {
    Column(
        Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            stringResource(R.string.lab_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DisplayLab.Option.entries.forEach { o ->
            val (title, help) = labTexts(o)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(value = lab[o], role = Role.Switch) { onSet(o, it) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = lab[o], onCheckedChange = null)
            }
        }
        OutlinedButton(
            onClick = onReset,
            enabled = !lab.isDefault,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp)
        ) { Text(stringResource(R.string.lab_reset)) }
    }
}
