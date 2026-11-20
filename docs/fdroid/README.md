# Publication F-Droid — guide pour Hugo

## Ce qui est prêt dans le dépôt
- Flavor `foss` sans aucune dépendance propriétaire (pas de Google Play Services, Firebase, analytics, publicité).
- `dependenciesInfo { includeInApk = false; includeInBundle = false }` (bloc de signature refusé par F-Droid).
- Métadonnées Fastlane : `fastlane/metadata/android/fr-FR/` (titre, descriptions, changelog `1.txt`, icône et captures à ajouter en M18).
- Brouillon de recette : `docs/fdroid/fr.labonnealerte.app.yml`.

## À faire par Hugo
1. Créer le dépôt public GitHub de l'app et y pousser ce dépôt (`git remote add origin … ; git push -u origin main`).
2. Garder en lieu sûr le keystore de release (`C:\Dev\secrets\lba-app\`) : **s'il est perdu, plus aucune mise à jour n'est possible** pour les utilisateurs.
3. Créer la release `v0.1.0` (workflow GitHub « Release », déclenché à la main) qui publie l'APK signé et son SHA-256.
4. Remplir dans la recette : URL du dépôt, hash complet du commit taggé, empreinte SHA-256 de la clé (`apksigner verify --print-certs app-foss-release.apk`, ligne « SHA-256 digest », en minuscules sans « : »).
5. Forker https://gitlab.com/fdroid/fdroiddata, copier la recette dans `metadata/fr.labonnealerte.app.yml`, lancer `fdroid lint fr.labonnealerte.app` et `fdroid build -v -l fr.labonnealerte.app`, puis ouvrir une merge request.
6. Décider de l'anti-fonctionnalité `TetheredNet` (service dont l'adresse n'est pas modifiable) : la garder par honnêteté, ou rendre l'adresse du serveur configurable dans l'app.

## Vérifier la reproductibilité soi-même
Voir `docs/reproductibilite.md` (rédigé en M18).

## Autres canaux
- IzzyOnDroid : demande d'inclusion sur https://github.com/IzzyOnDroid/repo (l'APK des GitHub Releases suffit).
- Obtainium : coller l'URL du dépôt GitHub de l'app.
