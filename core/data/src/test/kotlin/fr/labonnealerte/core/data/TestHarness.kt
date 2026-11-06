package fr.labonnealerte.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.labonnealerte.core.data.session.SessionStore
import fr.labonnealerte.core.data.session.TokenCipher
import fr.labonnealerte.core.database.LbaDatabase
import fr.labonnealerte.core.network.AccountApi
import fr.labonnealerte.core.network.AuthInterceptor
import fr.labonnealerte.core.network.LbaApi
import fr.labonnealerte.core.network.lbaRetrofit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient

/** Chiffrement factice reversible (Robolectric n'a pas d'AndroidKeyStore). */
internal class TestCipher : TokenCipher {
    override fun encrypt(plain: String) = "x:" + plain.reversed()
    override fun decrypt(encoded: String) = encoded.removePrefix("x:").reversed()
}

/** Hook de session qui note les appels (et peut echouer, pour verifier qu'il est isole). */
internal class RecordingHook(private val fail: Boolean = false) : SessionHook {
    val calls = mutableListOf<String>()
    override suspend fun onSignedIn() {
        calls += "in"
    }
    override suspend fun beforeSignOut() {
        calls += "before"
        if (fail) error("hook")
    }
    override suspend fun afterSignOut() {
        calls += "after"
        if (fail) error("hook")
    }
}

internal fun json(code: Int, body: String): MockResponse =
    MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

internal const val MY_ALERTS = """{"email":"a@b.fr","token":"SESSION123","pseudo":"hugo","sources":[
 {"id":"vigilance-meteo","name":"Vigilance","subscribed":true,"muted":true,
  "instances":[{"params":{"departement":"05","rayon":10,"liste":["a"]},"label":"HA","state":"active"}],
  "tasks":[{"id":2,"label":"Controle technique","next_due":"2027-03-01T00:00:00Z"},{"id":1,"label":"Vidange","tracking_mode":"counter"}]},
 {"id":"echeances","name":"Echeances","subscribed":false,"tasks":[{"id":2,"label":"Controle technique","next_due":"2027-03-01"},
  {"id":3,"label":"Assurance","next_due":"2026-11-01","tracking_mode":"time"},{"id":4,"label":"Bad","next_due":"pas-une-date"}]}],
 "favorites":["vigilance-meteo"],"country":"FR","departement":"05","region":"PACA","ville":"Gap","interests":["meteo"]}"""

/** Serveur MockWebServer + base Room en memoire + session + depots relies comme dans l'app. */
internal class Harness(hooks: Set<SessionHook> = emptySet(), now: Long = 1_000L) {
    val ctx: Context = ApplicationProvider.getApplicationContext()
    val server = MockWebServer().apply { start() }
    val db: LbaDatabase = Room.inMemoryDatabaseBuilder(ctx, LbaDatabase::class.java).allowMainThreadQueries().build()
    val session = SessionStore(ctx, TestCipher())
    val clock = Clock { now }
    private val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(session, server.url("/").toString())).build()
    private val retrofit = lbaRetrofit(server.url("/").toString(), client)
    val accountApi: AccountApi = retrofit.create(AccountApi::class.java)
    val lbaApi: LbaApi = retrofit.create(LbaApi::class.java)
    val favorites = FavoritesRepository(db.favoriteDao(), clock)
    val account = AccountRepository(ctx, accountApi, session, favorites, hooks)

    fun close() {
        db.close()
        server.close()
    }
}
