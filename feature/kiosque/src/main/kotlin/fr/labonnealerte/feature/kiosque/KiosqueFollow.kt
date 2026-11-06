package fr.labonnealerte.feature.kiosque

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Effet d'un balayage de vignette : vers la droite = suivre, vers la gauche = ne plus suivre. */
internal enum class SwipeIntent { FOLLOW, UNFOLLOW, NONE }

/**
 * Ce que declenche un balayage de [offset] pixels (pur, teste). Il faut depasser [threshold] ;
 * deja suivie, balayer a droite ne fait rien (et inversement).
 */
internal fun swipeIntent(offset: Float, threshold: Float, followed: Boolean): SwipeIntent = when {
    offset >= threshold && !followed -> SwipeIntent.FOLLOW
    offset <= -threshold && followed -> SwipeIntent.UNFOLLOW
    else -> SwipeIntent.NONE
}

/** Distance a parcourir pour declencher le geste. */
private val SWIPE_THRESHOLD = 96.dp

/**
 * Vignette balayable (APP-10). La vignette revient TOUJOURS en place apres le geste : seul
 * l'etat d'abonnement change (glisser simple plutot que SwipeToDismissBox, fait pour faire
 * disparaitre un element : la vignette restait hors champ ou redeclenchait le geste). Le geste
 * n'est jamais le seul moyen : bouton « Suivre » / « Suivi » et actions TalkBack equivalents.
 */
@Composable
internal fun SwipeToFollow(followed: Boolean, onFollow: () -> Unit, onUnfollow: () -> Unit, content: @Composable () -> Unit) {
    val followLabel = stringResource(R.string.action_suivre)
    val unfollowLabel = stringResource(R.string.action_ne_plus_suivre)
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    val followedNow by rememberUpdatedState(followed)
    val follow by rememberUpdatedState(onFollow)
    val unfollow by rememberUpdatedState(onUnfollow)
    val dragState = rememberDraggableState { delta ->
        // Sens sans effet : la vignette resiste (deplacement reduit), sans rien declencher.
        val towards = offset.value + delta
        val useful = swipeIntent(if (towards > 0) threshold else -threshold, threshold, followedNow) != SwipeIntent.NONE
        scope.launch { offset.snapTo(if (useful) towards else offset.value + delta * RESISTANCE) }
    }
    Box(
        Modifier.semantics {
            customActions = listOf(
                if (followed) {
                    CustomAccessibilityAction(unfollowLabel) {
                        onUnfollow()
                        true
                    }
                } else {
                    CustomAccessibilityAction(followLabel) {
                        onFollow()
                        true
                    }
                }
            )
        }
    ) {
        val preview = swipeIntent(
            when {
                offset.value > 0f -> threshold
                offset.value < 0f -> -threshold
                else -> 0f
            },
            threshold,
            followed
        )
        Box(Modifier.matchParentSize()) { SwipeBackground(preview, followLabel, unfollowLabel) }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        when (swipeIntent(offset.value, threshold, followedNow)) {
                            SwipeIntent.FOLLOW -> follow()
                            SwipeIntent.UNFOLLOW -> unfollow()
                            SwipeIntent.NONE -> Unit
                        }
                        scope.launch { offset.animateTo(0f) }
                    }
                )
        ) { content() }
    }
}

private const val RESISTANCE = 0.25f

@Composable
private fun SwipeBackground(intent: SwipeIntent, followLabel: String, unfollowLabel: String) {
    if (intent == SwipeIntent.NONE) return
    val follow = intent == SwipeIntent.FOLLOW
    Box(
        Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large)
            .background(if (follow) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 24.dp),
        contentAlignment = if (follow) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        // Icone ET texte : la couleur n'est jamais le seul indice.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(if (follow) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (follow) followLabel else unfollowLabel, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Snackbar des abonnements faits depuis le kiosque ; « Annuler » apres un desabonnement. */
@Composable
internal fun FollowSnackbar(message: FollowMessage?, host: SnackbarHostState, actions: FollowActions) {
    val text = message?.let { followText(it) }
    val annuler = stringResource(R.string.annuler)
    val connexion = stringResource(R.string.se_connecter)
    LaunchedEffect(message) {
        if (message == null || text == null) return@LaunchedEffect
        val action = when (message) {
            is FollowMessage.Retiree -> annuler
            FollowMessage.Connexion -> connexion
            else -> null
        }
        // Message consomme APRES l'affichage : le vider avant relancait cet effet et annulait la snackbar.
        val r = try {
            host.showSnackbar(text, actionLabel = action, duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short)
        } finally {
            actions.consume()
        }
        if (r == SnackbarResult.ActionPerformed) {
            when (message) {
                is FollowMessage.Retiree -> actions.undo(message.undo)
                FollowMessage.Connexion -> actions.login()
                else -> Unit
            }
        }
    }
}

@Composable
private fun followText(m: FollowMessage): String = when (m) {
    is FollowMessage.Suivie -> stringResource(R.string.follow_suivie, m.name)
    is FollowMessage.Retiree -> stringResource(R.string.follow_retiree, m.name)
    FollowMessage.Connexion -> stringResource(R.string.follow_connexion)
    FollowMessage.HorsLigne -> stringResource(R.string.follow_hors_ligne)
    is FollowMessage.Refus -> listOfNotNull(stringResource(R.string.follow_refus), m.serverText).joinToString(" ")
    FollowMessage.Erreur -> stringResource(R.string.follow_erreur)
}
