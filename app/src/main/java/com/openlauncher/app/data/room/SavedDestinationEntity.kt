package com.openlauncher.app.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_destinations")
data class SavedDestinationEntity(
    @PrimaryKey val id: String,
    val label: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val iconName: String
)
