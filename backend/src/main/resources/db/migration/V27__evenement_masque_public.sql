-- ============================================================================
-- V27 — Événements masqués du site public (tests, doublons) : l'admin peut
--       retirer un événement de la vitrine sans changer son statut.
-- ============================================================================

alter table events
    add column masque_public boolean not null default false;
