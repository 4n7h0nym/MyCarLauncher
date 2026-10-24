package com.openlauncher.app.ui.screen

import com.openlauncher.app.ui.components.LocalizedText as LocalizedLabel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openlauncher.app.model.OfflineRegion
import com.openlauncher.app.viewmodel.TileRegionDownloadViewModel

@Composable
fun OfflineManagerScreen(
    accent: Color,
    modifier: Modifier = Modifier,
    viewModel: TileRegionDownloadViewModel = viewModel()
) {
    val cardBg     = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outline
    val textColor  = MaterialTheme.colorScheme.onBackground
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    var regionQuery by remember { mutableStateOf("") }
    var isRegionSearchFocused by remember { mutableStateOf(false) }
    var searchRowBounds by remember { mutableStateOf<Rect?>(null) }
    val focusManager = LocalFocusManager.current
    val displayedRegions = if (regionQuery.isBlank()) {
        viewModel.downloadedOrDownloadingRegions
    } else {
        viewModel.regions
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(isRegionSearchFocused, searchRowBounds) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                    val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    if (up != null && isRegionSearchFocused && searchRowBounds?.contains(up.position) != true) {
                        focusManager.clearFocus()
                    }
                }
            }
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Public, null, tint = accent, modifier = Modifier.size(28.dp))
                Column {
                    LocalizedLabel(
                        text = "GESTIONNAIRE DE CARTES HORS-LIGNE MAPBOX",
                        style = MaterialTheme.typography.titleLarge,
                        color = textColor,
                        fontSize = 16.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LocalizedLabel(
                        text = "TileStore Mapbox - Téléchargement de régions mondiales",
                        color = mutedColor,
                        fontSize = 11.sp
                    )
                }
            }

            TextButton(
                onClick = { viewModel.clearAllRegions() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                LocalizedLabel("TOUT EFFACER", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
            // Regions List Column
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LocalizedLabel(
                    text = if (regionQuery.isBlank()) {
                        "RÉGIONS TÉLÉCHARGÉES OU EN COURS (${displayedRegions.size})"
                    } else {
                        "RÉSULTATS DE RECHERCHE (${displayedRegions.size})"
                    },
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth().onGloballyPositioned { searchRowBounds = it.boundsInRoot() },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = regionQuery,
                        onValueChange = { regionQuery = it },
                        modifier = Modifier.weight(1f).onFocusChanged { isRegionSearchFocused = it.isFocused },
                        singleLine = true,
                        label = { LocalizedLabel("Pays, région ou ville") },
                        placeholder = { LocalizedLabel("Canada, Québec, Montréal…") }
                    )
                    Button(
                        onClick = { viewModel.searchRegions(regionQuery) },
                        enabled = !viewModel.isSearchingRegions && regionQuery.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        if (viewModel.isSearchingRegions) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = "Rechercher")
                        }
                    }
                }
                viewModel.regionSearchError?.let { error ->
                    LocalizedLabel(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }

                if (displayedRegions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        LocalizedLabel(
                            text = if (regionQuery.isBlank()) {
                                "Aucune région téléchargée ou en cours de téléchargement. Recherchez une région pour la télécharger."
                            } else {
                                "Aucune région à afficher. Lancez la recherche."
                            },
                            color = mutedColor,
                            fontSize = 12.sp
                        )
                    }
                } else LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayedRegions, key = { it.id }) { region ->
                        RegionCard(
                            region = region,
                            isDownloading = viewModel.downloadingRegions.contains(region.id),
                            progress = viewModel.downloadProgress[region.id] ?: 0f,
                            refreshTrigger = viewModel.refreshTrigger,
                            isDownloaded = viewModel.downloadedRegions[region.id] ?: false,
                            sizeInMB = viewModel.regionSizes[region.id] ?: 0.0,
                            viewModel = viewModel,
                            accent = accent,
                            cardBorder = cardBorder,
                            textColor = textColor,
                            mutedColor = mutedColor
                        )
                    }
                }
            }

            // Status Column
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
                    LocalizedLabel(
                        text = "ÉTAT DU TILESTORE MAPBOX",
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    LocalizedLabel(
                        text = "Le TileStore Mapbox gère le stockage local des tuiles vectorielles et des styles pour garantir une navigation hors-ligne fluide et rapide.",
                        color = mutedColor,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }

                if (viewModel.downloadingRegions.isNotEmpty()) {
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
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = accent)
                            LocalizedLabel(
                                "Téléchargement de région(s) en cours...",
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF00C853).copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00C853), RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00C853), modifier = Modifier.size(24.dp))
                            LocalizedLabel(
                                "TileStore Mapbox actif et opérationnel.",
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
}

@Composable
fun RegionCard(
    region: OfflineRegion,
    isDownloading: Boolean,
    progress: Float,
    refreshTrigger: Boolean,
    isDownloaded: Boolean,
    sizeInMB: Double,
    viewModel: TileRegionDownloadViewModel,
    accent: Color,
    cardBorder: Color,
    textColor: Color,
    mutedColor: Color
) {
    var showDeleteConfirmation by remember(region.id) { mutableStateOf(false) }

    LaunchedEffect(key1 = isDownloading, key2 = refreshTrigger) {
        if (!isDownloading) {
            viewModel.checkIfDownloaded(region.id)
        }
    }

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
                    LocalizedLabel(
                        text = "${region.name} · ${region.regionType}",
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LocalizedLabel(
                        text = "Détails hors-ligne jusqu’au zoom ${region.maxZoom}",
                        color = mutedColor,
                        fontSize = 10.sp
                    )
                    when {
                        isDownloaded -> LocalizedLabel("Téléchargé • ${String.format(java.util.Locale.US, "%.1f", sizeInMB)} Mo", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        isDownloading -> LocalizedLabel("Téléchargement en cours...", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        else -> LocalizedLabel("Non téléchargé", color = mutedColor, fontSize = 12.sp)
                    }
                    viewModel.regionDownloadErrors[region.id]?.let { error ->
                        LocalizedLabel("Mise à jour échouée : $error", color = MaterialTheme.colorScheme.error, fontSize = 10.sp, maxLines = 2)
                    }
                    viewModel.regionRemovalErrors[region.id]?.let { error ->
                        LocalizedLabel("Suppression échouée : $error", color = MaterialTheme.colorScheme.error, fontSize = 10.sp, maxLines = 2)
                    }
                }
            }

            when {
                isDownloading -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(36.dp), color = accent)
                            Spacer(modifier = Modifier.height(2.dp))
                            LocalizedLabel("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = accent, fontFamily = FontFamily.Monospace)
                        }
                        OutlinedButton(
                            onClick = { viewModel.cancelDownload(region.id) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            LocalizedLabel("Annuler", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                isDownloaded -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.downloadRegion(region) },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            LocalizedLabel("Mettre à jour", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer ${region.name}", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                else -> {
                    Button(
                        onClick = { viewModel.downloadRegion(region) },
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        LocalizedLabel("Télécharger", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { LocalizedLabel("Supprimer la région ?") },
            text = { LocalizedLabel("Les données hors ligne de ${region.name} seront supprimées de cet appareil.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.removeRegion(region)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { LocalizedLabel("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { LocalizedLabel("Annuler") }
            }
        )
    }
}
