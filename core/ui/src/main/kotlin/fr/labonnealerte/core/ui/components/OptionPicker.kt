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
import fr.labonnealerte.core.model.ParamOption
import fr.labonnealerte.core.model.SearchNormalizer
import fr.labonnealerte.core.ui.R

/** Libelle affiche : « 05 - Hautes-Alpes » quand la valeur est un code court (departement…). */
fun ParamOption.displayLabel(): String =
    if (value.length <= 3 && value != label && value.any { it.isDigit() }) "$value - $label" else label

/** Liste de choix avec recherche (accents/apostrophes ignores ; un code se cherche aussi). */
@Composable
fun OptionPickerDialog(
    title: String,
    options: List<ParamOption>,
    onPick: (ParamOption) -> Unit,
    onDismiss: () -> Unit
) {
    var q by remember { mutableStateOf("") }
    val filtered = remember(q, options) {
        val t = q.trim()
        if (t.isEmpty()) {
            options
        } else {
            options.filter { o -> o.value.equals(t, true) || o.value.startsWith(t, true) || SearchNormalizer.matches(o.label, t) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.annuler)) } },
        title = { Text(title) },
        text = {
            Column {
                if (options.size > 8) {
                    OutlinedTextField(
                        value = q,
                        onValueChange = { q = it.take(40) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(R.string.recherche_departement)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(filtered, key = { it.value }) { o ->
                        Text(
                            o.displayLabel(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onPick(o) }.padding(vertical = 14.dp)
                        )
                    }
                }
            }
        }
    )
}
