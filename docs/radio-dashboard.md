# Page Radio intégrée

La page Radio utilise désormais `radio/ui/RadioDashboard.kt` et son `RadioDashboardViewModel`.
Le client `radio/JancarRadioManager.kt` est détenu une seule fois par `RadioManagerHelper`.
L'ancien objet `data/JancarRadioManager` ne contient plus de liaison Binder : il expose un état de compatibilité aux consommateurs existants.

## Commandes conservées

- Bande FM1/FM2/AM, accord, recherche précédente/suivante, arrêt de recherche, marche/arrêt et sourdine.
- Appui long sur une présélection pour mémoriser la fréquence actuelle. Les six fréquences initiales sont conservées ; les modifications sont désormais persistantes et distinctes pour FM1, FM2 et AM (`radio_presets`).
- Choix de l'application radio et ouverture de la radio OEM dans le menu Plus ; accès direct à l'application audio OEM si présente. Les bandes et la sourdine restent dans la barre de commandes.
- Le chemin MCU/MediaSession antérieur est conservé lorsqu'un état réel de cet autre backend est disponible et qu'IRadio n'est pas connecté.
- Le widget existant reçoit l'état du même client. La disponibilité réelle d'IRadio prime sur l'hypothèse de backend issue du board ID.

AF, TA et la mémorisation automatique restent désactivés tant que leurs commandes/états matériels ne sont pas raccordés. TP est un indicateur. Aucun faux verrouillage RF ou fréquence par défaut n'est affiché sur la nouvelle page.

## Cycle de vie et protocole

La connexion suit les `onStart/onStop` existants de MainActivity, sans extinction automatique du tuner au départ de la page. Le service ready Jancar et le bootstrap du service global sont conservés. Les opérations passent hors du thread principal et les transactions sont sérialisées. Aucun nom ServiceManager, gain audio ou seuil de réception n'est inventé.

Une recherche en cours ignore les nouvelles demandes et ne se relance pas depuis les callbacks. Un garde-fou de 30 s envoie stopScan en l'absence de fin confirmée. Ce délai ne prouve pas la réception d'une station. Les callbacks des boutons physiques continuent de gérer pas, recherche, sourdine, arrêt et changement AM/FM.

Les retours d'état sans commande d'ouverture peuvent lire fréquence/RDS/stéréo ; l'ouverture explicite reprend le callback et laisse le service Jancar activer l'antenne et sélectionner le canal RADIO.

### Séquence audio vérifiée avant accord

Le gestionnaire ouvre `IRadio` avec le Binder de callback et le vrai `context.packageName`, puis attend le canal RADIO via le service `com.jancar.services.action.audio` (`com.jancar.services.audio.IAudio`, transaction 50, `getMasterAudioChannel`). La constante 11 provient de `IVIAudio.Channel.RADIO` du SDK extrait. Aucun sélecteur de canal privé n'est inventé.

Le retour de `IRadio.open` et le callback d'alimentation ne prouvent pas que le commutateur audio a fini sa transition. Le gestionnaire lit donc le canal au maximum 20 fois, avec un intervalle de 100 ms, puis autorise la bande/fréquence ou le seek uniquement après observation du canal 11. La première liaison audio a un délai de 1500 ms. Un appel Binder bloqué dans le service ne peut pas être interrompu par ces délais de coroutine.

Le focus reste détenu par `com.jancar.services` : les logs fournis montrent sa demande `STREAM_MUSIC` / gain 1 (`AUDIOFOCUS_GAIN`) et sa sélection RADIO. Le code OEM de l'application radio ne demande pas un second focus Android. `AudioManager.requestAudioFocus(STREAM_MUSIC, …)` depuis le launcher ne sélectionne pas à lui seul le canal MCU 11 et pourrait concurrencer le propriétaire OEM. Aucun focus concurrent n'est donc ajouté au launcher.

Le réglage d'une présélection est une seule opération sérialisée : arrêter un scan seulement s'il est actif, ouvrir la session, vérifier le routage OEM, régler la bande puis la fréquence, puis désactiver la sourdine. Les deux anciennes ouvertures successives et le `scanStop` systématique même au repos sont supprimés. Pour le seek, l'ouverture/routage précèdent une seule commande de recherche ; le service pilote la sourdine pendant la recherche.

En cas de permission audio refusée, de liaison morte ou de canal différent après le délai, l'erreur est exposée à l'UI et journalisée ; la commande de fréquence/seek n'est pas envoyée. `audioChannel` est le dernier canal observé, `routingConfirmed` indique la vérification de cette ouverture. Une sélection du canal 11 ne prouve ni le verrouillage RF ni l'audibilité aux haut-parleurs. Le repli reste l'ouverture explicite de l'application radio d'origine, depuis le menu Plus.

Sources locales vérifiées : `BaseManager.ServiceAction.AUDIO_ACTION`, `IAudio.Stub.TRANSACTION_getMasterAudioChannel`, `IVIAudio.Channel.RADIO`, `RadioManager.open`, `App_Radio.openJancarRadio`, ainsi que les logs Jancar joints. Sur une autre ROM ou si l'ABI change, cette vérification peut échouer explicitement plutôt que simuler une ouverture réussie.

L'interface hérite des couleurs Material 3 du launcher, propose des cibles de 64–80 dp, une échelle 87,5–107,9 MHz et conserve les traductions FR/EN. La plage graphique est configurable sans limiter les fréquences du tuner. Les aperçus contiennent les seules données de démonstration.

## À vérifier sur l'autoradio

Réception et son sur une station connue, fin de seek dans les deux sens, commandes physiques, changement AM/FM, mémorisation par appui long après redémarrage, passage depuis la radio OEM, veille/réveil, densité réelle et thèmes jour/nuit. La compilation et les tests locaux ne remplacent pas ces essais matériels.
