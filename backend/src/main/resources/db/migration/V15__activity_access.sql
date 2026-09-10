-- ============================================================================
-- V15 — Mode d'accès d'une activité (sans billet / gratuit / payant) et
--      contrôle d'entrée par activité.
-- ============================================================================

alter table event_activities
    add column acces varchar(20) not null default 'SANS_BILLET';

-- Catégorie de billet gratuite gérée automatiquement quand acces = 'GRATUIT'.
-- Référence "souple" (pas de FK), comme les autres pointeurs du module contrôle.
alter table event_activities
    add column free_ticket_id uuid;

-- Un contrôle peut désormais viser une activité précise (null = entrée générale).
alter table checkins
    add column activity_id uuid;

create index ix_checkins_ticket_activity on checkins(ticket_id, activity_id);
