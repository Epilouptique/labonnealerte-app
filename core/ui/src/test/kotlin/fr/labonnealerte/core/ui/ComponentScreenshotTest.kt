package fr.labonnealerte.core.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.labonnealerte.core.ui.components.ComponentGallery
import fr.labonnealerte.core.ui.components.EmptyState
import fr.labonnealerte.core.ui.theme.LbaTheme
import fr.labonnealerte.core.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class ComponentScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun galerieClair() {
        compose.setContent { LbaTheme(ThemeMode.LIGHT) { ComponentGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/galerie_clair.png")
    }

    @Test
    fun galerieSombre() {
        compose.setContent { LbaTheme(ThemeMode.DARK) { ComponentGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/galerie_sombre.png")
    }

    /** Police systeme a 200 % : rien ne doit etre coupe ni se chevaucher. */
    @Test
    @Config(fontScale = 2.0f)
    fun galeriePolice200() {
        compose.setContent { LbaTheme(ThemeMode.LIGHT) { ComponentGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/galerie_police_200.png")
    }

    @Test
    fun etatVide() {
        compose.setContent {
            LbaTheme(ThemeMode.LIGHT) { EmptyState("Aucune alerte suivie", "Parcourez le kiosque pour en choisir.") }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/etat_vide.png")
    }
}
