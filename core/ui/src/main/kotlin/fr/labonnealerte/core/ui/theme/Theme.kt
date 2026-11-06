package fr.labonnealerte.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Couleurs reprises de public/css/tokens.css du site.
 * Le violet d'accent #a567e3 (--amber, nom historique) n'atteint que 3,6:1 sur fond clair :
 * il sert aux aplats ; le texte violet utilise #7d3fbf (--amber-ink), comme sur le site.
 */
object LbaPalette {
    val VioletAccent = Color(0xFFA567E3)
    val VioletInk = Color(0xFF7D3FBF)
    val VioletSoft = Color(0xFFEADDF8)
    val VioletAccentDark = Color(0xFFBD8EF0)
    val VioletSoftDark = Color(0xFF332A44)

    val BgLight = Color(0xFFFDFAFF)
    val SurfaceLight = Color(0xFFF4EDFB)
    val InkLight = Color(0xFF0F1419)
    val MutedLight = Color(0xFF6B6459)

    val BgDark = Color(0xFF141218)
    val SurfaceDark = Color(0xFF2E3440)
    val InkDark = Color(0xFFF2EFE9)
    val MutedDark = Color(0xFF9AA3AD)

    val Green = Color(0xFF16A34A)
    val GreenSoft = Color(0xFFE9F7EE)
    val GreenDark = Color(0xFF22C55E)
    val GreenSoftDark = Color(0xFF12281A)
    val Danger = Color(0xFFDC2626)
    val DangerSoft = Color(0xFFFDECEC)
    val DangerDark = Color(0xFFEF4444)
    val DangerSoftDark = Color(0xFF3A1C1C)
}

/** Couleurs semantiques des etats d'alerte (absentes de Material). */
@Immutable
data class LbaStateColors(
    val calm: Color,
    val calmContainer: Color,
    val active: Color,
    val activeContainer: Color,
    val incident: Color,
    val incidentContainer: Color,
    val muted: Color
)

private val LightState = LbaStateColors(
    calm = Color(0xFF15803D), // vert assombri : 4,9:1 sur fond clair (texte)
    calmContainer = LbaPalette.GreenSoft,
    active = LbaPalette.VioletInk,
    activeContainer = LbaPalette.VioletSoft,
    incident = Color(0xFFB91C1C),
    incidentContainer = LbaPalette.DangerSoft,
    muted = LbaPalette.MutedLight
)

private val DarkState = LbaStateColors(
    calm = LbaPalette.GreenDark,
    calmContainer = LbaPalette.GreenSoftDark,
    active = LbaPalette.VioletAccentDark,
    activeContainer = LbaPalette.VioletSoftDark,
    incident = Color(0xFFF87171), // 5,6:1 sur son conteneur sombre (DangerDark : 4,1)
    incidentContainer = LbaPalette.DangerSoftDark,
    muted = LbaPalette.MutedDark
)

val LocalLbaStateColors = staticCompositionLocalOf { LightState }

private val LightColors = lightColorScheme(
    primary = LbaPalette.VioletInk,
    onPrimary = Color.White,
    primaryContainer = LbaPalette.VioletSoft,
    onPrimaryContainer = Color(0xFF3B1466),
    secondary = LbaPalette.VioletAccent,
    onSecondary = Color.White,
    tertiary = LbaPalette.Green,
    background = LbaPalette.BgLight,
    onBackground = LbaPalette.InkLight,
    surface = LbaPalette.BgLight,
    onSurface = LbaPalette.InkLight,
    surfaceVariant = LbaPalette.SurfaceLight,
    onSurfaceVariant = LbaPalette.MutedLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF5FE),
    surfaceContainer = LbaPalette.SurfaceLight,
    surfaceContainerHigh = Color(0xFFEFE5F8),
    surfaceContainerHighest = Color(0xFFE9DDF4),
    error = LbaPalette.Danger,
    errorContainer = LbaPalette.DangerSoft,
    outline = Color(0xFF8B8496),
    outlineVariant = Color(0xFFD9CFE6)
)

private val DarkColors = darkColorScheme(
    primary = LbaPalette.VioletAccentDark,
    onPrimary = Color(0xFF2A0F4A),
    primaryContainer = LbaPalette.VioletSoftDark,
    onPrimaryContainer = Color(0xFFEBDCFF),
    secondary = LbaPalette.VioletAccentDark,
    tertiary = LbaPalette.GreenDark,
    background = LbaPalette.BgDark,
    onBackground = LbaPalette.InkDark,
    surface = LbaPalette.BgDark,
    onSurface = LbaPalette.InkDark,
    surfaceVariant = LbaPalette.SurfaceDark,
    onSurfaceVariant = LbaPalette.MutedDark,
    surfaceContainerLowest = Color(0xFF1F1C25),
    surfaceContainerLow = Color(0xFF1B1820),
    surfaceContainer = Color(0xFF221F28),
    surfaceContainerHigh = Color(0xFF2B2732),
    surfaceContainerHighest = Color(0xFF35303D),
    error = LbaPalette.DangerDark,
    errorContainer = LbaPalette.DangerSoftDark,
    outline = Color(0xFF8E8899),
    outlineVariant = Color(0xFF3F3A48)
)

/** Cartes arrondies comme sur le site (24 px pour les grandes cartes). */
val LbaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun LbaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalLbaStateColors provides if (dark) DarkState else LightState) {
        MaterialTheme(
            colorScheme = colors,
            typography = LbaTypography,
            shapes = LbaShapes,
            content = content
        )
    }
}

object LbaThemeExtras {
    val stateColors: LbaStateColors
        @Composable get() = LocalLbaStateColors.current
}
