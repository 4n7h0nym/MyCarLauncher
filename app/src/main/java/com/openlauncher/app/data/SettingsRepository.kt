package com.openlauncher.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.openlauncher.app.model.SavedPlace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

class SettingsRepository(private val context: Context) {

    private val gson = Gson()

    private object Keys {
        val VEHICLE_NAME       = stringPreferencesKey("vehicle_name")
        val ACCENT_COLOR       = intPreferencesKey("accent_color")
        val BG_COLOR           = intPreferencesKey("bg_color")
        val FONT_COLOR         = intPreferencesKey("font_color")
        val WALLPAPER_URI      = stringPreferencesKey("wallpaper_uri")
        val FONT_BOLD          = booleanPreferencesKey("font_bold")
        val TEXT_SCALE         = floatPreferencesKey("text_scale")
        val UI_SCALE           = floatPreferencesKey("ui_scale")
        val CLOCK_STYLE        = stringPreferencesKey("clock_style")
        val UNIT_SYSTEM        = stringPreferencesKey("unit_system")
        val APP_FONT           = stringPreferencesKey("app_font")
        val SHOW_WEATHER       = booleanPreferencesKey("show_weather")
        val SHOW_CLOCK         = booleanPreferencesKey("show_clock")
        val SHOW_TELEMETRY     = booleanPreferencesKey("show_telemetry")
        val SHOW_NOW_PLAYING   = booleanPreferencesKey("show_now_playing")
        val SHOW_ALTIMETER     = booleanPreferencesKey("show_altimeter")
        val SHOW_SPEEDOMETER   = booleanPreferencesKey("show_speedometer")
        val SHORTCUTS_JSON     = stringPreferencesKey("shortcuts_json")
        val WIDGET_LAYOUT_JSON = stringPreferencesKey("widget_layout_json")
        val SAVED_PLACES_JSON  = stringPreferencesKey("saved_places_json")
        val CAR_PLAY_PACKAGE      = stringPreferencesKey("car_play_package")
        val ANDROID_AUTO_PACKAGE  = stringPreferencesKey("android_auto_package")
        val USE_GRADIENT          = booleanPreferencesKey("use_gradient")
        val GRADIENT_END_COLOR    = intPreferencesKey("gradient_end_color")
        val WALLPAPER_DIM         = floatPreferencesKey("wallpaper_dim")
        val RIGHT_HAND_DRIVE      = booleanPreferencesKey("right_hand_drive")
        val SIDEBAR_POSITION           = stringPreferencesKey("sidebar_position")
        val BOTTOM_BAR_SHORTCUTS_RIGHT = booleanPreferencesKey("bottom_bar_shortcuts_right")
        val DAY_NIGHT_MODE        = stringPreferencesKey("day_night_mode")
        val MAP_THEME_MODE        = stringPreferencesKey("map_theme_mode")
        val SHOW_PIP              = booleanPreferencesKey("show_pip")
        val PIP_APP_PACKAGE       = stringPreferencesKey("pip_app_package")
        val RADIO_PACKAGE         = stringPreferencesKey("radio_package")
        val ONBOARDING_COMPLETED  = booleanPreferencesKey("onboarding_completed")
        val SHOW_VITALS           = booleanPreferencesKey("show_vitals")
        val SHOW_TRIP_TRACKER     = booleanPreferencesKey("show_trip_tracker")
        val COMPASS_OFFSET        = floatPreferencesKey("compass_offset")
        val SHOW_SOUNDBOARD       = booleanPreferencesKey("show_soundboard")
        val SOUNDBOARD_PADS_JSON  = stringPreferencesKey("soundboard_pads_json")
        val VITALS_AS_BARS        = booleanPreferencesKey("vitals_as_bars")
        val SPEEDOMETER_DIGITAL_ONLY = booleanPreferencesKey("speedometer_digital_only")
        val GRADIENT_DIRECTION    = stringPreferencesKey("gradient_direction")
        val USE_CUSTOM_BG_COLOR   = booleanPreferencesKey("use_custom_bg_color")
        val CARD_SURFACE_COLOR    = intPreferencesKey("card_surface_color")
        val CARD_BORDER_COLOR     = intPreferencesKey("card_border_color")
        val TEXT_MUTED_COLOR      = intPreferencesKey("text_muted_color")
        val SHOW_CLOCK_WEATHER    = booleanPreferencesKey("show_clock_weather")
        val ANNOUNCE_INCOMING_MESSAGES = booleanPreferencesKey("announce_incoming_messages")
        val SHOW_PHONE_FAVORITES = booleanPreferencesKey("show_phone_favorites_widget")
        val SHOW_MESSAGES = booleanPreferencesKey("show_messages_widget")
        val SHOW_APP_SHORTCUTS = booleanPreferencesKey("show_app_shortcuts_widget")
        val FAVORITE_PHONE_NUMBERS = stringPreferencesKey("favorite_phone_numbers_json")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val LAUNCH_AT_BOOT = booleanPreferencesKey("launch_at_boot")
        val RESTORE_LAST_PAGE = booleanPreferencesKey("restore_last_page")
        val AUTO_RESUME_MEDIA = booleanPreferencesKey("auto_resume_media_on_bluetooth")
        val LAST_DESTINATION = stringPreferencesKey("last_destination")
        val STEERING_BINDINGS_JSON = stringPreferencesKey("steering_wheel_bindings_json")
        val MEDIA_PLAYER_PACKAGE = stringPreferencesKey("media_player_package")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> readSettings(prefs) }

