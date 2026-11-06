package fr.labonnealerte.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import fr.labonnealerte.core.ui.theme.LbaPalette

/** Liens externes : Custom Tab (jamais de WebView), seulement en https. */
/** Signalement d'un contenu sans route serveur (APP-23) : e-mail pre-rempli a la moderation. */
fun Context.sendAbuseReport(reportId: Long) {
    val i = android.content.Intent(android.content.Intent.ACTION_SENDTO, "mailto:contact@labonnealerte.fr".toUri())
        .putExtra(android.content.Intent.EXTRA_SUBJECT, "Signalement d'abus - signalement communautaire $reportId")
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(i)
    } catch (e: ActivityNotFoundException) {
        // Aucune messagerie : rien a ouvrir.
    }
}

fun Context.openExternal(url: String) {
    val uri: Uri = url.toUri()
    if (uri.scheme != "https") return
    try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder().setToolbarColor(LbaPalette.BgLight.toArgb()).build()
            )
            .build()
            .launchUrl(this, uri)
    } catch (e: ActivityNotFoundException) {
        // Aucun navigateur : rien a ouvrir.
    }
}

/** Feuille de partage Android (texte + lien). */
fun Context.shareText(subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
