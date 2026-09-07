# Feuille de route — livraison module par module

| #   | Module                     | Statut        |
|-----|----------------------------|---------------|
| M0  | Fondations (monorepo, infra, `common/`, Flyway, OpenAPI) | ✅ livré |
| M1  | Authentification & RBAC     | ✅ livré |
| M2  | Structures & organisateurs   | ✅ livré |
| M3  | Événements (workflow, catégories, **activités**, programme, intervenants, partenaires, pages publiques) | ✅ livré |
| M4  | Billetterie (catégories, quotas, commandes, **portée événement / activité**) | ✅ livré |
| M5  | Stands (types, réservation, hold 15 min, expiration) | ✅ livré |
| M6  | Inscriptions (particulier & structure, documents) | à venir |
| M7  | Paiements (`PaymentProvider`, sandbox, webhook HMAC) | à venir |
| M8  | Billets électroniques & QR codes | à venir |
| M9  | Contrôle d'accès (scan) | à venir |
| M10 | Factures & reçus (PDF) | à venir |
| M11 | Notifications (in-app, email, canaux SMS/WhatsApp) | à venir |
| M12 | Statistiques & tableaux de bord | à venir |
| M13 | Pages publiques (recherche, filtres, détail) | à venir |
| M14 | Frontends Angular + Flutter | en continu |
| M15 | Durcissement, tests bout-en-bout, données de démo | à venir |

Chaque module est livré avec : structure de fichiers, entités, DTO, services,
controllers, routes API, validation, gestion d'erreurs, sécurité, tests,
migration Flyway.

## M5 — Stands (contenu livré)

- Entités (Flyway V5) : `stand_types`, `stands` (plan : numéro, position),
  `stand_reservations`.
- Création d'un type de stand → **génération automatique des stands** numérotés
  (`STANDARD-001`…) ; l'ajustement du nombre ajoute / retire des stands libres.
- Réservation : blocage temporaire **15 min** (`RESERVE_TEMP`,
  `hold_expire_le`) ; **anti-double réservation** garanti par un index unique
  partiel PostgreSQL (`uk_stand_active_reservation`) + verrou pessimiste sur le
  stand ; `StandExpiryJob` libère les stands non payés.
- Statuts : `EN_ATTENTE`, `RESERVE_TEMP`, `ATTENTE_PAIEMENT`, `PAYE`,
  `CONFIRME`, `ANNULE`, `EXPIRE`. Réservation gratuite → `CONFIRME` immédiat.
- Endpoints : `/api/events/{id}/stand-types` + `/stands` (organisateur),
  `/api/stand-reservations` (réserver / mes réservations / annuler /
  `pay-sandbox` / `for-event`), `/api/public/events/{slug}/stand-types` + `/stands`
  (plan avec disponibilité).
- Frontend : onglet « Stands » de l'éditeur, réservation d'un emplacement sur la
  page publique (sélection stand → blocage → paiement simulé), « Mes stands ».
- Tests : `StandReservationIT` (blocage + double réservation refusée + paiement ;
  annulation → stand libéré ; stand gratuit confirmé). 14/14 verts.

## M4 — Billetterie (contenu livré)

- Entités (Flyway V4) : `event_tickets` (catégories : prix, devise, quota,
  limite/utilisateur, fenêtre de vente), `event_ticket_activities` (N–N),
  `ticket_orders` + `ticket_order_lines`, `tickets`.
- **Portée** : `event_tickets.portee` ∈ {EVENEMENT, ACTIVITE} ; une catégorie de
  portée ACTIVITE référence une ou plusieurs activités (`event_ticket_activities`).
- Commande : réservation atomique du quota sous **verrou pessimiste**
  (`findByIdForUpdate`) ; contrôle du quota restant et de la limite par personne ;
  hold de **30 min** puis job d'expiration (`TicketExpiryJob`) qui libère le quota.
- Confirmation du paiement (`markPaid` / sandbox) → `quantite_reservee` →
  `quantite_vendue`, génération des billets (`tickets`, statut EMISE, numéro
  unique). Commandes gratuites confirmées immédiatement.
- Endpoints : `/api/events/{id}/tickets` (CRUD organisateur),
  `/api/ticket-orders` (créer / mes commandes / annuler / `pay-sandbox` /
  `for-event/{id}`), `/api/tickets/my`, `/api/public/events/{slug}/tickets`.
- `common/money/Money` (montant + devise, FCFA par défaut, `formatted()`).
- Frontend : onglet « Billetterie » de l'éditeur d'événement (catégories,
  portée activité), achat de billets sur la page publique (commande + paiement
  simulé), « Mes billets » + « Mes commandes ».
- Tests : `TicketingIT` (quota + limite/personne + paiement → billets ;
  billet gratuit confirmé ; annulation → quota libéré). 11/11 verts.

## M3 — Événements (contenu livré)

- Entités (Flyway V3) : `event_categories`, `events`, `event_activities`
  (programme), `speakers`, `partners`.
- **Activités** : `events.has_activities` déclare si l'événement contient
  plusieurs activités ; le créateur planifie alors le programme
  (`event_activities` : titre, type, début/fin, salle, intervenant, modérateur,
  capacité). Les catégories de tickets (M4) pourront cibler des activités.
