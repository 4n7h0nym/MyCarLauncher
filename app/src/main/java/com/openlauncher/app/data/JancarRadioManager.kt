package com.openlauncher.app.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Binder
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class JancarRadioState(
    val band: String = "FM1",
    val frequency: Float = 98.7f,
    val stationName: String = "",
    val isStereo: Boolean = false,
    val isMuted: Boolean = false,
    val isPowerOn: Boolean = false,
    val isConnected: Boolean = false,
    val signalStrength: Int = 0,
    val scanning: Boolean = false
)

object JancarRadioManager {
    private const val TAG = "JLOG-JancarRadioManager"
    private const val SERVICE_ACTION = "com.jancar.services.action.radio"
    private const val SERVICE_PACKAGE = "com.jancar.services"
    private const val RADIO_DESCRIPTOR = "com.jancar.services.radio.IRadio"
    private const val CALLBACK_DESCRIPTOR = "com.jancar.services.radio.IRadioCallback"

    private const val TX_OPEN = 1
    private const val TX_CLOSE = 2
    private const val TX_SET_FREQ = 3
    private const val TX_GET_FREQ = 4
    private const val TX_SET_BAND = 5
    private const val TX_GET_BAND = 6
    private const val TX_SCAN_UP = 8
    private const val TX_SCAN_DOWN = 9
    private const val TX_MUTE = 28
    private const val TX_UNMUTE = 29

    private val _radioState = MutableStateFlow(JancarRadioState())
    val radioState: StateFlow<JancarRadioState> = _radioState
    val isConnected: Boolean get() = _radioState.value.isConnected

    fun publish(state: JancarRadioState) {
        _radioState.value = state
    }

    private var appContext: Context? = null
    private var remote: IBinder? = null
    private var bound = false

    private val callback = object : Binder() {
        init { attachInterface(null, CALLBACK_DESCRIPTOR) }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return try {
                data.enforceInterface(CALLBACK_DESCRIPTOR)
                when (code) {
                    1, 4 -> {
                        val rawFreq = data.readInt()
                        updateFrequency(rawFreq)
                    }
                    2 -> _radioState.value = _radioState.value.copy(isPowerOn = true)
                    3 -> _radioState.value = _radioState.value.copy(isPowerOn = false)
                    5 -> {
                        val rawFreq = data.readInt()
                        val strength = data.readInt()
                        _radioState.value = _radioState.value.copy(
                            frequency = displayFrequency(rawFreq, _radioState.value.band),
                            signalStrength = strength,
                            scanning = false
                        )
                    }
                    21 -> {
                        data.readInt()
                        val rawFreq = data.readInt()
                        val ps = data.readString().orEmpty()
                        _radioState.value = _radioState.value.copy(
                            frequency = displayFrequency(rawFreq, _radioState.value.band),
                            stationName = ps
                        )
                    }
                    else -> return super.onTransact(code, data, reply, flags)
                }
                reply?.writeNoException()
                true
            } catch (e: Exception) {
                Log.w(TAG, "Callback transact error ($code)", e)
                false
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service == null) return
            remote = service
            Log.d(TAG, "Jancar radio service connected.")
            runCatching {
                openSession()
                val band = invoke(TX_GET_BAND, {}) { it.readInt() } ?: 1
                val rawFreq = invoke(TX_GET_FREQ, {}) { it.readInt() } ?: 98700
                _radioState.value = _radioState.value.copy(
                    band = if (band == 0) "AM" else "FM1",
                    frequency = displayFrequency(rawFreq, if (band == 0) "AM" else "FM"),
                    isPowerOn = true,
                    isConnected = true
                )
            }.onFailure { Log.e(TAG, "Failed initializing radio session", it) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            _radioState.value = _radioState.value.copy(isConnected = false, isPowerOn = false)
            Log.d(TAG, "Jancar radio service disconnected.")
        }
    }

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
        if (bound) return
        val intent = Intent(SERVICE_ACTION).setPackage(SERVICE_PACKAGE)
        bound = runCatching {
            appContext!!.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
    }

    @Synchronized
    fun release(context: Context) {
        if (remote != null) invoke(TX_CLOSE, {}) { true }
        if (bound) runCatching { context.applicationContext.unbindService(connection) }
        bound = false
        remote = null
        _radioState.value = _radioState.value.copy(isConnected = false, isPowerOn = false)
    }

