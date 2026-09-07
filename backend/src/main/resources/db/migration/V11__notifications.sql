-- ============================================================================
-- V11 — Notifications (in-app + email ; canaux SMS/WhatsApp prévus).
-- ============================================================================

create table notifications (
    id         uuid         primary key,
    version    bigint       not null default 0,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    user_id    uuid         not null references users(id) on delete cascade,
    type       varchar(50)  not null,
    canal      varchar(20)  not null,
    titre      varchar(200) not null,
    contenu    varchar(2000) not null,
    lien       varchar(500),
    lu         boolean      not null default false,
    lu_le      timestamptz,
    envoye_le  timestamptz,
    statut     varchar(20)  not null
);
create index ix_notifications_user on notifications(user_id);
create index ix_notifications_user_lu on notifications(user_id, lu);