    private fun readSettings(prefs: Preferences): AppSettings {
            val defaults = AppSettings()
            val shortcutsJson = prefs[Keys.SHORTCUTS_JSON]
            val shortcuts = if (shortcutsJson != null) {
                runCatching {
                    gson.fromJson<List<ShortcutConfig>>(
                        shortcutsJson,
                        object : TypeToken<List<ShortcutConfig>>() {}.type
                    )
                }.getOrNull() ?: defaults.shortcuts
            } else defaults.shortcuts

            val widgetJson = prefs[Keys.WIDGET_LAYOUT_JSON]
            val widgets = if (widgetJson != null) {
                val loaded = runCatching {
                    gson.fromJson<List<WidgetConfig>>(
                        widgetJson,
                        object : TypeToken<List<WidgetConfig>>() {}.type
                    )
                }.getOrNull() ?: defaults.widgetLayout
                if (loaded.none { it.gridX >= 2 }) defaults.widgetLayout else loaded
            } else defaults.widgetLayout

            val savedPlacesJson = prefs[Keys.SAVED_PLACES_JSON]
            val savedPlaces = if (savedPlacesJson != null) {
                runCatching {
                    gson.fromJson<List<SavedPlace>>(
                        savedPlacesJson,
                        object : TypeToken<List<SavedPlace>>() {}.type
                    )
                }.getOrNull() ?: defaults.savedPlaces
            } else defaults.savedPlaces

            val favoritePhoneNumbers = prefs[Keys.FAVORITE_PHONE_NUMBERS]?.let { json ->
                runCatching {
                    gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type)
                }.getOrNull()
            } ?: defaults.favoritePhoneNumbers

