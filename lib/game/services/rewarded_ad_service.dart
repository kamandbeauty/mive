import 'dart:async';

import 'package:adivery/adivery.dart';
import 'package:flutter/foundation.dart';

/// Adivery's rewarded-close callback is the only authoritative reward signal.
/// Closing an ad without [isRewarded] never grants a game reward.
enum RewardedAdState { disabled, idle, loading, loaded, showing, earned, dismissed, failed }

enum RewardedUseCase { coinBonus, extraMoves, freeBooster, dailyDouble, mapChest }

enum RewardedAdOutcome { earned, dismissed, unavailable, failed, alreadyClaimed, busy }

class RewardedAdAmounts {
  static const int _coinBonus = int.fromEnvironment('AD_REWARD_COINS', defaultValue: 25);
  static const int _freeBoosterDailyLimit =
      int.fromEnvironment('AD_FREE_BOOSTER_DAILY_LIMIT', defaultValue: 1);

  static int get coinBonus => _coinBonus.clamp(1, 100000).toInt();
  static int get freeBoosterDailyLimit => _freeBoosterDailyLimit.clamp(0, 50).toInt();
  static const int extraMoves = 5;
  static const int dailyBaseCoins = 50;
}

@immutable
class RewardedAdConfig {
  const RewardedAdConfig({required this.appId, required this.placements});

  /// Public demo credentials documented in Adivery's official Android sample.
  static const demoAppId = 'd5247816-a170-4b90-b4e2-c0f1fcd804a4';
  static const demoRewardedPlacementId = '271c8bd4-9000-476e-b096-6ef9bee296dc';

  static const _releaseAppId = String.fromEnvironment('ADIVERY_APP_ID');
  static const _releaseCoinPlacement =
      String.fromEnvironment('ADIVERY_REWARDED_COINS_PLACEMENT_ID');
  static const _releaseExtraMovesPlacement =
      String.fromEnvironment('ADIVERY_REWARDED_EXTRA_MOVES_PLACEMENT_ID');
  static const _releaseBoosterPlacement =
      String.fromEnvironment('ADIVERY_REWARDED_BOOSTER_PLACEMENT_ID');
  static const _releaseDailyDoublePlacement =
      String.fromEnvironment('ADIVERY_REWARDED_DAILY_DOUBLE_PLACEMENT_ID');
  static const _releaseMapChestPlacement =
      String.fromEnvironment('ADIVERY_REWARDED_MAP_CHEST_PLACEMENT_ID');
  static const _releaseFallbackPlacement =
      String.fromEnvironment('ADIVERY_REWARDED_PLACEMENT_ID');

  factory RewardedAdConfig.fromBuildMode({required bool debugBuild}) {
    if (debugBuild) {
      const demoPlacement = String.fromEnvironment(
        'ADIVERY_DEMO_REWARDED_PLACEMENT_ID',
        defaultValue: demoRewardedPlacementId,
      );
      const demoApp = String.fromEnvironment(
        'ADIVERY_DEMO_APP_ID',
        defaultValue: demoAppId,
      );
      return RewardedAdConfig(
        appId: demoApp,
        placements: {for (final useCase in RewardedUseCase.values) useCase: demoPlacement},
      );
    }

    String? configured(String specific) =>
        specific.isNotEmpty ? specific : _releaseFallbackPlacement;

    return RewardedAdConfig(
      appId: _releaseAppId,
      placements: {
        RewardedUseCase.coinBonus: configured(_releaseCoinPlacement),
        RewardedUseCase.extraMoves: configured(_releaseExtraMovesPlacement),
        RewardedUseCase.freeBooster: configured(_releaseBoosterPlacement),
        RewardedUseCase.dailyDouble: configured(_releaseDailyDoublePlacement),
        RewardedUseCase.mapChest: configured(_releaseMapChestPlacement),
      },
    );
  }

  final String appId;
  final Map<RewardedUseCase, String?> placements;

