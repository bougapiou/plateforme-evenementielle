# Hébergement — Plateforme Nationale de Gestion des Événements

Guide de mise en ligne avec Docker. Il suffit d'un serveur Linux avec Docker et
d'un nom de domaine qui pointe dessus.

## Ce qui est déployé

```
Internet ──► nginx (conteneur "frontend", ports 80/443)
               ├─ /          → site Angular (fichiers statiques)
               ├─ /api/…     → conteneur "backend" (Spring Boot, port 8080 interne)
               └─ /files/…   → conteneur "backend" (images, PDF de billets)
             backend ──► PostgreSQL (conteneur "postgres" ou serveur externe)
```

| Conteneur  | Rôle                                   | Exposé              |
|------------|----------------------------------------|---------------------|
| `frontend` | nginx : site web + proxy vers l'API    | 80 et 443 (hôte)    |
| `backend`  | API Spring Boot (Java 21), migrations Flyway automatiques | interne seulement |
| `postgres` | PostgreSQL 16 (profil `db`, optionnel) | interne seulement   |

Seuls les ports 80/443 sont ouverts vers l'extérieur. Le backend et la base ne
sont jamais joignables directement.

## Prérequis

- Linux (Ubuntu/Debian conseillé), 2 Go de RAM minimum, 10 Go de disque.
- Docker Engine ≥ 24 avec le plugin Compose (`docker compose version`).
  Installation : <https://docs.docker.com/engine/install/>
- Le serveur doit pouvoir télécharger les images Docker et les dépendances
  Maven/npm au premier build (accès Internet, ou un miroir interne).
- Le nom de domaine doit pointer vers ce serveur (voir « Réseau »).
- Le code source (ce dépôt) sur le serveur : `git clone` ou copie de l'archive.

## Déploiement pas à pas

Toutes les commandes se lancent depuis le dossier `infra/` du dépôt.

### 1. Créer le fichier de configuration

```bash
cd infra
cp .env.prod.example .env.prod
chmod 600 .env.prod
```

Ouvrir `.env.prod` et renseigner :

| Variable | Valeur |
|----------|--------|
| `CORS_ORIGINS`, `FRONTEND_BASE_URL`, `APP_CALLBACK_BASE_URL`, `STORAGE_LOCAL_URL` | l'URL publique, par ex. `https://evenements-19.mtdpce-test.gov.bf` (`STORAGE_LOCAL_URL` = cette URL + `/files`) |
| `DB_PASSWORD` | un mot de passe fort pour PostgreSQL |
| `APP_JWT_SECRET` | `openssl rand -base64 32` |
| `PAYMENT_WEBHOOK_SECRET` | `openssl rand -base64 32` |
| `SUPER_ADMIN_PASSWORD` | mot de passe du compte super admin (`SUPER_ADMIN_EMAIL`) |

**Toutes** les variables de la liste ci-dessus doivent avoir une valeur : une
variable laissée vide écrase la valeur par défaut avec une chaîne vide.

### 2. Choisir le mode réseau

**Mode 1 — le domaine est servi en HTTP par une passerelle qui gère déjà le
HTTPS (reverse proxy ANPTIC, load balancer…).** Rien à faire : le conteneur
écoute en HTTP sur le port 80 (`HTTP_PORT` pour en changer). La passerelle doit
relayer vers `http://<IP du serveur>:80` en conservant l'en-tête `Host`.

**Mode 2 — le conteneur gère lui-même le HTTPS.** Placer les certificats dans
`infra/certs/` :

```bash
mkdir -p certs
cp /chemin/vers/certificat-avec-chaine.crt certs/fullchain.pem
cp /chemin/vers/cle-privee.key            certs/privkey.pem
chmod 600 certs/privkey.pem
```

`fullchain.pem` = le certificat du domaine **suivi** des certificats
intermédiaires (`cat domaine.crt intermediaire.crt > certs/fullchain.pem`).
Puis dans `.env.prod`, décommenter :

```
NGINX_CONF=../frontend/nginx.tls.conf
```

Le port 80 redirige alors automatiquement vers HTTPS.

### 3. Lancer

```bash
# Avec PostgreSQL dans Docker (cas simple) :
docker compose --env-file .env.prod -f docker-compose.prod.yml --profile db up -d --build

# Avec une base PostgreSQL externe (DB_URL renseigné dans .env.prod) :
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

Le premier build prend plusieurs minutes (Maven et npm). Suivre l'état :

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml --profile db ps
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f backend
```

