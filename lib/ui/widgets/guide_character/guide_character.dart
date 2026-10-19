import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';

import '../orchard_girl.dart' show OrchardGirlController, OrchardGirlMood;
import 'guide_rig.dart';
import 'guide_rig_animator.dart';

/// Expression art used for each presentation mood.
const Map<OrchardGirlMood, String> _expressionForMood = <OrchardGirlMood, String>{
  OrchardGirlMood.idle: 'happy',
  OrchardGirlMood.happy: 'happy',
  OrchardGirlMood.harvest: 'happy',
  OrchardGirlMood.excited: 'excited',
  OrchardGirlMood.celebrate: 'laughing',
  OrchardGirlMood.encourage: 'winking',
  OrchardGirlMood.sad: 'sad',
};

/// Event-driven, jointed character renderer.
///
/// The body is composed from the delivered layers on a parented bone tree, so a
/// mood is performed by rotating real joints (shoulder, elbow, wrist, neck,
/// waist, knees, ankles) and letting the basket swing from the carrying hand.
/// The whole image is never swapped, faded, slid or scaled to fake motion.
///
/// Animation runs only while the widget is on screen, not [paused] and not in
/// Reduce Motion. The ticker, listeners and repaint notifier are released with
/// the widget, and no artwork is decoded per frame.
class GuideCharacter extends StatefulWidget {
  const GuideCharacter({
    this.controller,
    this.mood = OrchardGirlMood.idle,
    this.height = 170,
    this.paused = false,
    this.reduceMotionOverride,
    super.key,
  });

  final OrchardGirlController? controller;

  /// Used when [controller] is null (static decoration on the menu screens).
  final OrchardGirlMood mood;
  final double height;

  /// Freezes non-essential animation while the game is paused or hidden.
  final bool paused;

  /// Test/dev hook; when null the widget follows `MediaQuery.disableAnimations`.
  final bool? reduceMotionOverride;

  /// Width / height of the drawn figure (alpha bounds of the delivered art).
  static const double aspect = 604 / 1029;

  /// Live ticker count, for tests and QA. Zero means nothing is animating.
  @visibleForTesting
  static int debugActiveTickers = 0;

  @override
  State<GuideCharacter> createState() => _GuideCharacterState();
}

