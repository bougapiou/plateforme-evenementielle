-- ============================================================================
-- V24 — Fréquentation des stands : un visiteur signale son passage à un stand
--       depuis une page publique propre à ce stand. Identité facultative.
-- ============================================================================

create table visites_stand (
    id         uuid         primary key,
    version    bigint       not null default 0,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    event_id   uuid         not null references events(id) on delete cascade,
    stand_id   uuid         not null references stands(id) on delete cascade,
    nom        varchar(120),
    prenom     varchar(120),
    telephone  varchar(30)
);
create index ix_visites_stand_event on visites_stand(event_id);
create index ix_visites_stand_stand on visites_stand(stand_id);
