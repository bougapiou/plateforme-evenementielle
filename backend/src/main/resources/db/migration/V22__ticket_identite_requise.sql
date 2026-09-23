-- When a free category's form is required (formulaire_requis = true), the
-- organiser can pick which identity field(s) the visitor must fill:
-- NOM_ET_PRENOM (both, the default), NOM_SEUL or PRENOM_SEUL. Ignored when
-- formulaire_requis is false (phone-only) or for a paid category.
alter table event_tickets
    add column identite_requise varchar(20) not null default 'NOM_ET_PRENOM';

-- PRENOM_SEUL means a participant can be registered with no nom at all —
-- the application still requires at least one of nom/prenom (see
-- RegistrationService), just not always this specific one.
alter table participants alter column nom drop not null;
