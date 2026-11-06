package fr.labonnealerte.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Reglages des puces du kiosque : modifier ici pour changer toutes les puces d'un coup.
 * [LBA_CHIP_PADDING] : marge interieure (gauche/droite, haut/bas) autour du texte.
 */
val LBA_CHIP_PADDING = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
private val LBA_CHIP_SHAPE = RoundedCornerShape(8.dp)

/**
 * Puce de filtre (remplace FilterChip, dont le padding n'est pas reglable). Memes parametres que
 * FilterChip : selected, onClick, label. Selectionnee : fond teinte ; sinon contour. La zone
 * tactile reste de 48 dp minimum (Surface cliquable) ; TalkBack annonce l'etat « selectionne ».
 */
@Composable
fun LbaChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = LBA_CHIP_PADDING
) {
    val c = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        shape = LBA_CHIP_SHAPE,
        color = if (selected) c.secondaryContainer else c.surface,
        contentColor = if (selected) c.onSecondaryContainer else c.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, c.outlineVariant),
        modifier = modifier.semantics { role = Role.Checkbox }
    ) {
        Box(Modifier.padding(contentPadding)) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) { label() }
        }
    }
}
