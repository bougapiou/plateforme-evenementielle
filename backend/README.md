# Backend — API Plateforme des Événements

Spring Boot 3 · Java 21 · PostgreSQL · Flyway · Spring Security (JWT).

## Lancer

```bash
# Depuis infra/ : docker compose up -d   (PostgreSQL, Mailpit, MinIO)
./mvnw spring-boot:run
```

Variables : voir `../.env.example`. Par défaut, l'API se connecte à
`jdbc:postgresql://localhost:5433/plateforme` (user/pass `plateforme`) — le
conteneur Docker publie PostgreSQL sur le port **5433** pour éviter tout conflit
avec un PostgreSQL installé localement.

- API : http://localhost:8080
- Swagger : http://localhost:8080/swagger-ui.html
- Health : http://localhost:8080/actuator/health

## Tests

```bash
./mvnw test        # Testcontainers → nécessite Docker en cours d'exécution
```

## Architecture (package-by-feature)

`bf.evenements.plateforme`

| Package | Contenu |
|---------|---------|
| `common.domain` | `BaseEntity` (UUID, version, timestamps) |
| `common.web` | `ApiError`, `PageResponse`, `HttpUtils` |
| `common.exception` | `ApiException` + sous-classes, `GlobalExceptionHandler` |
| `common.config` | `AppProperties`, OpenAPI, WebMvc |
| `common.security` | JWT (`JwtService`, filtre), RBAC, `@CurrentUser`, `SecurityConfig` |
| `auth` | register / login / refresh / logout, session invité (`/auth/guest`) + revendication de compte (`/auth/complete`), `RefreshToken` |
| `user` | `User`, profils, administration des comptes |
| `rbac` | `Role`, `Permission`, `Permissions` (catalogue) |
| `audit` | `AuditLog`, `AuditService`, consultation |
| `bootstrap` | seeders RBAC + super-admin |

## Conventions

- `XxxController` → `XxxService` (`@Transactional`) → `XxxRepository` → `Xxx` (entity).
- DTO = `record` dans `dto/`, validation Bean Validation sur les requêtes.
- Filtres de liste via `JpaSpecificationExecutor` + `XxxSpecifications`
  (évite les paramètres nuls non typés côté PostgreSQL).
- Toute erreur passe par `GlobalExceptionHandler` → `ApiError`.
- Migrations : `src/main/resources/db/migration/V*__*.sql` ; `ddl-auto=validate`.
