# La Bonne Alerte — application Android

Application Android libre de [labonnealerte.fr](https://labonnealerte.fr) : le kiosque d'alertes gratuit,
sans publicité, « que du signal ». Les alertes que vous suivez arrivent en **notifications Android**,
**sans services Google** (UnifiedPush, ou vérification périodique).

- Kotlin, Jetpack Compose, Material 3 aux couleurs du site
- Aucun traceur, aucune publicité, aucune dépendance propriétaire (compatible F-Droid)
- Licence : MIT (comme le site)

## Construire

Prérequis : JDK 17+ (celui d'Android Studio convient), SDK Android avec la plateforme 37.

    # PowerShell
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
    .\gradlew.bat assembleFossDebug          # APK de debug
    .\gradlew.bat testDebugUnitTest :core:model:test   # tests
    .\gradlew.bat ktlintCheck lintFossDebug  # style et lint

L'APK est dans `app/build/outputs/apk/foss/debug/`.

## Distribution : F-Droid et Google Play

Deux variantes (flavors) du même code :

| Variante | Canal | Différence |
|---|---|---|
| `foss` | F-Droid, GitHub Releases | Aucune dépendance propriétaire. Pas de connexion Google. |
| `play` | Google Play | Ajoute « Continuer avec Google » (Credential Manager et services Google). |

Aucune des deux n'utilise Firebase : les notifications passent par UnifiedPush ou par une vérification périodique.

    .\gradlew.bat assembleFossRelease       # APK F-Droid / GitHub
    .\gradlew.bat bundlePlayRelease         # AAB Google Play (app/build/outputs/bundle/playRelease/)

- La signature lit `C:\Dev\secrets\lba-app\signing.properties` (ou `LBA_SIGNING_PROPERTIES`), hors dépôt.
- L'ID client Web Google se règle par la propriété Gradle `lbaGoogleWebClientId` (dans `~/.gradle/gradle.properties`), hors dépôt.
- La recette F-Droid doit construire la variante `foss`.

## Développer sans toucher à la production

`tools/fake-api` est un serveur factice (Node, aucune dépendance) servant des copies des réponses
publiques du site, plus les routes de compte et de flux d'événements :

    node tools/fake-api/server.js            # http://localhost:8787
    adb reverse tcp:8787 tcp:8787           # le telephone ou l emulateur joint le PC
    adb shell run-as fr.labonnealerte.app.debug touch files/use_fake_api   # le debug vise 127.0.0.1:8787
    # connexion : n'importe quelle adresse, code 123456
    curl -X POST localhost:8787/dev/emit -d "{}"   # émet une alerte de test

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) — modules et flux de données
- [CONTRIBUTING.md](CONTRIBUTING.md) — comment contribuer

## Licence

MIT — voir [LICENSE](LICENSE). © 2026 Hugo Vial-Jaime (Dahu-Concept).
