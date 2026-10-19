import 'dart:math';

import '../../../game/models/board_obstacle.dart';
import '../../../game/models/fruit_kind.dart';
import '../../../game/models/special_power.dart';
import '../../../game/models/tile_lock.dart';

export '../../../game/models/board_obstacle.dart';
export '../../../game/models/fruit_kind.dart';
export '../../../game/models/special_power.dart';
export '../../../game/models/tile_lock.dart';

class GemPiece {
  const GemPiece({
    required this.kind,
    required this.id,
    this.special,
    this.verticalLine = false,
  });

  final FruitKind kind;
  final int id;
  final SpecialPower? special;
  final bool verticalLine;
}

class GemSwapResult {
  const GemSwapResult({
    required this.accepted,
    this.matchedCells = const <int>{},
    this.activatedCells = const <int>{},
    this.createdSpecialCell,
    this.createdSpecial,
    this.targetCells = const <int>{},
    this.specialCombinationName,
  });

  final bool accepted;
  final Set<int> matchedCells;
  final Set<int> activatedCells;
  final int? createdSpecialCell;
  final SpecialPower? createdSpecial;

  /// Deterministic cells selected by a kite or kite combo for its flight path.
  final Set<int> targetCells;
  final String? specialCombinationName;
}

/// Pure Dart rules engine for an 8×8 match-three board.
///
/// No Flutter types or UI state are used here. The default generator creates a
/// full board without immediate matches and retries until a legal move exists.
class GemMatchEngine {
  GemMatchEngine({
    Random? random,
    Map<int, int> lockedCells = const <int, int>{},
    Map<int, SpecialPower> specialCells = const <int, SpecialPower>{},
    Map<int, BoardObstacle> obstacles = const <int, BoardObstacle>{},
  })  : _random = random ?? Random(),
        _initialLocks = Map<int, int>.unmodifiable(lockedCells),
        _initialSpecials = Map<int, SpecialPower>.unmodifiable(specialCells),
        _initialObstacles = Map<int, BoardObstacle>.unmodifiable(obstacles) {
    _validateFeatureIndices(_initialLocks.keys);
    _validateFeatureIndices(_initialSpecials.keys);
    _validateFeatureIndices(_initialObstacles.keys);
    if (_initialLocks.values.any((layers) => layers < 1 || layers > 2)) {
      throw ArgumentError.value(lockedCells, 'lockedCells', 'lock layers must be 1 or 2');
    }
    if (_initialObstacles.values.any((obstacle) => obstacle.layers < 1 || obstacle.layers > 2)) {
      throw ArgumentError.value(obstacles, 'obstacles', 'obstacle layers must be 1 or 2');
    }
    if (_initialSpecials.keys.any(_initialObstacles.containsKey) ||
        _initialSpecials.keys.any(_initialLocks.containsKey) ||
        _initialLocks.keys.any(_initialObstacles.containsKey)) {
      throw ArgumentError('Special pieces, legacy locks and obstacles cannot share a cell.');
    }
    reset();
  }

  /// Test/fixture constructor. The supplied row-major fruit layout is copied.
  GemMatchEngine.fromKinds(
    List<FruitKind> kinds, {
    Random? random,
    Map<int, int> lockedCells = const <int, int>{},
    Map<int, SpecialPower> specialCells = const <int, SpecialPower>{},
    Map<int, BoardObstacle> obstacles = const <int, BoardObstacle>{},
  })  : _random = random ?? Random(),
        _initialLocks = Map<int, int>.unmodifiable(lockedCells),
        _initialSpecials = Map<int, SpecialPower>.unmodifiable(specialCells),
        _initialObstacles = Map<int, BoardObstacle>.unmodifiable(obstacles) {
    if (kinds.length != cellCount) {
      throw ArgumentError.value(kinds.length, 'kinds.length', 'must be exactly $cellCount');
    }
    _validateFeatureIndices(_initialLocks.keys);
    _validateFeatureIndices(_initialSpecials.keys);
    _validateFeatureIndices(_initialObstacles.keys);
    if (_initialLocks.values.any((layers) => layers < 1 || layers > 2)) {
      throw ArgumentError.value(lockedCells, 'lockedCells', 'lock layers must be 1 or 2');
    }
    if (_initialObstacles.values.any((obstacle) => obstacle.layers < 1 || obstacle.layers > 2)) {
      throw ArgumentError.value(obstacles, 'obstacles', 'obstacle layers must be 1 or 2');
    }
    if (_initialSpecials.keys.any(_initialObstacles.containsKey) ||
        _initialSpecials.keys.any(_initialLocks.containsKey) ||
        _initialLocks.keys.any(_initialObstacles.containsKey)) {
      throw ArgumentError('Special pieces, legacy locks and obstacles cannot share a cell.');
    }
    _locks = _initialLocks.map((index, layers) => MapEntry(index, TileLock(layers: layers)));
    _obstacles = Map<int, BoardObstacle>.of(_initialObstacles);
    _board = List.generate(
      rows,
      (row) => List.generate(
        columns,
        (column) => _newGem(
          kinds[indexOf(row, column)],
          _initialSpecials[indexOf(row, column)],
        ),
      ),
    );
    for (final entry in _obstacles.entries) {
      if (entry.value.type == BoardObstacleType.woodBox) {
        _board[entry.key ~/ columns][entry.key % columns] = null;
      }
    }
  }

