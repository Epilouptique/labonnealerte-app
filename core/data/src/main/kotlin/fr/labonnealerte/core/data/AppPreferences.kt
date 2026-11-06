package fr.labonnealerte.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.labonnealerte.core.model.QuietHours
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.prefsStore: DataStore<Preferences> by preferencesDataStore(name = "preferences")

/** Mode de reception des alertes. */
enum class ReceptionMode {
    /** UnifiedPush si un distributeur est present, sinon verification periodique. */
    AUTO,
    UNIFIEDPUSH,

    /** Verification toutes les 15 minutes environ (WorkManager), sans distributeur. */
    PERIODIQUE
}

data class Settings(
    val theme: String = "system",
    val dynamicColor: Boolean = false,
    val fontScale: Float = 1f,
    val quietHours: QuietHours = QuietHours(),
    /** Exception aux heures calmes pour les urgences vitales (desactivee par defaut). */
    val urgentBypassQuiet: Boolean = false,
    val maxPerHour: Int = 10,
    val receptionMode: ReceptionMode = ReceptionMode.AUTO,
    val onboardingDone: Boolean = false,
    /** Tutoriel de premiere ouverture vu ou passe (APP-22). */
    val tutorialSeen: Boolean = false,
    val reduceMotion: Boolean = false,
    /** Options d'affichage (test), APP-11. */
    val lab: fr.labonnealerte.core.model.DisplayLab = fr.labonnealerte.core.model.DisplayLab()
)

/** Etat technique de la reception (diagnostic). */
data class ReceptionState(
    val lastEventId: Long = 0,
    val pushEndpoint: String? = null,
    val pushRegisteredOnServer: Boolean = false,
    val lastPushAtMillis: Long = 0,
    val lastPollAtMillis: Long = 0,
    val lastPollOk: Boolean = true,
    val distributor: String? = null,
    /**
     * Derniere releve qui a trouve, alors que le push etait actif, des evenements de plus de
     * 30 min jamais recus par push : le push instantane n'arrive plus (APP-05, decision E).
     */
    val missedPushAtMillis: Long = 0,
    /** Derniere releve REUSSIE (une releve en echec met quand meme a jour [lastPollAtMillis]). */
    val lastPollSuccessAtMillis: Long = 0
)

