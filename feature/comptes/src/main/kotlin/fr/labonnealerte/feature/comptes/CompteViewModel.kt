package fr.labonnealerte.feature.comptes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.labonnealerte.core.data.AccountError
import fr.labonnealerte.core.data.AccountRepository
import fr.labonnealerte.core.data.CatalogRepository
import fr.labonnealerte.core.data.Clock
import fr.labonnealerte.core.model.Account
import fr.labonnealerte.core.model.Geo
import fr.labonnealerte.core.model.LoginResult
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Adresse -> code -> (creation seulement) conditions. */
enum class LoginStep { EMAIL, CODE, TERMS }

/** Messages a l'ecran : des cles, jamais de texte technique. */
enum class CompteMessage {
    CODE_INVALIDE,
    CODE_EXPIRE,
    TROP_ESSAIS,
    HORS_LIGNE,
    ERREUR_SERVEUR,
    EMAIL_INVALIDE,
    LIEN_INVALIDE,
    PROFIL_ENREGISTRE,
    PSEUDO_PRIS,
    PSEUDO_LIMITE,
    SESSION_EXPIREE,
    COMPTE_SUPPRIME,
    COMPTE_CREE,
    GOOGLE_INDISPONIBLE,
    GOOGLE_AUDIENCE,
    GOOGLE_REFUSE
}

data class CompteUiState(
    val signedIn: Boolean = false,
    val account: Account? = null,
    val geo: Geo? = null,
    val step: LoginStep = LoginStep.EMAIL,
    val email: String = "",
    val busy: Boolean = false,
    val message: CompteMessage? = null,
    /** Lien de connexion recu, en attente de confirmation par l utilisateur. */
    val pendingLink: String? = null,
    /** Creation de compte : code deja verifie par le serveur, en attente des conditions. */
    val pendingCode: String? = null,
    /** Creation de compte par Google (APP-22) : jeton en attente des conditions. */
    val pendingGoogle: String? = null,
    val termsVersion: String? = null,
    val termsUrl: String? = null,
    val termsAccepted: Boolean = false,
    /** Envoi du dernier code (expiration a 30 min) et heure a partir de laquelle on peut renvoyer. */
    val codeSentAt: Long = 0,
    val resendAt: Long = 0,
    /** Erreur affichee sous le champ du code (code faux, expire, trop d'essais, reseau). */
    val codeError: CompteMessage? = null,
    /** Change a chaque erreur de code : l'ecran vide alors le champ et y remet le focus. */
    val codeAttempt: Int = 0,
    /** Evenement ponctuel : connexion ou creation reussie ; l'app choisit l'ecran suivant. */
    val signedInEvent: SignedInEvent? = null
)

