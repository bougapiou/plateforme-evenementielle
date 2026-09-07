# API REST

Base : `/api` · Auth : `Authorization: Bearer <accessToken>` · Doc interactive :
`/swagger-ui.html`.

Réponses d'erreur normalisées (`ApiError`) :

```json
{
  "timestamp": "2027-01-10T09:00:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "EMAIL_ALREADY_USED",
  "message": "Cet e-mail est deja utilise.",
  "path": "/api/auth/register",
  "fieldErrors": null
}
```

Pagination : paramètres `page`, `size`, `sort` ; enveloppe `PageResponse`
(`content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`).

## Disponible (M1)

| Méthode | Endpoint | Accès |
|--------|----------|-------|
| POST | `/api/auth/register` | public |
| POST | `/api/auth/login` | public |
| POST | `/api/auth/refresh` | public (refresh token) |
| POST | `/api/auth/logout` | public (refresh token) |
| GET | `/api/users/me` | authentifié |
| PATCH | `/api/users/me` | authentifié |
| POST | `/api/users/me/password` | authentifié |
| GET | `/api/users` | `USER_READ` |
| GET | `/api/users/{id}` | `USER_READ` |
| PATCH | `/api/users/{id}/status` | `USER_MANAGE` |
| PUT | `/api/users/{id}/roles` | `ROLE_MANAGE` |
| GET | `/api/roles` · `/api/permissions` | `ROLE_MANAGE` |
| POST | `/api/roles` | `ROLE_MANAGE` |
| PUT | `/api/roles/{id}/permissions` | `ROLE_MANAGE` |
| GET | `/api/audit-logs` | `AUDIT_READ` |
| GET | `/actuator/health` | public |

### Disponible (M2 — structures & organisateurs)

| Méthode | Endpoint | Accès |
|--------|----------|-------|
| POST | `/api/structures` | authentifié (créateur = propriétaire) |
| GET | `/api/structures/mine` | authentifié |
| GET / PUT | `/api/structures/{id}` | membre / admin de la structure ou `STRUCTURE_MANAGE` |
| GET | `/api/structures` | `STRUCTURE_READ` |
| PATCH | `/api/structures/{id}/status` | `STRUCTURE_MANAGE` |
| GET / POST | `/api/structures/{id}/members` | membre (lecture) / admin structure (ajout) |
| DELETE | `/api/structures/{id}/members/{userId}` | admin structure |
| POST | `/api/organizers/apply` | authentifié |
| GET / PUT | `/api/organizers/me` | authentifié |
| GET | `/api/organizers` · `/api/organizers/{id}` | `ORGANIZER_MANAGE` |
| POST | `/api/organizers/{id}/approve` · `/suspend` | `ORGANIZER_MANAGE` |

## Prévu (modules suivants)

```
Événements   GET/POST /api/events · GET /api/events/{id} · PUT /api/events/{id}
             DELETE /api/events/{id} · POST /api/events/{id}/submit|validate|publish
Tickets      GET/POST /api/events/{id}/tickets · POST /api/tickets/purchase
Stands       GET/POST /api/events/{id}/stands · POST /api/stand-reservations
Inscriptions POST /api/events/{id}/registrations · GET /api/registrations/my
Paiements    POST /api/payments · GET /api/payments/{id} · POST /api/payments/webhook
Contrôle     POST /api/checkins/scan
Public       GET /api/public/events (recherche + filtres) · GET /api/public/events/{slug}
Stats        GET /api/stats/overview · GET /api/stats/events/{id}
```
