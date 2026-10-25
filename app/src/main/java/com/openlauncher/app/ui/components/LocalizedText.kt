package com.openlauncher.app.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.openlauncher.app.data.AppLanguage

/** Centralized app language used by Compose text and the speech features. */
val LocalAppLanguage = androidx.compose.runtime.staticCompositionLocalOf { AppLanguage.FRENCH_CANADA }

private val translationPairs = mapOf(
    "Introduction" to "Introduction", "Language" to "Langue", "Get started" to "Commencer",
    "Home" to "Accueil", "Phone" to "Téléphone", "Radio" to "Radio", "Media" to "Médias",
    "Nav" to "Carte", "Offline" to "Hors ligne",
    "WELCOME TO OPEN LAUNCHER" to "BIENVENUE DANS OPEN LAUNCHER",
    "A clean, modern landscape dashboard designed to be the ultimate companion for your car's screen." to "Un tableau de bord clair et personnalisable, conçu pour accompagner vos trajets en voiture.",
    "Designed for the dashboard" to "Conçu pour le tableau de bord",
    "TELEMETRY & WEATHER" to "TÉLÉMÉTRIE ET MÉTÉO",
    "To compute your real-time speed, compass bearing, altitude telemetry, and update local weather conditions, Open Launcher requires high-precision GPS services." to "Pour afficher la vitesse, le cap, l’altitude et la météo locale, Open Launcher a besoin de la localisation précise.",
    "Permission Granted" to "Accès accordés", "Permission Required" to "Autorisation requise",
    "GPS telemetry is active and ready." to "La localisation est active.", "Telemetry is currently disabled." to "La localisation est désactivée.",
    "GRANT ACCESS" to "AUTORISER", "CONTINUE" to "CONTINUER", "BACK" to "RETOUR", "SKIP FOR NOW" to "IGNORER", "FINISH SETUP" to "TERMINER",
    "PHONE & CONTACTS" to "TÉLÉPHONE ET CONTACTS", "PHONE AND CONTACTS" to "TÉLÉPHONE ET CONTACTS",
    "TÉLÉPHONE ET CONTACTS" to "PHONE & CONTACTS",
    "These permissions enable quick calls and favorites. Only contacts synchronized from the phone connected through Bluetooth/PBAP are displayed." to "Ces accès activent les appels rapides et les favoris. Seuls les contacts synchronisés depuis le téléphone connecté en Bluetooth/PBAP sont affichés.",
    "Accès accordés" to "Permissions granted", "Autorisations nécessaires" to "Permissions required",
    "Le téléphone, les contacts et Bluetooth sont prêts." to "Phone, contacts and Bluetooth are ready.",
    "Contacts, appels et connexion Bluetooth (selon la version Android)." to "Contacts, calls and Bluetooth connection (depending on Android version).",
    "AUTORISER" to "ALLOW", "MÉDIAS ET ASSISTANT VOCAL" to "MEDIA & VOICE ASSISTANT",
    "L’accès aux notifications active les commandes média et les messages entrants. Le micro sert à l’assistant vocal, uniquement lorsque vous le lancez." to "Notification access enables media controls and incoming messages. The microphone is used by the voice assistant only when you start it.",
    "Accès aux notifications accordé" to "Notification access granted", "Accès aux notifications à activer" to "Enable notification access",
    "Nécessaire pour les médias et les messages entrants." to "Required for media controls and incoming messages.",
    "ACTIVER LES NOTIFICATIONS" to "ENABLE NOTIFICATIONS", "Microphone autorisé" to "Microphone enabled", "Microphone non autorisé" to "Microphone not enabled",
    "Utilisé par l’assistant vocal lorsque vous appuyez sur le micro." to "Used by the voice assistant when you tap the microphone.",
    "AUTORISER LE MICRO" to "ALLOW MICROPHONE", "CONTRÔLES DU SYSTÈME" to "SYSTEM CONTROLS",
    "Ces accès activent le réglage de luminosité système et les fenêtres flottantes (PIP). Ils sont facultatifs et peuvent être accordés plus tard dans les paramètres." to "These permissions enable system brightness control and floating windows (PIP). They are optional and can be granted later in Settings.",
    "Luminosité système autorisée" to "System brightness enabled", "Luminosité système non autorisée" to "System brightness not enabled",
    "Permet au curseur du launcher de modifier la luminosité Android." to "Allows the launcher slider to change Android brightness.",
    "AUTORISER LA LUMINOSITÉ" to "ALLOW BRIGHTNESS CONTROL", "Fenêtres flottantes autorisées" to "Floating windows enabled",
    "Fenêtres flottantes non autorisées" to "Floating windows not enabled", "Nécessaire seulement pour afficher une application en PIP par-dessus la carte." to "Only needed to display an app in PIP over the map.",
    "AUTORISER LE PIP" to "ALLOW PIP", "CARTE HORS LIGNE" to "OFFLINE MAP",
    "Téléchargez une zone avant de partir pour garder la carte disponible sans réseau. Voici les suggestions autour de votre position : une ville, une région et votre pays." to "Download an area before you leave to keep the map available without a network. Suggestions near your location include a city, region and country.",
    "La position n’est pas encore disponible. Autorisez la localisation et attendez le premier relevé GPS pour obtenir des suggestions." to "Your location is not available yet. Allow location access and wait for a GPS fix to get suggestions.",
    "Aucune région suggérée. Vous pourrez en rechercher une dans le gestionnaire hors ligne après la configuration." to "No region suggestions. You can search for one in the offline manager after setup.",
    "PRÊTE" to "READY", "Le téléchargement peut utiliser plusieurs centaines de mégaoctets selon la zone et le niveau de détail." to "Downloads may use hundreds of megabytes depending on the area and detail level.",
    "PRÊT À PRENDRE LA ROUTE" to "READY FOR THE ROAD", "La configuration est terminée. Vous pouvez définir Open Launcher comme écran d’accueil par défaut pour qu’il s’ouvre au démarrage du véhicule." to "Setup is complete. Set Open Launcher as your default home screen to launch it when your vehicle starts.",
    "CHOISIR COMME ACCUEIL" to "SET AS HOME APP", "100% Offline-Based" to "100 % hors ligne",
    "No reliance on a mobile signal or network connection to function. Speedometer, compass telemetry, and altimeter operate entirely offline." to "Aucune connexion réseau requise. Le compteur de vitesse, la boussole et l’altimètre fonctionnent hors ligne.",
    "Highly Customizable Dashboard" to "Tableau de bord personnalisable", "Soundboard & Media Shortcuts" to "Sons et raccourcis multimédias",
    "Introduction" to "Introduction", "Localisation" to "Location", "Téléphone" to "Phone", "Médias et micro" to "Media & microphone", "Contrôles système" to "System controls", "Carte hors ligne" to "Offline map", "Prêt à partir" to "Ready to go",
    "Paramètres" to "Settings", "Settings" to "Paramètres", "Permissions" to "Autorisations", "System Controls" to "Contrôles système",
    "Relancer le parcours de première ouverture" to "Restart first-run setup", "Revoir les accès et les suggestions de cartes hors ligne" to "Review permissions and offline map suggestions",
    "Notification Access" to "Accès aux notifications", "Draw Over Other Apps" to "Afficher par-dessus les autres applications", "Location Access" to "Accès à la localisation",
    "Set as Default Launcher" to "Définir comme launcher par défaut", "Wi-Fi Settings" to "Paramètres Wi-Fi", "Bluetooth Settings" to "Paramètres Bluetooth",
    "Téléphone" to "Phone", "Contacts synchronisés depuis le téléphone Bluetooth (PBAP)" to "Contacts synced from the Bluetooth phone (PBAP)",
    "Nom ou numéro" to "Name or number", "CONTACTS BLUETOOTH" to "BLUETOOTH CONTACTS", "APPELER" to "CALL",
    "Aucun contact Bluetooth/PBAP synchronisé. Connectez votre téléphone et activez la synchronisation des contacts dans les réglages Bluetooth de l’autoradio." to "No Bluetooth/PBAP contacts synced. Connect your phone and enable contact sharing in the car Bluetooth settings.",
    "Autorisez les contacts et Bluetooth pour afficher le répertoire PBAP synchronisé." to "Allow Contacts and Bluetooth to view the synced PBAP address book.",
    "Aucune nouvelle notification" to "No new notifications", "Choisir une icône" to "Choose an icon", "CHOISIR UNE ICÔNE" to "CHOOSE AN ICON", "Ajouter" to "Add", "Annuler" to "Cancel", "Enregistrer" to "Save", "Supprimer" to "Delete",
    "Favorite" to "Favori", "Itinéraire" to "Directions", "Rechercher adresse, McDonald's, station..." to "Search address, McDonald's, gas station...",
    "DESTINATIONS RÉCENTES" to "RECENT DESTINATIONS", "FAVORIS & ENREGISTRÉS" to "FAVORITES & SAVED", "DESTINATIONS PROCHES" to "NEARBY DESTINATIONS",
    "Rechercher sur le trajet" to "Search along route", "Station-service, péage, parking..." to "Gas station, toll, parking...", "Aucun trajet routier hors ligne pour cette région." to "No offline road route for this region.",
    "Mettez à jour son téléchargement hors ligne." to "Update its offline download.", "Arrêter" to "Stop", "Résultats POI" to "POI results",
    "Modifier le lieu enregistré" to "Edit saved place", "Nom du lieu (ex: Domicile, Travail)" to "Place name (e.g. Home, Work)", "Adresse avec Autofill Mapbox" to "Address with Mapbox autofill", "Icône du bouton :" to "Button icon:", "Ajouter un Favori" to "Add a favorite", "Nom (ex: Salle de sport)" to "Name (e.g. Gym)",
    "Pays, région ou ville" to "Country, region or city", "Canada, Québec, Montréal…" to "Canada, Quebec, Montreal…", "TOUT EFFACER" to "CLEAR ALL", "Téléchargé" to "Downloaded", "Téléchargement en cours..." to "Downloading...", "Non téléchargé" to "Not downloaded", "Mettre à jour" to "Update", "Télécharger" to "Download", "Supprimer la région ?" to "Delete this region?", "Les données hors ligne de" to "Offline data for",
    "ASSIGN RADIO APP" to "ASSIGN RADIO APP", "SEEK DOWN" to "RECHERCHE PRÉCÉDENTE", "SEEK UP" to "RECHERCHE SUIVANTE", "Any Player" to "N’importe quel lecteur", "FM/AM Radio" to "Radio FM/AM", "NO MEDIA PLAYING" to "AUCUNE LECTURE EN COURS", "CLEAR SOUND" to "EFFACER LE SON", "SAVE SOUND" to "ENREGISTRER LE SON", "CANCEL" to "ANNULER",
    "PROCHAINE ÉTAPE" to "NEXT STEP", "Guidage en cours" to "Guidance in progress", "Voice" to "Voix", "MAP" to "CARTE", "Apps" to "Applications", "Settings" to "Paramètres", "Offline Maps" to "Cartes hors ligne",
    "Search…" to "Rechercher…", "No apps found" to "Aucune application trouvée", "Apply" to "Appliquer", "Presets" to "Préréglages", "Custom" to "Personnalisé", "Hue" to "Teinte", "Saturation" to "Saturation", "Brightness" to "Luminosité",
    "MY CAR" to "MA VOITURE", "123 Main St" to "123, rue Principale", "Workplace" to "Lieu de travail", "Metric" to "Métrique", "Imperial" to "Impérial", "DEFAULT" to "PAR DÉFAUT", "REMOVE WALLPAPER" to "RETIRER LE FOND D’ÉCRAN", "Reset to Defaults" to "Réinitialiser les paramètres",
    "ROLL" to "ROULIS", "PITCH" to "TANGAGE", "DRIVE [TIME]" to "CONDUITE [DURÉE]", "IDLE [TIME]" to "ARRÊT [DURÉE]", "SYS STAT" to "ÉTAT SYSTÈME", "PAD LABEL" to "NOM DU BOUTON", "PRELOADED AUDIO" to "SON PRÉENREGISTRÉ", "CUSTOM AUDIO FILE" to "FICHIER AUDIO PERSONNALISÉ",
    "Bonjour" to "Hello",
    "Langue / Language" to "Language",
    "Langue du launcher et de l’assistant vocal" to "Launcher and voice assistant language",
    "SETTINGS" to "PARAMÈTRES",
    "Accent Color" to "Couleur d’accent", "Appearance" to "Apparence", "Background Color" to "Couleur d’arrière-plan",
    "Background" to "Arrière-plan", "Blend two colors as background" to "Mélanger deux couleurs en arrière-plan",
    "Bold Font" to "Caractères gras", "Bottom" to "Bas", "Card Border Color" to "Couleur de bordure des cartes",
    "Card Surface Color" to "Couleur de surface des cartes", "Check for Updates" to "Rechercher des mises à jour",
    "Choose image from gallery" to "Choisir une image dans la galerie", "Compass Heading Offset" to "Décalage du cap de la boussole",
    "Custom text color in dark mode" to "Couleur de texte personnalisée en mode sombre", "Dark" to "Sombre",
    "DEFAULT" to "PAR DÉFAUT", "Diagonal" to "Diagonale", "Display Mode" to "Mode d’affichage",
    "Display Settings" to "Paramètres d’affichage", "Empty" to "Vide", "Follows system theme" to "Suit le thème du système",
    "Font Color" to "Couleur du texte", "Font" to "Police", "GPS & Calibration" to "GPS et calibrage",
    "Gradient Direction" to "Direction du dégradé", "Gradient End Color" to "Couleur de fin du dégradé",
    "Gradient" to "Dégradé", "Home Address" to "Adresse du domicile", "Horizontal" to "Horizontal",
    "Imperial (°F, mi)" to "Impérial (°F, mi)", "Imperial" to "Impérial", "Left" to "Gauche",
    "Left side" to "Côté gauche", "Left to Right" to "De gauche à droite", "Light" to "Clair",
    "Maintenance" to "Maintenance", "Metric (°C, km)" to "Métrique (°C, km)", "Metric" to "Métrique",
    "Muted Text Color" to "Couleur du texte secondaire", "Permissions" to "Autorisations",
    "Read incoming messages aloud" to "Lire les messages entrants à voix haute", "Reset Settings" to "Réinitialiser les paramètres",
    "Reset to Defaults" to "Rétablir les valeurs par défaut", "Reset" to "Réinitialiser", "Right" to "Droite",
    "Right side" to "Côté droit", "Save" to "Enregistrer", "Set as Default Launcher" to "Définir comme launcher par défaut",
    "Set Wallpaper" to "Définir le fond d’écran", "Shortcuts Side" to "Côté des raccourcis",
    "Sidebar Position" to "Position de la barre latérale", "Sidebar" to "Barre latérale",
    "System Brightness" to "Luminosité système", "System Controls" to "Contrôles système", "System" to "Système",
    "Text Scale" to "Taille du texte", "Typography" to "Typographie", "UI highlight color" to "Couleur d’accentuation de l’interface",
    "UI Scale" to "Échelle de l’interface", "Always dark" to "Toujours sombre", "Always light" to "Toujours clair",
    "Are you sure you want to reset all settings to default? This cannot be undone." to "Voulez-vous vraiment rétablir tous les paramètres par défaut? Cette action est irréversible.",
    "Active — Open Launcher is the home app" to "Actif — Open Launcher est l’application d’accueil",
    "Required so the head unit boots into Open Launcher" to "Nécessaire pour ouvrir Open Launcher au démarrage de l’autoradio",
    "Configure wireless networks & internet" to "Configurer les réseaux sans fil et Internet",
    "Pair phone, audio & media devices" to "Associer un téléphone et des appareils audio", "Notification Access" to "Accès aux notifications",
    "Draw Over Other Apps" to "Afficher par-dessus les autres applications", "Location Access" to "Accès à la localisation",
    "Allow system brightness access" to "Autoriser l’accès à la luminosité système",
    "Allow system settings access to adjust brightness" to "Autoriser l’accès aux paramètres système pour régler la luminosité",
    "Required for compass, speed & weather" to "Nécessaire pour la boussole, la vitesse et la météo",
    "Required for media controls and message announcements" to "Nécessaire pour les commandes multimédias et l’annonce des messages",
    "Required for PIP floating window" to "Nécessaire pour les fenêtres flottantes PIP",
    "[COMPLETE]" to "[TERMINÉ]", "[READY]" to "[PRÊT]", "[RUNNING]" to "[EN COURS]", "[STOPPED]" to "[ARRÊTÉ]",
    "ALTITUDE" to "ALTITUDE", "AM/FM, DAB & Satellite Radio receiver" to "Récepteur radio AM/FM, DAB et satellite",
    "ANDROID AUTO" to "ANDROID AUTO", "ASSIGN PAD SOUND" to "ASSIGNER LE SON DU BOUTON",
    "ASSISTANT VOCAL" to "VOICE ASSISTANT", "AVG SPEED // SPD" to "VITESSE MOY. // VIT.",
    "BLUETOOTH MEDIA PLAYER (AVRCP)" to "LECTEUR MULTIMÉDIA BLUETOOTH (AVRCP)",
    "CARPLAY" to "CARPLAY", "DISTANCE // DIST" to "DISTANCE // DIST.",
    "ÉTAT DU TILESTORE MAPBOX" to "MAPBOX TILESTORE STATUS",
    "GESTIONNAIRE DE CARTES HORS-LIGNE MAPBOX" to "MAPBOX OFFLINE MAP MANAGER",
    "Hors ligne · itinéraire routier téléchargé" to "Offline · road route downloaded",
    "Le TileStore Mapbox gère le stockage local des tuiles vectorielles et des styles pour garantir une navigation hors-ligne fluide et rapide." to "The Mapbox TileStore manages local vector tile and style storage for smooth, fast offline navigation.",
    "LATITUDE" to "LATITUDE", "LONGITUDE" to "LONGITUDE", "NO RADIO SOURCE" to "AUCUNE SOURCE RADIO",
    "NO RADIO SOURCE CONNECTED" to "AUCUNE SOURCE RADIO CONNECTÉE",
    "Start your head unit's radio app — or assign it below so Open Launcher can mirror and control it" to "Lancez l’application radio de l’autoradio ou associez-la ci-dessous pour qu’Open Launcher puisse la contrôler.",
    "Start your head unit's radio app or assign it below to enable steering wheel & direct mirror controls." to "Lancez l’application radio de l’autoradio ou associez-la ci-dessous pour activer les commandes au volant et la synchronisation.",
    "Synchronized via Android MediaController & A2DP" to "Synchronisé avec Android MediaController et A2DP",
    "Téléchargement de région(s) en cours..." to "Downloading region(s)...",
    "TileStore Mapbox - Téléchargement de régions mondiales" to "Mapbox TileStore - Global region downloads",
    "TileStore Mapbox actif et opérationnel." to "Mapbox TileStore is active and ready.",
    "VEHICLE RADIO & SATELLITE TUNER" to "RADIO DU VÉHICULE ET SYNTONISEUR SATELLITE",
    "WEATHER LOADING..." to "CHARGEMENT DE LA MÉTÉO...", "WIDGET LIBRARY" to "BIBLIOTHÈQUE DE WIDGETS",
    "Add Slot" to "Ajouter un emplacement", "Append an empty shortcut to the sidebar" to "Ajouter un raccourci vide à la barre latérale",
    "Bluetooth contacts and quick calls" to "Contacts Bluetooth et appels rapides",
    "Aucun contact favori PBAP disponible" to "No PBAP favorite contacts available",
    "Choisir une application" to "Choose an app", "CHOOSE APP" to "CHOISIR UNE APPLICATION",
    "Choisissez la langue du launcher et de l’assistant vocal. Vous pourrez la modifier plus tard dans les paramètres." to "Choose the language for the launcher and voice assistant. You can change it later in Settings.",
    "Choose the language for the launcher and voice assistant. You can change it later in Settings." to "Choisissez la langue du launcher et de l’assistant vocal. Vous pourrez la modifier plus tard dans les paramètres.",
    "Aucune région à afficher. Lancez la recherche." to "No regions to show. Start a search.",
    "Aucune région téléchargée ou en cours de téléchargement. Recherchez une région pour la télécharger." to "No downloaded or downloading regions. Search for a region to download it.",
    "Activer le style clair de la carte" to "Enable light map style", "Activer le style sombre de la carte" to "Enable dark map style",
    "Actualiser les contacts" to "Refresh contacts", "Ajouter aux favoris" to "Add to favorites",
    "Ajouter un raccourci d’application" to "Add an app shortcut", "Ajoutez des contacts aux favoris depuis Téléphone" to "Add contacts to favorites from Phone",
    "Album Art" to "Pochette d’album", "APP SHORTCUTS" to "RACCOURCIS D’APPLICATIONS",
    "ASSIGN ANDROID AUTO APP" to "ASSOCIER ANDROID AUTO", "ASSIGN CARPLAY APP" to "ASSOCIER CARPLAY",
    "CLEAR ANDROID AUTO APP" to "DISSOCIER ANDROID AUTO", "CLEAR CARPLAY APP" to "DISSOCIER CARPLAY",
    "BARS VIEW" to "VUE EN BARRES", "BEST RECORD" to "MEILLEUR RÉSULTAT",
    "Ces accès activent les appels rapides et les favoris. Les contacts affichés sont uniquement ceux synchronisés depuis le téléphone connecté en Bluetooth/PBAP." to "These permissions enable quick calls and favorites. Only contacts synced from the connected Bluetooth/PBAP phone are shown.",
    "CLOCK & WEATHER" to "HORLOGE ET MÉTÉO", "Current conditions" to "Conditions actuelles",
    "CUSTOM FILE ASSIGNED" to "FICHIER PERSONNALISÉ ASSOCIÉ", "Custom sound pads" to "Boutons de sons personnalisés",
    "Custom wallpaper active" to "Fond d’écran personnalisé actif", "DIAL GAUGES" to "JAUGES CIRCULAIRES",
    "DIAL TRACK" to "PISTE DE JAUGE", "DIGITAL ONLY" to "AFFICHAGE NUMÉRIQUE SEULEMENT",
    "Écouter une commande" to "Listen for a command", "Edit widgets" to "Modifier les widgets",
    "Éditer" to "Edit", "Error playing pad" to "Erreur lors de la lecture du son",
    "FM1" to "FM1", "FM2" to "FM2", "FM3" to "FM3", "Forces cold start to download fresh satellite orbits entirely offline" to "Force un démarrage à froid pour télécharger les nouvelles orbites satellites hors ligne",
    "GPS speed" to "Vitesse GPS", "Granted — GPS, compass & weather active" to "Accès accordé — GPS, boussole et météo actifs",
    "Granted — media and message announcements active" to "Accès accordé — commandes média et annonces des messages actives",
    "Granted — PIP overlay enabled" to "Accès accordé — fenêtres PIP activées",
    "Guided sweep — Android self-calibrates the compass while you circle" to "Calibrage guidé — Android calibre la boussole pendant que vous faites un cercle",
    "Head Unit Health / Vitals" to "État de l’autoradio", "Heavier weight across all text" to "Texte plus gras dans toute l’interface",
    "I’m listening…" to "Je vous écoute…", "I'm listening…" to "Je vous écoute…",
    "Impossible de trouver votre région." to "Unable to find your region.",
    "Itinéraire prioritaire le plus rapide • Mapbox Navigation" to "Fastest route preference • Mapbox Navigation",
    "JetBrains Mono" to "JetBrains Mono", "Left — nav buttons on right" to "Gauche — boutons de navigation à droite",
    "Lieu personnalisé" to "Custom place", "Lire le message" to "Read message", "LIVE RECEPTION" to "RÉCEPTION EN DIRECT",
    "Magnetometer Sweep (Parking Lot)" to "Balayage du magnétomètre (stationnement)",
    "Masquer la notification" to "Dismiss notification", "Media controls" to "Commandes multimédias",
    "Microphone access is required." to "L’accès au microphone est nécessaire.", "Microphone access was denied." to "L’accès au microphone a été refusé.",
    "Not supported by this device's GPS driver — no data was cleared" to "Non pris en charge par le pilote GPS de cet appareil — aucune donnée n’a été effacée",
    "NOW PLAYING" to "EN COURS DE LECTURE", "OPEN LAUNCHER" to "OPEN LAUNCHER",
    "Outline and divider lines" to "Contours et séparateurs", "PHONE FAVORITES" to "FAVORIS TÉLÉPHONIQUES",
    "PICK AUDIO FILE" to "CHOISIR UN FICHIER AUDIO", "Précédent" to "Previous", "Recherche Split-Screen" to "Split-screen search",
    "Réessayer" to "Try again", "Reset A-GPS Assistance Data" to "Réinitialiser les données d’assistance A-GPS",
    "Restaurant et alimentation" to "Restaurants and food", "Retirer des favoris" to "Remove from favorites",
    "Right — nav buttons on left" to "Droite — boutons de navigation à gauche", "Roll, pitch & altitude" to "Roulis, tangage et altitude",
    "Secondary labels and subtitles" to "Libellés secondaires et sous-titres", "RADIO OFF" to "RADIO ÉTEINTE",
    "Read and dismiss phone notifications" to "Lire et masquer les notifications téléphoniques", "Recenter GPS" to "Recentrer le GPS",
    "SEEK ►" to "RECHERCHE ►", "◄ SEEK" to "◄ RECHERCHE",
    "SPEED // TARGET %d" to "VITESSE // CIBLE %d",
    "W" to "O", "SEC" to "s"
)

