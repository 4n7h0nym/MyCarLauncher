# Open Launcher

Launcher Android pour autoradios et unités multimédias embarquées, développé en Kotlin avec Jetpack Compose et Material 3. Le projet Android Studio s’appelle **MyCarLauncher** ; l’application installée s’appelle **Open Launcher**.

Il réunit navigation Mapbox, commandes multimédias, radio compatible OEM, téléphone et widgets personnalisables dans une interface plein écran. Il s’agit d’un launcher Android autonome : il ne transforme pas un appareil en Android Automotive OS et ne constitue pas une interface Android Auto certifiée.

## État actuel

| Paramètre | Valeur |
|---|---|
| Version des sources | **0.1.2**, `versionCode = 8` |
| Identifiant de l’application | `com.openlauncher.app` |
| Android minimal | Android 6.0 / API 23 |
| Compilation et cible | API 35 |
| Interface | Kotlin, Jetpack Compose, Material 3 |
| Langues du launcher | Français (Canada), anglais (Canada) |
| Stockage | DataStore pour les réglages, Room pour les lieux et métadonnées des régions |
| Architectures natives | APK universel ; aucune restriction ABI configurée |

La version 0.1.2 introduit la refonte Material : suppression des en-têtes redondants, marges réduites, lecteur adaptatif, commandes plus grandes et dock simplifié. Les sources ont été compilées en release avec R8 et réduction des ressources. **La vérification visuelle et fonctionnelle de cette refonte sur émulateur et autoradio reste à terminer.**

## Fonctionnalités

### Accueil et widgets

- Grille de **3 colonnes × 2 lignes**, avec déplacement, redimensionnement et activation des widgets depuis leur bibliothèque.
- Horloge numérique ou analogique, météo, horloge/météo combinées, boussole, vitesse GPS et altimètre.
- Statistiques de trajet, moniteur des ressources de l’autoradio et soundboard avec sons personnalisables.
- Lecteur multimédia, contacts favoris, messages et raccourcis d’applications avec icônes personnalisables.
- Les mesures dépendent des capteurs, des permissions et des données disponibles ; le launcher ne remplace pas les instruments du véhicule.

### Interface et personnalisation

- Dock à **gauche, à droite ou en bas** : accès à l’accueil, à la carte, aux médias, à la radio et aux applications. Le menu **Plus** donne accès au téléphone, aux cartes hors ligne, aux réglages et à l’assistant vocal.
- Possibilité de remplacer le raccourci radio du dock par une application favorite déjà configurée.
- Thèmes **Material**, **noir OLED**, **Material You** et **couleurs personnalisées**.
- Modes clair/sombre, suivi du système et bascule automatique selon le lever/coucher du soleil lorsque la position est disponible.
- Palette système Material You à partir d’Android 12 ; repli sur le thème Material sur les versions antérieures.
- Accent optionnel extrait de la pochette disponible, fond d’écran avec assombrissement et flou, couleurs et dégradés personnalisés.
- Police système sans-serif par défaut ; polices alternatives, échelles du texte et de l’interface configurables.
- Écrans Média et Radio sans titres de page redondants ; cartes arrondies et cibles principales agrandies. Personnaliser la disposition à l’arrêt.

Le mode noir OLED est un choix visuel ; son intérêt énergétique dépend de la dalle. Tous les réglages personnalisés de couleur ne garantissent pas automatiquement un contraste suffisant.

### Navigation Mapbox

- Carte avec suivi GPS, orientation selon le déplacement et affichage de l’itinéraire et des manœuvres.
- Recherche d’adresses et de points d’intérêt via les services Mapbox, avec replis Nominatim et Android Geocoder selon le chemin de recherche.
- Recherche secondaire pendant la navigation, favoris persistants, adresses domicile et travail.
- Calcul d’itinéraire avec le SDK Mapbox Navigation et utilisation du TileStore partagé ; la position GPS réelle est nécessaire pour démarrer un itinéraire.
- Guidage vocal par le moteur de synthèse vocale Android installé, dans la langue sélectionnée, lorsque les données vocales sont disponibles.

