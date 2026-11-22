-- ============================================================================
-- V29 — Sécurité des comptes : verrouillage temporaire après échecs de
--       connexion et double authentification (TOTP).
-- ============================================================================

alter table users
    add column echecs_connexion  int          not null default 0,
    add column verrouille_jusqu  timestamptz,
    add column totp_secret       varchar(64),
    add column totp_actif        boolean      not null default false;
