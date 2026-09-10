# Plateforme Nationale de Gestion des Événements

Plateforme de digitalisation des grands événements (SIAO, FESPACO, Semaine du
Numérique, salons, foires, forums, conférences…) : création et administration
d'événements, inscriptions, billetterie électronique, réservation de stands,
paiement en ligne, contrôle d'accès par QR code, factures/reçus, notifications,
tableaux de bord et statistiques.

## Stack

| Composant   | Technologie                                    |
|-------------|------------------------------------------------|
| Backend API | Spring Boot 3 · Java 21 · JPA · PostgreSQL · Flyway |
| Frontend    | Angular 19 · Tailwind CSS                       |
| Mobile      | Flutter · Riverpod · go_router (catalogue, billetterie, paiement, inscriptions, stands, portefeuille QR hors-ligne, factures, notifications, scan de contrôle) |
| Paiement    | Abstraction `PaymentProvider` + provider *sandbox* (FasoArzeka ) |
| PDF / QR    | PDFBox · ZXing                                  |
| Devise      | FCFA (XOF) par défaut, multi-devises prévu      |

## Arborescence

```
backend/    API REST (bf.evenements.plateforme, package-by-feature)
frontend/   Application Angular (site public + 4 espaces)
mobile/     Application Flutter
infra/      docker-compose (postgres, mailpit, minio)
```

## Démarrage (3 terminaux)

```bash
# 1. Infrastructure : PostgreSQL (port 5433), Mailpit, MinIO
cd infra && docker compose up -d

# 2. API
cd ../backend && ./mvnw spring-boot:run           # DEMO_DATA=true pour des données d'exemple

# 3. Frontend
cd ../frontend && npm install && npm start        # http://localhost:4200

# 4.  Application mobile
cd ../mobile && flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api   # émulateur Android
```

| Service | URL |
|---|---|
| Site + espaces | http://localhost:4200 |
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Santé | http://localhost:8080/actuator/health |
| Mailpit (emails) | http://localhost:8025 |
| MinIO (fichiers) | http://localhost:9001 |

### Comptes par défaut

| Rôle | Identifiants |
|---|---|
| Super-administrateur | `admin@plateforme.bf` / `ChangeMe!2026` |
| Organisateur de démo (`DEMO_DATA=true`) | `organisateur@plateforme.bf` / `Demo!2026` |

**À changer en production** (`SUPER_ADMIN_EMAIL` / `SUPER_ADMIN_PASSWORD`,
`APP_JWT_SECRET`, `PAYMENT_WEBHOOK_SECRET`). Voir [`.env.example`](.env.example).

## Modules 

M0 fondations · M1 auth & RBAC (JWT, invité, **mot de passe oublié**) · M2 structures & organisateurs · M3 événements
(workflow, **activités/programme**, intervenants, partenaires, site public) ·
M4 billetterie (quotas, portée événement/activité, **activité gratuite/payante,
participation & contrôle par activité**) · M5 stands (blocage 15 min,
anti-double réservation) · M6 inscriptions (particulier/structure, documents, **parcours invité sans compte**) ·
M7 paiements (`PaymentProvider`, sandbox, webhook HMAC) · M8 billets QR (PNG + PDF) ·
M9 contrôle à l'entrée (scan, **par activité**, **badges/accréditations**) · M10 factures & reçus (PDF) · M11 notifications ·
M12 statistiques & dashboards · M15 durcissement + données de démo.

Détail : [`docs/roadmap.md`](docs/roadmap.md).

## Tests

```bash
cd backend && ./mvnw test    # 31 tests d'intégration — nécessite Docker (Testcontainers PostgreSQL)
cd frontend && npm run build
```
