# Application mobile — Plateforme des Événements

Flutter · Riverpod · go_router · Dio · flutter_secure_storage · mobile_scanner ·
image_picker · open_filex · path_provider.

## Lancer

L'URL de l'API est **résolue automatiquement** selon la cible (voir
`lib/core/config.dart`) ; `--dart-define=API_BASE_URL=...` la remplace toujours.

```bash
flutter pub get

# Émulateur Android (cible principale) — API sur 10.0.2.2:8080, pas de CORS
flutter run

# Chrome / navigateur — API sur localhost:8080 (résolu tout seul)
flutter run -d chrome

# Appareil physique — indiquer l'IP LAN de la machine qui héberge l'API
flutter run --dart-define=API_BASE_URL=http://192.168.1.20:8080/api
```

- `10.0.2.2` = machine hôte vue depuis l'émulateur Android ; **inutilisable**
  depuis un navigateur (utiliser `localhost`, ce que fait déjà la config web).
- **CORS** : le backend accepte par défaut tout port `localhost` / `127.0.0.1`
  (`CORS_ORIGINS` dans `application.yml`). En production, pointer `CORS_ORIGINS`
  sur le vrai domaine.
- Build APK : `flutter build apk` · Build web : `flutter build web`.
- Sur **web**, le cache QR hors-ligne et le téléchargement des PDF sont
  désactivés (pas de système de fichiers dans le navigateur) ; le QR reste
  affiché en ligne.

## Fonctionnalités

| Zone | Écran | API |
|------|-------|-----|
| Catalogue | recherche + filtres catégorie, cartes événement | `GET /public/events` |
| Détail événement | infos, programme (activités), intervenants, partenaires, billetterie, stands | `GET /public/events/{slug}` (+ `/tickets`, `/stand-types`, `/stands`) |
| Achat de billets | sélection catégories + quantités, total, commande (option **au nom d'une structure**). **Sans compte** : session invité à la volée (nom + **téléphone** ; e-mail facultatif). Un événement gratuit = **1 billet par personne** | `POST /ticket-orders`, `POST /auth/guest` |
| Paiement | choix du moyen (Orange/Moov/Telecel Money, FasoArzeka, carte), sandbox | `POST /payments` + `/payments/{ref}/simulate` |
| Inscription | formulaire **particulier ou structure** + participants. **Sans compte** pour un particulier : session invité à la volée | `POST /events/{id}/registrations`, `POST /auth/guest` |
| Détail inscription | participants, **pièces jointes** (photo/galerie → upload/suppression), annulation, confirmation PDF | `GET/POST/DELETE /registrations/{id}/documents`, `/registrations/{id}/confirmation.pdf` |
| Réservation de stand | plan par type, blocage 15 min, paiement (option **structure**) | `POST /stand-reservations` |
| Détail réservation | annulation, confirmation PDF | `/stand-reservations/{id}/confirmation.pdf` |
| Détail commande | lignes, total, payer / annuler | `GET /ticket-orders/{id}` |
| Portefeuille | liste des billets, QR **mis en cache pour l'affichage hors-ligne**, PDF | `GET /tickets/my`, `/tickets/{id}/qr.png`, `/tickets/{id}/pdf` |
| Mon activité | commandes, inscriptions, réservations, paiements (chaque ligne ouvre son détail) | `*/my` |
| Factures & reçus | liste + téléchargement PDF | `GET /invoices/my` |
| Notifications | liste in-app, badge non-lus, tout marquer lu, **ouverture du contenu lié** | `GET /notifications` |
| **Mon compte** | modifier le profil (nom / téléphone), changer le mot de passe | `GET/PATCH /users/me`, `POST /users/me/password` |
| **Finaliser mon compte** (invités) | après un achat / une inscription : choix d'un mot de passe → la session invité devient un vrai compte | `POST /auth/complete` |
| **Mot de passe oublié** | demande d'un lien par e-mail, puis nouveau mot de passe à partir du code reçu | `POST /auth/password/forgot`, `POST /auth/password/reset` |
| **Mes structures** | liste, création, modification, représentants (ajout / retrait) | `/structures`, `/structures/{id}/members` |
| **Devenir organisateur** | formulaire de demande (rattachement structure optionnel) | `POST /organizers/apply`, `GET /organizers/me` |
| **Mes événements** (organisateurs) | liste, **création**, gestion complète : infos + image, programme (activités + visuel), billetterie (catégories, quotas, portée), stands, intervenants (photo), partenaires (logo), workflow (soumettre / publier / ouvrir-fermer les inscriptions) | `POST/PUT /events`, `/events/mine`, `/events/{id}/activities|tickets|stand-types|speakers|partners`, `/events/{id}/submit\|publish\|…`, `POST /uploads/image` |
| Contrôle à l'entrée | choix de l'événement **puis de l'activité** (ou entrée générale), scan caméra du QR → VALIDE / DÉJÀ UTILISÉ / INVALIDE (un billet d'activité n'est valable que pour la sienne) | `GET /checkins/events` (+ `/{id}/activities`), `POST /checkins/scan` (avec `activityId`) |
| Participer à une activité gratuite | bouton « Participer » sur une activité en accès gratuit → billet + QR immédiats | `POST /activities/{id}/attend` |
| **Accréditations** (organisateurs) | badges nominatifs (conférencier, exposant, modérateur, MC, panéliste, compétiteur, presse, staff…) pour une activité ou tout l'événement, badge PDF + QR, révocation | `GET/POST /events/{id}/accreditations`, `POST /accreditations/{id}/revoke`, `/badge.pdf` |
| Profil | compte, rôles, déconnexion | — |

Un **organisateur** peut désormais créer et gérer ses événements de bout en bout
depuis l'app (Profil → *Mes événements*). La **supervision admin** (validation
des événements, des structures, des organisateurs) reste sur le portail web.

