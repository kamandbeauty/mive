import 'package:flutter_test/flutter_test.dart';
import 'package:puzzle/game/models/board_obstacle.dart';
import 'package:puzzle/game/models/fruit_kind.dart';
import 'package:puzzle/game/models/level_definition.dart';
import 'package:puzzle/game/services/level_catalog.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('loads and validates 100 complete, ordered level definitions', () async {
    final catalog = await LevelCatalog.load();
    expect(catalog.levels, hasLength(100));
    expect(catalog.validate(), isEmpty);
    expect(catalog.byId(1)?.difficulty.name, 'tutorial');
    expect(catalog.byId(10)?.difficulty.name, 'tutorial');
    expect(catalog.byId(11)?.difficulty.name, 'easy');
    expect(catalog.byId(20)?.difficulty.name, 'easy');
    expect(catalog.byId(21)?.difficulty.name, 'medium');
    expect(catalog.byId(60)?.difficulty.name, 'medium');
    expect(catalog.byId(61)?.difficulty.name, 'hard');
    expect(catalog.byId(100)?.id, 100);
    expect(catalog.byId(101), isNull);
    for (final level in catalog.levels) {
      expect(level.fruitGoals, isNotEmpty);
      expect(level.starThresholds, hasLength(3));
      expect(level.starThresholds.first, level.targetScore);
      expect(level.starThresholds[0], lessThan(level.starThresholds[1]));
      expect(level.starThresholds[1], lessThan(level.starThresholds[2]));
    }
  });

  test('stage requires every fruit goal and target score and awards score stars', () async {
    final catalog = await LevelCatalog.load();
    final level = catalog.byId(1)!;
    final complete = {for (final entry in level.fruitGoals.entries) entry.key: entry.value};
    expect(level.isWon(score: level.targetScore - 1, collected: complete), isFalse);
    expect(level.isWon(score: level.targetScore, collected: complete), isTrue);
    expect(level.starsForScore(level.targetScore), 1);
    expect(level.starsForScore(level.starThresholds[1]), 2);
    expect(level.starsForScore(level.starThresholds[2]), 3);
    final incomplete = Map<FruitKind, int>.from(complete)..[complete.keys.first] = 0;
    expect(level.isWon(score: level.targetScore + 500, collected: incomplete), isFalse);
  });

  test('obstacle targets count removed blockers as part of level completion', () async {
    final catalog = await LevelCatalog.load();
    final level = catalog.byId(11)!;
    final fruits = {for (final entry in level.fruitGoals.entries) entry.key: entry.value};
    expect(level.goalsComplete(fruits), isFalse);
    expect(
      level.goalsComplete(fruits, {BoardObstacleType.woodBox: level.obstacleGoals[BoardObstacleType.woodBox]!}),
      isTrue,
    );
    expect(
      level.isWon(
        score: level.targetScore,
        collected: fruits,
        obstaclesCollected: {BoardObstacleType.woodBox: 0},
      ),
      isFalse,
    );
  });

  test('100-level phases introduce blockers and powers in the planned bands', () async {
    final catalog = await LevelCatalog.load();
    for (var id = 1; id <= 10; id++) {
      final level = catalog.byId(id)!;
      expect(level.difficulty, LevelDifficulty.tutorial, reason: 'level $id');
      expect(level.obstacles, isEmpty, reason: 'tutorial level $id');
      expect(level.specialPieces, isEmpty, reason: 'tutorial level $id');
      expect(level.fruitGoals.length, lessThanOrEqualTo(2), reason: 'tutorial level $id');
      expect(level.moveLimit, greaterThanOrEqualTo(28), reason: 'tutorial level $id');
    }

    for (var id = 11; id <= 20; id++) {
      final level = catalog.byId(id)!;
      expect(level.obstacles.values.map((obstacle) => obstacle.type).toSet(), {
        BoardObstacleType.woodBox,
      }, reason: 'level $id');
      expect(level.specialPieces, isEmpty, reason: 'level $id');
    }
    expect(catalog.byId(11)!.obstacles.values.map((obstacle) => obstacle.layers).toSet(), {1, 2});

    for (var id = 21; id <= 40; id++) {
      final level = catalog.byId(id)!;
      expect(level.obstacles.values.map((obstacle) => obstacle.type).toSet(), {
        BoardObstacleType.ice,
        BoardObstacleType.vine,
      }, reason: 'level $id');
      expect(level.specialPieces, isEmpty, reason: 'level $id');
    }

    for (var id = 41; id <= 60; id++) {
      final level = catalog.byId(id)!;
      expect(level.fruitGoals.length, greaterThanOrEqualTo(2), reason: 'level $id');
      expect(level.obstacles.values.map((obstacle) => obstacle.type).toSet(), {
        BoardObstacleType.woodBox,
        BoardObstacleType.ice,
        BoardObstacleType.vine,
        BoardObstacleType.soil,
      }, reason: 'level $id');
    }

    for (var id = 61; id <= 80; id++) {
      final level = catalog.byId(id)!;
      expect(level.specialPieces.length, greaterThanOrEqualTo(2), reason: 'level $id');
      expect(level.obstacles, isNotEmpty, reason: 'level $id');
    }
    for (var id = 81; id <= 100; id++) {
      final level = catalog.byId(id)!;
      expect(level.fruitGoals.length, 3, reason: 'level $id');
      expect(level.specialPieces.length, greaterThanOrEqualTo(2), reason: 'level $id');
      expect(level.obstacles.length, greaterThanOrEqualTo(4), reason: 'level $id');
    }
  });

  test('all 100 level setups are clean, playable and honor blocker placement', () async {
    final catalog = await LevelCatalog.load();
    expect(catalog.validateStartingBoards(samples: 2), isEmpty);
    for (var id = 11; id <= 100; id++) {
      final level = catalog.byId(id)!;
      expect(level.obstacles, isNotEmpty, reason: 'level $id');
      expect(level.obstacleGoals, isNotEmpty, reason: 'level $id');
    }
    for (var id = 61; id <= 100; id++) {
      expect(catalog.byId(id)!.specialPieces, isNotEmpty, reason: 'level $id');
    }
  });
}
