package fr.labonnealerte.feature.kiosque

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.labonnealerte.core.model.KiosqueMode
import fr.labonnealerte.core.ui.Famille
import fr.labonnealerte.core.ui.components.AlertCard
import fr.labonnealerte.core.ui.components.EmptyState
import fr.labonnealerte.core.ui.components.HeaderWithDivider
import fr.labonnealerte.core.ui.components.ListeSkeleton

/**
 * « Mes favoris » (APP-20) : page a part, ouverte depuis Compte. Barre de titre comme les autres
 * ecrans, puis les vignettes des alertes mises en favori (le coeur est sur la fiche).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavorisRoute(onBack: () -> Unit, onOpenSource: (String) -> Unit, viewModel: KiosqueViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onMode(KiosqueMode.FAVORIS) }
    Scaffold(
        topBar = {
            HeaderWithDivider {
                TopAppBar(
                    title = { Text(stringResource(R.string.favoris_titre)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.favoris_retour_cd))
                        }
                    },
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0)
                )
            }
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val favoris = state.sources.filter { it.id in state.favorites }
            when {
                state.loading -> ListeSkeleton()
                favoris.isEmpty() -> EmptyState(stringResource(R.string.vide_favoris_titre), stringResource(R.string.vide_favoris_message))
                else -> LazyColumn(
                    Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(favoris, key = { it.id }) { s ->
                        val famille = Famille.of(state.allCategories.firstOrNull { it.slug in s.categories }?.group)
                        AlertCard(
                            title = s.name,
                            subtitle = s.subtitle,
                            description = s.description,
                            state = s.state,
                            badge = s.badge,
                            subscribed = state.isFollowed(s.id),
                            favorite = true,
                            onClick = { onOpenSource(s.id) },
                            onToggleFavorite = null,
                            leading = Famille.emojiFor(famille, s.categories),
                            familyColor = famille.color
                        )
                    }
                }
            }
        }
    }
}
