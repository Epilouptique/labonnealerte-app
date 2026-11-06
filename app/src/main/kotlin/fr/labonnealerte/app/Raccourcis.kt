package fr.labonnealerte.app

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri

/** Raccourcis d'application (appui long sur l'icone) : Mes alertes, Favoris, Recherche. */
object Raccourcis {
    private data class Entree(val id: String, val label: Int, val icon: Int)

    fun publish(context: Context) {
        val list = listOf(
            Entree(
                "mes-alertes",
                fr.labonnealerte.app.R.string.raccourci_mes_alertes,
                fr.labonnealerte.app.R.drawable.ic_raccourci_alertes
            ),
            Entree("favoris", fr.labonnealerte.app.R.string.raccourci_favoris, fr.labonnealerte.app.R.drawable.ic_raccourci_favoris),
            Entree("recherche", fr.labonnealerte.app.R.string.raccourci_recherche, fr.labonnealerte.app.R.drawable.ic_raccourci_recherche)
        ).map { r ->
            ShortcutInfoCompat.Builder(context, r.id)
                .setShortLabel(context.getString(r.label))
                .setIcon(IconCompat.createWithResource(context, r.icon))
                .setIntent(
                    Intent(Intent.ACTION_VIEW, "labonnealerte://raccourci/${r.id}".toUri())
                        .setClass(context, MainActivity::class.java)
                )
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, list) }
    }
}
