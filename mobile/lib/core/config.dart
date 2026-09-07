/// Runtime configuration. Override the API base URL at build time with:
/// `flutter run --dart-define=API_BASE_URL=https://api.plateforme.bf`
class AppConfig {
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080/api', // Android emulator -> host machine
  );

  static const String appName = 'Plateforme des Événements';
  static const String currency = 'FCFA';
}