### Cartes et routage hors ligne

Le gestionnaire télécharge les ressources cartographiques dans le TileStore et enregistre les métadonnées des régions dans Room. Le téléchargement tente d’inclure les descripteurs de routage Mapbox en plus des cartes. Le moteur de navigation est configuré pour utiliser ce même stockage.

**Une carte déjà visible en cache ne prouve pas que le routage hors ligne est disponible.** Celui-ci dépend de la présence des données routières de la région et doit être validé sans réseau. La recherche d’adresses en ligne ne devient pas une recherche hors ligne après téléchargement des cartes. Les permissions Mapbox, la couverture des données et l’espace disponible conditionnent les téléchargements.

### Musique et Bluetooth

- Métadonnées, pochette et commandes des sessions multimédias Android : précédent, lecture/pause, suivant et recherche dans le morceau lorsque la source le permet.
- Intégration du service Bluetooth Jancar sur les autoradios compatibles.
- Application musicale configurable pour ouvrir le lecteur de l’autoradio lorsqu’aucune session n’est active.
- État vide explicite en l’absence de lecture, sans piste fictive ni progression artificielle.

L’accès aux notifications permet de découvrir et contrôler les sessions multimédias sur les installations ordinaires. L’accès à un service OEM dépend du firmware et de ses permissions. Le launcher ne fournit pas lui-même une pile Bluetooth A2DP/AVRCP et ne garantit pas le réappairage automatique d’un téléphone.

### Radio, téléphone et commandes physiques

La page Radio utilise le [tableau de bord Compose intégré](docs/radio-dashboard.md) : présélections à gauche, fréquence et échelle FM centrales, commandes tactiles agrandies et état partagé avec le widget. L’appui long mémorise désormais les présélections de façon persistante par banque FM1/FM2/AM. AF, TA et Auto restent désactivés sans intégration matérielle confirmée.

- Commandes et affichage de la radio à partir des services et événements OEM compatibles ; application radio configurable.
- Bandes, recherche de stations et réglage direct selon les capacités rapportées par le matériel.
- Contacts Android et fournisseurs Bluetooth/PBAP compatibles, favoris et appels via les mécanismes disponibles sur l’appareil.
- Association de touches physiques aux actions média, téléphone, navigation, volume et assistant vocal, avec actions longues configurables.
- Intégrations Jancar et événements `szchoiceway` présents dans le code ; compatibilité à vérifier sur chaque modèle d’autoradio.

### Apprentissage matériel des commandes au volant

Depuis Paramètres → Commandes au volant → Apprentissage matériel du volant : sélection d’une fonction, réception de cinq relevés ADC stables, puis enregistrement explicite dans la configuration Jancar. La page affiche aussi le keycode OEM brut, permet l’arrêt du mode Study et propose une réinitialisation avec confirmation. Une sauvegarde du fichier véhicule précède toute écriture ; les autres sections sont conservées.

Le bridge Binder et le format INI proviennent de l’analyse de l’application OEM. L’accès à `/jancar/config/ivi-studykey.ini` nécessite les droits réels de l’installation système ; ils ne sont pas attribués par l’APK de développement. Le bouton contextuel Accueil / Android Auto est optionnel et son interaction avec le gestionnaire de touches OEM reste à valider sur appareil.

Voir [le protocole, la calibration et les limites](docs/steering-wheel-jancar.md). Aucun apprentissage matériel n’a été testé sur autoradio.

### Démarrage et retour au launcher

L’application peut être choisie comme écran d’accueil Android. Elle propose une option de lancement au démarrage et la restauration de la dernière page. Le bouton Accueil ramène au launcher. Le lancement après redémarrage dépend également du rôle d’accueil, des restrictions Android et du firmware OEM ; aucun délai de démarrage instantané n’est garanti.

### Sauvegarde et restauration

