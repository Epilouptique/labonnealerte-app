package fr.labonnealerte.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.labonnealerte.core.ui.LocalDisplayLab

/**
 * En-tete d'un ecran secondaire suivi, si l'option 4 est active (APP-12), d'un filet leger. Le
 * kiosque (accueil) n'en a pas. Le filet suit le theme (clair et sombre).
 */
@Composable
fun HeaderWithDivider(bar: @Composable () -> Unit) {
    Column {
        bar()
        if (LocalDisplayLab.current.headerDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Filet sous le titre des ecrans d'onglet (Mes alertes, Publier, Forum, Compte ; pas Rechercher), APP-13. */
@Composable
fun HeaderDividerLine(bleed: Dp = 0.dp) {
    if (LocalDisplayLab.current.headerDivider) {
        // Pleine largeur comme sous une barre de titre (APP-16) : le trait deborde de [bleed] de
        // chaque cote, soit la marge laterale de la page qui le contient.
        HorizontalDivider(
            Modifier.padding(top = 8.dp).layout { m, c ->
                val extra = bleed.roundToPx() * 2
                val w = c.maxWidth + extra
                val p = m.measure(c.copy(minWidth = w, maxWidth = w))
                layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
            },
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/** Tuile « Mes alertes » / « Favoris » (ecran Compte), reprise par Mes alertes (APP-13). */
@Composable
fun LbaTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier, badge: Int = 0) {
    androidx.compose.material3.Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp).heightIn(min = 72.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            if (badge > 0) {
                androidx.compose.material3.BadgedBox(badge = {
                    androidx.compose.material3.Badge { Text(if (badge > MAX_TILE_BADGE) "$MAX_TILE_BADGE+" else badge.toString()) }
                }) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            } else {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val MAX_TILE_BADGE = 99

/**
 * Ligne de navigation facon « Mon compte » (APP-12) : icone, titre, sous-titre, fleche a droite,
 * filet de separation. Toute la ligne est cliquable (cible >= 56 dp) ; la fleche est decorative.
 */
@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    divider: Boolean = true,
    titleColor: Color = Color.Unspecified
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * Pastille ronde a initiale (comme sur le site) : violet, lettre blanche. Sans [initial] (non
 * connecte), une silhouette neutre. Decorative : le libelle voisin porte le sens pour TalkBack.
 */
@Composable
fun InitialAvatar(initial: Char?, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    val bg = if (initial != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        if (initial != null) {
            // Lettre a la taille de la pastille, independante de la taille du texte (sinon elle
            // deborde a 200 %) ; la pastille est decorative, le libelle voisin suit la police.
            val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
            Text(
                initial.toString(),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = (size.value * INITIAL_RATIO / fontScale).sp,
                lineHeight = (size.value * INITIAL_RATIO / fontScale).sp,
                maxLines = 1
            )
        } else {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.7f)
            )
        }
    }
}

private const val INITIAL_RATIO = 0.5f

/** Initiale affichee : premiere lettre du pseudo, sinon de l'adresse ; null sans compte. */
fun initialOf(displayName: String?, email: String?): Char? =
    (displayName?.trim()?.firstOrNull { it.isLetterOrDigit() } ?: email?.firstOrNull { it.isLetterOrDigit() })?.uppercaseChar()
