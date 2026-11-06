package fr.labonnealerte.app

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import fr.labonnealerte.core.data.session.SessionStore
import fr.labonnealerte.core.notifications.Channels
import fr.labonnealerte.core.push.PushRegistrar
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class LbaApplication :
    Application(),
    Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var registrar: PushRegistrar

    @Inject lateinit var session: SessionStore

    @Inject lateinit var notificationDao: fr.labonnealerte.core.database.NotificationDao

    @Inject lateinit var account: fr.labonnealerte.core.data.AccountRepository

    @Inject lateinit var notifier: fr.labonnealerte.core.notifications.AlertNotifier

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        fr.labonnealerte.core.ui.Dates.init(this) // langue changee : formats de date des ressources
    }

    override fun onCreate() {
        // Avant super.onCreate() : Hilt injecte (et cree les singletons) pendant super.onCreate().
        // Journaux en debug seulement, et jamais de donnees personnelles (email, jeton).
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().penaltyLog().build())
        }
        fr.labonnealerte.core.ui.Dates.init(this)
        Channels.createAll(this)
        Raccourcis.publish(this)
        // Widgets : rafraichis quand une notification arrive ou que le compte change.
        appScope.launch {
            kotlinx.coroutines.flow.combine(notificationDao.observeUnreadCount(), account.account) { a, b -> a to b?.subscriptions }
                .collect { fr.labonnealerte.app.widget.WidgetUpdater.updateAll(this@LbaApplication) }
        }
        appScope.launch {
            // Tiroir : une notification reste tant que son alerte n'a pas ete vue dans l'app (APP-07).
            notificationDao.observeUnreadAlerts().collect { list ->
                notifier.syncTray(list.map { fr.labonnealerte.core.notifications.UnreadAlert(it.id, it.sourceId) })
            }
        }
        appScope.launch {
            // Reception : UnifiedPush si un distributeur est present, sinon releve periodique.
            if (session.currentToken() != null) {
                registrar.ensure()
                registrar.syncNow()
            }
        }
    }
}
