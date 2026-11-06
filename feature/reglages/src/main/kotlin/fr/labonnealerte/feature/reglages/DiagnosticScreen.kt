package fr.labonnealerte.feature.reglages

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.labonnealerte.core.data.ReceptionMode
import fr.labonnealerte.core.push.EffectiveMode
import fr.labonnealerte.core.ui.openExternal
import fr.labonnealerte.core.ui.theme.LbaThemeExtras
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticRoute(onBack: () -> Unit, showDebugTools: Boolean, viewModel: DiagnosticViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    // Retour des reglages Android : relire l'etat.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onPermissionAsked() }
    val testTitle = stringResource(R.string.diag_test_titre)
    val testText = stringResource(R.string.diag_test_texte)

    Scaffold(
        topBar = {
            fr.labonnealerte.core.ui.components.HeaderWithDivider {
                TopAppBar(
                    title = { Text(stringResource(R.string.diag_titre), maxLines = 2) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reglages_retour))
                        }
                    },
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0)
                )
            }
        }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(if (s.allGood) R.string.diag_tout_va_bien else R.string.diag_a_corriger),
                    style = MaterialTheme.typography.titleMedium
                )

                Check(
                    stringResource(R.string.diag_connexion),
                    s.signedIn,
                    stringResource(if (s.signedIn) R.string.diag_connexion_oui else R.string.diag_connexion_non)
                )

                Check(
                    stringResource(R.string.diag_permission),
                    s.permissionGranted && s.notificationsEnabled,
                    stringResource(
                        if (s.permissionGranted &&
                            s.notificationsEnabled
                        ) {
                            R.string.diag_permission_oui
                        } else {
                            R.string.diag_permission_non
                        }
                    )
                ) {
                    if (!s.permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Button(onClick = {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }) { Text(stringResource(R.string.diag_autoriser)) }
                    } else if (!s.notificationsEnabled) {
                        OutlinedButton(onClick = {
                            ctx.startActivity(appNotificationSettings(ctx.packageName))
                        }) { Text(stringResource(R.string.diag_ouvrir_reglages)) }
                    }
                }

                Check(
                    stringResource(R.string.diag_canaux),
                    s.blockedChannels.isEmpty(),
                    if (s.blockedChannels.isEmpty()) {
                        stringResource(
                            R.string.diag_canaux_ok
                        )
                    } else {
                        stringResource(R.string.diag_canaux_bloques, s.blockedChannels.joinToString(", "))
                    }
                ) {
                    OutlinedButton(onClick = {
                        ctx.startActivity(appNotificationSettings(ctx.packageName))
                    }) { Text(stringResource(R.string.diag_ouvrir_reglages)) }
                }

                Check(
                    stringResource(R.string.diag_batterie),
                    !s.batteryOptimized,
                    stringResource(if (s.batteryOptimized) R.string.diag_batterie_non else R.string.diag_batterie_oui),
                    warningOnly = true
                ) {
                    if (s.batteryOptimized) {
                        OutlinedButton(onClick = {
                            ctx.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) { Text(stringResource(R.string.diag_batterie_reglages)) }
                    }
                }

                // Mode de reception : explication en deux phrases si aucun distributeur, bascule automatique.
                val dist = s.reception.distributor ?: s.distributors.firstOrNull()
                val modeText = when (s.effective) {
                    EffectiveMode.UNIFIEDPUSH -> stringResource(
                        if (s.reception.pushRegisteredOnServer) R.string.diag_mode_push else R.string.diag_mode_push_attente,
                        appLabel(ctx, dist)
                    )
                    EffectiveMode.PERIODIQUE -> stringResource(R.string.diag_mode_periodique)
                    EffectiveMode.AUCUN -> stringResource(R.string.diag_connexion_non)
                }
                Check(stringResource(R.string.diag_reception), s.effective != EffectiveMode.AUCUN, modeText) {
                    if (s.distributors.isEmpty()) {
                        Text(stringResource(R.string.diag_sans_distributeur), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = {
                            fr.labonnealerte.core.push.NtfyInstall.open(ctx)
                        }) {
                            // « F-Droid » n'est nomme que s'il est installe (APP-06).
                            val fdroid = fr.labonnealerte.core.push.NtfyInstall.fdroidInstalled(ctx)
                            Text(stringResource(if (fdroid) R.string.diag_installer_ntfy_fdroid else R.string.diag_installer_ntfy))
                        }
                    } else if (s.mode == ReceptionMode.PERIODIQUE) {
                        OutlinedButton(onClick = {
                            viewModel.setMode(ReceptionMode.AUTO)
                        }) { Text(stringResource(R.string.diag_utiliser_push)) }
                    } else {
                        TextButton(onClick = {
                            viewModel.setMode(ReceptionMode.PERIODIQUE)
                        }) { Text(stringResource(R.string.diag_utiliser_periodique)) }
                    }
                }

                val never = stringResource(R.string.diag_jamais)
                fun fmt(t: Long) = if (t <=
                    0
                ) {
                    never
                } else {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t))
                }
                Text(
                    stringResource(R.string.diag_dernier_push, fmt(s.reception.lastPushAtMillis)),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(
                        R.string.diag_derniere_releve,
                        fmt(s.reception.lastPollAtMillis),
                        stringResource(if (s.reception.lastPollOk) R.string.diag_releve_ok else R.string.diag_releve_echec)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(stringResource(R.string.diag_derniere_notif, fmt(s.lastNotificationAt)), style = MaterialTheme.typography.bodySmall)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.sendTest(testTitle, testText) }) { Text(stringResource(R.string.diag_tester)) }
                    OutlinedButton(onClick = viewModel::checkNow, enabled = s.signedIn && !s.checking) {
                        Text(stringResource(R.string.diag_verifier))
                    }
                }

                FabricantGuide()

                if (showDebugTools) {
                    Text(
                        stringResource(R.string.diag_debug_titre),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics {
                            heading()
                        }
                    )
                    OutlinedButton(onClick = {
                        viewModel.simulate("vigilance-meteo", "⛈️ Demain : vigilance ORANGE dans Hautes-Alpes : orages")
                    }) {
                        Text(stringResource(R.string.diag_debug_simuler))
                    }
                    OutlinedButton(onClick = {
                        viewModel.simulate("vigilance-meteo", "🔴 Aujourd'hui : vigilance ROUGE dans Gard : pluie-inondation")
                    }) {
                        Text(stringResource(R.string.diag_debug_rouge))
                    }
                    OutlinedButton(onClick = { viewModel.simulate("vigilance-meteo", "fin", deactivate = true) }) {
                        Text(stringResource(R.string.diag_debug_calme))
                    }
                }
            }
        }
    }
}