  String? placementFor(RewardedUseCase useCase) {
    final placement = placements[useCase]?.trim();
    return placement == null || placement.isEmpty ? null : placement;
  }

  bool get isConfigured =>
      appId.trim().isNotEmpty && RewardedUseCase.values.any((useCase) => placementFor(useCase) != null);

  bool supports(RewardedUseCase useCase) => isConfigured && placementFor(useCase) != null;
}

/// Minimal bridge around the official Flutter plugin. It is separate from UI
/// and can be replaced with a deterministic fake in tests.
abstract interface class RewardedAdClient {
  void setCallbacks({
    required void Function(String placement) onLoaded,
    required void Function(String placement) onShown,
    required void Function(String placement, bool isRewarded) onClosed,
    required void Function(String placement, String reason) onError,
  });

  Future<void> initialize(String appId);
  Future<void> prepare(String placement);
  Future<bool> isLoaded(String placement);
  Future<void> show(String placement);
}

class AdiveryRewardedAdClient implements RewardedAdClient {
  @override
  void setCallbacks({
    required void Function(String placement) onLoaded,
    required void Function(String placement) onShown,
    required void Function(String placement, bool isRewarded) onClosed,
    required void Function(String placement, String reason) onError,
  }) {
    AdiveryPlugin.addListener(
      onRewardedLoaded: onLoaded,
      onRewardedShown: onShown,
      onRewardedClosed: onClosed,
      onError: onError,
    );
  }

  @override
  Future<void> initialize(String appId) async {
    // The official Flutter plugin exposes this as async void. Its Android
    // method-channel events are surfaced through the callbacks above.
    AdiveryPlugin.initialize(appId);
  }

  @override
  Future<void> prepare(String placement) async {
    AdiveryPlugin.prepareRewardedAd(placement);
  }

  @override
  Future<bool> isLoaded(String placement) =>
      AdiveryPlugin.isLoaded(placement).then((loaded) => loaded ?? false);

  @override
  Future<void> show(String placement) async {
    AdiveryPlugin.show(placement);
  }
}

@immutable
class RewardedAdResult {
  const RewardedAdResult(this.outcome, {this.error});

  final RewardedAdOutcome outcome;
  final String? error;

  bool get earned => outcome == RewardedAdOutcome.earned;
}

@immutable
class RewardedAdSnapshot {
  const RewardedAdSnapshot({required this.state, this.useCase, this.error});

  final RewardedAdState state;
  final RewardedUseCase? useCase;
  final String? error;
}

class _RewardSession {
  _RewardSession({
    required this.claimId,
    required this.useCase,
    required this.placement,
    required this.onEarned,
  });

  final String claimId;
  final RewardedUseCase useCase;
  final String placement;
  final Future<void> Function(String claimId) onEarned;
  final Completer<RewardedAdResult> completer = Completer<RewardedAdResult>();
  Timer? timeoutTimer;
  bool settling = false;
  bool timedOut = false;
}

/// Coordinates one Adivery rewarded impression at a time and pays only after
/// Adivery closes that exact active placement with `isRewarded == true`.
class RewardedAdService {
  RewardedAdService({
    required this.config,
    RewardedAdClient? client,
    this.callbackTimeout = const Duration(minutes: 3),
  }) : _client = client ?? AdiveryRewardedAdClient();

  final RewardedAdConfig config;
  final RewardedAdClient _client;
  final Duration callbackTimeout;
  final StreamController<RewardedAdSnapshot> _states =
      StreamController<RewardedAdSnapshot>.broadcast();
  final Set<String> _settledClaimIds = <String>{};
  final Set<String> _loadedPlacements = <String>{};

  RewardedAdState _state = RewardedAdState.idle;
  RewardedUseCase? _activeUseCase;
  _RewardSession? _session;
  bool _initialized = false;
  bool _initializing = false;
  bool _consentGranted = false;
  bool _disposed = false;

