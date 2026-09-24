# Test de charge sur le vrai serveur — procédure

Trois pièces : `k6-billet-gratuit.js` (le test), `cleanup-1-apercu.sql` et `cleanup-2-suppression.sql`
(le nettoyage). Le SQL de nettoyage est vérifié par un test automatique
(`LoadTestCleanupIT`) sur un vrai PostgreSQL avec le vrai schéma : il ne supprime que les données de
test et **refuse de rien supprimer** si de vrais utilisateurs ont pris part à l'événement de test.

Ce qui est repérable comme « donnée de test » :
- comptes invités dont le téléphone commence par **`+226 99999`** (le script les génère ainsi) ;
- l'événement dont le nom commence par **`ZZ-TEST-CHARGE`**.

## Avant (à faire dans l'ordre)

1. **Prévenir l'ANPTIC** de la date et de l'heure (un trafic soudain peut être pris pour une attaque).
   Choisir une heure creuse, sans événement réel en cours.
2. **Sauvegarder la base** (depuis SRV10 ou toute machine avec `pg_dump`) :
   `pg_dump -h 10.185.22.72 -U <utilisateur> -Fc -f avant-test-charge.dump <base>`
3. **Créer l'événement de test** avec un compte organisateur, puis le faire valider par l'admin :
   - nom : **`ZZ-TEST-CHARGE …`** (le préfixe est obligatoire) ; publié, inscriptions ouvertes ;
   - une catégorie de billet **gratuite** : prix 0, quantité **100000**, limite par personne 1,
     case « formulaire » **décochée** (numéro de téléphone seul) ;
   - noter son **slug** (la fin de l'adresse `/evenements/<slug>`).
4. **Desserrer le limiteur de débit** : dans `infra/.env.prod`, mettre `RATE_LIMIT_PER_MINUTE=0`,
   puis relancer le backend : `docker compose --env-file .env.prod -f docker-compose.prod.yml up -d`.
   ⚠ À REMETTRE À LA VALEUR D'ORIGINE (20) après le test.
5. Installer k6 sur **une autre machine** que le serveur (`winget install k6` sous Windows).

## Pendant

Toujours dans cet ordre, en regardant le serveur entre chaque étape :

```
k6 run -e BASE_URL=https://<domaine> -e EVENT_SLUG=<slug> -e PROFILE=fumee  k6-billet-gratuit.js   # 30 s, 5 utilisateurs
k6 run -e BASE_URL=https://<domaine> -e EVENT_SLUG=<slug> -e PROFILE=palier k6-billet-gratuit.js   # ~16 min, 20 → 50 → 100
k6 run -e BASE_URL=https://<domaine> -e EVENT_SLUG=<slug> -e PROFILE=pic    k6-billet-gratuit.js   # pic brutal à 200
```
- `-e SCALE=2` double le nombre d'utilisateurs ; ajouter `--insecure-skip-tls-verify` si le certificat est interne.
- **Arrêt automatique** : le test s'arrête seul si plus de 5 % des requêtes échouent ou si 95 % des réponses
  dépassent 3 s. Vous pouvez aussi l'arrêter à tout moment avec Ctrl+C.
- **Sur SRV06** : `docker stats` (processeur / mémoire) et `docker compose … logs -f backend` (erreurs 500).
- **Sur PostgreSQL** : `select count(*) from pg_stat_activity;` — le pool de connexions est souvent le premier
  goulot.

Objectifs de lecture (résumé k6) : `http_req_failed` < 1 %, `http_req_duration p(95)` < 1,5 s, et surtout
aucun temps de réponse qui s'aggrave régulièrement au fil du palier.

## Après

1. **Remettre `RATE_LIMIT_PER_MINUTE`** à sa valeur d'origine et relancer le backend.
2. **Aperçu** (lecture seule) — vérifier les nombres et que les deux dernières listes sont vides :
   `psql -h <hote> -U <utilisateur> -d <base> -f cleanup-1-apercu.sql`
3. **Suppression** (irréversible, en une seule transaction ; annule tout si une garde échoue) :
   `psql -h <hote> -U <utilisateur> -d <base> -v ON_ERROR_STOP=1 -1 -f cleanup-2-suppression.sql`
4. Relancer l'aperçu : tout doit être à 0.

Restent après nettoyage, sans conséquence : les lignes du journal d'audit (`AUTH_GUEST`…) et les
compteurs internes qui repartent de zéro. Si le nettoyage échoue avec « vrais utilisateurs sur
l'événement de test », c'est qu'un visiteur réel s'est inscrit à l'événement de test : ne rien forcer,
retirer d'abord l'événement du public (le suspendre) et traiter ces inscriptions à part.

## Bon à savoir

- Les invités « téléphone seul » ont une adresse fictive `tel-…@guest.plateforme.local` : l'application
  n'essaie plus de leur envoyer d'e-mails (sinon un test de charge enverrait des milliers de messages
  vers un domaine inexistant).
- Le test ne teste pas le paiement en ligne (FasoArzeka) : billets gratuits uniquement.
