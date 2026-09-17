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
        : 'http://10.65.36.101:8080/api';
  }

  /// Server origin (`scheme://host:port`) — [apiBaseUrl] without its `/api` suffix.
  /// Used to build URLs for `/api/**` binary endpoints (QR, PDF) and `/files/**`.
  static String get apiOrigin {
    final u = Uri.parse(apiBaseUrl);
    return Uri(scheme: u.scheme, host: u.host, port: u.hasPort ? u.port : null)
        .toString();
  }

  /// Rewrites a backend URL so its host matches the configured API host.
  /// The backend emits `http://localhost:8080/...`; that host is unreachable
  /// from an Android emulator (`10.0.2.2`) or a physical device (LAN IP).
  /// External URLs are returned untouched.
  static String? resolveHost(String? url) {
    if (url == null || url.trim().isEmpty) return null;
    final api = Uri.tryParse(apiBaseUrl);
    final u = Uri.tryParse(url.trim());
    if (api == null || u == null || !u.hasScheme) return url;
    const local = {'localhost', '127.0.0.1', '10.0.2.2', '0.0.0.0'};
    if (local.contains(u.host) || u.host == api.host) {
      return u
          .replace(
            scheme: api.scheme,
            host: api.host,
            port: api.hasPort ? api.port : (u.hasPort ? u.port : null),
          )
          .toString();
    }
    return url;
  }

  static const String appName = 'Plateforme des Événements';
  static const String currency = 'FCFA';
}
