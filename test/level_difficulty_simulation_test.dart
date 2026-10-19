import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:puzzle/game/models/level_definition.dart';
import 'package:puzzle/game/services/level_catalog.dart';
import 'package:puzzle/game/services/score_manager.dart';
import 'package:puzzle/ui/features/gem_match/gem_match_engine.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('runs a reproducible legal-random-move sample across all 100 levels', () async {
    final catalog = await LevelCatalog.load();
    final byDifficulty = <String, List<_TrialResult>>{};
    final levelReports = <Map<String, Object?>>[];
    final simulationErrors = <Map<String, Object?>>[];

    for (final level in catalog.levels) {
      final trials = <_TrialResult>[];
      for (var sample = 0; sample < 3; sample++) {
        final seed = level.id * 1000 + sample;
        try {
          final trial = _simulate(level, seed: seed);
          trials.add(trial);
          if (sample == 0) {
            final replay = _simulate(level, seed: seed);
            if (jsonEncode(replay.toJson()) != jsonEncode(trial.toJson())) {
              simulationErrors.add({
                'levelId': level.id,
                'seed': seed,
                'error': 'same seed produced different trial results on replay',
              });
            }
          }
        } catch (error) {
          simulationErrors.add({
            'levelId': level.id,
            'seed': seed,
            'error': error.toString(),
          });
        }
      }
      byDifficulty.putIfAbsent(level.difficulty.name, () => []).addAll(trials);
      levelReports.add({
        'simulationErrors': simulationErrors.where((issue) => issue['levelId'] == level.id).toList(),
        'levelId': level.id,
        'difficulty': level.difficulty.name,
        'moveLimit': level.moveLimit,
        'trials': trials.map((trial) => trial.toJson()).toList(growable: false),
      });
    }

    final report = <String, Object?>{
      'reportVersion': 1,
      'catalogSource': 'assets/data/levels.json',
      'policy': 'At each move, shuffle adjacent candidate swaps with a seeded PRNG and take the first legal swap; resolve matches, powers, gravity, goals, score, and cascades with the production engine rules.',
      'trialCount': levelReports.fold<int>(
        0,
        (sum, level) => sum + (level['trials']! as List).length,
      ),
      'seedRule': 'engineSeed = levelId * 1000 + sample; policySeed = engineSeed XOR 0x5deece66d',
      'interpretation': 'Observed outcomes for this deliberately unskilled random policy only. These trials are not human win rates and do not establish player-facing balance or strategic solvability.',
      'simulationErrors': simulationErrors,
      'difficultyBands': {
        for (final entry in byDifficulty.entries)
          entry.key: _summarize(entry.value),
      },
      'levels': levelReports,
    };

    final output = File('build/level_difficulty_simulation.json');
    output.parent.createSync(recursive: true);
    output.writeAsStringSync(
      JsonEncoder.withIndent('  ').convert(report),
      flush: true,
    );
    // Keep test output concise; CI validates this machine-readable report and
    // attaches it to the unpublished draft preview release.
    // ignore: avoid_print
    print('LEVEL_SIMULATION_SUMMARY ${jsonEncode(report['difficultyBands'])}');

    expect(
      simulationErrors,
      isEmpty,
      reason: jsonEncode(simulationErrors),
    );
    expect(levelReports, hasLength(100));
    expect(report['trialCount'], 300);
    expect(
      levelReports.map((level) => level['levelId']).toList(),
      List<int>.generate(100, (index) => index + 1),
    );
    expect(
      levelReports.every((level) => (level['trials']! as List).length == 3),
      isTrue,
    );
  });
}

Map<String, Object?> _summarize(List<_TrialResult> trials) {
  final completed = trials.where((trial) => trial.requirementsMet).length;
  final stalled = trials.where((trial) => trial.stalled).length;
  final cascadeRecoveries = trials.fold<int>(
    0,
    (sum, trial) => sum + trial.cascadeLimitRecoveries,
  );
  final scores = trials.map((trial) => trial.score).toList()..sort();
  final moves = trials.map((trial) => trial.movesUsed).toList()..sort();
  return {
    'trials': trials.length,
    'observedRequirementCompletions': completed,
    'stalledBoardCount': stalled,
    'cascadeLimitRecoveries': cascadeRecoveries,
    'medianScore': scores.isEmpty ? null : scores[scores.length ~/ 2],
    'medianMovesConsumed': moves.isEmpty ? null : moves[moves.length ~/ 2],
  };
}

