package fr.labonnealerte.core.push

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import timber.log.Timber

/**
 * Service UnifiedPush (connector 3.x). Le distributeur (ntfy, NextPush, Sunup…) nous remet
 * l'adresse d'envoi et les messages Web Push deja dechiffres (RFC 8291).
 */
@AndroidEntryPoint
class LbaPushService : PushService() {
    @Inject lateinit var registrar: PushRegistrar

    @Inject lateinit var sync: EventSync

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        scope.launch { registrar.onNewEndpoint(endpoint.url, endpoint.pubKeySet?.pubKey, endpoint.pubKeySet?.auth) }
    }

    override fun onMessage(message: PushMessage, instance: String) {
        if (!message.decrypted) Timber.w("Message push non dechiffre : contenu ignore, releve declenchee")
        scope.launch { sync.onPush(if (message.decrypted) message.content else null) }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Timber.w("Enregistrement UnifiedPush refuse : $reason")
        scope.launch { registrar.onFailure() }
    }

    override fun onUnregistered(instance: String) {
        scope.launch { registrar.onUnregistered() }
    }
}
