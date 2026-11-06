package fr.labonnealerte.core.network

import fr.labonnealerte.core.network.dto.AppLoginDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

@Serializable
data class NotifyChannelDto(@SerialName("notify_channel") val notifyChannel: String? = null)

@Serializable
data class GoogleLoginBody(
    @SerialName("id_token") val idToken: String,
    @SerialName("accept_terms") val acceptTerms: Boolean? = null,
    @SerialName("terms_version") val termsVersion: String? = null
)

/** APP-22 : canal de reception et connexion Google (routes du LOT D du site). */
interface AppExtrasApi {
    @GET("api/app/notify-channel")
    suspend fun notifyChannel(): NotifyChannelDto

    @PUT("api/app/notify-channel")
    suspend fun setNotifyChannel(@Body body: NotifyChannelDto): NotifyChannelDto

    @POST("api/app/login/google")
    suspend fun loginGoogle(@Body body: GoogleLoginBody): AppLoginDto
}
