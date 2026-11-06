package fr.labonnealerte.core.network

import fr.labonnealerte.core.network.dto.EmptyBody
import fr.labonnealerte.core.network.dto.MessageDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** Publication communautaire (APP-21) : memes routes que /publier et /mes-publications du site. */
interface PublicationsApi {
    // Publication communautaire (APP-21), memes routes que /publier et /mes-publications du site.
    @GET("api/community-sources")
    suspend fun publications(): fr.labonnealerte.core.network.dto.PublicationListDto

    @POST("api/community-sources")
    suspend fun createPublication(@Body body: fr.labonnealerte.core.network.dto.CreatePublicationBody): fr.labonnealerte.core.network.dto.PublicationWriteDto

    @PATCH("api/community-sources/{id}")
    suspend fun patchPublication(
        @Path("id") id: String,
        @Body body: fr.labonnealerte.core.network.dto.PatchPublicationBody
    ): fr.labonnealerte.core.network.dto.PublicationWriteDto

    @POST("api/community-sources/{id}/preview")
    suspend fun previewPublication(@Path("id") id: String, @Body body: EmptyBody = EmptyBody()): fr.labonnealerte.core.network.dto.PreviewDto

    @POST("api/community-sources/{id}/submit")
    suspend fun submitPublication(@Path("id") id: String, @Body body: EmptyBody = EmptyBody()): fr.labonnealerte.core.network.dto.PublicationWriteDto

    @HTTP(method = "DELETE", path = "api/community-sources/{id}", hasBody = true)
    suspend fun deletePublication(@Path("id") id: String, @Body body: EmptyBody = EmptyBody()): MessageDto

    @POST("api/community-sources/{id}/report")
    suspend fun reportSource(@Path("id") id: String, @Body body: fr.labonnealerte.core.network.dto.SourceReportBody): MessageDto
}
