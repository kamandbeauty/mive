enum BoosterType { hammer, shuffle, extraMoves }

class EconomyProgress {
  const EconomyProgress({
    this.schemaVersion = 1,
    this.coins = 100,
    this.boosters = const {},
    this.rewardReceipts = const {},
    this.dailyRewardDay,
    this.dailyRewardCoins = 0,
    this.dailyStreak = 0,
    this.dailyDoubleDay,
    this.freeBoosterDay,
    this.freeBoosterAdCount = 0,
    this.extraMoveLevels = const {},
    this.openedChestIds = const {},
  });

  final int schemaVersion;
  final int coins;
  final Map<BoosterType, int> boosters;
  final Set<String> rewardReceipts;
  final String? dailyRewardDay;
  final int dailyRewardCoins;
  final int dailyStreak;
  final String? dailyDoubleDay;
  final String? freeBoosterDay;
  final int freeBoosterAdCount;
  final Set<int> extraMoveLevels;
  final Set<String> openedChestIds;

  int boostersOf(BoosterType type) => boosters[type] ?? 0;

  Map<String, Object?> toJson() => {
        'schemaVersion': schemaVersion,
        'coins': coins,
        'boosters': {for (final entry in boosters.entries) entry.key.name: entry.value},
        'rewardReceipts': rewardReceipts.toList(growable: false),
        'dailyRewardDay': dailyRewardDay,
        'dailyRewardCoins': dailyRewardCoins,
        'dailyStreak': dailyStreak,
        'dailyDoubleDay': dailyDoubleDay,
        'freeBoosterDay': freeBoosterDay,
        'freeBoosterAdCount': freeBoosterAdCount,
        'extraMoveLevels': extraMoveLevels.toList(growable: false),
        'openedChestIds': openedChestIds.toList(growable: false),
      };
}
