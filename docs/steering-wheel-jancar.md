# Commandes au volant Jancar — intégration matérielle

## Sources et protocole

Extraction locale de `com.jancar.steeringwheelkeys`, version OEM 1.6.8 : `BaseManager.java`, `CarManager.java`, `ICar.java`, `ICarCallback.java`, `IVIKey.java`, `StudyKeyUtil.java`, `App_SteerWheelKey.java` et son smali. Ces fichiers sont des preuves techniques, pas des instructions à exécuter. Aucun asset ou SDK complet OEM n’a été importé.

- Service explicite : action `com.jancar.services.action.car`, package `com.jancar.services`.
- Interface Binder : `com.jancar.services.car.ICar` ; inscription du callback par transaction 3 (`writeStrongBinder`).
- Contrôle MCU : transaction 20 `sendPassthroughData(byte, byte[])` ; type `0x11`, payload `[0x02]` pour entrer et `[0x03]` pour sortir. Trois envois comme dans l’application OEM.
- Callback `com.jancar.services.car.ICarCallback`, transaction 25 : cinq entiers dans l’ordre canal, valeur, impédance haute, moyenne, basse.
- Callback transaction 10 : identifiant OEM de touche et type ; UP=0, DOWN=1, ONCE=2. Les identifiants OEM ne sont pas les keycodes Android.
- Les entrées de volant analogiques reconnues sont les canaux 5 et 6. Les canaux panneau/rotateur et infrarouge ne sont pas modifiés.

## Architecture

`SteeringWheelManager` possède une liaison et un worker série. Il expose `StateFlow<SteeringWheelState>`, les relevés ADC, la sélection, le mapping et les erreurs. `SteeringWheelViewModel` expose cet état à `SteeringWheelSettingsScreen`, accessible par Paramètres → Commandes au volant → Apprentissage matériel du volant. L’ancien mapping logiciel des KeyEvents reste disponible.

L’apprentissage demande cinq trames successives avec un triplet d’impédances stable, normalise les valeurs 253–255 en 255 et rejette une touche sans impédance exploitable. C’est une stratégie plus stricte que les compteurs indépendants du logiciel OEM : un signal instable peut nécessiter une nouvelle tentative. Délai maximal 15 secondes ; arrêt demandé au passage en arrière-plan, à la sortie de la page et au changement de sélection. Une perte de connexion est affichée et invalide l’état d’apprentissage ; l’envoi de sortie ne peut être garanti si le service ou le processus est déjà mort.

La fonction reconnue reste préparée dans l’UI ; « Enregistrer » applique le lot au véhicule. Ne pas afficher la simple réception d’un ADC comme une écriture matériel confirmée.

## Format et stockage

Fichier : `/jancar/config/ivi-studykey.ini`. Section : `[section_wheel_Study_Key]`.

Clé décimale : `(flags << 24) | (canal << 16) | (maximum << 8) | minimum`. Flags : bit 0 répétition, bit 2 impédance basse, bit 1 moyenne, aucun de ces deux bits pour haute.

Valeur : `basse,moyenne,haute;actionCourte[,actionLongue]`.

Le calcul de la meilleure impédance et des bornes provient de `setOriMiddle(List, HashMap)`, dont le Java est incomplet : le smali a été inspecté. Les touches sont triées par somme des impédances, séparées par canal, puis les écarts avec les voisines déterminent le choix de mesure et les limites. Les bornes restent dans 0–252. Les appels longs préexistants sont conservés pour les entrées non remplacées.

Les noms d’actions sont ceux du SDK inspecté : `add_volume`, `del_volume`, `mute`, `next`, `prev`, `play_pause`, `home`, `phone_link`, `navigation`, `mode`, `answer`, `hang_up`, `voice`, `launcher_app`. Les touches volume ont la répétition activée.

Avant écriture : lecture complète, refus d’un format ancien non compris, comparaison avec le contenu chargé pour détecter un changement externe et sauvegarde complète `.bak` horodatée. Seule la section du volant est remplacée ; le panneau, l’IR et les autres réglages restent présents. Le fichier original est écrit et synchronisé en conservant son inode ; une restauration est tentée en cas d’erreur d’écriture. Il n’existe pas de garantie d’atomicité contre une coupure électrique pendant cette écriture. Réinitialiser crée une section volant vide, sans supprimer les autres sections ni les réglages logiciels du launcher.

