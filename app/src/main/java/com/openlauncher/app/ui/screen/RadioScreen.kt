package com.openlauncher.app.ui.screen

import com.openlauncher.app.ui.components.LocalizedText as LocalizedLabel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.ui.theme.LocalDayMode
import com.openlauncher.app.viewmodel.LauncherViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LegacyRadioScreen(
    hardwareRadio: LauncherViewModel.HardwareRadioState?,
    accent: Color,
    onLaunchHardwareRadio: () -> Unit,
    onStopHardwareRadio: () -> Unit,
    onRadioSeekUp: () -> Unit,
    onRadioSeekDown: () -> Unit,
    onRadioCycleFm: () -> Unit,
    onRadioSwitchAm: () -> Unit,
    onRadioTune: (band: String, freq: Float) -> Unit,
    onAssignRadio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg        = MaterialTheme.colorScheme.surface
    val cardBorder    = MaterialTheme.colorScheme.outline
    val textColor     = MaterialTheme.colorScheme.onBackground
    val mutedColor    = MaterialTheme.colorScheme.onSurfaceVariant

    val activeRadio = hardwareRadio ?: LauncherViewModel.HardwareRadioState(
        band = "FM1",
        freq = "94.3",
        stationName = "FM Radio",
        canTune = true,
        isPowerOn = true,
        supportedBands = listOf("FM1", "FM2", "FM3", "AM")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Radio Tuner Display Card
        val powerOn = activeRadio.isPowerOn
        val displayBand = activeRadio.band.uppercase()
        val freqClean   = activeRadio.freq.replace(Regex("[^0-9.]"), "")
        val displayFreq = freqClean.ifEmpty { activeRadio.freq }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(cardBg)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top row: Band selector & Power
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeRadio.canTune) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeRadio.supportedBands.forEach { b ->
                            val active = displayBand == b ||
                                (displayBand == "FM1" && b == "FM1") ||
                                (displayBand.startsWith("DAB") && b == "DAB")
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) accent else cardBorder.copy(alpha = 0.3f))
                                    .border(1.dp, if (active) accent else cardBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (b == "AM") onRadioSwitchAm()
                                        else onRadioCycleFm()
                                    }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LocalizedLabel(
                                    b,
                                    color = if (active) Color.Black else textColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent)
                            .border(1.dp, accent, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LocalizedLabel(
                            displayBand,
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Power Button
                IconButton(
                    onClick = {
                        if (powerOn) onStopHardwareRadio()
                        else onLaunchHardwareRadio()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (powerOn) accent.copy(alpha = 0.2f) else cardBorder.copy(alpha = 0.3f))
                        .border(2.dp, if (powerOn) accent else cardBorder, CircleShape)
                ) {
                    Icon(
                        Icons.Default.PowerSettingsNew,
                        null,
                        tint = if (powerOn) accent else mutedColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Center: Big Frequency / Channel & Station Name
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
            ) {
                LocalizedLabel(
                    text = if (!powerOn) "RADIO OFF" else activeRadio.stationName ?: "LIVE RECEPTION",
                    color = if (powerOn) accent else mutedColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    LocalizedLabel(
                        text = if (powerOn) displayFreq else "— —",
                        color = textColor,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-2).sp
                    )
                    Spacer(Modifier.width(12.dp))
                    LocalizedLabel(
                        text = if (displayBand == "AM") "kHz" else "MHz",
                        color = mutedColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }
            }

            // Bottom row: Seek and Presets
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Seek Controls (Longer & Wider buttons with borders)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onRadioSeekDown,
                        enabled = powerOn,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = cardBorder.copy(alpha = 0.2f)),
                        modifier = Modifier.weight(1f).height(60.dp).border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                    ) {
                        Icon(Icons.Default.SkipPrevious, null, tint = textColor, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        LocalizedLabel("PRÉCÉDENT", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onRadioSeekUp,
                        enabled = powerOn,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        modifier = Modifier.weight(1f).height(60.dp).border(1.dp, accent, RoundedCornerShape(20.dp))
                    ) {
                        LocalizedLabel("SUIVANT", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Default.SkipNext, null, tint = Color.Black, modifier = Modifier.size(28.dp))
                    }
                }

                // Presets Grid (3x2 format)
                if (activeRadio.canTune) {
                    var fmPresets by rememberSaveable { mutableStateOf(listOf(88.5f, 91.1f, 95.7f, 101.1f, 103.5f, 107.9f)) }
                    var amPresets by rememberSaveable { mutableStateOf(listOf(540f, 680f, 820f, 1040f, 1260f, 1420f)) }
                    val isFm           = displayBand != "AM"
                    val currentPresets = if (isFm) fmPresets else amPresets
                    val freqFloat      = freqClean.toFloatOrNull()
                    val chunks         = currentPresets.chunked(3)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        chunks.forEachIndexed { rowIndex, rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEachIndexed { colIndex, pf ->
                                    val pIdx = rowIndex * 3 + colIndex
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(cardBorder.copy(alpha = 0.2f))
                                            .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                                            .combinedClickable(
                                                enabled = powerOn,
                                                onClick = { onRadioTune(displayBand, pf) },
                                                onLongClick = {
                                                    freqFloat?.let { f ->
                                                        if (isFm) fmPresets = fmPresets.toMutableList().also { it[pIdx] = f }
                                                        else      amPresets = amPresets.toMutableList().also { it[pIdx] = f }
                                                    }
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LocalizedLabel(
                                            text = if (displayBand == "AM") "%.0f".format(java.util.Locale.US, pf) else "%.1f".format(java.util.Locale.US, pf),
                                            color = if (powerOn) textColor else mutedColor,
                                            fontSize = 14.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                repeat(3 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** IRadio page, with the original MCU/MediaSession page kept for other platforms. */
@Composable
fun RadioScreen(
    hardwareRadio: LauncherViewModel.HardwareRadioState?, accent: Color,
    onLaunchHardwareRadio: () -> Unit, onStopHardwareRadio: () -> Unit,
    onRadioSeekUp: () -> Unit, onRadioSeekDown: () -> Unit,
    onRadioCycleFm: () -> Unit, onRadioSwitchAm: () -> Unit,
    onRadioTune: (String, Float) -> Unit, onAssignRadio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radioVm: com.openlauncher.app.radio.ui.RadioDashboardViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by radioVm.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val french = com.openlauncher.app.ui.components.LocalAppLanguage.current.tag.startsWith("fr")
    val oemRadio = remember(context) { context.packageManager.getLaunchIntentForPackage("com.jancar.radio") }
    val audioIntent = remember(context) { context.packageManager.getLaunchIntentForPackage("com.jancar.audiosettings") }
    fun launch(intent: android.content.Intent?) {
        if (intent == null) return
        runCatching { context.startActivity(intent) }.onFailure {
            android.widget.Toast.makeText(context, if (french) "Application indisponible" else "Application unavailable", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    if (state.connected || hardwareRadio == null) {
        com.openlauncher.app.radio.ui.RadioDashboardRoute(
            viewModel = radioVm, modifier = modifier,
            onOpenRadioApp = { if (oemRadio != null) launch(oemRadio) else onLaunchHardwareRadio() },
            onAssignRadio = onAssignRadio,
            onAudioSettings = audioIntent?.let { intent -> { launch(intent) } }
        )
    } else {
        LegacyRadioScreen(hardwareRadio, accent, onLaunchHardwareRadio, onStopHardwareRadio,
            onRadioSeekUp, onRadioSeekDown, onRadioCycleFm, onRadioSwitchAm, onRadioTune, onAssignRadio, modifier)
    }
}
