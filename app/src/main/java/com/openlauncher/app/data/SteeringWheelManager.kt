package com.openlauncher.app.data

import android.content.*
import android.os.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

data class SteeringWheelState(val connected: Boolean=false, val studying: Boolean=false,
    val selected: WheelFunction?=null, val longPress: Boolean=false, val adc: WheelAdc?=null, val rawKey: String="",
    val mappings: List<WheelMapping> = emptyList(), val message: String="", val busy: Boolean=false)

/** Single process owner. No reflection, hidden API, shell/root or permission bypass. */
object SteeringWheelManager {
    private val scope = CoroutineScope(SupervisorJob()+Executors.newSingleThreadExecutor { r -> Thread(r,"WheelStudy").apply { isDaemon=true } }.asCoroutineDispatcher())
    private val _state = MutableStateFlow(SteeringWheelState())
    val state = _state.asStateFlow()
    private var context: Context? = null
    private var remote: IBinder? = null
    private var bound = false
    private var baseline: String? = null
    private var candidate: WheelAdc? = null
    private var count = 0
    private var timeout: Job? = null
    private val file = File("/jancar/config/ivi-studykey.ini")
    @Volatile var launcherVisible = false
    private var lastContextKey = 0L
    private val handler = Handler(Looper.getMainLooper())

