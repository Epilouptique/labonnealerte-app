package fr.labonnealerte.core.network

import fr.labonnealerte.core.network.dto.CategoryDto
import fr.labonnealerte.core.network.dto.CommunesDto
import fr.labonnealerte.core.network.dto.GeoDto
import fr.labonnealerte.core.network.dto.HistoryDto
import fr.labonnealerte.core.network.dto.LePointDto
import fr.labonnealerte.core.network.dto.ParamLookupDto
import fr.labonnealerte.core.network.dto.SourceDto
import fr.labonnealerte.core.network.dto.SupportLinksDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/** Endpoints publics du site. Contrat : docs/contrat-api-app.md §3.1. */
interface LbaApi {
    /** 304 si [etag] correspond : corps vide, garder le cache. */
    @GET("api/sources")
    suspend fun sources(@Header("If-None-Match") etag: String?): Response<List<SourceDto>>

    @GET("api/categories")
    suspend fun categories(@Header("If-None-Match") etag: String?): Response<List<CategoryDto>>

    @GET("api/sources/{id}/history")
    suspend fun history(@Path("id") id: String, @QueryMap params: Map<String, String>): HistoryDto

    @GET("api/le-point")
    suspend fun lePoint(): LePointDto

    @GET("api/geo")
    suspend fun geo(): GeoDto

    @GET("api/communes")
    suspend fun communes(@Query("departement") departement: String): CommunesDto

    @GET("api/param-lookup/{source}")
    suspend fun paramLookup(@Path("source") source: String, @Query("q") q: String, @Query("dept") dept: String?): ParamLookupDto

    @GET("api/app/forum")
    suspend fun forumHome(): fr.labonnealerte.core.network.dto.ForumHomeDto

    @GET("api/app/forum/topics")
    suspend fun forumTopics(
        @Query("category") category: String?,
        @Query("source") source: String?,
        @Query("author") author: String?,
        @Query("page") page: Int
    ): fr.labonnealerte.core.network.dto.ForumTopicsDto

    @GET("api/app/forum/topics/{slug}")
    suspend fun forumThread(@Path("slug") slug: String, @Query("page") page: Int): fr.labonnealerte.core.network.dto.ForumThreadDto

    @GET("api/app/forum/members/{pseudo}")
    suspend fun forumMember(@Path("pseudo") pseudo: String): fr.labonnealerte.core.network.dto.ForumMemberDto

    @GET("api/support-links")
    suspend fun supportLinks(): SupportLinksDto
}
