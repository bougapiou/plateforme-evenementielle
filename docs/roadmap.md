# Feuille de route — livraison module par module

| #   | Module                     | Statut        |
|-----|----------------------------|---------------|
| M0  | Fondations (monorepo, infra, `common/`, Flyway, OpenAPI) | ✅ livré |
| M1  | Authentification & RBAC     | ✅ livré |
| M2  | Structures & organisateurs   | ✅ livré |
| M3  | Événements (workflow, catégories, **activités**, programme, intervenants, partenaires, pages publiques) | ✅ livré |
| M4  | Billetterie (catégories, quotas, commandes, **portée événement / activité**) | ✅ livré |
| M5  | Stands (types, réservation, hold 15 min, expiration) | ✅ livré |
| M6  | Inscriptions (particulier & structure, documents) | ✅ livré |
| M7  | Paiements (`PaymentProvider`, sandbox, webhook HMAC) | ✅ livré |
| M8  | Billets électroniques & QR codes | ✅ livré |
| M9  | Contrôle d'accès (scan) | ✅ livré |
| M10 | Factures & reçus (PDF) | ✅ livré |
| M11 | Notifications (in-app, email, canaux SMS/WhatsApp) | à venir |
| M12 | Statistiques & tableaux de bord | à venir |
| M13 | Pages publiques (recherche, filtres, détail) | à venir |
| M14 | Frontends Angular + Flutter | en continu |
| M15 | Durcissement, tests bout-en-bout, données de démo | à venir |

Chaque module est livré avec : structure de fichiers, entités, DTO, services,
controllers, routes API, validation, gestion d'erreurs, sécurité, tests,
migration Flyway.

## M10 — Factures & reçus (contenu livré)

- Entité (Flyway V10) : `invoices` (numéro séquentiel `FAC-2027-00001` /
  `REC-2027-00001`, type FACTURE / REÇU, lié au paiement).
- **Génération automatique** : à chaque paiement réussi, `InvoiceService`
  (écoute `PaymentSucceededEvent`) émet une **facture** + un **reçu** ;
  idempotent, snapshot du client (raison sociale / RCCM / IFU pour une structure).
- `common/pdf/SimplePdf` (PDFBox) : constructeur de documents A4.
- `ConfirmationPdfService` : **confirmation d'inscription** et **confirmation de
  réservation de stand** en PDF.
- Endpoints : `/api/invoices/my` · `/api/invoices/{id}` · `/api/invoices/{id}/pdf`
  · `/api/payments/{id}/invoices` ;
  `/api/registrations/{id}/confirmation.pdf` ·
  `/api/stand-reservations/{id}/confirmation.pdf`.
- Frontend : page « Mes factures & reçus » (téléchargement PDF) ; boutons
  « Confirmation PDF » sur les inscriptions et réservations confirmées.
