# Cahier des charges – Appli de mixage audio Android

## 1. Objectif

Une appli Android qui contrôle en direct le son de plusieurs applis à la fois : volume, routage, effets et spatialisation, depuis l'appli ou depuis les notifications.

**Plateforme** : Android 10 minimum (capture audio des autres applis), Android 13+ recommandé. iOS est exclu car il ne permet pas de capter le son des autres applis.

## 2. Fonctions principales

### 2.1 Mixage multi-applis en temps réel

- Liste des applis qui jouent du son, avec un curseur de volume, un bouton muet et un bouton solo pour chacune.
- Capture via l'API AudioPlaybackCapture (les applis qui bloquent la capture sont signalées).
- Latence visée : moins de 40 ms.

### 2.2 Split gauche / droite

- Assigner chaque appli à l'oreillette gauche, droite ou aux deux.
- Balance G/D réglable par appli et en global.
- Option mono globale.

### 2.3 Son simultané TV (Cast) et casque

- Le média casté sur la TV est aussi joué dans le casque.
- Réglage du décalage (manuel au pas de 10 ms, plus un assistant de calage).
- Volumes séparés pour la TV et le casque.
- Point de vigilance : c'est la fonction la plus complexe, à valider avec un prototype.

### 2.4 Gains, égaliseur et presets

- Égaliseur 10 bandes par appli et en global.
- Compresseur et limiteur.
- Presets fournis : Musique, Film, Voix / Podcast, Jeu, Basses+, Nuit.
- Presets personnalisés, que l'on peut enregistrer, renommer, exporter et importer.

### 2.5 Volume précis de 0 à 200 %

- Réglage au pas de 1 %, par curseur ou par saisie.
- Limiteur automatique au-delà de 100 % pour éviter la saturation.
- Avertissement au-delà de 150 %.

### 2.6 Panneau de contrôle en notification

- Notification persistante avec le volume global, le preset actif, la 3D marche/arrêt et les principales applis.
- Tuiles de réglages rapides.
- Widget pour l'écran d'accueil.

## 3. Spatialisation : binaural et 3D virtuelle

### 3.1 Principe

Simulation d'un son 3D sur un contenu qui n'en a pas.

1. **Upmix** : le stéréo est converti en 5.1 ou 7.1 virtuel (centre pour les voix, côtés, ambiance).
2. **Binauralisation HRTF** : chaque canal virtuel est placé autour de la tête de l'auditeur.

**Bibliothèques possibles** : Resonance Audio, Steam Audio ou OpenAL Soft, avec des profils HRTF au format SOFA.

### 3.2 Options

- Choix entre plusieurs profils HRTF, avec un test d'écoute pour choisir le plus naturel.
- Taille de la pièce virtuelle : petite salle, salon, cinéma, plein air.
- Curseur d'intensité, de « stéréo élargi » jusqu'à « 3D complet ».
- Suivi de la tête, si le casque ou le téléphone a des capteurs compatibles.
- 3D par appli : chaque appli peut être placée dans l'espace (par exemple musique devant, appel sur le côté).
- Le mode 3D se désactive automatiquement si le contenu est déjà spatialisé.

### 3.3 Limites

- Fonctionne au casque uniquement, pas sur la TV.
- Légère latence supplémentaire, à éviter pour les jeux compétitifs (un mode basse latence est prévu).
- Consommation de batterie plus élevée.

## 4. Fonctions bonus

- Profils automatiques selon l'appareil connecté : casque, voiture ou TV.
- Ducking : la musique baisse automatiquement quand une voix ou une notification arrive.
- Mode nuit qui réduit les écarts de volume.
- Protection auditive : limite en dB et suivi de l'exposition.
- Boutons physiques assignables à des raccourcis.

### 4.1 Fonctions innovantes

