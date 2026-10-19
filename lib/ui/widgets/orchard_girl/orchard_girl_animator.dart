import 'dart:math' as math;

import 'package:flutter/animation.dart';
import 'package:flutter/foundation.dart';

import 'orchard_girl_rig.dart';

/// Procedural motion for the rig. Everything is a function of elapsed time, so
/// frames stay smooth and the widget never keeps more than one clock.
///
/// The class knows nothing about score, moves or goals: it only turns a mood
/// name into joint angles.
class OrchardGirlAnimator extends ChangeNotifier {
  OrchardGirlAnimator({this.reduceMotion = false}) {
    _pose = _moodPose(_mood, 0);
  }

  static const double moodBlendSeconds = 0.34;

  String _mood = 'idle';
  String _previousMood = 'idle';
  double _moodElapsed = 0;
  double _blend = 1;
  double _time = 0;
  bool _started = false;
  bool reduceMotion;
  double _blink = 0;
  double _blinkPhase = -1;
  double _nextBlink = 2.6;
  int _blinkSeed = 7;
  OrchardGirlPose _pose = const OrchardGirlPose();

  /// Unique id per blink, used by tests to count blinks.
  int _blinkCount = 0;

  String get mood => _mood;
  OrchardGirlPose get pose => _pose;
  int get blinkCount => _blinkCount;
  double get timeSeconds => _time;

  /// Seconds the current mood has been running (drives the procedural curves).
  double get moodElapsedSeconds => _moodElapsed;

  /// Switches mood. A new mood blends from the current pose instead of
  /// snapping, and re-selecting the active mood keeps its phase (a repeated
  /// match during `excited` must not restart the animation).
  void setMood(String mood) {
    if (mood == _mood) return;
    _previousMood = _mood;
    _mood = mood;
    _moodElapsed = 0;
    _blend = 0;
    _nextBlink = math.min(_nextBlink, 0.9);
    _advance(0);
  }

  void setReduceMotion(bool value) {
    if (value == reduceMotion) return;
    reduceMotion = value;
    if (value) {
      _blink = 0;
      _blinkPhase = -1;
    }
    _advance(0);
  }

  /// Advances the clock. [elapsed] is the absolute ticker time.
  void tick(Duration elapsed) {
    final seconds = elapsed.inMicroseconds / Duration.microsecondsPerSecond;
    final delta = _started ? (seconds - _time).clamp(0.0, 0.1) : 0.0;
    _started = true;
    _time = seconds;
    _advance(delta);
  }

  /// Pure pose evaluation, also used by the debug gallery and tests.
  static OrchardGirlPose poseAt(
    String mood,
    double time, {
    double blink = 0,
  }) {
    return _moodPose(mood, time, blink: blink);
  }

  void _advance(double delta) {
    if (reduceMotion) {
      final still = _moodPose(_mood, 0, blink: 0);
      if (still != _pose) {
        _pose = still;
        notifyListeners();
      }
      return;
    }
    _moodElapsed += delta;
    if (_blend < 1) {
      _blend = math.min(1, _blend + delta / moodBlendSeconds);
    }
    _updateBlink(delta);
    final next = _composePose();
    if (next != _pose) {
      _pose = next;
      notifyListeners();
    }
  }

  void _updateBlink(double delta) {
    if (!_canBlink) {
      _blink = 0;
      _blinkPhase = -1;
      return;
    }
    if (_blinkPhase >= 0) {
      _blinkPhase += delta;
      const close = 0.085;
      const hold = 0.045;
      const open = 0.16;
      if (_blinkPhase < close) {
        _blink = _blinkPhase / close;
      } else if (_blinkPhase < close + hold) {
        _blink = 1;
      } else if (_blinkPhase < close + hold + open) {
        _blink = 1 - (_blinkPhase - close - hold) / open;
      } else {
        _blink = 0;
        _blinkPhase = -1;
        _nextBlink = _irregularInterval();
      }
      return;
    }
    _nextBlink -= delta;
    if (_nextBlink <= 0) {
      _blinkPhase = 0;
      _blink = 0;
      _blinkCount++;
    }
  }

  /// 2.4 s .. 6.8 s with an occasional double blink, never a fixed metronome.
  double _irregularInterval() {
    _blinkSeed = (_blinkSeed * 1103515245 + 12345) & 0x7fffffff;
    final unit = (_blinkSeed % 1000) / 1000.0;
    final base = 2.4 + unit * 4.4;
    if (unit > 0.86) return 0.22; // quick second blink
    return base;
  }