Export/import JSON des réglages, de la disposition, des favoris et des métadonnées des régions. Le document est validé avant écriture ; les écritures Room sont transactionnelles et l’enregistrement des préférences est attendu avant l’annonce du succès.

La sauvegarde **ne contient pas les tuiles cartographiques**, les fichiers audio personnalisés ni les images externes. Sur un autre appareil, télécharger de nouveau les cartes et rétablir les fichiers et accès nécessaires. Room et DataStore sont deux stockages distincts : l’import ne constitue pas une transaction atomique globale en cas de coupure électrique.

## Technologies

| Composant | Version configurée |
|---|---|
| Android Gradle Plugin | 8.13.2 |
| Gradle Wrapper | 9.0 |
| Kotlin / plugin Compose | 2.1.0 |
| KSP | 2.1.0-1.0.29 |
| Cible Java / Kotlin JVM | 17 |
| Compose BOM | 2024.09.00 |
| Mapbox Maps, variante NDK 27 | 11.14.0 |
| Mapbox Navigation et UI Maps, variante NDK 27 | 3.11.0 |
| Room | 2.6.1 |
| DataStore Preferences | 1.1.7 |
| Retrofit | 2.12.0 |
| Coil | 2.7.0 |
| Coroutines | 1.10.2 |
| Gson | 2.13.1 |
| JUnit, tests JVM uniquement | 4.13.2 |

Les versions de référence se trouvent dans `build.gradle.kts`, `app/build.gradle.kts` et `gradle/wrapper/gradle-wrapper.properties`.

## Compiler le projet

### Prérequis

- Android Studio et un JDK capable d’exécuter le wrapper et le plugin Gradle configurés ; le JBR fourni avec Android Studio a été utilisé pour les dernières vérifications. Les sources ciblent JVM 17.
- Android SDK Platform 35 et les outils Android nécessaires.
- Accès aux dépôts Maven lors de la première résolution des dépendances, notamment au dépôt Mapbox.
- SDK Android configuré par Android Studio dans `local.properties` ou via l’environnement de développement.

### Configuration Mapbox

Le projet lit ces noms exacts de propriétés Gradle, avec repli sur les variables d’environnement du même nom :

| Nom | Utilisation |
|---|---|
| `MAPBOX_DOWNLOADS_TOKEN` | Authentification au dépôt Maven Mapbox pour télécharger les SDK |
| `MAPBOX_ACCESS_TOKEN` | Token public intégré à la ressource Android `mapbox_access_token`, utilisée par les SDK et requêtes Mapbox |

**La configuration et les valeurs déjà présentes dans ce projet personnel sont conservées.** Ce README ne reproduit aucune valeur de token. `MAPBOX_PUBLIC_TOKEN` n’est pas le nom lu par le script de compilation actuel.

### Commandes sous Windows

Depuis la racine du projet, avec Java disponible dans le terminal :

```powershell
# APK de développement, signé automatiquement avec la clé de test Android
.\gradlew.bat :app:assembleDebug

# APK release optimisé, analyse statique et tests JVM
.\gradlew.bat :app:assembleRelease :app:lintRelease :app:testReleaseUnitTest
```

Ajouter `--offline` seulement lorsque toutes les dépendances nécessaires sont déjà en cache.

| Résultat | Emplacement |
|---|---|
| APK debug | `app/build/outputs/apk/debug/app-debug.apk` |
| APK release non signé | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| Rapport lint | `app/build/reports/lint-results-release.html` |
| Rapport des tests JVM | `app/build/reports/tests/testReleaseUnitTest/index.html` |

Le build release active **R8** et la réduction des ressources. Les règles de conservation sont dans `app/proguard-rules.pro`. Aucune configuration de signature release n’est définie : l’APK release doit être signé avec la clé appropriée avant installation. Une mise à jour exige le même identifiant et la même clé que l’application déjà installée ; conserver cette clé pour les versions suivantes.

