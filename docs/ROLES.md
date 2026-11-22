# Rôles et permissions de la PNE

Source unique : `backend/…/rbac/Permissions.java` (catalogue) et
`bootstrap/RbacSeeder.java` (rôles système, ré-appliqués à chaque démarrage).
Les rôles personnalisés se créent dans l'administration (`ROLE_MANAGE`).
Règle d'or : **un compte = une personne** (comptes nominatifs, double
authentification obligatoire pour `SUPER_ADMIN` et `ORGANISATEUR`).

| Rôle | Pour qui | Permissions |
|---|---|---|
| `SUPER_ADMIN` | Administrateur technique du MTDPCE | Toutes |
| `VALIDEUR` | Agent qui valide / refuse les événements soumis | `EVENT_READ`, `EVENT_VALIDATE`, `STRUCTURE_READ`, `STATS_GLOBAL_READ` |
| `ORGANISATEUR` | Comité d'organisation d'un événement (ses événements) | `EVENT_READ/CREATE/UPDATE/DELETE/PUBLISH`, `TICKET_MANAGE`, `STAND_MANAGE`, `REGISTRATION_MANAGE`, `PAYMENT_READ`, `CHECKIN_SCAN`, `STATS_OWN_READ`, `STRUCTURE_READ` |
| `GESTIONNAIRE_PROGRAMME` | Délégué par un organisateur sur **un** événement (équipe → « Gestionnaire de programme ») : programme, intervenants, partenaires, billets, statistiques de cet événement | `EVENT_READ`, `EVENT_UPDATE`, `TICKET_MANAGE`, `STATS_OWN_READ`, `CHECKIN_SCAN` |
| `PERSONNEL_CONTROLE` | Agent de contrôle, ajouté à l'équipe d'un événement ; peut être **restreint à des salles / créneaux** | `EVENT_READ`, `CHECKIN_SCAN` |
| `LECTEUR_STATISTIQUES` | Cabinet, direction des études : lecture des statistiques de tous les événements | `EVENT_READ`, `STATS_GLOBAL_READ` |
| `STRUCTURE` | Entreprise / institution (réservation de stands) | `EVENT_READ`, `TICKET_PURCHASE`, `STAND_RESERVE`, `REGISTRATION_CREATE`, `STRUCTURE_MANAGE` |
| `PARTICIPANT` | Particulier (et sessions invitées) | `EVENT_READ`, `TICKET_PURCHASE`, `REGISTRATION_CREATE` |

## Délégation par événement

L'organisateur gère son équipe dans l'onglet **Contrôle** de l'événement :

- **Agent de contrôle** : accède au scanner ; si des activités sont cochées, il
  ne voit et ne peut scanner que celles-ci (le serveur refuse les autres salles).
- **Gestionnaire de programme** : voit l'événement dans « Mes événements » et
  en gère le programme, les billets et les statistiques, sans pouvoir le
  supprimer ni le publier.

## Traçabilité

Chaque action sensible (connexion, échec, verrouillage, validation,
publication, suppression, export de données, émission sur place, anonymisation)
est inscrite au journal d'audit avec auteur, date et adresse IP — consultable
par `AUDIT_READ` (*Administration → Journal d'audit*), non modifiable en base.

## Documentation de l'API

Swagger UI : `/swagger-ui.html` (description OpenAPI : `/v3/api-docs`).
