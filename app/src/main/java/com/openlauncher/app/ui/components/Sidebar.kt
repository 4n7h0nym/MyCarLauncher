package com.openlauncher.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.data.DefaultShortcutIcon
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.model.MapboxManeuverInstruction
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.model.NavDestination
import com.openlauncher.app.ui.theme.LocalDayMode

@Composable
fun Sidebar(
    currentDest: NavDestination,
    settings: AppSettings,
    onNavigate: (NavDestination) -> Unit,
    isHorizontal: Boolean = false,
    modifier: Modifier = Modifier,
    onVoiceAssistant: () -> Unit = {},
    nowPlaying: NowPlayingState? = null,
    isRouteActive: Boolean = false,
    nextManeuver: MapboxManeuverInstruction? = null,
    onPlayPause: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onLaunchFavorite: (Int) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val language = LocalAppLanguage.current
    val favorite = settings.shortcuts.getOrNull(settings.dockFavoriteSlot)?.takeIf { it.packageName.isNotBlank() }
    val isDayMode = LocalDayMode.current
    val sidebarBg = if (isDayMode) Color(0xFFE0E0E0) else Color.Black.copy(alpha = 0.62f)
    val items: @Composable () -> Unit = {
        DockButton(Icons.Default.Home, "Home", currentDest == NavDestination.HOME) { onNavigate(NavDestination.HOME) }
        DockButton(Icons.Default.Navigation, "Nav", currentDest == NavDestination.NAVIGATION) { onNavigate(NavDestination.NAVIGATION) }
        DockButton(Icons.Default.MusicNote, "Media", currentDest == NavDestination.MEDIA) { onNavigate(NavDestination.MEDIA) }

        if (favorite != null) DockButton((favorite.customIconOverride ?: favorite.defaultIcon).toIcon(), favorite.label, false) { onLaunchFavorite(settings.dockFavoriteSlot) }
        else DockButton(Icons.Default.Radio, "Radio", currentDest == NavDestination.RADIO) { onNavigate(NavDestination.RADIO) }
        DockButton(Icons.Default.Contacts, "Contacts", currentDest == NavDestination.PHONE) { onNavigate(NavDestination.PHONE) }
        DockButton(Icons.Default.Apps, "Apps", currentDest == NavDestination.APP_LIBRARY) { onNavigate(NavDestination.APP_LIBRARY) }
        Box {
            DockButton(Icons.Default.MoreHoriz, "More", expanded) { expanded = true }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                listOf(Triple("Phone", Icons.Default.Phone, NavDestination.PHONE),
                    Triple("Radio", Icons.Default.Radio, NavDestination.RADIO),
                    Triple("Offline", Icons.Default.CloudDownload, NavDestination.OFFLINE_MAPS),
                    Triple("Settings", Icons.Default.Settings, NavDestination.SETTINGS)).forEach { (label, icon, destination) ->
                    DropdownMenuItem(text = { LocalizedText(label) }, leadingIcon = { Icon(icon, null) },
                        modifier = Modifier.heightIn(min = 64.dp), onClick = { expanded = false; onNavigate(destination) })
                }
                DropdownMenuItem(text = { LocalizedText("Voice") }, leadingIcon = { Icon(Icons.Default.Mic, null) },
                    modifier = Modifier.heightIn(min = 64.dp), onClick = { expanded = false; onVoiceAssistant() })
                if (nowPlaying != null) {
                    DropdownMenuItem(text = { LocalizedText(if (nowPlaying.isPlaying) "Pause" else "Play") },
                        modifier = Modifier.heightIn(min = 64.dp), onClick = onPlayPause)
                    DropdownMenuItem(text = { LocalizedText("Next") }, modifier = Modifier.heightIn(min = 64.dp), onClick = onSkipNext)
                }
                if (isRouteActive) DropdownMenuItem(text = { Text(nextManeuver?.instruction ?: AppLocalization.translate("Nav", language)) },
                    modifier = Modifier.heightIn(min = 64.dp), onClick = { expanded = false; onNavigate(NavDestination.NAVIGATION) })
            }
        }
    }
    if (isHorizontal) {
        BoxWithConstraints(modifier.fillMaxWidth().height(88.dp)
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(colors.surface)) {
            // Keep both shortcut groups symmetric; allocate all remaining width to the widget.
            // Only scroll when the actual width cannot accommodate the touch targets.
            val contentWidth = maxOf(maxWidth, if (isRouteActive || nowPlaying != null) 1032.dp else 848.dp)
            Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.width(contentWidth).fillMaxHeight().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Left items
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DockButton(Icons.Default.Home, "Home", currentDest == NavDestination.HOME) { onNavigate(NavDestination.HOME) }
                DockButton(Icons.Default.Navigation, "Nav", currentDest == NavDestination.NAVIGATION) { onNavigate(NavDestination.NAVIGATION) }
                DockButton(Icons.Default.MusicNote, "Media", currentDest == NavDestination.MEDIA) { onNavigate(NavDestination.MEDIA) }
                if (favorite != null) DockButton((favorite.customIconOverride ?: favorite.defaultIcon).toIcon(), favorite.label, false) { onLaunchFavorite(settings.dockFavoriteSlot) }
                else DockButton(Icons.Default.Radio, "Radio", currentDest == NavDestination.RADIO) { onNavigate(NavDestination.RADIO) }
                DockButton(Icons.Default.Contacts, "Contacts", currentDest == NavDestination.PHONE) { onNavigate(NavDestination.PHONE) }

            }

            Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                // Contextual running widget
                if (isRouteActive) {
                    val navigationBackground = colors.primaryContainer
                    val navigationForeground = com.openlauncher.app.ui.theme.contrastForeground(navigationBackground)
                    Surface(
                        onClick = { onNavigate(NavDestination.NAVIGATION) },
                        shape = RoundedCornerShape(16.dp),
                        color = navigationBackground,
                        contentColor = navigationForeground,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(navigationManeuverIcon(nextManeuver?.iconType), null, tint = navigationForeground, modifier = Modifier.size(40.dp))
                            Text(
                                text = nextManeuver?.instruction?.takeIf { it.isNotBlank() }
                                    ?: AppLocalization.translate("Guidage en cours", language),
                                modifier = Modifier.weight(1f),
                                color = navigationForeground,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                fontSize = 20.sp,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                    lineHeight = 24.sp
                                ),
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                } else if (nowPlaying != null) {
                    Surface(
                        onClick = { onNavigate(NavDestination.MEDIA) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth().height(64.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(nowPlaying.title.ifBlank { "Musique" }, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = colors.onSecondaryContainer, style = MaterialTheme.typography.titleMedium)
                                Text(nowPlaying.artist.ifBlank { "Lecteur audio" }, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = colors.onSecondaryContainer)
                            }
                            IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    if (nowPlaying.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = AppLocalization.translate(if (nowPlaying.isPlaying) "Pause" else "Play", language),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Match the left group's width so the contextual widget remains centered.
            Row(
                modifier = Modifier.width(412.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                DockButton(Icons.Default.Mic, "Voice", false, onVoiceAssistant)
                DockButton(Icons.Default.Apps, "Apps", currentDest == NavDestination.APP_LIBRARY) { onNavigate(NavDestination.APP_LIBRARY) }
                DockButton(Icons.Default.CloudDownload, "Offline", currentDest == NavDestination.OFFLINE_MAPS) { onNavigate(NavDestination.OFFLINE_MAPS) }
                DockButton(Icons.Default.Settings, "Settings", currentDest == NavDestination.SETTINGS) { onNavigate(NavDestination.SETTINGS) }
            }
        }
        }
        }
    } else {
        Column(
            modifier = modifier
                .width(88.dp)
                .fillMaxHeight()
                .background(sidebarBg)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items()
        }
    }
}

@Composable
private fun DockButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(76.dp).clip(RoundedCornerShape(24.dp))
        .background(if (active) colors.secondaryContainer else Color.Transparent)
        .semantics { selected = active }.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, AppLocalization.translate(label, LocalAppLanguage.current), Modifier.size(32.dp),
                tint = if (active) colors.primary else colors.onSurfaceVariant)
            if (active) Box(Modifier.size(16.dp, 3.dp).background(colors.primary, RoundedCornerShape(2.dp)))
        }
    }
}

