-- ============================================================================
-- V28 — Programme : thème / piste, format de la session (sur place, en ligne,
--       hybride) et lien de diffusion réservé aux inscrits.
--       Les nouveaux types d'activité (DEMONSTRATION, FORUM, KEYNOTE,
--       EMISSION_MEDIA, EXPOSITION, GALA) tiennent dans type_activite varchar(30).
-- ============================================================================

alter table event_activities
    add column theme          varchar(120),
    add column format         varchar(20) not null default 'SUR_PLACE',
    add column lien_diffusion varchar(500);
