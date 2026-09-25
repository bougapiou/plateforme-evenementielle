import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'package:plateforme_mobile/core/api_client.dart';
import 'package:plateforme_mobile/core/providers.dart';
import 'package:plateforme_mobile/core/theme.dart';
import 'package:plateforme_mobile/core/token_store.dart';
import 'package:plateforme_mobile/data/domain.dart';
import 'package:plateforme_mobile/data/organizer_events_repository.dart';
import 'package:plateforme_mobile/features/organizer/event_manage_screen.dart';
import 'package:plateforme_mobile/features/organizer/organizer_events_screen.dart';

EventSummary _summary(String id, String nom, String statut) => EventSummary(
      id: id,
      nom: nom,
      slug: id,
      dateDebut: DateTime(2027, 10, 25, 9),
      dateFin: DateTime(2027, 10, 26, 20),
      statut: statut,
    );

/// Offline stub of the organiser API: a fixed list of events and one event in the given status.
class _StubRepo extends OrganizerEventsRepository {
  final String statut;
  _StubRepo([this.statut = 'BROUILLON']) : super(ApiClient(TokenStore()));

  @override
  Future<Paged<EventSummary>> mine({int page = 0, String? statut}) async => Paged(
        content: [
          _summary('a', 'Salon A', 'BROUILLON'),
          _summary('b', 'Forum B', 'REFUSE'),
          _summary('c', 'Foire C', 'SOUMIS'),
          _summary('d', 'Festival D', 'PUBLIE'),
        ],
        page: 0,
        totalPages: 1,
        totalElements: 4,
        last: true,
      );

  @override
  Future<EventFull> get(String id) async => EventFull(
        id: 'e1',
        nom: 'Salon de test',
        slug: 'salon-de-test',
        dateDebut: DateTime(2027, 10, 25, 9),
        dateFin: DateTime(2027, 10, 26, 20),
        standsActifs: true,
        statut: statut,
      );

  @override
  Future<EventStats> stats(String id) async => EventStats(
        billetsTotal: 300,
        billetsVendus: 240,
        standsTotal: 40,
        standsReserves: 12,
        inscriptionsConfirmees: 86,
        inscriptionsEnAttente: 4,
        revenus: 1875000,
      );
}

Widget _app(Widget home, {String statut = 'BROUILLON', bool admin = false}) => ProviderScope(
      overrides: [
        organizerEventsRepositoryProvider.overrideWithValue(_StubRepo(statut)),
        hasPermissionProvider.overrideWith((ref, p) => admin && p == 'EVENT_VALIDATE'),
      ],
      child: MaterialApp(theme: appTheme, home: home),
    );

void main() {
  setUpAll(() => initializeDateFormatting('fr'));

  group('Mes événements', () {
    testWidgets('groups the statuses in filters with their counts and filters the list', (tester) async {
      tester.view.physicalSize = const Size(1170, 4000);
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(_app(const OrganizerEventsScreen()));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));

      expect(find.text('Salon A'), findsOneWidget);
      expect(find.text('Festival D'), findsOneWidget);
      // "Brouillons" gathers the draft and the refused one; empty groups get no pill
      expect(find.text('Brouillons'), findsOneWidget);
      expect(find.text('2'), findsOneWidget);
      expect(find.text('En cours'), findsNothing);

      await tester.tap(find.text('Brouillons'));
      await tester.pump();
      expect(find.text('Salon A'), findsOneWidget);
      expect(find.text('Forum B'), findsOneWidget);
      expect(find.text('Festival D'), findsNothing);
      expect(find.text('Foire C'), findsNothing);

      await tester.tap(find.text('Tous'));
      await tester.pump();
      expect(find.text('Festival D'), findsOneWidget);

      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('tells the organiser what to do next on each card', (tester) async {
      tester.view.physicalSize = const Size(1170, 4000);
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(_app(const OrganizerEventsScreen()));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));

      expect(find.textContaining('soumettez-la à validation'), findsOneWidget);
      expect(find.textContaining('En attente de validation'), findsOneWidget);

      await tester.pumpWidget(const SizedBox());
    });
  });

  group('Gérer l\'événement', () {
    Future<void> open(WidgetTester tester, String statut, {bool admin = false}) async {
      tester.view.physicalSize = const Size(1170, 7500);
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(_app(const EventManageScreen(eventId: 'e1'), statut: statut, admin: admin));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
    }

    testWidgets('a draft can be submitted or deleted, and shows no figures yet', (tester) async {
      await open(tester, 'BROUILLON');

      expect(find.text('Soumettre à validation'), findsOneWidget);
      expect(find.text('Supprimer le brouillon'), findsOneWidget);
      expect(find.text('Billets vendus'), findsNothing);
      expect(find.text('Contrôle des accès'), findsNothing);

      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('a validated event can be published and is still editable', (tester) async {
      await open(tester, 'VALIDE');

      expect(find.text('Publier'), findsOneWidget);
      expect(find.text('Soumettre à validation'), findsNothing);
      expect(find.text('Verrouillé'), findsNothing);

      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('a submitted event waits for validation and is locked for its organiser', (tester) async {
      await open(tester, 'SOUMIS');

      expect(find.textContaining('En attente de validation'), findsOneWidget);
      expect(find.text('Soumettre à validation'), findsNothing);
      expect(find.text('Publier'), findsNothing);
      expect(find.text('Verrouillé'), findsOneWidget);

      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('a published event shows its figures, live attendance and control', (tester) async {
      await open(tester, 'PUBLIE');

      expect(find.text('Billets vendus'), findsOneWidget);
      expect(find.text('240 / 300'), findsOneWidget);
      expect(find.text('+4 en attente'), findsOneWidget);
      expect(find.text('Stands réservés'), findsOneWidget);
      expect(find.text('Contrôle des accès'), findsOneWidget);
      expect(find.text('Présence en direct'), findsOneWidget);
      expect(find.text('Fermer les inscriptions'), findsOneWidget);
      expect(find.text('Supprimer le brouillon'), findsNothing);

      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('an administrator is never locked out of the event information', (tester) async {
      await open(tester, 'SOUMIS', admin: true);

      expect(find.text('Verrouillé'), findsNothing);
      expect(find.text('Nom, dates, lieu, visuels…'), findsOneWidget);

      await tester.pumpWidget(const SizedBox());
    });
  });
}
