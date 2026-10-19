import '../../../game/models/economy_progress.dart';

class MapChestReward {
  const MapChestReward({
    required this.coins,
    required this.booster,
    this.boosterCount = 1,
  });

  final int coins;
  final BoosterType booster;
  final int boosterCount;
}

const Map<int, MapChestReward> mapChestRewards = {
  10: MapChestReward(coins: 80, booster: BoosterType.hammer),
  25: MapChestReward(coins: 100, booster: BoosterType.shuffle),
  40: MapChestReward(coins: 120, booster: BoosterType.hammer),
  60: MapChestReward(coins: 150, booster: BoosterType.extraMoves),
  80: MapChestReward(coins: 180, booster: BoosterType.shuffle),
  100: MapChestReward(coins: 250, booster: BoosterType.hammer, boosterCount: 2),
};
