-- ============================================================================
-- V26 — Pointage des visiteurs : module de comptage de passages totalement
--       indépendant des événements (pas d'event_id). Un "stand" ici est un
--       simple point de comptage nommé (ex. un ministère), pas un stand
--       réservable du module billetterie/stands existant.
-- ============================================================================

create table pointage_stands (
    id         uuid         primary key,
    version    bigint       not null default 0,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    nom        varchar(200) not null,
    ordre      integer      not null default 0
);

create table pointage_passages (
    id         uuid         primary key,
    version    bigint       not null default 0,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    stand_id   uuid         not null references pointage_stands(id) on delete cascade,
    nom        varchar(120),
    prenom     varchar(120),
    telephone  varchar(30)
);
create index ix_pointage_passages_stand on pointage_passages(stand_id);
