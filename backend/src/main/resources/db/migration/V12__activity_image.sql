-- ============================================================================
-- V12 — Image (visuel) optionnelle par activité du programme.
-- ============================================================================

alter table event_activities
    add column image_url varchar(500);
