-- ============================================================================
-- V23 — Capteurs de comptage (laser Arduino / Raspberry) : comptage anonyme
--       des passages à l'entrée et à la sortie d'un événement.
-- ============================================================================

create table capteurs (
    id                uuid         primary key,
    version           bigint       not null default 0,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    event_id          uuid         not null references events(id) on delete cascade,
    nom               varchar(120) not null,
    cle_hash          varchar(64)  not null unique,
    cle_prefixe       varchar(12)  not null,
    actif             boolean      not null default true,
    derniere_activite timestamptz
);
create index ix_capteurs_event on capteurs(event_id);

create table passages_capteur (
    id         uuid        primary key,
    version    bigint      not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    event_id   uuid        not null references events(id) on delete cascade,
    capteur_id uuid        not null references capteurs(id) on delete cascade,
    sens       varchar(10) not null,
    nombre     integer     not null check (nombre > 0),
    passe_le   timestamptz not null
);
create index ix_passages_event_sens on passages_capteur(event_id, sens);
create index ix_passages_capteur on passages_capteur(capteur_id, sens);
