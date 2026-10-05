# Capteurs de comptage (laser) — Arduino / Raspberry

Un capteur compte les passages **sans billet**. Il envoie chaque passage à la plateforme ;
le total s'affiche dans « Présence / Flux » sous « Comptage physique (capteurs laser) »,
séparément des scans de billets.

## Créer le capteur (une fois par boîtier)

Éditeur de l'événement → onglet **Contrôle** → *Capteurs de comptage* → nom (ex. « Porte principale ») → **Créer**.
La clé `pne_...` n'est affichée **qu'une seule fois** : la copier dans le boîtier. Elle est révocable
(bouton *Révoquer*) et ne fonctionne que pour cet événement.

##  Endpoints (sans compte, clé dans l'en-tête)

| Action | Requête |
|---|---|
| Une **entrée** | `POST /api/sensors/entry` |
| Une **sortie** | `POST /api/sensors/exit` |
| Plusieurs d'un coup (réseau coupé) | `POST /api/sensors/entry?count=5` (1 à 1000) |

En-tête obligatoire : `X-Sensor-Key: pne_...` — pas de corps de requête.
Réponse (`200`) : `{"entrees": 12, "sorties": 4, "presents": 8}`.
Erreurs : `401` clé absente/invalide/révoquée · `422 EVENT_NOT_ACTIVE` événement pas encore publié ·
`422 INVALID_COUNT` `count` hors 1–1000.

Test rapide : `curl -X POST -H "X-Sensor-Key: pne_..." https://<domaine>/api/sensors/entry`

Un faisceau = un sens. Pour compter entrée **et** sortie, utiliser deux faisceaux (deux voies) : le premier
appelle `/entry`, le second `/exit`. Un même boîtier peut porter les deux avec la même clé.

## Bon à savoir

- Un laser compte aussi les personnes sans billet et les doubles passages : ce chiffre ne se mélange
  volontairement pas aux scans de billets.
- Les passages ne sont acceptés que lorsque l'événement est publié ou en cours.
- Le certificat du domaine de test est interne : `setInsecure()` (ESP32) / `verify=False` (Python)
  peut être nécessaire en test ; en production, fournir le certificat CA.
