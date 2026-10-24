package com.openlauncher.app.data.room

import android.content.Context
import com.openlauncher.app.model.MapboxLocationPoint
import com.openlauncher.app.model.MapboxPoiResult
import com.openlauncher.app.model.SavedPlace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NavigationRepository(context: Context) {
    private val dao = NavigationDatabase.getDatabase(context).navigationDao()

    val recentDestinations: Flow<List<MapboxPoiResult>> = dao.getRecentDestinations().map { entities ->
        entities.map {
            MapboxPoiResult(
                id = "recent_${it.id}",
                name = it.name,
                category = "Récent",
                address = it.address,
                point = MapboxLocationPoint(it.latitude, it.longitude)
            )
        }
    }

    val savedDestinations: Flow<List<SavedPlace>> = dao.getSavedDestinations().map { entities ->
        entities.map {
            SavedPlace(
                id = it.id,
                label = it.label,
                iconName = it.iconName,
                address = it.address,
                point = MapboxLocationPoint(it.latitude, it.longitude)
            )
        }
    }

    suspend fun addRecent(name: String, address: String, point: MapboxLocationPoint) {
        dao.insertRecent(
            RecentDestinationEntity(
                name = name,
                address = address,
                latitude = point.latitude,
                longitude = point.longitude
            )
        )
    }

    suspend fun saveDestination(savedPlace: SavedPlace) {
        val pt = savedPlace.point ?: return
        dao.insertSaved(
            SavedDestinationEntity(
                id = savedPlace.id,
                label = savedPlace.label,
                address = savedPlace.address,
                latitude = pt.latitude,
                longitude = pt.longitude,
                iconName = savedPlace.iconName
            )
        )
    }

    suspend fun deleteSaved(id: String) {
        dao.deleteSaved(id)
    }

    suspend fun replaceSavedPlaces(places: List<SavedPlace>) {
        dao.deleteAllSavedDestinations()
        places.forEach { place ->
            val point = place.point ?: return@forEach
            dao.insertSaved(
                SavedDestinationEntity(
                    id = place.id,
                    label = place.label,
                    address = place.address,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    iconName = place.iconName
                )
            )
        }
    }
}