- **Isolation par IA** : séparer en direct la voix, la musique et les bruitages d'une vidéo pour régler chaque élément (par exemple monter les dialogues sans monter les explosions). Fonction lourde, à prévoir en fin de projet.
- **Réduction de bruit sur toute appli** : nettoyer une vidéo ou un appel mal enregistré avant la lecture.
- **Volume adaptatif selon l'environnement** : le micro mesure le bruit ambiant et ajuste automatiquement le volume et l'égaliseur.
- **Calibrage auditif personnalisé** : un test d'audition rapide, puis un égaliseur adapté aux oreilles de l'utilisateur.
- **Pièces virtuelles par appli** : chaque appli placée à une distance différente (une réunion proche, la musique au loin).
- **Égaliseur automatique par contenu** : détection de la musique, d'un podcast ou d'un jeu, et application du preset adapté.
- **Partage audio à plusieurs** : le même son sur deux casques Bluetooth, chacun avec son volume et son égaliseur.
- **Mode concentration** : bruit blanc ou rose sous la musique, et notifications coupées pendant une durée choisie.
- **Raccourcis gestuels** : secouer le téléphone pour couper le son, toucher le casque pour changer de preset.
- **Historique d'écoute santé** : un score d'exposition sonore quotidien, avec des conseils de pause.
- **Presets partageables** : une communauté pour échanger ses réglages (par exemple « Spotify + casque Sony »).

### 4.2 Autres idées

- **Traduction vocale en direct** : sous-titres ou doublage traduits pour n'importe quelle vidéo ou appel.
- **Transcription à la demande** : obtenir le texte de ce qui vient d'être dit dans une vidéo, un podcast ou une réunion.
- **Rembobinage audio instantané** : un tampon des 30 dernières secondes de tout ce qui joue, pour réécouter une phrase ratée, même en direct.
- **Enregistrement multipiste** : capturer plusieurs applis sur des pistes séparées, pour le streaming ou les podcasts.
- **Vitesse et tonalité par appli** : accélérer un podcast sans changer la voix, ou changer la tonalité d'une musique.
- **Mode karaoké** : suppression de la voix d'une chanson en direct.
- **Microphone mixé** : ajouter la voix de l'utilisateur au son d'une appli, par exemple pour commenter un jeu en stream.
- **Planification** : un preset selon l'heure de la journée.
- **Profils par lieu** : un preset automatique au bureau, à la salle de sport ou en voiture.
- **Alerte sonore externe** : le son baisse si le micro détecte une sirène, un klaxon ou le prénom de l'utilisateur.
- **Sortie vers un autre appareil** : envoyer une appli précise vers une enceinte Wi-Fi ou un PC.
- **Visualiseur** : spectre ou vu-mètre par appli, dans l'appli et en widget.
- **Commande vocale** : par exemple « baisse YouTube de 20 % » ou « active le mode film ».

### 4.3 Outils standards du marché

- **AutoEQ par modèle de casque** : correction automatique à partir d'une base de casques mesurés.
- **Égaliseur paramétrique** : fréquence, gain et largeur libres pour chaque filtre.
- **Égaliseur graphique** : courbe dessinée au doigt.
- **Bass boost et virtualiseur** : renforcement des basses et élargissement stéréo.
- **Réverbération** : presets de salle, de hall ou de cathédrale.
- **Préampli** : baisse du gain global pour éviter la saturation.
- **Crossfeed** : léger mélange des canaux gauche et droit pour réduire la fatigue au casque.
- **Pas de volume personnalisés** : plus de crans sur les boutons physiques.
- **Volume de démarrage** : volume fixe à chaque connexion d'un casque.
- **Verrouillage du volume** : empêcher une appli ou le système de changer le volume.
- **Minuterie de sommeil** : baisse progressive puis arrêt du son.
- **Volumes média et appel séparés au Bluetooth.**
- **Choix du codec Bluetooth** : LDAC, aptX ou AAC, avec affichage de la qualité.
- **Convolution (IRS)** : fichiers de réponse impulsionnelle pour simuler un casque ou une pièce.
- **Sauvegarde et restauration des réglages**, thèmes clair et sombre.

## 5. Contraintes techniques

