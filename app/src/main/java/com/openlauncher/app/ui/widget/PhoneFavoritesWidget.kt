package com.openlauncher.app.ui.widget

import com.openlauncher.app.ui.components.LocalizedText as LocalizedLabel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.data.BluetoothContactsRepository
import com.openlauncher.app.model.PhoneContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PhoneFavoritesWidget(
    favoritePhoneNumbers: List<String>,
    onCall: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    accent: Color,
    isDayMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var syncedContacts by remember { mutableStateOf<List<PhoneContact>>(emptyList()) }
    LaunchedEffect(favoritePhoneNumbers) {
        syncedContacts = withContext(Dispatchers.IO) {
            runCatching { BluetoothContactsRepository.load(context) }.getOrDefault(emptyList())
        }
    }
    val favorites = remember(syncedContacts, favoritePhoneNumbers) {
        syncedContacts.filter { BluetoothContactsRepository.phoneKey(it.phoneNumber) in favoritePhoneNumbers }
    }
    val textColor = if (isDayMode) Color(0xFF222222) else MaterialTheme.colorScheme.onBackground

    Column(modifier = modifier.padding(start = 14.dp, end = 10.dp, top = 22.dp, bottom = 8.dp)) {
        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LocalizedLabel(
                    if (favoritePhoneNumbers.isEmpty()) "Ajoutez des contacts aux favoris depuis Téléphone" else "Aucun contact favori PBAP disponible",
                    color = textColor.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(favorites.take(5), key = { BluetoothContactsRepository.phoneKey(it.phoneNumber) }) { contact ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 38.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            LocalizedLabel(contact.name, maxLines = 1, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            LocalizedLabel(contact.phoneNumber, maxLines = 1, color = textColor.copy(alpha = 0.55f), fontSize = 10.sp)
                        }
                        IconButton(onClick = { onCall(contact.phoneNumber) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Call, contentDescription = "Appeler ${contact.name}", tint = accent, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onToggleFavorite(BluetoothContactsRepository.phoneKey(contact.phoneNumber)) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Star, contentDescription = "Retirer des favoris", tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