            return AppSettings(
                appLanguage = prefs[Keys.APP_LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: defaults.appLanguage,
                launchAtBoot = prefs[Keys.LAUNCH_AT_BOOT] ?: defaults.launchAtBoot,
                restoreLastPage = prefs[Keys.RESTORE_LAST_PAGE] ?: defaults.restoreLastPage,
                autoResumeMediaOnBluetooth = prefs[Keys.AUTO_RESUME_MEDIA] ?: defaults.autoResumeMediaOnBluetooth,
                mediaPlayerPackage = prefs[Keys.MEDIA_PLAYER_PACKAGE] ?: defaults.mediaPlayerPackage,
                lastDestination = prefs[Keys.LAST_DESTINATION] ?: defaults.lastDestination,
                steeringWheelBindings = prefs[Keys.STEERING_BINDINGS_JSON]?.let { json ->
                    runCatching {
                        gson.fromJson<List<SteeringWheelBinding>>(json, object : TypeToken<List<SteeringWheelBinding>>() {}.type)
                    }.getOrNull()
                } ?: defaults.steeringWheelBindings,
                vehicleName    = prefs[Keys.VEHICLE_NAME]     ?: defaults.vehicleName,
                accentColor    = prefs[Keys.ACCENT_COLOR]     ?: defaults.accentColor,
                backgroundColor = prefs[Keys.BG_COLOR]        ?: defaults.backgroundColor,
                fontColor      = prefs[Keys.FONT_COLOR]       ?: defaults.fontColor,
                wallpaperUri   = prefs[Keys.WALLPAPER_URI]    ?: defaults.wallpaperUri,
                fontBold       = prefs[Keys.FONT_BOLD]        ?: defaults.fontBold,
                textScale      = prefs[Keys.TEXT_SCALE]       ?: defaults.textScale,
                uiScale        = prefs[Keys.UI_SCALE]         ?: defaults.uiScale,
                clockStyle     = prefs[Keys.CLOCK_STYLE]?.let { runCatching { ClockStyle.valueOf(it) }.getOrNull() } ?: defaults.clockStyle,
                unitSystem     = prefs[Keys.UNIT_SYSTEM]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() } ?: defaults.unitSystem,
                appFont        = prefs[Keys.APP_FONT]?.let { runCatching { AppFont.valueOf(it) }.getOrNull() } ?: defaults.appFont,
                showWeather    = prefs[Keys.SHOW_WEATHER]     ?: defaults.showWeather,
                showClock      = prefs[Keys.SHOW_CLOCK]       ?: defaults.showClock,
                showTelemetry  = prefs[Keys.SHOW_TELEMETRY]   ?: defaults.showTelemetry,
                showNowPlaying = prefs[Keys.SHOW_NOW_PLAYING] ?: defaults.showNowPlaying,
                showAltimeter   = prefs[Keys.SHOW_ALTIMETER]   ?: defaults.showAltimeter,
                showSpeedometer = prefs[Keys.SHOW_SPEEDOMETER] ?: defaults.showSpeedometer,
                shortcuts      = shortcuts,
                widgetLayout   = widgets,
                savedPlaces    = savedPlaces,
                carPlayPackage      = prefs[Keys.CAR_PLAY_PACKAGE]      ?: defaults.carPlayPackage,
                androidAutoPackage  = prefs[Keys.ANDROID_AUTO_PACKAGE]  ?: defaults.androidAutoPackage,
                useGradient      = prefs[Keys.USE_GRADIENT]        ?: defaults.useGradient,
                gradientEndColor = prefs[Keys.GRADIENT_END_COLOR]  ?: defaults.gradientEndColor,
                wallpaperDim     = prefs[Keys.WALLPAPER_DIM]       ?: defaults.wallpaperDim,
                sidebarPosition  = prefs[Keys.SIDEBAR_POSITION]?.let { runCatching { SidebarPosition.valueOf(it) }.getOrNull() }
                                   ?: if (prefs[Keys.RIGHT_HAND_DRIVE] == true) SidebarPosition.RIGHT else defaults.sidebarPosition,
                bottomBarShortcutsRight = prefs[Keys.BOTTOM_BAR_SHORTCUTS_RIGHT] ?: defaults.bottomBarShortcutsRight,
                dayNightMode     = prefs[Keys.DAY_NIGHT_MODE]?.let { runCatching { DayNightMode.valueOf(it) }.getOrNull() } ?: defaults.dayNightMode,
                mapThemeMode     = prefs[Keys.MAP_THEME_MODE]?.let { runCatching { MapThemeMode.valueOf(it) }.getOrNull() } ?: defaults.mapThemeMode,
                showPip          = prefs[Keys.SHOW_PIP]         ?: defaults.showPip,
                pipAppPackage    = prefs[Keys.PIP_APP_PACKAGE]  ?: defaults.pipAppPackage,
                radioPackage     = prefs[Keys.RADIO_PACKAGE]    ?: defaults.radioPackage,
                onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
                showVitals       = prefs[Keys.SHOW_VITALS]      ?: defaults.showVitals,
                showTripTracker  = prefs[Keys.SHOW_TRIP_TRACKER] ?: defaults.showTripTracker,
                compassOffset    = prefs[Keys.COMPASS_OFFSET]    ?: defaults.compassOffset,
                showSoundboard   = prefs[Keys.SHOW_SOUNDBOARD]   ?: defaults.showSoundboard,
                soundboardPads   = prefs[Keys.SOUNDBOARD_PADS_JSON]?.let {
                    runCatching {
                        gson.fromJson<List<SoundPadConfig>>(it, object : com.google.gson.reflect.TypeToken<List<SoundPadConfig>>() {}.type)
                    }.getOrNull()
                } ?: defaults.soundboardPads,
                vitalsAsBars     = prefs[Keys.VITALS_AS_BARS] ?: defaults.vitalsAsBars,
                speedometerDigitalOnly = prefs[Keys.SPEEDOMETER_DIGITAL_ONLY] ?: defaults.speedometerDigitalOnly,
                gradientDirection = prefs[Keys.GRADIENT_DIRECTION]?.let { runCatching { GradientDirection.valueOf(it) }.getOrNull() } ?: defaults.gradientDirection,
                useCustomBackgroundColor = prefs[Keys.USE_CUSTOM_BG_COLOR] ?: defaults.useCustomBackgroundColor,
                cardSurfaceColor = prefs[Keys.CARD_SURFACE_COLOR] ?: defaults.cardSurfaceColor,
                cardBorderColor  = prefs[Keys.CARD_BORDER_COLOR]  ?: defaults.cardBorderColor,
                textMutedColor   = prefs[Keys.TEXT_MUTED_COLOR]   ?: defaults.textMutedColor,
                showClockWeather = prefs[Keys.SHOW_CLOCK_WEATHER] ?: defaults.showClockWeather,
                announceIncomingMessages = prefs[Keys.ANNOUNCE_INCOMING_MESSAGES] ?: defaults.announceIncomingMessages,
                showPhoneFavorites = prefs[Keys.SHOW_PHONE_FAVORITES] ?: defaults.showPhoneFavorites,
                showMessages = prefs[Keys.SHOW_MESSAGES] ?: defaults.showMessages,
                showAppShortcuts = prefs[Keys.SHOW_APP_SHORTCUTS] ?: defaults.showAppShortcuts,
                favoritePhoneNumbers = favoritePhoneNumbers
            )
    }

    suspend fun saveSettings(s: AppSettings) {
        context.dataStore.edit { prefs -> writeSettings(prefs, s) }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs -> writeSettings(prefs, transform(readSettings(prefs))) }
    }

    private fun writeSettings(prefs: MutablePreferences, s: AppSettings) {
            prefs[Keys.LAUNCH_AT_BOOT] = s.launchAtBoot
            prefs[Keys.RESTORE_LAST_PAGE] = s.restoreLastPage
            prefs[Keys.AUTO_RESUME_MEDIA] = s.autoResumeMediaOnBluetooth
            prefs[Keys.MEDIA_PLAYER_PACKAGE] = s.mediaPlayerPackage
            prefs[Keys.LAST_DESTINATION] = s.lastDestination
            prefs[Keys.STEERING_BINDINGS_JSON] = gson.toJson(s.steeringWheelBindings)
            context.getSharedPreferences("launcher_runtime", Context.MODE_PRIVATE)
                .edit().putBoolean("launch_at_boot", s.launchAtBoot).apply()
            prefs[Keys.APP_LANGUAGE]      = s.appLanguage.name
            prefs[Keys.VEHICLE_NAME]       = s.vehicleName
            prefs[Keys.ACCENT_COLOR]       = s.accentColor
            prefs[Keys.BG_COLOR]           = s.backgroundColor
            prefs[Keys.FONT_COLOR]         = s.fontColor
            prefs[Keys.WALLPAPER_URI]      = s.wallpaperUri
            prefs[Keys.FONT_BOLD]          = s.fontBold
            prefs[Keys.TEXT_SCALE]         = s.textScale
            prefs[Keys.UI_SCALE]           = s.uiScale
            prefs[Keys.CLOCK_STYLE]        = s.clockStyle.name
            prefs[Keys.UNIT_SYSTEM]        = s.unitSystem.name
            prefs[Keys.APP_FONT]           = s.appFont.name
            prefs[Keys.SHOW_WEATHER]       = s.showWeather
            prefs[Keys.SHOW_CLOCK]         = s.showClock
            prefs[Keys.SHOW_TELEMETRY]     = s.showTelemetry
            prefs[Keys.SHOW_NOW_PLAYING]   = s.showNowPlaying
            prefs[Keys.SHOW_ALTIMETER]     = s.showAltimeter
            prefs[Keys.SHOW_SPEEDOMETER]   = s.showSpeedometer
            prefs[Keys.SHORTCUTS_JSON]     = gson.toJson(s.shortcuts)
            prefs[Keys.WIDGET_LAYOUT_JSON] = gson.toJson(s.widgetLayout)
            prefs[Keys.SAVED_PLACES_JSON]  = gson.toJson(s.savedPlaces)
            prefs[Keys.CAR_PLAY_PACKAGE]      = s.carPlayPackage
            prefs[Keys.ANDROID_AUTO_PACKAGE]  = s.androidAutoPackage
            prefs[Keys.USE_GRADIENT]       = s.useGradient
            prefs[Keys.GRADIENT_END_COLOR] = s.gradientEndColor
            prefs[Keys.WALLPAPER_DIM]      = s.wallpaperDim
            prefs[Keys.SIDEBAR_POSITION]           = s.sidebarPosition.name
            prefs[Keys.BOTTOM_BAR_SHORTCUTS_RIGHT] = s.bottomBarShortcutsRight
            prefs[Keys.DAY_NIGHT_MODE]     = s.dayNightMode.name
            prefs[Keys.MAP_THEME_MODE]     = s.mapThemeMode.name
            prefs[Keys.SHOW_PIP]           = s.showPip
            prefs[Keys.PIP_APP_PACKAGE]    = s.pipAppPackage
            prefs[Keys.RADIO_PACKAGE]      = s.radioPackage
            prefs[Keys.ONBOARDING_COMPLETED] = s.onboardingCompleted
            prefs[Keys.SHOW_VITALS]        = s.showVitals
            prefs[Keys.SHOW_TRIP_TRACKER]  = s.showTripTracker
            prefs[Keys.COMPASS_OFFSET]     = s.compassOffset
            prefs[Keys.SHOW_SOUNDBOARD]    = s.showSoundboard
            prefs[Keys.SOUNDBOARD_PADS_JSON] = gson.toJson(s.soundboardPads)
            prefs[Keys.VITALS_AS_BARS]     = s.vitalsAsBars
            prefs[Keys.SPEEDOMETER_DIGITAL_ONLY] = s.speedometerDigitalOnly
            prefs[Keys.GRADIENT_DIRECTION] = s.gradientDirection.name
            prefs[Keys.USE_CUSTOM_BG_COLOR] = s.useCustomBackgroundColor
            prefs[Keys.CARD_SURFACE_COLOR] = s.cardSurfaceColor
            prefs[Keys.CARD_BORDER_COLOR]  = s.cardBorderColor
            prefs[Keys.TEXT_MUTED_COLOR]   = s.textMutedColor
            prefs[Keys.SHOW_CLOCK_WEATHER] = s.showClockWeather
            prefs[Keys.ANNOUNCE_INCOMING_MESSAGES] = s.announceIncomingMessages
            prefs[Keys.SHOW_PHONE_FAVORITES] = s.showPhoneFavorites
            prefs[Keys.SHOW_MESSAGES] = s.showMessages
            prefs[Keys.SHOW_APP_SHORTCUTS] = s.showAppShortcuts
            prefs[Keys.FAVORITE_PHONE_NUMBERS] = gson.toJson(s.favoritePhoneNumbers)
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { it.clear() }
        context.getSharedPreferences("launcher_runtime", Context.MODE_PRIVATE).edit().remove("launch_at_boot").apply()
    }
}
