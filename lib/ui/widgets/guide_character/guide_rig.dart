import 'dart:convert';
import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart' show Listenable;
import 'package:flutter/painting.dart';
import 'package:flutter/rendering.dart' show CustomPainter;
import 'package:flutter/services.dart' show rootBundle;

// Jointed renderer for the orchard guide.
//
// The art is the delivered doll-maker set: 31 independent body/face layers on
// one 1152x1152 canvas. `tools/character_rig/build_rig_assets.py` only crops
// those layers and bakes the joint sockets; it never repaints the character.
// This file loads that package and draws it as a hierarchy of bones, so every
// visible motion is a rotation/translation of a real body part around a real
// joint.

const String _assetRoot = 'assets/characters/rig';

/// A 2D affine transform (canvas convention: x' = a·x + c·y + tx).
class Affine {
  const Affine(this.a, this.b, this.c, this.d, this.tx, this.ty);

  static const Affine identity = Affine(1, 0, 0, 1, 0, 0);

  final double a;
  final double b;
  final double c;
  final double d;
  final double tx;
  final double ty;

  /// `this ∘ child` — the child transform happens in this transform's space.
  Affine followedBy(Affine child) => Affine(
        a * child.a + c * child.b,
        b * child.a + d * child.b,
        a * child.c + c * child.d,
        b * child.c + d * child.d,
        a * child.tx + c * child.ty + tx,
        b * child.tx + d * child.ty + ty,
      );

  Float64List get storage => Float64List.fromList(<double>[
        a, b, 0, 0, //
        c, d, 0, 0, //
        0, 0, 1, 0, //
        tx, ty, 0, 1, //
      ]);
}

/// Target transform of one bone for a single frame.
class BonePose {
  const BonePose({this.angle = 0, this.dx = 0, this.dy = 0, this.scale = 1});

  /// Degrees, clockwise on screen.
  final double angle;
  final double dx;
  final double dy;
  final double scale;

  BonePose lerpTo(BonePose other, double t) => BonePose(
        angle: angle + (other.angle - angle) * t,
        dx: dx + (other.dx - dx) * t,
        dy: dy + (other.dy - dy) * t,
        scale: scale + (other.scale - scale) * t,
      );
}

/// Full-body pose: bone id → transform.
class RigPose {
  const RigPose(this.bones);

  static const RigPose rest = RigPose(<String, BonePose>{});

  final Map<String, BonePose> bones;

  BonePose bone(String id) => bones[id] ?? const BonePose();

  RigPose blend(RigPose other, double t) {
    final ids = <String>{...bones.keys, ...other.bones.keys};
    return RigPose(<String, BonePose>{
      for (final id in ids) bone(id).lerpTo(other.bone(id), t),
    });
  }

  RigPose copyWith(String id, BonePose pose) =>
      RigPose(<String, BonePose>{...bones, id: pose});
}

class RigLayer {
  RigLayer({
    required this.id,
    required this.image,
    required this.rect,
    required this.bone,
    required this.z,
  });

  final String id;
  final ui.Image image;
  final Rect rect;
  final String bone;
  final double z;
}

class RigBone {
  const RigBone({required this.id, required this.parent, required this.pivot});

  final String id;
  final String? parent;
  final Offset pivot;
}

/// Immutable, process-wide rig package: manifests, geometry and decoded
/// bitmaps. Decoding happens exactly once per run.
class GuideRig {
  GuideRig._({
    required this.bones,
    required this.layers,
    required this.faceSets,
    required this.blinkSets,
    required this.canvas,
    required this.bodyRect,
  });

  final Map<String, RigBone> bones;

  /// Body layers and joint sockets, already sorted by paint order.
  final List<RigLayer> layers;

  /// mood → semantic part → layer ids (eyes/brows/mouth/blush).
  final Map<String, Map<String, List<String>>> faceSets;

  /// "full" / "left" / "right" → the closed-eye plates.
  final Map<String, Map<String, List<String>>> blinkSets;

  final Size canvas;

  /// Alpha bounds of the whole figure inside [canvas].
  final Rect bodyRect;

  final Map<String, RigLayer> _byId = <String, RigLayer>{};

  static GuideRig? _cached;
  static Future<GuideRig>? _loading;

  static Future<GuideRig> load() {
    final cached = _cached;
    if (cached != null) return Future<GuideRig>.value(cached);
    return _loading ??= _load();
  }

