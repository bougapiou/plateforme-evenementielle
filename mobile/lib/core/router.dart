import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'providers.dart';
import '../features/home/home_screen.dart';
import '../features/auth/login_screen.dart';
import '../features/auth/register_screen.dart';
import '../features/dashboard/dashboard_screen.dart';

final routerProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/',
    redirect: (context, state) {
      final auth = ref.read(authControllerProvider);
      final signedIn = auth.valueOrNull != null;
      final goingToDashboard = state.matchedLocation.startsWith('/tableau-de-bord');
      if (goingToDashboard && !signedIn) return '/connexion';
      return null;
    },
    routes: [
      GoRoute(path: '/', builder: (_, __) => const HomeScreen()),
      GoRoute(path: '/connexion', builder: (_, __) => const LoginScreen()),
      GoRoute(path: '/inscription', builder: (_, __) => const RegisterScreen()),
      GoRoute(
        path: '/tableau-de-bord',
        builder: (_, __) => const DashboardScreen(),
      ),
    ],
  );
});
