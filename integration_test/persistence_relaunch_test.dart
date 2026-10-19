import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:puzzle/game/services/progress_repository.dart';
import 'package:puzzle/main.dart' as app;
import 'package:puzzle/ui/features/level_map/level_map_screen.dart';

Future<void> _pumpFor(WidgetTester tester, Duration duration) async {
  const frame = Duration(milliseconds: 100);
  var elapsed = Duration.zero;
  while (elapsed < duration) {
    final remaining = duration - elapsed;
    final step = remaining < frame ? remaining : frame;
    await tester.pump(step);
    elapsed += step;
  }
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('saved level frontier survives force-stop and a fresh app launch', (tester) async {
    final beforeLaunch = await ProgressRepository().load();
    expect(beforeLaunch.lastUnlockedLevel, 2);
    expect(beforeLaunch.lastPlayedLevel, 2);
    expect(beforeLaunch.starsFor(1), greaterThan(0));
    expect(beforeLaunch.highScoreFor(1), greaterThan(0));

    app.main();
    await _pumpFor(tester, const Duration(seconds: 2));
    expect(find.textContaining('ادامه از مرحلهٔ ۲'), findsOneWidget);

    await tester.tap(find.text('دیدن نقشهٔ مراحل'));
    await _pumpFor(tester, const Duration(seconds: 2));
    expect(find.byType(LevelMapScreen), findsOneWidget);
    final secondNode = tester.widget<Semantics>(
      find.byKey(const ValueKey<String>('level-node-2')),
    );
    expect(secondNode.properties.enabled, isTrue);
    final thirdNode = tester.widget<Semantics>(
      find.byKey(const ValueKey<String>('level-node-3')),
    );
    expect(thirdNode.properties.enabled, isFalse);
    expect(tester.takeException(), isNull);
  });
}