    /**
     * Executes the precise 4-step hardware startup sequence required by the Jancar RK3326 platform:
     * 1. Retrieves the last played station from SharedPreferences (defaulting to 98.7 MHz / 98700).
     * 2. Opens the system IPC session via `radioService?.open(...)`.
     * 3. Requests audio focus on STREAM_MUSIC (AUDIOFOCUS_GAIN) to force hardware multiplexer to channel 11 (RADIO).
     * 4. Injects the frequency via setFreq() to trigger instant tuner lock and actual audio output.
     */
    fun startLastPlayedRadio(context: Context) {
        val app = context.applicationContext
        appContext = app

        // 1. Retrieve last played station from local preferences (fallback 98.7 MHz / 98700 kHz)
        val prefs = app.getSharedPreferences("radio_last_station", Context.MODE_PRIVATE)
        val lastFreqFloat = prefs.getFloat("frequency", 98.7f)
        val lastBand = prefs.getString("band", "FM1") ?: "FM1"
        val rawFreq = if (lastBand.equals("AM", true)) lastFreqFloat.toInt() else (lastFreqFloat * 1000f).toInt()

        Log.d(TAG, "JLOG: startLastPlayedRadio -> Restoring station: band=$lastBand, freq=$lastFreqFloat (raw=$rawFreq)")

        // Ensure bound to service if not already connected
        if (!bound || remote == null) {
            init(app)
        }

        // 3. Request audio focus (AUDIOFOCUS_GAIN) to switch RK3326 audio multiplexer to RADIO channel
        val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.requestAudioFocus(
            null,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )

        // 2 & 4. Open session and inject frequency immediately
        val targetBandCode = if (lastBand.equals("AM", true)) 0 else 1
        setBandCode(targetBandCode)

        invoke(TX_OPEN, {
            writeStrongBinder(callback)
            writeString(app.packageName)
        }) { true }

        val success = invoke(TX_SET_FREQ, { writeInt(rawFreq) }) { true } ?: false
        if (success) {
            _radioState.value = _radioState.value.copy(
                band = lastBand,
                frequency = lastFreqFloat,
                isPowerOn = true,
                isMuted = false,
                stationName = ""
            )
            Log.d(TAG, "JLOG: startLastPlayedRadio -> Tuner locked successfully at raw=$rawFreq")
        } else {
            Log.w(TAG, "JLOG: startLastPlayedRadio -> setFreq failed or service busy")
        }
    }

    private fun openSession() {
        val audioManager = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)

        invoke(TX_OPEN, {
            writeStrongBinder(callback)
            writeString(appContext?.packageName.orEmpty())
        }) { true }
        Log.d(TAG, "Jancar radio session opened with package: ${appContext?.packageName}")
    }

    fun tuneFrequency(band: String, frequencyMhz: Float): Boolean {
        val targetBand = if (band.equals("AM", true)) 0 else 1
        val raw = if (targetBand == 1) (frequencyMhz * 1000f).toInt() else frequencyMhz.toInt()
        
        openSession()
        setBandCode(targetBand)
        
        val success = invoke(TX_SET_FREQ, { writeInt(raw) }) { true } ?: false
        if (success) {
            _radioState.value = _radioState.value.copy(
                band = band,
                frequency = frequencyMhz,
                isPowerOn = true,
                stationName = ""
            )
            Log.d(TAG, "Jancar radio tuned successfully to raw: $raw ($frequencyMhz)")
        }
        return success
    }

    fun scanUp(): Boolean {
        openSession()
        _radioState.value = _radioState.value.copy(scanning = true)
        val currentRaw = (_radioState.value.frequency * 1000f).toInt()
        return invoke(TX_SCAN_UP, { writeInt(currentRaw) }) { true } ?: false
    }

    fun scanDown(): Boolean {
        openSession()
        _radioState.value = _radioState.value.copy(scanning = true)
        val currentRaw = (_radioState.value.frequency * 1000f).toInt()
        return invoke(TX_SCAN_DOWN, { writeInt(currentRaw) }) { true } ?: false
    }

    fun setPower(power: Boolean): Boolean {
        val success = if (power) {
            openSession()
            invoke(TX_UNMUTE, {}) { true } ?: false
        } else {
            invoke(TX_CLOSE, {}) { true } ?: false
        }
        if (success) _radioState.value = _radioState.value.copy(isPowerOn = power)
        return success
    }

    fun setMute(mute: Boolean): Boolean {
        return invoke(if (mute) TX_MUTE else TX_UNMUTE, {}) { true }?.also {
            if (it) _radioState.value = _radioState.value.copy(isMuted = mute)
        } ?: false
    }

    private fun setBandCode(code: Int): Boolean {
        return invoke(TX_SET_BAND, { writeInt(code) }) { true } ?: false
    }

    private fun updateFrequency(raw: Int) {
        val current = _radioState.value
        val freq = displayFrequency(raw, current.band)
        _radioState.value = current.copy(frequency = freq, isPowerOn = true, scanning = false)
        Log.d(TAG, "Jancar radio frequency updated via callback: raw=$raw -> $freq")
    }

    private inline fun <T> invoke(transaction: Int, crossinline writeArgs: Parcel.() -> Unit, crossinline readResult: (Parcel) -> T): T? {
        val binder = remote ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(RADIO_DESCRIPTOR)
            data.writeArgs()
            if (!binder.transact(transaction, data, reply, 0)) return null
            reply.readException()
            readResult(reply)
        } catch (e: DeadObjectException) {
            Log.e(TAG, "Jancar radio Binder dead (DeadObjectException)", e)
            remote = null
            _radioState.value = _radioState.value.copy(isConnected = false)
            null
        } catch (e: RemoteException) {
            Log.e(TAG, "Jancar radio RemoteException on transaction $transaction", e)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun displayFrequency(raw: Int, band: String): Float =
        if (band.equals("AM", true)) raw.toFloat() else raw / 1000f
}
