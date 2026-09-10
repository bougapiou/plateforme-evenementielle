-- ============================================================================
-- V14 — Réinitialisation de mot de passe : jeton opaque à usage unique, envoyé
--      par e-mail. Seul le hash SHA-256 est stocké (comme refresh_tokens).
-- ============================================================================

create table password_reset_tokens (
    id         uuid         primary key,
    version    bigint       not null default 0,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    user_id    uuid         not null references users(id) on delete cascade,
    token_hash varchar(100) not null,
    expires_at timestamptz  not null,
    used_at    timestamptz,
    constraint uk_password_reset_tokens_hash unique (token_hash)
);
create index ix_password_reset_tokens_user on password_reset_tokens(user_id);
create index ix_password_reset_tokens_expires on password_reset_tokens(expires_at);
