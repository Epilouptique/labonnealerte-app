package fr.labonnealerte.core.push

import fr.labonnealerte.core.data.ReceptionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Bandeau « Pourquoi mes notifications n'arrivent pas ? » : causes detectees (APP-05, decision E). */
class NotificationHealthTest {
    private val now = 1_800_000_000_000L
    private val pushOk = ReceptionState(pushRegisteredOnServer = true, lastPushAtMillis = now - 60_000)
    private val ok = HealthInputs(
        permissionGranted = true,
        notificationsEnabled = true,
        blockedChannels = 0,
        backgroundRestricted = false,
        distributors = 1,
        reception = pushOk,
        now = now
    )

    @Test
    fun `tout va bien - aucun bandeau`() {
        assertNull(NotificationHealth.diagnose(ok))
        // Releve periodique a l'heure, sans distributeur.
        assertNull(NotificationHealth.diagnose(ok.copy(distributors = 0, reception = ReceptionState(lastPollAtMillis = now - 20 * 60_000))))
        // Jamais de releve encore (installation fraiche) : pas d'alarme.
        assertNull(NotificationHealth.diagnose(ok.copy(distributors = 0, reception = ReceptionState())))
    }

    @Test
    fun `permission refusee ou notifications coupees`() {
        assertEquals(NotificationProblem.PERMISSION, NotificationHealth.diagnose(ok.copy(permissionGranted = false)))
        assertEquals(NotificationProblem.PERMISSION, NotificationHealth.diagnose(ok.copy(notificationsEnabled = false)))
    }

    @Test
    fun `permission jamais demandee - invitation, pas d erreur`() {
        val jamais = ok.copy(permissionGranted = false, permissionAsked = false, notificationsEnabled = false)
        assertEquals(true, NotificationHealth.needsInvitation(jamais))
        assertNull(NotificationHealth.diagnose(jamais))
        // Apres un refus : l'erreur s'affiche.
        assertEquals(false, NotificationHealth.needsInvitation(jamais.copy(permissionAsked = true)))
        assertEquals(NotificationProblem.PERMISSION, NotificationHealth.diagnose(jamais.copy(permissionAsked = true)))
    }

    @Test
    fun `canal coupe`() {
        assertEquals(NotificationProblem.CANAL_COUPE, NotificationHealth.diagnose(ok.copy(blockedChannels = 1)))
    }

    @Test
    fun `economie de batterie qui restreint l app`() {
        assertEquals(NotificationProblem.BATTERIE, NotificationHealth.diagnose(ok.copy(backgroundRestricted = true)))
    }

    @Test
    fun `sans distributeur et releve en retard`() {
        val late = ReceptionState(lastPollAtMillis = now - NotificationHealth.POLL_LATE_MS - 1)
        assertEquals(NotificationProblem.RELEVE_EN_RETARD, NotificationHealth.diagnose(ok.copy(distributors = 0, reception = late)))
        // Un distributeur est installe : la releve n'est pas le mode attendu, pas ce bandeau-la.
        assertNull(NotificationHealth.diagnose(ok.copy(distributors = 1, reception = late)))
    }

    @Test
    fun `releves qui echouent depuis plus de 2 h - en retard meme si la derniere tentative est recente`() {
        val failing = ReceptionState(
            lastPollAtMillis = now - 60_000,
            lastPollOk = false,
            lastPollSuccessAtMillis = now - NotificationHealth.POLL_LATE_MS - 1
        )
        assertEquals(NotificationProblem.RELEVE_EN_RETARD, NotificationHealth.diagnose(ok.copy(distributors = 0, reception = failing)))
        // Un echec isole, derniere reussite recente : rien.
        val blip = failing.copy(lastPollSuccessAtMillis = now - 20 * 60_000)
        assertNull(NotificationHealth.diagnose(ok.copy(distributors = 0, reception = blip)))
        // Mise a jour depuis une version sans « derniere reussite » : pas de fausse alerte.
        val upgraded = failing.copy(lastPollSuccessAtMillis = 0)
        assertNull(NotificationHealth.diagnose(ok.copy(distributors = 0, reception = upgraded)))
    }

    @Test
    fun `push actif mais evenements trouves sans push`() {
        val missed = pushOk.copy(missedPushAtMillis = now)
        assertEquals(NotificationProblem.PUSH_MANQUE, NotificationHealth.diagnose(ok.copy(reception = missed)))
        // Un push est arrive depuis : le probleme est passe.
        assertNull(NotificationHealth.diagnose(ok.copy(reception = missed.copy(lastPushAtMillis = now + 1))))
    }

    @Test
    fun `une seule cause, la plus bloquante d abord`() {
        val all = ok.copy(
            permissionGranted = false,
            blockedChannels = 2,
            backgroundRestricted = true,
            reception = pushOk.copy(missedPushAtMillis = now)
        )
        assertEquals(NotificationProblem.PERMISSION, NotificationHealth.diagnose(all))
        assertEquals(NotificationProblem.CANAL_COUPE, NotificationHealth.diagnose(all.copy(permissionGranted = true)))
        assertEquals(NotificationProblem.BATTERIE, NotificationHealth.diagnose(all.copy(permissionGranted = true, blockedChannels = 0)))
    }
}
