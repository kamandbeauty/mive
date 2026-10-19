import 'package:flutter_test/flutter_test.dart';
import 'package:puzzle/game/services/rewarded_ad_service.dart';

class _FakeRewardedAdClient implements RewardedAdClient {
  void Function(String)? _onLoaded;
  void Function(String)? _onShown;
  void Function(String, bool)? _onClosed;
  void Function(String, String)? _onError;
  final List<String> prepared = <String>[];
  final List<String> shown = <String>[];
  int initializeCalls = 0;
  bool loaded = true;

  @override
  void setCallbacks({
    required void Function(String placement) onLoaded,
    required void Function(String placement) onShown,
    required void Function(String placement, bool isRewarded) onClosed,
    required void Function(String placement, String reason) onError,
  }) {
    _onLoaded = onLoaded;
    _onShown = onShown;
    _onClosed = onClosed;
    _onError = onError;
  }

  @override
  Future<void> initialize(String appId) async {
    initializeCalls++;
  }

  @override
  Future<bool> isLoaded(String placement) async => loaded;

  @override
  Future<void> prepare(String placement) async {
    prepared.add(placement);
    if (loaded) _onLoaded?.call(placement);
  }

  @override
  Future<void> show(String placement) async {
    shown.add(placement);
    _onShown?.call(placement);
  }

  void close(String placement, {required bool rewarded}) =>
      _onClosed?.call(placement, rewarded);

  void fail(String placement, String reason) => _onError?.call(placement, reason);
}

RewardedAdConfig _config({String placement = 'reward-placement'}) => RewardedAdConfig(
      appId: 'app-id',
      placements: {for (final useCase in RewardedUseCase.values) useCase: placement},
    );