  static Future<GuideRig> _load() async {
    try {
      final raw = await rootBundle.loadString('$_assetRoot/manifest.json');
      final json = jsonDecode(raw) as Map<String, dynamic>;
      final decoded = <String, ui.Image>{};

      Future<ui.Image> image(String file) async {
        final cached = decoded[file];
        if (cached != null) return cached;
        final data = await rootBundle.load('$_assetRoot/$file');
        final codec = await ui.instantiateImageCodec(data.buffer.asUint8List());
        final frame = await codec.getNextFrame();
        codec.dispose();
        decoded[file] = frame.image;
        return frame.image;
      }

      final bones = <String, RigBone>{};
      for (final entry in (json['bones'] as Map<String, dynamic>).entries) {
        final value = entry.value as Map<String, dynamic>;
        final pivot = (value['pivot'] as List<dynamic>).cast<num>();
        bones[entry.key] = RigBone(
          id: entry.key,
          parent: value['parent'] as String?,
          pivot: Offset(pivot[0].toDouble(), pivot[1].toDouble()),
        );
      }

      final items = <RigLayer>[];
      for (final entry in (json['layers'] as Map<String, dynamic>).entries) {
        final value = entry.value as Map<String, dynamic>;
        final rect = _rect(value['rect']);
        items.add(
          RigLayer(
            id: entry.key,
            image: await image(value['file'] as String),
            rect: rect,
            bone: value['bone'] as String,
            z: (value['z'] as num).toDouble(),
          ),
        );
      }
      for (final value in (json['sockets'] as List<dynamic>)) {
        final entry = value as Map<String, dynamic>;
        items.add(
          RigLayer(
            id: entry['id'] as String,
            image: await image(entry['file'] as String),
            rect: _rect(entry['rect']),
            bone: entry['bone'] as String,
            z: (entry['z'] as num).toDouble(),
          ),
        );
      }
      items.sort((a, b) => a.z.compareTo(b.z));

      final faceSets = <String, Map<String, List<String>>>{};
      for (final entry in (json['faces'] as Map<String, dynamic>).entries) {
        final parts = entry.value as Map<String, dynamic>;
        faceSets[entry.key] = <String, List<String>>{
          for (final part in parts.entries)
            part.key: (part.value as List<dynamic>).cast<String>(),
        };
      }
      final blinkSets = <String, Map<String, List<String>>>{};
      for (final entry in (json['blink'] as Map<String, dynamic>).entries) {
        final sides = entry.value as Map<String, dynamic>;
        blinkSets[entry.key] = <String, List<String>>{
          for (final side in sides.entries)
            side.key: (side.value as List<dynamic>).cast<String>(),
        };
      }

      final canvasEdge = (json['canvas'] as num).toDouble();
      final rig = GuideRig._(
        bones: bones,
        layers: items,
        faceSets: faceSets,
        blinkSets: blinkSets,
        canvas: Size(canvasEdge, canvasEdge),
        bodyRect: _boundsOf(items),
      );
      for (final layer in items) {
        rig._byId[layer.id] = layer;
      }
      _cached = rig;
      return rig;
    } catch (_) {
      _loading = null;
      rethrow;
    }
  }

  static Rect _rect(dynamic value) {
    final values = (value as List<dynamic>).cast<num>();
    return Rect.fromLTWH(
      values[0].toDouble(),
      values[1].toDouble(),
      values[2].toDouble(),
      values[3].toDouble(),
    );
  }

  /// Bounds of the figure, ignoring the face plates (they sit inside the head).
  static Rect _boundsOf(List<RigLayer> layers) {
    var rect = Rect.zero;
    var first = true;
    for (final layer in layers) {
      if (layer.id.startsWith('face-')) continue;
      rect = first ? layer.rect : rect.expandToInclude(layer.rect);
      first = false;
    }
    return rect;
  }

  RigLayer? layer(String id) => _byId[id];

