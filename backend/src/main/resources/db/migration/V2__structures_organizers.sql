-- ============================================================================
-- V2 — Structures (comptes professionnels), representants et organisateurs.
-- ============================================================================

create table structures (
    id               uuid         primary key,
    version          bigint       not null default 0,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    raison_sociale   varchar(200) not null,
    sigle            varchar(40),
    type_structure   varchar(30)  not null,
    secteur_activite varchar(120),
    rccm             varchar(60),
    ifu              varchar(60),
    adresse          varchar(255),
    ville            varchar(120),
    pays             varchar(120) not null default 'Burkina Faso',
    telephone        varchar(30),
    email            varchar(180),
    site_web         varchar(255),
    logo_url         varchar(500),
    description      text,
    statut           varchar(20)  not null,
    owner_user_id    uuid         not null references users(id),
    created_by       uuid         references users(id)
);
create index ix_structures_owner on structures(owner_user_id);
create index ix_structures_statut on structures(statut);

create table structure_members (
    id           uuid        primary key,
    version      bigint      not null default 0,
    created_at   timestamptz not null,
    updated_at   timestamptz not null,
    structure_id uuid        not null references structures(id) on delete cascade,
    user_id      uuid        not null references users(id) on delete cascade,
    role_interne varchar(20) not null,
    fonction     varchar(120),
    active       boolean     not null default true,
    constraint uk_structure_member unique (structure_id, user_id)
);
create index ix_structure_members_user on structure_members(user_id);

create table organizers (
    id                uuid         primary key,
    version           bigint       not null default 0,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    user_id           uuid         not null references users(id),
    structure_id      uuid         references structures(id),
    nom_affichage     varchar(200) not null,
    description       text,
    logo_url          varchar(500),
    contact_email     varchar(180),
    contact_telephone varchar(30),
    site_web          varchar(255),
    statut            varchar(20)  not null,
    approuve_par      uuid         references users(id),
    approuve_le       timestamptz,
    constraint uk_organizer_user unique (user_id)
);
create index ix_organizers_statut on organizers(statut);
