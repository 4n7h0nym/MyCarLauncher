package com.openlauncher.app.ui.widget

import com.openlauncher.app.ui.components.LocalizedText as LocalizedLabel

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.model.IncomingMessage

@Composable
fun MessagesWidget(
    message: IncomingMessage?,
    onRead: () -> Unit,
    onDismiss: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
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
        IconButton(onClick = onRead, enabled = message != null) {
            Icon(Icons.Default.RecordVoiceOver, contentDescription = "Lire le message", tint = accent)
        }
        IconButton(onClick = onDismiss, enabled = message != null) {
            Icon(Icons.Default.Close, contentDescription = "Masquer la notification", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
