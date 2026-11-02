package com.openlauncher.app.data

import android.content.*
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Binding establishes service availability; the OEM binder has no remote tuning AIDL. */
object NativeFmServiceClient {
    private val component=ComponentName("com.jancar.radio","com.jancar.radio.FmService")
    private var app:Context?=null
    @Volatile private var bindingAccepted=false
    private val _connected=MutableStateFlow(false)
    val connected=_connected.asStateFlow()
    private val connection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName,service:IBinder) {
            if(bindingAccepted) _connected.value=true
        }
        override fun onServiceDisconnected(name:ComponentName) { _connected.value=false }
        override fun onNullBinding(name:ComponentName) { close() }
        override fun onBindingDied(name:ComponentName) { close() }
    }
    @Synchronized
    fun connect(context:Context):Result<Unit> = runCatching {
        if(bindingAccepted) return@runCatching
        app=context.applicationContext
        bindingAccepted=app!!.bindService(Intent("com.jancar.radio.IFmRadioService").setComponent(component),connection,Context.BIND_AUTO_CREATE)
        check(bindingAccepted) { "FmService : liaison refusée" }
    }
    fun seek(context:Context,up:Boolean):Result<Unit> = send(context,if(up) "fmradio.seek.next" else "fmradio.seek.previous")
    fun powerOff(context:Context):Result<Unit> = send(context,"fmradio.turnoff")
    private fun send(context:Context,action:String):Result<Unit> = runCatching {
        connect(context).getOrThrow()
        checkNotNull(context.applicationContext.startService(Intent(action).setComponent(component))) { "FmService : commande refusée" }
    }
    @Synchronized
    fun close() {
        val context=app
        if(bindingAccepted && context!=null) runCatching { context.unbindService(connection) }
        bindingAccepted=false;_connected.value=false;app=null
        // Do not stop the shared radio service when leaving the launcher.
    }
}
