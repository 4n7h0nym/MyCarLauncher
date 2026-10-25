package com.openlauncher.app.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.os.RemoteException
import androidx.core.content.ContextCompat
import com.openlauncher.app.model.PhoneContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.Collections

/**
 * Narrow Binder client for the Jancar head-unit Bluetooth service.
 * Transaction IDs and parcel layouts match the interfaces in the supplied
 * Jancar decompilation (package com.jancar.btservice.bluetooth).
 */
object JancarBluetoothManager {
    private const val SERVICE_PACKAGE = "com.jancar.btservice"
    private const val SERVICE_ACTION = "com.jancar.btservice.action.bluetooth"
    private const val SERVICE_DESCRIPTOR = "com.jancar.btservice.bluetooth.IBluetooth"
    private const val EXEC_CALLBACK_DESCRIPTOR = "com.jancar.btservice.bluetooth.IBluetoothExecCallback"
    private const val VCARD_CALLBACK_DESCRIPTOR = "com.jancar.btservice.bluetooth.IBluetoothVCardCallback"
    private const val BLUETOOTH_CALLBACK_DESCRIPTOR = "com.jancar.btservice.bluetooth.IBluetoothCallback"
    private const val ACTION_PLAY_STATUS = "com.jancar.services.action.response_play_status"

    private const val TRANSACTION_NEXT = 8
    private const val TRANSACTION_PREVIOUS = 9
    private const val TRANSACTION_PLAY = 11
    private const val TRANSACTION_PAUSE = 12
    private const val TRANSACTION_GET_ID3 = 14
    private const val TRANSACTION_CALL = 23
    private const val TRANSACTION_HANGUP = 25
    private const val TRANSACTION_LISTEN_PHONE = 27
    private const val TRANSACTION_GET_CONTACTS = 40
    private const val TRANSACTION_REGISTER_CALLBACK = 38
    private const val TRANSACTION_UNREGISTER_CALLBACK = 39

    data class Playback(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val durationMs: Long = 0L,
        val isPlaying: Boolean = false
    )

    private val binderExecutor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Jancar-Binder").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val rebindRunnable = Runnable { appContext?.let(::connect) }
    private val musicInfoRefresh = object : Runnable {
        override fun run() {
            if (remote == null) return
            requestMusicInfo()
            mainHandler.postDelayed(this, 2_500L)
        }
    }
    private val _isBound = MutableStateFlow(false)
    val isBound: StateFlow<Boolean> = _isBound
    private val _playback = MutableStateFlow<Playback?>(null)
    val playback: StateFlow<Playback?> = _playback
    private val _contacts = MutableStateFlow<List<PhoneContact>>(emptyList())
    val contacts: StateFlow<List<PhoneContact>> = _contacts
    @Volatile private var contactsRequestInFlight = false
    @Volatile private var contactRefreshQueued = false
    @Volatile private var contactRetryAttempts = 0
    private val contactSyncRetry = object : Runnable {
        override fun run() {
            if (remote == null || _contacts.value.isNotEmpty() || contactRetryAttempts >= 8) return
            contactRetryAttempts++
            requestContacts(force = true)
            mainHandler.postDelayed(this, 4_000L)
        }
    }
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError
    private val _callStatus = MutableStateFlow(CALL_STATUS_NORMAL)
    val callStatus: StateFlow<Int> = _callStatus

    @Volatile private var appContext: Context? = null
    @Volatile private var remote: IBinder? = null
    @Volatile private var a2dpConnected = false
    private var bindRequested = false
    private var receiverRegistered = false
    private var callbackRegistered = false
    private val pendingCommandCallbacks = Collections.synchronizedSet(mutableSetOf<ExecCallbackBinder>())