  static const int rows = 8;
  static const int columns = 8;
  static const int cellCount = rows * columns;

  final Random _random;
  final Map<int, int> _initialLocks;
  final Map<int, SpecialPower> _initialSpecials;
  final Map<int, BoardObstacle> _initialObstacles;
  int _nextId = 0;
  late List<List<GemPiece?>> _board;
  Map<int, TileLock> _locks = <int, TileLock>{};
  Map<int, BoardObstacle> _obstacles = <int, BoardObstacle>{};
  Map<BoardObstacleType, int> _lastClearedObstacles = <BoardObstacleType, int>{};

  /// Read-only snapshot; game logic must change the board through engine APIs.
  List<List<GemPiece?>> get board => List.unmodifiable(
        _board.map((row) => List<GemPiece?>.unmodifiable(row)),
      );

  void reset() {
    _nextId = 0;
    _locks = _initialLocks.map(
      (index, layers) => MapEntry(index, TileLock(layers: layers)),
    );
    _obstacles = Map<int, BoardObstacle>.of(_initialObstacles);
    _lastClearedObstacles = <BoardObstacleType, int>{};
    var attempts = 0;
    do {
      _fillFreshBoard();
      for (final entry in _initialSpecials.entries) {
        setSpecialAt(entry.key, entry.value);
      }
      attempts++;
    } while (!hasPossibleMove() && attempts < 100);
    if (!hasPossibleMove()) {
      throw StateError('Board constraints leave no legal move.');
    }
  }

  void _fillFreshBoard() {
    _board = List.generate(rows, (_) => List<GemPiece?>.filled(columns, null));
    for (var row = 0; row < rows; row++) {
      for (var column = 0; column < columns; column++) {
        final index = indexOf(row, column);
        if (_obstacles[index]?.type == BoardObstacleType.woodBox) continue;
        _board[row][column] = _randomGemAvoidingImmediateMatch(row, column);
      }
    }
  }

  GemPiece _newGem([FruitKind? kind, SpecialPower? special]) => GemPiece(
        kind: kind ?? FruitKind.values[_random.nextInt(FruitKind.values.length)],
        id: _nextId++,
        special: special,
      );

  GemPiece _randomGemAvoidingImmediateMatch(int row, int column) {
    final options = FruitKind.values.toList();
    options.removeWhere((kind) {
      final makesHorizontal = column >= 2 &&
          _board[row][column - 1]?.kind == kind &&
          _board[row][column - 2]?.kind == kind;
      final makesVertical = row >= 2 &&
          _board[row - 1][column]?.kind == kind &&
          _board[row - 2][column]?.kind == kind;
      return makesHorizontal || makesVertical;
    });
    return _newGem(options[_random.nextInt(options.length)]);
  }

  static int indexOf(int row, int column) => row * columns + column;

  GemPiece? pieceAt(int index) {
    if (index < 0 || index >= cellCount) return null;
    return _board[index ~/ columns][index % columns];
  }

  TileLock? lockAt(int index) => _locks[index];

  BoardObstacle? obstacleAt(int index) => _obstacles[index];

  Map<int, BoardObstacle> get obstacles =>
      Map<int, BoardObstacle>.unmodifiable(_obstacles);

  Map<BoardObstacleType, int> get lastClearedObstacles =>
      Map<BoardObstacleType, int>.unmodifiable(_lastClearedObstacles);

  int lockLayersAt(int index) => lockAt(index)?.layers ?? 0;

  bool isCellBlocked(int index) =>
      lockLayersAt(index) > 0 || (_obstacles[index]?.type.blocksSwaps ?? false);

  int _gravityBlockerLayersAt(int index) {
    final obstacle = _obstacles[index];
    final obstacleLayers = obstacle != null && obstacle.type.blocksGravity
        ? obstacle.layers
        : 0;
    final lockLayers = lockLayersAt(index);
    return obstacleLayers > lockLayers ? obstacleLayers : lockLayers;
  }

