package fr.labonnealerte.feature.reglages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Licences verifiees dans les POM des dependances (2026-10-08). Les polices : SIL OFL 1.1. */
private val LICENCES = listOf(
    "AndroidX, Jetpack Compose, Material 3, Room, WorkManager, DataStore, Navigation, Glance" to
        "Apache 2.0 — The Android Open Source Project",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache 2.0 — JetBrains",
    "Dagger / Hilt" to "Apache 2.0 — Google",
    "OkHttp, Retrofit" to "Apache 2.0 — Square",
    "Coil" to "Apache 2.0 — Coil contributors",
    "Timber" to "Apache 2.0 — Jake Wharton",
    "UnifiedPush android-connector" to "Apache 2.0 — UnifiedPush",
    "Tink" to "Apache 2.0 — Google",
    "Police Inter" to "SIL Open Font License 1.1 — The Inter Project Authors",
    "Police Baloo 2" to "SIL Open Font License 1.1 — The Baloo 2 Project Authors",
    "Pictogrammes Material Symbols" to "Apache 2.0 — Google"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicencesRoute(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            fr.labonnealerte.core.ui.components.HeaderWithDivider {
                TopAppBar(
                    title = { Text(stringResource(R.string.licences_titre)) },
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
            LazyColumn(Modifier.widthIn(max = 640.dp), contentPadding = PaddingValues(16.dp)) {
                item { Text(stringResource(R.string.licences_intro), style = MaterialTheme.typography.bodyLarge) }
                items(LICENCES) { (name, licence) ->
                    Column(Modifier.padding(vertical = 10.dp)) {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text(licence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
