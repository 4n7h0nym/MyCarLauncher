package com.openlauncher.app.viewmodel

import android.app.Application
import android.location.Geocoder
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.common.MapboxOptions
import com.mapbox.common.TileStore
import com.mapbox.maps.mapsOptions
import java.io.File
import com.openlauncher.app.R
import com.openlauncher.app.data.room.NavigationRepository
import com.openlauncher.app.model.*
import com.openlauncher.app.util.LocationData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val navRepository = NavigationRepository(application)
    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    private var searchDebounceJob: Job? = null
    private var splitSearchDebounceJob: Job? = null
    private var tileStore: TileStore? = null

    init {
        setupMapboxOfflineTileStore()
        observeDatabaseData()
    }

    private fun setupMapboxOfflineTileStore() {
        try {
            val token = getMapboxAccessToken().trim()
            if (!token.startsWith("pk.") || token.length < 20) {
                _uiState.update {
                    it.copy(
                        hasMapboxToken = false,
                        offlineCacheStatus = "Jeton public Mapbox manquant ou invalide"
                    )
                }
                return
            }
            MapboxOptions.accessToken = token
            tileStore = TileStore.create(File(getApplication<Application>().filesDir, "offline_tiles").absolutePath)
            MapboxOptions.mapsOptions.tileStore = tileStore
            _uiState.update {
                it.copy(
                    hasMapboxToken = true,
                    offlineCacheStatus = "Cartes hors-ligne actives; calcul d’itinéraire en ligne"
                )
            }
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    hasMapboxToken = false,
                    offlineCacheStatus = "Impossible d’initialiser Mapbox"
                )
            }
        }
    }

    /**
     * Observe les destinations récentes et enregistrées depuis la base de données Room.
     */
    private fun observeDatabaseData() {
        viewModelScope.launch {
            navRepository.recentDestinations.collect { recents ->
                _uiState.update { it.copy(recentDestinations = recents) }
            }
        }
        viewModelScope.launch {
            navRepository.savedDestinations.collect { saved ->
                _uiState.update { it.copy(savedPlaces = saved) }
            }
        }
    }

    fun onLocationPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
    }

    fun updateCurrentLocation(location: LocationData?) {
        if (location == null) return
        val currentState = _uiState.value

        if (currentState.isNavigating && currentState.selectedDestination != null && currentState.routePoints.isNotEmpty()) {
            val destPoint = currentState.selectedDestination.point
            val distanceToDestKm = computeDistanceKm(location.latitude, location.longitude, destPoint.latitude, destPoint.longitude)

            if (distanceToDestKm < 0.03f) {
                stopNavigation()
                return
            }

            val minDistanceToRoute = computeMinDistanceToRouteMeters(location, currentState.routePoints)
            if (minDistanceToRoute > 45) {
                viewModelScope.launch(Dispatchers.IO) {
                    calculateFastestRoute(destPoint, location)
                }
            } else {
                updateRemainingEtaAndManeuver(location, currentState.routePoints)
            }
        }
    }

    // ── MACHINE À ÉTATS DU PANNEAU DE RECHERCHE ─────────────────────────────

    /**
     * Gère l'activation de la barre de recherche (État A : Panneau contextuel avec 3 sections).
     */
    fun onSearchFocused(focused: Boolean, currentLocation: LocationData?) {
        _uiState.update { it.copy(isSearchFocused = focused) }
        if (focused && _uiState.value.searchQuery.isBlank()) {
            loadNearbyPoiSuggestions(currentLocation)
        }
    }

    private fun loadNearbyPoiSuggestions(currentLocation: LocationData?) {
        viewModelScope.launch(Dispatchers.IO) {
            val nearby = fetchPoiSearchResults("restaurant", currentLocation).take(5) +
                    fetchPoiSearchResults("station-service", currentLocation).take(3)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(nearbyPoiSuggestions = nearby.distinctBy { item -> item.address }) }
            }
        }
    }

    fun updateSearchQuery(query: String, currentLocation: LocationData?) {
        _uiState.update { it.copy(searchQuery = query) }

        searchDebounceJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(suggestions = emptyList(), isSearching = false) }
            return
        }

        searchDebounceJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            _uiState.update { it.copy(isSearching = true) }
            val suggestions = fetchAutofillSuggestions(query.trim(), currentLocation)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        suggestions = suggestions,
                        isSearching = false,
                        isBottomSheetVisible = suggestions.isNotEmpty() && !it.isNavigating
                    )
                }
            }
        }
    }

    fun onSearchSubmitted(currentLocation: LocationData?) {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return

        searchDebounceJob?.cancel()
        _uiState.update { it.copy(isSearching = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val results = fetchPoiSearchResults(query, currentLocation)
            withContext(Dispatchers.Main) {
                if (results.isEmpty()) {
                    _uiState.update { it.copy(isSearching = false, isBottomSheetVisible = false) }
                } else if (results.size == 1 && isExactStreetAddress(query)) {
                    selectDestination(results[0], currentLocation)
                } else {
                    _uiState.update {
                        it.copy(
                            poiResults = results,
                            suggestions = emptyList(),
                            isBottomSheetVisible = true,
                            isSearching = false
                        )
                    }
                }
            }
        }
    }

    // ── RECHERCHE SPLIT-SCREEN (ÉTAT B) ─────────────────────────────────────

    fun toggleSplitSearch() {
        _uiState.update {
            it.copy(
                isSplitSearchOpen = !it.isSplitSearchOpen,
                splitSearchQuery = "",
                splitSuggestions = emptyList()
            )
        }
    }

    fun updateSplitSearchQuery(query: String, currentLocation: LocationData?) {
        _uiState.update { it.copy(splitSearchQuery = query) }

        splitSearchDebounceJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(splitSuggestions = emptyList()) }
            return
        }

        splitSearchDebounceJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            val suggestions = fetchAutofillSuggestions(query.trim(), currentLocation)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(splitSuggestions = suggestions) }
            }
        }
    }

    fun selectSplitDestination(poi: MapboxPoiResult, currentLocation: LocationData?) {
        _uiState.update { it.copy(isSplitSearchOpen = false, splitSearchQuery = "", splitSuggestions = emptyList()) }
        selectDestination(poi, currentLocation)
    }

    // ── GESTION DESTINATION & NAVIGATION ────────────────────────────────────

    /**
     * Sélection d'une destination -> Ajout automatique dans l'historique Room "Récentes" et lancement du calcul d'itinéraire.
     */
    fun selectDestination(poi: MapboxPoiResult, currentLocation: LocationData?) {
        viewModelScope.launch(Dispatchers.IO) {
            navRepository.addRecent(poi.name, poi.address, poi.point)
        }

        _uiState.update {
            it.copy(
                selectedDestination = poi,
                searchQuery = poi.name,
                isBottomSheetVisible = false,
                isSearchFocused = false,
                isSearching = true,
                routeError = null,
                suggestions = emptyList(),
                isNavigating = true
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            calculateFastestRoute(poi.point, currentLocation)
        }
    }

    fun selectSavedPlace(savedPlace: SavedPlace, currentLocation: LocationData?) {
        if (savedPlace.point != null) {
            val poi = MapboxPoiResult(
                id = savedPlace.id,
                name = savedPlace.label,
                category = "Lieu enregistré",
                address = savedPlace.address,
                point = savedPlace.point
            )
            selectDestination(poi, currentLocation)
        } else {
            _uiState.update {
                it.copy(
                    searchQuery = savedPlace.label,
                    isSearching = true
                )
            }

            viewModelScope.launch(Dispatchers.IO) {
                var point = savedPlace.address.takeIf { it.isNotBlank() }?.let { addr ->
                    fetchPoiSearchResults(addr, currentLocation).firstOrNull()?.point
                }

                if (point == null && savedPlace.address.isNotBlank()) {
                    point = runCatching {
                        val geocoder = Geocoder(getApplication(), Locale.getDefault())
                        val addresses = geocoder.getFromLocationName(savedPlace.address, 1)
                        addresses?.firstOrNull()?.let { MapboxLocationPoint(it.latitude, it.longitude) }
                    }.getOrNull()
                }

                if (point != null) {
                    val poi = MapboxPoiResult(
                        id = savedPlace.id,
                        name = savedPlace.label,
                        category = "Lieu enregistré",
                        address = savedPlace.address,
                        point = point
                    )
                    withContext(Dispatchers.Main) {
                        selectDestination(poi, currentLocation)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isSearching = false) }
                    }
                }
            }
        }
    }

    suspend fun getAutofillSuggestions(query: String, loc: LocationData?): List<MapboxPoiResult> {
        return withContext(Dispatchers.IO) {
            fetchAutofillSuggestions(query, loc)
        }
    }

    fun editSavedPlace(id: String, newLabel: String, newAddress: String, point: MapboxLocationPoint? = null, iconName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            var finalPoint = point
            if (finalPoint == null && newAddress.isNotBlank()) {
                val mapboxResults = fetchPoiSearchResults(newAddress, null)
                finalPoint = mapboxResults.firstOrNull()?.point
            }
            if (finalPoint == null && newAddress.isNotBlank()) {
                finalPoint = runCatching {
                    val geocoder = Geocoder(getApplication(), Locale.getDefault())
                    val addresses = geocoder.getFromLocationName(newAddress, 1)
                    addresses?.firstOrNull()?.let { MapboxLocationPoint(it.latitude, it.longitude) }
                }.getOrNull()
            }

            val updatedList = _uiState.value.savedPlaces.map { place ->
                if (place.id == id) {
                    place.copy(
                        label = newLabel,
                        address = newAddress,
                        point = finalPoint,
                        iconName = iconName ?: place.iconName
                    )
                } else {
                    place
                }
            }

            _uiState.update { it.copy(savedPlaces = updatedList) }
            updatedList.forEach { navRepository.saveDestination(it) }
        }
    }

    fun addSavedPlace(label: String, address: String, iconName: String, point: MapboxLocationPoint?) {
        viewModelScope.launch(Dispatchers.IO) {
            var finalPoint = point
            if (finalPoint == null && address.isNotBlank()) {
                val mapboxResults = fetchPoiSearchResults(address, null)
                finalPoint = mapboxResults.firstOrNull()?.point
            }

            val newPlace = SavedPlace(
                id = "fav_${System.currentTimeMillis()}",
                label = label,
                iconName = iconName,
                address = address,
                point = finalPoint
            )
            val updatedList = _uiState.value.savedPlaces + newPlace
            _uiState.update { it.copy(savedPlaces = updatedList) }
            navRepository.saveDestination(newPlace)
        }
    }

    fun removeSavedPlace(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedList = _uiState.value.savedPlaces.filterNot { place -> place.id == id }
            _uiState.update { it.copy(savedPlaces = updatedList) }
            navRepository.deleteSaved(id)
        }
    }

    fun stopNavigation() {
        _uiState.update {
            it.copy(
                isNavigating = false,
                isSplitSearchOpen = false,
                isSearchFocused = false,
                routePoints = emptyList(),
                nextManeuver = null,
                etaInfo = null,
                routeError = null,
                selectedDestination = null,
                searchQuery = "",
                suggestions = emptyList(),
                triggerOverview = 0L,
                triggerRecenter = System.currentTimeMillis()
            )
        }
    }

    fun triggerRecenter() {
        _uiState.update { it.copy(triggerRecenter = System.currentTimeMillis()) }
    }

    // ── CALCUL D'ITINÉRAIRE MAPBOX ──────────────────────────────────────────

    private suspend fun calculateFastestRoute(dest: MapboxLocationPoint, currentLocation: LocationData?) {
        if (currentLocation == null) {
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        isNavigating = false,
                        isSearching = false,
                        routePoints = emptyList(),
                        etaInfo = null,
                        nextManeuver = null,
                        routeError = "Position GPS indisponible. Activez la localisation pour calculer l’itinéraire."
                    )
                }
            }
            return
        }

        val startLat = currentLocation.latitude
        val startLon = currentLocation.longitude
        val destLon = dest.longitude
        val destLat = dest.latitude

        try {
            val token = getMapboxAccessToken()
            val mapboxUrl = "https://api.mapbox.com/directions/v5/mapbox/driving/$startLon,$startLat;$destLon,$destLat" +
                    "?overview=full&geometries=geojson&steps=true&banner_instructions=true&access_token=$token"

            var responseText = ""
            var code = 0
            try {
                val conn = URL(mapboxUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                code = conn.responseCode
                if (code == 200) {
                    responseText = conn.inputStream.bufferedReader().readText()
                    _uiState.update { it.copy(isOfflineMode = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isOfflineMode = true) }
            }

            if (responseText.isBlank() || code != 200) {
                val fallbackUrl = "https://router.project-osrm.org/route/v1/driving/$startLon,$startLat;$destLon,$destLat?overview=full&geometries=geojson&steps=true"
                try {
                    val conn = URL(fallbackUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    if (conn.responseCode == 200) {
                        responseText = conn.inputStream.bufferedReader().readText()
                    }
                } catch (_: Exception) {}
            }

            if (responseText.isNotBlank()) {
                val json = JSONObject(responseText)
                val routes = json.optJSONArray("routes")

                if (routes != null && routes.length() > 0) {
                    val route = routes.getJSONObject(0)
                    val distanceMeters = route.getDouble("distance")
                    val durationSeconds = route.getDouble("duration")

                    val durationMin = (durationSeconds / 60.0).toInt()
                    val durationText = if (durationMin >= 60) "${durationMin / 60}h ${durationMin % 60}min" else "$durationMin min"
                    val distText = if (distanceMeters >= 1000) "%.1f km".format(distanceMeters / 1000.0) else "${distanceMeters.toInt()} m"

                    val cal = Calendar.getInstance()
                    cal.add(Calendar.SECOND, durationSeconds.toInt())
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val etaTimeText = sdf.format(cal.time)

                    val eta = MapboxEtaInfo(durationText = durationText, etaTimeText = etaTimeText, distanceText = distText)

                    val geometry = route.getJSONObject("geometry")
                    val coordinates = geometry.getJSONArray("coordinates")
                    val points = mutableListOf<MapboxLocationPoint>()
                    for (i in 0 until coordinates.length()) {
                        val coord = coordinates.getJSONArray(i)
                        points.add(MapboxLocationPoint(coord.getDouble(1), coord.getDouble(0)))
                    }

                    val legs = route.optJSONArray("legs")
                    var maneuver: MapboxManeuverInstruction? = null
                    if (legs != null && legs.length() > 0) {
                        val steps = legs.getJSONObject(0).optJSONArray("steps")
                        if (steps != null && steps.length() > 1) {
                            val nextStep = steps.getJSONObject(1)
                            val stepManeuver = nextStep.optJSONObject("maneuver")
                            val type = stepManeuver?.optString("type", "straight") ?: "straight"
                            val modifier = stepManeuver?.optString("modifier", "") ?: ""
                            val stepDist = nextStep.optDouble("distance", 0.0)

                            val instructionText = buildManeuverText(type, modifier, stepDist)
                            maneuver = MapboxManeuverInstruction(
                                instruction = instructionText,
                                iconType = type.uppercase(),
                                distanceMeters = stepDist
                            )
                        }
                    }

                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                routePoints = points,
                                etaInfo = eta,
                                nextManeuver = maneuver,
                                isNavigating = true,
                                isSearching = false,
                                routeError = null
                            )
                        }
                    }
                    return
                }
            }

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        isNavigating = false,
                        routeError = "Impossible de calculer l’itinéraire. Vérifiez la connexion et le jeton Mapbox."
                    )
                }
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        isNavigating = false,
                        routeError = "Impossible de calculer l’itinéraire. Vérifiez la connexion et le jeton Mapbox."
                    )
                }
            }
        }
    }

    private suspend fun fetchAutofillSuggestions(query: String, loc: LocationData?): List<MapboxPoiResult> {
        val token = getMapboxAccessToken()
        val list = mutableListOf<MapboxPoiResult>()

        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val proximityParam = if (loc != null) "&proximity=${loc.longitude},${loc.latitude}" else ""
            val urlStr = "https://api.mapbox.com/geocoding/v5/mapbox.places/$encoded.json?autocomplete=true&limit=6$proximityParam&access_token=$token"

            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(jsonStr)
                val features = json.optJSONArray("features")
                if (features != null) {
                    for (i in 0 until features.length()) {
                        val feat = features.getJSONObject(i)
                        val text = feat.optString("text", query)
                        val placeName = feat.optString("place_name", text)
                        val center = feat.getJSONArray("center")
                        val lon = center.getDouble(0)
                        val lat = center.getDouble(1)

                        val point = MapboxLocationPoint(lat, lon)
                        val dist = loc?.let { computeDistanceKm(it.latitude, it.longitude, lat, lon) } ?: 0f

                        list.add(
                            MapboxPoiResult(
                                id = "mb_$i",
                                name = text,
                                category = feat.optJSONArray("place_type")?.optString(0) ?: "Adresse",
                                address = placeName,
                                point = point,
                                distanceKm = dist
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        if (list.isEmpty() && loc != null) {
            list.addAll(searchNearbyNominatim(query, loc, limit = 6))
        }

        if (list.isEmpty()) {
            list.addAll(searchLocalDatabaseAndGeocoder(query, loc))
        }

        return list.distinctBy { it.address }.sortedBy { it.distanceKm }
    }

    private suspend fun fetchPoiSearchResults(query: String, loc: LocationData?): List<MapboxPoiResult> {
        val list = mutableListOf<MapboxPoiResult>()
        val token = getMapboxAccessToken()

        if (loc != null) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val urlStr = "https://api.mapbox.com/geocoding/v5/mapbox.places/$encoded.json?proximity=${loc.longitude},${loc.latitude}&limit=15&access_token=$token"
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(jsonStr)
                    val features = json.optJSONArray("features")
                    if (features != null) {
                        for (i in 0 until features.length()) {
                            val feat = features.getJSONObject(i)
                            val text = feat.optString("text", query)
                            val placeName = feat.optString("place_name", text)
                            val center = feat.getJSONArray("center")
                            val lon = center.getDouble(0)
                            val lat = center.getDouble(1)

                            val point = MapboxLocationPoint(lat, lon)
                            val dist = computeDistanceKm(loc.latitude, loc.longitude, lat, lon)

                            list.add(
                                MapboxPoiResult(
                                    id = "poi_$i",
                                    name = text,
                                    category = feat.optJSONArray("place_type")?.optString(0)?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: "Lieu",
                                    address = placeName,
                                    point = point,
                                    distanceKm = dist
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (list.isEmpty() && loc != null) {
            list.addAll(searchNearbyNominatim(query, loc, limit = 15))
        }

        return rankResultsByPrefixAndDistance(list, query)
    }

    private fun searchNearbyNominatim(query: String, loc: LocationData, limit: Int): List<MapboxPoiResult> {
        return try {
            val encodedQuery = URLEncoder.encode("$query*", "UTF-8")
            val urlStr = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&lat=${loc.latitude}&lon=${loc.longitude}&addressdetails=1&limit=$limit"
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "OpenLauncherMapboxApp")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            val jsonText = conn.inputStream.bufferedReader().readText()
            val array = org.json.JSONArray(jsonText)

            val list = mutableListOf<MapboxPoiResult>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val lat = obj.getDouble("lat")
                val lon = obj.getDouble("lon")
                val displayName = obj.getString("display_name")
                val name = obj.optString("name", displayName.split(",")[0])
                val type = obj.optString("type", "Commerce")

                val pt = MapboxLocationPoint(lat, lon)
                val dist = computeDistanceKm(loc.latitude, loc.longitude, lat, lon)

                list.add(
                    MapboxPoiResult(
                        id = "nom_$i",
                        name = name,
                        category = type.replace("_", " ").replaceFirstChar { it.uppercase() },
                        address = displayName,
                        point = pt,
                        distanceKm = dist
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun updateRemainingEtaAndManeuver(location: LocationData, routePoints: List<MapboxLocationPoint>) {
        if (routePoints.isEmpty()) return
        val dest = routePoints.last()
        val remainingDistKm = computeDistanceKm(location.latitude, location.longitude, dest.latitude, dest.longitude)
        val remainingDistMeters = (remainingDistKm * 1000).toDouble()

        val durationHours = remainingDistKm / 50.0
        val durationMin = (durationHours * 60).toInt().coerceAtLeast(1)
        val durationText = if (durationMin >= 60) "${durationMin / 60}h ${durationMin % 60}min" else "$durationMin min"
        val distText = if (remainingDistMeters >= 1000) "%.1f km".format(remainingDistKm) else "${remainingDistMeters.toInt()} m"

        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, durationMin)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val etaTimeText = sdf.format(cal.time)

        _uiState.update {
            it.copy(
                etaInfo = MapboxEtaInfo(
                    durationText = durationText,
                    etaTimeText = etaTimeText,
                    distanceText = distText
                )
            )
        }
    }

    private fun computeMinDistanceToRouteMeters(location: LocationData, routePoints: List<MapboxLocationPoint>): Float {
        var minDistance = Float.MAX_VALUE
        val locArr = FloatArray(1)
        for (pt in routePoints) {
            Location.distanceBetween(location.latitude, location.longitude, pt.latitude, pt.longitude, locArr)
            if (locArr[0] < minDistance) {
                minDistance = locArr[0]
            }
        }
        return minDistance
    }

    fun dismissBottomSheet() {
        _uiState.update { it.copy(isBottomSheetVisible = false) }
    }

    fun clearRouteError() {
        _uiState.update { it.copy(routeError = null) }
    }

    private suspend fun searchLocalDatabaseAndGeocoder(query: String, loc: LocationData?): List<MapboxPoiResult> {
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<MapboxPoiResult>()
            val q = query.lowercase()

            val currentState = _uiState.value
            val matchesRecents = currentState.recentDestinations.filter { it.name.lowercase().contains(q) || it.address.lowercase().contains(q) }
            val matchesSaved = currentState.savedPlaces
                .filter { it.label.lowercase().contains(q) || it.address.lowercase().contains(q) }
                .mapNotNull { place ->
                    place.point?.let { point ->
                        MapboxPoiResult(
                            id = place.id,
                            name = place.label,
                            category = "Favori",
                            address = place.address,
                            point = point
                        )
                    }
            }
            results.addAll(matchesRecents)
            results.addAll(matchesSaved)

            try {
                val geocoder = Geocoder(getApplication(), Locale.getDefault())
                val addresses = geocoder.getFromLocationName(query, 5)
                addresses?.forEachIndexed { index, addr ->
                    val lat = addr.latitude
                    val lon = addr.longitude
                    val name = addr.featureName ?: addr.locality ?: query
                    val addressStr = addr.getAddressLine(0) ?: query
                    val dist = loc?.let { computeDistanceKm(it.latitude, it.longitude, lat, lon) } ?: 0f

                    results.add(
                        MapboxPoiResult(
                            id = "geo_$index",
                            name = name,
                            category = "Adresse locale",
                            address = addressStr,
                            point = MapboxLocationPoint(lat, lon),
                            distanceKm = dist
                        )
                    )
                }
            } catch (_: Exception) {}

            results
        }
    }

    private fun rankResultsByPrefixAndDistance(results: List<MapboxPoiResult>, query: String): List<MapboxPoiResult> {
        val q = query.lowercase().trim()
        if (q.isBlank()) return results.sortedBy { it.distanceKm }

        return results
            .distinctBy { "${it.name}_${"%.2f".format(it.distanceKm)}" }
            .sortedWith(
                compareByDescending<MapboxPoiResult> { poi ->
                    val words = poi.name.lowercase().split("\\s+".toRegex())
                    when {
                        poi.name.startsWith(q, ignoreCase = true) -> 3
                        words.any { word -> word.startsWith(q, ignoreCase = true) } -> 2
                        poi.name.contains(q, ignoreCase = true) -> 1
                        else -> 0
                    }
                }.thenBy { it.distanceKm }
            )
    }

    private fun isExactStreetAddress(query: String): Boolean {
        return query.any { it.isDigit() } && query.length > 8 && (query.contains("rue", ignoreCase = true) || query.contains("ave", ignoreCase = true) || query.contains("bd", ignoreCase = true) || query.contains("st", ignoreCase = true))
    }

    private fun buildManeuverText(type: String, modifier: String, distMeters: Double): String {
        val distText = if (distMeters >= 1000) "dans %.1f km".format(distMeters / 1000.0) else "dans ${distMeters.toInt()} m"
        return when {
            modifier.contains("right") -> "Tourner à droite $distText"
            modifier.contains("left")  -> "Tourner à gauche $distText"
            type.contains("roundabout") -> "Prendre le rond-point $distText"
            else -> "Continuer tout droit $distText"
        }
    }

    private fun computeDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, result)
        return result[0] / 1000f
    }

    private fun getMapboxAccessToken(): String {
        return try {
            getApplication<Application>().getString(R.string.mapbox_access_token)
        } catch (_: Exception) {
            ""
        }
    }
}
