package com.openlauncher.app.data

import android.content.Context
import android.util.Log
import com.mapbox.common.MapboxOptions
import com.mapbox.common.TileStore
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.openlauncher.app.model.MapboxLocationPoint
import com.openlauncher.app.model.MapboxManeuverInstruction
import com.mapbox.maps.mapsOptions
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.options.RoutingTilesOptions
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.api.directions.v5.models.RouteOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class OfflineRouteResult(
    val points: List<MapboxLocationPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val steps: List<MapboxManeuverInstruction>
)

/** Shared Mapbox routing instance configured to use the same offline TileStore as the map. */
object OfflineRouteEngine {
    private const val TAG = "OfflineRouteEngine"
    @Volatile private var navigation: MapboxNavigation? = null

    @Synchronized
    private fun getNavigation(context: Context): MapboxNavigation {
        navigation?.let { return it }
        val tileStore: TileStore = MapboxOptions.mapsOptions.tileStore
            ?: error("Le stockage des cartes hors ligne n'est pas disponible")
        val token = context.applicationContext.getString(com.openlauncher.app.R.string.mapbox_access_token)
        // Navigation SDK v3 uses the shared MapboxOptions token (the same
        // existing resource already used by the Maps SDK).
        MapboxOptions.accessToken = token
        val options = NavigationOptions.Builder(context.applicationContext)
            .routingTilesOptions(RoutingTilesOptions.Builder().tileStore(tileStore).build())
            .build()
        return MapboxNavigationProvider.create(options).also { navigation = it }
    }

    fun tilesetDescriptor(context: Context) = getNavigation(context).tilesetDescriptorFactory.getLatest()

    suspend fun route(context: Context, origin: Point, destination: Point): OfflineRouteResult? =
        withContext(Dispatchers.Main.immediate) {
            val nav = try {
                getNavigation(context)
            } catch (error: Throwable) {
                Log.e(TAG, "Could not initialize offline router", error)
                return@withContext null
            }
            suspendCancellableCoroutine { continuation ->
                val options = RouteOptions.builder()
                    .applyDefaultNavigationOptions()
                    .coordinatesList(listOf(origin, destination))
                    .build()
                nav.requestRoutes(options, object : NavigationRouterCallback {
                    override fun onRoutesReady(
                        routes: List<com.mapbox.navigation.base.route.NavigationRoute>,
                        routerOrigin: String
                    ) {
                        Log.i(TAG, "Route response from $routerOrigin (${routes.size} route(s))")
                        val directions = routes.firstOrNull()?.directionsRoute
                        val geometry = directions?.geometry()
                        val line = geometry?.let { runCatching { LineString.fromPolyline(it, 6).coordinates() }.getOrNull() }
                        val points = line?.map { MapboxLocationPoint(it.latitude(), it.longitude()) }.orEmpty()
                        if (directions == null || points.size < 2) {
                            if (continuation.isActive) continuation.resume(null)
                            return
                        }

                        val steps = directions.legs().orEmpty()
                            .flatMap { it.steps().orEmpty() }
                            .mapNotNull { step ->
                                val maneuver = step.maneuver() ?: return@mapNotNull null
                                val instruction = maneuver.instruction()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                val location = maneuver.location()
                                MapboxManeuverInstruction(
                                    instruction = instruction,
                                    iconType = "${maneuver.type().orEmpty()} ${maneuver.modifier().orEmpty()}".uppercase(),
                                    distanceMeters = step.distance() ?: 0.0,
                                    maneuverPoint = location?.let { MapboxLocationPoint(it.latitude(), it.longitude()) }
                                )
                            }
                        val result = OfflineRouteResult(
                            points = points,
                            distanceMeters = directions.distance() ?: 0.0,
                            durationSeconds = directions.duration() ?: 0.0,
                            steps = steps
                        )
                        if (continuation.isActive) continuation.resume(result)
                    }

                    override fun onFailure(
                        reasons: List<com.mapbox.navigation.base.route.RouterFailure>,
                        routeOptions: RouteOptions
                    ) {
                        Log.w(TAG, "Offline route request failed: $reasons")
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
                continuation.invokeOnCancellation { /* Ignore late callback after cancellation. */ }
            }
        }
}
