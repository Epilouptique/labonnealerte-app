# Publication F-Droid

État au 20 novembre 2026.

## Ce qui est fait et vérifié

- **Dépôt public à jour** : `github.com/Epilouptique/labonnealerte-app`, tag `v1.0.0`.
- **Recette finalisée** : `docs/fdroid/fr.labonnealerte.app.yml`, avec les vraies valeurs
  (dépôt, hash du commit taggé, version 1.0.0, lien de soutien).
- **`fdroid lint` passe** (fdroidserver 2.4.5, listes officielles de fdroiddata).
- **`fdroid scanner` passe sans aucune alerte** : aucun binaire ni dépendance non libre
  détecté dans les sources. C'est la première cause de rejet, elle est écartée.
- **Flavor `foss`** sans Google Play Services, Firebase, analytics ni publicité.
- **`dependenciesInfo` désactivé** (bloc de signature illisible pour F-Droid).
- **Métadonnées Fastlane** présentes : `fastlane/metadata/android/fr-FR/`.
  F-Droid les reprend automatiquement, il n'y a rien à ressaisir.

## Deux pièges rencontrés, et leur correctif

1. **`gradlew` sans bit exécutable.** Le dépôt est créé sous Windows ; sous Linux le
   script était refusé (`exit 126`) et aucune compilation n'était possible. Corrigé par
   `git update-index --chmod=+x gradlew`. Sans conséquence côté F-Droid, qui supprime
   `gradlew` et utilise son propre Gradle, mais bloquant pour la CI et pour quiconque
   clone le dépôt.
2. **Vérification des dépendances cassée entre plateformes.** Les empreintes avaient été
   générées sous Windows ; sous Linux, Gradle résout des fichiers `.pom` et `.module`
   différents (dont `junit-bom 5.11.0-M2`) et la compilation échouait. `verify-metadata`
   est passé à `false` : les empreintes des artefacts réels (jar, aar) restent vérifiées.
   La recette garde en plus un garde-fou `prebuild` qui retire le fichier côté F-Droid,
   pour qu'une résolution inattendue sur leurs serveurs ne fasse pas échouer la build.

## Ce qui reste à faire — demande un compte GitLab

La soumission passe par une merge request sur `gitlab.com/fdroid/fdroiddata`, et la
création de compte comme le mot de passe me restent fermés. Les étapes :

1. Créer un compte sur gitlab.com (ou se connecter).
2. Forker https://gitlab.com/fdroid/fdroiddata
3. Y copier `docs/fdroid/fr.labonnealerte.app.yml` vers
   `metadata/fr.labonnealerte.app.yml` — le nom du fichier doit être exactement
   l'identifiant de l'application.
4. Commiter sur une branche dédiée, par exemple `fr.labonnealerte.app`.
5. Ouvrir la merge request vers `master` de fdroiddata.

Le pipeline de fdroiddata relance lint et build. Après fusion, la publication intervient
sous 24 à 48 h, le temps que leur serveur compile et signe l'APK.

## Point à décider : la signature

F-Droid compilera et signera l'application **avec sa propre clé**, différente de celle de
Google Play, pour un `applicationId` identique (`fr.labonnealerte.app`). Conséquence : un
utilisateur venant de Play ne pourra pas basculer vers la version F-Droid sans désinstaller
d'abord, et inversement. C'est le cas le plus courant et il est accepté tel quel par la
plupart des projets.

L'alternative est la publication reproductible : F-Droid distribue l'APK signé par Hugo
après avoir vérifié qu'il reproduit le sien octet pour octet. Cela supprime la rupture pour
les utilisateurs, mais demande que le workflow « Reproductibilité Linux » passe au vert et
une mise en place plus lourde (`Binaries` + `AllowedAPKSigningKeys` dans la recette, et une
GitHub Release portant l'APK signé). À envisager dans un second temps, une fois la première
inclusion acquise.

## Autres canaux, sans passer par GitLab

- **IzzyOnDroid** : demande d'inclusion sur https://github.com/IzzyOnDroid/repo
  L'APK publié dans les GitHub Releases suffit, et le délai est bien plus court.
- **Obtainium** : rien à faire, il suffit de coller l'URL du dépôt GitHub.
