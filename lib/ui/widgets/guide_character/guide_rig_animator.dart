import 'dart:math' as math;

import '../orchard_girl.dart' show OrchardGirlMood;
import 'guide_rig.dart';

/// Procedural performance for the jointed guide rig.
///
/// Every mood is a function of elapsed seconds on that mood. Bones rotate about
/// their real joints (shoulder, elbow, wrist, neck, knee, ankle, waist) and the
/// smallest body moves stay inside a couple of degrees, so the character reads
/// as a drawn 2D character rather than a picture that scales up and down.
///
/// Two rules keep this honest:
///  * [RigPose.rest] is the delivered art, untouched. Every mood starts from
///    rest at t = 0 and the idle loop returns to it once per cycle.
///  * Nothing here scales the whole figure. Only [RigBone] rotations and small
///    whole-body offsets are used, and the offsets stay under a few pixels.
class GuideRigAnimator {
  GuideRigAnimator();

  /// Moods whose face set ships closed eyes (from the delivered expression
  /// sets), so blinking is real art and not a fade to transparent.
  static const Set<OrchardGirlMood> blinkingMoods = <OrchardGirlMood>{
    OrchardGirlMood.idle,
    OrchardGirlMood.happy,
    OrchardGirlMood.encourage,
  };

  final List<double> _blinkStarts = <double>[];
  int _seed = 20261019;

  /// Pose for [mood] at [seconds] on that mood.
  ///
  /// With `reduceMotion` the signature pose of the mood is returned without
  /// oscillation, so the character still reads as happy/sad/etc. but stands
  /// still.
  RigPose pose(
    OrchardGirlMood mood,
    double seconds, {
    bool reduceMotion = false,
  }) {
    final t = seconds;
    final env = reduceMotion ? 0.0 : _settle(t);
    final raw = switch (mood) {
      OrchardGirlMood.idle => _idle(t, env),
      OrchardGirlMood.happy => _happy(t, env),
      OrchardGirlMood.excited => _excited(t, env),
      OrchardGirlMood.celebrate => _celebrate(t, env),
      OrchardGirlMood.encourage => _encourage(t, env),
      OrchardGirlMood.sad => _sad(t, env),
      OrchardGirlMood.harvest => _harvest(t, env),
    };
    // The delivered joint pivots are approximate: at full amplitude the
    // limbs visibly detach and the figure falls apart on a real phone.
    // 85% keeps shoulders, elbows and the neck composed while the
    // performance actually reads from across the room.
    return RigPose(<String, BonePose>{
      for (final entry in raw.bones.entries)
        entry.key: BonePose(
          angle: entry.value.angle * 0.6,
          dx: entry.value.dx * 0.6,
          dy: entry.value.dy * 0.6,
          scale: entry.value.scale,
        ),
    });
  }

  /// 0 → 1 over the first 0.7 s, so a pose change always eases out of rest.
  static double _settle(double t) {
    final x = (t / 0.7).clamp(0.0, 1.0);
    return x * x * (3 - 2 * x);
  }

  static double _sine(double t, double period, [double phase = 0]) =>
      math.sin(2 * math.pi * t / period + phase);

  RigPose _idle(double t, double env) {
    final breathe = _sine(t, 4.6) * env;
    final sway = _sine(t, 9.2) * env;
    return RigPose(<String, BonePose>{
      'body': BonePose(angle: 0.55 * breathe),
      'neck': BonePose(angle: -0.35 * breathe + 0.25 * sway),
      'hairBack': BonePose(angle: 1.10 * breathe),
      'hairFrontL': BonePose(angle: 0.85 * breathe),
      'hairFrontR': BonePose(angle: 0.65 * breathe),
      'armRUpper': BonePose(angle: 0.5 * breathe),
      'armRFore': BonePose(angle: 0.8 * breathe),
      'armRHand': BonePose(angle: 0.6 * breathe),
      'armLUpper': BonePose(angle: -0.35 * breathe),
      'basket': BonePose(angle: 2.6 * breathe),
      'skirt': BonePose(angle: 0.25 * breathe),
    });
  }

