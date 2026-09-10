-- ============================================================================
-- V16 — Badges / accréditations : l'organisateur (ou l'admin) délivre des
--      badges nominatifs (conférencier, exposant, modérateur, MC, panéliste,
--      compétiteur, presse, staff…) pour une activité précise OU tout
--      l'événement. QR + PDF, scannables à l'entrée.
-- ============================================================================

create table accreditations (
    id             uuid         primary key,
    version        bigint       not null default 0,
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null,
    event_id       uuid         not null references events(id) on delete cascade,
    activity_id    uuid         references event_activities(id) on delete cascade,
    numero         varchar(40)  not null,
    personne_nom   varchar(200) not null,
    personne_email varchar(180),
    organisation   varchar(200),
    fonction       varchar(30)  not null,
    fonction_libre varchar(120),
    photo_url      varchar(500),
    qr_token       varchar(80)  not null,
    statut         varchar(20)  not null,
    constraint uk_accreditations_numero   unique (numero),
    constraint uk_accreditations_qr_token unique (qr_token)
);
create index ix_accreditations_event on accreditations(event_id);
create index ix_accreditations_activity on accreditations(activity_id);

-- Un contrôle peut désormais concerner une accréditation plutôt qu'un billet.
alter table checkins
    add column accreditation_id uuid;