- Workflow complet : `BROUILLON → SOUMIS → VALIDE → PUBLIE →
  INSCRIPTIONS_OUVERTES/FERMEES → EN_COURS → TERMINE` (+ `REFUSE` avec motif,
  `SUSPENDU`/`ANNULE` admin). Job `@Scheduled` (EventLifecycleJob) fait avancer
  `EN_COURS`/`TERMINE` selon les dates.
- Sécurité par propriété : un organisateur ne voit/gère que ses événements ;
  `EVENT_VALIDATE` (admin) voit tout, valide/refuse/suspend/annule.
- Endpoints organisateur : `POST/GET/PUT/DELETE /api/events`, `/events/mine`,
  `/events/{id}/{submit|publish|open-registrations|close-registrations}` ;
  `/api/events/{id}/{activities|speakers|partners}` (CRUD).
- Endpoints admin : `/api/events/admin`, `/events/{id}/{validate|reject|suspend|cancel}`.
- Endpoints publics : `GET /api/public/events` (recherche + filtres catégorie /
  ville / dates), `GET /api/public/events/{slug}` (page complète : programme +
  intervenants + partenaires), `GET /api/event-categories`.
- 10 catégories seedées (Salon, Foire, Festival, Forum, Conférence…).
- Frontend : « Mes événements » (+ création), éditeur à onglets
  (Informations / Programme / Intervenants / Partenaires + actions de workflow),
  écran admin de validation, **site public** (liste + filtres + page détail).
- Tests : `EventWorkflowIT` (cycle complet + activités + visibilité publique ;
  isolation entre organisateurs ; refus + motif + resoumission). 8/8 verts.

## M2 — Structures & organisateurs (contenu livré)

- Entités : `structures`, `structure_members`, `organizers` (Flyway V2).
- Structure : compte pro (raison sociale, sigle, type, secteur, RCCM, IFU,
  adresse…), statut `EN_ATTENTE`/`VERIFIEE`/`SUSPENDUE` (vérif. admin), créateur =
  propriétaire, obtention du rôle `STRUCTURE`.
- Représentants : ajout par e-mail (utilisateur existant), rôles internes
  `PROPRIETAIRE`/`ADMINISTRATEUR`/`MEMBRE`, sécurité par appartenance.
- Organisateur : demande (`/organizers/apply`) → validation admin
  (`/organizers/{id}/approve`) qui accorde le rôle `ORGANISATEUR` (permissions
  événements). Rattachement optionnel à une structure gérée.
- Endpoints : `POST/GET/PUT /api/structures`, `/structures/mine`,
  `/structures/{id}/members`, `PATCH /structures/{id}/status` ;
  `/api/organizers/apply|me`, `GET /api/organizers`, `/organizers/{id}/approve|suspend`.
- Frontend : « Mes structures » (+ création, représentants), « Espace
  organisateur » (demande / suivi), écrans admin Structures / Organisateurs /
  Utilisateurs.
- Tests : `StructureOrganizerIT` (cycle structure + membres + vérif. admin ;
  demande organisateur + approbation → permissions événements).

## M0 — Fondations (contenu livré)

- Monorepo `backend/` `frontend/` `mobile/` `infra/` `docs/`.
- `infra/docker-compose.yml` : PostgreSQL 16, Mailpit, MinIO.
- Backend Spring Boot 3 / Java 21, package-by-feature, `bf.evenements.plateforme`.
- `common/` : `BaseEntity`, `ApiError` + `GlobalExceptionHandler`, `PageResponse`,
  `AppProperties`, OpenAPI/Swagger, CORS, résolveur `@CurrentUser`.
- Flyway `V1` (baseline).
- Actuator `health`.

## M1 — Authentification & RBAC (contenu livré)

- Entités : `users`, `roles`, `permissions` (+ tables de liaison),
  `refresh_tokens`, `audit_logs`.
- JWT access (15 min) + refresh opaque rotatif (7 j, stocké haché SHA-256).
- BCrypt, `@EnableMethodSecurity`, autorités = permissions + `ROLE_*`.
- Endpoints :
  - `POST /api/auth/register` · `POST /api/auth/login` · `POST /api/auth/refresh`
    · `POST /api/auth/logout`
  - `GET/PATCH /api/users/me` · `POST /api/users/me/password`
  - `GET /api/users` · `GET /api/users/{id}` · `PATCH /api/users/{id}/status`
    · `PUT /api/users/{id}/roles` (admin)
  - `GET /api/roles` · `GET /api/permissions` · `POST /api/roles`
    · `PUT /api/roles/{id}/permissions` (admin)
  - `GET /api/audit-logs` (admin)
- Rôles système seedés : `SUPER_ADMIN`, `ORGANISATEUR`, `PARTICIPANT`,
  `STRUCTURE`, `PERSONNEL_CONTROLE`.
- Super-admin initial créé depuis la configuration.
- Journalisation des évènements d'authentification et des actions RBAC.
- Test d'intégration bout-en-bout (`AuthFlowIT`, Testcontainers PostgreSQL).
