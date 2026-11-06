package fr.labonnealerte.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Ajoute « Authorization: Bearer » uniquement vers l'hote du service (jamais vers un tiers),
 * et signale les 401 pour deconnecter proprement. Le jeton n'est jamais journalise.
 */
class AuthInterceptor(
    private val tokens: SessionTokenProvider,
    baseUrl: String
) : Interceptor {
    private val host = baseUrl.toHttpUrlOrNull()?.host

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val token = tokens.currentToken()
        val sameHost = req.url.host == host
        val added = token != null && sameHost && req.header("Authorization") == null
        val out = if (added) {
            req.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            req
        }
        val res = chain.proceed(out)
        if (res.code == 401 && added) tokens.onUnauthorized(token!!)
        return res
    }
}
