-- ============================================================================
-- Test de charge — ÉTAPE 1 : APERÇU (lecture seule, ne modifie RIEN).
-- À lancer AVANT cleanup-2-suppression.sql pour vérifier ce qui sera supprimé.
--
--   psql -h <hote-postgres> -U <utilisateur> -d <base> -f cleanup-1-apercu.sql
--
-- Marqueurs des données de test (à adapter ici ET dans cleanup-2 si besoin) :
--   * comptes invités dont le téléphone commence par '+226 99999'
--   * événement(s) dont le nom commence par 'ZZ-TEST-CHARGE'
-- ============================================================================

\echo '--- Comptes invités de test ---'
select count(*) as comptes_test
from users
where guest = true
  and phone like '+226 99999%'
  and email like 'tel-%@guest.plateforme.local';

\echo '--- Événement(s) de test ---'
select id, nom, statut from events where nom like 'ZZ-TEST-CHARGE%';

\echo '--- Données rattachées aux comptes de test ---'
with t as (
  select id from users
  where guest = true and phone like '+226 99999%' and email like 'tel-%@guest.plateforme.local'
)
select
  (select count(*) from ticket_orders   where user_id in (select id from t)) as commandes,
  (select count(*) from tickets tk join ticket_orders o on o.id = tk.order_id
     where o.user_id in (select id from t))                                  as billets,
  (select count(*) from registrations   where user_id in (select id from t)) as inscriptions,
  (select count(*) from payments        where user_id in (select id from t)) as paiements,
  (select count(*) from invoices        where user_id in (select id from t)) as factures,
  (select count(*) from stand_reservations where user_id in (select id from t)) as stands;

\echo '--- ATTENTION : lignes de VRAIS utilisateurs sur l''événement de test (doit être 0) ---'
with t as (
  select id from users
  where guest = true and phone like '+226 99999%' and email like 'tel-%@guest.plateforme.local'
), e as (select id from events where nom like 'ZZ-TEST-CHARGE%')
select
  (select count(*) from ticket_orders where event_id in (select id from e)
     and user_id not in (select id from t)) as commandes_autres,
  (select count(*) from registrations where event_id in (select id from e)
     and user_id not in (select id from t)) as inscriptions_autres,
  (select count(*) from stand_reservations where event_id in (select id from e)
     and user_id not in (select id from t)) as stands_autres;

\echo '--- Comptes qui NE sont PAS de test mais ressemblent (doit être vide) ---'
select id, email, phone, guest from users
where phone like '+226 99999%'
  and not (guest = true and email like 'tel-%@guest.plateforme.local');

\echo '--- Scans et capteurs de l''événement de test (supprimés avec lui) ---'
select
  (select count(*) from checkins where event_id in (select id from events where nom like 'ZZ-TEST-CHARGE%')) as scans,
  (select count(*) from capteurs where event_id in (select id from events where nom like 'ZZ-TEST-CHARGE%')) as capteurs,
  (select coalesce(sum(nombre), 0) from passages_capteur
     where event_id in (select id from events where nom like 'ZZ-TEST-CHARGE%')) as passages_laser;