void main() {
  test('reward tuning has safe defaults', () {
    expect(RewardedAdAmounts.coinBonus, 25);
    expect(RewardedAdAmounts.freeBoosterDailyLimit, 1);
    expect(RewardedAdAmounts.extraMoves, 5);
    expect(RewardedAdAmounts.dailyBaseCoins, 50);
  });

  test('debug uses Adivery public demo credentials and release requires app IDs', () {
    final debug = RewardedAdConfig.fromBuildMode(debugBuild: true);
    expect(debug.appId, RewardedAdConfig.demoAppId);
    expect(debug.placementFor(RewardedUseCase.coinBonus),
        RewardedAdConfig.demoRewardedPlacementId);

    final release = RewardedAdConfig.fromBuildMode(debugBuild: false);
    expect(release.isConfigured, isFalse);
    expect(release.supports(RewardedUseCase.coinBonus), isFalse);
  });

  test('SDK initialization and ad requests require explicit consent', () async {
    final client = _FakeRewardedAdClient();
    final service = RewardedAdService(config: _config(), client: client);
    addTearDown(service.dispose);

    await service.setConsent(false);
    await service.setConsent(true);
    expect(client.initializeCalls, 1);
    expect(client.prepared, ['reward-placement']);
    expect(service.state, RewardedAdState.loaded);

    await service.setConsent(false);
    expect(service.state, RewardedAdState.disabled);
    var payments = 0;
    final result = await service.show(
      useCase: RewardedUseCase.coinBonus,
      claimId: 'consent-revoked',
      onEarned: (_) async {
        payments++;
      },
    );
    client.close('reward-placement', rewarded: true);
    expect(result.outcome, RewardedAdOutcome.unavailable);
    expect(payments, 0);

    await service.setConsent(true);
    expect(client.initializeCalls, 1);
    expect(client.prepared.length, greaterThanOrEqualTo(2));
  });

  test('pays once only after Adivery confirms reward and ignores duplicate callback', () async {
    final client = _FakeRewardedAdClient();
    final service = RewardedAdService(config: _config(), client: client);
    addTearDown(service.dispose);
    var payments = 0;

    await service.setConsent(true);
    final resultFuture = service.show(
      useCase: RewardedUseCase.coinBonus,
      claimId: 'coins:2026-10-17:player',
      onEarned: (_) async {
        payments++;
      },
    );
    await Future<void>.delayed(Duration.zero);
    client.close('reward-placement', rewarded: true);
    final result = await resultFuture;
    client.close('reward-placement', rewarded: true);

    expect(result.outcome, RewardedAdOutcome.earned);
    expect(payments, 1);
    expect(client.shown, ['reward-placement']);
    expect(client.prepared.length, greaterThanOrEqualTo(2));

    final repeatedClaim = await service.show(
      useCase: RewardedUseCase.coinBonus,
      claimId: 'coins:2026-10-17:player',
      onEarned: (_) async {
        payments++;
      },
    );
    expect(repeatedClaim.outcome, RewardedAdOutcome.alreadyClaimed);
    expect(payments, 1);
    expect(client.shown, ['reward-placement']);
  });

  test('closing without earned reward never pays', () async {
    final client = _FakeRewardedAdClient();
    final service = RewardedAdService(config: _config(), client: client);
    addTearDown(service.dispose);
    var payments = 0;

    await service.setConsent(true);
    final resultFuture = service.show(
      useCase: RewardedUseCase.dailyDouble,
      claimId: 'daily-double:2026-10-17',
      onEarned: (_) async {
        payments++;
      },
    );
    await Future<void>.delayed(Duration.zero);
    client.close('reward-placement', rewarded: false);

    expect((await resultFuture).outcome, RewardedAdOutcome.dismissed);
    expect(payments, 0);
  });

  test('unavailable inventory and SDK errors return safely without reward', () async {
    final unavailableClient = _FakeRewardedAdClient()..loaded = false;
    final unavailableService = RewardedAdService(
      config: _config(),
      client: unavailableClient,
    );
    addTearDown(unavailableService.dispose);
    await unavailableService.setConsent(true);
    var payments = 0;

    final unavailable = await unavailableService.show(
      useCase: RewardedUseCase.freeBooster,
      claimId: 'booster:2026-10-17:1',
      onEarned: (_) async {
        payments++;
      },
    );
    expect(unavailable.outcome, RewardedAdOutcome.unavailable);
    expect(unavailableClient.shown, isEmpty);

    final client = _FakeRewardedAdClient();
    final service = RewardedAdService(config: _config(), client: client);
    addTearDown(service.dispose);
    await service.setConsent(true);
    final failedFuture = service.show(
      useCase: RewardedUseCase.extraMoves,
      claimId: 'moves:level-8',
      onEarned: (_) async {
        payments++;
      },
    );
    await Future<void>.delayed(Duration.zero);
    client.fail('reward-placement', 'network');

    final failed = await failedFuture;
    expect(failed.outcome, RewardedAdOutcome.failed);
    expect(failed.error, 'network');
    expect(payments, 0);
  });

  test('callback timeout keeps the old impression reserved against misattribution', () async {
    final client = _FakeRewardedAdClient();
    final service = RewardedAdService(
      config: _config(),
      client: client,
      callbackTimeout: const Duration(milliseconds: 10),
    );
    addTearDown(service.dispose);
    var payments = 0;

    await service.setConsent(true);
    final resultFuture = service.show(
      useCase: RewardedUseCase.mapChest,
      claimId: 'chest:chapter-1:marker-2',
      onEarned: (_) async {
        payments++;
      },
    );
    expect((await resultFuture).error, 'reward_callback_timeout');

    final whileLateCallbackPending = await service.show(
      useCase: RewardedUseCase.coinBonus,
      claimId: 'coins:another-claim',
      onEarned: (_) async {
        payments++;
      },
    );
    expect(whileLateCallbackPending.outcome, RewardedAdOutcome.busy);

    client.close('reward-placement', rewarded: true);
    expect(payments, 0);
  });
}