L’application OEM utilise aussi `fsync` après sauvegarde. Aucune transaction de « rechargement » supplémentaire n’a été identifiée dans ce chemin ; la prise en compte réelle du fichier doit être vérifiée sur le service du firmware.

## Bouton contextuel

Activer le commutateur et apprendre la fonction « Accueil / Android Auto ». L’action véhicule enregistrée est `launcher_app`, identifiant OEM 1004. Sur DOWN/ONCE, le bridge mémorise si le launcher était repris au moment du callback :

- Launcher visible : lancer l’application Android Auto configurée dans les réglages ; replis sur `com.auto.zlink` et `com.zjinnova.zlink` lorsqu’ils sont installés.
- Launcher non visible : ouvrir `MainActivity` du launcher.

Les événements rapprochés sont filtrés pendant 700 ms. Le bridge reste lié pour recevoir les callbacks lorsque l’Activity est en arrière-plan. Cela dépend de la survie du processus et des politiques OEM de lancement d’Activity. Le callback observé n’offre pas de mécanisme d’annulation : si le service exécute lui-même une action Home, une concurrence est possible. L’ordre des actions, l’absence de double lancement et le comportement écran éteint restent à vérifier sur autoradio. Le code ne prétend pas remplacer un gestionnaire global de touches garanti par le système.

## Installation système et limites

Le manifeste OEM déclare `android.uid.system`. Le launcher actuel conserve son identifiant, son manifeste et sa signature : ajouter ce texte seul ne lui attribuerait pas ces privilèges et pourrait empêcher les mises à jour existantes. L’accès au fichier Jancar exige les droits réels du firmware (UID, signature, permissions de fichiers et SELinux). Aucun `su`, chmod, remount ni contournement de sécurité n’est exécuté. Un refus est affiché dans la page.

La présence de l’UID système alléguée doit être vérifiée sur l’installation cible ; elle n’est pas établie par une compilation ou par l’APK de développement. La calibration n’a pas été exécutée sur un MCU réel. Les ressources et tokens du projet restent inchangés.

À tester à l’arrêt : mode Study et sortie, stabilité de chaque touche, associations persistantes après redémarrage, préservation du panneau et de l’IR, répétition du volume, appels et voix, reset et sauvegarde, interruption pendant l’apprentissage, retour de veille, mort du service et fonctionnement du bouton contextuel depuis une application tierce. Ne pas lancer simultanément deux applications d’apprentissage.

## Vérifications exécutées le 29 octobre 2026

Build release, R8, réduction des ressources, lint et tests JVM réussis. 34 tests réussis, dont 8 tests du codec de calibration ; aucun échec. Les tests JVM ne simulent pas le service Binder ni le MCU. Les configurations et tokens protégés sont inchangés, vérifiés par empreinte. APK release généré non signé ; aucune signature système ni installation sur autoradio effectuée.

## Fonctions combinées musique / téléphone

La grille propose aussi « Piste suivante / Raccrocher » (`next_hangup`, identifiant OEM 1002) et « Piste précédente / Décrocher » (`prev_answer`, identifiant OEM 1001), confirmées dans `IVIKey.java`. Elles sont enregistrées comme actions natives uniques ; le service du firmware choisit leur comportement selon le contexte média/téléphone. Elles n’envoient pas deux commandes simultanées depuis le launcher. Le fonctionnement pendant un appel reste à vérifier sur autoradio.

## Appuis longs et assistant du launcher

La page propose maintenant « Appui court » et « Appui long ». Le mapping INI conserve `actionCourte,actionLongue`, y compris une action longue seule. Réapprendre une pression préserve l’autre fonction du même bouton. L’appui physique pendant la calibration identifie le bouton ; c’est le mode sélectionné à l’écran qui définit court ou long. Le service OEM applique ensuite son seuil d’appui long.

L’événement voix OEM 106 ouvre explicitement MainActivity avec une demande à l’assistant du launcher. Le bip est joué lorsque SpeechRecognizer signale être prêt ; la réponse de commande et les erreurs sont transmises à la synthèse vocale Android, avec attributs audio de guidage. Une voix et un service de reconnaissance compatibles doivent être installés. Les callbacks d’anciennes reconnaissances sont ignorés. L’action native `voice` peut aussi être traitée par le firmware : vérifier qu’il ne lance pas simultanément un assistant système.

Les réponses TTS demandent un focus audio temporaire avec atténuation de la musique et le libèrent en fin de phrase. Les tests de régression comprennent 4 cas supplémentaires pour la conservation des associations court/long et le stockage INI des actions longues seules.
