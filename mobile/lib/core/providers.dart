import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../data/repositories.dart';
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

// --- feature repositories ---------------------------------------------------

final eventsRepositoryProvider =
    Provider((ref) => EventsRepository(ref.watch(apiClientProvider)));
final ticketsRepositoryProvider =
    Provider((ref) => TicketsRepository(ref.watch(apiClientProvider)));
final registrationsRepositoryProvider =
    Provider((ref) => RegistrationsRepository(ref.watch(apiClientProvider)));
final standsRepositoryProvider =
    Provider((ref) => StandsRepository(ref.watch(apiClientProvider)));
final paymentsRepositoryProvider =
    Provider((ref) => PaymentsRepository(ref.watch(apiClientProvider)));
final invoicesRepositoryProvider =
    Provider((ref) => InvoicesRepository(ref.watch(apiClientProvider)));
final notificationsRepositoryProvider =
    Provider((ref) => NotificationsRepository(ref.watch(apiClientProvider)));
final checkinRepositoryProvider =
    Provider((ref) => CheckinRepository(ref.watch(apiClientProvider)));

/// True when the signed-in user carries [permission].
final hasPermissionProvider = Provider.family<bool, String>((ref, permission) {
  final user = ref.watch(authControllerProvider).valueOrNull;
  return user?.permissions.contains(permission) ?? false;
});

/// Unread in-app notification count, refreshed on demand.
final unreadCountProvider = FutureProvider<int>((ref) async {
  final user = ref.watch(authControllerProvider).valueOrNull;
  if (user == null) return 0;
  try {
    return await ref.watch(notificationsRepositoryProvider).unreadCount();
  } catch (_) {
    return 0;
  }
});

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
