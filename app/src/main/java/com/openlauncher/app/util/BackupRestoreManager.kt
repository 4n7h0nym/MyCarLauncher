package com.openlauncher.app.util

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.room.NavigationDatabase
import com.openlauncher.app.data.room.OfflineRegionEntity
import com.openlauncher.app.data.room.SavedDestinationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class LauncherBackup(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val settings: AppSettings,
    val savedDestinations: List<SavedDestinationEntity>,
    val offlineRegions: List<OfflineRegionEntity>
)

object BackupRestoreManager {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportBackup(context: Context, uri: Uri, settings: AppSettings): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val db = NavigationDatabase.getDatabase(context)
            val dao = db.navigationDao()
            val savedDestinations = dao.getSavedDestinations().first()
            val offlineRegions = dao.getOfflineRegions().first()

            val backup = LauncherBackup(
                settings = settings,
                savedDestinations = savedDestinations,
                offlineRegions = offlineRegions
            )

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    gson.toJson(backup, writer)
                }
            } ?: return@withContext false
            true
        }.getOrDefault(false)
    }

    suspend fun importBackup(context: Context, uri: Uri, onSettingsRestored: (AppSettings) -> Unit): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val backup = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                    gson.fromJson(reader, LauncherBackup::class.java)
                }
            } ?: return@withContext false

            val db = NavigationDatabase.getDatabase(context)
            val dao = db.navigationDao()

            for (dest in backup.savedDestinations) {
                dao.insertSaved(dest)
            }

            for (region in backup.offlineRegions) {
                dao.insertOfflineRegion(region)
            }

            withContext(Dispatchers.Main) {
                onSettingsRestored(backup.settings)
            }
            true
        }.getOrDefault(false)
    }
}
