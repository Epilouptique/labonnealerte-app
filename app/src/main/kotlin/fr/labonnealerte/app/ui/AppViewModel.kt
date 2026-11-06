package fr.labonnealerte.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.labonnealerte.core.data.AccountRepository
import fr.labonnealerte.core.data.AppPreferences
import fr.labonnealerte.core.data.NotificationHistoryRepository
import fr.labonnealerte.core.data.Settings
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Etat global de l'interface : reglages d'affichage, pastille des notifications non lues. */
@HiltViewModel
class AppViewModel @Inject constructor(
    prefs: AppPreferences,
    history: NotificationHistoryRepository,
    private val account: AccountRepository
) : ViewModel() {
    val settings: StateFlow<Settings?> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val unread: StateFlow<Int> = history.activeUnseenCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        // Cache local seulement (pas de reseau) : l'initiale de l'onglet Compte s'affiche tout de suite.
        viewModelScope.launch { account.loadCached() }
    }

    /** Onglet Compte de la nouvelle navigation (APP-12) : connecte ? et initiale de la pastille. */
    val accountBadge: StateFlow<AccountBadge> = combine(account.signedIn, account.account) { signedIn, acc ->
        AccountBadge(signedIn, if (signedIn) fr.labonnealerte.core.ui.components.initialOf(acc?.displayName, acc?.email) else null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountBadge())
}

data class AccountBadge(val signedIn: Boolean = false, val initial: Char? = null)
