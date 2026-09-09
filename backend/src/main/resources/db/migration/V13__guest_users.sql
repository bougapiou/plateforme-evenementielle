-- ============================================================================
-- V13 — Comptes « invité » : un particulier peut prendre des billets ou
--      s'inscrire sans créer de compte. Le compte reste réclamable (définition
--      d'un mot de passe) pour retrouver ses billets ensuite.
-- ============================================================================

alter table users
    add column guest boolean not null default false;