- Le traitement audio tourne en service de premier plan, ce qui impose une notification obligatoire.
- Permissions : capture audio (avec une autorisation de l'utilisateur à chaque session), Bluetooth et notifications.
- Les applis protégées (certains services de streaming) ne peuvent pas être capturées.
- Moteur audio en C++ (Oboe) pour une faible latence.

## 6. Étapes proposées

Ordre d'implémentation : chaque étape ne dépend que des précédentes.

1. **Socle technique** : projet Kotlin, service de premier plan, notification obligatoire, autorisation de capture (§5). Sans eux, aucune capture n'est possible.
2. **Prototype de capture** : capture par appli, volume de 0 à 200 % avec limiteur, muet et solo (§2.1, §2.5). Valide la faisabilité : l'appli capturée continue de jouer le son d'origine.
3. **Moteur audio C++ (Oboe)** : passage du traitement en natif pour tenir la latence de 40 ms, avant d'empiler des effets.
4. **Table de mixage et routage** : vue en tranches (§7.3), règles par appli enregistrées et réappliquées (§7.1).
5. **Split G/D** : canal, balance et mono (§2.2).
6. **Notification, tuiles et widget** (§2.6) : ils pilotent un état devenu stable.
7. **Chaîne de traitement et presets** : égaliseur, compresseur, limiteur, presets (§2.4, §7.2, §7.4).
8. **Spatialisation 3D** (§3) : se branche à son étage de la chaîne.
9. **Son simultané Cast et casque** (§2.3) : la plus risquée, sur un moteur et une chaîne déjà éprouvés.
10. **Fonctions bonus** (§4).

## 7. Gestion du son en détail

### 7.1 Routage

Chaque appli a une règle de routage :

- **Sortie** : casque, haut-parleur, TV (Cast), enceinte Wi-Fi, ou plusieurs à la fois.
- **Canal** : gauche, droite, les deux, ou position 3D.
- **Priorité** : en cas de conflit, l'appli prioritaire garde son volume et les autres baissent (ducking).
- Les règles sont enregistrées par appli et réappliquées automatiquement au lancement.

### 7.2 Chaîne de traitement par piste

Ordre de traitement appliqué à chaque appli, puis au mix final :

1. **Préampli** : ajustement du niveau d'entrée (-20 à +20 dB).
2. **Réduction de bruit / isolation** (optionnelle).
3. **Égaliseur** : 10 bandes, paramétrique ou AutoEQ.
4. **Compresseur** : lissage des écarts de volume.
5. **Spatialisation** : stéréo élargi ou 3D binaurale.
6. **Volume de piste** : 0 à 200 %.
7. **Mix** des pistes, puis **égaliseur global**, **limiteur** et **volume maître**.

Chaque étape peut être activée ou contournée individuellement.

### 7.3 Vue table de mixage

- Une tranche verticale par appli active : icône, vu-mètre, curseur de volume, boutons muet et solo.
- Sous chaque tranche : choix de la sortie, du canal (G/D/les deux) et du preset.
- Un appui long ouvre la chaîne de traitement détaillée de la piste.
- Une tranche « Maître » à droite pour le mix final.
- Version compacte dans la notification : les 3 applis les plus actives et le volume maître.

### 7.4 Presets détaillés

- **Musique** : égaliseur en V léger (basses et aigus +3 dB), compression faible, stéréo élargi.
- **Film** : voix renforcées (1 à 4 kHz +4 dB), compression moyenne, 3D virtuelle salle de cinéma.
- **Voix / Podcast** : coupure sous 100 Hz, voix +5 dB, compression forte, mono possible.
- **Jeu** : aigus renforcés pour les bruits de pas, 3D sans réverbération, mode basse latence.
- **Basses+** : basses +6 dB sous 120 Hz, limiteur actif.
- **Nuit** : compression très forte, volume maximum plafonné, basses réduites.

Tous les presets sont modifiables et peuvent servir de base à un preset personnalisé.

### 7.5 Technologies inédites

- **Synchronisation automatique TV et casque** : le micro du téléphone écoute la TV et calcule lui-même le décalage, sans réglage manuel.
- **Séparation par IA appliquée au routage** : envoyer la voix d'un film dans le casque et les effets sur la TV, ou la voix dans une oreillette et la musique dans l'autre.
- **Volume perçu égalisé entre applis** : mesure LUFS en continu pour que toutes les applis sonnent au même niveau.
- **Correction selon l'ajustement du casque** : le micro des écouteurs détecte un embout mal placé et compense la perte de basses.
- **Mixage contextuel par IA** : l'appli apprend les habitudes de l'utilisateur et propose ou applique les réglages correspondants.
- **Enceintes multiples synchronisées** : plusieurs téléphones ou enceintes utilisés comme un système surround.
- **Profils partagés entre appareils** : réglages synchronisés entre téléphone, tablette et PC.
- **Égaliseur selon la pièce** : analyse de l'écho par le micro et correction du son du haut-parleur ou de l'enceinte.
- **Mode « bulle sonore »** : plusieurs personnes écoutent la même vidéo sur leur propre casque, chacune avec sa langue, son volume et ses réglages.

### 7.6 Technologies sonores

**À intégrer**

- **Loudness dynamique (courbes d'isosonie)** : compensation automatique des basses et des aigus à bas volume.
- **Basses psychoacoustiques** : harmoniques ajoutées pour percevoir des basses que les petits écouteurs ne restituent pas.
- **Compression multibande** : compression séparée des basses, médiums et aigus.
- **Exciter harmonique** : brillance retrouvée sur les fichiers compressés.
- **Transient shaper** : attaque des sons renforcée (batterie, bruits de pas).
- **De-esser** : adoucissement des « s » sifflants.
- **Traitement mid/side** : réglage séparé du centre (voix) et des côtés (ambiance).
- **Annulation de diaphonie** : effet 3D sur les haut-parleurs du téléphone.
- **Formats audio objet ouverts (IAMF, Ambisonics)** : lecture de son 3D objet, alternative libre au Dolby Atmos.
- **Auracast (Bluetooth LE Audio)** : diffusion vers un nombre illimité de casques compatibles.
- **Expansion dynamique** : relief redonné aux musiques trop compressées.
- **Égaliseur à phase linéaire** : correction sans décalage entre les fréquences.
- **Rééchantillonnage haute qualité et dithering** : pas de perte lors des conversions de fréquence d'Android.
- **Mode bit-perfect et DAC USB** : son envoyé sans modification vers un convertisseur externe.
- **Émulation analogique** : saturation à lampe, vinyle ou cassette.
- **Égaliseur dynamique** : bande active seulement au-delà d'un seuil.

**À créer**

- **Retour haptique sur les basses** : vibration du téléphone en rythme avec les basses.
- **Restauration par IA** : reconstruction des fréquences perdues d'un audio compressé ou ancien.
- **Spatialisation intelligente** : détection de chaque instrument ou voix et placement automatique dans l'espace 3D.
- **Profil sonore « signature »** : un son unique créé à partir des presets préférés de l'utilisateur, qui évolue avec ses réglages.
- **Profil acouphènes** : atténuation de la fréquence de l'acouphène et son de masquage personnalisé.
- **Remix par stems** : voix, batterie, basse et instruments séparés et réglables.
- **Comparaison A/B instantanée** : bascule entre deux réglages, avec volume égalisé.

### 7.7 Demandes des utilisateurs d'autres marques et applis

**Volume par appli**

- Les boutons physiques ne changent que le volume de l'appli choisie, sans toucher aux autres niveaux.
- Voix du GPS séparée de la musique : baisser la musique ne baisse pas la navigation.
- Volumes par appli conservés après un redémarrage.
- Plusieurs applis jouent en même temps sans se mettre en pause entre elles.

**Égaliseur**

- Égaliseur de qualité appliqué à tout le téléphone, quel que soit le constructeur.
- Aucune chute ni pic de volume à l'activation ou la désactivation.
- Égaliseur automatique par playlist ou par genre.

**Bluetooth et panneau de volume**

- Boost de volume par appareil Bluetooth.
- Panneau de volume à gauche ou à droite, au choix.
- Panneau déplié automatiquement avec tous les curseurs.
- Changement de piste en appui long sur les boutons de volume.
