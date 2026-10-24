package com.openlauncher.app.ui.screen

import com.openlauncher.app.ui.components.LocalizedText as LocalizedLabel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@Composable
fun RadioScreen(
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Radio, null, tint = accent, modifier = Modifier.size(28.dp))
                Column {
                    LocalizedLabel(
                        text = "VEHICLE RADIO & SATELLITE TUNER",
                        style = MaterialTheme.typography.titleLarge,
                        color = textColor,
                        fontSize = 16.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LocalizedLabel(
                        text = "AM/FM, DAB & Satellite Radio receiver",
                        color = mutedColor,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (hardwareRadio == null) {
            // No Radio Source Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(Icons.Default.Radio, null, tint = mutedColor, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    LocalizedLabel(
                        "NO RADIO SOURCE CONNECTED",
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    LocalizedLabel(
                        "Start your head unit's radio app or assign it below to enable steering wheel & direct mirror controls.",
                        color = mutedColor,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.widthIn(max = 400.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onAssignRadio,
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        LocalizedLabel("ASSIGN RADIO APP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Active Radio Tuner Display Card
            var isManuallyOff by remember { mutableStateOf(false) }
            LaunchedEffect(hardwareRadio) {
                if (isManuallyOff) isManuallyOff = false
            }
            val powerOn = !isManuallyOff
            val displayBand = hardwareRadio.band.uppercase()
            val freqClean   = hardwareRadio.freq.replace(Regex("[^0-9.]"), "")
            val displayFreq = freqClean.ifEmpty { hardwareRadio.freq }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                    .padding(32.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row: Band selector & Power
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hardwareRadio.canTune) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("FM1", "FM2", "FM3", "AM", "SAT", "SXM", "DAB").forEach { b ->
                                val active = displayBand == b || (displayBand.startsWith("DAB") && b == "DAB")
                                Surface(
                                    onClick = {
                                        if (b == "AM") onRadioSwitchAm()
                                        else onRadioCycleFm()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (active) accent else cardBorder.copy(alpha = 0.3f),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
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
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = accent,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                LocalizedLabel(
                                    displayBand,
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    // Power Button
                    IconButton(
                        onClick = {
                            if (powerOn) { onStopHardwareRadio(); isManuallyOff = true }
                            else { onLaunchHardwareRadio(); isManuallyOff = false }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (powerOn) accent.copy(alpha = 0.2f) else cardBorder.copy(alpha = 0.3f))
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
                        text = if (!powerOn) "RADIO OFF" else hardwareRadio.stationName ?: "LIVE RECEPTION",
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
                            text = hardwareRadio.displayUnit,
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
                    // Seek Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onRadioSeekDown,
                            enabled = powerOn,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = cardBorder.copy(alpha = 0.3f)),
                            modifier = Modifier.height(50.dp).width(160.dp)
                        ) {
                            Icon(Icons.Default.SkipPrevious, null, tint = textColor, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            LocalizedLabel("SEEK DOWN", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(24.dp))
                        Button(
                            onClick = onRadioSeekUp,
                            enabled = powerOn,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            modifier = Modifier.height(50.dp).width(160.dp)
                        ) {
                            LocalizedLabel("SEEK UP", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.SkipNext, null, tint = Color.Black, modifier = Modifier.size(24.dp))
                        }
                    }

                    // Presets Grid (if direct tuning supported)
                    if (hardwareRadio.canTune) {
                        val presetFreqs = when (displayBand) {
                            "AM" -> listOf(630f, 810f, 1050f, 1260f, 1440f, 1530f)
                            "SAT", "SXM" -> listOf(1f, 13f, 20f, 44f, 50f, 100f)
                            else -> listOf(88.5f, 91.1f, 95.7f, 101.1f, 103.5f, 107.9f)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetFreqs.forEach { pf ->
                                Surface(
                                    onClick = { onRadioTune(displayBand, pf) },
                                    enabled = powerOn,
                                    shape = RoundedCornerShape(12.dp),
                                    color = cardBorder.copy(alpha = 0.2f),
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        LocalizedLabel(
                                            text = if (displayBand == "AM" || displayBand in listOf("SAT", "SXM")) "%.0f".format(pf) else "%.1f".format(pf),
                                            color = if (powerOn) textColor else mutedColor,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
