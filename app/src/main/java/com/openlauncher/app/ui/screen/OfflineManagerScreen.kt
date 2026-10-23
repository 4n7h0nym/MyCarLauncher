package com.openlauncher.app.ui.screen

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapbox.common.TileRegionLoadProgress
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileStore
import com.mapbox.common.MapboxOptions
import com.mapbox.maps.mapsOptions
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.GlyphsRasterizationMode
import com.mapbox.maps.StylePackLoadOptions
import com.mapbox.maps.TilesetDescriptorOptions
import com.openlauncher.app.data.room.NavigationDatabase
import com.openlauncher.app.data.room.OfflineRegionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import java.io.File

data class RegionPreset(
    val name: String,
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double
)

// Liste de toutes les grandes régions du Monde
val worldPresets = listOf(
    RegionPreset("Monde Entier (Carte Globale)", -180.0, -85.0, 180.0, 85.0),
    RegionPreset("Amérique du Nord (Canada, USA, Mexique)", -170.0, 14.0, -50.0, 83.0),
    RegionPreset("Amérique du Sud", -82.0, -56.0, -34.0, 13.0),
    RegionPreset("Europe Occidentale & Centrale", -10.0, 35.0, 30.0, 71.0),
    RegionPreset("Europe de l'Est & Russie Ouest", 30.0, 40.0, 60.0, 70.0),
    RegionPreset("Asie de l'Est & Japon", 95.0, 10.0, 145.0, 55.0),
    RegionPreset("Asie du Sud & Inde", 60.0, 5.0, 95.0, 35.0),
    RegionPreset("Moyen-Orient & Afrique du Nord", -18.0, 12.0, 60.0, 42.0),
    RegionPreset("Afrique Subsaharienne", -18.0, -35.0, 51.0, 15.0),
    RegionPreset("Océanie (Australie & Nouvelle-Zélande)", 110.0, -50.0, 180.0, 0.0),
    RegionPreset("Zone personnalisée...", 0.0, 0.0, 0.0, 0.0)
)

data class OfflineManagerUiState(
    val isOnline: Boolean = true,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val activeRegionName: String? = null,
    val downloadedRegions: List<OfflineRegionEntity> = emptyList()
)

class OfflineManagerViewModel(context: Context) {
    private val appContext = context.applicationContext
    private val dao = NavigationDatabase.getDatabase(appContext).navigationDao()

    private val _uiState = MutableStateFlow(OfflineManagerUiState())
    val uiState: StateFlow<OfflineManagerUiState> = _uiState.asStateFlow()

    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    init {
        checkInitialConnectivity()
        registerNetworkCallback()
        observeOfflineRegions()
    }

