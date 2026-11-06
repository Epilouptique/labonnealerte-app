package fr.labonnealerte.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.labonnealerte.app.R
import fr.labonnealerte.core.data.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Bandeau d'accueil apres une creation de compte (APP-05, decision A) : une seule fois. */
@HiltViewModel
class AccueilViewModel @Inject constructor(private val prefs: AppPreferences) : ViewModel() {
    val welcomePending: StateFlow<Boolean> = prefs.welcomePending.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun markPending() {
        viewModelScope.launch { prefs.setWelcomePending(true) }
    }

    /** Bandeau montre : il ne reviendra plus, meme apres un redemarrage. */
    fun consume() {
        viewModelScope.launch { prefs.setWelcomePending(false) }
    }
}

@Composable
fun BandeauAccueil(onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.accueil_titre),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Text(stringResource(R.string.accueil_texte), style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.accueil_fermer))
            }
        }
    }
}