  void setLockLayersAt(int index, int layers) {
    _validateFeatureIndices([index]);
    if (layers < 0 || layers > 2) {
      throw RangeError.range(layers, 0, 2, 'layers');
    }
    if (layers == 0) {
      _locks.remove(index);
    } else {
      if (_obstacles.containsKey(index) || pieceAt(index)?.special != null) {
        throw ArgumentError('A lock cannot share a cell with a special piece or obstacle.');
      }
      _locks[index] = TileLock(layers: layers);
    }
  }

  void setSpecialAt(
    int index,
    SpecialPower? special, {
    bool verticalLine = false,
  }) {
    _validateFeatureIndices([index]);
    final piece = pieceAt(index);
    if (piece == null) throw StateError('Cannot place a special on an empty cell.');
    if (_locks.containsKey(index) || _obstacles.containsKey(index)) {
      throw ArgumentError('A special piece cannot share a cell with a lock or obstacle.');
    }
    _board[index ~/ columns][index % columns] = GemPiece(
      kind: piece.kind,
      id: piece.id,
      special: special,
      verticalLine: verticalLine,
    );
  }

  void _validateFeatureIndices(Iterable<int> indices) {
    if (indices.any((index) => index < 0 || index >= cellCount)) {
      throw RangeError('Board feature indices must be between 0 and ${cellCount - 1}.');
    }
  }

  Set<int> findMatches() {
    final matches = <int>{};

    for (var row = 0; row < rows; row++) {
      var column = 0;
      while (column < columns) {
        final kind = _board[row][column]?.kind;
        if (kind == null) {
          column++;
          continue;
        }
        var end = column + 1;
        while (end < columns && _board[row][end]?.kind == kind) {
          end++;
        }
        if (end - column >= 3) {
          for (var matchedColumn = column; matchedColumn < end; matchedColumn++) {
            matches.add(indexOf(row, matchedColumn));
          }
        }
        column = end;
      }
    }

    for (var column = 0; column < columns; column++) {
      var row = 0;
      while (row < rows) {
        final kind = _board[row][column]?.kind;
        if (kind == null) {
          row++;
          continue;
        }
        var end = row + 1;
        while (end < rows && _board[end][column]?.kind == kind) {
          end++;
        }
        if (end - row >= 3) {
          for (var matchedRow = row; matchedRow < end; matchedRow++) {
            matches.add(indexOf(matchedRow, column));
          }
        }
        row = end;
      }
    }

    return matches;
  }

  /// Backwards-compatible convenience API for ordinary game moves.
  bool trySwap(int firstIndex, int secondIndex) =>
      trySwapDetailed(firstIndex, secondIndex).accepted;

