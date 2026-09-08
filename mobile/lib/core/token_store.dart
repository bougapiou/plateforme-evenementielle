import 'dart:convert';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'models.dart';

/// Persists the session (tokens + cached user) in the platform secure storage.
class TokenStore {
  static const _access = 'pne.access';
  static const _refresh = 'pne.refresh';
  static const _user = 'pne.user';

  final FlutterSecureStorage _storage;
  TokenStore([FlutterSecureStorage? storage])
      : _storage = storage ?? const FlutterSecureStorage();

  Future<void> save(AuthResponse res) async {
    await _storage.write(key: _access, value: res.accessToken);
    await _storage.write(key: _refresh, value: res.refreshToken);
    await _storage.write(key: _user, value: jsonEncode(res.user.toJson()));
  }

  /// Refreshes only the cached user (tokens untouched).
  Future<void> saveUser(UserSummary user) async {
    await _storage.write(key: _user, value: jsonEncode(user.toJson()));
  }

  Future<void> clear() async {
    await _storage.deleteAll();
  }

  Future<String?> get accessToken => _storage.read(key: _access);
  Future<String?> get refreshToken => _storage.read(key: _refresh);

  Future<UserSummary?> get user async {
    final raw = await _storage.read(key: _user);
    if (raw == null) return null;
    return UserSummary.fromJson(jsonDecode(raw) as Map<String, dynamic>);
  }
}
