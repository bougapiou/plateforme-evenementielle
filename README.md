# Plateforme Nationale de Gestion des Événements

Plateforme de digitalisation des grands événements (SIAO, FESPACO, Semaine du
Numérique, salons, foires, forums, conférences…) : création et administration
d'événements, inscriptions, billetterie électronique, réservation de stands,
paiement en ligne, contrôle d'accès par QR code, tableaux de bord et statistiques.

## Stack

| Composant   | Technologie                                    |
|-------------|------------------------------------------------|
| Backend API | Spring Boot 3 · Java 21 · JPA · PostgreSQL · Flyway |
| Frontend    | Angular · Tailwind CSS                          |
| Mobile      | Flutter                                         |
| Paiement    | Abstraction `PaymentProvider` + provider *sandbox* (FasoArzeka / mobile money à brancher) |
| Devise      | FCFA (XOF) par défaut, multi-devises prévu      |

## Arborescence

```
backend/    API REST (bf.evenements.plateforme)
frontend/   Application Angular
mobile/     Application Flutter
infra/      docker-compose (postgres, mailpit, minio)
docs/       Architecture, base de données, workflows, API
```

## Démarrage rapide (backend)

```bash
# 1. Services d'infrastructure
cd infra && docker compose up -d

# 2. API
cd ../backend && ./mvnw spring-boot:run
```

- API : http://localhost:8080
- Swagger UI : http://localhost:8080/swagger-ui.html
- Santé : http://localhost:8080/actuator/health
- Mailpit (emails) : http://localhost:8025
- MinIO (fichiers) : http://localhost:9001

### Compte super-administrateur par défaut

`admin@plateforme.bf` / `ChangeMe!2026` — **à changer immédiatement** (variables
`SUPER_ADMIN_EMAIL` / `SUPER_ADMIN_PASSWORD`).

## Roadmap (livraison module par module)

Voir [`docs/roadmap.md`](docs/roadmap.md). État actuel : **M0 (fondations)** et
**M1 (authentification & RBAC)**.

## Tests

```bash
cd backend && ./mvnw test        # nécessite Docker (Testcontainers PostgreSQL)
```
