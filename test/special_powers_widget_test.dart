import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:puzzle/game/models/level_definition.dart';
import 'package:puzzle/ui/features/gem_match/gem_match_engine.dart';
import 'package:puzzle/ui/features/gem_match/gem_match_game.dart';

Future<void> _pumpFor(WidgetTester tester, Duration duration) async {
  const frame = Duration(milliseconds: 100);
  var elapsed = Duration.zero;
  while (elapsed < duration) {
    final remaining = duration - elapsed;
    final step = remaining < frame ? remaining : frame;
    await tester.pump(step);
    expect(tester.takeException(), isNull, reason: 'after ${elapsed.inMilliseconds}ms');
    elapsed += step;
  }
}

void main() {
  testWidgets('obstacle tiles expose their type, durability and blocking state', (tester) async {
    const level = LevelDefinition(
      id: 11,
      chapter: 'grove',
      moveLimit: 28,
      targetScore: 100000,
      fruitGoals: {FruitKind.strawberry: 64},
      starThresholds: [100000, 110000, 120000],
      difficulty: LevelDifficulty.easy,
      obstacles: {
        0: BoardObstacle(type: BoardObstacleType.woodBox, layers: 2),
        1: BoardObstacle(type: BoardObstacleType.ice),
        2: BoardObstacle(type: BoardObstacleType.vine, layers: 2),
        3: BoardObstacle(type: BoardObstacleType.soil),
      },
      obstacleGoals: {
        BoardObstacleType.woodBox: 1,
        BoardObstacleType.ice: 1,
        BoardObstacleType.vine: 1,
        BoardObstacleType.soil: 1,
      },
    );
    await tester.pumpWidget(MaterialApp(home: GemMatchGame(level: level)));
    await _pumpFor(tester, const Duration(seconds: 1));

    expect(find.byIcon(Icons.inventory_2_rounded), findsWidgets);
    expect(find.byIcon(Icons.ac_unit_rounded), findsWidgets);
    expect(find.byIcon(Icons.park_rounded), findsWidgets);
    expect(find.byIcon(Icons.grass_rounded), findsWidgets);
    expect(
      tester.widget<Semantics>(find.byKey(const ValueKey<String>('fruit-cell-0'))).properties.label,
      contains('جعبهٔ چوبی، ۲ لایه'),
    );
    expect(
      tester.widget<Semantics>(find.byKey(const ValueKey<String>('fruit-cell-3'))).properties.enabled,
      isTrue,
    );
    expect(
      tester.widget<Semantics>(find.byKey(const ValueKey<String>('fruit-cell-1'))).properties.enabled,
      isFalse,
    );
    expect(find.text('جعبهٔ چوبی ۰/۱'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('pause, resume and confirmed restart return to a playable state', (tester) async {
    const level = LevelDefinition(
      id: 1,
      chapter: 'tutorial',
      moveLimit: 30,
      targetScore: 100000,
      fruitGoals: {FruitKind.strawberry: 64},
      starThresholds: [100000, 110000, 120000],
      difficulty: LevelDifficulty.tutorial,
    );
    await tester.pumpWidget(MaterialApp(home: GemMatchGame(level: level)));
    await _pumpFor(tester, const Duration(milliseconds: 300));

    final hintButton = tester.widget<IconButton>(
      find.byWidgetPredicate(
        (widget) => widget is IconButton && widget.tooltip == 'راهنمایی',
      ),
    );
    expect(hintButton.constraints?.maxWidth, 34);
    expect(find.text('شروع دوباره'), findsNothing);
    await tester.tap(find.byTooltip('مکث'));
    await _pumpFor(tester, const Duration(milliseconds: 400));
    expect(find.text('بازی مکث شد'), findsOneWidget);
    expect(find.text('شروع دوباره'), findsOneWidget);

    await tester.tap(find.text('ادامهٔ بازی'));
    await _pumpFor(tester, const Duration(milliseconds: 400));
    expect(find.text('بازی مکث شد'), findsNothing);

    await tester.tap(find.byTooltip('مکث'));
    await _pumpFor(tester, const Duration(milliseconds: 400));
    await tester.tap(find.text('شروع دوباره'));
    await _pumpFor(tester, const Duration(milliseconds: 400));
    expect(find.text('شروع دوباره؟'), findsOneWidget);
    await tester.tap(
      find.descendant(
        of: find.byType(AlertDialog),
        matching: find.text('ادامهٔ بازی'),
      ),
    );
    await _pumpFor(tester, const Duration(milliseconds: 400));
    expect(find.text('بازی مکث شد'), findsOneWidget);

    await tester.tap(find.text('شروع دوباره'));
    await _pumpFor(tester, const Duration(milliseconds: 400));
    await tester.tap(find.widgetWithText(FilledButton, 'شروع دوباره'));
    await _pumpFor(tester, const Duration(milliseconds: 400));
    expect(find.text('شروع دوباره؟'), findsNothing);
    expect(find.text('بازی مکث شد'), findsNothing);
    expect(find.byTooltip('مکث'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('fruit drag swaps a hinted pair without scrolling the board', (tester) async {
    const level = LevelDefinition(
      id: 1,
      chapter: 'tutorial',
      moveLimit: 30,
      targetScore: 100000,
      fruitGoals: {FruitKind.strawberry: 64},
      starThresholds: [100000, 110000, 120000],
      difficulty: LevelDifficulty.tutorial,
    );
    await tester.pumpWidget(MaterialApp(home: GemMatchGame(level: level)));
    await _pumpFor(tester, const Duration(milliseconds: 300));

    expect(
      find.text('دو میوهٔ کنار هم را لمس کن یا یکی را به سمت میوهٔ کناری بکش'),
      findsNothing,
    );
    final board = find.byKey(const ValueKey<String>('game-board-frame'));
    final boardTopBefore = tester.getTopLeft(board).dy;
    await tester.tap(find.byTooltip('راهنمایی'));
    await tester.pump();
    final hintMessage = find.text('این دو میوه را با هم جابه‌جا کن');
    expect(hintMessage, findsOneWidget);
    expect(
      tester.getTopLeft(hintMessage).dy,
      greaterThanOrEqualTo(tester.getBottomLeft(board).dy),
    );

    final hintedCells = tester.widgetList<Semantics>(
      find.byWidgetPredicate(
        (widget) => widget is Semantics && widget.properties.label == 'حرکت پیشنهادی',
      ),
    );
    expect(hintedCells, hasLength(2));
    final centers = hintedCells
        .map((cell) => tester.getCenter(find.byKey(cell.key!)))
        .toList(growable: false);
    await tester.dragFrom(
      centers.first,
      centers.last - centers.first,
    );
    await _pumpFor(tester, const Duration(seconds: 3));

    expect(find.text('۲۹'), findsOneWidget);
    expect(tester.getTopLeft(board).dy, closeTo(boardTopBefore, 0.1));
    expect(tester.takeException(), isNull);
  });

  testWidgets('configured specials activate and damage locks once per cell', (tester) async {
    const level = LevelDefinition(
      id: 6,
      chapter: 'grove',
      moveLimit: 30,
      targetScore: 100000,
      fruitGoals: {FruitKind.strawberry: 64},
      starThresholds: [100000, 110000, 120000],
      difficulty: LevelDifficulty.easy,
      specialPieces: {
        0: SpecialPower.areaBomb,
        18: SpecialPower.lineClear,
        27: SpecialPower.colorBomb,
      },
      lockedCells: {9: 1, 63: 2},
    );
    await tester.pumpWidget(MaterialApp(home: GemMatchGame(level: level)));
    await _pumpFor(tester, const Duration(seconds: 1));

    expect(find.byIcon(Icons.brightness_7_rounded), findsOneWidget);
    expect(find.byIcon(Icons.horizontal_rule_rounded), findsOneWidget);
    expect(find.byIcon(Icons.album_rounded), findsOneWidget);
    expect(find.byIcon(Icons.lock_rounded), findsOneWidget);
    expect(find.byIcon(Icons.lock_outline_rounded), findsOneWidget);
    expect(
      tester.widget<Semantics>(find.byKey(const ValueKey<String>('fruit-cell-9'))).properties.label,
      contains('قفل ۱ لایه'),
    );

    await tester.tap(find.byKey(const ValueKey<String>('fruit-cell-0')));
    await tester.tap(find.byKey(const ValueKey<String>('fruit-cell-1')));
    await tester.pump();
    expect(find.text('آفرین! چه ترکیب خوبی.'), findsOneWidget);
    await _pumpFor(tester, const Duration(seconds: 3));

    expect(find.text('۲۹'), findsOneWidget);
    final openedLock = tester.widget<Semantics>(
      find.byKey(const ValueKey<String>('fruit-cell-9')),
    );
    final intactLock = tester.widget<Semantics>(
      find.byKey(const ValueKey<String>('fruit-cell-63')),
    );
    expect(openedLock.properties.label, isNot(contains('قفل')));
    expect(intactLock.properties.label, contains('قفل ۲ لایه'));
    expect(find.byIcon(Icons.lock_rounded), findsOneWidget);
    expect(find.byIcon(Icons.lock_outline_rounded), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
