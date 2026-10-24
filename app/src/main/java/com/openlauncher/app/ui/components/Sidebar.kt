package com.openlauncher.app.ui.components

import com.openlauncher.app.ui.components.LocalizedText as LocalizedLabel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.data.DefaultShortcutIcon
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.model.MapboxManeuverInstruction
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.model.NavDestination
import com.openlauncher.app.ui.theme.LocalDayMode
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ICON_SIZE   = 22.dp
private val SLOT_SIZE   = 52.dp

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
    onSkipNext: () -> Unit = {}
) {
    val isDayMode    = LocalDayMode.current
    val accent       = Color(settings.accentColor)
    val sidebarBg    = if (isDayMode) Color(0xFFE0E0E0) else Color.Black.copy(alpha = 0.4f)
    val iconInactive = if (isDayMode) Color(0xFF777777) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
    val dividerColor = if (isDayMode) Color(0xFFCCCCCC) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
    if (isHorizontal) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(sidebarBg)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavButton(Icons.Default.Home,       "Home",     currentDest == NavDestination.HOME,        accent, iconInactive, true) { onNavigate(NavDestination.HOME) }
                NavButton(Icons.Default.Phone,      "Phone",    currentDest == NavDestination.PHONE,       accent, iconInactive, true) { onNavigate(NavDestination.PHONE) }
                NavButton(Icons.Default.Radio,      "Radio",    currentDest == NavDestination.RADIO,       accent, iconInactive, true) { onNavigate(NavDestination.RADIO) }
                NavButton(Icons.Default.MusicNote,  "Media",    currentDest == NavDestination.MEDIA,       accent, iconInactive, true) { onNavigate(NavDestination.MEDIA) }
                NavButton(Icons.Default.Navigation, "Nav",      currentDest == NavDestination.NAVIGATION,  accent, iconInactive, true) { onNavigate(NavDestination.NAVIGATION) }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavButton(Icons.Default.Mic,        "Voice",    false,                                      accent, iconInactive, true, onVoiceAssistant)
                NavButton(Icons.Default.Apps,       "Apps",     currentDest == NavDestination.APP_LIBRARY, accent, iconInactive, true) { onNavigate(NavDestination.APP_LIBRARY) }
                NavButton(Icons.Default.CloudDownload, "Offline", currentDest == NavDestination.OFFLINE_MAPS, accent, iconInactive, true) { onNavigate(NavDestination.OFFLINE_MAPS) }
                NavButton(Icons.Default.Settings,   "Settings", currentDest == NavDestination.SETTINGS,    accent, iconInactive, true) { onNavigate(NavDestination.SETTINGS) }
            }
            if (isRouteActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 268.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), MaterialTheme.shapes.medium)
                            .clickable { onNavigate(NavDestination.NAVIGATION) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            LocalizedLabel("PROCHAINE ÉTAPE", style = MaterialTheme.typography.labelSmall, color = accent)
                            LocalizedLabel(nextManeuver?.instruction ?: "Guidage en cours", maxLines = 1, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            } else if (nowPlaying?.isPlaying == true) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 268.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), MaterialTheme.shapes.medium)
                            .clickable { onNavigate(NavDestination.MEDIA) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            LocalizedLabel(
                                nowPlaying?.title ?: "Aucune lecture en cours",
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            LocalizedLabel(
                                nowPlaying?.artist?.takeIf { it.isNotBlank() } ?: "Lecteur audio",
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onPlayPause, enabled = nowPlaying != null, modifier = Modifier.size(36.dp)) {
                            Icon(
                                if (nowPlaying?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (nowPlaying?.isPlaying == true) "Pause" else "Lecture",
                                tint = accent,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(onClick = onSkipNext, enabled = nowPlaying != null, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Suivant", tint = accent, modifier = Modifier.size(21.dp))
                        }
                    }
                }
            }
        }
    } else {
        val currentTime by produceState(
            initialValue = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        ) {
            while (true) {
                value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                delay(1_000L)
            }
        }
        Column(
            modifier = modifier
                .width(56.dp)
                .fillMaxHeight()
                .background(sidebarBg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LocalizedLabel(
                text = currentTime,
                color = if (isDayMode) Color(0xFF333333) else Color.White,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NavButton(
                    icon         = Icons.Default.Home,
                    label        = "Home",
                    isActive     = currentDest == NavDestination.HOME,
                    accent       = accent,
                    iconInactive = iconInactive,
                    isHorizontal = false,
                    onClick      = { onNavigate(NavDestination.HOME) }
                )
                NavButton(
                    icon         = Icons.Default.Radio,
                    label        = "Radio",
                    isActive     = currentDest == NavDestination.RADIO,
                    accent       = accent,
                    iconInactive = iconInactive,
                    isHorizontal = false,
                    onClick      = { onNavigate(NavDestination.RADIO) }
                )
                NavButton(
                    icon         = Icons.Default.Phone,
                    label        = "Phone",
                    isActive     = currentDest == NavDestination.PHONE,
                    accent       = accent,
                    iconInactive = iconInactive,
                    isHorizontal = false,
                    onClick      = { onNavigate(NavDestination.PHONE) }
                )
                NavButton(
                    icon         = Icons.Default.MusicNote,
                    label        = "Media",
                    isActive     = currentDest == NavDestination.MEDIA,
                    accent       = accent,
                    iconInactive = iconInactive,
                    isHorizontal = false,
                    onClick      = { onNavigate(NavDestination.MEDIA) }
                )
                NavButton(
                    icon         = Icons.Default.Navigation,
                    label        = "Nav",
                    isActive     = currentDest == NavDestination.NAVIGATION,
                    accent       = accent,
                    iconInactive = iconInactive,
                    isHorizontal = false,
                    onClick      = { onNavigate(NavDestination.NAVIGATION) }
                )
                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 4.dp))
            }

            HorizontalDivider(color = dividerColor)
            NavButton(
                icon = Icons.Default.Mic,
                label = "Assistant vocal",
                isActive = false,
                accent = accent,
                iconInactive = iconInactive,
                isHorizontal = false,
                onClick = onVoiceAssistant
            )
            NavButton(
                icon         = Icons.Default.Apps,
                label        = "Apps",
                isActive     = currentDest == NavDestination.APP_LIBRARY,
                accent       = accent,
                iconInactive = iconInactive,
                isHorizontal = false,
                onClick      = { onNavigate(NavDestination.APP_LIBRARY) }
            )
            NavButton(
                icon         = Icons.Default.CloudDownload,
                label        = "Offline",
                isActive     = currentDest == NavDestination.OFFLINE_MAPS,
                accent       = accent,
                iconInactive = iconInactive,
                isHorizontal = false,
                onClick      = { onNavigate(NavDestination.OFFLINE_MAPS) }
            )
            NavButton(
                icon         = Icons.Default.Settings,
                label        = "Settings",
                isActive     = currentDest == NavDestination.SETTINGS,
                accent       = accent,
                iconInactive = iconInactive,
                isHorizontal = false,
                onClick      = { onNavigate(NavDestination.SETTINGS) }
            )
            Spacer(Modifier.height(4.dp))
        }
    }

}
@Composable
private fun NavButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    accent: Color,
    iconInactive: Color,
    isHorizontal: Boolean = false,
    onClick: () -> Unit
) {
    val isDayMode = LocalDayMode.current
    val activeIconColor = if (isDayMode) Color(0xFF111111) else Color.White
    val activeBg = if (isDayMode) Color(0xFF000000).copy(alpha = 0.08f) else Color.White.copy(alpha = 0.06f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .then(
                if (isHorizontal) Modifier.fillMaxHeight().width(SLOT_SIZE)
                else              Modifier.fillMaxWidth().height(SLOT_SIZE)
            )
            .background(if (isActive) activeBg else Color.Transparent)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = label,
            tint               = if (isActive) activeIconColor else iconInactive,
            modifier           = Modifier.size(ICON_SIZE)
        )
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
