import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:puzzle/game/models/board_obstacle.dart';
import 'package:puzzle/game/models/economy_progress.dart';
import 'package:puzzle/game/models/fruit_kind.dart';
import 'package:puzzle/game/models/level_definition.dart';
import 'package:puzzle/game/models/special_power.dart';
import 'package:puzzle/game/services/level_catalog.dart';
import 'package:puzzle/game/services/progress_repository.dart';
import 'package:puzzle/main.dart' as app;
import 'package:puzzle/ui/features/gem_match/gem_match_game.dart';
import 'package:puzzle/ui/features/home/home_screen.dart';
import 'package:puzzle/ui/widgets/guide_character/guide_character.dart';

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

Future<void> _tapHintedMove(WidgetTester tester) async {
  await tester.tap(find.byTooltip('راهنمایی'));
  await tester.pump(const Duration(milliseconds: 100));
  final suggestedCells = tester
      .widgetList<Semantics>(
        find.byWidgetPredicate(
          (widget) =>
              widget is Semantics &&
              widget.properties.label == 'حرکت پیشنهادی',
        ),
      )
      .toList(growable: false);
  expect(suggestedCells, hasLength(2));
  await tester.tap(find.byKey(suggestedCells.first.key!));
  await tester.tap(find.byKey(suggestedCells.last.key!));
  await _pumpFor(tester, const Duration(seconds: 2));
}

Future<LevelCatalog> _completionFixtureCatalog() async {
  final decoded = jsonDecode(
    await rootBundle.loadString(LevelCatalog.assetPath),
  ) as Map<String, dynamic>;
  final levels = (decoded['levels'] as List<dynamic>)
      .map((raw) => Map<String, Object?>.from(raw as Map))
      .toList(growable: false);
  levels[0] = {
    ...levels[0],
    'moveLimit': 30,
    'targetScore': 1,
    'fruitGoals': {for (final kind in FruitKind.values) kind.name: 1},
    'starThresholds': [1, 2, 3],
  };
  return LevelCatalog.parse(jsonEncode({'version': 1, 'levels': levels}));
}

LevelDefinition _gameFixture({
  required int id,
  int moveLimit = 30,
  int targetScore = 1000000,
  Map<FruitKind, int>? fruitGoals,
  Map<int, SpecialPower> specialPieces = const {},
  Map<int, BoardObstacle> obstacles = const {},
  Map<BoardObstacleType, int> obstacleGoals = const {},
}) {
  final score = targetScore;
  return LevelDefinition(
    id: id,
    chapter: 'tutorial',
    moveLimit: moveLimit,
    targetScore: score,
    fruitGoals: fruitGoals ?? {for (final kind in FruitKind.values) kind: 64},
    starThresholds: [score, score + 100, score + 200],
    difficulty: LevelDifficulty.tutorial,
    specialPieces: specialPieces,
    obstacles: obstacles,
    obstacleGoals: obstacleGoals,
  );
}

