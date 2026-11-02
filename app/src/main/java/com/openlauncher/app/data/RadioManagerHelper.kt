package com.openlauncher.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.openlauncher.app.radio.JancarRadioManager as RadioClient
import com.openlauncher.app.radio.RadioUiState as TunerState
import com.openlauncher.app.radio.ui.RadioPreset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.Executors

/** One client shared by the full radio page, home widget and physical controls. */
object RadioManagerHelper {
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "RadioBinder").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var app: Context? = null
    private var client: RadioClient? = null
    private var registered = false
    private val _tuner = MutableStateFlow(TunerState())
    val tunerState = _tuner.asStateFlow()
    val state = JancarRadioManager.radioState
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _hardware = MutableStateFlow("")
    val hardware = _hardware.asStateFlow()
    private val _bank = MutableStateFlow("FM1")
    val selectedBank = _bank.asStateFlow()
    private val _presets = MutableStateFlow<List<RadioPreset>>(emptyList())
    val presets = _presets.asStateFlow()
    private val ready = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.jancar.services.ready") enqueue { client?.connect() }
        }
    }
    internal fun enqueue(block: suspend () -> Unit) { scope.launch { block() } }
    private fun command(block: suspend (RadioClient) -> Result<Unit>) = enqueue {
        val radio = client
        _error.value = if (radio == null) "Client radio non initialisé" else
            block(radio).exceptionOrNull()?.let { "${it.javaClass.simpleName} : ${it.message.orEmpty()}" }
    }

    @Synchronized
    fun connect(context: Context) {
        val application = context.applicationContext
        app = application
        if (client == null) {
            val radio = RadioClient(application)
            client = radio
            enqueue {
                val preferences = application.getSharedPreferences("radio_last_station", Context.MODE_PRIVATE)
                _bank.value = preferences.getString("bank", "FM1").takeIf { it in listOf("FM1", "FM2", "AM") } ?: "FM1"
                _presets.value = RadioPresetStore.read(application, _bank.value)
                _hardware.value = RadioHardwareInfo.read()
            }
            scope.launch {
                radio.state.collect { current ->
                    _tuner.value = current
                    _error.value = current.error
                    if (current.band == 0 && _bank.value != "AM") selectBank("AM")
                    if (current.band == 1 && _bank.value == "AM") {
                        selectBank(application.getSharedPreferences("radio_last_station", Context.MODE_PRIVATE)
                            .getString("last_fm_bank", "FM1") ?: "FM1")
                    }
                    val khzVal = current.frequencyKhz ?: 0
                    val bandInt = current.band ?: 0
                    val bank = if (bandInt == 0) "AM" else _bank.value.takeIf { it != "AM" } ?: "FM1"
                    JancarRadioManager.publish(JancarRadioState(
                        band = bank, frequency = if (bandInt == 0) khzVal.toFloat() else khzVal / 1000f,
                        stationName = current.rds, isStereo = current.stereo == true,
                        isMuted = current.muted, isPowerOn = current.powerOn ?: current.openAccepted,
                        isConnected = current.connected, signalStrength = current.signal ?: 0
                    ))
                    if (current.connected) {
                        if (RadioFrequency.matchesBand(bandInt, khzVal)) {
                            val frequency = if (bandInt == 0) khzVal.toFloat() else khzVal / 1000f
                            application.getSharedPreferences("radio_last_station", Context.MODE_PRIVATE).edit()
                                .putString("band", bank).putFloat("frequency", frequency)
                                .putFloat("frequency_$bank", frequency).apply()
                        }
                    }
                }
            }
        }
        if (!registered) runCatching {
            ContextCompat.registerReceiver(application, ready, IntentFilter("com.jancar.services.ready"), ContextCompat.RECEIVER_EXPORTED)
            registered = true
        }
        // Same bootstrap as the OEM SDK; failure is reported by the subsequent binding.
        runCatching { application.startService(Intent("com.jancar.services.action.main").setPackage("com.jancar.services")) }
        command { it.connect() }
    }

    @Synchronized
    fun disconnect(context: Context) {
        if (registered) runCatching { context.applicationContext.unregisterReceiver(ready) }
        registered = false
        enqueue { client?.disconnect() } // Keep shared tuner audio playing.
    }

    fun lastStation(context: Context): Pair<String, Float>? {
        val prefs = context.getSharedPreferences("radio_last_station", Context.MODE_PRIVATE)
        val frequency = prefs.getFloat("frequency", 0f)
        return if (frequency.isFinite() && frequency > 0f) prefs.getString("band", "FM").orEmpty() to frequency else null
    }
    fun power(on: Boolean) = command { radio ->
        if (on) radio.openRadio()
        else radio.closeRadio()
    }
    fun mute(on: Boolean) = command { it.setMuted(on) }
    fun tune(band: String, frequency: Float) = command { radio ->
        val bank = normalizeBank(band)
        val khz = RadioFrequency.toKhz(bank, frequency)
        if (khz == null) Result.failure(IllegalArgumentException("Fréquence invalide")) else {
            val result = radio.setFrequency(khz, fm = bank != "AM")
            if (result.isSuccess) selectBank(bank)
            result
        }
    }
    fun seek(up: Boolean) = command { if (up) it.seekUp() else it.seekDown() }
    fun stopSeek() = command { it.stopSeek() }
    fun band(value: String) = command { radio ->
        val bank = normalizeBank(value)
        val prefs = app?.getSharedPreferences("radio_last_station", Context.MODE_PRIVATE)
        val saved = prefs?.getFloat("frequency_$bank", 0f) ?: 0f
        val khz = RadioFrequency.toKhz(bank, saved)
        val result = if (khz != null) radio.setFrequency(khz, fm = bank != "AM") else radio.setBand(bank != "AM")
        if (result.isSuccess) {
            selectBank(bank)
        }
        result
    }
    private fun normalizeBank(value: String) = when(value.uppercase(java.util.Locale.ROOT)) { "AM" -> "AM"; "FM2" -> "FM2"; else -> "FM1" }
    private fun selectBank(value: String) {
        val bank = normalizeBank(value)
        _bank.value = bank
        app?.let {
            it.getSharedPreferences("radio_last_station", Context.MODE_PRIVATE).edit().apply {
                putString("bank", bank)
                if (bank != "AM") putString("last_fm_bank", bank)
            }.apply()
            _presets.value = RadioPresetStore.read(it, bank)
        }
    }
    fun savePreset(preset: RadioPreset) = enqueue {
        val context = app ?: return@enqueue
        val tuner = _tuner.value
        val freq = tuner.frequencyKhz ?: return@enqueue
        if (!tuner.connected || tuner.scanning || (tuner.band != 0) != preset.fm) return@enqueue
        RadioPresetStore.save(context, _bank.value, preset.number - 1, freq)
        _presets.value = RadioPresetStore.read(context, _bank.value)
    }
    fun nativeSeek(up: Boolean) = nativeCommand { NativeFmServiceClient.seek(it, up) }
    fun nativeStop() = nativeCommand { NativeFmServiceClient.powerOff(it) }
    private fun nativeCommand(block: (Context) -> Result<Unit>) = enqueue {
        _error.value = app?.let { block(it).exceptionOrNull()?.let { error ->
            "FmService : ${error.javaClass.simpleName} — ${error.message.orEmpty()}"
        } } ?: if (app == null) "Client radio non initialisé" else null
    }
}
