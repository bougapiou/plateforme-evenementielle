-- ============================================================================
-- V1 — Fondations : utilisateurs, RBAC (roles / permissions), refresh tokens,
--      journaux d'activite.
-- ============================================================================

create table permissions (
    id          uuid primary key,
    version     bigint      not null default 0,
    created_at  timestamptz not null,
    updated_at  timestamptz not null,
    name        varchar(80) not null,
    description varchar(255),
    constraint uk_permissions_name unique (name)
);

create table roles (
    id          uuid        primary key,
    version     bigint      not null default 0,
    created_at  timestamptz not null,
    updated_at  timestamptz not null,
    name        varchar(60) not null,
    description varchar(255),
    system_role boolean     not null default false,
    constraint uk_roles_name unique (name)
);

create table role_permissions (
    role_id       uuid not null references roles(id) on delete cascade,
    permission_id uuid not null references permissions(id) on delete cascade,
    primary key (role_id, permission_id)
);

create table users (
    id            uuid         primary key,
    version       bigint       not null default 0,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    email         varchar(180) not null,
    password_hash varchar(100) not null,
    first_name    varchar(100) not null,
    last_name     varchar(100) not null,
    phone         varchar(30),
    type          varchar(30)  not null,
    status        varchar(20)  not null,
    last_login_at timestamptz,
    constraint uk_users_email unique (email)
);
create index ix_users_type on users(type);
create index ix_users_status on users(status);

create table user_roles (
    user_id uuid not null references users(id) on delete cascade,
    role_id uuid not null references roles(id) on delete cascade,
    primary key (user_id, role_id)
);

create table refresh_tokens (
    id         uuid         primary key,
    version    bigint       not null default 0,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    user_id    uuid         not null references users(id) on delete cascade,
    token_hash varchar(100) not null,
    expires_at timestamptz  not null,
    revoked    boolean      not null default false,
    constraint uk_refresh_tokens_hash unique (token_hash)
);
create index ix_refresh_tokens_user on refresh_tokens(user_id);
create index ix_refresh_tokens_expires on refresh_tokens(expires_at);

-- audit_logs is an append-only trail: actor_id is a soft reference (no FK) so
-- entries survive user deletion and never block a business transaction.
create table audit_logs (
    id          uuid        primary key,
    version     bigint      not null default 0,
    created_at  timestamptz not null,
    updated_at  timestamptz not null,
    actor_id    uuid,
    actor_email varchar(180),
    action      varchar(80) not null,
    entity_type varchar(80),
    entity_id   varchar(80),
    ip_address  varchar(60),
    details     text
);
create index ix_audit_logs_actor on audit_logs(actor_id);
create index ix_audit_logs_action on audit_logs(action);
create index ix_audit_logs_created on audit_logs(created_at);
