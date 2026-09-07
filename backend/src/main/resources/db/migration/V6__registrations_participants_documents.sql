-- ============================================================================
-- V6 — Inscriptions (particulier / structure), participants, documents.
-- ============================================================================

alter table events add column validation_inscription boolean not null default false;

create table registrations (
    id                 uuid         primary key,
    version            bigint       not null default 0,
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null,
    reference          varchar(40)  not null,
    event_id           uuid         not null references events(id),
    user_id            uuid         not null references users(id),
    structure_id       uuid         references structures(id),
    type               varchar(20)  not null,
    statut             varchar(20)  not null,
    ticket_order_id    uuid         references ticket_orders(id),
    contact_nom        varchar(200),
    contact_email      varchar(180),
    contact_telephone  varchar(30),
    nombre_participants int         not null default 1,
    informations       text,
    motif_refus        varchar(1000),
    confirmee_le       timestamptz,
    constraint uk_registrations_reference unique (reference)
);
create index ix_registrations_event on registrations(event_id);
create index ix_registrations_user on registrations(user_id);
create index ix_registrations_statut on registrations(statut);

create table participants (
    id              uuid         primary key,
    version         bigint       not null default 0,
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null,
    registration_id uuid         not null references registrations(id) on delete cascade,
    nom             varchar(120) not null,
    prenom          varchar(120),
    email           varchar(180),
    telephone       varchar(30),
    fonction        varchar(120)
);
create index ix_participants_registration on participants(registration_id);

create table documents (
    id            uuid         primary key,
    version       bigint       not null default 0,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    owner_type    varchar(40)  not null,
    owner_id      uuid         not null,
    nom           varchar(200) not null,
    type_document varchar(60),
    url           varchar(600) not null,
    mime          varchar(120),
    taille        bigint,
    uploaded_by   uuid
);
create index ix_documents_owner on documents(owner_type, owner_id);
