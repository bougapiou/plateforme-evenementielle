-- ============================================================================
-- V17 — Contrôle à la sortie : comptage entrées / sorties / présents /
--      ré-entrées. Opt-in par événement.
-- ============================================================================

alter table events
    add column controle_sortie boolean not null default false;

-- Sens d'un contrôle : ENTREE (défaut) ou SORTIE.
alter table checkins
    add column sens varchar(10) not null default 'ENTREE';

create index ix_checkins_event_sens on checkins(event_id, sens, resultat);
