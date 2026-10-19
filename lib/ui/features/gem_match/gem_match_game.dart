import 'dart:async';
import 'dart:math';

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../theme/game_palette.dart';
import '../../widgets/game_ui/game_ui.dart';
import '../../widgets/guide_character/guide_character.dart';
import '../../widgets/orchard_girl.dart';
import '../../../game/models/economy_progress.dart';
import '../../../game/models/game_phase.dart';
import '../../../game/models/level_definition.dart';
import '../../../game/services/score_manager.dart';
import 'gem_match_engine.dart';

const _leaf = GamePalette.forest;
const _brown = GamePalette.wood;
const _creamLight = GamePalette.creamLight;
const _orange = GamePalette.orange;
const _berry = GamePalette.strawberry;
const _chocolate = GamePalette.chocolate;

class GemMatchGame extends StatefulWidget {
  const GemMatchGame({
    required this.level,
    this.onCompleted,
    this.onRequestExtraMoves,
    this.boosterInventory,
    this.onUseBooster,
    this.randomSeed,
    this.soundEnabled = true,
    this.hapticsEnabled = true,
    super.key,
  });

  final LevelDefinition level;
  final Future<void> Function(int levelId, int score, int stars)? onCompleted;
  final Future<bool> Function(int levelId)? onRequestExtraMoves;

  /// Snapshot of the local booster satchel. The booster bar is only shown when
  /// this is provided; [onUseBooster] persists the spend in the economy.
  final Map<BoosterType, int>? boosterInventory;
  final Future<bool> Function(BoosterType booster)? onUseBooster;
  /// Optional deterministic board seed for repeatable QA; production omits it.
  final int? randomSeed;
  final bool soundEnabled;
  final bool hapticsEnabled;

  @override
  State<GemMatchGame> createState() => _GemMatchGameState();
}

class _GemMatchGameState extends State<GemMatchGame> with WidgetsBindingObserver {
  static const _swapDuration = Duration(milliseconds: 230);
  static const _clearDuration = Duration(milliseconds: 210);
  static const _dropDuration = Duration(milliseconds: 300);
  static const _maxCascadeCount = 64;
  static const _rewardedExtraMoves = 5;
  static const _boosterExtraMoves = 5;

  late final GemMatchEngine _engine;
  late Map<BoosterType, int> _boosters;
  late Map<FruitKind, int> _collected;
  late Map<BoardObstacleType, int> _obstaclesCollected;
  final ScoreManager _scoreManager = const ScoreManager();
  final OrchardGirlController _orchardGirl = OrchardGirlController();
  bool _nearGoalNotified = false;
  bool _goalsCompleteNotified = false;
  int _score = 0;
  late int _moves;
  int? _selected;
  int? _fruitPointerId;
  int? _fruitPointerStart;
  Offset? _fruitPointerOrigin;
  Set<int> _clearing = <int>{};
  Set<int> _specialBlastCells = <int>{};
  Set<int> _kiteTargetCells = <int>{};
  Set<int> _hintCells = <int>{};
  Set<int> _invalidSwapCells = <int>{};
  Map<int, Offset> _swapEntryOffsets = <int, Offset>{};
  String? _floatingScore;
  int _scoreToastSerial = 0;
  int _invalidSwapPulse = 0;
  GamePhase _phase = GamePhase.idle;
  bool _completionReported = false;
  bool _extraMovesLoading = false;
  bool _extraMovesGranted = false;
  bool _autoPaused = false;
  bool _hammerMode = false;
  bool _boosterBusy = false;
  String _message = 'میوه‌های هم‌شکل را کنار هم جور کن';

  Duration _motion(Duration duration) =>
      MediaQuery.of(context).disableAnimations ? Duration.zero : duration;

