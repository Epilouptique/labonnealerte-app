package fr.labonnealerte.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.labonnealerte.core.data.AppPreferences
import fr.labonnealerte.core.model.Tutorial
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class TutorialViewModel @Inject constructor(private val prefs: AppPreferences) : ViewModel() {
    fun done() {
        viewModelScope.launch { prefs.setTutorialSeen(true) }
    }
}

private val PAGES = listOf(
    R.string.tuto_1_titre to R.string.tuto_1_texte,
    R.string.tuto_2_titre to R.string.tuto_2_texte,
    R.string.tuto_3_titre to R.string.tuto_3_texte,
    R.string.tuto_4_titre to R.string.tuto_4_texte
)

/** Tutoriel de premiere ouverture (APP-22) : 4 ecrans, « Passer » partout ; revu depuis Reglages > Aide. */
@Composable
fun TutorialRoute(viewModel: TutorialViewModel = hiltViewModel()) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val (title, text) = PAGES[page.coerceIn(0, Tutorial.PAGES - 1)]
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding().fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.tuto_page, page + 1, Tutorial.PAGES),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!Tutorial.isLast(page)) TextButton(onClick = viewModel::done) { Text(stringResource(R.string.tuto_passer)) }
                }
                Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = { Tutorial.next(page)?.let { page = it } ?: viewModel.done() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Text(stringResource(if (Tutorial.isLast(page)) R.string.tuto_commencer else R.string.tuto_suivant))
                }
                if (page > 0) {
                    TextButton(onClick = { page -= 1 }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(R.string.tuto_precedent))
                    }
                }
            }
        }
    }
}
