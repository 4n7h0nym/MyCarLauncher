package com.openlauncher.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.viewmodel.RadioUiState
import com.openlauncher.app.viewmodel.RadioViewModel

/** Same reactive control surface used by the radio page and the home media source. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadioCard(state: RadioUiState, viewModel: RadioViewModel, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val nativeRadioIntent = androidx.compose.runtime.remember(context) { context.packageManager.getLaunchIntentForPackage("com.jancar.radio") }
    val internalTuner=!state.connected && state.hardware.contains("tuner interne FmService")
    val language = LocalAppLanguage.current
    fun label(en: String, fr: String) = if (language.tag.startsWith("fr")) fr else en
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
            if(!internalTuner) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("FM1", "FM2", "AM").forEach { band ->
                    FilterChip(selected = state.band == band, enabled = state.connected,
                        onClick = { viewModel.band(band) }, label = { Text(band, fontSize = 20.sp) }, modifier = Modifier.heightIn(min = 76.dp))
                }
                IconButton(onClick = { viewModel.mute(!state.muted) }, enabled = state.connected && state.powerOn, modifier = Modifier.size(76.dp)) {
                    Icon(if (state.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        label("Mute", "Sourdine"), Modifier.size(32.dp))
                }
                IconButton(onClick = { viewModel.power(!state.powerOn) }, enabled = state.connected, modifier = Modifier.size(76.dp)) {
                    Icon(Icons.Default.PowerSettingsNew, label("Radio power", "Alimentation radio"), Modifier.size(36.dp),
                        tint = if (state.powerOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(if(internalTuner) label("OEM FM tuner", "Tuner FM d’origine") else if (state.frequency > 0) {
                if (state.band == "AM") "%.0f kHz".format(java.util.Locale.US, state.frequency)
                else "%.1f MHz".format(java.util.Locale.US, state.frequency)
            } else "—", fontSize = 48.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            if (!internalTuner && state.rds.isNotBlank()) Text(state.rds, style = MaterialTheme.typography.titleLarge, maxLines = 2)
            Text(when { internalTuner -> label("Frequency and RDS managed by the OEM app", "Fréquence et RDS gérés par l’application OEM")
                !state.connected -> label("Radio unavailable", "Radio indisponible")
                !state.powerOn -> label("Radio off", "Radio éteinte")
                state.muted -> label("Muted", "En sourdine")
                state.stereo -> "Stéréo / Stereo"
                else -> "FM / AM" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if(!internalTuner) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { viewModel.seek(false) }, enabled = state.connected && state.powerOn,
                    modifier = Modifier.weight(1f).heightIn(min = 76.dp)) {
                    Icon(Icons.Default.SkipPrevious, label("Seek down", "Station précédente"), Modifier.size(36.dp))
                }
                Button(onClick = { viewModel.seek(true) }, enabled = state.connected && state.powerOn,
                    modifier = Modifier.weight(1f).heightIn(min = 76.dp)) {
                    Icon(Icons.Default.SkipNext, label("Seek up", "Station suivante"), Modifier.size(36.dp))
                }
            }
            if(state.hardware.isNotBlank()) Text(state.hardware,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
            if(internalTuner && nativeRadioIntent != null) {
                Text(label("Internal OEM tuner controls", "Commandes du tuner interne OEM"),color=MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick={com.openlauncher.app.data.RadioManagerHelper.nativeSeek(false)},modifier=Modifier.weight(1f).heightIn(min=76.dp)) { Text(label("Seek down", "Chercher −")) }
                    FilledTonalButton(onClick={com.openlauncher.app.data.RadioManagerHelper.nativeSeek(true)},modifier=Modifier.weight(1f).heightIn(min=76.dp)) { Text(label("Seek up", "Chercher +")) }
                }
                TextButton(onClick={com.openlauncher.app.data.RadioManagerHelper.nativeStop()},modifier=Modifier.heightIn(min=48.dp)) { Text(label("Turn off OEM tuner", "Éteindre le tuner OEM")) }
            }
            if(state.connected && !internalTuner) TextButton(onClick={com.openlauncher.app.data.RadioManagerHelper.stopSeek()},modifier=Modifier.heightIn(min=48.dp)) { Text(label("Stop station search", "Arrêter la recherche")) }
            if(!internalTuner) state.signalStrength?.let { Text(label("Tuner signal", "Signal du tuner")+" : $it",color=MaterialTheme.colorScheme.onSurfaceVariant) }
            if(nativeRadioIntent != null) OutlinedButton(onClick = {
                runCatching { context.startActivity(nativeRadioIntent) }.onFailure {
                    android.widget.Toast.makeText(context,label("Unable to open OEM radio", "Impossible d’ouvrir la radio d’origine"),android.widget.Toast.LENGTH_LONG).show()
                }
            },modifier=Modifier.heightIn(min=64.dp)) { Text(label("Open OEM antenna radio", "Ouvrir la radio d’antenne d’origine")) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