Future<void> _mountGame(
  WidgetTester tester,
  LevelDefinition level, {
  int seed = 424242,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      home: MediaQuery(
        data: const MediaQueryData(disableAnimations: true),
        child: GemMatchGame(
          key: UniqueKey(),
          level: level,
          randomSeed: seed,
        ),
      ),
    ),
  );
  await _pumpFor(tester, const Duration(milliseconds: 300));
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('instrumented app opens the map, starts a level and resolves a legal move', (tester) async {
    final semantics = tester.ensureSemantics();
    try {
      app.main();
      await _pumpFor(tester, const Duration(seconds: 2));
      debugPrint('E2E: production app home rendered');

      expect(find.text('میوه‌چین'), findsOneWidget);
      expect(find.bySemanticsLabel('نشان سبد میوهٔ میوه‌چین'), findsOneWidget);
      expect(find.textContaining('ادامه از مرحله'), findsOneWidget);
      await tester.tap(find.textContaining('ادامه از مرحله'));
      await _pumpFor(tester, const Duration(seconds: 2));
      expect(find.byType(GridView), findsOneWidget);
      await tester.binding.handlePopRoute();
      await _pumpFor(tester, const Duration(seconds: 1));
      expect(find.text('میوه‌چین'), findsOneWidget);
      await tester.tap(find.text('دیدن نقشهٔ مراحل'));
      await _pumpFor(tester, const Duration(seconds: 2));
      debugPrint('E2E: production stage map opened');
      expect(find.text('باغ میوه‌چین'), findsOneWidget);
      final firstNode = tester.widget<Semantics>(
        find.byKey(const ValueKey<String>('level-node-1')),
      );
      final secondNode = tester.widget<Semantics>(
        find.byKey(const ValueKey<String>('level-node-2')),
      );
      expect(firstNode.properties.enabled, isTrue);
      expect(secondNode.properties.enabled, isFalse);

      await tester.tap(find.byKey(const ValueKey<String>('level-node-1')));
      await _pumpFor(tester, const Duration(seconds: 2));
      debugPrint('E2E: production level 1 started');
      expect(find.byType(GridView), findsOneWidget);
      expect(find.text('حرکت باقی‌مانده'), findsOneWidget);
      expect(find.text('۳۰'), findsOneWidget);
      expect(find.byType(SingleChildScrollView), findsNothing);
      await tester.tap(find.byTooltip('مکث'));
      await _pumpFor(tester, const Duration(milliseconds: 400));
      expect(find.text('بازی مکث شد'), findsOneWidget);
      await tester.tap(find.text('ادامهٔ بازی'));
      await _pumpFor(tester, const Duration(milliseconds: 400));
      expect(find.text('بازی مکث شد'), findsNothing);

      await tester.tap(find.byTooltip('راهنمایی'));
      await tester.pump(const Duration(milliseconds: 150));
      expect(find.text('این حرکت را امتحان کن.'), findsOneWidget);
      expect(find.text('این دو میوه را با هم جابه‌جا کن'), findsOneWidget);
      final suggestedCells = tester
          .widgetList<Semantics>(
            find.byWidgetPredicate(
              (widget) =>
                  widget is Semantics &&
                  widget.properties.label == 'حرکت پیشنهادی',
            ),
          )
          .toList(growable: false);
      expect(suggestedCells, hasLength(2));
      await tester.tap(find.byKey(suggestedCells.first.key!));
      await tester.tap(find.byKey(suggestedCells.last.key!));
      await _pumpFor(tester, const Duration(seconds: 3));
      debugPrint('E2E: one legal production move resolved');
      expect(find.text('۲۹'), findsOneWidget);
      final scoreValue = tester
          .widget<Text>(
            find.descendant(
              of: find.byKey(const ValueKey<String>('stat-score-value')),
              matching: find.byType(Text),
            ).first,
          )
          .data;
      final targetValue = tester
          .widget<Text>(
            find.descendant(
              of: find.byKey(const ValueKey<String>('stat-target-value')),
              matching: find.byType(Text),
            ).first,
          )
          .data;
      expect(scoreValue, isNot('۰'));
      expect(targetValue, '۱۳۶');
      expect(find.byType(GuideCharacter), findsOneWidget);
      expect(find.byType(SingleChildScrollView), findsNothing);
      expect(tester.takeException(), isNull);
    } finally {
      semantics.dispose();
    }
  });

  testWidgets('deterministic production map flow wins, saves progress and unlocks next level', (tester) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.clear();
    final catalog = await _completionFixtureCatalog();
    await tester.pumpWidget(
      app.GemMatchApp(
        home: HomeScreen(
          catalogLoader: () async => catalog,
          progressLoader: ProgressRepository().load,
          economyLoader: () async => const EconomyProgress(),
          randomSeed: 20261018,
        ),
      ),
    );
    await _pumpFor(tester, const Duration(seconds: 2));

    await tester.tap(find.text('دیدن نقشهٔ مراحل'));
    await _pumpFor(tester, const Duration(seconds: 2));
    await tester.tap(find.byKey(const ValueKey<String>('level-node-1')));
    await _pumpFor(tester, const Duration(seconds: 2));

    var turns = 0;
    while (find.byType(AlertDialog).evaluate().isEmpty && turns < 30) {
      await _tapHintedMove(tester);
      turns++;
      debugPrint('E2E: deterministic completion fixture move $turns');
    }
    expect(turns, greaterThan(0));
    expect(find.text('آفرین، باغبان ماهر!'), findsOneWidget);
    expect(find.text('مرحلهٔ بعدی در نقشه باز شد.'), findsOneWidget);
    expect(tester.takeException(), isNull);

    final saved = await ProgressRepository().load();
    expect(saved.lastUnlockedLevel, 2);
    expect(saved.lastPlayedLevel, 2);
    expect(saved.starsFor(1), greaterThan(0));
    expect(saved.highScoreFor(1), greaterThan(0));

    await tester.tap(find.widgetWithText(TextButton, 'نقشهٔ مراحل'));
    await _pumpFor(tester, const Duration(seconds: 2));
    final unlockedSecondNode = tester.widget<Semantics>(
      find.byKey(const ValueKey<String>('level-node-2')),
    );
    expect(unlockedSecondNode.properties.enabled, isTrue);
    await tester.tap(find.byKey(const ValueKey<String>('level-node-2')));
    await _pumpFor(tester, const Duration(seconds: 2));
    expect(find.text('مرحله ۲'), findsOneWidget);
    expect(await ProgressRepository().load().then((value) => value.lastPlayedLevel), 2);
    expect(tester.takeException(), isNull);
  });

  testWidgets('real loss offers retry and returns to the previous route', (tester) async {
    final losingLevel = _gameFixture(
      id: 3,
      moveLimit: 1,
      targetScore: 1000000,
      fruitGoals: {FruitKind.strawberry: 64},
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: TextButton(
                key: const ValueKey<String>('open-loss-fixture'),
                onPressed: () => Navigator.of(context).push<void>(
                  MaterialPageRoute<void>(
                    builder: (_) => GemMatchGame(level: losingLevel, randomSeed: 39),
                  ),
                ),
                child: const Text('QA fixture home'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.byKey(const ValueKey<String>('open-loss-fixture')));
    await _pumpFor(tester, const Duration(milliseconds: 500));
    await _tapHintedMove(tester);
    expect(find.text('یک بار دیگر امتحان کن'), findsOneWidget);
    expect(find.text('هدف‌ها کامل نشدند.'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'تلاش دوباره'), findsOneWidget);

    await tester.tap(find.widgetWithText(FilledButton, 'تلاش دوباره'));
    await _pumpFor(tester, const Duration(milliseconds: 500));
    expect(find.text('یک بار دیگر امتحان کن'), findsNothing);
    expect(find.byTooltip('راهنمایی'), findsOneWidget);
    expect(find.text('۱'), findsOneWidget);

    await _tapHintedMove(tester);
    expect(find.text('یک بار دیگر امتحان کن'), findsOneWidget);
    await tester.tap(find.widgetWithText(TextButton, 'نقشهٔ مراحل'));
    await _pumpFor(tester, const Duration(seconds: 1));
    expect(find.text('QA fixture home'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('all four powers and all ten unordered pairs activate in-game', (tester) async {
    for (final power in SpecialPower.values) {
      await _mountGame(
        tester,
        _gameFixture(id: 4, specialPieces: {0: power}),
        seed: 314159,
      );
      final source = tester.widget<Semantics>(
        find.byKey(const ValueKey<String>('fruit-cell-0')),
      );
      expect(source.properties.label, contains(power.persianName));
      await tester.tap(find.byKey(const ValueKey<String>('fruit-cell-0')));
      await tester.tap(find.byKey(const ValueKey<String>('fruit-cell-1')));
      await _pumpFor(tester, const Duration(milliseconds: 500));
      expect(find.text('۲۹'), findsOneWidget, reason: '$power must consume one move');
      expect(find.byType(AlertDialog), findsNothing);
      expect(tester.takeException(), isNull);
    }

    for (var first = 0; first < SpecialPower.values.length; first++) {
      for (var second = first; second < SpecialPower.values.length; second++) {
        final firstPower = SpecialPower.values[first];
        final secondPower = SpecialPower.values[second];
        await _mountGame(
          tester,
          _gameFixture(
            id: 5,
            specialPieces: {0: firstPower, 1: secondPower},
          ),
          seed: 271828,
        );
        await tester.tap(find.byKey(const ValueKey<String>('fruit-cell-0')));
        await tester.tap(find.byKey(const ValueKey<String>('fruit-cell-1')));
        await _pumpFor(tester, const Duration(milliseconds: 500));
        expect(
          find.text('۲۹'),
          findsOneWidget,
          reason: '$firstPower + $secondPower must be one accepted move',
        );
        expect(find.byType(AlertDialog), findsNothing);
        expect(tester.takeException(), isNull);
        debugPrint('E2E: pair $firstPower + $secondPower activated');
      }
    }
  });

  testWidgets('area bomb damages woodBox, ice, vine and soil in one wave', (tester) async {
    const obstacleGoals = {
      BoardObstacleType.woodBox: 1,
      BoardObstacleType.ice: 1,
      BoardObstacleType.vine: 1,
      BoardObstacleType.soil: 1,
    };
    const obstacles = {
      12: BoardObstacle(type: BoardObstacleType.woodBox),
      13: BoardObstacle(type: BoardObstacleType.ice),
      14: BoardObstacle(type: BoardObstacleType.vine),
      21: BoardObstacle(type: BoardObstacleType.soil),
    };
    await _mountGame(
      tester,
      _gameFixture(
        id: 6,
        specialPieces: const {19: SpecialPower.areaBomb},
        obstacles: obstacles,
        obstacleGoals: obstacleGoals,
      ),
      seed: 161803,
    );

    await tester.tap(find.byKey(const ValueKey<String>('fruit-cell-19')));
    await tester.tap(find.byKey(const ValueKey<String>('fruit-cell-20')));
    await _pumpFor(tester, const Duration(milliseconds: 500));
    for (final type in BoardObstacleType.values) {
      expect(find.text('${type.persianName} ۱ از ۱'), findsOneWidget, reason: '$type should be hit');
    }
    expect(find.text('۲۹'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
