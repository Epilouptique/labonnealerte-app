package fr.labonnealerte.core.push

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import fr.labonnealerte.core.data.SessionHook
import fr.labonnealerte.core.database.NotificationDao
import fr.labonnealerte.core.notifications.AlertNotifier
import javax.inject.Inject

/** Connexion : demarrer la reception. Deconnexion : retirer l'adresse push et effacer l'historique local. */
class PushSessionHook @Inject constructor(
    private val registrar: PushRegistrar,
    private val dao: NotificationDao,
    private val notifier: AlertNotifier
) : SessionHook {
    override suspend fun onSignedIn() {
        registrar.ensure()
        registrar.syncNow()
    }

    override suspend fun beforeSignOut() = registrar.onLogout()

    override suspend fun afterSignOut() {
        dao.clear()
        notifier.cancelAll()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {
    @Binds
    @IntoSet
    abstract fun sessionHook(h: PushSessionHook): SessionHook
}
