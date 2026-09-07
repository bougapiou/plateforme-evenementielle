-- ============================================================================
-- V9 — Contrôle à l'entrée : personnel de contrôle par événement et journal des
--      scans.
-- ============================================================================

create table event_staff (
    id         uuid        primary key,
    version    bigint      not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    event_id   uuid        not null references events(id) on delete cascade,
    user_id    uuid        not null references users(id) on delete cascade,
    ajoute_par uuid        references users(id),
    constraint uk_event_staff unique (event_id, user_id)
);
create index ix_event_staff_user on event_staff(user_id);

create table checkins (
    id         uuid        primary key,
    version    bigint      not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    qr_code_id uuid        references qr_codes(id),
    ticket_id  uuid        references tickets(id),
    event_id   uuid        not null references events(id),
    scanned_by uuid        references users(id),
    resultat   varchar(20) not null,
    scanned_at timestamptz not null,
    detail     varchar(255)
);
create index ix_checkins_ticket on checkins(ticket_id);
create index ix_checkins_event on checkins(event_id);
create index ix_checkins_scanned_at on checkins(scanned_at);
