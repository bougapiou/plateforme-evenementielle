-- ============================================================================
-- V30 — Contrôle d'accès et présence réelle :
--   * billets émis sur place par un agent (inscription au scanner) ;
--   * canal d'un pointage (scan agent / auto-enregistrement / hors ligne) ;
--   * référence client d'un scan hors ligne, pour une synchronisation sans doublon ;
--   * affectation des agents de contrôle à des activités (salles / créneaux) ;
--   * auto-enregistrement par QR affiché à l'entrée d'une salle.
-- ============================================================================

alter table tickets
    add column sur_place             boolean      not null default false,
    add column participant_telephone varchar(30);

alter table checkins
    add column canal      varchar(20) not null default 'SCAN',
    add column client_ref varchar(64);
create unique index uk_checkins_client_ref on checkins(client_ref) where client_ref is not null;
create index ix_checkins_activity on checkins(activity_id, resultat, sens);

create table event_staff_activities (
    event_staff_id uuid not null references event_staff(id) on delete cascade,
    activity_id    uuid not null references event_activities(id) on delete cascade,
    primary key (event_staff_id, activity_id)
);

alter table event_activities
    add column auto_enregistrement boolean     not null default false,
    add column auto_jeton          varchar(40);
create unique index uk_event_activities_auto_jeton on event_activities(auto_jeton) where auto_jeton is not null;
