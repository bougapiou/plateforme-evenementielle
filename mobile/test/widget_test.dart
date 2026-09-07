import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:plateforme_mobile/core/api_client.dart';
import 'package:plateforme_mobile/core/providers.dart';
import 'package:plateforme_mobile/core/token_store.dart';
import 'package:plateforme_mobile/data/domain.dart';
import 'package:plateforme_mobile/data/repositories.dart';
import 'package:plateforme_mobile/main.dart';

/// Offline stub so the catalogue renders without hitting the network.
class _StubEvents extends EventsRepository {
  _StubEvents() : super(ApiClient(TokenStore()));

  @override
  Future<Paged<EventSummary>> search({
    String? q,
    String? categorie,
    String? ville,
    int page = 0,
  }) async =>
      Paged(content: const [], page: 0, totalPages: 0, totalElements: 0, last: true);

  @override
  Future<List<EventCategory>> categories() async => const [];
}

void main() {
  testWidgets('App boots and shows the events catalogue', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          eventsRepositoryProvider.overrideWithValue(_StubEvents()),
        ],
        child: const PlateformeApp(),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('Événements'), findsWidgets);
    expect(find.text('Aucun événement'), findsOneWidget);
  });
}
