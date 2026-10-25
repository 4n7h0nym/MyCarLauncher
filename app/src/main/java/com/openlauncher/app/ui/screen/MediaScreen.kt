package com.openlauncher.app.ui.screen

import com.openlauncher.app.ui.components.LocalizedText as LocalizedLabel

import android.media.MediaMetadata
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.openlauncher.app.service.MediaListenerService
import com.openlauncher.app.data.JancarBluetoothManager
import com.openlauncher.app.model.NowPlayingState
import kotlinx.coroutines.delay

@Composable
fun MediaScreen(
    accent: Color,
    nowPlayingState: NowPlayingState? = null,
    mediaPlayerConfigured: Boolean = false,
    jancarServiceConnected: Boolean = false,
    onLaunchMediaPlayer: () -> Boolean = { false },
    onPlayPause: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val serviceState by MediaListenerService.nowPlaying.collectAsState()
    val state = nowPlayingState ?: serviceState
    val jancarError by JancarBluetoothManager.lastError.collectAsState()
    val cardBg     = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outline
    val textColor  = MaterialTheme.colorScheme.onBackground
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    var positionMs by remember { mutableLongStateOf(0L) }
    val durationMs = state?.durationMs?.takeIf { it > 0L }
        ?: state?.controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
        ?: 0L

    LaunchedEffect(state?.isPlaying, state?.title) {
        if (state == null) positionMs = 0L
        while (state?.isPlaying == true) {
            positionMs = state?.controller?.playbackState?.position ?: positionMs
            delay(500)
        }

    }

    val title = state?.title?.ifEmpty { null } ?: "Aucune lecture en cours"
    val artist = state?.artist?.ifEmpty { null }.orEmpty()
    val isPlaying = state?.isPlaying == true
    val controller = state?.controller
    var artworkFailed by remember(state?.artUri, state?.albumArt) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(28.dp))
                Column {
                    LocalizedLabel(
                        text = "BLUETOOTH MEDIA PLAYER (AVRCP)",
                        style = MaterialTheme.typography.titleLarge,
                        color = textColor,
                        fontSize = 16.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LocalizedLabel(
                        text = "Synchronized via Android MediaController & A2DP",
                        color = mutedColor,
                        fontSize = 11.sp
                    )
                }
            }
        }

        jancarError?.let { message ->
            LocalizedLabel(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Button(
                    enabled = mediaPlayerConfigured,
                    onClick = { onLaunchMediaPlayer() },
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    LocalizedLabel("Lancer l’application musique Bluetooth", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Main Player Container (Split Left / Right matching reference photo)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(28.dp))
                .padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── ZONE GAUCHE : VISUEL / ARTWORK & CAROUSEL PEEK ────────────────
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(340.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Main Artwork Card (Square with rounded corners)
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(cardBorder.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if ((state?.albumArt != null || state?.artUri != null) && !artworkFailed) {
                        AsyncImage(
                            model = state?.artUri ?: state?.albumArt,
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onError = { artworkFailed = true }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(64.dp))
                        }
                    }
                }
            }

            // ── ZONE DROITE : INFORMATIONS & CONTRÔLES ────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Meta & Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LocalizedLabel(
                            text = title,
                            color = textColor,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        LocalizedLabel(
                            text = artist,
                            color = mutedColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Middle Progress Bar & Audio Quality Tag
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slider(
                        enabled = controller != null,
                        value = positionMs.toFloat(),
                        onValueChange = { newVal ->
                            positionMs = newVal.toLong()
                            controller?.transportControls?.seekTo(newVal.toLong())
                        },
                        valueRange = 0f..(durationMs.coerceAtLeast(1L).toFloat()),
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = cardBorder.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LocalizedLabel(
                            text = formatMillis(positionMs),
                            color = mutedColor,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        LocalizedLabel(
                            text = formatMillis(durationMs),
                            color = mutedColor,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Main Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Button
                    Button(
                        enabled = controller != null || state != null || jancarServiceConnected,
                        onClick = onSkipPrevious,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                    ) {
                        Icon(Icons.Default.SkipPrevious, "Précédent", tint = textColor, modifier = Modifier.size(32.dp))
                    }

                    // Play / Pause Button
                    Button(
                        enabled = controller != null || state != null || mediaPlayerConfigured || jancarServiceConnected,
                        onClick = {
                            if (controller != null && isPlaying) controller.transportControls?.pause()
                            else if (controller != null) controller.transportControls?.play()
                            else onPlayPause()
                        },
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(72.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Lecture",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Next Button
                    Button(
                        enabled = controller != null || state != null || jancarServiceConnected,
                        onClick = onSkipNext,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, "Suivant", tint = textColor, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

private fun formatMillis(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