  Stream<RewardedAdSnapshot> get states => _states.stream;
  bool get consentGranted => _consentGranted;
  RewardedAdState get state =>
      config.isConfigured && _consentGranted ? _state : RewardedAdState.disabled;

  Future<void> setConsent(bool granted) async {
    if (_disposed || _consentGranted == granted) return;
    _consentGranted = granted;
    if (!granted) {
      final session = _session;
      if (session != null) {
        _finish(session, RewardedAdOutcome.failed, error: 'consent_revoked');
      }
      _loadedPlacements.clear();
      _emit(RewardedAdState.disabled);
      return;
    }
    await _initialize();
  }

  Future<void> _initialize() async {
    if (_disposed || !_consentGranted || _initializing || !config.isConfigured) return;
    if (_initialized) {
      final placements = RewardedUseCase.values
          .map(config.placementFor)
          .whereType<String>()
          .toSet();
      await Future.wait(placements.map(_prepare));
      return;
    }
    _initializing = true;
    _emit(RewardedAdState.loading);
    try {
      _client.setCallbacks(
        onLoaded: _onLoaded,
        onShown: _onShown,
        onClosed: _onClosed,
        onError: _onError,
      );
      await _client.initialize(config.appId.trim()).timeout(callbackTimeout);
      _initialized = true;
      final placements = RewardedUseCase.values
          .map(config.placementFor)
          .whereType<String>()
          .toSet();
      await Future.wait(placements.map(_prepare));
      if (_state == RewardedAdState.loading) _emit(RewardedAdState.idle);
    } catch (error) {
      _emit(RewardedAdState.failed, error: error.toString());
    } finally {
      _initializing = false;
    }
  }

  Future<RewardedAdResult> show({
    required RewardedUseCase useCase,
    required String claimId,
    required Future<void> Function(String claimId) onEarned,
  }) async {
    final normalizedClaimId = claimId.trim();
    if (normalizedClaimId.isEmpty) {
      return const RewardedAdResult(RewardedAdOutcome.failed, error: 'empty_claim_id');
    }
    if (_settledClaimIds.contains(normalizedClaimId)) {
      return const RewardedAdResult(RewardedAdOutcome.alreadyClaimed);
    }
    if (!config.supports(useCase) || !_consentGranted || !_initialized) {
      return const RewardedAdResult(RewardedAdOutcome.unavailable);
    }
    if (_session != null) return const RewardedAdResult(RewardedAdOutcome.busy);

    final placement = config.placementFor(useCase)!;
    try {
      final ready = await _client.isLoaded(placement);
      if (!ready) {
        _loadedPlacements.remove(placement);
        unawaited(_prepare(placement));
        _emit(RewardedAdState.loading, useCase: useCase);
        return const RewardedAdResult(RewardedAdOutcome.unavailable);
      }

      final session = _RewardSession(
        claimId: normalizedClaimId,
        useCase: useCase,
        placement: placement,
        onEarned: onEarned,
      );
      _session = session;
      _activeUseCase = useCase;
      _loadedPlacements.remove(placement);
      _emit(RewardedAdState.showing, useCase: useCase);
      session.timeoutTimer = Timer(callbackTimeout, () => _expireSession(session));
      unawaited(
        _client.show(placement).catchError((Object error) {
          _onError(placement, error.toString());
        }),
      );
      return await session.completer.future;
    } catch (error) {
      final current = _session;
      if (current != null && current.claimId == normalizedClaimId) {
        _finish(current, RewardedAdOutcome.failed, error: error.toString());
      }
      return RewardedAdResult(RewardedAdOutcome.failed, error: error.toString());
    }
  }

