package com.openlauncher.app.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openlauncher.app.data.WheelFunction
import com.openlauncher.app.viewmodel.SteeringWheelViewModel

@Composable
fun SteeringWheelSettingsScreen(onBack:()->Unit,modifier:Modifier=Modifier) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val oemIntent=remember(context) { context.packageManager.getLaunchIntentForPackage("com.jancar.steeringwheelkeys") }
    val vm:SteeringWheelViewModel=viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var longPress by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var contextual by remember { mutableStateOf(vm.contextualEnabled()) }
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) vm.cancel() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); vm.cancel() }
    }
    BackHandler { vm.cancel(); onBack() }
    Column(modifier.fillMaxSize().padding(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        FlowRowWithActions(onBack={ vm.cancel();onBack() },onSave=vm::save,onCancel=vm::cancel,onReload=vm::reload,onReset={confirmReset=true},enabled=!state.busy)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=!longPress,onClick={vm.cancel();longPress=false},label={Text("Appui court")},modifier=Modifier.heightIn(min=64.dp))
            FilterChip(selected=longPress,onClick={vm.cancel();longPress=true},label={Text("Appui long")},modifier=Modifier.heightIn(min=64.dp))
        }
        Text("À configurer à l’arrêt : choisissez une fonction, puis maintenez la touche physique. Enregistrez les associations reconnues.",style=MaterialTheme.typography.bodyLarge)
        if(oemIntent!=null) OutlinedButton(onClick={vm.cancel();runCatching { context.startActivity(oemIntent) }.onFailure { android.widget.Toast.makeText(context,"Configuration OEM inaccessible",android.widget.Toast.LENGTH_LONG).show() }},modifier=Modifier.heightIn(min=64.dp)) { Text("Ouvrir l’apprentissage OEM") }
        Text(state.message.ifBlank { "Connexion au service véhicule…" },color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleMedium)
        state.adc?.let { Text("ADC canal ${it.channel} · valeur ${it.value} · H ${it.high} / M ${it.mid} / L ${it.low}",style=MaterialTheme.typography.bodyMedium) }
        if(state.rawKey.isNotBlank()) Text("Signal : ${state.rawKey}")
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("Bouton Accueil / Android Auto contextuel",Modifier.weight(1f))
            Switch(checked=contextual,onCheckedChange={ contextual=it;vm.setContextual(it) })
        }
        LazyVerticalGrid(columns=GridCells.Adaptive(180.dp),modifier=Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(WheelFunction.entries,key={it.name}) { function ->
                val assigned=state.mappings.any { if(longPress) it.longAction==function.action else it.action==function.action }
                FilledTonalButton(onClick={vm.select(function,longPress)},enabled=state.connected&&!state.busy,
                    modifier=Modifier.fillMaxWidth().heightIn(min=88.dp),
                    colors=ButtonDefaults.filledTonalButtonColors(containerColor=if(state.selected==function) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Text(function.label+(if(assigned) " ✓" else ""),fontSize=20.sp)
                }
            }
        }
    }
    if(confirmReset) AlertDialog(onDismissRequest={confirmReset=false},title={Text("Réinitialiser les touches apprises ?")},text={Text("Cette action efface uniquement la section des commandes au volant dans la configuration véhicule. Une sauvegarde du fichier est créée avant l’écriture.")},
        confirmButton={TextButton(onClick={confirmReset=false;vm.reset()}){Text("Réinitialiser")}},dismissButton={TextButton(onClick={confirmReset=false}){Text("Annuler")}})
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowWithActions(onBack:()->Unit,onSave:()->Unit,onCancel:()->Unit,onReload:()->Unit,onReset:()->Unit,enabled:Boolean) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("Retour" to onBack,"Enregistrer" to onSave,"Arrêter" to onCancel,"Recharger" to onReload,"Réinitialiser" to onReset).forEach { (label,action) ->
            OutlinedButton(onClick=action,enabled=enabled,modifier=Modifier.heightIn(min=64.dp)){Text(label)}
        }
    }
}
