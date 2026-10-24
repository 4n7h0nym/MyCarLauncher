package com.openlauncher.app.ui.screen

import android.Manifest
import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openlauncher.app.model.OfflineRegion
import com.openlauncher.app.data.AppLanguage
import com.openlauncher.app.ui.components.LocalizedText as LocalizedLabel
import com.openlauncher.app.util.LocationData
import com.openlauncher.app.viewmodel.TileRegionDownloadViewModel

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    accent: Color,
    location: LocationData?,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onLocationPermissionGranted: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentStep by rememberSaveable { mutableStateOf(0) }
    var locationGranted by remember { mutableStateOf(false) }
    var phoneGranted by remember { mutableStateOf(false) }
    var notificationAccessGranted by remember { mutableStateOf(false) }
    var microphoneGranted by remember { mutableStateOf(false) }
    var systemControlsGranted by remember { mutableStateOf(false) }
    val offlineViewModel: TileRegionDownloadViewModel = viewModel()
    var recommendedRegions by remember { mutableStateOf<List<OfflineRegion>>(emptyList()) }
    var recommendationLoading by remember { mutableStateOf(false) }
    var recommendationError by remember { mutableStateOf<String?>(null) }
    var recommendationAttempted by remember { mutableStateOf(false) }

    val checkPermissions = {
        locationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val enabledListeners = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        )
        notificationAccessGranted = enabledListeners != null && enabledListeners.contains(context.packageName)
        microphoneGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        systemControlsGranted = Settings.System.canWrite(context) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context))
        val phonePermissions = buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.CALL_PHONE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        phoneGranted = phonePermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Trigger initial check
    LaunchedEffect(Unit) {
        checkPermissions()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            onLocationPermissionGranted()
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationGranted = granted
        if (granted) {
            onLocationPermissionGranted()
            currentStep = 3
        }
    }

    val phonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { checkPermissions() }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { checkPermissions() }

    LaunchedEffect(location?.latitude, location?.longitude) {
        if (location != null && !recommendationAttempted) {
            recommendationAttempted = true
            recommendationLoading = true
            recommendationError = null
            runCatching {
                offlineViewModel.recommendRegionsForLocation(location.latitude, location.longitude)
            }.onSuccess { recommendedRegions = it }
                .onFailure { recommendationError = it.message ?: "Impossible de trouver votre région." }
            recommendationLoading = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Aesthetic glowing background orb
        Box(
            modifier = Modifier
                .size(400.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.15f), Color.Transparent),
                        radius = 600f
                    )
                )
        )

        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Left branding pane ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(32.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    LocalizedLabel(
                        text = "OPEN LAUNCHER",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 2.sp,
                        fontSize = 15.sp
                    )
                    LocalizedLabel(
                        text = "Designed for the dashboard",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                // Step indicator list
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf("Language", "Introduction", "Localisation", "Téléphone", "Médias et micro", "Contrôles système", "Carte hors ligne", "Prêt à partir")
                        .forEachIndexed { index, title -> StepItem(index, title, currentStep) }
                }

                LocalizedLabel(
                    text = "v0.0.5",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                )
            }

            // Vertical separator line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            )

            // ── Right content wizard ────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .padding(48.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Active Step Content
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            fadeIn() + slideInHorizontally { it / 5 } togetherWith
                            fadeOut() + slideOutHorizontally { -it / 5 }
                        },
                        label = "step_transition"
                    ) { step ->
                        when (step) {
                            0 -> LanguageStep(accent, language, onLanguageChange)
                            1 -> IntroStep(accent)
                            2 -> LocationStep(accent, locationGranted, onGrant = {
                                locationLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            })
                            3 -> PhonePermissionsStep(accent, phoneGranted, onGrant = {
                                val permissions = buildList {
                                    add(Manifest.permission.READ_CONTACTS)
                                    add(Manifest.permission.CALL_PHONE)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
                                }
                                phonePermissionLauncher.launch(permissions.toTypedArray())
                            })
                            4 -> MediaStep(accent, notificationAccessGranted, microphoneGranted, onGrant = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                }
                            }, onGrantMicrophone = {
                                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            })
                            5 -> SystemControlsStep(
                                accent = accent,
                                canChangeSettings = Settings.System.canWrite(context),
                                canDrawOverlays = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context),
                                onGrantSettings = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                                    }
                                },
                                onGrantOverlay = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                                    }
                                }
                            )
                            6 -> OfflineRecommendationsStep(
                                accent = accent,
                                location = location,
                                regions = recommendedRegions,
                                isLoading = recommendationLoading,
                                error = recommendationError,
                                downloading = offlineViewModel.downloadingRegions,
                                progress = offlineViewModel.downloadProgress,
                                downloaded = offlineViewModel.downloadedRegions,
                                errors = offlineViewModel.regionDownloadErrors,
                                onDownload = offlineViewModel::downloadRegion,
                                onCancel = { region -> offlineViewModel.cancelDownload(region.id) }
                            )
                            7 -> FinalStep(accent, onSetDefault = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                                }
                            })
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // UNIFIED WIZARD FOOTER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button (left aligned)
                    if (currentStep > 0) {
                        TextButton(
                            onClick = { currentStep-- },
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            LocalizedLabel("RETOUR", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    // Next / Finish button (right aligned)
                    val permissionStepMissing = (currentStep == 2 && !locationGranted) ||
                        (currentStep == 3 && !phoneGranted) ||
                        (currentStep == 4 && (!notificationAccessGranted || !microphoneGranted)) ||
                        (currentStep == 5 && !systemControlsGranted)
                    val isPrimary = !permissionStepMissing

                    val nextButtonLabel = when (currentStep) {
                        0 -> "CONTINUER"
                        1 -> "GET STARTED"
                        2 -> if (locationGranted) "CONTINUER" else "IGNORER"
                        3 -> if (phoneGranted) "CONTINUER" else "IGNORER"
                        4 -> if (notificationAccessGranted && microphoneGranted) "CONTINUER" else "IGNORER"
                        5 -> if (systemControlsGranted) "CONTINUER" else "IGNORER"
                        6 -> "CONTINUER"
                        7 -> "TERMINER"
                        else -> "CONTINUE"
                    }

                    val nextButtonIcon = if (currentStep == 7) Icons.Default.Check else Icons.Default.ArrowForward

                    if (isPrimary) {
                        Button(
                            onClick = {
                                if (currentStep < 7) {
                                    currentStep++
                                } else {
                                    onComplete()
                                }
                            },
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            modifier = Modifier.height(44.dp)
                        ) {
                            LocalizedLabel(nextButtonLabel, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Icon(nextButtonIcon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                currentStep++
                            },
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.height(44.dp)
                        ) {
                            LocalizedLabel(nextButtonLabel, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Icon(nextButtonIcon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepItem(stepIndex: Int, title: String, currentStep: Int) {
    val active = stepIndex == currentStep
    val completed = stepIndex < currentStep
    val tint = when {
        active -> MaterialTheme.colorScheme.primary
        completed -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(tint)
            )
        }
        LocalizedLabel(
            text = title,
            fontSize = 11.sp,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun LanguageStep(
    accent: Color,
    selected: AppLanguage,
    onSelected: (AppLanguage) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        OnboardingPageTitle("LANGUE / LANGUAGE", accent)
        OnboardingPageDescription(
            if (selected == AppLanguage.FRENCH_CANADA)
                "Choisissez la langue du launcher et de l’assistant vocal. Vous pourrez la modifier plus tard dans les paramètres."
            else "Choose the language for the launcher and voice assistant. You can change it later in Settings."
        )
        AppLanguage.entries.forEach { language ->
            val isSelected = selected == language
            Surface(
                onClick = { onSelected(language) },
                color = if (isSelected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(
                    if (isSelected) 1.dp else 0.dp,
                    if (isSelected) accent else Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LocalizedLabel(language.label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PhonePermissionsStep(accent: Color, isGranted: Boolean, onGrant: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OnboardingPageTitle("TÉLÉPHONE ET CONTACTS", accent)
        OnboardingPageDescription("Ces accès activent les appels rapides et les favoris. Les contacts affichés sont uniquement ceux synchronisés depuis le téléphone connecté en Bluetooth/PBAP.")
        PermissionStatusCard(
            title = if (isGranted) "Accès accordés" else "Autorisations nécessaires",
            description = if (isGranted) "Le téléphone, les contacts et Bluetooth sont prêts." else "Contacts, appels et connexion Bluetooth (selon la version Android).",
            isGranted = isGranted
        )
        if (!isGranted) {
            Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Icon(Icons.Default.Contacts, null)
                Spacer(Modifier.width(8.dp))
                LocalizedLabel("AUTORISER", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(title: String, description: String, isGranted: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isGranted) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column {
                LocalizedLabel(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LocalizedLabel(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SystemControlsStep(
    accent: Color,
    canChangeSettings: Boolean,
    canDrawOverlays: Boolean,
    onGrantSettings: () -> Unit,
    onGrantOverlay: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OnboardingPageTitle("CONTRÔLES DU SYSTÈME", accent)
        OnboardingPageDescription("Ces accès activent le réglage de luminosité système et les fenêtres flottantes (PIP). Ils sont facultatifs et peuvent être accordés plus tard dans les paramètres.")
        PermissionStatusCard(
            title = if (canChangeSettings) "Luminosité système autorisée" else "Luminosité système non autorisée",
            description = "Permet au curseur du launcher de modifier la luminosité Android.",
            isGranted = canChangeSettings
        )
        if (!canChangeSettings) {
            OutlinedButton(onClick = onGrantSettings) {
                Icon(Icons.Default.BrightnessHigh, null)
                Spacer(Modifier.width(8.dp))
                LocalizedLabel("AUTORISER LA LUMINOSITÉ")
            }
        }
        PermissionStatusCard(
            title = if (canDrawOverlays) "Fenêtres flottantes autorisées" else "Fenêtres flottantes non autorisées",
            description = "Nécessaire seulement pour afficher une application en PIP par-dessus la carte.",
            isGranted = canDrawOverlays
        )
        if (!canDrawOverlays) {
            OutlinedButton(onClick = onGrantOverlay) {
                Icon(Icons.Default.Layers, null)
                Spacer(Modifier.width(8.dp))
                LocalizedLabel("AUTORISER LE PIP")
            }
        }
    }
}

@Composable
private fun OfflineRecommendationsStep(
    accent: Color,
    location: LocationData?,
    regions: List<OfflineRegion>,
    isLoading: Boolean,
    error: String?,
    downloading: Set<String>,
    progress: Map<String, Float>,
    downloaded: Map<String, Boolean>,
    errors: Map<String, String>,
    onDownload: (OfflineRegion) -> Unit,
    onCancel: (OfflineRegion) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        OnboardingPageTitle("CARTE HORS LIGNE", accent)
        OnboardingPageDescription("Téléchargez une zone avant de partir pour garder la carte disponible sans réseau. Voici les suggestions autour de votre position : une ville, une région et votre pays.")
        when {
            location == null -> LocalizedLabel(
                "La position n’est pas encore disponible. Autorisez la localisation et attendez le premier relevé GPS pour obtenir des suggestions.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            isLoading -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent)
            regions.isEmpty() -> LocalizedLabel(
                error ?: "Aucune région suggérée. Vous pourrez en rechercher une dans le gestionnaire hors ligne après la configuration.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            else -> androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(regions, key = { it.id }) { region ->
                    val isDownloading = region.id in downloading
                    val isDownloaded = downloaded[region.id] == true
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(4.dp),
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                LocalizedLabel(region.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                LocalizedLabel(region.regionType, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                if (isDownloading) {
                                    LinearProgressIndicator(
                                        progress = { progress[region.id] ?: 0f },
                                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                                        color = accent
                                    )
                                }
                                errors[region.id]?.let { error ->
                                    LocalizedLabel(error, color = MaterialTheme.colorScheme.error, fontSize = 9.sp, maxLines = 2)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            when {
                                isDownloaded -> LocalizedLabel("PRÊTE", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                isDownloading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    LocalizedLabel("${((progress[region.id] ?: 0f) * 100).toInt()}%", color = accent, fontSize = 10.sp)
                                    IconButton(onClick = { onCancel(region) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Annuler ${region.name}", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                else -> IconButton(onClick = { onDownload(region) }) {
                                    Icon(Icons.Default.Download, contentDescription = "Télécharger ${region.name}", tint = accent)
                                }
                            }
                        }
                    }
                }
            }
        }
        LocalizedLabel(
            "Le téléchargement peut utiliser plusieurs centaines de mégaoctets selon la zone et le niveau de détail.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun OnboardingPageTitle(text: String, accent: Color) {
    LocalizedLabel(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = accent,
        letterSpacing = 2.sp,
        fontSize = 20.sp
    )
}

@Composable
private fun OnboardingPageDescription(text: String) {
    LocalizedLabel(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        lineHeight = 20.sp
    )
}

@Composable
private fun IntroStep(accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OnboardingPageTitle("BIENVENUE DANS OPEN LAUNCHER", accent)
        OnboardingPageDescription("Un tableau de bord clair et personnalisable, conçu pour accompagner vos trajets en voiture.")

        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BulletItem(Icons.Default.CloudOff, "100% Offline-Based", "No reliance on a mobile signal or network connection to function. Speedometer, compass telemetry, and altimeter operate entirely offline.")
            BulletItem(Icons.Default.Palette, "Highly Customizable Dashboard", "Tailor color accents, background gradients, typography fonts, system units, and drag-and-drop to rearrange your tiles.")
            BulletItem(Icons.Default.VolumeUp, "Soundboard & Media Shortcuts", "Trigger custom soundboard sound effects, manage CarPlay & Android Auto shortcuts, and control active media players.")
        }
    }
}

@Composable
private fun LocationStep(accent: Color, isGranted: Boolean, onGrant: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OnboardingPageTitle("TÉLÉMÉTRIE ET MÉTÉO", accent)
        OnboardingPageDescription("Pour afficher la vitesse, le cap, l’altitude et la météo locale, Open Launcher a besoin de la localisation précise.")

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    LocalizedLabel(
                        text = if (isGranted) "Permission Granted" else "Permission Required",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LocalizedLabel(
                        text = if (isGranted) "GPS telemetry is active and ready." else "Telemetry is currently disabled.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (!isGranted) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onGrant,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                LocalizedLabel("AUTORISER", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MediaStep(
    accent: Color,
    notificationGranted: Boolean,
    microphoneGranted: Boolean,
    onGrant: () -> Unit,
    onGrantMicrophone: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OnboardingPageTitle("MÉDIAS ET ASSISTANT VOCAL", accent)
        OnboardingPageDescription("L’accès aux notifications active les commandes média et les messages entrants. Le micro sert à l’assistant vocal, uniquement lorsque vous le lancez.")

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (notificationGranted) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (notificationGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    LocalizedLabel(
                        text = if (notificationGranted) "Accès aux notifications accordé" else "Accès aux notifications à activer",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LocalizedLabel(
                        text = "Nécessaire pour les médias et les messages entrants.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (!notificationGranted) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onGrant,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(Icons.Default.VolumeUp, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                LocalizedLabel("ACTIVER LES NOTIFICATIONS", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 12.sp)
            }
        }
        PermissionStatusCard(
            title = if (microphoneGranted) "Microphone autorisé" else "Microphone non autorisé",
            description = "Utilisé par l’assistant vocal lorsque vous appuyez sur le micro.",
            isGranted = microphoneGranted
        )
        if (!microphoneGranted) {
            OutlinedButton(onClick = onGrantMicrophone) {
                Icon(Icons.Default.Mic, null)
                Spacer(Modifier.width(8.dp))
                LocalizedLabel("AUTORISER LE MICRO")
            }
        }
    }
}

@Composable
private fun FinalStep(accent: Color, onSetDefault: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OnboardingPageTitle("PRÊT À PRENDRE LA ROUTE", accent)
        OnboardingPageDescription("La configuration est terminée. Vous pouvez définir Open Launcher comme écran d’accueil par défaut pour qu’il s’ouvre au démarrage du véhicule.")

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onSetDefault,
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.height(44.dp)
        ) {
            Icon(Icons.Default.Home, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            LocalizedLabel("CHOISIR COMME ACCUEIL", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BulletItem(icon: ImageVector, title: String, desc: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Column {
            LocalizedLabel(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            LocalizedLabel(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
