import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'providers.dart';
import '../features/auth/login_screen.dart';
import '../features/auth/register_screen.dart';
import '../features/auth/complete_account_screen.dart';
import '../features/auth/forgot_password_screen.dart';
import '../features/auth/reset_password_screen.dart';
import '../features/catalogue/catalogue_screen.dart';
import '../features/event/event_detail_screen.dart';
import '../features/purchase/buy_tickets_screen.dart';
import '../features/purchase/register_event_screen.dart';
import '../features/purchase/reserve_stand_screen.dart';
import '../features/purchase/payment_screen.dart';
import '../features/wallet/wallet_screen.dart';
import '../features/wallet/ticket_detail_screen.dart';
import '../features/activity/my_activity_screen.dart';
import '../features/activity/registration_detail_screen.dart';
import '../features/activity/stand_reservation_detail_screen.dart';
import '../features/activity/ticket_order_detail_screen.dart';
import '../features/structures/structures_screen.dart';
import '../features/structures/structure_form_screen.dart';
import '../features/structures/structure_detail_screen.dart';
import '../features/organizer/become_organizer_screen.dart';
import '../features/organizer/organizer_events_screen.dart';
import '../features/organizer/event_form_screen.dart';
import '../features/organizer/event_manage_screen.dart';
import '../features/organizer/programme_editor_screen.dart';
import '../features/organizer/tickets_editor_screen.dart';
import '../features/organizer/stands_editor_screen.dart';
import '../features/organizer/people_editor_screen.dart';
import '../features/profile/edit_profile_screen.dart';
import '../features/profile/change_password_screen.dart';
import '../features/invoices/invoices_screen.dart';
import '../features/notifications/notifications_screen.dart';
import '../features/scanner/scanner_home_screen.dart';
import '../features/scanner/scanner_screen.dart';
import '../features/profile/profile_screen.dart';
import '../features/shell/app_shell.dart';

final _rootKey = GlobalKey<NavigatorState>();
final _shellKey = GlobalKey<NavigatorState>();

const _publicPrefixes = [
  '/evenements',
  '/connexion',
  '/inscription',
  '/mot-de-passe',
];

final routerProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    navigatorKey: _rootKey,
    initialLocation: '/evenements',
    redirect: (context, state) {
      final auth = ref.read(authControllerProvider);
      if (auth.isLoading) return null;
      final signedIn = auth.valueOrNull != null;
      final loc = state.matchedLocation;
      final isPublic = _publicPrefixes.any((p) => loc.startsWith(p));
      if (!signedIn && !isPublic) return '/connexion';
      if (signedIn && (loc == '/connexion' || loc == '/inscription')) {
        return '/evenements';
      }
      return null;
    },
    routes: [
      GoRoute(path: '/connexion', builder: (_, __) => const LoginScreen()),
      GoRoute(path: '/inscription', builder: (_, __) => const RegisterScreen()),
      GoRoute(
        path: '/finaliser-compte',
        builder: (_, __) => const CompleteAccountScreen(),
      ),
      GoRoute(
        path: '/mot-de-passe-oublie',
        builder: (_, __) => const ForgotPasswordScreen(),
      ),
      GoRoute(
        path: '/mot-de-passe/reinitialiser',
        builder: (_, s) => ResetPasswordScreen(
          initialToken: s.uri.queryParameters['token'],
        ),
      ),

      StatefulShellRoute.indexedStack(
        parentNavigatorKey: _rootKey,
        builder: (context, state, shell) => AppShell(shell: shell),
        branches: [
          StatefulShellBranch(
            navigatorKey: _shellKey,
            routes: [
              GoRoute(
                path: '/evenements',
                builder: (_, __) => const CatalogueScreen(),
              ),
            ],
          ),
          StatefulShellBranch(routes: [
            GoRoute(path: '/billets', builder: (_, __) => const WalletScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/notifications',
              builder: (_, __) => const NotificationsScreen(),
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/profil', builder: (_, __) => const ProfileScreen()),
          ]),
        ],
      ),

      // Full-screen routes pushed above the shell.
      GoRoute(
        path: '/evenements/:slug',
        parentNavigatorKey: _rootKey,
        builder: (_, s) => EventDetailScreen(slug: s.pathParameters['slug']!),
      ),
      GoRoute(
        path: '/evenements/:slug/billets',
        parentNavigatorKey: _rootKey,
        builder: (_, s) => BuyTicketsScreen(slug: s.pathParameters['slug']!),
      ),
      GoRoute(
        path: '/evenements/:slug/inscription',
        parentNavigatorKey: _rootKey,
        builder: (_, s) => RegisterEventScreen(slug: s.pathParameters['slug']!),
      ),
      GoRoute(
        path: '/evenements/:slug/stands',
        parentNavigatorKey: _rootKey,
        builder: (_, s) => ReserveStandScreen(slug: s.pathParameters['slug']!),
      ),
      GoRoute(
        path: '/paiement/:targetType/:targetId',
        parentNavigatorKey: _rootKey,
        builder: (_, s) => PaymentScreen(
          targetType: s.pathParameters['targetType']!,
          targetId: s.pathParameters['targetId']!,
        ),
      ),
      GoRoute(
        path: '/billets/:id',
        parentNavigatorKey: _rootKey,
        builder: (_, s) => TicketDetailScreen(ticketId: s.pathParameters['id']!),
      ),
      GoRoute(
        path: '/activite',
        parentNavigatorKey: _rootKey,
        builder: (_, __) => const MyActivityScreen(),
      ),
      GoRoute(
        path: '/activite/commandes/:id',
        parentNavigatorKey: _rootKey,
        builder: (_, s) =>
            TicketOrderDetailScreen(orderId: s.pathParameters['id']!),
      ),
      GoRoute(
        path: '/activite/inscriptions/:id',
        parentNavigatorKey: _rootKey,
        builder: (_, s) =>
            RegistrationDetailScreen(registrationId: s.pathParameters['id']!),
      ),
      GoRoute(
        path: '/activite/stands/:id',
        parentNavigatorKey: _rootKey,
        builder: (_, s) =>
            StandReservationDetailScreen(reservationId: s.pathParameters['id']!),
      ),
      GoRoute(
        path: '/factures',
        parentNavigatorKey: _rootKey,
        builder: (_, __) => const InvoicesScreen(),
      ),
      GoRoute(
        path: '/structures',
        parentNavigatorKey: _rootKey,
        builder: (_, __) => const StructuresScreen(),
      ),
      GoRoute(
        path: '/structures/nouvelle',
        parentNavigatorKey: _rootKey,
        builder: (_, __) => const StructureFormScreen(),
      ),
      GoRoute(
        path: '/structures/:id',
        parentNavigatorKey: _rootKey,
        builder: (_, s) =>
            StructureDetailScreen(structureId: s.pathParameters['id']!),
      ),
      GoRoute(
        path: '/structures/:id/modifier',
        parentNavigatorKey: _rootKey,
        builder: (_, s) =>
            StructureFormScreen(structureId: s.pathParameters['id']!),
      ),
      GoRoute(
        path: '/devenir-organisateur',
        parentNavigatorKey: _rootKey,
        builder: (_, __) => const BecomeOrganizerScreen(),
      ),
      GoRoute(
        path: '/mes-evenements',
        parentNavigatorKey: _rootKey,
        builder: (_, __) => const OrganizerEventsScreen(),
      ),
      GoRoute(
        path: '/mes-evenements/nouveau',
        parentNavigatorKey: _rootKey,
        builder: (_, __) => const EventFormScreen(),
      ),
      GoRoute(
        path: '/mes-evenements/:id',
        parentNavigatorKey: _rootKey,
        builder: (_, s) => EventManageScreen(eventId: s.pathParameters['id']!),
      ),
      GoRoute(
        path: '/mes-evenements/:id/infos',
        parentNavigatorKey: _rootKey,
        builder: (_, s) => EventFormScreen(eventId: s.pathParameters['id']!),
      ),
      GoRoute(
        path: '/mes-evenements/:id/programme',
        parentNavigatorKey: _rootKey,
        builder: (_, s) =>
            ProgrammeEditorScreen(eventId: s.pathParameters['id']!),
      ),
      GoRoute(
        path: '/mes-evenements/:id/billetterie',
        parentNavigatorKey: _rootKey,
        builder: (_, s) => TicketsEditorScreen(eventId: s.pathParameters['id']!),
      ),
      GoRoute(
        path: '/mes-evenements/:id/stands',
        parentNavigatorKey: _rootKey,
        builder: (_, s) => StandsEditorScreen(eventId: s.pathParameters['id']!),
      ),
      GoRoute(
        path: '/mes-evenements/:id/intervenants',
        parentNavigatorKey: _rootKey,
        builder: (_, s) => PeopleEditorScreen(
            eventId: s.pathParameters['id']!, kind: 'speakers'),
      ),
      GoRoute(
        path: '/mes-evenements/:id/partenaires',
        parentNavigatorKey: _rootKey,
        builder: (_, s) => PeopleEditorScreen(
            eventId: s.pathParameters['id']!, kind: 'partners'),
      ),
      GoRoute(
        path: '/profil/modifier',
        parentNavigatorKey: _rootKey,
        builder: (_, __) => const EditProfileScreen(),
      ),
      GoRoute(
        path: '/profil/mot-de-passe',
        parentNavigatorKey: _rootKey,
        builder: (_, __) => const ChangePasswordScreen(),
      ),
      GoRoute(
        path: '/scanner',
        parentNavigatorKey: _rootKey,
        builder: (_, __) => const ScannerHomeScreen(),
      ),
      GoRoute(
        path: '/scanner/:eventId',
        parentNavigatorKey: _rootKey,
        builder: (_, s) => ScannerScreen(
          eventId: s.pathParameters['eventId']!,
          eventNom: s.uri.queryParameters['nom'] ?? 'Événement',
        ),
      ),
    ],
  );
});
