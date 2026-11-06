package fr.labonnealerte.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColor
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fr.labonnealerte.app.MainActivity
import fr.labonnealerte.app.R
import fr.labonnealerte.core.data.AccountRepository
import fr.labonnealerte.core.database.NotificationDao
import fr.labonnealerte.core.ui.theme.LbaPalette

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun notificationDao(): NotificationDao
    fun account(): AccountRepository
}

// Couleurs jour / nuit (theme sombre du systeme) : jetons de core:ui (LbaPalette) en mode sombre.
private val VIOLET = DayNightColor(day = Color(0xFF7D3FBF), night = LbaPalette.VioletAccentDark)
private val INK = DayNightColor(day = Color(0xFF0F1419), night = LbaPalette.InkDark)
private val MUTED = DayNightColor(day = Color(0xFF6B6459), night = LbaPalette.MutedDark)
private val BG = DayNightColor(day = Color(0xFFFDFAFF), night = LbaPalette.BgDark)

private fun openApp(context: Context, uri: String) =
    actionStartActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).setClass(context, MainActivity::class.java))

@Composable
private fun Card(context: Context, uri: String, content: @Composable () -> Unit) {
    Column(
        GlanceModifier.fillMaxSize().background(BG).cornerRadius(20.dp).padding(14.dp).clickable(openApp(context, uri))
    ) { content() }
}

/** Widget « Alertes actives » : nombre d'alertes suivies en cours et leurs noms. */
class AlertesActivesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        ep.account().loadCached()
        val acc = ep.account().account.value
        val subs = acc?.subscriptions.orEmpty().filter { it.subscribed }
        val active = subs.filter { s -> s.state == "active" || s.instances.any { it.state == "active" } }
        provideContent {
            GlanceTheme {
                Card(context, "labonnealerte://raccourci/mes-alertes") {
                    Text(context.getString(R.string.widget_actives_titre), style = TextStyle(color = VIOLET, fontWeight = FontWeight.Bold, fontSize = 14.sp))
                    Spacer(GlanceModifier.height(4.dp))
                    when {
                        acc == null -> Text(
                            context.getString(R.string.widget_connexion),
                            style = TextStyle(color = MUTED, fontSize = 13.sp)
                        )
                        active.isEmpty() -> Text(
                            context.resources.getQuantityString(R.plurals.widget_calme, subs.size, subs.size),
                            style = TextStyle(color = INK, fontSize = 15.sp)
                        )
                        else -> {
                            Text(
                                context.resources.getQuantityString(R.plurals.widget_en_cours, active.size, active.size),
                                style = TextStyle(color = INK, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            )
                            active.take(4).forEach { Text("• " + it.name, style = TextStyle(color = INK, fontSize = 13.sp), maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}

/** Widget « Derniere alerte » : la notification la plus recente recue sur ce telephone. */
class DerniereAlerteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val last = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).notificationDao().latest()
            ?.takeIf { it.kind == "activated" || it.kind == "echeance" }
        provideContent {
            GlanceTheme {
                Card(context, last?.sourceId?.let { "labonnealerte://alert/$it" } ?: "labonnealerte://raccourci/mes-alertes") {
                    Text(context.getString(R.string.widget_derniere_titre), style = TextStyle(color = VIOLET, fontWeight = FontWeight.Bold, fontSize = 14.sp))
                    Spacer(GlanceModifier.height(4.dp))
                    if (last == null) {
                        Text(context.getString(R.string.widget_rien), style = TextStyle(color = MUTED, fontSize = 13.sp))
                    } else {
                        Text(last.title, style = TextStyle(color = INK, fontWeight = FontWeight.Bold, fontSize = 14.sp), maxLines = 1)
                        Text(last.text, style = TextStyle(color = INK, fontSize = 13.sp), maxLines = 3)
                        Text(
                            fr.labonnealerte.core.ui.Dates.full(java.time.Instant.ofEpochMilli(last.receivedAtMillis)),
                            style = TextStyle(color = MUTED, fontSize = 11.sp)
                        )
                    }
                }
            }
        }
    }
}

class AlertesActivesReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AlertesActivesWidget()
}

class DerniereAlerteReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DerniereAlerteWidget()
}

/** Rafraichit les widgets (appele quand une notification arrive ou que le compte change). */
object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        runCatching { AlertesActivesWidget().updateAll(context) }
        runCatching { DerniereAlerteWidget().updateAll(context) }
    }
}