  /// Performs one legal adjacent swap. Special combinations are resolved as a
  /// single deterministic effect; rejected swaps are rolled back immediately.
  GemSwapResult trySwapDetailed(
    int firstIndex,
    int secondIndex, {
    Map<FruitKind, int> goalNeeds = const <FruitKind, int>{},
  }) {
    if (firstIndex < 0 ||
        firstIndex >= cellCount ||
        secondIndex < 0 ||
        secondIndex >= cellCount ||
        !_areAdjacent(firstIndex, secondIndex) ||
        isCellBlocked(firstIndex) ||
        isCellBlocked(secondIndex)) {
      return const GemSwapResult(accepted: false);
    }
    final firstPiece = pieceAt(firstIndex);
    final secondPiece = pieceAt(secondIndex);
    if (firstPiece == null || secondPiece == null) {
      return const GemSwapResult(accepted: false);
    }

    _swap(firstIndex, secondIndex);
    final matches = findMatches();
    final firstSpecial = firstPiece.special;
    final secondSpecial = secondPiece.special;
    final hasSpecialSwap = firstSpecial != null || secondSpecial != null;
    if (hasSpecialSwap) {
      final firstSource = firstSpecial == null ? null : secondIndex;
      final secondSource = secondSpecial == null ? null : firstIndex;
      final sourceCells = <int>{
        ?firstSource,
        ?secondSource,
      };
      final effect = firstSpecial != null && secondSpecial != null
          ? _specialCombination(
              firstSource!,
              secondSource!,
              firstSpecial,
              secondSpecial,
              goalNeeds,
            )
          : _singleSpecialEffect(
              firstSource ?? secondSource!,
              firstSpecial ?? secondSpecial!,
              (firstSource == null ? firstPiece.kind : secondPiece.kind),
              goalNeeds,
            );
      final activated = _expandSpecialEffects(
        effect.cells,
        effect.colorTargets,
        goalNeeds: goalNeeds,
        kiteTargets: effect.kiteTargets,
        ignoredSpecialCells: sourceCells,
      );
      activated.addAll(matches);
      activated.addAll(sourceCells);
      return GemSwapResult(
        accepted: true,
        matchedCells: Set<int>.unmodifiable(matches),
        activatedCells: Set<int>.unmodifiable(activated),
        targetCells: Set<int>.unmodifiable(effect.targetCells),
        specialCombinationName: firstSpecial != null && secondSpecial != null
            ? '${firstSpecial.shortName} + ${secondSpecial.shortName}'
            : null,
      );
    }

    final square = matches.isEmpty && firstPiece.kind != secondPiece.kind
        ? _matchingSquareCreatedBySwap(firstIndex, secondIndex)
        : null;
    if (matches.isEmpty && square == null) {
      _swap(firstIndex, secondIndex);
      return const GemSwapResult(accepted: false);
    }

    if (square != null) {
      final anchor = _selectSpecialAnchor(square, firstIndex, secondIndex);
      if (anchor == null) {
        return GemSwapResult(
          accepted: true,
          matchedCells: Set<int>.unmodifiable(square),
        );
      }
      setSpecialAt(anchor, SpecialPower.rainbowKite);
      square.remove(anchor);
      return GemSwapResult(
        accepted: true,
        matchedCells: Set<int>.unmodifiable(square),
        createdSpecialCell: anchor,
        createdSpecial: SpecialPower.rainbowKite,
      );
    }

    final creation = _specialForMatch(matches);
    if (creation != null) {
      final anchor = _selectSpecialAnchor(matches, firstIndex, secondIndex);
      if (anchor == null) {
        return GemSwapResult(
          accepted: true,
          matchedCells: Set<int>.unmodifiable(matches),
        );
      }
      setSpecialAt(
        anchor,
        creation.power,
        verticalLine: creation.verticalLine,
      );
      final clearCells = Set<int>.of(matches)..remove(anchor);
      return GemSwapResult(
        accepted: true,
        matchedCells: Set<int>.unmodifiable(clearCells),
        createdSpecialCell: anchor,
        createdSpecial: creation.power,
      );
    }
    return GemSwapResult(
      accepted: true,
      matchedCells: Set<int>.unmodifiable(matches),
    );
  }

  int? _selectSpecialAnchor(Set<int> cells, int first, int second) {
    final candidates = <int>[
      if (cells.contains(second)) second,
      if (cells.contains(first)) first,
    ];
    final remaining = cells.toList()..sort();
    candidates.addAll(remaining.where((cell) => !candidates.contains(cell)));
    for (final cell in candidates) {
      if (!_locks.containsKey(cell) && !_obstacles.containsKey(cell)) {
        return cell;
      }
    }
    return null;
  }

  Set<int>? _matchingSquareCreatedBySwap(int first, int second) {
    final candidates = <int>{};
    for (final index in [first, second]) {
      final row = index ~/ columns;
      final column = index % columns;
      for (var top = max(0, row - 1); top <= min(rows - 2, row); top++) {
        for (var left = max(0, column - 1); left <= min(columns - 2, column); left++) {
          candidates.add(indexOf(top, left));
        }
      }
    }
    for (final topLeft in candidates.toList()..sort()) {
      final row = topLeft ~/ columns;
      final column = topLeft % columns;
      final square = <int>{
        indexOf(row, column),
        indexOf(row, column + 1),
        indexOf(row + 1, column),
        indexOf(row + 1, column + 1),
      };
      final kind = pieceAt(topLeft)?.kind;
      if (kind != null &&
          (square.contains(first) || square.contains(second)) &&
          square.every((cell) => pieceAt(cell)?.kind == kind)) {
        return square;
      }
    }
    return null;
  }

  ({SpecialPower power, bool verticalLine})? _specialForMatch(Set<int> matches) {
    if (matches.length < 4) return null;
    var longestHorizontal = 0;
    var longestVertical = 0;
    var hasThreeByThreeCross = false;
    for (final index in matches) {
      final horizontal = _runLengthAt(index, vertical: false);
      final vertical = _runLengthAt(index, vertical: true);
      if (horizontal > longestHorizontal) longestHorizontal = horizontal;
      if (vertical > longestVertical) longestVertical = vertical;
      if (horizontal >= 3 && vertical >= 3) hasThreeByThreeCross = true;
    }
    if (longestHorizontal >= 5 || longestVertical >= 5) {
      return (power: SpecialPower.colorBomb, verticalLine: false);
    }
    if (hasThreeByThreeCross) {
      return (power: SpecialPower.areaBomb, verticalLine: false);
    }
    if (longestHorizontal >= 4 || longestVertical >= 4) {
      return (
        power: SpecialPower.lineClear,
        verticalLine: longestVertical > longestHorizontal,
      );
    }
    return null;
  }

