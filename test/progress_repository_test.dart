import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:puzzle/game/services/progress_repository.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('completion unlocks the next level and preserves best stars and score', () async {
    final repository = ProgressRepository();
    var progress = await repository.saveCompletion(levelId: 1, stars: 2, score: 420);
    expect(progress.lastUnlockedLevel, 2);
    expect(progress.lastPlayedLevel, 2);
    expect(progress.starsFor(1), 2);
    expect(progress.highScoreFor(1), 420);

    progress = await repository.saveCompletion(levelId: 1, stars: 1, score: 300);
    expect(progress.starsFor(1), 2);
    expect(progress.highScoreFor(1), 420);

    final reloaded = await ProgressRepository().load();
    expect(reloaded.lastUnlockedLevel, 2);
    expect(reloaded.starsFor(1), 2);
    expect(reloaded.highScoreFor(1), 420);
  });

  test('malformed save data is discarded and defaults are restored', () async {
    SharedPreferences.setMockInitialValues({ProgressRepository.storageKey: '{broken'});
    final progress = await ProgressRepository().load();
    expect(progress.lastUnlockedLevel, 1);
    expect(progress.stars, isEmpty);
    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString(ProgressRepository.storageKey), isNull);
  });

  test('out-of-range progress is bounded and settings round trip', () async {
    SharedPreferences.setMockInitialValues({
      ProgressRepository.storageKey:
          '{"schemaVersion":1,"lastUnlockedLevel":999,"lastPlayedLevel":-5,"stars":{"2":9,"500":3},"highScores":{"2":999999999999},"soundEnabled":false,"hapticsEnabled":true}',
    });
    final repository = ProgressRepository();
    final loaded = await repository.load();
    expect(loaded.lastUnlockedLevel, 100);
    expect(loaded.lastPlayedLevel, 1);
    expect(loaded.starsFor(2), 3);
    expect(loaded.stars.containsKey(500), isFalse);
    expect(loaded.highScoreFor(2), 1 << 30);
    expect(loaded.soundEnabled, isFalse);
    expect(loaded.rewardedAdsConsent, isFalse);

    final updated = await repository.setSettings(
      soundEnabled: true,
      hapticsEnabled: false,
      rewardedAdsConsent: true,
    );
    expect(updated.soundEnabled, isTrue);
    expect(updated.hapticsEnabled, isFalse);
    expect(updated.rewardedAdsConsent, isTrue);
    final reloaded = await ProgressRepository().load();
    expect(reloaded.soundEnabled, isTrue);
    expect(reloaded.hapticsEnabled, isFalse);
    expect(reloaded.rewardedAdsConsent, isTrue);
  });

  test('cannot persist completion for a locked future level', () async {
    final repository = ProgressRepository();
    await expectLater(
      repository.saveCompletion(levelId: 3, stars: 3, score: 900),
      throwsStateError,
    );
  });
}
