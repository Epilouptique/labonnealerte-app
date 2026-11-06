package fr.labonnealerte.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.labonnealerte.core.data.session.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionStoreTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: SessionStore

    @Before
    fun setUp() = runBlocking {
        store = SessionStore(ctx, TestCipher())
        store.currentToken()
        store.clear()
    }

    @Test
    fun `jeton chiffre relu par une nouvelle instance`() = runTest {
        store.save("TOK", "a@b.fr")
        val again = SessionStore(ctx, TestCipher())
        assertEquals("TOK", again.currentToken())
        assertEquals("a@b.fr", again.email())
        assertTrue(store.signedIn.value)
    }

    @Test
    fun `sans e-mail l ancien n est pas efface et deconnexion vide tout`() = runTest {
        store.save("T1", "a@b.fr")
        store.save("T2", null)
        assertEquals("a@b.fr", store.email())
        store.clear()
        assertNull(store.currentToken())
        assertNull(store.email())
        assertFalse(store.signedIn.value)
    }

    @Test
    fun `401 sur le jeton courant expire la session`() = runBlocking {
        store.save("TOK", null)
        store.onUnauthorized("TOK")
        assertTrue(store.expired.value)
        withTimeout(5_000) { store.signedIn.first { !it } }
        assertNull(store.currentToken())
    }

    @Test
    fun `401 tardif sur un ancien jeton n efface pas la nouvelle session`() = runBlocking {
        store.save("ANCIEN", null)
        store.save("NOUVEAU", null)
        store.onUnauthorized("ANCIEN")
        assertFalse(store.expired.value)
        assertEquals("NOUVEAU", store.currentToken())
        assertTrue(store.signedIn.value)
        assertEquals("NOUVEAU", SessionStore(ctx, TestCipher()).currentToken())
    }

    @Test
    fun `nouvelle connexion leve l etat expire`() = runBlocking {
        store.save("A", null)
        store.onUnauthorized("A")
        withTimeout(5_000) { store.signedIn.first { !it } }
        store.save("B", null)
        assertFalse(store.expired.value)
        assertEquals("B", store.currentToken())
    }
}