  bool get _canBlink {
    if (reduceMotion) return false;
    return _mood == 'idle' ||
        _mood == 'happy' ||
        _mood == 'encourage' ||
        _mood == 'harvest' ||
        _mood == 'sad';
  }

  OrchardGirlPose _composePose() {
    final target = _moodPose(_mood, _moodElapsed, blink: _blink);
    if (_blend >= 1) return target;
    final from = _moodPose(_previousMood, _moodElapsed, blink: _blink);
    final eased = Curves.easeInOutCubic.transform(_blend);
    return OrchardGirlPose.lerp(from, target, eased);
  }

  static OrchardGirlPose _moodPose(
    String mood,
    double t, {
    double blink = 0,
  }) {
    final face = _faceFor(mood);
    final b = blink.clamp(0.0, 1.0);
    switch (mood) {
      case 'happy':
        return _happy(t, face, b);
      case 'excited':
        return _excited(t, face, b);
      case 'celebrate':
        return _celebrate(t, face, b);
      case 'encourage':
        return _encourage(t, face, b);
      case 'sad':
        return _sad(t, face, b);
      case 'harvest':
        return _harvest(t, face, b);
      case 'idle':
      default:
        return _idle(t, face, b);
    }
  }

  static OrchardGirlFace _faceFor(String mood) {
    switch (mood) {
      case 'happy':
        return const OrchardGirlFace(eyes: 'open', brows: 'joy', mouth: 'grin');
      case 'excited':
        return const OrchardGirlFace(
          eyes: 'wide',
          brows: 'raised',
          mouth: 'open',
        );
      case 'celebrate':
        return const OrchardGirlFace(
          eyes: 'open',
          brows: 'joy',
          mouth: 'laugh',
        );
      case 'encourage':
        return const OrchardGirlFace(
          eyes: 'soft',
          brows: 'soft',
          mouth: 'soft',
        );
      case 'sad':
        return const OrchardGirlFace(eyes: 'soft', brows: 'sad', mouth: 'sad');
      case 'harvest':
        return const OrchardGirlFace(eyes: 'open', brows: 'joy', mouth: 'grin');
      case 'idle':
      default:
        return const OrchardGirlFace(
          eyes: 'open',
          brows: 'neutral',
          mouth: 'smile',
        );
    }
  }

  static OrchardGirlPose _idle(double t, OrchardGirlFace face, double blink) {
    // Breathing has its own slow period; the head, hair and shoulder drift on
    // deliberately different periods so the loop never looks mechanical. All
    // of them ramp in from zero, so frame 0 is exactly the untouched drawing.
    final settle = _settle(t);
    final breath = settle * math.sin(t * 2 * math.pi / 3.6);
    final pause = 1 - 0.55 * _smoothPulse(t, 7.4, 1.3);
    final headSway = settle * math.sin(t * 2 * math.pi / 6.9);
    final lockLag = settle * math.sin(t * 2 * math.pi / 6.9 - 0.9);
    final frontLag = settle * math.sin(t * 2 * math.pi / 6.9 - 1.35);
    final shoulder = settle * math.sin(t * 2 * math.pi / 4.9 + 0.6);
    return OrchardGirlPose(
      angles: <String, double>{
        OrchardGirlJoints.neck: 0.5 * headSway,
        OrchardGirlJoints.head: 1.1 * headSway,
        OrchardGirlJoints.bangs: 0.5 * frontLag,
        OrchardGirlJoints.lockLeft: 1.9 * lockLag,
        OrchardGirlJoints.lockRight: -1.7 * lockLag,
        OrchardGirlJoints.shoulderRight: 0.9 * shoulder,
        // the basket hand stays put: any left-shoulder rotation swings the
        // basket away from the fingers and exposes reconstructed pixels
        OrchardGirlJoints.shoulderLeft: 0,
      },
      offsets: <String, Offset>{
        OrchardGirlJoints.hip: Offset(0, -1.1 * breath * pause),
        OrchardGirlJoints.neck: Offset(0, -0.6 * breath * pause),
      },
      rootScale: 1 + 0.0045 * breath * pause,
      blink: blink,
      eyes: face.eyes,
      brows: face.brows,
      mouth: face.mouth,
    );
  }

