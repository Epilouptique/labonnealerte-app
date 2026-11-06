package fr.labonnealerte.feature.reglages

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.labonnealerte.core.data.AccountRepository
import fr.labonnealerte.core.data.AppPreferences
import fr.labonnealerte.core.data.CatalogRepository
import fr.labonnealerte.core.model.SourceType
import fr.labonnealerte.core.push.HealthInputs
import fr.labonnealerte.core.push.NotificationHealth
import fr.labonnealerte.core.push.NotificationProblem
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ce que montre le haut de Mes alertes et de Compte au sujet des notifications. */
sealed interface NotificationBannerState {
    data object Aucun : NotificationBannerState

    /** Permission jamais demandee : invitation positive (APP-06, point 4). */
    data class Invitation(val alertes: Int) : NotificationBannerState

    /** Refus ou vrai probleme : « Pourquoi mes notifications n'arrivent pas ? » (APP-05). */
    data class Probleme(val cause: NotificationProblem) : NotificationBannerState
}

/**
 * Rien n'est montre si la personne n'est pas connectee, n'a aucun abonnement, ou si tout va bien.
 * La permission jamais demandee donne une invitation, pas une erreur ; le bandeau d'erreur ne vient
 * qu'apres un refus ou un vrai probleme.
 */
@HiltViewModel
class NotificationProblemViewModel @Inject constructor(
    private val health: NotificationHealth,
    private val prefs: AppPreferences,
    account: AccountRepository,
    catalog: CatalogRepository
) : ViewModel() {
    private val detected = MutableStateFlow<HealthInputs?>(null)

    val state: StateFlow<NotificationBannerState> = combine(detected, account.signedIn, account.account, catalog.sources) { inputs, signed, acc, sources ->
        // Meme decompte que Mes alertes : les echeances (USER_TASK) ne sont pas des alertes.
        val tasks = sources.filter { it.type == SourceType.USER_TASK }.map { it.id }.toSet()
        val count = acc?.subscriptions?.count { it.subscribed && it.tasks.isEmpty() && it.sourceId !in tasks } ?: 0
        bannerState(inputs, signed, count)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationBannerState.Aucun)

    /** Relu a chaque retour sur l'ecran (la personne revient peut-etre des reglages Android). */
    fun refresh() {
        viewModelScope.launch { detected.value = health.read() }
    }

    /** Reponse a la fenetre systeme : un refus fera ensuite apparaitre le bandeau d'erreur. */
    fun onPermissionResult() {
        viewModelScope.launch {
            prefs.markNotificationPermissionAsked()
            detected.value = health.read()
        }
    }

    companion object {
        fun bannerState(inputs: HealthInputs?, signedIn: Boolean, subscriptions: Int): NotificationBannerState = when {
            inputs == null || !signedIn || subscriptions == 0 -> NotificationBannerState.Aucun
            NotificationHealth.needsInvitation(inputs) -> NotificationBannerState.Invitation(subscriptions)
            else -> NotificationHealth.diagnose(inputs)?.let { NotificationBannerState.Probleme(it) } ?: NotificationBannerState.Aucun
        }
    }
}

@Composable
fun NotificationProblemBanner(onFix: () -> Unit, modifier: Modifier = Modifier, viewModel: NotificationProblemViewModel = hiltViewModel()) {
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onPermissionResult() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        NotificationBannerState.Aucun -> Unit
        is NotificationBannerState.Invitation -> NotificationInvitationCard(s.alertes, modifier) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        is NotificationBannerState.Probleme -> NotificationProblemCard(s.cause, onFix, modifier)
    }
}

/** Invitation positive, couleurs normales (pas d'erreur) : un seul bouton qui ouvre la fenetre systeme. */
@Composable
fun NotificationInvitationCard(alertes: Int, modifier: Modifier = Modifier, onAllow: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                pluralStringResource(R.plurals.invitation_texte, alertes, alertes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            Button(onClick = onAllow, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.invitation_autoriser)) }
        }
    }
}

/** Contenu du bandeau, sans etat (captures et tests). */
@Composable
fun NotificationProblemCard(problem: NotificationProblem, onFix: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.probleme_titre),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            Text(stringResource(problem.cause()), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onFix, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.probleme_corriger)) }
        }
    }
}

private fun NotificationProblem.cause(): Int = when (this) {
    NotificationProblem.PERMISSION -> R.string.probleme_permission
    NotificationProblem.CANAL_COUPE -> R.string.probleme_canal
    NotificationProblem.BATTERIE -> R.string.probleme_batterie
    NotificationProblem.RELEVE_EN_RETARD -> R.string.probleme_releve
    NotificationProblem.PUSH_MANQUE -> R.string.probleme_push
}
