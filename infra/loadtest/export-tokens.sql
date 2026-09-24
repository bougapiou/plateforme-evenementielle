-- Exporte les jetons QR des billets de l'événement de test (un par ligne) dans tokens.csv,
-- pour le test de scan (k6-scan-entree.js). Lecture seule.
--
--   psql -h <hote-postgres> -U <utilisateur> -d <base> -f export-tokens.sql
--
-- Le fichier tokens.csv est créé dans le dossier COURANT de la machine qui lance psql :
-- le placer à côté de k6-scan-entree.js.
-- Prérequis : avoir d'abord généré des billets avec k6-billet-gratuit.js (événement « ZZ-TEST-CHARGE… »).

\copy (select q.token from qr_codes q join tickets t on t.id = q.ticket_id join events e on e.id = t.event_id where e.nom like 'ZZ-TEST-CHARGE%' order by q.created_at) to 'tokens.csv' with (format csv)
