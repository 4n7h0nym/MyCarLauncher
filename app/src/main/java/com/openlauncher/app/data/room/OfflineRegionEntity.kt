package com.openlauncher.app.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_regions")
data class OfflineRegionEntity(

    @PrimaryKey
    val id: String,

    val name: String,

    val minLon: Double,
    val minLat: Double,

    val maxLon: Double,
    val maxLat: Double,

    val sizeMb: Float,

    val tileCount: Int,

    val status: String,

    val timestamp: Long = System.currentTimeMillis()
)