object AppLocalization {
    private val frenchMarkers = setOf(
        "à", "au", "aux", "de", "des", "du", "et", "ou", "pour", "ajouter", "ajoutez", "aucun", "aucune", "autoriser", "autorisez", "avec", "bas", "bouton", "carte", "cartes",
        "choisir", "choisissez", "claire", "clés", "couleur", "données", "définir", "dégradé", "droite", "écouter",
        "écran", "enregistrer", "fenêtre", "gauche", "hors", "itinéraire", "lecture", "lieu", "lire", "maison", "masquer",
        "météo", "micro", "modifier", "paramètres", "position", "précédent", "précédente", "suivante", "région", "recherche", "rechercher", "réessayer", "retirer",
        "retour", "route", "sombre", "supprimer", "téléphone", "télécharger", "terminer", "travail", "vocal", "votre", "vous", "langue", "cours", "lecture", "ouvre", "associer", "dissocier", "lecteur", "voiture", "n", "importe", "quel", "actuelles", "vide", "sur", "trajet", "favoris", "principal", "prochain", "téléchargement"
    )
    private val englishMarkers = setOf(
        "a", "about", "access", "add", "all", "allow", "app", "apps", "as", "back", "brightness", "call", "change", "choose",
        "clear", "color", "controls", "custom", "default", "download", "edit", "enable", "find", "for", "from", "get", "go",
        "home", "icon", "in", "launcher", "left", "light", "map", "media", "microphone", "mode", "music", "no", "notification",
        "offline", "open", "phone", "play", "read", "right", "search", "set", "show", "settings", "sidebar", "start", "system",
        "the", "to", "turn", "update", "voice", "weather", "widget", "with", "language", "playing", "speed", "target", "ready", "directions", "player", "current", "car", "empty", "along", "route"
    )

