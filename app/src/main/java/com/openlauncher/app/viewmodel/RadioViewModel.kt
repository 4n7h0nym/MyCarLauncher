package com.openlauncher.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openlauncher.app.data.RadioManagerHelper
import kotlinx.coroutines.flow.*

data class RadioUiState(
    val connected: Boolean = false, val powerOn: Boolean = false,
    val muted: Boolean = false, val frequency: Float = 0f,
    val band: String = "FM", val rds: String = "", val stereo: Boolean = false,
    val error: String? = null, val signalStrength: Int? = null, val hardware: String = ""
)

class RadioViewModel(application: Application) : AndroidViewModel(application) {
    val uiState: StateFlow<RadioUiState> = combine(RadioManagerHelper.state, RadioManagerHelper.error, RadioManagerHelper.selectedBank, RadioManagerHelper.hardware) { state, error, bank, hardware ->
        RadioUiState(state.isConnected, state.isPowerOn, state.isMuted, state.frequency,
            if (state.band == "AM") "AM" else bank.takeIf { it != "AM" } ?: "FM1", state.stationName, state.isStereo, error, state.signalStrength, hardware)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RadioUiState())
    fun power(on: Boolean) = RadioManagerHelper.power(on)
    fun mute(on: Boolean) = RadioManagerHelper.mute(on)
    fun tune(band: String, frequency: Float) = RadioManagerHelper.tune(band, frequency)
    fun seek(up: Boolean) = RadioManagerHelper.seek(up)
    fun band(value: String) = RadioManagerHelper.band(value)
}
