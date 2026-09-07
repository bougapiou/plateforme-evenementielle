# Application mobile — Plateforme des Événements

Flutter · Riverpod · go_router · Dio · flutter_secure_storage · mobile_scanner.

## Lancer

```bash
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api   # émulateur Android
```

- `10.0.2.2` = machine hôte vue depuis l'émulateur Android.
- iOS simulateur / web : utiliser `http://localhost:8080/api`.
- Build APK : `flutter build apk` (NDK 27 requis, fixé dans `android/app/build.gradle.kts`).

## Fonctionnalités

| Zone | Écran | API |
|------|-------|-----|
| Catalogue | recherche + filtres catégorie, cartes événement | `GET /public/events` |
| Détail événement | infos, programme (activités), intervenants, partenaires, billetterie, stands | `GET /public/events/{slug}` (+ `/tickets`, `/stand-types`, `/stands`) |
| Achat de billets | sélection catégories + quantités, total, création de commande | `POST /ticket-orders` |
| Paiement | choix du moyen (Orange/Moov/Telecel Money, FasoArzeka, carte), sandbox | `POST /payments` + `/payments/{ref}/simulate` |
| Inscription | formulaire particulier + participants | `POST /events/{id}/registrations` |
| Réservation de stand | plan par type, blocage 15 min, paiement | `POST /stand-reservations` |
| Portefeuille | liste des billets, QR **mis en cache pour l'affichage hors-ligne**, PDF | `GET /tickets/my`, `/tickets/{id}/qr.png`, `/tickets/{id}/pdf` |
| Mon activité | commandes, inscriptions, réservations, paiements | `*/my` |
| Factures & reçus | liste + téléchargement PDF | `GET /invoices/my` |
| Notifications | liste in-app, badge non-lus, tout marquer lu | `GET /notifications` |
| Contrôle à l'entrée | scan caméra du QR → VALIDE / DÉJÀ UTILISÉ / INVALIDE | `POST /checkins/scan` |
| Profil | compte, rôles, déconnexion | — |

Les inscriptions de structures/entreprises et l'espace organisateur restent sur le portail web.

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
│   ├── auth_repository.dart login / register / logout
│   ├── providers.dart       Providers Riverpod (repos, permissions, compteur non-lus)
│   ├── router.dart          go_router — shell à 4 onglets + routes plein écran
│   └── theme.dart
├── data/
│   ├── domain.dart          Modèles (Event, Ticket, Order, Stand, Payment, …)
│   └── repositories.dart    1 repository par domaine backend
└── features/
    ├── auth/                connexion, inscription
    ├── shell/               conteneur de navigation (bottom bar)
    ├── catalogue/           liste + recherche + filtres
    ├── event/               détail d'un événement
    ├── purchase/            billets, inscription, stand, paiement
    ├── wallet/              portefeuille de billets + QR hors-ligne
    ├── activity/            commandes / inscriptions / réservations / paiements
    ├── invoices/            factures & reçus
    ├── notifications/       centre de notifications
    ├── scanner/             contrôle d'accès (mobile_scanner)
    └── profile/             profil & déconnexion
```

## QR hors-ligne

À la première ouverture d'un billet, l'image QR (`/api/tickets/{id}/qr.png`) est
téléchargée puis enregistrée dans le répertoire documents de l'application
(`qr_<ticketId>.png`). Les ouvertures suivantes lisent le fichier local : le billet
reste présentable à l'entrée même sans réseau.
