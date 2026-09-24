import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../data/domain.dart';
import '../data/organizer_events_repository.dart';
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
final usersRepositoryProvider =
    Provider((ref) => UsersRepository(ref.watch(apiClientProvider)));
final structuresRepositoryProvider =
    Provider((ref) => StructuresRepository(ref.watch(apiClientProvider)));
final organizersRepositoryProvider =
    Provider((ref) => OrganizersRepository(ref.watch(apiClientProvider)));
final organizerEventsRepositoryProvider =
    Provider((ref) => OrganizerEventsRepository(ref.watch(apiClientProvider)));

/// Event categories (shared, cached for the session).
final eventCategoriesProvider = FutureProvider<List<EventCategory>>(
  (ref) => ref.watch(eventsRepositoryProvider).categories(),
);

/// The signed-in user's structures (for structure-scoped registrations / stands).
final myStructuresProvider =
    FutureProvider.autoDispose<List<StructureSummary>>((ref) async {
  final user = ref.watch(authControllerProvider).valueOrNull;
  if (user == null) return const [];
  return ref.watch(structuresRepositoryProvider).mine();
});

/// True when the signed-in user carries [permission].
final hasPermissionProvider = Provider.family<bool, String>((ref, permission) {
  final user = ref.watch(authControllerProvider).valueOrNull;
  return user?.permissions.contains(permission) ?? false;
});

/// True when a real (non-guest) account is signed in.
final isSignedInProvider = Provider<bool>((ref) {
  final user = ref.watch(authControllerProvider).valueOrNull;
  return user != null && !user.guest;
});

/// True when the current session is a guest (checkout without account).
final isGuestProvider = Provider<bool>((ref) =>
    ref.watch(authControllerProvider).valueOrNull?.guest ?? false);

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

  /// Opens a guest session (visitor checking out without an account).
  Future<void> guestSession({
    required String firstName,
    required String lastName,
    required String phone,
    String? email,
  }) async {
    final res = await _repo.guestSession(
      firstName: firstName,
      lastName: lastName,
      phone: phone,
      email: email,
    );
    state = AsyncValue.data(res.user);
  }

  /// Same as [guestSession], but for a "no form" free ticket: phone only.
  Future<void> guestSessionQuick(String phone) async {
    final res = await _repo.guestSessionQuick(phone);
    state = AsyncValue.data(res.user);
  }

  /// "Retrouver mon billet": only succeeds if this phone already has tickets.
  Future<void> guestLookup(String phone) async {
    final res = await _repo.guestLookup(phone);
    state = AsyncValue.data(res.user);
  }

  /// Turns the current guest session into a full account.
  Future<void> completeRegistration({
    required String password,
    String? email,
    String? firstName,
    String? lastName,
  }) async {
    final res = await _repo.completeRegistration(
      password: password,
      email: email,
      firstName: firstName,
      lastName: lastName,
    );
    state = AsyncValue.data(res.user);
  }

  /// Requests a password-reset e-mail (does not touch the session).
  Future<void> requestPasswordReset(String email) =>
      _repo.requestPasswordReset(email);

  /// Sets a new password from a reset token (does not sign in).
  Future<void> resetPassword({required String token, required String password}) =>
      _repo.resetPassword(token: token, password: password);

  Future<void> logout() async {
    await _repo.logout();
    state = const AsyncValue.data(null);
  }

  /// Replaces the cached user after a profile change (name / phone / roles).
  Future<void> setUser(UserSummary user) async {
    await _repo.cacheUser(user);
    state = AsyncValue.data(user);
  }
}

final authControllerProvider =
    StateNotifierProvider<AuthController, AsyncValue<UserSummary?>>(
  (ref) => AuthController(ref.watch(authRepositoryProvider)),
);
