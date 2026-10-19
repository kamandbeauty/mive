import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';

import 'orchard_girl/orchard_girl_animator.dart';
import 'orchard_girl/orchard_girl_assets.dart';
import 'orchard_girl/orchard_girl_painter.dart';

export 'orchard_girl/orchard_girl_animator.dart';
export 'orchard_girl/orchard_girl_assets.dart';
export 'orchard_girl/orchard_girl_painter.dart';
export 'orchard_girl/orchard_girl_rig.dart';

/// The garden helper's expression/pose. These are presentation-only states;
/// they never participate in score, move, goal, or progress calculations.
enum OrchardGirlMood {
  idle,
  happy,
  excited,
  encourage,
  sad,
  celebrate,
  harvest,
}

enum OrchardGirlEvent {
  ordinaryMatch,
  specialCreated,
  specialActivated,
  cascade,
  nearGoal,
  goalsComplete,
  levelWon,
  levelLost,
  validHint,
  rewardGranted,
}

/// Drives the character from real game events. It only chooses a mood and a
/// caption; gameplay state is never read or written here.
class OrchardGirlController extends ChangeNotifier {
  OrchardGirlMood _mood = OrchardGirlMood.idle;
  String? _caption;
  Timer? _resetTimer;
  DateTime? _lastCaptionAt;
  String? _lastCaption;
  bool _disposed = false;
  int _encouragementIndex = 0;
  DateTime? _moodChangedAt;

  OrchardGirlMood get mood => _mood;
  String? get caption => _caption;

  static const _captionCooldown = Duration(seconds: 3);
  static const _reactionDuration = Duration(milliseconds: 2400);

  /// A big moment must not be cut short by a small one that lands right after
  /// it. Lower-priority events wait at least this long.
  static const _priorityHold = Duration(milliseconds: 1200);

  static int _priorityOf(OrchardGirlMood mood) => switch (mood) {
        OrchardGirlMood.celebrate || OrchardGirlMood.sad => 3,
        OrchardGirlMood.excited ||
        OrchardGirlMood.harvest ||
        OrchardGirlMood.happy =>
          2,
        OrchardGirlMood.encourage || OrchardGirlMood.idle => 1,
      };

