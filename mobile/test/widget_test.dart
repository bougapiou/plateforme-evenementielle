import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:plateforme_mobile/main.dart';

void main() {
  testWidgets('App boots and shows the events home screen', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: PlateformeApp()));
    await tester.pump();

    expect(find.text('Événements'), findsOneWidget);
  });
}
