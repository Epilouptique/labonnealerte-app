package fr.labonnealerte.core.model

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** APP-22 : canal de reception, audience du jeton Google, pages du tutoriel. */
class AppExtrasTest {
    private fun jwt(payload: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString("{\"alg\":\"RS256\"}".toByteArray()) + "." + enc.encodeToString(payload.toByteArray()) + ".sig"
    }

    @Test
    fun `canal - valeurs du serveur, defaut both`() {
        assertEquals(NotifyChannel.PUSH, NotifyChannel.of("push"))
        assertEquals(NotifyChannel.EMAIL, NotifyChannel.of("email"))
        assertEquals(NotifyChannel.BOTH, NotifyChannel.of(null))
        assertEquals(NotifyChannel.BOTH, NotifyChannel.of("sms"))
    }

    @Test
    fun `jeton Google - audience comparee a l ID client Web`() {
        val t = jwt("{\"aud\":\"web.apps.googleusercontent.com\",\"email\":\"a@b.fr\"}")
        assertTrue(GoogleIdToken.audienceMatches(t, "web.apps.googleusercontent.com"))
        assertFalse(GoogleIdToken.audienceMatches(t, "android.apps.googleusercontent.com"))
        assertFalse(GoogleIdToken.audienceMatches(t, ""))
        assertEquals("a@b.fr", GoogleIdToken.claim(t, "email"))
        assertNull(GoogleIdToken.claim("pas-un-jeton", "aud"))
    }

    @Test
    fun `tutoriel - quatre pages`() {
        assertEquals(1, Tutorial.next(0))
        assertNull(Tutorial.next(3))
        assertTrue(Tutorial.isLast(3))
        assertFalse(Tutorial.isLast(2))
    }
}
