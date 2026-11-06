package fr.labonnealerte.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.labonnealerte.core.model.Badge
import fr.labonnealerte.core.model.SourceState
import fr.labonnealerte.core.ui.LocalDisplayLab
import fr.labonnealerte.core.ui.R
import fr.labonnealerte.core.ui.rememberReducedMotion
import fr.labonnealerte.core.ui.theme.LbaThemeExtras

/** Pastille d'etat : couleur + texte (jamais la couleur seule, pour l'accessibilite). */
@Composable
fun StatePill(state: SourceState, modifier: Modifier = Modifier) {
    val c = LbaThemeExtras.stateColors
    val (fg, bg, label) = when (state) {
        SourceState.CALM -> Triple(c.calm, c.calmContainer, stringResource(R.string.etat_calme))
        SourceState.ACTIVE -> Triple(c.active, c.activeContainer, stringResource(R.string.etat_active))
        SourceState.INCIDENT -> Triple(c.incident, c.incidentContainer, stringResource(R.string.etat_incident))
        SourceState.NONE -> Triple(c.muted, MaterialTheme.colorScheme.surfaceVariant, stringResource(R.string.etat_aucun))
    }
    val lab = LocalDisplayLab.current
    val active = state == SourceState.ACTIVE
    // e1) Pulsation douce du voyant (opacite 100 % -> 40 %, 1,6 s), jamais de mouvement ; coupee
    // si Android ou l'app reduisent les animations (APP-11).
    val pulse = active && lab.activePulse && !rememberReducedMotion()
    val alpha = if (pulse) {
        rememberInfiniteTransition(label = "voyant").animateFloat(
            initialValue = 1f,
            targetValue = PULSE_MIN_ALPHA,
            animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
            label = "voyant-alpha"
        ).value
    } else {
        1f
    }
    Row(
        modifier = modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (lab.activeIcon && (active || state == SourceState.CALM)) {
            // d) Voyant = cloche (le texte reste) : verte et inclinee au calme, secouee en cours
            // (statique si les animations sont reduites), APP-13.
            val motion = !rememberReducedMotion()
            BellIcon(active = active, shake = active && motion, tint = fg, alpha = alpha)
            // Au calme : « zzz » qui monte doucement (APP-19), coupe si les animations sont reduites.
            if (!active && motion) Snooze(fg)
        } else {
            Box(Modifier.size(8.dp).alpha(alpha).clip(CircleShape).background(fg))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
private fun BellIcon(active: Boolean, shake: Boolean, tint: Color, alpha: Float) {
    val angle = if (shake) {
        rememberInfiniteTransition(label = "cloche").animateFloat(
            initialValue = -BELL_SHAKE_DEG,
            targetValue = BELL_SHAKE_DEG,
            animationSpec = infiniteRepeatable(tween(BELL_SHAKE_MS), RepeatMode.Reverse),
            label = "cloche-angle"
        ).value
    } else if (active) {
        0f
    } else {
        BELL_CALM_DEG
    }
    Icon(
        if (active) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(14.dp).alpha(alpha).rotate(angle)
    )
}

private const val BELL_SHAKE_DEG = 18f
private const val BELL_SHAKE_MS = 180
private const val BELL_CALM_DEG = -25f

/** Petits « z » qui montent et s'effacent, decales (sommeil de la cloche). Decoratif. */
@Composable
private fun Snooze(tint: Color) {
    val p = rememberInfiniteTransition(label = "zzz").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SNOOZE_MS, easing = androidx.compose.animation.core.LinearEasing)),
        label = "zzz-phase"
    ).value
    Box(Modifier.size(width = 10.dp, height = 14.dp).clearAndSetSemantics { }) {
        listOf(0f, 0.5f).forEachIndexed { i, shift ->
            val t = (p + shift) % 1f
            Text(
                "z",
                color = tint,
                fontSize = (7 + i * 2).sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (i * 4).dp, y = (-t * SNOOZE_RISE_DP).dp)
                    .alpha(1f - t)
            )
        }
    }
}

private const val SNOOZE_MS = 2400
private const val SNOOZE_RISE_DP = 8f

/** Badge « Officiel » sur les vignettes : masque pour le moment (APP-17), la fiche le garde. */
const val SHOW_BADGE_ON_CARDS = false

private const val HALO_ALPHA = 0.55f
private const val PULSE_MS = 1600
private const val PULSE_MIN_ALPHA = 0.4f

@Composable
fun badgeLabel(badge: Badge): String? = when (badge) {
    Badge.OFFICIAL -> stringResource(R.string.badge_officiel)
    Badge.VERIFIED -> stringResource(R.string.badge_verifie)
    Badge.COMMUNITY -> stringResource(R.string.badge_communaute)
    Badge.UNKNOWN -> null
}