- Tests : `InvoiceIT` (paiement → facture + reçu + PDF `%PDF`, accès refusé à un
  tiers ; confirmation d'inscription PDF). 25/25 verts.

## M9 — Contrôle à l'entrée (contenu livré)

- Entités (Flyway V9) : `event_staff` (personnel de contrôle par événement),
  `checkins` (journal des scans).
- `POST /api/checkins/scan {token, eventId}` → **VALIDE** (nom, catégorie, n°,
  heure d'entrée ; billet marqué `UTILISE`), **DEJA_UTILISE** (date du 1er
  contrôle), **INVALIDE** (inconnu / annulé / autre événement). Chaque scan est
  journalisé et audité.
- Habilitation : organisateur de l'événement, personnel assigné (`event_staff`),
  ou admin (`EVENT_VALIDATE`). L'ajout au personnel accorde le rôle
  `PERSONNEL_CONTROLE`.
- Endpoints : `/api/events/{id}/staff` (CRUD), `/api/events/{id}/checkins`
  (journal), `/api/events/{id}/checkin-stats` (compteurs).
- Frontend : page **« Contrôle à l'entrée »** — sélection de l'événement, scan
  caméra (API `BarcodeDetector`) + saisie manuelle, résultat plein écran
  (vert / orange / rouge) + compteurs ; onglet « Contrôle » de l'éditeur
  (personnel + journal).
- Tests : `CheckinIT` — flux complet avec **décodage réel du QR** (ZXing) :
  valide → déjà utilisé → invalide, permission refusée à un participant,
  scan par un membre du personnel. 23/23 verts.

## M8 — Billets électroniques & QR codes (contenu livré)

- Entité (Flyway V8) : `qr_codes` (token unique par billet).
- QR code émis automatiquement à la génération de chaque billet (paiement réussi).
- `common/web/QrImages` (ZXing) : image PNG ; `TicketPdfService` (PDFBox) :
  billet A5 avec nom de l'événement, participant, catégorie, numéro, date, lieu
  et QR code.
- Endpoints : `GET /api/tickets/{id}` · `/qr.png` (image/png) · `/pdf`
  (application/pdf, téléchargement). Accès : titulaire du billet, organisateur
  de l'événement, ou personnel de contrôle (`CHECKIN_SCAN`).
- Le token du QR n'est jamais exposé en JSON — seulement dans l'image.
- Frontend : « Mes billets » affiche le vrai QR (blob authentifié) et permet le
  téléchargement du PDF.
- Tests : `TicketQrIT` (billet payé → image PNG + PDF `%PDF` ; inconnu 403,
  organisateur 200). 22/22 verts.

## M7 — Paiements (contenu livré)

- Entité (Flyway V7) : `payments` (référence, provider, moyen, cible
  TICKET_ORDER / STAND_RESERVATION, montant, statut) + index unique partiel
  `(provider, transaction_ref)` pour l'**idempotence des webhooks**.
- **Abstraction `PaymentProvider`** (`initiate`, `verifyWebhook`) ;
  `SandboxPaymentProvider` bundlé (signature HMAC-SHA256, corps JSON) ; provider
  actif choisi par `app.payment.provider`. FasoArzeka / mobile money = nouvelle
  implémentation à brancher.
- Endpoints : `POST /api/payments` (initier → renvoie `paymentUrl`),
  `GET /api/payments/{id,my}`, `GET /api/payments` (admin `PAYMENT_READ`),
  `POST /api/payments/{id}/refund` (`PAYMENT_MANAGE`),
  `POST /api/payments/webhook` (public, signé — rejeté 400 si signature invalide),
  `POST /api/payments/{ref}/simulate` (sandbox).
- Sur succès → `Payment.REUSSI` + `markPaid()` de la cible → billets / stand
  confirmés → `PaymentSucceededEvent` → inscription confirmée (M6).
- Les boutons « pay-sandbox » (billets / stands) passent désormais par la couche
  paiement (`quickSandboxPay`) : un vrai `Payment` est enregistré.
- Frontend : « Mes paiements ».
- Tests : `PaymentIT` (initiation → webhook succès → commande payée + idempotence ;
  signature invalide → 400 ; échec → commande reste en attente). 21/21 verts.

## M6 — Inscriptions (contenu livré)

- Entités (Flyway V6) : `registrations`, `participants`, `documents` (+
  `events.validation_inscription`).
- Inscription particulier **ou** structure (raison sociale, membre vérifié),
  liste de participants, informations complémentaires.
- Achat de billets **rattaché à l'inscription** : `POST /events/{id}/registrations`
  crée si besoin la commande de tickets (M4) et lie les deux.
- Confirmation : gratuite → `CONFIRMEE` immédiate ; payante → `CONFIRMEE` quand
  la commande est payée (via `PaymentSucceededEvent` — découplage inter-modules) ;
  `validation_inscription` → l'organisateur valide/refuse (`confirm` / `reject`).
- **Documents demandés** : `FileStorageService` (disque local, PDF/images ≤ 15 Mo,
  servis sous `/files/**`) ; `POST/GET/DELETE /api/registrations/{id}/documents`.
- Endpoints : `/api/events/{id}/registrations` (créer / lister organisateur),
  `/api/registrations/my`, `/registrations/{id}` (+ cancel / confirm / reject / documents).
- Frontend : flux « Participer » unifié sur la page publique (participants +
  billets + paiement), « Mes inscriptions », onglet Inscriptions de l'éditeur.
- Tests : `RegistrationIT` (gratuit → confirmé + doublon bloqué ; billets →
  confirmé au paiement ; validation organisateur ; upload de document). 18/18 verts.

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
