import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:puzzle/game/models/economy_progress.dart';
import 'package:puzzle/game/services/economy_repository.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('reward receipts are persistent and concurrent duplicate callbacks pay once', () async {
    final repository = EconomyRepository();
    final results = await Future.wait([
      repository.grantRewardOnce(
        claimId: 'ad-coins-impression-1',
        coins: 25,
        boosters: {BoosterType.hammer: 1},
      ),
      repository.grantRewardOnce(
        claimId: 'ad-coins-impression-1',
        coins: 25,
        boosters: {BoosterType.hammer: 1},
      ),
    ]);

    expect(results.where((granted) => granted), hasLength(1));
    final saved = await EconomyRepository().load();
    expect(saved.coins, 125);
    expect(saved.boostersOf(BoosterType.hammer), 1);
    expect(saved.rewardReceipts, contains('ad-coins-impression-1'));
  });

  test('daily base reward and ad double are distinct one-time claims', () async {
    final repository = EconomyRepository();
    const today = '2026-10-17';
    expect(
      await repository.claimDailyDouble(
        dayKey: today,
        claimId: 'daily-double:$today',
      ),
      isFalse,
    );
    expect(await repository.claimDailyReward(dayKey: today, coins: 50), isTrue);
    expect(await repository.claimDailyReward(dayKey: today, coins: 50), isFalse);
    expect(
      await repository.claimDailyDouble(
        dayKey: today,
        claimId: 'daily-double:$today',
      ),
      isTrue,
    );
    expect(
      await repository.claimDailyDouble(
        dayKey: today,
        claimId: 'daily-double:$today',
      ),
      isFalse,
    );

    final state = await EconomyRepository().load();
    expect(state.coins, 200);
    expect(state.dailyRewardDay, today);
    expect(state.dailyDoubleDay, today);
    expect(state.dailyRewardCoins, 50);
  });

  test('daily streak advances on the next local date and resets after a skipped day', () async {
    final repository = EconomyRepository();
    await repository.claimDailyReward(dayKey: '2026-10-17', coins: 20);
    expect(await repository.claimDailyReward(dayKey: '2026-10-18', coins: 20), isTrue);
    expect((await repository.load()).dailyStreak, 2);
    await repository.claimDailyReward(dayKey: '2026-10-20', coins: 20);
    expect((await repository.load()).dailyStreak, 1);
  });

  test('daily free booster ad cap is enforced and resets on a new day', () async {
    final repository = EconomyRepository();
    const dayOne = '2026-10-17';
    expect(
      await repository.claimFreeBooster(
        dayKey: dayOne,
        claimId: 'booster:$dayOne:1',
        booster: BoosterType.hammer,
        dailyLimit: 2,
      ),
      isTrue,
    );
    expect(
      await repository.claimFreeBooster(
        dayKey: dayOne,
        claimId: 'booster:$dayOne:2',
        booster: BoosterType.hammer,
        dailyLimit: 2,
      ),
      isTrue,
    );
    expect(
      await repository.claimFreeBooster(
        dayKey: dayOne,
        claimId: 'booster:$dayOne:3',
        booster: BoosterType.hammer,
        dailyLimit: 2,
      ),
      isFalse,
    );
    expect(
      await repository.claimFreeBooster(
        dayKey: '2026-10-18',
        claimId: 'booster:2026-10-18:1',
        booster: BoosterType.hammer,
        dailyLimit: 2,
      ),
      isTrue,
    );
    final state = await repository.load();
    expect(state.boostersOf(BoosterType.hammer), 3);
    expect(state.freeBoosterAdCount, 1);
    expect(state.freeBoosterDay, '2026-10-18');
  });

  test('extra moves and map chests are claimed at most once', () async {
    final repository = EconomyRepository();
    expect(await repository.claimExtraMoves(levelId: 8, claimId: 'moves:8'), isTrue);
    expect(
      await EconomyRepository().claimExtraMoves(levelId: 8, claimId: 'moves:8:retry'),
      isFalse,
    );
    expect(
      await repository.claimMapChest(
        chestId: 'chapter-1-marker-3',
        coins: 80,
        boosters: {BoosterType.shuffle: 1},
      ),
      isTrue,
    );
    expect(
      await EconomyRepository().claimMapChest(
        chestId: 'chapter-1-marker-3',
        coins: 80,
        boosters: {BoosterType.shuffle: 1},
      ),
      isFalse,
    );
    final state = await repository.load();
    expect(state.extraMoveLevels, contains(8));
    expect(state.openedChestIds, contains('chapter-1-marker-3'));
    expect(state.coins, 180);
    expect(state.boostersOf(BoosterType.shuffle), 1);
  });

  test('shop purchases atomically spend coins and increment booster inventory', () async {
    final repository = EconomyRepository();
    expect(
      await repository.purchaseBooster(booster: BoosterType.hammer, count: 1, cost: 80),
      isTrue,
    );
    expect(
      await repository.purchaseBooster(booster: BoosterType.shuffle, count: 1, cost: 60),
      isFalse,
    );
    final state = await repository.load();
    expect(state.coins, 20);
    expect(state.boostersOf(BoosterType.hammer), 1);
    expect(state.boostersOf(BoosterType.shuffle), 0);
  });

  test('using a booster on the board spends exactly one and never goes negative', () async {
    final repository = EconomyRepository();
    expect(await repository.consumeBooster(BoosterType.hammer), isFalse);

    await repository.grantRewardOnce(
      claimId: 'booster-fixture',
      boosters: {BoosterType.hammer: 2, BoosterType.shuffle: 1},
    );
    final results = await Future.wait([
      repository.consumeBooster(BoosterType.hammer),
      repository.consumeBooster(BoosterType.hammer),
      repository.consumeBooster(BoosterType.hammer),
    ]);
    expect(results.where((used) => used), hasLength(2));

    final state = await EconomyRepository().load();
    expect(state.boostersOf(BoosterType.hammer), 0);
    expect(state.boosters.containsKey(BoosterType.hammer), isFalse);
    expect(state.boostersOf(BoosterType.shuffle), 1);
    expect(await repository.consumeBooster(BoosterType.shuffle), isTrue);
    expect((await repository.load()).boostersOf(BoosterType.shuffle), 0);
  });

  test('invalid dates and corrupt saved data do not block the offline game', () async {
    final repository = EconomyRepository();
    expect(
      () => repository.claimDailyReward(dayKey: '2026-02-30', coins: 20),
      throwsFormatException,
    );

    SharedPreferences.setMockInitialValues({EconomyRepository.storageKey: '{bad json'});
    final reset = await EconomyRepository().load();
    expect(reset.coins, 100);
    expect(reset.boosters, isEmpty);
    expect(reset.openedChestIds, isEmpty);
  });
}
