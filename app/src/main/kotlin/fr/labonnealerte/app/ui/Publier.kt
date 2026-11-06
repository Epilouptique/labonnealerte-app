package fr.labonnealerte.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.labonnealerte.app.R

/**
 * Accueil de l'onglet « Publier » (APP-21) : entree du parcours communautaire du site (memes
 * routes, jeton de l'app). Deux gros boutons, et un lien discret vers le forum pour les idees.
 */
@Composable
fun PublierScreen(onPropose: () -> Unit, onMesPublications: () -> Unit, onIdees: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.publier_titre), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            fr.labonnealerte.core.ui.components.HeaderDividerLine(bleed = 20.dp)
            Text(stringResource(R.string.publier_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onPropose, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text(stringResource(R.string.publier_proposer), style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = onMesPublications, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text(stringResource(R.string.publier_mes_publications), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onIdees, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.publier_idees), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Route de l'onglet « Publier ». */
const val PUBLIER_ROUTE = "publier"

/** Parcours « Proposer une source » ; [id] : brouillon a modifier. */
const val PUBLIER_FLOW_ROUTE = "publier/source?id={id}"
const val MES_PUBLICATIONS_ROUTE = "publier/mes-publications"
const val SIGNALER_ROUTE = "signaler/{id}"

fun publierFlowRoute(id: String? = null) = if (id == null) "publier/source" else "publier/source?id=" + android.net.Uri.encode(id)