private fun appNotificationSettings(pkg: String) =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun appLabel(ctx: android.content.Context, pkg: String?): String {
    if (pkg == null) return "?"
    return runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}

@Composable
private fun Check(title: String, ok: Boolean, detail: String, warningOnly: Boolean = false, actions: @Composable () -> Unit = {}) {
    val c = LbaThemeExtras.stateColors
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = stringResource(
                        when {
                            ok -> R.string.diag_etat_correct
                            warningOnly -> R.string.diag_etat_a_verifier
                            else -> R.string.diag_etat_probleme
                        }
                    ),
                    tint = if (ok) {
                        c.calm
                    } else if (warningOnly) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        c.incident
                    },
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            if (!ok) actions()
        }
    }
}

@Composable
private fun FabricantGuide() {
    val ctx = LocalContext.current
    val brand = Build.MANUFACTURER.orEmpty().lowercase()
    val tip = when {
        brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> R.string.diag_fabricant_xiaomi
        brand.contains("samsung") -> R.string.diag_fabricant_samsung
        brand.contains("huawei") || brand.contains("honor") -> R.string.diag_fabricant_huawei
        brand.contains("oneplus") || brand.contains("oppo") || brand.contains("realme") -> R.string.diag_fabricant_oneplus
        else -> R.string.diag_fabricant_autre
    }
    Text(
        stringResource(R.string.diag_fabricant_titre, Build.MANUFACTURER.orEmpty()),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() }
    )
    Text(stringResource(tip), style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = { ctx.openExternal("https://dontkillmyapp.com/" + brand.ifBlank { "" }) }) {
        Text(stringResource(R.string.diag_plus_infos))
    }
}
