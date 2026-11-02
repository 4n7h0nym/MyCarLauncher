package com.openlauncher.app.radio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.Modifier
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import com.openlauncher.app.data.RadioManagerHelper
import com.openlauncher.app.ui.components.LocalAppLanguage
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Uses the existing shared repository, including home-widget consumers. */
class RadioDashboardViewModel(application: Application) : AndroidViewModel(application) {
    val state = RadioManagerHelper.tunerState
    val error = RadioManagerHelper.error
    val presets = RadioManagerHelper.presets
    val selectedBank = RadioManagerHelper.selectedBank
    fun preset(preset: RadioPreset) = RadioManagerHelper.tune(
        selectedBank.value, if (preset.fm) preset.frequencyKhz / 1000f else preset.frequencyKhz.toFloat())
    fun savePreset(preset: RadioPreset) = RadioManagerHelper.savePreset(preset)
    fun previous() = RadioManagerHelper.seek(false)
    fun next() = RadioManagerHelper.seek(true)
    fun stopSearch() = RadioManagerHelper.stopSeek()
    fun power() = RadioManagerHelper.power(!(state.value.openAccepted || state.value.powerOn == true))
    fun band(value: String) = RadioManagerHelper.band(value)
    fun mute() = RadioManagerHelper.mute(!state.value.muted)
}

/** Presets and options come from the existing repository / confirmed vendor callbacks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadioDashboardRoute(
    viewModel: RadioDashboardViewModel,
    onOpenRadioApp: () -> Unit,
    onAssignRadio: () -> Unit,
    modifier: Modifier = Modifier,
    bankLabel: String? = null,
    options: RadioOptions = RadioOptions(),
    onAfChange: ((Boolean) -> Unit)? = null,
    onTaChange: ((Boolean) -> Unit)? = null,
    onAutoStore: (() -> Unit)? = null,
    onAudioSettings: (() -> Unit)? = null,
    fmScale: RadioScale = RadioScale()
) {
    val current by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val bank by viewModel.selectedBank.collectAsStateWithLifecycle()
    val state = current.copy(error = error ?: current.error)
    val french = LocalAppLanguage.current.tag.startsWith("fr")
    fun label(en: String, fr: String) = if (french) fr else en
    RadioDashboard(
        tuner = state, presets = presets, modifier = modifier,
        bankLabel = bankLabel ?: if (state.band == null) "—" else bank,
        options = options,
        scale = if (state.band == 1) fmScale else null,
        actions = RadioActions(
            onPreset = viewModel::preset,
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            onPower = viewModel::power,
            onStopSearch = viewModel::stopSearch,
            onAfChange = onAfChange,
            onTaChange = onTaChange,
            onAutoStore = onAutoStore,
            onAudioSettings = onAudioSettings,
            onSavePreset = viewModel::savePreset
        ),
        extraControls = {
            listOf("FM1", "FM2", "AM").forEach { value ->
                FilterChip(selected = bank == value, enabled = state.connected && !state.scanning,
                    onClick = { viewModel.band(value) }, label = { Text(value) },
                    modifier = Modifier.heightIn(min = 64.dp).widthIn(min = 64.dp))
            }
            FilledTonalIconButton(onClick = viewModel::mute, enabled = state.connected,
                modifier = Modifier.size(64.dp)) {
                Icon(if (state.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    if (state.muted) label("Unmute", "Activer le son") else label("Mute", "Sourdine"))
            }
            var showMore by remember { mutableStateOf(false) }
            Box {
                FilledTonalIconButton(onClick = { showMore = true }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Default.MoreVert, label("More radio options", "Plus d’options radio"))
                }
                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                    DropdownMenuItem(text = { Text(label("OEM radio", "Radio d’origine")) },
                        modifier = Modifier.heightIn(min = 64.dp),
                        onClick = { showMore = false; onOpenRadioApp() })
                    DropdownMenuItem(text = { Text(label("Choose radio app", "Choisir l’application radio")) },
                        modifier = Modifier.heightIn(min = 64.dp),
                        onClick = { showMore = false; onAssignRadio() })
                }
            }
        }
    )
}
