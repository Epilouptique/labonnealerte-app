package fr.labonnealerte.core.data

import fr.labonnealerte.core.network.AccountApi
import fr.labonnealerte.core.network.LbaApi
import fr.labonnealerte.core.network.dto.ForumPostDto
import fr.labonnealerte.core.network.dto.ForumReplyBody
import fr.labonnealerte.core.network.dto.ForumReportBody
import fr.labonnealerte.core.network.dto.ForumTopicBody
import fr.labonnealerte.core.network.dto.ForumTopicDto
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

data class ForumCategory(val slug: String, val label: String, val count: Int)

data class ForumTopic(
    val id: Long,
    val slug: String,
    val title: String,
    val category: String,
    val replyCount: Int,
    val lastReplyAt: String?,
    val locked: Boolean,
    val sourceId: String?,
    val sourceName: String?,
    val author: String,
    val authorPseudo: String?
)

data class ForumPost(val id: Long, val body: String, val createdAt: String, val author: String, val authorPseudo: String?)

data class ForumPage<T>(val items: List<T>, val page: Int, val hasMore: Boolean)

data class ForumThread(val topic: ForumTopic, val posts: List<ForumPost>, val page: Int, val hasMore: Boolean)

/** Erreur d'ecriture du forum, avec le message du serveur quand il est destine a l'utilisateur. */
sealed class ForumError : Exception() {
    data object Offline : ForumError()
    data object NotSignedIn : ForumError()
    data object TooMany : ForumError()
    data object Locked : ForumError()
    data class Invalid(val serverMessage: String?) : ForumError()
    data object Server : ForumError()
}

/** Forum : lecture par l'API JSON ajoutee pour l'app, ecriture par les routes existantes du site. */
@Singleton
class ForumRepository @Inject constructor(private val api: LbaApi, private val account: AccountApi) {

    suspend fun home(): Result<Pair<List<ForumCategory>, List<ForumTopic>>> = runCatching {
        val h = api.forumHome()
        h.categories.map { ForumCategory(it.slug, it.label, it.count) } to h.recent.map { it.toDomain() }
    }

    suspend fun topics(category: String? = null, sourceId: String? = null, author: String? = null, page: Int = 1): Result<ForumPage<ForumTopic>> =
        runCatching {
            val r = api.forumTopics(category, sourceId, author, page)
            ForumPage(r.topics.map { it.toDomain() }, r.page, r.hasMore)
        }

    suspend fun thread(slug: String, page: Int = 1): Result<ForumThread> = runCatching {
        val r = api.forumThread(slug, page)
        ForumThread(r.topic.toDomain(), r.posts.map { it.toDomain() }, r.page, r.hasMore)
    }

    suspend fun reply(topicId: Long, body: String): Result<Unit> = write { account.reply(topicId, ForumReplyBody(body.trim())) }

    /** Renvoie le slug du nouveau sujet. */
    suspend fun createTopic(category: String, title: String, body: String, sourceId: String?): Result<String?> =
        write { account.createTopic(ForumTopicBody(category, title.trim(), body.trim(), sourceId)).slug }

    suspend fun report(postId: Long, reason: String?): Result<Unit> = write {
        account.reportPost(postId, ForumReportBody(reason?.take(40)))
    }

    private suspend fun <T> write(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: IOException) {
        Result.failure(ForumError.Offline)
    } catch (e: kotlinx.serialization.SerializationException) {
        Result.failure(ForumError.Server)
    } catch (e: HttpException) {
        val msg = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            ?.let { Regex("\"error\"\\s*:\\s*\"([^\"]{1,200})\"").find(it)?.groupValues?.get(1) }
        Result.failure(
            when (e.code()) {
                401 -> ForumError.NotSignedIn
                429 -> ForumError.TooMany
                409 -> ForumError.Locked
                400 -> ForumError.Invalid(msg)
                else -> ForumError.Server
            }
        )
    }
}

private fun ForumTopicDto.toDomain() =
    ForumTopic(id, slug, title, category, replyCount, lastReplyAt, locked, sourceId, sourceName, author, authorPseudo)

private fun ForumPostDto.toDomain() = ForumPost(id, body, createdAt, author, authorPseudo)
