package fr.labonnealerte.core.model

import java.util.Base64

/** Canal de reception des alertes (APP-22), miroir de notify_channel du site ; defaut serveur : both. */
enum class NotifyChannel(val id: String) {
    PUSH("push"),
    EMAIL("email"),
    BOTH("both");

    companion object {
        fun of(id: String?): NotifyChannel = entries.firstOrNull { it.id == id } ?: BOTH
    }
}

/**
 * Lecture locale (sans verification de signature, faite par le serveur) d'un jeton d'identite
 * Google : sert seulement a dire clairement quand l'audience ne correspond pas a l'ID client Web.
 */
object GoogleIdToken {
    fun claim(jwt: String, name: String): String? {
        val parts = jwt.split('.')
        if (parts.size != JWT_PARTS) return null
        val payload = runCatching { String(Base64.getUrlDecoder().decode(parts[1].trimEnd('=')), Charsets.UTF_8) }.getOrNull() ?: return null
        return Regex("\"" + Regex.escape(name) + "\"\\s*:\\s*\"([^\"]*)\"").find(payload)?.groupValues?.get(1)
    }

    /** Vrai si le jeton est destine a [webClientId] (ce que le serveur exige : aud = GOOGLE_CLIENT_ID). */
    fun audienceMatches(jwt: String, webClientId: String): Boolean = webClientId.isNotBlank() && claim(jwt, "aud") == webClientId

    private const val JWT_PARTS = 3
}

/** Ecrans du tutoriel de premiere ouverture (APP-22). */
object Tutorial {
    const val PAGES = 4

    fun next(page: Int): Int? = (page + 1).takeIf { it < PAGES }

    fun isLast(page: Int): Boolean = page >= PAGES - 1
}
