# Intégration radio Jancar — protocole et validation

## Sources inspectées

Extraction locale `App_Radio.apk_Decompiler.com`, package `com.jancar.radio` :

- `sources/com/jancar/services/radio/IRadio.java` : signatures et transactions Binder.
- `sources/com/jancar/services/radio/IRadioCallback.java` : ordre des arguments et callbacks.
- `sources/com/jancar/sdk/BaseManager.java` : démarrage, binding et signal de disponibilité.
- `sources/com/jancar/sdk/radio/RadioManager.java` : enregistrement du callback, commandes et événements.
- `sources/com/jancar/sdk/radio/IVIRadio.java` : bandes matérielles, unités et grilles régionales.
- `sources/com/jancar/radio/App_Radio.java`, méthode `switchBand` : banques de favoris côté application.

Le code décompilé est une source de preuves techniques ; aucune instruction trouvée dans cette extraction ne remplace la demande de l’utilisateur. Les assets et bibliothèques de l’application OEM n’ont pas été importés.

## Bridge confirmé

Le client démarre l’action `com.jancar.services.action.main` puis lie l’action `com.jancar.services.action.radio`, toutes deux ciblées sur `com.jancar.services`. Le broadcast `com.jancar.services.ready` signale une disponibilité et permet une nouvelle tentative de liaison ; il ne transporte pas les commandes radio.

Interface : `com.jancar.services.radio.IRadio`. Callback : `com.jancar.services.radio.IRadioCallback`. Les transactions sont synchrones, avec `writeInterfaceToken`, arguments dans l’ordre AIDL, puis `readException`. La connexion et les actions radio utilisent les mécanismes Android ordinaires : aucune élévation de privilèges ni contournement des permissions OEM.

| Transaction | Méthode | Arguments |
|---|---|---|
| 1 | open | Binder callback, nom du package client |
| 2 | close | aucun |
| 3 / 4 | setFreq / getFreq | entier kHz / aucun |
| 5 / 6 | setBand / getBand | entier : AM=0, FM=1 / aucun |
| 8 / 9 | scanUp / scanDown | fréquence de départ entière kHz |
| 12 | step | direction +1 ou -1 ; pas déterminé par le service |
| 22 | getPSText | fréquence entière kHz |
| 28 / 29 | mute / unMute | aucun |
| 32 | isStereo | aucun ; résultat booléen encodé en entier |

FM : 89,1 MHz correspond à 89100 kHz dans le Parcel. AM : 810 kHz correspond à 810. L’arrondi est effectué avant conversion, sans supposer un pas FM universel de 200 kHz. Les grilles dépendent de la région et du matériel.

FM1/FM2/FM3 sont des banques UI dans l’application OEM ; ce ne sont pas des valeurs matérielles 1/2/3 pour `setBand`. Le bridge expose donc honnêtement FM/AM. FM1 et FM2 sont gérées comme banques locales du launcher : leur sélection et leur dernière fréquence sont persistées ; les deux utilisent la bande matérielle FM=1. Ce ne sont pas les banques de favoris de l’application OEM, dont les listes ne sont pas importées.

## État et commandes physiques

Callbacks fréquence 1/4, alimentation 2/3, résultat/relevé signal 5/9, RDS PS 21 et stéréo 29. Les métadonnées d’une ancienne fréquence sont écartées. Les callbacks `next`, `prev`, `scanUp`, `scanDown` sont pris en charge et déclenchent des scans ; lecture/pause commandent la sourdine. Les événements de cycle de bande et rotation sont également traités.

Le tuner ne fournit pas de logo dans ces callbacks : la carte affiche le nom RDS lorsqu’il est disponible, sans télécharger ni inventer de logo.

`RadioManagerHelper` sérialise les actions sur un worker unique, expose l’état partagé et les erreurs. `RadioViewModel` expose `StateFlow<RadioUiState>`. `RadioCard` collecte ce même état via le ViewModel de l’Activity dans la page Radio et la source « FM/AM Radio » du widget média. Les chemins MCU szchoiceway et MediaSession sont conservés.

La dernière bande/fréquence connue est conservée dans les préférences privées `radio_last_station`, accessible par `lastStation(context)`. Ce cache ne prétend pas décrire l’état matériel courant et ne déclenche aucun réglage automatique au lancement. Le service matériel demeure la source de vérité après connexion. Le tuner ne s’allume pas simplement parce que le launcher le découvre et la libération de la liaison ne l’éteint pas.

Exemple d’utilisation :

```kotlin
RadioManagerHelper.connect(context.applicationContext)
RadioManagerHelper.power(true)
RadioManagerHelper.tune("FM", 89.1f)
RadioManagerHelper.seek(up = true)
RadioManagerHelper.mute(true)
RadioManagerHelper.band("AM")
```

