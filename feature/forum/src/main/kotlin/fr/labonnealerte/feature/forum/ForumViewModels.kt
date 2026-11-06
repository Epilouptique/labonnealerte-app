package fr.labonnealerte.feature.forum

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.labonnealerte.core.data.AccountRepository
import fr.labonnealerte.core.data.ForumCategory
import fr.labonnealerte.core.data.ForumError
import fr.labonnealerte.core.data.ForumPost
import fr.labonnealerte.core.data.ForumRepository
import fr.labonnealerte.core.data.ForumTopic
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Etat generique d'un chargement reseau. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data object Error : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

/** Limites du serveur (server/ugc.js) : titre 5..140, message 1..5000. */
object ForumLimits {
    const val TITLE_MIN = 5
    const val TITLE_MAX = 140
    const val BODY_MAX = 5000
}

@HiltViewModel
class ForumHomeViewModel @Inject constructor(
    private val repo: ForumRepository,
    store: fr.labonnealerte.core.data.HiddenContentStore
) : ViewModel() {
    /** Auteurs masques sur ce telephone (APP-23). */
    val hidden: StateFlow<Set<String>> = store.hidden.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val _state = MutableStateFlow<Load<Pair<List<ForumCategory>, List<ForumTopic>>>>(Load.Loading)
    val state: StateFlow<Load<Pair<List<ForumCategory>, List<ForumTopic>>>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = Load.Loading
        viewModelScope.launch { _state.value = repo.home().fold({ Load.Ready(it) }, { Load.Error }) }
    }
}

data class TopicListState(
    val topics: List<ForumTopic> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
    val hasMore: Boolean = false,
    val page: Int = 0
)

/** Liste de sujets d'une categorie, d'une alerte (source) ou d'un membre. */
@HiltViewModel
class TopicListViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ForumRepository,
    store: fr.labonnealerte.core.data.HiddenContentStore
) : ViewModel() {
    val hidden: StateFlow<Set<String>> = store.hidden.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val category: String? = savedState.get<String>("category")
    val sourceId: String? = savedState.get<String>("source")
    val author: String? = savedState.get<String>("pseudo")

    private val _state = MutableStateFlow(TopicListState())
    val state: StateFlow<TopicListState> = _state.asStateFlow()

    init {
        loadMore()
    }

    fun loadMore() {
        val next = _state.value.page + 1
        _state.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            repo.topics(category, sourceId, author, next).fold(
                { p -> _state.update { it.copy(topics = it.topics + p.items, loading = false, hasMore = p.hasMore, page = next) } },
                { _state.update { it.copy(loading = false, error = true) } }
            )
        }
    }
}

enum class ForumMsg { ENVOYE, SIGNALE, MASQUE, HORS_LIGNE, CONNEXION, TROP, VERROUILLE, REFUSE, ERREUR }

data class ThreadState(
    val topic: ForumTopic? = null,
    val posts: List<ForumPost> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
    val hasMore: Boolean = false,
    val page: Int = 0,
    val signedIn: Boolean = false,
    val sending: Boolean = false,
    val message: ForumMsg? = null,
    val serverText: String? = null
)

@HiltViewModel
class ThreadViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ForumRepository,
    private val account: AccountRepository,
    private val store: fr.labonnealerte.core.data.HiddenContentStore
) : ViewModel() {
    val hidden: StateFlow<Set<String>> = store.hidden.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** « Masquer cet auteur » : local, annulable dans Reglages > Confidentialite. */
    fun hideAuthor(key: String) {
        viewModelScope.launch {
            store.hide(key)
            _state.update { it.copy(message = ForumMsg.MASQUE) }
        }
    }

    private val slug: String = savedState.get<String>("slug").orEmpty().take(200)
    private val _state = MutableStateFlow(ThreadState(signedIn = account.signedIn.value))
    val state: StateFlow<ThreadState> = _state.asStateFlow()

    init {
        loadMore()
    }

    fun loadMore() {
        val next = _state.value.page + 1
        _state.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            repo.thread(slug, next).fold(
                { t ->
                    _state.update {
                        it.copy(topic = t.topic, posts = it.posts + t.posts, loading = false, hasMore = t.hasMore, page = next)
                    }
                },
                { _state.update { it.copy(loading = false, error = true) } }
            )
        }
    }

    /** Apres une reponse : on recharge depuis le debut pour voir son message. */
    private fun reload() {
        _state.update { it.copy(posts = emptyList(), page = 0) }
        loadMore()
    }

    fun reply(body: String, onSent: () -> Unit) {
        val topic = _state.value.topic ?: return
        if (body.isBlank()) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            val r = repo.reply(topic.id, body)
            r.onSuccess {
                onSent()
                _state.update { it.copy(sending = false, message = ForumMsg.ENVOYE) }
                reload()
            }.onFailure { e -> fail(e) }
        }
    }

    fun report(postId: Long) {
        viewModelScope.launch {
            repo.report(postId, null).onSuccess { _state.update { it.copy(message = ForumMsg.SIGNALE) } }.onFailure { fail(it) }
        }
    }

    private fun fail(e: Throwable) = _state.update {
        it.copy(sending = false, message = e.toMsg(), serverText = (e as? ForumError.Invalid)?.serverMessage)
    }

    fun consumeMessage() = _state.update { it.copy(message = null, serverText = null) }
}

internal fun Throwable.toMsg(): ForumMsg = when (this) {
    ForumError.Offline -> ForumMsg.HORS_LIGNE
    ForumError.NotSignedIn -> ForumMsg.CONNEXION
    ForumError.TooMany -> ForumMsg.TROP
    ForumError.Locked -> ForumMsg.VERROUILLE
    is ForumError.Invalid -> ForumMsg.REFUSE
    else -> ForumMsg.ERREUR
}

data class NewTopicState(
    val categories: List<ForumCategory> = emptyList(),
    val sending: Boolean = false,
    val createdSlug: String? = null,
    val message: ForumMsg? = null,
    val serverText: String? = null
)

@HiltViewModel
class NewTopicViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: ForumRepository) : ViewModel() {
    val sourceId: String? = savedState.get<String>("source")?.takeIf { Regex("^[a-z0-9][a-z0-9-]{0,63}$").matches(it) }

    /** Rubrique pre-choisie (« Publier » ouvre « Propositions », APP-12). */
    val initialCategory: String? = savedState.get<String>("categorie")?.takeIf { Regex("^[a-z]{2,32}$").matches(it) }
    private val _state = MutableStateFlow(NewTopicState())
    val state: StateFlow<NewTopicState> = _state.asStateFlow()

    init {
        viewModelScope.launch { repo.home().onSuccess { (cats, _) -> _state.update { it.copy(categories = cats) } } }
    }

    fun send(category: String, title: String, body: String) {
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            repo.createTopic(category, title, body, sourceId).fold(
                { slug -> _state.update { it.copy(sending = false, createdSlug = slug ?: "", message = ForumMsg.ENVOYE) } },
                { e ->
                    _state.update { it.copy(sending = false, message = e.toMsg(), serverText = (e as? ForumError.Invalid)?.serverMessage) }
                }
            )
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null, serverText = null) }
}