  int _runLengthAt(int index, {required bool vertical}) {
    final piece = pieceAt(index);
    if (piece == null) return 0;
    final row = index ~/ columns;
    final column = index % columns;
    final rowStep = vertical ? 1 : 0;
    final columnStep = vertical ? 0 : 1;
    var count = 1;
    for (final direction in const [-1, 1]) {
      var targetRow = row + rowStep * direction;
      var targetColumn = column + columnStep * direction;
      while (targetRow >= 0 &&
          targetRow < rows &&
          targetColumn >= 0 &&
          targetColumn < columns &&
          pieceAt(indexOf(targetRow, targetColumn))?.kind == piece.kind) {
        count++;
        targetRow += rowStep * direction;
        targetColumn += columnStep * direction;
      }
    }
    return count;
  }

  Set<int> specialEffectCells(
    int index, {
    FruitKind? targetKind,
    bool? verticalLine,
    Map<FruitKind, int> goalNeeds = const <FruitKind, int>{},
  }) => _specialCellsFor(
        index,
        targetKind: targetKind,
        verticalLine: verticalLine,
        goalNeeds: goalNeeds,
      );

  Set<int> _specialCellsFor(
    int index, {
    FruitKind? targetKind,
    bool? verticalLine,
    Map<FruitKind, int> goalNeeds = const <FruitKind, int>{},
  }) {
    final piece = pieceAt(index);
    if (piece?.special == null) return <int>{};
    final clearsColumn = verticalLine ?? piece!.verticalLine;
    return switch (piece!.special!) {
      SpecialPower.areaBomb => _areaCells(index, radius: 1),
      SpecialPower.lineClear => _lineCells(index, vertical: clearsColumn),
      SpecialPower.colorBomb => {
          for (var target = 0; target < cellCount; target++)
            if (pieceAt(target)?.kind == (targetKind ?? piece.kind)) target,
        },
      SpecialPower.rainbowKite => _selectKiteTargets(
          1,
          goalNeeds,
          excluded: {index},
        ).toSet(),
    };
  }

  Set<int> _areaCells(int index, {required int radius}) {
    final row = index ~/ columns;
    final column = index % columns;
    return {
      for (var targetRow = max(0, row - radius);
          targetRow <= min(rows - 1, row + radius);
          targetRow++)
        for (var targetColumn = max(0, column - radius);
            targetColumn <= min(columns - 1, column + radius);
            targetColumn++)
          indexOf(targetRow, targetColumn),
    };
  }

  Set<int> _lineCells(int index, {required bool vertical}) {
    final row = index ~/ columns;
    final column = index % columns;
    return {
      for (var offset = 0; offset < (vertical ? rows : columns); offset++)
        vertical ? indexOf(offset, column) : indexOf(row, offset),
    };
  }

  List<int> _selectKiteTargets(
    int count,
    Map<FruitKind, int> goalNeeds, {
    Set<int> excluded = const <int>{},
  }) {
    final candidates = [
      for (var index = 0; index < cellCount; index++)
        if (!excluded.contains(index) &&
            !isCellBlocked(index) &&
            pieceAt(index) != null)
          index,
    ];
    candidates.sort((first, second) {
      final firstNeed = goalNeeds[pieceAt(first)!.kind] ?? 0;
      final secondNeed = goalNeeds[pieceAt(second)!.kind] ?? 0;
      final priority = secondNeed.compareTo(firstNeed);
      return priority != 0 ? priority : first.compareTo(second);
    });
    return candidates.take(count).toList(growable: false);
  }

  ({Set<int> cells, Set<int> targetCells, Map<int, FruitKind> colorTargets,
      Map<int, Set<int>> kiteTargets}) _singleSpecialEffect(
    int source,
    SpecialPower power,
    FruitKind partnerKind,
    Map<FruitKind, int> goalNeeds,
  ) {
    final cells = <int>{source};
    final targetCells = <int>{};
    final colorTargets = <int, FruitKind>{};
    final kiteTargets = <int, Set<int>>{};
    if (power == SpecialPower.colorBomb) colorTargets[source] = partnerKind;
    if (power == SpecialPower.rainbowKite) {
      final targets = _selectKiteTargets(1, goalNeeds, excluded: {source}).toSet();
      kiteTargets[source] = targets;
      targetCells.addAll(targets);
      cells.addAll(targets);
    } else {
      cells.addAll(_specialCellsFor(
        source,
        targetKind: colorTargets[source],
        goalNeeds: goalNeeds,
      ));
    }
    return (
      cells: cells,
      targetCells: targetCells,
      colorTargets: colorTargets,
      kiteTargets: kiteTargets,
    );
  }

