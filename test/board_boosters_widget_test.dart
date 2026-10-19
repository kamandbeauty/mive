import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:puzzle/game/models/board_obstacle.dart';
import 'package:puzzle/game/models/economy_progress.dart';
import 'package:puzzle/game/models/fruit_kind.dart';
import 'package:puzzle/game/models/level_definition.dart';
import 'package:puzzle/ui/features/gem_match/gem_match_game.dart';

Future<void> _pumpFor(WidgetTester tester, Duration duration) async {
  const frame = Duration(milliseconds: 100);
  var elapsed = Duration.zero;
  while (elapsed < duration) {
    final remaining = duration - elapsed;
    final step = remaining < frame ? remaining : frame;
    await tester.pump(step);
    elapsed += step;
    expect(tester.takeException(), isNull, reason: 'after ${elapsed.inMilliseconds}ms');
  }
}

const _tutorialLevel = LevelDefinition(
  id: 1,
  chapter: 'tutorial',
  moveLimit: 30,
  targetScore: 100000,
  fruitGoals: {FruitKind.strawberry: 64},
  starThresholds: [100000, 110000, 120000],
  difficulty: LevelDifficulty.tutorial,
);

const _oneMoveLevel = LevelDefinition(
  id: 2,
  chapter: 'tutorial',
  moveLimit: 1,
  targetScore: 100000,
  fruitGoals: {FruitKind.strawberry: 64},
  starThresholds: [100000, 110000, 120000],
  difficulty: LevelDifficulty.tutorial,
);

const _crateLevel = LevelDefinition(
  id: 11,
  chapter: 'grove',
  moveLimit: 30,
  targetScore: 100000,
  fruitGoals: {FruitKind.strawberry: 64},
  starThresholds: [100000, 110000, 120000],
  difficulty: LevelDifficulty.easy,
  obstacles: {0: BoardObstacle(type: BoardObstacleType.woodBox)},
  obstacleGoals: {BoardObstacleType.woodBox: 1},
);

String _boosterLabel(WidgetTester tester, BoosterType type) => tester
    .widget<Semantics>(find.byKey(ValueKey<String>('booster-${type.name}')))
    .properties
    .label!;

