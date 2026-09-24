-- ============================================================================
-- Test de charge — ÉTAPE 2 : SUPPRESSION DÉFINITIVE des données de test.
-- IRRÉVERSIBLE : faire une sauvegarde (pg_dump) avant, et lancer d'abord
-- cleanup-1-apercu.sql.
--
--   psql -h <hote-postgres> -U <utilisateur> -d <base> -v ON_ERROR_STOP=1 -1 -f cleanup-2-suppression.sql
--
-- L'option -1 exécute TOUT dans UNE transaction : à la moindre erreur (ou si une
-- garde ci-dessous échoue), RIEN n'est supprimé.
--
-- Ne touche qu'à :
--   * comptes invités (guest = true) au téléphone '+226 99999…' ET e-mail « tel-…@guest.plateforme.local »
--   * l'événement dont le nom commence par 'ZZ-TEST-CHARGE'
-- ============================================================================

create temp table t_users on commit drop as
  select id from users
  where guest = true
    and phone like '+226 99999%'
    and email like 'tel-%@guest.plateforme.local';

create temp table t_events on commit drop as
  select id from events where nom like 'ZZ-TEST-CHARGE%';

-- Garde : l'événement de test ne doit contenir que des données de test.
do $$
declare n bigint;
begin
  select count(*) into n from ticket_orders
    where event_id in (select id from t_events) and user_id not in (select id from t_users);
  if n > 0 then raise exception 'Abandon : % commande(s) de vrais utilisateurs sur l''événement de test', n; end if;

  select count(*) into n from registrations
    where event_id in (select id from t_events) and user_id not in (select id from t_users);
  if n > 0 then raise exception 'Abandon : % inscription(s) de vrais utilisateurs sur l''événement de test', n; end if;

  select count(*) into n from stand_reservations
    where event_id in (select id from t_events) and user_id not in (select id from t_users);
  if n > 0 then raise exception 'Abandon : % réservation(s) de stand de vrais utilisateurs sur l''événement de test', n; end if;
end $$;

-- 1) Données des comptes de test (ordre imposé par les clés étrangères)
delete from checkins where ticket_id in (
  select t.id from tickets t join ticket_orders o on o.id = t.order_id
  where o.user_id in (select id from t_users));
delete from invoices  where user_id in (select id from t_users)
   or payment_id in (select id from payments where user_id in (select id from t_users));
delete from payments  where user_id in (select id from t_users);
delete from registrations    where user_id in (select id from t_users);  -- participants en cascade
delete from ticket_orders    where user_id in (select id from t_users);  -- lignes, billets, QR en cascade
delete from stand_reservations where user_id in (select id from t_users);

-- 2) Événement de test : contrôles/paiements restants, puis l'événement (types de billets,
--    activités, capteurs, passages… partent en cascade)
delete from checkins  where event_id in (select id from t_events);
delete from invoices  where event_id in (select id from t_events);
delete from payments  where event_id in (select id from t_events);
delete from events    where id in (select id from t_events);

-- 3) Comptes de test (rôles, jetons, notifications en cascade)
delete from users where id in (select id from t_users);

\echo 'Nettoyage terminé. Vérifiez avec cleanup-1-apercu.sql : tout doit être à 0.'
