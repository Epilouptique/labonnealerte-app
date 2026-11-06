# Contribuer à l'application La Bonne Alerte

Merci ! Quelques règles simples.

## Principes
- **Que du signal** : pas de publicité, pas de traceur, pas de service propriétaire (Google Play Services, Firebase, analytics…). Toute nouvelle dépendance doit être libre et compatible F-Droid.
- **Grand public** : textes en français simple, sans jargon technique à l'écran (pas d'« API », de « token »…). Toutes les chaînes vont dans `strings.xml`.
- **Accessibilité** : cibles tactiles d'au moins 48 dp, contrastes ≥ 4,5:1 pour le texte, descriptions pour TalkBack, rien ne doit casser avec une police à 200 %.
- **Vie privée** : aucune donnée personnelle dans les journaux ; pas de permission de localisation.

## Construire et tester

    # JDK 17+ et SDK Android (plateforme 37)
    ./gradlew assembleFossDebug
    ./gradlew testDebugUnitTest :core:model:test ktlintCheck lintFossDebug verifyRoborazziDebug

Développer sans toucher au vrai service : voir « Développer sans toucher à la production » dans le README (serveur factice `tools/fake-api`).

## Dépendances
Le dépôt vérifie l'empreinte de chaque dépendance (`gradle/verification-metadata.xml`). Après un ajout ou une montée de version :

    ./gradlew --no-configuration-cache --write-verification-metadata sha256 ktlintCheck lintFossDebug testDebugUnitTest :core:model:test verifyRoborazziDebug assembleFossDebug assembleFossRelease

Relisez le diff du fichier avant de le commiter.

## Captures d'écran (Roborazzi)
Une modification visuelle volontaire : `./gradlew recordRoborazziDebug`, puis vérifiez les images dans `*/src/test/screenshots/`.

## Style
- Kotlin, ktlint (style `android_studio`, lignes ≤ 160) : `./gradlew ktlintFormat`.
- Architecture : voir `ARCHITECTURE.md`. Le module `core:model` reste en Kotlin pur (aucune dépendance Android).
- Commits : un par unité logique, messages en français.

## Signaler une faille de sécurité
Ne l'exposez pas publiquement : écrivez à contact@labonnealerte.fr (adresse VAPID du site — A CONFIRMER PAR HUGO).
