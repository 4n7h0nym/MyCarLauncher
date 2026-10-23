package com.openlauncher.app.ui.screen

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.DayNightMode
import com.openlauncher.app.model.*
import com.openlauncher.app.util.LocationData
import com.openlauncher.app.viewmodel.NavigationViewModel
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.android.gestures.MoveGestureDetector
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun NavigationScreen(
    apps: List<AppInfo>,
    location: LocationData?,
    bearing: Float,
    settings: AppSettings,
    accent: Color,
    navViewModel: NavigationViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by navViewModel.uiState.collectAsState()
    val cardBg     = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outline
    val textColor  = MaterialTheme.colorScheme.onBackground
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    val isDayMode = settings.dayNightMode == DayNightMode.LIGHT

    // Permissions GPS
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    LaunchedEffect(locationPermissionState.status) {
        navViewModel.onLocationPermissionGranted(locationPermissionState.status.isGranted)
        if (!locationPermissionState.status.isGranted) {
            locationPermissionState.launchPermissionRequest()
        }
    }

    LaunchedEffect(location) {
        navViewModel.updateCurrentLocation(location)
    }

    var showAddFavoriteDialog by remember { mutableStateOf(false) }
    var editingPlace by remember { mutableStateOf<SavedPlace?>(null) }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // ── 1. CARTE MAPBOX INTERACTIVE 100% PLEIN ÉCRAN ─────────────────────
        MapboxFullInteractiveMap(
            location = location,
            bearing = bearing,
            destinationPoint = uiState.selectedDestination?.point,
            routePoints = uiState.routePoints,
            isDayMode = isDayMode,
            isSplitSearchOpen = uiState.isSplitSearchOpen,
            triggerRecenter = uiState.triggerRecenter,
            accent = accent,
            modifier = Modifier.fillMaxSize()
        )

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
                                placeholder = { Text("Rechercher adresse, McDonald's, station...", color = mutedColor, fontSize = 13.sp) },
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
                                            .clickable { navViewModel.selectDestination(sug, location) }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Default.Place, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(sug.name, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text(sug.address, color = mutedColor, fontSize = 11.sp, maxLines = 1)
                                        }
                                        if (sug.distanceKm > 0f) {
                                            Text("%.1f km".format(sug.distanceKm), color = accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
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
                                    Text("DESTINATIONS RÉCENTES", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                }
                                items(uiState.recentDestinations) { recent ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { navViewModel.selectDestination(recent, location) }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.History, null, tint = mutedColor, modifier = Modifier.size(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(recent.name, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(recent.address, color = mutedColor, fontSize = 10.sp, maxLines = 1)
                                        }
                                    }
                                }
                                item { HorizontalDivider(color = cardBorder) }
                            }

                            // Section 2 : Destinations Enregistrées / Favoris (Room DB)
                            if (uiState.savedPlaces.isNotEmpty()) {
                                item {
                                    Text("FAVORIS & ENREGISTRÉS", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
                                            Text(place.label, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(place.address, color = mutedColor, fontSize = 10.sp, maxLines = 1)
                                        }
                                    }
                                }
                                item { HorizontalDivider(color = cardBorder) }
                            }

                            // Section 3 : Destinations Proches (POI Mapbox)
                            if (uiState.nearbyPoiSuggestions.isNotEmpty()) {
                                item {
                                    Text("DESTINATIONS PROCHES", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                }
                                items(uiState.nearbyPoiSuggestions) { poi ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { navViewModel.selectDestination(poi, location) }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.Place, null, tint = accent, modifier = Modifier.size(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(poi.name, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(poi.address, color = mutedColor, fontSize = 10.sp, maxLines = 1)
                                        }
                                        if (poi.distanceKm > 0f) {
                                            Text("%.1f km".format(poi.distanceKm), color = mutedColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
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
                                Text(place.label, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)

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
                                Text("Ajouter", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                // Bannière de guidage supérieure
                uiState.nextManeuver?.let { maneuver ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = cardBg.copy(alpha = 0.95f),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, accent, RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(accent),
                                contentAlignment = Alignment.Center
                            ) {
                                val navIcon = when {
                                    maneuver.iconType.contains("RIGHT") -> Icons.Default.TurnRight
                                    maneuver.iconType.contains("LEFT")  -> Icons.Default.TurnLeft
                                    else -> Icons.Default.Straight
                                }
                                Icon(navIcon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                            }
                            Column {
                                Text(maneuver.instruction, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Itinéraire prioritaire le plus rapide • Mapbox Navigation", color = mutedColor, fontSize = 11.sp)
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
                        Text("Rechercher sur le trajet", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { navViewModel.toggleSplitSearch() }) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = mutedColor)
                        }
                    }

                    OutlinedTextField(
                        value = uiState.splitSearchQuery,
                        onValueChange = { navViewModel.updateSplitSearchQuery(it, location) },
                        placeholder = { Text("Station-service, péage, parking...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
                                        Text(poi.name, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(poi.address, color = mutedColor, fontSize = 11.sp, maxLines = 1)
                                    }
                                    Text("%.1f km".format(poi.distanceKm), color = accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 5. CARTE ETA & ARRÊT D'URGENCE (BAS D'ÉCRAN) ────────────────────────
        AnimatedVisibility(
            visible = uiState.isNavigating && uiState.etaInfo != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.6f)
                .padding(16.dp)
        ) {
            uiState.etaInfo?.let { eta ->
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
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(eta.durationText, color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                Text("(${eta.distanceText})", color = mutedColor, fontSize = 14.sp, modifier = Modifier.padding(bottom = 3.dp))
                            }
                            Text("Arrivée estimée : ${eta.etaTimeText}", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = { navViewModel.stopNavigation() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Arrêter", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── 6. BOÎTE À OUTILS DE LA CAMÉRA (RECENTER GPS) ─────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            FloatingActionButton(
                onClick = { navViewModel.triggerRecenter() },
                containerColor = cardBg,
                contentColor = accent,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Recenter GPS", modifier = Modifier.size(24.dp))
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
                        Text("Résultats POI (${uiState.poiResults.size})", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { navViewModel.dismissBottomSheet() }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = mutedColor)
                        }
                    }
                    HorizontalDivider(color = cardBorder)
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(uiState.poiResults) { poi ->
                            Surface(
                                onClick = { navViewModel.selectDestination(poi, location) },
                                shape = RoundedCornerShape(12.dp),
                                color = cardBorder.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(poi.name, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("${poi.category} • ${poi.address}", color = mutedColor, fontSize = 11.sp, maxLines = 1)
                                    }
                                    Text("%.1f km".format(poi.distanceKm), color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            title = { Text("Modifier le lieu enregistré", color = textColor) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = editLabel,
                        onValueChange = { editLabel = it },
                        label = { Text("Nom du lieu (ex: Domicile, Travail)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editAddress,
                        onValueChange = {
                            editAddress = it
                            selectedPoint = null
                        },
                        label = { Text("Adresse avec Autofill Mapbox") },
                        singleLine = true,
                        trailingIcon = {
                            if (isSearchingSuggestions) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Icône du bouton :", color = mutedColor, fontSize = 12.sp)
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
                                            Text(sug.name, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(sug.address, color = mutedColor, fontSize = 10.sp, maxLines = 1)
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
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        navViewModel.removeSavedPlace(place.id)
                        editingPlace = null
                    }) {
                        Text("Supprimer", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { editingPlace = null }) {
                        Text("Annuler")
                    }
                }
            }
        )
    }

    // Dialogue d'ajout de lieu favori
    if (showAddFavoriteDialog) {
        var favName by remember { mutableStateOf("") }
        var favAddress by remember { mutableStateOf("") }
        var favIconName by remember { mutableStateOf("STAR") }
        var selectedFavPoint by remember { mutableStateOf<MapboxLocationPoint?>(null) }
        var favSuggestions by remember { mutableStateOf<List<MapboxPoiResult>>(emptyList()) }
        var isSearchingFavSuggestions by remember { mutableStateOf(false) }

        val availableIcons = listOf("HOME", "WORK", "STAR", "CAR", "GAS", "STORE", "HEART", "PIN")

        LaunchedEffect(favAddress) {
            if (favAddress.trim().length >= 2) {
                delay(300)
                isSearchingFavSuggestions = true
                favSuggestions = navViewModel.getAutofillSuggestions(favAddress, location)
                isSearchingFavSuggestions = false
            } else {
                favSuggestions = emptyList()
            }
        }

        AlertDialog(
            onDismissRequest = { showAddFavoriteDialog = false },
            title = { Text("Ajouter un Favori", color = textColor) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = favName,
                        onValueChange = { favName = it },
                        label = { Text("Nom (ex: Salle de sport)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = favAddress,
                        onValueChange = {
                            favAddress = it
                            selectedFavPoint = null
                        },
                        label = { Text("Adresse avec Autofill Mapbox") },
                        singleLine = true,
                        trailingIcon = {
                            if (isSearchingFavSuggestions) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Icône du bouton :", color = mutedColor, fontSize = 12.sp)
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
                                            Text(sug.name, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(sug.address, color = mutedColor, fontSize = 10.sp, maxLines = 1)
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
                        if (favName.isNotBlank() && favAddress.isNotBlank()) {
                            navViewModel.addSavedPlace(favName, favAddress, favIconName, selectedFavPoint)
                            showAddFavoriteDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFavoriteDialog = false }) {
                    Text("Annuler")
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
    modifier: Modifier = Modifier
) {
    var isFollowingGps by remember { mutableStateOf(true) }

    // Managers d'annotations uniques et persistants
    var polylineManager by remember { mutableStateOf<PolylineAnnotationManager?>(null) }
    var pointManager by remember { mutableStateOf<PointAnnotationManager?>(null) }

    // Lorsque l'utilisateur clique sur le bouton Recenter, on réactive immédiatement le suivi GPS
    LaunchedEffect(triggerRecenter) {
        if (triggerRecenter > 0L) {
            isFollowingGps = true
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                val mapboxStyle = if (isDayMode) Style.MAPBOX_STREETS else Style.DARK
                mapboxMap.loadStyle(mapboxStyle) {
                    this@apply.location.updateSettings {
                        enabled = true
                        pulsingEnabled = true
                    }
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
            }
        },
        update = { mapView ->
            val mapboxMap = mapView.mapboxMap

            val leftPadding = if (isSplitSearchOpen) mapView.width / 2.0 else 0.0
            val edgeInsets = EdgeInsets(0.0, leftPadding, 0.0, 0.0)

            // 1. Suivi GPS en temps réel & Orientation vers le haut (Course-Up avec Pitch 3D)
            if (location != null && (isFollowingGps || triggerRecenter > 0L)) {
                val zoomLevel = if (routePoints.isNotEmpty()) 16.5 else 15.5
                val cameraOptions = CameraOptions.Builder()
                    .center(Point.fromLngLat(location.longitude, location.latitude))
                    .zoom(zoomLevel)
                    .bearing(bearing.toDouble()) // La rotation suit le cap pour orienter le tracé vers le HAUT
                    .pitch(40.0)                  // Inclinaison 3D pour la vue de conduite
                    .padding(edgeInsets)
                    .build()
                mapboxMap.setCamera(cameraOptions)
            }

            // 2. Purge et rendu propre du tracé Polyline
            polylineManager?.let { pm ->
                pm.deleteAll()
                if (routePoints.isNotEmpty()) {
                    val points = routePoints.map { Point.fromLngLat(it.longitude, it.latitude) }
                    val polylineOptions = PolylineAnnotationOptions()
                        .withPoints(points)
                        .withLineColor("#0080FF")
                        .withLineWidth(6.0)
                    pm.create(polylineOptions)
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
