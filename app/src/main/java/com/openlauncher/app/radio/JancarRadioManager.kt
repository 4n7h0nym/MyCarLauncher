package com.openlauncher.app.radio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

data class RadioUiState(
    val connected: Boolean = false,
    val openAccepted: Boolean = false,
    val muted: Boolean = false,
    val audioChannel: Int? = null,
    val routingConfirmed: Boolean = false,
    val powerOn: Boolean? = null,
    val band: Int? = null, // AM = 0, FM = 1; FM1/FM2 are preset banks, not tuner bands.
    val frequencyKhz: Int? = null,
    val scanning: Boolean = false,
    val rds: String = "",
    val stereo: Boolean? = null,
    val signal: Int? = null, // Vendor units; do not label this as a percentage.
    val error: String? = null
)

/**
 * Protocol verified against the supplied com.jancar.services.radio.IRadio Stub/Proxy.
 * Requires the same vendor Binder ABI on the installed ROM. No reflection or SDK JAR.
 * All public operations are suspend functions; Binder calls run on Dispatchers.IO.
 * Use one application-scoped instance, not one client per card/page.
 */
class JancarRadioManager(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Mutex()
    private val audioRoute = JancarAudioRoute(app)
    private val _state = MutableStateFlow(RadioUiState())
    val state: StateFlow<RadioUiState> = _state.asStateFlow()
    private val scanEpoch = AtomicLong()
    private val stationEpoch = AtomicLong()
    @Volatile private var remote: IBinder? = null
    @Volatile private var disposed = false
    @Volatile private var activeCallback: RadioCallback? = null
    // The following two fields are accessed only on the main thread.
    private var bound = false
    private var pending = CompletableDeferred<IBinder>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (!bound || disposed) return
            remote = service
            pending.complete(service)
            Log.d(TAG, "Service connected: $name; protocol validation pending")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            invalidate("Service disconnected: $name")
            // Android retains the binding and can deliver onServiceConnected again.
            pending.completeExceptionally(RemoteException("Service disconnected"))
            pending = CompletableDeferred()
        }

        override fun onBindingDied(name: ComponentName) { // Invoked on Android 8+.
            invalidate("Binding died: $name")
            unbind()
        }

        override fun onNullBinding(name: ComponentName) { // Invoked on Android 9+.
            invalidate("Service returned a null Binder: $name")
            unbind()
        }
    }

    suspend fun connect(): Result<Unit> = command("connect") {
        refresh(connectedBinder())
        Unit
    }

    /** The OEM service performs antenna power, media source and audio channel routing. */
    suspend fun openRadio(): Result<Unit> = command("openRadio") {
        open(connectedBinder())
    }

    /** FM in kHz: 107500 = 107.5 MHz. AM also uses kHz: 810 = 810 kHz. */
    suspend fun setFrequency(freq: Int, fm: Boolean? = null): Result<Unit> = command("setFrequency($freq kHz)") {
        require(freq > 0) { "Frequency must be positive, in kHz" }
        val binder = connectedBinder()
        cancelScan(binder)
        open(binder) // Session + confirmed OEM audio route before any tuning command.
        if (fm != null) transact(binder, 5, { writeInt(if (fm) 1 else 0) }) { Unit }
        Log.d(TAG, "tune: session opened by ${app.packageName}, RADIO route confirmed, freq=$freq kHz")
        transact(binder, 3, { writeInt(freq) }) { Unit }
        // Restore sound after synchronous tuning; no PCM gain or volume override.
        transact(binder, 29) { Unit }
        _state.update { it.copy(muted = false) }
        // Do not invent a successful tune: frequency changes come from callbacks.
    }

    suspend fun setBand(fm: Boolean): Result<Unit> = command("setBand(fm=$fm)") {
        val binder = connectedBinder()
        cancelScan(binder)
        open(binder)
        transact(binder, 5, { writeInt(if (fm) 1 else 0) }) { Unit }
        refresh(binder)
    }

    suspend fun setMuted(muted: Boolean): Result<Unit> = command("mute=$muted") {
        transact(connectedBinder(), if (muted) 28 else 29) { Unit }
        _state.update { it.copy(muted = muted) }
    }

    suspend fun seekUp(): Result<Unit> = seek(true)
    suspend fun seekDown(): Result<Unit> = seek(false)

    suspend fun stopSeek(): Result<Unit> = command("stopSeek") {
        remote?.let { cancelScan(it) }
    }

    /** Explicit power/source close, not just an unbind of the UI. */
    suspend fun closeRadio(): Result<Unit> = command("closeRadio") {
        val binder = remote
        if (binder != null) {
            cancelScan(binder)
            transact(binder, 2) { Unit }
        }
        activeCallback = null
        _state.update { it.copy(openAccepted = false, powerOn = null, scanning = false, routingConfirmed = false) }
    }

    /** Release this client without stopping shared audio. Reconnect remains possible. */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        commands.withLock {
            withContext(Dispatchers.Main.immediate) { unbind() }
            audioRoute.disconnect()
            invalidate(null)
        }
    }

    /** Terminal release; call from an owner that outlives individual screens. */
    suspend fun dispose() {
        withContext(Dispatchers.IO) {
            commands.withLock {
                disposed = true
                withContext(Dispatchers.Main.immediate) { unbind() }
                audioRoute.disconnect()
                invalidate(null)
                scope.cancel()
            }
        }
    }

    private suspend fun seek(up: Boolean): Result<Unit> = command("seek(${if (up) "up" else "down"})") {
        val binder = connectedBinder()
        // Ignore repeated taps while the current hardware search is pending/active.
        if (_state.value.scanning || transact(binder, 23) { it.readInt() } != 0) {
            _state.update { it.copy(scanning = true) }
            Log.d(TAG, "Seek already active; no additional scan sent")
            return@command
        }
        open(binder)
        val start = transact(binder, 4) { it.readInt() }
        check(start > 0) { "Tuner returned an invalid start frequency: $start" }
        val epoch = scanEpoch.incrementAndGet()
        _state.update { it.copy(scanning = true) }
        try {
            transact(binder, if (up) 8 else 9, { writeInt(start) }) { Unit }
        } catch (e: Exception) {
            scanEpoch.incrementAndGet()
            _state.update { it.copy(scanning = false) }
            throw e
        }
        // Safety limit, not a station-lock detector. Never restart from a frequency callback.
        scope.launch {
            delay(30_000)
            command("seek watchdog") {
                if (scanEpoch.get() == epoch && remote === binder) {
                    cancelScan(binder)
                    _state.update { it.copy(error = "Recherche arrêtée après 30 s sans fin confirmée") }
                    Log.e(TAG, "Seek timeout; stop sent, no automatic retry")
                }
            }
        }
    }

    private suspend fun open(binder: IBinder) {
        val callback = activeCallback ?: RadioCallback().also { activeCallback = it }
        _state.update { it.copy(routingConfirmed = false, audioChannel = null) }
        Log.d(TAG, "session.open: package=${app.packageName}; focus owner=com.jancar.services, expected RADIO channel=11")
        transact(binder, 1, {
            writeStrongBinder(callback)
            writeString(app.packageName) // Never impersonate com.jancar.radio.
        }) { Unit }
        _state.update { it.copy(openAccepted = true) }
        try {
            awaitRadioRoute(readChannel = { audioRoute.channel() }, onChannel = { channel ->
                if (_state.value.audioChannel != channel) Log.d(TAG, "OEM master audio channel=$channel (RADIO=11)")
                _state.update { it.copy(audioChannel = channel) }
            })
        } catch (e: Exception) {
            // No frequency/seek is issued on an unverified or failed hardware route.
            audioRoute.disconnect()
            throw e
        }
        _state.update { it.copy(routingConfirmed = true) }
        Log.d(TAG, "session.ready: IRadio open accepted; OEM RADIO channel confirmed. Focus remains owned by Jancar.")
        transact(binder, 29) { Unit }
        _state.update { it.copy(openAccepted = true, muted = false) }
    }

    private fun refresh(binder: IBinder) {
        val epoch = stationEpoch.get()
        val band = transact(binder, 6) { it.readInt() }
        val freq = transact(binder, 4) { it.readInt() }
        val stereo = transact(binder, 32) { it.readInt() != 0 }
        val ps = transact(binder, 22, { writeInt(freq) }) { it.readString().orEmpty() }
        _state.update { previous ->
            if (stationEpoch.get() != epoch) previous else previous.copy(
                band = band, frequencyKhz = freq.takeIf { it > 0 }, stereo = stereo, rds = ps,
                signal = if (previous.frequencyKhz == freq) previous.signal else null)
        }
    }

    private fun cancelScan(binder: IBinder) {
        if (_state.value.scanning || transact(binder, 23) { it.readInt() } != 0) {
            Log.d(TAG, "scan.stop: active hardware search")
            transact(binder, 11) { Unit }
        }
        scanEpoch.incrementAndGet()
        _state.update { it.copy(scanning = false) }
    }

    private suspend fun connectedBinder(): IBinder {
        check(!disposed) { "Manager already disposed" }
        val binder = withTimeoutOrNull(5_000) {
            val waiter = withContext(Dispatchers.Main.immediate) {
                remote?.takeIf { it.isBinderAlive }?.let {
                    return@withContext CompletableDeferred(it)
                }
                if (!bound) {
                    pending = CompletableDeferred()
                    val intent = Intent(ACTION).setPackage(PACKAGE)
                    bound = app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                    check(bound) { "Jancar radio binding refused or service absent" }
                }
                pending
            }
            waiter.await()
        } ?: throw RemoteException("Timed out waiting for the Jancar radio service (5 s)")
        // Reject a different vendor interface before sending any numeric transaction.
        check(binder.interfaceDescriptor == DESCRIPTOR) { "Unexpected radio Binder descriptor" }
        _state.update { it.copy(connected = true) }
        return binder
    }

    private suspend fun command(name: String, block: suspend () -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            commands.withLock {
                try {
                    check(!disposed) { "Manager already disposed" }
                    _state.update { it.copy(error = null) }
                    Log.d(TAG, "$name: request")
                    block()
                    Log.d(TAG, "$name: completed (not proof of RF lock)")
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e is DeadObjectException || (e is RemoteException && remote?.isBinderAlive != true)) {
                        audioRoute.disconnect()
                        withContext(Dispatchers.Main.immediate) { unbind() }
                        invalidate("Radio Binder died")
                    }
                    val message = "$name: ${e.javaClass.simpleName}: ${e.message}"
                    Log.e(TAG, message, e)
                    _state.update { it.copy(error = message) }
                    // Never replay a non-idempotent seek automatically.
                    Result.failure(e)
                }
            }
        }

    // Main thread only. Does not call stopService() on the shared vendor service.
    private fun unbind() {
        if (bound) runCatching { app.unbindService(connection) }
            .onFailure { Log.e(TAG, "unbind failed", it) }
        bound = false
        pending.completeExceptionally(RemoteException("Client unbound"))
        pending = CompletableDeferred()
        remote = null
    }

    private fun invalidate(message: String?) {
        remote = null
        stationEpoch.incrementAndGet()
        activeCallback = null
        scanEpoch.incrementAndGet()
        _state.update {
            // A former frequency can remain visible, but is explicitly offline.
            it.copy(connected = false, openAccepted = false, powerOn = null,
                scanning = false, signal = null, stereo = null, error = message,
                routingConfirmed = false, audioChannel = null)
        }
        if (message != null) Log.e(TAG, message)
    }

    private fun frequency(khz: Int) {
        if (khz <= 0) return
        stationEpoch.incrementAndGet()
        _state.update {
            if (it.frequencyKhz == khz) it else it.copy(
                frequencyKhz = khz, band = if (khz >= 10000) 1 else 0, rds = "", stereo = null, signal = null)
        }
        Log.d(TAG, "Tuner frequency reported: $khz kHz (not proof of RF lock)")
    }

    private fun <T> transact(
        binder: IBinder, code: Int,
        args: Parcel.() -> Unit = {}, result: (Parcel) -> T
    ): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.args()
            if (!binder.transact(code, data, reply, 0)) {
                throw RemoteException("Unsupported IRadio transaction: $code")
            }
            reply.readException() // Also propagates permission/SecurityException failures.
            return result(reply)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private inner class RadioCallback : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(CALLBACK)
                return true
            }
            if (code !in 1..30) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(CALLBACK)
            // Discard callbacks belonging to a released/dead client.
            if (activeCallback !== this) { reply?.writeNoException(); return true }
            when (code) {
                1, 4 -> frequency(data.readInt())
                2, 3 -> _state.update { it.copy(powerOn = code == 2,
                    routingConfirmed = if (code == 3) false else it.routingConfirmed) }
                5, 9 -> {
                    val freq = data.readInt(); val strength = data.readInt()
                    if (code == 5) frequency(freq)
                    _state.update { if (it.frequencyKhz == freq) it.copy(signal = strength) else it }
                }
                6, 7, 8 -> {
                    val allStations = data.readInt() != 0
                    if (code != 6) scanEpoch.incrementAndGet()
                    _state.update { it.copy(scanning = code == 6) }
                    Log.d(TAG, "Scan callback=$code allStations=$allStations; not a lock flag")
                }
                12, 13, 14 -> scope.launch {
                    setMuted(when (code) { 12 -> true; 13 -> false; else -> !_state.value.muted })
                }
                10 -> _state.update { it.copy(routingConfirmed = false) } // OEM source suspended.
                15, 18 -> scope.launch { closeRadio() }
                16, 17, 24 -> {
                    val up = if (code == 24) data.readInt() != 0 else code == 16
                    scope.launch { command("step callback") {
                        val binder = connectedBinder()
                        cancelScan(binder)
                        open(binder)
                        transact(binder, 12, { writeInt(if (up) 1 else -1) }) { Unit }
                    } }
                }
                21, 22 -> {
                    data.readInt() // PI
                    val freq = data.readInt(); val text = data.readString().orEmpty()
                    if (code == 21) _state.update { if (it.frequencyKhz == freq) it.copy(rds = text) else it }
                }
                30 -> {
                    val command = data.readInt()
                    data.createIntArray(); data.createFloatArray(); data.createStringArray()
                    if (command == 1) scope.launch { setBand(_state.value.band == 0) }
                }
                25 -> scope.launch { seekUp() }
                26 -> scope.launch { seekDown() }
                29 -> {
                    val freq = data.readInt(); val stereo = data.readInt() != 0
                    _state.update { if (it.frequencyKhz == freq) it.copy(stereo = stereo) else it }
                }
                // Known OEM callbacks outside this basic tuner API: no synchronous IPC
                // from the Binder thread, no auto-seek from frequency/signal updates.
                else -> Log.d(TAG, "OEM callback $code acknowledged without application action")
            }
            reply?.writeNoException()
            return true
        }
    }

    private companion object {
        const val TAG = "JancarRadioManager"
        const val PACKAGE = "com.jancar.services"
        const val ACTION = "com.jancar.services.action.radio"
        const val DESCRIPTOR = "com.jancar.services.radio.IRadio"
        const val CALLBACK = "com.jancar.services.radio.IRadioCallback"
    }
}