## Structure

```
lib/
├── core/
│   ├── config.dart          API_BASE_URL via --dart-define
│   ├── models.dart          DTO auth (AuthResponse, UserSummary, ApiException)
│   ├── format.dart          Formatage dates / montants (fr, FCFA)
│   ├── widgets.dart         StatusChip, FutureView, EmptyState, ErrorRetry
│   ├── token_store.dart     Session en stockage sécurisé
│   ├── api_client.dart      Dio + intercepteur JWT (refresh auto) + download binaire
│   ├── auth_repository.dart login / register / logout / session invité / finalisation de compte
│   ├── providers.dart       Providers Riverpod (repos, permissions, compteur non-lus)
│   ├── router.dart          go_router — shell à 4 onglets + routes plein écran
│   └── theme.dart
├── data/
│   ├── domain.dart          Modèles (Event, Ticket, Order, Stand, Payment, …)
│   └── repositories.dart    1 repository par domaine backend
└── features/
    ├── auth/                connexion, inscription, finaliser un compte invité
    ├── shell/               conteneur de navigation (bottom bar)
    ├── catalogue/           liste + recherche + filtres
    ├── event/               détail d'un événement
    ├── purchase/            billets, inscription, stand, paiement, structure_toggle
    ├── wallet/              portefeuille de billets + QR hors-ligne
    ├── activity/            listes + écrans de détail (commande / inscription / stand)
    ├── structures/          mes structures : liste, formulaire, détail + membres
    ├── organizer/           devenir organisateur + Mes événements (création & gestion complète)
    ├── invoices/            factures & reçus
    ├── notifications/       centre de notifications (+ deep-link)
    ├── scanner/             contrôle d'accès (mobile_scanner)
    └── profile/             profil, édition, mot de passe, déconnexion
```

## QR hors-ligne

À la première ouverture d'un billet, l'image QR (`/api/tickets/{id}/qr.png`) est
téléchargée puis enregistrée dans le répertoire documents de l'application
(`qr_<ticketId>.png`). Les ouvertures suivantes lisent le fichier local : le billet
reste présentable à l'entrée même sans réseau.
