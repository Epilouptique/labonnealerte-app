package fr.labonnealerte.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import fr.labonnealerte.core.model.DisplayLab

/** Options d'affichage (test) en cours, fournies par MainActivity (APP-11). Par defaut : rendu actuel. */
val LocalDisplayLab = staticCompositionLocalOf { DisplayLab() }
