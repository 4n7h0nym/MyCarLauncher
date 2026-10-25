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
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.api.directions.v5.models.RouteOptions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class OfflineRouteResult(
    val points: List<MapboxLocationPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val steps: List<MapboxManeuverInstruction>,
    val isOffline: Boolean = false
)

/** Shared Mapbox routing instance configured to use the same offline TileStore as the map. */
object OfflineRouteEngine {
    private const val TAG = "OfflineRouteEngine"
    @Volatile private var navigation: MapboxNavigation? = null
    @Volatile private var locationProvider: NavigationLocationProvider? = null
    @Volatile private var onMatchedLocation: ((LocationMatcherResult) -> Unit)? = null
    private val _routeUpdates = MutableSharedFlow<OfflineRouteResult>(extraBufferCapacity = 1)
    val routeUpdates = _routeUpdates.asSharedFlow()
    private val _routeProgressIndexes = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val routeProgressIndexes = _routeProgressIndexes.asSharedFlow()
    private val _activeRoute = MutableStateFlow<NavigationRoute?>(null)
    val activeRoute = _activeRoute.asStateFlow()

    private val routesObserver = RoutesObserver { result ->
        val route = result.navigationRoutes.firstOrNull()
        _activeRoute.value = route
        route?.directionsRoute?.let { directions ->
            convertRoute(directions)?.let { _routeUpdates.tryEmit(it) }
        }
    }

    private val routeProgressObserver = RouteProgressObserver { progress ->
        _routeProgressIndexes.tryEmit(progress.currentRouteGeometryIndex)
    }

    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) = Unit

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            locationProvider?.changePosition(
                locationMatcherResult.enhancedLocation,
                locationMatcherResult.keyPoints
            )
            onMatchedLocation?.invoke(locationMatcherResult)
        }
    }

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
        return MapboxNavigationProvider.create(options).also { nav ->
            navigation = nav
            nav.registerRoutesObserver(routesObserver)
            nav.registerRouteProgressObserver(routeProgressObserver)
            nav.registerLocationObserver(locationObserver)
            nav.setRerouteEnabled(true)
        }
    }

    fun attachMap(locationProvider: NavigationLocationProvider, onMatched: (LocationMatcherResult) -> Unit) {
        this.locationProvider = locationProvider
        this.onMatchedLocation = onMatched
    }

    fun detachMap(locationProvider: NavigationLocationProvider) {
        if (this.locationProvider === locationProvider) {
            this.locationProvider = null
            this.onMatchedLocation = null
        }
    }

    fun stopGuidance() {
        navigation?.let { nav ->
            nav.stopTripSession()
            nav.setNavigationRoutes(emptyList())
        }
        _activeRoute.value = null
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
                    .steps(true)
                    .language("fr-CA")
                    .build()
                nav.requestRoutes(options, object : NavigationRouterCallback {
                    override fun onRoutesReady(
                        routes: List<com.mapbox.navigation.base.route.NavigationRoute>,
                        routerOrigin: String
                    ) {
                        Log.i(TAG, "Route response from $routerOrigin (${routes.size} route(s))")
                        if (!continuation.isActive) return
                        val activeRoute = routes.firstOrNull()
                        val result = activeRoute?.directionsRoute?.let(::convertRoute)?.copy(
                            isOffline = routerOrigin.contains("offline", ignoreCase = true)
                        )
                        if (activeRoute == null || result == null || result.points.size < 2) {
                            if (continuation.isActive) continuation.resume(null)
                            return
                        }
                        // Register the exact route we return to the UI so progress, map matching,
                        // and SDK-managed offline rerouting all operate on the same route.
                        nav.setNavigationRoutes(routes)
                        nav.startTripSession(false)
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

    private fun convertRoute(directions: com.mapbox.api.directions.v5.models.DirectionsRoute): OfflineRouteResult? {
        val line = directions.geometry()?.let {
            runCatching { LineString.fromPolyline(it, 6).coordinates() }.getOrNull()
        }
        val points = line?.map { MapboxLocationPoint(it.latitude(), it.longitude()) }.orEmpty()
        if (points.size < 2) return null
        val steps = directions.legs().orEmpty().flatMap { it.steps().orEmpty() }.mapNotNull { step ->
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
        return OfflineRouteResult(
            points = points,
            distanceMeters = directions.distance() ?: 0.0,
            durationSeconds = directions.duration() ?: 0.0,
            steps = steps
        )
    }
}