/**
 * Carte d'alerte du kiosque. Pas de retournement : un appui ouvre le detail.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertCard(
    title: String,
    subtitle: String?,
    description: String?,
    state: SourceState,
    badge: Badge,
    subscribed: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    modifier: Modifier = Modifier,
    leading: String? = null,
    /** Bouton visible « Suivre » / « Suivi » (APP-10) ; null : pas de bouton (mention seule). */
    onToggleFollow: (() -> Unit)? = null,
    /** Couleur de la famille (option d'affichage b, APP-11) ; le pictogramme [leading] reste. */
    familyColor: Color? = null
) {
    val lab = LocalDisplayLab.current
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        // e2) Halo : fond teinte de la couleur « en cours », sans bord ; le texte « Alerte en cours » reste.
        colors = CardDefaults.cardColors(
            containerColor = if (lab.activeHalo && state == SourceState.ACTIVE) {
                LbaThemeExtras.stateColors.activeContainer.copy(alpha = HALO_ALPHA)
                    .compositeOver(MaterialTheme.colorScheme.surfaceContainerLowest)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (lab.categoryColor && familyColor != null) {
                // b) Lisere de couleur de la famille, a gauche de la vignette (decoratif).
                Box(Modifier.width(6.dp).fillMaxHeight().background(familyColor))
            }
            Column(Modifier.weight(1f).padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    if (leading != null) {
                        Text(leading, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!subtitle.isNullOrBlank() && !lab.hideSubtitle) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (onToggleFavorite != null) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(
                                    if (favorite) R.string.cd_favori_retirer else R.string.cd_favori_ajouter
                                ),
                                tint = if (favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(description, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        StatePill(state)
                        // « Officiel » masque sur les vignettes (APP-17) ; code garde pour plus tard.
                        badgeLabel(badge)?.takeIf { SHOW_BADGE_ON_CARDS || badge == Badge.COMMUNITY }?.let {
                            if (lab.officialIcon && badge == Badge.OFFICIAL) {
                                // a) Icone de validation ; TalkBack lit toujours « Officiel ».
                                Icon(
                                    Icons.Filled.Verified,
                                    contentDescription = it,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (subscribed && onToggleFollow == null) {
                            // Marquage discret « Deja abonne » (APP-08) : icone + texte, lu en une fois.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.semantics(mergeDescendants = true) {}
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.deja_abonne),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (onToggleFollow != null) {
                        Spacer(Modifier.width(8.dp))
                        FollowButton(subscribed, onToggleFollow)
                    }
                }
            }
        }
    }
}

/** Etat vide avec la mascotte du site. */
@Composable
fun EmptyState(
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painterResource(R.drawable.mascotte),
            contentDescription = stringResource(R.string.cd_mascotte),
            modifier = Modifier.height(140.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Squelette de chargement d'une liste de cartes. Animation coupee si [animate] = false. */
@Composable
fun ListeSkeleton(count: Int = 6, animate: Boolean = true, modifier: Modifier = Modifier) {
    val alpha = if (animate) {
        val t = rememberInfiniteTransition(label = "skeleton")
        val a by t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
        a
    } else {
        0.7f
    }
    val desc = stringResource(R.string.chargement)
    Column(
        modifier.semantics { contentDescription = desc }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(count) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 120.dp).alpha(alpha)
                    .clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
    }
}

/** Bandeau discret affiche quand l'app montre des donnees en cache faute de reseau. */
@Composable
fun BandeauHorsLigne(modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.hors_ligne), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** « Suivre » / « Suivi » (APP-10) : le texte change avec l'etat, jamais la couleur seule. */
@Composable
fun FollowButton(followed: Boolean, onClick: () -> Unit) {
    if (LocalDisplayLab.current.pillFollow) {
        PillFollowButton(followed, onClick)
    } else if (followed) {
        FilledTonalButton(onClick = onClick, modifier = Modifier.heightIn(min = 36.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.carte_suivi))
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 36.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text(stringResource(R.string.carte_suivre))
        }
    }
}

/**
 * Option 3 (APP-12), facon YouTube : pilule pleine « Suivre » ; une fois abonne, pilule grise
 * « Suivi » avec coche. Meme action, meme texte (lu par TalkBack), cible >= 36 dp.
 */
@Composable
private fun PillFollowButton(followed: Boolean, onClick: () -> Unit) {
    val colors = if (followed) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    } else {
        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
    }
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        colors = colors,
        modifier = Modifier.heightIn(min = 36.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        if (followed) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.carte_suivi))
        } else {
            Text(stringResource(R.string.carte_suivre))
        }
    }
}
