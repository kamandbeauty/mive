import 'board_obstacle.dart';
import 'fruit_kind.dart';
import 'special_power.dart';

enum LevelDifficulty { tutorial, easy, medium, hard }

class LevelDefinition {
  const LevelDefinition({
    required this.id,
    required this.chapter,
    required this.moveLimit,
    required this.targetScore,
    required this.fruitGoals,
    required this.starThresholds,
    required this.difficulty,
    this.specialPieces = const <int, SpecialPower>{},
    this.lockedCells = const <int, int>{},
    this.obstacles = const <int, BoardObstacle>{},
    this.obstacleGoals = const <BoardObstacleType, int>{},
  });

  final int id;
  final String chapter;
  final int moveLimit;
  final int targetScore;
  final Map<FruitKind, int> fruitGoals;

  /// Score cutoffs in ascending order for one, two, and three stars.
  final List<int> starThresholds;
  final LevelDifficulty difficulty;
  final Map<int, SpecialPower> specialPieces;

  /// Legacy lock map remains readable for old saved/catalog data.
  final Map<int, int> lockedCells;
  final Map<int, BoardObstacle> obstacles;
  final Map<BoardObstacleType, int> obstacleGoals;

  /// Engine-ready obstacle map, mapping legacy locks to ice cells.
  Map<int, BoardObstacle> get allObstacles => {
        for (final entry in lockedCells.entries)
          entry.key: BoardObstacle(type: BoardObstacleType.ice, layers: entry.value),
        ...obstacles,
      };

  String get chapterTitle => switch (chapter) {
        'tutorial' => 'آموزش بازی',
        'grove' => 'باغچه',
        'orchard' => 'باغ میوه',
        'hills' => 'تپه‌های سبز',
        'summit' => 'باغ بلند',
        _ => chapter,
      };

  factory LevelDefinition.fromJson(Map<String, Object?> json) {
    final rawGoals = json['fruitGoals'];
    final rawThresholds = json['starThresholds'];
    if (rawGoals is! Map<String, dynamic> || rawThresholds is! List<dynamic>) {
      throw const FormatException('Invalid fruit goals or star thresholds.');
    }
    final goals = <FruitKind, int>{};
    for (final entry in rawGoals.entries) {
      final kind = _fruitKindFromName(entry.key);
      final count = entry.value;
      if (kind == null || count is! int) {
        throw FormatException('Invalid fruit goal: ${entry.key}.');
      }
      goals[kind] = count;
    }
    final thresholds = rawThresholds.cast<int>();
    final specialPieces = _parseSpecialPieces(json['specialPieces']);
    final lockedCells = _parseLockLayers(json['lockedCells']);
    final obstacles = _parseObstacles(json['obstacles']);
    final obstacleGoals = _parseObstacleGoals(json['obstacleGoals']);
    final difficultyName = json['difficulty'];
    final difficulty = _difficultyFromName(difficultyName);
    if (difficulty == null) throw const FormatException('Unknown level difficulty.');

    return LevelDefinition(
      id: json['id'] as int,
      chapter: json['chapter'] as String,
      moveLimit: json['moveLimit'] as int,
      targetScore: json['targetScore'] as int,
      fruitGoals: Map.unmodifiable(goals),
      starThresholds: List.unmodifiable(thresholds),
      difficulty: difficulty,
      specialPieces: Map.unmodifiable(specialPieces),
      lockedCells: Map.unmodifiable(lockedCells),
      obstacles: Map.unmodifiable(obstacles),
      obstacleGoals: Map.unmodifiable(obstacleGoals),
    );
  }

  List<String> validate() {
    final errors = <String>[];
    if (id < 1) errors.add('id must be positive');
    if (chapter.trim().isEmpty) errors.add('chapter cannot be empty');
    if (moveLimit < 1 || moveLimit > 60) errors.add('moveLimit must be 1..60');
    if (targetScore <= 0) errors.add('targetScore must be positive');
    if (id >= 1 && id <= 100) {
      final expectedDifficulty = id <= 10
          ? LevelDifficulty.tutorial
          : id <= 20
              ? LevelDifficulty.easy
              : id <= 60
                  ? LevelDifficulty.medium
                  : LevelDifficulty.hard;
      if (difficulty != expectedDifficulty) {
        errors.add('difficulty does not match the configured 1–10 / 11–20 / 21–60 / 61–100 band');
      }
    }
    if (fruitGoals.isEmpty) errors.add('at least one fruit goal is required');
    if (fruitGoals.values.any((count) => count <= 0 || count > 64)) {
      errors.add('fruit goals must be 1..64');
    }
    if (specialPieces.keys.any((index) => index < 0 || index >= 64)) {
      errors.add('special piece indices must be between 0 and 63');
    }
    if (lockedCells.keys.any((index) => index < 0 || index >= 64)) {
      errors.add('locked cell indices must be between 0 and 63');
    }
    if (lockedCells.values.any((layers) => layers < 1 || layers > 2)) {
      errors.add('lock layers must be 1 or 2');
    }
    if (obstacles.keys.any((index) => index < 0 || index >= 64)) {
      errors.add('obstacle indices must be between 0 and 63');
    }
    if (obstacles.values.any((obstacle) => obstacle.layers < 1 || obstacle.layers > 2)) {
      errors.add('obstacle layers must be 1 or 2');
    }
    if (lockedCells.keys.any(obstacles.containsKey)) {
      errors.add('legacy locks and obstacles cannot share a cell');
    }
    if (specialPieces.keys.any((index) => lockedCells.containsKey(index) || obstacles.containsKey(index))) {
      errors.add('a special piece cannot be placed inside an obstacle');
    }
    if (obstacleGoals.values.any((count) => count <= 0 || count > 64)) {
      errors.add('obstacle goals must be 1..64');
    }
    for (final goal in obstacleGoals.entries) {
      final available = obstacles.values.where((item) => item.type == goal.key).length;
      if (available < goal.value) {
        errors.add('obstacle goal ${goal.key.name} exceeds the configured obstacle count');
      }
    }
    if (starThresholds.length != 3 ||
        starThresholds.any((value) => value <= 0) ||
        starThresholds.toSet().length != 3 ||
        starThresholds[0] != targetScore ||
        starThresholds[0] >= starThresholds[1] ||
        starThresholds[1] >= starThresholds[2]) {
      errors.add('star thresholds must be three ascending positive scores starting at targetScore');
    }
    return errors;
  }

