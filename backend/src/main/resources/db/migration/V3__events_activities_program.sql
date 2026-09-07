-- ============================================================================
-- V3 — Catégories, événements (workflow), activités (programme), intervenants,
--      partenaires.
-- ============================================================================

create table event_categories (
    id          uuid         primary key,
    version     bigint       not null default 0,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    nom         varchar(120) not null,
    slug        varchar(140) not null,
    description varchar(500),
    icone       varchar(60),
    actif       boolean      not null default true,
    ordre       int          not null default 0,
    constraint uk_event_categories_slug unique (slug)
);

create table events (
    id                       uuid         primary key,
    version                  bigint       not null default 0,
    created_at               timestamptz  not null,
    updated_at               timestamptz  not null,
    organizer_id             uuid         not null references organizers(id),
    category_id              uuid         references event_categories(id),
    nom                      varchar(200) not null,
    sigle                    varchar(40),
    slug                     varchar(220) not null,
    description_courte       varchar(500),
    description_detaillee    text,
    logo_url                 varchar(500),
    cover_url                varchar(500),
    date_debut               timestamptz  not null,
    date_fin                 timestamptz  not null,
    lieu                     varchar(200),
    adresse                  varchar(255),
    ville                    varchar(120),
    pays                     varchar(120) not null default 'Burkina Faso',
    latitude                 double precision,
    longitude                double precision,
    capacite_max             int,
    contact_email            varchar(180),
    contact_telephone        varchar(30),
    site_web                 varchar(255),
    conditions_participation text,
    has_activities           boolean      not null default false,
    stands_actifs            boolean      not null default false,
    inscription_debut        timestamptz,
    inscription_fin          timestamptz,
    reservation_debut        timestamptz,
    reservation_fin          timestamptz,
    statut                   varchar(30)  not null,
    motif_refus              varchar(1000),
    soumis_le                timestamptz,
    valide_le                timestamptz,
    valide_par               uuid,
    publie_le                timestamptz,
    constraint uk_events_slug unique (slug)
);
create index ix_events_organizer on events(organizer_id);
create index ix_events_statut on events(statut);
create index ix_events_category on events(category_id);
create index ix_events_date_debut on events(date_debut);
create index ix_events_ville on events(ville);

create table speakers (
    id           uuid         primary key,
    version      bigint       not null default 0,
    created_at   timestamptz  not null,
    updated_at   timestamptz  not null,
    event_id     uuid         not null references events(id) on delete cascade,
    nom          varchar(150) not null,
    titre        varchar(150),
    organisation varchar(150),
    bio          text,
    photo_url    varchar(500),
    ordre        int          not null default 0
);
create index ix_speakers_event on speakers(event_id);

create table event_activities (
    id            uuid         primary key,
    version       bigint       not null default 0,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    event_id      uuid         not null references events(id) on delete cascade,
    titre         varchar(200) not null,
    description   text,
    type_activite varchar(30),
    date_debut    timestamptz  not null,
    date_fin      timestamptz,
    salle         varchar(120),
    lieu          varchar(200),
    intervenant   varchar(255),
    moderateur    varchar(255),
    speaker_id    uuid         references speakers(id) on delete set null,
    capacite      int,
    ordre         int          not null default 0
);
create index ix_event_activities_event on event_activities(event_id);

create table partners (
    id         uuid         primary key,
    version    bigint       not null default 0,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    event_id   uuid         not null references events(id) on delete cascade,
    nom        varchar(150) not null,
    logo_url   varchar(500),
    site_web   varchar(255),
    niveau     varchar(30),
    ordre      int          not null default 0
);
create index ix_partners_event on partners(event_id);
