package fr.labonnealerte.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.labonnealerte.core.model.Badge
import fr.labonnealerte.core.model.SourceState
import fr.labonnealerte.core.ui.theme.LbaTheme

/** Galerie des composants, utilisee par les previews et les captures Roborazzi. */
@Composable
fun ComponentGallery() {
    Surface {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AlertCard(
                title = "Vigilance météo",
                subtitle = "Le département de votre choix",
                description = "Votre département passe en vigilance orange ou rouge ? Météo-France parle, vous êtes prévenu.",
                state = SourceState.ACTIVE,
                badge = Badge.OFFICIAL,
                subscribed = true,
                favorite = true,
                onClick = {},
                onToggleFavorite = {}
            )
            AlertCard(
                title = "Livraison à 0,99 €",
                subtitle = "Promo Mondial Relay sur leboncoin",
                description = null,
                state = SourceState.CALM,
                badge = Badge.VERIFIED,
                subscribed = false,
                favorite = false,
                onClick = {},
                onToggleFavorite = {}
            )
            BandeauHorsLigne()
            StatePill(SourceState.INCIDENT)
        }
    }
}

@Preview(name = "Clair", widthDp = 400)
@Preview(name = "Sombre", widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GalleryPreview() {
    LbaTheme { ComponentGallery() }
}

@Preview(name = "Etat vide", widthDp = 400)
@Composable
private fun EmptyPreview() {
    LbaTheme {
        Surface { EmptyState(title = "Aucune alerte suivie", message = "Parcourez le kiosque pour en choisir.") }
    }
}

@Preview(name = "Squelette", widthDp = 400)
@Composable
private fun SkeletonPreview() {
    LbaTheme { Surface { ListeSkeleton(count = 3, animate = false) } }
}
