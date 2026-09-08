import 'package:dio/dio.dart';
import 'api_client.dart';
import 'models.dart';
import 'token_store.dart';

class AuthRepository {
  final ApiClient _api;
  final TokenStore _store;

  AuthRepository(this._api, this._store);

  Future<AuthResponse> login(String email, String password) =>
      _post('/auth/login', {'email': email, 'password': password});

  Future<AuthResponse> register({
    required String firstName,
    required String lastName,
    required String email,
    required String password,
    String? phone,
    String type = 'PARTICULIER',
  }) =>
      _post('/auth/register', {
        'firstName': firstName,
        'lastName': lastName,
        'email': email,
        'password': password,
        'phone': phone,
        'type': type,
      });

  Future<void> logout() async {
    final refresh = await _store.refreshToken;
    if (refresh != null) {
      try {
        await _api.dio.post('/auth/logout', data: {'refreshToken': refresh});
      } catch (_) {
        // best effort
      }
    }
    await _store.clear();
  }

  Future<UserSummary?> restoreSession() => _store.user;

  Future<void> cacheUser(UserSummary user) => _store.saveUser(user);

  Future<AuthResponse> _post(String path, Map<String, dynamic> body) async {
    try {
      final res = await _api.dio.post(path, data: body);
      final auth = AuthResponse.fromJson(res.data as Map<String, dynamic>);
      await _store.save(auth);
      return auth;
    } on DioException catch (e) {
      throw _api.toApiException(e);
    }
  }
}