  static OrchardGirlPose _happy(double t, OrchardGirlFace face, double blink) {
    final bounce = math.sin(t * 2 * math.pi / 1.55);
    final armSwing = math.sin(t * 2 * math.pi / 1.55 - 0.5);
    final lockLag = math.sin(t * 2 * math.pi / 2.1 - 0.8);
    return OrchardGirlPose(
      angles: <String, double>{
        OrchardGirlJoints.head: -2.6 + 0.9 * bounce,
        OrchardGirlJoints.bangs: -1.2 + 0.5 * lockLag,
        OrchardGirlJoints.lockLeft: 3.2 * lockLag,
        OrchardGirlJoints.lockRight: -3 * lockLag,
        OrchardGirlJoints.shoulderRight: -8 - 3 * armSwing,
        OrchardGirlJoints.elbowRight: -4 - 2 * armSwing,
        OrchardGirlJoints.shoulderLeft: 0,
        OrchardGirlJoints.basketLeft: 0.8 * armSwing,
      },
      offsets: <String, Offset>{
        OrchardGirlJoints.hip: Offset(0, -1.4 - 1.1 * bounce),
      },
      rootScale: 1 + 0.003 * bounce,
      blink: blink,
      eyes: face.eyes,
      brows: face.brows,
      mouth: face.mouth,
    );
  }

  static OrchardGirlPose _excited(
    double t,
    OrchardGirlFace face,
    double blink,
  ) {
    // 1.3 s hop cycle: quick rise, short hang, soft landing.
    final cycle = t % 1.3;
    final hop = cycle < 0.35
        ? Curves.easeOutCubic.transform(cycle / 0.35)
        : cycle < 0.55
            ? 1
            : Curves.easeInOutCubic.transform(1 - (cycle - 0.55) / 0.75);
    final antisipation = cycle < 0.12
        ? -3.5 * (cycle / 0.12)
        : 0.0;
    final lift = -9 * hop;
    final armWave = math.sin(t * 2 * math.pi / 1.1);
    final lockLag = math.sin(t * 2 * math.pi / 1.3 - 1.1);
    return OrchardGirlPose(
      angles: <String, double>{
        OrchardGirlJoints.head: -4.5 + 1.5 * armWave,
        OrchardGirlJoints.bangs: -2 + 1.4 * lockLag,
        OrchardGirlJoints.lockLeft: 6 * lockLag,
        OrchardGirlJoints.lockRight: -6.5 * lockLag,
        OrchardGirlJoints.shoulderRight: -14 - 3 * armWave,
        OrchardGirlJoints.elbowRight: -8 + 2 * armWave,
        OrchardGirlJoints.shoulderLeft: 0,
        OrchardGirlJoints.basketLeft: 1.2 * armWave,
      },
      offsets: <String, Offset>{
        OrchardGirlJoints.hip: Offset(0, lift + antisipation),
      },
      blink: 0,
      eyes: face.eyes,
      brows: face.brows,
      mouth: face.mouth,
    );
  }

  static OrchardGirlPose _celebrate(
    double t,
    OrchardGirlFace face,
    double blink,
  ) {
    // One controlled jump, then a slow settle that keeps breathing.
    final jump = _oneShot(t, start: 0.12, rise: 0.38, hold: 0.18, fall: 0.5);
    final dip = _oneShot(t, start: 0.0, rise: 0.12, hold: 0.02, fall: 0.12);
    final settle = math.sin(
      (t - 1.1).clamp(0.0, double.infinity) * 2 * math.pi / 3.4,
    );
    final armHold = _oneShot(t, start: 0.06, rise: 0.3, hold: 1.1, fall: 0.7);
    final armSway = math.sin(t * 2 * math.pi / 1.05);
    final lockLag = math.sin(t * 2 * math.pi / 1.4 - 1.2);
    return OrchardGirlPose(
      angles: <String, double>{
        OrchardGirlJoints.head: -5.5 * armHold + 1.2 * settle,
        OrchardGirlJoints.bangs: -3.2 * armHold + 1.6 * lockLag,
        OrchardGirlJoints.lockLeft: 8 * lockLag,
        OrchardGirlJoints.lockRight: -8.5 * lockLag,
        OrchardGirlJoints.shoulderRight: -20 * armHold + 2 * armSway,
        OrchardGirlJoints.elbowRight: -14 * armHold,
        OrchardGirlJoints.shoulderLeft: 0,
        OrchardGirlJoints.basketLeft: 1.5 * armSway * armHold,
      },
      offsets: <String, Offset>{
        OrchardGirlJoints.hip: Offset(
          0,
          -15 * jump + 4 * dip + 0.8 * settle,
        ),
      },
      blink: 0,
      eyes: face.eyes,
      brows: face.brows,
      mouth: face.mouth,
    );
  }

