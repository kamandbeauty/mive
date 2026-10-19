import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';

import 'orchard_girl_assets.dart';
import 'orchard_girl_rig.dart';

/// Draws the layered rig with one canvas transform per joint.
///
/// Layers come straight from the manifest order, so hair, hands, face and
/// basket always sit above the body parts they overlap.
class OrchardGirlPainter extends CustomPainter {
  OrchardGirlPainter({
    required this.images,
    required this.pose,
    super.repaint,
  });

  final OrchardGirlImages images;
  final OrchardGirlPose pose;

  @override
  void paint(Canvas canvas, Size size) {
    final data = images.data;
    final scale = math.min(
      size.width / data.canvasWidth,
      size.height / data.canvasHeight,
    );
    if (scale <= 0) return;

    // Bottom-centre inside the box, exactly like the old BoxFit.contain art.
    final dx = (size.width - data.canvasWidth * scale) / 2;
    final dy = size.height - data.canvasHeight * scale;

    canvas.save();
    canvas.translate(dx, dy);
    canvas.scale(scale);

    final paint = Paint()
      ..filterQuality = FilterQuality.medium
      ..isAntiAlias = true;

    final world = _worldMatrices(data);

    for (final entry in data.order) {
      switch (entry) {
        case 'eyes':
          _drawFacePart(
            canvas,
            data,
            world,
            group: 'eyes',
            variant: pose.visibleEyes,
            previous: pose.previousEyes,
            blend: pose.faceBlend,
            paint: paint,
          );
        case 'brows':
          _drawFacePart(
            canvas,
            data,
            world,
            group: 'brows',
            variant: pose.brows,
            previous: pose.previousBrows,
            blend: pose.faceBlend,
            paint: paint,
          );
        case 'mouth':
          _drawFacePart(
            canvas,
            data,
            world,
            group: 'mouth',
            variant: pose.mouth,
            previous: pose.previousMouth,
            blend: pose.faceBlend,
            paint: paint,
            single: true,
          );
        default:
          _drawLayer(canvas, data, world, entry, paint);
      }
    }
    canvas.restore();
  }

  /// World transform per joint: parents first, each rotation around the joint
  /// pivot taken from the manifest.
  Map<String, Matrix4> _worldMatrices(OrchardGirlRigData data) {
    final matrices = <String, Matrix4>{};
    Matrix4 resolve(String joint) {
      final cached = matrices[joint];
      if (cached != null) return cached;
      final parent = orchardGirlJointParents[joint];
      final own = _jointMatrix(data, joint);
      final matrix = parent == null
          ? own
          : resolve(parent).multiplied(own);
      matrices[joint] = matrix;
      return matrix;
    }

    for (final joint in orchardGirlJointParents.keys) {
      resolve(joint);
    }
    return matrices;
  }

  Matrix4 _jointMatrix(OrchardGirlRigData data, String joint) {
    final pivot = data.joints[joint] ?? ui.Offset.zero;
    final angle = pose.angle(joint) * math.pi / 180;
    final offset = pose.offset(joint);
    final matrix = Matrix4.identity();
    if (joint == OrchardGirlJoints.root) {
      final rootPivot = data.joints[OrchardGirlJoints.root] ?? ui.Offset.zero;
      matrix
        ..translateByDouble(
          rootPivot.dx + pose.rootOffset.dx,
          rootPivot.dy + pose.rootOffset.dy,
          0,
          1,
        )
        ..scaleByDouble(pose.rootScale, pose.rootScale, 1, 1)
        ..translateByDouble(-rootPivot.dx, -rootPivot.dy, 0, 1);
      return matrix;
    }
    if (angle == 0 && offset == ui.Offset.zero) return matrix;
    matrix
      ..translateByDouble(pivot.dx + offset.dx, pivot.dy + offset.dy, 0, 1)
      ..rotateZ(angle)
      ..translateByDouble(-pivot.dx, -pivot.dy, 0, 1);
    return matrix;
  }

  void _drawLayer(
    Canvas canvas,
    OrchardGirlRigData data,
    Map<String, Matrix4> world,
    String name,
    Paint paint,
  ) {
    final image = images.layer(name);
    final rect = data.layers[name];
    final joint = orchardGirlLayerJoints[name];
    if (image == null || rect == null) return;
    final matrix = world[joint ?? OrchardGirlJoints.root] ?? Matrix4.identity();
    canvas.save();
    canvas.transform(matrix.storage);
    canvas.drawImageRect(
      image,
      ui.Rect.fromLTWH(0, 0, image.width.toDouble(), image.height.toDouble()),
      ui.Rect.fromLTWH(rect.x, rect.y, rect.width, rect.height),
      paint,
    );
    canvas.restore();
  }

  void _drawFacePart(
    Canvas canvas,
    OrchardGirlRigData data,
    Map<String, Matrix4> world, {
    required String group,
    required String variant,
    required String? previous,
    required double blend,
    required Paint paint,
    bool single = false,
  }) {
    final joints = world[OrchardGirlJoints.head] ?? Matrix4.identity();
    canvas.save();
    canvas.transform(joints.storage);
    final crossFade = previous != null && previous != variant && blend < 1;
    if (crossFade) {
      _drawVariant(canvas, data, group, previous, paint, 1 - blend, single);
      _drawVariant(canvas, data, group, variant, paint, blend, single);
    } else {
      _drawVariant(canvas, data, group, variant, paint, 1, single);
    }
    canvas.restore();
  }

  void _drawVariant(
    Canvas canvas,
    OrchardGirlRigData data,
    String group,
    String variant,
    Paint paint,
    double opacity,
    bool single,
  ) {
    final sides = single ? const <String>[''] : const <String>['left', 'right'];
    for (final side in sides) {
      final key = side.isEmpty
          ? '${group}_$variant'
          : data.faceAsset(group, variant, side);
      final image = images.layer(key);
      final rect = data.layers[key];
      if (image == null || rect == null) continue;
      final layerPaint = opacity >= 1
          ? paint
          : (Paint()
            ..filterQuality = FilterQuality.medium
            ..isAntiAlias = true
            ..color = ui.Color.fromRGBO(255, 255, 255, opacity));
      canvas.drawImageRect(
        image,
        ui.Rect.fromLTWH(0, 0, image.width.toDouble(), image.height.toDouble()),
        ui.Rect.fromLTWH(rect.x, rect.y, rect.width, rect.height),
        layerPaint,
      );
    }
  }

  @override
  bool shouldRepaint(OrchardGirlPainter oldDelegate) =>
      oldDelegate.pose != pose || oldDelegate.images != images;
}
