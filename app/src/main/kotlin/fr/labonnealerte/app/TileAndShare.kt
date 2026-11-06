package fr.labonnealerte.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import fr.labonnealerte.core.push.PushRegistrar
import fr.labonnealerte.core.ui.openExternal
import fr.labonnealerte.core.ui.theme.LbaTheme
import javax.inject.Inject

/** Tuile des reglages rapides : « Verifier mes alertes » lance une releve immediate. */
@AndroidEntryPoint
class VerifierTileService : TileService() {
    @Inject lateinit var registrar: PushRegistrar

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tuile_label)
            updateTile()
        }
    }

    override fun onClick() {
        registrar.syncNow()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }
}

/**
 * Partage vers l'app (« Proposer une source ») : l'adresse partagee est copiee, puis le
 * formulaire du site s'ouvre (la page /proposer ne lit pas de parametre : HYPOTHESE a revoir
 * si le site l'ajoute un jour).
 */
@AndroidEntryPoint
class PartageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(2000)
        val url = URL.find(text)?.value
        setContent {
            LbaTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text(stringResource(R.string.partage_titre)) },
                    text = {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.partage_recu_texte))
                            url?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            url?.let {
                                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("url", it))
                                Toast.makeText(this, R.string.partage_copie, Toast.LENGTH_SHORT).show()
                            }
                            openExternal(PROPOSER)
                            finish()
                        }) { Text(stringResource(R.string.partage_bouton)) }
                    },
                    dismissButton = { TextButton(onClick = { finish() }) { Text(stringResource(fr.labonnealerte.core.ui.R.string.annuler)) } }
                )
            }
        }
    }

    companion object {
        const val PROPOSER = "https://labonnealerte.fr/proposer"
        private val URL = Regex("https://[^\\s]{3,1900}")
    }
}
