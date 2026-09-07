-- ============================================================================
-- V8 — QR codes des billets électroniques.
-- ============================================================================

create table qr_codes (
    id         uuid        primary key,
    version    bigint      not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    ticket_id  uuid        not null references tickets(id) on delete cascade,
    token      varchar(80) not null,
    statut     varchar(20) not null,
    constraint uk_qr_codes_ticket unique (ticket_id),
    constraint uk_qr_codes_token unique (token)
);
create index ix_qr_codes_token on qr_codes(token);
