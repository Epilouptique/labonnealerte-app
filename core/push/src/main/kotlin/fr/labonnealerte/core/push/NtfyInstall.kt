package fr.labonnealerte.core.push

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Installation de ntfy, le relais UnifiedPush propose sans Google (APP-06, point 3c).
 * F-Droid installe sur le telephone : la page de ntfy s'ouvre dans F-Droid. Sinon : la page
 * officielle de ntfy (qui liste les sources d'installation). Le mot « F-Droid » n'est montre
 * que s'il est installe ([fdroidInstalled]).
 */
object NtfyInstall {
    /** Application F-Droid officielle (declaree dans <queries> du manifeste pour etre visible). */
    const val FDROID_PACKAGE = "org.fdroid.fdroid"
    const val NTFY_FDROID = "https://f-droid.org/packages/io.heckel.ntfy/"
    const val NTFY_OFFICIEL = "https://docs.ntfy.sh/subscribe/phone/"

    fun fdroidInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(FDROID_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** Ouvre la page d'installation adaptee ; rend false si rien n'a pu l'ouvrir. */
    fun open(context: Context): Boolean {
        val intents = buildList {
            if (fdroidInstalled(context)) add(view(NTFY_FDROID).setPackage(FDROID_PACKAGE))
            add(view(NTFY_OFFICIEL))
        }
        for (i in intents) {
            try {
                context.startActivity(i)
                return true
            } catch (_: ActivityNotFoundException) {
                // Essai suivant (F-Droid desinstalle entre-temps, pas de navigateur…).
            }
        }
        return false
    }

    private fun view(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
