import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:intl/intl.dart';

import 'package:plateforme_mobile/core/api_client.dart';
import 'package:plateforme_mobile/core/models.dart';
import 'package:plateforme_mobile/core/providers.dart';
import 'package:plateforme_mobile/core/theme.dart';
import 'package:plateforme_mobile/core/token_store.dart';
import 'package:plateforme_mobile/data/domain.dart';
import 'package:plateforme_mobile/data/repositories.dart';
import 'package:plateforme_mobile/features/presence/presence_flow.dart';
import 'package:plateforme_mobile/features/presence/presence_screen.dart';

const _qr = {'entrees': 1820, 'sorties': 320, 'presents': 1500, 'reentrees': 14};
const _sensors = {'entrees': 430, 'sorties': 60, 'presents': 370};

/// Offline stub: serves a fixed attendance view (or an error) instead of calling the API.
class _StubCheckin extends CheckinRepository {
  final AttendanceView? view;
  final ApiException? error;
  _StubCheckin({this.view, this.error}) : super(ApiClient(TokenStore()));

  @override
  Future<AttendanceView> publicAttendance(String slug) async {
    if (error != null) throw error!;
    return view!;
  }
}

AttendanceView _view({Map<String, int> sensors = _sensors}) => AttendanceView(
      eventId: 'e1',
      eventNom: 'Faso Culture 2026',
      event: _qr,
      comptagePhysique: sensors,
      activites: [
        ActivityFlow(
          id: 'a1',
          titre: "Cérémonie d'ouverture",
          acces: 'GRATUIT',
          flux: const {'entrees': 640, 'sorties': 20, 'presents': 620, 'reentrees': 3},
        ),
      ],
    );

String _n(int v) => NumberFormat.decimalPattern('fr').format(v);

void main() {
  setUpAll(() => initializeDateFormatting('fr'));

  group('flowOf', () {
    test('ticket source shows the scans only, with re-entries', () {
      final f = flowOf(PresenceSource.qr, _qr, _sensors);
      expect((f.entrees, f.sorties, f.presents, f.reentrees), (1820, 320, 1500, 14));
    });

    test('sensor source shows the sensors only and cannot know re-entries', () {
      final f = flowOf(PresenceSource.physique, _qr, _sensors);
      expect((f.entrees, f.sorties, f.presents, f.reentrees), (430, 60, 370, null));
    });

    test('combined adds every counter; re-entries stay ticket-only', () {
      final f = flowOf(PresenceSource.combine, _qr, _sensors);
      expect((f.entrees, f.sorties, f.presents, f.reentrees), (2250, 380, 1870, 14));
      final c = contributions('entrees', _qr, _sensors);
      expect((c.qr, c.physique), (1820, 430));
    });

    test('missing sensor data counts as zero', () {
      final f = flowOf(PresenceSource.combine, _qr, null);
      expect((f.entrees, f.sorties, f.presents), (1820, 320, 1500));
      expect(hasPhysicalCount(null), isFalse);
      expect(hasPhysicalCount(const {'entrees': 0, 'sorties': 0}), isFalse);
      expect(hasPhysicalCount(const {'entrees': 1, 'sorties': 0}), isTrue);
    });
  });

  group('PresenceScreen', () {
    Future<void> open(WidgetTester tester, _StubCheckin stub) async {
      await tester.pumpWidget(ProviderScope(
        overrides: [checkinRepositoryProvider.overrideWithValue(stub)],
        child: MaterialApp(theme: appTheme, home: const PresenceScreen(slug: 'faso-culture')),
      ));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
    }

    // the screen refreshes on a timer: dispose it so no timer is left pending
    Future<void> close(WidgetTester tester) => tester.pumpWidget(const SizedBox());

    testWidgets('opens on the combined view when sensors have counted, and switches source', (tester) async {
      await open(tester, _StubCheckin(view: _view()));

      expect(find.text('Faso Culture 2026'), findsOneWidget);
      expect(find.text(_n(2250)), findsOneWidget); // entries: 1820 + 430
      expect(find.text(_n(1870)), findsOneWidget); // present: 1500 + 370
      expect(find.text('Billets ${_n(1820)}\nCapteurs 430'), findsOneWidget);

      await tester.tap(find.text('Capteurs'));
      await tester.pump();
      expect(find.text('430'), findsOneWidget);
      expect(find.text('370'), findsOneWidget);
      expect(find.text('Ré-entrées'), findsNothing);

      await tester.tap(find.text('Billets QR'));
      await tester.pump();
      expect(find.text(_n(1820)), findsOneWidget);
      expect(find.text('Ré-entrées'), findsNWidgets(2)); // the counter and the activity card
      expect(find.text("Cérémonie d'ouverture"), findsOneWidget);

      await close(tester);
    });

    testWidgets('opens on tickets when no sensor has counted, and warns on the sensor view', (tester) async {
      await open(tester, _StubCheckin(view: _view(sensors: const {})));

      expect(find.text(_n(1820)), findsOneWidget);
      await tester.tap(find.text('Combiné'));
      await tester.pump();
      expect(find.text("Aucun passage n'a encore été compté par un capteur pour cet événement."),
          findsOneWidget);

      await close(tester);
    });

    testWidgets('full screen shows the dark board and comes back', (tester) async {
      await open(tester, _StubCheckin(view: _view()));

      await tester.tap(find.byIcon(Icons.fullscreen));
      await tester.pump();
      expect(find.text('PRÉSENCE EN DIRECT'), findsOneWidget);
      expect(find.text(_n(2250)), findsOneWidget);
      expect(find.byIcon(Icons.fullscreen_exit), findsOneWidget);

      await tester.tap(find.byIcon(Icons.fullscreen_exit));
      await tester.pump();
      expect(find.text('PRÉSENCE EN DIRECT'), findsNothing);
      expect(find.text('Présence en direct'), findsOneWidget); // the app bar again

      await close(tester);
    });

    testWidgets('an unknown or unpublished event shows a clear message', (tester) async {
      await open(tester, _StubCheckin(error: ApiException(404, 'NOT_FOUND', 'introuvable')));

      expect(find.text('Pas de présence à afficher'), findsOneWidget);

      await close(tester);
    });
  });
}
