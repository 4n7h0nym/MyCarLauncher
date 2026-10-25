package com.openlauncher.app.model

/**
 * Coordonnées géographiques (Latitude / Longitude).
 */
data class MapboxLocationPoint(
    val latitude: Double,
    val longitude: Double
)

/**
 * Résultat de recherche ou de suggestion Autofill POI de Mapbox Search SDK.
 */
data class MapboxPoiResult(
    val id: String,
    val name: String,
    val category: String,
    val address: String,
    val point: MapboxLocationPoint,
    val distanceKm: Float = 0f
)

/**
 * Instruction de manœuvre pour la bannière de guidage supérieure.
 */
data class MapboxManeuverInstruction(
    val instruction: String,     // ex: "Tourner à droite dans 2 km"
    val iconType: String,         // "TURN_RIGHT", "TURN_LEFT", "STRAIGHT", "ROUNDABOUT", etc.
    val distanceMeters: Double,
    val maneuverPoint: MapboxLocationPoint? = null
)

/**
 * Informations de durée, distance et heure estimée d'arrivée (ETA).
 */
data class MapboxEtaInfo(
    val durationText: String,      // ex: "24 min"
    val etaTimeText: String,       // ex: "18:42"
    val distanceText: String       // ex: "18.4 km"
)

/**
 * Représente un lieu enregistré (Domicile, Travail, Favoris).
 */
data class SavedPlace(
    val id: String,
    val label: String,             // "Domicile", "Travail", "Gym", etc.
    val iconName: String,          // "HOME", "WORK", "STAR"
    val address: String,
    val point: MapboxLocationPoint? = null
)

/**
 * État réactif de l'interface utilisateur de navigation (Machine à états UI).
 */
data class NavigationUiState(
    val isNavigating: Boolean = false,
    val selectedMapPoi: MapboxPoiResult? = null,
    
    val isSplitSearchOpen: Boolean = false,
    val splitSearchQuery: String = "",
    val splitSuggestions: List<MapboxPoiResult> = emptyList(),

    // Recherche principale & Panneau Contextuel (État A)
    val searchQuery: String = "",
    val isSearchFocused: Boolean = false,
    val isSearching: Boolean = false,
    val suggestions: List<MapboxPoiResult> = emptyList(),
    val poiResults: List<MapboxPoiResult> = emptyList(),
    val isBottomSheetVisible: Boolean = false,
    val recentDestinations: List<MapboxPoiResult> = emptyList(),
    val nearbyPoiSuggestions: List<MapboxPoiResult> = emptyList(),

    // Trajet et guidage actif (État B)
    val selectedDestination: MapboxPoiResult? = null,
    val routePoints: List<MapboxLocationPoint> = emptyList(),
    val routeProgressIndex: Int = 0,
    val nextManeuver: MapboxManeuverInstruction? = null,
    val routeSteps: List<MapboxManeuverInstruction> = emptyList(),
    val routeStepIndex: Int = 0,
    val etaInfo: MapboxEtaInfo? = null,

    // Commandes de caméra
    val triggerRecenter: Long = 0L,
    val triggerOverview: Long = 0L,

    // État GPS et Hors-Ligne (TileStore)
    val hasLocationPermission: Boolean = false,
    val isOfflineMode: Boolean = false,
    val offlineCacheStatus: String = "Cache hors-ligne Mapbox TileStore actif",

    // Favoris et lieux enregistrés
    val savedPlaces: List<SavedPlace> = listOf(
        SavedPlace("home", "Domicile", "HOME", "Avenue des Champs-Élysées, Paris", MapboxLocationPoint(48.8698, 2.3075)),
        SavedPlace("work", "Travail", "WORK", "La Défense, Paris", MapboxLocationPoint(48.8924, 2.2361)),
        SavedPlace("fav_gym", "Salle de sport", "STAR", "Place de la Bastille, Paris", MapboxLocationPoint(48.8530, 2.3691))
    )
)