void main() {
  testWidgets('purchased boosters are consumed on the board and update the UI', (tester) async {
    final used = <BoosterType>[];
    await tester.pumpWidget(
      MaterialApp(
        home: GemMatchGame(
          level: _tutorialLevel,
          randomSeed: 7,
          boosterInventory: const {
            BoosterType.hammer: 1,
            BoosterType.shuffle: 1,
            BoosterType.extraMoves: 2,
          },
          onUseBooster: (booster) async {
            used.add(booster);
            return true;
          },
        ),
      ),
    );
    await _pumpFor(tester, const Duration(milliseconds: 300));

    expect(find.byKey(const ValueKey<String>('booster-hammer')), findsOneWidget);
    expect(find.byKey(const ValueKey<String>('booster-shuffle')), findsOneWidget);
    expect(find.byKey(const ValueKey<String>('booster-extraMoves')), findsOneWidget);
    expect(find.text('۳۰'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey<String>('booster-hammer')));
    await tester.pump();
    expect(_boosterLabel(tester, BoosterType.hammer), contains('فعال'));
    await tester.tap(find.byKey(const ValueKey<String>('fruit-cell-10')));
    await _pumpFor(tester, const Duration(seconds: 3));
    expect(used, [BoosterType.hammer]);
    expect(_boosterLabel(tester, BoosterType.hammer), contains('۰ عدد'));

    await tester.tap(find.byKey(const ValueKey<String>('booster-shuffle')));
    await _pumpFor(tester, const Duration(milliseconds: 600));
    expect(used, [BoosterType.hammer, BoosterType.shuffle]);
    expect(_boosterLabel(tester, BoosterType.shuffle), contains('۰ عدد'));

    await tester.tap(find.byKey(const ValueKey<String>('booster-extraMoves')));
    await _pumpFor(tester, const Duration(milliseconds: 600));
    expect(used, [BoosterType.hammer, BoosterType.shuffle, BoosterType.extraMoves]);
    expect(find.text('۳۵'), findsOneWidget);
    expect(_boosterLabel(tester, BoosterType.extraMoves), contains('۱ عدد'));
    expect(tester.takeException(), isNull);
  });

  testWidgets('hammer damages a wood box goal and the booster bar stays playable', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: GemMatchGame(
          level: _crateLevel,
          randomSeed: 13,
          boosterInventory: const {BoosterType.hammer: 2},
          onUseBooster: (booster) async => true,
        ),
      ),
    );
    await _pumpFor(tester, const Duration(milliseconds: 300));

    expect(find.text('جعبهٔ چوبی ۰ از ۱'), findsOneWidget);
    expect(
      tester.widget<Semantics>(find.byKey(const ValueKey<String>('fruit-cell-0'))).properties.enabled,
      isFalse,
    );

    await tester.tap(find.byKey(const ValueKey<String>('booster-hammer')));
    await tester.pump();
    expect(
      tester.widget<Semantics>(find.byKey(const ValueKey<String>('fruit-cell-0'))).properties.enabled,
      isTrue,
    );
    await tester.tap(find.byKey(const ValueKey<String>('fruit-cell-0')));
    await _pumpFor(tester, const Duration(seconds: 3));

    expect(find.text('جعبهٔ چوبی ۱ از ۱'), findsOneWidget);
    expect(_boosterLabel(tester, BoosterType.hammer), contains('۱ عدد'));
    expect(find.byKey(const ValueKey<String>('booster-hammer')), findsOneWidget);
    expect(find.byTooltip('مکث'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a denied booster write keeps the inventory and reports no reward', (tester) async {
    var calls = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: GemMatchGame(
          level: _tutorialLevel,
          randomSeed: 9,
          boosterInventory: const {BoosterType.shuffle: 1},
          onUseBooster: (booster) async {
            calls++;
            return false;
          },
        ),
      ),
    );
    await _pumpFor(tester, const Duration(milliseconds: 300));

    await tester.tap(find.byKey(const ValueKey<String>('booster-shuffle')));
    await _pumpFor(tester, const Duration(milliseconds: 500));

    expect(calls, 1);
    expect(_boosterLabel(tester, BoosterType.shuffle), contains('۱ عدد'));
    expect(
      find.text('استفاده از تقویت‌کننده ثبت نشد؛ دوباره تلاش کن'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('purchased extra moves revive a lost level from the result dialog', (tester) async {
    final used = <BoosterType>[];
    await tester.pumpWidget(
      MaterialApp(
        home: GemMatchGame(
          level: _oneMoveLevel,
          randomSeed: 11,
          boosterInventory: const {BoosterType.extraMoves: 1},
          onUseBooster: (booster) async {
            used.add(booster);
            return true;
          },
        ),
      ),
    );
    await _pumpFor(tester, const Duration(milliseconds: 300));

    await tester.tap(find.byTooltip('راهنمایی'));
    await tester.pump();
    final hinted = tester
        .widgetList<Semantics>(
          find.byWidgetPredicate(
            (widget) =>
                widget is Semantics && widget.properties.label == 'حرکت پیشنهادی',
          ),
        )
        .toList(growable: false);
    expect(hinted, hasLength(2));
    await tester.tap(find.byKey(hinted.first.key!));
    await tester.tap(find.byKey(hinted.last.key!));
    await _pumpFor(tester, const Duration(seconds: 4));

    expect(find.text('یک بار دیگر امتحان کن'), findsOneWidget);
    await tester.tap(find.textContaining('حرکت اضافه از کیف'));
    await _pumpFor(tester, const Duration(milliseconds: 600));

    expect(used, [BoosterType.extraMoves]);
    expect(find.text('یک بار دیگر امتحان کن'), findsNothing);
    expect(find.text('۵'), findsOneWidget);
    expect(find.byTooltip('مکث'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