  ({Set<int> cells, Set<int> targetCells, Map<int, FruitKind> colorTargets,
      Map<int, Set<int>> kiteTargets}) _specialCombination(
    int first,
    int second,
    SpecialPower firstPower,
    SpecialPower secondPower,
    Map<FruitKind, int> goalNeeds,
  ) {
    final cells = <int>{first, second};
    final targetCells = <int>{};
    final colorTargets = <int, FruitKind>{};
    final kiteTargets = <int, Set<int>>{};
    final powers = {firstPower, secondPower};

    if (powers.length == 1) {
      switch (firstPower) {
        case SpecialPower.areaBomb:
          cells
            ..addAll(_areaCells(first, radius: 2))
            ..addAll(_areaCells(second, radius: 2));
          break;
        case SpecialPower.rainbowKite:
          final targets = _selectKiteTargets(3, goalNeeds, excluded: {first, second}).toSet();
          targetCells.addAll(targets);
          cells.addAll(targets);
          kiteTargets[first] = targets;
          kiteTargets[second] = targets;
          break;
        case SpecialPower.colorBomb:
          final playable = [
            for (var index = 0; index < cellCount; index++)
              if (!isCellBlocked(index) && pieceAt(index) != null) index,
          ];
          for (var offset = 0; offset < playable.length; offset += 2) {
            cells.add(playable[offset]);
          }
          break;
        case SpecialPower.lineClear:
          final firstPiece = pieceAt(first)!;
          final secondPiece = pieceAt(second)!;
          cells
            ..addAll(_lineCells(first, vertical: firstPiece.verticalLine))
            ..addAll(_lineCells(second, vertical: secondPiece.verticalLine));
          final center = indexOf(
            ((first ~/ columns + second ~/ columns) / 2).floor(),
            ((first % columns + second % columns) / 2).floor(),
          );
          cells
            ..addAll(_lineCells(center, vertical: true))
            ..addAll(_lineCells(center, vertical: false));
          break;
      }
    } else if (powers.contains(SpecialPower.lineClear) &&
        powers.contains(SpecialPower.areaBomb)) {
      final rocket = firstPower == SpecialPower.lineClear ? first : second;
      final bomb = firstPower == SpecialPower.areaBomb ? first : second;
      cells
        ..addAll(_lineCells(rocket, vertical: pieceAt(rocket)!.verticalLine))
        ..addAll(_areaCells(bomb, radius: 1));
    } else if (powers.contains(SpecialPower.lineClear) &&
        powers.contains(SpecialPower.colorBomb)) {
      final rocket = firstPower == SpecialPower.lineClear ? first : second;
      final kind = pieceAt(rocket)!.kind;
      final vertical = pieceAt(rocket)!.verticalLine;
      for (var index = 0; index < cellCount; index++) {
        if (pieceAt(index)?.kind == kind) {
          cells
            ..add(index)
            ..addAll(_lineCells(index, vertical: vertical));
        }
      }
    } else if (powers.contains(SpecialPower.lineClear) &&
        powers.contains(SpecialPower.rainbowKite)) {
      final rocket = firstPower == SpecialPower.lineClear ? first : second;
      final kite = firstPower == SpecialPower.rainbowKite ? first : second;
      final targets = _selectKiteTargets(3, goalNeeds, excluded: {first, second}).toSet();
      targetCells.addAll(targets);
      kiteTargets[kite] = targets;
      for (final target in targets) {
        cells.addAll(_lineCells(target, vertical: pieceAt(rocket)!.verticalLine));
      }
    } else if (powers.contains(SpecialPower.areaBomb) &&
        powers.contains(SpecialPower.rainbowKite)) {
      final kite = firstPower == SpecialPower.rainbowKite ? first : second;
      final targets = _selectKiteTargets(3, goalNeeds, excluded: {first, second}).toSet();
      targetCells.addAll(targets);
      kiteTargets[kite] = targets;
      for (final target in targets) {
        cells.addAll(_areaCells(target, radius: 1));
      }
    } else if (powers.contains(SpecialPower.areaBomb) &&
        powers.contains(SpecialPower.colorBomb)) {
      final bomb = firstPower == SpecialPower.areaBomb ? first : second;
      final color = firstPower == SpecialPower.colorBomb ? first : second;
      final kind = pieceAt(color)!.kind;
      for (var index = 0; index < cellCount; index++) {
        if (pieceAt(index)?.kind == kind) {
          cells
            ..add(index)
            ..addAll(_areaCells(index, radius: 1));
        }
      }
      cells.addAll(_areaCells(bomb, radius: 1));
    } else if (powers.contains(SpecialPower.colorBomb) &&
        powers.contains(SpecialPower.rainbowKite)) {
      final kite = firstPower == SpecialPower.rainbowKite ? first : second;
      final targets = _selectKiteTargets(3, goalNeeds, excluded: {first, second}).toSet();
      targetCells.addAll(targets);
      kiteTargets[kite] = targets;
      final targetKinds = targets.map((index) => pieceAt(index)!.kind).toSet();
      for (var index = 0; index < cellCount; index++) {
        if (targetKinds.contains(pieceAt(index)?.kind)) cells.add(index);
      }
    }

    return (
      cells: cells,
      targetCells: targetCells,
      colorTargets: colorTargets,
      kiteTargets: kiteTargets,
    );
  }

