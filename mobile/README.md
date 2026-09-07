# Application mobile — Plateforme des Événements

Flutter · Riverpod · go_router · Dio · flutter_secure_storage.

## Lancer

```bash
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api   # émulateur Android
```

- `10.0.2.2` = machine hôte vue depuis l'émulateur Android.
- iOS simulateur / web : utiliser `http://localhost:8080/api`.

## Structure

```
lib/
├── core/
│   ├── config.dart          Configuration (API_BASE_URL via --dart-define)
│   ├── models.dart          DTO (AuthResponse, UserSummary, ApiException)
│   ├── token_store.dart     Stockage sécurisé de la session
│   ├── api_client.dart      Dio + intercepteur JWT (refresh auto sur 401)
│   ├── auth_repository.dart  login / register / logout
│   ├── providers.dart       Providers Riverpod + AuthController
│   ├── router.dart          go_router + redirection selon l'authentification
│   └── theme.dart
└── features/
    ├── home/                Accueil (catalogue à venir)
    ├── auth/                Connexion, inscription
    └── dashboard/           Espace utilisateur (profil, rôles, permissions)
```

## Roadmap mobile

Consultation d'événements · inscription + achat de billet · paiement (webview) ·
portefeuille de billets avec QR hors-ligne · scan QR (personnel de contrôle) ·
notifications push.
