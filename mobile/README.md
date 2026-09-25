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

## Build iOS

Un build iOS exige **macOS + Xcode** : il ne se fait pas depuis un poste Windows. Deux voies.

**1. Sur GitHub (aucun Mac requis)** — workflow `.github/workflows/main.yml`, déclenché à la
main : onglet *Actions* → **Flutter iOS Build** → *Run workflow* (champ facultatif : URL de l'API ;
vide = production). Il installe la dernière version stable de Flutter, compile en `--no-codesign`
(le Podfile est généré par `flutter build ios`, il n'est pas dans le dépôt) et publie l'artefact
**`pne-mobile-ios-unsigned`** (IPA non signé, conservé 14 jours). Un IPA non signé ne s'installe
pas tel quel : il faut le signer (voir ci-dessous).

`pubspec.yaml` déclare `intl: any` exprès : la version d'`intl` est imposée par
`flutter_localizations` et change avec la version de Flutter (0.19 avec Flutter 3.29, 0.20.x avec les
versions récentes) ; une version fixe casse soit le poste de développement, soit le build GitHub.

**2. Sur un Mac** :

```bash
cd mobile && flutter pub get
open ios/Runner.xcworkspace     # Runner → Signing & Capabilities → choisir l'équipe Apple
flutter build ipa               # ou Xcode : Product → Archive
```

**Signer et distribuer** — selon ce dont on dispose :

| Besoin | Ce qu'il faut |
|--------|---------------|
| Tester sur son propre iPhone, sans payer | un Apple ID gratuit + un outil de signature (Sideloadly, AltStore) sur l'IPA non signé ; l'app expire au bout de 7 jours |
| TestFlight / App Store / distribution interne | **compte Apple Developer** (99 $/an) : certificat de distribution, profil de provisionnement, identifiant d'équipe. Signature automatisable dans le workflow une fois ces éléments fournis (secrets GitHub) |

À savoir pour iOS :

- Identifiant de l'app : `bf.evenements.plateformeMobile` (`ios/Runner.xcodeproj`) ; à changer avant
  la première publication si un autre identifiant doit être réservé chez Apple.
- iOS 12.0 minimum. Caméra et photothèque sont déjà déclarées dans `Info.plist`.
- L'API doit être en **HTTPS** (App Transport Security) : le build de production vise
  `https://evenements-19.mtdpce-test.gov.bf/api`. Un `http://` distant serait bloqué sur iPhone.
- App Store : icônes et captures, textes de présentation, et déclaration de chiffrement
  (`ITSAppUsesNonExemptEncryption`) demandée par App Store Connect.

## Fonctionnalités

| Zone | Écran | API |
|------|-------|-----|
| Catalogue | recherche + filtres catégorie, cartes événement | `GET /public/events` |
| Détail événement | infos, programme (activités), intervenants, partenaires, billetterie, stands | `GET /public/events/{slug}` (+ `/tickets`, `/stand-types`, `/stands`) |
| Achat de billets | sélection catégories + quantités, total, commande (option **au nom d'une structure**). **Sans compte** : session invité à la volée (nom + **téléphone** ; e-mail facultatif). Un événement gratuit = **1 billet par personne** | `POST /ticket-orders`, `POST /auth/guest` |
| Paiement | choix du moyen (Orange/Moov/Telecel Money, FasoArzeka, carte), sandbox | `POST /payments` + `/payments/{ref}/simulate` |
| Inscription | formulaire **particulier ou structure** + participants. **Sans compte** pour un particulier : session invité à la volée | `POST /events/{id}/registrations`, `POST /auth/guest` |
| Détail inscription | participants, **pièces jointes** (photo/galerie → upload/suppression), annulation, confirmation PDF | `GET/POST/DELETE /registrations/{id}/documents`, `/registrations/{id}/confirmation.pdf` |
| Réservation de stand | plan par type, blocage 15 min, paiement — au nom d'une **structure vérifiée** ou, si l'événement l'autorise, **en son nom propre** | `POST /stand-reservations` |
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
| **Mes événements** (organisateurs) | liste **filtrable par état** (brouillons, à valider, en ligne…) avec l'action attendue sur chaque carte ; **fiche de gestion** : couverture, chiffres clés (billets vendus, revenus, inscriptions, stands), prochaine étape du cycle de vie, configuration (infos, programme par jour, billetterie avec jauge de quota, stands, intervenants, partenaires), accréditations, contrôle, présence en direct ; **création / modification** en sections avec barre d'enregistrement fixe (un administrateur peut modifier un événement à tout état) ; suppressions avec confirmation | `POST/PUT /events`, `/events/mine`, `/events/{id}/activities|tickets|stand-types|speakers|partners`, `/events/{id}/submit\|publish\|…`, `GET /stats/events/{id}`, `POST /uploads/image` |
| Contrôle des accès | choix de l'événement, **puis de l'activité** (ou entrée générale), **puis du sens : contrôle à l'entrée / à la sortie** ; bascule Entrée / Sortie aussi dans le scanner ; compteurs live (entrées, sorties, présents, ré-entrées) ; scan caméra → VALIDE / REFUSÉ / INVALIDE | `GET /checkins/events` (+ `/{id}/activities`), `POST /checkins/scan` (`activityId`, `sens`), `GET /events/{id}/checkin-stats?activityId=` |
| **Présence en direct** (public, sans compte) | événements en cours, puis compteurs entrées / sorties / présents / ré-entrées, actualisés toutes les 4 s. **Source au choix** : billets QR, capteurs laser, ou **combiné** (les comptages s'additionnent, avec le détail par source). **Plein écran** immersif sur fond sombre, portrait ou paysage. Accessible depuis l'accueil, le contrôle et la fiche de gestion d'un événement | `GET /public/live-events`, `GET /public/events/{slug}/attendance` |
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
│   ├── manage_kit.dart      Design commun des écrans de gestion : SectionCard, ActionTile, KpiTile,
│   │                        ItemCard, FormSection, DateField, SheetScaffold, InfoBanner, MaxWidth…
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
    ├── scanner/             contrôle d'accès (mobile_scanner), retrouver mon billet
    ├── presence/            présence en direct : source QR / capteurs / combiné, plein écran
    └── profile/             profil, édition, mot de passe, déconnexion
```

## QR hors-ligne

À la première ouverture d'un billet, l'image QR (`/api/tickets/{id}/qr.png`) est
téléchargée puis enregistrée dans le répertoire documents de l'application
(`qr_<ticketId>.png`). Les ouvertures suivantes lisent le fichier local : le billet
reste présentable à l'entrée même sans réseau.
