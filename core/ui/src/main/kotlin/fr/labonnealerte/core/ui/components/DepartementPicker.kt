package fr.labonnealerte.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.labonnealerte.core.model.Departement
import fr.labonnealerte.core.model.SearchNormalizer
import fr.labonnealerte.core.ui.R

/**
 * Choix d'un departement avec recherche, affiche « 05 - Hautes-Alpes ».
 * Tri par code tel que fourni par le serveur (01, 02, 2A, 2B, 21…), recherche sur le code ou le nom.
 */
@Composable
fun DepartementPickerDialog(
    departements: List<Departement>,
    onPick: (Departement) -> Unit,
    onDismiss: () -> Unit
) {
    var q by remember { mutableStateOf("") }
    val filtered = remember(q, departements) {
        if (q.isBlank()) {
            departements
        } else {
            departements.filter { d ->
                d.code.equals(q.trim(), ignoreCase = true) ||
                    d.code.startsWith(q.trim(), ignoreCase = true) ||
                    SearchNormalizer.matches(d.name, q)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.annuler)) } },
        title = { Text(stringResource(R.string.choisir_departement)) },
        text = {
            Column {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it.take(40) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.recherche_departement)) },
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(filtered, key = { it.code }) { d ->
                        Text(
                            d.displayLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onPick(d) }.padding(vertical = 14.dp)
                        )
                    }
                }
            }
        }
    )
}
