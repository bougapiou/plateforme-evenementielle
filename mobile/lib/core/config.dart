import 'package:flutter/foundation.dart' show kIsWeb;

/// Runtime configuration.
///
/// Override the API base URL at build/run time with:
/// `flutter run --dart-define=API_BASE_URL=https://api.plateforme.bf/api`
class AppConfig {
  static const String _override =
      String.fromEnvironment('API_BASE_URL', defaultValue: '');

  /// Base URL of the REST API.
  /// - `--dart-define=API_BASE_URL=...` wins if provided.
  /// - Flutter **web** (run in a browser) → `localhost` (same machine).
  /// - Android **emulator** → `10.0.2.2` (the emulator's alias for the host).
  /// - A physical device needs `--dart-define` with the host's LAN IP.
  static String get apiBaseUrl {
    if (_override.isNotEmpty) return _override;
    return kIsWeb
        ? 'http://localhost:8080/api'
        : 'http://10.0.2.2:8080/api';
  }

  static const String appName = 'Plateforme des Événements';
  static const String currency = 'FCFA';
}
