package com.openlauncher.app.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_destinations")
data class RecentDestinationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)
