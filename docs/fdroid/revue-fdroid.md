# Revue F-Droid (2026-10-08)

| Critère | Constat | Statut |
|---|---|---|
| Licence | MIT (`LICENSE`), polices SIL OFL 1.1 (textes joints dans `core/ui/src/main/assets/licences/`) | OK |
| Dépendances propriétaires | aucune : pas de `com.google.android.gms`, Firebase, Crashlytics, analytics, publicité (groupes vérifiés dans `gradle/verification-metadata.xml`) | OK |
| Groupes `com.google.*` présents | Dagger/Hilt, Tink, KSP, Guava, Protobuf, Gson, AutoValue, ErrorProne… : tous libres (Apache 2.0) ; `com.android.tools.analytics-library` = outillage de build d'AGP, absent de l'APK | OK |
| UnifiedPush | connector 3.3.5 (Apache 2.0), sans `embedded_fcm_distributor` | OK |
| Bloc de signature des dépendances | désactivé (`dependenciesInfo`) | OK |
| WebView | aucune ; liens externes en Custom Tabs | OK |
| Télémétrie | aucune ; journaux Timber seulement en debug | OK |
| Binaires dans le dépôt | `gradle/wrapper/gradle-wrapper.jar` uniquement (accepté par F-Droid, qui utilise sa propre version de Gradle) | OK |
| Dépôts Maven | `google()` et `mavenCentral()` uniquement (+ `gradlePluginPortal()` pour les plugins de build) | OK |
| Builds reproductibles | mesures prises (voir `docs/reproductibilite.md`) ; comparaison sur deux machines NON VÉRIFIÉE | à vérifier |
| Anti-fonctionnalités | `TetheredNet` probable (service labonnealerte.fr non modifiable) — proposé dans la recette, à décider par Hugo | à décider |
| `NonFreeNet` | non : le code du service est libre (MIT, GitHub) | OK |
| Métadonnées | Fastlane fr-FR : titre, descriptions (courte : 77 caractères), changelog 1, icône, bannière, 6 captures | OK |

Non vérifié : `fdroid lint` et `fdroid build` (fdroidserver non installé ici ; à lancer par Hugo dans son fork de fdroiddata, voir `docs/fdroid/README.md`).