  RigPose _happy(double t, double env) {
    final wave = _sine(t, 1.6);
    final slow = _sine(t, 3.2);
    return RigPose(<String, BonePose>{
      'body': BonePose(angle: -1.2 * env + 0.5 * slow * env),
      'neck': BonePose(angle: -2.4 * env + 1.3 * slow * env),
      'armRUpper': BonePose(angle: -7.0 * env + 2.5 * wave * env),
      'armRFore': BonePose(angle: 13.0 * env + 9.0 * wave * env),
      'armRHand': BonePose(angle: 8.0 * env + 11.0 * wave * env),
      'armLUpper': BonePose(angle: -1.0 * env),
      'armLFore': BonePose(angle: 1.5 * env),
      'basket': BonePose(angle: -1.8 * env + 1.6 * slow * env),
      'hairBack': BonePose(angle: -2.2 * env + 1.8 * wave * env),
      'hairFrontL': BonePose(angle: 1.4 * env + 0.8 * wave * env),
      'hairFrontR': BonePose(angle: 1.0 * env - 0.6 * wave * env),
      'skirt': BonePose(angle: -0.6 * env),
    });
  }

  RigPose _excited(double t, double env) {
    final wave = _sine(t, 0.9);
    final hop = math.max(0.0, _sine(t, 1.1)) * env;
    return RigPose(<String, BonePose>{
      'root': BonePose(dy: -3.0 * hop),
      'body': BonePose(angle: -2.0 * env + 0.8 * wave * env),
      'neck': BonePose(angle: -3.5 * env + 2.2 * wave * env),
      'armRUpper': BonePose(angle: -12.0 * env + 6.0 * wave * env),
      'armRFore': BonePose(angle: 20.0 * env + 12.0 * wave * env),
      'armRHand': BonePose(angle: 14.0 * env + 14.0 * wave * env),
      'armLUpper': BonePose(angle: -5.0 * env - 1.5 * hop),
      'armLFore': BonePose(angle: 4.0 * env),
      'basket': BonePose(angle: -6.0 * env + 3.0 * wave * env),
      'hairBack': BonePose(angle: -3.5 * env + 3.0 * wave * env),
      'hairFrontL': BonePose(angle: 2.2 * env + 1.4 * wave * env),
      'hairFrontR': BonePose(angle: 1.6 * env - 1.0 * wave * env),
      'legL': BonePose(angle: -1.0 * hop),
      'legR': BonePose(angle: 1.0 * hop),
      'skirt': BonePose(angle: -1.0 * env - 0.8 * hop),
    });
  }

  RigPose _celebrate(double t, double env) {
    final wave = _sine(t, 0.8);
    final hop = math.max(0.0, _sine(t, 0.95)) * env;
    return RigPose(<String, BonePose>{
      'root': BonePose(dy: -6.0 * hop),
      'body': BonePose(angle: -3.4 * env),
      'neck': BonePose(angle: -6.0 * env + 2.4 * wave * env),
      'armRUpper': BonePose(angle: -19.0 * env + 7.0 * wave * env),
      'armRFore': BonePose(angle: 25.0 * env + 13.0 * wave * env),
      'armRHand': BonePose(angle: 18.0 * env + 15.0 * wave * env),
      'armLUpper': BonePose(angle: -9.0 * env),
      'armLFore': BonePose(angle: 7.0 * env),
      'basket': BonePose(angle: -11.0 * env + 3.0 * wave * env),
      'hairBack': BonePose(angle: -5.0 * env + 3.5 * wave * env),
      'hairFrontL': BonePose(angle: 3.0 * env + 1.6 * wave * env),
      'hairFrontR': BonePose(angle: 2.2 * env - 1.2 * wave * env),
      'legL': BonePose(angle: -2.0 * hop),
      'legR': BonePose(angle: 2.0 * hop),
      'skirt': BonePose(angle: -1.6 * env - 1.2 * hop),
      'bow': BonePose(angle: 2.0 * wave * env),
    });
  }

  RigPose _encourage(double t, double env) {
    final come = _sine(t, 1.9);
    final nod = _sine(t, 3.8);
    return RigPose(<String, BonePose>{
      'body': BonePose(angle: 1.1 * env + 0.4 * nod * env),
      'neck': BonePose(angle: 2.6 * env + 1.6 * nod * env),
      'armRUpper': BonePose(angle: -4.5 * env - 1.5 * come * env),
      'armRFore': BonePose(angle: 9.0 * env + 6.0 * come * env),
      'armRHand': BonePose(angle: 5.0 * env + 8.0 * come * env),
      'basket': BonePose(angle: -2.4 * env + 2.0 * nod * env),
      'hairBack': BonePose(angle: 1.6 * env + 1.2 * come * env),
      'hairFrontL': BonePose(angle: 1.0 * env),
    });
  }

