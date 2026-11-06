package fr.labonnealerte.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.labonnealerte.core.data.session.SessionStore
import fr.labonnealerte.core.model.Account
import fr.labonnealerte.core.model.LoginResult
import fr.labonnealerte.core.model.Subscription
import fr.labonnealerte.core.model.SubscriptionInstance
import fr.labonnealerte.core.model.UserTask
import fr.labonnealerte.core.network.AccountApi
import fr.labonnealerte.core.network.LbaJson
import fr.labonnealerte.core.network.dto.DisplayNameBody
import fr.labonnealerte.core.network.dto.EmailBody
import fr.labonnealerte.core.network.dto.FavoriteBody
import fr.labonnealerte.core.network.dto.FavoriteSyncBody
import fr.labonnealerte.core.network.dto.MyAlertsDto
import fr.labonnealerte.core.network.dto.ProfileBody
import fr.labonnealerte.core.network.dto.VerifyCodeBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import timber.log.Timber

private val Context.accountStore: DataStore<Preferences> by preferencesDataStore(name = "account")

/** Erreur metier lisible par l'UI (jamais de texte technique a l'ecran). */
sealed class AccountError : Exception() {
    data object Offline : AccountError()
    data object NotSignedIn : AccountError()
    data class Rejected(val httpCode: Int, val serverMessage: String?) : AccountError()
}

/**
 * Compte : connexion (code a 6 chiffres ou lien magique), profil, deconnexion, suppression.
 * Le dernier etat du compte est garde (sans jeton) pour l'affichage hors ligne.
 */
