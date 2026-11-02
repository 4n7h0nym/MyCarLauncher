package com.openlauncher.app.radio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import com.openlauncher.app.ui.components.LocalizedText as Text
import com.openlauncher.app.ui.components.AppLocalization
import com.openlauncher.app.ui.components.LocalAppLanguage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.radio.RadioUiState
import java.util.Locale

@Immutable
data class RadioPreset(
    val id: String,
    val number: Int,
    val frequencyKhz: Int,
    val fm: Boolean = true,
    val name: String = ""
)

@Immutable
data class RadioScale(val minKhz: Int = 87500, val maxKhz: Int = 107900, val minorTickKhz: Int = 200) {
    init {
        require(minKhz > 0 && maxKhz > minKhz && minorTickKhz > 0)
        require((maxKhz - minKhz) / minorTickKhz <= 500) { "Too many ruler ticks" }
    }
}

@Immutable
data class RadioOptions(
    val af: Boolean? = null,
    val tp: Boolean? = null,
    val ta: Boolean? = null,
    val rdsAvailable: Boolean? = null
)

data class RadioActions(
    val onPreset: (RadioPreset) -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onPower: () -> Unit,
    val onStopSearch: () -> Unit,
    val onAfChange: ((Boolean) -> Unit)? = null,
    val onTaChange: ((Boolean) -> Unit)? = null,
    val onAutoStore: (() -> Unit)? = null,
    val onAudioSettings: (() -> Unit)? = null,
    val onSavePreset: ((RadioPreset) -> Unit)? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadioDashboard(
    tuner: RadioUiState,
    presets: List<RadioPreset>,
    actions: RadioActions,
    modifier: Modifier = Modifier,
    bankLabel: String = when (tuner.band) { 0 -> "AM"; 1 -> "FM-1"; else -> "—" },
    options: RadioOptions = RadioOptions(),
    scale: RadioScale? = if (tuner.band == 1) RadioScale() else null,
    extraControls: @Composable () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val language = LocalAppLanguage.current
    val canControl = tuner.connected
    Surface(modifier = modifier.fillMaxSize(), color = colors.background) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = RoundedCornerShape(28.dp),
            color = colors.surface
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
                val compact = maxWidth < 760.dp || maxHeight < 620.dp
                val presetWidth = (maxWidth * 0.24f).coerceIn(148.dp, 260.dp)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PresetList(
                        presets, tuner, canControl && !tuner.scanning, actions.onPreset, actions.onSavePreset,
                        Modifier.width(presetWidth).fillMaxHeight()
                    )
                    Surface(
                        Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(28.dp), color = colors.surfaceVariant
                    ) {
                        Box(Modifier.fillMaxSize().padding(if (compact) 12.dp else 20.dp), contentAlignment = Alignment.Center) {
                            Column(
                                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Top row: Band, RDS, ST, and Power (ON/OFF) button
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Badge(bankLabel, active = true)
                                    Badge("RDS", if (canControl) options.rdsAvailable ?:
                                        tuner.rds.takeIf { it.isNotBlank() }?.let { true } else null)
                                    Badge("ST", if (canControl) tuner.stereo else null)
                                    Spacer(Modifier.weight(1f))
                                    IconButton(
                                        onClick = actions.onPower,
                                        enabled = canControl,
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.PowerSettingsNew,
                                            if (tuner.openAccepted || tuner.powerOn == true) AppLocalization.translate("Éteindre la radio", language) else AppLocalization.translate("Allumer la radio", language),
                                            Modifier.size(30.dp),
                                            tint = if (tuner.openAccepted || tuner.powerOn == true) colors.primary else colors.onSurfaceVariant
                                        )
                                    }
                                }

                                FrequencyPanel(tuner, actions, compact, canControl && !tuner.scanning)
                                if (scale != null) FrequencyRuler(
                                    tuner.frequencyKhz, scale,
                                    Modifier.fillMaxWidth().height(if (compact) 76.dp else 90.dp)
                                )
                                if (!tuner.connected || tuner.scanning || tuner.powerOn == false) {
                                    Text(when {
                                        !tuner.connected -> "Radio indisponible"
                                        tuner.scanning -> "Recherche en cours…"
                                        else -> "Radio éteinte"
                                    }, color = colors.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                                }
                                FlowRow(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OptionButton("AF", if (canControl) options.af else null, canControl, actions.onAfChange)
                                    Surface(
                                        Modifier.heightIn(min = 64.dp).widthIn(min = 64.dp)
                                            .semantics {
                                                contentDescription = AppLocalization.translate("Programme trafic", language)
                                                stateDescription = AppLocalization.translate(status(if (canControl) options.tp else null), language)
                                            },
                                        shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHighest
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                "TP", fontWeight = FontWeight.Bold,
                                                color = if (canControl && options.tp == true) colors.primary else colors.onSurfaceVariant
                                            )
                                        }
                                    }
                                    OptionButton("TA", if (canControl) options.ta else null, canControl, actions.onTaChange)
                                    if (tuner.scanning) {
                                        ActionButton("Arrêter", Icons.Default.Stop, canControl, actions.onStopSearch)
                                    } else {
                                        ActionButton(
                                            "Auto", Icons.Default.Search,
                                            canControl && actions.onAutoStore != null, { actions.onAutoStore?.invoke() },
                                            description = "Rechercher et mémoriser automatiquement les stations"
                                        )
                                    }
                                    ActionButton(
                                        "Audio", Icons.Default.Tune,
                                        actions.onAudioSettings != null, { actions.onAudioSettings?.invoke() }
                                    )
                                    extraControls()
                                }
                                tuner.error?.let {
                                    Text(it, color = colors.error, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetList(
    presets: List<RadioPreset>, tuner: RadioUiState, enabled: Boolean,
    onSelect: (RadioPreset) -> Unit, onSave: ((RadioPreset) -> Unit)?, modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    val language = LocalAppLanguage.current
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerLow) {
        if (presets.isEmpty()) {
            Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                Text("Aucune présélection", color = colors.onSurfaceVariant)
            }
        } else LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets, key = { it.id }) { preset ->
                val selected = tuner.connected && tuner.frequencyKhz == preset.frequencyKhz &&
                    tuner.band == if (preset.fm) 1 else 0
                val foreground = if (selected) colors.onPrimaryContainer else colors.onSurface
                Surface(shape = RoundedCornerShape(20.dp),
                    color = if (selected) colors.primaryContainer else colors.surfaceContainerLow) {
                    BoxWithConstraints(Modifier.fillMaxWidth()
                        .combinedClickable(enabled = enabled, role = Role.RadioButton,
                            onClick = { onSelect(preset) },
                            onLongClickLabel = AppLocalization.translate("Mémoriser la fréquence actuelle", language),
                            onLongClick = onSave?.let { save -> { save(preset) } })
                        .semantics { this.selected = selected }
                        .heightIn(min = 76.dp).padding(horizontal = 12.dp, vertical = 12.dp)) {
                        if (maxWidth < 172.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(preset.number.toString(), color = if (selected) colors.primary else colors.onSurfaceVariant,
                                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                PresetText(preset, foreground, compact = true)
                            }
                        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(preset.number.toString(), color = if (selected) colors.primary else colors.onSurfaceVariant,
                                fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Column(Modifier.weight(1f)) { PresetText(preset, foreground, compact = false) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetText(preset: RadioPreset, foreground: androidx.compose.ui.graphics.Color, compact: Boolean) {
    Text(formatFrequency(preset.frequencyKhz, preset.fm, Locale.forLanguageTag(LocalAppLanguage.current.tag)), color = foreground,
        fontSize = if (compact) 22.sp else 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    if (preset.name.isNotBlank()) Text(preset.name, color = foreground,
        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun FrequencyPanel(tuner: RadioUiState, actions: RadioActions, compact: Boolean, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    val language = LocalAppLanguage.current
    val text = remember(tuner.frequencyKhz, tuner.band, language) {
        tuner.frequencyKhz?.let { formatFrequency(it, tuner.band != 0, Locale.forLanguageTag(language.tag)) } ?: "—"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = actions.onPrevious, enabled = enabled,
            modifier = Modifier.size(if (compact) 64.dp else 80.dp)) {
            Icon(Icons.AutoMirrored.Filled.NavigateBefore, AppLocalization.translate("Rechercher la station précédente", language), Modifier.size(48.dp))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (tuner.rds.isNotBlank()) Text(tuner.rds, color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val fontSize = when {
                    maxWidth < 220.dp -> 40.sp
                    compact || maxWidth < 380.dp -> 60.sp
                    else -> 96.sp
                }
                Text(text, color = colors.onSurface, fontSize = fontSize,
                    fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                    overflow = TextOverflow.Ellipsis)
            }
            Text(when (tuner.band) { 0 -> "kHz"; 1 -> "MHz"; else -> "" }, color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge)
        }
        FilledTonalIconButton(onClick = actions.onNext, enabled = enabled,
            modifier = Modifier.size(if (compact) 64.dp else 80.dp)) {
            Icon(Icons.AutoMirrored.Filled.NavigateNext, AppLocalization.translate("Rechercher la station suivante", language), Modifier.size(48.dp))
        }
    }
}

/** Visual ruler only: tuning remains on large explicit controls, not tiny tick marks. */
@Composable
fun FrequencyRuler(frequencyKhz: Int?, scale: RadioScale, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val language = LocalAppLanguage.current
    val minLabel = remember(scale, language) { String.format(Locale.forLanguageTag(language.tag), "%.1f", scale.minKhz / 1000.0) }
    val maxLabel = remember(scale, language) { String.format(Locale.forLanguageTag(language.tag), "%.1f", scale.maxKhz / 1000.0) }
    val inRange = frequencyKhz != null && frequencyKhz in scale.minKhz..scale.maxKhz
    Column(modifier.semantics(mergeDescendants = true) {
        contentDescription = if (language.tag.startsWith("fr")) "Échelle FM de $minLabel à $maxLabel MHz" else "FM scale from $minLabel to $maxLabel MHz"
        stateDescription = AppLocalization.translate(when { frequencyKhz == null -> "Fréquence inconnue"
            !inRange -> "Fréquence hors de l’échelle affichée"
            else -> "${formatFrequency(frequencyKhz, true, Locale.forLanguageTag(language.tag))} MHz" }, language)
    }) {
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp)) {
            val tickColor = colors.onSurfaceVariant.copy(alpha = 0.65f)
            val span = scale.maxKhz - scale.minKhz
            val ticks = span / scale.minorTickKhz
            for (i in 0..ticks) {
                val x = size.width * (i * scale.minorTickKhz).toFloat() / span
                val height = size.height * when { i % 10 == 0 -> 0.75f; i % 5 == 0 -> 0.55f; else -> 0.34f }
                drawLine(tickColor, Offset(x, size.height), Offset(x, size.height - height),
                    strokeWidth = if (i % 10 == 0) 2.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round)
            }
            if (inRange) {
                val fraction = (frequencyKhz!! - scale.minKhz).toFloat() / (scale.maxKhz - scale.minKhz)
                val x = fraction * size.width
                drawLine(colors.primary, Offset(x, 7.dp.toPx()), Offset(x, size.height),
                    strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                val marker = Path().apply {
                    moveTo(x, 10.dp.toPx()); lineTo(x - 6.dp.toPx(), 0f)
                    lineTo(x + 6.dp.toPx(), 0f); close()
                }
                drawPath(marker, colors.primary)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(minLabel, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            Text(maxLabel, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Badge(label: String, active: Boolean?) {
    val colors = MaterialTheme.colorScheme
    val language = LocalAppLanguage.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = when (active) {
            true -> colors.primaryContainer
            false -> colors.surfaceVariant
            null -> colors.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier.semantics {
            contentDescription = AppLocalization.translate(label, language)
            stateDescription = AppLocalization.translate(status(active), language)
        }
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, fontWeight = FontWeight.Bold,
                color = when (active) {
                    true -> colors.onPrimaryContainer
                    false -> colors.onSurfaceVariant
                    null -> colors.onSurfaceVariant.copy(alpha = 0.5f)
                })
        }
    }
}

@Composable
private fun OptionButton(label: String, checked: Boolean?, connected: Boolean, onChange: ((Boolean) -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val language = LocalAppLanguage.current
    val active = checked == true
    Surface(
        onClick = { onChange?.invoke(!active) },
        enabled = connected && onChange != null,
        shape = RoundedCornerShape(20.dp),
        color = if (active) colors.primaryContainer else colors.surfaceVariant,
        modifier = Modifier.heightIn(min = 64.dp).widthIn(min = 64.dp).semantics {
            contentDescription = AppLocalization.translate(label, language)
            stateDescription = AppLocalization.translate(status(checked), language)
            this.selected = active
        }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, fontWeight = FontWeight.Bold,
                color = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit, description: String = label) {
    val language = LocalAppLanguage.current
    FilledTonalButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.heightIn(min = 64.dp).semantics { contentDescription = AppLocalization.translate(description, language) },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 18.sp)
    }
}

private fun status(value: Boolean?) = when (value) { true -> "Actif"; false -> "Inactif"; null -> "État indisponible" }
private fun formatFrequency(khz: Int, fm: Boolean, locale: Locale = Locale.CANADA_FRENCH): String =
    if (fm) String.format(locale, "%.2f", khz / 1000.0) else khz.toString()
