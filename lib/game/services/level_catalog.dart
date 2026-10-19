import 'dart:convert';
import 'dart:math';

import 'package:flutter/services.dart' show rootBundle;

import '../../ui/features/gem_match/gem_match_engine.dart';
import '../models/level_definition.dart';

class LevelCatalog {
  LevelCatalog._(this.levels);

  static const assetPath = 'assets/data/levels.json';
  static Future<LevelCatalog>? _cached;

  final List<LevelDefinition> levels;

  static Future<LevelCatalog> load() => _cached ??= rootBundle
      .loadString(assetPath)
      .then(LevelCatalog.parse);

  factory LevelCatalog.parse(String source) {
    final decoded = jsonDecode(source);
    if (decoded is! Map<String, dynamic> || decoded['version'] != 1) {
      throw const FormatException('Unsupported level catalog version.');
    }
    final rawLevels = decoded['levels'];
    if (rawLevels is! List<dynamic>) {
      throw const FormatException('Level catalog must contain a levels array.');
    }
    final levels = rawLevels
        .map((raw) => LevelDefinition.fromJson(Map<String, Object?>.from(raw as Map)))
        .toList(growable: false);
    final catalog = LevelCatalog._(List.unmodifiable(levels));
    final errors = catalog.validate();
    if (errors.isNotEmpty) throw FormatException(errors.join('\n'));
    return catalog;
  }

  LevelDefinition? byId(int id) {
    for (final level in levels) {
      if (level.id == id) return level;
    }
    return null;
  }

  List<String> validate({int expectedCount = 100}) {
    final errors = <String>[];
    if (levels.length != expectedCount) {
      errors.add('Expected $expectedCount levels, found ${levels.length}.');
    }
    final ids = levels.map((level) => level.id).toSet();
    if (ids.length != levels.length) errors.add('Level ids must be unique.');
    for (final level in levels) {
      for (final issue in level.validate()) {
        errors.add('Level ${level.id}: $issue');
      }
    }
    for (var id = 1; id <= levels.length; id++) {
      if (!ids.contains(id)) errors.add('Missing level id $id.');
    }
    return errors;
  }

  /// Seeded board-setup smoke simulation, not a claim that a level is
  /// strategically solvable. Every level is checked with its configured
  /// obstacles and specials for a full playable board without opening matches.
  List<String> validateStartingBoards({int samples = 3}) {
    final errors = <String>[];
    for (final level in levels) {
      for (var seed = 0; seed < samples; seed++) {
        try {
          final engine = GemMatchEngine(
            random: Random(level.id * 1000 + seed),
            lockedCells: level.lockedCells,
            specialCells: level.specialPieces,
            obstacles: level.obstacles,
          );
          final pieces = engine.board.expand((row) => row);
          if (pieces.length != GemMatchEngine.cellCount ||
              pieces.where((piece) => piece == null).length !=
                  level.obstacles.values
                      .where((obstacle) => obstacle.type == BoardObstacleType.woodBox)
                      .length) {
            errors.add('Level ${level.id}, seed $seed generated an invalid board size.');
          } else if (engine.findMatches().isNotEmpty) {
            errors.add('Level ${level.id}, seed $seed generated an immediate match.');
          } else if (!engine.hasPossibleMove()) {
            errors.add('Level ${level.id}, seed $seed generated a board with no legal move.');
          }
        } catch (error) {
          errors.add('Level ${level.id}, seed $seed could not start: $error');
        }
      }
    }
    return errors;
  }
}
