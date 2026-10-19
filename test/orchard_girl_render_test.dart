import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:flutter_test/flutter_test.dart';
import 'package:puzzle/ui/widgets/orchard_girl.dart';

/// Raster-level checks of the real `CustomPainter` output.
///
/// These run in plain `flutter test` (no goldens, no emulator): each pose is
/// painted into a RepaintBoundary at the native 512x768 canvas size and the
/// result is compared pixel by pixel with the original artwork.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late OrchardGirlImages images;

  setUpAll(() async {
    images = await OrchardGirlImages.acquire();
  });
  tearDownAll(OrchardGirlImages.release);

  const width = 512;
  const height = 768;

  Future<ByteData> render(WidgetTester tester, OrchardGirlPose pose) async {
    tester.view.physicalSize = Size(width.toDouble(), height.toDouble());
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    final key = GlobalKey();
    await tester.pumpWidget(
      Directionality(
        textDirection: TextDirection.ltr,
        child: Center(
          child: RepaintBoundary(
            key: key,
            child: SizedBox(
              width: width.toDouble(),
              height: height.toDouble(),
              child: CustomPaint(
                painter: OrchardGirlPainter(images: images, pose: pose),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pump();

    late ByteData data;
    await tester.runAsync(() async {
      final boundary =
          key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
      final image = await boundary.toImage();
      data = (await image.toByteData(format: ui.ImageByteFormat.rawRgba))!;
      image.dispose();
    });
    return data;
  }

  /// Real I/O, so the caller must run this inside `tester.runAsync`.
  Future<ByteData> reference(String name) async {
    final value = await rootBundle.load('assets/game/characters/$name.png');
    final codec = await ui.instantiateImageCodec(value.buffer.asUint8List());
    final frame = await codec.getNextFrame();
    final data =
        (await frame.image.toByteData(format: ui.ImageByteFormat.rawRgba))!;
    frame.image.dispose();
    codec.dispose();
    return data;
  }

  testWidgets('rest pose reproduces the original idle artwork', (tester) async {
    final painted =
        (await render(tester, OrchardGirlAnimator.poseAt('idle', 0)))
            .buffer
            .asUint8List();
    final source = (await tester.runAsync(
      () => reference('orchard_girl_idle'),
    ))!;
    final sourceBytes = source.buffer.asUint8List();

    expect(painted.length, sourceBytes.length);

    var sum = 0.0;
    var far = 0;
    var compared = 0;
    var paintedOpaque = 0;
    var sourceOpaque = 0;
    for (var i = 0; i < painted.length; i += 4) {
      final paintedAlpha = painted[i + 3];
      final sourceAlpha = sourceBytes[i + 3];
      if (paintedAlpha > 8) paintedOpaque++;
      if (sourceAlpha > 8) sourceOpaque++;
      // Colour under a fully transparent pixel is arbitrary; only compare
      // alpha there, never RGB.
      final first = paintedAlpha > 8 || sourceAlpha > 8 ? 0 : 3;
      for (var c = first; c < 4; c++) {
        final delta = (painted[i + c] - sourceBytes[i + c]).abs();
        sum += delta;
        compared++;
        if (delta > 30) far++;
      }
    }
    final mean = sum / compared;
    // The rig is built from the same pixels, so a correct draw is essentially
    // identical; a wrong pivot, order or missing layer breaks these bounds.
    expect(sourceOpaque, greaterThan(100000));
    expect(
      (paintedOpaque - sourceOpaque).abs() / sourceOpaque,
      lessThan(0.01),
      reason: 'layer coverage must match the original art',
    );
    // Skia resamples the layers with mipmapped filtering even at 1:1, so the
    // silhouette picks up a thin antialiased fringe. Anything structural
    // (wrong pivot, order, missing layer) changes tens of thousands of
    // channels, far beyond this bound.
    expect(mean, lessThan(6.0), reason: 'mean per-channel difference');
    expect(far / sourceOpaque, lessThan(0.045),
        reason: 'far-off pixels ($far channel deltas over 30)');
  });

  testWidgets('blinking only repaints the eye layers', (tester) async {
    final open =
        (await render(tester, OrchardGirlAnimator.poseAt('idle', 0)))
            .buffer
            .asUint8List();
    final blinkPose = OrchardGirlAnimator.poseAt('idle', 0, blink: 1);
    expect(blinkPose.visibleEyes, 'closed');
    final closed = (await render(tester, blinkPose)).buffer.asUint8List();

    final eye = _union(const [
      Rect.fromLTWH(149, 145, 69, 65),
      Rect.fromLTWH(213, 143, 70, 65),
    ]).inflate(1);

    var changedInside = 0;
    var changedOutside = 0;
    for (var y = 0; y < height; y++) {
      for (var x = 0; x < width; x++) {
        final i = (y * width + x) * 4;
        final different = open[i] != closed[i] ||
            open[i + 1] != closed[i + 1] ||
            open[i + 2] != closed[i + 2] ||
            open[i + 3] != closed[i + 3];
        if (!different) continue;
        if (eye.contains(Offset(x.toDouble(), y.toDouble()))) {
          changedInside++;
        } else {
          changedOutside++;
        }
      }
    }
    expect(changedInside, greaterThan(200), reason: 'eyes must visibly close');
    expect(changedOutside, 0,
        reason: 'no other pixel may change during a blink');
  });

  testWidgets('expression swaps stay inside the face layers', (tester) async {
    final smile =
        (await render(tester, OrchardGirlAnimator.poseAt('idle', 0)))
            .buffer
            .asUint8List();
    // Face only: no joint motion, so any change outside the face rectangles
    // would mean the expression swap is leaking into the body.
    final sad = (await render(
      tester,
      const OrchardGirlPose(eyes: 'soft', brows: 'sad', mouth: 'sad'),
    ))
        .buffer
        .asUint8List();

    final face = _union(const [
      Rect.fromLTWH(144, 114, 79, 43), // brows
      Rect.fromLTWH(210, 112, 79, 43),
      Rect.fromLTWH(174, 198, 87, 63), // mouth
    ]).inflate(1);
    final head = _union(const [
      Rect.fromLTWH(149, 145, 69, 65), // eyes
      Rect.fromLTWH(213, 143, 70, 65),
      Rect.fromLTWH(108, 76, 184, 109), // front hair
      Rect.fromLTWH(67, 126, 87, 133), // side locks
      Rect.fromLTWH(248, 122, 47, 134),
    ]).inflate(1);

    var changedInsideFace = 0;
    var changedOutsideFace = 0;
    var changedOutsideHead = 0;
    for (var y = 0; y < height; y++) {
      for (var x = 0; x < width; x++) {
        final i = (y * width + x) * 4;
        if (smile[i] == sad[i] &&
            smile[i + 1] == sad[i + 1] &&
            smile[i + 2] == sad[i + 2] &&
            smile[i + 3] == sad[i + 3]) {
          continue;
        }
        final p = Offset(x.toDouble(), y.toDouble());
        if (face.contains(p)) changedInsideFace++;
        if (!face.contains(p)) changedOutsideFace++;
        if (!head.contains(p)) changedOutsideHead++;
      }
    }
    expect(changedInsideFace, greaterThan(200),
        reason: 'brows and mouth must change');
    expect(changedOutsideFace, 0,
        reason: 'an expression swap may not touch hair, skin or clothes');
    expect(changedOutsideHead, 0,
        reason: 'nothing outside the face may move for an expression change');
  });

  testWidgets('rotating an arm leaves the head untouched', (tester) async {
    final rest =
        (await render(tester, OrchardGirlAnimator.poseAt('idle', 0)))
            .buffer
            .asUint8List();
    const pose = OrchardGirlPose(
      angles: {OrchardGirlJoints.shoulderRight: 35},
    );
    final moved = (await render(tester, pose)).buffer.asUint8List();

    final head = _union(const [
      Rect.fromLTWH(149, 145, 69, 65),
      Rect.fromLTWH(213, 143, 70, 65),
      Rect.fromLTWH(144, 114, 79, 43),
      Rect.fromLTWH(210, 112, 79, 43),
      Rect.fromLTWH(174, 198, 87, 63),
      Rect.fromLTWH(108, 76, 184, 109),
      Rect.fromLTWH(67, 126, 87, 133),
      Rect.fromLTWH(248, 122, 47, 134),
    ]).inflate(1);

    var headChanges = 0;
    var bodyChanges = 0;
    for (var y = 0; y < height; y++) {
      for (var x = 0; x < width; x++) {
        final i = (y * width + x) * 4;
        if (rest[i] == moved[i] &&
            rest[i + 1] == moved[i + 1] &&
            rest[i + 2] == moved[i + 2] &&
            rest[i + 3] == moved[i + 3]) {
          continue;
        }
        if (head.contains(Offset(x.toDouble(), y.toDouble()))) {
          headChanges++;
        } else {
          bodyChanges++;
        }
      }
    }
    expect(bodyChanges, greaterThan(500), reason: 'the arm really moved');
    expect(headChanges, 0, reason: 'no parent transform may reach the head');
  });

}

Rect _union(List<Rect> rects) {
  var result = rects.first;
  for (final rect in rects.skip(1)) {
    result = result.expandToInclude(rect);
  }
  return result;
}
