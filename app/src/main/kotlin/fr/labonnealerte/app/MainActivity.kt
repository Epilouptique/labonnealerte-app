package fr.labonnealerte.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.labonnealerte.app.ui.AppViewModel
import fr.labonnealerte.app.ui.LbaApp
import fr.labonnealerte.core.ui.theme.LbaTheme
import fr.labonnealerte.core.ui.theme.ThemeMode

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // L'ecran de demarrage reste le temps de lire les reglages (theme) : pas de flash de couleur.
        splash.setKeepOnScreenCondition { appViewModel.settings.value == null }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by appViewModel.settings.collectAsStateWithLifecycle()
            val unread by appViewModel.unread.collectAsStateWithLifecycle()
            val accountBadge by appViewModel.accountBadge.collectAsStateWithLifecycle()
            val s = settings
            LbaTheme(
                themeMode = when (s?.theme) {
                    "light" -> ThemeMode.LIGHT
                    "dark" -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                },
                dynamicColor = s?.dynamicColor == true
            ) {
                // Taille du texte choisie dans l app, en plus de celle du systeme.
                val d = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides
                        androidx.compose.ui.unit.Density(d.density, d.fontScale * (s?.fontScale ?: 1f)),
                    fr.labonnealerte.core.ui.LocalReduceMotionPref provides (s?.reduceMotion == true),
                    fr.labonnealerte.core.ui.LocalDisplayLab provides (s?.lab ?: fr.labonnealerte.core.model.DisplayLab())
                ) {
                    if (s != null && !s.tutorialSeen) {
                        fr.labonnealerte.feature.onboarding.TutorialRoute()
                    } else if (s != null && !s.onboardingDone) {
                        fr.labonnealerte.feature.onboarding.OnboardingRoute(onDone = {})
                    } else {
                        LbaApp(windowSizeClass = calculateWindowSizeClass(this), unread = unread, accountBadge = accountBadge)
                    }
                }
            }
        }
    }
}
