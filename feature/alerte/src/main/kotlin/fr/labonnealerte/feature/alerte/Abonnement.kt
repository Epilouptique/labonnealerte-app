package fr.labonnealerte.feature.alerte

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.labonnealerte.core.model.ParamDescriptor
import fr.labonnealerte.core.model.ParamOption
import fr.labonnealerte.core.model.ParamType
import fr.labonnealerte.core.model.ParamValidator
import fr.labonnealerte.core.model.Source
import fr.labonnealerte.core.model.SourceType
import fr.labonnealerte.core.ui.components.OptionPickerDialog
import fr.labonnealerte.core.ui.components.StatePill
import fr.labonnealerte.core.ui.components.displayLabel
import kotlinx.coroutines.delay

/** Bloc « Recevoir cette alerte » du detail : connexion, suivi simple ou par zone, pause. */
@Composable
fun AbonnementBlock(
    source: Source,
    sub: SubState,
    onRequestLogin: () -> Unit,
    onSubscribe: (Boolean) -> Unit,
    onMute: (Boolean) -> Unit,
    onAdd: (Map<String, String>) -> Unit,
    onRemove: (Map<String, String>) -> Unit,
    lookup: suspend (String) -> List<ParamOption>
) {
    if (source.type == SourceType.LINKED) return
    val loading = stringResource(fr.labonnealerte.core.ui.R.string.chargement)
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.sub_titre),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics {
                    heading()
                }
            )
            when {
                !sub.signedIn -> {
                    Text(stringResource(R.string.sub_connexion), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onRequestLogin, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sub_se_connecter)) }
                }
                !source.isParameterized -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = sub.subscribed, enabled = !sub.busy, role = Role.Switch, onValueChange = onSubscribe),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(if (sub.subscribed) R.string.sub_suivie else R.string.sub_suivre),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (sub.busy) {
                            CircularProgressIndicator(
                                Modifier.size(24.dp).semantics { contentDescription = loading }
                            )
                        } else {
                            Switch(checked = sub.subscribed, onCheckedChange = null)
                        }
                    }
                }
                else -> ParamInstances(source, sub, onAdd, onRemove, lookup)
            }
            if (sub.signedIn && sub.subscribed) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = sub.muted, enabled = !sub.busy, role = Role.Switch, onValueChange = onMute),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sub_pause), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.sub_pause_aide), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = sub.muted, onCheckedChange = null, enabled = !sub.busy)
                }
            }
        }
    }
}

@Composable
private fun ParamInstances(
    source: Source,
    sub: SubState,
    onAdd: (Map<String, String>) -> Unit,
    onRemove: (Map<String, String>) -> Unit,
    lookup: suspend (String) -> List<ParamOption>
) {
    val instances = sub.subscription?.instances.orEmpty()
    if (instances.isNotEmpty()) {
        Text(stringResource(R.string.sub_mes_zones), style = MaterialTheme.typography.titleSmall)
        instances.forEach { inst ->
            val label = inst.label ?: inst.params.values.joinToString(", ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    inst.state?.let { StatePill(stateOf(it)) }
                }
                IconButton(onClick = { onRemove(inst.params) }, enabled = !sub.busy) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.sub_retirer, label))
                }
            }
        }
    }
    // Formulaire d'ajout : un champ par parametre, pre-rempli avec le profil quand c'est pertinent.
    val values = remember(source.id) {
        mutableStateMapOf<String, String>().apply {
            source.params.forEach { p ->
                val pre = when {
                    p.key == "departement" && p.type == ParamType.ENUM -> sub.profileDepartement?.takeIf { d ->
                        p.options.any {
                            it.value ==
                                d
                        }
                    }
                    (p.type == ParamType.COMMUNE || p.type == ParamType.COMMUNE_COORDS) -> sub.profileVille
                    else -> p.default
                }
                if (pre != null) put(p.key, pre)
            }
        }
    }
    if (instances.isNotEmpty()) Text(stringResource(R.string.sub_ajouter_titre), style = MaterialTheme.typography.titleSmall)
    source.params.forEach { p ->
        ParamField(p, values[p.key].orEmpty(), sub.issues.firstOrNull { it.key == p.key }, lookup) { values[p.key] = it }
    }
    Button(onClick = { onAdd(values.toMap()) }, enabled = !sub.busy, modifier = Modifier.fillMaxWidth()) {
        if (sub.busy) {
            val loading = stringResource(fr.labonnealerte.core.ui.R.string.chargement)
            CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = loading })
        } else {
            Text(stringResource(if (instances.isEmpty()) R.string.sub_suivre else R.string.sub_ajouter))
        }
    }
}