  bool get _goalsComplete =>
      widget.level.goalsComplete(_collected, _obstaclesCollected) &&
      _score >= widget.level.targetScore;
  Map<FruitKind, int> get _remainingFruitGoals => {
        for (final entry in widget.level.fruitGoals.entries)
          entry.key: entry.value <= (_collected[entry.key] ?? 0)
              ? 0
              : entry.value - (_collected[entry.key] ?? 0),
      };

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _engine = GemMatchEngine(
      random: widget.randomSeed == null ? null : Random(widget.randomSeed!),
      lockedCells: widget.level.lockedCells,
      obstacles: widget.level.obstacles,
      specialCells: widget.level.specialPieces,
    );
    _moves = widget.level.moveLimit;
    _boosters = <BoosterType, int>{
      for (final entry in (widget.boosterInventory ?? const <BoosterType, int>{}).entries)
        if (entry.value > 0) entry.key: entry.value,
    };
    _collected = {for (final kind in widget.level.fruitGoals.keys) kind: 0};
    _obstaclesCollected = {
      for (final type in widget.level.obstacleGoals.keys) type: 0,
    };
  }

  int _boostersOf(BoosterType type) => _boosters[type] ?? 0;

  /// Spends one booster through the injected economy callback first and only
  /// then decrements the local snapshot. A denied or failed write changes
  /// nothing, so a stale inventory can never hand out a free booster.
  Future<bool> _spendBooster(BoosterType type) async {
    if (_boostersOf(type) < 1) return false;
    final callback = widget.onUseBooster;
    if (callback != null) {
      var spent = false;
      try {
        spent = await callback(type);
      } catch (_) {
        spent = false;
      }
      if (!spent) {
        if (mounted) {
          setState(() => _message = 'استفاده از تقویت‌کننده ثبت نشد؛ دوباره تلاش کن');
        }
        return false;
      }
    }
    if (!mounted) return false;
    setState(() {
      final remaining = _boostersOf(type) - 1;
      if (remaining <= 0) {
        _boosters.remove(type);
      } else {
        _boosters[type] = remaining;
      }
    });
    return true;
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (!mounted) return;
    if ((state == AppLifecycleState.inactive || state == AppLifecycleState.paused) &&
        _phase == GamePhase.idle) {
      setState(() {
        _phase = GamePhase.paused;
        _autoPaused = true;
      });
    } else if (state == AppLifecycleState.resumed && _autoPaused && _phase == GamePhase.paused) {
      setState(() {
        _phase = GamePhase.idle;
        _autoPaused = false;
      });
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _orchardGirl.dispose();
    super.dispose();
  }

  void _feedback(_FeedbackKind kind) {
    if (widget.soundEnabled) unawaited(SystemSound.play(SystemSoundType.click));
    if (!widget.hapticsEnabled) return;
    final feedback = switch (kind) {
      _FeedbackKind.selection => HapticFeedback.selectionClick(),
      _FeedbackKind.light => HapticFeedback.lightImpact(),
      _FeedbackKind.medium => HapticFeedback.mediumImpact(),
      _FeedbackKind.heavy => HapticFeedback.heavyImpact(),
    };
    unawaited(feedback);
  }

  void _handleTap(int index) {
    if (!_phase.acceptsInput || _boosterBusy) return;
    if (_hammerMode) {
      unawaited(_useHammerAt(index));
      return;
    }
    setState(() => _hintCells = <int>{});
    if (_selected == null) {
      setState(() => _selected = index);
      _feedback(_FeedbackKind.selection);
      return;
    }
    if (_selected == index) {
      setState(() => _selected = null);
      return;
    }

    final first = _selected!;
    final rowDiff = (first ~/ GemMatchEngine.columns - index ~/ GemMatchEngine.columns).abs();
    final columnDiff = (first % GemMatchEngine.columns - index % GemMatchEngine.columns).abs();
    if (rowDiff + columnDiff == 1) {
      _trySwap(first, index);
    } else {
      setState(() => _selected = index);
      _feedback(_FeedbackKind.selection);
    }
  }

  void _handleSwipe(int index, Offset dragDelta) {
    if (!_phase.acceptsInput || _boosterBusy || dragDelta.distance < 16) return;
    final row = index ~/ GemMatchEngine.columns;
    final column = index % GemMatchEngine.columns;
    final horizontal = dragDelta.dx.abs() > dragDelta.dy.abs();
    late final int destination;
    if (horizontal) {
      final nextColumn = column + (dragDelta.dx > 0 ? 1 : -1);
      destination = nextColumn < 0 || nextColumn >= GemMatchEngine.columns
          ? -1
          : index + (dragDelta.dx > 0 ? 1 : -1);
    } else {
      final nextRow = row + (dragDelta.dy > 0 ? 1 : -1);
      destination = nextRow < 0 || nextRow >= GemMatchEngine.rows
          ? -1
          : index + (dragDelta.dy > 0 ? GemMatchEngine.columns : -GemMatchEngine.columns);
    }
    if (destination >= 0) _trySwap(index, destination);
  }

  void _onFruitPointerDown(int index, PointerDownEvent event) {
    if (!_phase.acceptsInput || _boosterBusy) return;
    if (!_hammerMode && _engine.isCellBlocked(index)) return;
    _fruitPointerId = event.pointer;
    _fruitPointerStart = index;
    _fruitPointerOrigin = event.position;
  }

  void _onFruitPointerUp(PointerUpEvent event) {
    if (event.pointer != _fruitPointerId) return;
    final start = _fruitPointerStart;
    final origin = _fruitPointerOrigin;
    final hammerMode = _hammerMode;
    _clearFruitPointer();
    if (start == null || origin == null || !_phase.acceptsInput) return;
    final delta = event.position - origin;
    if (hammerMode) {
      _handleTap(start);
    } else if (delta.distance < 16) {
      _handleTap(start);
    } else {
      _handleSwipe(start, delta);
    }
  }

  void _clearFruitPointer() {
    _fruitPointerId = null;
    _fruitPointerStart = null;
    _fruitPointerOrigin = null;
  }

  Future<void> _trySwap(int first, int second) async {
    if (!_phase.acceptsInput || _boosterBusy) return;
    setState(() {
      _phase = GamePhase.swapping;
      _selected = null;
      _hintCells = <int>{};
      _invalidSwapCells = <int>{};
      _swapEntryOffsets = <int, Offset>{};
    });

    // The engine rolls ordinary rejected swaps back before returning. Special
    // piece swaps are valid even when they do not form a standard match.
    final swapResult = _engine.trySwapDetailed(
      first,
      second,
      goalNeeds: _remainingFruitGoals,
    );
    if (!swapResult.accepted) {
      setState(() {
        _message = 'این جابه‌جایی جور نشد؛ دوباره امتحان کن';
        _invalidSwapCells = {first, second};
        _invalidSwapPulse++;
      });
      _feedback(_FeedbackKind.light);
      await Future<void>.delayed(_motion(const Duration(milliseconds: 340)));
      if (!mounted) return;
      setState(() {
        _message = 'میوه‌های هم‌شکل را کنار هم جور کن';
        _invalidSwapCells = <int>{};
        _phase = GamePhase.idle;
      });
      return;
    }

    final rowDelta = (second ~/ GemMatchEngine.columns - first ~/ GemMatchEngine.columns).toDouble();
    final columnDelta = (second % GemMatchEngine.columns - first % GemMatchEngine.columns).toDouble();
    setState(() {
      // Each incoming fruit starts in the neighboring cell it was swapped from.
      _swapEntryOffsets = {
        first: Offset(columnDelta, rowDelta),
        second: Offset(-columnDelta, -rowDelta),
      };
      _moves--;
      _kiteTargetCells = swapResult.targetCells;
      _message = swapResult.specialCombinationName != null
          ? 'ترکیب ${swapResult.specialCombinationName} فعال شد!'
          : swapResult.activatedCells.isNotEmpty
              ? 'قدرت ویژه فعال شد!'
              : swapResult.createdSpecial != null
              ? '${swapResult.createdSpecial!.persianName} ساخته شد!'
              : 'آفرین! میوه‌ها جور شدند';
    });
    _orchardGirl.react(swapResult.activatedCells.isNotEmpty
        ? OrchardGirlEvent.specialActivated
        : swapResult.createdSpecial != null
            ? OrchardGirlEvent.specialCreated
            : OrchardGirlEvent.ordinaryMatch);
    _feedback(_FeedbackKind.medium);
    await Future<void>.delayed(_motion(_swapDuration));
    if (!mounted) return;

    await _resolveCascades(
      firstClear: swapResult.activatedCells.isNotEmpty
          ? swapResult.activatedCells
          : swapResult.matchedCells,
      firstMatchCells: swapResult.matchedCells,
      firstClearExpanded: swapResult.activatedCells.isNotEmpty,
    );
    if (!mounted) return;

    if (_goalsComplete) {
      await _finish(won: true);
    } else if (_moves <= 0) {
      await _finish(won: false);
    } else {
      final shuffled = _engine.shuffleIfStuck();
      setState(() {
        _phase = GamePhase.idle;
        if (shuffled) _message = 'حرکتی نمانده بود؛ صفحه را دوباره چیدیم';
      });
    }
  }

  Future<void> _resolveCascades({
    Set<int>? firstClear,
    Set<int>? firstMatchCells,
    bool firstClearExpanded = false,
  }) async {
    var cascadeIndex = 0;
    while (mounted && cascadeIndex < _maxCascadeCount) {
      setState(() {
        _phase = GamePhase.checking;
        _swapEntryOffsets = <int, Offset>{};
      });
      final matches = firstClear ?? _engine.findMatches();
      final matchCellsForObstacleDamage =
          firstClear == null ? matches : (firstMatchCells ?? matches);
      final hasPreExpandedEffect = firstClearExpanded;
      final affected = hasPreExpandedEffect
          ? Set<int>.of(matches)
          : _engine.clearEffectCells(
              matches,
              goalNeeds: _remainingFruitGoals,
            );
      firstClear = null;
      firstMatchCells = null;
      firstClearExpanded = false;
      if (affected.isEmpty) return;

      final isSpecialWave = hasPreExpandedEffect ||
          affected.any((index) => _engine.pieceAt(index)?.special != null);
      final matchedKinds = affected
          .map((index) => _engine.pieceAt(index)?.kind)
          .whereType<FruitKind>()
          .toList();
      final points = _scoreManager.scoreForMatch(
        matchedPieces: matchedKinds.length,
        cascadeIndex: cascadeIndex,
      );
      setState(() {
        _phase = GamePhase.resolving;
        _clearing = affected;
        _specialBlastCells = isSpecialWave ? affected : <int>{};
        _score += points;
        _floatingScore = '+${_fa(points)}';
        _scoreToastSerial++;
        for (final kind in matchedKinds) {
          if (!widget.level.fruitGoals.containsKey(kind)) continue;
          final current = _collected[kind] ?? 0;
          final goal = widget.level.fruitGoals[kind]!;
          if (current < goal) _collected[kind] = current + 1;
        }
        _message = cascadeIndex == 0
            ? '+${_fa(points)} امتیاز!'
            : 'زنجیرهٔ میوه‌ای! +${_fa(points)}';
      });
      if (cascadeIndex > 0) {
        _orchardGirl.react(OrchardGirlEvent.cascade);
      }
      final hasNearGoal = widget.level.fruitGoals.entries.any((entry) {
        final remaining = entry.value - (_collected[entry.key] ?? 0);
        return remaining > 0 && remaining <= 2;
      });
      if (hasNearGoal && !_nearGoalNotified) {
        _nearGoalNotified = true;
        _orchardGirl.react(OrchardGirlEvent.nearGoal);
      }
      if (widget.level.goalsComplete(_collected, _obstaclesCollected) &&
          !_goalsCompleteNotified) {
        _goalsCompleteNotified = true;
        if (!_goalsComplete) _orchardGirl.react(OrchardGirlEvent.goalsComplete);
      }
      final toastSerial = _scoreToastSerial;
      unawaited(Future<void>.delayed(_motion(const Duration(milliseconds: 760))).then((_) {
        if (!mounted || toastSerial != _scoreToastSerial) return;
        setState(() => _floatingScore = null);
      }));
      _feedback(_FeedbackKind.selection);
      await Future<void>.delayed(_motion(_clearDuration));
      if (!mounted) return;

      setState(() => _phase = GamePhase.dropping);
      _engine.clearAndDrop(
        affected,
        expanded: true,
        adjacentMatchCells: matchCellsForObstacleDamage,
      );
      _collectClearedObstacles();
      setState(() {
        _clearing = <int>{};
        _specialBlastCells = <int>{};
        _kiteTargetCells = <int>{};
        _phase = GamePhase.refilling;
      });
      await Future<void>.delayed(_motion(_dropDuration));
      if (!mounted) return;
      cascadeIndex++;
    }

    // Defensive recovery for a pathological random cascade; never leave a
    // half-resolved board or an input lock behind.
    if (mounted && _engine.findMatches().isNotEmpty) {
      _engine.reset();
      setState(() {
        _clearing = <int>{};
        _specialBlastCells = <int>{};
        _message = 'صفحه برای ادامهٔ بازی دوباره چیده شد';
      });
    }
  }

  /// Copies the engine's fully cleared obstacles into the level goals once per
  /// wave. Shared by matches, specials and the hammer booster.
  void _collectClearedObstacles() {
    final clearedObstacles = _engine.lastClearedObstacles;
    if (clearedObstacles.isEmpty) return;
    setState(() {
      for (final entry in clearedObstacles.entries) {
        final goal = widget.level.obstacleGoals[entry.key];
        if (goal == null) continue;
        final current = _obstaclesCollected[entry.key] ?? 0;
        _obstaclesCollected[entry.key] =
            current + entry.value > goal ? goal : current + entry.value;
      }
    });
    if (widget.level.goalsComplete(_collected, _obstaclesCollected) &&
        !_goalsCompleteNotified) {
      _goalsCompleteNotified = true;
      _orchardGirl.react(OrchardGirlEvent.goalsComplete);
    }
  }

  void _toggleHammerMode() {
    if (_boosterBusy) return;
    if (!_hammerMode && !_phase.acceptsInput) return;
    if (!_hammerMode && _boostersOf(BoosterType.hammer) < 1) return;
    setState(() {
      _hammerMode = !_hammerMode;
      _selected = null;
      _hintCells = <int>{};
      _message = _hammerMode
          ? 'چکش آماده است؛ یک خانه را برای شکستن انتخاب کن'
          : 'میوه‌های هم‌شکل را کنار هم جور کن';
    });
    if (_hammerMode) _feedback(_FeedbackKind.selection);
  }

  Future<void> _useHammerAt(int index) async {
    if (!_hammerMode || !_phase.acceptsInput || _boosterBusy) return;
    final piece = _engine.pieceAt(index);
    final obstacle = _engine.obstacleAt(index);
    final lock = _engine.lockAt(index);
    if (piece == null && obstacle == null && lock == null) {
      setState(() => _message = 'این خانه خالی است؛ خانهٔ دیگری را انتخاب کن');
      return;
    }
    setState(() {
      _boosterBusy = true;
      _phase = GamePhase.resolving;
      _hammerMode = false;
      _selected = null;
      _hintCells = <int>{};
      _clearing = <int>{index};
      _message = 'چکش یک خانه را شکست';
    });
    _feedback(_FeedbackKind.medium);
    if (!await _spendBooster(BoosterType.hammer)) {
      if (!mounted) return;
      setState(() {
        _boosterBusy = false;
        _phase = GamePhase.idle;
        _clearing = <int>{};
        _hammerMode = _boostersOf(BoosterType.hammer) > 0;
      });
      return;
    }
    if (!mounted) return;
    await Future<void>.delayed(_motion(_clearDuration));
    if (!mounted) return;
    setState(() => _phase = GamePhase.dropping);
    final affected = _engine.hammerCell(index);
    if (affected.isEmpty) {
      // Defensive: never leave the board locked if the cell changed underneath.
      setState(() {
        _clearing = <int>{};
        _phase = GamePhase.idle;
        _boosterBusy = false;
      });
      return;
    }
    _collectClearedObstacles();
    setState(() {
      _clearing = <int>{};
      _specialBlastCells = <int>{};
      _kiteTargetCells = <int>{};
      _phase = GamePhase.refilling;
    });
    await Future<void>.delayed(_motion(_dropDuration));
    if (!mounted) return;
    await _resolveCascades();
    if (!mounted) return;
    await _finishBoosterTurn();
  }

  Future<void> _useShuffleBooster() async {
    if (!_phase.acceptsInput ||
        _boosterBusy ||
        _boostersOf(BoosterType.shuffle) < 1) {
      return;
    }
    setState(() {
      _boosterBusy = true;
      _selected = null;
      _hintCells = <int>{};
      _hammerMode = false;
    });
    if (!await _spendBooster(BoosterType.shuffle)) {
      if (mounted) setState(() => _boosterBusy = false);
      return;
    }
    if (!mounted) return;
    final shuffled = _engine.shuffleBoard();
    setState(() {
      _boosterBusy = false;
      _message = shuffled
          ? 'صفحه دوباره چیده شد؛ یک حرکت تازه پیدا کن'
          : 'چیدمان تازه‌ای ممکن نبود';
    });
    if (shuffled) _feedback(_FeedbackKind.medium);
  }

  Future<void> _useExtraMovesBooster() async {
    if (!_phase.acceptsInput ||
        _boosterBusy ||
        _boostersOf(BoosterType.extraMoves) < 1) {
      return;
    }
    setState(() => _boosterBusy = true);
    if (!await _spendBooster(BoosterType.extraMoves)) {
      if (mounted) setState(() => _boosterBusy = false);
      return;
    }
    if (!mounted) return;
    setState(() {
      _moves += _boosterExtraMoves;
      _boosterBusy = false;
      _message = '${_fa(_boosterExtraMoves)} حرکت اضافه شد؛ ادامه بده';
    });
    _orchardGirl.react(OrchardGirlEvent.rewardGranted);
    _feedback(_FeedbackKind.medium);
  }

  /// Uses a purchased extra-moves booster from the loss dialog and revives the
  /// round. The dialog is closed by the caller only when this returns true.
  Future<bool> _reviveWithExtraMoves(StateSetter setDialogState) async {
    if (_extraMovesLoading) return false;
    setDialogState(() => _extraMovesLoading = true);
    final spent = await _spendBooster(BoosterType.extraMoves);
    if (!mounted) return false;
    if (!spent) {
      setDialogState(() => _extraMovesLoading = false);
      return false;
    }
    setState(() {
      _moves += _boosterExtraMoves;
      _phase = GamePhase.idle;
      _message = '${_fa(_boosterExtraMoves)} حرکت اضافه شد؛ ادامه بده';
      _extraMovesLoading = false;
    });
    _orchardGirl.react(OrchardGirlEvent.rewardGranted);
    _feedback(_FeedbackKind.medium);
    return true;
  }

  Future<void> _finishBoosterTurn() async {
    if (!mounted) return;
    setState(() => _boosterBusy = false);
    if (_goalsComplete) {
      await _finish(won: true);
      return;
    }
    if (_moves <= 0) {
      await _finish(won: false);
      return;
    }
    final shuffled = _engine.shuffleIfStuck();
    setState(() {
      _phase = GamePhase.idle;
      if (shuffled) _message = 'حرکتی نمانده بود؛ صفحه دوباره چیده شد';
    });
  }

  Future<void> _finish({required bool won}) async {
    final stars = won ? widget.level.starsForScore(_score).clamp(1, 3).toInt() : 0;
    setState(() {
      _phase = won ? GamePhase.won : GamePhase.lost;
      _message = won ? 'چه عالی! هدف‌های مرحله کامل شد' : 'حرکت‌ها تمام شد؛ دوباره تلاش کن';
    });
    _orchardGirl.react(won ? OrchardGirlEvent.levelWon : OrchardGirlEvent.levelLost);
    _feedback(won ? _FeedbackKind.heavy : _FeedbackKind.light);
    if (won && !_completionReported) {
      _completionReported = true;
      try {
        await widget.onCompleted?.call(widget.level.id, _score, stars);
      } catch (_) {
        if (mounted) setState(() => _message = 'مرحله برده شد؛ ذخیرهٔ پیشرفت انجام نشد');
      }
    }
    if (!mounted) return;
    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) => StatefulBuilder(
        builder: (dialogContext, setDialogState) => AlertDialog(
        backgroundColor: GamePalette.cream,
        elevation: 14,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(26),
          side: const BorderSide(color: GamePalette.gold, width: 3),
        ),
        icon: won
            ? SizedBox(
                width: 54,
                height: 54,
                child: Image.asset(
                  'assets/fruit/strawberry.png',
                  fit: BoxFit.contain,
                ),
              )
            : const Icon(Icons.spa_rounded, color: GamePalette.leaf, size: 42),
        title: Text(won ? 'آفرین، باغبان ماهر!' : 'یک بار دیگر امتحان کن', textAlign: TextAlign.center, style: const TextStyle(color: _chocolate, fontWeight: FontWeight.w900, fontSize: 19)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (won)
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(
                  3,
                  (index) => TweenAnimationBuilder<double>(
                    tween: Tween<double>(begin: 0, end: 1),
                    duration: _motion(const Duration(milliseconds: 680)),
                    curve: Interval(index * 0.18, 1, curve: Curves.elasticOut),
                    builder: (context, value, child) => Opacity(
                      opacity: value.clamp(0.0, 1.0).toDouble(),
                      child: Transform.rotate(
                        angle: (1 - value) * -0.32,
                        child: Transform.scale(scale: 0.55 + value * 0.45, child: child),
                      ),
                    ),
                    child: Icon(
                      Icons.star_rounded,
                      size: 38,
                      color: index < stars ? const Color(0xFFFFBE35) : const Color(0xFFD8C9AE),
                    ),
                  ),
                ),
              ),
            const SizedBox(height: 8),
            Text('امتیاز: ${_fa(_score)} از ${_fa(widget.level.targetScore)}', style: const TextStyle(color: _brown, fontWeight: FontWeight.w800)),
            Text(won ? 'مرحلهٔ بعدی در نقشه باز شد.' : 'هدف‌ها کامل نشدند.', style: const TextStyle(color: _brown)),
          ],
        ),
        actionsAlignment: MainAxisAlignment.center,
        actions: [
          TextButton(
            onPressed: _extraMovesLoading
                ? null
                : () {
                    Navigator.of(dialogContext).pop();
                    Navigator.of(context).pop();
                  },
            child: const Text('نقشهٔ مراحل'),
          ),
          if (!won && widget.onRequestExtraMoves != null && !_extraMovesGranted)
            OutlinedButton.icon(
              onPressed: _extraMovesLoading
                  ? null
                  : () async {
                      setDialogState(() => _extraMovesLoading = true);
                      var granted = false;
                      try {
                        granted = await widget.onRequestExtraMoves!(widget.level.id);
                      } catch (_) {
                        granted = false;
                      }
                      if (!mounted || !dialogContext.mounted) return;
                      if (granted) {
                        setState(() {
                          _moves += _rewardedExtraMoves;
                          _phase = GamePhase.idle;
                          _message = '۵ حرکت اضافه شد؛ بازی را ادامه بده';
                          _extraMovesGranted = true;
                          _extraMovesLoading = false;
                        });
                        _orchardGirl.react(OrchardGirlEvent.rewardGranted);
                        Navigator.of(dialogContext).pop();
                      } else {
                        setDialogState(() => _extraMovesLoading = false);
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('تبلیغ آماده نبود یا پاداش تأیید نشد؛ حرکتی اضافه نشد.')),
                        );
                      }
                    },
              icon: _extraMovesLoading
                  ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
                  : const Icon(Icons.ondemand_video_rounded),
              label: Text(_extraMovesLoading
                  ? 'در حال آماده‌سازی…'
                  : 'تماشای تبلیغ و ${_fa(_rewardedExtraMoves)} حرکت اضافه'),
            ),
          if (!won && _boostersOf(BoosterType.extraMoves) > 0)
            OutlinedButton.icon(
              onPressed: _extraMovesLoading
                  ? null
                  : () async {
                      final revived = await _reviveWithExtraMoves(setDialogState);
                      if (!mounted || !dialogContext.mounted) return;
                      if (revived) Navigator.of(dialogContext).pop();
                    },
              icon: _extraMovesLoading
                  ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
                  : const Icon(Icons.add_circle_rounded),
              label: Text(
                'حرکت اضافه از کیف (${_fa(_boostersOf(BoosterType.extraMoves))})',
              ),
            ),
          if (!won)
            FilledButton(
              onPressed: _extraMovesLoading
                  ? null
                  : () {
                      Navigator.of(dialogContext).pop();
                      _restart();
                    },
              child: const Text('تلاش دوباره'),
            ),
        ],
      ),
      ),
    );
  }

  void _showHint() {
    if (!_phase.acceptsInput || _boosterBusy) return;
    if (_hammerMode) setState(() => _hammerMode = false);
    final hint = _engine.findHint();
    if (hint == null) {
      final shuffled = _engine.shuffleIfStuck();
      setState(() {
        _hintCells = <int>{};
        _message = shuffled ? 'صفحه را دوباره چیدیم؛ یک حرکت پیدا کن' : 'حرکت ممکن پیدا نشد';
      });
      return;
    }
    setState(() {
      _selected = null;
      _hintCells = hint.toSet();
      _message = 'این دو میوه را با هم جابه‌جا کن';
    });
    _orchardGirl.react(OrchardGirlEvent.validHint);
    _feedback(_FeedbackKind.selection);
  }

  Future<void> _confirmRestart() async {
    if (!_phase.acceptsInput && !_phase.isTerminal && _phase != GamePhase.paused) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: _creamLight,
        title: const Text('شروع دوباره؟', style: TextStyle(color: _brown, fontWeight: FontWeight.w900)),
        content: const Text('امتیاز و هدف‌های این مرحله از نو آغاز می‌شوند.', style: TextStyle(color: _brown)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('ادامهٔ بازی')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('شروع دوباره')),
        ],
      ),
    );
    if (confirmed == true && mounted) _restart();
  }

  void _restart() {
    setState(() {
      _engine.reset();
      _score = 0;
      _moves = widget.level.moveLimit;
      _collected = {for (final kind in widget.level.fruitGoals.keys) kind: 0};
      _obstaclesCollected = {
        for (final type in widget.level.obstacleGoals.keys) type: 0,
      };
      _selected = null;
      _clearing = <int>{};
      _specialBlastCells = <int>{};
      _kiteTargetCells = <int>{};
      _hintCells = <int>{};
      _invalidSwapCells = <int>{};
      _swapEntryOffsets = <int, Offset>{};
      _floatingScore = null;
      _phase = GamePhase.idle;
      _completionReported = false;
      _nearGoalNotified = false;
      _goalsCompleteNotified = false;
      _hammerMode = false;
      _boosterBusy = false;
      _message = 'میوه‌های هم‌شکل را کنار هم جور کن';
      _orchardGirl.resetToIdle();
    });
    _feedback(_FeedbackKind.medium);
  }

  void _pause() {
    if (!_phase.acceptsInput) return;
    setState(() {
      _phase = GamePhase.paused;
      _autoPaused = false;
      _selected = null;
      _hammerMode = false;
    });
  }

  void _resume() => setState(() {
        _phase = GamePhase.idle;
        _autoPaused = false;
      });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: GamePalette.forestDeep,
      body: Stack(
        fit: StackFit.expand,
        children: <Widget>[
          const _OrchardBackdrop(),
          SafeArea(
            child: LayoutBuilder(
              builder: (context, constraints) => _buildLayout(constraints),
            ),
          ),
          if (_phase == GamePhase.paused) _buildPauseOverlay(),
        ],
      ),
    );
  }

  Widget _buildLayout(BoxConstraints constraints) {
    final screenHeight = MediaQuery.sizeOf(context).height;
    final wanted = screenHeight < 700
        ? 160.0
        : screenHeight < 820
            ? 205.0
            : 235.0;
    final characterHeight = min(wanted, constraints.maxHeight * 0.36);
    return Padding(
      padding: const EdgeInsets.fromLTRB(8, 6, 8, 8),
      child: Column(
        children: <Widget>[
          _buildHeader(),
          const SizedBox(height: 6),
          _buildHud(),
          const SizedBox(height: 8),
          Expanded(
            child: LayoutBuilder(
              builder: (context, boardConstraints) {
                final side = min(
                  min(boardConstraints.maxWidth, 520.0),
                  boardConstraints.maxHeight,
                );
                return Center(child: _buildBoard(side));
              },
            ),
          ),
          const SizedBox(height: 2),
          _buildMessage(),
          const SizedBox(height: 3),
          _buildCharacterZone(characterHeight),
        ],
      ),
    );
  }

  Widget _buildHeader() {
    // Single top row from the reference: title plaque, then the moves/score
    // panel, then the two round buttons (pause, hint) — all inline, left to
    // right. The row is pinned LTR so the Persian RTL ambient direction
    // cannot mirror it back.
    const rowHeight = 64.0;
    return SizedBox(
      height: rowHeight,
      child: Directionality(
        textDirection: TextDirection.ltr,
        child: Row(
          children: <Widget>[
            Expanded(
              flex: 9,
              child: SizedBox(height: rowHeight, child: _buildTitlePlaque()),
            ),
            const SizedBox(width: 6),
            Expanded(
              flex: 11,
              child: SizedBox(height: rowHeight, child: _buildStatsPanel()),
            ),
            const SizedBox(width: 5),
            _headerButton(
              icon: Icons.pause_rounded,
              tooltip: 'مکث',
              onPressed: _phase.acceptsInput ? _pause : null,
            ),
            const SizedBox(width: 4),
            _headerButton(
              icon: Icons.lightbulb_rounded,
              tooltip: 'راهنمایی',
              plate: 'assets/ui/button-gold.png',
              iconColor: GamePalette.chocolate,
              onPressed: _phase.acceptsInput ? _showHint : null,
            ),
          ],
        ),
      ),
    );
  }

  /// Wooden moves/score panel, now living inline in the header row instead of
  /// its own banner underneath.
  Widget _buildStatsPanel() {
    return OrchardPanel(
      asset: 'assets/ui/hud-panel.png',
      padding: const EdgeInsets.fromLTRB(10, 6, 10, 6),
      child: Row(
        children: <Widget>[
          Expanded(
            child: _statBlock(
              'حرکت باقی‌مانده',
              _fa(_moves),
              GamePalette.sky,
              key: const ValueKey<String>('stat-moves-value'),
            ),
          ),
          Expanded(child: _buildScoreStat()),
        ],
      ),
    );
  }

  /// Wooden title plaque from the reference layout. The finished plaque
  /// artwork already carries the strawberry medallion on its left side, so
  /// the title and level line are overlaid on the empty wooden field.
  Widget _buildTitlePlaque() {
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth;
        return Stack(
          fit: StackFit.expand,
          children: <Widget>[
            Image.asset(
              'assets/ui/title-plaque.png',
              fit: BoxFit.fill,
              filterQuality: FilterQuality.medium,
            ),
            Positioned(
              left: width * 0.24,
              right: 16,
              top: 0,
              bottom: 0,
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.end,
                children: <Widget>[
                  const FittedBox(
                    fit: BoxFit.scaleDown,
                    alignment: Alignment.centerRight,
                    child: Text(
                      'میوه‌چین',
                      textDirection: TextDirection.rtl,
                      style: TextStyle(
                        color: Color(0xFFFFF6E2),
                        fontSize: 18,
                        fontWeight: FontWeight.w900,
                        height: 1.05,
                        shadows: <Shadow>[
                          Shadow(color: Color(0xAA3A1C08), blurRadius: 5, offset: Offset(0, 2)),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 1),
                  Directionality(
                    textDirection: TextDirection.rtl,
                    child: FittedBox(
                      fit: BoxFit.scaleDown,
                      alignment: Alignment.centerRight,
                      child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: <Widget>[
                        Text(
                          'مرحله ${_fa(widget.level.id)}',
                          maxLines: 1,
                          style: TextStyle(
                            color: GamePalette.cream.withValues(alpha: 0.98),
                            fontSize: 9.5,
                            fontWeight: FontWeight.w900,
                            shadows: const <Shadow>[
                              Shadow(color: Color(0xAA2C1608), blurRadius: 4),
                            ],
                          ),
                        ),
                        if (width >= 170)
                          ConstrainedBox(
                            constraints: const BoxConstraints(maxWidth: 78),
                            child: Text(
                              ' • ${widget.level.chapterTitle}',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: GamePalette.cream.withValues(alpha: 0.88),
                              fontSize: 9.5,
                              fontWeight: FontWeight.w700,
                              shadows: const <Shadow>[
                                Shadow(color: Color(0xAA2C1608), blurRadius: 4),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        );
      },
    );
  }

  /// Compact, plate-backed header control.
  ///
  /// The tappable surface stays a real [IconButton] with the 34 px footprint
  /// its tests and accessibility contract expect; only the artwork behind it
  /// is new.
  Widget _headerButton({
    required IconData icon,
    required String tooltip,
    required VoidCallback? onPressed,
    String plate = 'assets/ui/button-wood.png',
    Color iconColor = const Color(0xFFFFF3D8),
  }) {
    final enabled = onPressed != null;
    return SizedBox(
      width: 38,
      height: 38,
      child: Stack(
        alignment: Alignment.center,
        children: <Widget>[
          Positioned.fill(
            child: Opacity(
              opacity: enabled ? 1 : 0.55,
              child: Image.asset(plate, fit: BoxFit.contain),
            ),
          ),
          Opacity(
            opacity: enabled ? 1 : 0.55,
            child: IconButton(
              tooltip: tooltip,
              onPressed: onPressed,
              icon: Icon(icon, size: 18, color: iconColor),
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints.tightFor(width: 34, height: 34),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHud() {
    final collected = widget.level.fruitGoals.entries.fold<int>(
          0,
          (sum, entry) => sum + min(_collected[entry.key] ?? 0, entry.value),
        ) +
        widget.level.obstacleGoals.entries.fold<int>(
          0,
          (sum, entry) =>
              sum + min(_obstaclesCollected[entry.key] ?? 0, entry.value),
        );
    final target = widget.level.fruitGoals.values.fold<int>(0, (sum, value) => sum + value) +
        widget.level.obstacleGoals.values.fold<int>(0, (sum, value) => sum + value);
    final progress = target == 0 ? 0.0 : (collected / target).clamp(0.0, 1.0).toDouble();
    final goalCount = widget.level.fruitGoals.length + widget.level.obstacleGoals.length;
    // Cream goal banner: "هدف مرحله", the plain icon + "X از Y" goal readout
    // and the progress bar underneath, exactly as in the reference. A level
    // with several goals gets a taller banner so the readouts can wrap.
    return SizedBox(
      height: goalCount > 3 ? 182 : goalCount > 1 ? 116 : 92,
      child: OrchardPanel(
        asset: 'assets/ui/goal-banner.png',
        padding: const EdgeInsets.fromLTRB(26, 12, 26, 12),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Row(
              children: <Widget>[
                const Text(
                  'هدف مرحله',
                  style: TextStyle(
                    color: _chocolate,
                    fontSize: 14,
                    fontWeight: FontWeight.w900,
                    height: 1.1,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Wrap(
                    alignment: WrapAlignment.center,
                    spacing: 16,
                    runSpacing: 4,
                    children: <Widget>[
                      for (final entry in widget.level.fruitGoals.entries)
                        _goalChip(entry.key, entry.value),
                      for (final entry in widget.level.obstacleGoals.entries)
                        _obstacleGoalChip(entry.key, entry.value),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            OrchardProgressBar(value: progress, height: 18),
          ],
        ),
      ),
    );
  }

  /// Score cell of the wooden panel: "score / target" with a gold star, laid
  /// out like the reference. The target keeps its own keyed [Text] so tests
  /// can still read the raw target value.
  Widget _buildScoreStat() {
    return Column(
      key: const ValueKey<String>('stat-score-value'),
      mainAxisSize: MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: <Widget>[
        FittedBox(
          fit: BoxFit.scaleDown,
          child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Text(
              _fa(_score),
              maxLines: 1,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 16,
                height: 1.05,
                fontWeight: FontWeight.w900,
                shadows: <Shadow>[
                  Shadow(color: Color(0xAA3A1C08), blurRadius: 4, offset: Offset(0, 1)),
                ],
              ),
            ),
            const Text(
              ' / ',
              style: TextStyle(
                color: Color(0xFFFFE3B3),
                fontSize: 11,
                fontWeight: FontWeight.w900,
              ),
            ),
            Container(
              key: const ValueKey<String>('stat-target-value'),
              child: Text(
                _fa(widget.level.targetScore),
                maxLines: 1,
                style: const TextStyle(
                  color: Color(0xFFFFE3B3),
                  fontSize: 13,
                  height: 1.05,
                  fontWeight: FontWeight.w900,
                ),
              ),
            ),
            const SizedBox(width: 4),
            const Icon(Icons.star_rounded, size: 14, color: GamePalette.gold),
          ],
          ),
        ),
        const SizedBox(height: 1),
        const Text('امتیاز', style: TextStyle(color: Color(0xFFFFE3B3), fontSize: 8.5, fontWeight: FontWeight.w800, height: 1.1)),
        const SizedBox(height: 2),
        Container(
          width: 18,
          height: 2,
          decoration: BoxDecoration(
            color: GamePalette.orange,
            borderRadius: BorderRadius.circular(2),
          ),
        ),
      ],
    );
  }

  Widget _statBlock(
    String label,
    String value,
    Color accent, {
    Key? key,
  }) =>
      Column(
        key: key,
        mainAxisSize: MainAxisSize.min,
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          FittedBox(
            fit: BoxFit.scaleDown,
            child: Text(
              value,
              maxLines: 1,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 16,
                height: 1.05,
                fontWeight: FontWeight.w900,
                shadows: <Shadow>[
                  Shadow(color: Color(0xAA3A1C08), blurRadius: 4, offset: Offset(0, 1)),
                ],
              ),
            ),
          ),
          const SizedBox(height: 1),
          FittedBox(
            fit: BoxFit.scaleDown,
            child: Text(label, style: const TextStyle(color: Color(0xFFFFE3B3), fontSize: 8.5, fontWeight: FontWeight.w800, height: 1.1)),
          ),
          const SizedBox(height: 2),
          Container(
            width: 18,
            height: 2,
            decoration: BoxDecoration(
              color: accent,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
        ],
      );

  Widget _buildCharacterZone(double height) {
    final caption = _orchardGirl.caption;
    final characterWidth = height * GuideCharacter.aspect;
    final trayHeight = (height * 0.30).clamp(42.0, 58.0).toDouble();
    final boosterHeight = (height * 0.26).clamp(38.0, 50.0).toDouble();
    final dockHeight = trayHeight + boosterHeight + 17;
    return SizedBox(
      height: height,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = constraints.maxWidth;
          final controlsWidth = max(0.0, width - characterWidth - 6);
          return Stack(
            clipBehavior: Clip.none,
            children: <Widget>[
              // One unified wooden dock: the four-slot power tray on top and
              // the three inventory boosters underneath it.
              Positioned(
                right: 0,
                bottom: 0,
                width: controlsWidth,
                child: _buildControlDock(trayHeight, boosterHeight),
              ),
              // The guide is drawn last, in front of everything in her zone,
              // standing on the bottom edge so her legs and shoes are fully
              // out and visible — never tucked behind the dock or the board.
              Positioned(
                left: 0,
                bottom: 0,
                width: characterWidth,
                height: height,
                child: GuideCharacter(
                  controller: _orchardGirl,
                  height: height,
                  paused: _phase == GamePhase.paused,
                ),
              ),
              // Her speech bubble floats beside her face, clear of both the
              // board above and the character herself.
              if (caption != null)
                Positioned(
                  left: characterWidth + 4,
                  right: 2,
                  top: 0,
                  height: max(38.0, height - dockHeight - 4),
                  child: Align(
                    alignment: Alignment.bottomCenter,
                    child: OrchardBubble(
                      key: ValueKey<String>(caption),
                      message: caption,
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }

  /// The single bottom-right control dock from the reference layout.
  Widget _buildControlDock(double trayHeight, double boosterHeight) {
    return Container(
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: <Color>[Color(0xFF8A5427), Color(0xFF673812)],
        ),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: GamePalette.gold.withValues(alpha: 0.75),
          width: 1.6,
        ),
        boxShadow: GamePalette.panelShadow,
      ),
      padding: const EdgeInsets.all(6),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          _buildPowerTray(trayHeight),
          const SizedBox(height: 5),
          _buildBoosterBar(boosterHeight),
        ],
      ),
    );
  }

  Widget _buildPowerTray(double height) {
    const powers = SpecialPower.values;
    final counts = <SpecialPower, int>{
      for (final power in powers) power: _countPowerOnBoard(power),
    };
    // One wooden plank carrying the four square power cards, exactly like
    // the reference toolbar: the delivered booster art is already a finished
    // square card, so it is shown full-bleed with a gold rim and a count
    // badge instead of being shrunk into a round slot.
    return Container(
      key: const ValueKey<String>('power-tray'),
      height: height,
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: <Color>[Color(0xFF96602C), Color(0xFF71431B)],
        ),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: GamePalette.gold.withValues(alpha: 0.8),
          width: 1.6,
        ),
        boxShadow: GamePalette.chipShadow,
      ),
      padding: const EdgeInsets.all(5),
      child: Row(
        textDirection: TextDirection.ltr,
        children: <Widget>[
          for (var i = 0; i < powers.length; i++) ...<Widget>[
            if (i > 0) const SizedBox(width: 5),
            Expanded(
              child: _powerButton(powers[i], counts[powers[i]] ?? 0),
            ),
          ],
        ],
      ),
    );
  }

  int _countPowerOnBoard(SpecialPower power) {
    var count = 0;
    for (var i = 0; i < GemMatchEngine.cellCount; i++) {
      if (_engine.pieceAt(i)?.special == power) count++;
    }
    return count;
  }

  Widget _powerButton(SpecialPower power, int count) {
    final ready = count > 0;
    return Semantics(
      label: '${power.persianName}، ${_fa(count)} روی صفحه',
      image: true,
      child: Opacity(
        opacity: ready ? 1 : 0.72,
        child: Stack(
          fit: StackFit.expand,
          children: <Widget>[
            ClipRRect(
              borderRadius: BorderRadius.circular(11),
              child: Image.asset(power.artAsset, fit: BoxFit.cover),
            ),
            Container(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(11),
                border: Border.all(
                  color: ready
                      ? GamePalette.gold
                      : GamePalette.gold.withValues(alpha: 0.45),
                  width: ready ? 2 : 1.2,
                ),
              ),
            ),
            if (ready)
              Positioned(
                right: 2,
                bottom: 2,
                child: OrchardBadge(
                  label: _fa(count),
                  color: GamePalette.leaf,
                  textColor: Colors.white,
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildBoosterBar(double height) {
    final canAct = _phase.acceptsInput && !_boosterBusy;
    return SizedBox(
      height: height,
      child: Row(
        textDirection: TextDirection.ltr,
        children: <Widget>[
          Expanded(
            child: _boosterChip(
              type: BoosterType.hammer,
              icon: Icons.hardware_rounded,
              label: 'چکش',
              active: _hammerMode,
              onTap: canAct ? _toggleHammerMode : null,
            ),
          ),
          const SizedBox(width: 5),
          Expanded(
            child: _boosterChip(
              type: BoosterType.shuffle,
              icon: Icons.shuffle_rounded,
              label: 'چیدمان دوباره',
              onTap: canAct ? () => unawaited(_useShuffleBooster()) : null,
            ),
          ),
          const SizedBox(width: 5),
          Expanded(
            child: _boosterChip(
              type: BoosterType.extraMoves,
              icon: Icons.add_rounded,
              label: 'حرکت اضافه',
              onTap: canAct ? () => unawaited(_useExtraMovesBooster()) : null,
            ),
          ),
        ],
      ),
    );
  }

  Widget _boosterChip({
    required BoosterType type,
    required IconData icon,
    required String label,
    required VoidCallback? onTap,
    bool active = false,
  }) {
    final count = _boostersOf(type);
    final enabled = onTap != null && count > 0;
    return Semantics(
      key: ValueKey<String>('booster-${type.name}'),
      button: true,
      enabled: enabled,
      label: '$label، ${_fa(count)} عدد${active ? '، فعال' : ''}',
      onTap: enabled ? onTap : null,
      excludeSemantics: true,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: enabled ? onTap : null,
          child: Stack(
            children: <Widget>[
              Positioned.fill(
                child: Container(
                  decoration: BoxDecoration(
                    gradient: active
                        ? GamePalette.goldGradient
                        : const LinearGradient(
                            begin: Alignment.topCenter,
                            end: Alignment.bottomCenter,
                            colors: <Color>[Color(0xFF96602C), Color(0xFF71431B)],
                          ),
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(
                      color: active
                          ? const Color(0xFFFFF3D0)
                          : GamePalette.gold.withValues(alpha: 0.7),
                      width: 1.5,
                    ),
                    boxShadow: GamePalette.chipShadow,
                  ),
                ),
              ),
              Positioned.fill(
                child: Padding(
                  padding: const EdgeInsets.all(9),
                  child: FittedBox(
                    fit: BoxFit.contain,
                    child: Icon(
                      icon,
                      color: enabled || active
                          ? (active ? GamePalette.chocolate : const Color(0xFFFFF1D2))
                          : const Color(0x99E8D6B8),
                    ),
                  ),
                ),
              ),
              Positioned(
                right: 0,
                top: 0,
                child: OrchardBadge(
                  label: _fa(count),
                  color: enabled || active
                      ? (active ? GamePalette.orange : GamePalette.leaf)
                      : GamePalette.gold,
                  textColor: enabled || active ? Colors.white : _chocolate,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// Plain icon + "current از goal" readout, no chip box — matches the
  /// reference banner, which prints the goal straight on the parchment.
  Widget _obstacleGoalChip(BoardObstacleType type, int goal) {
    final current = _obstaclesCollected[type] ?? 0;
    final complete = current >= goal;
    final icon = switch (type) {
      BoardObstacleType.woodBox => Icons.inventory_2_rounded,
      BoardObstacleType.ice => Icons.ac_unit_rounded,
      BoardObstacleType.vine => Icons.park_rounded,
      BoardObstacleType.soil => Icons.grass_rounded,
    };
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        Icon(icon, size: 22, color: _leaf),
        const SizedBox(width: 6),
        Text(
          '${type.persianName} ${_fa(current)} از ${_fa(goal)}',
          style: TextStyle(
            color: complete ? _leaf : _chocolate,
            fontSize: 13,
            fontWeight: FontWeight.w900,
          ),
        ),
      ],
    );
  }

  /// Plain icon + "current از goal" readout, no chip box — matches the
  /// reference banner (single orange icon with "۹ از ۹" beside it).
  Widget _goalChip(FruitKind kind, int goal) {
    final current = _collected[kind] ?? 0;
    final complete = current >= goal;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        AnimatedScale(
          scale: complete ? 1.15 : 1,
          duration: _motion(const Duration(milliseconds: 260)),
          curve: Curves.easeOutBack,
          child: Image.asset(kind.assetPath, width: 27, height: 27, fit: BoxFit.contain),
        ),
        const SizedBox(width: 7),
        Flexible(
          child: AnimatedSwitcher(
          duration: _motion(const Duration(milliseconds: 180)),
          transitionBuilder: (child, animation) => FadeTransition(
            opacity: animation,
            child: ScaleTransition(scale: animation, child: child),
          ),
          child: Text(
            '${_fa(current)} از ${_fa(goal)}',
            key: ValueKey<int>(current),
            maxLines: 1,
            style: TextStyle(
              color: complete ? _leaf : _chocolate,
              fontSize: 13.5,
              fontWeight: FontWeight.w900,
            ),
          ),
          ),
        ),
      ],
    );
  }

  Widget _buildBoard(double size) {
    // Slim wooden frame instead of the chunky board-frame art: the old frame
    // ate ~21% of the board on every side and shrank the fruit. A thin wood
    // rim with a gold edge keeps the orchard look while the 8x8 grid takes
    // ~94% of the (already full-width) board, so fruit render ~20% larger.
    final frame = size * 0.030;
    final inner = size - frame * 2;
    final gap = size * 0.008;
    return Container(
      key: const ValueKey<String>('game-board-frame'),
      width: size,
      height: size,
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: <Color>[Color(0xFF9A6330), Color(0xFF6E3E17)],
        ),
        borderRadius: BorderRadius.circular(size * 0.045),
        border: Border.all(color: GamePalette.gold, width: 2),
        boxShadow: GamePalette.panelShadow,
      ),
      padding: EdgeInsets.all(frame),
      child: Stack(
        fit: StackFit.expand,
        children: <Widget>[
          SizedBox(
              width: inner,
              height: inner,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(size * 0.028),
                child: Stack(
                  fit: StackFit.expand,
                  children: <Widget>[
                    const ColoredBox(color: Color(0xFFF3E2BF)),
                    Directionality(
                      textDirection: TextDirection.ltr,
                      child: GridView.builder(
                        itemCount: GemMatchEngine.cellCount,
                        physics: const NeverScrollableScrollPhysics(),
                        padding: EdgeInsets.all(gap),
                        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: GemMatchEngine.columns,
                          crossAxisSpacing: gap,
                          mainAxisSpacing: gap,
                        ),
                        itemBuilder: (context, index) => _buildFruitCell(index),
                      ),
                    ),
                    if (_floatingScore case final score?)
                      Positioned(
                        top: size * 0.02,
                        left: 0,
                        right: 0,
                        child: IgnorePointer(
                          child: TweenAnimationBuilder<double>(
                            key: ValueKey<int>(_scoreToastSerial),
                            tween: Tween<double>(begin: 0, end: 1),
                            duration: _motion(const Duration(milliseconds: 760)),
                            curve: Curves.easeOutCubic,
                            builder: (context, progress, child) => Opacity(
                              opacity: (1 - progress).clamp(0.0, 1.0).toDouble(),
                              child: Transform.translate(
                                offset: Offset(0, -38 * progress),
                                child: Transform.scale(
                                  scale: 0.88 + progress * 0.18,
                                  child: child,
                                ),
                              ),
                            ),
                            child: Center(
                              child: Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 13,
                                  vertical: 6,
                                ),
                                decoration: BoxDecoration(
                                  color: GamePalette.creamLight.withValues(alpha: 0.96),
                                  borderRadius: BorderRadius.circular(18),
                                  border: Border.all(color: GamePalette.gold, width: 1.5),
                                  boxShadow: GamePalette.chipShadow,
                                ),
                                child: Text(
                                  '$score امتیاز',
                                  style: const TextStyle(
                                    color: _chocolate,
                                    fontSize: 15,
                                    fontWeight: FontWeight.w900,
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }

  /// Cell tint per interaction state. The parchment tile art shows through
  /// when the cell is at rest, so the board reads as one woven surface.
  List<Color> _cellColors({
    required bool selected,
    required bool hinted,
    required bool specialBlast,
    required bool kiteTarget,
    required bool hammerReady,
  }) {
    if (specialBlast) {
      return const <Color>[Color(0xFFFFF5B8), Color(0xFFFF8B42)];
    }
    if (kiteTarget) {
      return const <Color>[Color(0xFFE6F9FF), Color(0xFFFFE7A5)];
    }
    if (selected) {
      return <Color>[
        GamePalette.gold.withValues(alpha: 0.85),
        GamePalette.orange.withValues(alpha: 0.85),
      ];
    }
    if (hinted) {
      return <Color>[
        const Color(0xFFE3F6C4).withValues(alpha: 0.85),
        GamePalette.leaf.withValues(alpha: 0.60),
      ];
    }
    if (hammerReady) {
      return const <Color>[Color(0x22FF942E), Color(0x22FF942E)];
    }
    return const <Color>[Color(0x00FFFFFF), Color(0x00FFFFFF)];
  }

  Widget _buildFruitCell(int index) {
    final piece = _engine.pieceAt(index);
    final selected = _selected == index;
    final hinted = _hintCells.contains(index);
    final clearing = _clearing.contains(index);
    final specialBlast = _specialBlastCells.contains(index);
    final kiteTarget = _kiteTargetCells.contains(index);
    final rejected = _invalidSwapCells.contains(index);
    final lock = _engine.lockAt(index);
    final obstacle = _engine.obstacleAt(index);
    final blocked = _engine.isCellBlocked(index);
    final hammerReady = _hammerMode && _phase.acceptsInput;
    final cellDetails = <String>[
      if (piece != null) piece.kind.persianName,
      if (piece?.special case final power?) power.persianName,
      if (piece?.special == SpecialPower.lineClear)
        piece!.verticalLine ? 'پاک‌کردن ستون' : 'پاک‌کردن ردیف',
      if (obstacle case final boardObstacle?)
        '${boardObstacle.type.persianName}، ${_fa(boardObstacle.layers)} لایه',
      if (lock case final tileLock?) 'قفل ${_fa(tileLock.layers)} لایه',
    ].join('، ');
    final reduceMotion = MediaQuery.of(context).disableAnimations;
    final duration = Duration(milliseconds: reduceMotion ? 0 : 180);
    final switchDuration = Duration(
      milliseconds: reduceMotion
          ? 0
          : _phase == GamePhase.refilling
              ? _dropDuration.inMilliseconds
              : _phase == GamePhase.swapping
                  ? _swapDuration.inMilliseconds
                  : _clearDuration.inMilliseconds,
    );
    final swapOffset = _swapEntryOffsets[index];
    final entryOffset = swapOffset ??
        (_phase == GamePhase.refilling ? const Offset(0, -1.15) : const Offset(0, -0.12));

    return Semantics(
      key: ValueKey<String>('fruit-cell-$index'),
      label: hinted
          ? 'حرکت پیشنهادی'
          : 'خانهٔ ${_fa(index + 1)}'
              '${cellDetails.isEmpty ? '' : '، $cellDetails'}',
      button: _phase.acceptsInput && (hammerReady || !blocked),
      enabled: hammerReady || !blocked,
      onTap: _phase.acceptsInput && (hammerReady || !blocked)
          ? () => _handleTap(index)
          : null,
      child: RawGestureDetector(
        gestures: {
          EagerGestureRecognizer:
              GestureRecognizerFactoryWithHandlers<EagerGestureRecognizer>(
            () => EagerGestureRecognizer(),
            (instance) {},
          ),
        },
        child: Listener(
          behavior: HitTestBehavior.opaque,
          onPointerDown: (event) => _onFruitPointerDown(index, event),
          onPointerUp: _onFruitPointerUp,
          onPointerCancel: (_) => _clearFruitPointer(),
          child: AnimatedContainer(
          duration: duration,
          curve: Curves.easeOut,
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: _cellColors(
                selected: selected,
                hinted: hinted,
                specialBlast: specialBlast,
                kiteTarget: kiteTarget,
                hammerReady: hammerReady,
              ),
            ),
            image: selected || hinted || specialBlast || kiteTarget
                ? null
                : DecorationImage(
                    image: AssetImage(
                      index.isEven
                          ? 'assets/ui/tile-light.png'
                          : 'assets/ui/tile-warm.png',
                    ),
                    fit: BoxFit.fill,
                    opacity: 0.94,
                  ),
            borderRadius: BorderRadius.circular(9),
            border: Border.all(
              color: specialBlast
                  ? Colors.white
                  : kiteTarget
                      ? GamePalette.gold
                      : selected
                          ? GamePalette.orange
                          : hinted
                              ? GamePalette.leaf
                              : hammerReady
                                  ? GamePalette.orange.withValues(alpha: 0.55)
                                  : Colors.white.withValues(alpha: 0.35),
              width: selected || hinted || specialBlast || kiteTarget
                  ? 2
                  : hammerReady
                      ? 1.4
                      : 0.6,
            ),
            boxShadow: <BoxShadow>[
              BoxShadow(
                color: GamePalette.woodShadow.withValues(
                  alpha: selected || hinted || specialBlast ? 0.30 : 0.14,
                ),
                blurRadius: selected || hinted || specialBlast ? 6 : 2,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: TweenAnimationBuilder<double>(
            key: ValueKey<String>(
              piece == null || clearing ? 'empty-$index' : 'piece-${piece.id}',
            ),
            tween: Tween<double>(begin: 0, end: 1),
            duration: switchDuration,
            curve: Curves.easeOutCubic,
            builder: (context, value, child) => FractionalTranslation(
              translation: Offset(0, entryOffset.dy * (1 - value)),
              child: child,
            ),
            child: piece == null || clearing
                ? Stack(
                    key: ValueKey<String>('empty-$index'),
                    fit: StackFit.expand,
                    children: <Widget>[
                      const SizedBox.expand(),
                      if (lock case final tileLock?)
                        Positioned(
                          bottom: 0,
                          left: 0,
                          child: _lockBadge(tileLock),
                        ),
                      if (obstacle case final boardObstacle?)
                        Positioned(
                          bottom: 0,
                          right: 0,
                          child: _obstacleBadge(boardObstacle),
                        ),
                    ],
                  )
                : AnimatedScale(
                    key: ValueKey<int>(piece.id),
                    scale: selected ? 1.10 : hinted ? 1.04 : 1,
                    duration: duration,
                    curve: Curves.easeOut,
                    child: Padding(
                      padding: const EdgeInsets.all(2),
                      child: Stack(
                        children: <Widget>[
                          Positioned.fill(
                            child: _buildPieceImage(piece, rejected, reduceMotion),
                          ),
                          if (kiteTarget)
                            Positioned.fill(
                              child: IgnorePointer(
                                child: TweenAnimationBuilder<double>(
                                  key: ValueKey<String>('kite-flight-$index'),
                                  tween: Tween<double>(begin: 0, end: 1),
                                  duration: _motion(const Duration(milliseconds: 420)),
                                  curve: Curves.easeOutCubic,
                                  builder: (context, progress, child) => Opacity(
                                    opacity: (1 - progress * 0.25)
                                        .clamp(0.0, 1.0)
                                        .toDouble(),
                                    child: Transform.translate(
                                      offset: Offset(14 * (1 - progress), -14 * (1 - progress)),
                                      child: Transform.scale(
                                        scale: 0.72 + progress * 0.38,
                                        child: child,
                                      ),
                                    ),
                                  ),
                                  child: const Align(
                                    alignment: Alignment.topCenter,
                                    child: Icon(
                                      Icons.emoji_nature_rounded,
                                      color: Color(0xFFFF7F50),
                                      size: 20,
                                      shadows: [Shadow(color: Colors.white, blurRadius: 4)],
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          if (piece.special case final power?)
                            Positioned(
                              top: 0,
                              right: 0,
                              child: _specialBadge(
                                power,
                                verticalLine: piece.verticalLine,
                              ),
                            ),
                          if (lock case final tileLock?)
                            Positioned(
                              bottom: 0,
                              left: 0,
                              child: _lockBadge(tileLock),
                            ),
                          if (obstacle case final boardObstacle?)
                            Positioned(
                              bottom: 0,
                              right: 0,
                              child: _obstacleBadge(boardObstacle),
                            ),
                        ],
                      ),
                    ),
                  ),
          ),
        ),
      ),
      ),
    );
  }

  Widget _buildPieceImage(GemPiece piece, bool rejected, bool reduceMotion) {
    final image = Image.asset(
      piece.kind.assetPath,
      fit: BoxFit.contain,
      filterQuality: FilterQuality.medium,
    );
    if (!rejected || reduceMotion) return image;
    return TweenAnimationBuilder<double>(
      key: ValueKey<String>('shake-${piece.id}-$_invalidSwapPulse'),
      tween: Tween<double>(begin: 0, end: 1),
      duration: const Duration(milliseconds: 340),
      builder: (context, progress, child) => Transform.translate(
        offset: Offset(sin(progress * pi * 6) * 4 * (1 - progress), 0),
        child: child,
      ),
      child: image,
    );
  }

  Widget _specialBadge(
    SpecialPower power, {
    bool verticalLine = false,
  }) {
    final (icon, color) = switch (power) {
      SpecialPower.areaBomb => (Icons.brightness_7_rounded, _orange),
      SpecialPower.lineClear => (
          verticalLine ? Icons.vertical_align_center_rounded : Icons.horizontal_rule_rounded,
          const Color(0xFF438ABD),
        ),
      SpecialPower.colorBomb => (Icons.album_rounded, _berry),
      SpecialPower.rainbowKite => (Icons.emoji_nature_rounded, const Color(0xFF9B66D8)),
    };
    return Container(
      width: 17,
      height: 17,
      decoration: BoxDecoration(
        color: color,
        shape: BoxShape.circle,
        border: Border.all(color: Colors.white, width: 1),
        boxShadow: const [BoxShadow(color: Color(0x553B2A18), blurRadius: 2)],
      ),
      child: Icon(icon, color: Colors.white, size: 10),
    );
  }

  Widget _obstacleBadge(BoardObstacle obstacle) {
    final (icon, color) = switch (obstacle.type) {
      BoardObstacleType.woodBox => (Icons.inventory_2_rounded, const Color(0xFF8B5A35)),
      BoardObstacleType.ice => (Icons.ac_unit_rounded, const Color(0xFF58A9D6)),
      BoardObstacleType.vine => (Icons.park_rounded, const Color(0xFF568447)),
      BoardObstacleType.soil => (Icons.grass_rounded, const Color(0xFF9A7449)),
    };
    return Container(
      height: 17,
      padding: const EdgeInsets.symmetric(horizontal: 3),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: Colors.white, width: 0.8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: Colors.white, size: 10),
          if (obstacle.layers > 1) ...[
            const SizedBox(width: 1),
            Text(
              _fa(obstacle.layers),
              style: const TextStyle(color: Colors.white, fontSize: 8, fontWeight: FontWeight.w900),
            ),
          ],
        ],
      ),
    );
  }

  Widget _lockBadge(TileLock lock) {
    final cracked = lock.layers == 1;
    return Container(
        padding: const EdgeInsets.symmetric(horizontal: 3, vertical: 2),
        decoration: BoxDecoration(
          color: cracked ? _orange : const Color(0xFF68492F),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: Colors.white, width: 0.8),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              cracked ? Icons.lock_outline_rounded : Icons.lock_rounded,
              color: Colors.white,
              size: 9,
            ),
            const SizedBox(width: 2),
            Text(
              _fa(lock.layers),
              style: const TextStyle(color: Colors.white, fontSize: 8, fontWeight: FontWeight.w900),
            ),
          ],
        ),
      );
  }

  Widget _buildMessage() {
    final color = _phase == GamePhase.won
        ? GamePalette.forest
        : _phase == GamePhase.lost
            ? GamePalette.strawberry
            : _chocolate;
    return AnimatedSwitcher(
      duration: _motion(const Duration(milliseconds: 240)),
      transitionBuilder: (child, animation) => FadeTransition(
        opacity: animation,
        child: ScaleTransition(scale: animation, child: child),
      ),
      child: SizedBox(
        key: ValueKey<String>(_message),
        height: 18,
        child: Center(
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 2),
            decoration: BoxDecoration(
              color: GamePalette.creamLight.withValues(alpha: 0.90),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                color: GamePalette.gold.withValues(alpha: 0.75),
                width: 1,
              ),
            ),
            child: Text(
              _message,
              textAlign: TextAlign.center,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: color,
                fontSize: 10.5,
                fontWeight: FontWeight.w900,
                height: 1.1,
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildPauseOverlay() => ColoredBox(
        color: const Color(0xB3142B12),
        child: Center(
          child: SizedBox(
            width: 300,
            child: AspectRatio(
              aspectRatio: 720 / 1080,
              child: OrchardPanel(
              asset: 'assets/ui/modal-panel.png',
              padding: const EdgeInsets.fromLTRB(44, 92, 44, 56),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  const Icon(
                    Icons.pause_circle_filled_rounded,
                    size: 44,
                    color: GamePalette.gold,
                  ),
                  const SizedBox(height: 6),
                  const Text(
                    'بازی مکث شد',
                    style: TextStyle(
                      color: _chocolate,
                      fontSize: 20,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 14),
                  _GameButton(
                    label: 'ادامهٔ بازی',
                    emoji: '▶️',
                    primary: true,
                    onPressed: _resume,
                  ),
                  const SizedBox(height: 8),
                  _GameButton(
                    label: 'شروع دوباره',
                    emoji: '🌱',
                    primary: false,
                    onPressed: _confirmRestart,
                  ),
                  const SizedBox(height: 8),
                ],
              ),
            ),
            ),
          ),
        ),
      );
}

class _GameButton extends StatelessWidget {
  const _GameButton({
    required this.label,
    required this.emoji,
    required this.primary,
    required this.onPressed,
  });

  final String label;
  final String emoji;
  final bool primary;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 46,
      child: DecoratedBox(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(23),
          boxShadow: GamePalette.chipShadow,
        ),
        child: Stack(
          fit: StackFit.expand,
          children: <Widget>[
            Image.asset(
              primary ? 'assets/ui/button-gold.png' : 'assets/ui/button-wood.png',
              fit: BoxFit.fill,
            ),
            Material(
              color: Colors.transparent,
              child: InkWell(
                borderRadius: BorderRadius.circular(23),
                onTap: onPressed,
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 22),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: <Widget>[
                      Text(emoji, style: const TextStyle(fontSize: 15)),
                      const SizedBox(width: 6),
                      Flexible(
                        child: Text(
                          label,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: primary
                                ? GamePalette.chocolate
                                : const Color(0xFFFFF0D2),
                            fontSize: 13,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Layered orchard scenery: the painted background, a warm depth scrim and a
/// soft light bloom in the top corner. Drawn behind every widget, never used as
/// the interface itself.
class _OrchardBackdrop extends StatelessWidget {
  const _OrchardBackdrop();

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: <Widget>[
        Image.asset(
          'assets/backgrounds/sunny-orchard.png',
          fit: BoxFit.cover,
          alignment: Alignment.bottomCenter,
          filterQuality: FilterQuality.medium,
        ),
        const DecoratedBox(
          decoration: BoxDecoration(gradient: GamePalette.screenScrim),
        ),
        const DecoratedBox(
          decoration: BoxDecoration(
            gradient: RadialGradient(
              center: Alignment(-0.7, -0.9),
              radius: 1.1,
              colors: <Color>[Color(0x33FFE9A8), Color(0x00FFE9A8)],
            ),
          ),
        ),
      ],
    );
  }
}

String _fa(int value) {
  const digits = '۰۱۲۳۴۵۶۷۸۹';
  return value.toString().split('').map((digit) {
    final number = int.tryParse(digit);
    return number == null ? digit : digits[number];
  }).join();
}

enum _FeedbackKind { selection, light, medium, heavy }