  static OrchardGirlPose _encourage(
    double t,
    OrchardGirlFace face,
    double blink,
  ) {
    final sway = math.sin(t * 2 * math.pi / 3.1);
    final invite = math.sin(t * 2 * math.pi / 2.4 - 0.4);
    final lockLag = math.sin(t * 2 * math.pi / 3.1 - 0.8);
    return OrchardGirlPose(
      angles: <String, double>{
        OrchardGirlJoints.head: 3.2 + 0.8 * sway,
        OrchardGirlJoints.neck: 1.4,
        OrchardGirlJoints.bangs: 1.4 + 0.7 * lockLag,
        OrchardGirlJoints.lockLeft: 2.6 * lockLag,
        OrchardGirlJoints.lockRight: -2.4 * lockLag,
        // the free hand opens towards the player, the basket hand stays close
        OrchardGirlJoints.shoulderRight: -10 - 3 * invite,
        OrchardGirlJoints.elbowRight: -6 - 2 * invite,
        OrchardGirlJoints.shoulderLeft: 0,
      },
      offsets: <String, Offset>{
        OrchardGirlJoints.hip: Offset(0, -0.8 * sway),
      },
      blink: blink,
      eyes: face.eyes,
      brows: face.brows,
      mouth: face.mouth,
    );
  }

  static OrchardGirlPose _sad(double t, OrchardGirlFace face, double blink) {
    // Small amplitude on purpose: a quiet slump, not a melodramatic collapse.
    final breath = math.sin(t * 2 * math.pi / 5.2);
    final lockLag = math.sin(t * 2 * math.pi / 5.2 - 1.0);
    return OrchardGirlPose(
      angles: <String, double>{
        OrchardGirlJoints.head: 7.5 + 0.5 * breath,
        OrchardGirlJoints.neck: 2.6,
        OrchardGirlJoints.bangs: 2.4 + 0.4 * lockLag,
        OrchardGirlJoints.lockLeft: 1.4 * lockLag,
        OrchardGirlJoints.lockRight: -1.3 * lockLag,
        OrchardGirlJoints.shoulderRight: 2.5,
        OrchardGirlJoints.shoulderLeft: 0,
      },
      offsets: <String, Offset>{
        OrchardGirlJoints.hip: Offset(0, 1.6 + 0.7 * breath),
      },
      rootScale: 1 - 0.0035 * (0.5 + 0.5 * breath),
      blink: blink,
      eyes: face.eyes,
      brows: face.brows,
      mouth: face.mouth,
    );
  }

  static OrchardGirlPose _harvest(
    double t,
    OrchardGirlFace face,
    double blink,
  ) {
    // Reach, pick, lift the basket, then a short happy nod.
    final reach = _oneShot(
      t % 2.2,
      start: 0.0,
      rise: 0.45,
      hold: 0.5,
      fall: 0.65,
    );
    final nod = math.sin((t % 2.2) * 2 * math.pi / 2.2);
    final lockLag = math.sin(t * 2 * math.pi / 2.2 - 0.9);
    return OrchardGirlPose(
      angles: <String, double>{
        OrchardGirlJoints.head: -1.5 + 1.8 * nod - 2.4 * reach,
        OrchardGirlJoints.bangs: -0.8 + 0.9 * lockLag,
        OrchardGirlJoints.lockLeft: 2.8 * lockLag,
        OrchardGirlJoints.lockRight: -2.6 * lockLag,
        OrchardGirlJoints.shoulderRight: -8 * reach,
        OrchardGirlJoints.elbowRight: -6 * reach,
        OrchardGirlJoints.shoulderLeft: 0,
        OrchardGirlJoints.basketLeft: 0,
      },
      blink: blink,
      eyes: face.eyes,
      brows: face.brows,
      mouth: face.mouth,
    );
  }

  /// Eased 0 -> 1 -> 0 pulse used for one-shot accents.
  static double _oneShot(
    double t, {
    required double start,
    required double rise,
    required double hold,
    required double fall,
  }) {
    if (t < start) return 0;
    final local = t - start;
    if (local < rise) return Curves.easeOutCubic.transform(local / rise);
    if (local < rise + hold) return 1;
    if (local < rise + hold + fall) {
      return 1 - Curves.easeInOutCubic.transform((local - rise - hold) / fall);
    }
    return 0;
  }

  /// Eases motion in from the exact rest pose instead of starting mid-swing.
  static double _settle(double t) {
    const duration = 0.7;
    if (t >= duration) return 1;
    if (t <= 0) return 0;
    return Curves.easeOutCubic.transform(t / duration);
  }

  static double _smoothPulse(double t, double period, double length) {
    final phase = t % period;
    if (phase > length) return 0;
    return math.sin(phase / length * math.pi);
  }
}
