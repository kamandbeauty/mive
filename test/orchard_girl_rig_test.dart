import 'dart:math' as math;

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:puzzle/ui/widgets/orchard_girl.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('rig manifest', () {
    late OrchardGirlRigData data;

    setUpAll(() async {
      data = await OrchardGirlRigData.load();
    });

    test('canvas and layer order come from the generated manifest', () {
      expect(data.canvasWidth, 512);
      expect(data.canvasHeight, 768);
      expect(data.order, isNotEmpty);
      for (final name in data.order) {
        if (name == 'eyes' || name == 'brows' || name == 'mouth') continue;
        expect(data.layers.containsKey(name), isTrue, reason: name);
      }
      // hair is drawn behind everything and bangs in front of the face
      expect(data.order.first, 'hair_back');
      expect(data.order.last, 'hair_front');
      expect(
        data.order.indexOf('face_base'),
        lessThan(data.order.indexOf('eyes')),
      );
      expect(
        data.order.indexOf('arm_left'),
        lessThan(data.order.indexOf('basket_bowl')),
        reason: 'the basket is attached to the arm and must cover the cut',
      );
    });

    test('every joint used by the Dart rig exists in the manifest', () {
      for (final joint in orchardGirlJointParents.keys) {
        expect(data.joints.containsKey(joint), isTrue, reason: joint);
        final pivot = data.joints[joint]!;
        expect(pivot.dx, inInclusiveRange(0, data.canvasWidth));
        expect(pivot.dy, inInclusiveRange(0, data.canvasHeight));
      }
    });

    test('every layer is mapped to a joint in the parent chain', () {
      for (final name in data.layers.keys) {
        final group = name.split('_').first;
        if (name.startsWith('eyes_') ||
            name.startsWith('brows_') ||
            name.startsWith('mouth_')) {
          continue;
        }
        expect(
          orchardGirlLayerJoints.containsKey(name),
          isTrue,
          reason: 'layer $name (group $group) has no joint',
        );
      }
    });

    test('all referenced PNG layers are present in the asset bundle', () async {
      for (final name in data.layers.keys) {
        final bytes = await rootBundle
            .load('assets/game/characters/rig/$name.png');
        expect(bytes.lengthInBytes, greaterThan(0), reason: name);
      }
    });

    test('mood faces only reference variants that exist as layers', () {
      for (final entry in data.moodFaces.entries) {
        final face = entry.value;
        expect(
          data.layers.containsKey('eyes_${face.eyes}_left'),
          isTrue,
          reason: '${entry.key} eyes',
        );
        expect(
          data.layers.containsKey('brows_${face.brows}_left'),
          isTrue,
          reason: '${entry.key} brows',
        );
        expect(
          data.layers.containsKey('mouth_${face.mouth}'),
          isTrue,
          reason: '${entry.key} mouth',
        );
        expect(
          data.layers.containsKey('eyes_closed_left'),
          isTrue,
          reason: 'blink needs a closed eye layer',
        );
      }
      expect(data.moodFaces.keys,
          containsAll(<String>['idle', 'happy', 'excited', 'encourage']));
    });
  });

  group('procedural pose', () {
    const moods = <String>[
      'idle',
      'happy',
      'excited',
      'encourage',
      'sad',
      'celebrate',
      'harvest',
    ];

    test('all seven moods produce finite, bounded poses', () {
      for (final mood in moods) {
        var moved = false;
        var previous = OrchardGirlAnimator.poseAt(mood, 0);
        for (var t = 0.0; t < 6; t += 0.05) {
          final pose = OrchardGirlAnimator.poseAt(mood, t);
          for (final angle in pose.angles.values) {
            expect(angle.isFinite, isTrue, reason: '$mood $t');
            // Visually safe envelope of the extracted, single-pose limbs.
            expect(angle.abs(), lessThanOrEqualTo(24), reason: '$mood $t');
          }
          expect(pose.angle(OrchardGirlJoints.head).abs(), lessThanOrEqualTo(9));
          expect(
            pose.angle(OrchardGirlJoints.elbowRight).abs(),
            lessThanOrEqualTo(20),
            reason: '$mood $t',
          );
          // The basket hand is frozen: rotating that arm swings the basket
          // away from the fingers and exposes reconstructed pixels.
          expect(
            pose.angle(OrchardGirlJoints.shoulderLeft),
            0,
            reason: '$mood $t',
          );
          expect(
            pose.angle(OrchardGirlJoints.basketLeft).abs(),
            lessThanOrEqualTo(2),
            reason: '$mood $t',
          );
          expect(pose.rootScale, inInclusiveRange(0.97, 1.03));
          if (pose != previous) moved = true;
          previous = pose;
        }
        expect(moved, isTrue, reason: '$mood should animate');
      }
    });

    test('rest pose of the idle mood is the neutral drawing', () {
      final pose = OrchardGirlAnimator.poseAt('idle', 0);
      // t = 0 sits exactly on the zero crossing of every idle oscillator
      expect(pose.angle(OrchardGirlJoints.head), 0);
      expect(pose.angle(OrchardGirlJoints.shoulderRight), 0);
      expect(pose.rootOffset, Offset.zero);
      expect(pose.rootScale, 1);
      expect(pose.visibleEyes, 'open');
      expect(pose.brows, 'neutral');
      expect(pose.mouth, 'smile');
    });

    test('joints move independently instead of one global transform', () {
      double maxDelta(String mood, String joint) {
        var low = double.infinity;
        var high = -double.infinity;
        for (var t = 0.0; t < 5; t += 0.05) {
          final value = OrchardGirlAnimator.poseAt(mood, t).angle(joint);
          low = math.min(low, value);
          high = math.max(high, value);
        }
        return high - low;
      }

      final head = maxDelta('idle', OrchardGirlJoints.head);
      final lock = maxDelta('idle', OrchardGirlJoints.lockLeft);
      expect(head, greaterThan(0));
      expect(lock, greaterThan(0));
      // hair and head never share one curve exactly (secondary motion)
      expect((lock - head).abs(), greaterThan(0.3));
      expect(
        maxDelta('excited', OrchardGirlJoints.shoulderRight),
        greaterThan(maxDelta('idle', OrchardGirlJoints.shoulderRight)),
      );
    });

    test('sad is quieter than celebrate', () {
      double motion(String mood) => OrchardGirlAnimator.poseAt(mood, 0.9)
          .angle(OrchardGirlJoints.shoulderRight)
          .abs();
      expect(motion('sad'), lessThan(motion('celebrate')));
      expect(motion('sad'), lessThan(4));
    });
  });

  group('blinking', () {
    test('blinks are irregular, brief and only change the eye layer', () {
      final animator = OrchardGirlAnimator();
      addTearDown(animator.dispose);
      var closedFrames = 0;
      var totalFrames = 0;
      for (var i = 0; i <= 60 * 12; i++) {
        animator.tick(Duration(milliseconds: i * 16));
        totalFrames++;
        if (animator.pose.eyesClosed) {
          closedFrames++;
          // A blink must not teleport the body or swap the whole face.
          final reference =
              OrchardGirlAnimator.poseAt('idle', animator.moodElapsedSeconds);
          for (final joint in reference.angles.keys) {
            expect(
              animator.pose.angle(joint),
              closeTo(reference.angle(joint), 1e-6),
              reason: 'blink moved $joint',
            );
          }
          expect(animator.pose.mouth, 'smile');
          expect(animator.pose.brows, 'neutral');
        }
      }
      expect(animator.blinkCount, greaterThanOrEqualTo(2));
      expect(closedFrames, greaterThan(0));
      expect(closedFrames / totalFrames, lessThan(0.35));
    });

    test('excited and celebrate never blink over their wide eyes', () {
      final animator = OrchardGirlAnimator()..setMood('celebrate');
      addTearDown(animator.dispose);
      for (var i = 0; i <= 60 * 6; i++) {
        animator.tick(Duration(milliseconds: i * 16));
        expect(animator.pose.eyesClosed, isFalse);
      }
    });

    test('reduce motion freezes the pose and stops blinking', () {
      final animator = OrchardGirlAnimator(reduceMotion: true);
      addTearDown(animator.dispose);
      final first = animator.pose;
      for (var i = 0; i <= 60 * 10; i++) {
        animator.tick(Duration(milliseconds: i * 16));
        expect(animator.pose, first);
      }
      expect(animator.blinkCount, 0);
    });

    test('mood switch blends instead of snapping', () {
      final animator = OrchardGirlAnimator();
      addTearDown(animator.dispose);
      animator.tick(const Duration(milliseconds: 0));
      animator.setMood('celebrate');
      final blended = animator.pose;
      expect(blended.mouth, anyOf('smile', 'laugh'));
      expect(blended.faceBlend, lessThan(1));
      for (var i = 1; i <= 40; i++) {
        animator.tick(Duration(milliseconds: i * 16));
      }
      expect(animator.pose.faceBlend, 1);
      expect(animator.pose.mouth, 'laugh');
    });
  });

  group('event priority', () {
    test('a small event cannot cancel a celebration that just started', () {
      final controller = OrchardGirlController();
      addTearDown(controller.dispose);
      controller.react(OrchardGirlEvent.levelWon);
      expect(controller.mood, OrchardGirlMood.celebrate);
      controller.react(OrchardGirlEvent.validHint);
      expect(
        controller.mood,
        OrchardGirlMood.celebrate,
        reason: 'celebrate holds for its priority window',
      );
      controller.react(OrchardGirlEvent.levelLost);
      expect(
        controller.mood,
        OrchardGirlMood.sad,
        reason: 'an equally important event may still take over',
      );
    });

    test('repeated events reuse the same controller and mood', () {
      final controller = OrchardGirlController();
      addTearDown(controller.dispose);
      for (var i = 0; i < 5; i++) {
        controller.react(OrchardGirlEvent.cascade);
        expect(controller.mood, OrchardGirlMood.excited);
      }
    });
  });
}