  /// Applies a brief reaction to a real game event. Captions are rate-limited
  /// and identical consecutive messages are suppressed.
  void react(OrchardGirlEvent event) {
    if (_disposed) return;
    final options = switch (event) {
      OrchardGirlEvent.ordinaryMatch => (
          OrchardGirlMood.harvest,
          const ['آفرین! سبدت پُرتر شد.', 'احسنت! چه جورچین خوبی.', 'دمت گرم! میوه‌ها را عالی چیدی.'],
        ),
      OrchardGirlEvent.specialCreated => (
          OrchardGirlMood.excited,
          const ['آفرین! یک قدرت تازه ساختی.', 'احسنت! این قدرت به‌دردبخوره.', 'دمت گرم! ترکیب ویژه ساختی.'],
        ),
      OrchardGirlEvent.specialActivated => (
          OrchardGirlMood.excited,
          const ['آفرین! چه ترکیب خوبی.', 'احسنت! قدرت‌ها عالی هماهنگ شدند.', 'دمت گرم! چه حرکت درخشانی.'],
        ),
      OrchardGirlEvent.cascade => (
          OrchardGirlMood.excited,
          const ['زنجیرهٔ میوه‌ای! آفرین.', 'احسنت، یکی پس از دیگری جور شدند.', 'دمت گرم! چه آبشار قشنگی.'],
        ),
      OrchardGirlEvent.nearGoal => (
          OrchardGirlMood.encourage,
          const ['فقط چند میوه مانده؛ از پسش برمی‌آیی.', 'آفرین، خیلی به هدفت نزدیک شدی.', 'ادامه بده؛ باغت دارد شکوفا می‌شود.'],
        ),
      OrchardGirlEvent.goalsComplete => (
          OrchardGirlMood.happy,
          const ['همهٔ میوه‌ها جمع شد؛ آفرین!', 'احسنت، هدف‌ها کامل شدند.'],
        ),
      OrchardGirlEvent.levelWon => (
          OrchardGirlMood.celebrate,
          const ['باغت شکوفا شد؛ آفرین!', 'احسنت، مرحله را بردی!'],
        ),
      OrchardGirlEvent.levelLost => (
          OrchardGirlMood.sad,
          const ['هر وقت خواستی دوباره تلاش کن.', 'این بار نشد؛ با خیال راحت دوباره امتحان کن.'],
        ),
      OrchardGirlEvent.validHint => (
          OrchardGirlMood.encourage,
          const ['این حرکت را امتحان کن.', 'شاید این جابه‌جایی کمکت کند.'],
        ),
      OrchardGirlEvent.rewardGranted => (
          OrchardGirlMood.happy,
          const ['جایزه به سبدت اضافه شد؛ آفرین!', 'حرکت‌های اضافه آماده‌اند.'],
        ),
    };
    final reaction = (
      options.$1,
      options.$2[_encouragementIndex++ % options.$2.length],
    );

    final now = DateTime.now();
    final cooldownPassed = _lastCaptionAt == null ||
        now.difference(_lastCaptionAt!) >= _captionCooldown;
    final terminal = event == OrchardGirlEvent.levelWon ||
        event == OrchardGirlEvent.levelLost ||
        event == OrchardGirlEvent.rewardGranted;
    final duplicate = reaction.$2 == _lastCaption;
    final showCaption = cooldownPassed || (terminal && !duplicate);

    final holdActive = _moodChangedAt != null &&
        now.difference(_moodChangedAt!) < _priorityHold &&
        _priorityOf(_mood) > _priorityOf(reaction.$1);
    if (!holdActive) {
      _mood = reaction.$1;
      _moodChangedAt = now;
    }
    if (showCaption) {
      _caption = reaction.$2;
      _lastCaption = reaction.$2;
      _lastCaptionAt = now;
    } else {
      _caption = null;
    }
    _resetTimer?.cancel();
    _resetTimer = Timer(_reactionDuration, resetToIdle);
    notifyListeners();
  }

  void resetToIdle() {
    if (_disposed) return;
    _resetTimer?.cancel();
    _resetTimer = null;
    if (_mood == OrchardGirlMood.idle && _caption == null) return;
    _mood = OrchardGirlMood.idle;
    _caption = null;
    _moodChangedAt = DateTime.now();
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _resetTimer?.cancel();
    _resetTimer = null;
    super.dispose();
  }
}

/// Responsive, non-interactive character art. The player can always touch the
/// board and controls through it; idle breathing/blinks respect Reduce Motion.
class OrchardGirl extends StatefulWidget {
  const OrchardGirl({
    this.controller,
    this.mood = OrchardGirlMood.idle,
    this.width = 96,
    this.height = 144,
    this.showCaption = false,
    this.reduceMotionOverride,
    super.key,
  });

  final OrchardGirlController? controller;
  final OrchardGirlMood mood;
  final double width;
  final double height;
  final bool showCaption;

  /// Test/dev hook; when null the widget follows `MediaQuery.disableAnimations`.
  final bool? reduceMotionOverride;

  @override
  State<OrchardGirl> createState() => _OrchardGirlState();
}

