package fr.labonnealerte.core.data

import fr.labonnealerte.core.model.LoginResult
import fr.labonnealerte.core.model.NotifyChannel
import fr.labonnealerte.core.network.AppExtrasApi
import fr.labonnealerte.core.network.GoogleLoginBody
import fr.labonnealerte.core.network.NotifyChannelDto
import javax.inject.Inject
import javax.inject.Singleton

/** APP-22 : canal de reception (compte) et connexion Google. */
@Singleton
class AppExtrasRepository @Inject constructor(
    private val api: AppExtrasApi,
    private val account: AccountRepository
) {
    suspend fun notifyChannel(): Result<NotifyChannel> = runCatching { NotifyChannel.of(api.notifyChannel().notifyChannel) }

    suspend fun setNotifyChannel(c: NotifyChannel): Result<NotifyChannel> =
        runCatching { NotifyChannel.of(api.setNotifyChannel(NotifyChannelDto(c.id)).notifyChannel) }

    /** Meme suite que le code par e-mail : 409 = conditions a accepter, puis rappel avec la version. */
    suspend fun loginGoogle(idToken: String, acceptedTermsVersion: String? = null): LoginResult = account.signIn {
        api.loginGoogle(GoogleLoginBody(idToken, acceptedTermsVersion?.let { true }, acceptedTermsVersion))
    }
}
