# Intégration des correctifs audio, SWC et radio

## Volume

La page multimédia contient une action explicite « Volume multimédia maximal ». Elle utilise STREAM_MUSIC, lit le maximum Android et affiche le volume réellement retourné après réglage. MODIFY_AUDIO_SETTINGS est déclaré. Aucune augmentation automatique au démarrage ni effet LoudnessEnhancer sur une session inconnue n’est appliqué. Le volume matériel MCU, les prévolumes par source et le téléphone peuvent rester indépendants ; ce bouton ne garantit pas la suppression des grésillements ou le gain souhaité dans un circuit analogique.

## Commandes au volant

Le bridge Study Binder et le format INI sont conservés. Avant la sauvegarde, les droits d’écriture du fichier et du répertoire de sauvegarde sont contrôlés ; les erreurs de lecture/écriture indiquent l’UID et la nécessité d’une installation OEM autorisée. L’UI propose « Ouvrir l’apprentissage OEM ». Ce chemin ouvre com.jancar.steeringwheelkeys, sans simuler une sauvegarde dans le launcher.

Aucun changement de sharedUserId, signature, SELinux ou permissions de fichiers. Les restrictions ne sont pas contournées : aucune méthode Binder de sauvegarde INI n’a été confirmée dans les sources fournies. Le résultat de la véritable écriture reste la vérification finale des droits, après les contrôles préalables.

## Radio externe

Le mode de recherche est lu par la transaction 23 avant un nouveau Seek. Si une recherche est déjà active, la pression l’arrête par la transaction 11. Sinon, ouverture du tuner, retrait de la sourdine, lecture de la fréquence matérielle puis transaction 8 ou 9. Un bouton « Arrêter la recherche » est également fourni. Aucune commande scanAll (transaction 10) n’est utilisée pour chercher une station unique. Les mises à jour UI ne relancent pas la recherche.

## Radio interne

Lorsque l’identifiant matériel détecté correspond au tuner interne, la page montre des commandes OEM distinctes. Elles démarrent explicitement com.jancar.radio.FmService avec fmradio.seek.previous / fmradio.seek.next / fmradio.turnoff, conformément à son onStartCommand. La page ne présente pas les mesures d’IRadio externe comme la fréquence, le RDS ou le signal du tuner interne. L’application radio OEM doit rester installée ; ses restrictions et le verrouillage RF restent à vérifier sur le firmware.

La commande de tuning de test fmradio.freq.valid n’est pas utilisée. La sensibilité, la région et les gains Jancar ne sont pas remplacés par des valeurs arbitraires. L’accès complet au tuner interne, à ses métadonnées et à son réglage de fréquence reste une limite distincte des actions de service confirmées.

## Validation requise sur autoradio

Tester le volume retourné, les effets sur la source Bluetooth réelle et la distorsion ; les droits du fichier SWC et la sauvegarde ; le backend affiché, les deux sens du Seek, l’arrêt et l’accrochage de stations connues. Ces modifications ne constituent pas une preuve de réception RF ni une attribution de privilèges système.
