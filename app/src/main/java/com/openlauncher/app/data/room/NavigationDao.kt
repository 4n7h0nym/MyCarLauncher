package com.openlauncher.app.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NavigationDao {

    @Query("""
        SELECT * FROM recent_destinations
        ORDER BY timestamp DESC
        LIMIT 10
    """)
    fun getRecentDestinations(): Flow<List<RecentDestinationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(
        destination: RecentDestinationEntity
    )

    @Query("""
        DELETE FROM recent_destinations
        WHERE id = :id
    """)
    suspend fun deleteRecent(id: Long)

    @Query("""
        SELECT * FROM saved_destinations
    """)
    fun getSavedDestinations():
            Flow<List<SavedDestinationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaved(
        destination: SavedDestinationEntity
    )

    @Query("""
        DELETE FROM saved_destinations
        WHERE id = :id
    """)
    suspend fun deleteSaved(id: String)

    @Query("""
        SELECT * FROM offline_regions
        ORDER BY timestamp DESC
    """)
    fun getOfflineRegions():
            Flow<List<OfflineRegionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOfflineRegion(
        region: OfflineRegionEntity
    )

    @Query("""
        DELETE FROM offline_regions
        WHERE id = :id
    """)
    suspend fun deleteOfflineRegion(
        id: String
    )

    @Query("""
        SELECT * FROM offline_regions
        WHERE :longitude >= minLon
        AND :longitude <= maxLon
        AND :latitude >= minLat
        AND :latitude <= maxLat
        LIMIT 1
    """)
    suspend fun findOfflineRegionForPosition(
        longitude: Double,
        latitude: Double
    ): OfflineRegionEntity?
}