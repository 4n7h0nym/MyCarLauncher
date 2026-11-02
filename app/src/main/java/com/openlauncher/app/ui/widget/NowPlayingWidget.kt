package com.openlauncher.app.ui.widget
import androidx.lifecycle.compose.collectAsStateWithLifecycle


import com.openlauncher.app.ui.components.LocalizedText as LocalizedLabel

import android.media.MediaMetadata
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.service.MediaListenerService
import kotlin.math.abs
import kotlinx.coroutines.delay

@Composable
fun NowPlayingWidget(
    state: NowPlayingState?,
    accent: Color,
    carPlayPackage: String,
    androidAutoPackage: String,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onLaunchCarPlay: () -> Unit,
    onLaunchAndroidAuto: () -> Unit,
    onTapToOpenApp: () -> Unit,
    modifier: Modifier = Modifier,
    isEditing: Boolean = false,
    isDayMode: Boolean = false,
    hardwareRadio: com.openlauncher.app.viewmodel.LauncherViewModel.HardwareRadioState? = null,
    onLaunchHardwareRadio: () -> Unit = {},
    onStopHardwareRadio: () -> Unit = {},
    onRadioSeekUp: () -> Unit = {},
    onRadioSeekDown: () -> Unit = {},
    onRadioCycleFm: () -> Unit = {},
    onRadioSwitchAm: () -> Unit = {},
    onRadioTune: (band: String, freq: Float) -> Unit = { _, _ -> },
    onAssignRadio: () -> Unit = {}
) {
    val radioViewModel: com.openlauncher.app.viewmodel.RadioViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val radioUi by radioViewModel.uiState.collectAsStateWithLifecycle()
    val context     = LocalContext.current
    val isConnected by MediaListenerService.isConnected.collectAsState()
    val hasCarPlay  = carPlayPackage.isNotEmpty()
    val hasAutoApp  = androidAutoPackage.isNotEmpty()
    val hasContent  = state != null && state.title.isNotEmpty()

    var selectedSource by rememberSaveable { mutableStateOf("Any Player") }

    val hasHardwareRadio = hardwareRadio != null
    LaunchedEffect(hasHardwareRadio) {
        if (hasHardwareRadio && selectedSource == "Any Player" && hardwareRadio != null) {
            // Keep default or allow user swipe
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .pointerInput(selectedSource, hasHardwareRadio) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        if (abs(dragAmount) > 25f) {
                            if (dragAmount > 0 && selectedSource == "FM/AM Radio") {
                                selectedSource = "Any Player"
                            } else if (dragAmount < 0 && selectedSource == "Any Player" && hasHardwareRadio) {
                                selectedSource = "FM/AM Radio"
                            }
                        }
                    }
                )
            }
    ) {
        // 1. CONDITIONAL VIEW TOGGLE
        if (selectedSource == "FM/AM Radio" && radioUi.connected) {
            com.openlauncher.app.ui.components.RadioCard(radioUi, radioViewModel, Modifier.fillMaxSize())
        } else if (selectedSource == "FM/AM Radio") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)
            ) {
                RadioDeck(
                    accent = accent,
                    isDayMode = isDayMode,
                    hardwareRadio = hardwareRadio,
                    onLaunchHardwareRadio = onLaunchHardwareRadio,
                    onStopHardwareRadio = onStopHardwareRadio,
                    onRadioSeekUp = onRadioSeekUp,
                    onRadioSeekDown = onRadioSeekDown,
                    onAssignRadio = onAssignRadio,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            StandardMinimalPlayer(
                state = state,
                accent = accent,
                hasContent = hasContent,
                isEditing = isEditing,
                isDayMode = isDayMode,
                isConnected = isConnected,
                hasCarPlay = hasCarPlay,
                hasAutoApp = hasAutoApp,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrev = onPrev,
                onLaunchCarPlay = onLaunchCarPlay,
                onLaunchAndroidAuto = onLaunchAndroidAuto,
                onTapToOpenApp = onTapToOpenApp,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. FLOATING MULTI-SOURCE SELECTOR (Top-Right)
        var menuExpanded by remember { mutableStateOf(false) }
        val selectorIconColor = if (isDayMode) Color.Black.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp, top = 4.dp)
        ) {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = com.openlauncher.app.ui.components.AppLocalization.translate("Source Selector", com.openlauncher.app.ui.components.LocalAppLanguage.current),
                    tint = selectorIconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            val dropdownBg   = if (isDayMode) Color(0xFFF0F0F0) else MaterialTheme.colorScheme.background
            val dropdownText = if (isDayMode) Color(0xFF111111) else MaterialTheme.colorScheme.onBackground
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(dropdownBg)
            ) {
                DropdownMenuItem(
                    text = { LocalizedLabel("Any Player", color = dropdownText, fontSize = 11.sp) },
                    onClick = {
                        selectedSource = "Any Player"
                        menuExpanded = false
                    },
                    leadingIcon = { Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(14.dp)) }
                )
                if (hasHardwareRadio) {
                    DropdownMenuItem(
                        text = { LocalizedLabel("FM/AM Radio", color = dropdownText, fontSize = 11.sp) },
                        onClick = {
                            selectedSource = "FM/AM Radio"
                            menuExpanded = false
                        },
                        leadingIcon = { Icon(Icons.Default.Radio, null, tint = accent, modifier = Modifier.size(14.dp)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioDeck(
    accent: Color,
    isDayMode: Boolean,
    hardwareRadio: com.openlauncher.app.viewmodel.LauncherViewModel.HardwareRadioState?,
    onLaunchHardwareRadio: () -> Unit,
    onStopHardwareRadio: () -> Unit,
    onRadioSeekUp: () -> Unit,
    onRadioSeekDown: () -> Unit,
    onAssignRadio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (isDayMode) Color(0xFF111111) else MaterialTheme.colorScheme.onBackground
    val dimColor     = if (isDayMode) Color(0xFF444444) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
    val borderColor  = if (isDayMode) Color(0xFF777777) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)

    if (hardwareRadio == null) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Radio, null, tint = dimColor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            LocalizedLabel(
                "PAS DE SOURCE RADIO",
                color = contentColor.copy(alpha = 0.85f),
                fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onAssignRadio() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                LocalizedLabel(
                    "ASSIGNER L'APPLI RADIO",
                    color = accent, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                )
            }
        }
        return
    }

    val powerOn = hardwareRadio.isPowerOn

    val freqClean   = hardwareRadio.freq.replace(Regex("[^0-9.]"), "")
    val displayFreq = freqClean.ifEmpty { hardwareRadio.freq }
    val displayUnit = hardwareRadio.displayUnit
    val chipInactiveBg = if (isDayMode) Color(0xFFE0E0E0) else Color(0xFF1A1A1A)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top: Power Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (powerOn) accent.copy(alpha = 0.2f) else chipInactiveBg)
                    .border(2.dp, if (powerOn) accent else borderColor, CircleShape)
                    .clickable {
                        if (powerOn) onStopHardwareRadio() else onLaunchHardwareRadio()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PowerSettingsNew, "PWR",
                    tint = if (powerOn) accent else dimColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Center: Enlarged Frequency Display
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            LocalizedLabel(
                text = if (powerOn) displayFreq else "— —",
                color = if (powerOn) contentColor else contentColor.copy(alpha = 0.3f),
                fontSize = 58.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(8.dp))
            LocalizedLabel(
                text = displayUnit,
                color = dimColor,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        // Bottom: Enlarged Previous & Next Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onRadioSeekDown,
                enabled = powerOn,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = chipInactiveBg),
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Icon(Icons.Default.SkipPrevious, null, tint = contentColor, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(6.dp))
                LocalizedLabel("PRÉCÉDENT", color = contentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onRadioSeekUp,
                enabled = powerOn,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                LocalizedLabel("SUIVANT", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.SkipNext, null, tint = Color.Black, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun StandardMinimalPlayer(
    state: NowPlayingState?,
    accent: Color,
    hasContent: Boolean,
    isEditing: Boolean,
    isDayMode: Boolean,
    isConnected: Boolean,
    hasCarPlay: Boolean,
    hasAutoApp: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onLaunchCarPlay: () -> Unit,
    onLaunchAndroidAuto: () -> Unit,
    onTapToOpenApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val idleIconColor = if (isDayMode) Color(0xFF555555) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.30f)
    val idleTextColor = if (isDayMode) Color(0xFF555555) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.30f)
    val currentTextColor = if (isDayMode) Color(0xFF111111) else MaterialTheme.colorScheme.onBackground
    val currentSubTextColor = if (isDayMode) Color(0xFF666666) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)

    Box(modifier = modifier) {
        if (!hasContent) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (hasCarPlay || hasAutoApp) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasCarPlay) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .let { if (!isEditing) it.clickable { onLaunchCarPlay() } else it }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.PhoneAndroid, null, tint = accent.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
                                    LocalizedLabel("CARPLAY", color = accent.copy(alpha = 0.6f), fontSize = 8.sp, letterSpacing = 2.sp)
                                }
                            }
                        }
                        if (hasCarPlay && hasAutoApp) {
                            androidx.compose.material3.VerticalDivider(
                                modifier = Modifier.fillMaxHeight().padding(vertical = 16.dp),
                                color = if (isDayMode) Color(0xFFBBBBBB) else Color(0xFF1E1E1E)
                            )
                        }
                        if (hasAutoApp) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .let { if (!isEditing) it.clickable { onLaunchAndroidAuto() } else it }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.DirectionsCar, null, tint = accent.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
                                    LocalizedLabel("ANDROID AUTO", color = accent.copy(alpha = 0.6f), fontSize = 8.sp, letterSpacing = 2.sp)
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.MusicNote, null, tint = idleIconColor, modifier = Modifier.size(24.dp))
                        LocalizedLabel("NO MEDIA PLAYING", color = idleTextColor, fontSize = 7.sp, letterSpacing = 1.sp)
                    }
                }
            }
        } else {
            val nonNullState = state!!
            var positionMs by remember { mutableLongStateOf(nonNullState.controller?.playbackState?.position ?: 0L) }
            val durationMs = nonNullState.controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

            LaunchedEffect(nonNullState.isPlaying, nonNullState.title) {
                while (nonNullState.isPlaying) {
                    positionMs = nonNullState.controller?.playbackState?.position ?: positionMs
                    delay(500)
                }
            }

            val hasAlbumArt = nonNullState.albumArt != null
            val useDarkTheme = hasAlbumArt || !isDayMode

            val finalTextColor = if (hasAlbumArt) Color.White else currentTextColor
            val finalSubTextColor = if (hasAlbumArt) Color.White.copy(alpha = 0.6f) else currentSubTextColor
            val currentProgressColor = if (useDarkTheme) accent else if (isDayMode) Color(0xFF111111) else accent
            val currentProgressTrack = finalTextColor.copy(alpha = 0.15f)
            val currentIconColor = finalTextColor.copy(alpha = 0.75f)
            val currentPlayBgColor = if (useDarkTheme) accent.copy(alpha = 0.9f) else if (isDayMode) Color(0xFF111111) else accent.copy(alpha = 0.9f)
            val currentPlayIconColor = if (useDarkTheme) Color.Black else Color.White

            if (hasAlbumArt) {
                coil.compose.AsyncImage(
                    model = nonNullState.artUri ?: nonNullState.albumArt,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    error = nonNullState.albumArt?.let {
                        androidx.compose.ui.graphics.painter.BitmapPainter(it.asImageBitmap())
                    },
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .let { if (!isEditing) it.clickable { onTapToOpenApp() } else it }
                ) {
                    androidx.compose.material3.Text(
                        text = nonNullState.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = finalTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp
                    )
                    androidx.compose.material3.Text(
                        text = nonNullState.artist.ifEmpty { com.openlauncher.app.ui.components.AppLocalization.translate("Unknown", com.openlauncher.app.ui.components.LocalAppLanguage.current) },
                        style = MaterialTheme.typography.bodySmall,
                        color = finalSubTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (durationMs > 0) {
                        LinearProgressIndicator(
                            progress = { (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = currentProgressColor,
                            trackColor = currentProgressTrack
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LocalizedLabel(formatMs(positionMs), style = MaterialTheme.typography.labelSmall, color = finalSubTextColor.copy(alpha = 0.75f), fontSize = 9.sp)
                            LocalizedLabel(formatMs(durationMs), style = MaterialTheme.typography.labelSmall, color = finalSubTextColor.copy(alpha = 0.75f), fontSize = 9.sp)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { if (!isEditing) onPrev() }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.SkipPrevious, "Prev", tint = currentIconColor, modifier = Modifier.size(28.dp))
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(currentPlayBgColor)
                        ) {
                            IconButton(onClick = { if (!isEditing) onPlayPause() }, modifier = Modifier.size(56.dp)) {
                                Icon(
                                    imageVector = if (nonNullState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (nonNullState.isPlaying) "Pause" else "Play",
                                    tint = currentPlayIconColor,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        IconButton(onClick = { if (!isEditing) onNext() }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.SkipNext, "Next", tint = currentIconColor, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
