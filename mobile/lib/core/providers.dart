import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'api_client.dart';
import 'auth_repository.dart';
import 'models.dart';
import 'token_store.dart';

final tokenStoreProvider = Provider<TokenStore>((ref) => TokenStore());

final apiClientProvider = Provider<ApiClient>(
  (ref) => ApiClient(ref.watch(tokenStoreProvider)),
);

final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => AuthRepository(ref.watch(apiClientProvider), ref.watch(tokenStoreProvider)),
);

/// Holds the currently authenticated user (null = signed out).
class AuthController extends StateNotifier<AsyncValue<UserSummary?>> {
  final AuthRepository _repo;

  AuthController(this._repo) : super(const AsyncValue.loading()) {
    _restore();
  }

  Future<void> _restore() async {
    final user = await _repo.restoreSession();
    state = AsyncValue.data(user);
  }

  Future<void> login(String email, String password) async {
    final res = await _repo.login(email, password);
    state = AsyncValue.data(res.user);
  }

  Future<void> register({
    required String firstName,
    required String lastName,
    required String email,
    required String password,
    String? phone,
    String type = 'PARTICULIER',
  }) async {
    final res = await _repo.register(
      firstName: firstName,
      lastName: lastName,
      email: email,
      password: password,
      phone: phone,
      type: type,
    );
    state = AsyncValue.data(res.user);
  }

  Future<void> logout() async {
    await _repo.logout();
    state = const AsyncValue.data(null);
  }
}

final authControllerProvider =
    StateNotifierProvider<AuthController, AsyncValue<UserSummary?>>(
  (ref) => AuthController(ref.watch(authRepositoryProvider)),
);
