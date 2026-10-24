package com.openlauncher.app.ui.screen

import com.openlauncher.app.ui.components.LocalizedText as LocalizedLabel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.openlauncher.app.data.BluetoothContactsRepository
import com.openlauncher.app.model.PhoneContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PhoneScreen(
    accent: Color,
    onCall: (String) -> Unit,
    favoritePhoneNumbers: List<String> = emptyList(),
    onToggleFavorite: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var dialText by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<PhoneContact>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var accessDenied by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Clavier & Contacts, 1 = Favoris

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.toTypedArray()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        accessDenied = result.values.any { !it }
        refreshToken++
    }
    val hasContactsPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(refreshToken, hasContactsPermission) {
        if (hasContactsPermission) {
            loading = true
            contacts = withContext(Dispatchers.IO) {
                runCatching { BluetoothContactsRepository.load(context) }.getOrDefault(emptyList())
            }
            loading = false
        } else {
            contacts = emptyList()
        }
    }

    val filteredContacts = remember(contacts, dialText) {
        if (dialText.isBlank()) contacts
        else contacts.filter { it.name.contains(dialText, ignoreCase = true) || it.phoneNumber.contains(dialText) }
    }

    val favoriteContacts = remember(contacts, favoritePhoneNumbers) {
        contacts.filter { BluetoothContactsRepository.phoneKey(it.phoneNumber) in favoritePhoneNumbers }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Tabs
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Contacts, null, tint = accent)
                Spacer(Modifier.width(10.dp))
                Column {
                    LocalizedLabel("Téléphone", style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
                    LocalizedLabel("Contacts synchronisés depuis le téléphone Bluetooth (PBAP)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { LocalizedLabel("Clavier & Répertoire") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent, selectedLabelColor = Color.Black)
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { LocalizedLabel("Favoris (${favoriteContacts.size})") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent, selectedLabelColor = Color.Black)
                )
                IconButton(onClick = { refreshToken++ }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualiser les contacts", tint = accent)
                }
            }
        }

        if (selectedTab == 0) {
            OutlinedTextField(
                value = dialText,
                onValueChange = { dialText = it },
                modifier = Modifier.fillMaxWidth(0.7f),
                singleLine = true,
                placeholder = { LocalizedLabel("Nom ou numéro") },
                trailingIcon = {
                    if (dialText.isNotEmpty()) IconButton(onClick = { dialText = "" }) {
                        Icon(Icons.Default.Backspace, contentDescription = "Effacer")
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(16.dp)
            )

            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Completely round keypad buttons
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.width(240.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")) { key ->
                            FilledTonalButton(
                                onClick = { dialText += key },
                                modifier = Modifier.size(56.dp), // Perfect circle width & height
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                )
                            ) {
                                LocalizedLabel(key, fontSize = 21.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                Column(Modifier.weight(1.15f).fillMaxHeight()) {
                    LocalizedLabel("CONTACTS BLUETOOTH", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    when {
                        !hasContactsPermission -> {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LocalizedLabel("Autorisez les contacts et Bluetooth pour afficher le répertoire PBAP synchronisé.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                Button(onClick = { permissionLauncher.launch(requiredPermissions) }, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                                    LocalizedLabel(if (accessDenied) "Réessayer" else "Autoriser")
                                }
                            }
                        }
                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) }
                        contacts.isEmpty() -> LocalizedLabel(
                            "Aucun contact Bluetooth/PBAP synchronisé. Connectez votre téléphone et activez la synchronisation des contacts dans les réglages Bluetooth de l’autoradio.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(filteredContacts, key = { it.id }) { contact ->
                                ListItem(
                                    headlineContent = { LocalizedLabel(contact.name, maxLines = 1) },
                                    supportingContent = { LocalizedLabel(contact.phoneNumber) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    trailingContent = {
                                        val contactKey = BluetoothContactsRepository.phoneKey(contact.phoneNumber)
                                        IconButton(onClick = { onToggleFavorite(contactKey) }) {
                                            Icon(
                                                imageVector = if (contactKey in favoritePhoneNumbers) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = if (contactKey in favoritePhoneNumbers) "Retirer des favoris" else "Ajouter aux favoris",
                                                tint = if (contactKey in favoritePhoneNumbers) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { onCall(contact.phoneNumber) }) {
                                            Icon(Icons.Default.Call, contentDescription = "Appeler ${contact.name}", tint = accent)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { if (dialText.any(Char::isDigit)) onCall(dialText) },
                enabled = dialText.any(Char::isDigit),
                modifier = Modifier.size(width = 220.dp, height = 56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A05D))
            ) {
                Icon(Icons.Default.Call, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                LocalizedLabel("APPELER", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        } else {
            // Favorites Tab View
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LocalizedLabel("VOS CONTACTS FAVORIS", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (favoriteContacts.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LocalizedLabel(
                            "Aucun contact favori. Ajoutez des favoris en cliquant sur l'étoile depuis l'onglet Clavier & Répertoire.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(favoriteContacts, key = { it.id }) { contact ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(24.dp))
                                        Column {
                                            LocalizedLabel(contact.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            LocalizedLabel(contact.phoneNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(onClick = { onToggleFavorite(BluetoothContactsRepository.phoneKey(contact.phoneNumber)) }) {
                                            Icon(Icons.Default.Star, contentDescription = "Retirer des favoris", tint = Color(0xFFFFC107))
                                        }
                                        Button(
                                            onClick = { onCall(contact.phoneNumber) },
                                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Call, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            LocalizedLabel("Appeler", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
