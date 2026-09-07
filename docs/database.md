# Base de données

PostgreSQL. Schéma géré par **Flyway** (`backend/src/main/resources/db/migration`).
`spring.jpa.hibernate.ddl-auto=validate` : Hibernate ne modifie jamais le schéma.

Convention commune (`BaseEntity`) : `id uuid` (PK), `version bigint`,
`created_at timestamptz`, `updated_at timestamptz`.

## V1 — Auth / RBAC / Audit (livré)

```
permissions(id, name*, description)
roles(id, name*, description, system_role)
role_permissions(role_id → roles, permission_id → permissions)          [PK composite]
users(id, email*, password_hash, first_name, last_name, phone,
      type, status, last_login_at)
user_roles(user_id → users, role_id → roles)                            [PK composite]
refresh_tokens(id, user_id → users, token_hash*, expires_at, revoked)
audit_logs(id, actor_id → users NULL, actor_email, action, entity_type,
           entity_id, ip_address, details)
```
`*` = contrainte d'unicité.

`type` ∈ {PARTICULIER, STRUCTURE, ORGANISATEUR, PERSONNEL, ADMIN}
`status` ∈ {ACTIF, EN_ATTENTE, DESACTIVE}

## Schéma cible (modules à venir)

```
structures(id, raison_sociale, rccm, ifu, secteur, adresse, ville, pays,
           telephone, email, statut) ── 1─n ── structure_members(user_id, role)
organizers(id, user_id → users, structure_id → structures NULL, statut)

event_categories(id, nom*, slug*, description, icone)
events(id, organizer_id → organizers, category_id → event_categories,
       nom, sigle, description_courte, description, logo_url, cover_url,
       date_debut, date_fin, lieu, adresse, ville, pays, latitude, longitude,
       capacite_max, statut, stands_actifs, periode_inscription_*,
       contact_email, contact_telephone, site_web)
event_programs(id, event_id, date, heure_debut, heure_fin, activite,
               description, salle, intervenant, moderateur)
speakers(id, event_id, nom, titre, organisation, bio, photo_url)
partners(id, event_id, nom, logo_url, site_web, niveau)

event_tickets(id, event_id, nom, description, prix_montant, prix_devise,
              quantite_totale, quantite_vendue, vente_debut, vente_fin,
              limite_par_utilisateur)
ticket_orders(id, event_id, user_id, reference*, montant_total, statut)
tickets(id, order_id → ticket_orders, event_ticket_id → event_tickets,
        numero*, participant_nom, statut)

stand_types(id, event_id, nom, description, dimensions, prix_montant,
            prix_devise, quantite_totale, quantite_reservee, equipements,
            conditions)
stands(id, stand_type_id → stand_types, numero*, position_x, position_y,
       statut)
stand_reservations(id, event_id, stand_id → stands, structure_id → structures,
                   reference*, numero_reservation*, montant, statut,
                   hold_expires_at, date_limite_paiement)

registrations(id, event_id, user_id NULL, structure_id NULL, type,
              statut, reference*)
participants(id, registration_id → registrations, nom, prenom, email,
             telephone, fonction)

payments(id, reference*, transaction_ref, user_id, event_id,
         ticket_order_id NULL, stand_reservation_id NULL, registration_id NULL,
         montant, devise, moyen, statut, provider, paid_at)
invoices(id, payment_id → payments, numero*, pdf_url, type)

qr_codes(id, ticket_id → tickets, token*, statut)
checkins(id, qr_code_id → qr_codes, event_id, scanned_by → users,
         resultat, scanned_at)

notifications(id, user_id → users, type, canal, titre, contenu, lu, sent_at)
documents(id, owner_type, owner_id, nom, url, mime, taille)
```

Les compteurs `quantite_vendue` / `quantite_reservee` sont mis à jour sous
verrou pessimiste à chaque inscription / réservation / paiement / annulation.
