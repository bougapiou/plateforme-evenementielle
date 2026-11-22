-- ============================================================================
-- V31 — Rôles délégués par événement, journal d'audit non modifiable,
--       conservation limitée des données personnelles, codes SMS (OTP).
-- ============================================================================

-- Fonction d'un membre de l'équipe d'un événement : agent de contrôle
-- (CONTROLE, existant) ou gestionnaire de programme (PROGRAMME).
alter table event_staff
    add column fonction varchar(20) not null default 'CONTROLE';

-- SEC-05 : le journal d'audit n'accepte que des insertions.
create or replace function audit_logs_immuable() returns trigger as $$
begin
    raise exception 'Le journal d''audit n''est pas modifiable';
end;
$$ language plpgsql;

create trigger trg_audit_logs_immuable
    before update or delete on audit_logs
    for each row execute function audit_logs_immuable();


-- SEC-08 : date d'anonymisation des participants d'un événement.
alter table events
    add column donnees_anonymisees_le timestamptz;

-- PART-04 : codes à usage unique envoyés par SMS (« Mes billets » sans mot de passe).
create table otp_codes (
    id          uuid        primary key,
    version     bigint      not null default 0,
    created_at  timestamptz not null,
    updated_at  timestamptz not null,
    telephone   varchar(30) not null,
    code_hash   varchar(100) not null,
    expire_le   timestamptz not null,
    tentatives  int         not null default 0,
    utilise_le  timestamptz
);
create index ix_otp_codes_telephone on otp_codes(telephone, created_at);