private fun stateOf(s: String) = when (s) {
    "active" -> fr.labonnealerte.core.model.SourceState.ACTIVE
    else -> fr.labonnealerte.core.model.SourceState.CALM
}

@Composable
private fun issueText(i: ParamValidator.Issue?): String? = when (i) {
    null -> null
    is ParamValidator.Issue.Required -> stringResource(R.string.sub_err_requis)
    is ParamValidator.Issue.NotAllowed -> stringResource(R.string.sub_err_liste)
    is ParamValidator.Issue.NotANumber -> stringResource(R.string.sub_err_nombre)
    is ParamValidator.Issue.TooSmall -> stringResource(R.string.sub_err_min, fmt(i.min))
    is ParamValidator.Issue.TooLarge -> stringResource(R.string.sub_err_max, fmt(i.max))
    is ParamValidator.Issue.BadFormat -> stringResource(R.string.sub_err_format)
}

private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

@Composable
private fun ParamField(
    p: ParamDescriptor,
    value: String,
    issue: ParamValidator.Issue?,
    lookup: suspend (String) -> List<ParamOption>,
    onChange: (String) -> Unit
) {
    val err = issueText(issue)
    when (p.type) {
        ParamType.ENUM -> {
            var open by remember { mutableStateOf(false) }
            val current = p.options.firstOrNull { it.value == value }
            Column {
                Text(p.label, style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(current?.displayLabel() ?: stringResource(R.string.sub_choisir))
                }
                if (err != null) Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (open) {
                OptionPickerDialog(p.label, p.options, onPick = {
                    onChange(it.value)
                    open = false
                }, onDismiss = { open = false })
            }
        }
        ParamType.DYNAMIC_ENUM -> {
            val suggestions = remember { mutableStateListOf<ParamOption>() }
            var menu by remember { mutableStateOf(false) }
            LaunchedEffect(value) {
                if (value.trim().length >= 2 && suggestions.none { it.value == value }) {
                    delay(350) // anti-rafale : la recherche serveur est limitee (30/min)
                    suggestions.clear()
                    suggestions.addAll(lookup(value.trim()))
                    menu = suggestions.isNotEmpty()
                }
            }
            Column {
                TextInput(p, value, err, KeyboardType.Text, onChange)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    suggestions.forEach { o ->
                        DropdownMenuItem(text = { Text(o.label) }, onClick = {
                            onChange(o.value)
                            menu = false
                        })
                    }
                }
            }
        }
        ParamType.NUMBER -> TextInput(p, value, err, KeyboardType.Decimal, onChange)
        else -> TextInput(p, value, err, KeyboardType.Text, onChange)
    }
}

@Composable
private fun TextInput(p: ParamDescriptor, value: String, err: String?, kb: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(p.maxLength ?: 120)) },
        label = { Text(p.label) },
        placeholder = p.placeholder?.let { { Text(it) } },
        supportingText = (err ?: p.hint)?.let { t ->
            {
                Text(
                    t,
                    color = if (err !=
                        null
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        },
        isError = err != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = kb),
        modifier = Modifier.fillMaxWidth()
    )
}
