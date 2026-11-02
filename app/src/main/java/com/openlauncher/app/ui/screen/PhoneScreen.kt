package com.openlauncher.app.ui.screen

import com.openlauncher.app.ui.components.LocalizedText as LocalizedLabel

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openlauncher.app.data.BluetoothContactsRepository
import com.openlauncher.app.data.JancarBluetoothManager
import com.openlauncher.app.model.PhoneContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun DialKey(key: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(88.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        )
    ) {
        LocalizedLabel(key, fontSize = 31.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ContactsPanel(
    modifier: Modifier,
    accent: Color,
    hasRequiredPermissions: Boolean,
    displayedContacts: List<PhoneContact>,
    filteredContacts: List<PhoneContact>,
    loading: Boolean,
    accessDenied: Boolean,
    permissionLauncher: () -> Unit,
    favoritePhoneNumbers: List<String>,
    onToggleFavorite: (String) -> Unit,
    onCall: (String) -> Unit
) {
    Column(modifier) {
        LocalizedLabel("CONTACTS BLUETOOTH", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        when {
            !hasRequiredPermissions && displayedContacts.isEmpty() -> {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    LocalizedLabel(
                        "Autorisez les contacts et Bluetooth pour afficher le répertoire PBAP synchronisé.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = permissionLauncher, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                        LocalizedLabel(if (accessDenied) "Réessayer" else "Autoriser")
                    }
                }
            }
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent)
            }
            displayedContacts.isEmpty() -> LocalizedLabel(
                "Aucun contact Bluetooth/PBAP synchronisé. Connectez votre téléphone et activez la synchronisation des contacts dans les réglages Bluetooth de l’autoradio.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            filteredContacts.isEmpty() -> LocalizedLabel(
                "Aucun contact correspondant.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(filteredContacts, key = { it.id }) { contact ->
                    val contactKey = BluetoothContactsRepository.phoneKey(contact.phoneNumber)
                    ListItem(
                        headlineContent = {
                            androidx.compose.material3.Text(contact.name, maxLines = 1, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            androidx.compose.material3.Text(contact.phoneNumber, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        leadingContent = {
                            ContactAvatar(name = contact.name, photoUri = contact.photoUri, accent = accent)
                        },
                        modifier = Modifier.fillMaxWidth().clickable { onCall(contact.phoneNumber) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        trailingContent = {
                            IconButton(onClick = { onToggleFavorite(contactKey) }) {
                                Icon(
                                    imageVector = if (contactKey in favoritePhoneNumbers) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = if (contactKey in favoritePhoneNumbers) "Retirer des favoris" else "Ajouter aux favoris",
                                    tint = if (contactKey in favoritePhoneNumbers) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PhoneScreen(
    accent: Color,
    onCall: (String) -> Unit,
    favoritePhoneNumbers: List<String> = emptyList(),
    onToggleFavorite: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val jancarContacts by JancarBluetoothManager.contacts.collectAsState()
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
    val hasRequiredPermissions = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED)

    LaunchedEffect(refreshToken, hasRequiredPermissions) {
        if (hasRequiredPermissions) {
            loading = true
            contacts = withContext(Dispatchers.IO) {
                runCatching { BluetoothContactsRepository.load(context) }.getOrDefault(emptyList())
            }
            loading = false
        } else {
            contacts = emptyList()
        }
    }

    // PBAP synchronization can finish after this screen first opens. Refresh
    // when Android's ContactsProvider changes and whenever the screen resumes.
    val lifecycleOwner = LocalLifecycleOwner.current
    val contactChangeHandler = remember { Handler(Looper.getMainLooper()) }
    val reloadAfterContactChange = remember { Runnable { refreshToken++ } }
    DisposableEffect(context, lifecycleOwner, hasRequiredPermissions) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshToken++
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        val contentObserver = if (hasRequiredPermissions) object : ContentObserver(contactChangeHandler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                contactChangeHandler.removeCallbacks(reloadAfterContactChange)
                contactChangeHandler.postDelayed(reloadAfterContactChange, 400L)
            }
        } else null
        contentObserver?.let { observer ->
            context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            context.contentResolver.registerContentObserver(ContactsContract.RawContacts.CONTENT_URI, true, observer)
            context.contentResolver.registerContentObserver(ContactsContract.Data.CONTENT_URI, true, observer)
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            contentObserver?.let(context.contentResolver::unregisterContentObserver)
            contactChangeHandler.removeCallbacks(reloadAfterContactChange)
        }
    }

    val displayedContacts = remember(contacts, jancarContacts) {
        (jancarContacts + contacts)
            .distinctBy { BluetoothContactsRepository.phoneKey(it.phoneNumber) }
            .sortedBy { it.name.lowercase() }
    }
    val filteredContacts = remember(displayedContacts, dialText) {
        if (dialText.isBlank()) displayedContacts
        else displayedContacts.filter { it.name.contains(dialText, ignoreCase = true) || it.phoneNumber.contains(dialText) }
    }

    val favoriteContacts = remember(displayedContacts, favoritePhoneNumbers) {
        displayedContacts.filter { BluetoothContactsRepository.phoneKey(it.phoneNumber) in favoritePhoneNumbers }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Tabs
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { LocalizedLabel("Clavier & Répertoire") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent, selectedLabelColor = MaterialTheme.colorScheme.onPrimary)
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { LocalizedLabel("Favoris (${favoriteContacts.size})") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent, selectedLabelColor = MaterialTheme.colorScheme.onPrimary)
                )
                IconButton(onClick = {
                    JancarBluetoothManager.requestContacts(force = true)
                    refreshToken++
                }) {
                    Icon(Icons.Default.Download, contentDescription = "Télécharger les contacts", tint = accent)
                }
            }
        }

        if (selectedTab == 0) {
            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = dialText,
                    onValueChange = { dialText = it },
                    modifier = Modifier.weight(1f),
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

                Button(
                    onClick = {
                        JancarBluetoothManager.requestContacts(force = true)
                        refreshToken++
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    LocalizedLabel("TÉLÉCHARGER", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                val narrowLayout = maxWidth < 700.dp
                if (narrowLayout) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.width(310.dp).height(380.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")) { key ->
                                DialKey(key = key, onClick = { dialText += key })
                            }
                        }
                        ContactsPanel(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            accent = accent,
                            hasRequiredPermissions = hasRequiredPermissions,
                            displayedContacts = displayedContacts,
                            filteredContacts = filteredContacts,
                            loading = loading,
                            accessDenied = accessDenied,
                            permissionLauncher = { permissionLauncher.launch(requiredPermissions) },
                            favoritePhoneNumbers = favoritePhoneNumbers,
                            onToggleFavorite = onToggleFavorite,
                            onCall = onCall
                        )
                    }
                } else Row(
                    modifier = Modifier.fillMaxSize(),
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
                            modifier = Modifier.width(310.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")) { key ->
                            DialKey(key = key, onClick = { dialText += key })
                        }
                    }
                }

                ContactsPanel(
                    modifier = Modifier.weight(1.15f).fillMaxHeight(),
                    accent = accent,
                    hasRequiredPermissions = hasRequiredPermissions,
                    displayedContacts = displayedContacts,
                    filteredContacts = filteredContacts,
                    loading = loading,
                    accessDenied = accessDenied,
                    permissionLauncher = { permissionLauncher.launch(requiredPermissions) },
                    favoritePhoneNumbers = favoritePhoneNumbers,
                    onToggleFavorite = onToggleFavorite,
                    onCall = onCall
                )
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
                                        ContactAvatar(name = contact.name, photoUri = contact.photoUri, accent = accent, size = 40.dp)
                                        Column {
                                            androidx.compose.material3.Text(contact.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            androidx.compose.material3.Text(contact.phoneNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
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
