package com.openlauncher.app.ui.widget

import com.openlauncher.app.ui.components.LocalizedText as LocalizedLabel

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(favorites, key = { BluetoothContactsRepository.phoneKey(it.phoneNumber) }) { contact ->
                    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                        ContactAvatar(name = contact.name, photoUri = contact.photoUri, accent = accent, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            androidx.compose.material3.Text(contact.name, maxLines = 1, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            androidx.compose.material3.Text(contact.phoneNumber, maxLines = 1, color = textColor.copy(alpha = 0.55f), fontSize = 12.sp)
                        }
                        IconButton(onClick = { onCall(contact.phoneNumber) }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.Call, contentDescription = "Appeler ${contact.name}", tint = accent, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { onToggleFavorite(BluetoothContactsRepository.phoneKey(contact.phoneNumber)) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Star, contentDescription = "Retirer des favoris", tint = Color(0xFFFFC107), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactAvatar(
    name: String,
    photoUri: String?,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val initials = remember(name) {
        name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercase() }
            .joinToString("")
            .ifBlank { "?" }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUri.isNullOrBlank()) {
            AsyncImage(
                model = Uri.parse(photoUri),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LocalizedLabel(
                text = initials,
                color = accent,
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
