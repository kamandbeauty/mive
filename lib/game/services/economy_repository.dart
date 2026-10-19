import 'dart:async';
import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../models/economy_progress.dart';

/// Local-only economy storage. It is intentionally offline and is not a
/// tamper-proof ledger; changing the device clock or local app data can affect it.
class EconomyRepository {
  EconomyRepository({SharedPreferences? initialPreferences})
      : _preferences = initialPreferences;

  static const storageKey = 'fruit_match_economy_v1';
  static const maxCoins = 1 << 30;
  static const maxBoosterCount = 999;
  static const maxReceiptCount = 2000;

  SharedPreferences? _preferences;
  Future<void> _tail = Future<void>.value();

  Future<SharedPreferences> get _store async =>
      _preferences ??= await SharedPreferences.getInstance();

  Future<EconomyProgress> load() async {
    final store = await _store;
    final raw = store.getString(storageKey);
    if (raw == null) return const EconomyProgress();
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic> || decoded['schemaVersion'] != 1) {
        throw const FormatException('Unsupported economy schema.');
      }
      return _sanitize(decoded);
    } catch (_) {
      await store.remove(storageKey);
      return const EconomyProgress();
    }
  }

  Future<bool> grantRewardOnce({
    required String claimId,
    int coins = 0,
    Map<BoosterType, int> boosters = const {},
  }) {
    if (coins < 0 || boosters.values.any((count) => count < 0)) {
      throw RangeError('Reward values cannot be negative.');
    }
    return _serialize(() async {
      final current = await load();
      final id = claimId.trim();
      if (id.isEmpty || current.rewardReceipts.contains(id)) return false;
      final nextBoosters = Map<BoosterType, int>.from(current.boosters);
      for (final entry in boosters.entries) {
        nextBoosters[entry.key] =
            ((nextBoosters[entry.key] ?? 0) + entry.value).clamp(0, maxBoosterCount).toInt();
      }
      final updated = _copy(
        current,
        coins: _clampCoins(current.coins + coins),
        boosters: nextBoosters,
        rewardReceipts: _withReceipt(current.rewardReceipts, id),
      );
      await _write(updated);
      return true;
    });
  }

  Future<bool> claimDailyReward({required String dayKey, required int coins}) {
    _validateDayAndAmount(dayKey, coins);
    return _serialize(() async {
      final current = await load();
      if (current.dailyRewardDay == dayKey) return false;
      final previousDay = _previousDay(dayKey);
      final streak = current.dailyRewardDay == previousDay ? current.dailyStreak + 1 : 1;
      final updated = _copy(
        current,
        coins: _clampCoins(current.coins + coins),
        dailyRewardDay: dayKey,
        dailyRewardCoins: coins,
        dailyStreak: streak.clamp(1, 3650).toInt(),
      );
      await _write(updated);
      return true;
    });
  }

  /// Adds the extra half of today's base reward; the base claim and the ad
  /// double are recorded independently so reopening the screen cannot repeat it.
  Future<bool> claimDailyDouble({
    required String dayKey,
    required String claimId,
  }) {
    if (_validDay(dayKey) == null) throw FormatException('Invalid day key: $dayKey');
    return _serialize(() async {
      final current = await load();
      final id = claimId.trim();
      if (current.dailyRewardDay != dayKey ||
          current.dailyRewardCoins <= 0 ||
          current.dailyDoubleDay == dayKey ||
          id.isEmpty ||
          current.rewardReceipts.contains(id)) {
        return false;
      }
      final updated = _copy(
        current,
        coins: _clampCoins(current.coins + current.dailyRewardCoins),
        dailyDoubleDay: dayKey,
        rewardReceipts: _withReceipt(current.rewardReceipts, id),
      );
      await _write(updated);
      return true;
    });
  }

  Future<bool> claimFreeBooster({
    required String dayKey,
    required String claimId,
    required BoosterType booster,
    required int dailyLimit,
  }) {
    _validateDayAndAmount(dayKey, dailyLimit);
    return _serialize(() async {
      final current = await load();
      final usedToday = current.freeBoosterDay == dayKey ? current.freeBoosterAdCount : 0;
      final id = claimId.trim();
      if (dailyLimit == 0 || usedToday >= dailyLimit || id.isEmpty ||
          current.rewardReceipts.contains(id)) {
        return false;
      }
      final nextBoosters = Map<BoosterType, int>.from(current.boosters);
      nextBoosters[booster] = ((nextBoosters[booster] ?? 0) + 1).clamp(0, maxBoosterCount).toInt();
      final updated = _copy(
        current,
        boosters: nextBoosters,
        freeBoosterDay: dayKey,
        freeBoosterAdCount: usedToday + 1,
        rewardReceipts: _withReceipt(current.rewardReceipts, id),
      );
      await _write(updated);
      return true;
    });
  }

  Future<bool> claimExtraMoves({required int levelId, required String claimId}) {
    if (levelId < 1) throw RangeError.range(levelId, 1, null, 'levelId');
    return _serialize(() async {
      final current = await load();
      final id = claimId.trim();
      if (id.isEmpty || current.extraMoveLevels.contains(levelId) ||
          current.rewardReceipts.contains(id)) {
        return false;
      }
      final updated = _copy(
        current,
        extraMoveLevels: {...current.extraMoveLevels, levelId},
        rewardReceipts: _withReceipt(current.rewardReceipts, id),
      );
      await _write(updated);
      return true;
    });
  }

  Future<bool> claimMapChest({
    required String chestId,
    required int coins,
    Map<BoosterType, int> boosters = const {},
  }) {
    if (chestId.trim().isEmpty || coins < 0 || boosters.values.any((count) => count < 0)) {
      throw ArgumentError('Chest ID and reward values must be valid.');
    }
    return _serialize(() async {
      final current = await load();
      final id = chestId.trim();
      if (current.openedChestIds.contains(id)) return false;
      final nextBoosters = Map<BoosterType, int>.from(current.boosters);
      for (final entry in boosters.entries) {
        nextBoosters[entry.key] =
            ((nextBoosters[entry.key] ?? 0) + entry.value).clamp(0, maxBoosterCount).toInt();
      }
      final updated = _copy(
        current,
        coins: _clampCoins(current.coins + coins),
        boosters: nextBoosters,
        openedChestIds: {...current.openedChestIds, id},
      );
      await _write(updated);
      return true;
    });
  }

  Future<bool> purchaseBooster({
    required BoosterType booster,
    required int count,
    required int cost,
  }) {
    if (count < 1 || cost < 1) throw RangeError('Purchase count and cost must be positive.');
    return _serialize(() async {
      final current = await load();
      if (current.coins < cost ||
          current.boostersOf(booster) + count > maxBoosterCount) {
        return false;
      }
      final nextBoosters = Map<BoosterType, int>.from(current.boosters)
        ..[booster] = current.boostersOf(booster) + count;
      await _write(_copy(current, coins: current.coins - cost, boosters: nextBoosters));
      return true;
    });
  }

  Future<bool> spendCoins(int amount) {
    if (amount < 1) throw RangeError.range(amount, 1, maxCoins, 'amount');
    return _serialize(() async {
      final current = await load();
      if (current.coins < amount) return false;
      await _write(_copy(current, coins: current.coins - amount));
      return true;
    });
  }

  /// Deducts exactly one booster for use on the board. The check and the write
  /// are serialized with purchases, so two taps cannot spend the same booster.
  Future<bool> consumeBooster(BoosterType booster) {
    return _serialize(() async {
      final current = await load();
      final count = current.boostersOf(booster);
      if (count < 1) return false;
      final nextBoosters = Map<BoosterType, int>.from(current.boosters);
      if (count == 1) {
        nextBoosters.remove(booster);
      } else {
        nextBoosters[booster] = count - 1;
      }
      await _write(_copy(current, boosters: nextBoosters));
      return true;
    });
  }

  Future<T> _serialize<T>(Future<T> Function() operation) {
    final result = Completer<T>();
    _tail = _tail.then((_) async {
      try {
        result.complete(await operation());
      } catch (error, stackTrace) {
        result.completeError(error, stackTrace);
      }
    });
    return result.future;
  }

  Future<void> _write(EconomyProgress progress) async {
    final store = await _store;
    await store.setString(storageKey, jsonEncode(progress.toJson()));
  }

  EconomyProgress _sanitize(Map<String, dynamic> json) {
    final rawBoosters = json['boosters'];
    final boosters = <BoosterType, int>{};
    if (rawBoosters is Map) {
      for (final type in BoosterType.values) {
        final value = rawBoosters[type.name];
        if (value is int && value > 0) {
          boosters[type] = value.clamp(0, maxBoosterCount).toInt();
        }
      }
    }
    return EconomyProgress(
      schemaVersion: 1,
      coins: _boundedInt(json['coins'], 0, maxCoins),
      boosters: Map.unmodifiable(boosters),
      rewardReceipts: Set.unmodifiable(_parseStrings(json['rewardReceipts'])),
      dailyRewardDay: _validDay(json['dailyRewardDay']),
      dailyRewardCoins: _boundedInt(json['dailyRewardCoins'], 0, maxCoins),
      dailyStreak: _boundedInt(json['dailyStreak'], 0, 3650),
      dailyDoubleDay: _validDay(json['dailyDoubleDay']),
      freeBoosterDay: _validDay(json['freeBoosterDay']),
      freeBoosterAdCount: _boundedInt(json['freeBoosterAdCount'], 0, 1000),
      extraMoveLevels: Set.unmodifiable(_parseInts(json['extraMoveLevels'])),
      openedChestIds: Set.unmodifiable(_parseStrings(json['openedChestIds'])),
    );
  }

  EconomyProgress _copy(
    EconomyProgress current, {
    int? coins,
    Map<BoosterType, int>? boosters,
    Set<String>? rewardReceipts,
    String? dailyRewardDay,
    int? dailyRewardCoins,
    int? dailyStreak,
    String? dailyDoubleDay,
    String? freeBoosterDay,
    int? freeBoosterAdCount,
    Set<int>? extraMoveLevels,
    Set<String>? openedChestIds,
  }) =>
      EconomyProgress(
        coins: coins ?? current.coins,
        boosters: Map.unmodifiable(boosters ?? current.boosters),
        rewardReceipts: Set.unmodifiable(rewardReceipts ?? current.rewardReceipts),
        dailyRewardDay: dailyRewardDay ?? current.dailyRewardDay,
        dailyRewardCoins: dailyRewardCoins ?? current.dailyRewardCoins,
        dailyStreak: dailyStreak ?? current.dailyStreak,
        dailyDoubleDay: dailyDoubleDay ?? current.dailyDoubleDay,
        freeBoosterDay: freeBoosterDay ?? current.freeBoosterDay,
        freeBoosterAdCount: freeBoosterAdCount ?? current.freeBoosterAdCount,
        extraMoveLevels: Set.unmodifiable(extraMoveLevels ?? current.extraMoveLevels),
        openedChestIds: Set.unmodifiable(openedChestIds ?? current.openedChestIds),
      );

  Set<String> _withReceipt(Set<String> current, String id) {
    final receipts = <String>{...current, id};
    if (receipts.length > maxReceiptCount) {
      return receipts.skip(receipts.length - maxReceiptCount).toSet();
    }
    return receipts;
  }

  Set<String> _parseStrings(Object? value) {
    if (value is! List) return <String>{};
    return value
        .whereType<String>()
        .map((item) => item.trim())
        .where((item) => item.isNotEmpty && item.length <= 160)
        .toSet()
        .take(maxReceiptCount)
        .toSet();
  }

  Set<int> _parseInts(Object? value) {
    if (value is! List) return <int>{};
    return value.whereType<int>().where((item) => item > 0 && item <= 10000).toSet();
  }

  int _boundedInt(Object? value, int min, int max) =>
      value is int ? value.clamp(min, max).toInt() : min;

  int _clampCoins(int coins) => coins.clamp(0, maxCoins).toInt();

  String? _validDay(Object? value) {
    if (value is! String) return null;
    final parsed = DateTime.tryParse(value);
    if (parsed == null || parsed.toIso8601String().substring(0, 10) != value) return null;
    return value;
  }

  String _previousDay(String dayKey) {
    final day = DateTime.parse(dayKey);
    return DateTime(day.year, day.month, day.day - 1).toIso8601String().substring(0, 10);
  }

  void _validateDayAndAmount(String dayKey, int amount) {
    if (_validDay(dayKey) == null) throw FormatException('Invalid day key: $dayKey');
    if (amount < 0) throw RangeError('Reward amount cannot be negative.');
  }
}
