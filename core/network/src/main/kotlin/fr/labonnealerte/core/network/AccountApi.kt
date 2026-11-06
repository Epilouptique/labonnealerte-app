package fr.labonnealerte.core.network

import fr.labonnealerte.core.network.dto.AppConfigDto
import fr.labonnealerte.core.network.dto.AppEventsDto
import fr.labonnealerte.core.network.dto.AppLoginDto
import fr.labonnealerte.core.network.dto.ConfirmTaskDto
import fr.labonnealerte.core.network.dto.CreateReportBody
import fr.labonnealerte.core.network.dto.CreateReportDto
import fr.labonnealerte.core.network.dto.CreateTaskBody
import fr.labonnealerte.core.network.dto.CreateTaskDto
import fr.labonnealerte.core.network.dto.DisplayNameBody
import fr.labonnealerte.core.network.dto.DisplayNameDto
import fr.labonnealerte.core.network.dto.EmailBody
import fr.labonnealerte.core.network.dto.EmailPrefBody
import fr.labonnealerte.core.network.dto.EmptyBody
import fr.labonnealerte.core.network.dto.FavoriteBody
import fr.labonnealerte.core.network.dto.FavoriteSyncBody
import fr.labonnealerte.core.network.dto.ForumReplyBody
import fr.labonnealerte.core.network.dto.ForumReportBody
import fr.labonnealerte.core.network.dto.ForumTopicBody
import fr.labonnealerte.core.network.dto.ForumTopicCreatedDto
import fr.labonnealerte.core.network.dto.LikeDto
import fr.labonnealerte.core.network.dto.MessageDto
import fr.labonnealerte.core.network.dto.MuteBody
import fr.labonnealerte.core.network.dto.MyAlertsDto
import fr.labonnealerte.core.network.dto.MyHistoryDto
import fr.labonnealerte.core.network.dto.OkDto
import fr.labonnealerte.core.network.dto.ProfileBody
import fr.labonnealerte.core.network.dto.ProfileDto
import fr.labonnealerte.core.network.dto.PushEndpointBody
import fr.labonnealerte.core.network.dto.PushSubscriptionBody
import fr.labonnealerte.core.network.dto.QuietHoursBody
import fr.labonnealerte.core.network.dto.QuietHoursDto
import fr.labonnealerte.core.network.dto.ReportDto
import fr.labonnealerte.core.network.dto.SourceDto
import fr.labonnealerte.core.network.dto.SpotBody
import fr.labonnealerte.core.network.dto.ToggleBody
import fr.labonnealerte.core.network.dto.ToggleParamBody
import fr.labonnealerte.core.network.dto.ToggleParamDto
import fr.labonnealerte.core.network.dto.VapidKeyDto
import fr.labonnealerte.core.network.dto.VerifyCodeBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Endpoints authentifies (Bearer ajoute par AuthInterceptor). Contrat : §3.2 a §3.9. */
interface AccountApi {
    // Connexion par lien magique : message neutre, toujours 200 (5/min/IP).
    @POST("api/my-alerts/request")
    suspend fun requestMagicLink(@Body body: EmailBody): MessageDto

    /** Echange aussi un jeton de lien magique (en Bearer) contre une session : voir `token`. */
    @GET("api/my-alerts")
    suspend fun myAlerts(): MyAlertsDto

    @GET("api/my-alerts/history")
    suspend fun myHistory(): MyHistoryDto

    @POST("api/my-alerts/toggle")
    suspend fun toggle(@Body body: ToggleBody): ToggleBody

    @POST("api/my-alerts/toggle-param")
    suspend fun toggleParam(@Body body: ToggleParamBody): ToggleParamDto

    @POST("api/my-alerts/toggle-mute")
    suspend fun toggleMute(@Body body: MuteBody): MuteBody

    @POST("api/my-alerts/profile")
    suspend fun profile(@Body body: ProfileBody): ProfileDto

    @POST("api/my-alerts/display-name")
    suspend fun displayName(@Body body: DisplayNameBody): DisplayNameDto

    @POST("api/my-alerts/preferences")
    suspend fun emailPreference(@Body body: EmailPrefBody): EmailPrefBody