@HiltViewModel
class CompteViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: AccountRepository,
    private val catalog: CatalogRepository,
    private val clock: Clock,
    private val extras: fr.labonnealerte.core.data.AppExtrasRepository,
    private val google: GoogleSignInProvider
) : ViewModel() {

    /** Bouton « Continuer avec Google » : seulement si l'ID client Web est configure (play). */
    val googleEnabled: Boolean get() = google.enabled

    private val local = MutableStateFlow(CompteUiState())
    private val geo = MutableStateFlow<Geo?>(null)

    val state: StateFlow<CompteUiState> = combine(local, repo.signedIn, repo.account, geo, repo.sessionExpired) {
            l,
            signed,
            acc,
            g,
            expired
        ->
        l.copy(
            signedIn = signed,
            account = acc,
            geo = g,
            message = l.message ?: if (expired && !signed) CompteMessage.SESSION_EXPIREE else null
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompteUiState())

    init {
        viewModelScope.launch {
            repo.loadCached()
            if (repo.signedIn.value) repo.refresh()
            catalog.geo().onSuccess { geo.value = it }
        }
        // Lien de l'e-mail ouvert dans l'app : https://labonnealerte.fr/connexion?token=… ou labonnealerte://connexion?token=…
        // Lien recu de l exterieur : jamais consomme sans confirmation (un tiers pourrait sinon
        // connecter l utilisateur a SON compte). Le jeton est seulement retenu en attente.
        savedState.get<String>("token")?.takeIf { TOKEN.matches(it) }?.let { t -> local.update { it.copy(pendingLink = t) } }
    }

    fun onEmail(v: String) = local.update { it.copy(email = v.take(254), message = null) }

    fun requestCode() {
        val email = local.value.email.trim()
        if (!EMAIL.matches(email)) {
            local.update { it.copy(message = CompteMessage.EMAIL_INVALIDE) }
            return
        }
        if (local.value.step == LoginStep.CODE && clock.now() < local.value.resendAt) return
        local.update { it.copy(busy = true, message = null, codeError = null) }
        viewModelScope.launch {
            val r = repo.requestLogin(email)
            val now = clock.now()
            local.update {
                if (r.isSuccess) {
                    it.copy(busy = false, step = LoginStep.CODE, codeSentAt = now, resendAt = now + RESEND_DELAY_MS)
                } else {
                    timber.log.Timber.w("Demande de code echouee : %s", r.exceptionOrNull()?.javaClass?.simpleName)
                    it.copy(busy = false, message = r.exceptionOrNull().toMessage())
                }
            }
        }
    }

    fun verifyCode(code: String) = submit(code, acceptedTermsVersion = null)

    fun onTermsChecked(v: Boolean) = local.update { it.copy(termsAccepted = v) }

    /** Creation de compte : conditions cochees, on renvoie le meme code avec la version acceptee. */
    fun createAccount() {
        val st = local.value
        val version = st.termsVersion ?: return
        if (!st.termsAccepted) return
        st.pendingGoogle?.let { return googleLogin(it, version) }
        val code = st.pendingCode ?: return
        submit(code, acceptedTermsVersion = version)
    }

    /** « Continuer avec Google » : jeton demande a Credential Manager, verifie par le serveur. */
    fun signInWithGoogle(activity: android.content.Context) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            when (val o = google.idToken(activity)) {
                GoogleIdOutcome.Cancelled -> local.update { it.copy(busy = false) }
                GoogleIdOutcome.Unavailable -> local.update { it.copy(busy = false, message = CompteMessage.GOOGLE_INDISPONIBLE) }
                is GoogleIdOutcome.Token ->
                    // Le serveur exige aud = ID client Web : on le dit au lieu d'envoyer un jeton refuse d'avance.
                    if (!fr.labonnealerte.core.model.GoogleIdToken.audienceMatches(o.idToken, google.webClientId)) {
                        timber.log.Timber.w("Jeton Google : audience differente de l'ID client Web")
                        local.update { it.copy(busy = false, message = CompteMessage.GOOGLE_AUDIENCE) }
                    } else {
                        local.update { it.copy(busy = false) }
                        googleLogin(o.idToken, null)
                    }
            }
        }
    }

    private fun googleLogin(idToken: String, acceptedTermsVersion: String?) {
        local.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val r = extras.loginGoogle(idToken, acceptedTermsVersion)
            val event = signedInEvent(r)
            local.update {
                when (r) {
                    is LoginResult.TermsRequired -> it.copy(
                        busy = false,
                        step = LoginStep.TERMS,
                        email = fr.labonnealerte.core.model.GoogleIdToken.claim(idToken, "email") ?: it.email,
                        pendingGoogle = idToken,
                        pendingCode = null,
                        termsVersion = r.version,
                        termsUrl = r.url,
                        termsAccepted = it.termsAccepted && it.termsVersion == r.version
                    )
                    LoginResult.Success, LoginResult.Created -> CompteUiState(message = r.toMessage(), signedInEvent = event)
                    LoginResult.InvalidCode -> it.copy(busy = false, message = CompteMessage.GOOGLE_REFUSE)
                    else -> it.copy(busy = false, message = r.toMessage())
                }
            }
            if (event != null) catalog.geo().onSuccess { geo.value = it }
        }
    }

    private fun submit(code: String, acceptedTermsVersion: String?) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, message = null, codeError = null) }
        viewModelScope.launch {
            val r = repo.verifyCode(local.value.email, code, acceptedTermsVersion)
            val event = signedInEvent(r)
            local.update {
                when (r) {
                    is LoginResult.TermsRequired -> it.copy(
                        busy = false,
                        step = LoginStep.TERMS,
                        pendingCode = code,
                        termsVersion = r.version,
                        termsUrl = r.url,
                        // Nouvelle version du texte : la case doit etre recochee.
                        termsAccepted = it.termsAccepted && it.termsVersion == r.version
                    )
                    LoginResult.Success, LoginResult.Created -> CompteUiState(message = r.toMessage(), signedInEvent = event)
                    // Etape du code : erreur affichee sous le champ, qui est vide pour un nouvel essai.
                    else -> if (it.step == LoginStep.CODE) {
                        it.copy(busy = false, codeError = codeErrorFor(r, it.codeSentAt), codeAttempt = it.codeAttempt + 1)
                    } else {
                        it.copy(busy = false, message = r.toMessage())
                    }
                }
            }
            if (event != null) catalog.geo().onSuccess { geo.value = it }
        }
    }

    fun confirmPendingLink() {
        val t = local.value.pendingLink ?: return
        local.update { it.copy(pendingLink = null) }
        consumeLink(t)
    }

    fun cancelPendingLink() = local.update { it.copy(pendingLink = null) }

    fun backToEmail() = local.update {
        it.copy(
            step = LoginStep.EMAIL,
            message = null,
            codeError = null,
            pendingCode = null,
            pendingGoogle = null,
            termsVersion = null,
            termsUrl = null,
            termsAccepted = false,
            resendAt = 0
        )
    }

    private fun codeErrorFor(r: LoginResult, sentAt: Long): CompteMessage? = codeErrorMessage(r, sentAt, clock.now())

    private fun signedInEvent(r: LoginResult): SignedInEvent? = when (r) {
        LoginResult.Success, LoginResult.Created -> SignedInEvent(
            created = r == LoginResult.Created,
            hasSubscriptions = repo.account.value?.subscriptions?.any { s -> s.subscribed } == true
        )
        else -> null
    }

    fun consumeLink(token: String) {
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = repo.consumeMagicToken(token.trim())
            val event = signedInEvent(r)
            local.update {
                it.copy(
                    busy = false,
                    message = if (r ==
                        LoginResult.InvalidCode
                    ) {
                        CompteMessage.LIEN_INVALIDE
                    } else {
                        r.toMessage()
                    },
                    signedInEvent = event
                )
            }
        }
    }

    fun consumeSignedIn() = local.update { it.copy(signedInEvent = null) }

    fun saveProfile(country: String?, departement: String?, ville: String?) {
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = repo.updateProfile(country, departement, ville)
            local.update {
                it.copy(busy = false, message = if (r.isSuccess) CompteMessage.PROFIL_ENREGISTRE else r.exceptionOrNull().toMessage())
            }
        }
    }

    fun saveDisplayName(name: String) {
        viewModelScope.launch {
            val r = repo.updateDisplayName(name)
            val err = r.exceptionOrNull()
            local.update {
                it.copy(
                    message = when {
                        err == null -> CompteMessage.PROFIL_ENREGISTRE
                        err is AccountError.Rejected && err.httpCode == 409 -> CompteMessage.PSEUDO_PRIS
                        err is AccountError.Rejected && err.httpCode == 429 -> CompteMessage.PSEUDO_LIMITE
                        else -> err.toMessage()
                    }
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch { repo.logout() }
    }

    fun deleteAccount() {
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = repo.deleteAccount()
            local.update {
                it.copy(busy = false, message = if (r.isSuccess) CompteMessage.COMPTE_SUPPRIME else r.exceptionOrNull().toMessage())
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { repo.refresh() }
    }

    fun consumeMessage() = local.update { it.copy(message = null) }

    private fun Throwable?.toMessage(): CompteMessage = when (this) {
        is AccountError.Offline -> CompteMessage.HORS_LIGNE
        is AccountError.Rejected -> if (httpCode == 429) CompteMessage.TROP_ESSAIS else CompteMessage.ERREUR_SERVEUR
        else -> CompteMessage.ERREUR_SERVEUR
    }

    private fun LoginResult.toMessage(): CompteMessage? = when (this) {
        LoginResult.Success -> null
        LoginResult.Created -> CompteMessage.COMPTE_CREE
        is LoginResult.TermsRequired -> null
        LoginResult.InvalidCode -> CompteMessage.CODE_INVALIDE
        LoginResult.TooManyAttempts -> CompteMessage.TROP_ESSAIS
        LoginResult.Offline -> CompteMessage.HORS_LIGNE
        LoginResult.ServerError -> CompteMessage.ERREUR_SERVEUR
    }

    companion object {
        val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        val TOKEN = Regex("^[a-f0-9]{64}$")

        /** Duree de validite d'un code cote serveur (CODE_TTL_MS de app-api.js). */
        const val CODE_TTL_MS = 30 * 60_000L

        /** Attente avant de pouvoir redemander un code (le serveur en accepte 3 par 15 min). */
        const val RESEND_DELAY_MS = 60_000L
    }
}

/**
 * Message sous le champ du code. Le serveur repond 400 pour un code faux comme pour un code
 * expire : l'heure d'envoi du code tranche (30 min, comme cote serveur).
 */
fun codeErrorMessage(r: LoginResult, sentAt: Long, now: Long): CompteMessage? = when (r) {
    LoginResult.InvalidCode ->
        if (sentAt > 0 && now - sentAt >= CompteViewModel.CODE_TTL_MS) CompteMessage.CODE_EXPIRE else CompteMessage.CODE_INVALIDE
    LoginResult.TooManyAttempts -> CompteMessage.TROP_ESSAIS
    LoginResult.Offline -> CompteMessage.HORS_LIGNE
    LoginResult.ServerError -> CompteMessage.ERREUR_SERVEUR
    LoginResult.Success, LoginResult.Created, is LoginResult.TermsRequired -> null
}
