-- ============================================================================
-- V7 — Paiements : enregistrement des paiements, référence au provider,
--      idempotence des webhooks.
-- ============================================================================

create table payments (
    id                   uuid           primary key,
    version              bigint         not null default 0,
    created_at           timestamptz    not null,
    updated_at           timestamptz    not null,
    reference            varchar(40)    not null,
    transaction_ref      varchar(100),
    provider_ref         varchar(100),
    provider             varchar(40)    not null,
    moyen                varchar(30)    not null,
    target_type          varchar(30)    not null,
    target_id            uuid           not null,
    ticket_order_id      uuid           references ticket_orders(id),
    stand_reservation_id uuid           references stand_reservations(id),
    registration_id      uuid           references registrations(id),
    user_id              uuid           not null references users(id),
    event_id             uuid           references events(id),
    montant              numeric(14, 2) not null,
    devise               varchar(3)     not null default 'XOF',
    statut               varchar(20)    not null,
    payment_url          varchar(1000),
    echec_motif          varchar(500),
    paid_at              timestamptz,
    constraint uk_payments_reference unique (reference)
);
create index ix_payments_target on payments(target_type, target_id);
create index ix_payments_user on payments(user_id);
create index ix_payments_statut on payments(statut);

-- Idempotence des webhooks : une transaction provider ne peut être traitée qu'une fois
create unique index uk_payments_provider_txn on payments(provider, transaction_ref)
    where transaction_ref is not null;
