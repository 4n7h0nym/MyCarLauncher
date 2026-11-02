# Implémentation Complète : Module de Gestion des Messages Bluetooth & Réponse Vocale Sécurisée

Ce module permet d'intercepter les messages provenant du téléphone connecté en Bluetooth (via `NotificationListenerService` ou le SDK Jancar), de les afficher avec une ergonomie "Fat Finger" optimisée pour la conduite, et d'offrir un système de réponse vocale sécurisé avec étape de relecture et confirmation.

---

## 1. Passerelle de Messages (`CarMessageService.kt`)

Service étendant `NotificationListenerService` pour capturer les notifications de messagerie (SMS, WhatsApp, etc.) et stocker l'action de réponse directe (`RemoteInput`).

```kotlin
package com.openlauncher.app.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.openlauncher.app.model.IncomingMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ActiveMessage(
    val key: String,
    val sender: String,
    val content: String,
    val packageName: String,
    val replyAction: Notification.Action?
)

object CarMessageGateway {
    private val _messages = MutableStateFlow<List<ActiveMessage>>(emptyList())
    val messages: StateFlow<List<ActiveMessage>> = _messages

    fun postMessage(msg: ActiveMessage) {
        val current = _messages.value.toMutableList()
        current.removeAll { it.sender == msg.sender || it.key == msg.key }
        current.add(0, msg)
        _messages.value = current
    }

    fun removeMessage(key: String) {
        _messages.value = _messages.value.filter { it.key != key }
    }

    fun sendReply(context: android.content.Context, messageKey: String, replyText: String): Boolean {
        val msg = _messages.value.firstOrNull { it.key == messageKey } ?: return false
        val action = msg.replyAction ?: return false

        val remoteInput = action.remoteInputs?.firstOrNull() ?: return false
        val intent = Intent().apply {
            val bundle = Bundle().apply {
                putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(action.remoteInputs, this, bundle)
        }

        return try {
            action.actionIntent.send(context, 0, intent)
            removeMessage(messageKey)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

class CarMessageService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Inconnu"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (text.isBlank()) return
        val pkg = sbn.packageName

        // Filtrer les applications de messagerie ou Bluetooth
        if (pkg.contains("mms") || pkg.contains("sms") || pkg.contains("whatsapp") ||
            pkg.contains("bluetooth") || extras.containsKey(Notification.EXTRA_MESSAGES)) {

            val replyAction = notification.actions?.firstOrNull { action ->
                action.remoteInputs?.any { input ->
                    input.resultKey.contains("result", ignoreCase = true) ||
                    input.label?.contains("reply", ignoreCase = true) == true
                } == true
            }

            CarMessageGateway.postMessage(
                ActiveMessage(
                    key = sbn.key,
                    sender = title,
                    content = text,
                    packageName = pkg,
                    replyAction = replyAction
                )
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.key?.let { CarMessageGateway.removeMessage(it) }
    }
}
```

---

## 2. Interface de Validation Vocale "Fat Finger" (`VoiceReplyDialog.kt`)

Panneau de confirmation affichant le texte dicté en temps réel avec 3 gros boutons tactiles sécurisés et un timeout de 5 secondes.

```kotlin
package com.openlauncher.app.ui.components

import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun VoiceReplyDialog(
    senderName: String,
    accent: Color,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var spokenText by remember { mutableStateOf("Écoute en cours...") }
    var isListening by remember { mutableStateOf(true) }
    var secondsLeft by remember { mutableStateOf(5) }

    // Timeout de sécurité de 5 secondes en cas d'inactivité
    LaunchedEffect(isListening, spokenText) {
        if (!isListening && spokenText.isNotBlank() && spokenText != "Écoute en cours...") {
            secondsLeft = 5
            while (secondsLeft > 0) {
                delay(1000L)
                secondsLeft--
            }
            if (secondsLeft <= 0) {
                onSend(spokenText)
            }
        }
    }

    DisposableEffect(Unit) {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                spokenText = "Erreur d'écoute. Réessayez."
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    spokenText = matches[0]
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
        onDispose { recognizer.destroy() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 500.dp).padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LocalizedLabel("Répondre à $senderName", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        LocalizedLabel(
                            text = spokenText,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                if (!isListening) {
                    LocalizedLabel("Envoi automatique dans ${secondsLeft}s...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }

                // 3 Boutons Fat Finger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Icon(Icons.Default.Cancel, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        LocalizedLabel("Annuler", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            isListening = true
                            spokenText = "Écoute en cours..."
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = accent)
                        Spacer(Modifier.width(8.dp))
                        LocalizedLabel("Modifier", color = accent, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onSend(spokenText) },
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1.2f).height(56.dp)
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        LocalizedLabel("Envoyer", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
```
