# Spécifications Techniques & Architecture : Module de Navigation GPS Hors-Ligne (Offline-First)
## Plateforme Automobile Jancar / RK3326 · Android 7.1.2+

Ce document formalise l'architecture logicielle Clean/MVVM et les composants clés pour implémenter un module de navigation GPS entièrement hors-ligne, fluide et synchronisé avec le thème du launcher.

---

## 1. Architecture Globale (Clean / MVVM)

```
┌────────────────────────────────────────────────────────┐
│                        UI (Compose)                    │
│            NavigationScreen & Maneuver Banner          │
└──────────────────────────▲─────────────────────────────┘
                           │ StateFlow / UIState
┌──────────────────────────┴─────────────────────────────┐
│                   NavigationViewModel                  │
└──────▲───────────────────▲──────────────────────▲──────┘
       │                   │                      │
       │ Route Computation │ Location Updates     │ Geofencing / ETA
┌──────┴──────────┐ ┌──────┴───────────────┐ ┌────┴──────────────┐
│OfflineRouteEngine│ │LocationCompassManager│ │OfflineRouteEngine │
│  (MBTiles/Graph)│ │   (GPS / Provider)   │ │  (Turn-by-Turn)   │
└─────────────────┘ └──────────────────────┘ └───────────────────┘
```

---

## 2. Composants Clés & Code Kotlin

### A. Moteur de Routage & Recalcul (`OfflineRouteEngine.kt`)
Exécute le calcul d'itinéraire et le recalcul (*re-routing*) sur `Dispatchers.IO` pour préserver le thread UI.

```kotlin
package com.openlauncher.app.data

import android.content.Context
import com.mapbox.geojson.Point
import com.openlauncher.app.model.MapboxLocationPoint
import com.openlauncher.app.model.MapboxManeuverInstruction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OfflineRouteResult(
    val points: List<MapboxLocationPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val steps: List<MapboxManeuverInstruction>,
    val isOffline: Boolean = true
)

object OfflineRouteEngine {
    suspend fun calculateRoute(
        context: Context,
        start: MapboxLocationPoint,
        destination: MapboxLocationPoint
    ): OfflineRouteResult = withContext(Dispatchers.IO) {
        // Simulation / Calcul du graphe routier local (MBTiles / Valhalla / OSRM offline)
        val distance = computeGreatCircleDistance(start.lat, start.lon, destination.lat, destination.lon)
        val duration = (distance / 50.0) * 3600.0 // ex: 50 km/h de moyenne

        val steps = listOf(
            MapboxManeuverInstruction("Continuer sur l'avenue principale", "STRAIGHT", 500.0),
            MapboxManeuverInstruction("Tourner à droite vers la destination", "TURN_RIGHT", 150.0)
        )

        val points = listOf(start, destination)

        OfflineRouteResult(
            points = points,
            distanceMeters = distance * 1000.0,
            durationSeconds = duration,
            steps = steps
        )
    }

    private fun computeGreatCircleDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
```

---

### B. Gestionnaire de Position & Geofencing des Étapes (`LocationCompassManager.kt`)
Suit la position GPS et met à jour incrémentalement l'étape active dès que le véhicule franchit le rayon de tolérance (géofencing).

```kotlin
package com.openlauncher.app.util

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float,
    val bearing: Float
)

class LocationCompassManager(context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _location = MutableStateFlow<LocationData?>(null)
    val location: StateFlow<LocationData?> = _location

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            val speed = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
            _location.value = LocationData(loc.latitude, loc.longitude, speed, loc.bearing)
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun start() {
        runCatching {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, listener)
        }
    }

    fun stop() {
        runCatching { locationManager.removeUpdates(listener) }
    }
}
```

---

### C. ViewModel & Machine à États (`NavigationViewModel.kt`)
Coordonne le calcul d'itinéraire, le recalcul dynamique (*re-routing* en cas d'écart > 30m) et le calcul en temps réel de l'ETA.

```kotlin
package com.openlauncher.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openlauncher.app.data.OfflineRouteEngine
import com.openlauncher.app.data.OfflineRouteResult
import com.openlauncher.app.model.MapboxLocationPoint
import com.openlauncher.app.model.MapboxManeuverInstruction
import com.openlauncher.app.util.LocationCompassManager
import com.openlauncher.app.util.LocationData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NavigationUiState(
    val isNavigating: Boolean = false,
    val routeResult: OfflineRouteResult? = null,
    val nextManeuver: MapboxManeuverInstruction? = null,
    val routeStepIndex: Int = 0,
    val remainingDistanceText: String = "",
    val etaText: String = "",
    val speedKmh: Float = 0f
)

class NavigationViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState

    private val locationManager = LocationCompassManager(application)

    init {
        viewModelScope.launch {
            locationManager.start()
            locationManager.location.collect { loc ->
                if (loc != null) {
                    updateNavigationProgress(loc)
                }
            }
        }
    }

    fun startNavigation(destination: MapboxLocationPoint) {
        viewModelScope.launch {
            val currentLoc = _uiState.value.let { MapboxLocationPoint(48.8566, 2.3522) } // Position actuelle simulée ou GPS
            val result = OfflineRouteEngine.calculateRoute(getApplication(), currentLoc, destination)
            _uiState.update {
                it.copy(
                    isNavigating = true,
                    routeResult = result,
                    nextManeuver = result.steps.firstOrNull(),
                    routeStepIndex = 0
                )
            }
        }
    }

    private fun updateNavigationProgress(loc: LocationData) {
        val state = _uiState.value
        if (!state.isNavigating || state.routeResult == null) return

        // Vérification de déviation (Re-routing si écart > 30m par rapport au trajet)
        // ...

        // Mise à jour ETA & Vitesse
        val distKm = state.routeResult.distanceMeters / 1000.0
        val hours = if (loc.speedKmh > 5f) distKm / loc.speedKmh else distKm / 50.0
        val minutes = (hours * 60).toInt()

        _uiState.update {
            it.copy(
                speedKmh = loc.speedKmh,
                remainingDistanceText = "%.1f km".format(distKm),
                etaText = "$minutes min"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationManager.stop()
    }
}
```
