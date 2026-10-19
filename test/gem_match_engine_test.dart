import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:puzzle/ui/features/gem_match/gem_match_engine.dart';

List<FruitKind> _stableBoard() => List.generate(
      GemMatchEngine.cellCount,
      (index) => FruitKind.values[(index ~/ GemMatchEngine.columns + index % GemMatchEngine.columns) % FruitKind.values.length],
    );

void main() {
  group('GemMatchEngine', () {
    test('starts with a full board, no matches, and at least one legal move', () {
      for (var seed = 0; seed < 100; seed++) {
        final engine = GemMatchEngine(random: Random(seed));
        expect(engine.board.length, GemMatchEngine.rows);
        expect(engine.board.every((row) => row.length == GemMatchEngine.columns), isTrue);
        expect(engine.board.expand((row) => row).every((piece) => piece != null), isTrue);
        expect(engine.findMatches(), isEmpty);
        expect(engine.hasPossibleMove(), isTrue);
        expect(engine.findHint(), isNotNull);
      }
    });

    test('rejects specials placed on locks in constructors and through the mutator', () {
      expect(
        () => GemMatchEngine(
          lockedCells: {0: 1},
          specialCells: {0: SpecialPower.areaBomb},
          random: Random(1),
        ),
        throwsArgumentError,
      );
      expect(
        () => GemMatchEngine.fromKinds(
          _stableBoard(),
          lockedCells: {0: 1},
          specialCells: {0: SpecialPower.areaBomb},
          random: Random(1),
        ),
        throwsArgumentError,
      );

      final engine = GemMatchEngine.fromKinds(
        _stableBoard(),
        lockedCells: {0: 1},
        random: Random(1),
      );
      expect(
        () => engine.setSpecialAt(0, SpecialPower.areaBomb),
        throwsArgumentError,
      );
    });

    test('dynamic feature setters reject overlapping locks, powers, and obstacles', () {
      final engine = GemMatchEngine.fromKinds(
        _stableBoard(),
        specialCells: {2: SpecialPower.lineClear},
        obstacles: {
          3: const BoardObstacle(type: BoardObstacleType.ice),
        },
        random: Random(2),
      );
      expect(() => engine.setLockLayersAt(2, 1), throwsArgumentError);
      expect(() => engine.setLockLayersAt(3, 1), throwsArgumentError);
      expect(() => engine.setSpecialAt(3, SpecialPower.areaBomb), throwsArgumentError);
    });

    test('detects horizontal runs and merges crossing runs without duplicates', () {
      final horizontal = _stableBoard();
      for (var column = 2; column <= 4; column++) {
        horizontal[3 * GemMatchEngine.columns + column] = FruitKind.strawberry;
      }
      final horizontalEngine = GemMatchEngine.fromKinds(horizontal, random: Random(4));
      expect(horizontalEngine.findMatches(), {26, 27, 28});

      final cross = _stableBoard();
      for (var column = 2; column <= 4; column++) {
        cross[3 * GemMatchEngine.columns + column] = FruitKind.strawberry;
      }
      for (var row = 2; row <= 4; row++) {
        cross[row * GemMatchEngine.columns + 3] = FruitKind.strawberry;
      }
      final crossEngine = GemMatchEngine.fromKinds(cross, random: Random(5));
      expect(crossEngine.findMatches(), {19, 26, 27, 28, 35});
    });

    test('detects vertical runs', () {
      final kinds = _stableBoard();
      for (var row = 2; row <= 4; row++) {
        kinds[row * GemMatchEngine.columns + 4] = FruitKind.blueberry;
      }
      final engine = GemMatchEngine.fromKinds(kinds, random: Random(7));
      expect(engine.findMatches(), {20, 28, 36});
    });

    test('rejects non-adjacent and non-matching swaps without changing the board', () {
      final engine = GemMatchEngine.fromKinds(_stableBoard(), random: Random(5));
      final before = engine.board.expand((row) => row).map((piece) => piece!.id).toList();

      expect(engine.trySwap(0, 2), isFalse);
      expect(engine.trySwap(0, GemMatchEngine.columns + 1), isFalse);
      expect(engine.trySwap(0, 1), isFalse);
      expect(engine.board.expand((row) => row).map((piece) => piece!.id).toList(), before);
    });

    test('keeps a legal adjacent swap when it creates a match', () {
      final kinds = _stableBoard();
      // Swapping (2, 3) with the strawberry directly above completes row 2.
      kinds[2 * GemMatchEngine.columns + 2] = FruitKind.strawberry;
      kinds[2 * GemMatchEngine.columns + 4] = FruitKind.strawberry;
      kinds[1 * GemMatchEngine.columns + 3] = FruitKind.strawberry;
      final engine = GemMatchEngine.fromKinds(kinds, random: Random(11));

      expect(engine.trySwap(1 * GemMatchEngine.columns + 3, 2 * GemMatchEngine.columns + 3), isTrue);
      expect(engine.findMatches(), containsAll([18, 19, 20]));
    });

    test('valid four-, five-, and T-shaped matches create preserved special pieces', () {
      final four = _stableBoard();
      four[3 * GemMatchEngine.columns + 1] = FruitKind.grapes;
      four[3 * GemMatchEngine.columns + 2] = FruitKind.grapes;
      four[3 * GemMatchEngine.columns + 4] = FruitKind.grapes;
      four[2 * GemMatchEngine.columns + 3] = FruitKind.grapes;
      final lineEngine = GemMatchEngine.fromKinds(four, random: Random(31));
      final lineResult = lineEngine.trySwapDetailed(19, 27);
      expect(lineResult.accepted, isTrue);
      expect(lineResult.createdSpecial, SpecialPower.lineClear);
      expect(lineResult.createdSpecialCell, 27);
      expect(lineEngine.pieceAt(27)?.special, SpecialPower.lineClear);
      expect(lineResult.matchedCells, {25, 26, 28});

      final vertical = _stableBoard();
      vertical[1 * GemMatchEngine.columns + 3] = FruitKind.grapes;
      vertical[2 * GemMatchEngine.columns + 3] = FruitKind.grapes;
      vertical[3 * GemMatchEngine.columns + 2] = FruitKind.grapes;
      final verticalEngine = GemMatchEngine.fromKinds(vertical, random: Random(34));
      final verticalResult = verticalEngine.trySwapDetailed(26, 27);
      expect(verticalResult.createdSpecial, SpecialPower.lineClear);
      expect(verticalEngine.pieceAt(27)?.verticalLine, isTrue);
      expect(
        verticalEngine.specialEffectCells(27),
        {3, 11, 19, 27, 35, 43, 51, 59},
      );

      final five = _stableBoard();
      for (final column in [1, 2, 4, 5]) {
        five[3 * GemMatchEngine.columns + column] = FruitKind.grapes;
      }
      five[2 * GemMatchEngine.columns + 3] = FruitKind.grapes;
      final colorEngine = GemMatchEngine.fromKinds(five, random: Random(32));
      final colorResult = colorEngine.trySwapDetailed(19, 27);
      expect(colorResult.accepted, isTrue);
      expect(colorResult.createdSpecial, SpecialPower.colorBomb);
      expect(colorEngine.pieceAt(27)?.special, SpecialPower.colorBomb);
      expect(colorResult.matchedCells, {25, 26, 28, 29});

      final tee = _stableBoard();
      for (final column in [2, 4, 5]) {
        tee[3 * GemMatchEngine.columns + column] = FruitKind.grapes;
      }
      tee[2 * GemMatchEngine.columns + 3] = FruitKind.grapes;
      tee[4 * GemMatchEngine.columns + 3] = FruitKind.grapes;
      final areaEngine = GemMatchEngine.fromKinds(tee, random: Random(33));
      final areaResult = areaEngine.trySwapDetailed(26, 27);
      expect(areaResult.accepted, isTrue);
      expect(areaResult.createdSpecial, SpecialPower.areaBomb);
      expect(areaEngine.pieceAt(27)?.special, SpecialPower.areaBomb);
      expect(areaResult.matchedCells, {19, 28, 29, 35});
    });

    test('clear, gravity and refill leave no empty cells', () {
      final kinds = _stableBoard();
      for (var column = 2; column <= 4; column++) {
        kinds[3 * GemMatchEngine.columns + column] = FruitKind.strawberry;
      }
      final engine = GemMatchEngine.fromKinds(kinds, random: Random(19));
      engine.clearAndDrop(engine.findMatches());
      expect(engine.board.expand((row) => row).every((piece) => piece != null), isTrue);
      expect(engine.board.length, GemMatchEngine.rows);
    });

    test('rejects invalid clear indices before mutating the board', () {
      final engine = GemMatchEngine(random: Random(21));
      final before = engine.board.expand((row) => row).map((piece) => piece!.id).toList();
      expect(() => engine.clearAndDrop({0, GemMatchEngine.cellCount}), throwsRangeError);
      expect(engine.board.expand((row) => row).map((piece) => piece!.id).toList(), before);
    });

    test('a no-move checkerboard is reshuffled into a clean playable board', () {
      final checkerboard = List.generate(
        GemMatchEngine.cellCount,
        (index) => FruitKind.values[(index ~/ GemMatchEngine.columns + index % GemMatchEngine.columns) % 3],
      );
      final engine = GemMatchEngine.fromKinds(checkerboard, random: Random(33));
      expect(engine.findMatches(), isEmpty);
      expect(engine.hasPossibleMove(), isFalse);
      expect(engine.shuffleIfStuck(), isTrue);
      expect(engine.findMatches(), isEmpty);
      expect(engine.hasPossibleMove(), isTrue);
      expect(engine.board.expand((row) => row).every((piece) => piece != null), isTrue);
    });

    test('read-only board snapshots cannot mutate engine state', () {
      final engine = GemMatchEngine(random: Random(22));
      expect(() => engine.board[0][0] = null, throwsUnsupportedError);
      expect(engine.pieceAt(0), isNotNull);
    });

    test('cascade simulation resolves until stable with every cell filled', () {
      for (var seed = 0; seed < 30; seed++) {
        final engine = GemMatchEngine(random: Random(seed));
        final hint = engine.findHint()!;
        expect(engine.trySwap(hint[0], hint[1]), isTrue);
        var cascades = 0;
        while (engine.findMatches().isNotEmpty && cascades < 64) {
          engine.clearAndDrop(engine.findMatches());
          cascades++;
        }
        expect(cascades, lessThan(64));
        expect(engine.board.expand((row) => row).every((piece) => piece != null), isTrue);
      }
    });

    test('area, line and color specials compute their real board effects', () {
      final kinds = _stableBoard();
      final engine = GemMatchEngine.fromKinds(
        kinds,
        specialCells: {
          0: SpecialPower.areaBomb,
          18: SpecialPower.lineClear,
          27: SpecialPower.colorBomb,
        },
      );

      expect(engine.specialEffectCells(0), {0, 1, 8, 9});
      expect(
        engine.specialEffectCells(18),
        {16, 17, 18, 19, 20, 21, 22, 23},
      );
      expect(
        engine.specialEffectCells(18, verticalLine: true),
        {2, 10, 18, 26, 34, 42, 50, 58},
      );
      final expectedColorCells = {
        for (var index = 0; index < kinds.length; index++)
          if (kinds[index] == FruitKind.strawberry) index,
      };
      expect(
        engine.specialEffectCells(27, targetKind: FruitKind.strawberry),
        expectedColorCells,
      );
    });

    test('special swaps work without a natural match and color bombs target the partner', () {
      final kinds = _stableBoard();
      final engine = GemMatchEngine.fromKinds(
        kinds,
        specialCells: {0: SpecialPower.colorBomb},
        random: Random(47),
      );
      final result = engine.trySwapDetailed(0, 1);
      expect(result.accepted, isTrue);
      expect(result.matchedCells, isEmpty);
      expect(engine.pieceAt(1)?.special, SpecialPower.colorBomb);
      final expectedGrapes = {
        for (var index = 0; index < kinds.length; index++)
          if (kinds[index] == FruitKind.grapes) index,
        0, // The partner fruit moves from cell 1 into the bomb's original cell.
      };
      expect(result.activatedCells, expectedGrapes);
    });

    test('a same-fruit 2x2 swap creates a kite beside a soil obstacle', () {
      final kinds = _stableBoard();
      for (final index in [18, 19, 26, 28]) {
        kinds[index] = FruitKind.grapes;
      }
      kinds[35] = FruitKind.lemon;
      final engine = GemMatchEngine.fromKinds(
        kinds,
        obstacles: {
          27: const BoardObstacle(type: BoardObstacleType.soil),
        },
        random: Random(51),
      );
      expect(engine.findMatches(), isEmpty);

      final result = engine.trySwapDetailed(28, 27);
      expect(result.accepted, isTrue);
      expect(result.createdSpecial, SpecialPower.rainbowKite);
      expect(result.createdSpecialCell, 18);
      expect(engine.pieceAt(18)?.special, SpecialPower.rainbowKite);
      expect(engine.obstacleAt(27)?.type, BoardObstacleType.soil);
      expect(result.matchedCells, {19, 26, 27});
    });

    test('a four-match on soil creates its special on a clear cell', () {
      final kinds = _stableBoard();
      kinds[25] = FruitKind.grapes;
      kinds[26] = FruitKind.grapes;
      kinds[28] = FruitKind.grapes;
      kinds[19] = FruitKind.grapes;
      final engine = GemMatchEngine.fromKinds(
        kinds,
        obstacles: {
          27: const BoardObstacle(type: BoardObstacleType.soil),
        },
        random: Random(52),
      );

      final result = engine.trySwapDetailed(19, 27);
      expect(result.accepted, isTrue);
      expect(result.createdSpecial, SpecialPower.lineClear);
      expect(result.createdSpecialCell, 25);
      expect(engine.pieceAt(25)?.special, SpecialPower.lineClear);
      expect(engine.obstacleAt(27)?.type, BoardObstacleType.soil);
      expect(result.matchedCells, {26, 27, 28});
    });

    test('a kite targets a remaining goal fruit deterministically', () {
      final engine = GemMatchEngine.fromKinds(
        _stableBoard(),
        specialCells: {0: SpecialPower.rainbowKite},
        random: Random(52),
      );
      final result = engine.trySwapDetailed(
        0,
        1,
        goalNeeds: {FruitKind.lemon: 2, FruitKind.strawberry: 1},
      );
      expect(result.accepted, isTrue);
      expect(result.targetCells, {4});
      expect(result.activatedCells, containsAll({1, 4}));
      expect(result.activatedCells.length, result.activatedCells.toSet().length);
    });

    test('paired special powers resolve bounded, repeat-free combinations', () {
      final lineAndBomb = GemMatchEngine.fromKinds(
        _stableBoard(),
        specialCells: {
          0: SpecialPower.lineClear,
          1: SpecialPower.areaBomb,
        },
        random: Random(53),
      );
      final lineBombResult = lineAndBomb.trySwapDetailed(0, 1);
      expect(lineBombResult.accepted, isTrue);
      expect(lineBombResult.activatedCells, containsAll(List<int>.generate(10, (i) => i)));
      expect(lineBombResult.activatedCells, isNot(contains(10)));
      expect(lineBombResult.activatedCells.length, lessThanOrEqualTo(GemMatchEngine.cellCount));

      final kiteAndRocket = GemMatchEngine.fromKinds(
        _stableBoard(),
        specialCells: {
          0: SpecialPower.rainbowKite,
          1: SpecialPower.lineClear,
        },
        random: Random(54),
      );
      final kiteRocketResult = kiteAndRocket.trySwapDetailed(
        0,
        1,
        goalNeeds: {FruitKind.lemon: 1},
      );
      expect(kiteRocketResult.targetCells, {4, 11, 18});
      expect(kiteRocketResult.activatedCells, containsAll(List<int>.generate(8, (i) => i)));
      expect(kiteRocketResult.activatedCells.length, lessThanOrEqualTo(GemMatchEngine.cellCount));

      final doubleKite = GemMatchEngine.fromKinds(
        _stableBoard(),
        specialCells: {
          0: SpecialPower.rainbowKite,
          1: SpecialPower.rainbowKite,
        },
        random: Random(55),
      );
      final doubleKiteResult = doubleKite.trySwapDetailed(
        0,
        1,
        goalNeeds: {FruitKind.lemon: 3},
      );
      expect(doubleKiteResult.targetCells, {4, 11, 18});
      expect(doubleKiteResult.activatedCells.length,
          doubleKiteResult.activatedCells.toSet().length);
    });

    test('double bombs, disco orbs and rockets use predictable union shapes', () {
      final doubleBomb = GemMatchEngine.fromKinds(
        _stableBoard(),
        specialCells: {0: SpecialPower.areaBomb, 1: SpecialPower.areaBomb},
      ).trySwapDetailed(0, 1);
      expect(doubleBomb.activatedCells, {
        0, 1, 2, 3, 8, 9, 10, 11, 16, 17, 18, 19,
      });

      final doubleDisco = GemMatchEngine.fromKinds(
        _stableBoard(),
        specialCells: {0: SpecialPower.colorBomb, 1: SpecialPower.colorBomb},
      ).trySwapDetailed(0, 1);
      expect(doubleDisco.activatedCells, hasLength(33));
      expect(doubleDisco.activatedCells, containsAll({0, 1, 2, 4, 6, 8, 10, 62}));

      final doubleRocket = GemMatchEngine.fromKinds(
        _stableBoard(),
        specialCells: {0: SpecialPower.lineClear, 1: SpecialPower.lineClear},
      ).trySwapDetailed(0, 1);
      expect(doubleRocket.activatedCells, hasLength(15));
      expect(doubleRocket.activatedCells, containsAll({0, 1, 2, 3, 4, 5, 6, 7}));
      expect(doubleRocket.activatedCells, containsAll({8, 16, 24, 32, 40, 48, 56}));
    });

    test('every distinct and same-type special pair has a bounded effect', () {
      const pairs = <(SpecialPower, SpecialPower)>[
        (SpecialPower.areaBomb, SpecialPower.areaBomb),
        (SpecialPower.rainbowKite, SpecialPower.rainbowKite),
        (SpecialPower.colorBomb, SpecialPower.colorBomb),
        (SpecialPower.lineClear, SpecialPower.lineClear),
        (SpecialPower.lineClear, SpecialPower.areaBomb),
        (SpecialPower.lineClear, SpecialPower.rainbowKite),
        (SpecialPower.lineClear, SpecialPower.colorBomb),
        (SpecialPower.areaBomb, SpecialPower.rainbowKite),
        (SpecialPower.areaBomb, SpecialPower.colorBomb),
        (SpecialPower.rainbowKite, SpecialPower.colorBomb),
      ];
      for (var index = 0; index < pairs.length; index++) {
        final pair = pairs[index];
        final engine = GemMatchEngine.fromKinds(
          _stableBoard(),
          specialCells: {0: pair.$1, 1: pair.$2},
          random: Random(60 + index),
        );
        final result = engine.trySwapDetailed(
          0,
          1,
          goalNeeds: {FruitKind.lemon: 3},
        );
        expect(result.accepted, isTrue, reason: '${pair.$1} + ${pair.$2}');
        expect(result.activatedCells, containsAll({0, 1}));
        expect(result.activatedCells.length, lessThanOrEqualTo(GemMatchEngine.cellCount));

        final replay = GemMatchEngine.fromKinds(
          _stableBoard(),
          specialCells: {0: pair.$1, 1: pair.$2},
          random: Random(60 + index),
        ).trySwapDetailed(
          0,
          1,
          goalNeeds: {FruitKind.lemon: 3},
        );
        expect(replay.accepted, isTrue, reason: 'repeat ${pair.$1} + ${pair.$2}');
        expect(replay.targetCells, result.targetCells,
            reason: 'repeat target ${pair.$1} + ${pair.$2}');
        expect(replay.activatedCells, result.activatedCells,
            reason: 'repeat effect ${pair.$1} + ${pair.$2}');
      }
    });

    test('one- and two-layer locks absorb hits and block swaps', () {
      final engine = GemMatchEngine.fromKinds(
        _stableBoard(),
        lockedCells: {0: 1, 1: 2},
        random: Random(49),
      );
      final before = engine.board.expand((row) => row).map((piece) => piece!.id).toList();
      expect(engine.trySwap(0, 8), isFalse);
      expect(engine.trySwap(1, 2), isFalse);
      expect(engine.board.expand((row) => row).map((piece) => piece!.id).toList(), before);

      engine.clearAndDrop({0, 1});
      expect(engine.lockLayersAt(0), 0);
      expect(engine.lockLayersAt(1), 1);
      engine.clearAndDrop({1});
      expect(engine.lockLayersAt(1), 0);
      expect(engine.board.expand((row) => row).every((piece) => piece != null), isTrue);
    });

    test('gravity does not pull fruit through a locked cell', () {
      final locked = GemMatchEngine.indexOf(3, 0);
      final below = GemMatchEngine.indexOf(4, 0);
      final engine = GemMatchEngine.fromKinds(
        _stableBoard(),
        lockedCells: {locked: 2},
      );
      final belowId = engine.pieceAt(below)!.id;
      engine.clearAndDrop({GemMatchEngine.indexOf(2, 0)});
      expect(engine.pieceAt(below)!.id, belowId);
      expect(engine.lockLayersAt(locked), 2);
    });

    test('obstacles resist one hit per wave and preserve their movement rules', () {
      final box = GemMatchEngine.fromKinds(
        _stableBoard(),
        obstacles: {
          0: const BoardObstacle(type: BoardObstacleType.woodBox, layers: 2),
          9: const BoardObstacle(type: BoardObstacleType.ice, layers: 1),
          18: const BoardObstacle(type: BoardObstacleType.soil, layers: 1),
        },
        random: Random(70),
      );
      expect(box.pieceAt(0), isNull);
      expect(box.isCellBlocked(0), isTrue);
      expect(box.isCellBlocked(9), isTrue);
      expect(box.isCellBlocked(18), isFalse);
      expect(box.obstacleAt(0)?.layers, 2);

      box.clearAndDrop({0}, expanded: true);
      expect(box.obstacleAt(0)?.layers, 1);
      expect(box.lastClearedObstacles, isEmpty);
      box.clearAndDrop({0}, expanded: true);
      expect(box.obstacleAt(0), isNull);
      expect(box.lastClearedObstacles[BoardObstacleType.woodBox], 1);
      expect(box.obstacleAt(9)?.layers, 1);
      expect(box.obstacleAt(18), isNotNull);
    });

    test('all obstacle types take one direct or adjacent hit per wave', () {
      for (final type in BoardObstacleType.values) {
        final direct = GemMatchEngine.fromKinds(
          _stableBoard(),
          obstacles: {2: BoardObstacle(type: type, layers: 2)},
          random: Random(80 + type.index),
        );
        direct.clearAndDrop({2}, expanded: true);
        expect(direct.obstacleAt(2)?.layers, 1, reason: '$type direct hit');
        expect(direct.lastClearedObstacles, isEmpty, reason: '$type first layer');
        direct.clearAndDrop({2}, expanded: true);
        expect(direct.obstacleAt(2), isNull, reason: '$type second direct hit');
        expect(direct.lastClearedObstacles[type], 1, reason: '$type clear count');

        final adjacent = GemMatchEngine.fromKinds(
          _stableBoard(),
          obstacles: {1: BoardObstacle(type: type)},
          random: Random(90 + type.index),
        );
        adjacent.clearAndDrop(
          {0},
          expanded: true,
          adjacentMatchCells: {0},
        );
        expect(adjacent.obstacleAt(1), isNull, reason: '$type adjacent hit');
        expect(adjacent.lastClearedObstacles[type], 1, reason: '$type adjacent clear count');
      }
    });

    test('bomb, line, color and kite effects resolve blockers by their affected cells', () {
      for (final power in [SpecialPower.areaBomb, SpecialPower.lineClear]) {
        const source = 9;
        final blockerCells = power == SpecialPower.areaBomb
            ? const [0, 1, 2, 8]
            : const [8, 10, 11, 12];
        final engine = GemMatchEngine.fromKinds(
          _stableBoard(),
          specialCells: {source: power},
          obstacles: {
            for (var i = 0; i < BoardObstacleType.values.length; i++)
              blockerCells[i]: BoardObstacle(type: BoardObstacleType.values[i]),
          },
          random: Random(100 + power.index),
        );
        final effect = engine.clearAndDrop({source});
        expect(effect, containsAll(blockerCells), reason: '$power coverage');
        for (final type in BoardObstacleType.values) {
          expect(engine.obstacleAt(blockerCells[type.index]), isNull,
              reason: '$power must hit $type');
          expect(engine.lastClearedObstacles[type], 1,
              reason: '$power clear count for $type');
        }
      }

      final colorKinds = _stableBoard()..[1] = FruitKind.strawberry;
      for (final index in [2, 8, 9]) {
        colorKinds[index] = FruitKind.strawberry;
      }
      final color = GemMatchEngine.fromKinds(
        colorKinds,
        specialCells: {0: SpecialPower.colorBomb},
        obstacles: {
          2: const BoardObstacle(type: BoardObstacleType.ice, layers: 2),
          8: const BoardObstacle(type: BoardObstacleType.vine, layers: 2),
          9: const BoardObstacle(type: BoardObstacleType.soil, layers: 2),
          10: const BoardObstacle(type: BoardObstacleType.woodBox, layers: 2),
        },
        random: Random(104),
      );
      final colorSwap = color.trySwapDetailed(0, 1);
      expect(colorSwap.accepted, isTrue);
      final colorEffect = color.clearAndDrop(colorSwap.activatedCells, expanded: true);
      expect(colorEffect, containsAll({2, 8, 9}));
      for (final index in [2, 8, 9]) {
        expect(color.obstacleAt(index)?.layers, 1, reason: 'color bomb overlay at $index');
      }
      expect(color.obstacleAt(10)?.layers, 2,
          reason: 'color bomb does not select a fruitless wood box');

      final kite = GemMatchEngine.fromKinds(
        _stableBoard(),
        specialCells: {0: SpecialPower.rainbowKite},
        obstacles: {
          1: const BoardObstacle(type: BoardObstacleType.woodBox),
          2: const BoardObstacle(type: BoardObstacleType.ice),
          8: const BoardObstacle(type: BoardObstacleType.vine),
          9: const BoardObstacle(type: BoardObstacleType.soil),
        },
        random: Random(105),
      );
      final kiteTargets = kite.specialEffectCells(
        0,
        goalNeeds: {FruitKind.lemon: 3},
      );
      expect(kiteTargets.intersection(kite.obstacles.keys.toSet()), isEmpty);
      kite.clearAndDrop({0});
      expect(kite.obstacles.keys.toSet(), {1, 2, 8, 9});
    });

    test('ordinary matches damage neighboring obstacles without clearing their fruit', () {
      final engine = GemMatchEngine.fromKinds(
        _stableBoard(),
        obstacles: {
          18: const BoardObstacle(type: BoardObstacleType.vine, layers: 2),
        },
        random: Random(71),
      );
      final fruitId = engine.pieceAt(18)!.id;
      engine.clearAndDrop(
        {17, 25, 33},
        expanded: true,
        adjacentMatchCells: {17, 25, 33},
      );
      expect(engine.obstacleAt(18)?.layers, 1);
      expect(engine.pieceAt(18)?.id, fruitId);
    });

    test('special and adjacent hits cannot double-damage a single blocker in one wave', () {
      final engine = GemMatchEngine.fromKinds(
        _stableBoard(),
        specialCells: {0: SpecialPower.lineClear},
        obstacles: {
          1: const BoardObstacle(type: BoardObstacleType.woodBox, layers: 2),
        },
        random: Random(72),
      );
      engine.clearAndDrop(
        {0},
        adjacentMatchCells: {0},
      );
      expect(engine.obstacleAt(1)?.layers, 1);
    });

    test('hammer clears a single piece, breaks one blocker layer and refills', () {
      final kinds = _stableBoard();
      final iceEngine = GemMatchEngine.fromKinds(
        kinds,
        obstacles: {18: const BoardObstacle(type: BoardObstacleType.ice)},
        random: Random(81),
      );
      final iceFruitId = iceEngine.pieceAt(18)!.id;
      expect(iceEngine.hammerCell(18), {18});
      expect(iceEngine.obstacleAt(18), isNull);
      expect(iceEngine.lastClearedObstacles[BoardObstacleType.ice], 1);
      expect(
        iceEngine.board.expand((row) => row).map((piece) => piece!.id),
        isNot(contains(iceFruitId)),
      );
      expect(
        iceEngine.board.expand((row) => row).every((piece) => piece != null),
        isTrue,
      );

      final boxEngine = GemMatchEngine.fromKinds(
        kinds,
        obstacles: {
          0: const BoardObstacle(type: BoardObstacleType.woodBox, layers: 2),
        },
        random: Random(82),
      );
      expect(boxEngine.pieceAt(0), isNull);
      expect(boxEngine.hammerCell(0), {0});
      expect(boxEngine.obstacleAt(0)?.layers, 1);
      expect(boxEngine.pieceAt(0), isNull);
      expect(boxEngine.lastClearedObstacles, isEmpty);
      expect(boxEngine.hammerCell(0), {0});
      expect(boxEngine.obstacleAt(0), isNull);
      expect(boxEngine.lastClearedObstacles[BoardObstacleType.woodBox], 1);
      expect(boxEngine.pieceAt(0), isNotNull);
      expect(() => boxEngine.hammerCell(GemMatchEngine.cellCount), throwsRangeError);
    });

    test('shuffle booster reshuffles a playable board even without being stuck', () {
      final engine = GemMatchEngine(random: Random(83));
      expect(engine.hasPossibleMove(), isTrue);
      final before = engine.board
          .expand((row) => row)
          .map((piece) => piece!.id)
          .toList(growable: false);
      expect(engine.shuffleBoard(), isTrue);
      final after = engine.board
          .expand((row) => row)
          .map((piece) => piece!.id)
          .toList(growable: false);
      expect(after, isNot(equals(before)));
      expect(engine.findMatches(), isEmpty);
      expect(engine.hasPossibleMove(), isTrue);
      expect(engine.board.expand((row) => row).every((piece) => piece != null), isTrue);
    });

    test('a line special triggers another special in its affected row', () {
      final engine = GemMatchEngine.fromKinds(
        _stableBoard(),
        specialCells: {
          0: SpecialPower.lineClear,
          7: SpecialPower.areaBomb,
        },
        lockedCells: {6: 2},
      );
      final affected = engine.clearAndDrop({0});
      expect(affected, containsAll({0, 1, 2, 3, 4, 5, 6, 7, 14, 15}));
      expect(engine.lockLayersAt(6), 1);
      for (var index = 0; index < GemMatchEngine.cellCount; index++) {
        if (engine.lockLayersAt(index) == 0) expect(engine.pieceAt(index), isNotNull);
      }
    });
  });
}
