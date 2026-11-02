package com.openlauncher.app.util

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.room.NavigationDatabase
import com.openlauncher.app.data.room.OfflineRegionEntity
import com.openlauncher.app.data.room.SavedDestinationEntity
import androidx.room.withTransaction
import com.openlauncher.app.data.SettingsRepository
import kotlinx.coroutines.CancellationException
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
        try {
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun importBackup(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val backup = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                    gson.fromJson(reader, LauncherBackup::class.java)
                }
            } ?: return@withContext false

            if (!BackupValidation.isValid(backup)) return@withContext false
            val db = NavigationDatabase.getDatabase(context)
            val dao = db.navigationDao()

            db.withTransaction {
            for (dest in backup.savedDestinations) {
                dao.insertSaved(dest)
            }

            for (region in backup.offlineRegions) {
                dao.insertOfflineRegion(region)
            }

            }
            // Await the persisted preference write before reporting success to the UI.
            SettingsRepository(context.applicationContext).saveSettings(backup.settings)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}