    private fun checkInitialConnectivity() {
        val network = connectivityManager?.activeNetwork
        val caps = connectivityManager?.getNetworkCapabilities(network)
        val isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _uiState.update { currentState -> currentState.copy(isOnline = isOnline) }
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _uiState.update { currentState -> currentState.copy(isOnline = true) }
            }
            override fun onLost(network: Network) {
                _uiState.update { currentState -> currentState.copy(isOnline = false) }
            }
        })
    }

    private fun observeOfflineRegions() {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            dao.getOfflineRegions().collect { list ->
                _uiState.update { currentState -> currentState.copy(downloadedRegions = list) }
            }
        }
    }

    private fun createBoundingBoxPolygon(
        minLon: Double, minLat: Double,
        maxLon: Double, maxLat: Double
    ): Polygon {
        val points = listOf(
            Point.fromLngLat(minLon, minLat),
            Point.fromLngLat(maxLon, minLat),
            Point.fromLngLat(maxLon, maxLat),
            Point.fromLngLat(minLon, maxLat),
            Point.fromLngLat(minLon, minLat)
        )
        return Polygon.fromLngLats(listOf(points))
    }

    /**
     * Télécharge toutes les régions du monde prédéfinies
     */
    fun downloadAllWorldRegions() {
        CoroutineScope(Dispatchers.IO).launch {
            for (preset in worldPresets) {
                if (!preset.name.contains("personnalisée") && !preset.name.contains("Monde Entier")) {
                    downloadSingleRegionInternal(preset.name, preset.minLon, preset.minLat, preset.maxLon, preset.maxLat)
                }
            }
        }
    }

    fun downloadRegion(
        regionName: String,
        minLon: Double,
        minLat: Double,
        maxLon: Double,
        maxLat: Double
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            downloadSingleRegionInternal(regionName, minLon, minLat, maxLon, maxLat)
        }
    }

    private suspend fun downloadSingleRegionInternal(
        regionName: String,
        minLon: Double,
        minLat: Double,
        maxLon: Double,
        maxLat: Double
    ) {
        val regionId = "reg_${System.currentTimeMillis()}_${regionName.hashCode().toString().takeLast(4)}"

        _uiState.update {
            it.copy(
                isDownloading = true,
                downloadProgress = 0f,
                activeRegionName = regionName
            )
        }

        try {
            val tileStore = TileStore.create(
                File(
                    appContext.filesDir,
                    "offline_tiles"
                ).absolutePath
            )
            MapboxOptions.mapsOptions.tileStore = tileStore

            val offlineManager = OfflineManager()

            // Cache les ressources de style utilisées par la carte (jour/nuit) en plus des tuiles.
            val styles = listOf(com.mapbox.maps.Style.MAPBOX_STREETS, com.mapbox.maps.Style.DARK)
            styles.forEach { styleUri ->
                offlineManager.loadStylePack(
                    styleUri,
                    StylePackLoadOptions.Builder()
                        .glyphsRasterizationMode(GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY)
                        .build(),
                    {},
                    {}
                )
            }

            val descriptors = styles.map { styleUri ->
                offlineManager.createTilesetDescriptor(
                    TilesetDescriptorOptions.Builder()
                        .styleURI(styleUri)
                        .pixelRatio(appContext.resources.displayMetrics.density)
                        .minZoom(0.toByte())
                        .maxZoom(14.toByte())
                        .build()
                )
            }

            val geometry =
                createBoundingBoxPolygon(
                    minLon,
                    minLat,
                    maxLon,
                    maxLat
                )

            val loadOptions =
                TileRegionLoadOptions.Builder()
                    .geometry(geometry)
                    .descriptors(descriptors)
                    .acceptExpired(true)
                    .build()

            var finalBytes = 0L
            var finalResources = 0L

            tileStore.loadTileRegion(
                regionId,
                loadOptions,
                { progress ->
                    finalBytes = progress.completedResourceSize
                    finalResources = progress.completedResourceCount

                    val percent = if (progress.requiredResourceCount > 0) {
                        finalResources.toFloat() / progress.requiredResourceCount.toFloat()
                    } else 0f

                    _uiState.update {
                        it.copy(
                            downloadProgress = percent.coerceIn(0f, 1f)
                        )
                    }
                }
            ) { expected ->
                CoroutineScope(Dispatchers.IO).launch {
                    if (!expected.isError) {
                        val tileRegion = expected.value
                        val bytes = tileRegion?.completedResourceSize ?: finalBytes
                        val resources = tileRegion?.completedResourceCount ?: finalResources
                        val sizeMb = bytes / 1024f / 1024f

                        dao.insertOfflineRegion(
                            OfflineRegionEntity(
                                id = regionId,
                                name = regionName,
                                minLon = minLon,
                                minLat = minLat,
                                maxLon = maxLon,
                                maxLat = maxLat,
                                sizeMb = sizeMb,
                                tileCount = resources.toInt(),
                                status = "Disponible Hors-Ligne"
                            )
                        )
                    }

                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            activeRegionName = null,
                            downloadProgress = 0f
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update {
                it.copy(
                    isDownloading = false,
                    activeRegionName = null,
                    downloadProgress = 0f
                )
            }
        }
    }

    fun deleteRegion(id: String) {
        val tileStore = TileStore.create(
            File(
                appContext.filesDir,
                "offline_tiles"
            ).absolutePath
        )
        MapboxOptions.mapsOptions.tileStore = tileStore

        tileStore.removeTileRegion(id) {
            CoroutineScope(Dispatchers.IO).launch {
                dao.deleteOfflineRegion(id)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineManagerScreen(
    accent: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel = remember { OfflineManagerViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()

    val cardBg     = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outline
    val textColor  = MaterialTheme.colorScheme.onBackground
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    var showDownloadDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header & Network Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Public, null, tint = accent, modifier = Modifier.size(28.dp))
                Column {
                    Text(
                        text = "GESTIONNAIRE DE CARTES MONDIALES",
                        style = MaterialTheme.typography.titleLarge,
                        color = textColor,
                        fontSize = 16.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Téléchargements de régions du monde entier & TileStore Mapbox",
                        color = mutedColor,
                        fontSize = 11.sp
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (uiState.isOnline) Color(0xFF00C853).copy(alpha = 0.2f) else Color(0xFFFF3D00).copy(alpha = 0.2f),
                modifier = Modifier.border(1.dp, if (uiState.isOnline) Color(0xFF00C853) else Color(0xFFFF3D00), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (uiState.isOnline) Color(0xFF00C853) else Color(0xFFFF3D00))
                    )
                    Text(
                        text = if (uiState.isOnline) "EN LIGNE (ONLINE)" else "HORS-LIGNE (OFFLINE)",
                        color = if (uiState.isOnline) Color(0xFF00C853) else Color(0xFFFF3D00),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // Main Content Container
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(28.dp))
                .padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // Left Column: Downloaded Regions List & Actions
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RÉGIONS STOCKÉES (${uiState.downloadedRegions.size})",
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.downloadAllWorldRegions() },
                            colors = ButtonDefaults.buttonColors(containerColor = cardBorder.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("TOUT LE MONDE", color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showDownloadDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("CHOISIR UNE RÉGION", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (uiState.downloadedRegions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Aucune région mondiale enregistrée", color = mutedColor, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.downloadedRegions) { region ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = cardBorder.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth().border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(accent.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Public, null, tint = accent, modifier = Modifier.size(22.dp))
                                        }
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = region.name,
                                                color = textColor,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${String.format(java.util.Locale.US, "%.1f", region.sizeMb)} Mo • ${region.tileCount} tuiles",
                                                color = mutedColor,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteRegion(region.id) }
                                    ) {
                                        Icon(Icons.Default.Delete, "Supprimer", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Right Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(22.dp))
                    .background(cardBorder.copy(alpha = 0.1f))
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "ÉTAT DE LACOUVERTURE MONDALE",
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Sélectionnez et téléchargez n'importe quel continent ou région du globe pour la navigation hors-ligne.",
                        color = mutedColor,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }

                if (uiState.isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Téléchargement : ${uiState.activeRegionName}", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("${(uiState.downloadProgress * 100).toInt()}%", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        LinearProgressIndicator(
                            progress = { uiState.downloadProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = accent,
                            trackColor = cardBorder.copy(alpha = 0.3f)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = accent.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth().border(1.dp, accent, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(24.dp))
                            Text(
                                "Base mondiale prête pour la navigation.",
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    // Download Dialog
    if (showDownloadDialog) {
        var expanded by remember { mutableStateOf(false) }
        var selectedPreset by remember { mutableStateOf(worldPresets[0]) }
        var regionName by remember { mutableStateOf(selectedPreset.name) }

        var minLon by remember { mutableStateOf(selectedPreset.minLon.toString()) }
        var minLat by remember { mutableStateOf(selectedPreset.minLat.toString()) }
        var maxLon by remember { mutableStateOf(selectedPreset.maxLon.toString()) }
        var maxLat by remember { mutableStateOf(selectedPreset.maxLat.toString()) }

        val isCustom = selectedPreset.name.contains("personnalisée")

        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Télécharger une région du globe", color = textColor) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedPreset.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Région MONDALE") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            worldPresets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.name) },
                                    onClick = {
                                        selectedPreset = preset
                                        regionName = preset.name
                                        if (!preset.name.contains("personnalisée")) {
                                            minLon = preset.minLon.toString()
                                            minLat = preset.minLat.toString()
                                            maxLon = preset.maxLon.toString()
                                            maxLat = preset.maxLat.toString()
                                        }
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = regionName,
                        onValueChange = { regionName = it },
                        label = { Text("Nom de la région") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isCustom) {
                        Spacer(Modifier.height(4.dp))
                        Text("Saisir les Coordonnées Mondiales (-180 à 180 / -90 à 90) :", color = mutedColor, fontSize = 11.sp)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = minLon, onValueChange = { minLon = it }, label = { Text("Min Lon") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = minLat, onValueChange = { minLat = it }, label = { Text("Min Lat") }, modifier = Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = maxLon, onValueChange = { maxLon = it }, label = { Text("Max Lon") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = maxLat, onValueChange = { maxLat = it }, label = { Text("Max Lat") }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (regionName.isNotBlank()) {
                            val fMinLon = minLon.toDoubleOrNull() ?: selectedPreset.minLon
                            val fMinLat = minLat.toDoubleOrNull() ?: selectedPreset.minLat
                            val fMaxLon = maxLon.toDoubleOrNull() ?: selectedPreset.maxLon
                            val fMaxLat = maxLat.toDoubleOrNull() ?: selectedPreset.maxLat

                            viewModel.downloadRegion(regionName, fMinLon, fMinLat, fMaxLon, fMaxLat)
                            showDownloadDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Lancer le téléchargement")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}
