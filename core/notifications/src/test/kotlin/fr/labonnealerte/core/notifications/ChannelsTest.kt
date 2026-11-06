package fr.labonnealerte.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import fr.labonnealerte.core.model.NotificationFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChannelsTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val nm = app.getSystemService(NotificationManager::class.java)

    @Test
    fun bonsPlansAvecSonSansBandeau() {
        Channels.createAll(app)
        val c = nm.getNotificationChannel(NotificationFamily.BONS_PLANS.channelId)
        assertNotNull(c)
        // DEFAULT : son, sans heads-up (reserve a HIGH, urgences).
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, c.importance)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, nm.getNotificationChannel(NotificationFamily.URGENCES.channelId).importance)
    }

    @Test
    fun ancienCanalSilencieuxSupprimeAuDemarrage() {
        // Installation precedente : ancien canal bons plans en importance basse.
        nm.createNotificationChannel(NotificationChannel("bons_plans_culture", "Bons plans", NotificationManager.IMPORTANCE_LOW))
        Channels.createAll(app)
        assertNull(nm.getNotificationChannel("bons_plans_culture"))
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, nm.getNotificationChannel("bons_plans_2").importance)
        // Idempotent : un second demarrage ne recree pas l'ancien canal.
        Channels.createAll(app)
        assertNull(nm.getNotificationChannel("bons_plans_culture"))
    }

    @Test
    fun canalDeTestAudibleAvecPastille() {
        Channels.createAll(app)
        val t = nm.getNotificationChannel(Channels.TEST_CHANNEL_ID)
        assertNotNull(t)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, t.importance)
        // Son par defaut du telephone (un canal sans setSound garde le son de notification par defaut).
        assertEquals(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, t.sound)
        assertEquals(true, t.canShowBadge())
    }

    @Test
    fun pastilleSurTousLesCanauxDAlertes() {
        Channels.createAll(app)
        NotificationFamily.entries.forEach { f ->
            val c = nm.getNotificationChannel(f.channelId)
            assertEquals(f.channelId, f != NotificationFamily.SYSTEME, c.canShowBadge())
        }
    }
}
