// Responsive layout sweep for the redesigned match-3 screen.
//
// The redesign has to hold up on 320x640, 360x800 and 412x915 phones with the
// system safe areas applied. These tests pump the real game widget at those
// sizes and fail on any layout exception (RenderFlex overflow, unbounded
// constraints, etc.), then check by geometry that the decorative layers stay
// off the board and inside the safe area.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:puzzle/game/models/fruit_kind.dart';
import 'package:puzzle/game/models/level_definition.dart';
import 'package:puzzle/ui/features/gem_match/gem_match_game.dart';
import 'package:puzzle/ui/widgets/game_ui/game_ui.dart';
import 'package:puzzle/ui/widgets/guide_character/guide_character.dart';

const _level = LevelDefinition(
  id: 3,
  chapter: 'grove',
  moveLimit: 24,
  targetScore: 1000,
  fruitGoals: <FruitKind, int>{FruitKind.strawberry: 12},
  starThresholds: <int>[1000, 1500, 2000],
  difficulty: LevelDifficulty.easy,
);

class _Device {
  const _Device(this.name, this.size, this.topInset, this.bottomInset);

  final String name;
  final Size size;
  final double topInset;
  final double bottomInset;
}

const _devices = <_Device>[
  _Device('320x640', Size(320, 640), 24, 0),
  _Device('360x800', Size(360, 800), 24, 24),
  _Device('412x915', Size(412, 915), 32, 48),
];

/// Pumps small frames until the opening animations have settled, failing on the
/// first frame that reports a layout error.
Future<void> _pumpFrames(WidgetTester tester, int frames) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 80));
    expect(tester.takeException(), isNull, reason: 'frame $i');
  }
}

Finder _asset(String name) => find.byWidgetPredicate(
      (Widget widget) =>
          widget is Image &&
          widget.image is AssetImage &&
          (widget.image as AssetImage).assetName == name,
      description: 'Image.asset($name)',
    );

void main() {
  for (final device in _devices) {
    testWidgets('redesigned game screen fits ${device.name} with safe areas',
        (tester) async {
      tester.view.physicalSize = device.size * 3;
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        MaterialApp(
          builder: (BuildContext context, Widget? child) {
            final media = MediaQuery.of(context);
            return MediaQuery(
              data: media.copyWith(
                padding: EdgeInsets.only(
                  top: device.topInset,
                  bottom: device.bottomInset,
                ),
                viewPadding: EdgeInsets.only(
                  top: device.topInset,
                  bottom: device.bottomInset,
                ),
              ),
              child: child!,
            );
          },
          home: const GemMatchGame(level: _level, randomSeed: 11),
        ),
      );
      await _pumpFrames(tester, 6);

      final body = Rect.fromLTWH(
        0,
        device.topInset,
        device.size.width,
        device.size.height - device.topInset - device.bottomInset,
      ).inflate(0.5);

      // Every board cell is laid out, on-screen and inside the safe area.
      var visibleCells = 0;
      for (var i = 0; i < 64; i++) {
        final cell = find.byKey(ValueKey<String>('fruit-cell-$i'));
        if (cell.evaluate().isEmpty) continue;
        visibleCells++;
        final rect = tester.getRect(cell);
        expect(rect.width, greaterThan(10), reason: 'cell $i width');
        expect(rect.height, greaterThan(10), reason: 'cell $i height');
        expect(body.contains(rect.topLeft), isTrue,
            reason: 'cell $i top-left ${rect.topLeft} leaves $body');
        expect(body.contains(rect.bottomRight), isTrue,
            reason: 'cell $i bottom-right ${rect.bottomRight} leaves $body');
      }
      expect(visibleCells, 64);

      final board = tester.getRect(
        find.byKey(const ValueKey<String>('game-board-frame')),
      );
      expect(body.contains(board.topLeft), isTrue, reason: 'board top');
      expect(body.contains(board.bottomRight), isTrue, reason: 'board bottom');
      expect(board.width, greaterThan(200), reason: 'board keeps its presence');

      // The character sits under the board, never on it.
      final guide = find.byType(GuideCharacter);
      expect(guide, findsOneWidget);
      final guideRect = tester.getRect(guide);
      expect(guideRect.top, greaterThanOrEqualTo(board.bottom - 0.5),
          reason: 'character zone overlaps the board');
      expect(body.contains(guideRect.bottomRight), isTrue,
          reason: 'character leaves the safe area');

      // The four-slot power tray is fully on screen, clear of the board.
      final tray = tester.getRect(
        _asset('assets/ui/booster-tray-four-slots.png').first,
      );
      expect(body.contains(tray.topLeft), isTrue, reason: 'tray top-left');
      expect(body.contains(tray.bottomRight), isTrue,
          reason: 'tray bottom-right');
      expect(tray.overlaps(board), isFalse, reason: 'tray overlaps the board');

      // A bubble, when the guide speaks, must not cover the board either.
      final bubble = find.byType(OrchardBubble);
      if (bubble.evaluate().isNotEmpty) {
        final bubbleRect = tester.getRect(bubble.first);
        expect(bubbleRect.overlaps(board), isFalse,
            reason: 'bubble overlaps the board');
        expect(body.contains(bubbleRect.topLeft), isTrue,
            reason: 'bubble leaves the safe area');
      }

      // Input still reaches the fruit under the decorative layers.
      await tester.tap(find.byKey(const ValueKey<String>('fruit-cell-0')));
      await _pumpFrames(tester, 3);

      // Tear the tree down before the next device size.
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(milliseconds: 400));
    });
  }
}
