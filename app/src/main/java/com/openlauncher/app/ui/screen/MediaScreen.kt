package com.openlauncher.app.ui.screen

import android.media.MediaMetadata
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.openlauncher.app.service.MediaListenerService
import com.openlauncher.app.data.JancarBluetoothManager
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.ui.components.LocalizedText as Label
import com.openlauncher.app.ui.components.AppLocalization
import com.openlauncher.app.ui.components.LocalAppLanguage
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
    val serviceState by MediaListenerService.nowPlaying.collectAsStateWithLifecycle()
    val state = nowPlayingState ?: serviceState
    val error by JancarBluetoothManager.lastError.collectAsStateWithLifecycle()
    val bluetooth by JancarBluetoothManager.playback.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = MaterialTheme.colorScheme
    val language = LocalAppLanguage.current
    val controller = state?.controller
    val playing = state?.isPlaying == true
    val duration = (state?.durationMs?.takeIf { it > 0 }
        ?: controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L)
    var position by remember(state?.title, controller) { mutableLongStateOf(0L) }
    var seeking by remember(state?.title, controller) { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, controller, playing, duration) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            do {
                if (!seeking) {
                    val playback = controller?.playbackState
                    val elapsed = if (playback?.state == android.media.session.PlaybackState.STATE_PLAYING && playback.lastPositionUpdateTime > 0L)
                        ((android.os.SystemClock.elapsedRealtime()-playback.lastPositionUpdateTime).coerceAtLeast(0L)*playback.playbackSpeed).toLong() else 0L
                    position = ((playback?.position ?: 0L)+elapsed).coerceIn(0L,duration)
                }
                delay(1000)
            } while (playing)
        }
    }
    var artworkFailed by remember(state?.artUri, state?.albumArt) { mutableStateOf(false) }
    val title = state?.title?.takeIf { it.isNotBlank() }
        ?: AppLocalization.translate("No media playing", language)
    var playbackRevision by remember(controller) { mutableIntStateOf(0) }
    DisposableEffect(controller) {
        val callback = object : android.media.session.MediaController.Callback() {
            override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) { playbackRevision++ }
            override fun onMetadataChanged(metadata: MediaMetadata?) { playbackRevision++ }
        }
        controller?.registerCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
        onDispose { controller?.unregisterCallback(callback) }
    }
    val metadata = remember(controller, playbackRevision, state?.title) { controller?.metadata }
    val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
        ?: bluetooth?.album?.takeIf { controller == null && jancarServiceConnected && it.isNotBlank() }
    val details = listOfNotNull(state?.artist?.takeIf { it.isNotBlank() }, album).joinToString(" • ")
    val source = remember(controller?.packageName,jancarServiceConnected,language) {
        if (controller == null && jancarServiceConnected) "BT"
        else controller?.packageName?.let { pkg -> runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg,0)).toString()
        }.getOrDefault(pkg) } ?: "—"
    }
    val actions = remember(controller, playbackRevision) { controller?.playbackState?.customActions.orEmpty() }
    val shuffleAction = actions.firstOrNull { it.action.contains("shuffle",ignoreCase=true) }
    val repeatAction = actions.firstOrNull { it.action.contains("repeat",ignoreCase=true) }
    fun label(fr: String, en: String) = if (language.tag.startsWith("fr")) fr else en
    val canControl = controller != null || state != null || jancarServiceConnected

    BoxWithConstraints(modifier.fillMaxSize().padding(8.dp)) {
        val compact = maxWidth < 600.dp
        val artworkSize = minOf(maxWidth * 0.32f, maxHeight - 32.dp, 300.dp).coerceAtLeast(48.dp)
        Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(28.dp), color = colors.surface) {
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!compact) {
                    Box(Modifier.size(if (state == null) minOf(artworkSize, 160.dp) else artworkSize)
                        .clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        if ((state?.artUri != null || state?.albumArt != null) && !artworkFailed) {
                            AsyncImage(model = state?.artUri ?: state?.albumArt,
                                contentDescription = AppLocalization.translate("Album Art", language),
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                                onError = { artworkFailed = true })
                        } else Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(64.dp))
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(title, color = colors.onSurface, style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if(details.isNotBlank()) Text(details, color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh,
                            modifier = Modifier.widthIn(max = 160.dp)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(if(jancarServiceConnected && controller == null) Icons.Default.Bluetooth else Icons.Default.MusicNote,
                                    null, Modifier.size(20.dp), tint = colors.primary)
                                Text("Source : $source", color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    OutlinedButton(onClick = {
                        com.openlauncher.app.util.MediaVolumeHelper.maximum(context).fold(
                            onSuccess = { (actual,maximum) -> android.widget.Toast.makeText(context,"Volume multimédia : $actual / $maximum",android.widget.Toast.LENGTH_SHORT).show() },
                            onFailure = { android.widget.Toast.makeText(context,it.message ?: "Volume indisponible",android.widget.Toast.LENGTH_LONG).show() }
                        )
                    },modifier=Modifier.heightIn(min=48.dp)) {
                        Icon(Icons.Default.VolumeUp,null); Spacer(Modifier.width(8.dp))
                        Text(label("Volume multimédia maximal", "Maximum media volume"))
                    }
                    if (state == null) {
                        if (error != null) Label("Bluetooth source unavailable. Open the head unit’s Bluetooth app and connect your phone.",
                            color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        if (mediaPlayerConfigured) {
                            Button(onClick = { onLaunchMediaPlayer() }, modifier = Modifier.heightIn(min = 76.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = colors.onPrimary)) {
                                Icon(Icons.Default.MusicNote, null)
                                Spacer(Modifier.width(8.dp))
                                Label("Open music", fontSize = 20.sp)
                            }
                        } else Label("Choose a music app in Settings", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (duration > 0 && state != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(formatMillis(position), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            Slider(value = position.coerceIn(0L, duration).toFloat(),
                                onValueChange = { seeking = true; position = it.toLong() },
                                onValueChangeFinished = { controller?.transportControls?.seekTo(position); seeking = false },
                                enabled = (controller?.playbackState?.actions?.and(android.media.session.PlaybackState.ACTION_SEEK_TO) ?: 0L) != 0L,
                                valueRange = 0f..duration.toFloat(),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                            Text("-${formatMillis((duration - position).coerceAtLeast(0L))}", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (canControl) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { shuffleAction?.let { controller?.transportControls?.sendCustomAction(it.action, it.extras) } },
                                enabled = shuffleAction != null, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Shuffle, label("Lecture aléatoire", "Shuffle"), Modifier.size(28.dp))
                            }
                            FilledTonalIconButton(onClick = onSkipPrevious, modifier = Modifier.weight(1f).height(76.dp)) {
                                Icon(Icons.Default.SkipPrevious, AppLocalization.translate("Previous", language), Modifier.size(36.dp))
                            }
                            Button(onClick = {
                                if (controller != null) {
                                    if (playing) controller.transportControls.pause() else controller.transportControls.play()
                                } else onPlayPause()
                            }, shape = RoundedCornerShape(28.dp), modifier = Modifier.weight(1.2f).height(88.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = colors.onPrimary)) {
                                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    AppLocalization.translate(if (playing) "Pause" else "Play", language), Modifier.size(40.dp))
                            }
                            FilledTonalIconButton(onClick = onSkipNext, modifier = Modifier.weight(1f).height(76.dp)) {
                                Icon(Icons.Default.SkipNext, AppLocalization.translate("Next", language), Modifier.size(36.dp))
                            }
                            IconButton(onClick = { repeatAction?.let { controller?.transportControls?.sendCustomAction(it.action, it.extras) } },
                                enabled = repeatAction != null, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Repeat, label("Répétition", "Repeat"), Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMillis(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
