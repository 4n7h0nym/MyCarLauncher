package com.openlauncher.app.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object OfflineNavigationConfigManager {
    private val _configState = MutableStateFlow(OfflineNavConfig())
    val configState: StateFlow<OfflineNavConfig> = _configState.asStateFlow()

    fun setForceOfflineRouting(enabled: Boolean) {
        _configState.update { it.copy(forceOfflineRouting = enabled) }
    }

    fun setOfflineVoiceGuidance(enabled: Boolean) {
        _configState.update { it.copy(offlineVoiceGuidance = enabled) }
    }

    fun setOfflineReroute(enabled: Boolean) {
        _configState.update { it.copy(offlineReroute = enabled) }
    }
}

data class OfflineNavConfig(
    val forceOfflineRouting: Boolean = false,
    val offlineVoiceGuidance: Boolean = true,
    val offlineReroute: Boolean = true
)