@Singleton
@Suppress("TooManyFunctions") // une preference = un setter
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val fontScale = floatPreferencesKey("font_scale")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val quietOn = booleanPreferencesKey("quiet_on")
        val urgentBypass = booleanPreferencesKey("urgent_bypass")
        val maxPerHour = intPreferencesKey("max_per_hour")
        val mode = stringPreferencesKey("reception_mode")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val tutorial = booleanPreferencesKey("tutorial_seen")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val lastEventId = longPreferencesKey("last_event_id")
        val endpoint = stringPreferencesKey("push_endpoint")
        val endpointOnServer = booleanPreferencesKey("push_on_server")
        val lastPush = longPreferencesKey("last_push_at")
        val lastPoll = longPreferencesKey("last_poll_at")
        val lastPollOk = booleanPreferencesKey("last_poll_ok")
        val distributor = stringPreferencesKey("distributor")
        val deptPrefere = stringPreferencesKey("departement_prefere")
        val missedPush = longPreferencesKey("missed_push_at")
        val lastPollSuccess = longPreferencesKey("last_poll_success_at")
        val welcomePending = booleanPreferencesKey("welcome_pending")
        val permissionAsked = booleanPreferencesKey("notif_permission_asked")
        fun mutedUntil(sourceId: String) = longPreferencesKey("muted_until_$sourceId")
    }

    val settings: Flow<Settings> = context.prefsStore.data.map { p ->
        Settings(
            theme = p[K.theme] ?: "system",
            dynamicColor = p[K.dynamic] ?: false,
            fontScale = p[K.fontScale] ?: 1f,
            quietHours = QuietHours(p[K.quietStart] ?: 23, p[K.quietEnd] ?: 8, p[K.quietOn] ?: true),
            urgentBypassQuiet = p[K.urgentBypass] ?: false,
            maxPerHour = p[K.maxPerHour] ?: 10,
            receptionMode = p[K.mode]?.let { runCatching { ReceptionMode.valueOf(it) }.getOrNull() } ?: ReceptionMode.AUTO,
            onboardingDone = p[K.onboarding] ?: false,
            tutorialSeen = p[K.tutorial] ?: false,
            reduceMotion = p[K.reduceMotion] ?: false,
            lab = fr.labonnealerte.core.model.DisplayLab.of { o -> p[booleanPreferencesKey(o.key)] }
        )
    }

    val reception: Flow<ReceptionState> = context.prefsStore.data.map { p ->
        ReceptionState(
            lastEventId = p[K.lastEventId] ?: 0,
            pushEndpoint = p[K.endpoint],
            pushRegisteredOnServer = p[K.endpointOnServer] ?: false,
            lastPushAtMillis = p[K.lastPush] ?: 0,
            lastPollAtMillis = p[K.lastPoll] ?: 0,
            lastPollOk = p[K.lastPollOk] ?: true,
            distributor = p[K.distributor],
            missedPushAtMillis = p[K.missedPush] ?: 0,
            lastPollSuccessAtMillis = p[K.lastPollSuccess] ?: 0
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun currentReception(): ReceptionState = reception.first()

    suspend fun setTheme(v: String) = context.prefsStore.edit { it[K.theme] = v }
    suspend fun setDynamicColor(v: Boolean) = context.prefsStore.edit { it[K.dynamic] = v }
    suspend fun setFontScale(v: Float) = context.prefsStore.edit { it[K.fontScale] = v.coerceIn(0.85f, 2f) }
    suspend fun setReduceMotion(v: Boolean) = context.prefsStore.edit { it[K.reduceMotion] = v }
    suspend fun setQuietHours(q: QuietHours) = context.prefsStore.edit {
        it[K.quietStart] = q.start.coerceIn(0, 23)
        it[K.quietEnd] = q.end.coerceIn(0, 23)
        it[K.quietOn] = q.enabled
    }
    suspend fun setUrgentBypass(v: Boolean) = context.prefsStore.edit { it[K.urgentBypass] = v }
    suspend fun setMaxPerHour(v: Int) = context.prefsStore.edit { it[K.maxPerHour] = v.coerceIn(1, 60) }
    suspend fun setReceptionMode(m: ReceptionMode) = context.prefsStore.edit { it[K.mode] = m.name }
    suspend fun setOnboardingDone(v: Boolean) = context.prefsStore.edit { it[K.onboarding] = v }
    suspend fun setTutorialSeen(v: Boolean) = context.prefsStore.edit { it[K.tutorial] = v }

    /** Le curseur ne recule jamais (pas de re-notification apres une reinstallation de donnees). */
    suspend fun advanceLastEventId(id: Long) = context.prefsStore.edit { if (id > (it[K.lastEventId] ?: 0)) it[K.lastEventId] = id }
    suspend fun setPushEndpoint(url: String?, onServer: Boolean) = context.prefsStore.edit {
        if (url == null) it.remove(K.endpoint) else it[K.endpoint] = url
        it[K.endpointOnServer] = onServer
    }
    suspend fun setDistributor(d: String?) = context.prefsStore.edit { if (d == null) it.remove(K.distributor) else it[K.distributor] = d }
    suspend fun markPush(now: Long) = context.prefsStore.edit { it[K.lastPush] = now }
    suspend fun markMissedPush(now: Long) = context.prefsStore.edit { it[K.missedPush] = now }

    /** Bandeau d'accueil du kiosque, affiche une seule fois apres la creation d'un compte. */
    val welcomePending: Flow<Boolean> = context.prefsStore.data.map { it[K.welcomePending] ?: false }
    suspend fun setWelcomePending(v: Boolean) = context.prefsStore.edit { it[K.welcomePending] = v }

    /**
     * La fenetre systeme de permission de notification a deja ete montree (quelle que soit la
     * reponse). Avant : invitation positive ; apres un refus : bandeau de probleme (APP-06).
     */
    suspend fun markNotificationPermissionAsked() = context.prefsStore.edit { it[K.permissionAsked] = true }
    suspend fun notificationPermissionAsked(): Boolean = context.prefsStore.data.first()[K.permissionAsked] ?: false
    suspend fun markPoll(now: Long, ok: Boolean) = context.prefsStore.edit {
        it[K.lastPoll] = now
        it[K.lastPollOk] = ok
        if (ok) it[K.lastPollSuccess] = now
    }

    /** Sourdine locale (1 h / 24 h) depuis une notification. */
    suspend fun muteSource(sourceId: String, untilMillis: Long) = context.prefsStore.edit { it[K.mutedUntil(sourceId)] = untilMillis }
    suspend fun mutedUntil(sourceId: String): Long = context.prefsStore.data.first()[K.mutedUntil(sourceId)] ?: 0

    /** Departement choisi a l accueil (sans compte) : pre-remplit les formulaires d abonnement. */
    suspend fun setDepartementPrefere(code: String) = context.prefsStore.edit { it[K.deptPrefere] = code.take(3) }
    suspend fun departementPrefere(): String? = context.prefsStore.data.first()[K.deptPrefere]

    suspend fun clearAll() = context.prefsStore.edit { it.clear() }
}

/** Ecriture des options d'affichage (test), APP-11 ; lues avec les autres reglages ([AppPreferences.settings]). */
@Singleton
class DisplayLabStore @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun set(o: fr.labonnealerte.core.model.DisplayLab.Option, on: Boolean) {
        context.prefsStore.edit { it[booleanPreferencesKey(o.key)] = on }
    }

    /** « Tout remettre par defaut » : retire toutes les options. */
    suspend fun reset() {
        context.prefsStore.edit { p -> fr.labonnealerte.core.model.DisplayLab.Option.entries.forEach { p.remove(booleanPreferencesKey(it.key)) } }
    }
}

/** APP-23 : auteurs et signalements masques sur ce telephone (cles de HiddenContent). */
@Singleton
class HiddenContentStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val key = androidx.datastore.preferences.core.stringSetPreferencesKey("hidden_content")

    val hidden: Flow<Set<String>> = context.prefsStore.data.map { it[key].orEmpty() }

    suspend fun hide(k: String) {
        context.prefsStore.edit { it[key] = it[key].orEmpty() + k }
    }

    suspend fun show(k: String) {
        context.prefsStore.edit { it[key] = it[key].orEmpty() - k }
    }
}
