import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:puzzle/ui/widgets/orchard_girl.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    // Decoding is real async work; do it once, outside the fake-async widget
    // clock, so every widget test sees the shared cache immediately.
    await OrchardGirlImages.acquire();
  });
  tearDownAll(OrchardGirlImages.release);

  test('game events map to expressive but non-gameplay character states', () {
    const cases = <(OrchardGirlEvent, OrchardGirlMood, String)>[
      (OrchardGirlEvent.ordinaryMatch, OrchardGirlMood.harvest, 'آفرین! سبدت پُرتر شد.'),
      (OrchardGirlEvent.specialCreated, OrchardGirlMood.excited, 'آفرین! یک قدرت تازه ساختی.'),
      (OrchardGirlEvent.specialActivated, OrchardGirlMood.excited, 'آفرین! چه ترکیب خوبی.'),
      (OrchardGirlEvent.cascade, OrchardGirlMood.excited, 'زنجیرهٔ میوه‌ای! آفرین.'),
      (OrchardGirlEvent.nearGoal, OrchardGirlMood.encourage, 'فقط چند میوه مانده؛ از پسش برمی‌آیی.'),
      (OrchardGirlEvent.goalsComplete, OrchardGirlMood.happy, 'همهٔ میوه‌ها جمع شد؛ آفرین!'),
      (OrchardGirlEvent.levelWon, OrchardGirlMood.celebrate, 'باغت شکوفا شد؛ آفرین!'),
      (OrchardGirlEvent.levelLost, OrchardGirlMood.sad, 'هر وقت خواستی دوباره تلاش کن.'),
      (OrchardGirlEvent.validHint, OrchardGirlMood.encourage, 'این حرکت را امتحان کن.'),
      (OrchardGirlEvent.rewardGranted, OrchardGirlMood.happy, 'جایزه به سبدت اضافه شد؛ آفرین!'),
    ];

    for (final (event, mood, caption) in cases) {
      final controller = OrchardGirlController();
      controller.react(event);
      expect(controller.mood, mood, reason: event.name);
      expect(controller.caption, caption, reason: event.name);
      controller.dispose();
    }
  });

  test('captions are throttled and disposal cancels timers', () {
    final controller = OrchardGirlController();
    controller.react(OrchardGirlEvent.validHint);
    expect(controller.caption, 'این حرکت را امتحان کن.');

    controller.react(OrchardGirlEvent.validHint);
    expect(controller.mood, OrchardGirlMood.encourage);
    expect(controller.caption, isNull, reason: 'do not repeat a short line');

    controller.dispose();
    // A disposed controller must not notify listeners when its former reset
    // deadline passes; the timer is canceled as part of dispose.
    expect(() => controller.react(OrchardGirlEvent.levelWon), returnsNormally);
  });

  Finder rigPaint() => find.byWidgetPredicate(
        (widget) => widget is CustomPaint && widget.painter is OrchardGirlPainter,
      );

  /// Pumps until the rig painter exists (assets may arrive a frame later).
  Future<void> waitForRig(WidgetTester tester) async {
    for (var i = 0; i < 60; i++) {
      await tester.pump(const Duration(milliseconds: 20));
      if (rigPaint().evaluate().isNotEmpty) return;
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 20)),
      );
    }
    fail('the layered rig did not finish loading');
  }

  testWidgets('character renders the layered rig and reacts to events',
      (tester) async {
    final controller = OrchardGirlController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: OrchardGirl(
            controller: controller,
            width: 96,
            height: 144,
            showCaption: true,
          ),
        ),
      ),
    );
    await waitForRig(tester);

    expect(find.byType(OrchardGirl), findsOneWidget);
    expect(find.byType(IgnorePointer), findsWidgets);
    expect(find.byType(Image), findsNothing,
        reason: 'the character is drawn from layers, not a whole PNG');

    OrchardGirlPainter painter() =>
        tester.widget<CustomPaint>(rigPaint()).painter! as OrchardGirlPainter;

    expect(painter().pose.mouth, 'smile');
    expect(painter().pose.visibleEyes, 'open');

    controller.react(OrchardGirlEvent.levelWon);
    await tester.pump();
    expect(find.text('باغت شکوفا شد؛ آفرین!'), findsOneWidget);
    for (var i = 0; i < 30; i++) {
      await tester.pump(const Duration(milliseconds: 16));
    }
    expect(painter().pose.mouth, 'laugh');
    expect(painter().pose.angle(OrchardGirlJoints.shoulderRight).abs(),
        greaterThan(10));

    controller.resetToIdle();
    await tester.pump();
    for (var i = 0; i < 30; i++) {
      await tester.pump(const Duration(milliseconds: 16));
    }
    expect(painter().pose.mouth, 'smile');
    expect(painter().pose.blink, lessThanOrEqualTo(1));

    // Remounting mid-animation must not leak a ticker or throw.
    await tester.pumpWidget(const MaterialApp(home: SizedBox()));
    await tester.pump();
    expect(tester.takeException(), isNull);
  });

  testWidgets('reduce motion draws a still character', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: MediaQuery(
          data: const MediaQueryData(disableAnimations: true),
          child: const Scaffold(
            body: OrchardGirl(width: 66, height: 98),
          ),
        ),
      ),
    );
    await waitForRig(tester);

    OrchardGirlPainter painter() =>
        tester.widget<CustomPaint>(rigPaint()).painter! as OrchardGirlPainter;

    final first = painter().pose;
    await tester.pump(const Duration(seconds: 3));
    expect(painter().pose, first);
    expect(first.blink, 0);
    expect(tester.takeException(), isNull);
  });

  testWidgets('public API stays compatible at the small game sizes',
      (tester) async {
    // Constructor used by home, level map and the game must keep working
    // without the controller (static mood).
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: Center(
            child: OrchardGirl(
              mood: OrchardGirlMood.encourage,
              width: 24,
              height: 34,
            ),
          ),
        ),
      ),
    );
    final semantics = tester.ensureSemantics();
    await tester.pump(const Duration(milliseconds: 100));
    expect(find.byType(OrchardGirl), findsOneWidget);
    expect(find.bySemanticsLabel('دختر باغبان'), findsOneWidget);
    expect(tester.takeException(), isNull);
    semantics.dispose();
  });
}
