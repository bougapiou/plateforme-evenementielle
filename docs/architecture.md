# Architecture

## Vue d'ensemble

```
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│  Angular Web │   │ Flutter App  │   │  Scanner QR  │
└──────┬───────┘   └──────┬───────┘   └──────┬───────┘
       │  HTTPS / JWT     │                  │
       └────────────┬─────┴──────────────────┘
                    ▼
        ┌───────────────────────────┐
        │   API REST Spring Boot    │
        │  (bf.evenements.plateforme)│
        │  package-by-feature        │
        └───┬───────────┬───────┬────┘
            │           │       │
       PostgreSQL     MinIO   SMTP (Mailpit)
       (Flyway)      (fichiers) (emails)
                    ▲
        Provider de paiement (sandbox → FasoArzeka)
              via webhook signé HMAC
```

## Principes

- **Package-by-feature** : chaque module (`auth`, `event`, `ticket`, …) contient
  ses `Controller` / `Service` / `Repository` / `Entity` / `dto`.
- **API REST** stateless, sécurisée par JWT (access court + refresh rotatif).
- **RBAC** : rôles → permissions ; `@PreAuthorize("hasAuthority('…')")` +
  filtrage par propriété dans les services (un organisateur ne voit que ses
  événements ; un utilisateur que ses données).
- **Migrations Flyway** versionnées ; `spring.jpa.hibernate.ddl-auto=validate`.
- **Aucune inscription / réservation validée avant confirmation du paiement**
  (webhook).
- **Quotas** recalculés atomiquement (verrou pessimiste) à chaque
  inscription / réservation / paiement / annulation.
- **Journalisation** (`audit_logs`) des actions sensibles.
- **Devise** : type `Money` (montant + `Currency`), FCFA par défaut.

## Couches transverses (`common/`)

| Package              | Rôle                                                   |
|----------------------|--------------------------------------------------------|
| `common.config`      | `AppProperties`, OpenAPI, WebMvc, CORS                  |
| `common.security`    | JWT, filtre d'authentification, `@CurrentUser`, RBAC    |
| `common.exception`   | `ApiException` + `GlobalExceptionHandler` → `ApiError`  |
| `common.web`         | `PageResponse`, `ApiError`, utilitaires HTTP            |
| `common.domain`      | `BaseEntity` (UUID, versions, timestamps)               |

## Modules métier

`user`, `rbac`, `structure`, `organizer`, `event`, `ticket`, `stand`,
`registration`, `payment`, `invoice`, `qrcode`, `checkin`, `notification`,
`document`, `stats`, `audit`.
