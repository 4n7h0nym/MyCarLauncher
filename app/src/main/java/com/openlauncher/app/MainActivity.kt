package com.openlauncher.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mapbox.common.MapboxOptions
import com.openlauncher.app.data.OfflineRegionManager
import com.openlauncher.app.data.BluetoothContactsRepository
import com.openlauncher.app.data.DayNightMode
import com.openlauncher.app.data.SidebarPosition
import com.openlauncher.app.data.GradientDirection
import com.openlauncher.app.data.AppLanguage
import com.openlauncher.app.model.NavDestination
import com.openlauncher.app.model.IncomingMessage
import com.openlauncher.app.service.MediaListenerService
import com.openlauncher.app.ui.components.VoiceAssistantDialog
import com.openlauncher.app.ui.components.LocalAppLanguage
import com.openlauncher.app.util.TextToSpeechManager
import com.openlauncher.app.ui.components.Sidebar
import com.openlauncher.app.ui.screen.*
import com.openlauncher.app.ui.theme.OpenLauncherTheme
import com.openlauncher.app.viewmodel.LauncherViewModel
import com.openlauncher.app.viewmodel.NavigationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()
    private val navVm: NavigationViewModel by viewModels()
    private lateinit var speech: TextToSpeechManager

    private val callPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val number = pendingCallNumber
        pendingCallNumber = null
        if (granted && number != null) dial(number) else speech.speak(if (currentVoiceLanguage().startsWith("en")) "Call permission is required to call this contact." else "L’autorisation d’appel est nécessaire pour appeler ce contact.")
    }
    private var pendingCallNumber: String? = null
    private fun currentVoiceLanguage(): String = vm.settings.value.appLanguage.tag

    private fun placeCall(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingCallNumber = number
            callPermission.launch(Manifest.permission.CALL_PHONE)
        } else dial(number)
    }

    private fun dial(number: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)))
        }.onFailure {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
        }
    }

    private fun normalizeVoiceText(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9+ ]"), " ").replace(Regex("\\s+"), " ").trim()

    private val locationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            vm.startLocationUpdates()
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speech = TextToSpeechManager(this)
        hideSystemBars()
        // Configure the existing Gradle-provided token before any offline Mapbox
        // manager or TileStore is initialized.
        MapboxOptions.accessToken = getString(R.string.mapbox_access_token)
        OfflineRegionManager.ensureStylePackDownloaded(this)

        setContent {
            val settingsLoaded by vm.settingsLoaded.collectAsStateWithLifecycle()
            val settings       by vm.settings.collectAsStateWithLifecycle()
            val nav            by vm.nav.collectAsStateWithLifecycle()
            val apps        by vm.apps.collectAsStateWithLifecycle()
            val appsLoading by vm.appsLoading.collectAsStateWithLifecycle()
            val nowPlaying  by vm.nowPlaying.collectAsStateWithLifecycle()
            val navigationState by navVm.uiState.collectAsStateWithLifecycle()
            val weather     by vm.weather.collectAsStateWithLifecycle()
            val location    by vm.location.collectAsStateWithLifecycle()
            val bearing     by vm.compassBearing.collectAsStateWithLifecycle()
            val isWifi      by vm.isWifi.collectAsStateWithLifecycle()
            val isData      by vm.isData.collectAsStateWithLifecycle()
            val isDayModeVM by vm.isDayMode.collectAsStateWithLifecycle()
            val hardwareRadio by vm.hardwareRadio.collectAsStateWithLifecycle()
            val systemIsDark = isSystemInDarkTheme()
            val isDayMode = if (settings.dayNightMode == DayNightMode.SYSTEM) !systemIsDark else isDayModeVM
            val pickerSlot      by vm.shortcutPickerSlot.collectAsStateWithLifecycle()
            val appPickerTarget by vm.appPickerTarget.collectAsStateWithLifecycle()
            val incomingMessage by MediaListenerService.incomingMessage.collectAsStateWithLifecycle()
            var voiceAssistantOpen by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                vm.triggerVoiceAssistant.collect {
                    voiceAssistantOpen = true
                }
            }
            SideEffect {
                MediaListenerService.announceIncomingMessages = settings.announceIncomingMessages
                MediaListenerService.voiceLanguageTag = settings.appLanguage.tag
                speech.setLanguage(settings.appLanguage.tag)
                java.util.Locale.setDefault(java.util.Locale.forLanguageTag(settings.appLanguage.tag))
            }

            val accent         = Color(settings.accentColor)
            val bg             = if (settings.useCustomBackgroundColor) {
                Color(settings.backgroundColor)
            } else {
                if (isDayMode) Color(0xFFEEEEEE) else Color.Black
            }
            val textColor      = if (isDayMode) Color(0xFF111111) else Color(settings.fontColor)
            val bgGradientEnd  = Color(settings.gradientEndColor)
            val bgBrush        = if (settings.useCustomBackgroundColor && settings.useGradient) {
                val colors = listOf(bg, bgGradientEnd)
                when (settings.gradientDirection) {
                    GradientDirection.TOP_TO_BOTTOM -> androidx.compose.ui.graphics.Brush.verticalGradient(colors)
                    GradientDirection.LEFT_TO_RIGHT -> androidx.compose.ui.graphics.Brush.horizontalGradient(colors)
                    GradientDirection.DIAGONAL -> androidx.compose.ui.graphics.Brush.linearGradient(colors)
                    GradientDirection.RADIAL -> androidx.compose.ui.graphics.Brush.radialGradient(colors)
                }
            } else null

            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density   = baseDensity.density * settings.uiScale,
                    fontScale = baseDensity.fontScale
                )
            ) {
                if (!settingsLoaded) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                } else OpenLauncherTheme(
                    accent         = accent,
                    background     = bg,
                    textColor      = textColor,
                    surfaceColor   = Color(settings.cardSurfaceColor),
                    borderColor    = Color(settings.cardBorderColor),
                    textMutedColor = Color(settings.textMutedColor),
                    fontBold       = settings.fontBold,
                    textScale      = settings.textScale,
                    appFont        = settings.appFont,
                    isDayMode      = isDayMode,
                    useCustomBg    = settings.useCustomBackgroundColor
                ) {
                CompositionLocalProvider(LocalAppLanguage provides settings.appLanguage) {
                if (!settings.onboardingCompleted) {
                    OnboardingScreen(
                        accent = accent,
                        location = location,
                        language = settings.appLanguage,
                        onLanguageChange = { language -> vm.updateSettings { copy(appLanguage = language) } },
                        onLocationPermissionGranted = { vm.startLocationUpdates() },
                        onComplete = {
                            vm.updateSettings { copy(onboardingCompleted = true) }
                            // Start location updates immediately upon completion
                            vm.startLocationUpdates()
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().let { m ->
                        if (bgBrush != null) m.background(bgBrush) else m.background(bg)
                    }) {
                        // Optional wallpaper layer
                        if (settings.wallpaperUri.isNotEmpty()) {
                            AsyncImage(
                                model              = android.net.Uri.parse(settings.wallpaperUri),
                                contentDescription = null,
                                contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier           = Modifier.fillMaxSize()
                            )
                            Box(modifier = Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = settings.wallpaperDim)))
                        }

                        val isBottomBar    = settings.sidebarPosition == SidebarPosition.BOTTOM
                        val layoutDivColor = if (isDayMode) Color(0xFFCCCCCC) else Color(0xFF1A1A1A)

                        val sidebarContent: @Composable () -> Unit = {
                            val sidebarDensity = Density(
                                density = baseDensity.density * (1.0f + (settings.uiScale - 1.0f) * 0.35f),
                                fontScale = baseDensity.fontScale
                            )
                            CompositionLocalProvider(LocalDensity provides sidebarDensity) {
                                Sidebar(
                                    currentDest   = nav,
                                    settings      = settings,
                                    isHorizontal  = isBottomBar,
                                    onNavigate    = { dest ->
                                        vm.cancelShortcutPicker()
                                        vm.cancelCarPlayPicker()
                                        vm.exitRearrangeMode()
                                        vm.navigate(dest)
                                    },
                                    onVoiceAssistant = { voiceAssistantOpen = true },
                                    nowPlaying = nowPlaying,
                                    isRouteActive = navigationState.isNavigating,
                                    nextManeuver = navigationState.nextManeuver,
                                    onPlayPause = vm::playPause,
                                    onSkipNext = vm::skipNext
                                )
                            }
                        }

                        val mainPane: @Composable (Modifier) -> Unit = { paneModifier ->
                            // ── Main content pane ─────────────────────────────
                            AnimatedContent(
                                targetState   = nav,
                                transitionSpec = {
                                    fadeIn() + slideInHorizontally { it / 10 } togetherWith
                                    fadeOut() + slideOutHorizontally { -it / 10 }
                                },
                                modifier = paneModifier,
                                label    = "pane_transition"
                            ) { destination ->
                                when (destination) {
                                    NavDestination.HOME -> HomeScreen(
                                        settings            = settings,
                                        weather             = weather,
                                        nowPlaying          = nowPlaying,
                                        location            = location,
                                        bearing             = bearing,
                                        isWifi              = isWifi,
                                        isData              = isData,
                                        isDayMode           = isDayMode,
                                        onPlayPause         = vm::playPause,
                                        onNext              = vm::skipNext,
                                        onPrev              = vm::skipPrev,
                                        onLaunchCarPlay     = { vm.launchApp(settings.carPlayPackage) },
                                        onLaunchAndroidAuto = { vm.launchApp(settings.androidAutoPackage) },
                                        onAssignCarPlay     = { vm.startCarPlayPicker() },
                                        onAssignAndroidAuto = { vm.startAndroidAutoPicker() },
                                        onClearCarPlay      = { vm.clearCarPlayApp() },
                                        onClearAndroidAuto  = { vm.clearAndroidAutoApp() },
                                        onAssignPip         = { vm.startPipPicker() },
                                        onClearPip          = { vm.clearPipApp() },
                                        onLaunchPip         = { vm.launchApp(settings.pipAppPackage) },
                                        onTapNowPlaying     = {
                                            val pkg = nowPlaying?.controller?.packageName
                                            if (!pkg.isNullOrEmpty()) vm.launchApp(pkg)
                                        },
                                        onUpdateWidget      = { id, sx, sy -> vm.updateWidgetConfig(id, sx, sy) },
                                        onMoveWidget        = { id, gx, gy -> vm.moveWidgetConfig(id, gx, gy) },
                                        onAddWidget         = { id -> vm.addWidget(id) },
                                        onRemoveWidget      = { id -> vm.removeWidget(id) },
                                        onSetClockStyle     = { style -> vm.updateSettings { copy(clockStyle = style) } },
                                        onSetVitalsAsBars   = { asBars -> vm.updateSettings { copy(vitalsAsBars = asBars) } },
                                        onSetSpeedometerDigitalOnly = { digital -> vm.updateSettings { copy(speedometerDigitalOnly = digital) } },
                                        onUpdateSoundPad    = { idx, pad -> vm.updateSoundboardPad(idx, pad) },
                                        hardwareRadio         = hardwareRadio,
                                        onLaunchHardwareRadio = { vm.launchHardwareRadioApp() },
                                        onStopHardwareRadio   = { vm.stopHardwareRadioApp() },
                                        onRadioSeekUp         = { vm.radioSeekUp() },
                                        onRadioSeekDown       = { vm.radioSeekDown() },
                                        onRadioCycleFm        = { vm.radioCycleFm() },
                                        onRadioSwitchAm       = { vm.radioSwitchAm() },
                                        onRadioTune           = { band, freq -> vm.radioTune(band, freq) },
                                        onAssignRadio         = { vm.startRadioPicker() },
                                        onNavigate            = { dest -> vm.navigate(dest) },
                                        incomingMessage = incomingMessage,
                                        onReadMessage = { MediaListenerService.readIncomingMessage() },
                                        onDismissMessage = { MediaListenerService.dismissIncomingMessage() },
                                        apps = apps,
                                        favoritePhoneNumbers = settings.favoritePhoneNumbers,
                                        onCallPhone = ::placeCall,
                                        onTogglePhoneFavorite = { key ->
                                            vm.updateSettings {
                                                val favorites = favoritePhoneNumbers
                                                copy(favoritePhoneNumbers = if (key in favorites) favorites - key else favorites + key)
                                            }
                                        },
                                        onAssignAppShortcut = vm::startShortcutPicker,
                                        onLaunchAppShortcut = vm::launchApp,
                                        onSetAppShortcutIcon = vm::setShortcutIcon
                                    )

                                    NavDestination.APP_LIBRARY -> AppLibraryScreen(
                                        apps                = apps,
                                        isLoading           = appsLoading,
                                        isPickerMode        = pickerSlot != null,
                                        pickerSlot          = pickerSlot,
                                        isCarPlayPickerMode = appPickerTarget != null,
                                        carPlayPickerLabel  = when (appPickerTarget) {
                                            com.openlauncher.app.viewmodel.LauncherViewModel.AppPickerTarget.ANDROID_AUTO -> "CHOOSE ANDROID AUTO APP"
                                            com.openlauncher.app.viewmodel.LauncherViewModel.AppPickerTarget.PIP          -> "CHOOSE PIP APP"
                                            com.openlauncher.app.viewmodel.LauncherViewModel.AppPickerTarget.RADIO        -> "CHOOSE RADIO APP"
                                            else -> "CHOOSE CARPLAY APP"
                                        },
                                        accent              = accent,
                                        onAppClick          = { app -> vm.launchApp(app.packageName) },
                                        onPickerSelect      = { slot, app -> vm.assignShortcut(slot, app) },
                                        onCarPlaySelect     = { app -> vm.assignPickerApp(app) }
                                    )

                                    NavDestination.SETTINGS -> SettingsScreen(
                                        settings = settings,
                                        accent   = accent,
                                        onUpdate = { block -> vm.updateSettings(block) },
                                        onReset  = { vm.resetSettings() }
                                    )

                                    NavDestination.RADIO -> RadioScreen(
                                        hardwareRadio         = hardwareRadio,
                                        accent                = accent,
                                        onLaunchHardwareRadio = { vm.launchHardwareRadioApp() },
                                        onStopHardwareRadio   = { vm.stopHardwareRadioApp() },
                                        onRadioSeekUp         = { vm.radioSeekUp() },
                                        onRadioSeekDown       = { vm.radioSeekDown() },
                                        onRadioCycleFm        = { vm.radioCycleFm() },
                                        onRadioSwitchAm       = { vm.radioSwitchAm() },
                                        onRadioTune           = { band, freq -> vm.radioTune(band, freq) },
                                        onAssignRadio         = { vm.startRadioPicker() }
                                    )

                                    NavDestination.MEDIA -> MediaScreen(
                                        accent   = accent,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    NavDestination.OFFLINE_MAPS -> OfflineManagerScreen(
                                        accent   = accent,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    NavDestination.NAVIGATION -> NavigationScreen(
                                        apps         = apps,
                                        location     = location,
                                        bearing      = bearing,
                                        settings     = settings,
                                        accent       = accent,
                                        navViewModel = navVm,
                                        onMapThemeModeChange = { mode -> vm.updateSettings { copy(mapThemeMode = mode) } }
                                    )

                                    NavDestination.PHONE -> PhoneScreen(
                                        accent = accent,
                                        onCall = ::placeCall,
                                        favoritePhoneNumbers = settings.favoritePhoneNumbers,
                                        onToggleFavorite = { key ->
                                            vm.updateSettings {
                                                val favorites = favoritePhoneNumbers
                                                copy(favoritePhoneNumbers = if (key in favorites) favorites - key else favorites + key)
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        if (isBottomBar) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                mainPane(Modifier.weight(1f).fillMaxWidth())
                                androidx.compose.material3.HorizontalDivider(color = layoutDivColor)
                                Box(modifier = Modifier.zIndex(10f)) {
                                    sidebarContent()
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxSize()) {
                                val vDivider: @Composable () -> Unit = {
                                    androidx.compose.material3.VerticalDivider(
                                        modifier = Modifier.fillMaxHeight(),
                                        color    = layoutDivColor
                                    )
                                }
                                if (settings.sidebarPosition == SidebarPosition.LEFT) {
                                    Box(modifier = Modifier.zIndex(10f)) {
                                        sidebarContent()
                                    }
                                    vDivider()
                                }
                                mainPane(Modifier.weight(1f).fillMaxHeight())
                                if (settings.sidebarPosition == SidebarPosition.RIGHT) {
                                    vDivider()
                                    Box(modifier = Modifier.zIndex(10f)) {
                                        sidebarContent()
                                    }
                                }
                            }
                        }
                    }
                }
                if (voiceAssistantOpen) {
                    VoiceAssistantDialog(
                        accent = accent,
                        languageTag = settings.appLanguage.tag,
                        onDismiss = { voiceAssistantOpen = false },
                        onCommand = { raw ->
                            val command = normalizeVoiceText(raw)
                            val currentLocation = location
                            val places = navVm.uiState.value.savedPlaces
                            val favoriteAlias = when {
                                listOf("domicile", "maison", "home").any(command::contains) -> listOf("domicile", "maison", "home")
                                listOf("travail", "bureau", "work", "office").any(command::contains) -> listOf("travail", "bureau", "work", "office")
                                else -> emptyList()
                            }
                            val saved = places.firstOrNull { place ->
                                val label = normalizeVoiceText(place.label)
                                favoriteAlias.any { it == label } || (label.length >= 3 && command.contains(label))
                            }
                            when {
                                command == "carte" || command.contains("ouvre la carte") || command == "map" || command.contains("open map") -> { vm.navigate(NavDestination.NAVIGATION); if (settings.appLanguage == AppLanguage.ENGLISH_CANADA) "Opening the map." else "J’ouvre la carte." }
                                command.contains("musique") || command.contains("lecteur audio") || command.contains("music") || command.contains("audio player") -> { vm.navigate(NavDestination.MEDIA); if (settings.appLanguage == AppLanguage.ENGLISH_CANADA) "Opening the music player." else "J’ouvre le lecteur audio." }
                                command == "accueil" || command.contains("ouvre l accueil") || command == "home" || command.contains("open home") -> { vm.navigate(NavDestination.HOME); if (settings.appLanguage == AppLanguage.ENGLISH_CANADA) "Returning home." else "Retour à l’accueil." }
                                command.contains("telephone") || command.contains("appels") || command.contains("phone") || command.contains("calls") -> { vm.navigate(NavDestination.PHONE); if (settings.appLanguage == AppLanguage.ENGLISH_CANADA) "Opening the phone." else "J’ouvre le téléphone." }
                                Regex("^(appelle|appeler|compose|telephone a|call|phone) ").containsMatchIn(command) -> {
                                    val name = command.replace(Regex("^(appelle|appeler|compose|telephone a|call|phone)\\s*"), "").trim()
                                    lifecycleScope.launch {
                                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                                            speech.speak(if (settings.appLanguage == AppLanguage.ENGLISH_CANADA) "Allow Bluetooth contacts on the Phone page before using voice calling." else "Autorisez les contacts Bluetooth dans la page Téléphone avant d’utiliser l’appel vocal.")
                                            vm.navigate(NavDestination.PHONE)
                                        } else {
                                            val matches = withContext(Dispatchers.IO) { BluetoothContactsRepository.load(this@MainActivity) }
                                                .filter { normalizeVoiceText(it.name).contains(name) }
                                            when (matches.size) {
                                                1 -> { speech.speak(if (settings.appLanguage == AppLanguage.ENGLISH_CANADA) "Calling ${matches.first().name}." else "Appel de ${matches.first().name} en cours."); placeCall(matches.first().phoneNumber) }
                                                0 -> speech.speak(if (settings.appLanguage == AppLanguage.ENGLISH_CANADA) "No matching Bluetooth contact for $name." else "Aucun contact Bluetooth correspondant à $name.")
                                                else -> { vm.navigate(NavDestination.PHONE); speech.speak(if (settings.appLanguage == AppLanguage.ENGLISH_CANADA) "More than one contact matches. Choose the right contact on screen." else "Plusieurs contacts correspondent. Choisissez le bon contact à l’écran.") }
                                            }
                                        }
                                    }
                                    if (settings.appLanguage == AppLanguage.ENGLISH_CANADA) "Searching for contact $name." else "Je cherche le contact $name."
                                }
                                saved != null && (command.contains("destination") || command.contains("emmene") || command.contains("va au") || command.contains("va a") || command.contains("take me") || command.contains("navigate to") || command == normalizeVoiceText(saved.label)) -> {
                                    navVm.selectSavedPlace(saved, currentLocation); vm.navigate(NavDestination.NAVIGATION)
                                    if (settings.appLanguage == AppLanguage.ENGLISH_CANADA) "Starting route to ${saved.label}." else "Lancement de l’itinéraire vers ${saved.label}."
                                }
                                else -> {
                                    val query = command.replace(Regex("^(emmene moi|conduis moi|destination|recherche|cherche|trouve|take me|navigate to|search for|find|search)( au| a| vers| to)?\\s*"), "").trim().ifBlank { raw }
                                    vm.navigate(NavDestination.NAVIGATION)
                                    navVm.updateSearchQuery(query, currentLocation)
                                    navVm.onSearchSubmitted(currentLocation)
                                    if (settings.appLanguage == AppLanguage.ENGLISH_CANADA) "Here are the search results for $query." else "Voici les résultats de recherche pour $query."
                                }
                            }.also(speech::speak)
                        }
                    )
                }
                }
            }
            } // CompositionLocalProvider
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshConnectivity()
        vm.refreshMedia()
    }

    override fun onDestroy() {
        speech.close()
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        vm.stopLocationUpdates()
    }

    override fun onStart() {
        super.onStart()
        vm.startLocationUpdates()
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        event?.startTracking()
        if (vm.handleSteeringKey(keyCode, isLongPress = false)) return true
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                vm.playPause()
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                vm.skipNext()
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                vm.skipPrev()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyLongPress(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (vm.handleSteeringKey(keyCode, isLongPress = true)) return true
        return super.onKeyLongPress(keyCode, event)
    }
}
