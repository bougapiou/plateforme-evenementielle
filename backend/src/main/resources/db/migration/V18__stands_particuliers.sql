-- ============================================================================
-- V18 — Réservation de stand ouverte aux particuliers (opt-in par événement).
--      Par défaut, un stand se réserve toujours au nom d'une structure vérifiée.
-- ============================================================================

alter table events
    add column stands_particuliers boolean not null default false;
