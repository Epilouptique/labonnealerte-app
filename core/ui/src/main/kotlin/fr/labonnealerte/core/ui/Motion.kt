package fr.labonnealerte.core.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Reglage de l'app « Reduire les animations » (fourni par MainActivity). */
val LocalReduceMotionPref = staticCompositionLocalOf { false }

private fun systemAnimationsOff(ctx: Context) = Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Vrai si l'utilisateur a coupe les animations (systeme) ou demande de les reduire (app). */
@Composable
fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    val pref = LocalReduceMotionPref.current
    var system by remember { mutableStateOf(systemAnimationsOff(ctx)) }
    // Le reglage systeme peut changer pendant que l'app est en arriere-plan : relu au retour.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { system = systemAnimationsOff(ctx) }
    return pref || system
}
