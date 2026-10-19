import 'dart:ui' show Offset;

import 'package:flutter/foundation.dart' show immutable;

/// Joint names used by the rig. They mirror `joints` in the generated
/// `assets/game/characters/rig/manifest.json`; a test asserts both agree.
abstract final class OrchardGirlJoints {
  static const String root = 'root';
  static const String hip = 'hip';
  static const String apron = 'apron';
  static const String neck = 'neck';
  static const String head = 'head';
  static const String bangs = 'bangs';
  static const String lockLeft = 'lockLeft';
  static const String lockRight = 'lockRight';
  static const String shoulderLeft = 'shoulderLeft';
  static const String shoulderRight = 'shoulderRight';
  static const String elbowRight = 'elbowRight';
  static const String basketLeft = 'basket';
}

/// Parent chain of the joints. Rotating a joint moves everything below it,
/// which is what keeps limbs attached at their real pivots.
const Map<String, String?> orchardGirlJointParents = <String, String?>{
  OrchardGirlJoints.root: null,
  OrchardGirlJoints.hip: OrchardGirlJoints.root,
  OrchardGirlJoints.apron: OrchardGirlJoints.hip,
  OrchardGirlJoints.neck: OrchardGirlJoints.hip,
  OrchardGirlJoints.head: OrchardGirlJoints.neck,
  OrchardGirlJoints.bangs: OrchardGirlJoints.head,
  OrchardGirlJoints.lockLeft: OrchardGirlJoints.head,
  OrchardGirlJoints.lockRight: OrchardGirlJoints.head,
  OrchardGirlJoints.shoulderLeft: OrchardGirlJoints.hip,
  OrchardGirlJoints.shoulderRight: OrchardGirlJoints.hip,
  OrchardGirlJoints.elbowRight: OrchardGirlJoints.shoulderRight,
  OrchardGirlJoints.basketLeft: OrchardGirlJoints.shoulderLeft,
};

/// Which joint drives each layer (order comes from the manifest).
const Map<String, String> orchardGirlLayerJoints = <String, String>{
  'hair_back': OrchardGirlJoints.head,
  'face_base': OrchardGirlJoints.head,
  'eyes': OrchardGirlJoints.head,
  'brows': OrchardGirlJoints.head,
  'mouth': OrchardGirlJoints.head,
  'hair_front': OrchardGirlJoints.bangs,
  'lock_left': OrchardGirlJoints.lockLeft,
  'lock_right': OrchardGirlJoints.lockRight,
  'torso': OrchardGirlJoints.hip,
  'apron_tail': OrchardGirlJoints.apron,
  'legs': OrchardGirlJoints.root,
  'arm_left': OrchardGirlJoints.shoulderLeft,
  'arm_right_up': OrchardGirlJoints.shoulderRight,
  'arm_right_fore': OrchardGirlJoints.elbowRight,
  'basket_bowl': OrchardGirlJoints.basketLeft,
  'basket_handle': OrchardGirlJoints.basketLeft,
};

/// One frame of the rig: a rotation per joint plus small body offsets.
///
/// Angles are degrees; positive is clockwise on screen. Offsets are in
/// canvas pixels (512 x 768) and are scaled with the widget.
class OrchardGirlPose {
  const OrchardGirlPose({
    this.angles = const <String, double>{},
    this.offsets = const <String, Offset>{},
    this.rootOffset = Offset.zero,
    this.rootScale = 1,
    this.blink = 0,
    this.eyes = 'open',
    this.brows = 'neutral',
    this.mouth = 'smile',
    this.previousEyes,
    this.previousBrows,
    this.previousMouth,
    this.faceBlend = 1,
  });

  final Map<String, double> angles;
  final Map<String, Offset> offsets;
  final Offset rootOffset;
  final double rootScale;

  /// 0 = eyes open, 1 = fully closed. Only the eye layer reacts.
  final double blink;
  final String eyes;
  final String brows;
  final String mouth;

  /// Outgoing expression while a mood change is still blending.
  final String? previousEyes;
  final String? previousBrows;
  final String? previousMouth;
  final double faceBlend;

  double angle(String joint) => angles[joint] ?? 0;
  Offset offset(String joint) => offsets[joint] ?? Offset.zero;

  /// True when the eyelid art should be used instead of the mood's eyes.
  bool get eyesClosed => blink > 0.5;

  /// Expression actually drawn this frame.
  String get visibleEyes => eyesClosed ? 'closed' : eyes;

  static OrchardGirlPose lerp(OrchardGirlPose a, OrchardGirlPose b, double t) {
    if (t >= 1) return b;
    t = t.clamp(0.0, 1.0);
    final joints = <String>{...a.angles.keys, ...b.angles.keys};
    final angles = <String, double>{};
    for (final joint in joints) {
      angles[joint] = _mix(a.angle(joint), b.angle(joint), t);
    }
    final nodes = <String>{...a.offsets.keys, ...b.offsets.keys};
    final offsets = <String, Offset>{};
    for (final joint in nodes) {
      offsets[joint] = Offset.lerp(a.offset(joint), b.offset(joint), t)!;
    }
    return OrchardGirlPose(
      angles: angles,
      offsets: offsets,
      rootOffset: Offset.lerp(a.rootOffset, b.rootOffset, t)!,
      rootScale: _mix(a.rootScale, b.rootScale, t),
      blink: _mix(a.blink, b.blink, t),
      eyes: t < 0.5 ? a.visibleEyes : b.visibleEyes,
      brows: t < 0.5 ? a.brows : b.brows,
      mouth: t < 0.5 ? a.mouth : b.mouth,
      previousEyes: a.visibleEyes,
      previousBrows: a.brows,
      previousMouth: a.mouth,
      faceBlend: t,
    );
  }

  static double _mix(double a, double b, double t) => a + (b - a) * t;

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is OrchardGirlPose &&
        _sameAngles(other) &&
        _sameOffsets(other) &&
        other.rootOffset == rootOffset &&
        other.rootScale == rootScale &&
        other.blink == blink &&
        other.visibleEyes == visibleEyes &&
        other.brows == brows &&
        other.mouth == mouth &&
        other.previousEyes == previousEyes &&
        other.faceBlend == faceBlend;
  }

  bool _sameAngles(OrchardGirlPose other) {
    final joints = <String>{...angles.keys, ...other.angles.keys};
    for (final joint in joints) {
      if (angle(joint) != other.angle(joint)) return false;
    }
    return true;
  }

  bool _sameOffsets(OrchardGirlPose other) {
    final joints = <String>{...offsets.keys, ...other.offsets.keys};
    for (final joint in joints) {
      if (offset(joint) != other.offset(joint)) return false;
    }
    return true;
  }

  @override
  int get hashCode => Object.hash(
        Object.hashAllUnordered(
          angles.entries.map((e) => '${e.key}:${e.value}'),
        ),
        Object.hashAllUnordered(
          offsets.entries.map((e) => '${e.key}:${e.value}'),
        ),
        rootOffset,
        rootScale,
        blink,
        visibleEyes,
        brows,
        mouth,
        previousEyes,
        faceBlend,
      );
}

/// Expression triple used by a mood (and by the blink override).
@immutable
class OrchardGirlFace {
  const OrchardGirlFace({
    required this.eyes,
    required this.brows,
    required this.mouth,
  });

  final String eyes;
  final String brows;
  final String mouth;
}
