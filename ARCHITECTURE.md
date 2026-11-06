# Architecture

## Modules

| Module | Rôle | Android ? |
|---|---|---|
| `core:model` | Domaine pur : sources, catégories, kiosque (filtres, recherche), validation des paramètres, classement des notifications, heures calmes | **Non** (Kotlin JVM, portable iOS/KMP) |
| `core:network` | Retrofit + kotlinx.serialization, DTO du serveur, intercepteurs (en-tête client, Bearer limité à l'hôte du service) | oui |
| `core:database` | Room : cache du catalogue, ETag, favoris, historique local des notifications (migrations explicites) | oui |
| `core:data` | Dépôts hors-ligne d'abord (catalogue ETag/304, compte, abonnements, favoris, historique), préférences DataStore, jeton chiffré (Keystore AES-GCM) | oui |
| `core:ui` | Thème (tokens du site), polices OFL, composants (AlertCard, StatePill, EmptyState…), sélecteurs, liens Custom Tabs | oui |
| `core:notifications` | Canaux par famille, notificateur riche, ingestion (déduplication, heures calmes, plafond, sourdine), actions | oui |
| `core:push` | UnifiedPush (service), enregistrement Web Push sur `/api/push/subscribe`, relève périodique WorkManager, synchronisation `/api/app/events` | oui |
| `feature:*` | Écrans (kiosque, alerte + Mes alertes + historique, comptes, réglages/diagnostic…) | oui |
| `app` | Navigation, thème appliqué, démarrage de la réception | oui |

Dépendances : `feature → core:data, core:ui, core:model` ; aucune feature ne dépend d'une autre
(les liens entre écrans passent par des lambdas/slots fournis par `app`). `core:data` ne dépend pas de
`core:push` : les réactions à la connexion/déconnexion passent par `SessionHook` (multibinding Hilt).

## Flux principaux

**Catalogue** : l'UI observe Room ; `CatalogRepository.refreshSources()` envoie `If-None-Match` ;
304 → rien à télécharger ; hors ligne → bandeau, cache conservé.

**Réception d'une alerte** :

    serveur (poller) ──Web Push──> distributeur UnifiedPush (ntfy…) ──> LbaPushService.onMessage
                                                                      └─> EventSync.onPush
    WorkManager (15 min, si pas de distributeur) ──> PollWorker ──> EventSync.sync
    EventSync ──GET /api/app/events?since_id=N──> EventIngestor
    EventIngestor : déduplication (id serveur + empreinte de contenu 24 h) → Room (historique)
                  → sourdine / heures calmes / plafond → AlertNotifier (canal, BigText, actions)

**Connexion** : e-mail → `POST /api/app/login/request` → code à 6 chiffres (ou lien
`/connexion?token=`) → session Bearer 90 jours stockée chiffrée → `SessionHook.onSignedIn`
démarre la réception.

## Choix structurants

Voir `DECISIONS.md` (D006 push par l'existant Web Push, D012 code de connexion, D013 déploiement…).