    private val execCallbackBinder = ExecCallbackBinder { result ->
        result.fold(
            onSuccess = { _lastError.value = null },
            onFailure = { error -> _lastError.value = describeCommandError(error) }
        )
    }
    private val vCardCallbackBinder = VCardCallbackBinder(
        onProgress = { entries ->
            val mapped = entries.mapNotNull { card ->
                val number = card.phoneNumber?.trim().orEmpty()
                val name = card.name?.trim().orEmpty()
                if (name.isBlank() || number.isBlank()) null
                else PhoneContact("jancar:${BluetoothContactsRepository.phoneKey(number)}", name, number)
            }
            _contacts.value = (_contacts.value + mapped)
                .distinctBy { BluetoothContactsRepository.phoneKey(it.phoneNumber) }
                .sortedBy { it.name.lowercase() }
        },
        onFailure = { code ->
            finishContactRequest()
            _lastError.value = "Jancar contact request failed ($code)"
        },
        onComplete = { finishContactRequest() }
    )
    private val bluetoothCallbackBinder = BluetoothCallbackBinder(
        onId3 = { title, artist, album, duration ->
            val old = _playback.value ?: Playback()
            _playback.value = old.copy(
                title = title.ifBlank { old.title },
                artist = artist.ifBlank { old.artist },
                album = album.ifBlank { old.album },
                durationMs = duration.takeIf { it > 0L } ?: old.durationMs
            )
        },
        onA2dpStatus = { status, _ ->
            // Jancar reports A2DP connection status 3 for connected. The first
            // metadata query can happen before the phone finishes connecting.
            if (status == 3) {
                val newlyConnected = !a2dpConnected
                a2dpConnected = true
                requestMusicInfo()
                if (newlyConnected) {
                    requestContacts(force = true)
                    scheduleContactSyncRetry(resetAttempts = true)
                }
                mainHandler.removeCallbacks(musicInfoRefresh)
                mainHandler.postDelayed(musicInfoRefresh, 1_000L)
            } else if (status == 0 || status == 4) {
                a2dpConnected = false
                setPlaying(false)
            }
        },
        onPhoneConnectionStatus = { status ->
            if (status != 0) {
                requestContacts(force = true)
                scheduleContactSyncRetry(resetAttempts = true)
            }
        },
        onCallStatus = { status ->
            _callStatus.value = status
        }
    )

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_PLAY_STATUS) return
            val old = _playback.value ?: Playback()
            val title = intent.stringExtra("title", "track", "name", "music_name", "musicName")
            val artist = intent.stringExtra("artist", "singer", "music_artist", "musicArtist")
            val album = intent.stringExtra("album", "music_album", "musicAlbum")
            val playing = intent.booleanExtra(
                "isPlaying", "is_playing", "playing", "play_status", "playStatus",
                "play_state", "playState", "music_play_status", "bt_music_play_status",
                "state", "status"
            )
            _playback.value = old.copy(
                title = title ?: old.title,
                artist = artist ?: old.artist,
                album = album ?: old.album,
                isPlaying = playing ?: old.isPlaying
            )
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service == null) {
                _lastError.value = "Jancar returned an empty Binder"
                return
            }
            val descriptor = runCatching { service.interfaceDescriptor }.getOrNull()
            if (descriptor != SERVICE_DESCRIPTOR) {
                _lastError.value = "Unexpected Jancar Binder interface: ${descriptor ?: "unknown"}"
                remote = null
                _isBound.value = false
                return
            }
            remote = service
            _isBound.value = true
            _lastError.value = null
            runCatching {
                service.linkToDeath({ handleBinderDeath(service) }, 0)
                transact(TRANSACTION_REGISTER_CALLBACK) { writeStrongBinder(bluetoothCallbackBinder) }
                callbackRegistered = true
                requestMusicInfo()
                requestContacts()
                scheduleContactSyncRetry(resetAttempts = true)
                mainHandler.removeCallbacks(musicInfoRefresh)
                mainHandler.postDelayed(musicInfoRefresh, 1_000L)
            }.onFailure { _lastError.value = it.message ?: "Unable to initialize Jancar service" }
        }

        override fun onServiceDisconnected(name: ComponentName?) = clearRemote()
        override fun onBindingDied(name: ComponentName?) {
            clearRemote()
            rebindAfterBindingDeath()
        }
        override fun onNullBinding(name: ComponentName?) {
            clearRemote()
            _lastError.value = "Jancar service returned no Binder"
        }
    }

    @Synchronized
    fun connect(context: Context): Boolean {
        if (bindRequested) return true
        val app = context.applicationContext
        appContext = app
        registerStatusReceiver(app)
        val intent = Intent(SERVICE_ACTION).setPackage(SERVICE_PACKAGE)
        return try {
            bindRequested = app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!bindRequested) _lastError.value = "Jancar Bluetooth service is not installed or exported"
            bindRequested
        } catch (error: Exception) {
            _lastError.value = error.message ?: "Unable to bind to Jancar Bluetooth service"
            false
        }
    }

    @Synchronized
    fun disconnect() {
        mainHandler.removeCallbacks(rebindRunnable)
        val app = appContext ?: return
        val service = remote
        if (service != null && callbackRegistered) {
            runCatching { transact(TRANSACTION_UNREGISTER_CALLBACK) { writeStrongBinder(bluetoothCallbackBinder) } }
            callbackRegistered = false
        }
        clearRemote()
        if (bindRequested) runCatching { app.unbindService(serviceConnection) }
        bindRequested = false
        unregisterStatusReceiver(app)
        appContext = null
    }

    fun playBtMusic(): Boolean = sendCommand(TRANSACTION_PLAY, commandCallback { setPlaying(true) })
    fun pauseBtMusic(): Boolean = sendCommand(TRANSACTION_PAUSE, commandCallback { setPlaying(false) })
    fun nextBtMusic(): Boolean = sendCommand(TRANSACTION_NEXT, commandCallback(::refreshTrackAfterSkip))
    fun previousBtMusic(): Boolean = sendCommand(TRANSACTION_PREVIOUS, commandCallback(::refreshTrackAfterSkip))
    fun hangPhone(): Boolean = sendCommand(TRANSACTION_HANGUP)
    fun listenPhone(): Boolean = sendCommand(TRANSACTION_LISTEN_PHONE, commandCallback())

    fun hasActiveCall(): Boolean = _callStatus.value in ACTIVE_CALL_STATUSES
    fun isIncomingCall(): Boolean = _callStatus.value == CALL_STATUS_INCOMING ||
        _callStatus.value == CALL_STATUS_THREE_INCOMING

    fun callPhone(number: String): Boolean {
        val normalized = number.trim()
        if (normalized.isBlank()) return false
        return sendCommand(TRANSACTION_CALL) { writeString(normalized) }
    }

    fun requestMusicInfo(): Boolean = sendCommand(TRANSACTION_GET_ID3)

    fun isA2dpConnected(): Boolean = a2dpConnected

    private fun refreshTrackAfterSkip() {
        listOf(350L, 900L, 1_800L).forEach { delayMs ->
            mainHandler.postDelayed({ requestMusicInfo() }, delayMs)
        }
    }

    @Synchronized
    fun requestContacts(force: Boolean = false): Boolean {
        if (remote == null) {
            if (!force) return false
            contactRefreshQueued = true
            return appContext?.let(::connect) ?: false
        }
        if (contactsRequestInFlight) {
            if (force) contactRefreshQueued = true
            return true
        }
        if (!force && _contacts.value.isNotEmpty()) return true
        contactsRequestInFlight = true
        return sendCommand(TRANSACTION_GET_CONTACTS, vCardCallbackBinder).also { requested ->
            if (!requested) contactsRequestInFlight = false
        }
    }

    @Synchronized
    private fun finishContactRequest() {
        contactsRequestInFlight = false
        if (_contacts.value.isNotEmpty()) mainHandler.removeCallbacks(contactSyncRetry)
        if (contactRefreshQueued) {
            contactRefreshQueued = false
            mainHandler.post { requestContacts(force = true) }
        }
    }

    private fun sendCommand(
        code: Int,
        callback: IBinder = execCallbackBinder,
        writeArguments: Parcel.() -> Unit = {}
    ): Boolean {
        if (remote == null) return false
        return runCatching {
            binderExecutor.execute {
                runCatching {
                    transact(code) {
                        writeArguments()
                        writeStrongBinder(callback)
                    }
                }.onFailure { _lastError.value = it.message ?: "Jancar command failed" }
            }
            true
        }.getOrDefault(false)
    }

    private fun commandCallback(onSuccess: () -> Unit = {}): IBinder {
        lateinit var callbackBinder: ExecCallbackBinder
        callbackBinder = ExecCallbackBinder { result ->
            result.fold(
                onSuccess = {
                    _lastError.value = null
                    onSuccess()
                },
                onFailure = { error -> _lastError.value = describeCommandError(error) }
            )
            pendingCommandCallbacks.remove(callbackBinder)
        }
        pendingCommandCallbacks.add(callbackBinder)
        mainHandler.postDelayed({ pendingCommandCallbacks.remove(callbackBinder) }, 30_000L)
        return callbackBinder
    }

    private fun setPlaying(isPlaying: Boolean) {
        val old = _playback.value ?: Playback()
        _playback.value = old.copy(isPlaying = isPlaying)
    }

    private fun scheduleContactSyncRetry(resetAttempts: Boolean = false) {
        if (resetAttempts) contactRetryAttempts = 0
        mainHandler.removeCallbacks(contactSyncRetry)
        mainHandler.postDelayed(contactSyncRetry, 2_000L)
    }

    private fun transact(code: Int, writeArguments: Parcel.() -> Unit = {}) {
        val binder = remote ?: throw RemoteException("Jancar service is disconnected")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR)
            data.writeArguments()
            if (!binder.transact(code, data, reply, 0)) throw RemoteException("Jancar rejected transaction $code")
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun handleBinderDeath(deadBinder: IBinder) {
        if (remote === deadBinder) clearRemote()
    }

    private fun describeCommandError(error: Throwable): String {
        val code = error.message?.substringAfterLast(' ')?.toIntOrNull()
        return when (code) {
            2 -> "Jancar : aucun téléphone Bluetooth actif."
            15 -> "Jancar : l’audio est indisponible pendant un appel."
            19 -> "Jancar : aucun flux audio A2DP connecté au téléphone."
            22 -> "Jancar : une autre source audio (Android Auto/QLink) est prioritaire."
            else -> error.message ?: "Échec de la commande média Jancar."
        }
    }

    @Synchronized
    private fun rebindAfterBindingDeath() {
        val app = appContext ?: return
        if (bindRequested) runCatching { app.unbindService(serviceConnection) }
        bindRequested = false
        mainHandler.removeCallbacks(rebindRunnable)
        mainHandler.postDelayed(rebindRunnable, 1_000L)
    }

    @Synchronized
    private fun clearRemote() {
        mainHandler.removeCallbacks(musicInfoRefresh)
        mainHandler.removeCallbacks(contactSyncRetry)
        contactsRequestInFlight = false
        contactRefreshQueued = false
        a2dpConnected = false
        remote = null
        callbackRegistered = false
        _isBound.value = false
        _playback.value = null
        _callStatus.value = CALL_STATUS_NORMAL
    }

    private fun registerStatusReceiver(context: Context) {
        if (receiverRegistered) return
        val filter = IntentFilter(ACTION_PLAY_STATUS)
        runCatching {
            ContextCompat.registerReceiver(context, statusReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            receiverRegistered = true
        }
    }

    private fun unregisterStatusReceiver(context: Context) {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(statusReceiver) }
        receiverRegistered = false
    }

    private class ExecCallbackBinder(private val onResult: (Result<String>) -> Unit) : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                reply?.writeString(EXEC_CALLBACK_DESCRIPTOR)
                return true
            }
            data.enforceInterface(EXEC_CALLBACK_DESCRIPTOR)
            when (code) {
                1 -> onResult(Result.success(data.readString().orEmpty()))
                2 -> onResult(Result.failure(RemoteException("Jancar error ${data.readInt()}")))
                else -> return super.onTransact(code, data, reply, flags)
            }
            reply?.writeNoException()
            return true
        }
    }

    private class VCardCallbackBinder(
        private val onProgress: (List<JancarVCard>) -> Unit,
        private val onFailure: (Int) -> Unit,
        private val onComplete: () -> Unit
    ) : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                reply?.writeString(VCARD_CALLBACK_DESCRIPTOR)
                return true
            }
            data.enforceInterface(VCARD_CALLBACK_DESCRIPTOR)
            when (code) {
                1 -> onProgress(data.createTypedArrayList(JancarVCard.CREATOR).orEmpty())
                2 -> onFailure(data.readInt())
                3 -> onComplete() // Terminal success notification; contacts arrive in progress chunks.
                else -> return super.onTransact(code, data, reply, flags)
            }
            reply?.writeNoException()
            return true
        }
    }

    private class BluetoothCallbackBinder(
        private val onId3: (String, String, String, Long) -> Unit,
        private val onA2dpStatus: (Int, Boolean) -> Unit,
        private val onPhoneConnectionStatus: (Int) -> Unit,
        private val onCallStatus: (Int) -> Unit
    ) : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                reply?.writeString(BLUETOOTH_CALLBACK_DESCRIPTOR)
                return true
            }
            data.enforceInterface(BLUETOOTH_CALLBACK_DESCRIPTOR)
            when (code) {
                1 -> {
                    val status = data.readInt()
                    data.readString()
                    data.readString()
                    onPhoneConnectionStatus(status)
                }
                2 -> {
                    val status = data.readInt()
                    data.readString()
                    data.readString()
                    onCallStatus(status)
                }
                3, 6, 7, 8 -> data.readInt()
                4 -> onA2dpStatus(data.readInt(), data.readInt() != 0)
                5 -> onId3(data.readString().orEmpty(), data.readString().orEmpty(), data.readString().orEmpty(), data.readLong())
                else -> return super.onTransact(code, data, reply, flags)
            }
            reply?.writeNoException()
            return true
        }
    }

    data class JancarVCard(
        val codingType: String?,
        val name: String?,
        val type: String?,
        val phoneNumber: String?,
        val callTime: String?
    ) : Parcelable {
        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeString(codingType)
            dest.writeString(name)
            dest.writeString(type)
            dest.writeString(phoneNumber)
            dest.writeString(callTime)
        }
        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<JancarVCard> {
            override fun createFromParcel(source: Parcel) = JancarVCard(
                source.readString(), source.readString(), source.readString(), source.readString(), source.readString()
            )
            override fun newArray(size: Int): Array<JancarVCard?> = arrayOfNulls(size)
        }
    }

    private fun Intent.stringExtra(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        runCatching { extras?.get(key)?.toString()?.takeIf(String::isNotBlank) }.getOrNull()
    }

    private fun Intent.booleanExtra(vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { key ->
        when (val value = runCatching { extras?.get(key) }.getOrNull()) {
            is Boolean -> value
            is Int -> when (value) { 1 -> true; 0 -> false; else -> null }
            is Long -> when (value) { 1L -> true; 0L -> false; else -> null }
            is String -> when (value.lowercase()) {
                "true", "playing", "play", "1", "on" -> true
                "false", "paused", "pause", "0", "off", "stop", "stopped" -> false
                else -> null
            }
            else -> null
        }
    }

    private const val CALL_STATUS_NORMAL = 0
    private const val CALL_STATUS_INCOMING = 1
    private const val CALL_STATUS_THREE_INCOMING = 5
    private val ACTIVE_CALL_STATUSES = setOf(1, 2, 4, 5, 6, 7, 8)
}
