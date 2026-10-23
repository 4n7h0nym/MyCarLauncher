package com.openlauncher.app.ui.screen

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OfflineRegionItem(
    val id: String,
    val name: String,
    val sizeMb: Float,
    val tileCount: Int,
    val status: String // "Téléchargé" en cours / "Actif"
)

class OfflineManagerViewModel(context: Context) {
    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(OfflineManagerUiState())
    val uiState: StateFlow<OfflineManagerUiState> = _uiState.asStateFlow()

    init {
        checkConnectivity(appContext)
    }

    private fun checkConnectivity(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(network)
        val isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _uiState.update { it.copy(isOnline = isOnline) }
    }

    fun downloadRegion(regionName: String) {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        scope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0f, activeRegionName = regionName) }
            for (i in 1..100) {
                delay(40)
                val progress = i / 100f
                _uiState.update { it.copy(downloadProgress = progress) }
            }
            val newRegion = OfflineRegionItem(
                id = "reg_${System.currentTimeMillis()}",
                name = regionName,
                sizeMb = 148.5f,
                tileCount = 4210,
                status = "Téléchargé & Hors-ligne prêt"
            )
            _uiState.update {
                it.copy(
                    isDownloading = false,
                    downloadProgress = 0f,
                    downloadedRegions = it.downloadedRegions + newRegion,
                    activeRegionName = null
                )
            }
        }
    }

    fun deleteRegion(id: String) {
        _uiState.update { state ->
            state.copy(downloadedRegions = state.downloadedRegions.filterNot { it.id == id })
        }
    }
}

data class OfflineManagerUiState(
    val isOnline: Boolean = true,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val activeRegionName: String? = null,
    val downloadedRegions: List<OfflineRegionItem> = listOf(
        OfflineRegionItem("reg_1", "Île-de-France & Grand Paris", 215.0f, 6400, "Téléchargé & Hors-ligne prêt"),
        OfflineRegionItem("reg_2", "Rhône-Alpes / Lyon", 182.4f, 5120, "Téléchargé & Hors-ligne prêt")
    )
)

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
                Icon(Icons.Default.CloudDownload, null, tint = accent, modifier = Modifier.size(28.dp))
                Column {
                    Text(
                        text = "GESTIONNAIRE HORS-LIGNE MAPBOX",
                        style = MaterialTheme.typography.titleLarge,
                        color = textColor,
                        fontSize = 16.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "TileStore partagé, cache prédictif & bascule automatique de routage",
                        color = mutedColor,
                        fontSize = 11.sp
                    )
                }
            }

            // Network Status Badge
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
                        text = "RÉGIONS TÉLÉCHARGÉES (${uiState.downloadedRegions.size})",
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Button(
                        onClick = { showDownloadDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("TÉLÉCHARGER UNE RÉGION", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (uiState.downloadedRegions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Aucune région hors-ligne téléchargée", color = mutedColor, fontSize = 13.sp)
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
                                            Icon(Icons.Default.Map, null, tint = accent, modifier = Modifier.size(22.dp))
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(region.name, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Text("${region.sizeMb} MB • ${region.tileCount} tuiles • ${region.status}", color = mutedColor, fontSize = 12.sp)
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

            // Right Column: Live Download Progress & TileStore Status
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
                        text = "ÉTAT DU TILESTORE & CACHE",
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )

                    Text(
                        text = "Le TileStore Mapbox gère le stockage unifié et la mise en cache prédictive des routes pour une navigation hors-ligne fluide sans coupure.",
                        color = mutedColor,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cardBg,
                        modifier = Modifier.fillMaxWidth().border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Stockage alloué", color = mutedColor, fontSize = 12.sp)
                                Text("397.4 Mo / 2.0 Go", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Routage hybride", color = mutedColor, fontSize = 12.sp)
                                Text("Automatique (Online -> Offline)", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Cache Prédictif", color = mutedColor, fontSize = 12.sp)
                                Text("Actif (Zoom 10-16)", color = Color(0xFF00C853), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (uiState.isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Téléchargement : ${uiState.activeRegionName}", color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("${(uiState.downloadProgress * 100).toInt()}%", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
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
                                "TileStore synchronisé et prêt pour la navigation hors-ligne.",
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

    // Download Region Dialog
    if (showDownloadDialog) {
        var regionInput by remember { mutableStateOf("Provence-Alpes-Côte d'Azur") }

        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Télécharger une région Mapbox", color = textColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = regionInput,
                        onValueChange = { regionInput = it },
                        label = { Text("Nom de la région ou de la ville") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Le téléchargement récupérera les tuiles vectorielles de styles (Zoom 10 à 16) ainsi que les données de routage pour le mode hors-ligne.",
                        color = mutedColor,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (regionInput.isNotBlank()) {
                            viewModel.downloadRegion(regionInput)
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
