import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:puzzle/game/models/economy_progress.dart';
import 'package:puzzle/game/models/fruit_kind.dart';
import 'package:puzzle/game/models/game_progress.dart';
import 'package:puzzle/game/models/level_definition.dart';
import 'package:puzzle/game/services/level_catalog.dart';
import 'package:puzzle/main.dart';
import 'package:puzzle/ui/features/gem_match/gem_match_game.dart';
import 'package:puzzle/ui/widgets/guide_character/guide_character.dart';
import 'package:puzzle/ui/features/home/home_screen.dart';
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

LevelCatalog _mapTestCatalog() => LevelCatalog.parse(
      jsonEncode({
        'version': 1,
        'levels': [
          for (var id = 1; id <= 100; id++)
            {
              'id': id,
              'chapter': id <= 10
                  ? 'tutorial'
                  : id <= 15
                      ? 'grove'
                      : id <= 30
                          ? 'orchard'
                          : id <= 60
                              ? 'hills'
                              : 'summit',
              'moveLimit': 24,
              'targetScore': 100,
              'fruitGoals': {'strawberry': 5},
              'starThresholds': [100, 140, 180],
              'difficulty': id <= 10
                  ? 'tutorial'
                  : id <= 20
                      ? 'easy'
                      : id <= 60
                          ? 'medium'
                          : 'hard',
            },
        ],
      }),
    );

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets('home opens the last stage and renders the gameplay board', (tester) async {
    await tester.pumpWidget(
      GemMatchApp(
        home: HomeScreen(
          catalogLoader: () async => _mapTestCatalog(),
          progressLoader: () async => const GameProgress(),
          economyLoader: () async => const EconomyProgress(),
        ),
      ),
    );
    await tester.pump();

    expect(find.text('میوه‌چین'), findsOneWidget);
    expect(find.text('دیدن نقشهٔ مراحل'), findsOneWidget);
    expect(
      find.byWidgetPredicate(
        (widget) =>
            widget is Image &&
            widget.semanticLabel == 'نشان سبد میوهٔ میوه‌چین',
      ),
      findsOneWidget,
    );
    final continueButton = find.textContaining('ادامه از مرحله');
    expect(continueButton, findsOneWidget);
    await tester.tap(find.text('دیدن نقشهٔ مراحل'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('باغ میوه‌چین'), findsOneWidget);
    await tester.tap(find.byTooltip('بازگشت به خانه'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    await tester.ensureVisible(continueButton);
    await tester.tap(continueButton);
    await _pumpFor(tester, const Duration(milliseconds: 500));

    expect(find.text('حرکت باقی‌مانده'), findsOneWidget);
    expect(find.byType(GridView), findsOneWidget);
    expect(find.byTooltip('مکث'), findsOneWidget);
  });

  testWidgets('level map shows real level and region locks', (tester) async {
    final catalog = _mapTestCatalog();
    await tester.pumpWidget(
      MaterialApp(
        home: LevelMapScreen(
          catalog: catalog,
          initialProgress: const GameProgress(),
        ),
      ),
    );
    await tester.pump();

    expect(find.text('باغ میوه‌چین'), findsOneWidget);
    expect(find.textContaining('ادامه از مرحله'), findsOneWidget);
    expect(find.byType(ListView), findsOneWidget);
    final lockedLevel = tester.widget<Semantics>(
      find.byKey(const ValueKey<String>('level-node-2')),
    );
    expect(lockedLevel.properties.enabled, isFalse);

    final lockedRegionFinder =
        find.byKey(const ValueKey<String>('chapter-marker-11'));
    await tester.dragUntilVisible(
      lockedRegionFinder,
      find.byType(ListView),
      const Offset(0, -250),
    );
    final lockedRegion = tester.widget<Semantics>(lockedRegionFinder);
    expect(lockedRegion.properties.label, 'باغچه، منطقه قفل است');
  });

  testWidgets('game board fills viewport width and enlarged character fits portrait sizes', (tester) async {
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });
    const level = LevelDefinition(
      id: 1,
      chapter: 'tutorial',
      moveLimit: 24,
      targetScore: 100,
      fruitGoals: {FruitKind.strawberry: 5},
      starThresholds: [100, 140, 180],
      difficulty: LevelDifficulty.tutorial,
    );
    for (final size in [const Size(320, 640), const Size(360, 800), const Size(412, 915)]) {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      await tester.pumpWidget(MaterialApp(home: GemMatchGame(level: level)));
      await _pumpFor(tester, const Duration(milliseconds: 300));
      expect(find.byType(GridView), findsOneWidget, reason: 'viewport $size');
      final board = tester.getSize(
        find.byKey(const ValueKey<String>('game-board-frame')),
      );
      expect(board.width, lessThanOrEqualTo(size.width - 16), reason: 'viewport $size');
      if (size.height == 640) {
        expect(board.width, lessThan(size.width - 16), reason: 'short viewport $size should fit without scrolling');
      } else {
        expect(board.width, closeTo(size.width - 16, 1), reason: 'viewport $size');
      }
      expect(board.height, board.width, reason: 'viewport $size');
      expect(find.byType(SingleChildScrollView), findsNothing, reason: 'viewport $size');
      expect(find.byType(GuideCharacter), findsOneWidget, reason: 'viewport $size');
      final character = tester.widget<GuideCharacter>(find.byType(GuideCharacter));
      final expectedCharacterHeight = size.height < 700
          ? 160
          : size.height < 820
              ? 190
              : 220;
      expect(character.height, expectedCharacterHeight, reason: 'viewport $size');
      expect(tester.takeException(), isNull, reason: 'viewport $size');
    }
  });
}
