-- ============================================================================
-- V10 — Factures et reçus de paiement.
-- ============================================================================

create table invoices (
    id             uuid           primary key,
    version        bigint         not null default 0,
    created_at     timestamptz    not null,
    updated_at     timestamptz    not null,
    numero         varchar(40)    not null,
    type           varchar(20)    not null,
    payment_id     uuid           not null references payments(id),
    user_id        uuid           not null references users(id),
    event_id       uuid           references events(id),
    montant        numeric(14, 2) not null,
    devise         varchar(3)     not null default 'XOF',
    client_nom     varchar(200),
    client_details varchar(500),
    lignes         text,
    emise_le       timestamptz    not null,
    constraint uk_invoices_numero unique (numero)
);
create index ix_invoices_payment on invoices(payment_id);
create index ix_invoices_user on invoices(user_id);
