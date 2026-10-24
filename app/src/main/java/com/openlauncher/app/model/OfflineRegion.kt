package com.openlauncher.app.model

import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.CoordinateBounds

data class OfflineRegion(
    val id: String,
    val name: String,
    val bounds: CoordinateBounds,
    val minZoom: Int = 0,
    val maxZoom: Int = 16,
    val regionType: String = "Région",
    val polygon: Polygon = Polygon.fromLngLats(
        listOf(
            listOf(
                Point.fromLngLat(bounds.northeast.longitude(), bounds.northeast.latitude()),
                Point.fromLngLat(bounds.southwest.longitude(), bounds.northeast.latitude()),
                Point.fromLngLat(bounds.southwest.longitude(), bounds.southwest.latitude()),
                Point.fromLngLat(bounds.northeast.longitude(), bounds.southwest.latitude()),
                Point.fromLngLat(bounds.northeast.longitude(), bounds.northeast.latitude())
            )
        )
    )
)