  /// World matrix for [bone] under [pose], composed through its ancestors.
  Affine matrixFor(String boneId, RigPose pose, [Map<String, Affine>? cache]) {
    final memo = cache ?? <String, Affine>{};
    final cached = memo[boneId];
    if (cached != null) return cached;
    final bone = bones[boneId];
    if (bone == null) return Affine.identity;
    final pose_ = pose.bone(boneId);
    final pivot = bone.pivot;
    final radians = pose_.angle * math.pi / 180;
    final cos = math.cos(radians) * pose_.scale;
    final sin = math.sin(radians) * pose_.scale;
    final px = pivot.dx;
    final py = pivot.dy;
    // Translate the pivot to the origin, rotate/scale, then put it back and
    // apply the bone's own offset.
    final local = Affine(
      cos,
      sin,
      -sin,
      cos,
      px - cos * px + sin * py + pose_.dx,
      py - sin * px - cos * py + pose_.dy,
    );
    final parent = bone.parent;
    final result =
        parent == null ? local : matrixFor(parent, pose, memo).followedBy(local);
    memo[boneId] = result;
    return result;
  }
}

/// Mutable per-frame values the painter reads on every tick.
///
/// Keeping these outside the widget means a 60 fps tick only repaints; it never
/// rebuilds the element tree.
class GuideRigFrame {
  String mood = 'happy';
  RigPose pose = RigPose.rest;
  double blink = 0;

  void update({String? mood, RigPose? pose, double? blink}) {
    if (mood != null) this.mood = mood;
    if (pose != null) this.pose = pose;
    if (blink != null) this.blink = blink;
  }
}

/// Draws [rig] under the current [frame]. The canvas is the rig's own 1152 px
/// space, mapped to the widget with [scale] and [offset].
class GuideRigPainter extends CustomPainter {
  GuideRigPainter({
    required this.rig,
    required this.frame,
    required this.scale,
    required this.offset,
    Listenable? repaint,
  }) : super(repaint: repaint);

  final GuideRig rig;
  final GuideRigFrame frame;
  final double scale;
  final Offset offset;

  String get mood => frame.mood;
  RigPose get pose => frame.pose;
  double get blink => frame.blink;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..filterQuality = FilterQuality.medium
      ..isAntiAlias = true;
    final matrices = <String, Affine>{};
    final visibleFace = <String>{
      for (final ids in (rig.faceSets[mood] ?? const <String, List<String>>{}).values)
        ...ids,
    };
    final moodIsRest = mood == 'happy';

    canvas.save();
    canvas.translate(offset.dx, offset.dy);
    canvas.scale(scale);

    for (final layer in rig.layers) {
      final isExpressionPlate = layer.id.startsWith('face-');
      final isRestPlate = _restFaceIds.contains(layer.id);
      if (isExpressionPlate && !visibleFace.contains(layer.id)) continue;
      if (isRestPlate && !moodIsRest) continue;
      canvas.save();
      canvas.transform(rig.matrixFor(layer.bone, pose, matrices).storage);
      canvas.drawImageRect(
        layer.image,
        Rect.fromLTWH(0, 0, layer.image.width.toDouble(), layer.image.height.toDouble()),
        layer.rect,
        paint,
      );
      canvas.restore();
    }

    if (blink > 0.02) {
      final plates = (rig.blinkSets['full'] ?? const <String, List<String>>{})['left'] ??
          const <String>[];
      final closed = <String>{
        ...plates,
        ...((rig.blinkSets['full'] ?? const <String, List<String>>{})['right'] ??
            const <String>[]),
      };
      final alphaPaint = Paint()
        ..filterQuality = FilterQuality.medium
        ..isAntiAlias = true
        ..color = const Color(0xFFFFFFFF).withValues(alpha: blink.clamp(0.0, 1.0));
      for (final id in closed) {
        final layer = rig.layer(id);
        if (layer == null) continue;
        canvas.save();
        canvas.transform(rig.matrixFor(layer.bone, pose, matrices).storage);
        canvas.drawImageRect(
          layer.image,
          Rect.fromLTWH(0, 0, layer.image.width.toDouble(), layer.image.height.toDouble()),
          layer.rect,
          alphaPaint,
        );
        canvas.restore();
      }
    }

    canvas.restore();
  }

  /// Canonical face layers that belong to the delivered rest pose.
  static const Set<String> _restFaceIds = <String>{
    'brow-left',
    'brow-right',
    'eye-left',
    'eye-right',
    'lid-upper-left',
    'lid-upper-right',
    'lid-lower-left',
    'lid-lower-right',
    'mouth',
  };

  @override
  bool shouldRepaint(GuideRigPainter oldDelegate) =>
      oldDelegate.rig != rig ||
      oldDelegate.frame != frame ||
      oldDelegate.scale != scale ||
      oldDelegate.offset != offset;
}
