package com.openlauncher.app.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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

    fun sendReply(context: Context, messageKey: String, replyText: String): Boolean {
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
