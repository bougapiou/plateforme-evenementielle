# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Présentation

Plateforme Nationale de gestion des grands Événements (PNE) du Burkina Faso :
création/validation d'événements, billetterie QR, réservation de stands,
inscriptions, paiement en ligne, contrôle d'accès, factures, notifications,
statistiques. Deux clients (`frontend` Angular, `mobile` Flutter) sur une seule
API (`backend` Spring Boot).

Le **domaine est nommé en français** (entités, colonnes SQL, enums, routes
Angular, messages d'erreur utilisateur : `nom`, `statut`, `dateDebut`,
`BROUILLON`, `/tableau-de-bord/evenements`) tandis que les **commentaires et la
Javadoc sont en anglais**. Suivre cette convention.

## Commandes

### Infrastructure de dev
```bash
cd infra && docker compose up -d      # PostgreSQL (hôte:5433), Mailpit (8025), MinIO (9001)
```

### Backend (`backend/`)
```bash
./mvnw spring-boot:run                 # API sur :8080, Swagger /swagger-ui.html
DEMO_DATA=true ./mvnw spring-boot:run  # + jeu de données de démo (SIAO, FESPACO…)
./mvnw test                            # tests d'intégration — Docker requis (Testcontainers)
./mvnw test -Dtest=TicketingIT         # une seule classe
./mvnw test -Dtest=TicketingIT#nomDuTest
```
Les tests démarrent **un seul conteneur PostgreSQL** partagé pour toute la JVM
(`support/AbstractIntegrationTest`) ; ne pas y ajouter de `@DynamicPropertySource`
divergent qui casserait le cache du contexte Spring.

### Frontend (`frontend/`)
```bash
npm install
npm start          # ng serve :4200, proxy /api et /files → :8080 (proxy.conf.json)
npm run build      # dist/frontend/browser
npm test           # Karma/Jasmine (aucun spec écrit à ce jour)
```

### Mobile (`mobile/`)
```bash
flutter pub get
flutter run                                                       # émulateur Android → 10.0.2.2:8080
flutter run -d chrome                                             # web → localhost:8080
flutter run --dart-define=API_BASE_URL=http://<IP LAN>:8080/api   # appareil physique
flutter build apk
```

### Production
```bash
cd infra
docker compose --env-file .env.prod -f docker-compose.prod.yml --profile db up -d --build
```
Procédure complète (TLS, permissions du stockage, pare-feu) :
`infra/README-HEBERGEMENT.md`. Retirer `--profile db` si PostgreSQL est externe.

## Architecture backend

`bf.evenements.plateforme`, **package-by-feature** : `auth`, `user`, `rbac`,
`audit`, `structure`, `organizer`, `event`, `ticket`, `stand`, `registration`,
`payment`, `qrcode`, `checkin`, `accreditation`, `invoice`, `notification`,
`document`, `upload`, `stats`, `bootstrap`, plus `common` (transverse).

Chaîne standard : `XxxController` → `XxxService` (`@Transactional`) →
`XxxRepository` → entité `Xxx`. DTO = `record` dans `dto/`, Bean Validation sur
les requêtes. Filtres de liste via `JpaSpecificationExecutor` +
`XxxSpecifications` (évite les paramètres nuls non typés côté PostgreSQL).
Toutes les erreurs passent par `GlobalExceptionHandler` → `ApiError`.

### Points transverses à connaître

- **Persistance** : toute entité étend `common.domain.BaseEntity` (PK UUID,
  `@Version` optimiste, `createdAt`/`updatedAt`). `ddl-auto=validate` — **tout
  changement de schéma exige une migration Flyway**
  `src/main/resources/db/migration/V<n>__<nom>.sql` ; ne jamais modifier une
  migration déjà appliquée.
- **Sécurité** : JWT stateless (`common.security`). `SecurityConfig` liste les
  routes publiques (`/api/public/**`, `/api/auth/**`, webhook et callback de
  paiement, `/files/**`, Swagger). Autorisation fine par `@PreAuthorize` sur les
  permissions du catalogue `rbac.Permissions` (source unique, également semée en
  base par `bootstrap`). L'utilisateur courant s'injecte avec `@CurrentUser`.
  `RateLimitFilter` limite les appels d'auth par IP (`RATE_LIMIT_PER_MINUTE`).
- **Sessions invité** : `/auth/guest` ouvre une session sans mot de passe
  (achat/inscription sans compte) ; `/auth/complete` la transforme en vrai
  compte. Un invité est authentifié mais porte `guest=true` — le distinguer
  côté client (`isFullyAuthenticated` en Angular).
