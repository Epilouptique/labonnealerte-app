package fr.labonnealerte.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Fourni par le module :app (BuildConfig). */
interface ClientInfo {
    /** URL de base terminee par « / ». */
    val baseUrl: String

    /** Valeur de l'en-tete X-LBA-Client, ex. « android/0.1.0 ». */
    val clientHeader: String
}

/** Jeton de session (Bearer). Implemente par core:data (stockage chiffre). */
interface SessionTokenProvider {
    fun currentToken(): String?

    /** Appele sur 401 : [sentToken] (celui envoye) est invalide ou expire. */
    fun onUnauthorized(sentToken: String)
}

val LbaJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

/** Construction Retrofit partagee (application et tests). */
fun lbaRetrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(client)
    .addConverterFactory(LbaJson.asConverterFactory("application/json".toMediaType()))
    .build()

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun okHttp(info: ClientInfo, tokens: SessionTokenProvider): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(clientHeaders(info))
        .addInterceptor(AuthInterceptor(tokens, info.baseUrl))
        .addInterceptor(Utf8JsonInterceptor())
        .build()

    @Provides
    @Singleton
    fun retrofit(info: ClientInfo, client: OkHttpClient): Retrofit = lbaRetrofit(info.baseUrl, client)

    @Provides
    @Singleton
    fun api(retrofit: Retrofit): LbaApi = retrofit.create(LbaApi::class.java)

    @Provides
    @Singleton
    fun accountApi(retrofit: Retrofit): AccountApi = retrofit.create(AccountApi::class.java)

    @Provides
    @Singleton
    fun publicationsApi(retrofit: Retrofit): PublicationsApi = retrofit.create(PublicationsApi::class.java)

    @Provides
    @Singleton
    fun appExtrasApi(retrofit: Retrofit): AppExtrasApi = retrofit.create(AppExtrasApi::class.java)

    private fun clientHeaders(info: ClientInfo) = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("X-LBA-Client", info.clientHeader)
                .header("Accept", "application/json")
                .build()
        )
    }
}
