package fr.labonnealerte.core.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.labonnealerte.core.network.SessionTokenProvider
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/** Chiffrement du jeton : abstrait pour les tests (Robolectric n'a pas d'AndroidKeyStore). */
interface TokenCipher {
    fun encrypt(plain: String): String
    fun decrypt(encoded: String): String?
}

/** AES-256-GCM avec une cle non exportable de l'Android Keystore. */
class KeystoreTokenCipher : TokenCipher {
    private val alias = "lba_session_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    override fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(out, Base64.NO_WRAP)
    }

    override fun decrypt(encoded: String): String? = runCatching {
        val (iv, data) = encoded.split(':', limit = 2)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(c.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()
}

private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * Jeton de session (Bearer, 90 jours glissants cote serveur) stocke chiffre.
 * Exclu des sauvegardes (app/src/main/res/xml/data_extraction_rules.xml). Jamais journalise.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: TokenCipher
) : SessionTokenProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val key = stringPreferencesKey("token_enc")
    private val emailKey = stringPreferencesKey("email_enc")

    @Volatile private var cached: String? = null

    @Volatile private var loaded = false
    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    /** Signale une session expiree (401) a l'UI. */
    private val _expired = MutableStateFlow(false)
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    init {
        scope.launch { ensureLoaded() }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        val prefs = context.sessionStore.data.first()
        cached = prefs[key]?.let { cipher.decrypt(it) }
        loaded = true
        _signedIn.value = cached != null
    }

    override fun currentToken(): String? {
        if (!loaded) runBlocking { ensureLoaded() }
        return cached
    }

    suspend fun email(): String? = context.sessionStore.data.first()[emailKey]?.let { cipher.decrypt(it) }

    suspend fun save(token: String, email: String?) {
        context.sessionStore.edit {
            it[key] = cipher.encrypt(token)
            if (email != null) it[emailKey] = cipher.encrypt(email)
        }
        cached = token
        loaded = true
        _signedIn.value = true
        _expired.value = false
    }

    suspend fun clear() {
        context.sessionStore.edit { it.clear() }
        cached = null
        _signedIn.value = false
    }

    override fun onUnauthorized(sentToken: String) {
        // Un 401 tardif ne doit pas effacer une session ouverte entre-temps.
        if (cached != sentToken) return
        Timber.i("Session refusee par le serveur (401) : deconnexion locale")
        _expired.value = true
        // Reverification au moment d effacer : une reconnexion a pu avoir lieu entre-temps.
        scope.launch { if (cached == sentToken) clear() }
    }
}