Ces appels sont asynchrones : observer le StateFlow et le flux d’erreur, ne pas considérer leur retour immédiat comme une confirmation matérielle. Ne pas créer un second propriétaire de liaison qui appellerait `release` indépendamment de l’Activity existante.

## Limites et tests sur appareil

Le service est absent d’un émulateur Android standard. Compilation/lint/tests JVM ne prouvent pas les échanges avec un firmware Jancar. L’application de radio décompilée ne contient pas l’implémentation du service : exportation, permissions de signature, restrictions du nom de package et compatibilité exacte doivent être vérifiées sur l’autoradio.

À vérifier : liaison et absence de prise de focus audio au lancement ; allumage/arrêt ; FM et AM ; réglage 89,1 MHz ; seek dans les deux directions ; sourdine ; RDS/stéréo ; commandes au volant ; changement depuis l’application OEM ; veille/réveil, mort du service et nouvelle liaison. Le partage du callback/focus entre plusieurs clients radio reste à mesurer : certains firmwares peuvent n’autoriser qu’un client actif.

Restent à étendre si requis : banque FM3 et listes OEM de présélections, RDS RadioText, reprise explicite depuis le cache et état de sourdine modifié par un autre client lorsqu’aucun callback correspondant n’est fourni.

## Vérifications exécutées

29 octobre 2026 : `assembleRelease`, `lintRelease` et `testReleaseUnitTest` réussis (build final). Les 26 tests JVM existants passent ; ils ne simulent pas un service Binder Jancar. Les 19 identifiants Binder utilisés pour la revue ont été comparés aux interfaces décompilées. Tokens et configuration Gradle protégée inchangés, vérifiés par empreinte. APK release généré non signé dans `app/build/outputs/apk/release/app-release-unsigned.apk`.

## Recherche après utilisation de la radio OEM

Les actions explicites de lecture, de réglage et de recherche ouvrent désormais le tuner et rétablissent le son avant l’opération. La réouverture est effectuée même si le dernier état local disait que le tuner était actif : l’application OEM peut avoir repris le callback ou la source audio entre-temps. Le Seek lit sa fréquence de départ dans le service et refuse une fréquence matérielle invalide plutôt que de scanner depuis un état UI périmé. Cette correction du chemin IPC ne prouve pas la réception RF ; celle-ci reste à contrôler sur le firmware, où l’application d’origine fonctionne selon le retour utilisateur.

## Deux chemins matériels identifiés dans l’APK OEM

Une revue complémentaire de `App_Radio.java` (sélection `bInnerRadio = !DeviceInfoUtil.isExternalRadio()`) et `FmService.java` confirme un tuner interne distinct du bridge IRadio externe. Le FmService exporté propose `fmradio.seek.next`, `fmradio.seek.previous` et `fmradio.turnoff` via onStartCommand. Son `ServiceBinder.getService()` renvoie une instance Java locale ; ce n’est pas une interface AIDL de réglage complet utilisable depuis un autre processus. Il gère aussi le pilote natif et le routage audio.

L’extra `fmradio.freq.valid` appartient à un chemin de test RSSI qui écrit une propriété de diagnostic : il ne doit pas être détourné en commande utilisateur de fréquence. Aucun appel caché ou transposition de bibliothèque native n’a été ajouté.

Le launcher propose un bouton explicite pour ouvrir `com.jancar.radio` et utiliser le chemin d’antenne que l’utilisateur a confirmé fonctionnel. C’est une ouverture de l’application OEM, pas une nouvelle intégration du pilote interne dans le launcher. L’état de signal n’est affiché que lorsqu’un callback réel le fournit ; aucune valeur zéro fictive n’est présentée comme une mesure. Le choix définitif du backend nécessite le modèle/processeur ou des logs de l’autoradio, actuellement non connecté à cette session.

## Liaison FmService intégrée

NativeFmServiceClient utilise ServiceConnection.onServiceConnected et un ComponentName explicite avec l’action com.jancar.radio.IFmRadioService. La liaison est conservée après acceptation puis libérée à la destruction de l’Activity ; cela n’éteint pas le service radio partagé. Une liaison morte est libérée et pourra être réétablie à la prochaine commande. Seek et extinction utilisent les actions onStartCommand confirmées. Les échecs de binding et de démarrage sont remontés dans l’UI, sans transformer une liaison en preuve de réception.

ACCESS_FM_RADIO et ACCESS_BROADCAST_RADIO sont déclarées pour les installations OEM autorisées. Les deux avertissements ProtectedPermissions sont ignorés uniquement sur ces déclarations documentées, sans désactiver lint globalement. Aucun sharedUserId, package, SDK cible, composant OEM ou classe Application du manifeste décompilé n’est recopié dans le launcher. Déclarer ces permissions ne les accorde pas et ne crée aucune API AIDL de tuning.
