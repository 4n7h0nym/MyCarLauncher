package com.openlauncher.app.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RecentDestinationEntity::class, SavedDestinationEntity::class], version = 1, exportSchema = false)
abstract class NavigationDatabase : RoomDatabase() {
    abstract fun navigationDao(): NavigationDao

    companion object {
        @Volatile
        private var INSTANCE: NavigationDatabase? = null

        fun getDatabase(context: Context): NavigationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NavigationDatabase::class.java,
                    "navigation_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