  RigPose _sad(double t, double env) {
    final slow = _sine(t, 6.4);
    return RigPose(<String, BonePose>{
      'body': BonePose(angle: 2.6 * env + 0.3 * slow * env),
      'neck': BonePose(angle: 7.5 * env + 0.8 * slow * env, dy: 5.0 * env),
      'armRUpper': BonePose(angle: 6.0 * env),
      'armRFore': BonePose(angle: -5.0 * env),
      'armRHand': BonePose(angle: -4.0 * env),
      'armLUpper': BonePose(angle: 3.0 * env),
      'armLFore': BonePose(angle: -2.0 * env),
      'basket': BonePose(angle: -3.5 * env + 1.0 * slow * env),
      'hairBack': BonePose(angle: 2.2 * env),
      'hairFrontL': BonePose(angle: -1.4 * env),
      'hairFrontR': BonePose(angle: -1.0 * env),
      'skirt': BonePose(angle: 0.8 * env),
    });
  }

  RigPose _harvest(double t, double env) {
    final pick = _sine(t, 1.5);
    final settle = _sine(t, 3.0);
    return RigPose(<String, BonePose>{
      'body': BonePose(angle: 1.8 * env + 0.5 * settle * env),
      'neck': BonePose(angle: 5.0 * env + 1.2 * settle * env, dy: 3.0 * env),
      'armLUpper': BonePose(angle: -6.0 * env - 1.6 * pick * env),
      'armLFore': BonePose(angle: 5.0 * env + 2.4 * pick * env),
      'armLHand': BonePose(angle: 2.0 * env),
      'basket': BonePose(angle: -7.0 * env + 3.2 * pick * env),
      'armRUpper': BonePose(angle: 2.0 * env),
      'armRFore': BonePose(angle: 5.0 * env + 4.0 * pick * env),
      'armRHand': BonePose(angle: 3.0 * env + 5.0 * pick * env),
      'hairBack': BonePose(angle: 1.8 * env + 1.0 * pick * env),
      'hairFrontL': BonePose(angle: -1.0 * env),
      'skirt': BonePose(angle: 0.5 * env),
    });
  }

  /// Closed-eye amount for [seconds] on [mood]: 0 = open, 1 = fully closed.
  ///
  /// Blinks are scheduled deterministically (2.6 s – 6.6 s apart, with the odd
  /// double blink) so tests and the gallery can reproduce any frame.
  double blink(OrchardGirlMood mood, double seconds, {bool reduceMotion = false}) {
    if (reduceMotion || !blinkingMoods.contains(mood)) return 0;
    _ensureSchedule(seconds);
    for (var i = 0; i < _blinkStarts.length; i++) {
      final start = _blinkStarts[i];
      final t = seconds - start;
      if (t < 0) break;
      const close = 0.07;
      const hold = 0.04;
      const open = 0.11;
      if (t < close) return t / close;
      if (t < close + hold) return 1;
      if (t < close + hold + open) {
        return 1 - (t - close - hold) / open;
      }
    }
    return 0;
  }

  void _ensureSchedule(double seconds) {
    var cursor = _blinkStarts.isEmpty ? 2.6 : _blinkStarts.last;
    while (cursor <= seconds + 8) {
      if (_blinkStarts.isEmpty) {
        _blinkStarts.add(cursor);
      } else {
        cursor = _blinkStarts.last + _interval();
        _blinkStarts.add(cursor);
      }
      if (_blinkStarts.length > 64) break;
    }
  }

  double _interval() {
    _seed = (_seed * 1103515245 + 12345) & 0x7fffffff;
    final unit = (_seed % 1000) / 1000.0;
    if (unit > 0.88) return 0.24;
    return 2.6 + unit * 4.0;
  }

  /// How many blinks have started by [seconds]; handy for tests and QA.
  int blinkCount(double seconds) {
    _ensureSchedule(seconds);
    return _blinkStarts.where((start) => start <= seconds).length;
  }
}
