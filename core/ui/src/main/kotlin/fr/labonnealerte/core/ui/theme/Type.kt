package fr.labonnealerte.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.labonnealerte.core.ui.R

// Polices du site (public/css/fonts.css) : Baloo 2 pour les titres, Inter pour le texte.
// Fichiers variables officiels google/fonts, licence SIL OFL 1.1 (assets/licences/).

private fun inter(weight: FontWeight) = Font(
    R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

private fun baloo(weight: FontWeight) = Font(
    R.font.baloo2_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

val InterFamily = FontFamily(
    inter(FontWeight.Normal),
    inter(FontWeight.Medium),
    inter(FontWeight.SemiBold),
    inter(FontWeight.Bold)
)

val BalooFamily = FontFamily(
    baloo(FontWeight.SemiBold),
    baloo(FontWeight.Bold),
    baloo(FontWeight.ExtraBold)
)

private val base = Typography()

private fun TextStyle.inter() = copy(fontFamily = InterFamily)

private fun TextStyle.titre(w: FontWeight = FontWeight.Bold) = copy(fontFamily = BalooFamily, fontWeight = w)

val LbaTypography = Typography(
    displayLarge = base.displayLarge.titre(FontWeight.ExtraBold),
    displayMedium = base.displayMedium.titre(FontWeight.ExtraBold),
    displaySmall = base.displaySmall.titre(),
    headlineLarge = base.headlineLarge.titre(),
    headlineMedium = base.headlineMedium.titre(),
    headlineSmall = base.headlineSmall.titre(),
    titleLarge = base.titleLarge.titre(),
    titleMedium = base.titleMedium.inter().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.inter().copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.inter(),
    bodyMedium = base.bodyMedium.inter(),
    bodySmall = base.bodySmall.inter(),
    labelLarge = base.labelLarge.inter().copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.inter().copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.inter().copy(fontWeight = FontWeight.Medium, fontSize = 12.sp)
)