class _GuideCharacterState extends State<GuideCharacter>
    with SingleTickerProviderStateMixin {
  final GuideRigAnimator _animator = GuideRigAnimator();
  final GuideRigFrame _frame = GuideRigFrame();
  final _Repaint _repaint = _Repaint();
  late final Ticker _ticker;

  GuideRig? _rig;
  bool _reduceMotion = false;
  bool _disposed = false;

  double _seconds = 0;
  double _sinceMood = 0;

  OrchardGirlMood _mood = OrchardGirlMood.idle;
  RigPose? _fromPose;
  bool _appLifecyclePaused = false;

  @override
  void initState() {
    super.initState();
    _mood = widget.controller?.mood ?? widget.mood;
    _frame.mood = _expressionForMood[_mood] ?? 'happy';
    _ticker = createTicker(_onTick);
    widget.controller?.addListener(_onControllerChanged);
    WidgetsBinding.instance.addObserver(_lifecycle);
    final state = WidgetsBinding.instance.lifecycleState;
    _appLifecyclePaused = state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused ||
        state == AppLifecycleState.hidden;
    _load();
  }

  Future<void> _load() async {
    try {
      final rig = await GuideRig.load();
      if (!mounted || _disposed) return;
      setState(() => _rig = rig);
      _applyMotionPolicy();
    } catch (error) {
      debugPrint('guide rig failed to load: $error');
    }
  }

  void _lifecycle(AppLifecycleState state) {
    final paused = state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused ||
        state == AppLifecycleState.hidden;
    if (paused == _appLifecyclePaused) return;
    _appLifecyclePaused = paused;
    _applyMotionPolicy();
  }

  void _onControllerChanged() {
    if (!mounted) return;
    final mood = widget.controller?.mood ?? widget.mood;
    if (mood != _mood) _setMood(mood);
  }

  void _setMood(OrchardGirlMood mood) {
    _fromPose = _frame.pose;
    _mood = mood;
    _frame.mood = _expressionForMood[mood] ?? 'happy';
    _sinceMood = 0;
    _repaint.mark();
  }

  @override
  void didUpdateWidget(covariant GuideCharacter oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller?.removeListener(_onControllerChanged);
      widget.controller?.addListener(_onControllerChanged);
      final mood = widget.controller?.mood ?? widget.mood;
      if (mood != _mood) _setMood(mood);
    } else if (oldWidget.mood != widget.mood &&
        widget.mood != _mood &&
        widget.controller == null) {
      _setMood(widget.mood);
    }
    _applyMotionPolicy();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _applyMotionPolicy();
  }

  bool get _motionAllowed {
    final reduce = widget.reduceMotionOverride ??
        (MediaQuery.maybeOf(context)?.disableAnimations ?? false);
    return !reduce && !widget.paused && !_appLifecyclePaused;
  }

  void _applyMotionPolicy() {
    _reduceMotion = !_motionAllowed;
    _frame.blink = 0;
    _updatePose();
    final shouldRun = !_reduceMotion && _rig != null && mounted;
    if (shouldRun && !_ticker.isActive) {
      _ticker.start();
      GuideCharacter.debugActiveTickers++;
    } else if (!shouldRun && _ticker.isActive) {
      _ticker.stop();
      GuideCharacter.debugActiveTickers = math.max(
        0,
        GuideCharacter.debugActiveTickers - 1,
      );
    }
    _repaint.mark();
  }

  void _onTick(Duration elapsed) {
    final seconds = elapsed.inMicroseconds / Duration.microsecondsPerSecond;
    final delta = (seconds - _seconds).clamp(0.0, 0.1);
    _seconds = seconds;
    _sinceMood += delta;
    _updatePose();
    _repaint.mark();
  }

  void _updatePose() {
    final target = _animator.pose(
      _mood,
      _sinceMood,
      reduceMotion: _reduceMotion,
    );
    final from = _fromPose;
    if (from != null) {
      final blend = math.min(1.0, _sinceMood / 0.26);
      if (blend >= 1) {
        _frame.pose = target;
        _fromPose = null;
      } else {
        _frame.pose = from.blend(target, _ease(blend));
      }
    } else {
      _frame.pose = target;
    }
    _frame.blink =
        _animator.blink(_mood, _sinceMood, reduceMotion: _reduceMotion);
  }

  static double _ease(double t) => t * t * (3 - 2 * t);

  @override
  void dispose() {
    _disposed = true;
    WidgetsBinding.instance.removeObserver(_lifecycle);
    widget.controller?.removeListener(_onControllerChanged);
    if (_ticker.isActive) {
      _ticker.stop();
      GuideCharacter.debugActiveTickers = math.max(
        0,
        GuideCharacter.debugActiveTickers - 1,
      );
    }
    _ticker.dispose();
    _repaint.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final rig = _rig;
    final width = widget.height * GuideCharacter.aspect;
    return IgnorePointer(
      child: Semantics(
        image: true,
        label: 'دختر باغبان',
        child: SizedBox(
          width: width,
          height: widget.height,
          child: RepaintBoundary(
            child: CustomPaint(
              painter: rig == null
                  ? null
                  : GuideRigPainter(
                      rig: rig,
                      frame: _frame,
                      scale: widget.height / rig.bodyRect.height,
                      offset: Offset(
                        -rig.bodyRect.left * widget.height / rig.bodyRect.height,
                        -rig.bodyRect.top * widget.height / rig.bodyRect.height,
                      ),
                      repaint: _repaint,
                    ),
              child: const SizedBox.expand(),
            ),
          ),
        ),
      ),
    );
  }
}

class _Repaint extends ChangeNotifier {
  void mark() => notifyListeners();
}
