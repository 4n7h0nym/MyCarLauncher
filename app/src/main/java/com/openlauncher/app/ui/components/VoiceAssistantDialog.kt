package com.openlauncher.app.ui.components

import com.openlauncher.app.ui.components.LocalizedText as LocalizedLabel

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.window.Dialog
import com.openlauncher.app.util.VoiceAssistantManager

@Composable
fun VoiceAssistantDialog(
    accent: Color,
    languageTag: String,
    onCommand: (String) -> String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember(context) { VoiceAssistantManager(context) }
    var listening by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var status by remember(languageTag) { mutableStateOf(if (languageTag.startsWith("en")) "Tap the microphone and say a command" else "Touchez le micro et dites une commande") }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        status = if (languageTag.startsWith("en")) {
            if (granted) "Tap the microphone again to speak" else "Microphone access was denied."
        } else if (granted) "Touchez encore le micro pour parler" else "L’accès au microphone a été refusé."
    }
    val pulse by rememberInfiniteTransition(label = "voice-pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "mic-pulse"
    )

    fun beginListening() {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        transcript = ""
        status = if (languageTag.startsWith("en")) "I'm listening…" else "Je vous écoute…"
        listening = true
        manager.listen(
            languageTag = languageTag,
            onPartial = { transcript = it },
            onFinal = { result ->
                transcript = result
                listening = false
                status = onCommand(result)
            },
            onFailure = {
                listening = false
                status = it
            }
        )
    }

    DisposableEffect(manager) { onDispose { manager.close() } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 12.dp) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LocalizedLabel("ASSISTANT VOCAL", color = accent, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Fermer") }
                }
                Box(
                    Modifier.size(84.dp).scale(if (listening) pulse else 1f).background(accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { beginListening() }, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.Mic, contentDescription = "Écouter une commande", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
                LocalizedLabel(transcript.ifBlank { status }, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                if (!hasPermission) {
                    LocalizedLabel(if (languageTag.startsWith("en")) "Microphone access is required." else "L’accès au microphone est nécessaire.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
    }
}
