package com.openlauncher.app.data

import android.content.Context
import android.util.Log
import com.mapbox.bindgen.Value
import com.mapbox.geojson.Geometry
import com.mapbox.maps.Style
import com.mapbox.common.TileRegion
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileStore
import com.mapbox.common.Cancelable
import com.mapbox.maps.TilesetDescriptorOptions
import com.mapbox.maps.StylePackLoadOptions
import java.util.concurrent.atomic.AtomicInteger
import com.mapbox.maps.GlyphsRasterizationMode
import com.mapbox.maps.OfflineManager
import com.mapbox.common.MapboxOptions
import com.mapbox.maps.mapsOptions
import com.openlauncher.app.model.OfflineRegion

object OfflineRegionManager {
    private const val TAG = "OfflineRegionManager"
    private val offlineManager: OfflineManager = OfflineManager()
    private val tileStore: TileStore? = MapboxOptions.mapsOptions.tileStore

    fun ensureStylePackDownloaded(context: Context) {
        listOf(Style.MAPBOX_STREETS, Style.DARK).distinct().forEach { styleUri ->
            try {
                val stylePackOptions = StylePackLoadOptions.Builder()
                    .glyphsRasterizationMode(GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY)
                    .metadata(Value("mapbox-stylepack-$styleUri"))
                    .acceptExpired(false)
                    .build()

                offlineManager.loadStylePack(
                    styleUri,
                    stylePackOptions,
                    { _ -> },
                    { expected ->
                        expected.value?.let { stylePack ->
                            Log.d(TAG, "Style pack downloaded for $styleUri: $stylePack")
                        }
                        expected.error?.let {
                            Log.w(TAG, "Style pack download failed for $styleUri: $it")
                        }
                    }
                )
            } catch (e: Throwable) {
                Log.w(TAG, "Could not download style pack for $styleUri: ${e.message}")
            }
        }
    }

    fun downloadRegion(
        context: Context,
        region: OfflineRegion,
        downloadingRegions: Set<String>,
        onDownloadingRegionsUpdate: (Set<String>) -> Unit,
        onProgress: (String, Float) -> Unit,
        onCompletion: (String, Result<TileRegion>) -> Unit
    ): Cancelable? {
        if (downloadingRegions.contains(region.id)) {
            Log.d(TAG, "Region ${region.id} is already downloading; skipping")
            return null
        }

        val mapDescriptors = listOf(Style.MAPBOX_STREETS, Style.DARK).distinct().map { styleUri ->
            val options = TilesetDescriptorOptions.Builder()
                .styleURI(styleUri)
                .minZoom(region.minZoom.toByte())
                .maxZoom(region.maxZoom.toByte())
                .build()
            offlineManager.createTilesetDescriptor(options)
        }
        // Maps tiles render the basemap; Navigation tiles contain the road
        // graph needed to calculate an offline route that follows roads.
        val tilesetDescriptors = try {
            mapDescriptors + OfflineRouteEngine.tilesetDescriptor(context)
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to initialize offline routing tiles", error)
            onCompletion(region.id, Result.failure(error))
            return null
        }

        val loadOptions: TileRegionLoadOptions = TileRegionLoadOptions.Builder()
            .geometry(region.polygon as Geometry)
            .descriptors(tilesetDescriptors)
            .metadata(Value(region.name))
            .acceptExpired(true)
            .build()

        val updated = downloadingRegions.toMutableSet()
        updated.add(region.id)
        onDownloadingRegionsUpdate(updated)
        onProgress(region.id, 0.0f)

        val ts = tileStore
        if (ts == null) {
            Log.e(TAG, "TileStore not available")
            val updatedAfter = updated.toMutableSet()
            updatedAfter.remove(region.id)
            onDownloadingRegionsUpdate(updatedAfter)
            onCompletion(region.id, Result.failure(Exception("TileStore not available")))
            return null
        }

        return ts.loadTileRegion(
            region.id,
            loadOptions,
            { progress ->
                val total = maxOf(progress.requiredResourceCount, 1)
                val progressVal = progress.completedResourceCount.toFloat() / total.toFloat()
                onProgress(region.id, progressVal)
            }
        ) { result ->
            val updatedAfter = updated.toMutableSet()
            updatedAfter.remove(region.id)
            onDownloadingRegionsUpdate(updatedAfter)

            if (result.isValue) {
                val tileRegion = result.value!!
                onCompletion(region.id, Result.success(tileRegion))
            } else {
                val rawError = result.error
                val throwable = when (rawError) {
                    null -> Exception("Unknown error")
                    else -> Exception(rawError.toString())
                }
                onCompletion(region.id, Result.failure(throwable))
            }
        }
    }

    fun removeRegion(regionId: String, onCompletion: (Result<TileRegion>) -> Unit) {
        val ts = tileStore
        if (ts == null) {
            onCompletion(Result.failure(IllegalStateException("TileStore not available")))
            return
        }
        ts.removeTileRegion(regionId) { result ->
            if (result.isValue && result.value != null) {
                onCompletion(Result.success(result.value!!))
            } else {
                onCompletion(Result.failure(IllegalStateException(result.error?.toString() ?: "Suppression impossible")))
            }
        }
    }

    fun clearAllRegions(onCompletion: () -> Unit) {
        val ts = tileStore
        if (ts == null) {
            onCompletion()
            return
        }
        ts.getAllTileRegions { result ->
            if (result.isValue) {
                val tileRegions = result.value ?: emptyList()
                if (tileRegions.isEmpty()) {
                    ts.clearAmbientCache { cacheResult ->
                        if (cacheResult.isValue) {
                            Log.i(TAG, "Cleared ${cacheResult.value} bytes from cache")
                        } else {
                            Log.e(TAG, "Failed to clear cache: ${cacheResult.error}")
                        }
                        onCompletion()
                    }
                    return@getAllTileRegions
                }

                val removalsPending = AtomicInteger(tileRegions.size)
                for (tileRegion in tileRegions) {
                    ts.removeTileRegion(tileRegion.id) { removeResult ->
                        if (removeResult.isValue) {
                            Log.i(TAG, "Removed region: ${tileRegion.id}")
                        } else {
                            Log.e(TAG, "Failed to remove region ${tileRegion.id}: ${removeResult.error}")
                        }
                        val remaining = removalsPending.decrementAndGet()
                        if (remaining <= 0) {
                            ts.clearAmbientCache { cacheResult ->
                                if (cacheResult.isValue) {
                                    Log.i(TAG, "Cleared ${cacheResult.value} bytes from cache")
                                } else {
                                    Log.e(TAG, "Failed to clear cache: ${cacheResult.error}")
                                }
                                onCompletion()
                            }
                        }
                    }
                }
            } else {
                Log.e(TAG, "Failed to get tile regions: ${result.error}")
                onCompletion()
            }
        }
    }
}