    private fun markerCount(text: String, markers: Set<String>): Int =
        Regex("\\p{L}+").findAll(text.lowercase()).count { it.value in markers }

    private fun hasFrenchAccent(text: String): Boolean =
        text.any { it in "àâçéèêëîïôùûüÿœæÀÂÇÉÈÊËÎÏÔÙÛÜŸŒÆ" }

    private fun isFrench(text: String): Boolean = hasFrenchAccent(text) ||
        (markerCount(text, frenchMarkers) > 0 && markerCount(text, frenchMarkers) >= markerCount(text, englishMarkers))

    // Older entries were added in both directions. Normalize them once so a
    // reversed pair cannot overwrite the correct translation when displayed.
    private val englishToFrench: Map<String, String> = translationPairs.entries.mapNotNull { (left, right) ->
        val leftIsFrench = isFrench(left)
        val rightIsFrench = isFrench(right)
        when {
            leftIsFrench && !rightIsFrench -> right to left
            !leftIsFrench && rightIsFrench -> left to right
            else -> null
        }
    }.toMap()

    private val frenchToEnglish = englishToFrench.entries.associate { (en, fr) -> fr to en }
    fun translate(text: String, language: AppLanguage): String {
        if (language == AppLanguage.FRENCH_CANADA) {
            if (text == "W") return "O"
            englishToFrench[text]?.let { return it }
            if (text.startsWith("Message from ")) return text.replaceFirst("Message from ", "Message de ")
            if (text.startsWith("Estimated arrival: ")) return text.replaceFirst("Estimated arrival: ", "Arrivée estimée : ")
            if (text.startsWith("Downloaded • ")) return text.replaceFirst("Downloaded • ", "Téléchargé • ").replace(" MB", " Mo")
            return text
        }
        if (text == "O") return "W"
        frenchToEnglish[text]?.let { return it }
        if (text.startsWith("Message de ")) return text.replaceFirst("Message de ", "Message from ")
        if (text.startsWith("Arrivée estimée : ")) return text.replaceFirst("Arrivée estimée : ", "Estimated arrival: ")
        if (text.startsWith("Téléchargé • ")) return text.replaceFirst("Téléchargé • ", "Downloaded • ").replace(" Mo", " MB")
        return text
    }
}

@Composable
fun LocalizedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    val language = LocalAppLanguage.current
    MaterialText(
        text = AppLocalization.translate(text, language), modifier = modifier, color = color,
        fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, onTextLayout = onTextLayout, style = style
    )
}

@Composable
fun LocalizedText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    MaterialText(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}