  Set<int> _expandSpecialEffects(
    Set<int> starts,
    Map<int, FruitKind> colorTargets, {
    Map<FruitKind, int> goalNeeds = const <FruitKind, int>{},
    Map<int, Set<int>> kiteTargets = const <int, Set<int>>{},
    Set<int> ignoredSpecialCells = const <int>{},
  }) {
    final affected = <int>{};
    final processed = <int>{};
    final pending = List<int>.of(starts);
    while (pending.isNotEmpty) {
      final index = pending.removeLast();
      if (!processed.add(index)) continue;
      affected.add(index);
      final piece = pieceAt(index);
      if (piece?.special == null || ignoredSpecialCells.contains(index)) continue;
      final targets = piece!.special == SpecialPower.rainbowKite
          ? (kiteTargets[index] ??
              _selectKiteTargets(1, goalNeeds, excluded: {index}).toSet())
          : <int>{};
      final effect = piece.special == SpecialPower.rainbowKite
          ? targets
          : _specialCellsFor(
              index,
              targetKind: colorTargets[index],
              goalNeeds: goalNeeds,
            );
      affected.addAll(effect);
      for (final target in effect) {
        if (pieceAt(target)?.special != null && !processed.contains(target)) {
          pending.add(target);
        }
      }
    }
    return affected;
  }

  Set<int> clearEffectCells(
    Set<int> matches, {
    Map<FruitKind, int> goalNeeds = const <FruitKind, int>{},
  }) {
    _validateFeatureIndices(matches);
    return Set<int>.unmodifiable(
      _expandSpecialEffects(matches, <int, FruitKind>{}, goalNeeds: goalNeeds),
    );
  }

  /// Clears matches and triggered specials, applies one hit per obstacle cell,
  /// and compacts columns only inside unblocked segments. Ordinary matches also
  /// damage adjacent obstacles; the sets ensure direct/adjacent hits never stack.
  Set<int> clearAndDrop(
    Set<int> matches, {
    bool expanded = false,
    Set<int> adjacentMatchCells = const <int>{},
  }) {
    _validateFeatureIndices(matches);
    _validateFeatureIndices(adjacentMatchCells);
    final affected = expanded ? Set<int>.of(matches) : clearEffectCells(matches);
    final hitCells = Set<int>.of(affected);
    for (final matchedCell in adjacentMatchCells) {
      final row = matchedCell ~/ columns;
      final column = matchedCell % columns;
      for (final (rowDelta, columnDelta) in const [
        (-1, 0),
        (1, 0),
        (0, -1),
        (0, 1),
      ]) {
        final targetRow = row + rowDelta;
        final targetColumn = column + columnDelta;
        if (targetRow < 0 ||
            targetRow >= rows ||
            targetColumn < 0 ||
            targetColumn >= columns) {
          continue;
        }
        final target = indexOf(targetRow, targetColumn);
        if (_obstacles.containsKey(target) || _locks.containsKey(target)) {
          hitCells.add(target);
        }
      }
    }

    _lastClearedObstacles = <BoardObstacleType, int>{};
    for (final index in hitCells) {
      final lock = _locks[index];
      if (lock != null) {
        final remaining = lock.afterHit();
        if (remaining == null) {
          _locks.remove(index);
        } else {
          _locks[index] = remaining;
        }
      }
      final obstacle = _obstacles[index];
      if (obstacle != null) {
        final remaining = obstacle.afterHit();
        if (remaining == null) {
          _obstacles.remove(index);
          _lastClearedObstacles.update(
            obstacle.type,
            (count) => count + 1,
            ifAbsent: () => 1,
          );
        } else {
          _obstacles[index] = remaining;
        }
      }
    }
    for (final index in affected) {
      _board[index ~/ columns][index % columns] = null;
    }

    for (var column = 0; column < columns; column++) {
      var segmentStart = 0;
      for (var row = 0; row <= rows; row++) {
        final isBlocker = row < rows &&
            _gravityBlockerLayersAt(indexOf(row, column)) > 0;
        if (row == rows || isBlocker) {
          _compactColumnSegment(column, segmentStart, row - 1);
          segmentStart = row + 1;
        }
      }
    }
    return Set<int>.unmodifiable(affected);
  }

