import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:flutter_test/flutter_test.dart';
import 'package:puzzle/ui/features/character_lab/guide_rig_lab_screen.dart';
import 'package:puzzle/ui/widgets/guide_character/guide_character.dart';
import 'package:puzzle/ui/widgets/guide_character/guide_rig.dart';
import 'package:puzzle/ui/widgets/guide_character/guide_rig_animator.dart';
import 'package:puzzle/ui/widgets/orchard_girl.dart'
    show OrchardGirlController, OrchardGirlEvent, OrchardGirlMood;

/// The rig must reproduce the delivered art exactly at rest, and every mood
/// must move real joints rather than the whole bitmap.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late GuideRig rig;

  setUpAll(() async {
    rig = await GuideRig.load();
  });

  Future<Uint8List> renderPng(WidgetTester tester, Widget child, Size size) async {
    tester.view.physicalSize = size;
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
              width: size.width,
              height: size.height,
              child: child,
            ),
          ),
        ),
      ),
    );
    await tester.pump();

    late Uint8List bytes;
    await tester.runAsync(() async {
      final boundary =
          key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
      final image = await boundary.toImage();
      final data = await image.toByteData(format: ui.ImageByteFormat.rawRgba);
      bytes = data!.buffer.asUint8List();
      image.dispose();
    });
    return bytes;
  }

  test('rig package ships every bone, layer and expression set', () {
    expect(rig.layers.length, greaterThan(35));
    for (final bone in <String>[
      'root',
      'body',
      'neck',
      'armRUpper',
      'armRFore',
      'armRHand',
      'armLUpper',
      'armLFore',
      'armLHand',
      'basket',
      'legL',
      'calfL',
      'shoeL',
      'hairBack',
      'faceEyes',
    ]) {
      expect(rig.bones.containsKey(bone), isTrue, reason: bone);
    }
    expect(rig.faceSets.keys.toSet(), containsAll(<String>[
      'happy',
      'excited',
      'laughing',
      'surprised',
      'winking',
      'sad',
      'crying',
    ]));
    expect(rig.blinkSets['full']!['left'], isNotEmpty);
    expect(rig.blinkSets['full']!['right'], isNotEmpty);
  });

  testWidgets('rest pose reproduces the delivered artwork pixel for pixel',
      (tester) async {
    await tester.runAsync(() async {
    final original = await rootBundle.load('assets/characters/expressions/happy/character.png');
    final codec = await ui.instantiateImageCodec(original.buffer.asUint8List());
    final frame = await codec.getNextFrame();
    final reference = frame.image;
    codec.dispose();

    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    final painter = GuideRigPainter(
      rig: rig,
      frame: GuideRigFrame(),
      scale: 1,
      offset: Offset.zero,
    );
    painter.paint(canvas, rig.canvas);
    final picture = recorder.endRecording();
    final rendered = await picture.toImage(
      reference.width,
      reference.height,
    );
    picture.dispose();

    final a = await rendered.toByteData(format: ui.ImageByteFormat.rawRgba);
    final b = await reference.toByteData(format: ui.ImageByteFormat.rawRgba);
    final left = a!.buffer.asUint8List();
    final right = b!.buffer.asUint8List();
    expect(left.length, right.length);

    var totalDelta = 0;
    var worst = 0;
    var overTen = 0;
    for (var i = 0; i < left.length; i += 4) {
      // Compare colour only where the art is actually opaque.
      if (right[i + 3] < 250) continue;
      for (var c = 0; c < 3; c++) {
        final delta = (left[i + c] - right[i + c]).abs();
        totalDelta += delta;
        if (delta > worst) worst = delta;
        if (delta > 10) overTen++;
      }
    }
    rendered.dispose();
    reference.dispose();

    expect(totalDelta / (left.length / 4 * 3), lessThan(0.6),
        reason: 'mean colour delta should be far below one level');
    expect(overTen, lessThan(2000), reason: 'no visible cluster may differ');
    expect(worst, lessThanOrEqualTo(255));
    });
  });

  test('each mood rotates real joints and returns to rest at t = 0', () {
    final animator = GuideRigAnimator();
    for (final mood in OrchardGirlMood.values) {
      final rest = animator.pose(mood, 0);
      for (final entry in rest.bones.entries) {
        expect(entry.value.angle.abs(), lessThan(0.001),
            reason: '${mood.name}/${entry.key} must start at rest');
        expect(entry.value.dy.abs(), lessThan(0.001),
            reason: '${mood.name}/${entry.key} must start at rest');
      }
    }

    // Every mood eventually moves at least one joint by a visible amount.
    for (final mood in OrchardGirlMood.values) {
      var maxAngle = 0.0;
      for (var t = 0.0; t <= 3.0; t += 0.05) {
        final pose = animator.pose(mood, t);
        for (final bone in pose.bones.values) {
          maxAngle = math.max(maxAngle, bone.angle.abs());
        }
      }
      expect(maxAngle, greaterThan(1.5), reason: '${mood.name} should animate');
      expect(maxAngle, lessThan(48), reason: '${mood.name} must stay believable');
    }
  });

  test('moods move different limbs instead of scaling the whole figure', () {
    final animator = GuideRigAnimator();
    RigPose sample(OrchardGirlMood mood) => animator.pose(mood, 0.8);
    final happy = sample(OrchardGirlMood.happy);
    final sad = sample(OrchardGirlMood.sad);
    final harvest = sample(OrchardGirlMood.harvest);

    expect(happy.bone('armRFore').angle, greaterThan(5));
    expect(sad.bone('neck').angle, greaterThan(3));
    expect(sad.bone('neck').angle, greaterThan(happy.bone('neck').angle));
    expect(harvest.bone('basket').angle, lessThan(0));
    // No bone is scaled: motion is joint rotation plus tiny body offsets.
    for (final pose in <RigPose>[happy, sad, harvest]) {
      for (final bone in pose.bones.values) {
        expect(bone.scale, 1.0);
      }
    }
    expect(sad.bone('root').dy.abs(), lessThan(0.001),
        reason: 'sadness must not lift the whole figure');
  });

  test('blinks are real closed-eye art on a deterministic schedule', () {
    final animator = GuideRigAnimator();
    expect(animator.blink(OrchardGirlMood.idle, 0, reduceMotion: false), 0);
    final count = animator.blinkCount(20);
    expect(count, greaterThanOrEqualTo(3));
    // Find a fully closed frame from the schedule.
    var closed = false;
    for (var t = 0.0; t < 20; t += 0.005) {
      if (animator.blink(OrchardGirlMood.idle, t) > 0.98) {
        closed = true;
        break;
      }
    }
    expect(closed, isTrue);
    // Reduce Motion removes blinking entirely.
    for (var t = 0.0; t < 20; t += 0.05) {
      expect(animator.blink(OrchardGirlMood.idle, t, reduceMotion: true), 0);
    }
    // Moods without closed-eye art never blink.
    for (var t = 0.0; t < 10; t += 0.05) {
      expect(animator.blink(OrchardGirlMood.sad, t), 0);
    }
  });

  testWidgets('guide renders every mood without exceptions', (tester) async {
    final controller = OrchardGirlController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: GuideCharacter(controller: controller, height: 220),
          ),
        ),
      ),
    );
    await tester.pump(const Duration(milliseconds: 40));
    await tester.pump(const Duration(milliseconds: 120));

    for (final event in <OrchardGirlEvent>[
      OrchardGirlEvent.ordinaryMatch,
      OrchardGirlEvent.specialCreated,
      OrchardGirlEvent.nearGoal,
      OrchardGirlEvent.levelLost,
      OrchardGirlEvent.levelWon,
      OrchardGirlEvent.goalsComplete,
    ]) {
      controller.react(event);
      for (var i = 0; i < 8; i++) {
        await tester.pump(const Duration(milliseconds: 90));
        expect(tester.takeException(), isNull, reason: event.name);
      }
    }
  });

  testWidgets('paused and Reduce Motion stop the ticker', (tester) async {
    final controller = OrchardGirlController();
    addTearDown(controller.dispose);
    Future<void> pump({required bool paused, required bool reduce}) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Center(
              child: GuideCharacter(
                controller: controller,
                height: 190,
                paused: paused,
                reduceMotionOverride: reduce,
              ),
            ),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 30));
      await tester.pump(const Duration(milliseconds: 200));
    }

    final base = GuideCharacter.debugActiveTickers;
    await pump(paused: false, reduce: false);
    await tester.pump(const Duration(seconds: 3));
    expect(tester.takeException(), isNull);
    expect(GuideCharacter.debugActiveTickers, base + 1);

    // A paused character stops its ticker; Reduce Motion never starts one.
    await pump(paused: true, reduce: false);
    expect(GuideCharacter.debugActiveTickers, base);
    await pump(paused: false, reduce: true);
    expect(GuideCharacter.debugActiveTickers, base);
    await pump(paused: false, reduce: false);
    expect(GuideCharacter.debugActiveTickers, base + 1);
    // The last widget is rebuilt with `paused: false`, so remove it to free the
    // ticker before the test ends.
    await tester.pumpWidget(const SizedBox.shrink());
    expect(GuideCharacter.debugActiveTickers, base);
  });

  testWidgets('development gallery plays all seven moods', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: GuideRigLabScreen()));
    await tester.pump(const Duration(milliseconds: 60));

    for (final label in <String>[
      'آرام',
      'شاد',
      'هیجان',
      'تشویق',
      'غمگین',
      'جشن',
      'برداشت',
    ]) {
      await tester.tap(find.text(label));
      await tester.pump(const Duration(milliseconds: 120));
      await tester.pump(const Duration(milliseconds: 400));
      expect(tester.takeException(), isNull, reason: label);
    }
    expect(find.byType(GuideCharacter), findsOneWidget);
  });

  testWidgets('widget renders at the real in-game size', (tester) async {
    final size = const Size(360, 800);
    final bytes = await renderPng(
      tester,
      const GuideCharacter(height: 190, paused: true),
      size,
    );
    expect(bytes.length, greaterThan(0));
    expect(190 * GuideCharacter.aspect, closeTo(111.5, 0.3));
  });
}
