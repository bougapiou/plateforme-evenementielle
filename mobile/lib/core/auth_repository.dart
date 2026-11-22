import 'package:dio/dio.dart';
import 'api_client.dart';
import 'models.dart';
import 'token_store.dart';

class AuthRepository {
  final ApiClient _api;
  final TokenStore _store;

  AuthRepository(this._api, this._store);

  /// Password step. Throws [MfaRequiredException] when a second factor is needed.
  Future<AuthResponse> login(String email, String password) =>
      _post('/auth/login', {'email': email, 'password': password});

  Future<AuthResponse> mfaVerify(String mfaToken, String code) =>
      _post('/auth/mfa/verify', {'mfaToken': mfaToken, 'code': code});

  Future<AuthResponse> mfaActivate(String mfaToken, String code) =>
      _post('/auth/mfa/activate', {'mfaToken': mfaToken, 'code': code});

  Future<MfaSetup> mfaSetup(String mfaToken) async {
    try {
      final res = await _api.dio.post('/auth/mfa/setup', data: {'mfaToken': mfaToken});
      return MfaSetup.fromJson(res.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _api.toApiException(e);
    }
  }

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

  /// Opens a guest session for a visitor buying / registering without an account.
  /// A phone number is required; the e-mail is optional.
  Future<AuthResponse> guestSession({
    required String firstName,
    required String lastName,
    required String phone,
    String? email,
  }) =>
      _post('/auth/guest', {
        'firstName': firstName,
        'lastName': lastName,
        'phone': phone,
        if (email != null && email.isNotEmpty) 'email': email,
      });

  /// Same as [guestSession], but for a "no form" free ticket: just a phone
  /// number, no name collected.
  Future<AuthResponse> guestLookup(String phone) =>
      _post('/auth/guest-lookup', {'phone': phone});

  /// « Mes billets » step 1: an SMS code is sent if this number holds tickets.
  Future<void> ticketOtpRequest(String phone) async {
    try {
      await _api.dio.post('/auth/otp/request', data: {'phone': phone});
    } on DioException catch (e) {
      throw _api.toApiException(e);
    }
  }

  /// Step 2: the code opens the session holding the tickets.
  Future<AuthResponse> ticketOtpVerify(String phone, String code) =>
      _post('/auth/otp/verify', {'phone': phone, 'code': code});

  Future<AuthResponse> guestSessionQuick(String phone) =>
      _post('/auth/guest-quick', {'phone': phone});

  /// Turns the current guest session into a full account (chooses a password).
  /// [email] is required only for a phone-only guest.
  Future<AuthResponse> completeRegistration({
    required String password,
    String? email,
    String? firstName,
    String? lastName,
  }) =>
      _post('/auth/complete', {
        'password': password,
        if (email != null && email.isNotEmpty) 'email': email,
        if (firstName != null && firstName.isNotEmpty) 'firstName': firstName,
        if (lastName != null && lastName.isNotEmpty) 'lastName': lastName,
      });

  /// Asks the backend to e-mail a password-reset link. Always succeeds.
  Future<void> requestPasswordReset(String email) async {
    try {
      await _api.dio.post('/auth/password/forgot', data: {'email': email});
    } on DioException catch (e) {
      throw _api.toApiException(e);
    }
  }

  /// Sets a new password from a token pasted from the reset e-mail.
  Future<void> resetPassword({
    required String token,
    required String password,
  }) async {
    try {
      await _api.dio.post('/auth/password/reset',
          data: {'token': token.trim(), 'password': password});
    } on DioException catch (e) {
      throw _api.toApiException(e);
    }
  }

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
      final data = res.data as Map<String, dynamic>;
      if (data['mfa'] != null) {
        throw MfaRequiredException(data['mfa'] as String, data['mfaToken'] as String? ?? '');
      }
      final auth = AuthResponse.fromJson(data);
      await _store.save(auth);
      return auth;
    } on DioException catch (e) {
      throw _api.toApiException(e);
    }
  }
}
