import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../models/game_progress.dart';

class ProgressRepository {
  ProgressRepository({SharedPreferences? initialPreferences})
      : _preferences = initialPreferences;

  static const storageKey = 'fruit_match_progress_v1';
  static const maxLevel = 100;

  SharedPreferences? _preferences;

  Future<SharedPreferences> get _store async =>
      _preferences ??= await SharedPreferences.getInstance();

  Future<GameProgress> load() async {
    final store = await _store;
    final raw = store.getString(storageKey);
    if (raw == null) return const GameProgress();
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic> || decoded['schemaVersion'] != 1) {
        throw const FormatException('Unsupported progress schema.');
      }
      return _sanitize(decoded);
    } catch (_) {
      // Corrupt or future-version data should not block the game from opening.
      await store.remove(storageKey);
      return const GameProgress();
    }
  }

  Future<GameProgress> saveCompletion({
    required int levelId,
    required int stars,
    required int score,
  }) async {
    if (levelId < 1 || levelId > maxLevel) {
      throw RangeError.range(levelId, 1, maxLevel, 'levelId');
    }
    final current = await load();
    if (levelId > current.lastUnlockedLevel) {
      throw StateError('Cannot record completion for a locked level.');
    }
    final nextUnlocked = levelId < maxLevel ? levelId + 1 : maxLevel;
    final reachedFrontier = levelId >= current.lastUnlockedLevel;
    final nextToPlay = reachedFrontier && levelId < maxLevel
        ? levelId + 1
        : current.lastPlayedLevel;
    final nextStars = Map<int, int>.from(current.stars)
      ..[levelId] = (current.stars[levelId] ?? 0).clamp(0, 3).toInt().max(stars.clamp(0, 3).toInt());
    final nextScores = Map<int, int>.from(current.highScores)
      ..[levelId] = (current.highScores[levelId] ?? 0).max(score.clamp(0, 1 << 30).toInt());
    final updated = GameProgress(
      lastUnlockedLevel: current.lastUnlockedLevel.max(nextUnlocked),
      lastPlayedLevel: nextToPlay,
      stars: Map.unmodifiable(nextStars),
      highScores: Map.unmodifiable(nextScores),
      soundEnabled: current.soundEnabled,
      hapticsEnabled: current.hapticsEnabled,
      rewardedAdsConsent: current.rewardedAdsConsent,
    );
    await _write(updated);
    return updated;
  }

  Future<GameProgress> setLastPlayedLevel(int levelId) async {
    final current = await load();
    final bounded = levelId.clamp(1, current.lastUnlockedLevel.clamp(1, maxLevel).toInt()).toInt();
    final updated = _copy(current, lastPlayedLevel: bounded);
    await _write(updated);
    return updated;
  }

  Future<GameProgress> setSettings({
    bool? soundEnabled,
    bool? hapticsEnabled,
    bool? rewardedAdsConsent,
  }) async {
    final current = await load();
    final updated = GameProgress(
      lastUnlockedLevel: current.lastUnlockedLevel,
      lastPlayedLevel: current.lastPlayedLevel,
      stars: current.stars,
      highScores: current.highScores,
      soundEnabled: soundEnabled ?? current.soundEnabled,
      hapticsEnabled: hapticsEnabled ?? current.hapticsEnabled,
      rewardedAdsConsent: rewardedAdsConsent ?? current.rewardedAdsConsent,
    );
    await _write(updated);
    return updated;
  }

  Future<void> _write(GameProgress progress) async {
    final store = await _store;
    await store.setString(storageKey, jsonEncode(progress.toJson()));
  }

  GameProgress _sanitize(Map<String, dynamic> json) {
    final maxUnlocked = _boundedInt(json['lastUnlockedLevel'], 1, maxLevel);
    final rawStars = json['stars'];
    final rawScores = json['highScores'];
    final stars = _parseMap(rawStars, maxValue: 3);
    final highScores = _parseMap(rawScores, maxValue: 1 << 30);
    return GameProgress(
      schemaVersion: 1,
      lastUnlockedLevel: maxUnlocked,
      lastPlayedLevel: _boundedInt(json['lastPlayedLevel'], 1, maxUnlocked),
      stars: Map.unmodifiable(stars),
      highScores: Map.unmodifiable(highScores),
      soundEnabled: json['soundEnabled'] is bool ? json['soundEnabled'] as bool : true,
      hapticsEnabled: json['hapticsEnabled'] is bool ? json['hapticsEnabled'] as bool : true,
      rewardedAdsConsent: json['rewardedAdsConsent'] is bool
          ? json['rewardedAdsConsent'] as bool
          : false,
    );
  }

  Map<int, int> _parseMap(Object? raw, {required int maxValue}) {
    if (raw is! Map) return {};
    final result = <int, int>{};
    for (final entry in raw.entries) {
      final id = int.tryParse(entry.key.toString());
      final value = entry.value;
      if (id == null || id < 1 || id > maxLevel || value is! int || value < 0) continue;
      result[id] = value.clamp(0, maxValue).toInt();
    }
    return result;
  }

  int _boundedInt(Object? value, int min, int max) =>
      value is int ? value.clamp(min, max).toInt() : min;

  GameProgress _copy(GameProgress old, {required int lastPlayedLevel}) => GameProgress(
        lastUnlockedLevel: old.lastUnlockedLevel,
        lastPlayedLevel: lastPlayedLevel,
        stars: old.stars,
        highScores: old.highScores,
        soundEnabled: old.soundEnabled,
        hapticsEnabled: old.hapticsEnabled,
        rewardedAdsConsent: old.rewardedAdsConsent,
      );
}

extension _IntMax on int {
  int max(int other) => this > other ? this : other;
}
