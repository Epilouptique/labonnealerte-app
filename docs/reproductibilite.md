# Vérifier que l'APK publié correspond au code source

Objectif F-Droid (« Reproducible Builds ») : n'importe qui reconstruit le même APK, octet pour octet,
hors signature. Mesures prises dans le projet :
- versions de toutes les dépendances et du wrapper Gradle épinglées (SHA-256 de la distribution Gradle vérifié) ;
- pas d'horodatage ni d'ordre de fichiers variable dans les archives (`isPreserveFileTimestamps = false`, `isReproducibleFileOrder = true`) ;
- pas de bloc « dependency metadata » dans l'APK ;
- fins de ligne normalisées (`.gitattributes`) ;
- R8 déterministe (pas de règles dépendant de l'environnement).

## Procédure

    git clone <depot> && cd <depot> && git checkout v<version>
    # JDK 21 Temurin, SDK Android plateforme 37, build-tools 37.0.0
    ./gradlew assembleFossRelease
    # APK non signé : app/build/outputs/apk/foss/release/app-foss-release-unsigned.apk
    # Comparer avec l'APK publié en retirant la signature :
    apksigcopier compare app-foss-release.apk --unsigned app-foss-release-unsigned.apk

`apksigcopier` (outil de F-Droid) copie la signature de l'APK publié sur l'APK reconstruit puis compare.

## État
NON VÉRIFIÉ : la comparaison de deux constructions sur deux machines différentes n'a pas encore été faite
(il faut la clé de release et un second environnement). À faire par Hugo ou en CI une fois le dépôt public.