class _OrchardGirlState extends State<OrchardGirl>
    with SingleTickerProviderStateMixin {
  late final Ticker _ticker;
  OrchardGirlAnimator? _animator;
  OrchardGirlImages? _images;
  bool _loading = false;
  bool _reduceMotion = false;
  bool _disposed = false;

  OrchardGirlMood get _mood => widget.controller?.mood ?? widget.mood;
  String? get _caption => widget.controller?.caption;

  @override
  void initState() {
    super.initState();
    _animator = OrchardGirlAnimator()..addListener(_onPoseChanged);
    _animator!.setMood(_mood.name);
    _ticker = createTicker(_onTick);
    widget.controller?.addListener(_onControllerChanged);
    _loadAssets();
  }

  Future<void> _loadAssets() async {
    if (_loading) return;
    _loading = true;
    try {
      final images = await OrchardGirlImages.acquire();
      if (!mounted || _disposed) {
        OrchardGirlImages.release();
        return;
      }
      setState(() => _images = images);
      _syncTicker();
    } catch (error, stack) {
      _loading = false;
      FlutterError.reportError(
        FlutterErrorDetails(
          exception: error,
          stack: stack,
          library: 'orchard_girl',
          context: ErrorDescription('loading the layered character rig'),
        ),
      );
      return;
    }
    _loading = false;
  }

  void _onPoseChanged() {
    if (!mounted) return;
    setState(() {});
  }

  void _onTick(Duration elapsed) {
    _animator?.tick(elapsed);
  }

  @override
  void didUpdateWidget(covariant OrchardGirl oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller?.removeListener(_onControllerChanged);
      widget.controller?.addListener(_onControllerChanged);
    }
    if (oldWidget.reduceMotionOverride != widget.reduceMotionOverride) {
      _applyReduceMotion();
    }
    _animator?.setMood(_mood.name);
    _syncTicker();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _applyReduceMotion();
  }

  void _applyReduceMotion() {
    final reduce = widget.reduceMotionOverride ??
        MediaQuery.maybeOf(context)?.disableAnimations ??
        false;
    if (reduce == _reduceMotion) return;
    _reduceMotion = reduce;
    _animator?.setReduceMotion(reduce);
    _syncTicker();
  }

  void _onControllerChanged() {
    if (!mounted) return;
    _animator?.setMood(_mood.name);
    _syncTicker();
    setState(() {});
  }

  /// One ticker for the whole character: started only while something can
  /// actually move, so a reduced-motion frame costs no clock.
  void _syncTicker() {
    final shouldRun = !_reduceMotion && _images != null && mounted;
    if (shouldRun && !_ticker.isActive) {
      _ticker.start();
    } else if (!shouldRun && _ticker.isActive) {
      _ticker.stop();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    widget.controller?.removeListener(_onControllerChanged);
    if (_ticker.isActive) _ticker.stop();
    _ticker.dispose();
    _animator?.removeListener(_onPoseChanged);
    _animator?.dispose();
    if (_images != null) OrchardGirlImages.release();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final caption = _caption;
    final images = _images;
    final animator = _animator;
    return IgnorePointer(
      child: Semantics(
        image: true,
        label: 'دختر باغبان',
        child: SizedBox(
          width: widget.width,
          height: widget.height,
          child: Stack(
            alignment: Alignment.bottomCenter,
            clipBehavior: Clip.none,
            children: [
              RepaintBoundary(
                child: CustomPaint(
                  size: Size(widget.width, widget.height),
                  painter: images == null || animator == null
                      ? null
                      : OrchardGirlPainter(
                          images: images,
                          pose: animator.pose,
                        ),
                  child: const SizedBox.expand(),
                ),
              ),
              if (widget.showCaption && caption != null)
                PositionedDirectional(
                  start: 0,
                  top: 0,
                  child: ConstrainedBox(
                    constraints: BoxConstraints(maxWidth: widget.width * 1.7),
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: const Color(0xFFFFF7E8).withValues(alpha: 0.96),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: const Color(0xFFE8C98F)),
                        boxShadow: const [
                          BoxShadow(
                            color: Color(0x332B2118),
                            blurRadius: 7,
                            offset: Offset(0, 2),
                          ),
                        ],
                      ),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 5,
                        ),
                        child: Text(
                          caption,
                          maxLines: 2,
                          textAlign: TextAlign.center,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: Color(0xFF68492F),
                            fontSize: 10,
                            fontWeight: FontWeight.w800,
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
    );
  }
}
