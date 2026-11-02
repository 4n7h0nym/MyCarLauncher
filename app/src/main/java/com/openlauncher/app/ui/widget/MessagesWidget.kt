package com.openlauncher.app.ui.widget

import com.openlauncher.app.ui.components.LocalizedText as LocalizedLabel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.model.IncomingMessage
import com.openlauncher.app.ui.components.VoiceReplyDialog
import com.openlauncher.app.service.CarMessageGateway

@Composable
fun MessagesWidget(
    message: IncomingMessage?,
    onRead: () -> Unit,
    onDismiss: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    var showVoiceReply by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Row(
        modifier = modifier.padding(start = 14.dp, end = 8.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            if (message == null) {
                LocalizedLabel("Aucune nouvelle notification", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            } else {
                LocalizedLabel("${message.sender} · ${message.packageName.substringAfterLast('.')}", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                LocalizedLabel(message.content, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, maxLines = 4)
            }
        }
        if (message != null) {
            Button(
                onClick = { showVoiceReply = true },
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(36.dp).padding(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.Mic, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                LocalizedLabel("Répondre", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        IconButton(onClick = onRead, enabled = message != null) {
            Icon(Icons.Default.RecordVoiceOver, contentDescription = "Lire le message", tint = accent)
        }
        IconButton(onClick = onDismiss, enabled = message != null) {
            Icon(Icons.Default.Close, contentDescription = "Masquer la notification", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showVoiceReply && message != null) {
        VoiceReplyDialog(
            senderName = message.sender,
            accent = accent,
            onSend = { text ->
                CarMessageGateway.sendReply(context, message.key, text)
                showVoiceReply = false
                onDismiss()
            },
            onDismiss = { showVoiceReply = false }
        )
    }
}