- **Cycle de vie d'un événement** : `EventStatus` (BROUILLON → SOUMIS → VALIDE →
  PUBLIE → INSCRIPTIONS_* → EN_COURS → TERMINE, plus REFUSE/SUSPENDU/ANNULE).
  Les transitions autorisées sont appliquées par `EventService` ; l'enum porte
  `isEditable()`, `isPubliclyVisible()`, `acceptsRegistrations()` — s'en servir
  plutôt que comparer des statuts en dur. `EventLifecycleJob` fait avancer les
  statuts temporels toutes les 10 min.
- **Paiement** : interface `payment.provider.PaymentProvider`, implémentations
  `sandbox` (défaut) et `arzeka` (FasoArzeka réel), choisies par
  `app.payment.provider`. Le retour FasoArzeka n'étant pas signé, il n'est
  jamais cru sur parole : `checkStatus()` revérifie auprès de leur API
  authentifiée. Le succès d'un paiement est idempotent et publie un
  `PaymentSucceededEvent` (Spring) qui déclenche la confirmation de la cible
  (`TicketOrderService.markPaid` / `StandReservationService.markPaid`) puis les
  notifications via `NotificationListener`. Brancher toute nouvelle réaction à
  un paiement sur cet événement plutôt que dans `PaymentService`.
- **Billets & QR** : à la confirmation d'une commande, `QrCodeService.issueFor`
  émet un jeton opaque par billet (idempotent) ; `CheckinService` le valide au
  scan avec un sens (`CheckinDirection` entrée/sortie) et une activité
  optionnelle. PDF via `common.pdf.SimplePdf` (PDFBox), QR via ZXing.
- **Réservations temporaires** : les stands sont bloqués 15 min et les commandes
  de billets expirent — `StandExpiryJob` et `TicketExpiryJob` (toutes les 60 s)
  libèrent ce qui n'a pas été payé.
- **Fichiers** : `common.storage.FileStorageService` (provider `local` par
  défaut, servi sur `/files/**`). En production le conteneur backend tourne sous
  l'UID **10001** : le dossier hôte de stockage doit lui appartenir.
- **Configuration** : tout passe par `application.yml` + variables
  d'environnement documentées dans `.env.example` (spring-dotenv lit un `.env`
  local). Ajouter une option = une entrée dans les deux fichiers.

## Architecture frontend (Angular 19)

Composants **standalone**, routes *lazy* déclarées dans `app.routes.ts` (URL en
français), signals pour l'état. Deux layouts : `public-layout` (site public —
le scanner de QR et la recherche de billet y sont accessibles sans compte) et
`dashboard-layout` (`/tableau-de-bord`, protégé par `authGuard`).

- `core/` : `auth.service.ts` (signals `user` / `isAuthenticated` / `isGuest` /
  `isFullyAuthenticated`, tokens en `localStorage` sous les clés `pne.*`),
  `auth.interceptor.ts` (Bearer + refresh), `auth.guard.ts` (vérifie aussi
  `route.data.permission`), `api.ts` (`ApiBase`, à étendre par chaque service de
  feature), `models.ts`.
- `features/<domaine>/` : un `<domaine>.service.ts` étendant `ApiBase`, des
  `*.models.ts` et les composants. `shared/` regroupe les briques réutilisables
  (icônes, badges de statut, upload d'image, saisie de téléphone, graphiques).
- `environment.apiBaseUrl` vaut `/api` en dev comme en prod : en dev via le
  proxy Angular, en prod via nginx (`frontend/nginx.conf`).

## Architecture mobile (Flutter)

Riverpod + go_router + Dio. `lib/core/` (config, `api_client` avec refresh JWT,
`token_store` sécurisé, `providers`, `router`), `lib/data/` (`domain.dart` +
`repositories.dart`, un repository par domaine backend), `lib/features/`.
`AppConfig.apiBaseUrl` se résout seul selon la cible et `AppConfig.resolveHost`
réécrit les URL `localhost` renvoyées par l'API (indispensable sur émulateur et
appareil physique). Sur le web, le cache QR hors-ligne et le téléchargement des
PDF sont désactivés. Détail des écrans et de leurs endpoints :
`mobile/README.md`.

## Attentes

- Tout changement de schéma ⇒ migration Flyway ; toute nouvelle permission ⇒
  `rbac.Permissions` (le seeder la propage).
- Une évolution fonctionnelle touche en général les trois couches : API, écran
  Angular **et** écran Flutter. Vérifier les trois avant de conclure.
- Les tests d'intégration forcent `app.payment.provider=sandbox` et désactivent
  le rate limiter : ne pas faire dépendre un test d'un `.env` local.