    @POST("api/my-alerts/quiet-hours")
    suspend fun quietHours(@Body body: QuietHoursBody): QuietHoursDto

    @POST("api/logout")
    suspend fun logout(@Body body: EmptyBody = EmptyBody()): OkDto

    @HTTP(method = "DELETE", path = "api/my-alerts/account", hasBody = true)
    suspend fun deleteAccount(@Body body: EmptyBody = EmptyBody()): OkDto

    // Favoris
    @GET("api/favorites")
    suspend fun favorites(): List<SourceDto>

    @POST("api/favorites/sync")
    suspend fun syncFavorites(@Body body: FavoriteSyncBody): MessageDto

    @HTTP(method = "DELETE", path = "api/favorites", hasBody = true)
    suspend fun removeFavorite(@Body body: FavoriteBody): MessageDto

    @POST("api/sources/{id}/like")
    suspend fun like(@Path("id") id: String, @Body body: EmptyBody = EmptyBody()): LikeDto

    @HTTP(method = "DELETE", path = "api/sources/{id}/like", hasBody = true)
    suspend fun unlike(@Path("id") id: String, @Body body: EmptyBody = EmptyBody()): LikeDto

    // Push (UnifiedPush = Web Push, voir DECISIONS.md D006)
    @GET("api/push/vapid-key")
    suspend fun vapidKey(): VapidKeyDto

    @POST("api/push/subscribe")
    suspend fun pushSubscribe(@Body body: PushSubscriptionBody): OkDto

    @POST("api/push/unsubscribe")
    suspend fun pushUnsubscribe(@Body body: PushEndpointBody): OkDto

    // Echeances
    @POST("api/user-tasks")
    suspend fun createTask(@Body body: CreateTaskBody): CreateTaskDto

    @POST("api/user-tasks/{id}/confirm")
    suspend fun confirmTask(@Path("id") id: Long, @Body body: EmptyBody = EmptyBody()): ConfirmTaskDto

    @HTTP(method = "DELETE", path = "api/user-tasks/{id}", hasBody = true)
    suspend fun deleteTask(@Path("id") id: Long, @Body body: EmptyBody = EmptyBody()): MessageDto

    // Signalements communautaires
    @GET("api/community-reports")
    suspend fun reports(@Query("type") type: String?): List<ReportDto>

    @POST("api/community-reports")
    suspend fun createReport(@Body body: CreateReportBody): CreateReportDto

    @POST("api/community-reports/{id}/spot")
    suspend fun spotReport(@Path("id") id: Long, @Body body: SpotBody): MessageDto

    @POST("api/community-reports/{id}/extend")
    suspend fun extendReport(@Path("id") id: Long, @Body body: EmptyBody = EmptyBody()): MessageDto

    @POST("api/community-reports/{id}/resolve")
    suspend fun resolveReport(@Path("id") id: Long, @Body body: EmptyBody = EmptyBody()): MessageDto

    // Forum (ecritures hors /api)
    @POST("forum/t")
    suspend fun createTopic(@Body body: ForumTopicBody): ForumTopicCreatedDto

    @POST("forum/t/{id}/reply")
    suspend fun reply(@Path("id") topicId: Long, @Body body: ForumReplyBody): OkDto

    @POST("forum/post/{id}/report")
    suspend fun reportPost(@Path("id") postId: Long, @Body body: ForumReportBody): OkDto

    /** Echange d'un jeton de lien magique (passe explicitement, l'intercepteur ne l'ecrase pas). */
    @GET("api/my-alerts")
    suspend fun myAlertsWithToken(@Header("Authorization") bearer: String): MyAlertsDto

    // Ajouts serveur pour l'app (server/routes/app-api.js)
    @POST("api/app/login/request")
    suspend fun requestAppLogin(@Body body: EmailBody): MessageDto

    @POST("api/app/login/verify")
    suspend fun verifyAppLogin(@Body body: VerifyCodeBody): AppLoginDto

    @GET("api/app/config")
    suspend fun appConfig(): AppConfigDto

    @GET("api/app/events")
    suspend fun appEvents(@Query("since_id") sinceId: Long, @Query("limit") limit: Int = 100): AppEventsDto
}
