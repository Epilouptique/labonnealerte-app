package fr.labonnealerte.feature.alerte

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.labonnealerte.core.model.Badge
import fr.labonnealerte.core.model.ParamDescriptor
import fr.labonnealerte.core.model.ParamOption
import fr.labonnealerte.core.model.ParamType
import fr.labonnealerte.core.model.Source
import fr.labonnealerte.core.model.SourceState
import fr.labonnealerte.core.model.SourceType
import fr.labonnealerte.core.model.Subscription
import fr.labonnealerte.core.model.SubscriptionInstance
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
class AbonnementScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val dept = ParamDescriptor(
        "departement", "Département", ParamType.ENUM,
        listOf(ParamOption("05", "Hautes-Alpes"), ParamOption("38", "Isère")),
        multiple = true, required = true, default = null, min = null, max = null, maxLength = null
    )
    private val source = Source(
        "vigilance-meteo", "Vigilance météo", "Le département de votre choix", null, null, Badge.OFFICIAL,
        SourceType.INTERNAL, null, listOf("vigilance-meteo"), listOf(dept), 0, null, null, SourceState.CALM, 10, null, 0
    )

    private fun capture(sub: SubState, name: String) {
        compose.setContent {
            LbaTheme(ThemeMode.LIGHT) {
                AbonnementBlock(source, sub, {}, {}, {}, {}, {}, { emptyList() })
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun deconnecte() = capture(SubState(signedIn = false), "abonnement_deconnecte")

    @Test
    fun connecteAvecZone() = capture(
        SubState(
            signedIn = true,
            profileDepartement = "38",
            subscription = Subscription(
                "vigilance-meteo",
                "Vigilance météo",
                "inactive",
                subscribed = true,
                muted = false,
                instances = listOf(SubscriptionInstance(mapOf("departement" to "05"), "Hautes-Alpes", "active", false)),
                tasks = emptyList()
            )
        ),
        "abonnement_connecte"
    )
}