fun DefaultShortcutIcon.toIcon(): ImageVector = when (this) {
    // Navigation & vehicle
    DefaultShortcutIcon.RADIO       -> Icons.Default.Radio
    DefaultShortcutIcon.CAMERA      -> Icons.Default.CameraAlt
    DefaultShortcutIcon.PHONE       -> Icons.Default.Phone
    DefaultShortcutIcon.MAP         -> Icons.Default.Map
    DefaultShortcutIcon.NAVIGATION  -> Icons.Default.Navigation
    DefaultShortcutIcon.CAR         -> Icons.Default.DirectionsCar
    DefaultShortcutIcon.GAS_STATION -> Icons.Default.LocalGasStation
    DefaultShortcutIcon.DASHBOARD   -> Icons.Default.Speed
    // Audio & media
    DefaultShortcutIcon.MUSIC       -> Icons.Default.MusicNote
    DefaultShortcutIcon.SPEAKER     -> Icons.Default.Speaker
    DefaultShortcutIcon.HEADSET     -> Icons.Default.Headset
    DefaultShortcutIcon.EQUALIZER   -> Icons.Default.Equalizer
    DefaultShortcutIcon.VOLUME_UP   -> Icons.Default.VolumeUp
    // Connectivity
    DefaultShortcutIcon.BLUETOOTH   -> Icons.Default.Bluetooth
    DefaultShortcutIcon.WIFI        -> Icons.Default.Wifi
    // Lighting & climate
    DefaultShortcutIcon.LIGHTBULB   -> Icons.Default.Lightbulb
    DefaultShortcutIcon.BRIGHTNESS  -> Icons.Default.BrightnessHigh
    DefaultShortcutIcon.AC          -> Icons.Default.AcUnit
    DefaultShortcutIcon.THERMOSTAT  -> Icons.Default.Thermostat
    // General utility
    DefaultShortcutIcon.TV          -> Icons.Default.Tv
    DefaultShortcutIcon.VIDEOCAM    -> Icons.Default.Videocam
    DefaultShortcutIcon.STAR        -> Icons.Default.Star
    DefaultShortcutIcon.MESSAGE     -> Icons.Default.Message
    DefaultShortcutIcon.TIMER       -> Icons.Default.Timer
    DefaultShortcutIcon.LOCK        -> Icons.Default.Lock
    DefaultShortcutIcon.SETTINGS    -> Icons.Default.Settings
    DefaultShortcutIcon.FAVORITE    -> Icons.Default.Favorite
    // Web / location
    DefaultShortcutIcon.GLOBE       -> Icons.Default.Language
    DefaultShortcutIcon.NONE        -> Icons.Default.Apps
}

/** Mapbox provides the maneuver type and modifier; never infer direction from translated text. */
private fun navigationManeuverIcon(iconType: String?): ImageVector {
    val type = iconType.orEmpty().uppercase(java.util.Locale.ROOT).replace('_', ' ').replace('-', ' ')
    return when {
        type.contains("ARRIV") -> Icons.Default.Flag
        type.contains("ROUNDABOUT") || type.contains("ROTARY") -> Icons.Default.RoundaboutRight
        type.contains("UTURN") || type.contains("U TURN") -> Icons.Default.UTurnLeft
        type.contains("SHARP RIGHT") -> Icons.Default.TurnSharpRight
        type.contains("SHARP LEFT") -> Icons.Default.TurnSharpLeft
        type.contains("SLIGHT RIGHT") -> Icons.Default.TurnSlightRight
        type.contains("SLIGHT LEFT") -> Icons.Default.TurnSlightLeft
        type.contains("RIGHT") -> Icons.Default.TurnRight
        type.contains("LEFT") -> Icons.Default.TurnLeft
        type.contains("STRAIGHT") || type.contains("CONTINUE") || type.contains("DEPART") -> Icons.Default.Straight
        else -> Icons.Default.Navigation
    }
}
