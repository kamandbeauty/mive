class GameProgress {
  const GameProgress({
    this.schemaVersion = 1,
    this.lastUnlockedLevel = 1,
    this.lastPlayedLevel = 1,
    this.stars = const {},
    this.highScores = const {},
    this.soundEnabled = true,
    this.hapticsEnabled = true,
    this.rewardedAdsConsent = false,
  });

  final int schemaVersion;
  final int lastUnlockedLevel;
  final int lastPlayedLevel;
  final Map<int, int> stars;
  final Map<int, int> highScores;
  final bool soundEnabled;
  final bool hapticsEnabled;
  final bool rewardedAdsConsent;

  int starsFor(int levelId) => stars[levelId] ?? 0;
  int highScoreFor(int levelId) => highScores[levelId] ?? 0;

  Map<String, Object> toJson() => {
        'schemaVersion': 1,
        'lastUnlockedLevel': lastUnlockedLevel,
        'lastPlayedLevel': lastPlayedLevel,
        'stars': {for (final entry in stars.entries) '${entry.key}': entry.value},
        'highScores': {for (final entry in highScores.entries) '${entry.key}': entry.value},
        'soundEnabled': soundEnabled,
        'hapticsEnabled': hapticsEnabled,
        'rewardedAdsConsent': rewardedAdsConsent,
      };
}
