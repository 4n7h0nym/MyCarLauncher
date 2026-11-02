package com.openlauncher.app.ui.screen

import com.openlauncher.app.ui.components.LocalizedText as LocalizedLabel

import android.Manifest
import android.graphics.Color as AndroidColor
import android.location.Location
import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.common.MapboxOptions
import com.mapbox.maps.Style
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.eq
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.get
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.literal
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerBelow
import com.mapbox.maps.extension.style.layers.generated.FillExtrusionLayer
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.TextAnchor
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.common.location.toCommonLocation
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.openlauncher.app.data.OfflineRouteEngine
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.DayNightMode
import com.openlauncher.app.data.MapThemeMode
import com.openlauncher.app.data.UnitSystem
import com.openlauncher.app.model.*
import com.openlauncher.app.util.LocationData
import com.openlauncher.app.viewmodel.NavigationViewModel
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.android.gestures.MoveGestureDetector
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun NavigationScreen(
    apps: List<AppInfo>,
    location: LocationData?,
    bearing: Float,
    settings: AppSettings,
    accent: Color,
    navViewModel: NavigationViewModel,
    onMapThemeModeChange: (MapThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by navViewModel.uiState.collectAsStateWithLifecycle()
    var mapLoadError by remember { mutableStateOf<String?>(null) }
    val cardBg     = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outline
    val textColor  = MaterialTheme.colorScheme.onBackground
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    val isDayMode = com.openlauncher.app.ui.theme.LocalDayMode.current
    val isMapLightMode = when (settings.mapThemeMode) {
        MapThemeMode.FOLLOW_APP -> isDayMode
        MapThemeMode.LIGHT -> true
        MapThemeMode.DARK -> false
    }
    val focusManager = LocalFocusManager.current
    var searchPanelBounds by remember { mutableStateOf<Rect?>(null) }
    val context = LocalContext.current
    var voiceGuidanceEnabled by rememberSaveable { mutableStateOf(true) }
    var ttsReady by remember { mutableStateOf(false) }
    val localTts = remember(context, settings.appLanguage) {
        val engineRef = java.util.concurrent.atomic.AtomicReference<TextToSpeech?>()
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engineRef.get()?.let { readyEngine ->
                    val canadianFrench = readyEngine.setLanguage(Locale.forLanguageTag(settings.appLanguage.tag))
                    if (canadianFrench == TextToSpeech.LANG_MISSING_DATA || canadianFrench == TextToSpeech.LANG_NOT_SUPPORTED) {
                        val french = readyEngine.setLanguage(Locale.forLanguageTag(settings.appLanguage.tag.substringBefore("-")))
                        ttsReady = french != TextToSpeech.LANG_MISSING_DATA && french != TextToSpeech.LANG_NOT_SUPPORTED
                    } else {
                        ttsReady = true
                    }
                }
            }
        }
        engineRef.set(engine)
        engine
    }
    var spokenManeuverIndex by remember { mutableIntStateOf(-1) }

    DisposableEffect(localTts) {
        onDispose {
            localTts.stop()
            localTts.shutdown()
        }
    }

    // Android's installed speech engine reads the already-computed maneuver text locally.
    // Trigger once when a turn is within roughly 200 m; no Mapbox Voice API is called.
    LaunchedEffect(
        uiState.isNavigating,
        voiceGuidanceEnabled,
        ttsReady,
        uiState.routeStepIndex,
        uiState.nextManeuver?.instruction,
        location?.latitude,
        location?.longitude
    ) {
        val maneuver = uiState.nextManeuver ?: return@LaunchedEffect
        if (!uiState.isNavigating || !voiceGuidanceEnabled || !ttsReady || spokenManeuverIndex == uiState.routeStepIndex) {
            return@LaunchedEffect
        }
        val point = maneuver.maneuverPoint ?: return@LaunchedEffect
        val current = location ?: return@LaunchedEffect
        val distance = FloatArray(1)
        Location.distanceBetween(current.latitude, current.longitude, point.latitude, point.longitude, distance)
        if (distance[0] <= 250f) {
            val distanceText = if (settings.appLanguage == com.openlauncher.app.data.AppLanguage.ENGLISH_CANADA)
                "In ${distance[0].roundToInt()} metres, " else "Dans ${distance[0].roundToInt()} mètres, "
            localTts.speak("$distanceText${maneuver.instruction}", TextToSpeech.QUEUE_FLUSH, null, "maneuver-${uiState.routeStepIndex}")
            spokenManeuverIndex = uiState.routeStepIndex
        }
    }
    LaunchedEffect(uiState.selectedDestination?.id) {
        spokenManeuverIndex = -1
    }

    // Permissions GPS
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    var requestedLocationPermission by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(locationPermissionState.status) {
        navViewModel.onLocationPermissionGranted(locationPermissionState.status.isGranted)
        if (!locationPermissionState.status.isGranted && !requestedLocationPermission) {
            // A denial changes shouldShowRationale; do not immediately reopen the system prompt.
            requestedLocationPermission = true
            locationPermissionState.launchPermissionRequest()
        }
    }

    LaunchedEffect(location) {
        navViewModel.updateCurrentLocation(location)
    }

    var showAddFavoriteDialog by remember { mutableStateOf(false) }
    var editingPlace by remember { mutableStateOf<SavedPlace?>(null) }
    var favoritePoiToAdd by remember { mutableStateOf<MapboxPoiResult?>(null) }

    fun previewPoi(poi: MapboxPoiResult) {
        navViewModel.selectMapPoi(poi)
        navViewModel.dismissBottomSheet()
        navViewModel.updateSearchQuery("", location)
        focusManager.clearFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(uiState.isSearchFocused, searchPanelBounds) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                    val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    if (up != null && uiState.isSearchFocused && searchPanelBounds?.contains(up.position) != true) {
                        focusManager.clearFocus()
                    }
                }
            }
    ) {

        // ── 1. CARTE MAPBOX INTERACTIVE 100% PLEIN ÉCRAN ─────────────────────
        MapboxFullInteractiveMap(
            location = location,
            bearing = bearing,
            destinationPoint = uiState.selectedDestination?.point,
            routePoints = if (uiState.routeProgressIndex in 1 until uiState.routePoints.size)
                uiState.routePoints.drop(uiState.routeProgressIndex) else uiState.routePoints,
            isDayMode = isMapLightMode,
            isSplitSearchOpen = uiState.isSplitSearchOpen,
            triggerRecenter = uiState.triggerRecenter,
            accent = accent,
            savedPlaces = uiState.savedPlaces,
            onMapPoiSelected = { navViewModel.selectMapPoi(it) },
            onMapTapped = { navViewModel.clearMapPoiSelection() },
            onMapLoadError = { mapLoadError = it },
            modifier = Modifier.fillMaxSize()
        )

        mapLoadError?.let { error ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LocalizedLabel("La carte Mapbox n’a pas pu charger son style.", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                    LocalizedLabel(error, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 11.sp, maxLines = 3)
                    LocalizedLabel("Vérifiez la connexion réseau et les cartes hors ligne téléchargées.", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 11.sp)
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.isNavigating,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 24.dp)
        ) {
            FilledTonalIconButton(
                onClick = {
                    voiceGuidanceEnabled = !voiceGuidanceEnabled
                    if (!voiceGuidanceEnabled) localTts.stop()
                },
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (voiceGuidanceEnabled) accent else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    contentColor = if (voiceGuidanceEnabled) Color.White else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    if (voiceGuidanceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = if (voiceGuidanceEnabled) "Désactiver le guidage vocal" else "Activer le guidage vocal",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Compact speed HUD layered over the map so the map remains full-screen.
        val metricUnits = settings.unitSystem == UnitSystem.METRIC
        val speedMps = location?.speedMps?.coerceAtLeast(0f)
        val speedValue = speedMps?.let {
            (it * if (metricUnits) 3.6f else 2.23694f).roundToInt()
        }
        val speedHudText = if (isMapLightMode) Color(0xFF151515) else Color.White
        val speedHudMuted = if (isMapLightMode) Color(0xFF666666) else Color(0xFFBBBBBB)
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 18.dp),
            shape = RoundedCornerShape(18.dp),
            color = if (isMapLightMode) Color.White.copy(alpha = 0.94f) else Color(0xEE111111),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Speed, contentDescription = "Vitesse", tint = accent, modifier = Modifier.size(20.dp))
                LocalizedLabel(
                    text = speedValue?.toString() ?: "—",
                    color = speedHudText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                LocalizedLabel(
                    text = if (metricUnits) "km/h" else "mph",
                    color = speedHudMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        uiState.selectedMapPoi?.let { poi ->
            AnimatedVisibility(
                visible = !uiState.isNavigating,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg.copy(alpha = 0.97f),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.border(1.dp, cardBorder, RoundedCornerShape(18.dp))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            androidx.compose.material3.Text(poi.name, color = textColor, fontWeight = FontWeight.Bold, maxLines = 1)
                            LocalizedLabel(poi.category + if (poi.address.isBlank()) "" else " • ${poi.address}", color = mutedColor, fontSize = 11.sp, maxLines = 1)
                        }
                        TextButton(
                            onClick = {
                                favoritePoiToAdd = poi
                                showAddFavoriteDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            LocalizedLabel("Favori")
                        }
                        IconButton(onClick = { navViewModel.clearMapPoiSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer")
                        }
                        Button(
                            onClick = {
                                navViewModel.selectDestination(poi, location)
                                navViewModel.clearMapPoiSelection()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accent)
                        ) {
                            Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            LocalizedLabel("Itinéraire")
                        }
                    }
                }
            }
        }
        // ── 2. ÉTAT A : MODE RECHERCHE / REPOS (HORS NAVIGATION) ─────────────
        AnimatedVisibility(
            visible = !uiState.isNavigating,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Barre de recherche principale occupant 50% de la largeur
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cardBg.copy(alpha = 0.95f),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .onGloballyPositioned { searchPanelBounds = it.boundsInRoot() }
                        .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { navViewModel.updateSearchQuery(it, location) },
                                placeholder = { LocalizedLabel("Rechercher adresse, McDonald's, station...", color = mutedColor, fontSize = 13.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) navViewModel.clearMapPoiSelection()
                                        navViewModel.onSearchFocused(focusState.isFocused, location)
                                    }
                            )

                            if (uiState.isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = accent, strokeWidth = 2.dp)
                            } else if (uiState.searchQuery.isNotBlank()) {
                                IconButton(onClick = { navViewModel.updateSearchQuery("", location) }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Effacer", tint = mutedColor, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { navViewModel.onSearchSubmitted(location) }) {
                                    Icon(Icons.Default.Directions, contentDescription = "Lancer", tint = accent, modifier = Modifier.size(24.dp))
                                }
                            }
                        }

                        // État B : Suggestions Autofill en temps réel dès la saisie d'un caractère
                        if (uiState.searchQuery.isNotBlank() && uiState.suggestions.isNotEmpty()) {
                            HorizontalDivider(color = cardBorder)
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                uiState.suggestions.take(5).forEach { sug ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { previewPoi(sug) }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Default.Place, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            LocalizedLabel(sug.name, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            LocalizedLabel(sug.address, color = mutedColor, fontSize = 11.sp, maxLines = 1)
                                        }
                                        if (sug.distanceKm > 0f) {
                                            LocalizedLabel("%.1f km".format(sug.distanceKm), color = accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // État A : Panneau contextuel déroulant (3 sections : Récentes, Favoris, Proches) lorsque le champ est vide et actif
                if (uiState.isSearchFocused && uiState.searchQuery.isBlank()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = cardBg.copy(alpha = 0.98f),
                        tonalElevation = 10.dp,
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .heightIn(max = 380.dp)
                            .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Section 1 : Destinations Récentes (Room DB)
                            if (uiState.recentDestinations.isNotEmpty()) {
                                item {
                                    LocalizedLabel("DESTINATIONS RÉCENTES", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                }
                                items(uiState.recentDestinations) { recent ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { previewPoi(recent) }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.History, null, tint = mutedColor, modifier = Modifier.size(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            LocalizedLabel(recent.name, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            LocalizedLabel(recent.address, color = mutedColor, fontSize = 10.sp, maxLines = 1)
                                        }
                                    }
                                }
                                item { HorizontalDivider(color = cardBorder) }
                            }

                            // Section 2 : Destinations Enregistrées / Favoris (Room DB)
                            if (uiState.savedPlaces.isNotEmpty()) {
                                item {
                                    LocalizedLabel("FAVORIS & ENREGISTRÉS", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                }
                                items(uiState.savedPlaces) { place ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { navViewModel.selectSavedPlace(place, location) }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        val ico = when (place.iconName) {
                                            "HOME" -> Icons.Default.Home
                                            "WORK" -> Icons.Default.Work
                                            else   -> Icons.Default.Star
                                        }
                                        Icon(ico, null, tint = accent, modifier = Modifier.size(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            androidx.compose.material3.Text(place.label, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            androidx.compose.material3.Text(place.address, color = mutedColor, fontSize = 10.sp, maxLines = 1)
                                        }
                                    }
                                }
                                item { HorizontalDivider(color = cardBorder) }
                            }

                            // Section 3 : Destinations Proches (POI Mapbox)
                            if (uiState.nearbyPoiSuggestions.isNotEmpty()) {
                                item {
                                    LocalizedLabel("DESTINATIONS PROCHES", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                }
                                items(uiState.nearbyPoiSuggestions) { poi ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { previewPoi(poi) }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.Place, null, tint = accent, modifier = Modifier.size(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            androidx.compose.material3.Text(poi.name, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            androidx.compose.material3.Text(poi.address, color = mutedColor, fontSize = 10.sp, maxLines = 1)
                                        }
                                        if (poi.distanceKm > 0f) {
                                            LocalizedLabel("%.1f km".format(poi.distanceKm), color = mutedColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Raccourcis Lieux Enregistrés (Persistés sur disque avec icônes dynamiques)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    items(uiState.savedPlaces) { place ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cardBg.copy(alpha = 0.92f),
                            shadowElevation = 4.dp,
                            modifier = Modifier.border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable { navViewModel.selectSavedPlace(place, location) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val icon = when (place.iconName) {
                                    "HOME"  -> Icons.Default.Home
                                    "WORK"  -> Icons.Default.Work
                                    "CAR"   -> Icons.Default.DirectionsCar
                                    "GAS"   -> Icons.Default.LocalGasStation
                                    "STORE" -> Icons.Default.Store
                                    "HEART" -> Icons.Default.Favorite
                                    "PIN"   -> Icons.Default.Place
                                    else    -> Icons.Default.Star
                                }
                                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                                androidx.compose.material3.Text(place.label, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                                IconButton(
                                    onClick = { editingPlace = place },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Éditer", tint = mutedColor, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            onClick = { showAddFavoriteDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = accent.copy(alpha = 0.2f),
                            shadowElevation = 2.dp,
                            modifier = Modifier.border(1.dp, accent, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                                LocalizedLabel("Ajouter", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ── 3. ÉTAT B : MODE CONDUITE / NAVIGATION ACTIVE ───────────────────────
        AnimatedVisibility(
            visible = uiState.isNavigating,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bannière de guidage supérieure (Style Android Auto / AAOS avec flèches de voies)
                uiState.nextManeuver?.let { maneuver ->
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF005A49),
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Color(0xFF00796B), RoundedCornerShape(24.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Top row: Lane guidance / Intersection options (Android Auto style)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Alignment.CenterHorizontally.let { Arrangement.Center },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val laneIcons = when {
                                    maneuver.iconType.contains("RIGHT") -> listOf(Icons.Default.ArrowUpward, Icons.Default.TurnRight)
                                    maneuver.iconType.contains("LEFT")  -> listOf(Icons.Default.ArrowUpward, Icons.Default.TurnLeft)
                                    maneuver.iconType.contains("ROUNDABOUT") -> listOf(Icons.Default.Refresh)
                                    else -> listOf(Icons.Default.ArrowUpward, Icons.Default.TurnRight, Icons.Default.TurnLeft)
                                }
                                laneIcons.forEach { icon ->
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(20.dp).padding(horizontal = 4.dp)
                                    )
                                }
                            }

                            // Main row: Icon + Distance + Instruction
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val navIcon = when {
                                        maneuver.iconType.contains("RIGHT") -> Icons.Default.TurnRight
                                        maneuver.iconType.contains("LEFT")  -> Icons.Default.TurnLeft
                                        maneuver.iconType.contains("ROUNDABOUT") -> Icons.Default.Refresh
                                        else -> Icons.Default.Straight
                                    }
                                    Icon(navIcon, contentDescription = null, tint = Color(0xFF005A49), modifier = Modifier.size(26.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    LocalizedLabel(
                                        text = if (maneuver.distanceMeters >= 1000.0) "%.1f km".format(java.util.Locale.US, maneuver.distanceMeters / 1000.0) else "%.0f m".format(java.util.Locale.US, maneuver.distanceMeters),
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    LocalizedLabel(
                                        maneuver.instruction,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Bouton Split-Screen Recherche Secondaire
                Surface(
                    onClick = { navViewModel.toggleSplitSearch() },
                    shape = CircleShape,
                    color = cardBg.copy(alpha = 0.95f),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(52.dp).border(1.dp, accent, CircleShape)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (uiState.isSplitSearchOpen) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Recherche Split-Screen",
                            tint = accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // ── 4. PANNEAU SPLIT-SCREEN RECHERCHE SECONDAIRE ───────────────────────
        AnimatedVisibility(
            visible = uiState.isNavigating && uiState.isSplitSearchOpen,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.5f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardBg.copy(alpha = 0.98f),
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxSize().border(1.dp, cardBorder, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LocalizedLabel("Rechercher sur le trajet", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { navViewModel.toggleSplitSearch() }) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = mutedColor)
                        }
                    }

                    OutlinedTextField(
                        value = uiState.splitSearchQuery,
                        onValueChange = { navViewModel.updateSplitSearchQuery(it, location) },
                        placeholder = { LocalizedLabel("Station-service, péage, parking...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) navViewModel.clearMapPoiSelection() }
                    )

                    HorizontalDivider(color = cardBorder)

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.splitSuggestions) { poi ->
                            Surface(
                                onClick = { navViewModel.selectSplitDestination(poi, location) },
                                shape = RoundedCornerShape(16.dp),
                                color = cardBorder.copy(alpha = 0.2f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        androidx.compose.material3.Text(poi.name, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        androidx.compose.material3.Text(poi.address, color = mutedColor, fontSize = 11.sp, maxLines = 1)
                                    }
                                    LocalizedLabel("%.1f km".format(poi.distanceKm), color = accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 5. CARTE ETA & ARRÊT D'URGENCE (BAS D'ÉCRAN) ────────────────────────
        AnimatedVisibility(
            visible = uiState.isNavigating && (uiState.etaInfo != null || uiState.isOfflineMode),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.6f)
                .padding(16.dp)
        ) {
            Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = cardBg.copy(alpha = 0.95f),
                    shadowElevation = 10.dp,
                    modifier = Modifier.fillMaxWidth().border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            val eta = uiState.etaInfo
                            if (eta == null) {
                                LocalizedLabel("Aucun trajet routier hors ligne pour cette région.", color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                LocalizedLabel("Mettez à jour son téléchargement hors ligne.", color = mutedColor, fontSize = 11.sp)
                            } else {
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                LocalizedLabel(eta.durationText, color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                LocalizedLabel("(${eta.distanceText})", color = mutedColor, fontSize = 14.sp, modifier = Modifier.padding(bottom = 3.dp))
                            }
                            LocalizedLabel("Arrivée estimée : ${eta.etaTimeText}", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            if (uiState.isOfflineMode) {
                                LocalizedLabel("Hors ligne · itinéraire routier téléchargé", color = mutedColor, fontSize = 10.sp)
                            }
                            }
                        }

                        Button(
                            onClick = { navViewModel.stopNavigation() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            LocalizedLabel("Arrêter", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
            }
        }

        // ── 6. BOÎTE À OUTILS DE LA CAMÉRA (RECENTER GPS) ─────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FloatingActionButton(
                onClick = {
                    onMapThemeModeChange(if (isMapLightMode) MapThemeMode.DARK else MapThemeMode.LIGHT)
                },
                containerColor = cardBg,
                contentColor = accent,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    if (isMapLightMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = if (isMapLightMode) "Activer le style sombre de la carte" else "Activer le style clair de la carte",
                    modifier = Modifier.size(24.dp)
                )
            }
            FloatingActionButton(
                onClick = { navViewModel.triggerRecenter() },
                containerColor = cardBg,
                contentColor = accent,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = com.openlauncher.app.ui.components.AppLocalization.translate("Recenter GPS", com.openlauncher.app.ui.components.LocalAppLanguage.current), modifier = Modifier.size(24.dp))
            }
        }

        // ── 7. BOTTOM SHEET RÉSULTATS POI (ÉTAT A) ────────────────────────────
        AnimatedVisibility(
            visible = !uiState.isNavigating && uiState.isBottomSheetVisible && uiState.poiResults.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardBg.copy(alpha = 0.98f),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .heightIn(max = 300.dp)
                    .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        LocalizedLabel("Résultats POI (${uiState.poiResults.size})", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { navViewModel.dismissBottomSheet() }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = mutedColor)
                        }
                    }
                    HorizontalDivider(color = cardBorder)
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(uiState.poiResults) { poi ->
                            Surface(
                                onClick = { previewPoi(poi) },
                                shape = RoundedCornerShape(12.dp),
                                color = cardBorder.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        androidx.compose.material3.Text(poi.name, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        LocalizedLabel("${poi.category} • ${poi.address}", color = mutedColor, fontSize = 11.sp, maxLines = 1)
                                    }
                                    LocalizedLabel("%.1f km".format(poi.distanceKm), color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogue de modification d'un lieu enregistré
    editingPlace?.let { place ->
        var editLabel by remember(place) { mutableStateOf(place.label) }
        var editAddress by remember(place) { mutableStateOf(place.address) }
        var editIconName by remember(place) { mutableStateOf(place.iconName) }
        var selectedPoint by remember(place) { mutableStateOf<MapboxLocationPoint?>(place.point) }
        var editSuggestions by remember { mutableStateOf<List<MapboxPoiResult>>(emptyList()) }
        var isSearchingSuggestions by remember { mutableStateOf(false) }

        val availableIcons = listOf("HOME", "WORK", "STAR", "CAR", "GAS", "STORE", "HEART", "PIN")

        LaunchedEffect(editAddress) {
            if (editAddress.trim().length >= 2 && editAddress != place.address) {
                delay(300)
                isSearchingSuggestions = true
                editSuggestions = navViewModel.getAutofillSuggestions(editAddress, location)
                isSearchingSuggestions = false
            } else {
                editSuggestions = emptyList()
            }
        }

        AlertDialog(
            onDismissRequest = { editingPlace = null },
            title = { LocalizedLabel("Modifier le lieu enregistré", color = textColor) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = editLabel,
                        onValueChange = { editLabel = it },
                        label = { LocalizedLabel("Nom du lieu (ex: Domicile, Travail)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editAddress,
                        onValueChange = {
                            editAddress = it
                            selectedPoint = null
                        },
                        label = { LocalizedLabel("Adresse avec Autofill Mapbox") },
                        singleLine = true,
                        trailingIcon = {
                            if (isSearchingSuggestions) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    LocalizedLabel("Icône du bouton :", color = mutedColor, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(availableIcons) { ico ->
                            val isSelected = editIconName == ico
                            val icoVector = when (ico) {
                                "HOME"  -> Icons.Default.Home
                                "WORK"  -> Icons.Default.Work
                                "CAR"   -> Icons.Default.DirectionsCar
                                "GAS"   -> Icons.Default.LocalGasStation
                                "STORE" -> Icons.Default.Store
                                "HEART" -> Icons.Default.Favorite
                                "PIN"   -> Icons.Default.Place
                                else    -> Icons.Default.Star
                            }
                            Surface(
                                onClick = { editIconName = ico },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) accent.copy(alpha = 0.3f) else cardBorder.copy(alpha = 0.2f),
                                modifier = Modifier.size(40.dp).border(1.dp, if (isSelected) accent else cardBorder, RoundedCornerShape(10.dp))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(icoVector, contentDescription = null, tint = if (isSelected) accent else textColor, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    if (editSuggestions.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cardBorder.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp)
                        ) {
                            LazyColumn(modifier = Modifier.padding(4.dp)) {
                                items(editSuggestions) { sug ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                editAddress = sug.address
                                                selectedPoint = sug.point
                                                editSuggestions = emptyList()
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Place, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            LocalizedLabel(sug.name, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            LocalizedLabel(sug.address, color = mutedColor, fontSize = 10.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editLabel.isNotBlank() && editAddress.isNotBlank()) {
                            navViewModel.editSavedPlace(place.id, editLabel, editAddress, selectedPoint, editIconName)
                            editingPlace = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    LocalizedLabel("Enregistrer")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        navViewModel.removeSavedPlace(place.id)
                        editingPlace = null
                    }) {
                        LocalizedLabel("Supprimer", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { editingPlace = null }) {
                        LocalizedLabel("Annuler")
                    }
                }
            }
        )
    }

    // Dialogue d'ajout de lieu favori
    if (showAddFavoriteDialog) {
        val selectedPoi = favoritePoiToAdd
        var favName by remember(selectedPoi) { mutableStateOf(selectedPoi?.name.orEmpty()) }
        var favAddress by remember(selectedPoi) { mutableStateOf(selectedPoi?.address.orEmpty()) }
        var favIconName by remember(selectedPoi) {
            mutableStateOf(
                when {
                    selectedPoi?.category?.contains("station", ignoreCase = true) == true -> "GAS"
                    selectedPoi?.category?.contains("magasin", ignoreCase = true) == true -> "STORE"
                    selectedPoi?.category?.contains("restaurant", ignoreCase = true) == true -> "HEART"
                    else -> "STAR"
                }
            )
        }
        var selectedFavPoint by remember(selectedPoi) { mutableStateOf<MapboxLocationPoint?>(selectedPoi?.point) }
        var favSuggestions by remember { mutableStateOf<List<MapboxPoiResult>>(emptyList()) }
        var isSearchingFavSuggestions by remember { mutableStateOf(false) }

        val availableIcons = listOf("HOME", "WORK", "STAR", "CAR", "GAS", "STORE", "HEART", "PIN")

        LaunchedEffect(favAddress) {
            if (favAddress.trim().length >= 2 && favAddress != selectedPoi?.address) {
                delay(300)
                isSearchingFavSuggestions = true
                favSuggestions = navViewModel.getAutofillSuggestions(favAddress, location)
                isSearchingFavSuggestions = false
            } else {
                favSuggestions = emptyList()
            }
        }

        AlertDialog(
            onDismissRequest = {
                showAddFavoriteDialog = false
                favoritePoiToAdd = null
            },
            title = { LocalizedLabel("Ajouter un Favori", color = textColor) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = favName,
                        onValueChange = { favName = it },
                        label = { LocalizedLabel("Nom (ex: Salle de sport)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = favAddress,
                        onValueChange = {
                            favAddress = it
                            selectedFavPoint = null
                        },
                        label = { LocalizedLabel("Adresse avec Autofill Mapbox") },
                        singleLine = true,
                        trailingIcon = {
                            if (isSearchingFavSuggestions) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    LocalizedLabel("Icône du bouton :", color = mutedColor, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(availableIcons) { ico ->
                            val isSelected = favIconName == ico
                            val icoVector = when (ico) {
                                "HOME"  -> Icons.Default.Home
                                "WORK"  -> Icons.Default.Work
                                "CAR"   -> Icons.Default.DirectionsCar
                                "GAS"   -> Icons.Default.LocalGasStation
                                "STORE" -> Icons.Default.Store
                                "HEART" -> Icons.Default.Favorite
                                "PIN"   -> Icons.Default.Place
                                else    -> Icons.Default.Star
                            }
                            Surface(
                                onClick = { favIconName = ico },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) accent.copy(alpha = 0.3f) else cardBorder.copy(alpha = 0.2f),
                                modifier = Modifier.size(40.dp).border(1.dp, if (isSelected) accent else cardBorder, RoundedCornerShape(10.dp))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(icoVector, contentDescription = null, tint = if (isSelected) accent else textColor, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    if (favSuggestions.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cardBorder.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp)
                        ) {
                            LazyColumn(modifier = Modifier.padding(4.dp)) {
                                items(favSuggestions) { sug ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                favAddress = sug.address
                                                selectedFavPoint = sug.point
                                                favSuggestions = emptyList()
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Place, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            LocalizedLabel(sug.name, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            LocalizedLabel(sug.address, color = mutedColor, fontSize = 10.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (favName.isNotBlank() && (favAddress.isNotBlank() || selectedFavPoint != null)) {
                            navViewModel.addSavedPlace(favName, favAddress, favIconName, selectedFavPoint)
                            showAddFavoriteDialog = false
                            favoritePoiToAdd = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    LocalizedLabel("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddFavoriteDialog = false
                    favoritePoiToAdd = null
                }) {
                    LocalizedLabel("Annuler")
                }
            }
        )
    }
}

@Composable
private fun MapboxFullInteractiveMap(
    location: LocationData?,
    bearing: Float,
    destinationPoint: MapboxLocationPoint?,
    routePoints: List<MapboxLocationPoint>,
    isDayMode: Boolean,
    isSplitSearchOpen: Boolean,
    triggerRecenter: Long,
    accent: Color,
    savedPlaces: List<SavedPlace>,
    onMapPoiSelected: (MapboxPoiResult) -> Unit,
    onMapTapped: () -> Unit,
    onMapLoadError: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFollowingGps by remember { mutableStateOf(true) }
    var viewportDataSource by remember { mutableStateOf<MapboxNavigationViewportDataSource?>(null) }
    var navigationCamera by remember { mutableStateOf<NavigationCamera?>(null) }
    val navigationLocationProvider = remember { NavigationLocationProvider() }
    val activeNavigationRoute by OfflineRouteEngine.activeRoute.collectAsState()

    // Managers d'annotations uniques et persistants
    var polylineManager by remember { mutableStateOf<PolylineAnnotationManager?>(null) }
    var pointManager by remember { mutableStateOf<PointAnnotationManager?>(null) }
    var savedPlaceManager by remember { mutableStateOf<PointAnnotationManager?>(null) }
    var mapErrorSubscription by remember { mutableStateOf<com.mapbox.common.Cancelable?>(null) }
    val latestSavedPlaces = rememberUpdatedState(savedPlaces)
    val latestPoiCallback = rememberUpdatedState(onMapPoiSelected)
    val latestMapTappedCallback = rememberUpdatedState(onMapTapped)
    val latestMapErrorCallback = rememberUpdatedState(onMapLoadError)

    val context = LocalContext.current
    val hostingLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val mapLifecycleOwner = remember { MapCompositionLifecycleOwner() }
    val mapView = remember {
        // The Maps SDK needs its access token configured before MapView is created.
        // Reuse the existing Gradle-provided resource; do not duplicate the token.
        MapboxOptions.accessToken = context.getString(com.openlauncher.app.R.string.mapbox_access_token)
        MapView(context).apply {
            setViewTreeLifecycleOwner(mapLifecycleOwner)
            this.location.setLocationProvider(navigationLocationProvider)
            mapErrorSubscription = mapboxMap.subscribeMapLoadingError { error ->
                latestMapErrorCallback.value("${error.type}: ${error.message}")
            }
            gestures.addOnMoveListener(object : OnMoveListener {
                override fun onMoveBegin(detector: MoveGestureDetector) {
                    isFollowingGps = false
                }
                override fun onMove(detector: MoveGestureDetector): Boolean = false
                override fun onMoveEnd(detector: MoveGestureDetector) {}
            })
            val annotationPlugin = annotations
            polylineManager = annotationPlugin.createPolylineAnnotationManager()
            pointManager = annotationPlugin.createPointAnnotationManager()
            savedPlaceManager = annotationPlugin.createPointAnnotationManager()
            gestures.addOnMapClickListener { clickedPoint ->
                latestMapTappedCallback.value()
                val map = mapboxMap
                val clickedPixel = map.pixelForCoordinate(clickedPoint)
                val nearbySaved = latestSavedPlaces.value.firstOrNull { saved ->
                    val point = saved.point ?: return@firstOrNull false
                    val pixel = map.pixelForCoordinate(Point.fromLngLat(point.longitude, point.latitude))
                    kotlin.math.abs(pixel.x - clickedPixel.x) < 44.0 && kotlin.math.abs(pixel.y - clickedPixel.y) < 44.0
                }
                if (nearbySaved != null) {
                    nearbySaved.point?.let { point ->
                        latestPoiCallback.value(MapboxPoiResult(nearbySaved.id, nearbySaved.label, "Lieu personnalisé", nearbySaved.address, point))
                    }
                } else {
                    map.queryRenderedFeatures(
                        RenderedQueryGeometry(clickedPixel),
                        RenderedQueryOptions(listOf("offline-poi-labels"), null)
                    ) { result ->
                        val feature = result.value?.firstOrNull()?.queriedFeature?.feature ?: return@queryRenderedFeatures
                        val point = feature.geometry() as? Point ?: return@queryRenderedFeatures
                        val name = feature.getStringProperty("name") ?: return@queryRenderedFeatures
                        val rawCategory = feature.getStringProperty("class") ?: feature.getStringProperty("type") ?: "point_of_interest"
                        val category = when (rawCategory.lowercase()) {
                            "food_and_drink", "food_and_drink_stores" -> "Restaurant et alimentation"
                            "motorist" -> "Station-service"
                            "store_like" -> "Magasin"
                            else -> rawCategory.replace("_", " ").replaceFirstChar { it.uppercase() }
                        }
                        latestPoiCallback.value(MapboxPoiResult("${name}_${point.longitude()}_${point.latitude()}", name, category, "", MapboxLocationPoint(point.latitude(), point.longitude())))
                    }
                }
                true
            }
        }
    }

    DisposableEffect(mapView, hostingLifecycle) {
        // The SDK observes this per-composition owner, including disposal before Activity destruction.
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            mapLifecycleOwner.registry.currentState = hostingLifecycle.currentState
        }
        hostingLifecycle.addObserver(observer)
        mapLifecycleOwner.registry.currentState = hostingLifecycle.currentState
        OfflineRouteEngine.attachMap(navigationLocationProvider) { matcherResult ->
            viewportDataSource?.onLocationChanged(matcherResult.enhancedLocation)
            viewportDataSource?.evaluate()
            if (isFollowingGps) navigationCamera?.requestNavigationCameraToFollowing()
        }
        onDispose {
            OfflineRouteEngine.detachMap(navigationLocationProvider)
            mapErrorSubscription?.cancel()
            hostingLifecycle.removeObserver(observer)
            mapLifecycleOwner.registry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
        }
    }

    LaunchedEffect(location, routePoints.isNotEmpty()) {
        // Before a trip session starts, keep the normal GPS dot alive through the same provider.
        // During guidance, the SDK's matched locations replace these raw fixes.
        if (routePoints.isEmpty() && location != null) {
            val rawFix = Location("launcher").apply {
                latitude = location.latitude
                longitude = location.longitude
                accuracy = location.accuracy
                speed = location.speedMps
                setBearing(bearing)
                time = System.currentTimeMillis()
            }
            navigationLocationProvider.changePosition(rawFix.toCommonLocation())
        }
    }

    LaunchedEffect(activeNavigationRoute, viewportDataSource) {
        activeNavigationRoute?.let { route ->
            viewportDataSource?.onRouteChanged(route)
            viewportDataSource?.evaluate()
            navigationCamera?.requestNavigationCameraToFollowing()
        }
    }

    // Mettre à jour le style si le mode jour/nuit change
    LaunchedEffect(isDayMode) {
        onMapLoadError(null)
        val mapboxStyle = if (isDayMode) Style.MAPBOX_STREETS else Style.DARK
        mapView.mapboxMap.loadStyle(mapboxStyle) { style ->
            onMapLoadError(null)
            // The Streets vector source includes building footprints and
            // heights. Extrude them at city zooms, while keeping road labels
            // above the buildings for a readable driving map.
            if (style.styleSourceExists("composite") && !style.styleLayerExists("navigation-3d-buildings")) {
                val buildings = FillExtrusionLayer("navigation-3d-buildings", "composite").apply {
                    sourceLayer("building")
                    filter(eq(get("extrude"), literal("true")))
                    minZoom(15.0)
                    fillExtrusionColor(AndroidColor.parseColor(if (isDayMode) "#B8C6D2" else "#526170"))
                    fillExtrusionHeight(get("height"))
                    fillExtrusionBase(get("min_height"))
                    fillExtrusionOpacity(0.88)
                }
                if (style.styleLayerExists("road-label")) style.addLayerBelow(buildings, "road-label")
                else style.addLayer(buildings)
            }
            // Add explicit labels from Mapbox Streets vector tiles. The offline tile packs
            // contain these same tiles, so the labels work online and offline.
            if (style.styleSourceExists("composite") && !style.styleLayerExists("offline-poi-labels")) {
                style.addLayer(
                    SymbolLayer("offline-poi-labels", "composite")
                        .sourceLayer("poi_label")
                        .minZoom(12.0)
                        .iconImage(Expression.get("maki"))
                        .iconSize(0.9)
                        .iconAllowOverlap(true)
                        .textField(Expression.get("name"))
                        .textAnchor(TextAnchor.LEFT)
                        .textOffset(listOf(1.0, 0.0))
                        .textSize(11.0)
                        .textColor(if (isDayMode) "#263238" else "#F5F5F5")
                        .textHaloColor(if (isDayMode) "#FFFFFF" else "#202124")
                        .textHaloWidth(1.2)
                )
            }
            mapView.location.updateSettings {
                enabled = true
                pulsingEnabled = routePoints.isEmpty()
            }
        }
    }

    LaunchedEffect(mapView) {
        val source = MapboxNavigationViewportDataSource(mapView.mapboxMap)
        val density = context.resources.displayMetrics.density
        source.followingPadding = EdgeInsets(
            175.0 * density,
            36.0 * density,
            150.0 * density,
            36.0 * density
        )
        viewportDataSource = source
        navigationCamera = NavigationCamera(mapView.mapboxMap, mapView.camera, source)
    }

    // In guidance, use a course-oriented arrow like a turn-by-turn map. In map
    // browsing, retain the familiar pulsing blue location dot.
    LaunchedEffect(routePoints.isNotEmpty()) {
        mapView.location.apply {
            puckBearing = PuckBearing.COURSE
            updateSettings {
                enabled = true
                puckBearingEnabled = routePoints.isNotEmpty()
                pulsingEnabled = routePoints.isEmpty()
                locationPuck = createDefault2DPuck(withBearing = routePoints.isNotEmpty())
            }
        }
    }

    // Lorsque l'utilisateur clique sur le bouton Recenter, on réactive immédiatement le suivi GPS
    LaunchedEffect(triggerRecenter, navigationCamera, viewportDataSource) {
        if (triggerRecenter > 0L) {
            isFollowingGps = true
            val matchedLocation = navigationLocationProvider.lastLocation
            if (routePoints.size > 1 && matchedLocation != null) {
                viewportDataSource?.onLocationChanged(matchedLocation)
                viewportDataSource?.evaluate()
                val guidancePadding = EdgeInsets(mapView.height * 0.38, 0.0, 0.0, 0.0)
                mapView.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(matchedLocation.longitude, matchedLocation.latitude))
                        .zoom(17.2)
                        .bearing(matchedLocation.bearing ?: bearing.toDouble())
                        .pitch(55.0)
                        .padding(guidancePadding)
                        .build()
                )
            } else if (location != null) {
                mapView.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(location.longitude, location.latitude))
                        .zoom(if (routePoints.size > 1) 17.2 else 15.5)
                        .bearing(if (routePoints.size > 1) bearing.toDouble() else 0.0)
                        .pitch(if (routePoints.size > 1) 55.0 else 0.0)
                        .build()
                )
            }
            if (routePoints.size > 1) navigationCamera?.requestNavigationCameraToFollowing()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { mv ->
            val mapboxMap = mv.mapboxMap

            val leftPadding = if (isSplitSearchOpen) mv.width / 2.0 else 0.0
            val isGuidanceActive = routePoints.size > 1
            // Top padding shifts the followed location down on screen, leaving
            // more room to see the road ahead, as in dedicated navigation apps.
            val topPadding = if (isGuidanceActive) mv.height * 0.38 else 0.0
            val edgeInsets = EdgeInsets(topPadding, leftPadding, 0.0, 0.0)

            // 1. Suivi GPS en temps réel & Orientation vers le haut (Course-Up avec Pitch 3D)
            if (!isGuidanceActive && location != null && (isFollowingGps || triggerRecenter > 0L)) {
                val zoomLevel = if (isGuidanceActive) 17.2 else 15.5
                val cameraOptions = CameraOptions.Builder()
                    .center(Point.fromLngLat(location.longitude, location.latitude))
                    .zoom(zoomLevel)
                    .bearing(if (isGuidanceActive) bearing.toDouble() else 0.0)
                    .pitch(if (isGuidanceActive) 55.0 else 0.0)
                    .padding(edgeInsets)
                    .build()
                mapboxMap.setCamera(cameraOptions)
            }

            // 2. Purge et rendu propre du tracé Polyline
            polylineManager?.let { pm ->
                pm.deleteAll()
                if (routePoints.isNotEmpty()) {
                    val points = routePoints.map { Point.fromLngLat(it.longitude, it.latitude) }
                    pm.create(
                        PolylineAnnotationOptions()
                            .withPoints(points)
                            .withLineColor(if (isDayMode) "#FFFFFF" else "#171A1F")
                            .withLineWidth(10.0)
                    )
                    val polylineOptions = PolylineAnnotationOptions()
                        .withPoints(points)
                        .withLineColor("#4285F4")
                        .withLineWidth(7.0)
                    pm.create(polylineOptions)
                }
            }

            // Render saved custom places with the icon selected by the user and their label.
            savedPlaceManager?.let { manager ->
                manager.deleteAll()
                savedPlaces.forEach { saved ->
                    saved.point?.let { savedPoint ->
                        manager.create(
                            PointAnnotationOptions()
                                .withPoint(Point.fromLngLat(savedPoint.longitude, savedPoint.latitude))
                                .withTextField("${savedPlaceMapGlyph(saved.iconName)} ${saved.label}")
                                .withTextColor(if (isDayMode) "#536B78" else "#F5F5F5")
                                .withTextHaloColor(if (isDayMode) "#FFFFFF" else "#202124")
                                .withTextHaloWidth(1.5)
                                .withTextSize(13.0)
                        )
                    }
                }
            }
            // 3. Purge et rendu propre du marqueur Destination Pin
            pointManager?.let { ptm ->
                ptm.deleteAll()
                destinationPoint?.let { dest ->
                    val destPoint = Point.fromLngLat(dest.longitude, dest.latitude)
                    val pointOptions = PointAnnotationOptions()
                        .withPoint(destPoint)
                        .withTextField("Destination")
                        .withTextColor("#FF3D00")
                        .withTextSize(12.0)
                    ptm.create(pointOptions)
                }
            }
        }
    )
}

private fun savedPlaceMapGlyph(iconName: String): String = when (iconName.uppercase()) {
    "HOME" -> "🏠"
    "WORK" -> "💼"
    "CAR" -> "🚗"
    "GAS" -> "⛽"
    "STORE" -> "🏬"
    "HEART" -> "♥"
    "PIN" -> "📍"
    else -> "★"
}

private class MapCompositionLifecycleOwner : androidx.lifecycle.LifecycleOwner {
    val registry = androidx.lifecycle.LifecycleRegistry(this)
    override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
}