_TrialResult _simulate(LevelDefinition level, {required int seed}) {
  final engineSeed = seed;
  final policyRandom = Random(seed ^ 0x5deece66d);
  final engine = GemMatchEngine(
    random: Random(engineSeed),
    lockedCells: level.lockedCells,
    obstacles: level.obstacles,
    specialCells: level.specialPieces,
  );
  if (engine.findMatches().isNotEmpty || !engine.hasPossibleMove()) {
    throw StateError('Level ${level.id}, seed $seed did not start clean and playable.');
  }

  final collected = {for (final kind in level.fruitGoals.keys) kind: 0};
  final obstaclesCollected = {
    for (final type in level.obstacleGoals.keys) type: 0,
  };
  final scoreManager = const ScoreManager();
  var score = 0;
  var movesUsed = 0;
  var stalled = false;
  var cascadeLimitRecoveries = 0;

  bool requirementsMet() =>
      score >= level.targetScore &&
      level.goalsComplete(collected, obstaclesCollected);

  while (movesUsed < level.moveLimit && !requirementsMet()) {
    final candidates = <(int, int)>[];
    for (var row = 0; row < GemMatchEngine.rows; row++) {
      for (var column = 0; column < GemMatchEngine.columns; column++) {
        final index = GemMatchEngine.indexOf(row, column);
        if (column + 1 < GemMatchEngine.columns) {
          candidates.add((index, index + 1));
        }
        if (row + 1 < GemMatchEngine.rows) {
          candidates.add((index, index + GemMatchEngine.columns));
        }
      }
    }
    candidates.shuffle(policyRandom);

    GemSwapResult? acceptedMove;
    for (final (first, second) in candidates) {
      final result = engine.trySwapDetailed(
        first,
        second,
        goalNeeds: _remainingGoals(level, collected),
      );
      if (result.accepted) {
        acceptedMove = result;
        break;
      }
    }

    if (acceptedMove == null) {
      if (!engine.shuffleIfStuck()) {
        stalled = true;
        break;
      }
      continue;
    }
    movesUsed++;

    Set<int>? firstClear = acceptedMove.activatedCells.isNotEmpty
        ? acceptedMove.activatedCells
        : acceptedMove.matchedCells;
    Set<int>? firstMatchCells = acceptedMove.matchedCells;
    var firstClearExpanded = acceptedMove.activatedCells.isNotEmpty;
    var cascadeIndex = 0;

    while (cascadeIndex < 64) {
      final matches = firstClear ?? engine.findMatches();
      final matchCellsForObstacleDamage = firstClear == null
          ? matches
          : (firstMatchCells ?? matches);
      final affected = firstClearExpanded
          ? Set<int>.of(matches)
          : engine.clearEffectCells(
              matches,
              goalNeeds: _remainingGoals(level, collected),
            );
      firstClear = null;
      firstMatchCells = null;
      firstClearExpanded = false;
      if (affected.isEmpty) break;

      final matchedKinds = affected
          .map((index) => engine.pieceAt(index)?.kind)
          .whereType<FruitKind>()
          .toList(growable: false);
      score += scoreManager.scoreForMatch(
        matchedPieces: matchedKinds.length,
        cascadeIndex: cascadeIndex,
      );
      for (final kind in matchedKinds) {
        if (!level.fruitGoals.containsKey(kind)) continue;
        final required = level.fruitGoals[kind]!;
        final current = collected[kind] ?? 0;
        if (current < required) collected[kind] = current + 1;
      }

      engine.clearAndDrop(
        affected,
        expanded: true,
        adjacentMatchCells: matchCellsForObstacleDamage,
      );
      for (final entry in engine.lastClearedObstacles.entries) {
        final required = level.obstacleGoals[entry.key];
        if (required == null) continue;
        final current = obstaclesCollected[entry.key] ?? 0;
        obstaclesCollected[entry.key] = current + entry.value > required
            ? required
            : current + entry.value;
      }
      cascadeIndex++;
    }

    if (cascadeIndex == 64 && engine.findMatches().isNotEmpty) {
      cascadeLimitRecoveries++;
      engine.reset();
    }
    if (movesUsed < level.moveLimit && !requirementsMet()) {
      engine.shuffleIfStuck();
    }
  }

  return _TrialResult(
    levelId: level.id,
    seed: seed,
    movesUsed: movesUsed,
    score: score,
    requirementsMet: requirementsMet(),
    stalled: stalled,
    cascadeLimitRecoveries: cascadeLimitRecoveries,
    fruitGoalsMet: level.fruitGoals.entries
        .where((entry) => (collected[entry.key] ?? 0) >= entry.value)
        .length,
    fruitGoalKinds: level.fruitGoals.length,
    obstacleGoalsMet: level.obstacleGoals.entries
        .where((entry) => (obstaclesCollected[entry.key] ?? 0) >= entry.value)
        .length,
    obstacleGoalKinds: level.obstacleGoals.length,
  );
}

Map<FruitKind, int> _remainingGoals(
  LevelDefinition level,
  Map<FruitKind, int> collected,
) => {
      for (final entry in level.fruitGoals.entries)
        entry.key: entry.value > (collected[entry.key] ?? 0)
            ? entry.value - (collected[entry.key] ?? 0)
            : 0,
    };

class _TrialResult {
  const _TrialResult({
    required this.levelId,
    required this.seed,
    required this.movesUsed,
    required this.score,
    required this.requirementsMet,
    required this.stalled,
    required this.cascadeLimitRecoveries,
    required this.fruitGoalsMet,
    required this.fruitGoalKinds,
    required this.obstacleGoalsMet,
    required this.obstacleGoalKinds,
  });

  final int levelId;
  final int seed;
  final int movesUsed;
  final int score;
  final bool requirementsMet;
  final bool stalled;
  final int cascadeLimitRecoveries;
  final int fruitGoalsMet;
  final int fruitGoalKinds;
  final int obstacleGoalsMet;
  final int obstacleGoalKinds;

  Map<String, Object?> toJson() => {
        'levelId': levelId,
        'seed': seed,
        'movesUsed': movesUsed,
        'score': score,
        'requirementsMet': requirementsMet,
        'stalled': stalled,
        'cascadeLimitRecoveries': cascadeLimitRecoveries,
        'fruitGoalsMet': fruitGoalsMet,
        'fruitGoalKinds': fruitGoalKinds,
        'obstacleGoalsMet': obstacleGoalsMet,
        'obstacleGoalKinds': obstacleGoalKinds,
      };
}