  void _compactColumnSegment(int column, int startRow, int endRow) {
    if (startRow > endRow) return;
    var writeRow = endRow;
    for (var row = endRow; row >= startRow; row--) {
      final piece = _board[row][column];
      if (piece == null) continue;
      _board[writeRow][column] = piece;
      if (writeRow != row) _board[row][column] = null;
      writeRow--;
    }
    for (var row = writeRow; row >= startRow; row--) {
      _board[row][column] = _newGem();
    }
  }

  bool hasPossibleMove() {
    for (var row = 0; row < rows; row++) {
      for (var column = 0; column < columns; column++) {
        final index = indexOf(row, column);
        if (column + 1 < columns && _isLegalCandidate(index, index + 1)) return true;
        if (row + 1 < rows && _isLegalCandidate(index, index + columns)) return true;
      }
    }
    return false;
  }

  List<int>? findHint() {
    for (var row = 0; row < rows; row++) {
      for (var column = 0; column < columns; column++) {
        final index = indexOf(row, column);
        if (column + 1 < columns && _isLegalCandidate(index, index + 1)) {
          return [index, index + 1];
        }
        if (row + 1 < rows && _isLegalCandidate(index, index + columns)) {
          return [index, index + columns];
        }
      }
    }
    return null;
  }

  bool shuffleIfStuck() {
    if (hasPossibleMove()) return false;
    return shuffleBoard();
  }

  /// Reshuffles every movable piece into a fresh layout even when the board
  /// still has a legal move. This backs the purchased shuffle booster.
  bool shuffleBoard() {
    final movableIndices = [
      for (var index = 0; index < cellCount; index++)
        if (!isCellBlocked(index) && pieceAt(index) != null) index,
    ];
    final pieces = [
      for (final index in movableIndices) ?pieceAt(index),
    ];
    if (pieces.length < 2) return false;
    for (var attempt = 0; attempt < 100; attempt++) {
      pieces.shuffle(_random);
      for (var offset = 0; offset < movableIndices.length; offset++) {
        final index = movableIndices[offset];
        _board[index ~/ columns][index % columns] = pieces[offset];
      }
      if (findMatches().isEmpty && hasPossibleMove()) return true;
    }
    reset();
    return true;
  }

  /// Hammer booster: removes the piece at [index] and breaks one lock or
  /// obstacle layer there without needing a match. Columns then compact and
  /// refill exactly like a normal clear. A cell with nothing to break is
  /// rejected with an empty set so the caller does not spend a booster.
  Set<int> hammerCell(int index) {
    _validateFeatureIndices([index]);
    if (pieceAt(index) == null &&
        !_locks.containsKey(index) &&
        !_obstacles.containsKey(index)) {
      return const <int>{};
    }
    return clearAndDrop(<int>{index}, expanded: true);
  }

  bool _isLegalCandidate(int firstIndex, int secondIndex) {
    if (isCellBlocked(firstIndex) || isCellBlocked(secondIndex)) return false;
    final first = pieceAt(firstIndex);
    final second = pieceAt(secondIndex);
    if (first == null || second == null) return false;
    if (first.special != null || second.special != null) return true;
    return _wouldMatchAfterSwap(firstIndex, secondIndex);
  }

  bool _wouldMatchAfterSwap(int firstIndex, int secondIndex) {
    if (isCellBlocked(firstIndex) || isCellBlocked(secondIndex)) return false;
    final differentKinds = pieceAt(firstIndex)?.kind != pieceAt(secondIndex)?.kind;
    _swap(firstIndex, secondIndex);
    final matched = findMatches().isNotEmpty ||
        (differentKinds &&
            _matchingSquareCreatedBySwap(firstIndex, secondIndex) != null);
    _swap(firstIndex, secondIndex);
    return matched;
  }

  bool _areAdjacent(int firstIndex, int secondIndex) {
    final rowDifference = (firstIndex ~/ columns - secondIndex ~/ columns).abs();
    final columnDifference = (firstIndex % columns - secondIndex % columns).abs();
    return rowDifference + columnDifference == 1;
  }

  void _swap(int firstIndex, int secondIndex) {
    final firstRow = firstIndex ~/ columns;
    final firstColumn = firstIndex % columns;
    final secondRow = secondIndex ~/ columns;
    final secondColumn = secondIndex % columns;
    final firstPiece = _board[firstRow][firstColumn];
    _board[firstRow][firstColumn] = _board[secondRow][secondColumn];
    _board[secondRow][secondColumn] = firstPiece;
  }
}