Le backend est prêt quand `ps` affiche `healthy` (environ 1 minute) : les
migrations de base de données s'appliquent seules au premier démarrage.

### 4. Vérifier

```bash
curl -i https://<domaine>/api/public/events        # 200 et une liste JSON
curl -i https://<domaine>/                          # 200, page HTML
```

Puis dans un navigateur : ouvrir le site, se connecter avec
`SUPER_ADMIN_EMAIL` / `SUPER_ADMIN_PASSWORD`, créer un événement avec une image.

## Réseau et pare-feu

- Ouvrir **80 et 443** en entrée sur le serveur (et 22 pour l'administration).
  Rien d'autre : 8080 et 5432 ne doivent pas être ouverts.
- Le domaine doit pointer vers **l'IP du serveur qui exécute Docker** (celui qui
  héberge le conteneur `frontend`), pas vers un autre serveur du parc
  (par exemple celui de la base de données).
- Si une passerelle/reverse proxy se trouve devant, elle doit transmettre
  `Host` et idéalement `X-Forwarded-For` (limitation de débit par IP).

## Exploitation

| Action | Commande (depuis `infra/`, préfixe : `docker compose --env-file .env.prod -f docker-compose.prod.yml`) |
|--------|---------|
| Arrêter | `… down` (les données sont conservées) |
| Redémarrer un service | `… restart backend` |
| Logs | `… logs -f --tail=100 backend` |
| Mettre à jour après un `git pull` | `… --profile db up -d --build` |

### Données à sauvegarder

1. **La base** : volume Docker `plateforme_pgdata`.
   ```bash
   docker compose --env-file .env.prod -f docker-compose.prod.yml exec postgres \
     pg_dump -U plateforme plateforme | gzip > sauvegarde-$(date +%F).sql.gz
   ```
2. **Les fichiers téléversés** : le dossier `STORAGE_HOST_PATH`
   (par défaut `infra/data/storage`).
3. `.env.prod` (dans un endroit sûr : il contient les secrets).

Ne jamais lancer `docker compose down -v` : l'option `-v` supprime la base.

### Changer le schéma de base de données

Les évolutions passent par des migrations Flyway
(`backend/src/main/resources/db/migration/`) appliquées automatiquement au
démarrage du backend. Les données existantes sont conservées ; une mise à jour
= `git pull` puis relancer la commande de l'étape 3.

## Fonctionnalités dépendant de la configuration

- **E-mails** (mot de passe oublié…) : nécessitent un serveur SMTP sans
  authentification (`MAIL_HOST`, `MAIL_PORT`). Tant que `MAIL_HOST=localhost`,
  aucun e-mail n'est envoyé ; le reste de la plateforme fonctionne. Une fois un
  relais SMTP configuré, retirer la ligne `MANAGEMENT_HEALTH_MAIL_ENABLED` du
  `docker-compose.prod.yml` pour réactiver le contrôle de santé du mail.
- **Paiement** : `PAYMENT_PROVIDER=sandbox` simule les paiements. Pour de vrais
  paiements FasoArzeka, passer à `arzeka` et renseigner les variables
  `FASOARZEKA_*` (voir `backend/.env.example`) ; l'URL `APP_CALLBACK_BASE_URL`
  doit alors être joignable depuis Internet.

## Dépannage

| Symptôme | Cause probable / action |
|----------|-------------------------|
| Le site ne répond pas depuis Internet, mais répond sur l'IP du serveur | Le domaine ou la passerelle ne pointe pas vers ce serveur (voir « Réseau ») |
| `502 Bad Gateway` | Le backend démarre encore (attendre `healthy`) ou a planté : `logs backend` |
| Le backend redémarre en boucle | `logs backend` : mot de passe/URL de base erronés, ou variable vide dans `.env.prod` |
| `413 Request Entity Too Large` | Envoi > 20 Mo (limite volontaire) |
| Erreur CORS dans le navigateur | `CORS_ORIGINS` ne correspond pas exactement à l'URL du site (schéma + domaine, sans `/` final) |
| Images cassées | `STORAGE_LOCAL_URL` incorrect (doit finir par `/files`) |
| `docker compose` : `DB_PASSWORD doit être défini` | Oubli de `--env-file .env.prod` ou variable vide |
