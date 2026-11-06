package fr.labonnealerte.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import fr.labonnealerte.core.data.AppPreferences
import fr.labonnealerte.core.data.Clock
import fr.labonnealerte.core.database.NotificationDao
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Actions des notifications (non exporte : seules nos PendingIntent l'appellent). */
@AndroidEntryPoint
class NotificationActions : BroadcastReceiver() {
    @Inject lateinit var prefs: AppPreferences

    @Inject lateinit var notifier: AlertNotifier

    @Inject lateinit var dao: NotificationDao

    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID)?.takeIf { SOURCE_ID.matches(it) } ?: return
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_MUTE_1H -> prefs.muteSource(sourceId, clock.now() + HOUR)
                    ACTION_MUTE_24H -> prefs.muteSource(sourceId, clock.now() + 24 * HOUR)
                }
                dao.markSourceRead(sourceId)
                notifier.cancelForSource(sourceId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MUTE_1H = "fr.labonnealerte.action.MUTE_1H"
        const val ACTION_MUTE_24H = "fr.labonnealerte.action.MUTE_24H"
        const val EXTRA_SOURCE_ID = "source_id"
        const val EXTRA_LOCAL_ID = "local_id"
        private const val HOUR = 3_600_000L
        private val SOURCE_ID = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
