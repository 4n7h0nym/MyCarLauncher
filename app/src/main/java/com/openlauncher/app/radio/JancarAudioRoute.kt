package com.openlauncher.app.radio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Read-only OEM channel check, from the supplied IAudio Stub/Proxy (transaction 50). */
internal class JancarAudioRoute(context: Context) {
    private val app = context.applicationContext
    @Volatile private var remote: IBinder? = null
    // Binding bookkeeping stays on the main thread; caller serializes all public operations.
    private var bound = false
    private var pending = CompletableDeferred<IBinder>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (!bound) return
            remote = service
            pending.complete(service)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            remote = null
            pending.completeExceptionally(RemoteException("Jancar audio disconnected"))
            pending = CompletableDeferred()
        }
        override fun onBindingDied(name: ComponentName) { releaseBinding() }
        override fun onNullBinding(name: ComponentName) { releaseBinding() }
    }

    suspend fun channel(): Int {
        val binder = withTimeoutOrNull(1500) {
            val waiter = withContext(Dispatchers.Main.immediate) {
                remote?.takeIf { it.isBinderAlive }?.let { return@withContext CompletableDeferred(it) }
                if (!bound) {
                    pending = CompletableDeferred()
                    bound = app.bindService(Intent("com.jancar.services.action.audio")
                        .setPackage("com.jancar.services"), connection, Context.BIND_AUTO_CREATE)
                    check(bound) { "Liaison au service audio Jancar refusée" }
                }
                pending
            }
            waiter.await()
        } ?: throw RemoteException("Service audio Jancar indisponible après 1500 ms")
        check(binder.interfaceDescriptor == DESCRIPTOR) { "Interface audio Jancar incompatible" }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            if (!binder.transact(50, data, reply, 0)) throw RemoteException("getMasterAudioChannel non pris en charge")
            reply.readException()
            return reply.readInt()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.Main.immediate) { releaseBinding() }

    private fun releaseBinding() {
        if (bound) runCatching { app.unbindService(connection) }
        bound = false
        remote = null
        pending.completeExceptionally(RemoteException("Jancar audio unbound"))
        pending = CompletableDeferred()
    }

    companion object {
        const val RADIO_CHANNEL = 11 // IVIAudio.Channel.RADIO in the supplied OEM SDK.
        private const val DESCRIPTOR = "com.jancar.services.audio.IAudio"
    }
}