@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AccountApi,
    private val session: SessionStore,
    private val favorites: FavoritesRepository,
    private val hooks: Set<@JvmSuppressWildcards SessionHook>
) {
    private val cacheKey = stringPreferencesKey("account_json")
    private val _account = MutableStateFlow<Account?>(null)

    val signedIn: StateFlow<Boolean> = session.signedIn
    val sessionExpired: StateFlow<Boolean> = session.expired

    /** Compte connu (cache puis serveur). Null si deconnecte. */
    val account: StateFlow<Account?> = _account.asStateFlow()

    val subscribedIds: Flow<Set<String>> = account.map { a ->
        a?.subscriptions?.filter { it.subscribed }?.map { it.sourceId }?.toSet().orEmpty()
    }

    suspend fun loadCached() {
        if (_account.value != null || session.currentToken() == null) return
        val raw = context.accountStore.data.first()[cacheKey] ?: return
        _account.value = runCatching { LbaJson.decodeFromString(Account.serializer(), raw) }.getOrNull()
    }

    /** Demande un e-mail contenant un lien et un code (reponse neutre du serveur). */
    suspend fun requestLogin(email: String): Result<Unit> = call { api.requestAppLogin(EmailBody(email.trim())) }.map { }

    /**
     * Valide le code. Pour une adresse sans compte, le serveur repond [LoginResult.TermsRequired]
     * (le code reste valable) ; on rappelle alors avec [acceptedTermsVersion] pour creer le compte.
     */
    suspend fun verifyCode(email: String, code: String, acceptedTermsVersion: String? = null): LoginResult = signIn {
        api.verifyAppLogin(
            VerifyCodeBody(
                email = email.trim(),
                code = code.filter { it.isDigit() },
                acceptTerms = acceptedTermsVersion?.let { true },
                termsVersion = acceptedTermsVersion
            )
        )
    }

    /** Ouvre la session a partir d'une reponse de connexion (code par e-mail ou Google, APP-22). */
    suspend fun signIn(request: suspend () -> fr.labonnealerte.core.network.dto.AppLoginDto): LoginResult = try {
        val r = request()
        val token = r.token
        if (token.isNullOrBlank()) {
            LoginResult.InvalidCode
        } else {
            session.save(token, r.email)
            refresh()
            hooks.forEach { it.onSignedIn() }
            if (r.created) LoginResult.Created else LoginResult.Success
        }
    } catch (e: IOException) {
        LoginResult.Offline
    } catch (e: kotlinx.serialization.SerializationException) {
        LoginResult.ServerError
    } catch (e: HttpException) {
        when (e.code()) {
            400 -> LoginResult.InvalidCode
            409 -> termsRequired(e) ?: LoginResult.ServerError
            429 -> LoginResult.TooManyAttempts
            else -> LoginResult.ServerError
        }
    }

    private fun termsRequired(e: HttpException): LoginResult.TermsRequired? = runCatching {
        val body = e.response()?.errorBody()?.string() ?: return null
        val dto = LbaJson.decodeFromString(fr.labonnealerte.core.network.dto.AppLoginDto.serializer(), body)
        val version = dto.termsVersion
        val url = dto.termsUrl
        // Lien affiche a l'utilisateur : uniquement une page https du site.
        val safeUrl = url?.takeIf { TERMS_URL.matches(it) }
        if (dto.needsTerms && !version.isNullOrBlank() && safeUrl != null) LoginResult.TermsRequired(version, safeUrl) else null
    }.getOrNull()

    /** Jeton venu du lien de l'e-mail (lien profond) : le serveur le change en session. */
    suspend fun consumeMagicToken(magicToken: String): LoginResult {
        if (!MAGIC_TOKEN.matches(magicToken)) return LoginResult.InvalidCode
        return try {
            val dto = api.myAlertsWithToken("Bearer $magicToken")
            val token = dto.token
            if (token.isNullOrBlank()) return LoginResult.InvalidCode
            session.save(token, dto.email)
            store(dto)
            hooks.forEach { it.onSignedIn() }
            LoginResult.Success
        } catch (e: IOException) {
            LoginResult.Offline
        } catch (e: HttpException) {
            if (e.code() == 401) LoginResult.InvalidCode else LoginResult.ServerError
        }
    }

    suspend fun refresh(): Result<Account> = call {
        if (session.currentToken() == null) throw AccountError.NotSignedIn
        val dto = api.myAlerts()
        store(dto)
        syncFavorites(dto)
        _account.value!!
    }

    private suspend fun store(dto: MyAlertsDto) {
        val acc = dto.toDomain()
        _account.value = acc
        context.accountStore.edit { it[cacheKey] = LbaJson.encodeToString(Account.serializer(), acc) }
    }

    /** Favoris : modifications locales poussees, puis l'etat serveur fait foi. */
    private suspend fun syncFavorites(dto: MyAlertsDto) {
        val pending = favorites.pendingChanges()
        val toAdd = pending.filter { it.pending == FavoritesRepository.PENDING_ADD }.map { it.sourceId }
        val toRemove = pending.filter { it.pending == FavoritesRepository.PENDING_REMOVE }.map { it.sourceId }
        runCatching {
            if (toAdd.isNotEmpty()) api.syncFavorites(FavoriteSyncBody((dto.favorites + toAdd).distinct()))
            toRemove.forEach { api.removeFavorite(FavoriteBody(it)) }
            favorites.replaceWithServer(((dto.favorites + toAdd) - toRemove.toSet()).distinct())
        }.onFailure { Timber.w("Synchronisation des favoris reportee") }
    }

    suspend fun updateProfile(country: String?, departement: String?, ville: String?): Result<Account> = call {
        val cur = _account.value ?: throw AccountError.NotSignedIn
        // Le serveur ecrase tous les champs : on renvoie tout. La region est deduite du
        // departement par le serveur (on n'envoie pas d'ancienne region incoherente).
        val fr = country == "FR"
        val p = api.profile(
            ProfileBody(
                country = country,
                departement = if (fr) departement else null,
                region = if (fr && departement != null) null else cur.region,
                ville = ville?.trim()?.take(80)?.ifBlank { null },
                interests = cur.interests
            )
        )
        val updated = cur.copy(
            country = p.country,
            departement = p.departement,
            region = p.region,
            ville = p.ville,
            interests = p.interests
        )
        _account.value = updated
        context.accountStore.edit { it[cacheKey] = LbaJson.encodeToString(Account.serializer(), updated) }
        updated
    }

    suspend fun updateDisplayName(name: String): Result<Account> = call {
        val cur = _account.value ?: throw AccountError.NotSignedIn
        val r = api.displayName(DisplayNameBody(name.trim()))
        cur.copy(displayName = r.displayName).also { _account.value = it }
    }

    /** Deconnexion : revoque la session cote serveur si possible, puis efface tout localement. */
    suspend fun logout() {
        hooks.forEach { runCatching { it.beforeSignOut() } }
        runCatching { api.logout() }
        clearLocal()
    }

    /** Suppression definitive du compte (RGPD). Les donnees locales sont effacees ensuite. */
    suspend fun deleteAccount(): Result<Unit> = call {
        hooks.forEach { runCatching { it.beforeSignOut() } }
        api.deleteAccount()
    }.map { clearLocal() }

    private suspend fun clearLocal() {
        session.clear()
        _account.value = null
        context.accountStore.edit { it.clear() }
        hooks.forEach { runCatching { it.afterSignOut() } }
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: AccountError) {
        Result.failure(e)
    } catch (e: IOException) {
        Timber.w("Reseau indisponible : %s %s", e.javaClass.simpleName, e.message)
        Result.failure(AccountError.Offline)
    } catch (e: kotlinx.serialization.SerializationException) {
        Result.failure(AccountError.Rejected(0, null))
    } catch (e: HttpException) {
        val msg = runCatching {
            e.response()?.errorBody()?.string()?.let { LbaJson.parseToJsonElement(it) }
                ?.let { (it as? kotlinx.serialization.json.JsonObject)?.get("error")?.jsonPrimitive?.content }
        }.getOrNull()
        Result.failure(AccountError.Rejected(e.code(), msg))
    }

    companion object {
        /** Jeton de lien magique : 64 caracteres hexadecimaux (crypto.randomBytes(32)). */
        val MAGIC_TOKEN = Regex("^[a-f0-9]{64}$")

        /** Page des conditions : https sur le domaine du site uniquement. */
        val TERMS_URL = Regex("^https://(www\\.)?labonnealerte\\.fr/[A-Za-z0-9/_-]{0,80}$")
    }
}

internal fun MyAlertsDto.toDomain() = Account(
    email = email.orEmpty(),
    displayName = displayName ?: pseudo,
    country = country,
    departement = departement,
    region = region,
    ville = ville,
    interests = interests,
    emailEnabled = emailEnabled,
    pushEndpointsCount = pushEndpointsCount,
    quietStart = quietStart,
    quietEnd = quietEnd,
    quietDisabled = quietDisabled,
    hideCommunityReports = hideCommunityReports,
    favorites = favorites,
    subscriptions = sources.map { s ->
        Subscription(
            sourceId = s.id,
            name = s.name,
            state = s.state,
            subscribed = s.subscribed,
            muted = s.muted == true,
            instances = s.instances.map { i ->
                SubscriptionInstance(
                    params = i.params?.mapValues { (_, v) -> (v as? JsonPrimitive)?.content ?: v.toString() }.orEmpty(),
                    label = i.label,
                    state = i.state,
                    muted = i.muted == true
                )
            },
            tasks = s.tasks.map { UserTask(it.id, it.label, it.trackingMode, it.nextDue) }
        )
    }
)
