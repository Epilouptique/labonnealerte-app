package fr.labonnealerte.core.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Decodage UTF-8 explicite des reponses JSON sans charset (APP-10). OkHttp decode deja en UTF-8
 * par defaut (verifie par UTF8DecodingTest) ; cet intercepteur rend la regle visible et la
 * protege d'un changement de bibliotheque. Un charset annonce par le serveur est respecte.
 */
class Utf8JsonInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body
        val type = body.contentType() ?: return response
        if (type.subtype != "json" || type.charset() != null) return response
        val utf8 = "${type.type}/${type.subtype}; charset=utf-8".toMediaType()
        return response.newBuilder().body(body.bytes().toResponseBody(utf8)).build()
    }
}