    private fun execute(block: () -> Unit) { scope.launch { try { block() } catch (e: Exception) {
        _state.update { it.copy(message=e.message ?: "Erreur de communication",busy=false) }
    } } }
    private fun transact(code: Int, args: Parcel.() -> Unit = {}) {
        val binder=remote ?: error("Service Jancar indisponible")
        val data=Parcel.obtain(); val reply=Parcel.obtain()
        try {
            data.writeInterfaceToken("com.jancar.services.car.ICar"); data.args()
            check(binder.transact(code,data,reply,0)) { "Commande Jancar non prise en charge" }; reply.readException()
        } finally { data.recycle(); reply.recycle() }
    }
    private fun studyMode(on: Boolean) {
        repeat(3) { transact(20) { writeByte(17.toByte()); writeByteArray(byteArrayOf(if(on) 2 else 3)) } }
        _state.update { it.copy(studying=on) }
    }
    private val callback=object: Binder() {
        init { attachInterface(null,"com.jancar.services.car.ICarCallback") }
        override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
            if(code==IBinder.INTERFACE_TRANSACTION) { reply?.writeString("com.jancar.services.car.ICarCallback"); return true }
            data.enforceInterface("com.jancar.services.car.ICarCallback")
            when(code) {
                25 -> {
                    val sample=WheelAdc(data.readInt(),data.readInt(),data.readInt(),data.readInt(),data.readInt())
                    execute { receive(sample) }
                }
                10 -> {
                    val id=data.readInt(); val type=data.readInt(); val visible=launcherVisible
                    execute { _state.update { it.copy(rawKey="id=$id type=$type") }; contextual(id,type,visible) }
                }
                else -> { reply?.writeNoException(); return true } // Other car telemetry is intentionally not subscribed to UI.
            }
            reply?.writeNoException(); return true
        }
    }
    private val connection=object: ServiceConnection {
        override fun onServiceConnected(name:ComponentName?, service:IBinder?) { execute {
            remote=service
            transact(3) { writeStrongBinder(callback) }
            _state.update { it.copy(connected=true,message="Service véhicule connecté") }
        } }
        override fun onServiceDisconnected(name:ComponentName?) { execute {
            remote=null; timeout?.cancel(); _state.update { it.copy(connected=false,studying=false,selected=null,message="Service véhicule déconnecté") }
        } }
        override fun onBindingDied(name:ComponentName?) {
            onServiceDisconnected(name)
            handler.post { context?.let { runCatching { it.unbindService(this) }; bound=false; connect(it) } }
        }
        override fun onNullBinding(name:ComponentName?) { onServiceDisconnected(name) }
    }
    fun connect(ctx:Context) {
        context=ctx.applicationContext
        if(bound) return
        runCatching {
            bound=context!!.bindService(Intent("com.jancar.services.action.car").setPackage("com.jancar.services"),connection,Context.BIND_AUTO_CREATE)
            if(!bound) _state.update { it.copy(message="Service véhicule absent ou accès refusé") }
        }.onFailure { e -> _state.update { it.copy(message=e.message ?: "Accès refusé") } }
    }
    fun load() = execute {
        check(file.isFile && file.canRead()) { "SWC : lecture refusée (UID ${android.os.Process.myUid()}). Utilisez l’application OEM ou une installation signée et autorisée par le firmware." }
        val text=file.readText(); val mappings=WheelStudyCodec.read(text)
        baseline=text; _state.update { it.copy(mappings=mappings,message="Configuration véhicule chargée") }
    }
    fun select(function:WheelFunction, longPress:Boolean=false) = execute {
        check(_state.value.connected) { "Service véhicule indisponible" }
        if(_state.value.studying) studyMode(false)
        candidate=null; count=0
        _state.update { it.copy(selected=function,longPress=longPress,message="Appuyez et maintenez la touche du volant…") }
        studyMode(true)
        timeout?.cancel(); timeout=scope.launch { delay(15000); cancelStudy() }
    }
    private fun receive(sample:WheelAdc) {
        _state.update { it.copy(adc=sample) }
        val selected=_state.value.selected ?: return
        if(!_state.value.studying || !sample.valid()) return
        val normalized=sample.copy(high=if(sample.high>=253)255 else sample.high,mid=if(sample.mid>=253)255 else sample.mid,low=if(sample.low>=253)255 else sample.low)
        if(candidate?.let { it.channel==normalized.channel && it.high==normalized.high && it.mid==normalized.mid && it.low==normalized.low } == true) count++ else { candidate=normalized; count=1 }
        if(count<5) return
        val updated=WheelStudyCodec.assign(_state.value.mappings,normalized,selected,_state.value.longPress)
        timeout?.cancel(); studyMode(false)
        _state.update { it.copy(selected=null,mappings=updated,message="Touche reconnue. Enregistrez pour appliquer au véhicule.") }
    }
    fun cancelStudy() = execute {
        timeout?.cancel()
        if(_state.value.studying) studyMode(false)
        _state.update { it.copy(selected=null,message="Apprentissage arrêté") }
    }
    fun save(reset:Boolean=false) = execute {
        if(_state.value.studying) studyMode(false)
        check(file.canWrite() && file.parentFile?.canWrite()==true) { "SWC : écriture ou sauvegarde refusée (UID ${android.os.Process.myUid()}). Le Binder Study ne donne pas les droits sur le fichier ; utilisez la configuration OEM." }
        val old=baseline ?: error("Chargez la configuration avant de l’enregistrer")
        check(file.readText()==old) { "Configuration modifiée par une autre application : rechargez-la" }
        val entries=if(reset) emptyList() else WheelStudyCodec.entries(_state.value.mappings)
        val updated=WheelStudyCodec.replaceSection(old,entries)
        _state.update { it.copy(busy=true) }
        // Keep a complete backup before touching the original; preserve its inode/ownership/mode.
        val backup=File(file.parentFile,"ivi-studykey.launcher-${System.currentTimeMillis()}.bak")
        FileOutputStream(backup).use { it.write(old.toByteArray(Charsets.UTF_8)); it.fd.sync() }
        try { FileOutputStream(file).use { it.write(updated.toByteArray(Charsets.UTF_8)); it.fd.sync() } }
        catch(e:Exception) { runCatching { FileOutputStream(file).use { it.write(old.toByteArray(Charsets.UTF_8)); it.fd.sync() } }; throw e }
        baseline=updated
        _state.update { it.copy(busy=false,mappings=if(reset) emptyList() else it.mappings,message="Configuration enregistrée. Vérifiez les touches hors apprentissage.") }
    }
    fun setContextual(enabled:Boolean) { context?.getSharedPreferences("wheel_context",Context.MODE_PRIVATE)?.edit()?.putBoolean("enabled",enabled)?.apply() }
    fun contextualEnabled()=context?.getSharedPreferences("wheel_context",Context.MODE_PRIVATE)?.getBoolean("enabled",false) ?: false
    private fun contextual(id:Int,type:Int,wasVisible:Boolean) {
        if(id==106 && type in listOf(1,2) && !_state.value.studying) {
            val now=SystemClock.elapsedRealtime(); if(now-lastContextKey<700) return; lastContextKey=now
            val ctx=context ?: return
            handler.post { runCatching { ctx.startActivity(Intent(ctx,com.openlauncher.app.MainActivity::class.java).putExtra("launcher_voice",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { e -> _state.update { it.copy(message=e.message ?: "Assistant indisponible") } } }
            return
        }
        if(id!=1004 || type !in listOf(1,2) || _state.value.studying || !contextualEnabled()) return
        val now=SystemClock.elapsedRealtime(); if(now-lastContextKey<700) return; lastContextKey=now
        val ctx=context ?: return
        val settings=ctx.getSharedPreferences("launcher_runtime",Context.MODE_PRIVATE)
        val configured=settings.getString("wheel_android_auto_package","").orEmpty()
        val intent=if(!wasVisible) Intent(ctx,com.openlauncher.app.MainActivity::class.java)
            else (listOf(configured,"com.auto.zlink","com.zjinnova.zlink").filter { it.isNotBlank() }.firstNotNullOfOrNull { ctx.packageManager.getLaunchIntentForPackage(it) }
                ?: error("Application Android Auto introuvable : configurez-la dans les réglages"))
        handler.post { runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { e -> _state.update { it.copy(message=e.message ?: "Lancement impossible") } } }
    }
}
