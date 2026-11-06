package fr.labonnealerte.core.data

import kotlinx.coroutines.test.runTest
import org.junit.After
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
class CommunityAndForumTest {
    private lateinit var h: Harness
    private lateinit var community: CommunityRepository
    private lateinit var forum: ForumRepository

    @Before
    fun setUp() {
        h = Harness()
        community = CommunityRepository(h.accountApi)
        forum = ForumRepository(h.lbaApi, h.accountApi)
    }

    @After
    fun tearDown() = h.close()

    private fun take() = h.server.takeRequest()

    @Test
    fun `liste des signalements et etat ouvert`() = runTest {
        h.server.enqueue(
            json(
                200,
                """[{"id":1,"type":"chien-perdu","commune_nom":"Gap","status":"active","spot_count":2},
                {"id":2,"type":"inconnu","status":"resolved"},{"id":3,"type":"chat-perdu"}]"""
            )
        )
        val list = community.list(ReportType.CHIEN).getOrThrow()
        assertEquals("chien-perdu", take().url.queryParameter("type"))
        assertEquals(listOf(ReportType.CHIEN, ReportType.CHAT, ReportType.CHAT), list.map { it.type })
        assertEquals(listOf(true, false, true), list.map { it.open })
        assertEquals("Gap", list.first().commune)
        assertEquals(ReportType.CHAT, ReportType.of(null))
    }

    @Test
    fun `creation nettoie les champs et lien vide`() = runTest {
        h.server.enqueue(json(200, """{"created":true,"report":{"id":5,"type":"chat-perdu"}}"""))
        assertEquals(5L, community.create(ReportType.CHAT, " Gap ", 15, " Tigre ", "  ").getOrThrow()!!.id)
        val b = take().body!!.utf8()
        assertTrue(b, b.contains("\"ville\":\"Gap\"") && b.contains("\"description\":\"Tigre\"") && !b.contains("link"))
        h.server.enqueue(json(200, """{"joined":true}"""))
        assertNull(community.create(ReportType.CHIEN, "Gap", 10, "x", " https://a ").getOrThrow())
        assertTrue(take().body!!.utf8().contains("\"link\":\"https://a\""))
    }

    @Test
    fun `vu prolonger resoudre et erreurs`() = runTest {
        repeat(3) { h.server.enqueue(json(200, """{"message":"ok"}""")) }
        assertTrue(community.spot(1, " ici ", " hier ").isSuccess)
        assertEquals("""{"where":"ici","when":"hier"}""", take().body!!.utf8())
        assertTrue(community.extend(1).isSuccess)
        assertEquals("/api/community-reports/1/extend", take().url.encodedPath)
        assertTrue(community.resolve(1).isSuccess)
        assertEquals("/api/community-reports/1/resolve", take().url.encodedPath)
        h.server.enqueue(json(403, """{"error":"Pas l'auteur"}"""))
        assertEquals(AccountError.Rejected(403, "Pas l'auteur"), community.resolve(1).exceptionOrNull())
        h.server.enqueue(json(500, ""))
        assertEquals(AccountError.Rejected(500, null), community.extend(1).exceptionOrNull())
        h.server.close()
        assertEquals(AccountError.Offline, community.list(ReportType.CHAT).exceptionOrNull())
    }

    @Test
    fun `forum en lecture`() = runTest {
        h.server.enqueue(
            json(
                200,
                """{"categories":[{"slug":"general","label":"General","count":4}],
                "recent":[{"id":1,"slug":"s","title":"T","category":"general","reply_count":2,"author_pseudo":"p"}]}"""
            )
        )
        val (cats, recent) = forum.home().getOrThrow()
        assertEquals(4, cats.single().count)
        assertEquals("p", recent.single().authorPseudo)
        take()

        h.server.enqueue(json(200, """{"topics":[{"id":2,"slug":"a","title":"A","category":"c"}],"page":2,"has_more":true}"""))
        val page = forum.topics(category = "c", page = 2).getOrThrow()
        assertTrue(page.hasMore)
        assertEquals(2, page.page)
        assertEquals("Membre", page.items.single().author)
        take()

        h.server.enqueue(
            json(200, """{"topic":{"id":2,"slug":"a","title":"A","category":"c","locked":true},"posts":[{"id":7,"body":"b","created_at":"t"}]}""")
        )
        val t = forum.thread("a").getOrThrow()
        assertTrue(t.topic.locked)
        assertEquals(7L, t.posts.single().id)
        assertFalse(t.hasMore)
        take()

        h.server.enqueue(json(500, "{}"))
        assertTrue(forum.home().isFailure)
    }

    @Test
    fun `forum en ecriture et codes d erreur`() = runTest {
        h.server.enqueue(json(200, """{"slug":"nouveau"}"""))
        assertEquals("nouveau", forum.createTopic("c", " Titre ", " Corps ", null).getOrThrow())
        assertEquals("""{"category":"c","title":"Titre","body":"Corps"}""", take().body!!.utf8())

        h.server.enqueue(json(200, """{"ok":true}"""))
        assertTrue(forum.reply(3, " merci ").isSuccess)
        assertEquals("""{"body":"merci"}""", take().body!!.utf8())

        h.server.enqueue(json(200, """{"ok":true}"""))
        assertTrue(forum.report(4, "x".repeat(60)).isSuccess)
        assertEquals("""{"reason":"${"x".repeat(40)}"}""", take().body!!.utf8())

        val cases = listOf(
            401 to ForumError.NotSignedIn,
            429 to ForumError.TooMany,
            409 to ForumError.Locked,
            400 to ForumError.Invalid("Message trop court"),
            500 to ForumError.Server
        )
        for ((code, expected) in cases) {
            h.server.enqueue(json(code, """{"error":"Message trop court"}"""))
            assertEquals(expected, forum.reply(1, "a").exceptionOrNull())
        }
        h.server.enqueue(json(400, "pas du json"))
        assertEquals(ForumError.Invalid(null), forum.report(1, null).exceptionOrNull())
        h.server.close()
        assertEquals(ForumError.Offline, forum.reply(1, "a").exceptionOrNull())
    }
}
