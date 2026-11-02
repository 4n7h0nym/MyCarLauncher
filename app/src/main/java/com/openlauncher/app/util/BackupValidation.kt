package com.openlauncher.app.util

/** Gson can deserialize missing non-null fields as null; reject the complete document before any write. */
object BackupValidation {
    fun isValid(backup: LauncherBackup?): Boolean = runCatching {
        requireNotNull(backup)
        require(backup.version == 1)
        val s = requireNotNull(backup.settings)
        require(s.uiScale.isFinite() && s.uiScale in 0.7f..1.5f)
        require(s.textScale.isFinite() && s.textScale in 0.5f..3f)
        require(s.wallpaperDim.isFinite() && s.wallpaperDim in 0f..1f)
        requireNotNull(s.visualTheme)
        require(s.wallpaperBlur.isFinite() && s.wallpaperBlur in 0f..24f)
        require(s.dockFavoriteSlot in -1..3)
        requireNotNull(s.appLanguage); requireNotNull(s.dayNightMode); requireNotNull(s.mapThemeMode)
        requireNotNull(s.clockStyle); requireNotNull(s.unitSystem); requireNotNull(s.appFont)
        requireNotNull(s.sidebarPosition); requireNotNull(s.gradientDirection)
        requireNotNull(s.lastDestination); requireNotNull(s.vehicleName); requireNotNull(s.wallpaperUri)
        requireNotNull(s.homeAddress); requireNotNull(s.workAddress)
        requireNotNull(s.carPlayPackage); requireNotNull(s.androidAutoPackage)
        requireNotNull(s.pipAppPackage); requireNotNull(s.radioPackage); requireNotNull(s.mediaPlayerPackage)
        s.shortcuts.forEach { requireNotNull(it.packageName); requireNotNull(it.label); requireNotNull(it.defaultIcon) }
        s.soundboardPads.forEach { requireNotNull(it.label); requireNotNull(it.audioUri); requireNotNull(it.synthType) }
        s.steeringWheelBindings.forEach { requireNotNull(it.action) }
        s.favoritePhoneNumbers.forEach { requireNotNull(it) }
        s.widgetLayout.forEach {
            require(it.spanX in 1..3 && it.spanY in 1..2 && it.gridX in 0..2 && it.gridY in 0..1)
            require(it.gridX + it.spanX <= 3 && it.gridY + it.spanY <= 2)
            requireNotNull(it.id)
        }
        s.savedPlaces.forEach {
            requireNotNull(it.id); requireNotNull(it.label); requireNotNull(it.address); requireNotNull(it.iconName)
            it.point?.let { point -> require(validCoordinates(point.latitude, point.longitude)) }
        }
        backup.savedDestinations.forEach {
            require(it.id.isNotBlank()); requireNotNull(it.label); requireNotNull(it.address); requireNotNull(it.iconName)
            require(validCoordinates(it.latitude, it.longitude))
        }
        backup.offlineRegions.forEach {
            require(it.id.isNotBlank()); requireNotNull(it.name); requireNotNull(it.status)
            require(validCoordinates(it.minLat, it.minLon) && validCoordinates(it.maxLat, it.maxLon))
            require(it.minLat <= it.maxLat && it.minLon <= it.maxLon && it.sizeMb.isFinite() && it.sizeMb >= 0)
        }
        true
    }.getOrDefault(false)
}