  bool goalsComplete(
    Map<FruitKind, int> collected, [
    Map<BoardObstacleType, int> obstaclesCollected = const <BoardObstacleType, int>{},
  ]) =>
      fruitGoals.entries.every(
        (goal) => (collected[goal.key] ?? 0) >= goal.value,
      ) &&
      obstacleGoals.entries.every(
        (goal) => (obstaclesCollected[goal.key] ?? 0) >= goal.value,
      );

  bool isWon({
    required int score,
    required Map<FruitKind, int> collected,
    Map<BoardObstacleType, int> obstaclesCollected = const <BoardObstacleType, int>{},
  }) =>
      score >= targetScore && goalsComplete(collected, obstaclesCollected);

  int starsForScore(int score) {
    if (score < starThresholds[0]) return 0;
    if (score < starThresholds[1]) return 1;
    if (score < starThresholds[2]) return 2;
    return 3;
  }
}

Map<int, SpecialPower> _parseSpecialPieces(Object? raw) {
  if (raw == null) return const <int, SpecialPower>{};
  if (raw is! Map) throw const FormatException('Invalid special piece map.');
  final result = <int, SpecialPower>{};
  for (final entry in raw.entries) {
    final index = int.tryParse(entry.key.toString());
    final special = _specialPowerFromName(entry.value);
    if (index == null || special == null) {
      throw FormatException('Invalid special piece entry: ${entry.key}.');
    }
    result[index] = special;
  }
  return result;
}

Map<int, int> _parseLockLayers(Object? raw) {
  if (raw == null) return const <int, int>{};
  if (raw is! Map) throw const FormatException('Invalid locked cell map.');
  final result = <int, int>{};
  for (final entry in raw.entries) {
    final index = int.tryParse(entry.key.toString());
    final layers = entry.value;
    if (index == null || layers is! int || layers < 1 || layers > 2) {
      throw FormatException('Invalid locked cell entry: ${entry.key}.');
    }
    result[index] = layers;
  }
  return result;
}

Map<int, BoardObstacle> _parseObstacles(Object? raw) {
  if (raw == null) return const <int, BoardObstacle>{};
  if (raw is! Map) throw const FormatException('Invalid obstacle map.');
  final result = <int, BoardObstacle>{};
  for (final entry in raw.entries) {
    final index = int.tryParse(entry.key.toString());
    final value = entry.value;
    if (index == null || value is! Map) {
      throw FormatException('Invalid obstacle entry: ${entry.key}.');
    }
    final type = _obstacleTypeFromName(value['type']);
    final layers = value['layers'] ?? 1;
    if (type == null || layers is! int || layers < 1 || layers > 2) {
      throw FormatException('Invalid obstacle entry: ${entry.key}.');
    }
    result[index] = BoardObstacle(type: type, layers: layers);
  }
  return result;
}

Map<BoardObstacleType, int> _parseObstacleGoals(Object? raw) {
  if (raw == null) return const <BoardObstacleType, int>{};
  if (raw is! Map) throw const FormatException('Invalid obstacle goal map.');
  final result = <BoardObstacleType, int>{};
  for (final entry in raw.entries) {
    final type = _obstacleTypeFromName(entry.key);
    final count = entry.value;
    if (type == null || count is! int || count < 1 || count > 64) {
      throw FormatException('Invalid obstacle goal: ${entry.key}.');
    }
    result[type] = count;
  }
  return result;
}

BoardObstacleType? _obstacleTypeFromName(Object? name) {
  for (final type in BoardObstacleType.values) {
    if (type.name == name) return type;
  }
  return null;
}

SpecialPower? _specialPowerFromName(Object? name) {
  for (final power in SpecialPower.values) {
    if (power.name == name) return power;
  }
  return null;
}

FruitKind? _fruitKindFromName(String name) {
  for (final kind in FruitKind.values) {
    if (kind.name == name) return kind;
  }
  return null;
}

LevelDifficulty? _difficultyFromName(Object? name) {
  for (final difficulty in LevelDifficulty.values) {
    if (difficulty.name == name) return difficulty;
  }
  return null;
}
