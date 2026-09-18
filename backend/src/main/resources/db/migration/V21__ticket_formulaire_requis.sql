-- Free ticket categories can opt out of collecting the visitor's identity:
-- when false, claiming the ticket needs only a phone number (or none at all
-- if the visitor already has a session), no name/e-mail form. Ignored by the
-- application for paid categories (a payment always needs an identity).
alter table event_tickets add column formulaire_requis boolean not null default true;