  void _expireSession(_RewardSession session) {
    if (identical(_session, session) && !session.completer.isCompleted) {
      // Keep the session reserved after timing out. Adivery callbacks identify
      // an impression by placement, not by a unique impression token; allowing
      // another show here could attribute a late reward to the wrong request.
      session.timedOut = true;
      session.completer.complete(
        const RewardedAdResult(RewardedAdOutcome.failed, error: 'reward_callback_timeout'),
      );
      _emit(RewardedAdState.failed, useCase: session.useCase, error: 'reward_callback_timeout');
    }
  }

  Future<void> _prepare(String placement) async {
    if (_disposed || !_consentGranted || !_initialized || _loadedPlacements.contains(placement)) return;
    try {
      await _client.prepare(placement).timeout(callbackTimeout);
    } catch (error) {
      _onError(placement, error.toString());
    }
  }

  void _onLoaded(String placement) {
    if (!_consentGranted || _disposed) return;
    _loadedPlacements.add(placement);
    if (_session == null) _emit(RewardedAdState.loaded);
  }

  void _onShown(String placement) {
    final session = _session;
    if (session == null || session.placement != placement) return;
    _emit(RewardedAdState.showing, useCase: session.useCase);
  }

  void _onClosed(String placement, bool isRewarded) {
    final session = _session;
    if (session == null || session.placement != placement || session.settling) return;
    if (session.timedOut) {
      _finish(
        session,
        RewardedAdOutcome.failed,
        error: 'reward_callback_after_timeout',
      );
      return;
    }
    if (!isRewarded) {
      _finish(session, RewardedAdOutcome.dismissed);
      return;
    }
    session.settling = true;
    unawaited(_settleEarned(session));
  }

  Future<void> _settleEarned(_RewardSession session) async {
    try {
      // Record before notifying UI so a duplicate callback cannot invoke the
      // game economy twice during this process. The repository also persists
      // claim IDs across restarts to provide durable idempotency.
      if (!_settledClaimIds.add(session.claimId)) {
        _finish(session, RewardedAdOutcome.alreadyClaimed);
        return;
      }
      await session.onEarned(session.claimId);
      _finish(session, RewardedAdOutcome.earned);
    } catch (error) {
      _settledClaimIds.remove(session.claimId);
      _finish(session, RewardedAdOutcome.failed, error: error.toString());
    }
  }

  void _onError(String placement, String reason) {
    _loadedPlacements.remove(placement);
    final session = _session;
    if (session != null && session.placement == placement) {
      _finish(session, RewardedAdOutcome.failed, error: reason);
    } else {
      _emit(RewardedAdState.failed, error: reason);
    }
  }

  void _finish(
    _RewardSession session,
    RewardedAdOutcome outcome, {
    String? error,
  }) {
    session.timeoutTimer?.cancel();
    if (identical(_session, session)) {
      _session = null;
      _activeUseCase = null;
    }
    if (!session.completer.isCompleted) {
      session.completer.complete(RewardedAdResult(outcome, error: error));
    }
    final nextState = switch (outcome) {
      RewardedAdOutcome.earned => RewardedAdState.earned,
      RewardedAdOutcome.dismissed => RewardedAdState.dismissed,
      RewardedAdOutcome.failed => RewardedAdState.failed,
      _ => RewardedAdState.idle,
    };
    _emit(nextState, useCase: session.useCase, error: error);
    unawaited(_prepare(session.placement));
  }

  void _emit(RewardedAdState state, {RewardedUseCase? useCase, String? error}) {
    _state = state;
    if (!_disposed) _states.add(RewardedAdSnapshot(state: state, useCase: useCase ?? _activeUseCase, error: error));
  }

  /// Releases Dart-side resources. The official plugin exposes no per-ad
  /// destroy API; its native lifecycle is owned by Flutter/plugin detach.
  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    final session = _session;
    session?.timeoutTimer?.cancel();
    if (session != null && !session.completer.isCompleted) {
      session.completer.complete(const RewardedAdResult(RewardedAdOutcome.failed, error: 'disposed'));
    }
    _session = null;
    _activeUseCase = null;
    await _states.close();
  }
}
