-- ============================================================================
-- V5 — Stands : types de stands, stands individuels (plan), réservations avec
--      blocage temporaire.
-- ============================================================================

create table stand_types (
    id                uuid           primary key,
    version           bigint         not null default 0,
    created_at        timestamptz    not null,
    updated_at        timestamptz    not null,
    event_id          uuid           not null references events(id) on delete cascade,
    nom               varchar(120)   not null,
    description       varchar(1000),
    dimensions        varchar(60),
    prix_montant      numeric(14, 2) not null default 0,
    devise            varchar(3)     not null default 'XOF',
    quantite_totale   int            not null,
    equipements       varchar(1000),
    conditions        varchar(1000),
    ordre             int            not null default 0,
    constraint ck_stand_types_qty check (quantite_totale >= 0)
);
create index ix_stand_types_event on stand_types(event_id);

create table stands (
    id            uuid        primary key,
    version       bigint      not null default 0,
    created_at    timestamptz not null,
    updated_at    timestamptz not null,
    stand_type_id uuid        not null references stand_types(id) on delete cascade,
    event_id      uuid        not null references events(id) on delete cascade,
    numero        varchar(40) not null,
    position_x    double precision,
    position_y    double precision,
    statut        varchar(20) not null,
    constraint uk_stands_event_numero unique (event_id, numero)
);
create index ix_stands_type on stands(stand_type_id);

create table stand_reservations (
    id                   uuid           primary key,
    version              bigint         not null default 0,
    created_at           timestamptz    not null,
    updated_at           timestamptz    not null,
    reference            varchar(40)    not null,
    numero_reservation   varchar(40)    not null,
    event_id             uuid           not null references events(id),
    stand_id             uuid           not null references stands(id),
    stand_type_id        uuid           not null references stand_types(id),
    structure_id         uuid           references structures(id),
    user_id              uuid           not null references users(id),
    montant              numeric(14, 2) not null default 0,
    devise               varchar(3)     not null default 'XOF',
    statut               varchar(20)    not null,
    informations         text,
    hold_expire_le       timestamptz,
    date_limite_paiement timestamptz,
    paye_le              timestamptz,
    constraint uk_stand_reservations_reference unique (reference),
    constraint uk_stand_reservations_numero unique (numero_reservation)
);
create index ix_stand_reservations_event on stand_reservations(event_id);
create index ix_stand_reservations_stand on stand_reservations(stand_id);
create index ix_stand_reservations_user on stand_reservations(user_id);
create index ix_stand_reservations_statut on stand_reservations(statut);

-- Un seul stand ne peut avoir qu'une réservation active à la fois
create unique index uk_stand_active_reservation on stand_reservations(stand_id)
    where statut in ('RESERVE_TEMP', 'ATTENTE_PAIEMENT', 'PAYE', 'CONFIRME');