L’APK universel conserve les architectures natives résolues par les dépendances, notamment ARM et x86. Aucune architecture n’a été retirée pour supposer un modèle d’autoradio. Un AAB n’est pas nécessaire pour installer directement un APK signé sur l’appareil.

## Installer et configurer

1. Transférer un APK signé sur l’autoradio, puis l’installer en autorisant cette source si Android le demande.
2. Ouvrir Open Launcher et effectuer la configuration initiale en français ou en anglais.
3. Le sélectionner comme application d’accueil dans les paramètres Android pour utiliser le bouton Accueil.
4. Accorder les accès utiles : localisation pour la navigation et les mesures GPS, notifications pour les médias et messages, contacts/Bluetooth/appels pour les fonctions téléphoniques, microphone pour l’assistant vocal.
5. Choisir les applications musicales et radio compatibles avec l’autoradio ; configurer les favoris et les commandes physiques à l’arrêt.
6. Télécharger les régions nécessaires avec une connexion disponible, puis vérifier le comportement hors réseau avant de compter sur le routage local.

Les fonctions GPS, téléphonie et capteurs sont optionnelles pour l’installation. Leur absence limite les fonctions associées. L’assistant vocal dépend du service de reconnaissance installé ; sa disponibilité hors réseau n’est pas garantie.

## Structure du code

```text
app/src/main/java/com/openlauncher/app/
├── MainActivity.kt       # Point d’entrée, navigation entre vues et intégration des états
├── data/                 # Réglages, intégrations OEM, TileStore et moteur de routage
│   └── room/             # Base, entités et DAO
├── model/                # États UI, navigation et modèles métier
├── service/              # Sessions média, notifications et démarrage
├── ui/
│   ├── components/       # Dock, traduction et composants partagés
│   ├── screen/           # Accueil, médias, radio, carte, téléphone, réglages…
│   ├── theme/            # Material, typographie, accent de pochette et flou
│   └── widget/           # Widgets de l’accueil
├── util/                 # Sauvegardes, validation, voix et outils
└── viewmodel/            # États, actions et orchestration

app/src/test/             # Tests JVM de régression
```

## Vérifications et limites connues

Dernière compilation enregistrée des sources **0.1.2**, le **28 octobre 2026** :

- `assembleRelease`, `lintRelease` et `testReleaseUnitTest` terminés avec succès.
- **26 tests JVM réussis**, aucun échec ni erreur.
- Lint : **0 erreur, 72 avertissements et 6 indications**. Ces avertissements ne constituent pas une validation matérielle.
- La release générée est non signée. Le dernier APK signé et testé sur émulateur avant la refonte appartient à la version **0.1.1** ; ne pas le confondre avec la version actuelle des sources.

À vérifier avant utilisation régulière : rendu des nouveaux écrans aux densités réelles, dock dans ses trois positions, grandes polices, nouvelles préférences après relancement, accent de pochette et fond flouté. Sur autoradio, contrôler Bluetooth/PBAP, radio, commandes au volant, veille profonde et réveil MCU, GPS et guidage vocal, ainsi que le calcul d’itinéraire avec données téléchargées et réseau coupé. Aucun test physique sur autoradio n’est revendiqué.

La base Room est en **version 3** avec `fallbackToDestructiveMigration()`. Une mise à jour depuis une ancienne base sans migration correspondante peut recréer la base et perdre ses données. Les migrations depuis les schémas 1 et 2 restent à traiter et à vérifier ; ne pas considérer ces chemins de mise à jour comme validés.

## Licence et ressources tierces

L’ancien README annonçait une licence MIT, mais aucun fichier `LICENSE` n’est actuellement présent dans le dépôt. Le statut de licence du code doit être formalisé avant une redistribution publique.

Les SDK Mapbox, polices, icônes et fichiers audio conservent leurs conditions respectives. Les droits de redistribution de chaque ressource, notamment des sons inclus, doivent être confirmés avant publication. La présence d’une ressource dans le projet ne vaut pas autorisation de redistribution.
