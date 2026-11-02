package com.openlauncher.app

import com.google.gson.Gson
import com.openlauncher.app.data.AppLanguage
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.defaultSavedPlaces
import com.openlauncher.app.data.room.SavedDestinationEntity
import com.openlauncher.app.model.NavDestination
import com.openlauncher.app.model.NavigationUiState
import com.openlauncher.app.ui.components.AppLocalization
import com.openlauncher.app.util.*
import org.junit.Assert.*
import org.junit.Test

class ReleaseRegressionTest {
    @Test fun restoredPageUsesSavedValue() { assertEquals(NavDestination.MEDIA, restoredDestination(true, "MEDIA")) }
    @Test fun disabledRestorationOpensHome() { assertEquals(NavDestination.HOME, restoredDestination(false, "MEDIA")) }
    @Test fun invalidRestoredPageOpensHome() { assertEquals(NavDestination.HOME, restoredDestination(true, "obsolete-page")) }
    @Test fun pickerCannotBeRestoredWithoutPickerState() { assertEquals(NavDestination.HOME, restoredDestination(true, "APP_LIBRARY")) }
    @Test fun coordinateBoundsAreInclusive() { assertTrue(validCoordinates(-90.0, 180.0)) }
    @Test fun invalidLatitudeIsRejected() { assertFalse(validCoordinates(91.0, 0.0)) }
    @Test fun nonFiniteCoordinateIsRejected() { assertFalse(validCoordinates(Double.NaN, 0.0)) }
    @Test fun firstCpuSampleHasNoInventedReading() { assertNull(CpuTimes(100, 40).usageSince(null)) }
    @Test fun cpuWaitIsIdleAndGuestIsNotCountedTwice() {
        assertEquals(CpuTimes(36, 9), CpuTimes.parse("cpu 1 2 3 4 5 6 7 8 100 100"))
    }
    @Test fun cpuUsageUsesCounterDelta() { assertEquals(75f, CpuTimes(200, 65).usageSince(CpuTimes(100, 40))!!, 0.001f) }
    @Test fun resetCountersDoNotProduceFakeLoad() { assertNull(CpuTimes(10, 5).usageSince(CpuTimes(100, 50))) }
    @Test fun malformedCpuSampleIsUnavailable() { assertNull(CpuTimes.parse("cpu inaccessible")) }
    @Test fun offlineDefaultsDoNotContainDemoPlaces() { assertTrue(defaultSavedPlaces().isEmpty()); assertTrue(NavigationUiState().savedPlaces.isEmpty()) }
    @Test fun shortFrenchLabelsAreNotLostToLanguageGuessing() {
        assertEquals("Accueil", AppLocalization.translate("Home", AppLanguage.FRENCH_CANADA))
        assertEquals("Clair", AppLocalization.translate("Light", AppLanguage.FRENCH_CANADA))
        assertEquals("Police", AppLocalization.translate("Font", AppLanguage.FRENCH_CANADA))
    }
    @Test fun frenchLabelsCanBeDisplayedInEnglish() { assertEquals("Home", AppLocalization.translate("Accueil", AppLanguage.ENGLISH_CANADA)) }
    @Test fun uppercaseLabelsStayUppercase() { assertEquals("APPLICATIONS", AppLocalization.translate("APPS", AppLanguage.FRENCH_CANADA)) }
    @Test fun namesOutsideCatalogueArePreserved() { assertEquals("Café My Destination 42", AppLocalization.translate("Café My Destination 42", AppLanguage.ENGLISH_CANADA)) }
    @Test fun westUsesFrenchCompassAbbreviation() { assertEquals("O", AppLocalization.translate("W", AppLanguage.FRENCH_CANADA)) }
    @Test fun backupFeedbackIsTranslated() { assertEquals("Sauvegarde enregistrée.", AppLocalization.translate("Backup saved.", AppLanguage.FRENCH_CANADA)) }
    @Test fun emptyMediaLabelUsesChosenLanguage() {
        assertEquals("No media playing", AppLocalization.translate("Aucune lecture en cours", AppLanguage.ENGLISH_CANADA))
    }
    private fun backup() = LauncherBackup(settings = AppSettings(), savedDestinations = listOf(
        SavedDestinationEntity("user", "My place", "My address", 45.5, -73.5, "STAR")
    ), offlineRegions = emptyList())
    @Test fun currentBackupRoundTripsPreferencesAndFavorite() {
        val gson = Gson()
        val original = backup()
        val restored = gson.fromJson(gson.toJson(original), LauncherBackup::class.java)
        assertTrue(BackupValidation.isValid(restored))
        assertEquals(original.settings, restored.settings)
        assertEquals(original.savedDestinations, restored.savedDestinations)
    }
    @Test fun unsupportedBackupVersionIsRejected() { assertFalse(BackupValidation.isValid(backup().copy(version = 999))) }
    @Test fun emptyGsonDocumentCannotOverwritePreferences() { assertFalse(BackupValidation.isValid(Gson().fromJson("{}", LauncherBackup::class.java))) }
    @Test fun nullBackupIsRejected() { assertFalse(BackupValidation.isValid(null)) }
    @Test fun unsafeUiScaleIsRejected() { assertFalse(BackupValidation.isValid(backup().copy(settings = AppSettings(uiScale = Float.NaN)))) }
    @Test fun outOfBoundsFavoriteIsRejectedBeforeAnyWrite() {
        val b = backup(); assertFalse(BackupValidation.isValid(b.copy(savedDestinations = listOf(b.savedDestinations[0].copy(latitude = 120.0)))))
    }
}
