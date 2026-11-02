package com.openlauncher.app.viewmodel

import android.util.Log
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import com.mapbox.maps.CoordinateBounds
import com.mapbox.common.MapboxOptions
import com.mapbox.common.Cancelable
import com.mapbox.common.TileStore
import com.mapbox.common.TileRegion
import com.mapbox.maps.mapsOptions
import com.openlauncher.app.data.OfflineRegionManager
import com.openlauncher.app.model.OfflineRegion
import com.openlauncher.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class TileRegionDownloadViewModel(application: Application) : AndroidViewModel(application) {
    var downloadingRegions by mutableStateOf(setOf<String>())
        private set

    var downloadProgress by mutableStateOf(mapOf<String, Float>())
        private set

    var refreshTrigger by mutableStateOf(false)
        private set

    var downloadedRegions by mutableStateOf<Map<String, Boolean>>(emptyMap())
    var regionSizes by mutableStateOf<Map<String, Double>>(emptyMap())
    var regionDownloadErrors by mutableStateOf<Map<String, String>>(emptyMap())
    var regionRemovalErrors by mutableStateOf<Map<String, String>>(emptyMap())
    var isSearchingRegions by mutableStateOf(false)
        private set
    var regionSearchError by mutableStateOf<String?>(null)
        private set
    var searchedRegions by mutableStateOf<List<OfflineRegion>>(emptyList())
        private set
    private var knownRegions by mutableStateOf<List<OfflineRegion>>(emptyList())
    private var isRegionSearchActive by mutableStateOf(false)
    private val tileStore: TileStore? = MapboxOptions.mapsOptions.tileStore
    private val activeDownloads = ConcurrentHashMap<String, Cancelable>()
    private val cancellationRequested = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private val presetRegions = listOf(
        OfflineRegion(
            id = "paris-region",
            name = "Paris",
            bounds = CoordinateBounds(
                Point.fromLngLat(2.3522, 48.8366),
                Point.fromLngLat(2.3822, 48.8666)
            ),
            regionType = "Ville"
        ),
        OfflineRegion(
            id = "new-york-region",
            name = "New York",
            bounds = CoordinateBounds(
                Point.fromLngLat(-74.28127, 40.48398),
                Point.fromLngLat(-73.58442, 40.98701)
            ),
            regionType = "Ville"
        ),
        OfflineRegion(
            id = "london-region",
            name = "London",
            bounds = CoordinateBounds(
                Point.fromLngLat(-0.1278, 51.4874),
                Point.fromLngLat(-0.0978, 51.5174)
            ),
            regionType = "Ville"
        )
    )

    val regions: List<OfflineRegion>
        get() = if (isRegionSearchActive) searchedRegions.distinctBy { it.id }
        else (presetRegions + knownRegions).distinctBy { it.id }

    /** Regions that have local data or are currently being downloaded. */
    val downloadedOrDownloadingRegions: List<OfflineRegion>
        get() = (presetRegions + knownRegions).distinctBy { it.id }
            .filter { it.id in downloadingRegions || downloadedRegions[it.id] == true }
            .sortedWith(compareByDescending<OfflineRegion> { it.id in downloadingRegions }.thenBy { it.name.lowercase() })

    init {
        knownRegions = loadSearchedRegions()
        searchedRegions = knownRegions
        (presetRegions + knownRegions).distinctBy { it.id }.forEach { checkIfDownloaded(it.id) }
    }

    /** Search Mapbox's worldwide country, region and locality catalog. */
    fun searchRegions(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.length < 2) {
            regionSearchError = "Entrez au moins deux caractères."
            isRegionSearchActive = true
            searchedRegions = emptyList()
            return
        }

        viewModelScope.launch {
            isSearchingRegions = true
            isRegionSearchActive = true
            regionSearchError = null
            try {
                val results = withContext(Dispatchers.IO) { fetchRegionResults(trimmedQuery) }
                // Display only results for the latest query, without old searches or presets.
                searchedRegions = results.distinctBy { it.id }
                knownRegions = (knownRegions + results).distinctBy { it.id }
                persistSearchedRegions()
                results.forEach { checkIfDownloaded(it.id) }
                if (results.isEmpty()) {
                    regionSearchError = "Aucun pays ou région trouvé. Vérifiez la connexion et l’accès Mapbox."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                regionSearchError = e.message ?: "Recherche Mapbox impossible."
            } finally {
                isSearchingRegions = false
            }
        }
    }

    private fun fetchRegionResults(query: String): List<OfflineRegion> {
        val token = getApplication<Application>().getString(R.string.mapbox_access_token)
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL(
            "https://api.mapbox.com/geocoding/v5/mapbox.places/$encodedQuery.json" +
                "?types=country,region,place,locality,postcode&limit=10&access_token=$token"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("Mapbox a répondu HTTP $status.")
            }

            val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
            return (0 until features.length()).mapNotNull { index ->
                val feature = features.optJSONObject(index) ?: return@mapNotNull null
                val placeTypes = feature.optJSONArray("place_type") ?: return@mapNotNull null
                if ((0 until placeTypes.length()).none {
                        placeTypes.optString(it) in setOf("country", "region", "place", "locality", "postcode")
                    }) {
                    return@mapNotNull null
                }

                val bbox = feature.optJSONArray("bbox") ?: return@mapNotNull null
                if (bbox.length() < 4) return@mapNotNull null
                val west = bbox.getDouble(0)
                val south = bbox.getDouble(1)
                val east = bbox.getDouble(2)
                val north = bbox.getDouble(3)
                if (west >= east || south >= north) return@mapNotNull null

                val span = max(east - west, north - south)
                // Broad country and macro-region downloads stay at overview zooms;
                // smaller regions include progressively more road detail.
                val maxZoom = when {
                    span >= 80.0 -> 5
                    span >= 40.0 -> 6
                    span >= 20.0 -> 7
                    span >= 10.0 -> 8
                    span >= 5.0 -> 10
                    span >= 2.0 -> 12
                    else -> 16
                }
                val shortCode = feature.optJSONObject("properties")
                    ?.optString("short_code")
                    .orEmpty()
                    .uppercase()
                val regionType = when {
                    (0 until placeTypes.length()).any { placeTypes.optString(it) == "country" } -> "Pays"
                    (0 until placeTypes.length()).any { placeTypes.optString(it) == "region" } -> when (shortCode.substringBefore('-')) {
                        "CA" -> "Province"
                        "US" -> "État"
                        "AU" -> "État ou territoire"
                        else -> "Région"
                    }
                    (0 until placeTypes.length()).any { placeTypes.optString(it) == "place" } -> "Ville"
                    (0 until placeTypes.length()).any { placeTypes.optString(it) == "locality" } -> "Localité"
                    else -> "Code postal"
                }
                val name = feature.optString("text").ifBlank { query }
                val featureId = feature.optString("id").ifBlank { "${name}_$index" }
                    .replace(Regex("[^A-Za-z0-9_-]"), "_")
                val southWest = Point.fromLngLat(west, south)
                val northEast = Point.fromLngLat(east, north)
                OfflineRegion(
                    id = "mapbox-$featureId",
                    name = name,
                    bounds = CoordinateBounds(southWest, northEast),
                    minZoom = 0,
                    maxZoom = maxZoom,
                    regionType = regionType
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Resolve the current location into a nearby city/locality, region and country. */
    suspend fun recommendRegionsForLocation(latitude: Double, longitude: Double): List<OfflineRegion> =
        withContext(Dispatchers.IO) {
            val token = getApplication<Application>().getString(R.string.mapbox_access_token)
            val url = URL(
                "https://api.mapbox.com/geocoding/v5/mapbox.places/$longitude,$latitude.json" +
                    "?types=place,locality,region,country&limit=10&access_token=$token"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
            }
            val locationNames = try {
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) throw IllegalStateException("Mapbox a répondu HTTP $status.")
                val features = JSONObject(body).optJSONArray("features") ?: return@withContext emptyList()
                val wantedTypes = listOf("place", "locality", "region", "country")
                wantedTypes.mapNotNull { wantedType ->
                    (0 until features.length()).asSequence()
                        .mapNotNull { features.optJSONObject(it) }
                        .firstOrNull { feature ->
                            val types = feature.optJSONArray("place_type") ?: return@firstOrNull false
                            (0 until types.length()).any { types.optString(it) == wantedType }
                        }
                        ?.let { wantedType to it.optString("text").takeIf { name -> name.isNotBlank() } }
                        ?.let { (type, name) -> name?.let { type to it } }
                }
            } finally {
                connection.disconnect()
            }

            locationNames.mapNotNull { (type, name) ->
                val results = fetchRegionResults(name)
                val expectedType = when (type) {
                    "country" -> "Pays"
                    "region" -> null // The UI will show the localized province/state type from Mapbox.
                    "place", "locality" -> if (type == "place") "Ville" else "Localité"
                    else -> null
                }
                results.firstOrNull {
                    it.name.equals(name, ignoreCase = true) && (expectedType == null || it.regionType == expectedType)
                } ?: results.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: results.firstOrNull()
            }.distinctBy { it.id }
        }

    private fun loadSearchedRegions(): List<OfflineRegion> {
        return try {
            val saved = getApplication<Application>()
                .getSharedPreferences("offline_region_catalog", Application.MODE_PRIVATE)
                .getString("regions", null) ?: return emptyList()
            val array = org.json.JSONArray(saved)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val west = item.optDouble("west", Double.NaN)
                val south = item.optDouble("south", Double.NaN)
                val east = item.optDouble("east", Double.NaN)
                val north = item.optDouble("north", Double.NaN)
                if (!west.isFinite() || !south.isFinite() || !east.isFinite() || !north.isFinite() || west >= east || south >= north) {
                    return@mapNotNull null
                }
                OfflineRegion(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    bounds = CoordinateBounds(Point.fromLngLat(west, south), Point.fromLngLat(east, north)),
                    minZoom = item.optInt("minZoom", 0),
                    maxZoom = item.optInt("maxZoom", 16),
                    regionType = item.optString("regionType", "Région")
                )
            }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistSearchedRegions() {
        val array = org.json.JSONArray()
        knownRegions.forEach { region ->
            array.put(
                JSONObject()
                    .put("id", region.id)
                    .put("name", region.name)
                    .put("west", region.bounds.southwest.longitude())
                    .put("south", region.bounds.southwest.latitude())
                    .put("east", region.bounds.northeast.longitude())
                    .put("north", region.bounds.northeast.latitude())
                    .put("minZoom", region.minZoom)
                    .put("maxZoom", region.maxZoom)
                    .put("regionType", region.regionType)
            )
        }
        getApplication<Application>()
            .getSharedPreferences("offline_region_catalog", Application.MODE_PRIVATE)
            .edit()
            .putString("regions", array.toString())
            .apply()
    }

    fun checkIfDownloaded(regionId: String) {
        tileStore?.getTileRegion(regionId) { result ->
            if (result.isValue) {
                val tileRegion: TileRegion? = result.value
                if (tileRegion != null) {
                    downloadedRegions = downloadedRegions + (regionId to true)
                    regionSizes = regionSizes + (regionId to (tileRegion.completedResourceSize.toDouble() / (1024.0 * 1024.0)))
                } else {
                    downloadedRegions = downloadedRegions + (regionId to false)
                    regionSizes = regionSizes + (regionId to 0.0)
                }
            } else {
                downloadedRegions = downloadedRegions + (regionId to false)
                regionSizes = regionSizes + (regionId to 0.0)
            }
        }
    }

    fun downloadRegion(region: OfflineRegion) {
        knownRegions = (knownRegions + region).distinctBy { it.id }
        persistSearchedRegions()
        regionDownloadErrors = regionDownloadErrors - region.id
        val cancelable = OfflineRegionManager.downloadRegion(
            context = getApplication(),
            region = region,
            downloadingRegions = downloadingRegions,
            onDownloadingRegionsUpdate = { updatedSet ->
                // The SDK callback captures an older set; change only this region.
                downloadingRegions = if (region.id in updatedSet) downloadingRegions + region.id else downloadingRegions - region.id
            },
            onProgress = { regionId, progress ->
                downloadProgress = downloadProgress + (regionId to progress)
            },
            onCompletion = { regionId, result ->
                activeDownloads.remove(regionId)
                downloadProgress = downloadProgress - regionId
                when {
                    result.isSuccess -> {
                        regionDownloadErrors = regionDownloadErrors - regionId
                        val tileRegion: TileRegion? = result.getOrNull()
                        Log.i("TileRegionVM", "Downloaded ${region.name}: ${tileRegion?.completedResourceSize}")
                        checkIfDownloaded(regionId)
                    }
                    result.isFailure -> {
                        val error = result.exceptionOrNull()
                        if (cancellationRequested.remove(regionId)) {
                            regionDownloadErrors = regionDownloadErrors - regionId
                            Log.i("TileRegionVM", "Cancelled download ${region.name}")
                        } else {
                            regionDownloadErrors = regionDownloadErrors + (regionId to (error?.message ?: "Échec du téléchargement des données routières"))
                            Log.e("TileRegionVM", "Failed to download ${region.name}: $error")
                        }
                        checkIfDownloaded(regionId)
                    }
                }
            }
        )
        if (cancelable != null && region.id in downloadingRegions) {
            activeDownloads[region.id] = cancelable
        }
    }

    fun cancelDownload(regionId: String) {
        val cancelable = activeDownloads[regionId] ?: return
        cancellationRequested.add(regionId)
        cancelable.cancel()
    }

    fun removeRegion(region: OfflineRegion) {
        if (region.id in downloadingRegions) return
        regionRemovalErrors = regionRemovalErrors - region.id
        OfflineRegionManager.removeRegion(region.id) { result ->
            if (result.isSuccess) {
                downloadedRegions = downloadedRegions - region.id
                regionSizes = regionSizes - region.id
                regionDownloadErrors = regionDownloadErrors - region.id
                regionRemovalErrors = regionRemovalErrors - region.id
                knownRegions = knownRegions.filterNot { it.id == region.id }
                searchedRegions = searchedRegions.filterNot { it.id == region.id }
                persistSearchedRegions()
                refreshTrigger = !refreshTrigger
            } else {
                regionRemovalErrors = regionRemovalErrors + (region.id to (result.exceptionOrNull()?.message ?: "Suppression impossible"))
            }
        }
    }

    override fun onCleared() {
        activeDownloads.values.toList().forEach { it.cancel() }
        activeDownloads.clear()
        super.onCleared()
    }

    fun clearAllRegions() {
        OfflineRegionManager.clearAllRegions {
            downloadingRegions = emptySet()
            downloadProgress = emptyMap()
            refreshTrigger = !refreshTrigger
            regions.forEach { checkIfDownloaded(it.id) }
        }
    }
}
