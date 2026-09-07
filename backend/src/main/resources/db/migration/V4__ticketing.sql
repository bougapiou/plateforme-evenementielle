-- ============================================================================
-- V4 — Billetterie : catégories de tickets (quotas, portée événement/activité),
--      commandes et billets.
-- ============================================================================

create table event_tickets (
    id                    uuid         primary key,
    version               bigint       not null default 0,
    created_at            timestamptz  not null,
    updated_at            timestamptz  not null,
    event_id              uuid         not null references events(id) on delete cascade,
    nom                   varchar(120) not null,
    description           varchar(1000),
    prix_montant          numeric(14, 2) not null default 0,
    devise                varchar(3)   not null default 'XOF',
    portee                varchar(20)  not null default 'EVENEMENT',
    quantite_totale       int          not null,
    quantite_vendue       int          not null default 0,
    quantite_reservee     int          not null default 0,
    limite_par_utilisateur int         not null default 10,
    vente_debut           timestamptz,
    vente_fin             timestamptz,
    actif                 boolean      not null default true,
    ordre                 int          not null default 0,
    constraint ck_event_tickets_qty check (quantite_totale >= 0
        and quantite_vendue >= 0 and quantite_reservee >= 0)
);
create index ix_event_tickets_event on event_tickets(event_id);

-- N–N : un ticket de portée ACTIVITE donne accès à une ou plusieurs activités
create table event_ticket_activities (
    event_ticket_id uuid not null references event_tickets(id) on delete cascade,
    activity_id     uuid not null references event_activities(id) on delete cascade,
    primary key (event_ticket_id, activity_id)
);

create table ticket_orders (
    id             uuid           primary key,
    version        bigint         not null default 0,
    created_at     timestamptz    not null,
    updated_at     timestamptz    not null,
    reference      varchar(40)    not null,
    event_id       uuid           not null references events(id),
    user_id        uuid           not null references users(id),
    structure_id   uuid           references structures(id),
    montant_total  numeric(14, 2) not null default 0,
    devise         varchar(3)     not null default 'XOF',
    statut         varchar(20)    not null,
    acheteur_nom   varchar(200),
    acheteur_email varchar(180),
    expire_le      timestamptz,
    paye_le        timestamptz,
    constraint uk_ticket_orders_reference unique (reference)
);
create index ix_ticket_orders_event on ticket_orders(event_id);
create index ix_ticket_orders_user on ticket_orders(user_id);
create index ix_ticket_orders_statut on ticket_orders(statut);

create table ticket_order_lines (
    id              uuid           primary key,
    version         bigint         not null default 0,
    created_at      timestamptz    not null,
    updated_at      timestamptz    not null,
    order_id        uuid           not null references ticket_orders(id) on delete cascade,
    event_ticket_id uuid           not null references event_tickets(id),
    quantite        int            not null,
    prix_unitaire   numeric(14, 2) not null,
    constraint ck_ticket_order_lines_qty check (quantite > 0)
);
create index ix_ticket_order_lines_order on ticket_order_lines(order_id);

create table tickets (
    id              uuid         primary key,
    version         bigint       not null default 0,
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null,
    order_id        uuid         not null references ticket_orders(id) on delete cascade,
    event_ticket_id uuid         not null references event_tickets(id),
    event_id        uuid         not null references events(id),
    numero          varchar(40)  not null,
    participant_nom varchar(200),
    participant_email varchar(180),
    statut          varchar(20)  not null,
    constraint uk_tickets_numero unique (numero)
);
create index ix_tickets_order on tickets(order_id);
create index ix_tickets_event on tickets(event_id);
