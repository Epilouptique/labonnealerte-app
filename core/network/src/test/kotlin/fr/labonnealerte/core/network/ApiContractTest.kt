package fr.labonnealerte.core.network

import fr.labonnealerte.core.network.dto.CreateReportBody
import fr.labonnealerte.core.network.dto.CreateTaskBody
import fr.labonnealerte.core.network.dto.DisplayNameBody
import fr.labonnealerte.core.network.dto.EmailBody
import fr.labonnealerte.core.network.dto.EmailPrefBody
import fr.labonnealerte.core.network.dto.FavoriteBody
import fr.labonnealerte.core.network.dto.FavoriteSyncBody
import fr.labonnealerte.core.network.dto.ForumReplyBody
import fr.labonnealerte.core.network.dto.ForumReportBody
import fr.labonnealerte.core.network.dto.ForumTopicBody
import fr.labonnealerte.core.network.dto.MuteBody
import fr.labonnealerte.core.network.dto.MyAlertsDto
import fr.labonnealerte.core.network.dto.ProfileBody
import fr.labonnealerte.core.network.dto.PushEndpointBody
import fr.labonnealerte.core.network.dto.PushKeysDto
import fr.labonnealerte.core.network.dto.PushSubscriptionBody
import fr.labonnealerte.core.network.dto.PushSubscriptionDto
import fr.labonnealerte.core.network.dto.QuietHoursBody
import fr.labonnealerte.core.network.dto.SourceDto
import fr.labonnealerte.core.network.dto.SpotBody
import fr.labonnealerte.core.network.dto.ToggleBody
import fr.labonnealerte.core.network.dto.ToggleParamBody
import fr.labonnealerte.core.network.dto.VerifyCodeBody
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiContractTest {
    private val server = MockWebServer()
    private lateinit var account: AccountApi
    private lateinit var api: LbaApi

    @Before
    fun setUp() {
        server.start()
        val retrofit = lbaRetrofit(server.url("/").toString(), OkHttpClient())
        account = retrofit.create(AccountApi::class.java)
        api = retrofit.create(LbaApi::class.java)
    }

    @After
    fun tearDown() = server.close()

    private fun reply(body: String, code: Int = 200, etag: String? = null) {
        val b = MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body)
        if (etag != null) b.addHeader("ETag", etag)
        server.enqueue(b.build())
    }

    private fun take(method: String, path: String): RecordedRequest {
        val r = server.takeRequest()
        assertEquals(method, r.method)
        assertEquals(path, r.url.encodedPath + (r.url.encodedQuery?.let { "?$it" } ?: ""))
        return r
    }

    private fun RecordedRequest.text() = body?.utf8() ?: ""

    @Test
    fun `DELETE authentifies envoient un corps vide`() = runTest {
        repeat(4) { reply("""{"ok":true,"message":"ok","id":"s","likes_count":3}""") }
        assertTrue(account.deleteAccount().ok)
        assertEquals("{}", take("DELETE", "/api/my-alerts/account").text())
        assertEquals("s", account.unlike("s").id)
        assertEquals("{}", take("DELETE", "/api/sources/s/like").text())
        account.deleteTask(7)
        assertEquals("{}", take("DELETE", "/api/user-tasks/7").text())
        account.removeFavorite(FavoriteBody("s"))
        assertEquals("""{"source_id":"s"}""", take("DELETE", "/api/favorites").text())
    }

    @Test
    fun `endpoints de compte methode chemin et corps`() = runTest {
        reply("""{"message":"envoye"}""")
        assertEquals("envoye", account.requestMagicLink(EmailBody("a@b.fr")).message)
        assertEquals("""{"email":"a@b.fr"}""", take("POST", "/api/my-alerts/request").text())

        reply("""{"events":[{"event":"activated","created_at":"2026-01-01","source_id":"s","extra":1}]}""")
        assertEquals("s", account.myHistory().events.single().sourceId)
        take("GET", "/api/my-alerts/history")

        reply("""{"source_id":"s","subscribed":true}""")
        assertTrue(account.toggle(ToggleBody("s", true)).subscribed)
        assertEquals("""{"source_id":"s","subscribed":true}""", take("POST", "/api/my-alerts/toggle").text())

        reply("""{"source_id":"s","subscribed":false,"params":{"d":"05"},"label":"L"}""")
        assertEquals("L", account.toggleParam(ToggleParamBody("s", mapOf("d" to JsonPrimitive("05")), false)).label)
        assertTrue(take("POST", "/api/my-alerts/toggle-param").text().contains("\"params\":{\"d\":\"05\"}"))

        reply("""{"source_id":"s","muted":true}""")
        assertTrue(account.toggleMute(MuteBody("s", true)).muted)
        take("POST", "/api/my-alerts/toggle-mute")

        reply("""{"country":"FR","interests":["x"]}""")
        assertEquals(listOf("x"), account.profile(ProfileBody("FR", null, null, null, listOf("x"))).interests)
        take("POST", "/api/my-alerts/profile")

        reply("""{"display_name":"Hugo"}""")
        assertEquals("Hugo", account.displayName(DisplayNameBody("Hugo")).displayName)
        assertEquals("""{"display_name":"Hugo"}""", take("POST", "/api/my-alerts/display-name").text())

        reply("""{"email_enabled":false}""")
        assertFalse(account.emailPreference(EmailPrefBody(false)).emailEnabled)
        take("POST", "/api/my-alerts/preferences")

        reply("""{}""")
        val q = account.quietHours(QuietHoursBody(true, 22, 7))
        assertEquals(23, q.quietStart)
        assertEquals(8, q.quietEnd)
        assertEquals("""{"disabled":true,"start":22,"end":7}""", take("POST", "/api/my-alerts/quiet-hours").text())

        reply("""{"ok":true}""")
        assertTrue(account.logout().ok)
        assertEquals("{}", take("POST", "/api/logout").text())

        reply("""{"token":"T","email":"a@b.fr"}""")
        assertEquals("T", account.verifyAppLogin(VerifyCodeBody("a@b.fr", "123456")).token)
        take("POST", "/api/app/login/verify")

        reply("""{"message":"ok"}""")
        account.requestAppLogin(EmailBody("a@b.fr"))
        take("POST", "/api/app/login/request")

        reply("""{"min_version_code":3,"maintenance":true,"inconnu":"x"}""")
        val cfg = account.appConfig()
        assertEquals(3, cfg.minVersionCode)
        assertTrue(cfg.maintenance)
        assertNull(cfg.vapidPublicKey)
        take("GET", "/api/app/config")

        reply("""{"events":[{"id":5,"source_id":"s","event":"activated","created_at":"t","categories":["a"]}],"last_id":5,"has_more":true}""")
        val ev = account.appEvents(4)
        assertEquals(5, ev.lastId)
        assertEquals(listOf("a"), ev.events.single().categories)
        take("GET", "/api/app/events?since_id=4&limit=100")
    }

    @Test
    fun `myAlerts tolere champs inconnus et absents et null`() = runTest {
        reply(
            """{"email":"a@b.fr","quiet_start":null,"points_balance":4,"sources":[{"id":"s","name":"S",
            "params_schema":[{"key":"d","values":[{"value":"05","label":"HA"}],"default":"05","extra":true}],
            "instances":[{"params":{"d":"05"}}],"tasks":[{"id":1,"label":"T","counter_current":1.5}]}]}"""
        )
        val dto: MyAlertsDto = account.myAlerts()
        assertEquals(23, dto.quietStart) // null force a la valeur par defaut (coerceInputValues)
        assertTrue(dto.emailEnabled)
        val src = dto.sources.single()
        assertEquals("string", src.paramsSchema!!.single().type)
        assertEquals(1.5, src.tasks.single().counterCurrent!!, 0.0)
        assertNull(server.takeRequest().headers["Authorization"])

        reply("""{"email":"a@b.fr"}""")
        account.myAlertsWithToken("Bearer M")
        assertEquals("Bearer M", take("GET", "/api/my-alerts").headers["Authorization"])
    }

    @Test
    fun `favoris likes push echeances`() = runTest {
        reply("""[{"id":"s","name":"S","likes_count":2,"unknown":[1]}]""")
        assertEquals(2, account.favorites().single().likesCount)
        take("GET", "/api/favorites")

        reply("""{"message":"ok"}""")
        account.syncFavorites(FavoriteSyncBody(listOf("a", "b")))
        assertEquals("""{"ids":["a","b"]}""", take("POST", "/api/favorites/sync").text())

        reply("""{"id":"s","likes_count":4}""")
        assertEquals(4, account.like("s").likesCount)
        assertEquals("{}", take("POST", "/api/sources/s/like").text())

        reply("""{"key":"K","enabled":true}""")
        assertEquals("K", account.vapidKey().key)
        take("GET", "/api/push/vapid-key")

        reply("""{"ok":true}""")
        account.pushSubscribe(PushSubscriptionBody(PushSubscriptionDto("https://e", PushKeysDto("p", "a"))))
        assertEquals(
            """{"subscription":{"endpoint":"https://e","keys":{"p256dh":"p","auth":"a"}}}""",
            take("POST", "/api/push/subscribe").text()
        )
        reply("""{"ok":true}""")
        account.pushUnsubscribe(PushEndpointBody("https://e"))
        take("POST", "/api/push/unsubscribe")

        reply("""{"task":{"id":9,"label":"Vidange","next_due":"2027-01-01"}}""")
        assertEquals(9L, account.createTask(CreateTaskBody("Vidange", "2026-01-01", 1, "year")).task!!.id)
        val body = take("POST", "/api/user-tasks").text()
        assertTrue(body, body.contains("\"periodicity_unit\":\"year\"") && !body.contains("announce_days"))

        reply("""{"id":9,"next_due":"2028-01-01"}""")
        assertEquals("2028-01-01", account.confirmTask(9).nextDue)
        take("POST", "/api/user-tasks/9/confirm")
    }

    @Test
    fun `signalements et forum en ecriture`() = runTest {
        reply("""[{"id":1,"type":"panne","lat":1.5,"spot_count":2,"is_author":true,"x":0}]""")
        val r = account.reports("panne").single()
        assertEquals(2, r.spotCount)
        assertTrue(r.isAuthor)
        take("GET", "/api/community-reports?type=panne")

        reply("[]")
        account.reports(null)
        take("GET", "/api/community-reports")

        reply("""{"joined":true,"report":{"id":2,"type":"panne"}}""")
        assertTrue(account.createReport(CreateReportBody("panne", "Gap", null, null, null)).joined)
        take("POST", "/api/community-reports")

        repeat(3) { reply("""{"message":"ok"}""") }
        account.spotReport(2, SpotBody("ici", "maintenant"))
        assertEquals("""{"where":"ici","when":"maintenant"}""", take("POST", "/api/community-reports/2/spot").text())
        account.extendReport(2)
        take("POST", "/api/community-reports/2/extend")
        account.resolveReport(2)
        take("POST", "/api/community-reports/2/resolve")

        reply("""{"slug":"sujet","url":"/forum/t/sujet"}""")
        assertEquals("sujet", account.createTopic(ForumTopicBody("general", "T", "B")).slug)
        take("POST", "/forum/t")
        reply("""{"ok":true}""")
        account.reply(3, ForumReplyBody("salut"))
        take("POST", "/forum/t/3/reply")
        reply("""{"ok":true}""")
        account.reportPost(4, ForumReportBody(null))
        take("POST", "/forum/post/4/report")
    }

    @Test
    fun `api publique catalogue geo forum`() = runTest {
        reply("""[{"id":"s","name":"S","categories":["meteo"]}]""", etag = "W/\"e\"")
        val res = api.sources("W/\"old\"")
        assertEquals("W/\"e\"", res.headers()["ETag"])
        assertEquals(listOf("meteo"), res.body()!!.single().categories)
        assertEquals("W/\"old\"", take("GET", "/api/sources").headers["If-None-Match"])

        reply("""[{"slug":"meteo","label":"Meteo","group":"g"}]""")
        assertEquals("g", api.categories(null).body()!!.single().group)
        take("GET", "/api/categories")

        reply("""{"events":[{"event":"e","created_at":"t"}],"days":[{"date":"d","status":"calm"}]}""")
        val h = api.history("s", mapOf("departement" to "05"))
        assertEquals("calm", h.days.single().status)
        take("GET", "/api/sources/s/history?departement=05")

        reply("""{"generated_at":"t","active":[{"kind":"k","id":"i","name":"n","labels":["l"]}],"upcoming":[{"id":"u"}]}""")
        val p = api.lePoint()
        assertEquals("u", p.upcoming.single().id)
        assertEquals(listOf("l"), p.active.single().labels)
        take("GET", "/api/le-point")

        reply("""{"countries":[{"code":"FR","name":"France"}],"departements":[{"code":"05","name":"HA","region":"PACA"}],"regions":[]}""")
        assertEquals("PACA", api.geo().departements.single().region)
        take("GET", "/api/geo")

        reply("""{"departement":"05","communes":["Gap"]}""")
        assertEquals(listOf("Gap"), api.communes("05").communes)
        take("GET", "/api/communes?departement=05")

        reply("""{"options":[{"value":"1","label":"Un"}]}""")
        assertEquals("Un", api.paramLookup("eau", "ga", null).options.single().label)
        take("GET", "/api/param-lookup/eau?q=ga")

        reply("""{"categories":[{"slug":"c","label":"C","count":2}],"recent":[{"id":1,"slug":"s","title":"T","category":"c"}]}""")
        val home = api.forumHome()
        assertEquals("Membre", home.recent.single().author)
        take("GET", "/api/app/forum")

        reply("""{"topics":[],"page":2,"has_more":true}""")
        assertTrue(api.forumTopics("c", null, null, 2).hasMore)
        take("GET", "/api/app/forum/topics?category=c&page=2")

        reply("""{"topic":{"id":1,"slug":"s","title":"T","category":"c","locked":true},"posts":[{"id":1,"body":"b","created_at":"t"}]}""")
        val t = api.forumThread("s", 1)
        assertTrue(t.topic.locked)
        assertEquals("Membre", t.posts.single().author)
        take("GET", "/api/app/forum/topics/s?page=1")

        reply("""{"pseudo":"p","display_name":"P"}""")
        assertEquals("P", api.forumMember("p").displayName)
        take("GET", "/api/app/forum/members/p")

        reply("""{"paypal":"https://p"}""")
        assertNull(api.supportLinks().kofi)
        take("GET", "/api/support-links")
    }

    @Test
    fun `serialisation directe des DTO avec valeurs par defaut`() {
        val s = LbaJson.decodeFromString(SourceDto.serializer(), """{"id":"a","name":"A","likes_count":null,"x":{"y":1}}""")
        assertEquals(0, s.likesCount)
        assertEquals(emptyList<String>(), s.categories)
        val back = LbaJson.encodeToString(SourceDto.serializer(), s)
        assertFalse(back, back.contains("subtitle")) // explicitNulls = false
    }
}
